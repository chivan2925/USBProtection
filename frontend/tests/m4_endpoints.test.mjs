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

before(async () => {
  vite = await createServer({ server: { middlewareMode: true, ws: false }, appType: 'custom' })
  ;({ Endpoints, EndpointDetail } = await vite.ssrLoadModule('/src/pages/Endpoints.tsx'))
  ;({ EndpointWhitelistPanel } = await vite.ssrLoadModule('/src/components/endpoint/EndpointWhitelistPanel.tsx'))
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
