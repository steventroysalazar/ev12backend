# EV12 Backend API

Spring Boot backend for user/location/device management and SMS gateway integration.

## Update endpoints

The API supports updating users, devices, and locations.

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
  "userId": 10,
  "protocolSettings": {
    "contacts": [
      {
        "slot": 1,
        "smsEnabled": true,
        "callEnabled": true,
        "phone": "123456789",
        "name": "Emma"
      },
      {
        "slot": 2,
        "smsEnabled": true,
        "callEnabled": false,
        "phone": "123456780",
        "name": "Dad"
      }
    ],
    "contactNumber": "123456789",
    "contactSlot": 1,
    "contactSmsEnabled": true,
    "contactCallEnabled": true,
    "contactName": "Emma",
    "smsPassword": "123456",
    "smsWhitelistEnabled": true,
    "requestLocation": true,
    "requestGpsLocation": false,
    "requestLbsLocation": false,
    "wifiEnabled": true,
    "bluetoothEnabled": false,
    "micVolume": 10,
    "speakerVolume": 90,
    "vibrationEnabled": true,
    "beepEnabled": true,
    "prefixEnabled": true,
    "prefixName": "Emma",
    "checkBattery": true,
    "sosMode": 1,
    "sosActionTime": 20,
    "sosCallRingTime": "35S",
    "sosCallTalkTime": "20M",
    "fallDownEnabled": true,
    "fallDownSensitivity": 5,
    "fallDownCall": true,
    "noMotionEnabled": true,
    "noMotionTime": "80M",
    "noMotionCall": true,
    "motionEnabled": false,
    "motionStaticTime": "",
    "motionDurationTime": "",
    "motionCall": false,
    "overSpeedEnabled": true,
    "overSpeedLimit": "100km/h",
    "geoFenceEnabled": true,
    "geoFenceMode": 0,
    "geoFenceRadius": "100m",
    "apnEnabled": true,
    "apn": "internet",
    "serverEnabled": true,
    "serverHost": "www.smart-locator.com",
    "serverPort": 6060,
    "gprsEnabled": true,
    "workingMode": "mode2",
    "workingModeInterval": "03M",
    "workingModeNoMotionInterval": "01H",
    "continuousLocateInterval": "10S",
    "continuousLocateDuration": "600S",
    "timeZone": "+1",
    "turnOffDevice": false,
    "findMyDevice": true,
    "heartRateEnabled": true,
    "heartRateInterval": "10M",
    "stepDetectionEnabled": true,
    "stepDetectionInterval": "10M",
    "checkStatus": true
  }
}
```

Notes:
- Any provided field is updated.
- `userId` can reassign the device to another user.
- `protocolSettings` lets frontend persist EV-07B / EV-04 / EV-05 SMS configuration profile per device.
- Use `protocolSettings.contacts` to manage up to 10 contact slots (`A1`..`A10`) in one payload.
- Legacy single-contact fields (`contactNumber`, `contactSlot`, `contactSmsEnabled`, `contactCallEnabled`, `contactName`) are still accepted for backward compatibility.
- Device list/read APIs now include `protocolSettings` in each `DeviceResponse`.

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

---

## Send configuration and persist device profile

### Endpoint

- **Method:** `POST`
- **Path:** `/api/send-config`

### What happens now

When frontend submits EV SMS protocol config to `/api/send-config`:
1. Backend loads the device by `deviceId`.
2. Backend stores the submitted configuration as `protocolSettings` for that device.
3. Backend builds SMS commands and sends them to the device phone number.

That means UI can:
- Save profile + send in one action.
- Reload saved values for edit forms later.
- Use `GET /api/devices` or `GET /api/users/{userId}/devices` to prefill config UI.

### Request shape

`/api/send-config` request accepts the same EV settings fields shown in `protocolSettings` above, plus:

```json
{
  "deviceId": 123
}
```

Contact behavior for `/api/send-config`:
- If `contacts` is provided, backend sends contact commands for each entry (up to 10).
- If `contacts` is omitted, backend falls back to legacy single-contact fields.

### Frontend prompt/form suggestion

Use these sections in your UI for better UX:
- Contacts and SOS (`A<n>`, `SOS`, `soscall`, `Loop`)
- Positioning (`loc`, `Wifi`, BLE, GPS/LBS)
- Safety alarms (`FL`, `Tilt`, `NMO`, `MO`, `Geo`, `Speed`, `Low`)
- Network (`S1`, `S2/S0`, `IP1`, `GPRSHB`)
- Working mode (`mode1..mode6`, `CL`)
- Audio/device behavior (`Micvolume`, `Speakervolume`, `LED`, `Vibrate`, `Beep`, `TZ`)
- EV-05 extras (`hrs`, `detpedo`, `display`, `reboot`)
