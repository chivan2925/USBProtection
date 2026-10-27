import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

// M5 — Event History regression and Week 2 resource-state coverage.

let vite
let Events
let EventTable
let EventFilters

before(async () => {
  vite = await createServer({ server: { middlewareMode: true, ws: false }, appType: 'custom' })
  ;({ Events } = await vite.ssrLoadModule('/src/pages/Events.tsx'))
  ;({ EventTable } = await vite.ssrLoadModule('/src/components/event/EventTable.tsx'))
  ;({ EventFilters } = await vite.ssrLoadModule('/src/components/event/EventFilters.tsx'))
})

test('Events loading, unavailable and error states never display fixture rows', () => {
  for (const [state, expected] of [[{ status: 'loading' }, 'Đang tải dữ liệu'], [{ status: 'unavailable' }, 'Chưa có nguồn dữ liệu'], [{ status: 'error', error: 'TEST_NETWORK_FAILURE' }, 'TEST_NETWORK_FAILURE']]) {
    const html = renderToStaticMarkup(createElement(Events, { state, refresh() {}, refreshButton: null }))
    assert.ok(html.includes(expected), expected)
    for (const value of ['student01', 'employee07', 'Kingston', 'SanDisk', 'Dữ liệu mock', 'Nhật ký hệ thống']) assert.ok(!html.includes(value), value)
    assert.ok(!html.includes('<tbody><tr>'))
  }
})

test('Events ready empty data stays empty, and UNKNOWN is never displayed as BLOCK', () => {
  const html = renderToStaticMarkup(createElement(Events, { state: { status: 'ready', data: { events: [] } }, refresh() {}, refreshButton: null }))
  assert.ok(html.includes('Chưa có dữ liệu'))
  assert.ok(!html.includes('student01'))
  const unknown = renderToStaticMarkup(createElement(EventTable, { events: [{ ...mockEvents[0], username: 'UNKNOWN', decision: 'unknown' }] }))
  assert.ok(unknown.includes('badge amber'))
  assert.ok(unknown.includes('UNKNOWN'))
  assert.ok(!unknown.includes('>BLOCK<'))
})
after(async () => { await vite?.close() })

const mockEvents = [
  {
    id: 'evt-001',
    timestamp: '2026-09-28T14:10:16+07:00',
    endpointId: 'POC-LAB-PC-01',
    endpointName: 'lab-pc-01.local',
    username: 'student01',
    deviceName: 'Kingston DataTraveler 2.0',
    type: 'connected',
    decision: 'blocked',
    message: 'Unknown USB Mass Storage blocked',
  },
  {
    id: 'evt-002',
    timestamp: '2026-09-28T10:05:00+07:00',
    endpointId: 'POC-LAB-PC-02',
    endpointName: 'lab-pc-02.local',
    username: 'employee07',
    deviceName: 'SanDisk Ultra',
    type: 'connected',
    decision: 'allowed',
    message: 'Whitelisted USB Mass Storage allowed',
  },
]

// --- EventTable component ---

test('EventTable renders required columns: Endpoint, Linux User, USB, Event, Decision', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('Endpoint'),    'should have Endpoint column')
  assert.ok(html.includes('Linux User'),  'should have Linux User column')
  assert.ok(html.includes('USB'),         'should have USB column')
  assert.ok(html.includes('Event'),     'should have Event column')
  assert.ok(html.includes('Decision'),    'should have Decision column')
})

test('EventTable renders username for each event', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('student01'),   'should show student01 linux user')
  assert.ok(html.includes('employee07'),  'should show employee07 linux user')
})

test('EventTable renders device names', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('Kingston DataTraveler 2.0'), 'should show Kingston device')
  assert.ok(html.includes('SanDisk Ultra'), 'should show SanDisk device')
})

test('EventTable renders BLOCK and ALLOW decisions visibly', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('BLOCK'), 'should show BLOCKED decision')
  assert.ok(html.includes('ALLOW'), 'should show ALLOWED decision')
})

test('EventTable shows empty state when no events match', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: [] }))
  assert.ok(html.includes('Chưa có dữ liệu'), 'should show empty state')
})

test('EventTable shows loading state when loading=true', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: [], loading: true }))
  assert.ok(html.includes('Đang tải'), 'should show loading text')
})

test('EventTable links endpoint to /endpoints/:id detail page', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('href="#/endpoints/POC-LAB-PC-01"'), 'should include endpoint detail link')
})

test('Events page uses the tested table for ready data and preserves frontend fields', () => {
  const html = renderToStaticMarkup(createElement(Events, {
    state: { status: 'ready', data: { events: mockEvents } }, refreshButton: null,
  }))
  for (const value of ['student01', 'employee07', 'Kingston', 'SanDisk', 'BLOCK', 'ALLOW', 'href="#/endpoints/POC-LAB-PC-01"']) assert.ok(html.includes(value), value)
  assert.ok(!html.includes('Dữ liệu mock'))
})

test('EventTable preserves source time, escaped attribution and encoded endpoint identity', () => {
  const event = { ...mockEvents[0], endpointId: 'endpoint A/&B', username: '<script>user</script>', deviceName: '<USB>', timestamp: '2026-10-21T10:11:12.123456789Z' }
  const html = renderToStaticMarkup(createElement(EventTable, { events: [event] }))
  assert.ok(html.includes('href="#/endpoints/endpoint%20A%2F%26B"'))
  assert.ok(html.includes('dateTime="2026-10-21T10:11:12.123456789Z"'))
  assert.ok(html.includes('&lt;script&gt;user&lt;/script&gt;'))
  assert.ok(html.includes('&lt;USB&gt;'))
  assert.ok(!html.includes('<script>'))
})

test('EventTable last server page retains all rows, their order and global pagination', () => {
  const events = Array.from({ length: 5 }, (_, index) => ({ ...mockEvents[0], id: String(25 - index), username: `page-two-user-${index}`, usbDeviceId: String(index + 1) }))
  const html = renderToStaticMarkup(createElement(EventTable, {
    events, pagination: { page: 1, size: 20, totalElements: 25, totalPages: 2, onPageChange() {} },
  }))
  assert.equal((html.match(/<tbody><tr>|<\/tr><tr>/g) ?? []).length, 5)
  assert.ok(html.includes('21–25 trên 25 bản ghi'))
  assert.ok(html.includes('Trang 2 / 2'))
  assert.ok(html.indexOf('page-two-user-0') < html.indexOf('page-two-user-4'))
  assert.ok(html.includes('ID: 5'))
})

test('live filters match table labels and accept endpoint/USB names on a text keyboard', () => {
  const html = renderToStaticMarkup(createElement(EventFilters, { live: true, value: { endpoint: '', username: '', device: '', eventType: '', decision: '', dateFrom: '', dateTo: '' }, onChange() {}, onReset() {} }))
  for (const label of ['Endpoint', 'Linux User', 'USB']) assert.ok(html.includes(`<label>${label}<input`))
  for (const placeholder of ['Tên hoặc ID endpoint', 'Tên hoặc một phần tên', 'Tên hoặc ID USB']) assert.ok(html.includes(`placeholder="${placeholder}"`))
  assert.ok(!html.includes('inputMode="numeric"'))
})

test('live endpoint cell displays the name and its searchable ID together', () => {
  const html = renderToStaticMarkup(createElement(EventTable, {
    events: mockEvents, pagination: { page: 0, size: 20, totalElements: 2, totalPages: 1, onPageChange() {} },
  }))
  assert.ok(html.includes('lab-pc-01.local'))
  assert.ok(html.includes('class="endpoint-event-id">ID: POC-LAB-PC-01'))
})
