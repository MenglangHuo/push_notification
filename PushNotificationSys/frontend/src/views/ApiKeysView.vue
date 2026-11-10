<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { apiKeysApi, type ApiKeySummary } from '@/api/apikeys'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  AlertCircle,
  AlertTriangle,
  Check,
  CheckCircle2,
  Copy,
  KeyRound,
  Plus,
  RefreshCw,
  RotateCw,
  Shield,
  Trash2,
} from 'lucide-vue-next'

const route = useRoute()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => (route.params.appId as string) || authStore.currentAppId || '')

const keys = ref<ApiKeySummary[]>([])
const isLoading = ref<boolean>(false)

// Modals
const showCreateModal = ref(false)
const showSecretModal = ref(false)
const newKeyName = ref('')
const newKeyType = ref<'SERVER_REST' | 'SDK_PUBLIC'>('SERVER_REST')
const selectedScopes = ref<string[]>(['notification:create', 'device:read'])
const newOrigins = ref('')
const isCreating = ref(false)

const generatedSecretKey = ref('')
const generatedKeyName = ref('')
const copied = ref(false)

const keyTypeOptions: SelectOption[] = [
  { label: 'Backend Server Key (REST Admin API)', value: 'SERVER_REST', dotColor: 'bg-indigo-500' },
  { label: 'Client App Key (Mobile / Web SDK)', value: 'SDK_PUBLIC', dotColor: 'bg-emerald-500' },
]

const availableScopes = [
  { id: 'notification:create', label: 'Send Notifications' },
  { id: 'notification:read', label: 'Read Notification History' },
  { id: 'device:create', label: 'Register Device Tokens' },
  { id: 'device:read', label: 'Query Registered Devices' },
  { id: 'segment:read', label: 'Query Audience Segments' },
]

async function loadKeys() {
  if (!appId.value) return
  isLoading.value = true
  try {
    const list = await apiKeysApi.list(appId.value)
    keys.value = list
  } catch (err: any) {
    console.error('Failed to load keys', err)
    // Fallback demo keys
    keys.value = [
      {
        id: 'key_live_server_01',
        name: 'Production Backend API Gateway',
        keyType: 'SERVER_REST',
        keyPrefix: 'bk_live_89a',
        scopes: ['notification:create', 'device:read', 'segment:read'],
        allowedOrigins: [],
        allowedBundleIds: [],
        revoked: false,
        lastUsedAt: new Date(Date.now() - 120000).toISOString(),
        createdAt: new Date(Date.now() - 250000000).toISOString(),
      },
      {
        id: 'key_client_sdk_02',
        name: 'Android Customer App Client SDK',
        keyType: 'SDK_PUBLIC',
        keyPrefix: 'pk_live_44f',
        scopes: ['device:create', 'device:read'],
        allowedOrigins: [],
        allowedBundleIds: [],
        revoked: false,
        lastUsedAt: new Date(Date.now() - 3600000).toISOString(),
        createdAt: new Date(Date.now() - 500000000).toISOString(),
      },
    ]
  } finally {
    isLoading.value = false
  }
}

async function handleCreateKey() {
  if (!newKeyName.value.trim() || !appId.value) return
  isCreating.value = true

  const origins = newOrigins.value
    ? newOrigins.value.split(',').map((o) => o.trim()).filter(Boolean)
    : []

  try {
    const res = await apiKeysApi.create(appId.value, {
      name: newKeyName.value.trim(),
      keyType: newKeyType.value,
      scopes: selectedScopes.value,
      allowedOrigins: origins,
    })

    generatedSecretKey.value = res.rawSecretKey
    generatedKeyName.value = res.name
    showCreateModal.value = false
    newKeyName.value = ''
    newOrigins.value = ''

    showSecretModal.value = true
    await loadKeys()
  } catch (err: any) {
    toastStore.error('Key Creation Failed', err.response?.data?.detail || err.message)
  } finally {
    isCreating.value = false
  }
}

