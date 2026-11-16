<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useAppStore } from '@/stores/appStore'
import { orgsApi } from '@/api/orgs'
import {
  ChevronDown,
  Building2,
  Plus,
  Check,
  Layers,
  Sparkles,
  Search,
  X,
  SlidersHorizontal,
  ArrowRight,
} from 'lucide-vue-next'
import ModernModal from '@/components/common/ModernModal.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useToastStore } from '@/stores/toastStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()
const toastStore = useToastStore()

const isOpen = ref(false)
const searchQuery = ref('')
const activeTab = ref<'apps' | 'workspaces' | 'all'>('apps')
const containerRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)

const showCreateModal = ref(false)
const newOrgName = ref('')
const newOrgSlug = ref('')
const isCreating = ref(false)
const createError = ref('')

const appList = computed(() => appStore.apps)
const orgList = computed(() => authStore.user?.organizations || [])
const currentOrg = computed(() => authStore.currentOrg)

onMounted(() => {
  if (authStore.currentOrgId) {
    appStore.loadApps(authStore.currentOrgId)
  }
  document.addEventListener('click', handleClickOutside)
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('keydown', handleKeydown)
})

const currentAppName = computed(() => {
  if (!authStore.currentAppId) return 'All Apps'
  const found = appList.value.find((a) => a.id === authStore.currentAppId)
  return found ? found.name : 'Select App'
})

function toggleDropdown() {
  isOpen.value = !isOpen.value
}

watch(isOpen, (open) => {
  if (open) {
    searchQuery.value = ''
    nextTick(() => {
      searchInputRef.value?.focus()
    })
  }
})

function handleClickOutside(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    isOpen.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (!isOpen.value) return
  if (e.key === 'Escape') {
    isOpen.value = false
  }
}

// Search Filtering Logic
const filteredApps = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) return appList.value
  return appList.value.filter((app) => {
    const nameMatch = app.name.toLowerCase().includes(query)
    const descMatch = app.description?.toLowerCase().includes(query)
    const providerMatch = app.configuredProviders?.some((p) => p.toLowerCase().includes(query))
    return nameMatch || descMatch || providerMatch
  })
})

const filteredOrgs = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) return orgList.value
  return orgList.value.filter((org) => {
    const nameMatch = org.name.toLowerCase().includes(query)
    const slugMatch = org.slug.toLowerCase().includes(query)
    const tierMatch = org.tier?.toLowerCase().includes(query)
    return nameMatch || slugMatch || tierMatch
  })
})

async function selectOrg(orgId: string) {
  isOpen.value = false
  authStore.setCurrentOrg(orgId)
  const apps = await appStore.loadApps(orgId, true)
  const newAppId = apps && apps.length > 0 ? apps[0].id : null
  authStore.setCurrentApp(newAppId)

  // If currently on an app-scoped route (e.g. /apps/:appId/...), synchronize the URL param
  if (route.params.appId) {
    if (newAppId) {
      await router.push({
        name: route.name || undefined,
        params: { ...route.params, appId: newAppId },
        query: route.query,
      })
    } else {
      const directPath = route.path.replace(/^\/apps\/[^/]+/, '')
      await router.push(directPath || '/dashboard')
    }
  }
}

async function selectApp(appId: string | null) {
  appStore.selectApp(appId)
  isOpen.value = false

  // If currently on an app-scoped route (e.g. /apps/:appId/...), synchronize the URL param
  if (route.params.appId) {
    if (appId) {
      await router.push({
        name: route.name || undefined,
        params: { ...route.params, appId },
        query: route.query,
      })
    } else {
      const directPath = route.path.replace(/^\/apps\/[^/]+/, '')
      await router.push(directPath || '/dashboard')
    }
  }
}

function goToManageApps() {
  isOpen.value = false
  router.push('/apps')
}

