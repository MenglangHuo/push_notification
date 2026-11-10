# Setup, Runbook & Operations Manual (Developer & Admin Guide)

> **Document Version:** 1.0  
> **Target Audience:** DevOps Engineers, Backend Developers, Platform Administrators, Account Managers  
> **Purpose:** Comprehensive guide covering system requirements, startup options (bare metal & Docker), initial developer configuration, and daily administrative app management for clients.

---

## 1. System Requirements & Prerequisites

### 1.1 Hardware & Environment Sizing
| Resource | Development / Evaluation | Production Minimum | Production High-Throughput (10M+ Pushes) |
| :--- | :--- | :--- | :--- |
| **CPU** | 2 Cores (x86_64 or ARM64) | 4 vCPUs | 8+ vCPUs (Kubernetes cluster / Multi-node) |
| **RAM** | 4 GB | 8 GB | 16–32 GB |
| **Disk** | 10 GB SSD | 50 GB NVMe | 250+ GB NVMe (PostgreSQL WAL & Kafka logs) |
| **Operating System** | Linux (Ubuntu/Debian/Fedora), macOS 13+, Windows 11 (via WSL2) | Linux (Ubuntu 22.04 LTS / Amazon Linux 2023) | Linux Container Orchestration (K8s) |

### 1.2 Toolchain Dependencies
1. **Java Development Kit:**
   - **Supported Version:** **Amazon Corretto Java 25 LTS** (`25.0.3-amzn`) or OpenJDK 25.
   - **Framework Baseline:** Spring Boot 4.1.0 (Spring Framework 7.0, Jakarta EE 11).
   - Lombok `1.18.42+` and MapStruct `1.6.3+` are configured for Java 25 compiler compatibility.
2. **Node.js & Package Manager:**
   - Node.js `v18.x` or `v20.x` LTS
   - npm `v9+`
3. **Container Infrastructure:**
   - Docker Engine `v24+` and Docker Compose `v2.20+`
4. **Build Tools:**
   - Apache Maven `v3.9+`

### 1.3 Default Port Allocation
| Port | Service | Bound To | Purpose |
| :--- | :--- | :--- | :--- |
| `8080` | Spring Boot Backend | `0.0.0.0` | REST API, WebSocket STOMP (`/ws`), Actuator, Swagger UI |
| `5173` | Vite Dev Server | `localhost` | Vue 3 Admin Portal (Development Mode) |
| `3001` | Nginx Frontend Proxy| `0.0.0.0` | Vue 3 Admin Portal (Full Docker Compose Stack, configurable via `FRONTEND_PORT`) |
| `5432` | PostgreSQL 16 | `0.0.0.0` | Primary Relational Database (`push_platform`) |
| `6380` | Redis 7 | `0.0.0.0` | Host port mapped to container port `6379` (Rate limits, ShedLock) |
| `9092` | Apache Kafka 3.7 | `0.0.0.0` | Ingestion & Fan-out Event Broker (internal `29092`) |

---

## 2. How to Start and Run the Application

You can run the application in two modes depending on your goal:
- **Mode A (Recommended for Developers):** Run infrastructure in Docker; run Backend and Frontend locally for instant hot-reload and fast debugging.
- **Mode B (Full Stack Docker):** Spin up the entire environment (DB, Redis, Kafka, Backend, Frontend) with a single command.

```mermaid
flowchart TD
    subgraph Selection["Choose Startup Strategy"]
        Dev[Mode A: Local Dev Hot-Reload]
        Prod[Mode B: Full Docker Compose Stack]
    end

    subgraph ModeA["Mode A: Hybrid Local Dev"]
        D1[docker compose up -d postgres redis kafka]
        D2[Backend: mvn spring-boot:run with Java 25 & Spring Boot 4.1.0]
        D3[Frontend: npm run dev]
        D1 --> D2 --> D3
    end

    subgraph ModeB["Mode B: Complete Container Stack"]
        C1[docker compose up -d --build]
    end

    Dev --> ModeA
    Prod --> ModeB
```

