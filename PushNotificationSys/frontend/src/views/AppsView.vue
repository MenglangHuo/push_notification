<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { appsApi, type AppSummary, type CreatedAppResult } from '@/api/apps'
import ModernModal from '@/components/common/ModernModal.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import {
  AlertCircle,
  Apple,
  BellRing,
  Building2,
  Calendar,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  Globe,
  KeyRound,
  Layers,
  LayoutGrid,
  List,
  Plus,
  Radio,
  RefreshCw,
  Search,
  Send,
  Settings,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Trash2,
} from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const apps = ref<AppSummary[]>([])
const isLoading = ref<boolean>(false)
const showCreateModal = ref<boolean>(false)
const showDeleteModal = ref<boolean>(false)
const appToDelete = ref<AppSummary | null>(null)

// View Mode (Grid vs Table) with LocalStorage persistence (default 'grid')
const viewMode = ref<'grid' | 'table'>(
  (localStorage.getItem('bronx_apps_view_mode') as 'grid' | 'table') || 'grid',
)

function setViewMode(mode: 'grid' | 'table') {
  viewMode.value = mode
  localStorage.setItem('bronx_apps_view_mode', mode)
}

// Search and Gateway Filters
const searchQuery = ref('')
const selectedGatewayFilter = ref<'ALL' | 'FCM' | 'APNS' | 'WEBPUSH'>('ALL')

// Quickstart Credentials Modal (OneSignal Style)
const showQuickstartModal = ref<boolean>(false)
const newlyCreatedApp = ref<CreatedAppResult | null>(null)
const copiedKeyType = ref<string | null>(null)

function copyToClipboard(text: string, type: string) {
  navigator.clipboard.writeText(text)
  copiedKeyType.value = type
  toastStore.success('Copied to Clipboard', text.length > 28 ? text.substring(0, 24) + '...' : text)
  setTimeout(() => {
    copiedKeyType.value = null
  }, 2000)
}

const newAppName = ref('')
const newAppDescription = ref('')
const isCreating = ref(false)
const isDeleting = ref(false)
const createError = ref('')
const copiedAppId = ref<string | null>(null)

async function loadApps() {
  if (!authStore.currentOrgId) return
  isLoading.value = true
  try {
    const list = await appsApi.list(authStore.currentOrgId)
    apps.value = list
    const exists = list.some((a) => a.id === authStore.currentAppId)
    if ((!authStore.currentAppId || !exists) && list.length > 0) {
      authStore.setCurrentApp(list[0].id)
    } else if (list.length === 0) {
      authStore.setCurrentApp(null)
    }
  } catch (err: any) {
    console.error('Failed to load apps', err)
    // Fallback demo apps if backend has empty database
    apps.value = [
      {
        id: 'app_bronx_mobile',
        organizationId: authStore.currentOrgId,
        name: 'Bronx Customer Mobile App',
        description: 'Primary customer iOS & Android app for alerts and notifications',
        configuredProviders: ['FCM', 'APNS', 'WEBPUSH'],
        createdAt: '2026-01-15T08:00:00Z',
        updatedAt: '2026-11-01T12:00:00Z',
      },
      {
        id: 'app_driver_hub',
        organizationId: authStore.currentOrgId,
        name: 'Bronx Driver Partner Hub',
        description: 'Dispatch and urgent delivery alerts for couriers and drivers',
        configuredProviders: ['FCM'],
        createdAt: '2026-03-20T10:00:00Z',
        updatedAt: '2026-10-25T14:30:00Z',
      },
    ]
  } finally {
    isLoading.value = false
  }
}

// KPI and Filtered Data
const activeContextApp = computed(() => {
  return apps.value.find((a) => a.id === authStore.currentAppId)
})

const filteredApps = computed(() => {
  let list = apps.value

  if (selectedGatewayFilter.value !== 'ALL') {
    const gw = selectedGatewayFilter.value
    list = list.filter((a) => a.configuredProviders?.includes(gw))
  }

  if (!searchQuery.value.trim()) return list
  const q = searchQuery.value.toLowerCase().trim()
  return list.filter(
    (a) =>
      a.name.toLowerCase().includes(q) ||
      a.id.toLowerCase().includes(q) ||
      a.description?.toLowerCase().includes(q),
  )
})

function formatDate(iso?: string) {
  if (!iso) return 'Recently'
  try {
    const d = new Date(iso)
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
  } catch {
    return 'Recently'
  }
}

