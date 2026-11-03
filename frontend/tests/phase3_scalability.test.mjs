import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { protectionApi } from '../src/services/api.ts'
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

test('R10: 10,050 audit events do not require 101 HTTP page reads or hide dashboard counts', async () => {
  const original = fixture.state.events
  const baseline = structuredClone(original[0])
  try {
    fixture.state.events = Array.from({ length: 10050 }, (_, i) => ({ ...baseline,
      id: i + 100, occurredAt: new Date(Date.UTC(2026, 10, 3, 0, 0, 0) + i * 1000).toISOString(),
    }))
    const start = fixture.state.requests.length
    const snapshot = await protectionApi.getSnapshot(signal(), undefined, '/dashboard')
    assert.equal(snapshot.overview.recentEvents, 10050)
    assert.equal(snapshot.overview.totalDevices, 1)
    assert.equal(snapshot.events.length, 100)
    assert.equal(snapshot.devices.length, 0, 'dashboard never materializes a global device list')
    assert.equal(fixture.state.requests.slice(start).filter(item => item.path === '/api/admin/events').length, 1)
    assert.equal(fixture.state.requests.slice(start).filter(item => item.path === '/api/admin/device-states').length, 0)
  } finally { fixture.state.events = original }
})

test('R11: event arrival between HTTP requests does not abort dashboard snapshot', async () => {
  const originalFetch = globalThis.fetch
  const originalEvents = fixture.state.events
  const initialCount = originalEvents.length
  let injected = false
  try {
    globalThis.fetch = (path, options) => {
      if (!injected && path.startsWith('/api/admin/dashboard/summary')) {
        injected = true
        fixture.state.events.push({ ...fixture.state.events[0], id: 99999, occurredAt: new Date().toISOString() })
      }
      return originalFetch(path, options)
    }
    const snapshot = await protectionApi.getSnapshot(signal(), undefined, '/dashboard')
    assert.ok(snapshot.events.length > 0)
    assert.equal(snapshot.overview.recentEvents, initialCount + 1)
    assert.equal(injected, true)
  } finally { globalThis.fetch = originalFetch; fixture.state.events = originalEvents }
})

test('R10: device states are server-paged and filtering is applied before pagination', async () => {
  const page = await protectionApi.getDevicePage({ page: 0, size: 1, status: 'blocked', q: 'fixture' }, signal())
  assert.equal(page.totalElements, 1)
  assert.equal(page.content.length, 1)
  assert.equal(page.content[0].endpointId, 'A')
  const other = await protectionApi.getDevicePage({ page: 1, size: 1 }, signal())
  assert.equal(other.totalElements, 2)
  assert.equal(other.content.length, 1)
  assert.equal(other.content[0].endpointId, 'B')
})
