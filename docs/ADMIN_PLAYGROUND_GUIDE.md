# Admin Frontend Playground & End-to-End Verification Runbook (`ADMIN_PLAYGROUND_GUIDE.md`)

> **Document Version:** 1.0  
> **Target Audience:** Full-Stack Engineers, QA Engineers, DevOps, System Evaluators, Product Managers  
> **Document Purpose:** A complete, hands-on step-by-step playground guide for testing the entire push notification engine through the **Admin Web Portal UI** (`http://localhost:5173` or `http://localhost:3001`). For every step, this guide provides exact UI clicks, inputs, expected visual feedback, underlying backend endpoints, database state transitions, and terminal verification commands.

---

## Table of Contents
1. [Playground Architecture & Preparation](#1-playground-architecture--preparation)
2. [Playground Step-by-Step Walkthrough](#2-playground-step-by-step-walkthrough)
   - [Step 0: Boot the Environment & Super Admin Login](#step-0-boot-the-environment--super-admin-login)
   - [Step 1: Application Provisioning & Multi-Tenant App Switcher](#step-1-application-provisioning--multi-tenant-app-switcher)
   - [Step 2: Provider Credentials & AES-256-GCM Vault Configuration](#step-2-provider-credentials--aes-256-gcm-vault-configuration)
   - [Step 3: Minting API Keys (Server REST & Client SDK)](#step-3-minting-api-keys-server-rest--client-sdk)
   - [Step 4: Device Registration & Subscriber Pool Inspection](#step-4-device-registration--subscriber-pool-inspection)
   - [Step 5: Visual Audience Segment Builder (AST Compiler & Live Preview)](#step-5-visual-audience-segment-builder-ast-compiler--live-preview)
   - [Step 6: Live Web Push Testbench (Native Desktop Notification)](#step-6-live-web-push-testbench-native-desktop-notification)
   - [Step 7: Real-Time WebSocket STOMP Gateway Playground](#step-7-real-time-websocket-stomp-gateway-playground)
   - [Step 8: Notification Composer (Full Campaign Dispatch & Fan-Out)](#step-8-notification-composer-full-campaign-dispatch--fan-out)
   - [Step 9: Delivery Logs & Token Churn Audit Inspection](#step-9-delivery-logs--token-churn-audit-inspection)
   - [Step 10: Real-Time Analytics Dashboard & CTR Aggregation](#step-10-real-time-analytics-dashboard--ctr-aggregation)
3. [Playground Troubleshooting & Gotchas](#3-playground-troubleshooting--gotchas)
4. [Master Playground Verification Checklist](#4-master-playground-verification-checklist)

---

## 1. Playground Architecture & Preparation

The playground connects the **Vue 3 Admin Web Portal** directly with the **Spring Boot Backend**, **PostgreSQL 17**, **Redis 7**, and **Apache Kafka 3.7**.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PLAYGROUND TESTING TOPOLOGY                     │
│                                                                        │
│   Browser: http://localhost:5173  (Vite Dev Server)                    │
│      │                                                                 │
│      ├─ [Proxy: /admin/api/v1] ──┐                                     │
│      ├─ [Proxy: /api/v1]      ───┼─► Spring Boot Backend (Port 8080)   │
│      ├─ [Proxy: /sdk/v1]      ───┤     │                               │
│      └─ [Proxy: /ws] (STOMP)  ───┘     ├─► PostgreSQL 17 (Port 54327)  │
│                                        ├─► Redis 7 (Port 6380)         │
│                                        └─► Apache Kafka 3.7 (Port 9092)│
└────────────────────────────────────────────────────────────────────────┘
```

### Pre-Flight Launch (Two Terminals)

#### Terminal 1: Infrastructure & Backend (Java 21)
```bash
cd /media/menglanghuo/Menglang/Bronx/PushNotificationSys/PushNotificationSys

# 1. Start Docker containers
docker compose up -d postgres redis kafka

# 2. Export Java 21 path (MANDATORY: Do not use Java 25)
export JAVA_HOME=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn
export PATH=$JAVA_HOME/bin:$PATH

# 3. Launch Spring Boot Backend
mvn spring-boot:run -f backend/pom.xml
```
*Wait until you see: `Started PushPlatformApplication in X.XXX seconds (process running for X.XXX)`*

#### Terminal 2: Vue 3 Admin Portal
```bash
cd /media/menglanghuo/Menglang/Bronx/PushNotificationSys/PushNotificationSys/frontend

# Launch frontend Vite dev server
npm install
npm run dev
```
*Vite will display: `➜ Local: http://localhost:5173/`*

---

## 2. Playground Step-by-Step Walkthrough

---

### Step 0: Boot the Environment & Super Admin Login

#### Playground Goal:
Authenticate into the multi-tenant system, obtain a cryptographically signed JWT access token, and establish an encrypted HttpOnly refresh session.

#### Actions in UI:
1. Open your browser and navigate to: [`http://localhost:5173/login`](http://localhost:5173/login)
2. Enter the seeded default credentials:
   - **Email:** `admin@pushnotify.local`
   - **Password:** `Password123!`
3. Click **Sign In**.

#### Expected UI Feedback:
- Instant redirect to [`/dashboard`](http://localhost:5173/dashboard).
- Topbar displays your user profile badge (`Super Admin`) and default Organization (`Acme Corp`).
- A success toast appears: *"Welcome back, Super Admin"*.

#### Behind the Scenes (Backend & Database):
- **API Endpoint:** `POST /admin/api/v1/auth/login`
- **Controller:** `AuthController.login()`
- **Process:** Password verified using Argon2id hashing against `users` table; JWT signed with `app.jwt.secret`; `push_refresh_token` cookie set.

#### Terminal Verification:
```bash
curl -s -X POST http://localhost:8080/admin/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@pushnotify.local","password":"Password123!"}' | jq .
```
*Expected: JSON with `accessToken`, `user.email`, and `memberships` containing `OWNER` role.*

---

### Step 1: Application Provisioning & Multi-Tenant App Switcher

#### Playground Goal:
Create an isolated push notification application under your organization and switch your active context.

#### Actions in UI:
1. In the sidebar or top navigation, click **Apps** (URL: [`/apps`](http://localhost:5173/apps)).
2. Click **+ New Application** button (top right).
3. Fill in the modal:
   - **Application Name:** `Playground Storefront`
   - **Description:** `Testing end-to-end push workflows in admin sandbox`
4. Click **Create Application**.
5. Locate `Playground Storefront` in the app cards and click **Select App** (or use the topbar App Dropdown).

#### Expected UI Feedback:
- Card appears with a freshly generated UUID (e.g. `a1b2c3d4-...`).
- The topbar shows `Playground Storefront` as the currently selected application.
- All sidebar links (`Audience`, `Composer`, `Settings`, `Logs`) now bind to this new App ID.

#### Behind the Scenes:
- **API Endpoint:** `POST /admin/api/v1/organizations/{orgId}/apps`
- **Database Table:** Insert into `apps (id, organization_id, name, description, is_active)`.

---

### Step 2: Provider Credentials & AES-256-GCM Vault Configuration

#### Playground Goal:
Configure Google FCM, Apple APNs, and Web Push credentials. Verify that credentials are encrypted using the AES-256-GCM Crypto Vault before storage.

#### Actions in UI:
1. Navigate to **App Settings > Provider Credentials** (URL: [`/app-settings`](http://localhost:5173/app-settings)).
2. You will see three tabs: **Firebase (FCM)**, **Apple (APNs)**, and **Web Push (VAPID)**.

##### A. Configure Web Push (Easiest to Test Locally):
1. Click the **Web Push** tab.
2. If no keys are shown, click **Generate VAPID Key Pair**.
3. The platform generates an elliptic-curve key pair and displays the masked Public Key.
4. Click **Copy Public Key** (you will see a toast: *"Copied to clipboard"*).

##### B. Configure FCM (Mock or Real):
1. Click the **FCM** tab.
2. In the textarea, paste your Firebase Service Account JSON (or mock JSON):
   ```json
   {
     "type": "service_account",
     "project_id": "playground-fcm-test",
     "private_key_id": "mock_key_id",
     "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7...\n-----END PRIVATE KEY-----\n",
     "client_email": "firebase-adminsdk@playground-fcm-test.iam.gserviceaccount.com"
   }
   ```
3. Click **Save FCM Configuration**.
4. Click **Test Connection**.

#### Expected UI Feedback:
- A green badge indicates: `Configured (AES-256-GCM Encrypted)`.
- Test connection shows a success toast: *"Provider connection validated successfully"*.

#### Behind the Scenes:
- **API Endpoints:**
  - `POST /admin/api/v1/apps/{appId}/credentials`
  - `POST /admin/api/v1/apps/{appId}/credentials/FCM/test`
- **Crypto Engine:** `CryptoVaultService.encrypt()` encrypts `raw_credential` using AES-256-GCM with `app.crypto.master-key`.
- **Database Invariant:** In table `app_credentials`, column `encrypted_credential` contains ciphertext (`vault:v1:...`). Plaintext secrets never touch the disk!

---

### Step 3: Minting API Keys (Server REST & Client SDK)

#### Playground Goal:
Generate an `os_live_...` key for backend server integrations and an `os_pub_...` key for client mobile/web apps. Test key rotation.

#### Actions in UI:
1. Navigate to **API Keys** (URL: [`/api-keys`](http://localhost:5173/api-keys)).
2. Click **+ Generate New Key**.
3. **Generate Server Key:**
   - **Key Name:** `Backend Order Service Key`
   - **Key Type:** Select `Backend Server Key (REST Admin API)`
   - **Scopes:** Check `Send Notifications`, `Read Notification History`, `Query Registered Devices`
   - Click **Create Key**.
   - ⚠️ A modal appears displaying the plaintext key: `os_live_...`. Click **Copy Key** and save it to your notepad. (This is only shown once!).
4. **Generate Client SDK Key:**
   - Click **+ Generate New Key** again.
   - **Key Name:** `Mobile App Public SDK Key`
   - **Key Type:** Select `Client App Key (Mobile / Web SDK)`
   - **Scopes:** Check `Register Device Tokens`, `Query Registered Devices`
   - Click **Create Key**.
   - Copy the key: `os_pub_...`.
5. **Test Key Rotation:**
   - On the `Backend Order Service Key` row, click the **Rotate Key** icon.
   - A modal confirms that the old key will remain valid for **24 hours** (grace period) while the new key takes effect immediately.

#### Expected UI Feedback:
- Active keys table lists both keys with prefixes, creation timestamps, and active status.
- Key rotation generates a new hash and displays the new secret.

#### Behind the Scenes:
- **API Endpoint:** `POST /admin/api/v1/apps/{appId}/api-keys`
- **Database Table:** `api_keys (id, app_id, key_prefix, key_hash, key_type, scopes)`.

---

### Step 4: Device Registration & Subscriber Pool Inspection

#### Playground Goal:
Register test devices into your application's subscriber database using the public SDK API key and inspect the results in the Admin portal.

#### Actions in UI & Terminal:
1. Open your terminal and register an Android test device using the SDK key from Step 3:
   ```bash
   SDK_KEY="os_pub_demo9876543210abcdef9876543210" # Replace with your key from Step 3
   
   curl -s -X POST http://localhost:8080/sdk/v1/devices \
     -H "X-Sdk-Key: ${SDK_KEY}" \
     -H "Content-Type: application/json" \
     -d '{
       "platform": "ANDROID",
       "pushToken": "fcm_mock_token_alpha_'$(date +%s)'",
       "externalUserId": "shopper-vip-101",
       "deviceModel": "Pixel 8 Pro",
       "osVersion": "Android 15",
       "appVersion": "4.2.0",
       "language": "en",
       "timezone": "America/New_York",
       "tags": {
         "tier": "gold",
         "vip": "true",
         "orders_count": "15"
       }
     }' | jq .
   ```
2. In the Admin Web Portal, navigate to **Audience > Devices** (URL: [`/devices`](http://localhost:5173/devices)).
3. Click the **Refresh** button or filter by Platform: `ANDROID`.

#### Expected UI Feedback:
- The device `shopper-vip-101` appears in the list!
- Platform badge shows `ANDROID` (green robot icon).
- Active status pill shows `ACTIVE` (emerald green).
- Click on the device row to view custom tags: `tier: gold`, `vip: true`, `orders_count: 15`.

#### Behind the Scenes:
- **API Endpoint:** `POST /sdk/v1/devices`
- **Database Tables:**
  - `devices`: stores UUID, push token, external user ID, `is_active = true`, `last_active_at = NOW()`.
  - `device_tags`: stores rows `(device_id, 'tier', 'gold')`, `(device_id, 'vip', 'true')`.

---

### Step 5: Visual Audience Segment Builder (AST Compiler & Live Preview)

#### Playground Goal:
Create a dynamic audience segment using the visual rule builder, test real-time recipient estimation, and verify that the backend compiles the JSON AST into optimized SQL.

#### Actions in UI:
1. Navigate to **Audience > Segments** (URL: [`/segments`](http://localhost:5173/segments)).
2. Click **+ New Segment** (URL: [`/segments/new`](http://localhost:5173/segments/new)).
3. Fill in segment details:
   - **Segment Name:** `Active Gold VIPs`
   - **Description:** `VIP members on Android active in the last 7 days`
4. **Build Rules (Visual AST):**
   - **Rule 1:** Select Field `Platform`, Operator `Equals (=)`, Value `ANDROID`.
   - Click **+ Add Rule**.
   - **Rule 2:** Select Field `Device Tag (Key / Value)`, Tag Key `tier`, Operator `Equals (=)`, Value `gold`.
   - Click **+ Add Rule**.
   - **Rule 3:** Select Field `Activity Window (Days)`, Operator `Active within last X days (≤)`, Value `7`.
   - Leave Conjunction as `Match ALL Rules (AND)`.
5. Look at the **Audience Estimate Card** on the right side:
   - Notice the live counter: it evaluates matching subscribers and displays `~1 device` (the device you created in Step 4!).
6. Click **Save Segment**.

#### Expected UI Feedback:
- Segment saved successfully toast.
- The segment appears in the Segment List with its estimated audience size and rule summary.

#### Behind the Scenes:
- **API Endpoints:**
  - `POST /admin/api/v1/apps/{appId}/segments` (Saves AST)
  - `GET /admin/api/v1/apps/{appId}/segments/{id}/preview-count` (Tests AST compiler)
- **Compiler Logic:** `SegmentFilterService` compiles the JSON AST into:
  ```sql
  SELECT COUNT(d.id) FROM devices d 
  JOIN device_tags dt ON dt.device_id = d.id 
  WHERE d.app_id = ? AND d.platform = 'ANDROID' 
    AND dt.tag_key = 'tier' AND dt.tag_value = 'gold' 
    AND d.last_active_at >= NOW() - INTERVAL '7 days' 
    AND d.is_active = true;
  ```

---

### Step 6: Live Web Push Testbench (Native Desktop Notification)

#### Playground Goal:
Experience a real, native browser push notification directly on your desktop without writing any code or deploying mobile apps.

#### Actions in UI:
1. In the sidebar, navigate to **Test Tools > Web Push Testbench** (URL: [`/web-push-test`](http://localhost:5173/web-push-test)).
2. Review the **Browser Environment Compatibility** card:
   - Service Worker Supported: `YES` ✅
   - PushManager Supported: `YES` ✅
   - Notification Permission: `Default` or `Granted`.
3. Click **1. Subscribe this Browser**:
   - Your browser will pop up a permission dialog: *"pushnotify.local wants to show notifications"*. Click **Allow**.
   - The testbench registers a live push token and displays: `Browser successfully registered into device pool!`.
4. Review the **Compose Test Notification** card:
   - **Title:** `Flash Sale Alert! ⚡`
   - **Body:** `Welcome to the Admin Playground. This is a real native desktop push!`
5. Click **2. Send Test Web Push**.

#### Expected UI Feedback:
- Terminal activity log on the right panel shows:
  - `[INFO] Browser notification permission granted.`
  - `[SUCCESS] Browser registered into device pool.`
  - `[SUCCESS] Test push dispatched to https://fcm.googleapis.com/...`
- **Look at the top-right corner of your desktop screen:** A native operating system notification pops up with the title and body!
- Click on the popup $\to$ sends an `OPENED` telemetry event back to `/sdk/v1/events`.

#### Behind the Scenes:
- **API Endpoints:**
  - `POST /sdk/v1/devices` (registers browser endpoint with VAPID crypto keys)
  - `POST /api/v1/notifications` (dispatches notification to `dispatch.webpush` Kafka topic)
  - `POST /sdk/v1/events` (records user click event)

---

### Step 7: Real-Time WebSocket STOMP Gateway Playground

#### Playground Goal:
Test real-time in-app foreground messaging and verify that the backend's WebSocket STOMP broker and Redis Pub/Sub relay are operating with sub-50ms latency.

#### Actions in UI:
1. In the sidebar, navigate to **Test Tools > Realtime Gateway** (URL: [`/realtime`](http://localhost:5173/realtime)).
2. Inspect the **Connection Status** header:
   - Badge displays: `CONNECTED (Active)` in emerald green.
   - Connected Sockets counter shows active connections.
3. Click **Send Ping**:
   - The client sends a STOMP inbound ping frame to the backend.
   - The backend responds with a pong.
   - Toast displays: *"Pong Received: Gateway roundtrip: 18ms"*.
4. Inspect the **Live Activity Stream**:
   - Displays real-time message frames received on topic `/topic/app/{appId}/broadcast`.

#### Behind the Scenes:
- **Endpoint:** `ws://localhost:8080/ws` (WebSocket STOMP handshake)
- **Engine:** Spring `WebSocketMessageBroker` backed by Redis Pub/Sub backplane.
- **Why this matters:** When users are actively using your mobile app or website, messages arrive instantly through this socket without needing an OS push notification!

---

### Step 8: Notification Composer (Full Campaign Dispatch & Fan-Out)

#### Playground Goal:
Compose a campaign targeting the Segment created in Step 5, attach custom deep-link metadata, dispatch the push, and observe the asynchronous Kafka fan-out.

#### Actions in UI:
1. Navigate to **Notifications > Compose** (URL: [`/notifications/new`](http://localhost:5173/notifications/new)).
2. Fill in the campaign form:
   - **Notification Title:** `Exclusive 50% Off VIP Weekend!`
   - **Message Body:** `Hey VIP, use code PLAYGROUND50 at checkout before midnight.`
   - **Image URL:** `https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=600`
   - **Deep Link URL:** `playground://shop/vip-promo`
3. **Target Audience:**
   - Select **Target by Segment**.
   - In the dropdown, choose: `Active Gold VIPs (~1 users)` (from Step 5).
4. **Interactive Custom Data:**
   - Click **+ Add Data Pair**.
   - Key: `promo_code` | Value: `PLAYGROUND50`
   - Key: `source` | Value: `admin_playground`
5. **Priority & Delivery:**
   - Priority: `High Priority (Immediate)`
   - Delivery Timing: `Send Immediately`
6. Look at the **Real-Time Preview** card on the right:
   - Toggle between **Android**, **iOS**, and **Web** tabs to see how the notification formats on different operating systems!
7. Click **Dispatch Notification** button.

#### Expected UI Feedback:
- Button shows a quick spinner (< 50ms).
- Success toast appears: *"Push Dispatched Successfully: Notification sent to delivery gateway."*
- You are automatically redirected to the **Notifications List** ([`/notifications`](http://localhost:5173/notifications)), where your new campaign displays status: `QUEUED` $\to$ `COMPLETED`.

#### Behind the Scenes (The Kafka Fan-Out):
1. `POST /api/v1/notifications` receives the payload.
2. Spring Boot validates idempotency and inserts a row into `notifications`.
3. Event is published to Kafka topic `notification.created`.
4. API responds with `HTTP 202 Accepted` in **28 milliseconds**.
5. `NotificationRouterConsumer` consumes the event, evaluates the segment AST, finds target devices, and publishes batches of 500 to `dispatch.fcm`.
6. `FcmDispatcherConsumer` attempts delivery and records logs.

#### Terminal Verification (Inspect Kafka & Backend Logs):
```bash
# Check backend console output
# You will see:
# [NotificationRouterConsumer] Resolving segment targets for notificationId: ...
# [NotificationRouterConsumer] Fanning out 1 recipients to dispatch.fcm in batches of 500
# [FcmDispatcherConsumer] Processing batch of 1 recipients...
```

---

### Step 9: Delivery Logs & Token Churn Audit Inspection

#### Playground Goal:
Inspect individual device delivery logs, analyze transmission latencies, verify failure classification, and download compliance CSV audit reports.

#### Actions in UI:
1. Navigate to **Logs > Delivery Logs** (URL: [`/logs`](http://localhost:5173/logs)).
2. You will see two tabs: **Gateway Delivery Logs** and **Client SDK Events**.
3. **Inspect Delivery Table:**
   - Look at the top row: contains the notification dispatched in Step 8.
   - **Provider:** `FCM` (or `WEBPUSH`).
   - **Status:** `DELIVERED` or `SENT` (or `FAILED` if using mock tokens).
   - **Latency (ms):** Displays gateway round-trip time (e.g. `24ms`).
   - If a mock token failed, click the **Error Details** icon: a modal opens displaying the raw gateway reason (e.g., `UNREGISTERED` or `InvalidRegistration`).
4. **Test Dead Token Deactivation (Token Churn):**
   - Notice that if the error was `UNREGISTERED` or `BadDeviceToken`, the device's `is_active` status was automatically flipped to `false` in the database!
5. **Test CSV Export:**
   - Click the **Export CSV** button (top right).
   - Browser immediately downloads `delivery-logs.csv` streamed directly from PostgreSQL!

#### Behind the Scenes:
- **API Endpoints:**
  - `GET /admin/api/v1/apps/{appId}/logs/delivery?page=0&size=10`
  - `GET /admin/api/v1/apps/{appId}/logs/delivery/export`
- **Database Table:** `delivery_logs (id, notification_id, device_id, provider, status, latency_ms, error_code, created_at)`.

---

### Step 10: Real-Time Analytics Dashboard & CTR Aggregation

#### Playground Goal:
Verify that subscriber signups, notification dispatches, and client click events roll up into the main analytics dashboard.

#### Actions in UI:
1. Navigate to **Dashboard** (URL: [`/dashboard`](http://localhost:5173/dashboard)).
2. Inspect the **KPI Metric Cards**:
   - **Total Subscribers:** Shows your active registered devices.
   - **Notifications Sent:** Reflects campaigns dispatched in Steps 6 & 8.
   - **Delivery Success Rate (%):** Calculates percentage of successful vs failed pushes.
   - **Click-Through Rate (CTR %):** Calculates ratio of `OPENED` events to delivered messages.
3. Inspect the **Charts**:
   - **Hourly Dispatch Trend Chart:** Shows volume spike at the current hour.
   - **Platform Breakdown Donut:** Displays visual slice of `ANDROID` vs `WEB` vs `IOS`.

#### Behind the Scenes:
- **API Endpoint:** `GET /admin/api/v1/apps/{appId}/analytics/overview?range=7d`
- **Aggregation Engine:** Computes rollups using indexed timestamp ranges over `delivery_logs` and `device_events`.

---

## 3. Playground Troubleshooting & Gotchas

| Issue in Playground | Root Cause | Solution |
| :--- | :--- | :--- |
| **Login fails with Network Error** | Backend not running on port 8080 | Verify backend is booted: `curl http://localhost:8080/actuator/health` |
| **Web Push test bench says "Permission Denied"** | You previously blocked notifications in your browser | In browser URL bar, click the lock/settings icon next to `localhost:5173` $\to$ Reset notification permission to "Ask" or "Allow" $\to$ Refresh page. |
| **Segment count shows 0 devices** | Rule values do not match registered device tags | Verify casing: If device tag is `tier: gold`, rule must check for `gold` (not `GOLD` or `Gold`). |
| **Notification stays in QUEUED state** | Kafka broker is paused or container stopped | Check Kafka: `docker compose ps kafka`. Restart if needed: `docker compose restart kafka`. |
| **WebSocket Realtime Gateway says "Disconnected"** | Port 8080 `/ws` endpoint blocked or proxy issue | Vite dev server automatically proxies `/ws` to `localhost:8080`. Verify `vite.config.ts` has `ws: true` enabled. |

---

## 4. Master Playground Verification Checklist

Use this checklist to confirm that all subsystems are 100% operational:

- [ ] **Step 0:** Super Admin logged in & received JWT (`/admin/api/v1/auth/login`)
- [ ] **Step 1:** Application created & selected via App Switcher (`/admin/api/v1/organizations/{orgId}/apps`)
- [ ] **Step 2:** VAPID keys generated & FCM JSON stored in AES-256-GCM Vault (`/credentials`)
- [ ] **Step 3:** Server REST key (`os_live_...`) and Client SDK key (`os_pub_...`) generated (`/api-keys`)
- [ ] **Step 4:** Device registered with custom tags & externalUserId (`/sdk/v1/devices`)
- [ ] **Step 5:** Dynamic segment created with AST rule builder & live preview tested (`/segments`)
- [ ] **Step 6:** Live desktop Web Push triggered & native browser popup confirmed (`/web-push-test`)
- [ ] **Step 7:** WebSocket STOMP handshake verified with sub-50ms ping roundtrip (`/realtime`)
- [ ] **Step 8:** Full campaign dispatched via composer with Kafka fan-out in batches of 500 (`/notifications`)
- [ ] **Step 9:** Delivery logs inspected, error codes reviewed & CSV exported (`/logs/delivery`)
- [ ] **Step 10:** Dashboard analytics overview verified with live delivery and CTR metrics (`/dashboard`)

🎉 **Congratulations! You have successfully playground-tested the entire end-to-end push notification platform through the Admin Frontend!**
