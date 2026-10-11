import type { ResourceState } from '../hooks/useResource'
import type { Snapshot } from '../services/models'
import { mockEndpoints, mockWhitelist } from './endpoints'
import { mockEvents } from './events'

// Week 1 preview only. A real empty response or load failure stays unchanged.
export const mockAdminSnapshot: Snapshot = {
  overview: {
    protection: 'unknown', service: 'unknown',
    totalDevices: 2, allowedDevices: 1, blockedDevices: 1,
    recentEvents: mockEvents.length, onlineEndpoints: 1, offlineEndpoints: 1,
  },
  endpoints: mockEndpoints.map(endpoint => ({
    id: endpoint.endpointId,
    name: endpoint.name ?? endpoint.hostname,
    hostname: endpoint.hostname,
    operatingSystem: endpoint.operatingSystem,
    status: endpoint.status,
    currentUser: endpoint.currentUser,
    lastSeenAt: endpoint.lastSeen,
    policyVersion: endpoint.policyVersion,
  })),
  devices: mockEndpoints.map(endpoint => ({
    id: `kingston-${endpoint.endpointId}`,
    name: mockWhitelist[0].name ?? 'Kingston',
    vendorId: mockWhitelist[0].vendorId,
    productId: mockWhitelist[0].productId,
    serialNumber: mockWhitelist[0].serial,
    endpointId: endpoint.endpointId,
    endpointName: endpoint.name,
    status: mockWhitelist.some(entry => entry.endpointId === endpoint.endpointId) ? 'allowed' : 'blocked',
    detectedAt: endpoint.lastSeen,
  })),
  events: mockEvents,
  whitelist: mockWhitelist,
}

export function adminPreviewState(state: ResourceState<Snapshot>): ResourceState<Snapshot> {
  return state.status === 'unavailable' ? { status: 'ready', data: mockAdminSnapshot } : state
}
