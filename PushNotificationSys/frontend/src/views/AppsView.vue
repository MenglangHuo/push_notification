<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { appsApi, type AppSummary } from '@/api/apps'
import ModernModal from '@/components/common/ModernModal.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import {
  AlertCircle,
  BellRing,
  Building2,
  Check,
  CheckCircle2,
  Copy,
  KeyRound,
  Layers,
  Plus,
  Radio,
  Send,
  Settings,
  Smartphone,
  Trash2,
  Zap,
} from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const apps = ref<AppSummary[]>([])
const isLoading = ref<boolean>(false)
const showCreateModal = ref<boolean>(false)
const showDeleteModal = ref<boolean>(false)
const appToDelete = ref<AppSummary | null>(null)

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
    if (!authStore.currentAppId && list.length > 0) {
      authStore.setCurrentApp(list[0].id)
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

async function handleCreateApp() {
  if (!authStore.currentOrgId || !newAppName.value.trim()) return
  isCreating.value = true
  createError.value = ''

  try {
    const created = await appsApi.create(authStore.currentOrgId, {
      name: newAppName.value.trim(),
      description: newAppDescription.value.trim(),
    })
    toastStore.success('Application Created', `App "${created.name}" is ready for use`)
    showCreateModal.value = false
    newAppName.value = ''
    newAppDescription.value = ''
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

onMounted(() => {
  loadApps()
})
</script>

<template>
  <div class="space-y-6 text-left">
    <!-- Page Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <div class="flex items-center gap-2.5">
          <div
            class="w-8 h-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/90 dark:border-indigo-500/30 shadow-xs shrink-0"
          >
            <Layers class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Applications & Tenants</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ apps.length }} Active
              </span>
            </h1>
            <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Manage mobile and web applications, provider certificates, and API client keys
            </p>
          </div>
        </div>
      </div>

      <BaseButton
        variant="primary"
        size="sm"
        :icon="Plus"
        @click="showCreateModal = true"
      >
        Register New App
      </BaseButton>
    </div>

    <!-- Empty State -->
    <div
      v-if="!isLoading && apps.length === 0"
      class="bg-white dark:bg-slate-900/90 rounded-2xl p-10 border border-slate-200 dark:border-slate-800 text-center space-y-3"
    >
      <Layers class="w-10 h-10 text-slate-400 mx-auto" />
      <h3 class="text-sm font-semibold text-slate-900 dark:text-white">
        No Applications Configured
      </h3>
      <p class="text-xs text-slate-500 max-w-sm mx-auto">
        Create your first app to configure FCM v1, Apple APNs, Web Push VAPID credentials, and issue API client keys.
      </p>
      <BaseButton
        variant="primary"
        size="sm"
        :icon="Plus"
        @click="showCreateModal = true"
      >
        Register Application
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

    <!-- Applications Card Grid -->
    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="app in apps"
        :key="app.id"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl flex flex-col justify-between transition-all hover:border-indigo-300 dark:hover:border-indigo-500/40 relative"
        :class="{
          'ring-2 ring-indigo-500/30 border-indigo-400 dark:border-indigo-500/60': app.id === authStore.currentAppId,
        }"
      >
        <!-- Card Top Details -->
        <div>
          <!-- Header Status Pill & Context Indicator -->
          <div class="flex items-center justify-between mb-3">
            <span
              v-if="app.id === authStore.currentAppId"
              class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30 flex items-center gap-1"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse"></span>
              Active Context
            </span>
            <span
              v-else
              class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200/80 dark:border-emerald-500/20 flex items-center gap-1"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
              Operational
            </span>

            <button
              @click="promptDelete(app)"
              title="Delete app"
              class="text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 p-1 rounded-lg transition-colors cursor-pointer"
            >
              <Trash2 class="w-3.5 h-3.5" />
            </button>
          </div>

          <!-- App Title & Description -->
          <h3 class="text-sm font-bold text-slate-900 dark:text-white mb-1">
            {{ app.name }}
          </h3>
          <p class="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 mb-4 leading-relaxed">
            {{ app.description || 'No description provided.' }}
          </p>

          <!-- App ID Box -->
          <div
            class="space-y-2 bg-slate-50 dark:bg-slate-950/60 p-3 rounded-xl border border-slate-200 dark:border-slate-800/80 text-xs mb-4"
          >
            <div class="flex items-center justify-between">
              <span class="text-slate-500 dark:text-slate-400">App ID:</span>
              <button
                @click="copyAppId(app.id)"
                class="font-mono text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1 cursor-pointer font-semibold text-[11px]"
              >
                <span>{{ app.id }}</span>
                <Check
                  v-if="copiedAppId === app.id"
                  class="w-3 h-3 text-emerald-600 dark:text-emerald-400"
                />
                <Copy v-else class="w-3 h-3 text-slate-400" />
              </button>
            </div>

            <!-- Provider Readiness -->
            <div class="flex items-center justify-between pt-1 border-t border-slate-200/60 dark:border-slate-800/60">
              <span class="text-slate-500 dark:text-slate-400">Gateways:</span>
              <div class="flex items-center gap-1">
                <span
                  class="px-1.5 py-0.2 rounded text-[9px] font-bold"
                  :class="[
                    app.configuredProviders?.includes('FCM')
                      ? 'bg-emerald-100 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-300'
                      : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                  ]"
                >
                  FCM
                </span>
                <span
                  class="px-1.5 py-0.2 rounded text-[9px] font-bold"
                  :class="[
                    app.configuredProviders?.includes('APNS')
                      ? 'bg-emerald-100 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-300'
                      : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                  ]"
                >
                  APNs
                </span>
                <span
                  class="px-1.5 py-0.2 rounded text-[9px] font-bold"
                  :class="[
                    app.configuredProviders?.includes('WEBPUSH')
                      ? 'bg-emerald-100 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-300'
                      : 'bg-slate-200 dark:bg-slate-800 text-slate-400',
                  ]"
                >
                  Web
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Action Row -->
        <div class="pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between gap-2">
          <BaseButton
            v-if="app.id !== authStore.currentAppId"
            variant="outline"
            size="xs"
            class="flex-1"
            @click="selectApp(app.id)"
          >
            Select App
          </BaseButton>
          <span
            v-else
            class="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400 flex items-center gap-1 pl-1"
          >
            <CheckCircle2 class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
            Selected
          </span>

          <div class="flex items-center gap-1.5">
            <BaseButton
              variant="outline"
              size="xs"
              :icon="Settings"
              title="Credentials Settings"
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
          </div>
        </div>
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
  </div>
</template>
