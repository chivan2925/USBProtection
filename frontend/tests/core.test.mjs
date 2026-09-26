import assert from 'node:assert/strict'
import { test } from 'node:test'
import { count, formatDate, matches, newestFirst, paginate } from '../src/utils/format.ts'
import { BackendUnavailableError, capabilities, protectionApi } from '../src/services/api.ts'

test('unknown statistics are not represented as zero or protected', () => {
  assert.equal(count(undefined), '—')
  assert.equal(count(null), '—')
  assert.equal(count(0), '0')
  assert.equal(formatDate(undefined), '—')
  assert.equal(formatDate('invalid'), '—')
})

test('search accepts Vietnamese without accents, case differences and USB identifiers', () => {
  assert.equal(matches('  o dia  ', 'Ổ ĐĨA USB'), true)
  assert.equal(matches('abcd', undefined, 'ABCD', '1234'), true)
  assert.equal(matches('unknown', 'USB', undefined), false)
  assert.equal(matches('', undefined), true)
})

test('event sorting compares timestamps across timezone offsets and does not mutate input', () => {
  const input = [
    { id: 'older', time: '2026-09-26T16:00:00+07:00' },
    { id: 'invalid', time: 'invalid' },
    { id: 'newer', time: '2026-09-26T10:00:00Z' },
  ]
  assert.deepEqual(newestFirst(input, item => item.time).map(item => item.id), ['newer', 'older', 'invalid'])
  assert.equal(input[0].id, 'older')
})

test('pagination clamps stale page numbers after filtering and handles empty results', () => {
  const rows = Array.from({ length: 23 }, (_, index) => index)
  assert.deepEqual(paginate(rows, 3).rows, [20, 21, 22])
  assert.equal(paginate(rows.slice(0, 3), 3).page, 1)
  assert.equal(paginate(rows, -1).page, 1)
  assert.deepEqual(paginate([], 99), { page: 1, totalPages: 1, rows: [] })
})

test('unavailable backend rejects reads and mutations without any HTTP calls', async () => {
  const originalFetch = globalThis.fetch
  let requests = 0
  globalThis.fetch = async () => { requests++; throw new Error('Unexpected request') }
  try {
    assert.ok(Object.values(capabilities).every(value => value === false))
    await assert.rejects(protectionApi.getSnapshot(new AbortController().signal), BackendUnavailableError)
    for (const action of ['allow', 'block', 'revoke']) {
      await assert.rejects(protectionApi.changeDevicePolicy('test-only', action), BackendUnavailableError)
    }
    await assert.rejects(protectionApi.login('test-only', 'not-a-real-password'), BackendUnavailableError)
    assert.equal(requests, 0)
  } finally { globalThis.fetch = originalFetch }
})

test('cancelled reads stay cancelled instead of reporting backend errors', async () => {
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(protectionApi.getSnapshot(controller.signal), { name: 'AbortError' })
})
