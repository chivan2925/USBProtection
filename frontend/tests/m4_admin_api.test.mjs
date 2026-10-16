import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { protectionApi } from '../src/services/api.ts'
import { ApiError } from '../src/services/http.ts'
import { startAdminFixture, useFixtureFetch } from './fixtures/adminServer.mjs'

let fixture
let restore
before(async () => {
  fixture = await startAdminFixture()
  restore = useFixtureFetch(fixture.origin)
  await protectionApi.login('fixture-admin', 'fixture-only')
})
after(async () => { restore?.(); await fixture?.close() })
const signal = () => new AbortController().signal

test('loads all event pages, real endpoint IDs, unknown users and distinct desired/applied versions', async () => {
  const snapshot = await protectionApi.getSnapshot(signal(), 'A')
  assert.equal(snapshot.events.length, 2)
  assert.equal(snapshot.events[1].username, 'UNKNOWN')
  assert.equal(snapshot.endpoints[0].policyVersion, '1')
  assert.equal(snapshot.endpoints[0].appliedPolicyVersion, '0')
  assert.equal(snapshot.endpoints[0].ipAddress, '192.0.2.10')
  assert.equal(snapshot.overview.protection, 'unknown')
  assert.equal(snapshot.overview.blockedToday, 1)
  assert.equal(snapshot.overview.onlineEndpoints, 1)
  assert.equal(snapshot.overview.offlineEndpoints, 1)
  assert.equal(snapshot.devices.length, 2, 'same USB on A and B stays isolated')
  assert.ok(fixture.state.requests.some(item => item.path === '/api/admin/endpoints/A'))
  assert.ok(fixture.state.requests.some(item => item.query.includes('page=1')))
})

test('Allow/Revoke updates only A desired version; applied and B remain unchanged', async () => {
  const beforeB = structuredClone(fixture.state.endpoints[1])
  const beforeRulesB = structuredClone(fixture.state.whitelist.B)
  await protectionApi.allowOnEndpoint('A', { usbDeviceId: 1 })
  let snapshot = await protectionApi.getSnapshot(signal())
  assert.equal(snapshot.endpoints[0].policyVersion, '2')
  assert.equal(snapshot.endpoints[0].appliedPolicyVersion, '0')
  const entry = snapshot.whitelist.find(item => item.endpointId === 'A')
  assert.ok(entry)
  await protectionApi.allowOnEndpoint('A', { usbDeviceId: 1 })
  assert.equal((await protectionApi.getEndpoint('A', signal())).policyVersion, '2', 'duplicate Allow is idempotent')
  await protectionApi.revokeOnEndpoint('A', entry.id)
  snapshot = await protectionApi.getSnapshot(signal())
  assert.equal(snapshot.endpoints[0].policyVersion, '3')
  assert.equal(snapshot.endpoints[0].appliedPolicyVersion, '0')
  assert.ok(!snapshot.whitelist.some(item => item.endpointId === 'A'))
  assert.deepEqual(fixture.state.endpoints[1], beforeB)
  assert.deepEqual(fixture.state.whitelist.B, beforeRulesB)
})

test('empty response stays empty with zero counts, not demo data', async () => {
  fixture.state.empty = true
  try {
    const data = await protectionApi.getSnapshot(signal())
    assert.deepEqual(data.endpoints, [])
    assert.deepEqual(data.events, [])
    assert.deepEqual(data.whitelist, [])
    assert.equal(data.overview.recentEvents, 0)
  } finally { fixture.state.empty = false }
})

test('403/409/500 and malformed pagination reject reads or mutations, never succeed', async () => {
  for (const status of [403, 409, 500]) {
    fixture.state.failure = status
    await assert.rejects(protectionApi.getSnapshot(signal()), error => error instanceof ApiError && error.status === status)
  }
  fixture.state.failure = 0
  fixture.state.brokenPage = true
  try { await assert.rejects(protectionApi.getSnapshot(signal()), /phân trang không hợp lệ/) }
  finally { fixture.state.brokenPage = false }
  fixture.state.failMutation = 500
  try {
    await assert.rejects(protectionApi.allowOnEndpoint('A', { usbDeviceId: 1 }), { status: 500 })
    assert.equal(fixture.state.whitelist.A.length, 0)
  } finally { fixture.state.failMutation = 0 }
})

test('invalid device IDs are rejected before HTTP and unsafe endpoint IDs are URL-encoded', async () => {
  const count = fixture.state.requests.length
  for (const usbDeviceId of [0, -1, 1.5, '1', undefined]) await assert.rejects(protectionApi.allowOnEndpoint('A', { usbDeviceId }), /usbDeviceId/)
  await assert.rejects(protectionApi.allowOnEndpoint('', { usbDeviceId: 1 }), /endpoint/)
  assert.equal(fixture.state.requests.length, count)
  await assert.rejects(protectionApi.getEndpoint('A/../B', signal()), { status: 404 })
  assert.equal(fixture.state.requests.at(-1).path, '/api/admin/endpoints/A%2F..%2FB')
})

test('duplicate events/endpoints fail rather than inflating rows and counts', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async path => new Response(JSON.stringify(path.startsWith('/api/admin/endpoints')
    ? [...fixture.state.endpoints, fixture.state.endpoints[0]] : fixture.state.events), { headers: { 'Content-Type': 'application/json' } })
  try { await assert.rejects(protectionApi.getSnapshot(signal()), /endpoint ID bị trùng/) }
  finally { globalThis.fetch = original }
  globalThis.fetch = async path => new Response(JSON.stringify(path.startsWith('/api/admin/endpoints')
    ? fixture.state.endpoints : [...fixture.state.events, fixture.state.events[0]]), { headers: { 'Content-Type': 'application/json' } })
  try { await assert.rejects(protectionApi.getSnapshot(signal()), /event ID bị trùng/) }
  finally { globalThis.fetch = original }
})

test('cross-endpoint whitelist and detail responses fail rather than being reassigned', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify([{ id: 1, endpointId: 'B' }]), { headers: { 'Content-Type': 'application/json' } })
  try { await assert.rejects(protectionApi.getWhitelist('A', signal()), /endpoint khác/) }
  finally { globalThis.fetch = original }
  globalThis.fetch = async () => new Response(JSON.stringify(fixture.state.endpoints[1]), { headers: { 'Content-Type': 'application/json' } })
  try { await assert.rejects(protectionApi.getEndpoint('A', signal()), /endpoint khác/) }
  finally { globalThis.fetch = original }
})
