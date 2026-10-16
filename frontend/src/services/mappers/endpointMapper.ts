import type { Endpoint } from '../models.ts'
import { readObject, readText, readVersion } from '../http.ts'

export function mapEndpoint(value: unknown): Endpoint {
  const data = readObject(value)
  const hostname = readText(data.hostname, true)!
  let status: Endpoint['status'] = 'unknown'
  if (data.status === 'ONLINE') status = 'online'
  if (data.status === 'OFFLINE') status = 'offline'
  return {
    id: readText(data.endpointId, true)!, name: readText(data.name) ?? hostname,
    hostname, ipAddress: readText(data.ipAddress), operatingSystem: readText(data.operatingSystem),
    status, currentUser: readText(data.currentUser), lastSeenAt: readText(data.lastSeen),
    policyVersion: readVersion(data.policyVersion), appliedPolicyVersion: readVersion(data.appliedPolicyVersion),
  }
}
