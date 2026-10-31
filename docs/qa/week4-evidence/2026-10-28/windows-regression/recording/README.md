# Week 4 packaged-JAR Events rehearsal

Source revision: `2013ce726ef8cc960b30720435be23e8ccef1903`. Exact source/variant/wrapper/JAR hashes and reversible substitutions: [provenance.json](provenance.json). The tracked frontend harness is unchanged. Every original assertion is retained.

Data: **synthetic Agent HTTP events, isolated H2, Windows browser**. The JAR serves React and API on the same origin. This does not execute a real Agent, USBGuard, physical USB, Ubuntu GUI or installer.

When recording is enabled, capture starts after authenticated Events rows are visible and stops before logout. [frames.json](frames.json) states whether recording actually succeeded, original CDP timestamps, receipt timings, JPEG hashes, byte/frame limits and errors. [replay.html](replay.html), when created, is a self-contained replay of actual screencast frames at their capture timings. It is not an encoded video file; no audio is recorded. Zero frames means **NOT CAPTURED**. A prepare-only run is **NOT EXECUTED**.

No historical evidence is overwritten; repeated invocations reserve a new directory. Browser screenshots still use the original harness artifact paths and should be copied/indexed by the main QA collector.
