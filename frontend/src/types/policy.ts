// M4 owner — per-endpoint policy / whitelist UI types
// MVP: whitelist is per-endpoint, NOT global.

export interface WhitelistEntry {
  id: string
  endpointId: string
  vendorId: string
  productId: string
  serial?: string
  name?: string
  hash?: string
  addedAt: string
  addedBy?: string
}

export interface EndpointPolicy {
  policyVersion: string
  appliedPolicyVersion?: string
  allowedDevices: WhitelistEntry[]
}

export type WhitelistAction = 'allow' | 'revoke'
