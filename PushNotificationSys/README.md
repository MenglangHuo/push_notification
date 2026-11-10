# Unified Multi-Tenant Push Notification Engine

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7-blue.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)

An enterprise-grade, high-throughput multi-tenant push notification platform modeled after the internal architecture of OneSignal, Braze, and Firebase Cloud Messaging. It allows organizations to send millions of push notifications across Android (FCM), iOS (APNs), and Web Browsers (W3C Web Push) with sub-second fan-out latency, automatic token churn handling, and complete data sovereignty.

---

## 📚 Complete Business & Engineering Documentation (`/docs`)

Comprehensive documentation is available in the [`docs/`](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/docs/) directory:

1. [**System Overview & Business Value (`docs/README.md`)**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/docs/README.md): Why businesses need this platform, cost savings (90%+ compared to commercial SaaS), key performance indicators (KPIs), and architectural highlights.
2. [**Business Flows & System Lifecycles (`docs/01_BUSINESS_FLOW_AND_LIFECYCLE.md`)**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/docs/01_BUSINESS_FLOW_AND_LIFECYCLE.md): Deep dive into the 3 core personas (Marketers, App Users, Developers) and the 5 operational lifecycles (Tenant Onboarding, Device Subscription, Dynamic AST Segmentation, Campaign Orchestration, and Telemetry Attribution).
3. [**Real-World Production Scenarios (`docs/02_REAL_WORLD_EXAMPLES.md`)**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/docs/02_REAL_WORLD_EXAMPLES.md): Step-by-step walkthroughs with JSON payloads and sequence diagrams for E-Commerce Flash Sales, High-Priority Transactional Alerts (Dual Push + WebSocket), Clustered Campaign Scheduling (ShedLock), and Dead Token Auto-Recovery.
4. [**Developer Integration Playbook (`docs/03_DEVELOPER_INTEGRATION_GUIDE.md`)**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/docs/03_DEVELOPER_INTEGRATION_GUIDE.md): 5-minute quickstart, Client SDK integration code snippets (Android Kotlin, iOS Swift, Web JS/TS), Server REST API guide, Kafka topic maps, and reviewer code checklist.
5. [**Setup, Runbook & Operations Manual (`docs/04_SETUP_AND_OPERATIONS_MANUAL.md`)**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/docs/04_SETUP_AND_OPERATIONS_MANUAL.md): Hardware & software prerequisites, bare-metal vs full Docker Compose startup guides, initial developer credentials vault configuration, and complete operational admin guide for managing client apps, segments, team RBAC, and campaigns.

---

## ⚡ Quickstart

### 1. Boot Required Infrastructure
```bash
cd PushNotificationSys && docker compose up -d postgres redis kafka
```

### 2. Build & Verify with Java 21 (Mandatory)
```bash
JAVA_HOME=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn \
PATH=/home/menglanghuo/.sdkman/candidates/java/21.0.10-amzn/bin:$PATH \
mvn clean test -f PushNotificationSys/backend/pom.xml
```

### 3. Run End-to-End Verification
```bash
bash PushNotificationSys/scripts/verify_platform.sh
```

---

## 🔍 Core Architecture & Specifications
- [**`BACKEND_ARCHITECTURE.md`**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/BACKEND_ARCHITECTURE.md) - Deep system architecture, Mermaid diagrams, database schemas, and cryptographic vault details.
- [**`API_REFERENCE.md`**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/API_REFERENCE.md) - Ultra-compact REST/SDK endpoint cheatsheet.
- [**`AG_RULES.md`**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/AG_RULES.md) - Operational guidelines and runtime invariants for AI coding assistants.
- [**`MEMORY.md`**](file:///media/menglanghuo/Menglang/Bronx/PushNotificationSys/MEMORY.md) - Persistent memory of test credentials, seeded UUIDs, ports, and Kafka topic mappings.
