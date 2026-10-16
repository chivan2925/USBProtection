import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { mapEvent, mapEventPage, mapEvents } from '../src/services/mappers/eventMapper.ts'

// Synthetic Admin API v0.2 fixture matching EventResponse/EventPage, not a live HTTP run.
const pageFixture = JSON.parse(readFileSync(new URL('./fixtures/admin-events-page.json', import.meta.url), 'utf8'))
const fixture = pageFixture.content[0]
// Preserve the frozen M2 Mouse attribution while using Admin serialization fields.
const sample = JSON.parse(readFileSync(new URL('../../docs/agent-contract/sample-usb-event.json', import.meta.url), 'utf8'))

test('Admin read DTO preserves M2 mouse attribution, device ID and canonical interfaceClass', () => {
  const snapshot = structuredClone(fixture)
  const event = mapEvent(fixture)
  assert.equal(event.id, '101')
  assert.equal(event.endpointId, sample.endpointId)
  assert.equal(event.endpointName, sample.hostname)
  assert.equal(Date.parse(event.timestamp), Date.parse(sample.occurredAt))
  assert.equal(event.timestamp, fixture.occurredAt)
  assert.equal(event.receivedAt, fixture.receivedAt)
  assert.notEqual(event.timestamp, event.receivedAt)
  assert.equal(event.username, 'usbdev')
  assert.equal(event.linuxUid, 1000)
  assert.equal(event.sessionId, '21')
  assert.equal(event.sessionType, 'wayland')
  assert.equal(event.seat, 'seat0')
  assert.equal(event.type, 'connected')
  assert.equal(event.decision, 'allowed')
  assert.equal(event.deviceName, 'USB Optical Mouse')
  assert.equal(event.usbDeviceId, '7')
  assert.deepEqual(event.device, { name: sample.device.name, vendorId: '093a', productId: '2510', serial: '', hash: sample.device.hash, interfaceClass: '03:01:02' })
  assert.equal(event.message, '')
  assert.deepEqual(fixture, snapshot)
})

test('BLOCKED decision and UNKNOWN Linux user remain independent', () => {
  const event = mapEvent(pageFixture.content[1])
  assert.equal(event.decision, 'blocked')
  assert.equal(event.username, 'UNKNOWN')
  assert.equal(event.linuxUid, undefined)
  assert.equal(event.sessionId, undefined)
  assert.equal(event.usbDeviceId, '8')
  assert.equal(event.device.interfaceClass, '08:06:50')
  assert.equal(mapEvent({ ...fixture, eventType: 'DISCONNECTED' }).type, 'disconnected')
})

test('nullable legacy device/hostname and defensive UNKNOWN decision do not invent attribution', () => {
  // v0.2 emits ALLOWED/BLOCKED; retain existing explicit UNKNOWN read compatibility.
  for (const linuxUsername of ['UNKNOWN', '', ' ', null, undefined]) {
    const event = mapEvent({ ...fixture, linuxUsername, decision: 'UNKNOWN', hostname: null, usbDeviceId: null, device: null })
    assert.equal(event.username, 'UNKNOWN')
    assert.equal(event.decision, 'unknown')
    assert.equal(event.endpointName, undefined)
    assert.equal(event.usbDeviceId, undefined)
    assert.equal(event.device, undefined)
  }
})

test('array helper preserves empty results and rejects a page envelope or invalid item', () => {
  assert.deepEqual(mapEvents([]), [])
  assert.deepEqual(mapEvents([fixture]).map(event => event.id), ['101'])
  for (const payload of [null, {}, { content: [fixture] }]) assert.throws(() => mapEvents(payload), /expected event array/)
  assert.throws(() => mapEvents([fixture, { ...fixture, decision: 'INVALID' }]), /decision/)
})

test('bad identity, enums, sessions and canonical device fields fail the read', () => {
  const invalid = [null, [], {}, { ...fixture, id: undefined }, { ...fixture, id: 0 }, { ...fixture, id: 'event-101' }, { ...fixture, id: Number.MAX_SAFE_INTEGER + 1 }, { ...fixture, endpointId: '' }, { ...fixture, usbDeviceId: -1 }, { ...fixture, usbDeviceId: '' }, { ...fixture, usbDeviceId: 1.5 }, { ...fixture, usbDeviceId: Number.MAX_SAFE_INTEGER + 1 }, { ...fixture, eventType: 'INSERT' }, { ...fixture, decision: undefined }, { ...fixture, decision: 'allow' }, { ...fixture, linuxUsername: 123 }, { ...fixture, linuxUid: -1 }, { ...fixture, sessionId: 21 }, { ...fixture, device: [] }, { ...fixture, device: { name: 42 } }, { ...fixture, device: { interfaceClass: 8 } }]
  for (const payload of invalid) assert.throws(() => mapEvent(payload), /Invalid event payload/)
})

