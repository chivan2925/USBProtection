import type { DeviceAction, Snapshot } from './models'

export class BackendUnavailableError extends Error {
  constructor() {
    super('Backend hiện chưa cung cấp API cho chức năng này.')
    this.name = 'BackendUnavailableError'
  }
}

// Admin Web APIs are not integrated yet. admin-server has Agent controllers,
// which are not Admin monitoring/authentication/whitelist APIs.
// Replace this adapter once the Admin routes and schemas are agreed and tested.
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
