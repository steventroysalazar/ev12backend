# EV12 Backend API

Spring Boot backend for:
- Multi-company hierarchy (companies → locations → users → devices)
- Authentication and user/device/location/company management
- SMS gateway send/reply workflows
- EV12 webhook ingestion
- Device protocol configuration + SMS command delivery
- Company hierarchy support (companies → locations → users → devices)

## Base URL

All endpoints below are relative to your API host, for example:
- Local: `http://localhost:8090`

## Database migration (required for existing databases)

If you are upgrading an existing database and see errors like `Database unavailable`, run this idempotent SQL file in your PostgreSQL SQL editor first:

- `src/main/resources/db/manual/2026-04-19-company-hierarchy-backfill.sql`

This script creates/backfills `companies`, company/location/user linkage columns, role value migrations, and foreign keys/indexes required by the new company hierarchy model.
It also drops/recreates any legacy `app_users` role check constraints (name can vary per DB) so new role values (`COMPANY_ADMIN`, `PORTAL_USER`, `MOBILE_APP_USER`) stop failing inserts.

Default datasource fallback is local file-based H2 (for out-of-the-box startup).
For PostgreSQL (recommended for shared/dev/prod environments), set:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME` (typically `org.postgresql.Driver`)
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## Common headers

Some gateway/webhook endpoints support optional headers:
- `Authorization`: Gateway token (preferred)
- `X-Gateway-Token`: Legacy gateway token fallback
- `X-Gateway-Base-Url`: Override gateway base URL for this request
- `X-Webhook-Token`: Optional token used by webhook endpoints

---

## User roles
- `1 = SUPER_ADMIN` (global head/admin)
- `2 = COMPANY_ADMIN` (company-level admin; all locations or selected locations)
- `3 = PORTAL_USER` (web portal user)
- `4 = MOBILE_APP_USER` (mobile app only user)

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
  "companyId": 1,
  "locationId": 4,
  "allCompanyLocations": false,
  "managedLocationIds": [4, 9],
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
- `userRole`: `1=SUPER_ADMIN`, `2=COMPANY_ADMIN`, `3=PORTAL_USER`, `4=MOBILE_APP_USER`
- Role `1` (SUPER_ADMIN) has global access across all companies and locations.
- Roles `2/3/4` must include `companyId`.
- Role `2` can be scoped with `allCompanyLocations=false` + `managedLocationIds`.
- `device` is optional, but when provided it creates a device during registration.
- `device.deviceId` is stored as `externalDeviceId` and used to map EV12 webhook `deviceId` to this device.

---

### `POST /api/auth/login`
Authenticate and return auth payload.

**Request body**
```json
{
  "email": "john@example.com",
  "password": "StrongPassword123",
  "grant_type": "password",
  "scope": "type:1",
  "os_type": "iOS",
  "api_version": "iOS 15",
  "device_id": "APPLE_IPHONE11_EEB9E30F7CB649D6A7C7385369748D03"
}
```

**Required fields**
- `password`
- send one of: `email` or `username` (both map to user email lookup)

**Response shape**
```json
{
  "success": true,
  "token": "<base64-token>",
  "user": {
    "id": 7,
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "contactNumber": "+15550123",
    "address": "123 Main St",
    "userRole": 2,
    "companyId": 1,
    "locationId": 4,
    "allCompanyLocations": false,
    "managedLocationIds": [4, 6]
  },
  "loginContext": {
    "loginIdentifier": "john@example.com",
    "grantType": "password",
    "scope": "type:1",
    "osType": "iOS",
    "apiVersion": "iOS 15",
    "deviceId": "APPLE_IPHONE11_EEB9E30F7CB649D6A7C7385369748D03",
    "ipAddress": "203.0.113.5",
    "userAgent": "Mozilla/5.0 ...",
    "loggedAt": "2026-04-27T12:34:56.000Z"
  }
}
```

**Audit logging**
- Every successful login is stored in `login_logs`.
- Use `GET /api/auth/login-logs` for recent logs (`?userId=7` optional filter).

**Migration compatibility note**
- If a legacy user record still has plaintext password stored, first successful login auto-upgrades it to bcrypt.

### `POST /api/auth/fcm-token`
Save/update a user FCM token after login (for push notifications).

**Request body**
```json
{
  "userId": 7,
  "fcm_token": "f5Vx....",
  "device_id": "APPLE_IPHONE11_EEB9E30F7CB649D6A7C7385369748D03",
  "os_type": "iOS",
  "api_version": "iOS 15"
}
```

**Required fields**
- `userId`, `fcm_token`

---

## User APIs

### `GET /api/users`
List users.


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
  "companyId": 1,
  "locationId": 4,
  "allCompanyLocations": false,
  "managedLocationIds": [4, 9],
  "clearLocation": false
}
```

