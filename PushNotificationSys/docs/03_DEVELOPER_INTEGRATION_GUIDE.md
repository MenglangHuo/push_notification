# Developer Integration Playbook & Architecture Review Guide

> **Document Version:** 1.0  
> **Target Audience:** Backend Engineers, Mobile Developers, DevOps, Code Reviewers  
> **Purpose:** Rapid onboarding guide, API integration patterns, code review checklist, and system failure mode analysis.

---

## 1. System Technology Stack & Architectural Decisions

| Layer | Component | Version | Why This Tech Was Chosen |
| :--- | :--- | :--- | :--- |
| **Language & Framework** | Java / Spring Boot | 25 LTS / 4.1.0 | High concurrency, type safety, Scoped Values, Virtual Threads, Spring Framework 7. |
| **Persistence** | PostgreSQL | 17 | ACID compliance, JSONB support for AST filters, `uuid-ossp`, and `pg_trgm` for search. |
| **Caching & Pub/Sub** | Redis | 7 (Lettuce) | Microsecond token bucket rate limiting, ShedLock distributed locks, and STOMP backplane. |
| **Event Backbone** | Apache Kafka | 3.7 | Decouples HTTP ingestion from push gateways; guarantees at-least-once partitioned fan-out. |
| **Database Migrations** | Flyway | Core 10+ | Strict version-controlled schema evolution (`V1` to `V5`); eliminates manual DDL drifts. |
| **Distributed Lock** | ShedLock | 5.14.0 | Coordinates scheduled campaign execution across autoscaling cluster instances. |
| **Cryptography** | Bouncy Castle | 1.78.1 / 1.80+ | RFC 8291 / 8292 Web Push encryption (AES-128-GCM, ECDH) and Argon2id password hashing. |

---

## 2. Five-Minute Developer Quickstart

### Prerequisites
1. **Docker & Docker Compose** (running locally)
2. **Java 25 (LTS):**
   ```bash
   java -version # Amazon Corretto 25 or OpenJDK 25
   ```

### Step-by-Step Setup
```bash
# 1. Clone repository & enter workspace
cd /media/menglanghuo/Menglang/Bronx/PushNotificationSys/PushNotificationSys

# 2. Boot dependencies in Docker (PostgreSQL, Redis, Kafka)
docker compose up -d postgres redis kafka

# 3. Verify Docker health
docker compose ps

# 4. Build and run unit tests with Java 25 & Spring Boot 4.1.0
mvn clean test -f backend/pom.xml

# 5. Boot the backend server locally
mvn spring-boot:run -f backend/pom.xml

# 6. Run the End-to-End Automated Platform Verification
bash scripts/verify_platform.sh
```

---

## 3. Client SDK Integration Playbook

### 3.1 Android Integration (Kotlin)
When initializing your Android application:

```kotlin
// 1. Fetch token from FirebaseMessaging
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (task.isSuccessful) {
        val fcmToken = task.result
        registerDeviceWithPushPlatform(fcmToken)
    }
}

// 2. Register with Unified Push Platform
fun registerDeviceWithPushPlatform(token: String) {
    val payload = JSONObject().apply {
        put("platform", "ANDROID")
        put("pushToken", token)
        put("externalUserId", CurrentUser.id) // e.g., "shopper-888"
        put("deviceModel", Build.MODEL)
        put("osVersion", "Android ${Build.VERSION.RELEASE}")
        put("language", Locale.getDefault().language)
        put("timezone", TimeZone.getDefault().id)
        put("tags", JSONObject().apply {
            put("tier", "gold")
            put("vip", "true")
        })
    }

    val request = Request.Builder()
        .url("https://api.pushnotify.local/sdk/v1/devices")
        .addHeader("X-Sdk-Key", "os_pub_demo9876543210abcdef9876543210")
        .post(payload.toString().toRequestBody("application/json".toMediaType()))
        .build()

    httpClient.newCall(request).execute()
}
```

---

### 3.2 iOS Integration (Swift)
In your `AppDelegate.swift`:

```swift
func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
    let tokenString = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
    
    let payload: [String: Any] = [
        "platform": "IOS",
        "pushToken": tokenString,
        "externalUserId": CurrentUser.id,
        "deviceModel": UIDevice.current.model,
        "osVersion": UIDevice.current.systemVersion,
        "language": Locale.current.languageCode ?? "en",
        "timezone": TimeZone.current.identifier,
        "tags": [
            "tier": "gold",
            "vip": "true"
        ]
    ]
    
    var request = URLRequest(url: URL(string: "https://api.pushnotify.local/sdk/v1/devices")!)
    request.httpMethod = "POST"
    request.addValue("os_pub_demo9876543210abcdef9876543210", forHTTPHeaderField: "X-Sdk-Key")
    request.addValue("application/json", forHTTPHeaderField: "Content-Type")
    request.httpBody = try? JSONSerialization.data(withJSONObject: payload)
    
    URLSession.shared.dataTask(with: request).resume()
}
```

---

### 3.3 Web Browser Integration (Service Worker & PushManager)
In your web application (`service-worker.js` + client):

