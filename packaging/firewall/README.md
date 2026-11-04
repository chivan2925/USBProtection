# USBShield LAN firewall / reverse proxy deployment

These are templates, not live firewall rules. Replace placeholders before deployment.

- Spring Boot **must bind `127.0.0.1:18080`** (`USBSHIELD_BIND_ADDRESS=127.0.0.1`, `PORT=18080`, `prod`). Never expose port 18080 directly; firewall must reject external TCP/18080 and restrict PostgreSQL TCP/5432 to the Admin Server only.
- **Critical:** `ADMIN_CIDR` must not include Client IPs. On a shared LAN, use each Admin workstation's exact `/32` IP (or a dedicated Admin VLAN); broad `192.168.x.0/24` would also allow Client browsers. Ensure clients and Admin appear under distinguishable source IPs at Nginx.
- Deploy `packaging/nginx/usbshield-https-acl.conf.example` as a real Nginx site **after** replacing `@@ADMIN_CIDR@@`, `@@CLIENT_CIDR@@`, `@@USBSHIELD_DNS_NAME@@`, certificate/key file paths. Do not let Nginx load an unmodified template.
- Permit TCP/443 to the HTTPS reverse proxy **from the explicitly allowed Admin and Client CIDRs only**. Nginx has a second, path-level ACL: `/api/agent/**` for permitted Agent clients; everything else Admin-only. Network firewall alone cannot enforce HTTP paths.
- Example UFW (replace values; use a maintenance console to avoid losing SSH access):

  ```bash
  sudo ufw allow from <ADMIN_CIDR> to any port 443 proto tcp
  sudo ufw allow from <CLIENT_CIDR> to any port 443 proto tcp
  # Ensure no broader inbound 443/18080/5432 allowances remain.
  sudo ufw status numbered
  ```

- Trust only the actual source IP on the Nginx connection. A load-balancer/NAT that collapses all sources to one IP requires revised ACL design; do not use arbitrary `X-Forwarded-For` headers as an access decision.
- Provide the root-managed Client Agent config with `USBSHIELD_SERVER_URL=https://<DNS_NAME>` (must match certificate SAN); if issued from an internal PKI set `USBSHIELD_TLS_CA_CERTIFICATE` to the absolute path of the CA PEM. Store the enrollment token securely. Never set `trust-all`, `--insecure`, or disable hostname verification.
- After deployment: from the Admin workstation `GET /` should load the login page, Admin login should work; from a Client workstation `GET /`, `/api/auth/csrf`, `/api/admin/endpoints/page` must return 403; `/api/agent/heartbeat` without credentials must return 401/403 from Spring (meaning the proxy passed the path); an Agent with valid credentials must successfully enroll/heartbeat over validated TLS.
- No rate limiting is implemented (explicit project decision); authorized Admin network clients may still attempt repeated login requests.
- `nginx -t` validates syntax but does **not** verify actual source-IP segregation. Run the acceptance tests from separate Admin and Client machines.