**Notes**
- Only supplied fields are updated.
- `clearLocation=true` removes assigned location.
- Do not send `locationId` together with `clearLocation=true`.
- Role `2` users may manage all company locations or a specific subset (`managedLocationIds`).

---


## Lookup APIs (lightweight dropdown/filter data)

These endpoints return compact records for frontend filter controls and select inputs, so you do not need to fetch full `/api/users` payloads.

### `GET /api/lookups/company-admins`
Returns only users with role `2` (COMPANY_ADMIN).

**Example response**
```json
[
  {
    "id": 7,
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "userRole": 2
  }
]
```

---

### `GET /api/lookups/portal-users`
Returns only users with role `3` (PORTAL_USER).

---

### `GET /api/lookups/mobile-users`
Returns only users with role `4` (MOBILE_APP_USER).

---

### `GET /api/lookups/super-admins`
Returns only users with role `1` (SUPER_ADMIN).

---

### `GET /api/lookups/companies`
Returns lightweight company list for selectors.

---

### `GET /api/lookups/locations`
Returns lightweight location list for location selectors.

**Example response**
```json
[
  {
    "id": 4,
    "name": "East Warehouse"
  }
]
```

---

### `GET /api/lookups/locations/{locationId}/users`
Returns lightweight users assigned to a specific location.

**Path params**
- `locationId` (required)

**Example response**
```json
[
  {
    "id": 21,
    "firstName": "Ari",
    "lastName": "Lane",
    "email": "ari@example.com",
    "userRole": 3
  }
]
```

---

### `GET /api/lookups/alerts`
Returns distinct active device alarm codes (`devices.alarmCode`) for current alert filtering.

**Example response**
```json
[
  "Fall-Down Alert",
  "SOS Alert"
]
```

---

### `GET /api/lookups/alert-logs`
Returns distinct values from alert logs for log filter controls.

**Example response**
```json
{
  "alarmCodes": ["Fall-Down Alert", "SOS Alert"],
  "actions": ["ALARM_CANCELLED", "ALARM_TRIGGERED"],
  "sources": ["MANUAL", "WEBHOOK"]
}
```

---

### Frontend feed examples for the new lookup endpoints

```ts
// Example: hydrate filter dropdowns in parallel
const locationId = 11;
const [companyAdmins, usersRole3, mobileUsers, superAdmins, companies, locations, locationUsers, alerts, alertLogFilters] = await Promise.all([
  api.get('/api/lookups/company-admins').then(r => r.data),
  api.get('/api/lookups/portal-users').then(r => r.data),
  api.get('/api/lookups/mobile-users').then(r => r.data),
  api.get('/api/lookups/super-admins').then(r => r.data),
  api.get('/api/lookups/companies').then(r => r.data),
  api.get('/api/lookups/locations').then(r => r.data),
  api.get(`/api/lookups/locations/${locationId}/users`).then(r => r.data),
  api.get('/api/lookups/alerts').then(r => r.data),
  api.get('/api/lookups/alert-logs').then(r => r.data)
]);

// companyAdmins/usersRole3/mobileUsers/superAdmins item shape:
// { id, firstName, lastName, email, userRole }

// locations item shape:
// { id, name }

// locationUsers item shape:
// { id, firstName, lastName, email, userRole }

// alerts item shape:
// string[]

// alertLogFilters shape:
// { alarmCodes: string[], actions: string[], sources: string[] }
```

