# API Reference & Route Cheatsheet (API_REFERENCE.md)

> **Format:** Compact endpoint index for high-speed review with low token overhead.  
> **Base URL:** `http://localhost:8080` (or `http://localhost:4000` via frontend proxy)  
> **Auth Boundaries & Header Specification:**
> - **1. Admin Web Management (`/admin/api/v1/**`):** Authenticated strictly via User Session JWT (`Authorization: Bearer <JWT>`). **Never** send or require `X-Api-Key` or `X-Sdk-Key`. Unused headers (`X-App-Id`, `X-Org-Id`) are stripped when resource paths `/apps/{appId}` or `/organizations/{orgId}` already identify the tenant target.
> - **2. Customer Server REST Ingest (`/api/v1/**`):** Authenticated via confidential Server Key (`X-Api-Key: os_live_<hash>`). Kept on backend servers; never transmitted by browser web clients. Optional `Idempotency-Key` for deduplication.
> - **3. Client Mobile & Web SDK (`/sdk/v1/**`):** Authenticated via Public SDK Key (`X-Sdk-Key: os_pub_<hash>`). Embedded in native mobile apps / web push clients. Never send admin `Authorization: Bearer` or `X-Org-Id` to SDK endpoints.
> **Comprehensive Integration Guide:** See [SYSTEM_INTEGRATION_GUIDE.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/docs/SYSTEM_INTEGRATION_GUIDE.md) for complete multi-language SDK code, Kafka streaming, and client apps integration.

---

## 1. Authentication & IAM (`/admin/api/v1`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/admin/api/v1/auth/login` | None | `{"email": "...", "password": "..."}` -> Returns `{accessToken, user, memberships}` + sets refresh cookie |
| `POST` | `/admin/api/v1/auth/refresh` | Cookie | Reads `push_refresh_token` cookie -> Returns new `{accessToken}` |
| `POST` | `/admin/api/v1/auth/mfa/verify` | None | `{"mfaTicket": "...", "totpCode": "..."}` -> Verifies TOTP or backup code, returns `{accessToken, user}` + sets cookie |
| `GET` | `/admin/api/v1/auth/mfa/status` | Bearer | Returns `{enabled: boolean, remainingBackupCodes: number}` |
| `POST` | `/admin/api/v1/auth/mfa/setup` | Bearer | Generates Base32 secret, QR code data URL, and 10 backup recovery codes (10m TTL in Redis) |
| `POST` | `/admin/api/v1/auth/mfa/confirm` | Bearer | `{"totpCode": "123456"}` -> Verifies test code, encrypts secret via AES-256-GCM, stores Argon2id backup hashes, activates MFA |
| `POST` | `/admin/api/v1/auth/mfa/disable` | Bearer | `{"password": "..."}` -> Verifies password and turns off MFA |
| `POST` | `/admin/api/v1/auth/logout` | None | Clears refresh cookie & invalidates session |
| `GET` | `/admin/api/v1/auth/me` | Bearer | Returns current authenticated user and org roles |
| `PUT` | `/admin/api/v1/auth/me/profile` | Bearer | `{"fullName": "...", "avatarUrl": "...", "removeAvatar": false}` -> Updates self profile & avatar. Normalizes relative storage path. |
| `GET` | `/admin/api/v1/organizations` | Bearer | Lists user's accessible organizations |
| `POST` | `/admin/api/v1/organizations` | Bearer | `{"name": "Acme", "slug": "acme"}` |
| `GET` | `/admin/api/v1/organizations/{orgId}/members` | Bearer | Lists org members, avatars, roles, and organization affiliations |
| `PUT` | `/admin/api/v1/organizations/{orgId}/members/{membershipId}` | Bearer | `{"fullName": "...", "avatarUrl": "...", "removeAvatar": false}` -> Updates member profile attributes (strictly self-only; 403 Forbidden for other members) |
| `POST` | `/admin/api/v1/organizations/{orgId}/members/{membershipId}/roles` | Bearer | `{"roleId": "...", "appId": "<optional>"}` -> Assigns role to member |
| `DELETE` | `/admin/api/v1/organizations/{orgId}/members/{membershipId}/roles/{assignmentId}` | Bearer | Revokes specific role assignment |
| `DELETE` | `/admin/api/v1/organizations/{orgId}/members/{membershipId}` | Bearer | Removes member from organization |
| `POST` | `/admin/api/v1/organizations/{orgId}/invitations` | Bearer | `{"email": "...", "roleId": "...", "appId": "<optional-uuid>"}` -> Generates 7-day invite, returns `{id, token, inviteUrl}` |
| `POST` | `/admin/api/v1/invitations/accept` | None | `{"token": "...", "fullName": "...", "password": "..."}` -> Creates or links user and membership |

---

## 2. App & Provider Credentials (`/admin/api/v1`)

| Method | Path | Auth | Payload / Description |
| `GET` | `/admin/api/v1/users/me/accessible-apps` | Bearer | Query `?orgId=<UUID>` (optional). Returns apps accessible by the current authenticated user (org-wide or app-scoped) |
| `GET` | `/admin/api/v1/organizations/{orgId}/apps` | Bearer | List apps in organization |
| `POST` | `/admin/api/v1/organizations/{orgId}/apps` | Bearer | `{"name": "Mobile Store", "description": "..."}`. Auto-provisions default `SERVER_REST` key, `SDK_PUBLIC` key, and Web Push VAPID pair |
| `GET` | `/admin/api/v1/apps/{appId}` | Bearer | Get app details |
| `GET` | `/admin/api/v1/apps/{appId}/credentials` | Bearer | Returns active providers & masked metadata (never raw keys) |
| `POST` | `/admin/api/v1/apps/{appId}/credentials` | Bearer | `{"providerType": "FCM"|"APNS"|"WEBPUSH", "rawCredential": "...", "metadata": {...}}` (Stored in AES-256-GCM vault) |
| `POST` | `/admin/api/v1/apps/{appId}/credentials/{provider}/test` | Bearer | Validates credentials against gateway/cipher |

