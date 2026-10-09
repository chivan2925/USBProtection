// Browser smoke checks using Node's WebSocket and the locally installed Chromium.
// No browser automation dependency or browser download is required.
import assert from 'node:assert/strict'
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
const profile = await mkdtemp(join(artifacts, 'chrome-'))
const server = spawn(process.execPath, [join(root, 'node_modules/vite/bin/vite.js'), 'preview', '--host', '127.0.0.1', '--port', '4173', '--strictPort'], { cwd: root, windowsHide: true, stdio: 'pipe' })
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
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })
  await cdp('Page.navigate', { url: 'http://127.0.0.1:4173/#/dashboard' })
  await until(() => evaluate(`document.body.innerText.includes('Đang chờ kết nối backend')`), 'dashboard load')
  assert.deepEqual(await evaluate(`[...document.querySelectorAll('.stat-value')].map(el => el.textContent)`), ['—', '—', '—', '—'])
  assert.equal(await evaluate(`document.querySelector('[aria-current="page"]').textContent`), 'Dashboard')
  await evaluate(`document.querySelector('.refresh-actions button').click()`)
  await until(() => evaluate(`document.querySelector('.toast')?.textContent.includes('chưa cung cấp API')`), 'unavailable refresh feedback')
  await evaluate(`document.querySelector('.toast button').click()`)
  const screenshot = async name => {
    const shot = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    await writeFile(join(artifacts, name), Buffer.from(shot.data, 'base64'))
  }
  await screenshot('dashboard-desktop.png')
  const navigate = async (route, heading) => {
    await evaluate(`location.hash = ${JSON.stringify(route)}`)
    await until(() => evaluate(`document.querySelector('h1')?.textContent === ${JSON.stringify(heading)}`), route)
  }
  for (const [route, heading] of [['/devices', 'Quản lý thiết bị USB'], ['/whitelist', 'Whitelist & Policy'], ['/blocked', 'Thiết bị bị chặn'], ['/events', 'Lịch sử sự kiện'], ['/endpoints', 'Quản lý endpoints'], ['/endpoints/missing', 'Chi tiết endpoint'], ['/settings', 'Cài đặt hệ thống']]) {
    await navigate(route, heading)
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, `Desktop overflow: ${route}`)
  }
  await navigate('/events', 'Lịch sử sự kiện')
  assert.deepEqual(await evaluate(`Array.from(document.querySelectorAll('thead th'), th => th.textContent)`), ['Timestamp', 'Endpoint', 'Linux User', 'USB', 'Event', 'Decision'])
  assert.equal(await evaluate(`document.querySelector('tbody').innerText.includes('student01') && document.querySelector('tbody').innerText.includes('employee07')`), true)
  await screenshot('events-desktop.png')
  await evaluate(`const select = document.querySelector('[aria-label="Decision"]'); select.value = 'blocked'; select.dispatchEvent(new Event('change', { bubbles: true }))`)
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 2`), 'event decision filter')
  assert.equal(await evaluate(`Array.from(document.querySelectorAll('tbody tr'), row => row.lastElementChild.textContent).every(text => text === 'BLOCK')`), true)
  await evaluate(`const input = document.querySelector('[aria-label="Endpoint"]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, 'no-such-endpoint'); input.dispatchEvent(new Event('input', { bubbles: true }))`)
  await until(() => evaluate(`document.body.innerText.includes('Không tìm thấy kết quả')`), 'event empty filter')
  await evaluate(`Array.from(document.querySelectorAll('button')).find(button => button.getAttribute('aria-label') === 'Đặt lại tất cả bộ lọc').click()`)
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 6`), 'event reset filters')
  await navigate('/devices', 'Quản lý thiết bị USB')
  await evaluate(`document.querySelector('select').value = 'blocked'; document.querySelector('select').dispatchEvent(new Event('change', { bubbles: true }))`)
  await until(() => evaluate(`document.body.innerText.includes('Đặt lại bộ lọc')`), 'status filter')
  await evaluate(`document.querySelector('.filters .text-button').click()`)
  await until(() => evaluate(`document.querySelector('select').value === 'all'`), 'reset filter')
  await navigate('/missing', 'Không tìm thấy trang')
  await navigate('/endpoints/%ZZ', 'Không tìm thấy trang')
  await evaluate(`location.hash = '/login'`)
  await until(() => evaluate(`!!document.querySelector('.login-page')`), 'login')
  assert.equal(await evaluate(`document.querySelector('button[type="submit"]').disabled && document.querySelector('#password').disabled`), true)
  assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0)
  await screenshot('login-desktop.png')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 375, height: 812, deviceScaleFactor: 1, mobile: true })
  for (const [route, heading] of [['/dashboard', 'Tổng quan bảo vệ'], ['/devices', 'Quản lý thiết bị USB'], ['/whitelist', 'Whitelist & Policy'], ['/blocked', 'Thiết bị bị chặn'], ['/events', 'Lịch sử sự kiện'], ['/endpoints', 'Quản lý endpoints'], ['/endpoints/missing', 'Chi tiết endpoint'], ['/settings', 'Cài đặt hệ thống']]) {
    await navigate(route, heading)
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, `Mobile overflow: ${route}`)
  }
  await navigate('/dashboard', 'Tổng quan bảo vệ')
  await evaluate(`document.querySelector('.mobile-toggle').click()`)
  await until(() => evaluate(`document.querySelector('.sidebar').classList.contains('is-open')`), 'mobile menu')
  await evaluate(`document.querySelector('.sidebar a[href="#/devices"]').click()`)
  await until(() => evaluate(`!document.querySelector('.sidebar').classList.contains('is-open') && document.querySelector('h1').textContent === 'Quản lý thiết bị USB'`), 'mobile navigation')
  await navigate('/dashboard', 'Tổng quan bảo vệ')
  await screenshot('dashboard-mobile.png')
  assert.deepEqual(errors, [], 'No browser runtime or console errors')
  assert.deepEqual(requests, [], 'No invented API requests')
  console.log('PASS: routes, 404, refresh toast, filters, login safety, mobile navigation, responsive overflow and browser errors. Screenshots: .artifacts/')
  await cdp('Browser.close')
} finally {
  socket?.close()
  browser?.kill()
  server.kill()
  await delay(500)
  // Only remove the temporary browser profile created under this workspace.
  const profileRelative = relative(artifacts, resolve(profile))
  assert.ok(profileRelative.startsWith('chrome-') && !profileRelative.includes(sep))
  await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 })
}
