# Antigravity Project Context & Operational Rules

This workspace contains the Multi-Tenant Push Notification Engine. Follow these foundational rules across all tasks:

## 1. Critical Environment Invariant (Java 21 Required)
- **Mandatory JDK:** Amazon Corretto Java 21 (`21.0.10-amzn`).
- **Do NOT use default Java:** The host default is Java 25, which causes fatal compiler failures with Lombok.
- **Always prefix Maven commands with:**
  ```bash
  JAVA_HOME=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn PATH=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn/bin:$PATH mvn <command>
  ```
- **Maven Working Directory:** `PushNotificationSys/backend/` contains `pom.xml`. Always run Maven commands from that directory or supply `-f PushNotificationSys/backend/pom.xml`.

## 2. Fast-Path Context Documentation
Before scanning or reading large source code files, consult these references on demand:
- **[AG_RULES.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/AG_RULES.md):** Complete operational invariants, DDD module boundaries, tenant isolation rules, AES-256-GCM crypto rules, token optimization instructions, and test commands.
- **[MEMORY.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/MEMORY.md):** System credentials, Docker ports (Postgres: 5432, Redis: 6380, Kafka: 9092), Kafka topic mappings, seed IDs, and directory index.
- **[BACKEND_ARCHITECTURE.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/BACKEND_ARCHITECTURE.md):** End-to-end architecture diagrams, domain entities, dispatch pipelines, and worker flows.
- **[FRONTEND_ARCHITECTURE.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/FRONTEND_ARCHITECTURE.md):** Frontend architecture, Vue 3 + Vite structure, state management, and UI conventions.
- **[API_REFERENCE.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/API_REFERENCE.md):** Complete REST API contract, endpoints, payload schemas, and authentication headers.

## 3. Surgical Code Modifications & Token Efficiency
- Do NOT read full files over 200 lines; use grep or line-bounded `view_file` slices (50-80 lines).
- Always use `replace_file_content` for surgical diffs. Do not rewrite existing files completely.

## 4. Documentation Maintenance Directive
Whenever implementing, modifying, or refactoring:
- **Backend Architecture or APIs**: Proactively update [BACKEND_ARCHITECTURE.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/BACKEND_ARCHITECTURE.md) and [API_REFERENCE.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/API_REFERENCE.md).
- **Frontend Architecture, State, or Components**: Proactively update [FRONTEND_ARCHITECTURE.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/FRONTEND_ARCHITECTURE.md).
- **Environment, Ports, Kafka Topics, Redis Keys, or Seed UUIDs**: Proactively update [MEMORY.md](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/MEMORY.md).

