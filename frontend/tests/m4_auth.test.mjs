import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { protectionApi } from '../src/services/api.ts'
import { ApiError, clearCsrfToken, request } from '../src/services/http.ts'
import { startAdminFixture, useFixtureFetch } from './fixtures/adminServer.mjs'

let fixture
let restore
before(async () => { fixture = await startAdminFixture(); restore = useFixtureFetch(fixture.origin) })
after(async () => { restore?.(); await fixture?.close() })

test('invalid credentials do not create a session; login verifies /me and rotated CSRF', async () => {
  await assert.rejects(protectionApi.login('fixture-admin', 'wrong-fixture-password'), { status: 401 })
  assert.equal(fixture.state.sessions.size, 0)
  assert.deepEqual(await protectionApi.login('fixture-admin', 'fixture-only'), { username: 'fixture-admin' })
  assert.deepEqual(await protectionApi.getCurrentAdmin(), { username: 'fixture-admin' })
  await protectionApi.allowOnEndpoint('A', { usbDeviceId: 1 })
  assert.equal(fixture.state.requests.at(-1).csrf, 'fixture-csrf-1')
})

test('logout invalidates server session and protected reads return 401', async () => {
  await protectionApi.logout()
  assert.equal(fixture.state.sessions.size, 0)
  await assert.rejects(protectionApi.getCurrentAdmin(), { status: 401 })
  await assert.rejects(protectionApi.getSnapshot(new AbortController().signal), { status: 401 })
})

test('missing CSRF prevents mutation HTTP calls', async () => {
  clearCsrfToken()
  const count = fixture.state.requests.length
  await assert.rejects(protectionApi.allowOnEndpoint('A', { usbDeviceId: 1 }), { status: 403 })
  assert.equal(fixture.state.requests.length, count)
})

test('HTML fallback cannot impersonate a valid Admin API or session', async () => {
  const previous = globalThis.fetch
  globalThis.fetch = async () => new Response('<html>login</html>', { headers: { 'Content-Type': 'text/html' } })
  try { await assert.rejects(protectionApi.getCurrentAdmin(), /JSON/) }
  finally { globalThis.fetch = previous }
})

test('network failure, timeout and caller cancellation stay distinguishable', async () => {
  const previous = globalThis.fetch
  globalThis.fetch = async () => { throw new TypeError('test offline') }
  try { await assert.rejects(request('/api/auth/me'), error => error instanceof ApiError && error.status === 0 && error.message.includes('Không kết nối')) }
  finally { globalThis.fetch = previous }
  globalThis.fetch = async (path, options) => new Promise((resolve, reject) => {
    const timer = setTimeout(() => resolve(new Response('{}')), 100)
    options.signal.addEventListener('abort', () => { clearTimeout(timer); reject(options.signal.reason) }, { once: true })
  })
  try { await assert.rejects(request('/api/auth/me', { timeoutMs: 5 }), /phản hồi quá lâu/) }
  finally { globalThis.fetch = previous }
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(protectionApi.getSnapshot(controller.signal), { name: 'AbortError' })
})

test('protected 401 notifies session expiry; session probes can opt out', async () => {
  const previousWindow = globalThis.window
  const previousFetch = globalThis.fetch
  const events = new EventTarget()
  let expired = 0
  events.addEventListener('usbshield:session-expired', () => { expired++ })
  globalThis.window = events
  globalThis.fetch = async () => Response.json({}, { status: 401 })
  try {
    await assert.rejects(request('/api/admin/endpoints'), { status: 401 })
    assert.equal(expired, 1)
    await assert.rejects(request('/api/auth/me', { notifyUnauthorized: false }), { status: 401 })
    assert.equal(expired, 1)
  } finally {
    globalThis.fetch = previousFetch
    if (previousWindow === undefined) delete globalThis.window
    else globalThis.window = previousWindow
  }
})
