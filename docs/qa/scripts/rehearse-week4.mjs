// Run after the release-candidate server JAR (with React assets) has been built.
// node docs/qa/scripts/rehearse-week4.mjs [--no-recording] [--prepare-only]
// The tracked frontend harness stays unchanged. Its temporary variant retains
// every original assertion and uses the JAR directly, without Vite preview.
import assert from 'node:assert/strict'
import { createHash, randomUUID } from 'node:crypto'
import { spawn, spawnSync } from 'node:child_process'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repository = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const frontend = join(repository, 'frontend')
const evidence = join(repository, 'docs/qa/week4-evidence/2026-10-28/windows-regression')
const args = new Set(process.argv.slice(2))
assert.ok([...args].every(value => ['--no-recording', '--prepare-only'].includes(value)), 'Unknown rehearsal argument')
const recordingRequested = !args.has('--no-recording')
const hash = value => createHash('sha256').update(value).digest('hex')
const sourcePath = join(frontend, 'tests/browser-live.mjs')
const originalBytes = await readFile(sourcePath)
const original = originalBytes.toString('utf8')
const normalized = original.replaceAll('\r\n', '\n')
const newline = original.includes('\r\n') ? '\r\n' : '\n'
await mkdir(evidence, { recursive: true })
let outputDirectory
for (let attempt = 1; attempt <= 100; attempt++) {
  const candidate = join(evidence, attempt === 1 ? 'recording' : `recording-${String(attempt).padStart(2, '0')}`)
  try { await mkdir(candidate); outputDirectory = candidate; break }
  catch (error) { if (error.code !== 'EEXIST') throw error }
}
assert.ok(outputDirectory, 'Could not reserve a fresh rehearsal evidence directory')

