# EV12 Backend API

Spring Boot backend for:
- Authentication and user management
- Location and device management
- SMS gateway send/reply workflows
- EV12 webhook ingestion
- Device protocol configuration + SMS command delivery

## Base URL

All endpoints below are relative to your API host, for example:
- Local: `http://localhost:8080`

## Common headers

Some gateway/webhook endpoints support optional headers:
- `Authorization`: Gateway token (preferred)
- `X-Gateway-Token`: Legacy gateway token fallback
- `X-Gateway-Base-Url`: Override gateway base URL for this request
- `X-Webhook-Token`: Optional token used by webhook endpoints

---

## Authentication APIs

### `POST /api/auth/register`
Create a new user.

**Request body**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "password": "StrongPassword123",
  "contactNumber": "+15550123",
  "address": "123 Main St",
  "userRole": 2,
  "locationId": 4,
  "managerId": 7,
  "device": {
    "name": "Truck GPS 01",
    "phoneNumber": "+1555999000",
    "deviceId": "862667084205114"
  }
}
```

**Required fields**
- `firstName`, `lastName`, `email`, `password`, `userRole`

**Notes**
- `email` must be valid.
- `userRole`: `1=SUPER_ADMIN`, `2=MANAGER`, `3=USER`
- `device` is optional, but when provided it creates a device during registration.
- `device.deviceId` is stored as `externalDeviceId` and used to map EV12 webhook `deviceId` to this device.

---

### `POST /api/auth/login`
Authenticate and return auth payload.

**Request body**
```json
{
  "email": "john@example.com",
  "password": "StrongPassword123"
}
```

**Required fields**
- `email`, `password`

---

## User APIs

### `GET /api/users`
List users.

**Query params**
- `managerId` (optional): filter users by manager

---

### `PUT /api/users/{userId}`
Update user fields (partial update behavior).

**Request body (all optional)**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "contactNumber": "+15550123",
  "address": "123 Main St",
  "userRole": 2,
  "locationId": 4,
  "managerId": 7,
  "clearLocation": false,
  "clearManager": false
}
```

**Notes**
- Only supplied fields are updated.
- `clearLocation=true` removes assigned location.
- `clearManager=true` removes assigned manager.
- Do not send `locationId` together with `clearLocation=true`.
- Do not send `managerId` together with `clearManager=true`.
- Role `3` users must still have a manager.

---

## Device APIs

### `POST /api/users/{userId}/devices`
Create a device for a specific user.

**Request body**
```json
{
  "name": "Truck GPS 01",
  "phoneNumber": "+1555999000",
  "externalDeviceId": "862667084205114"
}
```

**Required fields**
- `name`, `phoneNumber`

**Optional fields**
- `externalDeviceId`: EV12 `deviceId` used for webhook alarm mapping

---

### `POST /api/devices`
Create a device by specifying `userId` in body.

**Request body**
```json
{
  "userId": 10,
  "name": "Truck GPS 01",
  "phoneNumber": "+1555999000",
  "externalDeviceId": "862667084205114"
}
```

**Required fields**
- `userId`, `name`, `phoneNumber`

**Optional fields**
- `externalDeviceId`: EV12 `deviceId` used for webhook alarm mapping

---

### `PUT /api/devices/{deviceId}`
Update device fields (partial update behavior).

**Request body (all optional)**
```json
{
  "name": "Truck GPS 01",
  "phoneNumber": "+1555999000",
  "externalDeviceId": "862667084205114",
  "userId": 10,
  "protocolSettings": {
    "contacts": [
      {
        "slot": 1,
        "smsEnabled": true,
        "callEnabled": true,
        "phone": "123456789",
        "name": "Emma"
      }
    ],
    "smsPassword": "123456",
    "requestLocation": true,
    "workingMode": "mode2",
    "timeZone": "+1"
  }
}
```

**Notes**
- Any provided field is updated.
- `userId` reassigns the device.
- `protocolSettings` persists EV protocol profile on the device record.
- `externalDeviceId` links the API device to EV12 webhook payload `deviceId`.
- Device responses include alarm tracking fields for frontend state:
  - `alarmCode`: current active alarm (`SOS Alert`, `Fall-Down Alert`, or `null` when cancelled/idle)
- Device responses include latest telemetry location fields (auto-updated by EV12 webhook GPS payloads):
  - `latitude`: last known latitude from webhook `GPS Location`
  - `longitude`: last known longitude from webhook `GPS Location`
  - `locationUpdatedAt`: UTC timestamp when coordinates were last updated
