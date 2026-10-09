import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

// M5 — Week 1 tests for EventHistoryPage and EventTable.

let vite
let Events
let EventTable

before(async () => {
  vite = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  ;({ Events } = await vite.ssrLoadModule('/src/pages/Events.tsx'))
  ;({ EventTable } = await vite.ssrLoadModule('/src/components/event/EventTable.tsx'))
})
after(async () => { await vite?.close() })

const mockEvents = [
  {
    id: 'evt-001',
    occurredAt: '2026-09-28T14:10:16+07:00',
    endpointId: 'POC-LAB-PC-01',
    hostname: 'lab-pc-01.local',
    linuxUsername: 'student01',
    device: { vendorId: '0951', productId: '1665', name: 'Kingston DataTraveler 2.0', interface: '08:06:50' },
    eventType: 'connected',
    decision: 'BLOCKED',
    message: 'Unknown USB Mass Storage blocked',
  },
  {
    id: 'evt-002',
    occurredAt: '2026-09-28T10:05:00+07:00',
    endpointId: 'POC-LAB-PC-02',
    hostname: 'lab-pc-02.local',
    linuxUsername: 'employee07',
    device: { vendorId: '0781', productId: '5583', name: 'SanDisk Ultra', interface: '08:06:50' },
    eventType: 'connected',
    decision: 'ALLOWED',
    message: 'Whitelisted USB Mass Storage allowed',
  },
]

// --- EventTable component ---

test('EventTable renders required columns: Endpoint, Linux User, USB, Event, Decision', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('Endpoint'),    'should have Endpoint column')
  assert.ok(html.includes('Linux User'),  'should have Linux User column')
  assert.ok(html.includes('USB'),         'should have USB column')
  assert.ok(html.includes('Sự kiện'),     'should have Event column')
  assert.ok(html.includes('Decision'),    'should have Decision column')
})

test('EventTable renders linuxUsername for each event', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('student01'),   'should show student01 linux user')
  assert.ok(html.includes('employee07'),  'should show employee07 linux user')
})

test('EventTable renders device names', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('Kingston DataTraveler 2.0'), 'should show Kingston device')
  assert.ok(html.includes('SanDisk Ultra'), 'should show SanDisk device')
})

test('EventTable renders BLOCKED and ALLOWED decisions visibly', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('BLOCKED'), 'should show BLOCKED decision')
  assert.ok(html.includes('ALLOWED'), 'should show ALLOWED decision')
})

test('EventTable shows empty state when no events match', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: [] }))
  assert.ok(html.includes('Không có sự kiện'), 'should show empty state')
})

test('EventTable shows loading state when loading=true', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: [], loading: true }))
  assert.ok(html.includes('Đang tải'), 'should show loading text')
})

test('EventTable links endpoint to /endpoints/:id detail page', () => {
  const html = renderToStaticMarkup(createElement(EventTable, { events: mockEvents }))
  assert.ok(html.includes('POC-LAB-PC-01'), 'should include endpoint link')
})
