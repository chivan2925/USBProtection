// Shared HTTP code. Passwords and session cookies are never saved in JavaScript.
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let csrfToken = ''
let csrfHeader = 'X-XSRF-TOKEN'

export function clearCsrfToken() {
  csrfToken = ''
  csrfHeader = 'X-XSRF-TOKEN'
}

export async function refreshCsrfToken(signal?: AbortSignal) {
  clearCsrfToken()
  const data = readObject(await request('/api/auth/csrf', { signal, notifyUnauthorized: false }))
  if (typeof data.token !== 'string' || !data.token.trim() || data.headerName !== 'X-CSRF-TOKEN') {
    throw new Error('Máy chủ trả CSRF token hoặc header không hợp lệ.')
  }
  csrfToken = data.token
  csrfHeader = data.headerName
}

function errorMessage(status: number) {
  if (status === 400) return 'Dữ liệu gửi lên không hợp lệ. Hãy kiểm tra fingerprint.'
  if (status === 401) return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.'
  if (status === 403) return 'Không có quyền thực hiện hoặc CSRF không hợp lệ. Hãy tải lại trang.'
  if (status === 404) return 'API hoặc dữ liệu chưa có trên máy chủ.'
  if (status === 409) return 'Dữ liệu bị trùng hoặc đã thay đổi. Hãy tải lại dữ liệu.'
  if (status >= 500) return 'Máy chủ gặp lỗi. Vui lòng thử lại sau.'
  return `Yêu cầu thất bại (HTTP ${status}).`
}

interface RequestOptions {
  method?: string
  body?: unknown
  signal?: AbortSignal
  notifyUnauthorized?: boolean
  timeoutMs?: number
}

export async function request(path: string, options: RequestOptions = {}): Promise<unknown> {
  options.signal?.throwIfAborted()
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    if (!csrfToken) throw new ApiError(403, 'Chưa có CSRF token từ server. Hãy tải lại trang hoặc kiểm tra API /api/auth/csrf.')
    headers[csrfHeader] = csrfToken
  }

  const timeout = AbortSignal.timeout(options.timeoutMs ?? 15000)
  const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout
  let response: Response
  try {
    response = await fetch(path, {
      method, headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'include', redirect: 'error', cache: 'no-store', signal,
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    if (timeout.aborted) throw new ApiError(0, 'Máy chủ phản hồi quá lâu. Vui lòng thử lại.')
    throw new ApiError(0, 'Không kết nối được máy chủ. Hãy kiểm tra mạng và backend.')
  }

  if (response.status === 401 && options.notifyUnauthorized !== false && typeof window !== 'undefined') {
    window.dispatchEvent(new Event('usbshield:session-expired'))
  }
  let data: unknown = null
  if (response.status !== 204 && response.headers.get('content-type')?.includes('application/json')) {
    try { data = await response.json() } catch { throw new ApiError(response.status, 'Máy chủ trả JSON không hợp lệ.') }
  }
  if (!response.ok) {
    throw new ApiError(response.status, errorMessage(response.status))
  }
  if (response.status !== 204 && data === null) {
    throw new ApiError(response.status, 'Máy chủ chưa trả dữ liệu JSON theo contract Admin API.')
  }
  return data
}

// Small checks keep bad payloads from looking like valid empty data.
export function readObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('API trả object không hợp lệ.')
  return value as Record<string, unknown>
}

export function readText(value: unknown, required = false): string | undefined {
  if (value == null && !required) return undefined
  if (typeof value !== 'string' || (required && !value.trim())) throw new Error('API trả trường văn bản không hợp lệ.')
  return value
}

export function readId(value: unknown): string {
  if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) return String(value)
  return readText(value, true)!
}

export function readVersion(value: unknown): string | undefined {
  if (value == null) return undefined
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error('API trả policy version không hợp lệ.')
  return String(value)
}
