import assert from 'node:assert/strict'
import { test } from 'node:test'
import { execFileSync } from 'node:child_process'
import { getEventDateRange, matchesEventFilters } from '../src/utils/eventFilters.ts'
import { emptyEventFilters } from '../src/types/event.ts'
const event = { id: '1', type: 'connected', timestamp: '2026-10-05T03:30:00Z', endpointName: 'LAB-PC-01', username: 'student01', deviceName: 'Kingston', decision: 'blocked', message: '' }
test('event field filters combine and reset', () => {
  assert.equal(matchesEventFilters(event, { ...emptyEventFilters, endpoint: 'lab-pc', username: 'STUDENT', device: 'king', decision: 'blocked' }), true)
  for (const mismatch of [{ endpoint: 'pc-02' }, { username: 'employee' }, { device: 'sandisk' }, { decision: 'allowed' }, { eventType: 'disconnected' }, { dateTo: '2000-01-01' }]) assert.equal(matchesEventFilters(event, { ...emptyEventFilters, ...mismatch }), false)
  assert.equal(matchesEventFilters(event, emptyEventFilters), true)
})
test('date uses browser local day and no-decision filter excludes policy decisions', () => {
  const time = new Date(event.timestamp)
  const day = `${time.getFullYear()}-${String(time.getMonth()+1).padStart(2,'0')}-${String(time.getDate()).padStart(2,'0')}`
  assert.equal(matchesEventFilters(event, { ...emptyEventFilters, dateFrom: day, dateTo: day }), true)
  assert.equal(matchesEventFilters({ ...event, decision: undefined }, { ...emptyEventFilters, decision: 'none' }), true)
  assert.equal(matchesEventFilters({ ...event, type: 'blocked', decision: undefined }, { ...emptyEventFilters, decision: 'none' }), false)
})

test('UNKNOWN differs from no decision and device identity fields are searchable', () => {
  const unknown = { ...event, username: 'UNKNOWN', decision: 'unknown', device: { vendorId: '0951', productId: '1666', serial: 'ABC', hash: 'HASH' } }
  assert.equal(matchesEventFilters(unknown, { ...emptyEventFilters, decision: 'unknown' }), true)
  assert.equal(matchesEventFilters(unknown, { ...emptyEventFilters, decision: 'none' }), false)
  for (const device of ['0951', '1666', 'abc', 'hash']) assert.equal(matchesEventFilters(unknown, { ...emptyEventFilters, device }), true)
})

test('bad dates, reversed ranges and invalid event timestamps do not match', () => {
  for (const filters of [{ dateFrom: '2026-02-30' }, { dateTo: 'invalid' }, { dateFrom: '2026-10-06', dateTo: '2026-10-05' }]) {
    assert.equal(getEventDateRange({ ...emptyEventFilters, ...filters }).valid, false)
    assert.equal(matchesEventFilters(event, { ...emptyEventFilters, ...filters }), false)
  }
  assert.equal(matchesEventFilters({ ...event, timestamp: 'invalid' }, { ...emptyEventFilters, dateFrom: '2026-10-05' }), false)
})

function inTimezone(tz, dateFrom, dateTo, timestamps) {
  const script = `
    import { getEventDateRange, matchesEventFilters } from './src/utils/eventFilters.ts';
    import { emptyEventFilters } from './src/types/event.ts';
    const filters = { ...emptyEventFilters, dateFrom: ${JSON.stringify(dateFrom)}, dateTo: ${JSON.stringify(dateTo)} };
    console.log(JSON.stringify({ range: getEventDateRange(filters), matches: ${JSON.stringify(timestamps)}.map(timestamp => matchesEventFilters({ id: '1', type: 'connected', timestamp, message: '' }, filters)) }));
  `
  return JSON.parse(execFileSync(process.execPath, ['--experimental-strip-types', '--input-type=module', '-e', script], { cwd: new URL('../', import.meta.url), env: { ...process.env, TZ: tz }, encoding: 'utf8', timeout: 10000 }))
}

test('Bangkok range includes both selected days and excludes the next midnight', () => {
  const result = inTimezone('Asia/Bangkok', '2026-10-05', '2026-10-06', ['2026-10-04T16:59:59.999Z', '2026-10-04T17:00:00Z', '2026-10-06T16:59:59.999Z', '2026-10-06T17:00:00Z', '2026-10-05T00:00:00+07:00'])
  assert.deepEqual(result.range, { valid: true, fromInclusive: '2026-10-04T17:00:00.000Z', toExclusive: '2026-10-06T17:00:00.000Z' })
  assert.deepEqual(result.matches, [false, true, true, false, true])
})

test('one-sided dates work in UTC and DST days use calendar boundaries', () => {
  assert.deepEqual(inTimezone('UTC', '', '2026-10-05', ['2026-10-05T23:59:59Z', '2026-10-06T00:00:00Z']).matches, [true, false])
  assert.deepEqual(inTimezone('UTC', '2026-10-05', '', ['2026-10-04T23:59:59Z', '2026-10-05T00:00:00Z']).matches, [false, true])
  for (const [day, hours] of [['2026-03-08', 23], ['2026-11-01', 25]]) {
    const { range } = inTimezone('America/New_York', day, day, [])
    assert.equal((Date.parse(range.toExclusive) - Date.parse(range.fromInclusive)) / 3600000, hours)
  }
})