```ts
// Example: convert lookup payload into <Select /> options
const companyAdminOptions = companyAdmins.map((m: any) => ({
  value: m.id,
  label: `${m.firstName} ${m.lastName} (${m.email})`
}));

const locationOptions = locations.map((loc: any) => ({
  value: loc.id,
  label: loc.name
}));

const alertOptions = alerts.map((code: string) => ({
  value: code,
  label: code
}));

const alertLogActionOptions = alertLogFilters.actions.map((action: string) => ({
  value: action,
  label: action
}));
```

---


### `GET /api/error-logs`
Returns recent backend API errors captured by global exception handling.

**Query params**
- `limit` (optional, default `100`, max `500`)

**Example response**
```json
[
  {
    "id": 71,
    "method": "POST",
    "path": "/api/devices/999/commands",
    "statusCode": 400,
    "errorType": "IllegalArgumentException",
    "errorMessage": "Device not found",
    "stackTrace": "java.lang.IllegalArgumentException: Device not found...",
    "occurredAt": "2026-04-23T14:26:55.001Z"
  }
]
```

### Frontend feed example for error logs

```ts
// fetch latest backend errors for an admin/dev diagnostics screen
const errorLogs = await api
  .get('/api/error-logs', { params: { limit: 150 } })
  .then(r => r.data);

// render-ready shape
const rows = errorLogs.map((log: any) => ({
  id: log.id,
  when: new Date(log.occurredAt).toLocaleString(),
  route: `${log.method} ${log.path}`,
  status: log.statusCode,
  type: log.errorType,
  message: log.errorMessage,
  stackTrace: log.stackTrace
}));
```

---

## Company APIs

### `POST /api/companies`
Create a company.

```json
{
  "name": "Acme Logistics",
  "details": "National operations"
}
```

### `PUT /api/companies/{companyId}`
Update company name/details.

### `GET /api/companies`
List companies with counts:
- `locationsCount`
- `usersCount`
- `devicesCount`

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
- Device responses now include `companyId` (derived from the assigned user).
- Device responses include alarm tracking fields for frontend state:
  - `alarmCode`: current active alarm (`SOS Alert`, `Fall-Down Alert`, or `null` when cancelled/idle)
  - `alarmTriggeredAt`: UTC timestamp when the current `alarmCode` was triggered (falls back to webhook `updatedAt`)
  - `alarmCancelledAt`: UTC timestamp when alarm was cancelled/cleared
- Device responses include latest telemetry location fields (auto-updated by EV12 webhook GPS payloads):
  - `latitude`: last known latitude from webhook `GPS Location`
  - `longitude`: last known longitude from webhook `GPS Location`
  - `locationUpdatedAt`: UTC timestamp when coordinates were last updated
- Device responses include EV lifecycle timestamps for battery/power diagnostics:
  - `lastPowerOnAt`: last webhook timestamp where alarm code matched power-on alert
  - `lastPowerOffAt`: last webhook timestamp where alarm code matched power-off alert
  - `lastDisconnectedAt`: last webhook timestamp where status was disconnected / `ECONNRESET`
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

## IMEI SMS auto-link flow (frontend integration)

When a new device is created (`POST /api/users/{userId}/devices` or `POST /api/devices`):
1. Backend immediately sends SMS command `V?` (uppercase) to that device phone number.
2. Backend background poller checks gateway inbound replies every ~30 seconds.
3. If reply text contains `IMEI:<value>` or `IMEI=<value>`, backend saves that value into `externalDeviceId` for the matching device phone number.

### Manual retry endpoint (resend `V?`)

