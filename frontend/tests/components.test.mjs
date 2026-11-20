import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let vite
let Devices
let Events
let EndpointDetail
let DataTable
let PageHeading
let Dashboard
let AdminLayout
let LiveEvents
let EndpointDetailModal
before(async () => {
  vite = await createServer({ server: { middlewareMode: true, ws: false }, appType: 'custom' })
  ;({ Devices } = await vite.ssrLoadModule('/src/pages/Devices.tsx'))
  ;({ Events } = await vite.ssrLoadModule('/src/pages/Events.tsx'))
  ;({ EndpointDetail } = await vite.ssrLoadModule('/src/pages/Endpoints.tsx'))
  ;({ DataTable } = await vite.ssrLoadModule('/src/components/DataTable.tsx'))
  ;({ PageHeading } = await vite.ssrLoadModule('/src/components/Common.tsx'))
  ;({ Dashboard } = await vite.ssrLoadModule('/src/pages/Dashboard.tsx'))
  ;({ AdminLayout } = await vite.ssrLoadModule('/src/layouts/AdminLayout.tsx'))
  ;({ LiveEvents } = await vite.ssrLoadModule('/src/pages/Events.tsx'))
  ;({ EndpointDetailModal } = await vite.ssrLoadModule('/src/components/endpoint/EndpointDetailModal.tsx'))
})
after(async () => { await vite?.close() })

test('page headings omit decorative copy without leaving empty elements', () => {
  const render = extra => renderToStaticMarkup(createElement(PageHeading, { title: 'Test heading', ...extra }, createElement('button', null, 'Refresh')))
  for (const extra of [{}, { eyebrow: '', description: '' }]) {
    const html = render(extra)
    assert.ok(html.includes('<h1>Test heading</h1>'))
    assert.ok(html.includes('<button>Refresh</button>'))
    assert.ok(!html.includes('class="eyebrow"'))
    assert.ok(!html.includes('<p>'))
  }
  const html = render({ eyebrow: '404', description: 'Page not found.' })
  assert.ok(html.includes('class="eyebrow">404</div>'))
  assert.ok(html.includes('<p>Page not found.</p>'))
})

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
test('dashboard omits removed controls while retaining overview and error recovery', () => {
  const html = renderToStaticMarkup(createElement(Dashboard, { ...props, refreshButton: createElement('button', null, 'Refresh dữ liệu') }))
  for (const text of ['Truy cập nhanh', 'Người dùng gần đây', 'Refresh dữ liệu']) assert.ok(!html.includes(text), text)
  for (const text of ['Tổng quan bảo vệ', 'Hoạt động USB gần nhất', 'Trạng thái endpoints']) assert.ok(html.includes(text), text)
  const failed = renderToStaticMarkup(createElement(Dashboard, { ...props, state: { status: 'error', error: 'Offline' } }))
  assert.ok(failed.includes('Thử lại'))
})
test('sidebar no longer exposes Settings route metadata or links', () => {
  for (const demo of [false, true]) {
    const html = renderToStaticMarkup(createElement(AdminLayout, { route: '/settings', demo }, 'Page content'))
    assert.ok(!html.includes('href="#/settings"'))
    assert.ok(!html.includes('href="#/blocked"'))
    assert.ok(!html.includes('Blocked Devices'))
    assert.ok(html.includes('<strong>Không tìm thấy</strong>'))
    assert.ok(html.includes('href="#/endpoints"'))
    assert.ok(html.includes('Page content'))
    if (demo) assert.ok(html.includes('href="/#/login"'))
    else assert.ok(!/demo|mock/i.test(html))
  }
})
test('refresh buttons, filter reset and update timestamps are removed while modal close remains', async () => {
  for (const file of ['App.tsx', 'pages/Events.tsx', 'components/endpoint/EndpointDetailModal.tsx']) {
    const source = await readFile(new URL(`../src/${file}`, import.meta.url), 'utf8')
    assert.ok(!source.includes('Refresh dữ liệu'), file)
    assert.ok(!source.includes('Cập nhật:'), file)
    assert.ok(!source.includes('updatedAt'), file)
  }
  const events = renderToStaticMarkup(createElement(LiveEvents))
  assert.ok(!events.includes('Refresh dữ liệu'))
  assert.ok(!events.includes('Cập nhật:'))
  assert.ok(!events.includes('aria-label="Đặt lại tất cả bộ lọc"'))
  const modal = renderToStaticMarkup(createElement(EndpointDetailModal, { endpointId: 'a', demo: true, onClose() {}, onPolicyChange() {} }))
  assert.ok(!modal.includes('Refresh dữ liệu'))
  assert.ok(!modal.includes('Cập nhật:'))
  assert.ok(modal.includes('aria-label="Đóng chi tiết endpoint"'))
})
test('demo whitelist uses rules while Devices retains all observed states', () => {
  const whitelist = renderToStaticMarkup(createElement(Devices, { ...props, mode: 'whitelist', demo: true }))
  const inventory = renderToStaticMarkup(createElement(Devices, { ...props, mode: 'devices', demo: true }))
  assert.ok(whitelist.includes('WHITELIST_RULE_DEVICE'))
  assert.ok(!whitelist.includes('ALLOWED_TEST_DEVICE'))
  assert.ok(!whitelist.includes('BLOCKED_TEST_DEVICE'))
  assert.ok(!whitelist.includes('DETECTED_TEST_DEVICE'))
  for (const device of devices) assert.ok(inventory.includes(device.name))
  assert.ok(inventory.includes('<option value="detected">Chưa xác định</option>'))
  assert.ok(!inventory.includes('<option value="detected">Đã phát hiện</option>'))
  assert.ok(whitelist.includes('href="#/endpoints/a"'))
})
test('demo endpoint detail does not display devices belonging to another endpoint', () => {
  const html = renderToStaticMarkup(createElement(EndpointDetail, { ...props, endpointId: 'a', demo: true }))
  assert.ok(html.includes('ALLOWED_TEST_DEVICE'))
  assert.ok(html.includes('DETECTED_TEST_DEVICE'))
  assert.ok(!html.includes('BLOCKED_TEST_DEVICE'))
})
test('production pages never treat snapshot audit preview as a complete device inventory', () => {
  const inventory = renderToStaticMarkup(createElement(Devices, { ...props, mode: 'devices' }))
  const endpoint = renderToStaticMarkup(createElement(EndpointDetail, { ...props, endpointId: 'a' }))
  assert.ok(!inventory.includes('BLOCKED_TEST_DEVICE'))
  assert.ok(!endpoint.includes('ALLOWED_TEST_DEVICE'))
  assert.ok(!endpoint.includes('DETECTED_TEST_DEVICE'))
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

test('server pagination displays ten records per page and global totals', () => {
  const rows = Array.from({ length: 10 }, (_, index) => ({ id: String(index + 11) }))
  const html = renderToStaticMarkup(createElement(DataTable, { rows, columns: [{ key: 'id', label: 'ID', render: row => row.id }], label: 'server page', pagination: { page: 1, size: 10, totalElements: 25, totalPages: 3, onPageChange() {} } }))
  assert.equal((html.match(/<tr>/g) ?? []).length, 11)
  assert.ok(html.includes('11–20 trên 25 bản ghi'))
  assert.ok(html.includes('Trang 2 / 3'))
  assert.ok(html.includes('<td>20</td>'))
})
