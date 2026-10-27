import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

// M4 — Week 1 tests for EndpointsPage and EndpointDetailPage.

let vite
let Endpoints
let EndpointDetail
let EndpointWhitelistPanel
let getDemoSnapshot
let Dashboard
let Devices
let Settings
let Login
let AuthContext

before(async () => {
  vite = await createServer({ server: { middlewareMode: true, ws: false }, appType: 'custom' })
  ;({ Endpoints, EndpointDetail } = await vite.ssrLoadModule('/src/pages/Endpoints.tsx'))
  ;({ EndpointWhitelistPanel } = await vite.ssrLoadModule('/src/components/endpoint/EndpointWhitelistPanel.tsx'))
  ;({ getDemoSnapshot } = await vite.ssrLoadModule('/src/services/demo.ts'))
  ;({ Dashboard } = await vite.ssrLoadModule('/src/pages/Dashboard.tsx'))
  ;({ Devices } = await vite.ssrLoadModule('/src/pages/Devices.tsx'))
  ;({ Settings } = await vite.ssrLoadModule('/src/pages/Settings.tsx'))
  ;({ Login } = await vite.ssrLoadModule('/src/pages/Login.tsx'))
  ;({ AuthContext } = await vite.ssrLoadModule('/src/hooks/useAuth.ts'))
})
after(async () => { await vite?.close() })

const mockEndpoints = [
  { id: 'POC-LAB-PC-01', name: 'LAB-PC-01', hostname: 'lab-pc-01.local', status: 'online',  currentUser: 'usbdev',    policyVersion: '2', appliedPolicyVersion: '1' },
  { id: 'POC-LAB-PC-02', name: 'LAB-PC-02', hostname: 'lab-pc-02.local', status: 'offline', currentUser: 'student01', policyVersion: '1' },
]
const mockDevices = [
  { id: 'd1', name: 'Kingston DataTraveler 2.0', status: 'blocked', endpointId: 'POC-LAB-PC-01', detectedAt: '2026-09-28T14:00:00+07:00', serialNumber: 'C81F660E8BE8FFA14601FEF6' },
]
const mockWhitelist = [
  { id: 'wl-01', endpointId: 'POC-LAB-PC-01', vendorId: '0951', productId: '1665', serial: 'C81F660E8BE8FFA14601FEF6', name: 'Kingston DataTraveler 2.0', addedAt: '2026-09-28T14:00:00+07:00' },
]

const baseProps = {
  state: { status: 'ready', data: { endpoints: mockEndpoints, devices: mockDevices, events: [] } },
  refresh() {},
  refreshButton: null,
}

// --- EndpointsPage ---

test('endpoint rows render with hostname, status, and policy version', () => {
  const html = renderToStaticMarkup(createElement(Endpoints, baseProps))
  assert.ok(html.includes('LAB-PC-01'), 'should render LAB-PC-01')
  assert.ok(html.includes('LAB-PC-02'), 'should render LAB-PC-02')
  assert.ok(html.includes('lab-pc-01.local'), 'should show hostname')
  assert.ok(html.includes('Online'), 'should show online status')
})

test('endpoint list shows ID and distinct policy and applied versions, with a fallback when unapplied', () => {
  const html = renderToStaticMarkup(createElement(Endpoints, baseProps))
  assert.match(html, /<th scope="col">Endpoint ID<\/th>/)
  assert.match(html, /<th scope="col">Policy version<\/th><th scope="col">Last Applied Version<\/th>/)
  assert.match(html, /<td><span class="mono">POC-LAB-PC-01<\/span><\/td>/)
  assert.match(html, /<td><span class="mono">2<\/span><\/td><td><span class="mono">1<\/span><\/td>/)
  assert.match(html, /<td><span class="mono">1<\/span><\/td><td><span class="mono">—<\/span><\/td>/)
})

test('endpoint list has links to detail pages', () => {
  const html = renderToStaticMarkup(createElement(Endpoints, baseProps))
  assert.ok(html.includes('POC-LAB-PC-01'), 'should link to endpoint detail')
})