Use this if automatic IMEI request or reply processing fails:

### `POST /api/devices/{deviceId}/imei-resend`

Sends `V?` to a specific device again.

**Response**
```json
{
  "success": true,
  "deviceId": 12,
  "phoneNumber": "+15551234567",
  "command": "V?",
  "sentAt": "2026-04-21T09:35:00Z"
}
```

### Frontend polling example after device create

```ts
const created = await api.post(`/api/users/${userId}/devices`, {
  name: 'Tracker 01',
  phoneNumber: '+15551234567'
}).then(r => r.data);

// backend is now auto-sending V? and polling replies in background

const waitForImei = async (deviceId: number) => {
  for (let i = 0; i < 20; i++) {
    const device = await api.get(`/api/devices/${deviceId}`).then(r => r.data);
    if (device.externalDeviceId) return device.externalDeviceId;
    await new Promise(resolve => setTimeout(resolve, 3000));
  }
  throw new Error('IMEI not linked yet');
};

// optional manual retry button
await api.post(`/api/devices/${created.id}/imei-resend`);
```

---

## Device Settings Defaults (for frontend prefill/reference)

Use this section to pre-populate frontend device settings when a device is first added.

### Audio defaults

| Setting | Default |
|---|---|
| Ringtone volume | `100` |
| Microphone volume | `10` *(mid-level on 0..15 scale)* |
| Speaker volume | `100` |

### Input / button behavior defaults

| Setting | Default |
|---|---|
| Feedback mode | `vibrate + voice prompt` |
| Long press time | `2.0s` *(20 × 0.1s)* |
| SOS task | `enabled` |
| SOS trigger mode | `long press` |

### Fall / motion defaults

| Setting | Default |
|---|---|
| Fall detection sensor | `enabled` |
| Fall wait time | configurable `10..600 sec` (`0` = disabled special case) |
| Fall sensitivity | `6` *(middle fallback for frontend prefill; firmware/provisioning may override)* |
| Motion detection level | `7` *(scale 1..16)* |

### Scanning / location defaults

| Setting | Default |
|---|---|
| Wi-Fi/BLE scan interval | `180 sec` |
| GPS interval | `180 sec` |
| BLE scan time | `5 sec` |
| BLE sleep time | `55 sec` |

### Time / alert defaults

| Setting | Default |
|---|---|
| General alert timer | `21600 sec` *(6 hours)* |
| Manual update threshold | `1200 sec` *(20 min)* |

### Alarm clock defaults

| Setting | Default |
|---|---|
| Alarm type | `standard alarm` |
| Hour | `0` |
| Minute | `0` |
| Reminder duration | `30 sec` |
| Voice repeat | `22` |
| Ringtone | `1` |

### Network / system defaults

| Setting | Default |
|---|---|
| Preferred network | `GSM/WCDMA/LTE (auto)` |
| CLIR (caller ID) | `network default` |
| Factory mode | `data-off mode` |

### Feature toggle defaults

| Setting | Default |
|---|---|
| VoLTE | `enabled` |
| Welfare check SMS ack | `off` |
| Debug LED | `off` |
| NFC | `off` |
| LoRa | `off` |

### Location feature defaults

| Setting | Default |
|---|---|
| Homecheck | `off` (`Homecheck0`) |
| Beacon | `off` (`beacon0`) |
| SMS GEO | `off` (`smsgeo0`) |
| Long SMS | `off` (`lsms0`) |

### Power alert defaults (EV-05)

| Setting | Default |
|---|---|
| Power-on alert | `off` |
| Power-off alert | `off` |

### Watch UI defaults (EV-05)

| Setting | Default |
|---|---|
| Time report | `off` |
| Raise hand detect | `off` |
| Tap screen wake | `off` |
| Step display | `off` |
| Heart display | `off` |
| Heart menu | `on` |
| Step menu | `on` |
| Contact menu | `on` |
| Weather menu | `off` |
| Settings menu | `on` |

