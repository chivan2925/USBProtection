// Chạy riêng: node --test tests/m4_policy_live.test.mjs
// Backend thật, DB riêng; event và ACK là đầu vào HTTP tổng hợp trên Windows.
import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import { startLiveServer } from './liveServer.mjs'

test('real backend keeps duplicate Revoke idempotent and rejects future ACK without changing B', async () => {
  const artifacts = fileURLToPath(new URL('../.artifacts', import.meta.url))
  await mkdir(artifacts, { recursive: true })
  const backend = await startLiveServer(artifacts)
  try {
    await backend.seed()
    const beforeB = await backend.endpoint(backend.b.endpointId)
    const beforeRulesB = await backend.rules(backend.b.endpointId)
    const events = await backend.http('/api/admin/events', { admin: true })
    const path = `/api/admin/endpoints/${backend.a.endpointId}/whitelist`
    await backend.http(path, { method: 'POST', admin: true, body: { usbDeviceId: events.content[0].usbDeviceId } })
    const [entry] = await backend.rules(backend.a.endpointId)
    const allowed = await backend.endpoint(backend.a.endpointId)
    await backend.http('/api/agent/policy/ack', {
      method: 'POST', headers: backend.agentHeaders,
      body: { appliedPolicyVersion: allowed.policyVersion + 1 }, expected: 409,
    })
    assert.equal((await backend.endpoint(backend.a.endpointId)).appliedPolicyVersion, 0)
    await backend.http(`${path}/${entry.id}`, { method: 'DELETE', admin: true, expected: 204 })
    const revoked = await backend.endpoint(backend.a.endpointId)
    await backend.http(`${path}/${entry.id}`, { method: 'DELETE', admin: true, expected: 204 })
    assert.deepEqual(await backend.endpoint(backend.a.endpointId), revoked)
    assert.deepEqual(await backend.rules(backend.a.endpointId), [])
    assert.deepEqual(await backend.endpoint(backend.b.endpointId), beforeB)
    assert.deepEqual(await backend.rules(backend.b.endpointId), beforeRulesB)
  } finally {
    await backend.close()
  }
})
