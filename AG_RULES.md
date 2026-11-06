# Antigravity Operational Rules & Engineering Guidelines (AG_RULES.md)

> **Workspace:** `/media/menglanghuo/Menglang/Bronx/PushNotificationSys`  
> **Domain:** Multi-Tenant Push Notification Engine (OneSignal Architecture)  
> **Target:** Antigravity AI Coding Assistant & Engineers

---

## 1. Runtime Environment & Toolchain Invariants

- **Java Version Invariant (CRITICAL):**
  - **MANDATORY JDK:** Amazon Corretto Java 21 (`21.0.10-amzn`).
  - **DO NOT USE DEFAULT JAVA:** The host system's default JDK is Java 25 (`25.0.3-amzn`), which causes fatal compiler failures with Lombok (`com.sun.tools.javac.code.TypeTag :: UNKNOWN`).
  - **Always prefix Maven commands with:**
    ```bash
    JAVA_HOME=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn PATH=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn/bin:$PATH mvn <command>
    ```
- **Working Directory for Maven:**
  - `PushNotificationSys/backend/` contains `pom.xml`. Always run Maven commands from that directory or supply `-f PushNotificationSys/backend/pom.xml`.
- **Infrastructure Services (Docker Ports):**
  - **PostgreSQL 16:** Port `5432` (database: `push_platform`, user: `postgres`, pass: `postgres_password`).
  - **Redis 7:** Host port `6380` (mapped to container port `6379`).
  - **Kafka 3.7:** Port `9092` (host) / `29092` (internal).

---

## 2. Architecture & Design Patterns

- **Modular Monolith & DDD Boundaries:**
  - Modules in `com.onesignal.platform` (`iam`, `app`, `apikey`, `device`, `segment`, `notification`, `dispatch`, `realtime`, `event`, `analytics`, `audit`) must remain strictly decoupled.
  - Cross-module communication must proceed through `@Service` interfaces or Kafka events (`NotificationCreatedEvent`, `FcmDispatchBatchMessage`, `ApnsDispatchBatchMessage`, `WebPushDispatchBatchMessage`, `EventBatchIngestMessage`).
  - **NO cross-boundary direct JPA repository joins or foreign entity updates.**
- **Tenant Isolation:**
  - Never query `devices`, `notifications`, `segments`, `events`, or `delivery_logs` without scoping by `app_id` or verifying `TenantContextHolder.getContext()`.
  - Always clean up ThreadLocal state via `TenantContextHolder.clear()` in filter `finally` blocks.
- **Provider Credentials & Cryptographic Security:**
  - Never store raw FCM Service Account JSONs, APNs `.p8` private keys, or WebPush VAPID secrets in plaintext or logs.
  - Always route credentials through `CryptoVaultService.encrypt()` and `decrypt()` (AES-256-GCM with 96-bit random IV).
  - WebPush subscription endpoints must always be validated via `SsrfValidator.validateEndpoint()` before any outbound HTTP dispatch.
- **Database Migrations:**
  - Never modify live schema using `hibernate.ddl-auto=update`. Schema changes must be recorded as incremental Flyway scripts in `src/main/resources/db/migration/` (e.g., `V6__<description>.sql`).

---

## 3. Antigravity Token Optimization & Review Directives

To maximize agent performance and minimize token consumption:
1. **Targeted Code Reads:**
   - Do NOT read full files over 200 lines. Use grep or `find` to pinpoint line numbers, then call `view_file` with precise `StartLine` and `EndLine` ranges (max 50-80 lines).
2. **Surgical Diffs:**
   - Always use `replace_file_content` for editing code. Never replace an entire file with `write_to_file` unless creating a new file from scratch.
3. **Reference Memory First:**
   - Check `MEMORY.md` for existing endpoints, seed data, UUIDs, Kafka topics, and configuration before reading source code.
4. **Command Execution Efficiency:**
   - Run tests selectively using `-Dtest=ClassName` rather than the entire test suite when verifying a specific feature or bug fix.
   - For verification of end-to-end functionality, refer to `PushNotificationSys/scripts/verify_platform.sh`.

---

## 4. Testing & Verification Checklist

- **Unit Tests:** Run without Docker using mock dependencies:
  ```bash
  JAVA_HOME=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn PATH=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn/bin:$PATH mvn test -Dtest=RateLimitingServiceTest,CryptoVaultServiceTest,ApnsPayloadBuilderTest,SsrfValidatorTest,SegmentSpecificationBuilderTest
  ```
- **End-to-End Verification:**
  - Ensure Docker stack is up: `docker compose up -d postgres redis kafka`
  - Run the platform verification script: `bash PushNotificationSys/scripts/verify_platform.sh`
