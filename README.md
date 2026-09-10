# SMS API

Android application for receiving **new** SMS messages from selected bank senders and forwarding them to one configurable HTTPS API endpoint.

## What it does

- Receives only new incoming SMS after you enable it.
- Filters messages by sender names you enter in the app.
- Excludes common OTP and verification-code messages.
- Sends each selected message to the HTTPS endpoint with a unique event ID.
- Queues delivery until the network is available and retries temporary server failures.
- Stores the device API token encrypted using Android Keystore.

## What it does not do

- It does not read old SMS inbox messages.
- It does not open or control WhatsApp, Telegram, Accessibility, overlays, or a foreground service.
- It does not send OTP messages.

## API contract

The app sends an HTTPS `POST` request to the URL configured in the app.

```text
Content-Type: application/json
Authorization: Bearer <device-token>
```

```json
{
  "event_id": "sha256-unique-event-id",
  "sender": "ALRAJHI",
  "body": "Bank message text",
  "received_at": 1735689600000
}
```

The API should treat `event_id` as an idempotency key and return a `2xx` response only after it accepts the message.

## Setup

1. Install the APK on the Android phone.
2. Add the exact sender labels used by your bank.
3. Enter the HTTPS Baseer Relay API URL and the device token.
4. Tap **Save and enable**, then allow SMS reception when Android asks.
5. Use **Send test message** after the API is ready.

## Permissions

| Permission | Reason |
| --- | --- |
| `RECEIVE_SMS` | Receive new incoming bank SMS for the senders you select. |
| `INTERNET` | Send selected messages to the configured HTTPS API. |
