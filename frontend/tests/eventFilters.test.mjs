import assert from 'node:assert/strict'
import { test } from 'node:test'
import { matchesEventFilters } from '../src/utils/eventFilters.ts'
import { emptyEventFilters } from '../src/types/event.ts'
const event = { id: '1', type: 'connected', timestamp: '2026-10-05T03:30:00Z', endpointName: 'LAB-PC-01', username: 'student01', deviceName: 'Kingston', decision: 'blocked', message: '' }
test('event field filters combine and reset', () => {
  assert.equal(matchesEventFilters(event, { ...emptyEventFilters, endpoint: 'lab-pc', username: 'STUDENT', device: 'king', decision: 'blocked' }), true)
  for (const mismatch of [{ endpoint: 'pc-02' }, { username: 'employee' }, { device: 'sandisk' }, { decision: 'allowed' }, { date: '2000-01-01' }]) assert.equal(matchesEventFilters(event, { ...emptyEventFilters, ...mismatch }), false)
  assert.equal(matchesEventFilters(event, emptyEventFilters), true)
})
test('date uses browser local day and no-decision filter excludes policy decisions', () => {
  const time = new Date(event.timestamp)
  const day = `${time.getFullYear()}-${String(time.getMonth()+1).padStart(2,'0')}-${String(time.getDate()).padStart(2,'0')}`
  assert.equal(matchesEventFilters(event, { ...emptyEventFilters, date: day }), true)
  assert.equal(matchesEventFilters({ ...event, decision: undefined }, { ...emptyEventFilters, decision: 'none' }), true)
  assert.equal(matchesEventFilters({ ...event, type: 'blocked', decision: undefined }, { ...emptyEventFilters, decision: 'none' }), false)
})