- Device responses now include config queue tracking fields:
  - `configStatus`: `IDLE`, `PENDING`, `APPLIED`
  - `configLastSentAt`: UTC timestamp of the last configuration SMS send
  - `configAppliedAt`: UTC timestamp when a device reply confirmed config applied
- `contacts` supports up to 10 entries (`A1..A10`).
- Legacy single-contact fields are still accepted in protocol settings (`contactNumber`, `contactSlot`, `contactSmsEnabled`, `contactCallEnabled`, `contactName`).

---

### `GET /api/devices`
List all devices.

---

### `GET /api/users/{userId}/devices`
List devices for one user.

---

### `GET /api/locations/{locationId}/devices`
List devices for all users under a location.

---


## Live alarm stream (frontend "always listening")

Use this when you want the frontend to keep receiving alarm changes globally (not tied to one screen).

### `GET /api/alarms/stream`
Server-Sent Events (SSE) endpoint for real-time alarm updates.

**Response content type**
- `text/event-stream`

**Events emitted**
- `connected`: sent once when stream is connected
- `alarm-update`: sent whenever a device alarm code changes

**`alarm-update` payload**
```json
{
  "deviceId": 12,
  "externalDeviceId": "862667084205114",
  "alarmCode": "SOS Alert",
  "updatedAt": "2026-03-22T09:10:11.000Z"
}
```

**Alarm code behavior**
- `SOS Alert` => active SOS
- `Fall-Down Alert` => active fall detection
- `null` => alarm cleared (for example after `SOS Ending` webhook)

### Frontend integration pattern (global listener)
Create the stream once at app root (e.g. App provider / layout), then update global state/store so every page gets live changes.

```ts
// alarmStream.ts
export function startAlarmStream(onAlarmUpdate: (update: {
  deviceId: number;
  externalDeviceId: string;
  alarmCode: string | null;
  updatedAt: string;
}) => void) {
  const baseUrl = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
  const source = new EventSource(`${baseUrl}/api/alarms/stream`);

  source.addEventListener("alarm-update", (event) => {
    const data = JSON.parse((event as MessageEvent).data);
    onAlarmUpdate(data);
  });

  // Optional: useful for debugging connection lifecycle
  source.addEventListener("connected", () => {
    console.log("Alarm stream connected");
  });

  source.onerror = () => {
    // Browser EventSource automatically retries connection
    console.warn("Alarm stream disconnected. Retrying...");
  };

  return () => source.close();
}
```

```ts
// Example usage in app bootstrap (React)
// Call this once so app keeps listening even when routes/pages change
const stop = startAlarmStream((update) => {
  // Update your global store by deviceId
  // e.g. setDeviceAlarm(update.deviceId, update.alarmCode)
});

// on app cleanup/logout:
// stop();
```

> Recommended UX: show a persistent alarm badge/toast and a global sound/vibration trigger based on `alarm-update`, not per-page polling.

---
## Location APIs

### `POST /api/locations`
Create a location.

**Request body**
```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3"
}
```

**Required fields**
- `name`

---

### `PUT /api/locations/{locationId}`
Update location fields.

**Request body (all optional)**
```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3"
}
```

**Notes**
- Any provided field is updated.
- To clear details, send `"details": ""`.
- Location names remain unique (case-insensitive).

---

### `GET /api/locations`
List locations.

---

## Message/Gateway APIs

### `POST /api/messages/send`
Send an SMS through configured gateway.

**Request body**
```json
{
  "to": "+1555999000",
  "message": "Hello device",
  "slot": 1
}
```

**Required fields**
- `to`, `message`

**Optional headers**
- `Authorization`
- `X-Gateway-Token`
- `X-Gateway-Base-Url`

---

### `GET /api/messages/replies`
Fetch inbound reply messages from gateway.

**Query params**
- `phone` (optional)
- `since` (optional)
- `limit` (optional)

**Optional headers**
- `Authorization`
- `X-Gateway-Token`
- `X-Gateway-Base-Url`

---

### `GET /api/messages/health`
Checks whether the gateway is reachable.

**Optional headers**
- `Authorization`
- `X-Gateway-Token`
- `X-Gateway-Base-Url`

---

### `GET /api/messages/debug/config`
Returns resolved gateway configuration and token preview for debugging.

