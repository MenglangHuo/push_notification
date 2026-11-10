# 🎫 TicketAdmin — Bronx Operations Console

<p align="center">
  <strong>Enterprise-grade ticket management, customer support operations, and notification dispatcher.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Vue-3.5+-4FC08D?style=for-the-badge&logo=vue.js&logoColor=white" alt="Vue 3" />
  <img src="https://img.shields.io/badge/TypeScript-6.0-3178C6?style=for-the-badge&logo=typescript&logoColor=white" alt="TypeScript" />
  <img src="https://img.shields.io/badge/Vite-8.2-646CFF?style=for-the-badge&logo=vite&logoColor=white" alt="Vite" />
  <img src="https://img.shields.io/badge/Tailwind_CSS-v4.3-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white" alt="Tailwind CSS v4" />
  <img src="https://img.shields.io/badge/Pinia-4.0-FFD859?style=for-the-badge&logo=pinia&logoColor=black" alt="Pinia" />
  <img src="https://img.shields.io/badge/Vitest-4.1-6E9F18?style=for-the-badge&logo=vitest&logoColor=white" alt="Vitest" />
  <img src="https://img.shields.io/badge/Node-%3E%3D22.18.0-339933?style=for-the-badge&logo=node.js&logoColor=white" alt="Node Engine" />