// --- EndpointDetailPage ---

test('endpoint detail does not show devices from other endpoints', () => {
  const html = renderToStaticMarkup(createElement(EndpointDetail, { ...baseProps, endpointId: 'POC-LAB-PC-02' }))
  assert.ok(!html.includes('Kingston DataTraveler 2.0'), 'LAB-PC-02 should not show LAB-PC-01 devices')
})

test('endpoint detail shows devices that belong to the endpoint', () => {
  const html = renderToStaticMarkup(createElement(EndpointDetail, { ...baseProps, endpointId: 'POC-LAB-PC-01' }))
  assert.ok(html.includes('Kingston DataTraveler 2.0'), 'LAB-PC-01 should show its own device')
})

// --- EndpointWhitelistPanel ---

test('whitelist panel renders "Allow on this endpoint" button', () => {
  const html = renderToStaticMarkup(createElement(EndpointWhitelistPanel, {
    endpointId: 'POC-LAB-PC-01',
    entries: mockWhitelist,
  }))
  assert.ok(html.includes('Allow on this endpoint'), 'must say Allow on this endpoint')
  assert.ok(!html.includes('Allow globally'), 'must NOT say Allow globally')
  assert.ok(!html.includes('Allow on all machines'), 'must NOT say Allow on all machines')
})

test('whitelist panel renders "Revoke on this endpoint" button per entry', () => {
  const html = renderToStaticMarkup(createElement(EndpointWhitelistPanel, {
    endpointId: 'POC-LAB-PC-01',
    entries: mockWhitelist,
  }))
  assert.ok(html.includes('Revoke on this endpoint'), 'must say Revoke on this endpoint')
})

test('whitelist panel shows empty state when endpoint has no whitelist entries', () => {
  const html = renderToStaticMarkup(createElement(EndpointWhitelistPanel, {
    endpointId: 'POC-LAB-PC-02',
    entries: mockWhitelist,
  }))
  assert.ok(html.includes('Chưa có thiết bị nào được cho phép'), 'should show empty state for endpoint with no entries')
  assert.ok(!html.includes('Kingston'), 'should not show LAB-PC-01 entries for LAB-PC-02')
})

