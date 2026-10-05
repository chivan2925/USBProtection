import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let vite
let Devices
let Events
let EndpointDetail
let DataTable
before(async () => {
  vite = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  ;({ Devices } = await vite.ssrLoadModule('/src/pages/Devices.tsx'))
  ;({ Events } = await vite.ssrLoadModule('/src/pages/Events.tsx'))
  ;({ EndpointDetail } = await vite.ssrLoadModule('/src/pages/Endpoints.tsx'))
  ;({ DataTable } = await vite.ssrLoadModule('/src/components/DataTable.tsx'))
})
after(async () => { await vite?.close() })

// Fixtures are test-only; the shipped application never imports these records.
const devices = [
  { id: '1', name: 'ALLOWED_TEST_DEVICE', status: 'allowed', endpointId: 'a' },
  { id: '2', name: 'BLOCKED_TEST_DEVICE', status: 'blocked', endpointId: 'b' },
  { id: '3', name: 'DETECTED_TEST_DEVICE', status: 'detected', endpointId: 'a' },
]
const props = {
  state: { status: 'ready', data: { devices, endpoints: [{ id: 'a', name: 'TEST_ENDPOINT', status: 'online' }], events: [] } },
  refresh() {}, refreshButton: null,
}
test('whitelist and blacklist isolate device policy states', () => {
  const whitelist = renderToStaticMarkup(createElement(Devices, { ...props, mode: 'whitelist' }))
  const blocked = renderToStaticMarkup(createElement(Devices, { ...props, mode: 'blocked' }))
  assert.ok(whitelist.includes('ALLOWED_TEST_DEVICE'))
  assert.ok(!whitelist.includes('BLOCKED_TEST_DEVICE'))
  assert.ok(!whitelist.includes('DETECTED_TEST_DEVICE'))
  assert.ok(blocked.includes('BLOCKED_TEST_DEVICE'))
  assert.ok(!blocked.includes('ALLOWED_TEST_DEVICE'))
  assert.ok(!blocked.includes('DETECTED_TEST_DEVICE'))
  assert.match(whitelist, /disabled=""[^>]*>Xóa khỏi whitelist/)
})
test('endpoint detail does not display devices belonging to another endpoint', () => {
  const html = renderToStaticMarkup(createElement(EndpointDetail, { ...props, endpointId: 'a' }))
  assert.ok(html.includes('ALLOWED_TEST_DEVICE'))
  assert.ok(html.includes('DETECTED_TEST_DEVICE'))
  assert.ok(!html.includes('BLOCKED_TEST_DEVICE'))
})
test('event page shows only the newest ten records on the first page', () => {
  const events = Array.from({ length: 12 }, (_, index) => ({ id: String(index), type: 'connected', timestamp: new Date(Date.UTC(2026, 8, 26, index)).toISOString(), deviceName: `EVENT_TEST_${String(index).padStart(2, '0')}` }))
  const html = renderToStaticMarkup(createElement(Events, { ...props, state: { status: 'ready', data: { ...props.state.data, events } } }))
  assert.ok(html.includes('EVENT_TEST_11'))
  assert.ok(html.includes('EVENT_TEST_02'))
  assert.ok(!html.includes('EVENT_TEST_01'))
  assert.ok(html.indexOf('EVENT_TEST_11') < html.indexOf('EVENT_TEST_02'))
})
test('table distinguishes empty, filtered, loading, unavailable and API failure', () => {
  const render = extra => renderToStaticMarkup(createElement(DataTable, { rows: [], columns: [], label: 'test', ...extra }))
  assert.ok(render({}).includes('Chưa có dữ liệu'))
  assert.ok(render({ filtered: true }).includes('Không tìm thấy kết quả'))
  assert.ok(render({ unavailable: true }).includes('Chưa có nguồn dữ liệu'))
  assert.ok(render({ failed: true }).includes('Dữ liệu tạm thời không khả dụng'))
  assert.ok(render({ loading: true }).includes('Đang tải dữ liệu'))
})
test('device names are rendered as text, never injected as HTML', () => {
  const html = renderToStaticMarkup(createElement(DataTable, { rows: [{ id: 'unsafe', name: '<img src=x onerror=alert(1)>' }], columns: [{ key: 'name', label: 'Name', render: row => row.name }], label: 'test' }))
  assert.ok(html.includes('&lt;img'))
  assert.ok(!html.includes('<img'))
})

test('event mock renders required columns, users, devices and decisions', () => {
  const html = renderToStaticMarkup(createElement(Events, { ...props, state: { status: 'unavailable' } }))
  for (const text of ['Timestamp', 'Endpoint', 'Linux User', 'USB', 'Event', 'Decision', 'LAB-PC-01', 'student01', 'Kingston', 'LAB-PC-02', 'employee07', 'SanDisk', 'ALLOW', 'BLOCK', 'Dữ liệu mock']) assert.ok(html.includes(text), text)
  for (const label of ['Endpoint', 'Linux Username', 'Device', 'Decision', 'Date']) assert.ok(html.includes(`aria-label="${label}"`), label)
})
test('ready empty event response does not display mock records', () => {
  const html = renderToStaticMarkup(createElement(Events, props))
  assert.ok(!html.includes('student01'))
  assert.ok(html.includes('Chưa có dữ liệu'))
})
