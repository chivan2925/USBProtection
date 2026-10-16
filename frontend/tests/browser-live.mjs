// Real Spring Boot/H2 + Chromium integration on Windows. HTTP-seeded QA events
// are synthetic; this run does not represent physical Ubuntu/USBGuard evidence.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const artifacts = join(root, '.artifacts')
await mkdir(artifacts, { recursive: true })
const runtime = await mkdtemp(join(artifacts, 'live-events-'))
const jar = resolve(root, '../admin-server/target/usbshield-admin-server.jar')
const chrome = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(path => path && existsSync(path))
assert.ok(chrome && existsSync(jar), 'Requires local Chromium and a built Admin Server JAR.')
const api = 'http://127.0.0.1:18083'
const web = 'http://127.0.0.1:4174'
const password = randomUUID()
const enrollmentToken = randomUUID()
const username = 'qa-browser-admin'
const profile = join(runtime, 'chrome')
const children = []
let socket
let serverOutput = ''
const delay = ms => new Promise(resolveDelay => setTimeout(resolveDelay, ms))
async function until(check, label, limit = 200) {
  for (let attempt = 0; attempt < limit; attempt++) {
    try { if (await check()) return } catch { /* Wait for process/React state. */ }
    await delay(100)
  }
  throw new Error(`Timed out: ${label}`)
}
function start(executable, args, options = {}) {
  const child = spawn(executable, args, { cwd: root, windowsHide: true, stdio: 'pipe', ...options })
  child.stdout?.on('data', data => { serverOutput = (serverOutput + data).slice(-16000) })
  child.stderr?.on('data', data => { serverOutput = (serverOutput + data).slice(-16000) })
  children.push(child)
  return child
}
async function post(path, body, headers = {}) {
  const response = await fetch(`${api}${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) })
  assert.equal(response.status, 200, `QA HTTP ${path}`)
  return response.json()
}
try {
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin/java.exe') : 'C:/Program Files/Java/jdk-22/bin/java.exe'
  const backend = start(java, ['-jar', jar, '--server.port=18083', '--server.address=127.0.0.1', `--spring.datasource.url=jdbc:h2:file:${join(runtime, 'qa-db').replaceAll('\\', '/')};DB_CLOSE_ON_EXIT=FALSE`], {
    env: { ...process.env, USBSHIELD_ADMIN_USERNAME: username, USBSHIELD_ADMIN_PASSWORD: password, USBSHIELD_ENROLLMENT_TOKEN: enrollmentToken },
  })
  await until(async () => {
    assert.equal(backend.exitCode, null, 'Backend must stay alive')
    return (await fetch(`${api}/api/auth/csrf`)).ok
  }, 'real backend readiness', 1200)
  const endpoint = await post('/api/agent/enroll', { hostname: 'Windows-HTTP-QA', machineId: randomUUID(), enrollmentToken })
  const headers = { 'X-Endpoint-Id': endpoint.endpointId, 'X-Agent-Token': endpoint.agentToken }
  const baseEvent = {
    endpointId: endpoint.endpointId, hostname: 'Windows-HTTP-QA', linuxUsername: 'qa-linux-user', linuxUid: 1000, sessionId: 'qa-session', sessionType: 'wayland', seat: 'seat0',
    device: { vendorId: '0951', productId: '1666', serial: 'HTTP-QA-ONLY', name: 'Windows HTTP QA storage', hash: 'windows-http-qa-device-hash', interfaceClass: '08:06:50' },
    eventType: 'CONNECTED', decision: 'BLOCKED',
  }
  for (let index = 0; index < 23; index++) await post('/api/agent/events', { ...baseEvent, decision: index % 2 ? 'ALLOWED' : 'BLOCKED', occurredAt: new Date(Date.UTC(2026, 9, 16, 10, 0, index)).toISOString() }, headers)
  // H2's persisted timestamp column has microsecond precision.
  await post('/api/agent/events', { ...baseEvent, occurredAt: '2026-10-16T16:59:59.999999Z' }, headers)
  await post('/api/agent/events', { ...baseEvent, occurredAt: '2026-10-16T17:00:00Z' }, headers)
  console.log('PASS: isolated Spring Boot/H2 accepted 25 synthetic QA events through authenticated Agent HTTP.')

  start(process.execPath, [join(root, 'node_modules/vite/bin/vite.js'), 'preview', '--host', '127.0.0.1', '--port', '4174', '--strictPort'], { env: { ...process.env, USBSHIELD_API_TARGET: api } })
  await until(async () => (await fetch(web)).ok, 'preview/proxy readiness')
  start(chrome, ['--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-extensions', '--disable-background-networking', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' })
  // Chromium has no stdout/stderr with stdio ignore.
  const portFile = join(profile, 'DevToolsActivePort')
  await until(() => existsSync(portFile), 'Chromium readiness')
  const port = (await readFile(portFile, 'utf8')).split('\n')[0].trim()
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
  socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolveOpen, reject) => { socket.addEventListener('open', resolveOpen, { once: true }); socket.addEventListener('error', reject, { once: true }) })
  let sequence = 0
  const pending = new Map()
  const errors = []
  const requests = []
  let injectFailure
  const cdp = (method, params = {}) => new Promise((resolveCall, reject) => {
    const id = ++sequence
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)) }, 15000)
    pending.set(id, { resolveCall, reject, timer })
    socket.send(JSON.stringify({ id, method, params }))
  })
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text)
    if (message.method === 'Network.requestWillBeSent' && ['Fetch', 'XHR'].includes(message.params.type)) requests.push(message.params.request.url)
    if (message.method === 'Fetch.requestPaused') {
      const params = { requestId: message.params.requestId }
      const response = injectFailure === 'network'
        ? cdp('Fetch.failRequest', { ...params, errorReason: 'InternetDisconnected' })
        : cdp('Fetch.fulfillRequest', { ...params, responseCode: Number(injectFailure), responseHeaders: [{ name: 'Content-Type', value: 'application/json' }], body: Buffer.from('{}').toString('base64') })
      response.catch(error => errors.push(error.message))
    }
    if (pending.has(message.id)) {
      const { resolveCall, reject, timer } = pending.get(message.id)
      clearTimeout(timer)
      pending.delete(message.id)
      if (message.error) reject(new Error(JSON.stringify(message.error)))
      else resolveCall(message.result)
    }
  })
  const evaluate = async expression => {
    const result = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    assert.equal(result.exceptionDetails, undefined, 'No exception in browser evaluation')
    return result.result.value
  }
  const fill = (selector, value) => evaluate(`{ const input = document.querySelector(${JSON.stringify(selector)}); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(value)}); input.dispatchEvent(new Event('input', { bubbles: true })); }`)
  const bodyHas = text => evaluate(`document.body.innerText.includes(${JSON.stringify(text)})`)
  const screenshot = async name => {
    const result = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    await writeFile(join(artifacts, name), Buffer.from(result.data, 'base64'))
  }
  await cdp('Runtime.enable')
  await cdp('Network.enable')
  await cdp('Page.enable')
  await cdp('Emulation.setTimezoneOverride', { timezoneId: 'Asia/Bangkok' })
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })
  await cdp('Page.navigate', { url: `${web}/#/events` })
  await until(() => bodyHas('Cần đăng nhập quản trị'), 'unauthenticated Events')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  await evaluate(`location.hash = '/login'`)
  await until(() => evaluate('!!document.querySelector("#username")'), 'login form')
  await fill('#username', username)
  await fill('#password', 'incorrect-test-password')
  await evaluate('document.querySelector("button[type=submit]").click()')
  await until(() => bodyHas('Tên đăng nhập hoặc mật khẩu không đúng.'), 'real invalid login')
  await fill('#password', password)
  await evaluate('document.querySelector("button[type=submit]").click()')
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'real persisted Events page')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 20)
  const deviceId = await evaluate(`document.querySelector('.device-event-id').textContent.match(/[0-9]+/)[0]`)
  assert.equal(await evaluate('document.querySelector("tbody").innerText.includes("qa-linux-user")'), true)
  assert.equal(await evaluate('localStorage.length + sessionStorage.length'), 0)
  await screenshot('events-real-http-desktop.png')
  await evaluate(`document.querySelector('[aria-label="Trang sau"]').click()`)
  await until(() => bodyHas('21–25 trên 25 bản ghi'), 'server second page')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 5)
  await fill('[aria-label="Linux Username"]', 'no-such-user')
  await until(() => bodyHas('Không tìm thấy kết quả'), 'real empty filter result')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  const reset = async () => {
    await evaluate(`document.querySelector('[aria-label="Đặt lại tất cả bộ lọc"]').click()`)
    await until(() => bodyHas('1–20 trên 25 bản ghi'), 'reset server query')
  }
  await reset()
  await fill('[aria-label="Endpoint"]', 'no-such-endpoint')
  await until(() => bodyHas('Không tìm thấy kết quả'), 'exact endpoint empty result')
  await fill('[aria-label="Endpoint"]', endpoint.endpointId)
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'exact endpoint identity')
  await reset()
  await fill('[aria-label="Device"]', '999999')
  await until(() => bodyHas('Không tìm thấy kết quả'), 'unknown device ID empty result')
  await fill('[aria-label="Device"]', deviceId)
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'persisted device ID query')
  await reset()
  await evaluate(`{ const select = document.querySelector('[aria-label="Event Type"]'); select.value = 'disconnected'; select.dispatchEvent(new Event('change', { bubbles: true })); }`)
  await until(() => bodyHas('Không tìm thấy kết quả'), 'event type global filter')
  await reset()
  await evaluate(`{ const select = document.querySelector('[aria-label="Decision"]'); select.value = 'blocked'; select.dispatchEvent(new Event('change', { bubbles: true })); }`)
  await until(() => bodyHas('1–14 trên 14 bản ghi'), 'server decision filter')
  assert.equal(await evaluate('Array.from(document.querySelectorAll("tbody tr"), row => row.lastElementChild.textContent).every(value => value === "BLOCK")'), true)
  await reset()
  await fill('[aria-label="From Date"]', '2026-10-16')
  await fill('[aria-label="To Date"]', '2026-10-16')
  await until(() => bodyHas('1–20 trên 24 bản ghi'), 'inclusive calendar date query')
  assert.equal(await evaluate(`!!document.querySelector('time[datetime="2026-10-16T16:59:59.999999Z"]')`), true)
  assert.equal(await evaluate(`!!document.querySelector('time[datetime="2026-10-16T17:00:00Z"]')`), false)
  assert.ok(requests.some(url => new URL(url).searchParams.get('to') === '2026-10-16T16:59:59.999999999Z'))
  await screenshot('events-real-http-date-filter.png')
  await reset()
  await cdp('Page.reload')
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'cookie session restored on reload')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 375, height: 812, deviceScaleFactor: 1, mobile: true })
  assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'Live Events mobile overflow')
  await screenshot('events-real-http-mobile.png')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })
  await cdp('Fetch.enable', { patterns: [{ urlPattern: '*/api/admin/events*', requestStage: 'Request' }] })
  injectFailure = '403'
  await evaluate('document.querySelector(".refresh-actions button").click()')
  await until(() => bodyHas('Bạn không có quyền truy cập'), 'injected HTTP 403 UI')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  injectFailure = '500'
  await evaluate('document.querySelector(".refresh-actions button").click()')
  await until(() => bodyHas('Máy chủ chưa xử lý được yêu cầu.'), 'injected HTTP 500 UI')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  injectFailure = 'network'
  await evaluate('document.querySelector(".refresh-actions button").click()')
  await until(() => bodyHas('kiểm tra kết nối với máy chủ'), 'injected network outage UI')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  await cdp('Fetch.disable')
  await evaluate('document.querySelector(".refresh-actions button").click()')
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'recovery without demo fallback')
  await evaluate(`Array.from(document.querySelectorAll('button')).find(button => button.textContent === 'Đăng xuất').click()`)
  await until(() => evaluate('!!document.querySelector(".login-page")'), 'real logout')
  await evaluate(`location.hash = '/events'`)
  await until(() => bodyHas('Cần đăng nhập quản trị'), 'logout invalidates browser session')
  assert.deepEqual(errors, [])
  console.log('PASS: real Admin login/invalid login, CSRF/session, H2-backed Events, server pagination/filter/empty/date nanoseconds, reload, logout, mobile layout.')
  console.log('PASS: browser-injected 403/500/network failures clear rows; recovery reads real API. Screenshots: frontend/.artifacts/events-real-http-*.png')
  console.log('LIMIT: Windows HTTP-seeded QA events in isolated H2; physical Ubuntu/USB/GUI tests remain NOT EXECUTED.')
  await cdp('Browser.close')
} catch (error) {
  await writeFile(join(artifacts, 'live-events-server-last.log'), serverOutput, 'utf8')
  throw error
} finally {
  socket?.close()
  for (const child of children.reverse()) if (child.exitCode === null) child.kill()
  await delay(1000)
  const runtimeRelative = relative(artifacts, resolve(runtime))
  assert.ok(runtimeRelative.startsWith('live-events-') && !runtimeRelative.includes(sep))
  await rm(runtime, { recursive: true, force: true, maxRetries: 10, retryDelay: 300 })
}
