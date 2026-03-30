import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { authFetch } from '../api/authClient'
import { useNavigate } from 'react-router-dom'

interface SessionEvent {
  id: number
  username: string
  eventType: string
  ipAddress: string
  detail: string | null
  occurredAt: string
}

interface EventsResponse {
  events: SessionEvent[]
  totalElements: number
  totalPages: number
  page: number
}

const EVENT_COLORS: Record<string, string> = {
  LOGIN_SUCCESS:     '#1D9E75',
  TOTP_SUCCESS:      '#1D9E75',
  CHALLENGE_SUCCESS: '#1D9E75',
  TOTP_ENROLLED:     '#185FA5',
  PASSWORD_CHANGED:  '#185FA5',
  LOGOUT:            '#888780',
  SESSION_TIMEOUT:   '#888780',
  LOGIN_FAILURE:     '#E24B4A',
  TOTP_FAILURE:      '#E24B4A',
  CHALLENGE_FAILURE: '#E24B4A',
  ACCOUNT_LOCKED:    '#BA7517',
}

export function AdminPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [data, setData]     = useState<EventsResponse | null>(null)
  const [page, setPage]     = useState(0)
  const [filter, setFilter] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (user && user.role !== 'ADMIN') {
      navigate('/dashboard', { replace: true })
    }
  }, [user, navigate])

  useEffect(() => {
    loadEvents()
  }, [page, filter])

  const loadEvents = async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams({ page: String(page), size: '25' })
      if (filter) params.set('username', filter)
      const res = await authFetch(`/admin/events?${params}`)
      const json = await res.json()
      setData(json)
    } finally {
      setLoading(false)
    }
  }

  if (!user || user.role !== 'ADMIN') return null

  return (
    <div className="page">
      <header className="page-header">
        <div className="header-left">
          <span className="logo-icon">🔐</span>
          <span className="logo-text">VaultAuth</span>
          <span className="page-title">Audit log</span>
        </div>
        <div className="header-right">
          <button className="btn-ghost" onClick={() => navigate('/dashboard')}>Dashboard</button>
          <button className="btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </header>

      <main className="page-main">
        <div className="toolbar">
          <input
            type="text"
            placeholder="Filter by username…"
            value={filter}
            onChange={e => { setFilter(e.target.value); setPage(0) }}
            className="filter-input"
          />
          <span className="event-count">
            {data ? `${data.totalElements} events` : ''}
          </span>
        </div>

        <div className="table-wrapper">
          <table className="event-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Username</th>
                <th>Event</th>
                <th>IP</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={5} className="table-loading">Loading…</td></tr>
              )}
              {!loading && data?.events.map(ev => (
                <tr key={ev.id}>
                  <td className="td-time">
                    {new Date(ev.occurredAt).toLocaleString()}
                  </td>
                  <td><code>{ev.username}</code></td>
                  <td>
                    <span
                      className="event-badge"
                      style={{ background: (EVENT_COLORS[ev.eventType] ?? '#888') + '22',
                               color: EVENT_COLORS[ev.eventType] ?? '#888' }}
                    >
                      {ev.eventType.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td className="td-ip">{ev.ipAddress ?? '—'}</td>
                  <td className="td-detail">{ev.detail ?? '—'}</td>
                </tr>
              ))}
              {!loading && data?.events.length === 0 && (
                <tr><td colSpan={5} className="table-empty">No events found.</td></tr>
              )}
            </tbody>
          </table>
        </div>

        {data && data.totalPages > 1 && (
          <div className="pagination">
            <button
              className="btn-ghost"
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
            >
              Previous
            </button>
            <span>Page {page + 1} of {data.totalPages}</span>
            <button
              className="btn-ghost"
              disabled={page >= data.totalPages - 1}
              onClick={() => setPage(p => p + 1)}
            >
              Next
            </button>
          </div>
        )}
      </main>
    </div>
  )
}
