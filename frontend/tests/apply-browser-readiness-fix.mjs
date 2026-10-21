// One-time, idempotent migration of the Week 2 browser harness from a fragile
// Vite console-output assertion to a real HTTP readiness probe.
// This keeps all existing browser assertions and test flows unchanged.
import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { resolve } from 'node:path'

export const oldCheck = "await until(() => output.includes('http://127.0.0.1:4173'), 'preview server startup')"
export const newCheck = `await until(async () => {
    // Vite can colorize its startup URL. Successful HTTP is the readiness gate.
    if (server.exitCode !== null || server.signalCode !== null) return false
    const response = await fetch('http://127.0.0.1:4173/', { signal: AbortSignal.timeout(750) })
    return response.ok
  }, 'preview server HTTP readiness')`

export function updateBrowserHarness(source) {
  if (source.includes(newCheck)) {
    if (source.includes(oldCheck)) throw new Error('Conflicting preview startup checks in browser.mjs')
    return { source, changed: false }
  }
  const occurrences = source.split(oldCheck).length - 1
  if (occurrences !== 1) {
    throw new Error(`Expected exactly one known preview startup check in browser.mjs, found ${occurrences}. No changes written.`)
  }
  return { source: source.replace(oldCheck, newCheck), changed: true }
}

async function main() {
  const path = fileURLToPath(new URL('./browser.mjs', import.meta.url))
  const before = await readFile(path, 'utf8')
  const { source, changed } = updateBrowserHarness(before)
  if (changed) await writeFile(path, source, 'utf8')
  console.log(changed
    ? 'Patched frontend/tests/browser.mjs: Vite preview HTTP readiness instead of console URL matching.'
    : 'frontend/tests/browser.mjs: HTTP readiness fix already applied.')
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  await main()
}
