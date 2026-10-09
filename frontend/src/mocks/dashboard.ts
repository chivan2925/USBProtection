// M4 owner — mock dashboard summary data for Week 1 UI.
// M4 does NOT create event-history mock table data (that is M5 scope).

export const mockDashboard = {
  endpointsOnline: 1,
  endpointsOffline: 1,
  blockedEventsToday: 3,
  totalDevices: 5,
  allowedDevices: 2,
  blockedDevices: 3,
  recentEvents: 8,
  protection: 'enabled' as const,
  service: 'running' as const,
}
