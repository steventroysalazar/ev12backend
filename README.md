# EV12 Backend API

Spring Boot backend for:
- Multi-company hierarchy (companies → locations → users → devices)
- Authentication and user/device/location/company management
- SMS gateway send/reply workflows
- EV12 webhook ingestion
- Device protocol configuration + SMS command delivery

## Hierarchy model
- `Company` is the top-level tenant.
- A company owns many `Location` records.
- Users belong to a company (except role 1 super admins).
- Devices belong to users, and therefore inherit company + location context.

## User roles
- `1 = SUPER_ADMIN` (global head/admin)
- `2 = COMPANY_ADMIN` (company-level admin; all locations or selected locations)
- `3 = PORTAL_USER` (web portal user)
- `4 = MOBILE_APP_USER` (mobile app only user)

## Base URL
All endpoints are relative to your API host, for example `http://localhost:8090`.

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

## Location APIs

### `POST /api/locations`
Create a location inside a company.

```json
{
  "name": "Dallas Hub",
  "details": "Main dispatch",
  "companyId": 10
}
```

### `PUT /api/locations/{locationId}`
Partial update location.

```json
{
  "name": "Dallas HQ",
  "details": "Updated details",
  "companyId": 10
}
```

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

