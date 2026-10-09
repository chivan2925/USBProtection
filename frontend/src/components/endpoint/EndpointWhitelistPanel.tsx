import type { ReactNode } from 'react'
import type { WhitelistEntry } from '../../types/policy'

// M4 owner — EndpointWhitelistPanel
// REQUIRED: per-endpoint whitelist UX.
// Labels must say "Allow on this endpoint" / "Revoke on this endpoint".
// Must NOT say "Allow globally" or "Allow on all machines".

interface Props {
  endpointId: string
  entries: WhitelistEntry[]
  onAllow?: (deviceId: string) => void
  onRevoke?: (entryId: string) => void
  disabled?: boolean
}

export function EndpointWhitelistPanel({
  endpointId,
  entries,
  onAllow,
  onRevoke,
  disabled = false,
}: Props): ReactNode {
  const ownEntries = entries.filter(e => e.endpointId === endpointId)

  return (
    <section className="panel" data-testid="endpoint-whitelist-panel">
      <div className="panel-heading">
        <div>
          <h2>Whitelist thiết bị</h2>
          <p>Danh sách USB được phép sử dụng trên máy tính này.</p>
        </div>
      </div>

      {ownEntries.length === 0 ? (
        <div className="empty-state">
          <p>Chưa có thiết bị nào được cho phép trên endpoint này.</p>
        </div>
      ) : (
        <div className="table-scroll">
          <table>
            <caption className="sr-only">Whitelist trên endpoint {endpointId}</caption>
            <thead>
              <tr>
                <th scope="col">Tên thiết bị</th>
                <th scope="col">VID:PID</th>
                <th scope="col">Serial</th>
                <th scope="col">Thêm lúc</th>
                <th scope="col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {ownEntries.map(entry => (
                <tr key={entry.id}>
                  <td>{entry.name ?? '—'}</td>
                  <td className="mono">{entry.vendorId}:{entry.productId}</td>
                  <td className="mono">{entry.serial ?? '—'}</td>
                  <td>{entry.addedAt}</td>
                  <td>
                    <button
                      id={`revoke-${entry.id}`}
                      className="button small"
                      disabled={disabled}
                      onClick={() => onRevoke?.(entry.id)}
                      aria-label={`Revoke on this endpoint: ${entry.name ?? entry.id}`}
                    >
                      Revoke on this endpoint
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="panel-bottom">
        <button
          id={`allow-on-${endpointId}`}
          className="button primary"
          disabled={disabled}
          onClick={() => onAllow?.(endpointId)}
          aria-label="Allow on this endpoint"
        >
          Allow on this endpoint
        </button>
      </div>
    </section>
  )
}