---

### Strategy A: Local Development Mode (Recommended)

#### Step 1: Start Background Services
Launch PostgreSQL, Redis, and Kafka in the background:
```bash
cd /media/menglanghuo/Menglang/Bronx/PushNotificationSys/PushNotificationSys
docker compose up -d postgres redis kafka
```
*Verify containers are healthy:*
```bash
docker compose ps
```

#### Step 2: Launch Spring Boot Backend (Java 25 & Spring Boot 4.1.0)
```bash
# Verify JDK version
java -version # Amazon Corretto 25 or OpenJDK 25

# Launch Spring Boot
mvn spring-boot:run -f backend/pom.xml
```
*The backend will boot on `http://localhost:8080` and run Flyway database migrations `V1` to `V5` automatically.*

#### Step 3: Launch Vue 3 Frontend Portal
In a separate terminal window:
```bash
cd /media/menglanghuo/Menglang/Bronx/PushNotificationSys/PushNotificationSys/frontend
npm install
npm run dev
```
*The Vite development server will start at `http://localhost:5173` with automated reverse-proxying of `/admin/api/v1`, `/api/v1`, `/sdk/v1`, and `/ws` to port `8080`.*

---

### Strategy B: Full-Stack Docker Compose Mode

If you want to run the entire system in isolated containers without installing Java or Node.js locally:

```bash
cd /media/menglanghuo/Menglang/Bronx/PushNotificationSys/PushNotificationSys

# 1. Build and run all services
docker compose up -d --build

# 2. Inspect logs
docker compose logs -f backend
```

**Port Access:**
- **Admin Dashboard:** `http://localhost:3001` (or `http://localhost:${FRONTEND_PORT}`)
- **Backend API & Swagger:** `http://localhost:8080/swagger-ui/index.html`
- **Actuator Health:** `http://localhost:8080/actuator/health`

---

### Step 4: Automated Verification (Smoke Test)

Run the included verification script to confirm all subsystems (Authentication, SDK registration, Campaign creation, Kafka routing, Event ingestion, Redis rate limiting) are operational:

```bash
cd /media/menglanghuo/Menglang/Bronx/PushNotificationSys/PushNotificationSys
bash scripts/verify_platform.sh
```
Expected output:
```text
=== 1. Checking Actuator Health & Swagger Docs ===
✅ Actuator Health: UP
✅ OpenAPI 3.0 Docs (http://localhost:8080/v3/api-docs): HTTP 200
=== 2. Authenticating Platform Super Admin ===
✅ Authenticated: JWT Access Token retrieved
...
🎉 ALL PLATFORM MODULES & WORKFLOWS VERIFIED SUCCESSFULLY!
```

---

## 3. Developer Guide: Initial System Setup & Configuration

This section is for the **Lead Engineer** configuring the system for client onboarding.

### 3.1 Core Security Keys & Configuration
In `PushNotificationSys/backend/src/main/resources/application.yml` (or via environment variables):

```yaml
app:
  jwt:
    # 256-bit secret for HS256 JWT tokens
    secret: ${JWT_SECRET:4z7x9w2v8y5k1m3p6r9t0q4s7u2w5x8z1a3c5e7g9i1k3m5o7q9s1u3w5y7z9a1b}
    access-token-expiration-seconds: 900       # 15 minutes
    refresh-token-expiration-seconds: 604800   # 7 days
    cookie-name: push_refresh_token
    cookie-secure: false                       # Set to true in production behind HTTPS
  crypto:
    # 256-bit Master Key for AES-GCM Provider Credentials Vault
    master-key: ${CRYPTO_MASTER_KEY:k4P8q3Z1y7W2v9T0x5R8u1O3m6L9j2H5g7E0b3D6a8=}
```

