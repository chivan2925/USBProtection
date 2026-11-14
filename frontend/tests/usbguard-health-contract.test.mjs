import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mapEndpoint } from '../src/services/mappers/endpointMapper.ts'

const base = { endpointId: 'EP-01', hostname: 'ubuntu-01', status: 'ONLINE',
  policyVersion: 4, appliedPolicyVersion: 4 }

test('USBGuard health is mapped independently of last policy ACK', () => {
  const healthy = mapEndpoint({ ...base, usbGuardStatus: 'HEALTHY',
    usbGuardCheckedAt: '2026-11-04T02:00:00Z', usbGuardStale: false })
  assert.equal(healthy.usbGuardStatus, 'HEALTHY')
  assert.equal(healthy.appliedPolicyVersion, '4')
  assert.equal(healthy.usbGuardStale, false)
  const old = mapEndpoint({ ...base, usbGuardStatus: 'UNKNOWN', usbGuardStale: true })
  assert.equal(old.usbGuardStatus, 'UNKNOWN')
})

test('rejects invalid health contract', () => {
  assert.throws(() => mapEndpoint({ ...base, usbGuardStatus: 'MAGIC' }), /USBGuard/)
  assert.throws(() => mapEndpoint({ ...base, usbGuardStale: 'false' }), /stale/)
})
