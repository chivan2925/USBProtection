import assert from 'node:assert/strict'
import { randomBytes, randomUUID } from 'node:crypto'

export const agentProtocol = { 'X-USBShield-Protocol': '2' }

export async function enrollSyntheticAgent(http, hostname) {
  const installationId = randomUUID()
  const slot = await http('/api/admin/enrollment/slots', {
    method: 'POST', admin: true, expected: 201, body: { hostname, installationId },
  })
  const ticket = await http(`/api/admin/enrollment/${slot.endpointId}/ticket`, {
    method: 'POST', admin: true, expected: 201,
  })
  const endpoint = await http('/api/agent/enroll', {
    method: 'POST', headers: agentProtocol, body: {
      hostname, installationId, machineId: randomUUID(), requestId: randomUUID(),
      enrollmentToken: ticket.enrollmentToken, agentToken: randomBytes(32).toString('hex'),
    },
  })
  assert.equal(endpoint.endpointId, slot.endpointId)
  return endpoint
}