> [!WARNING]
> **Production Rule:** In production, override `JWT_SECRET` and `CRYPTO_MASTER_KEY` using secure environment variables or a secret manager (AWS Secrets Manager / HashiCorp Vault). Never use defaults in production.

---

### 3.2 Configuring Push Gateway Credentials

Before sending live notifications, the developer or administrator must store push credentials in the **AES-256-GCM Vault**:

#### 1. Google FCM (Android & Chrome Web Push)
1. Go to [Firebase Console](https://console.firebase.google.com/) > Project Settings > **Service accounts**.
2. Click **Generate new private key** to download the JSON service account file.
3. Save the credential via the Admin API:
   ```http
   POST /admin/api/v1/apps/{appId}/credentials HTTP/1.1
   Authorization: Bearer <ADMIN_JWT>
   Content-Type: application/json

   {
     "providerType": "FCM",
     "rawCredential": "{\"type\":\"service_account\",\"project_id\":\"acme-prod\",\"private_key\":\"-----BEGIN PRIVATE KEY...\"}",
     "metadata": {
       "project_id": "acme-prod",
       "client_email": "firebase-adminsdk@acme-prod.iam.gserviceaccount.com"
     }
   }
   ```

#### 2. Apple APNs (iOS & macOS)
1. In [Apple Developer Portal](https://developer.apple.com/) > Certificates, Identifiers & Profiles > **Keys**.
2. Create an **Apple Push Notifications service (APNs)** key and download the `.p8` file.
3. Note your **Key ID** (10 characters) and **Team ID** (10 characters).
4. Save the credential:
   ```http
   POST /admin/api/v1/apps/{appId}/credentials HTTP/1.1
   Authorization: Bearer <ADMIN_JWT>
   Content-Type: application/json

   {
     "providerType": "APNS",
     "rawCredential": "-----BEGIN PRIVATE KEY-----\nMIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg...\n-----END PRIVATE KEY-----",
     "metadata": {
       "key_id": "ABC1234567",
       "team_id": "DEF7654321",
       "bundle_id": "com.acme.store",
       "is_production": true
     }
   }
   ```

#### 3. W3C Web Push (Browser Notifications)
If the client needs Web Push, generate a standard VAPID key pair or configure it directly:
```http
POST /admin/api/v1/apps/{appId}/credentials HTTP/1.1
Authorization: Bearer <ADMIN_JWT>
Content-Type: application/json

{
  "providerType": "WEBPUSH",
  "rawCredential": "<VAPID_PRIVATE_KEY_BASE64>",
  "metadata": {
    "public_key": "<VAPID_PUBLIC_KEY_BASE64>",
    "subject": "mailto:push-admin@acme.com"
  }
}
```

#### 4. Testing Gateway Connectivity
Verify that the credentials decrypt and handshake cleanly:
```http
POST /admin/api/v1/apps/{appId}/credentials/FCM/test HTTP/1.1
Authorization: Bearer <ADMIN_JWT>
```
Response:
```json
{
  "provider": "FCM",
  "valid": true,
  "message": "Connection test successful."
}
```

---

### 3.3 Generating API Keys for Client Apps and Server Systems

Every client app requires two API keys:

1. **Client SDK Public Key (`SDK_PUBLIC`):** Embedded inside the mobile and web apps.
   ```http
   POST /admin/api/v1/apps/{appId}/api-keys HTTP/1.1
   Authorization: Bearer <ADMIN_JWT>
   Content-Type: application/json

   {
     "name": "Mobile App Public Key",
     "keyType": "SDK_PUBLIC",
     "scopes": ["device:write", "event:write"]
   }
   ```
   *Returns an `os_pub_...` key. Safe to place in client app code.*

2. **Server REST API Key (`SERVER_REST`):** Kept strictly on customer backend servers.
   ```http
   POST /admin/api/v1/apps/{appId}/api-keys HTTP/1.1
   Authorization: Bearer <ADMIN_JWT>
   Content-Type: application/json

   {
     "name": "E-Commerce Backend REST Key",
     "keyType": "SERVER_REST",
     "scopes": ["notification:create", "device:read", "log:read"]
   }
   ```
   *Returns an `os_live_...` key. Plaintext is shown only once upon generation.*

---

## 4. Admin Guide: Managing Organizations, Apps, and Campaigns

This section is for **System Administrators, Operations Engineers, and Account Managers** handling day-to-day client requests.

### 4.1 Accessing the Admin Portal
1. Open your browser and navigate to:
   - Development: `http://localhost:5173`
   - Production / Docker: `http://localhost:3000`
2. **Default Super Admin Login:**
   - **Email:** `admin@pushnotify.local`
   - **Password:** `Password123!`
3. Upon login, the system receives a signed JWT access token stored in memory and an HttpOnly refresh cookie.

---

### 4.2 Step-by-Step Operations Walkthrough

```mermaid
flowchart TD
    Login[1. Login to Portal] --> Org[2. Create Organization]
    Org --> App[3. Provision Client App]
    App --> Creds[4. Upload Push Credentials]
    Creds --> Team[5. Invite Client Team Members]
    Team --> Seg[6. Build Audience Segments]
    Seg --> Send[7. Send or Schedule Notification]
    Send --> Monitor[8. Inspect Delivery Logs & Analytics]
```

#### Step 1: Onboard a New Client Organization
1. In the sidebar, select **Organizations** (or top Organization dropdown > *Manage Organizations*).
2. Click **Create Organization**.
3. Fill in the client's business details:
   - **Name:** `Global Health Corp`
   - **Slug:** `global-health` (must be unique URL-friendly string)
   - **Subscription Tier:** `ENTERPRISE` (or `PRO`, `STARTER`)
4. Click **Save**.

#### Step 2: Provision Applications under the Organization
1. Navigate to **Apps** in the navigation bar.
2. Click **New Application**.
3. Enter:
   - **App Name:** `Health Track Mobile`
   - **Description:** `Primary patient mobile application for iOS & Android`
4. Once created, copy the generated **App ID** (UUID).

#### Step 3: Configure Client Credentials & API Keys
1. Click the gear icon or **Settings** next to the newly created app.
2. Select **Provider Credentials**:
   - Paste the client's **Firebase Service Account JSON** under the *FCM* tab.
   - Paste the client's **Apple APNs `.p8` key**, Key ID, Team ID, and Bundle ID under the *APNs* tab.
   - Click **Test Connection** to verify green status.
3. Switch to the **API Keys** tab:
   - Click **Generate Key**.
   - Create one `SDK_PUBLIC` key for their mobile team.
   - Create one `SERVER_REST` key for their backend team.
   - Copy both keys and deliver them securely to the client.

#### Step 4: Invite Client Team Members (RBAC)
1. Go to **Team Members** in the main menu.
2. Click **Invite Member**:
   - **Email:** `marketing.lead@globalhealth.com`
   - **Role:** Select `Marketer` (can create campaigns and segments, but cannot delete apps or view server secrets).
3. The platform generates an invitation link (`/accept-invite?token=...`).

**Role Matrix:**
| Role | Permissions | Best For |
| :--- | :--- | :--- |
| **Owner** | All permissions (`org:*`, `app:*`, `credential:*`, `apikey:*`) | Enterprise CTO / Client Tech Lead |
| **Admin** | Manage apps, credentials, API keys, campaigns, and members | System Ops / Client Project Manager |
| **Developer** | Manage API keys, test dispatch, inspect delivery logs, create notifications | Software Developers & QA |
| **Marketer** | Create and schedule notifications, build audience segments, view analytics | Growth & Marketing Teams |
| **Viewer** | Read-only access to analytics, subscriber counts, and delivery logs | Stakeholders & Auditors |

#### Step 5: Audience Segmentation & Subscriber Management
1. To view registered users, navigate to **Audience > Devices**.
   - Review subscriber growth by platform (`ANDROID`, `IOS`, `WEB`).
   - Search for specific users by `externalUserId` (e.g. customer email or user ID).
   - Click on any device to view custom tags (`tier: gold`, `purchases: 5`).
2. To create a targeted cohort, navigate to **Audience > Segments**:
   - Click **New Segment**.
   - Use the visual **AST Filter Builder** to add rules:
     - `Platform = ANDROID`
     - `Tag "plan" = premium`
     - `Last Active <= 14 days`
   - Click **Preview Recipient Count** to see live matching subscriber totals.
   - Click **Save Segment**.

#### Step 6: Composing and Sending Notifications
1. Navigate to **Notifications > Compose**.
2. **Message Content:**
   - **Title:** `Don't miss your appointment today!`
   - **Body:** `Tap to check in with Dr. Smith at 2:00 PM.`
   - **Image URL (optional):** `https://cdn.globalhealth.com/banners/reminder.jpg`
   - **Deep Link:** `healthapp://appointments/APT-9921`
3. **Audience Targeting:**
   - Select **Target Type**:
     - *All Active Devices* (Broadcast)
     - *Segment* (Select your saved Segment)
     - *Specific User ID(s)* (e.g., patient user ID)
4. **Scheduling & Priority:**
   - **Priority:** `HIGH` (Wakes up device immediately) or `NORMAL` (Battery optimized).
   - **Delivery Time:**
     - *Send Immediately*
     - *Schedule for Later* (Select calendar date/time)
5. Click **Dispatch Notification**. The UI will show `HTTP 202 Accepted` and provide a tracking link.

#### Step 7: Monitoring Delivery Logs & Troubleshooting
1. Open **Delivery Logs**:
   - Real-time stream of all outbound messages.
   - Filter by status (`SUCCESS`, `FAILED`), provider (`FCM`, `APNS`, `WEBPUSH`), or date range.
   - Inspect **Latency (ms)** to detect gateway slowdowns.
2. **Handling Failures:**
   - If an error displays `UNREGISTERED` or `BadDeviceToken`, note that the system has automatically deactivated the dead token. No manual cleanup is necessary.
3. Click **Export CSV** to download compliance audit reports for the client.

#### Step 8: Using the Interactive Test Benches
The portal includes two built-in developer testing tools:
1. **Web Push Test Bench (`/apps/{appId}/web-push-test`):**
   - Click **Subscribe to Web Push** in your Chrome or Firefox browser.
   - Click **Send Test Notification**.
   - Validates that your browser receives native desktop push notifications instantly without needing a backend deployment.
2. **Realtime Gateway (`/apps/{appId}/realtime`):**
   - Connects to the STOMP WebSocket broker at `/ws`.
   - Sends real-time foreground messages and verifies cross-cluster Redis Pub/Sub relaying.

---

## 5. Operations Troubleshooting Cheatsheet

| Symptom | Probable Cause | Action |
| :--- | :--- | :--- |
| **`Compilation Error` on older Lombok** | Legacy Lombok version parsing Java 25 AST | Verify `lombok.version` is `>= 1.18.42` and `mapstruct.version` is `>= 1.6.3` in `pom.xml` |
| **Pushes not arriving on Android** | FCM Service Account JSON missing or invalid | Go to *App Settings > Credentials > FCM* and click **Test Connection** |
| **Pushes not arriving on iOS** | APNs `.p8` key invalid or Bundle ID mismatch | Verify Key ID, Team ID, and matching Bundle ID in APNs credentials |
| **`429 Too Many Requests` response** | Redis token-bucket rate limit exceeded | Check `RateLimitingService`. Defaults to 100 req/min for SDK keys. |
| **Scheduled campaign did not trigger** | ShedLock table lock collision or Redis down | Check Redis connectivity on port `6380` and verify ShedLock lock TTL |
| **Web Push fails with SSRF Error** | Target endpoint points to localhost or private IP | Verify browser push subscription endpoint starts with valid public HTTPS URL |
