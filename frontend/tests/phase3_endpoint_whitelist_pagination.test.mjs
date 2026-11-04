import assert from 'node:assert/strict'
import { before, after, test } from 'node:test'
import { protectionApi } from '../src/services/api.ts'
import { startAdminFixture, useFixtureFetch } from './fixtures/adminServer.mjs'

let fixture, restore
const signal = () => new AbortController().signal
before(async () => {
  fixture = await startAdminFixture()
  restore = useFixtureFetch(fixture.origin)
  await protectionApi.login('fixture-admin', 'fixture-only')
})
after(async () => { restore?.(); await fixture?.close() })

test('Endpoints: server filters first, paginates and returns global online/offline counts', async () => {
  const a = await protectionApi.getEndpointPage({ page: 0, size: 1, q: 'fixture' }, signal())
  assert.equal(a.content.length, 1)
  assert.equal(a.totalElements, 2)
  assert.equal(a.totalPages, 2)
  assert.equal(a.onlineEndpoints, 1)
  assert.equal(a.offlineEndpoints, 1)
  const b = await protectionApi.getEndpointPage({ page: 1, size: 1, status: 'offline' }, signal())
  assert.deepEqual(b.content, [])
  assert.equal(b.totalElements, 1)
  assert.equal((await protectionApi.getEndpointPage({ page: 0, size: 1, status: 'offline' }, signal())).content[0].id, 'B')
  await assert.rejects(protectionApi.getEndpointPage({ page: -1, size: 20 }, signal()), /phân trang/)
})

test('Whitelist: global search reads one bounded page, not N Endpoint HTTP calls', async () => {
  const start = fixture.state.requests.length
  const page = await protectionApi.getWhitelistPage({ page: 0, size: 20 }, signal())
  assert.equal(page.totalElements, 1)
  assert.equal(page.content[0].endpointId, 'B')
  assert.equal(page.content[0].fingerprintType, 'HASH')
  assert.equal(fixture.state.requests.slice(start).filter(r => r.path === '/api/admin/whitelist/page').length, 1)
  assert.equal(fixture.state.requests.slice(start).filter(r => r.path.includes('/whitelist') && r.path !== '/api/admin/whitelist/page').length, 0)
  assert.equal((await protectionApi.getWhitelistPage({ page: 0, size: 20, endpointId: 'A' }, signal())).totalElements, 0)
  await assert.rejects(protectionApi.getWhitelistPage({ page: 0, size: 101 }, signal()), /phân trang/)
})

test('Dashboard totals are global and not inferred from truncated Endpoint pages', async () => {
  const snapshot = await protectionApi.getSnapshot(signal(), undefined, '/dashboard')
  assert.equal(snapshot.overview.onlineEndpoints, 1)
  assert.equal(snapshot.overview.offlineEndpoints, 1)
  assert.equal(snapshot.whitelist.length, 0)
  const paths = fixture.state.requests.slice(-4).map(r => r.path)
  assert.ok(paths.includes('/api/admin/endpoints/page'))
  assert.ok(!paths.includes('/api/admin/endpoints'))
})
