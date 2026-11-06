# Persistent Antigravity Memory (MEMORY.md)

> **Workspace:** `/media/menglanghuo/Menglang/Bronx/PushNotificationSys`  
> **Subproject:** `PushNotificationSys/` (Backend in `PushNotificationSys/backend`)  
> **Architecture:** OneSignal-Grade Multi-Tenant Push Notification Engine  
> **Status:** Fully implemented, compiling cleanly with Java 21, all unit tests passing.

---

## 1. Quick Reference & Environment Paths

| Resource | Value / Path | Notes |
| :--- | :--- | :--- |
| **Mandatory JDK** | `/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn` | **NEVER use host default Java 25** (breaks Lombok javac plugin) |
| **Backend Dir** | `PushNotificationSys/backend` | Root of `pom.xml` |
| **PostgreSQL 16** | `localhost:5432` | DB: `push_platform`, User: `postgres`, Pass: `postgres_password` |
| **Redis 7** | `localhost:6380` | Host port is **6380** (mapped to container 6379 in docker-compose) |
| **Apache Kafka 3.7**| `localhost:9092` | Internal listener: `kafka:29092` |
| **Backend Port** | `http://localhost:8080` | Swagger: `/swagger-ui/index.html`, Actuator: `/actuator/health` |
| **Master AES Key** | `k4P8q3Z1y7W2v9T0x5R8u1O3m6L9j2H5g7E0b3D6a8=` | Configured in `app.crypto.master-key` |

---

## 2. Seed Accounts & Test Credentials (From Flyway Migrations)

- **Platform Super Admin:**
  - Email: `admin@pushnotify.local`
  - Password: `Password123!` (Argon2id hashed)
  - User ID: `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`
- **Demo Owner:**
  - Email: `demo@pushnotify.local`
  - Password: `Password123!`
  - User ID: `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb`
- **Pre-seeded Organization:**
  - ID: `cccccccc-cccc-cccc-cccc-cccccccccccc`
  - Name: `Acme Corporation` (Slug: `acme`, Tier: `ENTERPRISE`)
- **Pre-seeded Application:**
  - ID: `11111111-2222-3333-4444-555555555551`
  - Name: `Acme Retail Store`
- **Pre-seeded API Keys:**
  - **Server REST API Key:** `os_live_demo1234567890abcdef1234567890` (Header: `X-Api-Key`)
  - **Client SDK Public Key:** `os_pub_demo9876543210abcdef9876543210` (Header: `X-Sdk-Key`)

---

## 3. Fast Command Cheat Sheet

```bash
# 1. Compile backend with mandatory Java 21
JAVA_HOME=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn PATH=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn/bin:$PATH mvn test-compile -DskipTests -f PushNotificationSys/backend/pom.xml

# 2. Run core unit tests
JAVA_HOME=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn PATH=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn/bin:$PATH mvn test -Dtest=RateLimitingServiceTest,CryptoVaultServiceTest,ApnsPayloadBuilderTest,SsrfValidatorTest,SegmentSpecificationBuilderTest -f PushNotificationSys/backend/pom.xml

# 3. Boot container infrastructure
cd PushNotificationSys && docker compose up -d postgres redis kafka

# 4. Run Spring Boot application locally
JAVA_HOME=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn PATH=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn/bin:$PATH mvn spring-boot:run -f PushNotificationSys/backend/pom.xml

# 5. Run end-to-end verification script
bash PushNotificationSys/scripts/verify_platform.sh
```

---

## 4. Architecture & Module Index