// Serialized into the isolated harness. It depends only on Node builtins and
// names already imported by browser-live.mjs; it does not inspect browser DOM,
// cookies, HTTP bodies, passwords, tokens, or application storage.
async function installScreencast(cdp, socket, directory) {
  const { createHash } = await import('node:crypto')
  const framesDirectory = join(directory, 'frames')
  await mkdir(framesDirectory)
  const limits = { maxFrames: 600, maxTotalBytes: 32 * 1024 * 1024, maxFrameBytes: 512 * 1024 }
  const options = { format: 'jpeg', quality: 45, maxWidth: 1280, maxHeight: 960, everyNthFrame: 2 }
  const frames = []
  const errors = []
  let suppressedErrorCount = 0
  let active = false
  let streamStarted = false
  let firstClock
  let startedAt
  let reservedFrames = 0
  let reservedBytes = 0
  let capReason
  let writes = Promise.resolve()
  let finishing
  const errorText = error => error instanceof Error ? error.message : String(error)
  const addError = error => { if (errors.length < 20) errors.push(errorText(error)); else suppressedErrorCount++ }
  const elapsed = () => firstClock === undefined ? 0 : Math.round((performance.now() - firstClock) * 1000) / 1000
  function onMessage(event) {
    const message = JSON.parse(event.data)
    if (message.method !== 'Page.screencastFrame') return
    const { data, sessionId, metadata } = message.params
    // Acknowledge even a skipped/capped frame so recording cannot stall Chrome.
    void cdp('Page.screencastFrameAck', { sessionId }).catch(addError)
    if (!active) return
    const bytes = Buffer.from(data, 'base64')
    if (reservedFrames >= limits.maxFrames || reservedBytes + bytes.length > limits.maxTotalBytes) {
      capReason = reservedFrames >= limits.maxFrames ? 'frame limit' : 'total byte limit'
      active = false
      return
    }
    if (bytes.length > limits.maxFrameBytes) {
      addError(`Skipped an oversized JPEG (${bytes.length} bytes)`)
      return
    }
    if (bytes[0] !== 0xff || bytes[1] !== 0xd8) { addError('Skipped a non-JPEG frame'); return }
    const index = ++reservedFrames
    reservedBytes += bytes.length
    const file = `frames/${String(index).padStart(5, '0')}.jpg`
    const frame = {
      index, file, elapsedMs: elapsed(), receivedAt: new Date().toISOString(),
      cdpTimestampSeconds: metadata?.timestamp ?? null,
      deviceWidth: metadata?.deviceWidth ?? null, deviceHeight: metadata?.deviceHeight ?? null,
      bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex'),
    }
    writes = writes.then(async () => {
      await writeFile(join(directory, file), bytes, { flag: 'wx' })
      frames.push(frame)
    }).catch(addError)
  }
  socket.addEventListener('message', onMessage)
  async function start() {
    firstClock = performance.now()
    startedAt = new Date().toISOString()
    active = true
    try { await cdp('Page.startScreencast', options); streamStarted = true }
    catch (error) { active = false; addError(error) }
  }
  function stop(reason = 'before logout') {
    if (finishing) return finishing
    finishing = (async () => {
      active = false
      const durationMs = elapsed()
      if (streamStarted && socket.readyState === 1) {
        try { await cdp('Page.stopScreencast') } catch (error) { addError(error) }
      }
      await writes
      socket.removeEventListener('message', onMessage)
      const manifest = {
        format: 'CDP JPEG screencast with original capture timings',
        status: frames.length > 0 ? 'CAPTURED' : 'NOT CAPTURED',
        scope: 'Actual browser recording, Windows, isolated H2, synthetic HTTP QA events; no audio; no encoded video file; no physical USB or Ubuntu claim.',
        privacy: 'Started after successful login on populated Events; stopped before logout. Login/password phase excluded.',
        startedAt: startedAt ?? null, endedAt: new Date().toISOString(), durationMs,
        stopReason: capReason ?? reason, capped: Boolean(capReason),
        limits, options, frameCount: frames.length,
        totalBytes: frames.reduce((sum, frame) => sum + frame.bytes, 0), errors, suppressedErrorCount, frames,
      }
      await writeFile(join(directory, 'frames.json'), JSON.stringify(manifest, null, 2) + '\n', { flag: 'wx' })
      if (frames.length) {
        const embedded = []
        for (const frame of frames) embedded.push({ t: frame.elapsedMs, src: `data:image/jpeg;base64,${(await readFile(join(directory, frame.file))).toString('base64')}` })
        const payload = JSON.stringify({ durationMs, frames: embedded }).replaceAll('<', '\\u003c')
        const html = `<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>USBShield Week 4 — CDP screencast replay</title>
<style>body{font:16px system-ui;margin:20px;background:#101827;color:#e2e8f0}main{max-width:1280px;margin:auto}h1{font-size:22px}p{line-height:1.5}img{width:100%;height:auto;background:#fff;border:1px solid #475569}section{display:flex;align-items:center;gap:12px;margin:12px 0}input{flex:1}button{padding:8px 18px}small{color:#cbd5e1}</style>
<main><h1>USBShield Week 4 — recorded browser screencast</h1>
<p>Actual CDP JPEG frames replayed at their capture timings. Windows · synthetic HTTP QA events · isolated H2 · no audio. This is a self-contained screencast replay, without an encoded video file. Ubuntu/physical USB acceptance is deferred.</p>
<section><button id="play">Play</button><input id="time" type="range" min="0" max="${Math.max(1, Math.ceil(durationMs))}" step="10" value="0" aria-label="Recording position"><output id="clock"></output></section>
<img id="frame" alt="Recorded USBShield Events browser frame"><p><small>${frames.length} captured frames. Login/password phase excluded. ${capReason ? `Capture stopped at ${capReason}; later actions are outside this recording.` : 'Capture ends before logout.'}</small></p></main>
<script id="capture" type="application/json">${payload}</script>
<script>
const capture=JSON.parse(document.getElementById('capture').textContent);
const image=document.getElementById('frame'), seek=document.getElementById('time'), play=document.getElementById('play'), clock=document.getElementById('clock');
let position=0, playing=false, anchor=0, anchorPosition=0, shown=-1;
function draw(){let i=0;while(i+1<capture.frames.length&&capture.frames[i+1].t<=position)i++;if(shown!==i){image.src=capture.frames[i].src;shown=i;}seek.value=String(position);clock.textContent=(position/1000).toFixed(1)+' / '+(capture.durationMs/1000).toFixed(1)+' s';}
function tick(now){if(!playing)return;position=Math.min(capture.durationMs,anchorPosition+now-anchor);draw();if(position>=capture.durationMs){playing=false;play.textContent='Replay';return;}requestAnimationFrame(tick);}
play.onclick=()=>{if(playing){playing=false;play.textContent='Play';return;}if(position>=capture.durationMs)position=0;playing=true;anchor=performance.now();anchorPosition=position;play.textContent='Pause';requestAnimationFrame(tick);};
seek.oninput=()=>{position=Number(seek.value);anchor=performance.now();anchorPosition=position;draw();};draw();
</script></html>`
        await writeFile(join(directory, 'replay.html'), html, { flag: 'wx' })
      }
      console.log(`RECORDING: ${manifest.status}; ${frames.length} CDP JPEG frames, ${manifest.totalBytes} bytes; ${directory}`)
      return manifest
    })()
    return finishing
  }
  return { start, stop }
}

