<script setup lang="ts">
import {
  BellRing,
  ChevronRight,
  KeyRound,
  Layers,
  LayoutDashboard,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  Radio,
  ScrollText,
  Send,
  Settings,
  ShieldCheck,
  Smartphone,
  UserCheck,
  Users,
  Zap,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

defineProps<{
  isMobileOpen?: boolean
}>()

const emit = defineEmits<{
  (e: 'closeMobile'): void
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isCollapsed = ref<boolean>(false)

onMounted(() => {
  const saved = localStorage.getItem('bronx_sidebar_collapsed')
  if (saved !== null) {
    isCollapsed.value = saved === 'true'
  }
})

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('bronx_sidebar_collapsed', String(isCollapsed.value))
}

const currentPath = computed(() => route.path)

interface NavItem {
  id: string
  name: string
  description: string
  path: string
  icon: any
  badge?: string | number | null
  permission?: string
}

interface NavSection {
  title: string
  items: NavItem[]
}

const navSections = computed<NavSection[]>(() => {
  const appId = authStore.currentAppId

  return [
    {
      title: 'Overview',
      items: [
        {
          id: 'dashboard',
          name: 'Dashboard',
          description: 'Metrics & Performance',
          path: '/dashboard',
          icon: LayoutDashboard,
        },
        {
          id: 'apps',
          name: 'Applications',
          description: 'Multi-Tenant Apps',
          path: '/apps',
          icon: Layers,
          permission: 'app:read',
        },
      ],
    },
    {
      title: 'Messaging',
      items: [
        {
          id: 'notifications',
          name: 'Notifications',
          description: 'History & Dispatches',
          path: appId ? `/apps/${appId}/notifications` : '/notifications',
          icon: BellRing,
          permission: 'notification:read',
        },
        {
          id: 'compose',
          name: 'Compose Push',
          description: 'Dispatch Notification',
          path: appId ? `/apps/${appId}/notifications/new` : '/notifications/new',
          icon: Send,
          permission: 'notification:create',
        },
        {
          id: 'logs',
          name: 'Delivery Logs',
          description: 'Dispatch Audit Trail',
          path: appId ? `/apps/${appId}/logs` : '/logs',
          icon: ScrollText,
          permission: 'log:read',
        },
      ],
    },
    {
      title: 'Audience',
      items: [
        {
          id: 'devices',
          name: 'Devices',
          description: 'Registered Push Tokens',
          path: appId ? `/apps/${appId}/devices` : '/devices',
          icon: Smartphone,
          permission: 'device:read',
        },
        {
          id: 'segments',
          name: 'Segments',
          description: 'Target Audiences',
          path: appId ? `/apps/${appId}/segments` : '/segments',
          icon: Users,
          permission: 'segment:read',
        },
      ],
    },
    {
      title: 'Developer Tools',
      items: [
        {
          id: 'api-keys',
          name: 'API Keys',
          description: 'App & Client Secrets',
          path: appId ? `/apps/${appId}/api-keys` : '/api-keys',
          icon: KeyRound,
          permission: 'apikey:read',
        },
        {
          id: 'realtime',
          name: 'Realtime Gateway',
          description: 'Live STOMP Monitor',
          path: appId ? `/apps/${appId}/realtime` : '/realtime',
          icon: Zap,
          permission: 'notification:read',
        },
        {
          id: 'web-push-test',
          name: 'Web Push Bench',
          description: 'VAPID Push Tester',
          path: appId ? `/apps/${appId}/web-push-test` : '/web-push-test',
          icon: Radio,
          permission: 'notification:create',
        },
      ],
    },
    {
      title: 'Administration',
      items: [
        {
          id: 'members',
          name: 'Team Members',
          description: 'Access & Members',
          path: '/members',
          icon: UserCheck,
          permission: 'member:read',
        },
        {
          id: 'roles',
          name: 'Roles & RBAC',
          description: 'Permission Matrix',
          path: '/roles',
          icon: ShieldCheck,
          permission: 'role:read',
        },
        {
          id: 'settings',
          name: 'App Settings',
          description: 'FCM / APNs Credentials',
          path: appId ? `/apps/${appId}/settings` : '/app-settings',
          icon: Settings,
          permission: 'credential:read',
        },
      ],
    },
  ]
})

function isItemActive(itemOrPath: NavItem | string): boolean {
  const p = currentPath.value
  const id = typeof itemOrPath === 'string' ? '' : itemOrPath.id
  const path = typeof itemOrPath === 'string' ? itemOrPath : itemOrPath.path

  if (id) {
    switch (id) {
      case 'dashboard':
        return p === '/dashboard' || p === '/'
      case 'apps':
        return p === '/apps' || p === '/apps/'
      case 'compose':
        return p.endsWith('/notifications/new') || p === '/notifications/new'
      case 'notifications':
        return (p.includes('/notifications') || p === '/notifications') && !p.includes('/notifications/new')
      case 'logs':
        return p.includes('/logs')
      case 'devices':
        return p.includes('/devices')
      case 'segments':
        return p.includes('/segments')
      case 'api-keys':
        return p.includes('/api-keys')
      case 'realtime':
        return p.includes('/realtime')
      case 'web-push-test':
        return p.includes('/web-push-test')
      case 'members':
        return p.startsWith('/members')
      case 'roles':
        return p.startsWith('/roles')
      case 'settings':
        return p.includes('/settings') || p.startsWith('/app-settings')
      default:
        return p === path
    }
  }

  // Fallback if called directly with a path string
  if (path === '/dashboard') return p === '/dashboard' || p === '/'
  if (path === '/apps') return p === '/apps' || p === '/apps/'
  if (path.endsWith('/notifications/new')) return p.endsWith('/notifications/new')
  if (path.includes('/notifications')) return p.includes('/notifications') && !p.includes('/notifications/new')
  return p === path
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <aside
    class="h-screen bg-white/95 dark:bg-slate-950/95 border-r border-slate-200/80 dark:border-slate-800/80 flex flex-col justify-between select-none z-30 transition-[width,transform] duration-300"
    :class="[
      isMobileOpen ? 'translate-x-0 w-64' : '-translate-x-full md:translate-x-0',
      isCollapsed ? 'md:w-20' : 'md:w-64',
      'fixed md:static inset-y-0 left-0',
    ]"
  >
    <!-- Top Brand Area -->
    <div :class="isCollapsed ? 'p-3 flex flex-col items-center gap-2' : 'p-4'">
      <!-- Collapsed Header -->
      <template v-if="isCollapsed">
        <div
          class="w-10 h-10 rounded-2xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-sky-400 flex items-center justify-center shadow-md shadow-indigo-600/25 ring-1 ring-white/20 shrink-0"
          title="Push Notification Admin"
        >
          <BellRing class="w-5 h-5 text-white animate-pulse-subtle" />
        </div>

        <button
          type="button"
          @click="toggleCollapse"
          title="Expand sidebar"
          class="hidden md:flex p-1.5 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
        >
          <PanelLeftOpen class="w-4 h-4" />
        </button>
      </template>

      <!-- Expanded Header -->
      <template v-else>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3 min-w-0">
            <div
              class="w-10 h-10 rounded-2xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-sky-400 flex items-center justify-center shadow-md shadow-indigo-600/25 ring-1 ring-white/20 shrink-0"
            >
              <BellRing class="w-5 h-5 text-white" />
            </div>
            <div class="truncate">
              <div class="flex items-center gap-1.5">
                <span
                  class="text-sm font-bold tracking-tight text-slate-900 dark:text-white truncate"
                >
                  Push Platform
                </span>
                <span
                  class="px-1.5 py-0.2 rounded text-[10px] font-bold bg-indigo-50 dark:bg-indigo-500/20 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30"
                >
                  v2.0
                </span>
              </div>
              <p class="text-[11px] text-slate-500 dark:text-slate-400 font-medium truncate">
                Multi-Tenant Gateway
              </p>
            </div>
          </div>

          <!-- Desktop Collapse Toggle Button -->
          <button
            type="button"
            @click="toggleCollapse"
            title="Collapse sidebar"
            class="hidden md:flex p-1.5 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer shrink-0"
          >
            <PanelLeftClose class="w-4 h-4" />
          </button>

          <!-- Mobile Close Button -->
          <button
            v-if="isMobileOpen"
            @click="emit('closeMobile')"
            class="md:hidden text-slate-400 hover:text-slate-700 dark:hover:text-white p-1 rounded-lg cursor-pointer"
          >
            ✕
          </button>
        </div>
      </template>
    </div>

    <!-- Navigation Menu with Groups -->
    <div class="flex-1 py-3 space-y-4 overflow-y-auto" :class="isCollapsed ? 'px-2.5' : 'px-3'">
      <div v-for="(section, sIdx) in navSections" :key="sIdx" class="space-y-1.5">
        <!-- Section Label -->
        <div v-if="!isCollapsed" class="flex items-center justify-between px-2 pt-1 pb-0.5">
          <span
            class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500"
          >
            {{ section.title }}
          </span>
          <span class="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-700"></span>
        </div>
        <div v-else-if="sIdx > 0" class="flex justify-center py-1">
          <div class="w-6 h-px bg-slate-200 dark:bg-slate-800"></div>
        </div>

        <!-- Collapsed Nav Items (Icon Only) -->
        <template v-if="isCollapsed">
          <router-link
            v-for="item in section.items"
            :key="item.name"
            :to="item.path"
            @click="emit('closeMobile')"
            :title="`${item.name} - ${item.description}`"
            class="relative group flex items-center justify-center p-2 rounded-2xl transition-all duration-200 cursor-pointer overflow-hidden mb-1"
            :class="[
              isItemActive(item)
                ? 'bg-gradient-to-r from-indigo-50/90 via-white to-white dark:from-indigo-950/50 dark:via-slate-900 dark:to-slate-900 border border-indigo-400/80 dark:border-indigo-500/60 shadow-md shadow-indigo-500/10 dark:shadow-[0_4px_20px_-4px_rgba(99,102,241,0.25)] ring-1 ring-indigo-500/15'
                : 'bg-white/60 dark:bg-slate-900/40 hover:bg-white dark:hover:bg-slate-900/80 border border-slate-200/70 dark:border-slate-800/70 hover:border-indigo-200/80 dark:hover:border-indigo-500/30 shadow-xs hover:shadow-sm hover:-translate-y-0.5',
            ]"
          >
            <!-- Active Left Indicator Glow Bar -->
            <span
              v-if="isItemActive(item)"
              class="absolute left-0 top-2.5 bottom-2.5 w-1 rounded-r-full bg-gradient-to-b from-indigo-600 to-sky-500 shadow-sm shadow-indigo-500/50"
            ></span>

            <div
              class="w-9 h-9 rounded-xl flex items-center justify-center shrink-0 transition-all duration-200"
              :class="[
                isItemActive(item)
                  ? 'bg-gradient-to-tr from-indigo-600 via-indigo-500 to-sky-500 text-white shadow-md shadow-indigo-600/30 ring-2 ring-indigo-500/20'
                  : 'bg-slate-100/90 dark:bg-slate-800/80 text-slate-500 dark:text-slate-400 border border-slate-200/60 dark:border-slate-700/60 group-hover:bg-indigo-50 dark:group-hover:bg-indigo-950/60 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 group-hover:border-indigo-200 dark:group-hover:border-indigo-500/30 group-hover:scale-105',
              ]"
            >
              <component :is="item.icon" class="w-4 h-4" />
            </div>
          </router-link>
        </template>

        <!-- Expanded Nav Items (Icon + Text + Badge + Chevron) -->
        <template v-else>
          <router-link
            v-for="item in section.items"
            :key="item.name"
            :to="item.path"
            @click="emit('closeMobile')"
            class="relative group flex items-center justify-between p-2 rounded-2xl transition-all duration-200 cursor-pointer overflow-hidden mb-1"
            :class="[
              isItemActive(item)
                ? 'bg-gradient-to-r from-indigo-50/90 via-white to-white dark:from-indigo-950/50 dark:via-slate-900 dark:to-slate-900 border border-indigo-400/80 dark:border-indigo-500/60 shadow-md shadow-indigo-500/10 dark:shadow-[0_4px_20px_-4px_rgba(99,102,241,0.25)] ring-1 ring-indigo-500/15'
                : 'bg-white/60 dark:bg-slate-900/40 hover:bg-white dark:hover:bg-slate-900/80 border border-slate-200/70 dark:border-slate-800/70 hover:border-indigo-200/80 dark:hover:border-indigo-500/30 shadow-xs hover:shadow-sm hover:-translate-y-0.5',
            ]"
          >
            <!-- Active Left Indicator Glow Bar -->
            <span
              v-if="isItemActive(item)"
              class="absolute left-0 top-2.5 bottom-2.5 w-1 rounded-r-full bg-gradient-to-b from-indigo-600 to-sky-500 shadow-sm shadow-indigo-500/50"
            ></span>

            <div class="flex items-center gap-2.5 min-w-0 pl-1">
              <!-- Icon Box Card -->
              <div
                class="w-8 h-8 rounded-xl flex items-center justify-center shrink-0 transition-all duration-200"
                :class="[
                  isItemActive(item)
                    ? 'bg-gradient-to-tr from-indigo-600 via-indigo-500 to-sky-500 text-white shadow-md shadow-indigo-600/30 ring-2 ring-indigo-500/20'
                    : 'bg-slate-100/90 dark:bg-slate-800/80 text-slate-500 dark:text-slate-400 border border-slate-200/60 dark:border-slate-700/60 group-hover:bg-indigo-50 dark:group-hover:bg-indigo-950/60 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 group-hover:border-indigo-200 dark:group-hover:border-indigo-500/30 group-hover:scale-105',
                ]"
              >
                <component :is="item.icon" class="w-3.5 h-3.5" />
              </div>

              <!-- Label and Subtitle -->
              <div class="truncate text-left">
                <p
                  class="text-xs tracking-tight transition-colors truncate"
                  :class="[
                    isItemActive(item)
                      ? 'font-bold text-slate-900 dark:text-white'
                      : 'font-semibold text-slate-700 dark:text-slate-300 group-hover:text-slate-900 dark:group-hover:text-white',
                  ]"
                >
                  {{ item.name }}
                </p>
                <p
                  class="text-[10px] tracking-normal transition-colors truncate"
                  :class="[
                    isItemActive(item)
                      ? 'text-indigo-600 dark:text-indigo-400 font-medium'
                      : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-500 dark:group-hover:text-slate-400',
                  ]"
                >
                  {{ item.description }}
                </p>
              </div>
            </div>

            <!-- Right Side Badge / Arrow -->
            <div class="flex items-center gap-1 shrink-0 pr-1">
              <span
                v-if="item.badge !== undefined && item.badge !== null"
                class="text-[10px] px-1.5 py-0.2 rounded-full font-bold"
                :class="[
                  isItemActive(item)
                    ? 'bg-indigo-600 text-white shadow-xs'
                    : 'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400',
                ]"
              >
                {{ item.badge }}
              </span>

              <ChevronRight
                class="w-3 h-3 transition-all duration-200"
                :class="[
                  isItemActive(item)
                    ? 'text-indigo-600 dark:text-indigo-400 translate-x-0.5'
                    : 'text-slate-300 dark:text-slate-600 opacity-0 group-hover:opacity-100 group-hover:text-slate-500 dark:group-hover:text-slate-400 group-hover:translate-x-0.5',
                ]"
              />
            </div>
          </router-link>
        </template>
      </div>
    </div>

    <!-- Sidebar Bottom: Organization / App Mini Status & Sign Out -->
    <div
      class="border-t border-slate-200/80 dark:border-slate-800/80 p-3 bg-slate-50/50 dark:bg-slate-950/40"
    >
      <div v-if="!isCollapsed" class="flex items-center justify-between mb-2 px-1">
        <div class="truncate text-left min-w-0">
          <p class="text-[11px] font-semibold text-slate-800 dark:text-slate-200 truncate">
            {{ authStore.user?.fullName || authStore.user?.email || 'Push Admin' }}
          </p>
          <p class="text-[10px] text-slate-400 truncate">
            {{ authStore.roleLabel }}
          </p>
        </div>
        <button
          type="button"
          @click="handleLogout"
          title="Sign Out"
          class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition-colors cursor-pointer"
        >
          <LogOut class="w-4 h-4" />
        </button>
      </div>
      <div v-else class="flex justify-center">
        <button
          type="button"
          @click="handleLogout"
          title="Sign Out"
          class="p-2 rounded-xl text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition-colors cursor-pointer"
        >
          <LogOut class="w-4 h-4" />
        </button>
      </div>
    </div>
  </aside>
</template>
