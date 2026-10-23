// Real Spring Boot + file-backed H2. Enrollment/events/ACK requests are synthetic.
import assert from 'node:assert/strict'
import { execFile, spawn } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { existsSync } from 'node:fs'
import { mkdtemp, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { join, resolve } from 'node:path'

export async function startLiveServer(artifacts) {
  const repo = resolve(artifacts, '../..')
  const jar = join(repo, 'admin-server/target/usbshield-admin-server.jar')
  assert.ok(existsSync(jar), 'Build admin-server first: mvn package -DskipTests (Java 21).')
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java'
  const runtime = await mkdtemp(join(artifacts, 'live-'))
  const password = randomUUID()
  const enrollmentToken = randomUUID()
  const reservation = createServer()
  await new Promise(resolveListen => reservation.listen(0, '127.0.0.1', resolveListen))
  const port = reservation.address().port
  await new Promise(resolveClose => reservation.close(resolveClose))
  const origin = `http://127.0.0.1:${port}`
  let child
  let log = ''
  let cookie = ''
  let csrf
  async function start() {
    child = spawn(java, ['-jar', jar, `--server.port=${port}`, '--server.address=127.0.0.1',
      `--spring.datasource.url=jdbc:h2:file:${join(runtime, 'db').replaceAll('\\', '/')};DB_CLOSE_ON_EXIT=FALSE`], {
      cwd: repo, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'],
      env: { ...process.env, USBSHIELD_ADMIN_USERNAME: 'm4-live-admin', USBSHIELD_ADMIN_PASSWORD: password, USBSHIELD_ENROLLMENT_TOKEN: enrollmentToken },
    })
    let launchError
    child.on('error', error => { launchError = error })
    child.stdout.on('data', data => { log += data })
    child.stderr.on('data', data => { log += data })
    for (let attempt = 0; attempt < 180; attempt++) {
      if (launchError) throw launchError
      if (child.exitCode !== null) throw new Error(`Spring Boot exited: ${child.exitCode}`)
      try { if ((await fetch(`${origin}/api/auth/csrf`)).ok) return } catch { /* Startup. */ }
      await new Promise(resolveDelay => setTimeout(resolveDelay, 250))
    }
    throw new Error('Spring Boot readiness timeout.')
  }
  async function stop() {
    if (child?.pid && child.exitCode === null) {
      const stopped = new Promise(resolveExit => child.once('exit', resolveExit))
      // Windows java launchers can spawn a separate JVM. Killing only the
      // launcher leaves that JVM serving requests and holding the H2 file.
      if (process.platform === 'win32') {
        await new Promise((resolveKill, rejectKill) => {
          execFile('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, timeout: 15000 }, error => {
            if (error && child.exitCode === null) rejectKill(error)
            else resolveKill()
          })
        })
      } else child.kill()
      await stopped
    }
  }
  async function http(path, { method = 'GET', body, headers = {}, expected = 200, admin = false } = {}) {
    const response = await fetch(`${origin}${path}`, {
      method, headers: { 'Content-Type': 'application/json', ...(admin && cookie ? { Cookie: cookie } : {}),
        ...(admin && method !== 'GET' && csrf ? { [csrf.headerName]: csrf.token } : {}), ...headers },
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000),
    })
    if (admin && response.headers.get('set-cookie')) cookie = response.headers.get('set-cookie').split(';')[0]
    assert.equal(response.status, expected, `${method} ${path}`)
    return response.status === 204 ? undefined : response.json()
  }
  async function adminLogin() {
    csrf = await http('/api/auth/csrf', { admin: true })
    await http('/api/auth/login', { method: 'POST', admin: true, headers: { [csrf.headerName]: csrf.token }, body: { username: 'm4-live-admin', password } })
    csrf = await http('/api/auth/csrf', { admin: true })
  }
  const close = async () => { await stop(); await writeFile(join(runtime, 'server.log'), log) }
  try {
    await start()
    await http('/api/admin/endpoints', { expected: 401 })
    await http('/api/auth/login', { method: 'POST', body: { username: 'm4-live-admin', password }, expected: 403 })
    await adminLogin()
    assert.deepEqual(await http('/api/admin/endpoints', { admin: true }), [])
    async function enroll(name) {
      return http('/api/agent/enroll', { method: 'POST', body: { hostname: name, machineId: randomUUID(), enrollmentToken } })
    }
    const a = await enroll('m4-live-ubuntu-a')
    const b = await enroll('m4-live-ubuntu-b')
    const agentHeaders = { 'X-Endpoint-Id': a.endpointId, 'X-Agent-Token': a.agentToken }
    await http('/api/agent/policy', { expected: 401 })
    await http('/api/agent/policy', { headers: { ...agentHeaders, 'X-Endpoint-Id': b.endpointId }, expected: 401 })
    const endpoint = id => http(`/api/admin/endpoints/${id}`, { admin: true })
    const rules = id => http(`/api/admin/endpoints/${id}/whitelist`, { admin: true })
    return {
      origin, password, a, b, agentHeaders, http, endpoint, rules, adminLogin,
      async seed() {
        await http('/api/agent/heartbeat', { method: 'POST', headers: agentHeaders, body: { endpointId: a.endpointId, currentPolicyVersion: 999 } })
        assert.equal((await endpoint(a.endpointId)).appliedPolicyVersion, 0)
        await http('/api/agent/events', { method: 'POST', headers: agentHeaders, body: {
          endpointId: a.endpointId, hostname: 'm4-live-ubuntu-a', linuxUsername: 'm4-synthetic-user', linuxUid: 1000,
          sessionId: 'm4-test-session', sessionType: 'wayland', seat: 'seat0',
          device: { name: 'M4 Synthetic USB', vendorId: '0951', productId: '1666', serial: 'm4-test', hash: 'm4-synthetic-hash', interface: '08:06:50' },
          eventType: 'CONNECTED', decision: 'BLOCKED', occurredAt: new Date().toISOString(),
        } })
      },
      async restart() { await stop(); cookie = ''; await start(); await adminLogin() },
      close,
    }
  } catch (error) { await close(); throw error }
}

