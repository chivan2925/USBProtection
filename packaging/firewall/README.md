# USBShield LAN firewall and HTTPS

- The Central backend uses 127.0.0.1:18080 and PostgreSQL binds loopback. Keep both ports inaccessible from LAN.
- Fill the static LAN IPv4, Agent CIDR, internal DNS name and TLS paths in [the Nginx template](../nginx/usbshield-https-acl.conf.example). Use a server certificate with IP SAN 127.0.0.1 and the LAN DNS SAN. Keep the private key accessible to the proxy service identity.
- Permit inbound TCP/443 on the LAN address from the authorized Agent CIDRs. Nginx forwards normalized Agent API paths only on that listener. The dedicated loopback listener serves Admin and authentication routes.
- Match the Agent HTTPS URL to the LAN DNS SAN. Supply its provisioned CA using `USBSHIELD_TLS_CA_CERTIFICATE`. Desktop trusts the CA PEM from its launcher configuration and validates the loopback IP SAN.
- Run the [ACL verifier](../tests/verify-phase3-https-acl.sh) from Windows Central loopback and separately from an actual Agent workstation. Check that the LAN listener denies Admin routes, and then verify enrollment and heartbeat with a valid Agent credential. [Setup and acceptance steps](../../docs/IT_SETUP_GUIDE.md)