> Note: fall sensitivity is not formally documented in exposed defaults. `6` is a practical middle prefill for UI consistency, but firmware/provisioning may override it.

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

## Company APIs

### `POST /api/companies`
Create a company.

**Request body**
```json
{
  "companyName": "Acme Logistics",
  "details": "National operations",
  "address": "123 Main St",
  "city": "Dallas",
  "state": "TX",
  "postalCode": "75001",
  "country": "USA",
  "phone": "+15551234567",
  "isAlarmReceiverIncluded": true
}
```

### `PUT /api/companies/{companyId}`
Update company profile fields (`companyName`, address, phone, AR include flag, etc.).

### `PUT /api/companies/{companyId}/alarm-receiver`
Update alarm receiver configuration and whitelists.

**Request body**
```json
{
  "alarmReceiverConfig": {
    "en": true,
    "server": {
      "xml": "MAS",
      "MAS": {
        "primary": { "url": "https://primary.example", "user": "u1", "pass": "p1" },
        "backup": { "url": "https://backup.example", "user": "u2", "pass": "p2" }
      }
    }
  },
  "dnsWhitelist": ["api.example.com", "backup.example.com"],
  "ipWhitelist": ["1.1.1.1", "8.8.8.8"],
  "alarmReceiverEnabled": true
}
```

### `GET /api/companies`
List companies with aggregate counts (`locationsCount`, `usersCount`, `devicesCount`) and full company profile/alarm-receiver config fields.

---

## Location APIs

## Company APIs

### `POST /api/companies`
Create a company.

**Request body**
```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3",
  "companyId": 1
}
```

**Required fields**
- `name`, `companyId`

---

### `PUT /api/companies/{companyId}/alarm-receiver`
Update alarm receiver configuration and whitelists.

**Request body**
```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3",
  "companyId": 1
}
```

**Notes**
- Any provided field is updated.
- To clear details, send `"details": ""`.
- Location names remain unique per company (case-insensitive).

---


### `PUT /api/locations/{locationId}/alarm-receiver`
Update location-specific alarm monitoring config (`Companies/{companyId}/ar/location/{locationId}` equivalent payload).

**Request body**
```json
{
  "accountNumber": "ACCT-001",
  "en": true,
  "users": "john,jane,dispatch",
  "toggleCompanyAlarmReceiver": true
}
```

**Behavior**
- Stores location-level alarm receiver config keys matching your frontend flow:
  - `account_number`
  - `en`
  - `users`
- If `toggleCompanyAlarmReceiver=true`, backend toggles company alarm receiver enable off->on to trigger re-init behavior.

**Frontend integration notes for your existing flow**
- Backend now also cascades the location `accountNumber` to all devices in that location as `branchAccountNumber` (watch-equivalent behavior).
- Relay board updates are intentionally not part of this endpoint.
- After this change, frontend only needs to call this API once, then use `GET /api/users/{userId}/devices`, `GET /api/locations/{locationId}/devices`, or `GET /api/devices` to read each device `branchAccountNumber`.

---

### `GET /api/locations`
List locations. Response includes `alarmReceiverConfig` object with `account_number`, `en`, and `users`.

---


### `PUT /api/locations/{locationId}/alarm-receiver`
Update location-specific alarm monitoring config (`Companies/{companyId}/ar/location/{locationId}` equivalent payload).

**Request body**
```json
{
  "accountNumber": "ACCT-001",
  "en": true,
  "users": "john,jane,dispatch",
  "toggleCompanyAlarmReceiver": true
}
```

**Behavior**
- Stores location-level alarm receiver config keys matching your frontend flow:
  - `account_number`
  - `en`
  - `users`
- If `toggleCompanyAlarmReceiver=true`, backend toggles company alarm receiver enable off->on to trigger re-init behavior.

