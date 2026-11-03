import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { protectionApi } from '../src/services/api.ts'
import { ApiError } from '../src/services/http.ts'
import { mapEndpoint } from '../src/services/mappers/endpointMapper.ts'
import { mapWhitelistEntry } from '../src/services/mappers/policyMapper.ts'
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

// R10/R11: snapshot reads only one preview page and server summaries, not all audit pages.
test('loads scoped audit preview without redundant inventory reads', async () => {
  const start = fixture.state.requests.length
  const snapshot = await protectionApi.getSnapshot(signal(), 'A')
  assert.equal(snapshot.events.length, 1)
  assert.equal(snapshot.endpoints[0].policyVersion, '1')
  assert.equal(snapshot.endpoints[0].appliedPolicyVersion, '0')
  assert.equal(snapshot.endpoints[0].ipAddress, '192.0.2.10')
  assert.equal(snapshot.overview.protection, 'unknown')
  assert.equal(snapshot.overview.blockedToday, 1)
  assert.equal(snapshot.overview.onlineEndpoints, 1)
  assert.equal(snapshot.overview.offlineEndpoints, 1)
  assert.equal(snapshot.devices.length, 0, 'endpoint detail owns the paged device fetch')
  assert.ok(fixture.state.requests.some(item => item.path === '/api/admin/endpoints/A'))
  const reads = fixture.state.requests.slice(start)
  assert.equal(reads.filter(item => item.path === '/api/admin/events').length, 1)
  assert.equal(reads.filter(item => item.path === '/api/admin/device-states').length, 0,
    'snapshot must not duplicate the endpoint detail device-page request')
  assert.ok(reads.some(item => item.path === '/api/admin/dashboard/summary'))
  assert.equal(reads.filter(item => item.path === '/api/admin/events' && item.query.includes('page=1')).length, 0)
  const global = await protectionApi.getSnapshot(signal())
  assert.equal(global.events[1].username, 'UNKNOWN')
  const inventory = await protectionApi.getDevicePage({ page: 0, size: 20 }, signal())
  assert.equal(inventory.content.length, 2, 'same USB on A and B stays isolated')
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
    assert.deepEqual(data.devices, [])
    assert.deepEqual(data.whitelist, [])
    assert.equal(data.overview.recentEvents, 0)
    assert.equal(data.overview.totalDevices, 0)
  } finally { fixture.state.empty = false }
})

test('403/409/500 and malformed pagination reject reads or mutations, never succeed', async () => {
  for (const status of [403, 409, 500]) {
    fixture.state.failure = status
    await assert.rejects(protectionApi.getSnapshot(signal()), error => error instanceof ApiError && error.status === status)
  }
  fixture.state.failure = 0
  fixture.state.brokenPage = true
  try { await assert.rejects(protectionApi.getSnapshot(signal()), /Invalid event payload/) }
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
  const json = data => new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } })
  globalThis.fetch = async (path, options) => path === '/api/admin/endpoints'
    ? json([...fixture.state.endpoints, fixture.state.endpoints[0]]) : original(path, options)
  try { await assert.rejects(protectionApi.getSnapshot(signal()), /endpoint ID bị trùng/) }
  finally { globalThis.fetch = original }
  globalThis.fetch = async (path, options) => path.startsWith('/api/admin/events?')
    ? json({ content: [fixture.state.events[0], fixture.state.events[0]], page: 0, size: 100, totalElements: 2, totalPages: 1 })
    : original(path, options)
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

test('endpoint mapper preserves offline/no-heartbeat and rejects impossible policy versions', () => {
  const endpoint = { endpointId: 'A', hostname: 'lab-a', status: 'OFFLINE', lastSeen: null, policyVersion: 2, appliedPolicyVersion: 0 }
  const mapped = mapEndpoint(endpoint)
  assert.equal(mapped.status, 'offline')
  assert.equal(mapped.lastSeenAt, undefined)
  assert.equal(mapped.policyVersion, '2')
  assert.equal(mapped.appliedPolicyVersion, '0')
  assert.equal(mapEndpoint({ ...endpoint, appliedPolicyVersion: null }).appliedPolicyVersion, undefined)
  assert.equal(mapEndpoint({ ...endpoint, status: 'UNKNOWN' }).status, 'unknown')
  for (const version of [-1, 1.5, '2', Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => mapEndpoint({ ...endpoint, policyVersion: version }), /policy version/)
  }
  assert.throws(() => mapEndpoint({ ...endpoint, appliedPolicyVersion: 3 }), /applied.*desired/)
  assert.throws(() => mapEndpoint({ ...endpoint, lastSeen: 'bad-date' }), /lastSeen/)
})

test('whitelist mapper accepts no-serial HASH and rejects weak or malformed identities', () => {
  const entry = { id: 1, endpointId: 'A', fingerprintType: 'HASH', fingerprintValue: 'usbguard-hash', enabled: true }
  const mapped = mapWhitelistEntry(entry, 'A')
  assert.equal(mapped.fingerprintValue, 'usbguard-hash')
  assert.equal(mapped.serial, undefined)
  for (const fingerprintType of ['SERIAL', 'VID_PID', '', null]) {
    assert.throws(() => mapWhitelistEntry({ ...entry, fingerprintType }, 'A'), /HASH/)
  }
  assert.throws(() => mapWhitelistEntry({ ...entry, fingerprintValue: ' ' }, 'A'), /văn bản/)
  assert.throws(() => mapWhitelistEntry({ ...entry, enabled: 'false' }, 'A'), /trạng thái whitelist/)
  assert.throws(() => mapWhitelistEntry({ ...entry, endpointId: 'B' }, 'A'), /endpoint khác/)
})

test('fixture rejects duplicate Revoke without changing desired version again', async () => {
  await protectionApi.allowOnEndpoint('A', { usbDeviceId: 1 })
  const [entry] = await protectionApi.getWhitelist('A', signal())
  await protectionApi.revokeOnEndpoint('A', entry.id)
  const afterFirstRevoke = await protectionApi.getEndpoint('A', signal())
  await assert.rejects(protectionApi.revokeOnEndpoint('A', entry.id), { status: 404 })
  assert.deepEqual(await protectionApi.getEndpoint('A', signal()), afterFirstRevoke)
})

test('policy-applied event overrides transient BLOCK without inventing a second CONNECTED', async () => {
  const original = structuredClone(fixture.state.events)
  try {
    fixture.state.events.push({ ...original[0], id: 77, eventType: 'POLICY_APPLIED',
      decision: 'ALLOWED', occurredAt: new Date(Date.now() + 1500).toISOString() })
    const data = await protectionApi.getSnapshot(signal())
    assert.equal(data.events.filter(event => event.type === 'connected' && event.endpointId === 'A').length, 1)
    assert.equal(data.events.filter(event => event.type === 'policy_applied' && event.endpointId === 'A').length, 1)
    const inventory = await protectionApi.getDevicePage({ page: 0, size: 20 }, signal())
    assert.equal(inventory.content.find(device => device.endpointId === 'A').status, 'allowed')
    assert.equal(data.overview.blockedToday, 0, 'transient BLOCK is not a current blocked device')
    const filtered = await protectionApi.getEventPage({ page: 0, size: 20, eventType: 'POLICY_APPLIED' }, signal())
    assert.equal(filtered.totalElements, 1)
  } finally {
    fixture.state.events = original
  }
})
