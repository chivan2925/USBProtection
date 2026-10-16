# M1 — Week 2 Admin Server integration



Updated: 2026-10-16 (Asia/Saigon). Scope: Admin Server source, canonical contract,

automated backend tests and standalone HTTP verification. Week 1 stays closed.



## Implemented



- BCrypt Admin accounts loaded from H2; optional first-account bootstrap from

  environment, without a public password default. Login/logout/me, session ID

  rotation and session-bound CSRF. H2 web console disabled/denied.

- Enrollment with configured bootstrap credential; persisted BCrypt Agent token

  hash; unique machineId; duplicate registration 409 rather than silent token rotation.

- Agent token verification on heartbeat/event/policy/ACK, identity binding and

  separate Admin/Agent authorization. No `DEFAULT` endpoint fallback.

- Heartbeat updates connectivity only. Applied version changes solely via validated

  ACK; negative/future/regressive values rejected, duplicate ACK idempotent.

- Endpoint list/detail exposes explicit DTO and derived ONLINE/OFFLINE (90s default).

- Authenticated event ingestion preserves user/session/device/time and hostname.

  UNKNOWN user clears UID/session instead of attributing an arbitrary user.

- Whitelist Allow/Revoke by endpoint URL and observed `usbDeviceId`; HASH identity

  only. Transactional row locks serialize edits, policy reads and ACK. Duplicate

  add/revoke does not bump version; A changes do not alter B.

- Event list/detail, exact filters, inclusive UTC time range, stable pagination,

  explicit empty results and uniform validation/auth error shape.

- OpenAPI v0.2 security metadata plus [API](../API.md) and

  [protocol](../AGENT_SERVER_PROTOCOL.md). Historical v0.1 is retained separately.



## Execution environment and evidence



Windows 11, Maven 3.9.9, JDK 22.0.2 compiling Java release 21, Spring Boot 3.3.4.

Final Maven package: **13 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**.
Standalone JAR HTTP script: **PASS** on 2026-10-16 after the final build; all six
HTTP evidence summaries are present. Startup OpenAPI readiness probes timed out
before the initial schema generation finished, producing aborted-response warnings;
the later readiness request and all functional HTTP checks succeeded.



Test results and standalone HTTP checks are recorded under

[week2-evidence](week2-evidence/). No physical Ubuntu or integrated frontend execution

is claimed. Initial sandbox run could not establish localhost connections; final

HTTP test runs used the permitted local execution environment.



| Requirement | Evidence |

|---|---|

| M1-W2-01 Admin auth/session/CSRF | `Week2ServerIntegrationTest`, [HTTP auth](week2-evidence/02-admin-auth-http.txt) |

| M1-W2-02 missing/invalid Agent token | negative integration tests, [HTTP Agent auth](week2-evidence/03-agent-auth-http.txt) |

| M1-W2-03 A token/B identity | filter/header/body/query spoof tests, HTTP auth evidence |

| M1-W2-04 H2 event data | authenticated real HTTP integration + sample contract + query tests |

| M1-W2-05/06 Allow/Revoke A/B | policy integration and concurrency test, [HTTP isolation](week2-evidence/04-policy-A-B-isolation.txt) |

| M1-W2-07 actual M3 DTO compatibility | `PolicyConsumerContractTest` compiles unchanged M3 source DTOs with JDK/Lombok, deserializes server policy and checks populated hash |

| M1-W2-08 ACK validation | negative/future/regressive/duplicate tests, [HTTP ACK](week2-evidence/05-policy-version-ack.txt) |

| M1-W2-09 filters/pages/empty | integration tests, [HTTP event query](week2-evidence/06-event-ingestion-query.txt) |

| M1-W2-10 boot/H2 | [Maven package](week2-evidence/01-maven-test.txt), [server runtime](week2-evidence/server-runtime.log), existing-H2 upgrade test |



The standalone script sends the M2 sample with only its temporary endpointId replaced

in memory by an enrolled identity. The frozen file remains unchanged. Its mouse

session 21 / ALLOWED is not evidence of physical Flash Disk BLOCK.



## Run / reproduce



Requires JDK 21/22 and Maven. From repository root:



```powershell

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'

mvn -B -f admin-server/pom.xml package

./admin-server/verify-week2.ps1 -JavaHome $env:JAVA_HOME

```



The script starts a standalone JAR bound to 127.0.0.1:18082, generates ephemeral

secrets in process environment, uses a new target-local H2 file, tests HTTP and

stops only the process it started. Credentials are not written into evidence.

It requires PowerShell 7 (`SkipHttpErrorCheck`), and overwrites evidence on rerun.

Its HTTP client is a test harness, not a claim of full Agent/Web E2E.



For normal use, provide `USBSHIELD_ADMIN_PASSWORD`, optional

`USBSHIELD_ADMIN_USERNAME`, and `USBSHIELD_ENROLLMENT_TOKEN` outside source, then

run Maven or JAR from admin-server. Empty bootstrap disables enrollment; absent

Admin password creates no account. Admin secret only initializes an absent account,

never silently resets an existing one. For HTTPS deployment, enable secure session

cookies. M4 should proxy `/api` in dev; no wildcard CORS has been introduced.



`verify-week1.ps1` is retained as a historical Week 1 unauthenticated harness;

it is superseded by `verify-week2.ps1` for the secured server. Week 1 logs describe

the earlier execution and must not be treated as current security verification.



## H2 upgrade and recovery



No existing `.mv.db` was deleted or reset. Tests use isolated databases under target

or in-memory H2. `Week2ExistingH2Test` creates a Week 1-shaped file DB, inserts an

endpoint at desired/applied 7/6, closes it, starts the updated application with

`ddl-auto=update`, and verifies the endpoint/versions and added credential column

through a separate JDBC connection.



Before upgrading a real existing DB: stop server and back up its file; inspect

duplicate non-null machineId values (the new constraint requires uniqueness).

Resolve duplicates with the team rather than deleting unrelated data. Hibernate

schema errors are configured to fail startup. The synthetic upgrade test does not

guarantee every historical database variant migrates; validate a copy first.



Legacy endpoints have no usable token hash because Week 1 never persisted their

issued tokens. They correctly receive 401. Duplicate machine re-enrollment returns

409. Credential recovery/rotation requires a controlled administrator operation;

there is no unauthenticated automatic takeover/reset API in this change.

Legacy non-HASH whitelist entries require identity review before policy can be

served. Missing serial is supported when HASH exists; no hash means 409 for a grant.



## Handoff and remaining integration



- M3: canonical `whitelistedDevices`; only supported identity is HASH. Policy omits

  `fingerprintType` because the current consumer DTO does not declare it. Keep token

  headers, handle enrollment 409/401 recovery, and ACK only after M2 apply succeeds.
  Enrollment currentPolicyVersion is desired state, not proof of an applied version.

- M4/M5: session + CSRF lifecycle, `/api/admin/**`, explicit DTOs and event paging

  documented in API v0.2. Admin entry adds use `usbDeviceId`, not VID/PID-only grants.

- M2/M3: UNKNOWN and actual Ubuntu event/session values must be verified on hardware.

  Client native notification remains their integration scope, not server behavior.

- Team review/sign-off of v0.2 is pending; publishing docs does not impersonate it.

- Physical USB → actual Agent → persisted server event → Web row, per-endpoint rule

  application and native client notification are NOT EXECUTED in this server run.



This completes the backend implementation/verification portion available locally;

Week 2 group Gate and real-device integration remain open until appropriate evidence

is collected. No M2/M3 client source or M4/M5 UI/QA source is changed here.

