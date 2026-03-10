# EV12 Backend API

Spring Boot backend for user/location/device management and SMS gateway integration.

## Update endpoints

The API now supports updating users, devices, and locations.

### 1) Update user

- **Method:** `PUT`
- **Path:** `/api/users/{userId}`

Request body fields are optional; only supplied fields are updated.

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

Notes:
- `userRole`: `1=SUPER_ADMIN`, `2=MANAGER`, `3=USER`
- To remove a location/manager, send `clearLocation: true` or `clearManager: true`
- Do not send `locationId` with `clearLocation: true`
- Do not send `managerId` with `clearManager: true`
- Role `3` users must still have a manager assigned

### 2) Update device

- **Method:** `PUT`
- **Path:** `/api/devices/{deviceId}`

```json
{
  "name": "Truck GPS 01",
  "phoneNumber": "+1555999000",
  "userId": 10
}
```

Notes:
- Any provided field is updated
- `userId` can be used to reassign the device to another user

### 3) Update location

- **Method:** `PUT`
- **Path:** `/api/locations/{locationId}`

```json
{
  "name": "East Warehouse",
  "details": "Dock 2 and 3"
}
```

Notes:
- Any provided field is updated
- To clear details, send `"details": ""`
- Location names remain unique (case-insensitive)
