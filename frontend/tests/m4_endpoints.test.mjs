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
let Dashboard

before(async () => {
  vite = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  ;({ Endpoints, EndpointDetail } = await vite.ssrLoadModule('/src/pages/Endpoints.tsx'))
  ;({ EndpointWhitelistPanel } = await vite.ssrLoadModule('/src/components/endpoint/EndpointWhitelistPanel.tsx'))
  ;({ Dashboard } = await vite.ssrLoadModule('/src/pages/Dashboard.tsx'))
})
after(async () => { await vite?.close() })

const mockEndpoints = [
  { id: 'POC-LAB-PC-01', name: 'LAB-PC-01', hostname: 'lab-pc-01.local', status: 'online',  currentUser: 'usbdev',    policyVersion: '2' },
  { id: 'POC-LAB-PC-02', name: 'LAB-PC-02', hostname: 'lab-pc-02.local', status: 'offline', currentUser: 'student01', policyVersion: '1' },
]
const mockDevices = [
  { id: 'd1', name: 'Kingston DataTraveler 2.0', status: 'blocked', endpointId: 'POC-LAB-PC-01', detectedAt: '2026-09-28T14:00:00+07:00', serialNumber: 'C81F660E8BE8FFA14601FEF6' },
]
const mockWhitelist = [
  { id: 'wl-01', endpointId: 'POC-LAB-PC-01', vendorId: '0951', productId: '1665', serial: 'C81F660E8BE8FFA14601FEF6', name: 'Kingston DataTraveler 2.0', addedAt: '2026-09-28T14:00:00+07:00' },
]

const baseProps = {
  state: { status: 'ready', data: { endpoints: mockEndpoints, devices: mockDevices, events: [], whitelist: mockWhitelist } },
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

test('endpoints page shows policy version column', () => {
  const html = renderToStaticMarkup(createElement(Endpoints, baseProps))
  assert.ok(html.includes('Policy version'), 'should have policy version column header')
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

test('active endpoint detail integrates its whitelist and disables unimplemented actions', () => {
  const own = renderToStaticMarkup(createElement(EndpointDetail, { ...baseProps, endpointId: 'POC-LAB-PC-01' }))
  const other = renderToStaticMarkup(createElement(EndpointDetail, { ...baseProps, endpointId: 'POC-LAB-PC-02' }))
  assert.ok(own.includes('data-testid="endpoint-whitelist-panel"'))
  assert.match(own, /id="revoke-wl-01"[^>]*disabled=""/)
  assert.match(own, /id="allow-on-POC-LAB-PC-01"[^>]*disabled=""/)
  assert.ok(other.includes('Chưa có thiết bị nào được cho phép'))
  assert.ok(!other.includes('revoke-wl-01'))
  assert.ok(!own.includes('href="#/whitelist"'))
})

test('unavailable backend offers labelled dashboard and endpoint preview with working detail IDs', () => {
  const props = { ...baseProps, state: { status: 'unavailable' } }
  const dashboard = renderToStaticMarkup(createElement(Dashboard, props))
  const endpoints = renderToStaticMarkup(createElement(Endpoints, props))
  const detail = renderToStaticMarkup(createElement(EndpointDetail, { ...props, endpointId: 'POC-LAB-PC-01' }))
  for (const html of [dashboard, endpoints, detail]) assert.ok(html.includes('Dữ liệu mock'))
  assert.ok(dashboard.includes('Chưa xác định trạng thái'))
  assert.ok(!dashboard.includes('Hệ thống đang được bảo vệ'))
  assert.ok(endpoints.includes('href="#/endpoints/POC-LAB-PC-01"'))
  assert.ok(detail.includes('Kingston'))
  assert.ok(detail.includes('student01'))
})

test('ready empty and failed endpoint responses never fall back to mock data', () => {
  for (const state of [
    { status: 'ready', data: { endpoints: [], devices: [], events: [], whitelist: [] } },
    { status: 'error', error: 'Server failed' },
    { status: 'loading' },
  ]) {
    const html = renderToStaticMarkup(createElement(Endpoints, { ...baseProps, state }))
    assert.ok(!html.includes('LAB-PC-01'))
    assert.ok(!html.includes('Dữ liệu mock'))
  }
  const missing = renderToStaticMarkup(createElement(EndpointDetail, { ...baseProps, state: { status: 'unavailable' }, endpointId: 'missing' }))
  assert.ok(missing.includes('Endpoint không tồn tại'))
  assert.ok(!missing.includes('data-testid="endpoint-whitelist-panel"'))
})
