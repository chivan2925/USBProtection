// Test-only HTTP server. This file is never imported by the application.
import { createServer } from 'node:http'

export async function startAdminFixture() {
  const state = {
    sessions: new Set(), csrf: 'fixture-csrf-0', sequence: 0, requests: [],
    failure: 0, failMutation: 0, brokenPage: false, empty: false,
    endpoints: [
      { endpointId: 'A', hostname: 'fixture-ubuntu-a', ipAddress: '192.0.2.10', status: 'ONLINE', lastSeen: '2026-10-14T10:00:00Z', policyVersion: 1, appliedPolicyVersion: 0 },
      { endpointId: 'B', hostname: 'fixture-ubuntu-b', status: 'OFFLINE', lastSeen: '2026-10-14T09:00:00Z', policyVersion: 1, appliedPolicyVersion: 1 },
    ],
    events: [
      { id: 1, usbDeviceId: 1, endpointId: 'A', hostname: 'fixture-ubuntu-a', linuxUsername: 'fixture-linux-user', linuxUid: 1000, sessionId: '21', device: { name: 'Fixture USB', vendorId: '0951', productId: '1666', hash: 'fixture-hash-a' }, eventType: 'CONNECTED', decision: 'BLOCKED', occurredAt: new Date().toISOString() },
      { id: 2, usbDeviceId: 1, endpointId: 'B', hostname: 'fixture-ubuntu-b', linuxUsername: 'UNKNOWN', device: { name: 'Fixture USB', hash: 'fixture-hash-a' }, eventType: 'CONNECTED', decision: 'ALLOWED', occurredAt: '2026-10-13T10:00:00Z' },
    ],
    whitelist: { A: [], B: [{ id: 10, endpointId: 'B', fingerprintType: 'HASH', fingerprintValue: 'fixture-hash-a', createdAt: '2026-10-13T10:00:00Z', enabled: true }] },
  }
  const server = createServer(async (req, res) => {
    function send(status, data) {
      res.writeHead(status, { 'Content-Type': 'application/json' })
      res.end(status === 204 ? undefined : JSON.stringify(data))
    }
    const url = new URL(req.url, 'http://fixture.test')
    const path = url.pathname
    state.requests.push({ method: req.method, path, query: url.search, csrf: req.headers['x-csrf-token'] })
    let body = ''
    for await (const chunk of req) body += chunk
    let payload = {}
    try { if (body) payload = JSON.parse(body) } catch { send(400, {}); return }
    const cookie = (req.headers.cookie ?? '').split('; ').find(item => item.startsWith('fixture-session='))
    const session = cookie?.split('=')[1]
    const authenticated = state.sessions.has(session)

    if (path === '/api/auth/csrf') {
      send(200, { token: state.csrf, headerName: 'X-CSRF-TOKEN' })
      return
    }
    if (path === '/api/auth/me') {
      send(authenticated ? 200 : 401, { username: authenticated ? 'fixture-admin' : undefined })
      return
    }
    if (req.method !== 'GET' && req.headers['x-csrf-token'] !== state.csrf) { send(403, {}); return }
    if (path === '/api/auth/login') {
      if (payload.username !== 'fixture-admin' || payload.password !== 'fixture-only') { send(401, {}); return }
      state.sequence++
      const newSession = String(state.sequence)
      state.sessions.add(newSession)
      state.csrf = `fixture-csrf-${state.sequence}`
      res.setHeader('Set-Cookie', `fixture-session=${newSession}; HttpOnly; Path=/; SameSite=Lax`)
      send(204)
      return
    }
    if (path === '/api/auth/logout') {
      if (!authenticated) { send(401, {}); return }
      state.sessions.delete(session)
      res.setHeader('Set-Cookie', 'fixture-session=; HttpOnly; Path=/; Max-Age=0; SameSite=Lax')
      send(204)
      return
    }
    if (!authenticated) { send(401, {}); return }
    if (state.failure) { send(state.failure, {}); return }
    if (req.method !== 'GET' && state.failMutation) { send(state.failMutation, {}); return }

    if (path === '/api/admin/endpoints') {
      send(200, state.empty ? [] : state.endpoints)
      return
    }
    if (path === '/api/admin/events') {
      let events = state.empty ? [] : state.events
      for (const field of ['endpointId', 'linuxUsername', 'decision', 'eventType']) {
        const value = url.searchParams.get(field)
        if (value) events = events.filter(event => event[field] === value)
      }
      const deviceId = url.searchParams.get('deviceId')
      if (deviceId) events = events.filter(event => String(event.usbDeviceId) === deviceId)
      const from = url.searchParams.get('from')
      const to = url.searchParams.get('to')
      if (from) events = events.filter(event => Date.parse(event.occurredAt) >= Date.parse(from))
      if (to) events = events.filter(event => Date.parse(event.occurredAt) <= Date.parse(to))
      const page = Number(url.searchParams.get('page') ?? 0)
      const requestedSize = Number(url.searchParams.get('size') ?? 20)
      // Force snapshot reads to traverse pages, while live Events uses its requested size.
      const size = requestedSize === 100 ? 1 : requestedSize
      send(200, { content: events.slice(page * size, (page + 1) * size), page: state.brokenPage ? page + 1 : page, size, totalPages: Math.ceil(events.length / size), totalElements: events.length })
      return
    }
    const match = path.match(/^\/api\/admin\/endpoints\/([^/]+)(?:\/whitelist(?:\/([^/]+))?)?$/)
    if (!match) { send(404, {}); return }
    const endpointId = decodeURIComponent(match[1])
    const endpoint = state.endpoints.find(item => item.endpointId === endpointId)
    if (!endpoint) { send(404, {}); return }
    if (!path.includes('/whitelist')) { send(200, endpoint); return }
    const entries = state.whitelist[endpointId]
    if (req.method === 'GET') { send(200, entries); return }
    if (req.method === 'POST') {
      const device = state.events.find(event => event.usbDeviceId === payload.usbDeviceId)?.device
      if (!device) { send(404, {}); return }
      const existing = entries.find(entry => entry.fingerprintValue === device.hash)
      if (existing) { send(200, existing); return }
      entries.push({ id: ++state.sequence + 10, endpointId, usbDeviceId: payload.usbDeviceId, fingerprintType: 'HASH', fingerprintValue: device.hash, enabled: true })
      endpoint.policyVersion++
      send(204)
      return
    }
    if (req.method === 'DELETE') {
      const index = entries.findIndex(entry => String(entry.id) === decodeURIComponent(match[2]))
      if (index < 0) { send(404, {}); return }
      entries.splice(index, 1)
      endpoint.policyVersion++
      send(204)
      return
    }
    send(405, {})
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  return {
    state, origin: `http://127.0.0.1:${server.address().port}`,
    close: () => new Promise((resolve, reject) => { server.close(error => error ? reject(error) : resolve()); server.closeAllConnections() }),
  }
}

// Node fetch has no cookie jar; emulate only this fixture's HttpOnly session cookie.
export function useFixtureFetch(origin) {
  const original = globalThis.fetch
  let cookie = ''
  globalThis.fetch = async (path, options = {}) => {
    const headers = { ...options.headers }
    if (cookie) headers.Cookie = cookie
    const response = await original(`${origin}${path}`, { ...options, headers })
    const setCookie = response.headers.get('set-cookie')
    if (setCookie) cookie = setCookie.split(';')[0]
    return response
  }
  return () => { globalThis.fetch = original }
}
