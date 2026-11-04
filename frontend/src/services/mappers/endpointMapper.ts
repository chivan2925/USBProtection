import type { Endpoint } from '../models.ts'
import { readObject, readText, readVersion } from '../http.ts'

export function mapEndpoint(value: unknown): Endpoint {
  const data = readObject(value)
  const hostname = readText(data.hostname, true)!
  const lastSeenAt = readText(data.lastSeen)
  const policyVersion = readVersion(data.policyVersion)
  const appliedPolicyVersion = readVersion(data.appliedPolicyVersion)
  const health = data.usbGuardStatus
  if (health != null && !['HEALTHY', 'DEGRADED', 'UNAVAILABLE', 'UNKNOWN'].includes(String(health)))
    throw new Error('API trả trạng thái USBGuard không hợp lệ.')
  const checkedAt = readText(data.usbGuardCheckedAt)
  if (checkedAt !== undefined && !Number.isFinite(Date.parse(checkedAt)))
    throw new Error('API trả thời gian kiểm tra USBGuard không hợp lệ.')
  if (data.usbGuardStale != null && typeof data.usbGuardStale !== 'boolean')
    throw new Error('API trả dấu hiệu stale USBGuard không hợp lệ.')
  if (lastSeenAt !== undefined && !Number.isFinite(Date.parse(lastSeenAt))) {
    throw new Error('API trả lastSeen không hợp lệ.')
  }
  if (policyVersion !== undefined && appliedPolicyVersion !== undefined &&
      Number(appliedPolicyVersion) > Number(policyVersion)) {
    throw new Error('API trả applied policy version lớn hơn desired policy version.')
  }
  let status: Endpoint['status'] = 'unknown'
  if (data.status === 'ONLINE') status = 'online'
  if (data.status === 'OFFLINE') status = 'offline'
  return {
    id: readText(data.endpointId, true)!, name: readText(data.name) ?? hostname,
    hostname, ipAddress: readText(data.ipAddress), operatingSystem: readText(data.operatingSystem),
    status, currentUser: readText(data.currentUser), lastSeenAt,
    policyVersion, appliedPolicyVersion,
    usbGuardStatus: health == null ? 'UNKNOWN' : health as Endpoint['usbGuardStatus'],
    usbGuardCheckedAt: checkedAt, usbGuardStale: data.usbGuardStale === true,
  }
}
