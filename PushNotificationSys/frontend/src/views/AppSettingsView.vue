<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { appsApi, type AppSummary } from '@/api/apps'
import { credentialsApi, type CredentialSummary } from '@/api/credentials'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import {
  Apple,
  ArrowLeft,
  Check,
  CheckCircle2,
  Copy,
  Globe,
  Key,
  Shield,
  Smartphone,
  Trash2,
  Zap,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => (route.params.appId as string) || authStore.currentAppId || '')

const app = ref<AppSummary | null>(null)
const credentials = ref<CredentialSummary[]>([])
const activeTab = ref<'fcm' | 'apns' | 'webpush'>('fcm')
const isLoading = ref<boolean>(false)

// FCM form
const fcmJson = ref('')
const isSavingFcm = ref(false)

// APNs form
const apnsP8Key = ref('')
const apnsKeyId = ref('')
const apnsTeamId = ref('')
const apnsBundleId = ref('')
const isSavingApns = ref(false)

// VAPID
const isGeneratingVapid = ref(false)
const copied = ref(false)

const fcmCredential = computed(() => credentials.value.find((c) => c.providerType === 'FCM'))
const apnsCredential = computed(() => credentials.value.find((c) => c.providerType === 'APNS'))
const webpushCredential = computed(() => credentials.value.find((c) => c.providerType === 'WEBPUSH'))
const webpushPublicKey = computed(() => {
  return (webpushCredential.value?.metadataMasked?.vapidPublicKey || (webpushCredential.value?.metadataMasked as any)?.publicKey || '') as string
})

async function loadData() {
  if (!appId.value) return
  isLoading.value = true
  try {
    const [appData, credsData] = await Promise.all([
      appsApi.get(appId.value).catch(() => null),
      credentialsApi.list(appId.value).catch(() => []),
    ])
    if (appData) app.value = appData
    credentials.value = credsData
  } catch (err: any) {
    console.error('Failed to load credentials', err)
  } finally {
    isLoading.value = false
  }
}

async function handleSaveFcm() {
  if (!fcmJson.value.trim() || !appId.value) return
  isSavingFcm.value = true
  try {
    await credentialsApi.uploadFcm(appId.value, fcmJson.value.trim())
    fcmJson.value = ''
    await loadData()
    toastStore.success('FCM Configured', 'Google Service Account JSON encrypted and stored')
  } catch (err: any) {
    toastStore.error('Save Failed', err.response?.data?.detail || err.message)
  } finally {
    isSavingFcm.value = false
  }
}

async function handleSaveApns() {
  if (!apnsP8Key.value.trim() || !apnsKeyId.value.trim() || !appId.value) return
  isSavingApns.value = true
  try {
    await credentialsApi.saveApns(appId.value, {
      rawCredential: apnsP8Key.value.trim(),
      keyId: apnsKeyId.value.trim(),
      teamId: apnsTeamId.value.trim(),
      bundleId: apnsBundleId.value.trim(),
    })
    apnsP8Key.value = ''
    await loadData()
    toastStore.success('APNs Configured', 'Apple Push .p8 key encrypted and stored')
  } catch (err: any) {
    toastStore.error('Save Failed', err.response?.data?.detail || err.message)
  } finally {
    isSavingApns.value = false
  }
}

async function handleGenerateVapid() {
  if (!appId.value) return
  isGeneratingVapid.value = true
  try {
    await credentialsApi.generateVapid(appId.value)
    await loadData()
    toastStore.success('VAPID Generated', 'NIST P-256 public/private key pair created')
  } catch (err: any) {
    toastStore.error('Generation Failed', err.response?.data?.detail || err.message)
  } finally {
    isGeneratingVapid.value = false
  }
}

async function handleDeleteCredential(provider: 'fcm' | 'apns' | 'webpush') {
  if (!confirm(`Are you sure you want to remove ${provider.toUpperCase()} credentials?`)) return
  try {
    await credentialsApi.delete(appId.value, provider)
    toastStore.success('Credential Removed', `${provider.toUpperCase()} certificate removed`)
    await loadData()
  } catch (err: any) {
    toastStore.error('Delete Failed', err.response?.data?.detail || err.message)
  }
}

