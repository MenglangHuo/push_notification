<script setup lang="ts">
import {
  BellRing,
  ChevronDown,
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
  Wrench,
  Zap,
} from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
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

// Track open state for collapsible parent groups
const openGroups = ref<Record<string, boolean>>({
  campaigns: true,
  audience: true,
  monitoring: true,
  devTools: false,
  admin: true,
})

onMounted(() => {
  const saved = localStorage.getItem('bronx_sidebar_collapsed')
  if (saved !== null) {
    isCollapsed.value = saved === 'true'
  }
  const savedGroups = localStorage.getItem('bronx_sidebar_groups')
  if (savedGroups) {
    try {
      openGroups.value = { ...openGroups.value, ...JSON.parse(savedGroups) }
    } catch {
      // ignore
    }
  }
  autoExpandActiveGroup()
})

function toggleGroup(groupId: string) {
  openGroups.value[groupId] = !openGroups.value[groupId]
  localStorage.setItem('bronx_sidebar_groups', JSON.stringify(openGroups.value))
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('bronx_sidebar_collapsed', String(isCollapsed.value))
}

const currentPath = computed(() => route.path)

interface SubItem {
  id: string
  name: string
  path: string
  icon?: any
  permission?: string
  badge?: string | number | null
}

interface NavItem {
  id: string
  name: string
  description: string
  path?: string
  icon: any
  badge?: string | number | null
  permission?: string
  children?: SubItem[]
}

interface NavSection {
  title: string
  items: NavItem[]
}

const navSections = computed<NavSection[]>(() => {
  const appId = (route.params.appId as string) || authStore.currentAppId

  const sections: NavSection[] = [
    {
      title: 'Platform',
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
  ]

  if (appId) {
    sections.push({
      title: 'App Workspace',
      items: [
        {
          id: 'campaigns',
          name: 'Campaigns',
          description: 'Compose & History',
          icon: BellRing,
          children: [
            {
              id: 'compose',
              name: 'Compose Push',
              path: `/apps/${appId}/notifications/new`,
              icon: Send,
              permission: 'notification:create',
            },
            {
              id: 'notifications',
              name: 'Notifications',
              path: `/apps/${appId}/notifications`,
              icon: BellRing,
              permission: 'notification:read',
            },
          ],
        },
        {
          id: 'audience',
          name: 'Audience',
          description: 'Segments & Devices',
          icon: Users,
          children: [
            {
              id: 'segments',
              name: 'Segments',
              path: `/apps/${appId}/segments`,
              icon: Users,
              permission: 'segment:read',
            },
            {
              id: 'devices',
              name: 'Devices',
              path: `/apps/${appId}/devices`,
              icon: Smartphone,
              permission: 'device:read',
            },
          ],
        },
        {
          id: 'monitoring',
          name: 'Monitoring',
          description: 'Delivery Audit Logs',
          icon: ScrollText,
          path: `/apps/${appId}/logs`,
          permission: 'log:read',
        },
        {
          id: 'devTools',
          name: 'Developer Tools',
          description: 'Keys, Setup & Testing',
          icon: Wrench,
          children: [
            {
              id: 'api-keys',
              name: 'API Keys',
              path: `/apps/${appId}/api-keys`,
              icon: KeyRound,
              permission: 'apikey:read',
            },
            {
              id: 'settings',
              name: 'Credentials & Setup',
              path: `/apps/${appId}/settings`,
              icon: Settings,
              permission: 'credential:read',
            },
            {
              id: 'web-push-test',
              name: 'Push Testbench & Sim',
              path: `/apps/${appId}/web-push-test`,
              icon: Radio,
              permission: 'notification:create',
            },
            {
              id: 'realtime',
              name: 'Realtime Gateway',
              path: `/apps/${appId}/realtime`,
              icon: Zap,
              permission: 'notification:read',
            },
          ],
        },
      ],
    })
  }

  sections.push({
    title: 'Administration',
    items: [
      {
        id: 'admin',
        name: 'Access Control',
        description: 'Members & Roles',
        icon: ShieldCheck,
        children: [
          {
            id: 'members',
            name: 'Team Members',
            path: '/members',
            icon: UserCheck,
            permission: 'member:read',
          },
          {
            id: 'roles',
            name: 'Roles & RBAC',
            path: '/roles',
            icon: ShieldCheck,
            permission: 'role:read',
          },
        ],
      },
    ],
  })

  return sections
})

function isItemActive(itemOrPath: NavItem | SubItem | string): boolean {
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
      case 'monitoring':
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
        break
    }
  }

  if (path) {
    if (path === '/dashboard') return p === '/dashboard' || p === '/'
    if (path === '/apps') return p === '/apps' || p === '/apps/'
    if (path.endsWith('/notifications/new')) return p.endsWith('/notifications/new')
    if (path.includes('/notifications')) return p.includes('/notifications') && !p.includes('/notifications/new')
    return p === path
  }

  return false
}

