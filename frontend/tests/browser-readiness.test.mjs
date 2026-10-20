import assert from 'node:assert/strict'
import { test } from 'node:test'
import { oldCheck, newCheck, updateBrowserHarness } from './apply-browser-readiness-fix.mjs'

test('Vite console URL is replaced by HTTP readiness without removing browser assertions', () => {
  const fixture = `// Chromium browser tests\nconst output = ''\n${oldCheck}\n// existing assertions remain intact`
  const updated = updateBrowserHarness(fixture)
  assert.equal(updated.changed, true)
  assert.ok(updated.source.includes(newCheck))
  assert.ok(!updated.source.includes(oldCheck))
  assert.ok(updated.source.includes('// existing assertions remain intact'))
})

test('startup fix is idempotent', () => {
  const first = updateBrowserHarness(oldCheck)
  const second = updateBrowserHarness(first.source)
  assert.equal(second.changed, false)
  assert.equal(second.source, first.source)
})

test('unrecognized browser harness is not overwritten', () => {
  assert.throws(() => updateBrowserHarness('different startup check'), /No changes written/)
  assert.throws(() => updateBrowserHarness(`${oldCheck}\n${oldCheck}`), /No changes written/)
})

test('success is based on HTTP rather than colorized startup output', () => {
  const coloredViteLog = '  ➜  Local:   \u001b[36mhttp://\u001b[0m\u001b[36m127.0.0.1:4173/\u001b[0m'
  assert.equal(coloredViteLog.includes('http://127.0.0.1:4173'), false)
  assert.ok(newCheck.includes("fetch('http://127.0.0.1:4173/'"))
})
