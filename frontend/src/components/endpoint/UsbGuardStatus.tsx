import { Badge } from '../Common'
import type { Endpoint } from '../../services/models'

const labels = { HEALTHY: 'Healthy', DEGRADED: 'Degraded', UNAVAILABLE: 'Unavailable', UNKNOWN: 'Unknown' }
const tones = { HEALTHY: 'green', DEGRADED: 'amber', UNAVAILABLE: 'red', UNKNOWN: 'neutral' }
export function UsbGuardStatus({ endpoint }: { endpoint?: Endpoint }) {
  const raw = endpoint?.usbGuardStatus ?? 'UNKNOWN'
  const current = endpoint?.status === 'online' && !endpoint?.usbGuardStale ? raw : 'UNKNOWN'
  const status = current === 'HEALTHY' && endpoint?.policyVersion !== endpoint?.appliedPolicyVersion ? 'DEGRADED' : current
  return <span title={endpoint?.usbGuardCheckedAt ? `Kiểm tra gần nhất: ${new Date(endpoint.usbGuardCheckedAt).toLocaleString('vi-VN')}` : 'Chưa có bằng chứng từ Agent'}>
    <Badge tone={tones[status]}>{labels[status]}</Badge>
    {(endpoint?.usbGuardStale || endpoint?.status === 'offline') && <small> · stale/offline</small>}
  </span>
}
