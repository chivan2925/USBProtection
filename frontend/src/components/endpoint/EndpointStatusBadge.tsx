import type { ReactNode } from 'react'

// M4 owner — EndpointStatusBadge
// Visual primitive for endpoint online/offline state.
// M5 may consume this in Event History but must not redefine it.

export type EndpointStatus = 'online' | 'offline' | 'unknown'

const statusConfig: Record<EndpointStatus, { label: string; tone: string }> = {
  online:  { label: 'Online',           tone: 'green'   },
  offline: { label: 'Offline',          tone: 'neutral' },
  unknown: { label: 'Chưa xác định',    tone: 'neutral' },
}

export function EndpointStatusBadge({ status }: { status: EndpointStatus }): ReactNode {
  const { label, tone } = statusConfig[status] ?? statusConfig.unknown
  return (
    <span className={`badge ${tone}`} data-testid="endpoint-status-badge">
      <span className="status-dot" />
      {label}
    </span>
  )
}
