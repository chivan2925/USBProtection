import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { mapEvent, mapEvents } from '../src/services/mappers/eventMapper.ts'

// Draft read fixture = unchanged M2 sample plus a persisted ID; no live API.
const sample = JSON.parse(readFileSync(new URL('../../docs/agent-contract/sample-usb-event.json', import.meta.url), 'utf8'))
const fixture = { ...sample, id: 101 }

test('draft mapper preserves M2 mouse identity, session and ALLOWED decision', () => {
  const snapshot = structuredClone(fixture)
  const event = mapEvent(fixture)
  assert.equal(event.id, '101')
  assert.equal(event.endpointId, sample.endpointId)
  assert.equal(event.endpointName, sample.hostname)
  assert.equal(event.timestamp, sample.occurredAt)
  assert.equal(event.username, 'usbdev')
  assert.equal(event.linuxUid, 1000)
  assert.equal(event.sessionId, '21')
  assert.equal(event.sessionType, 'wayland')
  assert.equal(event.seat, 'seat0')
  assert.equal(event.type, 'connected')
  assert.equal(event.decision, 'allowed')
  assert.equal(event.deviceName, 'USB Optical Mouse')
  assert.deepEqual(event.device, { name: sample.device.name, vendorId: '093a', productId: '2510', serial: '', hash: sample.device.hash, interfaceClass: '03:01:02' })
  assert.equal(event.message, '')
  assert.deepEqual(fixture, snapshot)
})

test('blocked/disconnected and explicit UNKNOWN keep their meaning', () => {
  assert.equal(mapEvent({ ...fixture, decision: 'BLOCKED' }).decision, 'blocked')
  assert.equal(mapEvent({ ...fixture, eventType: 'DISCONNECTED' }).type, 'disconnected')
  for (const linuxUsername of ['UNKNOWN', '', ' ', null, undefined]) {
    const event = mapEvent({ ...fixture, linuxUsername, decision: 'UNKNOWN', device: null })
    assert.equal(event.username, 'UNKNOWN')
    assert.equal(event.decision, 'unknown')
    assert.equal(event.device, undefined)
  }
})

test('arrays preserve empty results without inventing a pagination envelope', () => {
  assert.deepEqual(mapEvents([]), [])
  assert.deepEqual(mapEvents([fixture]).map(event => event.id), ['101'])
  for (const payload of [null, {}, { content: [fixture] }]) assert.throws(() => mapEvents(payload), /expected event array/)
  assert.throws(() => mapEvents([fixture, { ...fixture, decision: 'INVALID' }]), /decision/)
})

test('bad identity, enums, sessions and devices fail without fabricated values', () => {
  const invalid = [null, [], {}, { ...fixture, id: undefined }, { ...fixture, id: 0 }, { ...fixture, id: Number.MAX_SAFE_INTEGER + 1 }, { ...fixture, endpointId: '' }, { ...fixture, eventType: 'INSERT' }, { ...fixture, decision: undefined }, { ...fixture, decision: 'allow' }, { ...fixture, linuxUsername: 123 }, { ...fixture, linuxUid: -1 }, { ...fixture, sessionId: 21 }, { ...fixture, device: [] }, { ...fixture, device: { name: 42 } }]
  for (const payload of invalid) assert.throws(() => mapEvent(payload), /Invalid event payload/)
  assert.equal(mapEvent({ ...fixture, id: '9007199254740993' }).id, '9007199254740993')
})

test('occurredAt requires a valid offset-bearing instant and preserves the source', () => {
  for (const occurredAt of ['invalid', '2026-10-13', '2026-10-13T12:00:00', '2026-02-30T12:00:00Z', '2026-10-13T24:00:00Z', '2026-10-13T12:60:00Z']) assert.throws(() => mapEvent({ ...fixture, occurredAt }), /occurredAt/)
  for (const occurredAt of ['2026-10-13T12:00:00Z', '2026-10-13T19:00:00+07:00', '2024-02-29T12:00:00.123456Z']) assert.equal(mapEvent({ ...fixture, occurredAt }).timestamp, occurredAt)
})