**Frontend integration notes for your existing flow**
- Backend now also cascades the location `accountNumber` to all devices in that location as `branchAccountNumber` (watch-equivalent behavior).
- Relay board updates are intentionally not part of this endpoint.
- After this change, frontend only needs to call this API once, then use `GET /api/users/{userId}/devices`, `GET /api/locations/{locationId}/devices`, or `GET /api/devices` to read each device `branchAccountNumber`.

---

### `GET /api/locations`
List locations. Response includes `alarmReceiverConfig` object with `account_number`, `en`, and `users`.

---


### `PUT /api/locations/{locationId}/alarm-receiver`
Update location-specific alarm monitoring config (`Companies/{companyId}/ar/location/{locationId}` equivalent payload).

**Request body**
```json
{
  "accountNumber": "ACCT-001",
  "en": true,
  "users": "john,jane,dispatch",
  "toggleCompanyAlarmReceiver": true
}
```

**Behavior**
- Stores location-level alarm receiver config keys matching your frontend flow:
  - `account_number`
  - `en`
  - `users`
- If `toggleCompanyAlarmReceiver=true`, backend toggles company alarm receiver enable off->on to trigger re-init behavior.

**Frontend integration notes for your existing flow**
- Backend now also cascades the location `accountNumber` to all devices in that location as `branchAccountNumber` (watch-equivalent behavior).
- Relay board updates are intentionally not part of this endpoint.
- After this change, frontend only needs to call this API once, then use `GET /api/users/{userId}/devices`, `GET /api/locations/{locationId}/devices`, or `GET /api/devices` to read each device `branchAccountNumber`.

---

### `GET /api/locations`
List locations. Response includes `alarmReceiverConfig` object with `account_number`, `en`, and `users`.

---

## Company APIs

### `POST /api/companies`
Create a company.

**Request body**
```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3",
  "companyId": 1
}
```

**Required fields**
- `name`, `companyId`

---

### `PUT /api/companies/{companyId}/alarm-receiver`
Update alarm receiver configuration and whitelists.

**Request body**
```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3",
  "companyId": 1
}
```

**Notes**
- Any provided field is updated.
- To clear details, send `"details": ""`.
- Location names remain unique per company (case-insensitive).

---


### `PUT /api/locations/{locationId}/alarm-receiver`
Update location-specific alarm monitoring config (`Companies/{companyId}/ar/location/{locationId}` equivalent payload).

**Request body**
```json
{
  "accountNumber": "ACCT-001",
  "en": true,
  "users": "john,jane,dispatch",
  "toggleCompanyAlarmReceiver": true
}
```

**Behavior**
- Stores location-level alarm receiver config keys matching your frontend flow:
  - `account_number`
  - `en`
  - `users`
- If `toggleCompanyAlarmReceiver=true`, backend toggles company alarm receiver enable off->on to trigger re-init behavior.

**Frontend integration notes for your existing flow**
- Backend now also cascades the location `accountNumber` to all devices in that location as `branchAccountNumber` (watch-equivalent behavior).
- Relay board updates are intentionally not part of this endpoint.
- After this change, frontend only needs to call this API once, then use `GET /api/users/{userId}/devices`, `GET /api/locations/{locationId}/devices`, or `GET /api/devices` to read each device `branchAccountNumber`.

---

### `GET /api/locations`
List locations. Response includes `alarmReceiverConfig` object with `account_number`, `en`, and `users`.

---

## Location APIs

### `POST /api/locations`
Create a location.

**Request body**
```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3",
  "companyId": 1
}
```

**Required fields**
- `name`, `companyId`

### `POST /api/companies`
Create a company.

**Request body**
```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3",
  "companyId": 1
}
```

**Notes**
- Any provided field is updated.
- To clear details, send `"details": ""`.
- Location names remain unique per company (case-insensitive).

---

### `GET /api/companies`
List companies with aggregate counts (`locationsCount`, `usersCount`, `devicesCount`).

---

## Location APIs

### `POST /api/locations`
Create a location inside a company.

```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3",
  "companyId": 1
}
```