test('large string IDs retain precision for event links and device selection', () => {
  const event = mapEvent({ ...fixture, id: '9007199254740993', usbDeviceId: '9007199254740995' })
  assert.equal(event.id, '9007199254740993')
  assert.equal(event.usbDeviceId, '9007199254740995')
})

test('occurredAt and receivedAt require offset-bearing instants and preserve source precision', () => {
  for (const field of ['occurredAt', 'receivedAt']) {
    for (const value of ['invalid', '2026-10-13', '2026-10-13T12:00:00', '2026-02-30T12:00:00Z', '2026-10-13T24:00:00Z', '2026-10-13T12:60:00Z']) assert.throws(() => mapEvent({ ...fixture, [field]: value }), new RegExp(field))
    for (const value of ['2026-10-13T12:00:00Z', '2026-10-13T19:00:00+07:00', '2024-02-29T12:00:00.123456789Z']) assert.equal(mapEvent({ ...fixture, [field]: value })[field === 'occurredAt' ? 'timestamp' : field], value)
  }
  assert.throws(() => mapEvent({ ...fixture, occurredAt: null }), /occurredAt/)
  assert.equal(mapEvent({ ...fixture, receivedAt: null }).receivedAt, undefined)
})

test('Admin page mapping retains server totals, order and input without local pagination', () => {
  const snapshot = structuredClone(pageFixture)
  const result = mapEventPage(pageFixture)
  assert.deepEqual(result, { content: pageFixture.content.map(mapEvent), page: 0, size: 2, totalElements: 3, totalPages: 2 })
  assert.deepEqual(result.content.map(event => event.id), ['101', '100'])
  assert.notEqual(result.totalElements, result.content.length)
  assert.deepEqual(pageFixture, snapshot)
  // Sorting remains the server's responsibility, even if timestamp order differs.
  const reordered = mapEventPage({ ...pageFixture, content: [...pageFixture.content].reverse() })
  assert.deepEqual(reordered.content.map(event => event.id), ['100', '101'])
})

test('last partial page keeps its zero-based page and global totals', () => {
  const result = mapEventPage({ ...pageFixture, content: [{ ...fixture, id: 99 }], page: 1 })
  assert.equal(result.page, 1)
  assert.equal(result.size, 2)
  assert.equal(result.totalPages, 2)
  assert.equal(result.totalElements, 3)
  assert.deepEqual(result.content.map(event => event.id), ['99'])
})

test('empty results and out-of-range empty pages retain actual server metadata', () => {
  assert.deepEqual(mapEventPage({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }), { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  assert.deepEqual(mapEventPage({ ...pageFixture, content: [], page: 3 }), { content: [], page: 3, size: 2, totalElements: 3, totalPages: 2 })
})

test('malformed or contradictory page metadata cannot look like complete empty history', () => {
  const invalid = [null, [], {}, fixture, { ...pageFixture, content: null }]
  for (const field of ['page', 'size', 'totalElements', 'totalPages']) {
    for (const value of [undefined, null, -1, 0.5, '2', Number.MAX_SAFE_INTEGER + 1]) invalid.push({ ...pageFixture, [field]: value })
  }
  invalid.push(
    { ...pageFixture, size: 0 }, { ...pageFixture, size: 101 },
    { ...pageFixture, totalPages: 1 }, { ...pageFixture, totalElements: 0, totalPages: 0 },
    { ...pageFixture, page: 2 }, { ...pageFixture, content: [...pageFixture.content, fixture] },
  )
  for (const payload of invalid) assert.throws(() => mapEventPage(payload), /Invalid event payload/)
})

test('one invalid event rejects the entire page without silently dropping audit rows', () => {
  assert.throws(() => mapEventPage({ ...pageFixture, content: [fixture, { ...fixture, decision: 'INVALID' }] }), /decision/)
})