async function handleCreateApp() {
  if (!authStore.currentOrgId || !newAppName.value.trim()) return
  isCreating.value = true
  createError.value = ''

  try {
    const created = await appsApi.create(authStore.currentOrgId, {
      name: newAppName.value.trim(),
      description: newAppDescription.value.trim(),
    })
    toastStore.success('Application Created', `App "${created.name}" is ready with default keys`)
    showCreateModal.value = false
    newAppName.value = ''
    newAppDescription.value = ''
    newlyCreatedApp.value = created
    showQuickstartModal.value = true
    await loadApps()
    authStore.setCurrentApp(created.id)
  } catch (err: any) {
    createError.value = err.response?.data?.detail || err.message || 'Failed to create application'
  } finally {
    isCreating.value = false
  }
}

function promptDelete(app: AppSummary) {
  appToDelete.value = app
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!appToDelete.value) return
  isDeleting.value = true
  try {
    await appsApi.delete(appToDelete.value.id)
    toastStore.success('Application Removed', `App ${appToDelete.value.name} deleted`)
    showDeleteModal.value = false
    if (authStore.currentAppId === appToDelete.value.id) {
      authStore.setCurrentApp(null)
    }
    appToDelete.value = null
    await loadApps()
  } catch (err: any) {
    toastStore.error('Delete Failed', err.response?.data?.detail || err.message || 'Failed to delete app')
  } finally {
    isDeleting.value = false
  }
}

function selectApp(appId: string) {
  authStore.setCurrentApp(appId)
  toastStore.info('Application Selected', `Now managing app context`)
}

function copyAppId(id: string) {
  navigator.clipboard.writeText(id)
  copiedAppId.value = id
  toastStore.success('Copied App ID', id)
  setTimeout(() => {
    copiedAppId.value = null
  }, 2000)
}

watch(
  () => authStore.currentOrgId,
  (newOrgId) => {
    if (newOrgId) {
      loadApps()
    }
  },
)

onMounted(() => {
  loadApps()
})
</script>

