import type { Snapshot } from './models'
import { mockEndpoints, mockWhitelist } from '../mocks/endpoints'
import { mockEvents } from '../mocks/events'

// Explicit Week 1 demo: read-only fixtures, no HTTP, auth or policy mutations.
export async function getDemoSnapshot(signal: AbortSignal): Promise<Snapshot> {
  signal.throwIfAborted()
  const endpoints = mockEndpoints.map(endpoint => ({
    id: endpoint.endpointId,
    name: endpoint.name ?? endpoint.hostname,
    hostname: endpoint.hostname,
    operatingSystem: endpoint.operatingSystem,
    status: endpoint.status,
    currentUser: endpoint.currentUser,
    lastSeenAt: endpoint.lastSeen,
    policyVersion: endpoint.policyVersion,
  }))
  const usb = mockWhitelist[0]
  // Same physical USB, allowed on A and blocked on B.
  const devices = endpoints.map(endpoint => ({
    id: `demo-usb-${endpoint.id}`,
    name: usb.name ?? 'Kingston USB',
    vendorId: usb.vendorId,
    productId: usb.productId,
    serialNumber: usb.serial,
    endpointId: endpoint.id,
    endpointName: endpoint.name,
    status: endpoint.id === usb.endpointId ? 'allowed' as const : 'blocked' as const,
    detectedAt: usb.addedAt,
    ...(endpoint.id === usb.endpointId ? {} : {
      blockedAt: usb.addedAt,
      blockReason: 'USB chưa được whitelist trên endpoint này.',
    }),
  }))
  const legacyNames: Record<string, string> = { 'linux-lab-01': 'LAB-PC-01', 'linux-lab-02': 'LAB-PC-02' }
  const events = mockEvents.map(event => {
    const endpoint = endpoints.find(item => item.name === (legacyNames[event.endpointName ?? ''] ?? event.endpointName))
    return { ...event, endpointId: endpoint?.id, endpointName: endpoint?.name ?? event.endpointName }
  })
  return {
    overview: {
      protection: 'enabled', service: 'running',
      totalDevices: devices.length,
      blockedDevices: devices.filter(device => device.status === 'blocked').length,
      allowedDevices: devices.filter(device => device.status === 'allowed').length,
      recentEvents: events.length,
      onlineEndpoints: endpoints.filter(endpoint => endpoint.status === 'online').length,
      offlineEndpoints: endpoints.filter(endpoint => endpoint.status === 'offline').length,
    },
    endpoints, devices, events,
    whitelist: mockWhitelist.map(entry => ({ ...entry })),
  }
}