**Optional headers**
- `Authorization`
- `X-Gateway-Token`
- `X-Gateway-Base-Url`

---

## Device Config APIs

### `POST /api/send-config`
Persist a device protocol profile and send generated SMS commands to the device phone number.

**Required request field**
- `deviceId`

**Request body shape**
- Accepts all EV protocol fields used by UI/profile persistence, including:
  - `contacts` array (`slot`, `smsEnabled`, `callEnabled`, `phone`, `name`)
  - legacy contact fields (`contactNumber`, `contactSlot`, `contactSmsEnabled`, `contactCallEnabled`, `contactName`)
  - device metadata fields (`imei`, `eviewVersion`)
  - safety, positioning, network, mode, and device behavior fields (for example: `requestLocation`, `wifiEnabled`, `sosMode`, `geoFenceEnabled`, `apn`, `workingMode`, `timeZone`, `heartRateEnabled`, `checkStatus`, etc.)

**Example request**
```json
{
  "deviceId": 123,
  "imei": "860000000000001",
  "eviewVersion": "1.0.5",
  "contacts": [
    {
      "slot": 1,
      "smsEnabled": true,
      "callEnabled": true,
      "phone": "123456789",
      "name": "Emma"
    }
  ],
  "smsPassword": "123456",
  "requestLocation": true,
  "wifiEnabled": true,
  "sosMode": 1,
  "geoFenceEnabled": true,
  "workingMode": "mode2",
  "timeZone": "+1",
  "checkStatus": true
}
```

**Optional headers**
- `Authorization`
- `X-Gateway-Token`
- `X-Gateway-Base-Url`

**Behavior**
1. Loads device by `deviceId`.
2. Stores submitted config as `protocolSettings` on device.
3. Builds command sequence.
4. Splits commands into SMS-sized parts.
5. Sends all command SMS messages through gateway.
6. Marks device config status as `PENDING` and starts resend cooldown timer.

---

### `GET /api/devices/{deviceId}/config-status`
Returns current queue status for the latest configuration request and auto-checks for device reply confirmation.

**Optional headers**
- `Authorization`
- `X-Gateway-Token`
- `X-Gateway-Base-Url`

**Behavior**
- If status is `PENDING`, backend fetches inbound messages for the device number since the last config send time.
- If at least one reply exists, status becomes `APPLIED` and `configAppliedAt` is stored.
- Response includes `nextResendAt` so frontend can enable/disable resend button.

**Example response**
```json
{
  "deviceId": 123,
  "status": "PENDING",
  "pending": true,
  "lastSentAt": "2026-03-12T09:30:00Z",
  "appliedAt": null,
  "nextResendAt": "2026-03-12T09:35:00Z",
  "commandPreview": "A1,1,1,123456789,Emma,P123456,loc"
}
```

---

### `POST /api/devices/{deviceId}/config-resend`
Resends the pending device configuration SMS command payload.

**Optional headers**
- `Authorization`
- `X-Gateway-Token`
- `X-Gateway-Base-Url`

**Rules**
- Works only if latest config status is `PENDING`.
- Can be called only once every 5 minutes (HTTP `429` if called too soon).
- Before resending, backend auto-checks for confirmation reply; if already confirmed, resend is rejected.

**Example response**
```json
{
  "success": true,
  "deviceId": 123,
  "status": "PENDING",
  "sentAt": "2026-03-12T09:35:00Z",
  "messages": [
    { "message": "A1,1,1,123456789,Emma,P123456,loc" }
  ]
}
```

---

### `GET /api/inbound-messages`
Fetch normalized inbound messages (timestamp formatted as ISO offset datetime).

**Query params**
- `since` (optional; accepts epoch seconds or epoch milliseconds)
- `phone` (optional)
- `limit` (optional)

**Optional headers**
- `Authorization`
- `X-Gateway-Token`
- `X-Gateway-Base-Url`

---

## EV12 Webhook APIs

### `POST /api/webhooks/ev12`
Ingest EV12 webhook payload in any content type.

**Consumes**
- `*/*` (raw body supported)

**Optional headers**
- `Content-Type`
- `X-Webhook-Token`
- Any other raw headers are captured

**Response**
- HTTP `201 Created`
- Includes `{ "success": true, "event": ... }`
- Alarm update processing always runs in background from the webhook payload.
- Device location update processing also runs from the same webhook payload when `GPS Location` exists.
- Event history storage is configurable:
  - `webhook.ev12-persist-events=true`: save in database table `ev12_webhook_events`
  - `webhook.ev12-persist-events=false` (default): keep only in-memory recent history

