// M4 owner — mock endpoint data for Week 1 UI
// Demonstrates per-endpoint policy differs between machines.
// LAB-PC-01 has Kingston allowed; LAB-PC-02 does not.

import type { Endpoint } from '../types/endpoint'
import type { WhitelistEntry } from '../types/policy'

export const mockEndpoints: Endpoint[] = [
  {
    endpointId: 'POC-LAB-PC-01',
    hostname: 'lab-pc-01.local',
    name: 'LAB-PC-01',
    operatingSystem: 'Ubuntu 24.04.5 LTS',
    status: 'online',
    currentUser: 'usbdev',
    lastSeen: '2026-09-28T14:10:16+07:00',
    policyVersion: '2',
    appliedPolicyVersion: '2',
  },
  {
    endpointId: 'POC-LAB-PC-02',
    hostname: 'lab-pc-02.local',
    name: 'LAB-PC-02',
    operatingSystem: 'Ubuntu 24.04.5 LTS',
    status: 'offline',
    currentUser: 'student01',
    lastSeen: '2026-09-27T09:32:00+07:00',
    policyVersion: '1',
    appliedPolicyVersion: '1',
  },
]

// Per-endpoint whitelist — LAB-PC-01 has Kingston; LAB-PC-02 does NOT.
export const mockWhitelist: WhitelistEntry[] = [
  {
    id: 'wl-01',
    endpointId: 'POC-LAB-PC-01',
    vendorId: '0951',
    productId: '1665',
    serial: 'C81F660E8BE8FFA14601FEF6',
    name: 'Kingston DataTraveler 2.0',
    hash: 'xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=',
    addedAt: '2026-09-28T14:00:00+07:00',
    addedBy: 'admin',
  },
]