function isGroupActive(item: NavItem): boolean {
  if (item.path && isItemActive(item)) return true
  if (item.children) {
    return item.children.some((c) => isItemActive(c))
  }
  return false
}

function autoExpandActiveGroup() {
  for (const section of navSections.value) {
    for (const item of section.items) {
      if (item.children && isGroupActive(item)) {
        openGroups.value[item.id] = true
      }
    }
  }
}

watch(currentPath, () => {
  autoExpandActiveGroup()
})

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <aside
    class="h-screen bg-white/95 dark:bg-slate-950/95 border-r border-slate-200/80 dark:border-slate-800/80 flex flex-col justify-between select-none z-30 transition-[width,transform] duration-300"
    :class="[
      isMobileOpen ? 'translate-x-0 w-72' : '-translate-x-full md:translate-x-0',
      isCollapsed ? 'md:w-20' : 'md:w-72',
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
                  v1.0
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

        <!-- Items in Section -->
        <div v-for="item in section.items" :key="item.id" class="space-y-1">
          <!-- COLLAPSED MODE: Icon-only buttons -->
          <template v-if="isCollapsed">
            <!-- Direct link -->
            <router-link
              v-if="item.path"
              :to="item.path"
              @click="emit('closeMobile')"
              :title="`${item.name} - ${item.description}`"
              class="relative group flex items-center justify-center p-2 rounded-2xl transition-all duration-200 cursor-pointer overflow-hidden mb-1"
              :class="[
                isItemActive(item)
                  ? 'bg-gradient-to-r from-indigo-50/90 via-white to-white dark:from-indigo-950/50 dark:via-slate-900 dark:to-slate-900 border border-indigo-400/80 dark:border-indigo-500/60 shadow-md shadow-indigo-500/10 ring-1 ring-indigo-500/15'
                  : 'bg-white/60 dark:bg-slate-900/40 hover:bg-white dark:hover:bg-slate-900/80 border border-slate-200/70 dark:border-slate-800/70 shadow-xs hover:shadow-sm hover:-translate-y-0.5',
              ]"
            >
              <div
                class="w-9 h-9 rounded-xl flex items-center justify-center shrink-0 transition-all duration-200"
                :class="[
                  isItemActive(item)
                    ? 'bg-gradient-to-tr from-indigo-600 via-indigo-500 to-sky-500 text-white shadow-md shadow-indigo-600/30'
                    : 'bg-slate-100/90 dark:bg-slate-800/80 text-slate-500 dark:text-slate-400 group-hover:text-indigo-600',
                ]"
              >
                <component :is="item.icon" class="w-4 h-4" />
              </div>
            </router-link>

            <!-- Parent group with children: click toggles or opens first child -->
            <button
              v-else
              type="button"
              @click="toggleGroup(item.id)"
              :title="`${item.name} - ${item.description}`"
              class="relative group flex items-center justify-center p-2 rounded-2xl transition-all duration-200 cursor-pointer overflow-hidden mb-1 w-full"
              :class="[
                isGroupActive(item)
                  ? 'bg-gradient-to-r from-indigo-50/90 via-white to-white dark:from-indigo-950/50 dark:via-slate-900 dark:to-slate-900 border border-indigo-400/80 dark:border-indigo-500/60 shadow-md shadow-indigo-500/10 ring-1 ring-indigo-500/15'
                  : 'bg-white/60 dark:bg-slate-900/40 hover:bg-white dark:hover:bg-slate-900/80 border border-slate-200/70 dark:border-slate-800/70 shadow-xs hover:shadow-sm hover:-translate-y-0.5',
              ]"
            >
              <div
                class="w-9 h-9 rounded-xl flex items-center justify-center shrink-0 transition-all duration-200"
                :class="[
                  isGroupActive(item)
                    ? 'bg-gradient-to-tr from-indigo-600 via-indigo-500 to-sky-500 text-white shadow-md shadow-indigo-600/30'
                    : 'bg-slate-100/90 dark:bg-slate-800/80 text-slate-500 dark:text-slate-400 group-hover:text-indigo-600',
                ]"
              >
                <component :is="item.icon" class="w-4 h-4" />
              </div>
            </button>
          </template>

          <!-- EXPANDED MODE: Full Item (Direct Link or Collapsible Parent) -->
          <template v-else>
            <!-- Case 1: Direct Link (No children) -->
            <router-link
              v-if="item.path"
              :to="item.path"
              @click="emit('closeMobile')"
              class="relative group flex items-center justify-between p-2 rounded-2xl transition-all duration-200 cursor-pointer overflow-hidden"
              :class="[
                isItemActive(item)
                  ? 'bg-gradient-to-r from-indigo-50/90 via-white to-white dark:from-indigo-950/50 dark:via-slate-900 dark:to-slate-900 border border-indigo-400/80 dark:border-indigo-500/60 shadow-md shadow-indigo-500/10 ring-1 ring-indigo-500/15'
                  : 'bg-white/60 dark:bg-slate-900/40 hover:bg-white dark:hover:bg-slate-900/80 border border-slate-200/70 dark:border-slate-800/70 shadow-xs hover:shadow-sm hover:-translate-y-0.5',
              ]"
            >
              <span
                v-if="isItemActive(item)"
                class="absolute left-0 top-2.5 bottom-2.5 w-1 rounded-r-full bg-gradient-to-b from-indigo-600 to-sky-500"
              ></span>

              <div class="flex items-center gap-2.5 min-w-0 pl-1">
                <div
                  class="w-8 h-8 rounded-xl flex items-center justify-center shrink-0 transition-all duration-200"
                  :class="[
                    isItemActive(item)
                      ? 'bg-gradient-to-tr from-indigo-600 via-indigo-500 to-sky-500 text-white shadow-md shadow-indigo-600/30'
                      : 'bg-slate-100/90 dark:bg-slate-800/80 text-slate-500 dark:text-slate-400 border border-slate-200/60 dark:border-slate-700/60 group-hover:text-indigo-600',
                  ]"
                >
                  <component :is="item.icon" class="w-3.5 h-3.5" />
                </div>

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
                  <p class="text-[10px] text-slate-400 dark:text-slate-500 truncate">
                    {{ item.description }}
                  </p>
                </div>
              </div>

              <ChevronRight class="w-3 h-3 text-slate-300 dark:text-slate-600 group-hover:text-slate-500 transition-colors" />
            </router-link>

            <!-- Case 2: Parent Menu with Collapsible Submenu / Children -->
            <div v-else>
              <!-- Parent toggle button -->
              <button
                type="button"
                @click="toggleGroup(item.id)"
                class="w-full relative group flex items-center justify-between p-2.5 rounded-2xl transition-all duration-200 cursor-pointer overflow-hidden text-left"
                :class="[
                  isGroupActive(item)
                    ? 'bg-gradient-to-r from-indigo-50/80 via-indigo-50/40 to-white dark:from-indigo-950/60 dark:via-slate-900/80 dark:to-slate-900 border border-indigo-200/80 dark:border-indigo-500/30 shadow-sm shadow-indigo-500/5'
                    : 'bg-white/60 dark:bg-slate-900/40 hover:bg-white dark:hover:bg-slate-900/80 border border-slate-200/70 dark:border-slate-800/70 shadow-xs hover:shadow-sm hover:-translate-y-0.5',
                ]"
              >
                <!-- Active left accent -->
                <span
                  v-if="isGroupActive(item)"
                  class="absolute left-0 top-3 bottom-3 w-[3px] rounded-r-full bg-gradient-to-b from-indigo-600 to-indigo-400 dark:from-indigo-400 dark:to-indigo-600"
                ></span>

                <div class="flex items-center gap-2.5 min-w-0 pl-1">
                  <div
                    class="w-8 h-8 rounded-xl flex items-center justify-center shrink-0 transition-all duration-200"
                    :class="[
                      isGroupActive(item)
                        ? 'bg-gradient-to-tr from-indigo-600 to-indigo-500 text-white shadow-sm shadow-indigo-600/20'
                        : 'bg-slate-100/90 dark:bg-slate-800/80 text-slate-500 dark:text-slate-400 border border-slate-200/60 dark:border-slate-700/60 group-hover:text-indigo-600',
                    ]"
                  >
                    <component :is="item.icon" class="w-3.5 h-3.5" />
                  </div>

                  <div class="truncate text-left">
                    <p
                      class="text-xs tracking-tight transition-colors truncate font-semibold"
                      :class="isGroupActive(item) ? 'text-indigo-900 dark:text-indigo-200 font-bold' : 'text-slate-700 dark:text-slate-300'"
                    >
                      {{ item.name }}
                    </p>
                    <p class="text-[10px] text-slate-400 dark:text-slate-500 truncate">
                      {{ item.description }}
                    </p>
                  </div>
                </div>

                <!-- Collapse / Expand chevron -->
                <div class="pr-0.5">
                  <div
                    class="w-5 h-5 rounded-md flex items-center justify-center transition-all duration-200"
                    :class="[
                      openGroups[item.id]
                        ? 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400'
                        : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-600',
                    ]"
                  >
                    <ChevronDown
                      class="w-3 h-3 transition-transform duration-300 ease-out"
                      :class="openGroups[item.id] ? 'rotate-180' : ''"
                    />
                  </div>
                </div>
              </button>

              <!-- Collapsible Children Submenu (Clean & Professional Tree Link UI) -->
              <div
                v-show="openGroups[item.id]"
                class="relative mt-1 pt-1 pb-1 space-y-1.5 transition-all duration-200"
              >
                <!-- Child Menu Items -->
                <div
                  v-for="(sub, subIdx) in item.children"
                  :key="sub.id"
                  class="relative flex items-center"
                >
                  <!-- Continuous vertical trunk segment (rendered through each item EXCEPT below the last child) -->
                  <div
                    v-if="subIdx < (item.children?.length ?? 0) - 1"
                    class="absolute w-[2px] pointer-events-none transition-colors duration-200 z-10"
                    :class="[
                      isGroupActive(item)
                        ? 'bg-indigo-300 dark:bg-indigo-600/60'
                        : 'bg-slate-300 dark:bg-slate-700',
                    ]"
                    style="left: 29px; top: -8px; bottom: -8px;"
                  ></div>

                  <!-- Connector from parent button down into the first child -->
                  <div
                    v-if="subIdx === 0"
                    class="absolute w-[2px] pointer-events-none transition-colors duration-200 z-10"
                    :class="[
                      isGroupActive(item)
                        ? 'bg-indigo-300 dark:bg-indigo-600/60'
                        : 'bg-slate-300 dark:bg-slate-700',
                    ]"
                    style="left: 29px; top: -10px; height: 10px;"
                  ></div>

                  <!-- Curved Branch Link (Curving 90° from trunk into child card's left edge, z-20 so card background/shadow never covers it) -->
                  <div
                    class="absolute pointer-events-none transition-colors duration-200 z-20"
                    :class="[
                      isItemActive(sub)
                        ? 'border-indigo-600 dark:border-indigo-400'
                        : isGroupActive(item)
                          ? 'border-indigo-300 dark:border-indigo-600/70'
                          : 'border-slate-300 dark:border-slate-700',
                    ]"
                    style="
                      left: 29px;
                      top: -8px;
                      width: 22px;
                      height: calc(50% + 8px);
                      border-left-width: 2px;
                      border-bottom-width: 2px;
                      border-bottom-left-radius: 10px;
                    "
                  ></div>

                  <!-- Submenu Item Card (Both selected & unselected have border and background) -->
                  <router-link
                    :to="sub.path"
                    @click="emit('closeMobile')"
                    class="w-full relative flex items-center justify-between ml-[50px] px-3.5 py-2.5 rounded-xl text-xs font-medium transition-all duration-200 group overflow-hidden"
                    :class="[
                      isItemActive(sub)
                        ? 'bg-gradient-to-r from-indigo-600 via-indigo-600 to-indigo-500 text-white font-semibold shadow-md shadow-indigo-600/20 ring-1 ring-white/20 border border-indigo-600'
                        : 'bg-white/95 dark:bg-slate-900/90 hover:bg-white dark:hover:bg-slate-800 border border-slate-200/90 dark:border-slate-800/90 hover:border-indigo-300 dark:hover:border-indigo-600/80 text-slate-700 dark:text-slate-300 hover:text-indigo-600 dark:hover:text-indigo-400 shadow-2xs hover:shadow-xs hover:-translate-y-0.5',
                    ]"
                  >
                    <div class="flex items-center gap-2.5 min-w-0">
                      <!-- Active glowing dot or Inactive icon -->
                      <span
                        v-if="isItemActive(sub)"
                        class="w-2 h-2 rounded-full bg-white ring-2 ring-indigo-300/50 shrink-0 shadow-xs"
                      ></span>
                      <component
                        v-else-if="sub.icon"
                        :is="sub.icon"
                        class="w-3.5 h-3.5 shrink-0 text-slate-400 dark:text-slate-500 group-hover:text-indigo-500 dark:group-hover:text-indigo-400 transition-colors"
                      />
                      <span
                        v-else
                        class="w-1.5 h-1.5 rounded-full bg-slate-300 dark:bg-slate-600 group-hover:bg-indigo-400 shrink-0 transition-colors"
                      ></span>

                      <span class="truncate tracking-tight leading-snug">{{ sub.name }}</span>
                    </div>

                    <!-- Subtle right chevron on hover when unselected -->
                    <ChevronRight
                      v-if="!isItemActive(sub)"
                      class="w-3 h-3 text-slate-300 dark:text-slate-600 opacity-0 group-hover:opacity-100 transition-all duration-200 -translate-x-1 group-hover:translate-x-0 shrink-0"
                    />
                  </router-link>
                </div>
              </div>
            </div>
          </template>
        </div>
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