let variant = normalized
const substitutions = []
function substitute(label, before, after) {
  const count = variant.split(before).length - 1
  assert.equal(count, 1, `Expected exactly one ${label} substitution; found ${count}`)
  variant = variant.replace(before, after)
  substitutions.push({ label, before, after, count })
}
substitute('serve UI from packaged JAR', "const web = 'http://127.0.0.1:4174'", 'const web = api')
substitute('remove Vite preview process', "  start(process.execPath, [join(root, 'node_modules/vite/bin/vite.js'), 'preview', '--host', '127.0.0.1', '--port', '4174', '--strictPort'], { env: { ...process.env, USBSHIELD_API_TARGET: api } })", '  // Week 4: the existing JAR serves both React assets and /api on its isolated port.')
if (recordingRequested) {
  substitute('recording cleanup handle', 'let socket\n', 'let socket\nlet week4FinishRecording = async () => {}\n')
  const handlerAnchor = "  socket.addEventListener('message', event => {"
  substitute('install bounded CDP recorder', handlerAnchor,
    `  const week4Recorder = await (${installScreencast.toString()})(cdp, socket, ${JSON.stringify(outputDirectory)})\n  week4FinishRecording = reason => week4Recorder.stop(reason)\n${handlerAnchor}`)
  const firstScreenshot = "  await screenshot('events-real-http-desktop.png')"
  substitute('start recording after successful login and populated Events', firstScreenshot, `  await week4Recorder.start()\n${firstScreenshot}`)
  const logout = '  await evaluate(`document.querySelector(\'[aria-label="Đăng xuất"]\').click()`)'
  substitute('stop recording before logout', logout, `  await week4FinishRecording('before logout')\n${logout}`)
  substitute('finish recording before WebSocket cleanup', '} finally {\n  socket?.close()', "} finally {\n  try { await week4FinishRecording('harness cleanup') } catch (error) { console.error('RECORDING FINALIZATION FAILED:', error.message); process.exitCode = 1 }\n  socket?.close()")
}
const originalTermination = String.raw`  for (const child of children.reverse()) {
    if (child.exitCode !== null || child.signalCode !== null || !child.pid) continue
    if (process.platform === 'win32') {
      // Windows Java/Chrome launchers may spawn additional processes.
      await new Promise(resolveKill => {
        execFile('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, timeout: 15000 }, () => resolveKill())
      })
    } else child.kill()
  }
  await delay(1000)`