---

## 3. API Keys Management (`/admin/api/v1`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/admin/api/v1/apps/{appId}/api-keys` | Bearer | List API keys (prefix, type, scopes, rate limits) |
| `POST` | `/admin/api/v1/apps/{appId}/api-keys` | Bearer | `{"name": "...", "keyType": "SERVER_REST"|"SDK_PUBLIC", "scopes": [...]}` -> Returns plaintext key **ONCE** |
| `POST` | `/admin/api/v1/apps/{appId}/api-keys/{keyId}/rotate` | Bearer | Rotates key hash with 24-hour overlap grace period |
| `DELETE` | `/admin/api/v1/apps/{appId}/api-keys/{keyId}` | Bearer | Revokes key |

---

## 4. Device Management (`/sdk/v1` & `/admin/api/v1`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/sdk/v1/devices` | `X-Sdk-Key` | Upserts device. Payload: `{"platform":"ANDROID"|"IOS"|"WEB", "pushToken":"...", "externalUserId":"...", "tags":{"vip":"true"}}` |
| `DELETE` | `/sdk/v1/devices/{pushToken}` | `X-Sdk-Key` | Deactivates device subscription |
| `GET` | `/admin/api/v1/apps/{appId}/devices` | Bearer | Query devices with pagination, platform, tags, and active status |

---

## 5. Dynamic Segments (`/admin/api/v1`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/admin/api/v1/apps/{appId}/segments` | Bearer | List segments |
| `POST` | `/admin/api/v1/apps/{appId}/segments` | Bearer | `{"name": "VIP Android", "filterAst": {"conjunction":"AND", "rules":[...]}}` |
| `GET` | `/admin/api/v1/apps/{appId}/segments/{id}/preview-count` | Bearer | Evaluates AST and returns matching device count |

---

## 6. Notifications & Campaigns (`/api/v1` & `/admin/api/v1`)

| Method | Path | Auth | Headers | Payload / Description |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/notifications` | `X-Api-Key` | `Idempotency-Key` (opt) | Dispatches notification. Payload: `{"title":"...", "body":"...", "idempotency":"...", "targetType":"ALL"|"SEGMENT"|"DEVICE_IDS"|"EXTERNAL_USER_IDS", "targetPayload":{...}, "scheduledAt":"..."}` |
| `GET` | `/api/v1/notifications/{id}` | `X-Api-Key` | None | Get notification dispatch status and stats |
| `POST` | `/admin/api/v1/apps/{appId}/notifications` | Bearer | `Idempotency-Key` (opt) | Create and dispatch notification. Payload: `{"appId":"...", "title":"...", "body":"...", "idempotency":"...", "priority":"HIGH"|"NORMAL", "targetType":"ALL"|"SEGMENT"|"DEVICE_IDS"|"EXTERNAL_USER_IDS", "targetPayload":{...}, "scheduledAt":"..."}` |
| `GET` | `/admin/api/v1/apps/{appId}/notifications` | Bearer | None | Admin list notifications |
| `DELETE` | `/admin/api/v1/apps/{appId}/notifications/{id}/cancel` | Bearer | None | Cancels scheduled campaign |

---

## 7. Client Telemetry Ingestion (`/sdk/v1`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/sdk/v1/events` | `X-Sdk-Key` / Bearer / Public | `{"appId": "...", "events": [{"notificationId":"...", "deviceId":"...", "eventType":"DELIVERED"|"OPENED"|"CLICKED"|"CUSTOM", "timestamp":"..."}]}` |

---

## 8. Analytics & Delivery Logs (`/admin/api/v1`)

| Method | Path | Auth | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/admin/api/v1/apps/{appId}/analytics/overview?range=7d` | Bearer | Delivery rate, CTR%, platform breakdown, 24h timeline |
| `GET` | `/admin/api/v1/apps/{appId}/logs/delivery` | Bearer | Paginated delivery logs with provider, status, latency |
| `GET` | `/admin/api/v1/apps/{appId}/logs/delivery/export` | Bearer | Streams delivery logs as CSV |

---

## 9. Realtime WebSockets (`/ws`)

| Protocol | Path / Destination | Description |
| :--- | :--- | :--- |
| **STOMP (SockJS)** | `/ws` | Handshake with optional `token` query param |
| **STOMP Sub** | `/topic/notifications` | Broadcast notification channel for foreground apps |
| **STOMP Sub** | `/user/queue/notifications` | Targeted direct user push channel |
| **STOMP Send** | `/app/presence/heartbeat` | Refreshes Redis presence TTL (120s) |

---

## 10. File & Static Media Storage (`/admin/api/v1/files` & `/uploads`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/admin/api/v1/files/avatars` | Bearer | `multipart/form-data` with `file` part (JPEG, PNG, WebP <= 5MB). Strips EXIF metadata, resizes <= 512x512, saves via `StorageBackend` -> returns `{path, url, filename, size, contentType, width, height}` |
| `DELETE` | `/admin/api/v1/files/avatars?path=/avatars/{userId}_...` | Bearer | Deletes avatar file from storage backend. Enforces user ownership prefix verification to prevent unauthorized deletion. |
| `GET` | `/uploads/**` | None | Public static media resource handler. Serves uploaded avatars and files with immutable 1-year caching (`Cache-Control: public, max-age=31536000, immutable`), `X-Content-Type-Options: nosniff`, and restrictive `Content-Security-Policy: default-src 'none'`. |

