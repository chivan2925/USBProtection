// M4 owner — endpoint frontend types
// Reflects the Endpoint model and per-endpoint whitelist contract.

export type EndpointStatus = 'online' | 'offline' | 'unknown'

export interface Endpoint {
  endpointId: string
  hostname: string
  name?: string
  operatingSystem?: string
  status: EndpointStatus
  currentUser?: string
  lastSeen?: string
  policyVersion?: string
  appliedPolicyVersion?: string
}
