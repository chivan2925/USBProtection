import type { DeviceAction, Snapshot } from './models'

export class BackendUnavailableError extends Error {
  constructor() {
    super('Backend hiện chưa cung cấp API cho chức năng này.')
    this.name = 'BackendUnavailableError'
  }
}

// Audited against backend/src: there are no controllers, services or auth APIs.
// Do not invent URLs or fetch a non-existent endpoint. Replace this adapter only
// once real routes, response schemas and authentication have been implemented.
export const capabilities = Object.freeze({
  monitoring: false,
  authentication: false,
  allow: false,
  block: false,
  revoke: false,
  settings: false,
})

export interface ProtectionApi {
  getSnapshot(signal: AbortSignal): Promise<Snapshot>
  changeDevicePolicy(id: string, action: DeviceAction): Promise<void>
  login(username: string, password: string): Promise<void>
}

export const protectionApi: ProtectionApi = {
  async getSnapshot(signal) {
    signal.throwIfAborted()
    throw new BackendUnavailableError()
  },
  async changeDevicePolicy() {
    throw new BackendUnavailableError()
  },
  async login() {
    throw new BackendUnavailableError()
  },
}
