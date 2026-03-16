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
  "managerId": 7
}
```

**Required fields**
- `firstName`, `lastName`, `email`, `password`, `userRole`

**Notes**
- `email` must be valid.
- `userRole`: `1=SUPER_ADMIN`, `2=MANAGER`, `3=USER`

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
  "phoneNumber": "+1555999000"
}
```

**Required fields**
- `name`, `phoneNumber`

---

### `POST /api/devices`
Create a device by specifying `userId` in body.

**Request body**
```json
{
  "userId": 10,
  "name": "Truck GPS 01",
  "phoneNumber": "+1555999000"
}
```

**Required fields**
- `userId`, `name`, `phoneNumber`

---

### `PUT /api/devices/{deviceId}`
Update device fields (partial update behavior).

**Request body (all optional)**
```json
{
  "name": "Truck GPS 01",
  "phoneNumber": "+1555999000",
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

---

### `GET /api/webhooks/ev12/events`
Return recent ingested EV12 webhook events.

**Query params**
- `limit` (optional, default `3`)

**Optional headers**
- `X-Webhook-Token`

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
- Token precedence on gateway-backed endpoints:
  1. `Authorization`
  2. `X-Gateway-Token`
  3. server config fallback