test('real endpoint detail integrates whitelist and keeps rules isolated by endpoint', () => {
  const props = { ...baseProps, state: { status: 'ready', data: { ...baseProps.state.data, whitelist: mockWhitelist } } }
  const first = renderToStaticMarkup(createElement(EndpointDetail, { ...props, endpointId: 'POC-LAB-PC-01' }))
  const second = renderToStaticMarkup(createElement(EndpointDetail, { ...props, endpointId: 'POC-LAB-PC-02' }))
  assert.ok(first.includes('data-testid="endpoint-whitelist-panel"'))
  assert.ok(first.includes('Revoke on this endpoint'))
  assert.match(first, /disabled=""[^>]*aria-label="Revoke on this endpoint/)
  assert.match(first, /disabled=""[^>]*aria-label="Allow on this endpoint/)
  assert.ok(!second.includes('Revoke on this endpoint'))
  assert.ok(!second.includes('C81F660E8BE8FFA14601FEF6'))
  assert.ok(second.includes('Chưa có thiết bị nào được cho phép'))
})

test('missing or unavailable endpoint does not claim an empty whitelist', () => {
  for (const state of [baseProps.state, { status: 'unavailable' }, { status: 'loading' }, { status: 'error', error: 'test failure' }]) {
    const html = renderToStaticMarkup(createElement(EndpointDetail, { ...baseProps, state, endpointId: 'missing' }))
    assert.ok(!html.includes('data-testid="endpoint-whitelist-panel"'))
  }
})

test('demo snapshot maps endpoint IDs, preserves per-endpoint policy and returns independent fixtures', async () => {
  const first = await getDemoSnapshot(new AbortController().signal)
  assert.equal(first.endpoints[0].id, 'POC-LAB-PC-01')
  assert.deepEqual(first.endpoints.map(endpoint => endpoint.appliedPolicyVersion), ['2', '1'])
  assert.ok(first.endpoints.every(endpoint => endpoint.lastSeenAt))
  assert.deepEqual(first.devices.map(device => device.status), ['allowed', 'blocked'])
  assert.equal(first.devices[0].serialNumber, first.devices[1].serialNumber)
  assert.ok(first.events.every(event => first.endpoints.some(endpoint => endpoint.id === event.endpointId)))
  assert.equal(first.overview.recentEvents, first.events.length)
  first.whitelist[0].endpointId = 'changed'
  const second = await getDemoSnapshot(new AbortController().signal)
  assert.equal(second.whitelist[0].endpointId, 'POC-LAB-PC-01')
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(getDemoSnapshot(controller.signal), { name: 'AbortError' })
})

test('policy sync distinguishes offline pending, missing ACK and reported applied version', () => {
  const cases = [
    { status: 'offline', policyVersion: '3', appliedPolicyVersion: '1', message: 'Endpoint offline · Chờ Agent áp dụng chính sách' },
    { status: 'online', policyVersion: '3', appliedPolicyVersion: '1', message: 'Chờ Agent áp dụng chính sách' },
    { status: 'online', policyVersion: '3', appliedPolicyVersion: undefined, message: 'Chưa có dữ liệu ACK' },
    { status: 'online', policyVersion: '3', appliedPolicyVersion: '3', message: 'Agent đã ACK phiên bản này' },
  ]
  for (const example of cases) {
    const endpoint = { ...mockEndpoints[0], ...example }
    const state = { status: 'ready', data: { ...baseProps.state.data, endpoints: [endpoint] } }
    const html = renderToStaticMarkup(createElement(EndpointDetail, { ...baseProps, state, endpointId: endpoint.id }))
    assert.ok(html.includes(example.message), example.message)
    assert.equal(html.includes('Agent đã ACK phiên bản này'), example.policyVersion === example.appliedPolicyVersion)
    assert.ok(!html.includes('Hệ thống đang được bảo vệ'))
  }
})

test('management pages clear protected rows during loading, empty, unavailable and failure', () => {
  for (const state of [
    { status: 'loading' },
    { status: 'unavailable' },
    { status: 'error', error: 'Mất kết nối', statusCode: 403 },
    { status: 'ready', data: { endpoints: [], devices: [], events: [], whitelist: [] } },
  ]) {
    for (const page of [Endpoints, Dashboard, Devices]) {
      const html = renderToStaticMarkup(createElement(page, { ...baseProps, state, mode: 'devices' }))
      assert.ok(!html.includes('LAB-PC-01'))
      assert.ok(!html.includes('Kingston'))
      assert.ok(!html.includes('Hệ thống đang được bảo vệ'))
    }
  }
})

test('Settings explains unavailable service API instead of offering fake controls', () => {
  const html = renderToStaticMarkup(createElement(Settings))
  assert.ok(html.includes('API cài đặt dịch vụ chưa có'))
  assert.ok(html.includes('Chưa xác định'))
  assert.ok(!html.includes('<input'))
  assert.ok(!html.includes('<button'))
})

test('expired session shows a persistent login message; fresh login and demo do not', () => {
  function render(sessionExpired, demo = false) {
    return renderToStaticMarkup(createElement(AuthContext.Provider, {
      value: { user: null, loading: false, error: '', sessionExpired },
    }, createElement(Login, { demo })))
  }
  const previousWindow = globalThis.window
  globalThis.window = { location: { pathname: '/' } }
  try {
    assert.ok(render(true).includes('Phiên Admin đã hết hạn. Vui lòng đăng nhập lại.'))
    assert.ok(!render(false).includes('Phiên Admin đã hết hạn'))
    assert.ok(!render(true, true).includes('Phiên Admin đã hết hạn'))
  } finally {
    if (previousWindow === undefined) delete globalThis.window
    else globalThis.window = previousWindow
  }
})