**Required fields**
- `name`, `companyId`

---

### `PUT /api/locations/{locationId}`
Partial update location.

```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3",
  "companyId": 1
}
```

**Notes**
- Any provided field is updated.
- To clear details, send `"details": ""`.
- Location names remain unique per company (case-insensitive).

---

### `GET /api/locations`
Returns locations including `companyId`, `usersCount`, `devicesCount`.

---

## Authentication APIs

### `POST /api/auth/register`
Create a user.

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "password": "StrongPassword123",
  "contactNumber": "+15550123",
  "address": "123 Main St",
  "userRole": 2,
  "companyId": 10,
  "locationId": 4,
  "managerId": null,
  "allCompanyLocations": false,
  "managedLocationIds": [4, 6],
  "device": {
    "name": "Truck GPS 01",
    "phoneNumber": "+1555999000",
    "deviceId": "862667084205114"
  }
}
```

Notes:
- Roles `2/3/4` require `companyId`.
- Roles `3/4` must be assigned a role `2` manager (`managerId`).
- For role `2`, set:
  - `allCompanyLocations=true` to manage every company location, or
  - `allCompanyLocations=false` + `managedLocationIds` for scoped access.

### `POST /api/auth/login`
Authenticate user.

Example:
```json
{
  "email": "john@example.com",
  "password": "StrongPassword123",
  "grant_type": "password",
  "scope": "type:1",
  "os_type": "iOS",
  "api_version": "iOS 15",
  "device_id": "APPLE_IPHONE11_EEB9E30F7CB649D6A7C7385369748D03"
}
```

- Required: `password` and one of `email` or `username`.
- Response now includes `loginContext` for audit visibility.
- Successful logins are persisted and can be read from `GET /api/auth/login-logs` (optional `userId` filter).

### `POST /api/auth/fcm-token`
Persist frontend/device FCM token.

```json
{
  "userId": 7,
  "fcm_token": "f5Vx....",
  "device_id": "APPLE_IPHONE11_EEB9E30F7CB649D6A7C7385369748D03",
  "os_type": "iOS",
  "api_version": "iOS 15"
}
```

### `GET /api/auth/login-logs`
Return latest 200 login audit logs (or by `userId`).

---

## User APIs

### `GET /api/users`
List users.

Optional query param:
- `managerId`: list users assigned to one company admin.

### `PUT /api/users/{userId}`
Partial update user.

Supported fields:
- basic profile fields
- `userRole`
- `companyId`
- `locationId` / `clearLocation`
- `managerId` / `clearManager`
- `allCompanyLocations`
- `managedLocationIds` / `clearManagedLocations`

Response shape includes:
- `companyId`
- `allCompanyLocations`
- `managedLocationIds`

---

## Device APIs
Device responses now include `companyId` for easier tenant-aware frontend filtering.

---

## Lookup APIs

### `GET /api/lookups/company-admins`
Role 2 users.

### `GET /api/lookups/portal-users`
Role 3 users.

### `GET /api/lookups/mobile-users`
Role 4 users.

### `GET /api/lookups/super-admins`
Role 1 users.

### `GET /api/lookups/companies`
Lightweight company list: `{ id, name }`.

### `GET /api/lookups/locations`
Lightweight location list: `{ id, name }`.

### `GET /api/lookups/locations/{locationId}/users`
Users assigned to a location.

### `GET /api/lookups/alerts`
Distinct active `alarmCode` values.

### `GET /api/lookups/alert-logs`
Distinct filters from alert logs.

---

## SIM activation/deactivation (Cisco Control Center)

This backend now supports SIM lifecycle management per device plus bulk activation/deactivation.

### 1) New device SIM fields
Every `DeviceResponse` now includes:
- `simIccid` (string | null)
- `simStatus` (string | null)
- `simActivated` (boolean)
- `simStatusUpdatedAt` (ISO date-time | null)

> `simIccid` is required for Cisco lookup/activation/deactivation endpoints.

### 2) Configure Cisco Control Center credentials
Set these env vars in backend runtime:

- `CISCO_CC_ENABLED=true`
- `CISCO_CC_HOST=your-control-center-host`
- `CISCO_CC_USERNAME=your_username`
- `CISCO_CC_API_KEY=your_api_key`
- Optional overrides:
  - `CISCO_CC_DEVICE_DETAILS_PATH=/rws/api/v1/devices/{iccid}`
  - `CISCO_CC_ACTIVATE_PATH=/rws/api/v1/devices/{iccid}/activate`
  - `CISCO_CC_DEACTIVATE_PATH=/rws/api/v1/devices/{iccid}/deactivate`
  - `CISCO_CC_TIMEOUT_MS=30000`

### 3) Backend endpoints

#### Refresh SIM status from Cisco
`GET /api/devices/{deviceId}/sim/status`

Returns:
```json
{
  "deviceId": 101,
  "simIccid": "8988216716970004975",
  "msisdn": "882351697004975",
  "status": "ACTIVATED",
  "activated": true,
  "updatedAt": "2026-04-22T10:15:30Z"
}
```

#### Activate one SIM
`POST /api/devices/{deviceId}/sim/activate`

#### Deactivate one SIM
`POST /api/devices/{deviceId}/sim/deactivate`

Both return `SimStatusResponse`.

#### Bulk activate/deactivate
`POST /api/devices/sim/bulk`

Request:
```json
{
  "deviceIds": [101, 102, 103],
  "activate": true
}
```

Response (per-device success/failure):
```json
[
  {
    "deviceId": 101,
    "success": true,
    "error": null,
    "status": {
      "deviceId": 101,
      "simIccid": "8988216716970004975",
      "msisdn": "882351697004975",
      "status": "ACTIVATED",
      "activated": true,
      "updatedAt": "2026-04-22T10:15:30Z"
    }
  },
  {
    "deviceId": 102,
    "success": false,
    "error": "Device does not have simIccid. Update device.simIccid first.",
    "status": null
  }
]
```

---

## Frontend integration guide (React/TypeScript)

### Device create/update payload
When creating or editing devices, include `simIccid` so SIM APIs can work:

```ts
await api.post('/api/devices', {
  userId,
  name,
  phoneNumber,
  externalDeviceId,
  simIccid, // required for Cisco SIM controls
});
```

```ts
await api.patch(`/api/devices/${deviceId}`, {
  simIccid,
});
```

### Show SIM node on device card/table
Use these fields from `DeviceResponse`:
- `simActivated` for badge/toggle state
- `simStatus` for raw provider status text
- `simStatusUpdatedAt` for "last synced"

Example:
```ts
const isSmsUsable = device.simActivated;
```

### Single-device actions
```ts
export async function activateSim(deviceId: number) {
  return api.post(`/api/devices/${deviceId}/sim/activate`).then(r => r.data);
}

export async function deactivateSim(deviceId: number) {
  return api.post(`/api/devices/${deviceId}/sim/deactivate`).then(r => r.data);
}

export async function refreshSimStatus(deviceId: number) {
  return api.get(`/api/devices/${deviceId}/sim/status`).then(r => r.data);
}
```

### Bulk actions
```ts
export async function bulkSetSimActivation(deviceIds: number[], activate: boolean) {
  return api.post('/api/devices/sim/bulk', { deviceIds, activate }).then(r => r.data);
}
```

Recommended UX:
1. User selects N devices.
2. Click **Bulk Activate** or **Bulk Deactivate**.
3. Call `/api/devices/sim/bulk`.
4. Show result counters: success/fail.
5. For failed rows show `error` text and allow retry.

### SMS usability guard in UI
Disable SMS command buttons when SIM is inactive:
```ts
const canSendSms = device.simActivated === true;
```

If disabled, show helper text:
> "SIM is not activated. Activate SIM to enable SMS features."