export async function checkLiveBrowser(backend, { cdp, evaluate, until, screenshot, navigate, fillInput }) {
  async function login(password = backend.password) {
    await fillInput('#username', 'm4-live-admin')
    await fillInput('#password', password)
    await evaluate(`document.querySelector('button[type="submit"]').click()`)
  }
  await cdp('Page.navigate', { url: 'http://127.0.0.1:4173/#/dashboard' })
  await until(() => evaluate(`!!document.querySelector('#password')`), 'real protected route')
  await login('wrong-test-password')
  await until(() => evaluate(`!!document.querySelector('[role="alert"]')`), 'real wrong password 401')
  await screenshot('m4-live-login.png')
  await login()
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '0'`), 'real empty H2 Dashboard')
  await screenshot('m4-live-empty.png')
  await backend.seed()
  await evaluate(`document.querySelector('.refresh-actions button').click()`)
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '1' && document.querySelector('.status-ring strong')?.textContent === '2'`), 'real H2 endpoints and USB')
  await screenshot('m4-live-dashboard.png')
  await cdp('Page.reload')
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '1'`), 'real session reload and rotated CSRF')
  const beforeA = await backend.endpoint(backend.a.endpointId)
  const beforeB = await backend.endpoint(backend.b.endpointId)
  const beforeRulesB = await backend.rules(backend.b.endpointId)
  await navigate(`/endpoints/${backend.a.endpointId}`, 'm4-live-ubuntu-a')
  await until(() => evaluate(`!!document.querySelector('[aria-label="Allow on this endpoint"]')`), 'real endpoint A')
  async function allow() {
    await evaluate(`document.querySelector('[aria-label="Allow on this endpoint"]').click()`)
    await until(() => evaluate(`!!document.querySelector('#usb-device-id option[value]:not([value=""])')`), 'server USB choice')
    await evaluate(`{ const select = document.querySelector('#usb-device-id'); select.value = select.options[1].value; select.dispatchEvent(new Event('change', { bubbles: true })); }`)
    await evaluate(`document.querySelector('.policy-form button[type="submit"]').click()`)
    await until(() => evaluate(`!!document.querySelector('dialog[open]')`), 'real Allow confirmation')
    await evaluate(`document.querySelector('dialog .primary').click()`)
    await until(() => evaluate(`!document.querySelector('dialog[open]') && !document.querySelector('.loading-state')`), 'real mutation/refetch')
  }
  await allow()
  const allowedA = await backend.endpoint(backend.a.endpointId)
  assert.equal(allowedA.policyVersion, beforeA.policyVersion + 1)
  assert.equal(allowedA.appliedPolicyVersion, 0, 'Admin success is not Agent ACK')
  const [entry] = await backend.rules(backend.a.endpointId)
  assert.equal(entry.fingerprintValue, 'm4-synthetic-hash')
  await backend.http(`/api/admin/endpoints/${backend.a.endpointId}/whitelist`, {
    method: 'POST', admin: true, headers: { 'X-CSRF-TOKEN': 'invalid-test-token' }, body: { usbDeviceId: entry.usbDeviceId }, expected: 403,
  })
  await until(() => evaluate(`document.querySelector('[data-testid="endpoint-whitelist-panel"]').innerText.includes('m4-synthetic-hash')`), 'real whitelist without createdAt')
  await screenshot('m4-live-allow-a.png')
  await allow()
  assert.equal((await backend.endpoint(backend.a.endpointId)).policyVersion, allowedA.policyVersion)
  const policy = await backend.http('/api/agent/policy', { headers: backend.agentHeaders })
  assert.equal(policy.whitelistedDevices[0].deviceHash, 'm4-synthetic-hash')
  // ponytail: controlled HTTP ACK covers server/UI; add real apply/ACK on Ubuntu with USBGuard hardware.
  await backend.http('/api/agent/policy/ack', { method: 'POST', headers: backend.agentHeaders, body: { appliedPolicyVersion: allowedA.policyVersion + 1 }, expected: 409 })
  await backend.http('/api/agent/policy/ack', { method: 'POST', headers: backend.agentHeaders, body: { appliedPolicyVersion: allowedA.policyVersion } })
  await evaluate(`document.querySelector('.refresh-actions button').click()`)
  await until(() => evaluate(`document.body.innerText.includes('Agent đã ACK phiên bản này')`), 'controlled HTTP ACK visible')
  await screenshot('m4-live-http-ack.png')
  await backend.http(`/api/admin/endpoints/${backend.b.endpointId}/whitelist/${entry.id}`, { method: 'DELETE', admin: true, expected: 404 })
  await evaluate(`document.querySelector('[aria-label^="Revoke on this endpoint"]').click()`)
  await until(() => evaluate(`!!document.querySelector('dialog[open]')`), 'real Revoke confirmation')
  await evaluate(`document.querySelector('dialog .primary').click()`)
  await until(() => evaluate(`document.querySelector('[data-testid="endpoint-whitelist-panel"]')?.innerText.includes('Chưa có thiết bị nào')`), 'real Revoke refetch')
  const revokedA = await backend.endpoint(backend.a.endpointId)
  assert.equal(revokedA.policyVersion, allowedA.policyVersion + 1)
  assert.equal(revokedA.appliedPolicyVersion, allowedA.policyVersion)
  assert.deepEqual(await backend.endpoint(backend.b.endpointId), beforeB)
  assert.deepEqual(await backend.rules(backend.b.endpointId), beforeRulesB)
  assert.deepEqual((await backend.http('/api/agent/policy', { headers: backend.agentHeaders })).whitelistedDevices, [])
  await screenshot('m4-live-revoke-a.png')
  await navigate('/events', 'Lịch sử sự kiện')
  await until(() => evaluate(`document.querySelector('tbody')?.innerText.includes('m4-synthetic-user')`), 'M5 event from real H2')
  await screenshot('m4-live-events.png')
  await cdp('Emulation.setDeviceMetricsOverride', { width: 375, height: 812, deviceScaleFactor: 1, mobile: true })
  assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true)
  await screenshot('m4-live-mobile.png')
  await backend.restart()
  const persistedA = await backend.endpoint(backend.a.endpointId)
  assert.equal(persistedA.policyVersion, revokedA.policyVersion)
  assert.equal(persistedA.appliedPolicyVersion, revokedA.appliedPolicyVersion)
  assert.deepEqual(await backend.rules(backend.a.endpointId), [])
  assert.equal((await backend.http('/api/admin/events', { admin: true })).totalElements, 1)
  assert.equal(await evaluate(`fetch('/api/auth/me').then(response => response.status)`), 401, 'Restart must invalidate the browser session')
  await evaluate(`document.querySelector('.refresh-actions button').click()`)
  await until(() => evaluate(`!!document.querySelector('#password')`), 'session invalid after server restart')
  assert.equal(await evaluate(`document.querySelectorAll('tbody tr').length`), 0, 'Expired session clears protected event rows')
  await screenshot('m4-live-expired-session.png')
  await login()
  await until(() => evaluate(`document.querySelector('.stat-value')?.textContent === '1'`), 'login after H2 restart')
  await evaluate(`document.querySelector('[aria-label="Đăng xuất"]').click()`)
  await until(() => evaluate(`!!document.querySelector('#password')`), 'real logout')
  assert.equal(await evaluate(`fetch('/api/admin/endpoints').then(response => response.status)`), 401)
  assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0)
  console.log('PASS: real Spring Boot/Spring Security + file-backed H2; browser invalid/valid login, empty data, session reload, endpoint/event mapping, A Allow/idempotency/Revoke, B isolation, CSRF rejection, policy fetch, controlled HTTP ACK display, persistence after restart, expired session, logout, mobile.')
  console.log(JSON.stringify({ endpointA: backend.a.endpointId, endpointB: backend.b.endpointId, beforeA, allowedA, revokedA, persistedDesired: persistedA.policyVersion, persistedApplied: persistedA.appliedPolicyVersion, eventCount: 1 }))
  console.log('NOT EXECUTED: actual M3 Agent apply/ACK, Ubuntu USBGuard enforcement, physical USB, native notification. Events and HTTP ACK above are synthetic inputs to real backend.')
}
