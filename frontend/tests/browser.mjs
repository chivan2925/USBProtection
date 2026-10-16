// Browser smoke checks using Node's WebSocket and the locally installed Chromium.
// No browser automation dependency or browser download is required.
import assert from 'node:assert/strict'
import { startAdminFixture } from './fixtures/adminServer.mjs'
import { startLiveServer, checkLiveBrowser } from './liveServer.mjs'
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const artifacts = join(root, '.artifacts')
await mkdir(artifacts, { recursive: true })
const executable = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(path => path && existsSync(path))
assert.ok(executable, 'Set CHROME_PATH to a locally installed Chromium browser.')
assert.ok(existsSync(join(root, 'dist/index.html')), 'Run npm run build first.')
const live = process.argv.includes('--live')
const fixture = live ? await startLiveServer(artifacts) : await startAdminFixture()
const profile = await mkdtemp(join(artifacts, 'chrome-'))
const server = spawn(process.execPath, [join(root, 'node_modules/vite/bin/vite.js'), 'preview', '--host', '127.0.0.1', '--port', '4173', '--strictPort'], { cwd: root, env: { ...process.env, API_PROXY_TARGET: fixture.origin }, windowsHide: true, stdio: 'pipe' })
let browser
let socket
let output = ''
server.stdout.on('data', data => { output += data })
server.stderr.on('data', data => { output += data })
const delay = milliseconds => new Promise(resolveDelay => setTimeout(resolveDelay, milliseconds))
async function until(check, label) {
  for (let attempt = 0; attempt < 100; attempt++) {
    try { if (await check()) return } catch { /* Wait for process or React commit. */ }
    await delay(100)
  }
  throw new Error(`Timed out: ${label}\n${output}`)
}
try {
  await until(() => output.includes('http://127.0.0.1:4173'), 'preview server startup')
  browser = spawn(executable, ['--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-extensions', '--disable-background-networking', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' })
  const portFile = join(profile, 'DevToolsActivePort')
  await until(() => existsSync(portFile), 'Chromium startup')
  const port = (await readFile(portFile, 'utf8')).split('\n')[0].trim()
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
  const target = targets.find(item => item.type === 'page')
  socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolveOpen, reject) => { socket.addEventListener('open', resolveOpen, { once: true }); socket.addEventListener('error', reject, { once: true }) })
  let sequence = 0
  const pending = new Map()
  const errors = []
  const requests = []
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text)
    if (message.method === 'Runtime.consoleAPICalled' && message.params.type === 'error') errors.push(JSON.stringify(message.params.args))
    if (message.method === 'Network.requestWillBeSent' && ['Fetch', 'XHR'].includes(message.params.type)) requests.push(message.params.request.url)
    if (pending.has(message.id)) {
      const { resolveCall, reject, timer } = pending.get(message.id)
      clearTimeout(timer)
      pending.delete(message.id)
      if (message.error) reject(new Error(JSON.stringify(message.error)))
      else resolveCall(message.result)
    }
  })
  const cdp = (method, params = {}) => new Promise((resolveCall, reject) => {
    const id = ++sequence
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)) }, 10000)
    pending.set(id, { resolveCall, reject, timer })
    socket.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async expression => {
    const result = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails))
    return result.result.value
  }
  await cdp('Runtime.enable')
  await cdp('Network.enable')
  await cdp('Page.enable')
  await cdp('Emulation.setTimezoneOverride', { timezoneId: 'Asia/Bangkok' })
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })

  const screenshot = async name => {
    const shot = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    await writeFile(join(artifacts, name), Buffer.from(shot.data, 'base64'))
  }
  const navigate = async (route, heading) => {
    await evaluate(`location.hash = ${JSON.stringify(route)}`)
    await until(() => evaluate(`document.querySelector('h1')?.textContent === ${JSON.stringify(heading)}`), route)
  }
  const fillInput = async (selector, value) => {
    await evaluate(`{ const input = document.querySelector(${JSON.stringify(selector)}); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(value)}); input.dispatchEvent(new Event('input', { bubbles: true })); }`)
  }
  if (live) {
    try { await checkLiveBrowser(fixture, { cdp, evaluate, until, screenshot, navigate, fillInput }) }
    catch (error) { console.error(await evaluate('document.body.innerText')); await screenshot('m4-live-failure.png'); throw error }
  } else {
  async function login(password = 'fixture-only') {
    await fillInput('#username', 'fixture-admin')
    await fillInput('#password', password)
    await evaluate(`document.querySelector('button[type="submit"]').click()`)
  }
  await cdp('Page.navigate', { url: 'http://127.0.0.1:4173/#/endpoints/A' })
  await until(() => evaluate(`location.hash === '#/login' && !!document.querySelector('#password')`), 'protected route redirects')
  assert.equal(fixture.state.requests.some(item => item.path.startsWith('/api/admin/')), false, 'No Admin reads before /me succeeds')
  await login('wrong-fixture-password')
  await until(() => evaluate(`document.querySelector('[role="alert"]')?.textContent.includes('không đúng')`), 'invalid login')
  assert.equal(fixture.state.sessions.size, 0)
  assert.equal(await evaluate(`document.querySelector('#password').value`), '')
  await screenshot('m4-login.png')
  await login()
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '2'`), 'authenticated Dashboard')
  assert.deepEqual(await evaluate(`[...document.querySelectorAll('.stat-value')].map(el => el.textContent)`), ['2', '1', '1', '2'])
  assert.equal(await evaluate(`document.querySelector('.sidebar-user').innerText.includes('fixture-admin')`), true)
  assert.equal(await evaluate(`document.body.innerText.includes('Hệ thống đang được bảo vệ')`), false, 'No invented USBGuard health')
  await screenshot('m4-dashboard.png')
  await cdp('Page.reload')
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '2'`), 'session survives reload via /me')
  const beforeB = structuredClone(fixture.state.endpoints[1])
  const beforeRulesB = structuredClone(fixture.state.whitelist.B)
  await navigate('/endpoints/A', 'fixture-ubuntu-a')
  await until(() => evaluate(`!!document.querySelector('[aria-label="Allow on this endpoint"]')`), 'endpoint A data')
  assert.equal(await evaluate(`document.body.innerText.includes('fixture-linux-user')`), true)
  assert.equal(await evaluate(`document.body.innerText.includes('Last Applied Version') && document.body.innerText.includes('Chờ Agent áp dụng')`), true)

  async function openAllow() {
    await evaluate(`document.querySelector('[aria-label="Allow on this endpoint"]').click()`)
    await until(() => evaluate(`!!document.querySelector('#usb-device-id')`), 'allow form')
    await evaluate(`{ const select = document.querySelector('#usb-device-id'); select.value = '1'; select.dispatchEvent(new Event('change', { bubbles: true })); }`)
    await evaluate(`document.querySelector('.policy-form button[type="submit"]').click()`)
    await until(() => evaluate(`!!document.querySelector('dialog[open]')`), 'allow confirmation')
    await evaluate(`document.querySelector('dialog .primary').click()`)
  }
  await openAllow('fixture-hash-a')
  await until(() => evaluate(`document.querySelector('[data-testid="endpoint-whitelist-panel"]')?.innerText.includes('fixture-hash-a')`), 'Allow refetch whitelist')
  assert.equal(fixture.state.endpoints[0].policyVersion, 2)
  assert.equal(fixture.state.endpoints[0].appliedPolicyVersion, 0)
  assert.deepEqual(fixture.state.endpoints[1], beforeB)
  assert.deepEqual(fixture.state.whitelist.B, beforeRulesB)
  await screenshot('m4-allow-a.png')
  await openAllow('fixture-hash-a')
  await until(() => evaluate(`!document.querySelector('dialog[open]') && !document.querySelector('.loading-state')`), 'duplicate Allow succeeds without version bump')
  assert.equal(fixture.state.endpoints[0].policyVersion, 2)
  assert.equal(fixture.state.whitelist.A.length, 1)
  await evaluate(`document.querySelector('[aria-label^="Revoke on this endpoint"]').click()`)
  await until(() => evaluate(`!!document.querySelector('dialog[open]')`), 'revoke confirmation')
  await evaluate(`document.querySelector('dialog .primary').click()`)
  await until(() => evaluate(`document.querySelector('[data-testid="endpoint-whitelist-panel"]')?.innerText.includes('Chưa có thiết bị nào')`), 'Revoke refetch')
  assert.equal(fixture.state.endpoints[0].policyVersion, 3)
  assert.equal(fixture.state.endpoints[0].appliedPolicyVersion, 0)
  assert.deepEqual(fixture.state.endpoints[1], beforeB)
  assert.deepEqual(fixture.state.whitelist.B, beforeRulesB)

  fixture.state.failMutation = 500
  await openAllow('fixture-failed-hash')
  await until(() => evaluate(`document.querySelector('dialog [role="alert"]')?.textContent.includes('Máy chủ gặp lỗi')`), 'mutation error visible')
  assert.equal(fixture.state.whitelist.A.length, 0)
  await screenshot('m4-mutation-error.png')
  await evaluate(`document.querySelector('dialog .button').click()`)
  fixture.state.failMutation = 0
  await navigate('/endpoints/B', 'fixture-ubuntu-b')
  await until(() => evaluate(`document.querySelector('[data-testid="endpoint-whitelist-panel"]')?.innerText.includes('fixture-hash-a')`), 'B rule remains')
  await navigate('/events', 'Lịch sử sự kiện')
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 2`), 'M5 receives all API event pages')
  assert.deepEqual(await evaluate(`Array.from(document.querySelectorAll('thead th'), th => th.textContent)`), ['Timestamp', 'Endpoint', 'Linux User', 'USB', 'Event', 'Decision'])
  await evaluate(`{ const select = document.querySelector('[aria-label="Decision"]'); select.value = 'blocked'; select.dispatchEvent(new Event('change', { bubbles: true })); }`)
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 1`), 'live event filter')
  assert.equal(await evaluate(`document.querySelector('tbody').innerText.includes('fixture-linux-user')`), true)
  await evaluate(`document.querySelector('[aria-label="Đặt lại tất cả bộ lọc"]').click()`)
  await screenshot('m4-events.png')

  for (const status of [403, 500]) {
    fixture.state.failure = status
    await evaluate(`document.querySelector('.refresh-actions button').click()`)
    await until(() => evaluate(`document.querySelector('.error-notice')?.innerText.includes(${JSON.stringify(status === 403 ? 'Không có quyền' : 'Máy chủ gặp lỗi')})`), `read error ${status}`)
    assert.equal(await evaluate(`document.querySelectorAll('tbody tr').length`), 0, 'No stale or mock rows')
    fixture.state.failure = 0
    await evaluate(`document.querySelector('.error-notice button').click()`)
    await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 2`), 'retry recovery')
  }
  fixture.state.empty = true
  await evaluate(`document.querySelector('.refresh-actions button').click()`)
  await until(() => evaluate(`document.body.innerText.includes('Chưa có dữ liệu')`), 'empty API remains empty')
  assert.equal(await evaluate(`document.querySelectorAll('tbody tr').length`), 0)
  assert.equal(await evaluate(`document.body.innerText.includes('student01') || document.body.innerText.includes('Kingston')`), false)
  await screenshot('m4-empty.png')
  fixture.state.empty = false

  await cdp('Emulation.setDeviceMetricsOverride', { width: 375, height: 812, deviceScaleFactor: 1, mobile: true })
  for (const [route, heading] of [['/dashboard', 'Tổng quan bảo vệ'], ['/devices', 'Quản lý thiết bị USB'], ['/whitelist', 'Whitelist & Policy'], ['/blocked', 'Thiết bị bị chặn'], ['/events', 'Lịch sử sự kiện'], ['/endpoints', 'Quản lý endpoints'], ['/endpoints/A', 'fixture-ubuntu-a'], ['/settings', 'Cài đặt hệ thống'], ['/missing', 'Không tìm thấy trang'], ['/endpoints/%ZZ', 'Không tìm thấy trang']]) {
    await navigate(route, heading)
    await until(() => evaluate(`!document.querySelector('.loading-state')`), 'route read finished')
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, `Mobile overflow: ${route}`)
  }
  await navigate('/dashboard', 'Tổng quan bảo vệ')
  await evaluate(`document.querySelector('.mobile-toggle').click()`)
  await until(() => evaluate(`document.querySelector('.sidebar').classList.contains('is-open')`), 'mobile menu')
  await evaluate(`document.querySelector('.sidebar a[href="#/devices"]').click()`)
  await until(() => evaluate(`!document.querySelector('.sidebar').classList.contains('is-open') && document.querySelector('h1').textContent === 'Quản lý thiết bị USB'`), 'mobile navigation')
  await screenshot('m4-mobile.png')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })
  fixture.state.sessions.clear()
  await navigate('/dashboard', 'Tổng quan bảo vệ')
  await until(() => evaluate(`!!document.querySelector('#password') && location.hash === '#/login'`), 'expired session redirects')
  await login()
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '2'`), 'login again')
  await evaluate(`document.querySelector('[aria-label="Đăng xuất"]').click()`)
  await until(() => evaluate(`location.hash === '#/login' && !!document.querySelector('#password')`), 'logout')
  assert.equal(fixture.state.sessions.size, 0)
  assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0)
  const beforeDemoRequests = fixture.state.requests.length
  await cdp('Page.navigate', { url: 'http://127.0.0.1:4173/?demo=1#/dashboard' })
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '2'`), 'demo snapshot load')
  assert.equal(await evaluate(`document.body.innerText.includes('Chế độ demo · Dữ liệu mock')`), true)
  assert.deepEqual(await evaluate(`[...document.querySelectorAll('.stat-value')].map(el => el.textContent)`), ['2', '1', '1', '6'])
  await screenshot('demo-dashboard-desktop.png')
  await navigate('/endpoints', 'Quản lý endpoints')
  assert.deepEqual(await evaluate(`Array.from(document.querySelectorAll('thead th'), th => th.textContent)`), ['Máy tính', 'Endpoint ID', 'Hệ điều hành', 'Trạng thái', 'Người dùng', 'Policy version', 'Last Applied Version', 'Kết nối gần nhất', 'Chi tiết'])
  assert.deepEqual(await evaluate(`Array.from(document.querySelectorAll('tbody tr'), row => [row.cells[1].textContent, row.cells[5].textContent, row.cells[6].textContent])`), [['POC-LAB-PC-01', '2', '2'], ['POC-LAB-PC-02', '1', '1']])
  assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, 'Demo desktop endpoint list overflow')
  await screenshot('demo-endpoints-list-desktop.png')
  await evaluate(`document.querySelector('a[href="#/endpoints/POC-LAB-PC-01"]').click()`)
  await until(() => evaluate(`document.querySelector('h1')?.textContent === 'LAB-PC-01'`), 'demo endpoint link')
  assert.equal(await evaluate(`document.querySelector('[data-testid="endpoint-whitelist-panel"]').innerText.includes('Kingston')`), true)
  assert.equal(await evaluate(`document.querySelector('[data-testid="endpoint-whitelist-panel"] [aria-label^="Revoke"]').disabled`), true)
  assert.equal(await evaluate(`document.querySelector('[aria-label="Allow on this endpoint"]').disabled`), true)
  await screenshot('demo-endpoint-desktop.png')
  await navigate('/endpoints/POC-LAB-PC-02', 'LAB-PC-02')
  assert.equal(await evaluate(`document.querySelector('[data-testid="endpoint-whitelist-panel"]').innerText.includes('Chưa có thiết bị nào được cho phép')`), true)
  assert.equal(await evaluate(`!!document.querySelector('[data-testid="endpoint-whitelist-panel"] [aria-label^="Revoke"]')`), false)
  await navigate('/events', 'Lịch sử sự kiện')
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 6`), 'explicit demo events')
  assert.equal(await evaluate(`document.querySelector('tbody').innerText.includes('student01') && document.querySelector('tbody').innerText.includes('employee07')`), true)
  assert.equal(await evaluate(`document.body.innerText.includes('Dữ liệu mock · Demo')`), true)
  await evaluate(`{ const select = document.querySelector('[aria-label="Decision"]'); select.value = 'blocked'; select.dispatchEvent(new Event('change', { bubbles: true })); }`)
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 2`), 'demo decision filter')
  assert.equal(await evaluate(`Array.from(document.querySelectorAll('tbody tr'), row => row.lastElementChild.textContent).every(text => text === 'BLOCK')`), true)
  await evaluate(`{ const select = document.querySelector('[aria-label="Event Type"]'); select.value = 'connected'; select.dispatchEvent(new Event('change', { bubbles: true })); }`)
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 1`), 'combined demo type and decision filters')
  await evaluate(`const input = document.querySelector('[aria-label="Endpoint"]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, 'no-such-endpoint'); input.dispatchEvent(new Event('input', { bubbles: true }))`)
  await until(() => evaluate(`document.body.innerText.includes('Không tìm thấy kết quả')`), 'demo empty filter')
  const resetEvents = async () => {
    await evaluate(`document.querySelector('[aria-label="Đặt lại tất cả bộ lọc"]').click()`)
    await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 6`), 'reset demo filters')
  }
  await resetEvents()
  for (const label of ['From Date', 'To Date']) {
    await evaluate(`{ const input = document.querySelector('[aria-label="${label}"]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, '2000-01-01'); input.dispatchEvent(new Event('input', { bubbles: true })); }`)
    await until(() => evaluate(`document.querySelector('[aria-label="${label}"]').value === '2000-01-01'`), `demo ${label}`)
  }
  await until(() => evaluate(`document.body.innerText.includes('Không tìm thấy kết quả')`), 'demo date range excludes events')
  await resetEvents()
  for (const label of ['From Date', 'To Date']) {
    await evaluate(`{ const input = document.querySelector('[aria-label="${label}"]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, '2026-10-05'); input.dispatchEvent(new Event('input', { bubbles: true })); }`)
    await until(() => evaluate(`document.querySelector('[aria-label="${label}"]').value === '2026-10-05'`), `inclusive demo ${label}`)
  }
  assert.equal(await evaluate(`document.querySelectorAll('tbody tr').length`), 6)
  await resetEvents()
  await screenshot('demo-events-desktop.png')
  await evaluate(`document.querySelector('a[href="#/endpoints/POC-LAB-PC-01"]').click()`)
  await until(() => evaluate(`document.querySelector('h1')?.textContent === 'LAB-PC-01'`), 'demo event detail link')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 375, height: 812, deviceScaleFactor: 1, mobile: true })
  for (const [route, heading] of [['/dashboard', 'Tổng quan bảo vệ'], ['/events', 'Lịch sử sự kiện'], ['/endpoints', 'Quản lý endpoints'], ['/endpoints/POC-LAB-PC-01', 'LAB-PC-01'], ['/endpoints/POC-LAB-PC-02', 'LAB-PC-02']]) {
    await navigate(route, heading)
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, `Demo mobile overflow: ${route}`)
  }
  await navigate('/events', 'Lịch sử sự kiện')
  await screenshot('demo-events-mobile.png')
  await navigate('/endpoints/POC-LAB-PC-02', 'LAB-PC-02')
  await screenshot('demo-endpoint-mobile.png')

  assert.equal(fixture.state.requests.length, beforeDemoRequests, 'Demo issues no HTTP or mutation requests')
  await evaluate(`Array.from(document.querySelectorAll('a')).find(link => link.textContent === 'Thoát demo').click()`)
  await until(() => evaluate(`!location.search && !!document.querySelector('#password')`), 'exit demo requires Admin login')
  await login()
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '2'`), 'session before network failure')
  await cdp('Network.emulateNetworkConditions', { offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0 })
  await evaluate(`document.querySelector('.refresh-actions button').click()`)
  await until(() => evaluate(`document.querySelector('.error-notice')?.innerText.includes('Không kết nối')`), 'network error has retry and no demo fallback')
  await screenshot('m4-network.png')
  assert.equal(await evaluate(`document.body.innerText.includes('student01') || document.body.innerText.includes('Kingston')`), false)
  assert.deepEqual(errors, [], 'No browser runtime or application console errors')
  assert.ok(requests.some(url => url.includes('/api/auth/login')))
  assert.ok(requests.every(url => url.startsWith('http://127.0.0.1:4173/api/')), 'Same-origin API requests')
  console.log('PASS: M4 browser with HTTP fixture; protected routes, invalid/valid login, refresh /me, CSRF, A/B Allow/Revoke, duplicate/error/empty states, M5 events/filter, expired session/logout, demo read-only, network error, responsive routes.')
  console.log('Scope: HTTP fixture regression. Run npm run test:live for Spring Security/H2 evidence. Physical USB and actual Agent apply/ACK require Ubuntu.')
  console.log('Screenshots: frontend/.artifacts/m4-*.png; demo regression screenshots.')
  }
  assert.deepEqual(errors, [], 'No browser runtime or application console errors')
  await cdp('Browser.close')
} finally {
  socket?.close()
  browser?.kill()
  server.kill()
  await fixture.close()
  await delay(500)
  // Only remove the temporary browser profile created under this workspace.
  const profileRelative = relative(artifacts, resolve(profile))
  assert.ok(profileRelative.startsWith('chrome-') && !profileRelative.includes(sep))
  await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 })
}