function copyVapidKey(key: string) {
  navigator.clipboard.writeText(key)
  copied.value = true
  toastStore.success('Copied Public VAPID Key', key)
  setTimeout(() => (copied.value = false), 2000)
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-6 text-left pb-12">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-1 border-b border-slate-200 dark:border-slate-800">
      <div class="flex items-center gap-3">
        <button
          type="button"
          @click="router.back()"
          class="p-2 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition cursor-pointer"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <span>Provider Credentials Settings</span>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
              {{ app?.name || appId }}
            </span>
          </h1>
          <p class="text-xs text-slate-500 dark:text-slate-400">
            Configure Google FCM v1 JSON service account, Apple APNs .p8 keys, and Web Push VAPID secrets
          </p>
        </div>
      </div>
    </div>

    <!-- Provider Tabs -->
    <div class="flex items-center gap-2 border-b border-slate-200 dark:border-slate-800 pb-2">
      <button
        v-for="tab in [
          { id: 'fcm', label: 'Firebase Cloud Messaging (FCM)', icon: Smartphone, configured: !!fcmCredential },
          { id: 'apns', label: 'Apple Push (APNs)', icon: Apple, configured: !!apnsCredential },
          { id: 'webpush', label: 'Web Push (VAPID)', icon: Globe, configured: !!webpushCredential },
        ] as const"
        :key="tab.id"
        type="button"
        @click="activeTab = tab.id"
        class="flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition cursor-pointer"
        :class="[
          activeTab === tab.id
            ? 'bg-indigo-600 text-white shadow-xs'
            : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
        ]"
      >
        <component :is="tab.icon" class="w-4 h-4" />
        <span>{{ tab.label }}</span>
        <span
          class="w-1.5 h-1.5 rounded-full"
          :class="tab.configured ? 'bg-emerald-400' : 'bg-slate-400'"
        ></span>
      </button>
    </div>

    <!-- FCM v1 Configuration Panel -->
    <div v-if="activeTab === 'fcm'" class="space-y-4 max-w-2xl animate-in fade-in">
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
        <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
          <div>
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white">
              Google Firebase Cloud Messaging v1
            </h3>
            <p class="text-[11px] text-slate-500">Service account JSON with Firebase Cloud Messaging Admin permissions</p>
          </div>
          <span
            v-if="fcmCredential"
            class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200"
          >
            Configured
          </span>
        </div>

        <div v-if="fcmCredential" class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div class="text-xs">
            <span class="font-bold text-slate-800 dark:text-slate-200 block">Service Account Active</span>
            <span class="text-slate-400 text-[11px]">Updated: {{ new Date(fcmCredential.updatedAt).toLocaleDateString() }}</span>
          </div>
          <BaseButton variant="danger" size="xs" @click="handleDeleteCredential('fcm')">
            Remove FCM
          </BaseButton>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
            Paste Service Account JSON
          </label>
          <textarea
            v-model="fcmJson"
            rows="6"
            placeholder='{ "type": "service_account", "project_id": "my-firebase-app", ... }'
            class="w-full bg-slate-50 dark:bg-slate-950 font-mono text-xs text-slate-900 dark:text-white p-3 rounded-xl border border-slate-200 dark:border-slate-800 focus:outline-none focus:border-indigo-500"
          ></textarea>
        </div>

        <div class="flex justify-end pt-2">
          <BaseButton variant="primary" size="sm" :loading="isSavingFcm" @click="handleSaveFcm">
            Save FCM Certificate
          </BaseButton>
        </div>
      </div>
    </div>

    <!-- APNs Configuration Panel -->
    <div v-if="activeTab === 'apns'" class="space-y-4 max-w-2xl animate-in fade-in">
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
        <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
          <div>
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white">
              Apple Push Notification service (APNs)
            </h3>
            <p class="text-[11px] text-slate-500">Apple Developer .p8 token-based authentication</p>
          </div>
          <span
            v-if="apnsCredential"
            class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200"
          >
            Configured
          </span>
        </div>

        <div v-if="apnsCredential" class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div class="text-xs">
            <span class="font-bold text-slate-800 dark:text-slate-200 block">APNs Key Configured</span>
            <span class="text-slate-400 text-[11px]">Updated: {{ new Date(apnsCredential.updatedAt).toLocaleDateString() }}</span>
          </div>
          <BaseButton variant="danger" size="xs" @click="handleDeleteCredential('apns')">
            Remove APNs
          </BaseButton>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <BaseInput v-model="apnsKeyId" label="Key ID (10 chars)" placeholder="e.g. 2X994992D2" size="sm" />
          <BaseInput v-model="apnsTeamId" label="Team ID (10 chars)" placeholder="e.g. DEF456GHIJ" size="sm" />
          <BaseInput v-model="apnsBundleId" label="Bundle Identifier" placeholder="com.company.app" size="sm" />
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
            .p8 Private Key Contents
          </label>
          <textarea
            v-model="apnsP8Key"
            rows="5"
            placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----"
            class="w-full bg-slate-50 dark:bg-slate-950 font-mono text-xs text-slate-900 dark:text-white p-3 rounded-xl border border-slate-200 dark:border-slate-800 focus:outline-none focus:border-indigo-500"
          ></textarea>
        </div>

        <div class="flex justify-end pt-2">
          <BaseButton variant="primary" size="sm" :loading="isSavingApns" @click="handleSaveApns">
            Save APNs Key
          </BaseButton>
        </div>
      </div>
    </div>

    <!-- Web Push (VAPID) Panel -->
    <div v-if="activeTab === 'webpush'" class="space-y-4 max-w-2xl animate-in fade-in">
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
        <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
          <div>
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white">
              Web Push (VAPID Key Pair)
            </h3>
            <p class="text-[11px] text-slate-500">Voluntary Application Server Identification for browser notifications</p>
          </div>
          <span
            v-if="webpushCredential"
            class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200"
          >
            Generated
          </span>
        </div>

        <div v-if="webpushPublicKey" class="space-y-3">
          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1">
              Public VAPID Key (Include in Web Browser SDK)
            </label>
            <div class="flex items-center gap-2">
              <input
                type="text"
                readonly
                :value="webpushPublicKey"
                class="flex-1 p-2 rounded-xl font-mono text-xs bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 select-all"
              />
              <BaseButton
                variant="outline"
                size="sm"
                :icon="copied ? Check : Copy"
                @click="copyVapidKey(webpushPublicKey)"
              >
                Copy
              </BaseButton>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between pt-2">
          <BaseButton
            v-if="webpushCredential"
            variant="danger"
            size="xs"
            @click="handleDeleteCredential('webpush')"
          >
            Remove VAPID
          </BaseButton>
          <div v-else></div>

          <BaseButton
            variant="primary"
            size="sm"
            :loading="isGeneratingVapid"
            @click="handleGenerateVapid"
          >
            {{ webpushCredential ? 'Regenerate VAPID Pair' : 'Generate VAPID Key Pair' }}
          </BaseButton>
        </div>
      </div>
    </div>
  </div>
</template>
