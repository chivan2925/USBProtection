// Real Spring Boot/H2 + Chromium integration on Windows. HTTP-seeded QA events
// are synthetic; this run does not represent physical Ubuntu/USBGuard evidence.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { execFile, spawn, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'node:net'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const artifacts = join(root, '.artifacts')
await mkdir(artifacts, { recursive: true })
const runtime = await mkdtemp(join(artifacts, 'live-events-'))
const jar = resolve(root, '../admin-server/target/usbshield-admin-server.jar')
const chrome = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(path => path && existsSync(path))
assert.ok(chrome && existsSync(jar), 'Requires local Chromium and a built Admin Server JAR.')
// Bind an OS-selected port: an orphaned JVM from a previous run must not
// impersonate this run's isolated Spring Boot/H2 server.
const portReservation = createServer()
await new Promise((resolveListen, rejectListen) => {
  portReservation.once('error', rejectListen)
  portReservation.listen(0, '127.0.0.1', resolveListen)
})
const backendPort = portReservation.address().port
await new Promise(resolveClose => portReservation.close(resolveClose))
const api = `http://127.0.0.1:${backendPort}`
const web = 'http://127.0.0.1:4174'
const password = randomUUID()
const enrollmentToken = randomUUID()
const username = 'qa-browser-admin'
const profile = join(runtime, 'chrome')
const children = []
let socket
let serverOutput = ''
const spawnErrors = new WeakMap()
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
  child.on('error', error => { spawnErrors.set(child, error) })
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
  // The previous hard-coded JDK 22 path broke Windows machines using JDK 21.
  // Honor JAVA_HOME when provided; otherwise resolve `java` from PATH, like M4's live harness.
  const java = process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java'
  if (process.env.JAVA_HOME && !existsSync(java)) {
    throw new Error(`JAVA_HOME points to a missing Java executable: ${java}`)
  }
  const javaCheck = spawnSync(java, ['-version'], { windowsHide: true, encoding: 'utf8', timeout: 15000 })
  if (javaCheck.error || javaCheck.status !== 0) {
    throw new Error(`Cannot launch Java (${java}): ${javaCheck.error?.message || javaCheck.stderr || `exit ${javaCheck.status}`}`)
  }
  const backend = start(java, ['-jar', jar, `--server.port=${backendPort}`, '--server.address=127.0.0.1', `--spring.datasource.url=jdbc:h2:file:${join(runtime, 'qa-db').replaceAll('\\', '/')};DB_CLOSE_ON_EXIT=FALSE`], {
    env: { ...process.env, USBSHIELD_ADMIN_USERNAME: username, USBSHIELD_ADMIN_PASSWORD: password, USBSHIELD_ENROLLMENT_TOKEN: enrollmentToken },
  })
  // Do not suppress a JVM launch error or early exit for two minutes.
  let ready = false
  for (let attempt = 0; attempt < 1200; attempt++) {
    const launchError = spawnErrors.get(backend)
    if (launchError) throw new Error(`Admin Server Java spawn failed (${java}): ${launchError.message}`)
    if (backend.exitCode !== null || backend.signalCode !== null) {
      throw new Error(`Admin Server exited before readiness: exit=${backend.exitCode} signal=${backend.signalCode}\n${serverOutput}`)
    }
    try {
      if ((await fetch(`${api}/api/auth/csrf`, { signal: AbortSignal.timeout(1500) })).ok) {
        ready = true
        break
      }
    } catch { /* Spring Boot is not ready yet. */ }
    await delay(100)
  }
  if (!ready) throw new Error(`Timed out: real backend readiness (${java}, ${api})\n${serverOutput}`)
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
  await until(() => evaluate('location.hash === "#/login" && !!document.querySelector("#password")'), 'unauthenticated Events redirects to login')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  await evaluate(`location.hash = '/login'`)
  await until(() => evaluate('!!document.querySelector("#username")'), 'login form')
  await fill('#username', username)
  await fill('#password', 'incorrect-test-password')
  await evaluate('document.querySelector("button[type=submit]").click()')
  await until(() => bodyHas('Tên đăng nhập hoặc mật khẩu không đúng.'), 'real invalid login')
  await fill('#password', password)
  await evaluate('document.querySelector("button[type=submit]").click()')
  await until(() => evaluate('location.hash === "#/dashboard"'), 'authenticated dashboard')
  await evaluate(`location.hash = '/events'`)
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
  await fill('[aria-label="Endpoint"]', 'missing-endpoint-name')
  await until(() => bodyHas('Không tìm thấy kết quả'), 'unknown endpoint name')
  await fill('[aria-label="Endpoint"]', 'windows-http-qa')
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'visible endpoint hostname search')
  assert.ok(requests.some(url => new URL(url).searchParams.get('endpoint') === 'windows-http-qa'))
  await reset()
  await fill('[aria-label="Device"]', '999999')
  await until(() => bodyHas('Không tìm thấy kết quả'), 'unknown device ID empty result')
  await fill('[aria-label="Device"]', deviceId)
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'persisted device ID query')
  await fill('[aria-label="Device"]', 'missing-usb-name')
  await until(() => bodyHas('Không tìm thấy kết quả'), 'unknown USB name')
  await fill('[aria-label="Device"]', 'http qa storage')
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'visible USB name search')
  assert.ok(requests.some(url => new URL(url).searchParams.get('device') === 'http qa storage'))
  await screenshot('events-name-search.png')
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
  await until(() => bodyHas('Không có quyền'), 'injected HTTP 403 UI')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  injectFailure = '500'
  await evaluate('document.querySelector(".refresh-actions button").click()')
  await until(() => bodyHas('Máy chủ gặp lỗi'), 'injected HTTP 500 UI')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  injectFailure = 'network'
  await evaluate('document.querySelector(".refresh-actions button").click()')
  await until(() => bodyHas('Không kết nối'), 'injected network outage UI')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  await cdp('Fetch.disable')
  await evaluate('document.querySelector(".refresh-actions button").click()')
  await until(() => bodyHas('1–20 trên 25 bản ghi'), 'recovery without demo fallback')

  // Week 3 synthetic attribution and isolation regression; no Linux resolver is run.
  const endpointB = await post('/api/agent/enroll', { hostname: 'Windows-HTTP-QA-B', machineId: randomUUID(), enrollmentToken })
  const headersB = { 'X-Endpoint-Id': endpointB.endpointId, 'X-Agent-Token': endpointB.agentToken }
  const noSerialDevice = { ...baseEvent.device, serial: null, name: 'Week 3 HTTP no-serial storage', hash: 'week3-http-no-serial-shared-hash' }
  await post('/api/agent/events', {
    ...baseEvent, device: noSerialDevice, linuxUsername: 'UNKNOWN', linuxUid: null, sessionId: null, sessionType: null, seat: null,
    occurredAt: '2026-10-17T10:00:00.123456Z',
  }, headers)
  await post('/api/agent/events', {
    ...baseEvent, device: noSerialDevice, linuxUsername: 'qa-week3-user-a', linuxUid: 1001, sessionId: 'week3-new-session',
    occurredAt: '2026-10-17T10:00:01Z',
  }, headers)
  await post('/api/agent/events', {
    ...baseEvent, endpointId: endpointB.endpointId, hostname: 'Windows-HTTP-QA-B', device: noSerialDevice,
    linuxUsername: 'qa-week3-user-b', linuxUid: 1002, sessionId: 'week3-endpoint-b-session', occurredAt: '2026-10-17T10:00:02Z',
  }, headersB)
  const resetWeek3 = async () => {
    await evaluate(`document.querySelector('[aria-label="Đặt lại tất cả bộ lọc"]').click()`)
    // An unchanged reset may not trigger a read; Refresh always requests the current source.
    await evaluate('document.querySelector(".refresh-actions button").click()')
    await until(() => bodyHas('1–20 trên 28 bản ghi'), 'Week 3 global history reset')
  }
  await resetWeek3()
  await fill('[aria-label="Linux Username"]', 'WEEK3-USER')
  await until(() => bodyHas('1–2 trên 2 bản ghi'), 'partial case-insensitive username across endpoints')
  assert.deepEqual(await evaluate('Array.from(document.querySelectorAll("tbody tr"), row => row.children[2].textContent)'), ['qa-week3-user-b', 'qa-week3-user-a'])
  assert.ok(requests.some(url => new URL(url).searchParams.get('username') === 'WEEK3-USER'))
  await screenshot('events-username-substring.png')
  await fill('[aria-label="Linux Username"]', 'UnKn')
  await until(() => bodyHas('1–1 trên 1 bản ghi'), 'partial UNKNOWN attribution')
  assert.equal(await evaluate('document.querySelector("tbody tr td:nth-child(3)").textContent'), 'UNKNOWN')
  assert.equal(await evaluate(`!!document.querySelector('time[datetime="2026-10-17T10:00:00.123456Z"]')`), true)
  assert.equal(await evaluate('document.querySelector("tbody").innerText.includes("Week 3 HTTP no-serial storage")'), true)
  const noSerialDeviceId = await evaluate(`document.querySelector('.device-event-id').textContent.match(/[0-9]+/)[0]`)
  await screenshot('events-week3-unknown-no-serial.png')
  await fill('[aria-label="Linux Username"]', 'qa-week3-user-a')
  await until(async () => await bodyHas('1–1 trên 1 bản ghi') && await evaluate('document.querySelector("tbody").innerText.includes("qa-week3-user-a")'), 'synthetic user switch retains historical attribution')
  await resetWeek3()
  await fill('[aria-label="Endpoint"]', 'http-qa-b')
  await until(() => bodyHas('1–1 trên 1 bản ghi'), 'endpoint B isolated history by hostname')
  assert.equal(await evaluate('document.querySelector("tbody tr td:nth-child(3)").textContent'), 'qa-week3-user-b')
  assert.equal(await evaluate('document.querySelector("tbody tr td:nth-child(2) a").getAttribute("href")'), `#/endpoints/${encodeURIComponent(endpointB.endpointId)}`)
  await screenshot('events-week3-endpoint-b.png')
  await fill('[aria-label="Linux Username"]', 'qa-week3-user-a')
  await until(() => bodyHas('Không tìm thấy kết quả'), 'endpoint B does not expose endpoint A user history')
  assert.equal(await evaluate('document.querySelectorAll("tbody tr").length'), 0)
  await resetWeek3()
  await fill('[aria-label="Device"]', noSerialDeviceId)
  await until(() => bodyHas('1–3 trên 3 bản ghi'), 'shared no-serial hash retains three events across endpoints')
  assert.deepEqual(await evaluate('Array.from(document.querySelectorAll("tbody tr"), row => row.children[2].textContent)'), ['qa-week3-user-b', 'qa-week3-user-a', 'UNKNOWN'])
  await fill('[aria-label="Endpoint"]', endpoint.endpointId)
  await until(() => bodyHas('1–2 trên 2 bản ghi'), 'endpoint A isolated history for shared no-serial device')
  assert.deepEqual(await evaluate('Array.from(document.querySelectorAll("tbody tr"), row => row.children[2].textContent)'), ['qa-week3-user-a', 'UNKNOWN'])
  await screenshot('events-week3-endpoint-a.png')
  console.log('PASS: Week 3 HTTP synthetic no-serial storage, UNKNOWN/source timestamp, user-switch history and endpoint A/B isolation (28 persisted events).')
  console.log('PASS: Endpoint/USB searches accept visible names and IDs with server-side filtering/global pagination; Linux User accepts a case-insensitive part of the username.')
  await evaluate(`document.querySelector('[aria-label="Đăng xuất"]').click()`)
  await until(() => evaluate('!!document.querySelector(".login-page")'), 'real logout')
  await evaluate(`location.hash = '/events'`)
  await until(() => evaluate('location.hash === "#/login" && !!document.querySelector("#password")'), 'logout invalidates browser session')
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
  for (const child of children.reverse()) {
    if (child.exitCode !== null || child.signalCode !== null || !child.pid) continue
    if (process.platform === 'win32') {
      // Windows Java/Chrome launchers may spawn additional processes.
      await new Promise(resolveKill => {
        execFile('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, timeout: 15000 }, () => resolveKill())
      })
    } else child.kill()
  }
  await delay(1000)
  const runtimeRelative = relative(artifacts, resolve(runtime))
  assert.ok(runtimeRelative.startsWith('live-events-') && !runtimeRelative.includes(sep))
  await rm(runtime, { recursive: true, force: true, maxRetries: 10, retryDelay: 300 })
}
