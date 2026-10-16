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
  vite = await createServer({ server: { middlewareMode: true, ws: false }, appType: 'custom' })
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
  state: { status: 'ready', data: { devices, whitelist: [{ id: 'rule-a', endpointId: 'a', name: 'WHITELIST_RULE_DEVICE', vendorId: '0951', productId: '1666', addedAt: '2026-10-14T10:00:00Z' }], endpoints: [{ id: 'a', name: 'TEST_ENDPOINT', status: 'online' }], events: [] } },
  refresh() {}, refreshButton: null,
}
test('whitelist uses rules rather than event ALLOWED states; blocked list uses observations', () => {
  const whitelist = renderToStaticMarkup(createElement(Devices, { ...props, mode: 'whitelist' }))
  const blocked = renderToStaticMarkup(createElement(Devices, { ...props, mode: 'blocked' }))
  assert.ok(whitelist.includes('WHITELIST_RULE_DEVICE'))
  assert.ok(!whitelist.includes('ALLOWED_TEST_DEVICE'))
  assert.ok(!whitelist.includes('BLOCKED_TEST_DEVICE'))
  assert.ok(!whitelist.includes('DETECTED_TEST_DEVICE'))
  assert.ok(blocked.includes('BLOCKED_TEST_DEVICE'))
  assert.ok(!blocked.includes('ALLOWED_TEST_DEVICE'))
  assert.ok(!blocked.includes('DETECTED_TEST_DEVICE'))
  assert.ok(whitelist.includes('href="#/endpoints/a"'))
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

test('unavailable events preserve columns and filters without mock records', () => {
  const html = renderToStaticMarkup(createElement(Events, { ...props, state: { status: 'unavailable' } }))
  for (const text of ['Timestamp', 'Endpoint', 'Linux User', 'USB', 'Event', 'Decision', 'Chưa có nguồn dữ liệu']) assert.ok(html.includes(text), text)
  for (const text of ['student01', 'employee07', 'Kingston', 'SanDisk', 'Dữ liệu mock']) assert.ok(!html.includes(text), text)
  for (const label of ['Endpoint', 'Linux Username', 'Device', 'Decision', 'Event Type', 'From Date', 'To Date']) assert.ok(html.includes(`aria-label="${label}"`), label)
})
test('ready empty event response does not display mock records', () => {
  const html = renderToStaticMarkup(createElement(Events, props))
  assert.ok(!html.includes('student01'))
  assert.ok(html.includes('Chưa có dữ liệu'))
})

test('server pagination displays the full received page and global totals', () => {
  const rows = Array.from({ length: 20 }, (_, index) => ({ id: String(index + 21) }))
  const html = renderToStaticMarkup(createElement(DataTable, { rows, columns: [{ key: 'id', label: 'ID', render: row => row.id }], label: 'server page', pagination: { page: 1, size: 20, totalElements: 42, totalPages: 3, onPageChange() {} } }))
  assert.equal((html.match(/<tr>/g) ?? []).length, 21)
  assert.ok(html.includes('21–40 trên 42 bản ghi'))
  assert.ok(html.includes('Trang 2 / 3'))
  assert.ok(html.includes('<td>40</td>'))
})
