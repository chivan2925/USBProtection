import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { ApiError, protectionApi } from '../src/services/api.ts'
import { getEventQuery } from '../src/utils/eventFilters.ts'
import { emptyEventFilters } from '../src/types/event.ts'

const fixture = JSON.parse(readFileSync(new URL('./fixtures/admin-events-page.json', import.meta.url), 'utf8'))
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
async function withFetch(handler, run) {
  const original = globalThis.fetch
  globalThis.fetch = handler
  try { await run() } finally { globalThis.fetch = original }
}

test('live event adapter uses exact canonical query, session cookie and server page totals', async () => {
  const query = { page: 0, size: 2, endpointId: 'endpoint&one', linuxUsername: 'student01', deviceId: '7', decision: 'ALLOWED', eventType: 'CONNECTED', to: '2026-10-16T16:59:59.999999999Z' }
  await withFetch(async (path, options) => {
    const url = new URL(path, 'http://localhost')
    assert.equal(url.pathname, '/api/admin/events')
    assert.deepEqual(Object.fromEntries(url.searchParams), Object.fromEntries(Object.entries(query).map(([key, value]) => [key, String(value)])))
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.cache, 'no-store')
    assert.ok(options.signal instanceof AbortSignal)
    return json(fixture)
  }, async () => {
    const result = await protectionApi.getEventPage(query, new AbortController().signal)
    assert.equal(result.totalElements, 3)
    assert.deepEqual(result.content.map(event => event.usbDeviceId), ['7', '8'])
  })
})

test('empty HTTP result stays empty; auth/server/network/HTML failures never become fixtures', async () => {
  const query = { page: 0, size: 20 }
  await withFetch(async () => json({ content: [], ...query, totalElements: 0, totalPages: 0 }), async () => {
    assert.equal((await protectionApi.getEventPage(query, new AbortController().signal)).content.length, 0)
  })
  for (const status of [400, 401, 403, 500]) await withFetch(async () => json({ message: 'server detail' }, status), async () => {
    await assert.rejects(protectionApi.getEventPage(query, new AbortController().signal), failure => failure instanceof ApiError && failure.status === status && !failure.message.includes('server detail'))
  })
  for (const handler of [async () => { throw new TypeError('offline') }, async () => new Response('<html>index</html>'), async () => json({ content: [] })]) await withFetch(handler, async () => {
    await assert.rejects(protectionApi.getEventPage(query, new AbortController().signal))
  })
})

test('cancelled live read makes no HTTP request', async () => {
  await withFetch(async () => { assert.fail('Aborted request must not fetch') }, async () => {
    const controller = new AbortController()
    controller.abort()
    await assert.rejects(protectionApi.getEventPage({ page: 0, size: 20 }, controller.signal), { name: 'AbortError' })
  })
})

test('login rotates CSRF, me restores session, logout fetches current token', async () => {
  const requests = []
  let csrf = 0
  await withFetch(async (path, options) => {
    requests.push(path)
    assert.equal(options.credentials, 'same-origin')
    if (path === '/api/auth/csrf') return json({ token: `csrf-${++csrf}`, headerName: 'X-CSRF-TOKEN' })
    if (path === '/api/auth/login') {
      assert.equal(options.method, 'POST')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-1')
      assert.deepEqual(JSON.parse(options.body), { username: 'qa-admin', password: 'test-only-password' })
      return json({ username: 'qa-admin' })
    }
    if (path === '/api/auth/me') return json({ username: 'qa-admin' })
    assert.equal(path, '/api/auth/logout')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-3')
    return json({ success: true })
  }, async () => {
    await protectionApi.login('qa-admin', 'test-only-password')
    assert.deepEqual(await protectionApi.getCurrentAdmin(new AbortController().signal), { username: 'qa-admin' })
    await protectionApi.logout()
  })
  assert.deepEqual(requests, ['/api/auth/csrf', '/api/auth/login', '/api/auth/csrf', '/api/auth/me', '/api/auth/csrf', '/api/auth/logout'])
})

test('invalid CSRF response prevents credentials from being submitted', async () => {
  await withFetch(async path => { assert.equal(path, '/api/auth/csrf'); return json({ token: '', headerName: 'Unexpected-Header' }) }, async () => {
    await assert.rejects(protectionApi.login('qa-admin', 'test-only-password'), /CSRF/)
  })
})

test('live filters use exact device/user identity and include the last nanosecond of local dates', () => {
  const originalTZ = process.env.TZ
  try {
    process.env.TZ = 'Asia/Bangkok'
    const query = getEventQuery({ ...emptyEventFilters, endpoint: ' endpoint-A ', username: ' student01 ', device: '9007199254740993', decision: 'blocked', eventType: 'disconnected', dateFrom: '2026-10-16', dateTo: '2026-10-16' }, 1, 20)
    assert.deepEqual(query, { page: 1, size: 20, endpointId: 'endpoint-A', linuxUsername: 'student01', deviceId: '9007199254740993', decision: 'BLOCKED', eventType: 'DISCONNECTED', from: '2026-10-15T17:00:00.000Z', to: '2026-10-16T16:59:59.999999999Z' })
    process.env.TZ = 'America/New_York'
    const dst = getEventQuery({ ...emptyEventFilters, dateFrom: '2026-03-08', dateTo: '2026-03-08' })
    assert.equal(dst.from, '2026-03-08T05:00:00.000Z')
    assert.equal(dst.to, '2026-03-09T03:59:59.999999999Z')
  } finally { if (originalTZ === undefined) delete process.env.TZ; else process.env.TZ = originalTZ }
})

test('invalid live filters are rejected instead of silently changing matching semantics', () => {
  for (const filters of [{ device: 'Kingston' }, { device: '-1' }, { device: '9223372036854775808' }, { decision: 'unknown' }, { eventType: 'service' }, { dateFrom: '2026-10-17', dateTo: '2026-10-16' }]) assert.throws(() => getEventQuery({ ...emptyEventFilters, ...filters }))
})
