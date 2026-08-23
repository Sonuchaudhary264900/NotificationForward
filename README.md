# Notification Forward (Android)

Android app that captures incoming notifications and forwards them to any configurable webhook API — Telegram, Discord, Slack, or your own server.

Based on [ItsAzni/NotificationForwarder](https://github.com/ItsAzni/NotificationForwarder) (MIT License).

## Features

- **Notification capture** using `NotificationListenerService`
- **Webhook forwarding** with configurable URL, HTTP method, auth mode, custom headers, query params, and payload template
- **Compatible** with Telegram Bot API, Discord webhooks, Slack, and any custom API
- **Queue system** with Room (durable local storage)
- **Retry system** with WorkManager (network constraints + exponential backoff)
- **Auto-purge** — sent items older than 7 days are cleaned automatically
- **Retry all failed** — one-tap retry for all failed queue items
- **Deduplication** — skips duplicate/ongoing/group summary notifications
- **Background support** — survives app kill and device reboot
- **Auto start** after reboot (`BOOT_COMPLETED`)
- **Call recording backup** — automatically uploads newly recorded call recordings to a Telegram chat as they're created
- **ProGuard** ready — release builds with minification enabled

## Setup

1. Open the app → **Home** tab.
2. Tap **Open Access Settings** and enable Notification Access for the app.
3. Tap **Open Battery Settings** and set the app to "No restriction" / "Unrestricted".
4. On OEM ROMs (MIUI/ColorOS/FuntouchOS/OneUI), also enable **Auto Start** for the app.

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Webhook Configuration

### Supported HTTP Methods

- `GET` — no request body, query params appended to URL
- `POST` — with JSON body
- `PUT` — with JSON body
- `PATCH` — with JSON body

### Authentication

- **None** — no auth header
- **Bearer** — adds `Authorization: Bearer <token>`
- **Custom** — define any headers manually

### Custom Query Params

Add per line as `key=value`:

```
chat_id=123456789
token=abc123
```

### Custom Payload Template

Use JSON with variable placeholders. Leave blank for default payload.

Available variables: `{deviceId}`, `{packageName}`, `{appName}`, `{title}`, `{text}`, `{postedAt}`, `{notificationKey}`

#### Example: Telegram Bot API

- URL: `https://api.telegram.org/bot<token>/sendMessage`
- Method: `POST`
- Payload template:

```json
{"chat_id":"123456789","text":"*{appName}*\n*{title}*\n{text}","parse_mode":"Markdown"}
```

#### Example: Discord Webhook

- URL: `https://discord.com/api/webhooks/.../...`
- Method: `POST`
- Payload template:

```json
{"content":"**{appName}**\n**{title}**\n{text}"}
```

#### Example: Custom GET API

- URL: `https://example.com/api/alert`
- Method: `GET`
- Query params:

```
device={deviceId}
msg={title}
```

## Call Recording Backup

The app can watch your phone's call-recording folder and automatically upload each new recording to a Telegram chat via a bot — a simple off-device backup for call recordings, without any cloud storage account.

Only recordings made **after** the feature is enabled are ever uploaded; existing recordings are left untouched.

### Setup

1. Open the app → **Recordings** tab.
2. Enter your **Telegram bot token** (from [@BotFather](https://t.me/BotFather)) and your **chat ID**.
3. Set the **recording folder path** — this varies by manufacturer. Common paths:
   - `/storage/emulated/0/Music/PhoneRecord` (Transsion/HiOS — Tecno, Infinix, itel)
   - `/storage/emulated/0/Recordings/Call` (many stock/AOSP-based ROMs)
   - `/storage/emulated/0/MIUI/sound_recorder/call_rec` (MIUI)
4. Tap **Grant Storage Access** and allow "All files access" for the app (Android 11+) — required because call recordings live outside the app's own storage sandbox and outside the shared media index.
5. Enable the toggle and save.

New recordings are picked up automatically (checked every 15 minutes, and immediately after saving settings or on boot), or tap **Scan Now** to check immediately. Each file is uploaded via Telegram's `sendDocument` API (50MB max per file, a Bot API limit).

## Local Webhook Server (`webhook/`)

A Node.js webhook receiver is included in `webhook/` for local testing.

```bash
cd webhook
npm install
cp .env.example .env
npm run start
```

Endpoints:
- `POST /webhook` — receives notification payloads
- `GET /health` — health check

Environment config (`webhook/.env`):

| Key | Description |
|---|---|
| `HOST` | Server host |
| `PORT` | Server port |
| `WEBHOOK_PATH` | Webhook endpoint path |
| `WEBHOOK_BEARER_TOKEN` | Optional bearer token |
| `WEBHOOK_LOG_FILE` | Log file path |
| `JSON_LIMIT` | Max JSON body size |

## Tech Stack

- **Kotlin** + Jetpack Compose (Material 3)
- **Room** — local notification queue database
- **WorkManager** — reliable background processing with network constraints
- **OkHttp** — HTTP client for webhook delivery
- **Gradle KTS** — build scripts

## License

MIT License — see [LICENSE](LICENSE) for details.

Original project: [ItsAzni/NotificationForwarder](https://github.com/ItsAzni/NotificationForwarder)