</p>

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [System Architecture & Tech Stack](#-system-architecture--tech-stack)
- [Project Directory Structure](#-project-directory-structure)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Environment Setup](#environment-setup)
  - [Running Development Server](#running-development-server)
- [Multi-Environment Architecture & Runtime Switcher](#-multi-environment-architecture--runtime-switcher)
- [Mock Data Sandbox & Offline Mode](#-mock-data-sandbox--offline-mode)
- [Environment Variables Reference](#-environment-variables-reference)
- [Role-Based Access Control (RBAC)](#-role-based-access-control-rbac)
- [Available Scripts](#-available-scripts)
- [Security Architecture](#-security-architecture)
- [Developer Tooling & Recommended Setup](#-developer-tooling--recommended-setup)
- [Contributing & Code Standards](#-contributing--code-standards)

---

## 🌟 Overview

**TicketAdmin** is a centralized administrative console designed for high-velocity support workflows and multi-tenant issue tracking within the Bronx ecosystem. 

Engineered with **Vue 3 (Composition API)**, **TypeScript**, and **Tailwind CSS v4**, TicketAdmin bridges customer requests submitted across multiple ingestion channels—including authenticated REST APIs, public self-service portals, and staff consoles—directly with operational agents, featuring real-time **Telegram Bot outbox notifications** for urgent escalations.

---

## ✨ Key Features

### 🌐 Multi-Environment Switching & Build Modes (Enterprise Standard)
- **Multi-Environment Build Modes**: Seamless presets for **Development (Local)**, **Production (Live)**, and **Mock Sandbox** via standard Vite modes.
- **In-App Runtime Environment Switcher**: Instant switching between backend targets directly from the top navigation bar or the login screen—no recompile or dev server restart required.
- **Live Endpoint Diagnostics**: Integrated **Ping test** measuring server reachability, HTTP response codes, and round-trip network latency in milliseconds.
- **Dynamic Origin & Token Isolation**: Automated session clearance option when switching between distinct domains to prevent 401 redirect loops, paired with origin-bounded token security.
- **Production Guardrail**: Visual caution indicators and warning badges when operating against live customer production clusters.

### 🧪 Mock Data Engine & Zero-Backend Mode
- **Zero-Backend Development**: Test and preview the entire administrative console immediately without running or provisioning backend services.
- **Full Page Simulation**: Complete mock data coverage across Tickets, Dashboard Analytics, Telegram Settings, Connected Clients, and Login.
- **Stateful In-Memory Store**: Add comments, create tickets, transition statuses, search, and generate invite links with reactive updates.
- **One-Click Demo Authentication**: Instant sign-in buttons for `HQ ADMIN` and `BRANCH MANAGER` roles on the login screen.

### 📋 Multi-Modal Ticket Workspace
- **Kanban Board**: Drag-and-drop cards across workflow columns (`Open`, `In Progress`, `Resolved`, `Closed`) with instant status transitions.
- **Interactive Data Table**: High-density view featuring configurable column visibility, facet counters, sorting, and pagination.
- **Faceted Search & Filters**: Filter instantly by Status, Priority (`Low`, `Medium`, `High`, `Critical`), Issue Type (`Bug`, `Enhancement`, `Question`, `Task`), Source, and Assignee.
- **Detailed Ticket Inspection**: Slide-over drawer and focused modal providing full ticket metadata, author info, status audit history, and direct assignee management.

### ✍️ Hybrid WYSIWYG & HTML Code Editor
- **Rich Text Mode**: Powered by **Tiptap** with support for headings, bold/italic formatting, links, lists, and code blocks.
- **Code Mode**: Direct HTML editing powered by **CodeMirror 6** with real-time syntax highlighting and instantaneous preview rendering.
- **Safe Rendering**: Built-in sanitization pipeline to safeguard agents from arbitrary markup injections.

### 💬 Threaded Collaboration & Attachments
- **Internal Notes vs. Public Discussion**: Distinguish between private staff notes and customer-facing responses.
- **Multi-File Attachments**: Drag-and-drop file uploaders with file size validation, mime-type detection, and preview attachments.

### 📊 Real-Time Analytics & Executive Reporting
- **KPI Metrics Dashboard**: Monitor total volume, active workload, resolution velocity, and critical open tickets.
- **Visual Analytics**: Interactive status distribution donuts, ticket category breakdown, and priority metrics.
- **Dynamic Date Filtering**: Built-in date range presets (`Today`, `Last 7 Days`, `Last 30 Days`, `This Month`, and `Custom Range`) with instant in-memory caching.

### 🤖 Telegram Bot Dispatcher & Subscriber Management
- **Instant Escalation Alerts**: Dispatches incoming ticket alerts directly to private Telegram chats of on-call engineers.
- **One-Click Invite Generation**: Generate cryptographically secured bot onboarding invite links on demand.
- **Subscriber Directory**: Inspect active chat IDs, toggle notification subscriptions, and fire automated test pings.

### 🔌 Connected API Clients Management (`/clients`)
- **Integration Overview**: Inspect registered external consumer applications, authorized endpoints, and active security tokens.
- **Rate Limit & Scope Auditing**: Monitor assigned requests-per-minute quotas and allowed ticket categories per client.

### 🌓 Adaptive Theming
- Native Dark / Light / System auto-detection with smooth transitions and persistent state.

---

## 🛠 System Architecture & Tech Stack

| Category | Technology | Description |
| :--- | :--- | :--- |
| **Core Framework** | [Vue 3](https://vuejs.org/) (`^3.5.42`) | Progressive frontend framework using `<script setup>` Composition API |
| **Language** | [TypeScript](https://www.typescriptlang.org/) (`~6.0.0`) | Strict type checking and enterprise contract validation |
| **Build & Tooling** | [Vite](https://vite.dev/) (`^8.2.2`) | Next-generation frontend tooling with lightning-fast HMR |
| **Styling** | [Tailwind CSS v4](https://tailwindcss.com/) (`^4.3.3`) | Modern utility-first CSS engine via `@tailwindcss/vite` |
| **State Management** | [Pinia](https://pinia.vuejs.org/) (`^4.0.3`) | Modular, type-safe state stores (`auth`, `ticket`, `report`, `settings`, `toast`, `theme`) |
| **Routing** | [Vue Router](https://router.vuejs.org/) (`^5.3.1`) | Client-side routing with navigation authentication guards |
| **Rich Text Editor** | [Tiptap](https://tiptap.dev/) (`^3.31.4`) | Headless WYSIWYG editor framework |
| **Code Editor** | [CodeMirror 6](https://codemirror.net/) | Extensible in-browser code editor with HTML grammar highlighting |
| **HTTP Client** | [Axios](https://axios-http.com/) (`^1.20.0`) | Promise-based HTTP client with origin-sanitized token injection |
| **Icons** | [Lucide Vue Next](https://lucide.dev/) (`^1.0.0`) | Clean, consistent SVG icon system |
| **Testing** | [Vitest](https://vitest.dev/) (`^4.1.11`) + Vue Test Utils | Unit and component testing suite |
| **Code Quality** | ESLint (`^10.10.0`), Oxlint (`~1.82.0`), Prettier | Multi-stage linting, fast static analysis, and code formatting |

---

## 📂 Project Directory Structure

```text
ticket-admin/
├── public/                 # Static assets & public favicon
├── src/
│   ├── __tests__/          # Vitest unit test suites (auth, environment, tickets, etc.)
│   ├── api/                # API communication layer
│   │   ├── client.ts       # Dynamic Axios instance, runtime env baseURL, origin security
│   │   ├── reportApi.ts    # Dashboard analytics & reporting endpoints
│   │   ├── telegramApi.ts  # Telegram bot subscriber & invite endpoints
│   │   └── ticketApi.ts    # Tickets, comments, attachments & auth endpoints
│   ├── assets/             # Global styles and Tailwind configuration
│   ├── components/         # Reusable Vue components
│   │   ├── common/         # Base UI, EnvironmentBadge, WYSIWYG
│   │   ├── dashboard/      # Metrics, status donut, and trend charts
│   │   ├── layout/         # Topbar, Sidebar, Notification & Toast containers
│   │   ├── settings/       # Telegram bot config, subscribers table & invite form
│   │   └── tickets/        # Kanban board, table view, modals & ticket cards
│   ├── config/             # Multi-environment configuration manager
│   │   └── environment.ts  # Profile resolver, localStorage persistence & event sync
│   ├── mocks/              # Mock data engine & offline development provider
│   │   ├── config.ts       # Mock flag evaluator, localStorage sync & test isolation
│   │   ├── mockService.ts  # Unified mock API service facade
│   │   └── data/           # Realistic datasets (tickets, reports, settings, clients, auth)
│   ├── router/             # Vue Router routes and authentication guards
│   ├── stores/             # Pinia centralized stores
│   │   ├── authStore.ts    # Authentication state, JWT tokens & RBAC permissions
│   │   ├── reportStore.ts  # Reporting cache and date range analytics
│   │   ├── settingsStore.ts# Telegram bot status and subscriber state
│   │   ├── themeStore.ts   # Dark / Light / System theme controller
│   │   ├── ticketStore.ts  # Ticket CRUD, filtering, pagination & comments
│   │   └── toastStore.ts   # Toast notifications
│   ├── types/              # TypeScript interfaces and domain schemas
│   ├── utils/              # Helper utilities (HTML sanitizer, security helpers)
│   ├── views/              # Page views (Tickets, Dashboard, Clients, Settings, Login)
│   ├── App.vue             # Root component
│   ├── env.d.ts            # TypeScript definitions for Vite environment variables
│   └── main.ts             # Application bootstrapping entrypoint
├── .env                    # Local environment overrides (gitignored)
├── .env.development        # Development environment configuration
├── .env.production         # Production live release environment configuration
├── .env.mock               # Offline mock sandbox environment configuration
├── .env.example            # Comprehensive environment configuration reference
├── eslint.config.ts        # ESLint flat configuration
├── index.html              # HTML entrypoint
├── package.json            # Project dependencies and multi-mode script declarations
├── tsconfig.json           # TypeScript configuration
└── vite.config.ts          # Vite configuration with Tailwind CSS & dynamic proxying
```

---

## 🚀 Getting Started

### Prerequisites

Ensure your development environment meets the minimum engine specifications:
- **Node.js**: `^22.18.0` or `>=24.12.0`
- **Package Manager**: [pnpm](https://pnpm.io/) (recommended), `npm`, or `yarn`

### Installation

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd ticket-admin
   ```

2. **Install project dependencies:**
   ```bash
   pnpm install
   ```

### Environment Setup

Copy the environment template and customize the variables for your target backend:

```bash
cp .env.example .env
```

Configure your `.env` file according to your backend host (see [Environment Variables Reference](#-environment-variables-reference)).

### Running Development Server

Start Vite's local development server with Hot Module Replacement (HMR) for your target environment:

```bash
# 1. Local Development (default: connects to local Laravel API via /api proxy)
pnpm dev

# 2. Production Mode (connects to live production cluster)
pnpm dev:prod

# 3. Offline Mock Mode (runs standalone without requiring any backend)
pnpm dev:mock
```

Open your browser and navigate to `http://localhost:4000`.

---

## 🌐 Multi-Environment Architecture & Runtime Switcher

TicketAdmin provides an enterprise-standard multi-environment architecture matching Google internal dashboards and modern web consoles, supporting both **build/dev-time environment modes** and **instant in-app runtime switching**.

### Supported Environments
| Environment | Key | Target API Origin | Description | Status Pill |
| :--- | :--- | :--- | :--- | :--- |
| **Development** | `development` | `http://127.0.0.1:8000` (`/api`) | Local backend server with Vite dev reverse proxy | 🟢 `Local Dev` |
| **Production** | `production` | Configured via `.env.production` | Live customer production cluster | Hidden in production |
| **Mock Sandbox** | `mock` | `mock://in-memory` | Zero-backend in-memory simulation | 🟣 `Mock Data` |
| **Custom Host** | `custom` | User-defined URL | PR preview branch or custom cluster endpoint | 🔵 `Custom` |

### 1. Build & Dev Time Environment Selection
Vite modes load environment files in standard cascade order:
- `pnpm dev`: Runs `vite` using `.env.development`.
- `pnpm dev:prod`: Runs `vite --mode production` proxying to the production cluster.
- `pnpm dev:mock`: Runs `vite --mode mock` activating the client-side mock backend.
- `pnpm build`: Produces an optimized production bundle (`--mode production`).
- `pnpm build:dev`: Produces a development build (`--mode development`).

### 2. In-App Runtime Environment Switcher
Switch backend targets on the fly without re-bundling or restarting Vite:
1. **Interactive Environment Pill**: Located in the **Topbar** (and floating on the **Login** page), showing the active target with live status indicators.
2. **Environment & API Modal**:
   - Select between any of the preset environments or enter a custom host URL.
   - **Production Safety Banner**: Visual caution warning alerts before executing changes against live customer data.
   - **Live Backend Ping**: Actively checks backend endpoint reachability (`GET /tickets?per_page=1`), reporting HTTP status and round-trip latency in milliseconds.
   - **Session Reset Protection**: Automatically clears stored session tokens on domain change to prevent 401 redirect loops caused by token mismatches between distinct backend servers.
   - **Reset to .env Defaults**: Clears runtime `localStorage` overrides with one click.

---

## 🧪 Mock Data Sandbox & Offline Mode

TicketAdmin features a comprehensive client-side mock data provider enabling zero-backend development, UI testing, and presentation demos.

### What is Mocked?
| Page / Area | Route | Mock Capabilities & Dataset |
| :--- | :--- | :--- |
| **Tickets Console** | `/tickets` | 12+ preloaded tickets (Bugs, Enhancements, Tasks) with realistic Khmer & English text, full-text search, column toggles, category/priority facets, comment threads, attachments, and Kanban drag-and-drop state. |
| **Executive Dashboard** | `/dashboard` | Complete KPI summary cards, status donuts, priority breakdowns, top clients volume ranking, and interactive daily trend points. |
| **Telegram Settings** | `/settings` | Telegram Bot status, 8 team subscribers with active/inactive notification toggles, test dispatch pings, and dynamic cryptographically formatted bot invite links. |
| **Connected Clients** | `/clients` | 5 external API clients (POS Terminal, Mobile App, Payment Gateway, Webhook Dispatcher) with rate limit badges and authorized category tags. |
| **Authentication & 2FA** | `/login`, `/two-factor` | Primary credential auth, automatic RBAC role routing, dedicated **HQ Admin 2-Factor Authentication Checkpoint** with 6-digit OTP input, rate limiting, challenge expiration, and demo helpers. |

### Vitest Test Suite Isolation
When running automated unit tests (`pnpm test:unit`), the mock provider automatically stays disabled (`import.meta.env.MODE === 'test'`) to ensure Axios network interceptors and backend error paths remain cleanly testable. Individual test suites can explicitly activate mock mode via `setMockDataOverride(true)` when needed.

---

## ⚙️ Environment Variables Reference

| Variable | Type | Default Value | Description |
| `VITE_APP_ENV` | `string` | `'development'` | Active environment identifier (`development`, `production`, `mock`). |
| `VITE_PORT` | `number` | `4000` | Port for the local Vite dev server and preview server. |
| `VITE_PROXY_TARGET` | `string` | `http://127.0.0.1:8000` | Target host forwarded by Vite's development reverse proxy. |
| `VITE_API_BASE_URL` | `string` | `/api` | Base REST API endpoint for ticket, report, and subscriber endpoints. |
| `VITE_AUTH_BASE_URL` | `string` | `""` | Host URL for root auth endpoints (`/login`, `/two-factor`). |
| `VITE_ENABLE_MOCK_DATA` | `boolean` | `false` | Enables client-side mock data provider across all pages without requiring a backend. |
| `VITE_USE_MOCK_DATA` | `boolean` | `false` | Backward-compatible fallback alias for `VITE_ENABLE_MOCK_DATA`. |

---

## 🛡️ Role-Based Access Control (RBAC)

TicketAdmin enforces strict permission controls based on the authenticated user's role:

| Capability | HQ Admin (`HQ ADMIN`) | Branch Staff (`BRANCH STAFF`) | Preview / Viewer (`PREVIEW`) |
| :--- | :---: | :---: | :---: |
| **View Tickets & Kanban** | ✅ | ✅ | ✅ |
| **Search & Apply Filters** | ✅ | ✅ | ✅ |
| **Create New Ticket** | ✅ | ✅ | ❌ |
| **Transition Ticket Status** | ✅ | ✅ | ❌ |
| **Delete Ticket** | ✅ | ❌ | ❌ |
| **Post Discussion Comments** | ✅ | ✅ | ❌ |
| **Post Internal Notes** | ✅ | ✅ | ❌ |
| **Generate Telegram Invites** | ✅ | ✅ | ❌ |
| **Manage Subscribers & Test Pings** | ✅ | ❌ | ❌ |
| **Two-Factor Authentication (2FA)** | Enforced | Optional | Optional |

---

## 📜 Available Scripts

| Command | Description |
| :--- | :--- |
| `pnpm dev` | Starts Vite local dev server with HMR in Development mode (proxying to `http://127.0.0.1:8000`). |
| `pnpm dev:prod` | Starts Vite dev server in Production mode (proxying to production cluster). |
| `pnpm dev:mock` | Starts Vite dev server in Mock mode with in-memory mock backend. |
| `pnpm build` | Executes TypeScript type-checking (`vue-tsc`) and builds production assets for release. |
| `pnpm build:dev` | Executes type-checking and builds development assets. |
| `pnpm build-only` | Runs Vite production bundling directly without type-checking. |
| `pnpm preview` | Serves the production build locally for verification. |
| `pnpm preview:prod` | Serves the production build locally for verification. |
| `pnpm test:unit` | Executes all Vitest unit and integration test suites. |
| `pnpm type-check` | Performs static TypeScript validation across `.vue` and `.ts` files via `vue-tsc`. |
| `pnpm lint` | Runs sequential linting passes using `oxlint` and `eslint --fix`. |
| `pnpm lint:oxlint` | Runs Oxlint for ultra-fast JavaScript/TypeScript linting. |
| `pnpm lint:eslint` | Runs ESLint for comprehensive Vue & TypeScript static analysis. |
| `pnpm format` | Formats codebase using Prettier. |

---

## 🔒 Security Architecture

TicketAdmin incorporates defensive measures to safeguard corporate assets and credentials:

1. **Origin-Bounded Token Injection & Multi-Environment Protection**:
   The Axios interceptor strictly validates target URLs against the active runtime environment's authorized origins (`apiBaseUrl` and `authBaseUrl`) before injecting the `Authorization: Bearer <token>` header, preventing token leakage to third-party domains when switching environments.
2. **XSS & Markup Sanitization**:
   All user-provided HTML descriptions render through a dedicated viewer with sanitization logic to neutralize malicious script vectors and dangerous attributes.
3. **Hardened HTTP Response Headers**:
   The development and preview servers enforce secure defense-in-depth headers including `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and restrictive `Permissions-Policy`.
4. **Automated Session Revocation & Multi-Tenant Cleansing**:
   On receiving HTTP `401 Unauthorized` responses from trusted backend endpoints—or upon switching backend environment hosts—the client cleans stored authorization tokens to prevent authentication desynchronization and redirects unauthenticated users to `/login`.

---

## 💻 Developer Tooling & Recommended Setup

- **IDE**: [Visual Studio Code](https://code.visualstudio.com/)
- **Recommended Extensions**:
  - [Vue - Official (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) — Disable legacy Vetur.
  - [Tailwind CSS IntelliSense](https://marketplace.visualstudio.com/items?itemName=bradlc.vscode-tailwindcss) — Class auto-completion for Tailwind CSS v4.
  - [Prettier - Code Formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) — Automated code formatting.
- **TypeScript Workspace Setup**:
  This project utilizes `typescript-native-bridge`. When prompted in VS Code, select **Use Workspace Version** to ensure synchronization between the IDE language service and CLI build commands.
- **Browser DevTools**:
  Install [Vue.js DevTools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd) and enable **Custom Object Formatter** in Chrome DevTools Settings.

---

## 🤝 Contributing & Code Standards

1. Ensure all TypeScript types are explicitly declared; avoid `any` wherever possible.
2. Run `pnpm type-check` and `pnpm lint` before submitting pull requests.
3. Add unit test coverage under `src/__tests__/` for new business logic and store modules.
4. Adhere to Tailwind utility conventions and keep layout components modular.

---

<p align="center">
  <sub>Built with ❤️ for Bronx Operations. Confidential and proprietary.</sub>
</p>
# small-k8s
# small-k8s