const verifiedTermination = String.raw`  const week4Cleanup = []
  let week4TerminationConfirmed = true
  for (const child of children.reverse()) {
    if (child.exitCode !== null || child.signalCode !== null || !child.pid) continue
    let killError = null
    if (process.platform === 'win32') {
      killError = await new Promise(resolveKill => {
        execFile('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, timeout: 15000 }, error => resolveKill(error ? { code: error.code, message: error.message } : null))
      })
    } else {
      try { child.kill() } catch (error) { killError = { message: error.message } }
    }
    for (let attempt = 0; attempt < 50 && child.exitCode === null && child.signalCode === null; attempt++) await delay(100)
    const confirmed = child.exitCode !== null || child.signalCode !== null
    week4Cleanup.push({ pid: child.pid, killError, exitCode: child.exitCode, signalCode: child.signalCode, confirmed })
    if (!confirmed) week4TerminationConfirmed = false
  }
  // A taskkill error for a child whose exit is confirmed is not a test failure.
  if (!week4TerminationConfirmed) {
    console.error('CLEANUP: child termination unconfirmed; retaining ignored runtime:', runtime)
    process.exitCode = 1
  }
  await writeFile(join(${JSON.stringify(outputDirectory)}, 'cleanup.json'), JSON.stringify({ terminationConfirmed: week4TerminationConfirmed, runtimeRetained: !week4TerminationConfirmed, children: week4Cleanup }, null, 2) + '\n', { flag: 'wx' })`
substitute('verify process termination before runtime removal', originalTermination, verifiedTermination)
const originalRemove = '  await rm(runtime, { recursive: true, force: true, maxRetries: 10, retryDelay: 300 })'
substitute('retain runtime when process exit is unconfirmed', originalRemove, `  if (week4TerminationConfirmed) {\n${originalRemove}\n  }`)
// Each original assertion line must remain byte-for-byte after newline normalization.
const assertionLines = normalized.split('\n').filter(line => /\bassert\./.test(line))
assert.ok(assertionLines.length > 0, 'No original assertions found')
for (const line of assertionLines) assert.ok(variant.includes(line), `Original assertion changed: ${line}`)
let recovered = variant
for (const change of [...substitutions].reverse()) recovered = recovered.replace(change.after, change.before)
assert.equal(recovered, normalized, 'Variant must contain only the documented substitutions')

