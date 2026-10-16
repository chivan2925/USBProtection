import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

// M5 — Event History regression and Week 2 resource-state coverage.

let vite
let Events
let EventTable

before(async () => {
  vite = await createServer({ server: { middlewareMode: true, ws: false }, appType: 'custom' })
  ;({ Events } = await vite.ssrLoadModule('/src/pages/Events.tsx'))
  ;({ EventTable } = await vite.ssrLoadModule('/src/components/event/EventTable.tsx'))
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