**Webhook debug fields in `event`**
- `event.alarmAttempts`: array describing what backend detected and attempted from the webhook payload.
- `candidateIndex`: index of parsed payload candidate (helps when webhook contains nested/array entries).
- `externalDeviceId`: device id extracted from webhook (what backend tries to match with device `externalDeviceId`).
- `alarmCode`: alarm code extracted (`SOS Alert`, `SOS Ending`, `Fall-Down Alert`, etc.).
- `eventTimestamp`: timestamp backend will use for update ordering.
- `action`: backend action (`applied` or `ignored`).
- `reason`: short explanation (for example `missing deviceId`, `missing alarm code`, `alarm code does not contain sos/fall`, `malformed or non-json payload`).

**Example response (debug-friendly)**
```json
{
  "success": true,
  "event": {
    "id": 17,
    "receivedAt": "2026-03-23T11:15:22.112Z",
    "payloadJson": "{\"rawHeaders\":{\"x-webhook-token\":\"***\"},\"contentType\":\"application/json\",\"rawBody\":\"{\\\"deviceId\\\":\\\"862667084205114\\\",\\\"data\\\":{\\\"Alarm Code\\\":[\\\"SOS Alert\\\"]}}\"}",
    "alarmAttempts": [
      {
        "candidateIndex": 0,
        "externalDeviceId": "862667084205114",
        "alarmCode": "SOS Alert",
        "eventTimestamp": "2026-03-23T11:15:22.111Z",
        "action": "applied",
        "reason": "alarm code updated"
      }
    ]
  }
}
```

**Webhook debug fields in `event`**
- `event.alarmAttempts`: array describing what backend detected and attempted from the webhook payload.
- `candidateIndex`: index of parsed payload candidate (helps when webhook contains nested/array entries).
- `externalDeviceId`: device id extracted from webhook (what backend tries to match with device `externalDeviceId`).
- `alarmCode`: alarm code extracted (`SOS Alert`, `SOS Ending`, `Fall-Down Alert`, etc.).
- `eventTimestamp`: timestamp backend will use for update ordering.
- `action`: backend action (`queued` or `ignored`).
- `reason`: short explanation (for example `missing deviceId`, `missing alarm code`, `alarm code does not contain sos/fall`, `malformed or non-json payload`).

**Example response (debug-friendly)**
```json
{
  "success": true,
  "event": {
    "id": 17,
    "receivedAt": "2026-03-23T11:15:22.112Z",
    "payloadJson": "{\"rawHeaders\":{\"x-webhook-token\":\"***\"},\"contentType\":\"application/json\",\"rawBody\":\"{\\\"deviceId\\\":\\\"862667084205114\\\",\\\"data\\\":{\\\"Alarm Code\\\":[\\\"SOS Alert\\\"]}}\"}",
    "alarmAttempts": [
      {
        "candidateIndex": 0,
        "externalDeviceId": "862667084205114",
        "alarmCode": "SOS Alert",
        "eventTimestamp": "2026-03-23T11:15:22.111Z",
        "action": "queued",
        "reason": "alarm update enqueued"
      }
    ]
  }
}
```

---


**Alarm code tracking behavior**
- When webhook `data["Alarm Code"]` includes `SOS Alert` and does **not** include `SOS Ending`, device `alarmCode` is set to `SOS Alert`.
- When webhook `data["Alarm Code"]` includes `Fall-Down Alert` (and no `SOS Ending`), device `alarmCode` is set to `Fall-Down Alert`.
- When webhook has only `SOS Ending`, it is ignored for active alarm counting.
- Alarm updates apply only when the webhook `deviceId` matches a device `externalDeviceId`.

**Device GPS location auto-update behavior**
- If webhook payload contains `data["GPS Location"]` (array or object), backend reads the first valid coordinate object.
- Supported coordinate keys: `latitude` + `longitude` (also accepts `lat` and `lng`/`lon`).
- Backend updates matching device fields:
  - `latitude`
  - `longitude`
  - `locationUpdatedAt` (uses webhook timestamp when available, otherwise server receive time)
- Device matching uses webhook `deviceId` against device `externalDeviceId`.

