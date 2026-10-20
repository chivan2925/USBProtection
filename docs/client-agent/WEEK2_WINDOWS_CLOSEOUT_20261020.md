# M3 Week 2 correction — Windows-only closeout addendum

**Date:** 2026-10-20. **Base:** `main` commit `e75213b437d01b3335b952618b02705874d257dc`.

This addendum corrects the earlier `WEEK2_AGENT_INTEGRATION.md` where its `IMPLEMENTED` checklist and `02`–`08` evidence references could be misread as real Ubuntu PASS. Software implementation and unit/contract coverage are distinct from installation and hardware acceptance.

## Corrected status

- **Agent implementation:** enrollment, authenticated HTTP transport, heartbeat, policy sync with pending ACK, event outbox and XDG notifier packaging implemented.
- **Previous automated test evidence:** `week2-evidence/01-agent-maven-test.txt` reports **61 tests, 0 failures/errors/skips, BUILD SUCCESS** on 2026-10-17. That raw log does not state the actual OS or revision, and it is *not* a new post-patch run.
- **New packaging correction:** explicit `live` profile, external protected runtime YAML with absolute data paths, `.deb` staging using the installation YAML, and Spring Boot `PropertiesLauncher` desktop helper bootstrap. Windows/static validation is required after application.
- **Ubuntu/systemd/USBGuard/IPC/GUI/physical USB:** `NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4` under the group's revised schedule. Full Week 2 physical/E2E gate remains OPEN, not FAIL.
- **Evidence classification:** see [`week2-evidence/STATUS_AND_PROVENANCE_20261020.md`](week2-evidence/STATUS_AND_PROVENANCE_20261020.md). The old scenario text files remain untouched for auditability. Their claims are not accepted as hardware proof.

### Runtime configuration details (for Ubuntu after Week 4)

`usbshield-agent.service` uses `--spring.profiles.active=live` and `--spring.config.additional-location=file:/etc/usbshield-agent/application.yml`. The installer copies `packaging/agent-deb/etc/usbshield-agent/application.yml`, not the developer resource. The config uses `/var/lib/usbshield-agent` for writable state, a `false` simulation setting, and **no public enrollment-token default**. Configure the real Admin Server URL and the matching enrollment secret securely during installation. Do not introduce real secrets into Git.

The XDG helper executes:

```sh
/usr/bin/java -Dloader.home=/opt/usbshield-agent \
  -Dloader.main=com.group.usbshield.agent.notification.DesktopNotificationClientMain \
  -cp /opt/usbshield-agent/usbshield-agent.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher "$(id -u)"
```

Unlike invoking the M2 main class directly on the fat-JAR classpath, Boot `PropertiesLauncher` can load `BOOT-INF/classes` and `BOOT-INF/lib` and dispatch the correct main class. **This only fixes loading/entry point**, not all socket ACL/session security or real GUI outcomes. All end-to-end claims require later Ubuntu evidence.

Windows regression commands and verification expectations: [`../qa/week2-evidence/WINDOWS_CLOSEOUT_20261020.md`](../qa/week2-evidence/WINDOWS_CLOSEOUT_20261020.md).
