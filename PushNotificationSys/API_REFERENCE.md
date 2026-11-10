# API Reference & Route Cheatsheet (API_REFERENCE.md)

> **Format:** Compact endpoint index for high-speed review with low token overhead.  
> **Base URL:** `http://localhost:8080`  
> **Auth Methods:**
> - Admin Dashboard: `Authorization: Bearer <JWT>`
> - Server REST Ingest: `X-Api-Key: os_live_<hash>`
> - Mobile/Web SDK: `X-Sdk-Key: os_pub_<hash>`

---

## 1. Authentication & IAM (`/admin/api/v1`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/admin/api/v1/auth/login` | None | `{"email": "...", "password": "..."}` -> Returns `{accessToken, user, memberships}` + sets refresh cookie |
| `POST` | `/admin/api/v1/auth/refresh` | Cookie | Reads `push_refresh_token` cookie -> Returns new `{accessToken}` |
| `POST` | `/admin/api/v1/auth/mfa/verify`| None | `{"mfaToken": "...", "code": "123456"}` -> Returns `{accessToken}` |
| `POST` | `/admin/api/v1/auth/logout` | None | Clears refresh cookie & invalidates session |
| `GET` | `/admin/api/v1/auth/me` | Bearer | Returns current authenticated user and org roles |
| `GET` | `/admin/api/v1/organizations` | Bearer | Lists user's accessible organizations |
| `POST` | `/admin/api/v1/organizations` | Bearer | `{"name": "Acme", "slug": "acme"}` |
| `GET` | `/admin/api/v1/organizations/{orgId}/members` | Bearer | Lists org members and roles |
| `POST` | `/admin/api/v1/organizations/{orgId}/members/invite` | Bearer | `{"email": "...", "roleId": "..."}` |
| `POST` | `/admin/api/v1/invitations/accept` | None | `{"token": "...", "password": "..."}` |

---

## 2. App & Provider Credentials (`/admin/api/v1`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/admin/api/v1/organizations/{orgId}/apps` | Bearer | List apps in organization |
| `POST` | `/admin/api/v1/organizations/{orgId}/apps` | Bearer | `{"name": "Mobile Store", "description": "..."}` |
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
| `POST` | `/api/v1/notifications` | `X-Api-Key` | `Idempotency-Key` (opt) | Dispatches notification. Payload: `{"title":"...", "body":"...", "targetType":"ALL"|"SEGMENT"|"DEVICE_IDS"|"EXTERNAL_USER_IDS", "targetPayload":{...}, "scheduledAt":"..."}` |
| `GET` | `/api/v1/notifications/{id}` | `X-Api-Key` | None | Get notification dispatch status and stats |
| `GET` | `/admin/api/v1/apps/{appId}/notifications` | Bearer | None | Admin list notifications |
| `DELETE` | `/admin/api/v1/apps/{appId}/notifications/{id}/cancel` | Bearer | None | Cancels scheduled campaign |

---

## 7. Client Telemetry Ingestion (`/sdk/v1`)

| Method | Path | Auth | Payload / Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/sdk/v1/events` | `X-Sdk-Key` | `{"events": [{"notificationId":"...", "deviceId":"...", "eventType":"DELIVERED"|"OPENED"|"CLICKED"|"CUSTOM", "timestamp":"..."}]}` |

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