const artifactDirectory = join(frontend, '.artifacts')
await mkdir(artifactDirectory, { recursive: true })
const variantPath = join(artifactDirectory, `week4-packaged-rehearsal-${randomUUID()}.mjs`)
const ignored = spawnSync('git', ['check-ignore', '--quiet', '--', relative(repository, variantPath)], { cwd: repository, windowsHide: true })
assert.equal(ignored.status, 0, 'Temporary browser variant must be under an ignored path')
const variantBytes = Buffer.from(variant.replaceAll('\n', newline), 'utf8')
await writeFile(variantPath, variantBytes, { flag: 'wx' })
const syntax = spawnSync(process.execPath, ['--check', variantPath], { cwd: frontend, windowsHide: true, encoding: 'utf8' })
assert.equal(syntax.status, 0, `Generated variant syntax is invalid: ${syntax.stderr}`)
const revision = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: repository, windowsHide: true, encoding: 'utf8' })
assert.equal(revision.status, 0, 'Cannot record source revision')
const jarPath = join(repository, 'admin-server/target/usbshield-admin-server.jar')
const provenance = {
  status: 'PREPARED', date: '2026-10-28', timezone: 'Asia/Bangkok',
  preparedAt: new Date().toISOString(), revision: revision.stdout.trim(),
  node: process.version, platform: process.platform, architecture: process.arch,
  source: { path: relative(repository, sourcePath), sha256: hash(originalBytes) },
  variant: { path: relative(repository, variantPath), sha256: hash(variantBytes), syntaxCheckExitCode: syntax.status },
  jar: { path: relative(repository, jarPath), sha256: hash(await readFile(jarPath)) },
  wrapper: { path: relative(repository, fileURLToPath(import.meta.url)), sha256: hash(await readFile(fileURLToPath(import.meta.url))) },
  originalAssertionLinesPreserved: assertionLines.length,
  reversibleToOriginalSource: true, substitutions, recordingRequested,
  scope: 'Packaged JAR same-origin Events browser regression, Windows synthetic HTTP/H2 input; no actual Agent/USBGuard/physical USB/Ubuntu claim.',
  recording: 'Optional actual CDP JPEG screencast plus capture timeline and self-contained replay; no encoded video or audio. Login/password phase excluded.',
}
const provenancePath = join(outputDirectory, 'provenance.json')
await writeFile(provenancePath, JSON.stringify(provenance, null, 2) + '\n', { flag: 'wx' })
await writeFile(join(outputDirectory, 'README.md'), `# Week 4 packaged-JAR Events rehearsal\n\nSource revision: \`${provenance.revision}\`. Exact source/variant/wrapper/JAR hashes and reversible substitutions: [provenance.json](provenance.json). The tracked frontend harness is unchanged. Every original assertion is retained.\n\nData: **synthetic Agent HTTP events, isolated H2, Windows browser**. The JAR serves React and API on the same origin. This does not execute a real Agent, USBGuard, physical USB, Ubuntu GUI or installer.\n\nWhen recording is enabled, capture starts after authenticated Events rows are visible and stops before logout. [frames.json](frames.json) states whether recording actually succeeded, original CDP timestamps, receipt timings, JPEG hashes, byte/frame limits and errors. [replay.html](replay.html), when created, is a self-contained replay of actual screencast frames at their capture timings. It is not an encoded video file; no audio is recorded. Zero frames means **NOT CAPTURED**. A prepare-only run is **NOT EXECUTED**.\n\nNo historical evidence is overwritten; repeated invocations reserve a new directory. Browser screenshots still use the original harness artifact paths and should be copied/indexed by the main QA collector.\n`, { flag: 'wx' })
console.log(`REHEARSAL: prepared ${relative(repository, variantPath)}; original assertions retained: ${assertionLines.length}; evidence ${relative(repository, outputDirectory)}`)
if (!args.has('--prepare-only')) {
  provenance.status = 'RUNNING'
  provenance.startedAt = new Date().toISOString()
  await writeFile(provenancePath, JSON.stringify(provenance, null, 2) + '\n')
  let result
  provenance.recordingResult = { status: recordingRequested ? 'NOT CAPTURED' : 'DISABLED', frameCount: 0 }
  try {
    result = await new Promise((resolveRun, rejectRun) => {
      const child = spawn(process.execPath, [variantPath], { cwd: frontend, windowsHide: true, stdio: 'inherit' })
      child.once('error', rejectRun)
      child.once('exit', (code, signal) => resolveRun({ code, signal }))
    })
    provenance.exitCode = result.code
    provenance.signal = result.signal
    provenance.status = result.code === 0 ? 'PASS' : 'FAIL'
    assert.equal(hash(await readFile(sourcePath)), provenance.source.sha256, 'Tracked frontend harness changed during rehearsal')
    provenance.originalSourceUnchangedAfterRun = true
    try {
      const recorded = JSON.parse(await readFile(join(outputDirectory, 'frames.json'), 'utf8'))
      provenance.recordingResult = { status: recorded.status, frameCount: recorded.frameCount, totalBytes: recorded.totalBytes, capped: recorded.capped, errors: recorded.errors, suppressedErrorCount: recorded.suppressedErrorCount }
    } catch (error) { if (error.code !== 'ENOENT') throw error }
  } catch (error) {
    provenance.status = 'FAIL'
    provenance.error = { name: error.name, message: error.message }
    console.error('REHEARSAL FAILED:', error.message)
  } finally {
    provenance.endedAt = new Date().toISOString()
    await writeFile(provenancePath, JSON.stringify(provenance, null, 2) + '\n')
    console.log(`REHEARSAL: ${provenance.status}; recording ${provenance.recordingResult.status}; ${relative(repository, outputDirectory)}`)
  }
  process.exitCode = provenance.status === 'PASS' ? 0 : result?.code || 1
}
