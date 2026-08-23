# Notification Forward (Android)

Android app that captures incoming notifications and forwards them to any configurable webhook API — Telegram, Discord, Slack, or your own server. It can also automatically back up newly recorded call recordings to a Telegram chat, labeled by caller number.

Based on [ItsAzni/NotificationForwarder](https://github.com/ItsAzni/NotificationForwarder) (MIT License).

## Features

- **Notification capture** using `NotificationListenerService`
- **Webhook forwarding** with configurable URL, HTTP method, auth mode, custom headers, query params, and payload template
- **Compatible** with Telegram Bot API, Discord webhooks, Slack, and any custom API
- **Call recording backup** — automatically uploads newly recorded call recordings to a Telegram chat, captioned with the caller's number and time
- **Queue system** with Room (durable local storage) for both notifications and recordings
- **Retry system** with WorkManager (network constraints + exponential backoff)
- **Auto-purge** — sent items older than 7 days are cleaned automatically
- **Retry all failed** — one-tap retry for all failed queue items
- **Deduplication** — skips duplicate/ongoing/group summary notifications
- **Background support** — survives app kill and device reboot
- **Auto start** after reboot (`BOOT_COMPLETED`)
- **ProGuard** ready — release builds with minification enabled

## Quick Setup

Open the app → **Home** tab. A single "Quick Setup" card walks through the three permissions the app needs — each row shows whether it's granted and, if not, a one-tap button to fix it:

1. **Notification Access** — lets the app read incoming notifications so it can forward them.
2. **Battery: No Restriction** — stops Android from killing the app in the background.
3. **All Files Access** — only needed for Call Recording Backup, to read recordings your phone already saved outside the app's own storage.

On OEM ROMs (MIUI/ColorOS/FuntouchOS/HiOS), also enable **Auto Start** for the app in the phone's own app settings — Android doesn't expose this as a standard permission, so there's no in-app button for it.

Once all three show "Granted", you're done — the card gets out of the way and just shows green checkmarks.

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Call Recording Backup

The app can watch your phone's call-recording folder and automatically upload each new recording to a Telegram chat via a bot — a simple off-device backup for call recordings, without any cloud storage account. Each upload is captioned with the caller's number and the recording time, so it's immediately clear whose call it was.

Only recordings made **after** the feature is enabled are ever uploaded; existing recordings are left untouched.

### Setup

1. Open the app → **Recordings** tab.
2. Enter your **Telegram bot token** (from [@BotFather](https://t.me/BotFather)) and your **chat ID**. Tap the eye icon to reveal the token while typing it.
3. Set the **recording folder path** — this varies by manufacturer. Common paths:
   - `/storage/emulated/0/Music/PhoneRecord` (Transsion/HiOS — Tecno, Infinix, itel)
   - `/storage/emulated/0/Recordings/Call` (many stock/AOSP-based ROMs)
   - `/storage/emulated/0/MIUI/sound_recorder/call_rec` (MIUI)
4. Grant **All Files Access** if you haven't already (from the Home tab's Quick Setup card, or the button here) — required because call recordings live outside the app's own storage sandbox and outside the shared media index on most OEM ROMs.
5. Enable the toggle and save.

New recordings are picked up automatically (checked every 15 minutes, and immediately after saving settings or on boot), or tap **Scan Now** to check immediately. Each file is uploaded via Telegram's `sendDocument` API (50MB max per file, a Bot API limit) with a caption identifying the caller's number, extracted from the recording's folder or file name.

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
- **Room** — local queue database for notifications and call recordings
- **WorkManager** — reliable background processing with network constraints
- **OkHttp** — shared HTTP client for webhook delivery and Telegram file uploads
- **Gradle KTS** — build scripts

## License

MIT License — see [LICENSE](LICENSE) for details.

Original project: [ItsAzni/NotificationForwarder](https://github.com/ItsAzni/NotificationForwarder)
