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

Default datasource fallback is PostgreSQL local (`localhost:5432/smsbackend`).
Set these env vars for your target DB environment:
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
  "password": "StrongPassword123"
}
```

**Required fields**
- `email`, `password`

**Migration compatibility note**
- If a legacy user record still has plaintext password stored, first successful login auto-upgrades it to bcrypt.

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
- After saving location alarm config, keep your existing frontend cascade logic to update:
  - `Watches/*/branchAccountNumber` for matching `locationId`
  - `RelayBoards/*/conf/ban` for matching `conf.lo_idn`
- Those Watch/RelayBoard collections are not modeled in this backend schema yet, so continue updating them from frontend (or a separate service) exactly like your current function.

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
- After saving location alarm config, keep your existing frontend cascade logic to update:
  - `Watches/*/branchAccountNumber` for matching `locationId`
  - `RelayBoards/*/conf/ban` for matching `conf.lo_idn`
- Those Watch/RelayBoard collections are not modeled in this backend schema yet, so continue updating them from frontend (or a separate service) exactly like your current function.

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
- After saving location alarm config, keep your existing frontend cascade logic to update:
  - `Watches/*/branchAccountNumber` for matching `locationId`
  - `RelayBoards/*/conf/ban` for matching `conf.lo_idn`
- Those Watch/RelayBoard collections are not modeled in this backend schema yet, so continue updating them from frontend (or a separate service) exactly like your current function.

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
- After saving location alarm config, keep your existing frontend cascade logic to update:
  - `Watches/*/branchAccountNumber` for matching `locationId`
  - `RelayBoards/*/conf/ban` for matching `conf.lo_idn`
- Those Watch/RelayBoard collections are not modeled in this backend schema yet, so continue updating them from frontend (or a separate service) exactly like your current function.

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