```typescript
// 1. Subscribe to Browser Push
const registration = await navigator.serviceWorker.register('/service-worker.js');
const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlB64ToUint8Array(VAPID_PUBLIC_KEY)
});

// 2. Register device with Push Platform
const p256dh = btoa(String.fromCharCode.apply(null, new Uint8Array(subscription.getKey('p256dh')!)));
const auth = btoa(String.fromCharCode.apply(null, new Uint8Array(subscription.getKey('auth')!)));

await fetch('https://api.pushnotify.local/sdk/v1/devices', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'X-Sdk-Key': 'os_pub_demo9876543210abcdef9876543210'
    },
    body: JSON.stringify({
        platform: 'WEB',
        pushToken: subscription.endpoint,
        webAuthKey: auth,
        webP256dhKey: p256dh,
        externalUserId: 'shopper-888',
        tags: { browser: 'chrome' }
    })
});
```

---

## 4. Server REST API Integration Playbook

Backend services (Order Service, Billing, Security) dispatch notifications using the **Server REST API Key**:

### 4.1 Authentication & Request Headers
| Header | Required? | Example Value | Description |
| :--- | :--- | :--- | :--- |
| `X-Api-Key` | **Yes** | `os_live_demo1234567890abcdef1234567890` | Server REST API Key for authentication and tenant binding. |
| `Idempotency-Key`| Optional | `tx-order-88192-confirmed` | Unique client UUID/string. If sent again within 24h, returns original response without re-sending. |
| `Content-Type` | **Yes** | `application/json` | Request payload MIME type. |

### 4.2 Target Types & Payloads
The engine supports 4 targeting modes:

```json
// Mode 1: Broadcast to ALL active devices
{ "targetType": "ALL" }

// Mode 2: Dynamic Segment (evaluated in real time)
{ 
  "targetType": "SEGMENT", 
  "targetPayload": { "segmentId": "88888888-4444-4444-4444-444444444441" } 
}

// Mode 3: Specific Device UUIDs
{ 
  "targetType": "DEVICE_IDS", 
  "targetPayload": { "deviceIds": ["d1111111-1111-1111-1111-111111111111"] } 
}

// Mode 4: Custom User Accounts (e.g., your internal user IDs)
{ 
  "targetType": "EXTERNAL_USER_IDS", 
  "targetPayload": { "externalUserIds": ["shopper-888", "shopper-999"] } 
}
```

---

## 5. Kafka Architecture & Failure Modes

### 5.1 Kafka Topics Map
```
notification.created   ---> Router Worker (Splits into 500-device batches)
 ├── dispatch.fcm      ---> FCM Dispatch Worker (Google Multicast HTTP v1)
 ├── dispatch.apns     ---> APNs Dispatch Worker (Apple HTTP/2 streams)
 ├── dispatch.webpush  ---> WebPush Worker (RFC 8291/8292 standard)
 └── dispatch.dlq      ---> Dead Letter Queue (Unrecoverable errors)

events.ingest          ---> Event Consumer (Micro-batch DB persistence)
```

### 5.2 Failure Modes & Resilience Strategies

| Scenario / Outage | System Behavior | Recovery Mechanism |
| :--- | :--- | :--- |
| **PostgreSQL Outage** | Ingestion rejects with HTTP 500 / 503; already-queued Kafka messages pause at consumer offsets. | Once PostgreSQL recovers, Kafka consumers resume processing from committed offsets without message loss. |
| **Redis Outage** | Rate limiter automatically **fails open** so customer pushes are not dropped. STOMP presence pauses. | System continues delivering background notifications through Kafka and database. |
| **Kafka Outage** | Ingestion cannot emit `NotificationCreatedEvent`; returns HTTP 500. | Use Kafka clustered broker replicas in production (replication factor: 3, `min.insync.replicas: 2`). |
| **Apple APNs / Google FCM Slowdown** | Individual worker threads pause on HTTP timeout (10s connect limit). | Work is chunked across Kafka consumer partitions so other apps and platforms remain unaffected. |
| **Fatal Provider Errors** | Invalid payloads or unauthorized accounts are sent to `dispatch.dlq`. | SREs can inspect the DLQ payload and reprocess once credentials are fixed. |

---

## 6. Code Review Checklist for Engineers

When reviewing pull requests in this codebase, verify these rules:
- [ ] **Java 25 & Spring Boot 4 Compatibility:** Does the new code compile with Java 25 & Spring Boot 4 without Lombok or Jackson errors?
- [ ] **Tenant Isolation:** Are all queries filtered by `appId` or scoped through `TenantContextHolder`?
- [ ] **Zero Credential Leaks:** Are provider secrets (APNs p8, FCM JSON) encrypted via `CryptoVaultService`? Are they excluded from logs?
- [ ] **SSRF Validation:** Are all external WebPush or webhook endpoints checked against `SsrfValidator`?
- [ ] **Non-Blocking Ingestion:** Do REST endpoints return immediately with `HTTP 202 Accepted` rather than waiting for Apple/Google HTTP roundtrips?
- [ ] **Database Indexing:** Are new query criteria backed by database indexes in a Flyway migration file (`V...__name.sql`)?