```
PushNotificationSys/backend/src/main/java/com/onesignal/platform/
├── iam/
│   ├── api/AuthController.java               # POST /admin/api/v1/auth/login, /refresh, /mfa
│   ├── api/OrganizationController.java       # GET/POST /admin/api/v1/organizations
│   └── application/AuthService.java           # Argon2, JWT & Refresh cookie issue
├── app/
│   ├── api/AppAdminController.java            # GET/POST /admin/api/v1/organizations/{orgId}/apps
│   └── application/CryptoVaultService.java    # AES-256-GCM cipher encryption & decryption
├── apikey/
│   ├── api/ApiKeyAdminController.java         # GET/POST /admin/api/v1/apps/{appId}/api-keys
│   └── application/ApiKeyService.java         # Generate, hash, rotate & verify API keys
├── device/
│   ├── api/DeviceSdkController.java           # POST /sdk/v1/devices (Upsert with tags)
│   ├── api/DeviceAdminController.java        # GET /admin/api/v1/apps/{appId}/devices
│   └── application/DeviceRegistrationService.java # Device upsert & deactivation
├── segment/
│   ├── api/SegmentAdminController.java        # GET/POST /admin/api/v1/apps/{appId}/segments
│   ├── application/SegmentService.java        # Segment evaluation & device resolution
│   └── infrastructure/SegmentSpecificationBuilder.java # AST -> JPA Criteria Specification
├── notification/
│   ├── api/NotificationServerApiController.java # POST /api/v1/notifications (X-Api-Key)
│   ├── api/NotificationAdminController.java     # GET/POST /admin/api/v1/apps/{appId}/notifications
│   ├── application/NotificationService.java     # Notification persistence & dispatch
│   └── application/CampaignSchedulerService.java # ShedLock scheduled campaign execution
├── dispatch/
│   ├── consumer/NotificationRouterConsumer.java # Fans out into batches of 500
│   ├── consumer/FcmDispatchConsumer.java        # FCM HTTP v1 multicast worker
│   ├── consumer/ApnsDispatchConsumer.java       # APNs HTTP/2 worker
│   ├── consumer/WebPushDispatchConsumer.java    # WebPush RFC 8291/8292 worker
│   ├── service/FcmProvider.java                 # Firebase Messaging wrapper
│   ├── service/ApnsProvider.java                # Apple HTTP/2 client
│   ├── service/WebPushProvider.java             # WebPush crypto & HTTP dispatcher
│   └── service/SsrfValidator.java               # Blocks loopback/private/internal IP endpoints
├── realtime/
│   ├── api/WebSocketInboundController.java      # STOMP /app/inbound controller
│   ├── service/RealtimeNotificationRelayService.java # Redis Pub/Sub STOMP relay
│   └── service/PresenceService.java             # Redis set presence tracking (120s TTL)
├── event/
│   ├── api/SdkEventController.java              # POST /sdk/v1/events (DELIVERED, OPENED, CLICKED)
│   ├── consumer/EventIngestConsumer.java        # Kafka consumer batch writer
│   └── infrastructure/EventIngestProducer.java  # Kafka events.ingest producer
├── analytics/
│   ├── api/AnalyticsAdminController.java        # GET /admin/api/v1/apps/{appId}/analytics/overview
│   └── application/AnalyticsService.java        # CTR%, delivery rate & timeline rollups
└── audit/
    ├── api/AuditController.java                 # GET /admin/api/v1/organizations/{orgId}/audit-logs
    └── application/AuditLogService.java         # Audit trail recorder
```

---

## 5. Kafka Topics & Payloads

| Topic Name | Payload Type | Description |
| :--- | :--- | :--- |
| `notification.created` | `NotificationCreatedEvent` | Ingested notifications ready for recipient fan-out |
| `dispatch.fcm` | `FcmDispatchBatchMessage` | Chunks of max 500 Android FCM push recipients |
| `dispatch.apns` | `ApnsDispatchBatchMessage` | Chunks of max 500 iOS APNs push recipients |
| `dispatch.webpush` | `WebPushDispatchBatchMessage` | Chunks of max 500 WebPush browser recipients |
| `events.ingest` | `EventBatchIngestMessage` | Inbound SDK telemetry events for batch DB persistence |
| `dispatch.dlq` | Generic Object + Error | Dead letter queue for unrecoverable provider failures |

---

## 6. Known Gotchas & Architectural Pitfalls

1. **Java Version:** Running Maven with system default (`java -version` returning 25) will fail compilation. Must use `21.0.10-amzn`.
2. **Redis Port:** `application.yml` defaults `REDIS_PORT` to `6380` on host, because Docker maps `6380:6379`. Inside Docker container network it uses `6379`.
3. **Simulated Dispatch Mode:** If live FCM/APNs credentials are not configured in `provider_credentials`, the dispatchers log a warning and run in **simulated dispatch mode** rather than crashing. This enables full integration tests and local sandbox verifications without paid Apple/Google developer accounts.
4. **SSRF Guard:** WebPush endpoint URLs must use `https://` on port `443` and cannot resolve to private IP spaces (`127.0.0.1`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.169.254`). Mock URLs (`mock://` or `sample_web_token`) are whitelisted for tests.
5. **ThreadLocal Context:** `TenantContextFilter` populates `TenantContextHolder` and clears it in `finally`. When running async tasks or Kafka consumers, `TenantContextHolder` is not present unless manually populated.
