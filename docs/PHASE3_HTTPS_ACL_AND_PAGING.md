# USBShield Phase 3 — HTTPS, network ACL and paged Admin API

Baseline: `vinhkhang` `8177288d99d90c1f37ec7a8c349099bca790956f`.
**This is a deployment guide, not a claim of successful Ubuntu/LAN E2E.** F01/F02/I02 are only accepted after real TLS/ACL tests from distinct machines.

## Access boundary

- **Admin allowlist MUST NOT contain Client source IPs.** If both share one LAN subnet, use Admin workstation `/32` address(es) rather than allowing the whole subnet; NAT collapsing both classes onto one source IP makes source-IP ACL insufficient.
- Admin subnet/workstations: allowed by proxy to **all Web assets**, `/api/auth/**`, `/api/admin/**` and `/api/agent/**` (the last path still uses Agent authentication).
- Client subnet/workstations: allowed **only** to `/api/agent/**`. All other paths are rejected with HTTP 403 *by the proxy*.
- Spring Boot `prod` must bind `127.0.0.1:18080` and be inaccessible via a second interface; database PostgreSQL TCP/5432 must not be exposed to clients.
- `packaging/nginx/usbshield-https-acl.conf.example`: replace every `@@...@@` placeholder and configure real certificate/key paths and IP/CIDR. TLS termination occurs at Nginx. Do **not** configure `real_ip` based on arbitrary untrusted headers. See `packaging/firewall/README.md`.
- This release **does not implement rate limiting** as explicitly decided by the owner; brute force remains possible from allowed Admin sources. Browser authentication and CSRF remain enforced by Spring Boot.

## Server setup

1. Obtain a CA-issued certificate for the chosen DNS name. The SAN must contain the exact name in `USBSHIELD_SERVER_URL`. For a private CA, distribute the CA public PEM certificate to Client machines; **never distribute the CA private key**.
2. Start Admin Server with the existing PostgreSQL/Flyway setup, for example `USBSHIELD_BIND_ADDRESS=127.0.0.1`, `PORT=18080`, profile `prod`. Complete existing `POSTGRES_FLYWAY_BASELINE.md` instructions first if the application schema is an unversioned pre-Flyway schema. This phase contains no database migration.
3. Install Nginx, adapt the ACL template, run `sudo nginx -t`, then reload Nginx. No general HTTP-to-HTTPS fallback is needed for Agent; open inbound port 443 only as documented.
4. Firewall: block external TCP/18080 and TCP/5432; restrict TCP/443 to permitted Client and Admin networks. Confirm the backend cannot be reached around the proxy.

## Ubuntu Client Agent deployment

1. Before installing/upgrading the `.deb` (its `postinst` starts the service), preconfigure `/etc/usbshield-agent/agent.env` using `packaging/agent-deb/etc/usbshield-agent/agent.env.example` as a reference. File must be root-owned mode **0600**. Enter the deployment enrollment token privately; do not include real secrets in Git or chat logs.
2. Set `USBSHIELD_SERVER_URL=https://<actual-admin-dns-name>`. Do not use IP unless certificate SAN includes that IP. If using a private CA, copy its certificate to `/etc/usbshield-agent/usbshield-ca.pem` as root-owned readable file and set `USBSHIELD_TLS_CA_CERTIFICATE` to its **absolute** path. With a publicly trusted CA, omit the variable.
3. The unit file includes `EnvironmentFile=-/etc/usbshield-agent/agent.env`; the installed `application.yml` uses these variables. Missing URL, HTTP URL, invalid CA, invalid certificate chain or hostname mismatch must prevent authentication/enrollment. There is **no HTTP fallback** in the live profile.
4. Check `systemctl status usbshield-agent`, `journalctl -u usbshield-agent`; a valid heartbeat is additional evidence but does not replace checking actual USBGuard enforcement.

## Acceptance checks — perform on actual LAN

| Origin | Request | Expected |
|---|---|---|
| Admin workstation | `https://<hostname>/` | Login/static Admin Web loads |
| Admin workstation | `https://<hostname>/api/auth/csrf` | HTTP 200 (CSRF JSON) |
| Client workstation | `https://<hostname>/` | HTTP 403 |
| Client workstation | `/api/auth/csrf`, `/api/admin/endpoints/page` | HTTP 403 |
| Client workstation | `/api/agent/heartbeat` without credentials | Spring 401/403, **not proxy 403** (proxy path permitted) |
| Client Agent with valid credentials | Enrollment/heartbeat/USB event/policy/ACK | Success over **validated HTTPS** |
| Client Agent with wrong CA or wrong DNS certificate | Enrollment/heartbeat | TLS handshake fails, no insecure retry |
| Any remote workstation | `http://<server-ip>:18080/` | Connection refused/blocked |

## API compatibility and pagination

- `GET /api/admin/endpoints/page?page=0&size=20&q=<search>&status=online|offline` returns `{content,page,size,totalElements,totalPages,onlineEndpoints,offlineEndpoints}`. `q` searches endpointId, hostname, IP and currentUser; filter and count happen in PostgreSQL, not on a downloaded list. Page max 100 items.
- `GET /api/admin/whitelist/page?page=0&size=20&endpointId=<id>&q=<search>` returns `{content,page,size,totalElements,totalPages}`. A single bounded query joins device metadata for the selected page and avoids N requests for N endpoints.
- `/api/admin/endpoints/{endpointId}` and per-endpoint `/whitelist` remain unchanged for Allow/Revoke and detail. The deprecated `GET /api/admin/endpoints` is retained temporarily for compatibility but capped at 100 entries; **new Frontend does not depend on it**. Migrate other consumers to `/page`.
- Dashboard `onlineEndpoints`/`offlineEndpoints` counts are global, not computed from the first page. Frontend list and whitelist pages fetch independent server pages as the user searches or navigates.
- The global whitelist dropdown shows Endpoint suggestions from the initial loaded page; search by Endpoint ID in the text box to include Endpoint IDs not currently listed in that dropdown.

## Verification

Run `packaging/tests/verify-phase3-https-acl.sh` separately from Admin and Client hosts after configuring Nginx, with CA PEM argument for private PKI. This script does not disable TLS verification. Run Maven Admin + Client tests, then Frontend tests/lint/build and both browser-live suites with dedicated PostgreSQL test databases (never application `usbshield`). The browser-live tests do **not** test the Nginx ACL/certificate handshake or physical USBGuard; run LAN/Ubuntu checks separately. For Windows PowerShell, the project's `scripts/with-env.ps1` can load `.env` for Maven/npm test DB credentials; do not put secret values in committed files.