function getProviderMeta(provider: 'FCM' | 'APNS' | 'WEBPUSH') {
  switch (provider) {
    case 'FCM':
      return {
        label: 'FCM',
        title: 'Firebase Cloud Messaging (Android/iOS)',
        class: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400 border-emerald-200/60 dark:border-emerald-500/30',
      }
    case 'APNS':
      return {
        label: 'APNs',
        title: 'Apple Push Notification service (iOS/macOS)',
        class: 'bg-sky-50 text-sky-700 dark:bg-sky-500/15 dark:text-sky-400 border-sky-200/60 dark:border-sky-500/30',
      }
    case 'WEBPUSH':
      return {
        label: 'Web',
        title: 'Web Push (VAPID)',
        class: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-400 border-indigo-200/60 dark:border-indigo-500/30',
      }
  }
}

function autoSlug() {
  newOrgSlug.value = newOrgName.value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

async function handleCreateOrg() {
  if (!newOrgName.value || !newOrgSlug.value) return
  isCreating.value = true
  createError.value = ''

  try {
    const created = await orgsApi.create({
      name: newOrgName.value,
      slug: newOrgSlug.value,
    })
    await authStore.fetchMe()
    authStore.setCurrentOrg(created.id)
    showCreateModal.value = false
    isOpen.value = false
    toastStore.success('Workspace Created', `Switched to ${created.name}`)
    await appStore.loadApps(created.id, true)
  } catch (err: any) {
    createError.value = err.response?.data?.detail || 'Failed to create organization'
  } finally {
    isCreating.value = false
  }
}
</script>

<template>
  <div ref="containerRef" class="relative inline-block text-left select-none">
    <!-- Trigger Button -->
    <button
      type="button"
      @click="toggleDropdown"
      class="flex items-center gap-2 px-3 py-1.5 rounded-xl border transition-all text-xs font-semibold cursor-pointer select-none"
      :class="[
        isOpen
          ? 'bg-slate-100 dark:bg-slate-800 border-indigo-300 dark:border-indigo-500/50 ring-2 ring-indigo-500/20 text-slate-900 dark:text-white shadow-xs'
          : 'bg-white dark:bg-slate-900 border-slate-200/90 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/80 text-slate-800 dark:text-slate-200 shadow-2xs',
      ]"
      :title="`${authStore.currentOrg?.name || 'Workspace'} • ${currentAppName}`"
      aria-haspopup="true"
      :aria-expanded="isOpen"
    >
      <div
        class="w-6 h-6 rounded-lg bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0 border border-indigo-200/80 dark:border-indigo-500/30 font-bold text-[11px]"
      >
        <Building2 class="w-3.5 h-3.5" />
      </div>

      <div class="text-left leading-tight truncate max-w-[120px] sm:max-w-[160px]">
        <div class="truncate text-[11px] font-bold text-slate-900 dark:text-white">
          {{ authStore.currentOrg?.name || 'Select Org' }}
        </div>
        <div class="truncate text-[10px] text-slate-500 dark:text-slate-400 font-medium">
          {{ currentAppName }}
        </div>
      </div>

      <ChevronDown
        class="w-3.5 h-3.5 text-slate-400 transition-transform duration-200 shrink-0 ml-0.5"
        :class="{ 'rotate-180 text-indigo-600 dark:text-indigo-400': isOpen }"
      />
    </button>

    <!-- Dropdown Popover Surface (Solid opaque, no background bleed) -->
    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="transform scale-95 opacity-0 -translate-y-1"
      enter-to-class="transform scale-100 opacity-100 translate-y-0"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="transform scale-100 opacity-100 translate-y-0"
      leave-to-class="transform scale-95 opacity-0 -translate-y-1"
    >
      <div
        v-if="isOpen"
        class="absolute left-0 mt-2 w-84 sm:w-96 bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-slate-800 rounded-2xl shadow-2xl shadow-slate-950/15 dark:shadow-black/70 ring-1 ring-black/5 dark:ring-white/10 z-50 overflow-hidden flex flex-col"
      >
        <!-- Top Workspace Bar & Switch Hint -->
        <div
          class="px-3 pt-2.5 pb-2 border-b border-slate-100 dark:border-slate-800/80 bg-slate-50/60 dark:bg-slate-900/60 flex items-center justify-between"
        >
          <div class="flex items-center gap-2 min-w-0">
            <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Workspace</span>
            <span class="text-xs font-bold text-slate-800 dark:text-slate-100 truncate">
              {{ currentOrg?.name || 'Select Workspace' }}
            </span>
            <span
              v-if="currentOrg?.tier"
              class="text-[9px] px-1.5 py-0.5 rounded-md font-bold uppercase tracking-wider bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200/60 dark:border-indigo-500/30"
            >
              {{ currentOrg.tier }}
            </span>
          </div>

          <button
            v-if="activeTab !== 'workspaces'"
            type="button"
            @click="activeTab = 'workspaces'"
            class="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 transition flex items-center gap-1 cursor-pointer shrink-0"
          >
            <span>Switch</span>
            <ArrowRight class="w-3 h-3" />
          </button>
        </div>

        <!-- Search Bar -->
        <div class="p-2.5 pb-1.5">
          <div class="relative flex items-center">
            <Search class="absolute left-2.5 w-3.5 h-3.5 text-slate-400 pointer-events-none" />
            <input
              ref="searchInputRef"
              v-model="searchQuery"
              type="text"
              placeholder="Search apps or workspaces..."
              class="w-full bg-slate-100/80 dark:bg-slate-800/80 text-xs text-slate-900 dark:text-white placeholder:text-slate-400 rounded-xl pl-8 pr-7 py-1.5 border border-slate-200/80 dark:border-slate-700/80 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition"
            />
            <button
              v-if="searchQuery"
              type="button"
              @click="searchQuery = ''; searchInputRef?.focus()"
              class="absolute right-2 p-0.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-full transition cursor-pointer"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <!-- Segmented Filter Pills -->
        <div class="px-2.5 pb-2 flex items-center gap-1 border-b border-slate-100 dark:border-slate-800/80">
          <button
            type="button"
            @click="activeTab = 'apps'"
            class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold transition cursor-pointer"
            :class="[
              activeTab === 'apps'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <Layers class="w-3 h-3" />
            <span>Apps</span>
            <span
              class="text-[10px] px-1 rounded-md"
              :class="activeTab === 'apps' ? 'bg-indigo-500/60 text-white' : 'bg-slate-200/70 dark:bg-slate-700 text-slate-600 dark:text-slate-300'"
            >
              {{ filteredApps.length }}
            </span>
          </button>

          <button
            type="button"
            @click="activeTab = 'workspaces'"
            class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold transition cursor-pointer"
            :class="[
              activeTab === 'workspaces'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <Building2 class="w-3 h-3" />
            <span>Workspaces</span>
            <span
              class="text-[10px] px-1 rounded-md"
              :class="activeTab === 'workspaces' ? 'bg-indigo-500/60 text-white' : 'bg-slate-200/70 dark:bg-slate-700 text-slate-600 dark:text-slate-300'"
            >
              {{ filteredOrgs.length }}
            </span>
          </button>

          <button
            type="button"
            @click="activeTab = 'all'"
            class="flex items-center gap-1 px-2 py-1 rounded-lg text-xs font-medium transition cursor-pointer"
            :class="[
              activeTab === 'all'
                ? 'bg-indigo-600 text-white shadow-xs font-semibold'
                : 'text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <span>All</span>
          </button>
        </div>

        <!-- Scrollable Content Area -->
        <div class="max-h-72 overflow-y-auto p-1.5 space-y-1">
          <!-- TAB: APPS -->
          <template v-if="activeTab === 'apps' || activeTab === 'all'">
            <div
              v-if="activeTab === 'all'"
              class="flex items-center justify-between px-2 pt-1 pb-0.5 text-[10px] font-bold tracking-wider text-slate-400 uppercase"
            >
              <span>Apps ({{ currentOrg?.name || 'Current' }})</span>
              <span class="text-[9px] font-mono text-indigo-500">{{ filteredApps.length }}</span>
            </div>

            <!-- "All Apps Overview" Option -->
            <button
              v-if="!searchQuery"
              type="button"
              @click="selectApp(null)"
              class="w-full flex items-center justify-between p-2 rounded-xl text-xs transition text-left cursor-pointer group"
              :class="[
                !authStore.currentAppId
                  ? 'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 font-bold border border-indigo-200/90 dark:border-indigo-500/30'
                  : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80 border border-transparent',
              ]"
            >
              <div class="flex items-center gap-2.5 min-w-0">
                <div
                  class="w-7 h-7 rounded-lg flex items-center justify-center shrink-0"
                  :class="[
                    !authStore.currentAppId
                      ? 'bg-indigo-600 text-white'
                      : 'bg-slate-100 dark:bg-slate-800 text-slate-500 group-hover:text-slate-700 dark:group-hover:text-slate-200',
                  ]"
                >
                  <Layers class="w-3.5 h-3.5" />
                </div>
                <div class="truncate">
                  <div class="font-bold text-xs truncate">All Apps Overview</div>
                  <div class="text-[10px] text-slate-400 font-normal truncate">Combined analytics & dispatch</div>
                </div>
              </div>
              <Check v-if="!authStore.currentAppId" class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0" />
            </button>

            <!-- Apps List -->
            <button
              v-for="app in filteredApps"
              :key="app.id"
              type="button"
              @click="selectApp(app.id)"
              class="w-full flex items-center justify-between p-2 rounded-xl text-xs transition text-left cursor-pointer group"
              :class="[
                app.id === authStore.currentAppId
                  ? 'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 font-bold border border-indigo-200/90 dark:border-indigo-500/30'
                  : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80 border border-transparent',
              ]"
            >
              <div class="flex items-center gap-2.5 min-w-0">
                <div
                  class="w-7 h-7 rounded-lg flex items-center justify-center shrink-0 font-bold text-xs"
                  :class="[
                    app.id === authStore.currentAppId
                      ? 'bg-indigo-600 text-white'
                      : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-200/60 dark:border-slate-700/60 group-hover:border-slate-300 dark:group-hover:border-slate-600',
                  ]"
                >
                  {{ app.name.slice(0, 1).toUpperCase() }}
                </div>
                <div class="truncate min-w-0">
                  <div class="font-bold text-xs truncate">{{ app.name }}</div>
                  <div class="flex items-center gap-1 mt-0.5">
                    <template v-if="app.configuredProviders && app.configuredProviders.length > 0">
                      <span
                        v-for="provider in app.configuredProviders"
                        :key="provider"
                        class="text-[9px] px-1 py-0.2 rounded border font-mono font-medium leading-none"
                        :class="getProviderMeta(provider)?.class"
                        :title="getProviderMeta(provider)?.title"
                      >
                        {{ getProviderMeta(provider)?.label }}
                      </span>
                    </template>
                    <span v-else class="text-[10px] text-slate-400">No platforms active</span>
                  </div>
                </div>
              </div>

              <Check v-if="app.id === authStore.currentAppId" class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 ml-2" />
            </button>

            <!-- Apps Empty State -->
            <div
              v-if="filteredApps.length === 0"
              class="py-6 px-3 text-center text-slate-400"
            >
              <div class="w-9 h-9 mx-auto rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-400 mb-2">
                <Layers class="w-4 h-4" />
              </div>
              <p class="text-xs font-semibold text-slate-700 dark:text-slate-300">
                {{ searchQuery ? `No apps matching "${searchQuery}"` : 'No apps in this workspace' }}
              </p>
              <p v-if="searchQuery && filteredOrgs.length > 0" class="text-[11px] text-indigo-600 dark:text-indigo-400 mt-1 cursor-pointer" @click="activeTab = 'workspaces'">
                Found {{ filteredOrgs.length }} matching workspace{{ filteredOrgs.length > 1 ? 's' : '' }} &rarr;
              </p>
              <p v-else-if="!searchQuery" class="text-[10px] text-slate-400 mt-0.5">
                Create your first push app to begin sending
              </p>
            </div>
          </template>

          <!-- TAB: WORKSPACES -->
          <template v-if="activeTab === 'workspaces' || activeTab === 'all'">
            <div
              v-if="activeTab === 'all'"
              class="flex items-center justify-between px-2 pt-2.5 pb-0.5 text-[10px] font-bold tracking-wider text-slate-400 uppercase border-t border-slate-100 dark:border-slate-800 mt-2"
            >
              <span>Workspaces</span>
              <span class="text-[9px] font-mono text-indigo-500">{{ filteredOrgs.length }}</span>
            </div>

            <button
              v-for="org in filteredOrgs"
              :key="org.orgId"
              type="button"
              @click="selectOrg(org.orgId)"
              class="w-full flex items-center justify-between p-2 rounded-xl text-xs transition text-left cursor-pointer group"
              :class="[
                org.orgId === authStore.currentOrgId
                  ? 'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 font-bold border border-indigo-200/90 dark:border-indigo-500/30'
                  : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80 border border-transparent',
              ]"
            >
              <div class="flex items-center gap-2.5 min-w-0">
                <div
                  class="w-7 h-7 rounded-lg flex items-center justify-center shrink-0"
                  :class="[
                    org.orgId === authStore.currentOrgId
                      ? 'bg-indigo-600 text-white'
                      : 'bg-slate-100 dark:bg-slate-800 text-slate-500 group-hover:text-slate-700 dark:group-hover:text-slate-300',
                  ]"
                >
                  <Building2 class="w-3.5 h-3.5" />
                </div>
                <div class="truncate min-w-0">
                  <div class="font-bold text-xs truncate flex items-center gap-1.5">
                    <span>{{ org.name }}</span>
                    <span
                      v-if="org.tier"
                      class="text-[9px] px-1 py-0.2 rounded font-mono font-medium"
                      :class="[
                        org.orgId === authStore.currentOrgId
                          ? 'bg-indigo-100 dark:bg-indigo-500/20 text-indigo-700 dark:text-indigo-300'
                          : 'bg-slate-100 dark:bg-slate-800 text-slate-500',
                      ]"
                    >
                      {{ org.tier }}
                    </span>
                  </div>
                  <div class="text-[10px] text-slate-400 font-mono truncate">@{{ org.slug }}</div>
                </div>
              </div>

              <Check v-if="org.orgId === authStore.currentOrgId" class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 ml-2" />
            </button>

            <!-- Workspaces Empty State -->
            <div
              v-if="filteredOrgs.length === 0"
              class="py-6 px-3 text-center text-slate-400"
            >
              <div class="w-9 h-9 mx-auto rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-400 mb-2">
                <Building2 class="w-4 h-4" />
              </div>
              <p class="text-xs font-semibold text-slate-700 dark:text-slate-300">
                No workspaces matching "{{ searchQuery }}"
              </p>
            </div>
          </template>
        </div>

        <!-- Popover Action Footer -->
        <div
          class="border-t border-slate-100 dark:border-slate-800/80 p-2 bg-slate-50/70 dark:bg-slate-900/70 flex items-center justify-between gap-2"
        >
          <button
            type="button"
            @click="showCreateModal = true; isOpen = false"
            class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition cursor-pointer"
          >
            <Plus class="w-3.5 h-3.5" />
            <span>New Workspace</span>
          </button>

          <button
            type="button"
            @click="goToManageApps"
            class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl text-xs font-medium text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800 transition cursor-pointer"
            title="Open App Management"
          >
            <SlidersHorizontal class="w-3.5 h-3.5" />
            <span>Manage Apps</span>
          </button>
        </div>
      </div>
    </Transition>

    <!-- Create Org Modal -->
    <ModernModal
      v-model="showCreateModal"
      title="Create Organization Workspace"
      subtitle="Provision a multi-tenant workspace for push notification apps"
      :icon="Building2"
      :initial-width="500"
      @close="showCreateModal = false"
    >
      <form id="create-org-form" @submit.prevent="handleCreateOrg" class="p-6 space-y-5 text-left">
        <div
          v-if="createError"
          class="p-3.5 rounded-2xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2"
        >
          {{ createError }}
        </div>

        <BaseInput
          v-model="newOrgName"
          label="Organization Name"
          placeholder="e.g. Acme Corporation"
          required
          :icon="Building2"
          size="md"
          @input="autoSlug"
        />

        <BaseInput
          v-model="newOrgSlug"
          label="URL Slug (Identifier)"
          placeholder="e.g. acme"
          required
          :icon="Sparkles"
          size="md"
        />
      </form>

      <template #footer>
        <BaseButton variant="ghost" @click="showCreateModal = false">
          Cancel
        </BaseButton>
        <BaseButton
          type="submit"
          form="create-org-form"
          variant="primary"
          :loading="isCreating"
        >
          Create Workspace
        </BaseButton>
      </template>
    </ModernModal>
  </div>
</template>