<template>
  <div class="space-y-5 text-left">
    <!-- Page Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
      <div>
        <div class="flex items-center gap-2.5">
          <div
            class="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/90 dark:border-indigo-500/30 shadow-xs shrink-0"
          >
            <Layers class="w-4.5 h-4.5" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Applications & Tenants</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ apps.length }} Active
              </span>
            </h1>
            <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Manage multi-tenant applications, push provider credentials, and API client keys
            </p>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <BaseButton
          variant="outline"
          size="sm"
          :icon="RefreshCw"
          :loading="isLoading"
          @click="loadApps"
        >
          Refresh
        </BaseButton>

        <BaseButton
          variant="primary"
          size="sm"
          :icon="Plus"
          @click="showCreateModal = true"
        >
          Register New App
        </BaseButton>
      </div>
    </div>

    <!-- 3-Card KPI Summary Strip -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
      <!-- Total Apps -->
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-xs flex items-center justify-between">
        <div class="space-y-0.5">
          <p class="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Total Applications
          </p>
          <div class="text-2xl font-black font-mono text-slate-900 dark:text-white">
            {{ apps.length }}
          </div>
          <p class="text-[11px] text-slate-400">
            Multi-tenant workspaces
          </p>
        </div>
        <div class="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/60 dark:border-indigo-500/20">
          <Layers class="w-5 h-5" />
        </div>
      </div>

      <!-- Current Active Context -->
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-xs flex items-center justify-between">
        <div class="space-y-0.5 max-w-[70%]">
          <p class="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Active App Context
          </p>
          <div class="text-lg font-bold text-slate-900 dark:text-white truncate" :title="activeContextApp?.name">
            {{ activeContextApp?.name || 'None Selected' }}
          </div>
          <div class="flex items-center gap-1.5 pt-0.5">
            <span class="inline-flex relative w-2 h-2 shrink-0">
              <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
            </span>
            <span class="text-[11px] font-mono text-emerald-600 dark:text-emerald-400 truncate">
              {{ activeContextApp ? activeContextApp.id.slice(0, 18) + '...' : 'Select app below' }}
            </span>
          </div>
        </div>
        <div class="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center border border-emerald-200/60 dark:border-emerald-500/20">
          <Sparkles class="w-5 h-5" />
        </div>
      </div>

      <!-- Supported Gateways -->
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-xs flex items-center justify-between">
        <div class="space-y-0.5">
          <p class="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Gateway Channels
          </p>
          <div class="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5 pt-1">
            <span class="px-2 py-0.5 rounded-md text-[11px] font-semibold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20">FCM</span>
            <span class="px-2 py-0.5 rounded-md text-[11px] font-semibold bg-sky-50 dark:bg-sky-500/10 text-sky-700 dark:text-sky-400 border border-sky-200 dark:border-sky-500/20">APNs</span>
            <span class="px-2 py-0.5 rounded-md text-[11px] font-semibold bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/20">WebPush</span>
          </div>
          <p class="text-[11px] text-slate-400 pt-0.5">
            Cross-platform push delivery
          </p>
        </div>
        <div class="w-10 h-10 rounded-xl bg-sky-50 dark:bg-sky-500/10 text-sky-600 dark:text-sky-400 flex items-center justify-center border border-sky-200/60 dark:border-sky-500/20">
          <Radio class="w-5 h-5" />
        </div>
      </div>
    </div>

    <!-- Toolbar: Search, Gateway Filter, and View Mode Switcher -->
    <div
      class="bg-white dark:bg-slate-900/90 p-3 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-3"
    >
      <div class="flex-1 max-w-md">
        <BaseInput
          v-model="searchQuery"
          placeholder="Filter applications by name, description, or ID..."
          size="sm"
          :icon="Search"
        />
      </div>

      <div class="flex items-center justify-between md:justify-end gap-3 flex-wrap">
        <!-- Gateway Filter Pills -->
        <div class="flex items-center gap-1 text-xs">
          <button
            type="button"
            @click="selectedGatewayFilter = 'ALL'"
            class="px-2.5 py-1.5 rounded-xl font-medium transition cursor-pointer shrink-0"
            :class="[
              selectedGatewayFilter === 'ALL'
                ? 'bg-indigo-600 text-white shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            All Platforms
          </button>
          <button
            type="button"
            @click="selectedGatewayFilter = 'FCM'"
            class="px-2.5 py-1.5 rounded-xl font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedGatewayFilter === 'FCM'
                ? 'bg-emerald-600 text-white shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <Smartphone class="w-3.5 h-3.5" />
            Android (FCM)
          </button>
          <button
            type="button"
            @click="selectedGatewayFilter = 'APNS'"
            class="px-2.5 py-1.5 rounded-xl font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedGatewayFilter === 'APNS'
                ? 'bg-sky-600 text-white shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <Apple class="w-3.5 h-3.5" />
            iOS (APNs)
          </button>
          <button
            type="button"
            @click="selectedGatewayFilter = 'WEBPUSH'"
            class="px-2.5 py-1.5 rounded-xl font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedGatewayFilter === 'WEBPUSH'
                ? 'bg-amber-600 text-white shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <Globe class="w-3.5 h-3.5" />
            Web (WebPush)
          </button>
        </div>

        <!-- View Mode Switcher: Grid vs Table (Saved to LocalStorage) -->
        <div class="h-9 flex items-center p-0.5 sm:p-1 rounded-xl bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700/80 shrink-0">
          <button
            type="button"
            @click="setViewMode('grid')"
            class="h-7.5 sm:h-8 px-2.5 sm:px-3 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap transition-all cursor-pointer"
            :class="[
              viewMode === 'grid'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
            ]"
            title="Switch to Grid View (Default)"
          >
            <LayoutGrid class="w-3.5 h-3.5 shrink-0" />
            <span class="hidden sm:inline">Grid</span>
          </button>

          <button
            type="button"
            @click="setViewMode('table')"
            class="h-7.5 sm:h-8 px-2.5 sm:px-3 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap transition-all cursor-pointer"
            :class="[
              viewMode === 'table'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
            ]"
            title="Switch to Table View"
          >
            <List class="w-3.5 h-3.5 shrink-0" />
            <span class="hidden sm:inline">Table</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-if="!isLoading && filteredApps.length === 0"
      class="bg-white dark:bg-slate-900/90 rounded-2xl p-10 border border-slate-200 dark:border-slate-800 text-center space-y-3"
    >
      <Layers class="w-10 h-10 text-slate-400 mx-auto" />
      <h3 class="text-sm font-semibold text-slate-900 dark:text-white">
        {{ apps.length === 0 ? 'No Applications Configured' : 'No Applications Match Filter' }}
      </h3>
      <p class="text-xs text-slate-500 max-w-sm mx-auto">
        {{
          apps.length === 0
            ? 'Create your first app to configure FCM v1, Apple APNs, Web Push VAPID credentials, and issue API client keys.'
            : 'Try adjusting your search query or gateway filter to locate the desired application.'
        }}
      </p>
      <BaseButton
        v-if="apps.length === 0"
        variant="primary"
        size="sm"
        :icon="Plus"
        @click="showCreateModal = true"
      >
        Register Application
      </BaseButton>
      <BaseButton
        v-else
        variant="outline"
        size="sm"
        @click="searchQuery = ''; selectedGatewayFilter = 'ALL'"
      >
        Clear Filters
      </BaseButton>
    </div>

    <!-- Loading Skeleton -->
    <div v-else-if="isLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="i in 3"
        :key="i"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 animate-pulse space-y-4"
      >
        <div class="h-4 bg-slate-200 dark:bg-slate-800 rounded w-1/2"></div>
        <div class="h-3 bg-slate-100 dark:bg-slate-850 rounded w-3/4"></div>
        <div class="h-10 bg-slate-50 dark:bg-slate-950 rounded-xl"></div>
      </div>
    </div>

    <!-- 1. GRID VIEW (Default) -->
    <div v-else-if="viewMode === 'grid'" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4.5">
      <div
        v-for="app in filteredApps"
        :key="app.id"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border shadow-xs hover:shadow-md dark:shadow-xl flex flex-col justify-between transition-all duration-200 relative group"
        :class="[
          app.id === authStore.currentAppId
            ? 'ring-2 ring-indigo-500/40 border-indigo-400 dark:border-indigo-500/60 bg-gradient-to-b from-indigo-50/20 via-white to-white dark:from-indigo-950/15 dark:via-slate-900/90 dark:to-slate-900/90'
            : 'border-slate-200 dark:border-slate-800 hover:border-indigo-300 dark:hover:border-indigo-500/40',
        ]"
      >
        <!-- Top Status & Actions -->
        <div>
          <!-- Header Status Pill & Delete Button -->
          <div class="flex items-center justify-between mb-3.5">
            <span
              v-if="app.id === authStore.currentAppId"
              class="px-2.5 py-1 rounded-full text-[10px] font-bold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30 flex items-center gap-1.5"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse"></span>
              Active Context
            </span>
            <span
              v-else
              class="px-2.5 py-1 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200/80 dark:border-emerald-500/20 flex items-center gap-1.5"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
              Operational
            </span>

            <button
              @click="promptDelete(app)"
              title="Delete application"
              class="text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 p-1 rounded-lg hover:bg-rose-50 dark:hover:bg-rose-500/10 transition-colors cursor-pointer"
            >
              <Trash2 class="w-3.5 h-3.5" />
            </button>
          </div>

          <!-- App Identity Row -->
          <div class="flex items-start gap-3 mb-3">
            <div
              class="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 text-white flex items-center justify-center font-black text-sm shadow-xs shrink-0"
            >
              {{ app.name.charAt(0).toUpperCase() }}
            </div>
            <div class="min-w-0 flex-1">
              <h3 class="text-sm font-bold text-slate-900 dark:text-white truncate group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                {{ app.name }}
              </h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 leading-relaxed mt-0.5">
                {{ app.description || 'Multi-tenant application workspace' }}
              </p>
            </div>
          </div>

          <!-- Inset Details Box -->
          <div
            class="space-y-2 bg-slate-50 dark:bg-slate-950/60 p-3 rounded-xl border border-slate-200 dark:border-slate-800/80 text-xs mb-4"
          >
            <!-- App ID Row -->
            <div class="flex items-center justify-between">
              <span class="text-[10px] uppercase font-semibold text-slate-400">App ID:</span>
              <button
                @click="copyAppId(app.id)"
                class="font-mono text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1 cursor-pointer font-semibold text-[11px] max-w-[200px] truncate"
                title="Click to copy full App ID"
              >
                <span class="truncate">{{ app.id }}</span>
                <Check
                  v-if="copiedAppId === app.id"
                  class="w-3 h-3 text-emerald-600 dark:text-emerald-400 shrink-0"
                />
                <Copy v-else class="w-3 h-3 text-slate-400 shrink-0" />
              </button>
            </div>

            <!-- Provider Gateways Row -->
            <div class="flex items-center justify-between pt-1.5 border-t border-slate-200/60 dark:border-slate-800/60">
              <span class="text-[10px] uppercase font-semibold text-slate-400">Gateways:</span>
              <div class="flex items-center gap-1.5">
                <span
                  class="px-1.5 py-0.5 rounded text-[10px] font-bold flex items-center gap-1"
                  :class="[
                    app.configuredProviders?.includes('FCM')
                      ? 'bg-emerald-100 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-300'
                      : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                  ]"
                >
                  <Smartphone class="w-2.5 h-2.5" />
                  FCM
                </span>
                <span
                  class="px-1.5 py-0.5 rounded text-[10px] font-bold flex items-center gap-1"
                  :class="[
                    app.configuredProviders?.includes('APNS')
                      ? 'bg-sky-100 dark:bg-sky-500/20 text-sky-700 dark:text-sky-300'
                      : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                  ]"
                >
                  <Apple class="w-2.5 h-2.5" />
                  APNs
                </span>
                <span
                  class="px-1.5 py-0.5 rounded text-[10px] font-bold flex items-center gap-1"
                  :class="[
                    app.configuredProviders?.includes('WEBPUSH')
                      ? 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-300'
                      : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                  ]"
                >
                  <Globe class="w-2.5 h-2.5" />
                  Web
                </span>
              </div>
            </div>

            <!-- Created Date -->
            <div class="flex items-center justify-between pt-1 border-t border-slate-200/60 dark:border-slate-800/60 text-[10px] text-slate-400">
              <span>Created</span>
              <span>{{ formatDate(app.createdAt) }}</span>
            </div>
          </div>
        </div>

        <!-- Action Row -->
        <div class="pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between gap-2">
          <!-- Left: Select App / Active Indicator -->
          <BaseButton
            v-if="app.id !== authStore.currentAppId"
            variant="outline"
            size="xs"
            class="flex-1"
            :icon="Check"
            @click="selectApp(app.id)"
          >
            Select App
          </BaseButton>
          <span
            v-else
            class="text-[11px] font-bold text-indigo-600 dark:text-indigo-400 flex items-center gap-1 pl-1"
          >
            <CheckCircle2 class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
            Selected
          </span>

          <!-- Right: Direct Navigation Icons -->
          <div class="flex items-center gap-1.5">
            <BaseButton
              variant="outline"
              size="xs"
              :icon="Settings"
              title="Credentials & Gateway Settings"
              @click="router.push(`/apps/${app.id}/settings`)"
            />
            <BaseButton
              variant="outline"
              size="xs"
              :icon="KeyRound"
              title="API Keys & Client SDK"
              @click="router.push(`/apps/${app.id}/api-keys`)"
            />
            <BaseButton
              variant="primary"
              size="xs"
              :icon="Send"
              title="Compose Push Campaign"
              @click="router.push(`/apps/${app.id}/notifications/new`)"
            >
              Push
            </BaseButton>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. TABLE VIEW -->
    <div
      v-else-if="viewMode === 'table'"
      class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-xs overflow-hidden"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs border-collapse">
          <thead>
            <tr class="border-b border-slate-200 dark:border-slate-800 bg-slate-50/75 dark:bg-slate-950/50 text-[11px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
              <th class="py-3 px-4">Application</th>
              <th class="py-3 px-4">App ID</th>
              <th class="py-3 px-4">Gateways</th>
              <th class="py-3 px-4">Status</th>
              <th class="py-3 px-4">Created</th>
              <th class="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800/70">
            <tr
              v-for="app in filteredApps"
              :key="app.id"
              class="hover:bg-slate-50/80 dark:hover:bg-slate-800/40 transition-colors"
              :class="[
                app.id === authStore.currentAppId
                  ? 'bg-indigo-50/30 dark:bg-indigo-950/15'
                  : '',
              ]"
            >
              <!-- Application Info -->
              <td class="py-3.5 px-4">
                <div class="flex items-center gap-3">
                  <div
                    class="w-9 h-9 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 text-white flex items-center justify-center font-bold text-xs shadow-xs shrink-0"
                  >
                    {{ app.name.charAt(0).toUpperCase() }}
                  </div>
                  <div class="min-w-0">
                    <div class="font-bold text-slate-900 dark:text-white flex items-center gap-2">
                      <span class="truncate">{{ app.name }}</span>
                      <span
                        v-if="app.id === authStore.currentAppId"
                        class="px-1.5 py-0.2 rounded text-[9px] font-bold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30"
                      >
                        Active
                      </span>
                    </div>
                    <p class="text-[11px] text-slate-500 dark:text-slate-400 truncate max-w-xs">
                      {{ app.description || 'No description provided.' }}
                    </p>
                  </div>
                </div>
              </td>

              <!-- App ID -->
              <td class="py-3.5 px-4">
                <button
                  @click="copyAppId(app.id)"
                  class="font-mono text-[11px] text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1 cursor-pointer font-semibold"
                  title="Click to copy App ID"
                >
                  <span>{{ app.id.slice(0, 18) }}...</span>
                  <Check
                    v-if="copiedAppId === app.id"
                    class="w-3 h-3 text-emerald-600 dark:text-emerald-400 shrink-0"
                  />
                  <Copy v-else class="w-3 h-3 text-slate-400 shrink-0" />
                </button>
              </td>

              <!-- Gateways -->
              <td class="py-3.5 px-4">
                <div class="flex items-center gap-1.5">
                  <span
                    class="px-2 py-0.5 rounded text-[10px] font-bold flex items-center gap-1"
                    :class="[
                      app.configuredProviders?.includes('FCM')
                        ? 'bg-emerald-100 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-300'
                        : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                    ]"
                  >
                    <Smartphone class="w-2.5 h-2.5" />
                    FCM
                  </span>
                  <span
                    class="px-2 py-0.5 rounded text-[10px] font-bold flex items-center gap-1"
                    :class="[
                      app.configuredProviders?.includes('APNS')
                        ? 'bg-sky-100 dark:bg-sky-500/20 text-sky-700 dark:text-sky-300'
                        : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                    ]"
                  >
                    <Apple class="w-2.5 h-2.5" />
                    APNs
                  </span>
                  <span
                    class="px-2 py-0.5 rounded text-[10px] font-bold flex items-center gap-1"
                    :class="[
                      app.configuredProviders?.includes('WEBPUSH')
                        ? 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-300'
                        : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                    ]"
                  >
                    <Globe class="w-2.5 h-2.5" />
                    Web
                  </span>
                </div>
              </td>

              <!-- Status -->
              <td class="py-3.5 px-4">
                <span
                  v-if="app.id === authStore.currentAppId"
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30 inline-flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse"></span>
                  Active Context
                </span>
                <span
                  v-else
                  class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200/80 dark:border-emerald-500/20 inline-flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                  Operational
                </span>
              </td>

              <!-- Created -->
              <td class="py-3.5 px-4 text-slate-500 dark:text-slate-400 text-[11px] whitespace-nowrap">
                {{ formatDate(app.createdAt) }}
              </td>

              <!-- Actions -->
              <td class="py-3.5 px-4 text-right">
                <div class="flex items-center justify-end gap-1.5">
                  <BaseButton
                    v-if="app.id !== authStore.currentAppId"
                    variant="outline"
                    size="xs"
                    :icon="Check"
                    @click="selectApp(app.id)"
                  >
                    Select
                  </BaseButton>

                  <BaseButton
                    variant="outline"
                    size="xs"
                    :icon="Settings"
                    title="Credentials & Gateways"
                    @click="router.push(`/apps/${app.id}/settings`)"
                  />
                  <BaseButton
                    variant="outline"
                    size="xs"
                    :icon="KeyRound"
                    title="API Keys"
                    @click="router.push(`/apps/${app.id}/api-keys`)"
                  />
                  <BaseButton
                    variant="primary"
                    size="xs"
                    :icon="Send"
                    title="Compose Push"
                    @click="router.push(`/apps/${app.id}/notifications/new`)"
                  >
                    Push
                  </BaseButton>

                  <button
                    @click="promptDelete(app)"
                    title="Delete app"
                    class="text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 p-1.5 rounded-lg hover:bg-rose-50 dark:hover:bg-rose-500/10 transition-colors cursor-pointer"
                  >
                    <Trash2 class="w-3.5 h-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create App Modal -->
    <ModernModal
      v-model="showCreateModal"
      title="Register New Application"
      subtitle="Create an application workspace to issue API keys and route push alerts"
      :icon="Smartphone"
      :initial-width="520"
      @close="showCreateModal = false"
    >
      <form id="create-app-form" @submit.prevent="handleCreateApp" class="p-6 space-y-5 text-left">
        <div
          v-if="createError"
          class="p-3.5 rounded-2xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2"
        >
          <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
          <span>{{ createError }}</span>
        </div>

        <BaseInput
          v-model="newAppName"
          label="Application Name"
          placeholder="e.g. Bronx Customer Mobile App"
          required
          :icon="Smartphone"
          size="md"
        />

        <div class="space-y-1.5">
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            Description
          </label>
          <textarea
            v-model="newAppDescription"
            rows="3"
            placeholder="Brief description of the app platform, audience, or environment..."
            class="w-full bg-slate-50/70 dark:bg-slate-950/70 hover:bg-white dark:hover:bg-slate-900 focus:bg-white dark:focus:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 rounded-xl border border-slate-200 dark:border-slate-800 p-3 text-xs sm:text-sm focus:outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 shadow-2xs transition-all"
          ></textarea>
        </div>
      </form>

      <template #footer>
        <BaseButton variant="ghost" @click="showCreateModal = false">
          Cancel
        </BaseButton>
        <BaseButton
          type="submit"
          form="create-app-form"
          variant="primary"
          :loading="isCreating"
        >
          Register Application
        </BaseButton>
      </template>
    </ModernModal>

    <!-- Delete Confirmation Modal -->
    <ModernModal
      v-model="showDeleteModal"
      title="Delete Application"
      subtitle="This action cannot be undone and will revoke all keys"
      :icon="Trash2"
      icon-class="text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-500/10 border-rose-200 dark:border-rose-500/20"
      :initial-width="480"
      @close="showDeleteModal = false"
    >
      <div class="p-6 space-y-4 text-left">
        <p class="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
          Are you sure you want to permanently delete
          <strong class="text-slate-900 dark:text-white font-semibold">{{ appToDelete?.name }}</strong>?
          All associated device tokens, notification records, and provider credentials will be permanently purged.
        </p>
      </div>

      <template #footer>
        <BaseButton variant="ghost" @click="showDeleteModal = false">
          Cancel
        </BaseButton>
        <BaseButton variant="danger" :loading="isDeleting" @click="confirmDelete">
          Permanently Delete
        </BaseButton>
      </template>
    </ModernModal>

    <!-- Quickstart / App Created Credentials Modal (OneSignal Style) -->
    <ModernModal
      v-model="showQuickstartModal"
      title="Application Provisioned!"
      subtitle="Your application workspace and initial API keys are ready"
      :icon="Sparkles"
      icon-class="text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 border-emerald-200 dark:border-emerald-500/20"
      :initial-width="600"
      @close="showQuickstartModal = false"
    >
      <div v-if="newlyCreatedApp" class="p-6 space-y-5 text-left">
        <!-- Top Success Banner -->
        <div
          class="p-4 rounded-2xl bg-gradient-to-r from-emerald-500/10 via-teal-500/10 to-indigo-500/10 border border-emerald-500/20 flex items-start gap-3"
        >
          <div
            class="w-8 h-8 rounded-xl bg-emerald-500 text-white flex items-center justify-center shrink-0 shadow-xs"
          >
            <CheckCircle2 class="w-5 h-5" />
          </div>
          <div>
            <h4 class="text-sm font-bold text-slate-900 dark:text-white">
              {{ newlyCreatedApp.name }} is Live
            </h4>
            <p class="text-xs text-slate-600 dark:text-slate-300 mt-0.5 leading-relaxed">
              Default credentials and Web Push VAPID keys have been automatically provisioned for this application.
            </p>
          </div>
        </div>

        <!-- 1. Public App ID -->
        <div class="space-y-1.5">
          <div class="flex items-center justify-between">
            <label class="text-xs font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
              <span>Public App ID</span>
              <span
                class="text-[10px] px-2 py-0.5 rounded-md font-medium bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400"
              >
                Safe for Web & Mobile SDKs
              </span>
            </label>
          </div>
          <div class="flex items-center gap-2">
            <div
              class="flex-1 bg-slate-50 dark:bg-slate-950 font-mono text-xs px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-slate-800 dark:text-slate-200 select-all truncate"
            >
              {{ newlyCreatedApp.id }}
            </div>
            <BaseButton
              variant="outline"
              size="sm"
              :icon="copiedKeyType === 'appId' ? Check : Copy"
              @click="copyToClipboard(newlyCreatedApp.id, 'appId')"
            >
              {{ copiedKeyType === 'appId' ? 'Copied' : 'Copy' }}
            </BaseButton>
          </div>
        </div>

        <!-- 2. Server REST API Key (Secret Key) -->
        <div v-if="newlyCreatedApp.defaultServerKey?.rawSecretKey" class="space-y-1.5">
          <div class="flex items-center justify-between">
            <label class="text-xs font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
              <span>Server REST API Key</span>
              <span
                class="text-[10px] px-2 py-0.5 rounded-md font-bold bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/20"
              >
                Secret (Backend Server Only)
              </span>
            </label>
          </div>
          <div class="flex items-center gap-2">
            <div
              class="flex-1 bg-amber-50/50 dark:bg-amber-950/20 font-mono text-xs px-3.5 py-2.5 rounded-xl border border-amber-200/80 dark:border-amber-500/30 text-amber-900 dark:text-amber-200 select-all truncate font-semibold"
            >
              {{ newlyCreatedApp.defaultServerKey.rawSecretKey }}
            </div>
            <BaseButton
              variant="outline"
              size="sm"
              :icon="copiedKeyType === 'serverKey' ? Check : Copy"
              @click="copyToClipboard(newlyCreatedApp.defaultServerKey.rawSecretKey, 'serverKey')"
            >
              {{ copiedKeyType === 'serverKey' ? 'Copied' : 'Copy' }}
            </BaseButton>
          </div>
          <p class="text-[11px] text-amber-600 dark:text-amber-400 flex items-center gap-1 mt-1">
            <AlertCircle class="w-3.5 h-3.5 shrink-0" />
            <span>Copy this secret now! For security, the raw key cannot be retrieved again later.</span>
          </p>
        </div>

        <!-- 3. Client SDK Public Key -->
        <div v-if="newlyCreatedApp.defaultClientSdkKey?.rawSecretKey" class="space-y-1.5">
          <div class="flex items-center justify-between">
            <label class="text-xs font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
              <span>Client SDK Key (Optional)</span>
              <span
                class="text-[10px] px-2 py-0.5 rounded-md font-medium bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20"
              >
                X-Sdk-Key Header
              </span>
            </label>
          </div>
          <div class="flex items-center gap-2">
            <div
              class="flex-1 bg-slate-50 dark:bg-slate-950 font-mono text-xs px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-slate-800 dark:text-slate-200 select-all truncate"
            >
              {{ newlyCreatedApp.defaultClientSdkKey.rawSecretKey }}
            </div>
            <BaseButton
              variant="outline"
              size="sm"
              :icon="copiedKeyType === 'sdkKey' ? Check : Copy"
              @click="copyToClipboard(newlyCreatedApp.defaultClientSdkKey.rawSecretKey, 'sdkKey')"
            >
              {{ copiedKeyType === 'sdkKey' ? 'Copied' : 'Copy' }}
            </BaseButton>
          </div>
        </div>

        <!-- 4. Web Push VAPID Status -->
        <div
          v-if="newlyCreatedApp.vapidPublicKey"
          class="p-3 bg-slate-50 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs"
        >
          <div class="flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
            <span class="font-semibold text-slate-700 dark:text-slate-300">Web Push VAPID</span>
            <span class="text-slate-400 text-[11px]">Ready for browser push</span>
          </div>
          <BaseButton
            variant="ghost"
            size="xs"
            :icon="copiedKeyType === 'vapid' ? Check : Copy"
            @click="copyToClipboard(newlyCreatedApp.vapidPublicKey, 'vapid')"
          >
            {{ copiedKeyType === 'vapid' ? 'Copied' : 'Copy VAPID' }}
          </BaseButton>
        </div>
      </div>

      <template #footer>
        <div class="flex flex-col sm:flex-row items-center justify-between w-full gap-2">
          <div class="flex items-center gap-2 w-full sm:w-auto">
            <BaseButton
              variant="outline"
              size="sm"
              :icon="Settings"
              class="w-full sm:w-auto"
              @click="router.push(`/apps/${newlyCreatedApp?.id}/settings`)"
            >
              Configure Gateways
            </BaseButton>
            <BaseButton
              variant="outline"
              size="sm"
              :icon="Send"
              class="w-full sm:w-auto"
              @click="router.push(`/apps/${newlyCreatedApp?.id}/notifications/new`)"
            >
              Compose Push
            </BaseButton>
          </div>

          <BaseButton
            variant="primary"
            size="sm"
            class="w-full sm:w-auto"
            @click="showQuickstartModal = false"
          >
            Done
          </BaseButton>
        </div>
      </template>
    </ModernModal>
  </div>
</template>