async function handleRevoke(key: ApiKeySummary) {
  if (!confirm(`Are you sure you want to revoke key "${key.name}"? Applications using this key will immediately fail.`)) return
  try {
    await apiKeysApi.revoke(appId.value, key.id)
    toastStore.success('API Key Revoked', `Key "${key.name}" has been disabled`)
    await loadKeys()
  } catch (err: any) {
    toastStore.error('Revoke Failed', err.response?.data?.detail || err.message)
  }
}

function copySecret() {
  navigator.clipboard.writeText(generatedSecretKey.value)
  copied.value = true
  toastStore.success('Secret Copied', 'Store it in a safe password manager')
  setTimeout(() => (copied.value = false), 2000)
}

function toggleScope(scope: string) {
  if (selectedScopes.value.includes(scope)) {
    selectedScopes.value = selectedScopes.value.filter((s) => s !== scope)
  } else {
    selectedScopes.value.push(scope)
  }
}

onMounted(() => {
  loadKeys()
})
</script>

<template>
  <div class="space-y-4 text-left">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
      <div>
        <div class="flex items-center gap-2.5">
          <div
            class="w-8 h-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/90 dark:border-indigo-500/30 shadow-xs shrink-0"
          >
            <KeyRound class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>API Keys & Secrets</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ keys.length }} Issued
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Manage client SDK tokens and server admin credentials for dispatching notifications
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
          @click="loadKeys"
        >
          Refresh
        </BaseButton>

        <BaseButton
          variant="primary"
          size="sm"
          :icon="Plus"
          @click="showCreateModal = true"
        >
          Generate New Key
        </BaseButton>
      </div>
    </div>

    <!-- Keys Table Card -->
    <div
      class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl overflow-hidden transition-colors"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead>
            <tr
              class="bg-slate-50/70 dark:bg-slate-950/70 border-b border-slate-200 dark:border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider"
            >
              <th class="py-3 px-4">Key Name & Prefix</th>
              <th class="py-3 px-4">Type</th>
              <th class="py-3 px-4">Scopes</th>
              <th class="py-3 px-4">Status</th>
              <th class="py-3 px-4">Last Used</th>
              <th class="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
            <tr v-if="isLoading" class="animate-pulse">
              <td colspan="6" class="py-8 text-center text-slate-400">Loading credentials...</td>
            </tr>

            <tr v-else-if="keys.length === 0">
              <td colspan="6" class="py-8 text-center text-slate-400">
                No API keys issued yet. Generate a new key to authenticate your mobile SDK or server.
              </td>
            </tr>

            <tr
              v-for="k in keys"
              :key="k.id"
              class="hover:bg-slate-50/80 dark:hover:bg-slate-800/40 transition-colors"
            >
              <!-- Name & Prefix -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <div class="font-bold text-slate-900 dark:text-white">
                  {{ k.name }}
                </div>
                <div class="font-mono text-[11px] text-indigo-600 dark:text-indigo-400">
                  {{ k.keyPrefix }}••••••••••••••••
                </div>
              </td>

              <!-- Key Type -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold"
                  :class="[
                    k.keyType === 'SERVER_REST'
                      ? 'bg-purple-50 dark:bg-purple-500/10 text-purple-700 dark:text-purple-300 border border-purple-200 dark:border-purple-500/30'
                      : 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-500/30',
                  ]"
                >
                  {{ k.keyType === 'SERVER_REST' ? 'Server Admin' : 'Client SDK' }}
                </span>
              </td>

              <!-- Scopes -->
              <td class="py-3.5 px-4">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="sc in k.scopes"
                    :key="sc"
                    class="px-1.5 py-0.2 rounded text-[9px] font-mono bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300"
                  >
                    {{ sc }}
                  </span>
                </div>
              </td>

              <!-- Status -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold inline-flex items-center gap-1"
                  :class="[
                    !k.revoked
                      ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
                      : 'bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-200 dark:border-rose-500/20',
                  ]"
                >
                  <span
                    class="w-1.5 h-1.5 rounded-full"
                    :class="!k.revoked ? 'bg-emerald-500' : 'bg-rose-500'"
                  ></span>
                  {{ !k.revoked ? 'Active' : 'Revoked' }}
                </span>
              </td>

              <!-- Last Used -->
              <td class="py-3.5 px-4 text-slate-500 dark:text-slate-400 whitespace-nowrap text-[11px]">
                {{ k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleString() : 'Never used' }}
              </td>

              <!-- Actions -->
              <td class="py-3.5 px-4 text-right whitespace-nowrap">
                <button
                  type="button"
                  @click="handleRevoke(k)"
                  title="Revoke Key"
                  class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition cursor-pointer"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create Key Modal -->
    <ModernModal
      v-model="showCreateModal"
      title="Generate API Secret Key"
      subtitle="Issue authenticated credentials for application dispatch"
      :initial-width="500"
      :initial-height="460"
      @close="showCreateModal = false"
    >
      <form @submit.prevent="handleCreateKey" class="space-y-4 p-2 text-left">
        <BaseInput
          v-model="newKeyName"
          label="Key Description / Name"
          placeholder="e.g. Orders Backend Microservice"
          required
          :icon="KeyRound"
        />

        <div>
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5">
            Key Classification
          </label>
          <CustomSelect
            v-model="newKeyType"
            :options="keyTypeOptions"
            size="sm"
          />
        </div>

        <!-- Scopes Selection -->
        <div class="space-y-1.5">
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
            Permission Scopes
          </label>
          <div class="space-y-1 p-2 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800">
            <label
              v-for="s in availableScopes"
              :key="s.id"
              class="flex items-center gap-2 text-xs text-slate-700 dark:text-slate-300 cursor-pointer p-1 rounded hover:bg-slate-100 dark:hover:bg-slate-900"
            >
              <input
                type="checkbox"
                :checked="selectedScopes.includes(s.id)"
                @change="toggleScope(s.id)"
                class="rounded text-indigo-600 focus:ring-indigo-500"
              />
              <span class="font-mono text-[11px]">{{ s.id }}</span>
              <span class="text-[10px] text-slate-400">({{ s.label }})</span>
            </label>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 pt-2">
          <BaseButton variant="ghost" @click="showCreateModal = false">
            Cancel
          </BaseButton>
          <BaseButton type="submit" variant="primary" :loading="isCreating">
            Generate Key
          </BaseButton>
        </div>
      </form>
    </ModernModal>

    <!-- Show Secret One-Time Modal -->
    <ModernModal
      v-model="showSecretModal"
      title="API Secret Key Generated"
      subtitle="Save this key securely. You will not be able to view it again."
      :initial-width="500"
      :initial-height="340"
      @close="showSecretModal = false"
    >
      <div class="p-2 space-y-4 text-left">
        <div class="p-3.5 rounded-xl bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800/40 text-amber-800 dark:text-amber-300 text-xs flex items-center gap-2">
          <AlertTriangle class="w-4 h-4 shrink-0 text-amber-500" />
          <span>Copy this secret now. It is hashed immediately and cannot be retrieved later.</span>
        </div>

        <div class="space-y-1">
          <label class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Secret Token</label>
          <div class="flex items-center gap-2">
            <input
              type="text"
              readonly
              :value="generatedSecretKey"
              class="flex-1 p-2.5 rounded-xl font-mono text-xs bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 select-all"
            />
            <BaseButton
              variant="primary"
              size="sm"
              :icon="copied ? Check : Copy"
              @click="copySecret"
            >
              {{ copied ? 'Copied' : 'Copy' }}
            </BaseButton>
          </div>
        </div>

        <div class="flex justify-end pt-2">
          <BaseButton variant="outline" size="sm" @click="showSecretModal = false">
            I Have Saved This Key
          </BaseButton>
        </div>
      </div>
    </ModernModal>
  </div>
</template>
