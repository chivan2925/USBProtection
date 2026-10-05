# Week 1 Agent Event Contract

This folder contains the Member 2 Week 1 contract sample for Member 1 and Member 3.

## Meaning

The event describes:

```text
endpoint
+ active local Ubuntu session
+ USB device identity
+ USBGuard decision
+ event timestamp
```

`linuxUsername` is the username of the **active local Ubuntu session at event time**. It is not a claim that software can prove which physical person inserted the USB.

## Fields

- `endpointId`: production value will come from endpoint enrollment; Week 1 uses a placeholder.
- `hostname`: client hostname.
- `linuxUsername`, `linuxUid`, `sessionId`, `sessionType`, `seat`: active-session attribution.
- `device`: USB fingerprint/metadata.
- `eventType`: e.g. `CONNECTED`, `DISCONNECTED`.
- `decision`: e.g. `ALLOWED`, `BLOCKED`, `N/A`.
- `occurredAt`: ISO-8601 timestamp.

## Week 2 target

```text
UsbGuardEventListener
→ ActiveUserResolver
→ ClientUsbEvent
→ EventSender
→ Admin Server
```

The Week 1 JSON is a manual PoC contract. Week 2 must construct it atomically from the event-time context.
