import type { WhitelistEntry } from '../../types/policy.ts'
import { readId, readObject, readText } from '../http.ts'

export function mapWhitelistEntry(value: unknown, endpointId: string): WhitelistEntry {
  const data = readObject(value)
  if (data.endpointId != null && data.endpointId !== endpointId) {
    throw new Error('Server trả whitelist của endpoint khác. Đã dừng tải dữ liệu.')
  }
  if (data.enabled != null && typeof data.enabled !== 'boolean') {
    throw new Error('API trả trạng thái whitelist không hợp lệ.')
  }
  // Contract hiện tại chỉ hỗ trợ HASH; không suy ra quyền từ serial hoặc tên USB.
  if (data.fingerprintType !== 'HASH') {
    throw new Error('API trả loại fingerprint chưa được hỗ trợ; cần HASH.')
  }
  return {
    id: readId(data.id), endpointId,
    vendorId: readText(data.vendorId) ?? '', productId: readText(data.productId) ?? '',
    serial: readText(data.serial), name: readText(data.name), hash: readText(data.hash),
    fingerprintType: 'HASH', fingerprintValue: readText(data.fingerprintValue, true)!,
    addedAt: readText(data.createdAt), addedBy: readText(data.addedBy),
  }
}
