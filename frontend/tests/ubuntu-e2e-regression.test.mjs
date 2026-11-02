import assert from 'node:assert/strict'
import { before, after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let vite
let mapEndpoint
let mapWhitelistEntry
let EndpointDetail
let EndpointWhitelistPanel

before(async () => {
  vite = await createServer({ server: { middlewareMode: true, ws: false }, appType: 'custom' })
  ;({ mapEndpoint } = await vite.ssrLoadModule('/src/services/mappers/endpointMapper.ts'))
  ;({ mapWhitelistEntry } = await vite.ssrLoadModule('/src/services/mappers/policyMapper.ts'))
  ;({ EndpointDetail } = await vite.ssrLoadModule('/src/pages/Endpoints.tsx'))
  ;({ EndpointWhitelistPanel } = await vite.ssrLoadModule('/src/components/endpoint/EndpointWhitelistPanel.tsx'))
})
after(async () => { await vite?.close() })

const endpointId = 'EP-UBUNTU-A'
const usb = {
  id: '100', endpointId, name: 'DataTraveler 2.0', serialNumber: 'SERIAL-LAB',
  status: 'allowed', detectedAt: '2026-11-02T09:01:00Z', usbDeviceId: 100,
}

test('live endpoint DTO maps non-loopback IP, OS, graphical user and acknowledged version', () => {
  const mapped = mapEndpoint({ endpointId, hostname: 'usbshield-lab', ipAddress: '192.168.23.133',
    operatingSystem: 'Ubuntu 24.04', currentUser: 'usbdev', status: 'ONLINE',
    policyVersion: 4, appliedPolicyVersion: 4, lastSeen: '2026-11-02T09:02:00Z' })
  assert.equal(mapped.ipAddress, '192.168.23.133')
  assert.equal(mapped.operatingSystem, 'Ubuntu 24.04')
  assert.equal(mapped.currentUser, 'usbdev')
  const state = { status: 'ready', data: { endpoints: [mapped], devices: [usb], events: [], whitelist: [] } }
  const html = renderToStaticMarkup(createElement(EndpointDetail, { endpointId, state, refresh() {}, refreshButton: null }))
  assert.match(html, /192\.168\.23\.133/)
  assert.match(html, /Ubuntu 24\.04/)
  assert.match(html, /usbdev/)
  assert.match(html, /Agent đã ACK phiên bản này/)
})

test('whitelist DTO populates name, vendor/product, serial and createdAt from backend', () => {
  const row = mapWhitelistEntry({ id: 12, endpointId, usbDeviceId: 100,
    fingerprintType: 'HASH', fingerprintValue: 'hash-of-kingston', enabled: true,
    name: 'DataTraveler 2.0', vendorId: '0951', productId: '1665', serial: 'SERIAL-LAB',
    createdAt: '2026-11-02T09:00:00Z' }, endpointId)
  const html = renderToStaticMarkup(createElement(EndpointWhitelistPanel, { endpointId, entries: [row] }))
  assert.match(html, /DataTraveler 2\.0/)
  assert.match(html, /0951:1665/)
  assert.match(html, /SERIAL-LAB/)
  assert.match(html, /hash-of-kingston/)
  assert.doesNotMatch(html, /<td>—<\/td><td class="mono">—<\/td>/)
})

test('endpoint historical BLOCK remains audit evidence while latest device decision ALLOW is shown', () => {
  const mapped = mapEndpoint({ endpointId, hostname: 'usbshield-lab', status: 'ONLINE',
    policyVersion: 4, appliedPolicyVersion: 4 })
  const state = { status: 'ready', data: { endpoints: [mapped], devices: [usb],
    events: [{ id: 'e1', endpointId, timestamp: '2026-11-02T09:00:00Z', type: 'connected',
      decision: 'blocked', username: 'usbdev', deviceName: 'DataTraveler 2.0', message: 'USB bị chặn' },
      { id: 'e2', endpointId, timestamp: '2026-11-02T09:01:00Z', type: 'connected',
        decision: 'allowed', username: 'usbdev', deviceName: 'DataTraveler 2.0' }], whitelist: [] } }
  const html = renderToStaticMarkup(createElement(EndpointDetail, { endpointId, state, refresh() {}, refreshButton: null }))
  assert.match(html, /Được phép|Cho phép|Allowed/i)
  assert.match(html, /Sự kiện trên endpoint/)
  assert.match(html, /Bị chặn|BLOCKED/i)
})
