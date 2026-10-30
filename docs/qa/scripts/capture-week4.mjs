// Capture one real command without changing its result or reusing historical logs.
// Usage: capture-week4.mjs [--run-dir PATH --expected-revision SHA] ID CWD SCOPE -- EXE [ARGS...]
import assert from 'node:assert/strict'
import { spawn, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync } from 'node:fs'
import { copyFile, mkdir, readFile, readdir, stat, writeFile } from 'node:fs/promises'
import { dirname, isAbsolute, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const input = process.argv.slice(2)
const options = {}
while (input[0]?.startsWith('--') && input[0] !== '--') {
  const option = input.shift()
  assert.ok(['--run-dir', '--expected-revision'].includes(option) && input[0], `Invalid option: ${option}`)
  assert.ok(options[option] === undefined, `Repeated option: ${option}`)
  options[option] = input.shift()
}
const [id, directory, scope, separator, executable, ...args] = input
assert.ok(/^[a-z0-9-]+$/.test(id ?? '') && separator === '--' && executable,
  'Usage: capture-week4.mjs [--run-dir PATH --expected-revision SHA] ID CWD SCOPE -- EXE [ARGS...]')
const evidenceRoot = join(root, 'docs/qa/week4-evidence')
const evidence = resolve(root, options['--run-dir'] ?? 'docs/qa/week4-evidence/2026-10-28/windows-regression')
const evidenceRelative = relative(evidenceRoot, evidence)
assert.ok(evidenceRelative && !evidenceRelative.startsWith('..') && !isAbsolute(evidenceRelative), 'Run directory must stay inside docs/qa/week4-evidence')
if (options['--run-dir']) assert.match(options['--expected-revision'] ?? '', /^[0-9a-f]{40}$/, 'New runs require an explicit full source SHA')
await mkdir(evidence, { recursive: true })
assert.ok(!existsSync(join(evidence, `${id}.json`)) && !existsSync(join(evidence, `${id}.txt`)), `Existing capture ID: ${id}; choose a new ID`)
const cwd = resolve(root, directory)
const cwdRelative = relative(root, cwd)
assert.ok(!cwdRelative.startsWith('..') && !isAbsolute(cwdRelative), 'CWD must stay in this repository')

function git(...argv) {
  const executable = process.platform === 'win32' ? 'C:/Program Files/Git/cmd/git.exe' : 'git'
  const result = spawnSync(executable, argv, { cwd: root, windowsHide: true, encoding: 'utf8' })
  assert.equal(result.status, 0, result.error?.message ?? result.stderr)
  return result.stdout.trimEnd()
}
const digest = bytes => createHash('sha256').update(bytes).digest('hex')
async function sourceSnapshot() {
  const paths = git('ls-files', '-z', '--', 'frontend', 'admin-server', 'client-agent', 'packaging', '.github')
    .split('\0').filter(Boolean).sort()
  const files = []
  for (const path of paths) files.push({ path, sha256: digest(await readFile(join(root, path))) })
  return { treeSHA256: digest(JSON.stringify(files)), files }
}
const localTime = date => new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Bangkok', dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(date) + ' +07:00'
const redact = value => value.replace(/\x1b\[[0-9;]*m/g, '')
  .replace(/("(?:agentToken|enrollmentToken|password)"\s*:\s*")[^"]*(")/gi, '$1[REDACTED]$2')
const revision = git('rev-parse', 'HEAD')
if (options['--expected-revision']) assert.equal(revision, options['--expected-revision'], 'HEAD differs from the requested source revision')
const before = await sourceSnapshot()
await mkdir(join(evidence, 'sources'), { recursive: true })
await writeFile(join(evidence, 'sources', `${before.treeSHA256}.json`), JSON.stringify(before, null, 2) + '\n')
const started = new Date()
const env = { ...process.env }
if (process.platform === 'win32' && existsSync('C:/Program Files/Java/jdk-22/bin/java.exe')) {
  env.JAVA_HOME = 'C:/Program Files/Java/jdk-22'
  env.PATH = join(env.JAVA_HOME, 'bin') + ';' + env.PATH
}
const quotePS = value => "'" + value.replaceAll("'", "''") + "'"
const launch = /\.(cmd|bat)$/i.test(executable) && process.platform === 'win32'
  ? { exe: 'powershell.exe', args: ['-NoProfile', '-Command', `& ${[executable, ...args].map(quotePS).join(' ')}; exit $LASTEXITCODE`] }
  : { exe: executable, args }
console.log(`START ${id}: ${executable} ${args.join(' ')}`)
let output = ''
let launchError
let timedOut = false
const child = spawn(launch.exe, launch.args, { cwd, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
child.stdout.on('data', data => { output += data })
child.stderr.on('data', data => { output += data })
child.on('error', error => { launchError = error.message })
const timer = setTimeout(() => {
  timedOut = true
  if (child.pid && process.platform === 'win32') {
    spawnSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true })
  } else child.kill('SIGTERM')
}, 15 * 60 * 1000)
const result = await new Promise(resolveExit => child.once('close', (code, signal) => resolveExit({ code, signal })))
clearTimeout(timer)
const ended = new Date()
const cleanOutput = redact(output + (launchError ? `\nLAUNCH ERROR: ${launchError}\n` : ''))
await writeFile(join(evidence, `${id}.txt`), cleanOutput)
const after = await sourceSnapshot()
const screenshots = []
const artifactDir = join(root, 'frontend/.artifacts')
if (id.includes('browser') && existsSync(artifactDir)) {
  for (const name of await readdir(artifactDir)) {
    if (!name.endsWith('.png')) continue
    const path = join(artifactDir, name)
    const info = await stat(path)
    if (info.mtimeMs < started.getTime() || info.mtimeMs > ended.getTime() + 2000) continue
    const destination = join(evidence, 'screenshots', id, name)
    await mkdir(dirname(destination), { recursive: true })
    await copyFile(path, destination)
    screenshots.push({ path: relative(evidence, destination).replaceAll('\\', '/'), sha256: digest(await readFile(destination)), bytes: info.size, modifiedAt: info.mtime.toISOString() })
  }
}
const junit = []
const buildArtifacts = []
for (const module of ['admin-server', 'client-agent']) {
  if (!args.includes(`${module}/pom.xml`)) continue
  const jarName = module === 'admin-server' ? 'usbshield-admin-server.jar' : 'usbshield-agent.jar'
  const jarPath = join(root, module, 'target', jarName)
  if (existsSync(jarPath)) {
    const info = await stat(jarPath)
    if (info.mtimeMs >= started.getTime() && info.mtimeMs <= ended.getTime()) {
      buildArtifacts.push({ path: relative(root, jarPath).replaceAll('\\', '/'), sha256: digest(await readFile(jarPath)), bytes: info.size, modifiedAt: info.mtime.toISOString() })
    }
  }
  const reports = join(root, module, 'target/surefire-reports')
  if (!existsSync(reports)) continue
  for (const name of await readdir(reports)) {
    if (!/^TEST-.*\.xml$/.test(name)) continue
    const path = join(reports, name)
    if ((await stat(path)).mtimeMs < started.getTime() - 2000) continue
    const xml = await readFile(path, 'utf8')
    const attributes = xml.match(/<testsuite\s[^>]*>/)?.[0] ?? ''
    const attribute = key => attributes.match(new RegExp(`\\b${key}="([^"]*)"`))?.[1]
    junit.push({ module, name: attribute('name'), tests: Number(attribute('tests')), failures: Number(attribute('failures')), errors: Number(attribute('errors')), skipped: Number(attribute('skipped')), xmlSHA256: digest(xml) })
  }
}
const metadata = {
  id, scope, revision, revisionAfter: git('rev-parse', 'HEAD'),
  sourceTreeSHA256: before.treeSHA256, sourceTreeSHA256After: after.treeSHA256,
  sourceStable: before.treeSHA256 === after.treeSHA256,
  cwd: relative(root, cwd).replaceAll('\\', '/') || '.', executable, args, launch,
  startedAt: started.toISOString(), endedAt: ended.toISOString(),
  startedAtBangkok: localTime(started), endedAtBangkok: localTime(ended),
  exitCode: result.code, signal: result.signal, launchError, timedOut,
  verdict: result.code === 0 && !timedOut && !launchError && before.treeSHA256 === after.treeSHA256 ? 'PASS' : 'FAIL',
  runnerSHA256: digest(await readFile(fileURLToPath(import.meta.url))),
  javaHome: env.JAVA_HOME, outputSHA256: digest(cleanOutput), screenshots, junit, buildArtifacts,
  evidenceDirectory: relative(root, evidence).replaceAll('\\', '/'),
}
await writeFile(join(evidence, `${id}.json`), JSON.stringify(metadata, null, 2) + '\n')
console.log(`${metadata.verdict} ${id}: exit=${result.code}, sourceStable=${metadata.sourceStable}, screenshots=${screenshots.length}`)
console.log(cleanOutput.trim().split(/\r?\n/).slice(-8).join('\n'))
process.exitCode = metadata.verdict === 'PASS' ? 0 : 1