**Frontend sample device response (new fields)**
```json
{
  "id": 12,
  "userId": 10,
  "name": "Truck GPS 01",
  "phoneNumber": "+1555999000",
  "externalDeviceId": "862667084205114",
  "alarmCode": "Fall-Down Alert",
  "alarmCancelledAt": null,
  "latitude": 15.1468038,
  "longitude": 120.5463361,
  "locationUpdatedAt": "2026-03-25T12:48:17.268Z",
  "protocolSettings": null,
  "configStatus": "IDLE",
  "configLastSentAt": null,
  "configAppliedAt": null
}
```

### `GET /api/webhooks/ev12/events`
Return recent ingested EV12 webhook events.

**Query params**
- `limit` (optional; defaults to `200`)
- Max `limit` is `500` per request.
- Reads latest events from DB or from in-memory history, depending on `webhook.ev12-persist-events`.

**Optional headers**
- `X-Webhook-Token`

**Common failure mode**
- If storage is temporarily unavailable, API returns `503 Service Unavailable` with JSON:
  - `error: "Database unavailable"`
  - `message: "The service cannot access its database right now. Please retry shortly."`
- If this API is behind Vercel/Azure/NGINX, that upstream `503` can appear in browser devtools as `502 Bad Gateway`.

**Should you clear the table?**
- Usually **no**: clearing `ev12_webhook_events` does not fix DB connectivity/auth/network failures.
- It can still help if your history table became very large and requests are timing out.
- Preferred cleanup path is `DELETE /api/webhooks/ev12/events` (same webhook token rules).
- Direct SQL fallback:
  - `DELETE FROM ev12_webhook_events;`
  - `VACUUM (ANALYZE) ev12_webhook_events;` (Postgres)
- If `webhook.ev12-persist-events=false`, there is no DB table write for webhook history; `DELETE /api/webhooks/ev12/events` clears in-memory history only.

---
### `DELETE /api/webhooks/ev12/events`
Clear stored EV12 webhook history.

**Optional headers**
- `X-Webhook-Token`

**Response**
- HTTP `200 OK`
- Includes `{ "success": true, "deleted": <count> }`

---

## Notes for frontend integration

- Device list/read APIs return persisted `protocolSettings` when available.
- `/api/send-config` is the preferred "save profile + send SMS commands" flow.

- For a "Delete Webhook History" button in your frontend, call `DELETE /api/webhooks/ev12/events` and then refresh the table/list with `GET /api/webhooks/ev12/events`.
- For webhook troubleshooting UI, render `event.alarmAttempts` in a table and show:
  - extracted `externalDeviceId`
  - extracted `alarmCode`
  - `action` + `reason` (this tells you what backend tried to do and why).
  - If `action=ignored`, the `reason` explains exactly why it was not written to DB (example: `no matching device found by externalDeviceId`).
  - If `action=applied`, DB update already happened in the same webhook request.
- Token precedence on gateway-backed endpoints:
  1. `Authorization`
  2. `X-Gateway-Token`
  3. server config fallback

### `GET /api/devices/{deviceId}/alarm-logs`
Returns historical alarm log entries for one device (newest first).

**What gets logged**
- Alarm triggers from EV12 webhook (includes alarm code + snapshot coordinates if available).
- Manual alarm cancellation via `PUT/PATCH /api/devices/{deviceId}` when `alarmCode` is cleared.

**Response item**
```json
{
  "id": 101,
  "deviceId": 12,
  "externalDeviceId": "862667084205114",
  "alarmCode": "SOS Alert",
  "action": "ALARM_TRIGGERED",
  "source": "WEBHOOK",
  "latitude": 15.1468038,
  "longitude": 120.5463361,
  "eventAt": "2026-04-01T12:00:00Z",
  "note": "Alarm state updated from EV12 webhook"
}
```

---

### `GET /api/devices/{deviceId}/location-breadcrumbs`
Returns location breadcrumb history for one device (newest first).

**What gets logged**
- Every coordinate update captured from EV12 webhook GPS payloads.
- Coordinates parsed from inbound SMS messages (when the sender phone matches a device phone number).

**Response item**
```json
{
  "id": 301,
  "deviceId": 12,
  "externalDeviceId": "862667084205114",
  "latitude": 15.1468038,
  "longitude": 120.5463361,
  "source": "WEBHOOK",
  "capturedAt": "2026-04-01T12:00:00Z",
  "gatewayMessageId": null
}
```
