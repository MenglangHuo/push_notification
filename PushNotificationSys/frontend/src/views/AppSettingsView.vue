<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { appsApi, type AppSummary } from '@/api/apps'
import { credentialsApi, type CredentialSummary } from '@/api/credentials'
import { devicesApi } from '@/api/devices'
import { notificationsApi } from '@/api/notifications'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  AlertCircle,
  Apple,
  ArrowLeft,
  Bell,
  BellRing,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  FileCode,
  FileText,
  Globe,
  HelpCircle,
  Info,
  Key,
  Lock,
  RefreshCw,
  Send,
  Shield,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Trash2,
  UploadCloud,
  X,
  XCircle,
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

// FCM form & file upload
const fcmJson = ref('')
const isSavingFcm = ref(false)
const isDraggingFcm = ref(false)
const fcmFileName = ref('')

// APNs form & file upload
const apnsP8Key = ref('')
const apnsKeyId = ref('')
const apnsTeamId = ref('')
const apnsBundleId = ref('')
const isSavingApns = ref(false)
const isDraggingApns = ref(false)
const apnsFileName = ref('')

// VAPID
const isGeneratingVapid = ref(false)
const copied = ref(false)

// Delete / Confirmation Modal
const showDeleteModal = ref(false)
const providerToDelete = ref<'fcm' | 'apns' | 'webpush' | null>(null)
const isDeleting = ref(false)

const fcmCredential = computed(() => credentials.value.find((c) => c.providerType === 'FCM'))
const apnsCredential = computed(() => credentials.value.find((c) => c.providerType === 'APNS'))
const webpushCredential = computed(() => credentials.value.find((c) => c.providerType === 'WEBPUSH'))
const webpushPublicKey = computed(() => {
  return (
    webpushCredential.value?.metadataMasked?.public_key ||
    (app.value as any)?.vapidPublicKey ||
    ''
  ) as string
})

// Current Browser Push Registration State
const isBrowserPushSupported = ref(
  typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window,
)
const browserPermission = ref<NotificationPermission>(
  typeof window !== 'undefined' && 'Notification' in window ? Notification.permission : 'default',
)
const isCheckingSubscription = ref(false)
const isBrowserSubscribed = ref(false)
const browserSubscriptionEndpoint = ref<string>('')
const registeredDeviceId = ref<string>('')
const isSubscribingBrowser = ref(false)
const isSendingTestPush = ref(false)

const fcmValidation = computed(() => {
  if (!fcmJson.value.trim()) return null
  try {
    const parsed = JSON.parse(fcmJson.value)
    const hasProjectId = !!parsed.project_id
    const hasPrivateKey = !!parsed.private_key
    if (hasProjectId && hasPrivateKey) {
      return {
        valid: true,
        projectId: parsed.project_id,
        clientEmail: parsed.client_email || 'service_account',
        message: `Valid Service Account JSON for project "${parsed.project_id}"`,
      }
    }
    return {
      valid: false,
      message: 'Parsed JSON is missing required fields "project_id" or "private_key"',
    }
  } catch (e: any) {
    return {
      valid: false,
      message: 'JSON syntax error: ' + (e.message || 'malformed'),
    }
  }
})

function formatFcmJson() {
  if (!fcmJson.value.trim()) return
  try {
    const parsed = JSON.parse(fcmJson.value)
    fcmJson.value = JSON.stringify(parsed, null, 2)
    toastStore.success('JSON Formatted', 'Formatted with 2-space indentation')
  } catch {
    toastStore.error('Formatting Failed', 'Ensure content is valid JSON first')
  }
}

function clearFcmForm() {
  fcmJson.value = ''
  fcmFileName.value = ''
}

function clearApnsForm() {
  apnsP8Key.value = ''
  apnsFileName.value = ''
}

function handleFcmFileUpload(file: File) {
  if (!file) return
  if (!file.name.endsWith('.json')) {
    toastStore.warning('File Type Notice', 'Recommended to upload a .json Google service account file')
  }
  fcmFileName.value = file.name
  const reader = new FileReader()
  reader.onload = (e) => {
    const content = e.target?.result as string
    if (content) {
      fcmJson.value = content
      toastStore.success('JSON Loaded', `Loaded file "${file.name}"`)
    }
  }
  reader.readAsText(file)
}

function handleFcmDrop(e: DragEvent) {
  isDraggingFcm.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) handleFcmFileUpload(file)
}

function handleFcmFileInput(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) handleFcmFileUpload(file)
}

function handleApnsFileUpload(file: File) {
  if (!file) return
  apnsFileName.value = file.name
  const match = file.name.match(/AuthKey_([A-Z0-9]{10})\.p8/i)
  if (match && match[1] && !apnsKeyId.value) {
    apnsKeyId.value = match[1]
    toastStore.info('Key ID Detected', `Extracted Key ID ${match[1]} from filename`)
  }
  const reader = new FileReader()
  reader.onload = (e) => {
    const content = e.target?.result as string
    if (content) {
      apnsP8Key.value = content
      toastStore.success('P8 Key Loaded', `Loaded file "${file.name}"`)
    }
  }
  reader.readAsText(file)
}

function handleApnsDrop(e: DragEvent) {
  isDraggingApns.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) handleApnsFileUpload(file)
}

function handleApnsFileInput(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) handleApnsFileUpload(file)
}

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
    fcmFileName.value = ''
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
    apnsFileName.value = ''
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

function promptDelete(provider: 'fcm' | 'apns' | 'webpush') {
  providerToDelete.value = provider
  showDeleteModal.value = true
}

async function confirmDeleteCredential() {
  if (!providerToDelete.value || !appId.value) return
  isDeleting.value = true
  try {
    await credentialsApi.delete(appId.value, providerToDelete.value)
    toastStore.success('Credential Removed', `${providerToDelete.value.toUpperCase()} configuration removed`)
    showDeleteModal.value = false
    providerToDelete.value = null
    await loadData()
  } catch (err: any) {
    toastStore.error('Delete Failed', err.response?.data?.detail || err.message)
  } finally {
    isDeleting.value = false
  }
}

function copyText(text: string, label: string) {
  if (!text) return
  navigator.clipboard.writeText(text)
  toastStore.success('Copied', `${label} copied to clipboard`)
}

function copyVapidKey(key: string) {
  navigator.clipboard.writeText(key)
  copied.value = true
  toastStore.success('Copied Public VAPID Key', 'Key copied to clipboard')
  setTimeout(() => (copied.value = false), 2000)
}

async function checkCurrentBrowserSubscription() {
  if (!isBrowserPushSupported.value) return
  isCheckingSubscription.value = true
  try {
    browserPermission.value = Notification.permission
    if (browserPermission.value !== 'granted') {
      isBrowserSubscribed.value = false
      return
    }

    const registration = await navigator.serviceWorker.getRegistration('/service-worker.js')
    if (!registration) {
      isBrowserSubscribed.value = false
      return
    }

    const subscription = await registration.pushManager.getSubscription()
    if (subscription) {
      isBrowserSubscribed.value = true
      browserSubscriptionEndpoint.value = subscription.endpoint
    } else {
      isBrowserSubscribed.value = false
      browserSubscriptionEndpoint.value = ''
    }
  } catch (err) {
    console.warn('Failed to check browser push subscription', err)
  } finally {
    isCheckingSubscription.value = false
  }
}

async function handleSubscribeCurrentBrowser() {
  if (!isBrowserPushSupported.value) {
    toastStore.error('Unsupported', 'This browser does not support Web Push API.')
    return
  }
  if (!webpushPublicKey.value) {
    toastStore.warning('VAPID Required', 'Please generate a VAPID key pair first.')
    return
  }
  if (!appId.value) return

  isSubscribingBrowser.value = true
  try {
    const permission = await Notification.requestPermission()
    browserPermission.value = permission
    if (permission !== 'granted') {
      toastStore.warning('Permission Denied', 'Notification permission was denied in your browser settings.')
      return
    }

    const reg = await navigator.serviceWorker.register('/service-worker.js')
    await navigator.serviceWorker.ready

    const padding = '='.repeat((4 - (webpushPublicKey.value.length % 4)) % 4)
    const base64 = (webpushPublicKey.value + padding).replace(/-/g, '+').replace(/_/g, '/')
    const rawData = window.atob(base64)
    const outputArray = new Uint8Array(rawData.length)
    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i)
    }

    const sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: outputArray as unknown as BufferSource,
    })

    const subJson = sub.toJSON()
    const endpoint = sub.endpoint
    const p256dh = subJson.keys?.p256dh
    const auth = subJson.keys?.auth

    browserSubscriptionEndpoint.value = endpoint

    const deviceRes = await devicesApi.register(appId.value, {
      pushToken: endpoint,
      platform: 'WEB',
      webAuthKey: auth,
      webP256dhKey: p256dh,
      deviceModel: navigator.userAgent.slice(0, 30),
      osVersion: navigator.platform || 'Browser',
      appVersion: '1.0.0',
      language: navigator.language || 'en',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
      tags: {
        registered_from: 'app_settings_console',
        browser: 'current_admin_browser',
      },
    })

    registeredDeviceId.value = deviceRes.id
    isBrowserSubscribed.value = true
    toastStore.success('Browser Subscribed!', 'This browser is now registered to receive push notifications.')
  } catch (err: any) {
    toastStore.error('Subscription Failed', err.message || 'Could not subscribe browser')
  } finally {
    isSubscribingBrowser.value = false
  }
}

async function handleUnsubscribeCurrentBrowser() {
  isSubscribingBrowser.value = true
  try {
    const reg = await navigator.serviceWorker.getRegistration('/service-worker.js')
    if (reg) {
      const sub = await reg.pushManager.getSubscription()
      if (sub) {
        await sub.unsubscribe()
      }
    }
    isBrowserSubscribed.value = false
    browserSubscriptionEndpoint.value = ''
    toastStore.info('Unsubscribed', 'This browser has been unsubscribed from push notifications.')
  } catch (err: any) {
    toastStore.error('Unsubscribe Failed', err.message)
  } finally {
    isSubscribingBrowser.value = false
  }
}

async function handleSendTestPushToCurrentBrowser() {
  if (!appId.value || !browserSubscriptionEndpoint.value) return
  isSendingTestPush.value = true
  try {
    await notificationsApi.create(appId.value, {
      title: 'Realtime Web Push Test ⚡',
      body: `Push notification delivered successfully to your ${navigator.platform || 'browser'}!`,
      targetType: 'DEVICE_IDS',
      targetPayload: {
        deviceTokens: [browserSubscriptionEndpoint.value],
      },
      priority: 'HIGH',
    })
    toastStore.success('Test Push Dispatched!', 'Check your desktop/browser notification center.')
  } catch (err: any) {
    toastStore.error('Dispatch Failed', err.response?.data?.detail || err.message)
  } finally {
    isSendingTestPush.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'webpush') {
    checkCurrentBrowserSubscription()
  }
})

watch(appId, () => {
  loadData()
  checkCurrentBrowserSubscription()
})

onMounted(() => {
  loadData()
  checkCurrentBrowserSubscription()
})
</script>

<template>
  <div class="space-y-6 text-left pb-16 max-w-7xl mx-auto">
    <!-- Top Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200 dark:border-slate-800">
      <div class="flex items-center gap-3">
        <button
          type="button"
          @click="router.back()"
          class="p-2.5 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 border border-slate-200/80 dark:border-slate-800 transition cursor-pointer shadow-2xs"
          title="Back to Apps"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <div class="flex items-center gap-2.5 flex-wrap">
            <h1 class="text-xl font-bold tracking-tight text-slate-900 dark:text-white">
              Provider Credentials Settings
            </h1>
            <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
              <span class="w-1.5 h-1.5 rounded-full bg-indigo-500"></span>
              {{ app?.name || appId }}
            </span>
          </div>
          <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Manage push gateway credentials for Google Android, Apple iOS, and WebPush browsers
          </p>
        </div>
      </div>

      <!-- Header Quick Actions -->
      <div class="flex items-center gap-2">
        <BaseButton
          variant="outline"
          size="sm"
          :icon="Send"
          @click="router.push(appId ? `/apps/${appId}/notifications/new` : '/notifications/new')"
        >
          Compose Push
        </BaseButton>
      </div>
    </div>

    <!-- Provider Selection Cards (3-Column Grid) -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
      <!-- 1. FCM Card -->
      <div
        @click="activeTab = 'fcm'"
        class="cursor-pointer p-4.5 rounded-2xl border transition-all duration-200 text-left relative overflow-hidden group"
        :class="activeTab === 'fcm'
          ? 'bg-white dark:bg-slate-900 border-indigo-600 dark:border-indigo-500 shadow-md ring-2 ring-indigo-500/20'
          : 'bg-white dark:bg-slate-900/60 border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs'"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-center gap-3">
            <div class="w-11 h-11 rounded-xl flex items-center justify-center bg-gradient-to-br from-amber-500/15 to-orange-500/20 text-amber-600 dark:text-amber-400 border border-amber-500/30 shrink-0">
              <Smartphone class="w-5 h-5" />
            </div>
            <div>
              <h3 class="text-sm font-bold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                Firebase FCM
              </h3>
              <p class="text-[11px] text-slate-500 dark:text-slate-400">Google Cloud HTTP v1</p>
            </div>
          </div>
          <span
            class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-bold shrink-0"
            :class="fcmCredential
              ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
              : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700'"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="fcmCredential ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'"></span>
            {{ fcmCredential ? 'Configured' : 'Not Set' }}
          </span>
        </div>
      </div>

      <!-- 2. APNs Card -->
      <div
        @click="activeTab = 'apns'"
        class="cursor-pointer p-4.5 rounded-2xl border transition-all duration-200 text-left relative overflow-hidden group"
        :class="activeTab === 'apns'
          ? 'bg-white dark:bg-slate-900 border-indigo-600 dark:border-indigo-500 shadow-md ring-2 ring-indigo-500/20'
          : 'bg-white dark:bg-slate-900/60 border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs'"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-center gap-3">
            <div class="w-11 h-11 rounded-xl flex items-center justify-center bg-gradient-to-br from-slate-500/15 to-zinc-600/20 text-slate-800 dark:text-slate-200 border border-slate-500/30 shrink-0">
              <Apple class="w-5 h-5" />
            </div>
            <div>
              <h3 class="text-sm font-bold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                Apple Push (APNs)
              </h3>
              <p class="text-[11px] text-slate-500 dark:text-slate-400">HTTP/2 .p8 Key Auth</p>
            </div>
          </div>
          <span
            class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-bold shrink-0"
            :class="apnsCredential
              ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
              : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700'"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="apnsCredential ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'"></span>
            {{ apnsCredential ? 'Configured' : 'Not Set' }}
          </span>
        </div>
      </div>

      <!-- 3. Web Push Card -->
      <div
        @click="activeTab = 'webpush'"
        class="cursor-pointer p-4.5 rounded-2xl border transition-all duration-200 text-left relative overflow-hidden group"
        :class="activeTab === 'webpush'
          ? 'bg-white dark:bg-slate-900 border-indigo-600 dark:border-indigo-500 shadow-md ring-2 ring-indigo-500/20'
          : 'bg-white dark:bg-slate-900/60 border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs'"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-center gap-3">
            <div class="w-11 h-11 rounded-xl flex items-center justify-center bg-gradient-to-br from-sky-500/15 to-indigo-500/20 text-sky-600 dark:text-sky-400 border border-sky-500/30 shrink-0">
              <Globe class="w-5 h-5" />
            </div>
            <div>
              <h3 class="text-sm font-bold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                Web Push (VAPID)
              </h3>
              <p class="text-[11px] text-slate-500 dark:text-slate-400">NIST P-256 Web Standard</p>
            </div>
          </div>
          <span
            class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-bold shrink-0"
            :class="webpushCredential
              ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
              : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700'"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="webpushCredential ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'"></span>
            {{ webpushCredential ? 'Active' : 'Not Set' }}
          </span>
        </div>
      </div>
    </div>

    <!-- Main Workspace (Two-Column Layout) -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
      <!-- Left Column: Active Configuration Form (8 Cols) -->
      <div class="lg:col-span-8 space-y-6">
        <!-- FCM PANEL -->
        <div v-if="activeTab === 'fcm'" class="bg-white dark:bg-slate-900 rounded-3xl p-6 sm:p-7 border border-slate-200 dark:border-slate-800 shadow-xs space-y-6 animate-in fade-in">
          <div class="flex items-start justify-between pb-4 border-b border-slate-100 dark:border-slate-800">
            <div>
              <div class="flex items-center gap-2">
                <h2 class="text-base font-bold text-slate-900 dark:text-white">
                  Google Firebase Cloud Messaging v1
                </h2>
                <span class="px-2 py-0.5 rounded-md text-[10px] font-bold bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200/80 dark:border-amber-500/20">
                  HTTP v1 API
                </span>
              </div>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
                Upload your Google Cloud service account JSON file with Firebase Cloud Messaging Admin privileges.
              </p>
            </div>
            <span
              v-if="fcmCredential"
              class="px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20 shrink-0"
            >
              Configured
            </span>
          </div>

          <!-- Active FCM Credential Showcase -->
          <div
            v-if="fcmCredential"
            class="p-5 rounded-2xl bg-white dark:bg-slate-900 border border-emerald-500/30 dark:border-emerald-500/20 shadow-xs space-y-4 relative overflow-hidden"
          >
            <!-- Top accent bar -->
            <div class="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-emerald-500 via-teal-500 to-emerald-400"></div>

            <!-- Card Header: Status & Revoke Action -->
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 border border-emerald-200/60 dark:border-emerald-800/50 flex items-center justify-center shrink-0 shadow-2xs">
                  <ShieldCheck class="w-5 h-5" />
                </div>
                <div class="min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-sm font-bold text-slate-900 dark:text-white truncate">
                      Google Service Account
                    </span>
                    <span class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/60">
                      <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                      Active
                    </span>
                    <span class="px-2 py-0.5 rounded-md text-[10px] font-medium bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-200/60 dark:border-slate-700/60">
                      AES-256-GCM Vault
                    </span>
                  </div>
                  <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Encrypted and ready for Firebase Cloud Messaging HTTP v1 dispatch
                  </p>
                </div>
              </div>

              <!-- Refined Subtle Revoke Button -->
              <button
                type="button"
                @click="promptDelete('fcm')"
                class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold text-rose-600 dark:text-rose-400 hover:text-rose-700 dark:hover:text-rose-300 bg-rose-50/70 hover:bg-rose-100 dark:bg-rose-950/30 dark:hover:bg-rose-900/40 border border-rose-200/80 dark:border-rose-900/50 transition-colors cursor-pointer shrink-0"
                title="Revoke and delete this Google Service Account key"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>Remove</span>
              </button>
            </div>

            <!-- Metadata Inset Grid -->
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 p-3.5 rounded-xl bg-slate-50/80 dark:bg-slate-950/60 border border-slate-200/60 dark:border-slate-800/80">
              <!-- Firebase Project -->
              <div class="min-w-0 space-y-0.5">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Firebase Project
                </span>
                <div class="flex items-center gap-1.5">
                  <span class="font-mono text-xs font-semibold text-slate-800 dark:text-slate-200 truncate" :title="fcmCredential.metadataMasked?.project_id || 'Not specified'">
                    {{ fcmCredential.metadataMasked?.project_id || '—' }}
                  </span>
                  <button
                    v-if="fcmCredential.metadataMasked?.project_id"
                    type="button"
                    @click="copyText(fcmCredential.metadataMasked.project_id, 'Project ID')"
                    class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 p-0.5 transition-colors cursor-pointer"
                    title="Copy Project ID"
                  >
                    <Copy class="w-3 h-3" />
                  </button>
                </div>
              </div>

              <!-- Service Account Email -->
              <div class="min-w-0 space-y-0.5">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Client Email
                </span>
                <div class="flex items-center gap-1.5">
                  <span class="font-mono text-xs text-slate-700 dark:text-slate-300 truncate" :title="fcmCredential.metadataMasked?.client_email || 'Service account'">
                    {{ fcmCredential.metadataMasked?.client_email || '—' }}
                  </span>
                  <button
                    v-if="fcmCredential.metadataMasked?.client_email"
                    type="button"
                    @click="copyText(fcmCredential.metadataMasked.client_email, 'Service Account Email')"
                    class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 p-0.5 transition-colors cursor-pointer"
                    title="Copy Email"
                  >
                    <Copy class="w-3 h-3" />
                  </button>
                </div>
              </div>

              <!-- Last Synchronized / Stored Date -->
              <div class="min-w-0 space-y-0.5">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Last Updated
                </span>
                <div class="text-xs font-medium text-slate-700 dark:text-slate-300">
                  {{ new Date(fcmCredential.updatedAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) }}
                </div>
              </div>
            </div>
          </div>

          <!-- Drag and Drop Dropzone -->
          <div
            class="border-2 border-dashed rounded-2xl p-6 text-center transition-all cursor-pointer relative"
            :class="isDraggingFcm
              ? 'border-indigo-500 bg-indigo-50/50 dark:bg-indigo-500/10'
              : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50/50 dark:bg-slate-950/40'"
            @dragover.prevent="isDraggingFcm = true"
            @dragleave.prevent="isDraggingFcm = false"
            @drop.prevent="handleFcmDrop"
            @click="($refs.fcmFileInput as HTMLInputElement).click()"
          >
            <input
              ref="fcmFileInput"
              type="file"
              accept=".json"
              class="hidden"
              @change="handleFcmFileInput"
            />
            <div class="flex flex-col items-center gap-2">
              <div class="w-12 h-12 rounded-2xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
                <UploadCloud class="w-6 h-6" />
              </div>
              <div>
                <p class="text-xs font-semibold text-slate-800 dark:text-slate-200">
                  <span class="text-indigo-600 dark:text-indigo-400 hover:underline">Click to browse</span> or drag and drop your <code class="font-mono text-[11px] bg-slate-200 dark:bg-slate-800 px-1.5 py-0.5 rounded">service-account.json</code>
                </p>
                <p class="text-[11px] text-slate-400 dark:text-slate-500 mt-0.5">
                  Private keys are automatically read and validated in the browser
                </p>
              </div>
              <div v-if="fcmFileName" class="mt-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20">
                <FileCode class="w-3.5 h-3.5" />
                <span>{{ fcmFileName }}</span>
              </div>
            </div>
          </div>

          <!-- Code Box with Toolbar -->
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <label class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300 flex items-center gap-2">
                <span>Service Account JSON</span>
                <span
                  v-if="fcmValidation"
                  class="text-[11px] font-medium px-2 py-0.5 rounded-md"
                  :class="fcmValidation.valid
                    ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
                    : 'bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-200 dark:border-rose-500/20'"
                >
                  {{ fcmValidation.message }}
                </span>
              </label>

              <div class="flex items-center gap-2">
                <button
                  type="button"
                  v-if="fcmJson"
                  @click="formatFcmJson"
                  class="text-xs text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1 cursor-pointer"
                >
                  <Sparkles class="w-3 h-3" />
                  Format JSON
                </button>
                <button
                  type="button"
                  v-if="fcmJson"
                  @click="clearFcmForm"
                  class="text-xs text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
                >
                  Clear
                </button>
              </div>
            </div>

            <textarea
              v-model="fcmJson"
              rows="7"
              placeholder='{&#10;  "type": "service_account",&#10;  "project_id": "your-firebase-project",&#10;  "private_key_id": "...",&#10;  "private_key": "-----BEGIN PRIVATE KEY-----\n...",&#10;  "client_email": "firebase-adminsdk@..."&#10;}'
              class="w-full bg-slate-50 dark:bg-slate-950 font-mono text-xs text-slate-900 dark:text-white p-3.5 rounded-2xl border border-slate-200 dark:border-slate-800 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all leading-relaxed"
            ></textarea>
          </div>

          <!-- Action Footer -->
          <div class="flex flex-col sm:flex-row items-center justify-between gap-3 pt-2">
            <p class="text-[11px] text-slate-400 dark:text-slate-500 flex items-center gap-1.5">
              <Lock class="w-3.5 h-3.5 text-emerald-500" />
              <span>Encrypted with AES-256-GCM before database insertion.</span>
            </p>
            <BaseButton
              variant="primary"
              size="md"
              :loading="isSavingFcm"
              :disabled="!fcmJson.trim()"
              :icon="Shield"
              @click="handleSaveFcm"
            >
              Save & Encrypt FCM Credentials
            </BaseButton>
          </div>
        </div>

        <!-- APNS PANEL -->
        <div v-if="activeTab === 'apns'" class="bg-white dark:bg-slate-900 rounded-3xl p-6 sm:p-7 border border-slate-200 dark:border-slate-800 shadow-xs space-y-6 animate-in fade-in">
          <div class="flex items-start justify-between pb-4 border-b border-slate-100 dark:border-slate-800">
            <div>
              <div class="flex items-center gap-2">
                <h2 class="text-base font-bold text-slate-900 dark:text-white">
                  Apple Push Notification service (APNs)
                </h2>
                <span class="px-2 py-0.5 rounded-md text-[10px] font-bold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700">
                  HTTP/2 Token Auth (.p8)
                </span>
              </div>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
                Configure Apple Developer token authentication with your Key ID, Team ID, Bundle Identifier, and .p8 key.
              </p>
            </div>
            <span
              v-if="apnsCredential"
              class="px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20 shrink-0"
            >
              Configured
            </span>
          </div>

          <!-- Active APNs Credential Showcase -->
          <div
            v-if="apnsCredential"
            class="p-5 rounded-2xl bg-white dark:bg-slate-900 border border-emerald-500/30 dark:border-emerald-500/20 shadow-xs space-y-4 relative overflow-hidden"
          >
            <!-- Top accent bar -->
            <div class="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-emerald-500 via-teal-500 to-emerald-400"></div>

            <!-- Card Header: Status & Revoke Action -->
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 border border-emerald-200/60 dark:border-emerald-800/50 flex items-center justify-center shrink-0 shadow-2xs">
                  <ShieldCheck class="w-5 h-5" />
                </div>
                <div class="min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-sm font-bold text-slate-900 dark:text-white truncate">
                      Apple Push Token Auth (.p8)
                    </span>
                    <span class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/60">
                      <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                      Active
                    </span>
                    <span class="px-2 py-0.5 rounded-md text-[10px] font-medium bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-200/60 dark:border-slate-700/60">
                      AES-256-GCM Vault
                    </span>
                  </div>
                  <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Encrypted Apple private key ready for APNs HTTP/2 dispatch
                  </p>
                </div>
              </div>

              <!-- Refined Subtle Revoke Button -->
              <button
                type="button"
                @click="promptDelete('apns')"
                class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold text-rose-600 dark:text-rose-400 hover:text-rose-700 dark:hover:text-rose-300 bg-rose-50/70 hover:bg-rose-100 dark:bg-rose-950/30 dark:hover:bg-rose-900/40 border border-rose-200/80 dark:border-rose-900/50 transition-colors cursor-pointer shrink-0"
                title="Revoke and delete this APNs private key"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>Remove</span>
              </button>
            </div>

            <!-- Metadata Inset Grid -->
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 p-3.5 rounded-xl bg-slate-50/80 dark:bg-slate-950/60 border border-slate-200/60 dark:border-slate-800/80">
              <!-- Key ID -->
              <div class="min-w-0 space-y-0.5">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Key ID
                </span>
                <div class="flex items-center gap-1.5">
                  <span class="font-mono text-xs font-semibold text-slate-800 dark:text-slate-200 truncate">
                    {{ apnsCredential.metadataMasked?.key_id || '—' }}
                  </span>
                  <button
                    v-if="apnsCredential.metadataMasked?.key_id"
                    type="button"
                    @click="copyText(apnsCredential.metadataMasked.key_id, 'Key ID')"
                    class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 p-0.5 transition-colors cursor-pointer"
                    title="Copy Key ID"
                  >
                    <Copy class="w-3 h-3" />
                  </button>
                </div>
              </div>

              <!-- Team ID -->
              <div class="min-w-0 space-y-0.5">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Team ID
                </span>
                <div class="flex items-center gap-1.5">
                  <span class="font-mono text-xs font-semibold text-slate-800 dark:text-slate-200 truncate">
                    {{ apnsCredential.metadataMasked?.team_id || '—' }}
                  </span>
                  <button
                    v-if="apnsCredential.metadataMasked?.team_id"
                    type="button"
                    @click="copyText(apnsCredential.metadataMasked.team_id, 'Team ID')"
                    class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 p-0.5 transition-colors cursor-pointer"
                    title="Copy Team ID"
                  >
                    <Copy class="w-3 h-3" />
                  </button>
                </div>
              </div>

              <!-- Bundle ID -->
              <div class="min-w-0 space-y-0.5">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Bundle ID
                </span>
                <div class="flex items-center gap-1.5">
                  <span class="font-mono text-xs text-slate-700 dark:text-slate-300 truncate">
                    {{ apnsCredential.metadataMasked?.bundle_id || '—' }}
                  </span>
                  <button
                    v-if="apnsCredential.metadataMasked?.bundle_id"
                    type="button"
                    @click="copyText(apnsCredential.metadataMasked.bundle_id, 'Bundle ID')"
                    class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 p-0.5 transition-colors cursor-pointer"
                    title="Copy Bundle ID"
                  >
                    <Copy class="w-3 h-3" />
                  </button>
                </div>
              </div>

              <!-- Last Synchronized / Stored Date -->
              <div class="min-w-0 space-y-0.5">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Last Updated
                </span>
                <div class="text-xs font-medium text-slate-700 dark:text-slate-300">
                  {{ new Date(apnsCredential.updatedAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) }}
                </div>
              </div>
            </div>
          </div>

          <!-- Parameter Fields Grid -->
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <BaseInput
              v-model="apnsKeyId"
              label="Key ID (10 chars)"
              placeholder="e.g. 2X994992D2"
              size="sm"
            />
            <BaseInput
              v-model="apnsTeamId"
              label="Team ID (10 chars)"
              placeholder="e.g. DEF456GHIJ"
              size="sm"
            />
            <BaseInput
              v-model="apnsBundleId"
              label="Bundle Identifier"
              placeholder="com.company.app"
              size="sm"
            />
          </div>

          <!-- Drag and Drop Dropzone for .p8 -->
          <div
            class="border-2 border-dashed rounded-2xl p-5 text-center transition-all cursor-pointer relative"
            :class="isDraggingApns
              ? 'border-indigo-500 bg-indigo-50/50 dark:bg-indigo-500/10'
              : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50/50 dark:bg-slate-950/40'"
            @dragover.prevent="isDraggingApns = true"
            @dragleave.prevent="isDraggingApns = false"
            @drop.prevent="handleApnsDrop"
            @click="($refs.apnsFileInput as HTMLInputElement).click()"
          >
            <input
              ref="apnsFileInput"
              type="file"
              accept=".p8,.txt"
              class="hidden"
              @change="handleApnsFileInput"
            />
            <div class="flex flex-col items-center gap-1.5">
              <UploadCloud class="w-5 h-5 text-indigo-500" />
              <p class="text-xs font-semibold text-slate-800 dark:text-slate-200">
                Drop your <code class="font-mono text-[11px] bg-slate-200 dark:bg-slate-800 px-1 py-0.5 rounded">AuthKey_XXXXXXXXXX.p8</code> file here
              </p>
              <div v-if="apnsFileName" class="mt-1 inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200">
                <FileText class="w-3 h-3" />
                <span>{{ apnsFileName }}</span>
              </div>
            </div>
          </div>

          <!-- Code Box for .p8 -->
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <label class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                .p8 Private Key Contents
              </label>
              <button
                type="button"
                v-if="apnsP8Key"
                @click="clearApnsForm"
                class="text-xs text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
              >
                Clear
              </button>
            </div>
            <textarea
              v-model="apnsP8Key"
              rows="5"
              placeholder="-----BEGIN PRIVATE KEY-----&#10;MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg...&#10;-----END PRIVATE KEY-----"
              class="w-full bg-slate-50 dark:bg-slate-950 font-mono text-xs text-slate-900 dark:text-white p-3.5 rounded-2xl border border-slate-200 dark:border-slate-800 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all"
            ></textarea>
          </div>

          <!-- Action Footer -->
          <div class="flex flex-col sm:flex-row items-center justify-between gap-3 pt-2">
            <p class="text-[11px] text-slate-400 dark:text-slate-500 flex items-center gap-1.5">
              <Lock class="w-3.5 h-3.5 text-emerald-500" />
              <span>APNs authentication tokens are signed in-memory using Apple ES256 algorithm.</span>
            </p>
            <BaseButton
              variant="primary"
              size="md"
              :loading="isSavingApns"
              :disabled="!apnsP8Key.trim() || !apnsKeyId.trim() || !apnsTeamId.trim() || !apnsBundleId.trim()"
              :icon="Shield"
              @click="handleSaveApns"
            >
              Save & Encrypt APNs Key
            </BaseButton>
          </div>
        </div>

        <!-- WEBPUSH (VAPID) PANEL -->
        <div v-if="activeTab === 'webpush'" class="bg-white dark:bg-slate-900 rounded-3xl p-6 sm:p-7 border border-slate-200 dark:border-slate-800 shadow-xs space-y-6 animate-in fade-in">
          <div class="flex items-start justify-between pb-4 border-b border-slate-100 dark:border-slate-800">
            <div>
              <div class="flex items-center gap-2">
                <h2 class="text-base font-bold text-slate-900 dark:text-white">
                  Web Push (VAPID Key Pair)
                </h2>
                <span class="px-2 py-0.5 rounded-md text-[10px] font-bold bg-sky-50 dark:bg-sky-500/10 text-sky-700 dark:text-sky-400 border border-sky-200 dark:border-sky-500/20">
                  RFC 8292 NIST P-256
                </span>
              </div>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
                Voluntary Application Server Identification (VAPID) allows web browsers (Chrome, Safari, Firefox, Edge) to identify your push server.
              </p>
            </div>
            <span
              v-if="webpushCredential"
              class="px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20 shrink-0"
            >
              Generated
            </span>
          </div>

          <!-- 1. Gateway VAPID Key Pair (Server Credentials) -->
          <div
            v-if="webpushCredential && webpushPublicKey"
            class="p-5 rounded-2xl bg-white dark:bg-slate-900 border border-emerald-500/30 dark:border-emerald-500/20 shadow-xs space-y-4 relative overflow-hidden"
          >
            <!-- Top accent bar -->
            <div class="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-emerald-500 via-teal-500 to-emerald-400"></div>

            <!-- Card Header: Status & Actions -->
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 border border-emerald-200/60 dark:border-emerald-800/50 flex items-center justify-center shrink-0 shadow-2xs">
                  <ShieldCheck class="w-5 h-5" />
                </div>
                <div class="min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-sm font-bold text-slate-900 dark:text-white truncate">
                      VAPID Application Server Key
                    </span>
                    <span class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/60">
                      <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                      Active Gateway
                    </span>
                    <span class="px-2 py-0.5 rounded-md text-[10px] font-medium bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-200/60 dark:border-slate-700/60">
                      RFC 8292 NIST P-256
                    </span>
                  </div>
                  <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Voluntary server identity used to sign web push messages dispatched to Chrome, Safari, Firefox, and Edge
                  </p>
                </div>
              </div>

              <!-- Action Buttons -->
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  @click="handleGenerateVapid"
                  :disabled="isGeneratingVapid"
                  class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 bg-indigo-50/70 hover:bg-indigo-100 dark:bg-indigo-950/30 dark:hover:bg-indigo-900/40 border border-indigo-200/80 dark:border-indigo-900/50 transition-colors cursor-pointer shrink-0"
                  title="Rotate and regenerate a new VAPID key pair"
                >
                  <Sparkles class="w-3.5 h-3.5" :class="{ 'animate-spin': isGeneratingVapid }" />
                  <span>Regenerate</span>
                </button>
                <button
                  type="button"
                  @click="promptDelete('webpush')"
                  class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold text-rose-600 dark:text-rose-400 hover:text-rose-700 dark:hover:text-rose-300 bg-rose-50/70 hover:bg-rose-100 dark:bg-rose-950/30 dark:hover:bg-rose-900/40 border border-rose-200/80 dark:border-rose-900/50 transition-colors cursor-pointer shrink-0"
                  title="Revoke and delete this VAPID keypair"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                  <span>Remove</span>
                </button>
              </div>
            </div>

            <!-- Public Key Box -->
            <div class="p-3.5 rounded-xl bg-slate-50/80 dark:bg-slate-950/60 border border-slate-200/60 dark:border-slate-800/80 space-y-2">
              <div class="flex items-center justify-between">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 flex items-center gap-1.5">
                  <Key class="w-3.5 h-3.5 text-indigo-500" />
                  <span>Public Application Server Key</span>
                </span>
                <span class="text-[10px] text-slate-400">Embed in client navigator.serviceWorker.ready</span>
              </div>
              <div class="flex items-center gap-2">
                <div class="flex-1 p-2.5 rounded-lg font-mono text-xs bg-white dark:bg-slate-900 border border-slate-200/60 dark:border-slate-800 text-slate-800 dark:text-slate-200 break-all select-all">
                  {{ webpushPublicKey }}
                </div>
                <button
                  type="button"
                  @click="copyVapidKey(webpushPublicKey)"
                  class="px-3 py-2 rounded-lg text-xs font-semibold bg-white dark:bg-slate-900 hover:bg-slate-50 dark:hover:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 flex items-center gap-1.5 transition-colors cursor-pointer shrink-0 shadow-2xs"
                >
                  <component :is="copied ? Check : Copy" class="w-3.5 h-3.5 text-indigo-500" />
                  <span>{{ copied ? 'Copied' : 'Copy Key' }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- Empty State (No VAPID Provisioned) -->
          <div v-else class="p-8 rounded-2xl bg-slate-50 dark:bg-slate-950 border border-dashed border-slate-300 dark:border-slate-800 text-center space-y-4">
            <div class="w-12 h-12 rounded-2xl bg-indigo-50 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 flex items-center justify-center mx-auto">
              <Globe class="w-6 h-6" />
            </div>
            <div class="max-w-md mx-auto space-y-1">
              <h3 class="text-sm font-bold text-slate-800 dark:text-slate-200">No VAPID Key Pair Provisioned</h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
                Generate a cryptographically secure NIST P-256 elliptic-curve key pair to enable browser push notifications for Chrome, Safari, Firefox, and Edge.
              </p>
            </div>
            <div>
              <BaseButton
                variant="primary"
                size="md"
                :loading="isGeneratingVapid"
                :icon="Sparkles"
                @click="handleGenerateVapid"
              >
                Generate VAPID Key Pair
              </BaseButton>
            </div>
          </div>

          <!-- 2. Current Browser Push Registration & Diagnostic Status Card -->
          <div class="p-5 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-xs space-y-4">
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-violet-50 dark:bg-violet-950/50 text-violet-600 dark:text-violet-400 border border-violet-200/60 dark:border-violet-800/50 flex items-center justify-center shrink-0 shadow-2xs">
                  <BellRing class="w-5 h-5" />
                </div>
                <div class="min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <h3 class="text-sm font-bold text-slate-900 dark:text-white">
                      Current Browser Registration
                    </h3>
                    <!-- Dynamic Registration Status Badge -->
                    <span
                      v-if="isBrowserSubscribed"
                      class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/60"
                    >
                      <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                      Subscribed & Listening
                    </span>
                    <span
                      v-else-if="browserPermission === 'denied'"
                      class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-rose-50 dark:bg-rose-950/60 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800/60"
                    >
                      <XCircle class="w-3.5 h-3.5 text-rose-500" />
                      Permission Blocked in Browser
                    </span>
                    <span
                      v-else
                      class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800/60"
                    >
                      <span class="w-2 h-2 rounded-full bg-amber-400"></span>
                      Not Subscribed Yet
                    </span>
                  </div>
                  <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Live check: identifies whether this specific browser is registered as a push recipient for this app
                  </p>
                </div>
              </div>

              <!-- Quick Refresh Diagnostic Status Button -->
              <button
                type="button"
                @click="checkCurrentBrowserSubscription"
                :disabled="isCheckingSubscription"
                class="p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 border border-slate-200/80 dark:border-slate-800 transition cursor-pointer shadow-2xs"
                title="Re-check browser subscription status"
              >
                <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': isCheckingSubscription }" />
              </button>
            </div>

            <!-- Diagnostics 3-Card Grid -->
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 p-3.5 rounded-xl bg-slate-50/80 dark:bg-slate-950/60 border border-slate-200/60 dark:border-slate-800/80">
              <!-- 1. Web Push Support -->
              <div class="space-y-1">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Push API Support
                </span>
                <div class="flex items-center gap-1.5 text-xs font-semibold" :class="isBrowserPushSupported ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-600'">
                  <CheckCircle2 v-if="isBrowserPushSupported" class="w-4 h-4 text-emerald-500 shrink-0" />
                  <XCircle v-else class="w-4 h-4 text-rose-500 shrink-0" />
                  <span>{{ isBrowserPushSupported ? 'Supported in this Browser' : 'Not Supported' }}</span>
                </div>
              </div>

              <!-- 2. System Permission -->
              <div class="space-y-1">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Notification Permission
                </span>
                <div class="flex items-center gap-1.5 text-xs font-semibold">
                  <span
                    class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px]"
                    :class="{
                      'bg-emerald-100 dark:bg-emerald-950/80 text-emerald-800 dark:text-emerald-300': browserPermission === 'granted',
                      'bg-rose-100 dark:bg-rose-950/80 text-rose-800 dark:text-rose-300': browserPermission === 'denied',
                      'bg-amber-100 dark:bg-amber-950/80 text-amber-800 dark:text-amber-300': browserPermission === 'default',
                    }"
                  >
                    {{ browserPermission === 'granted' ? 'Granted (Allowed)' : browserPermission === 'denied' ? 'Denied (Blocked)' : 'Default (Prompt Needed)' }}
                  </span>
                </div>
              </div>

              <!-- 3. Push Token / Endpoint -->
              <div class="space-y-1 min-w-0">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  Browser Push Endpoint
                </span>
                <div class="text-xs font-mono truncate" :class="isBrowserSubscribed ? 'text-slate-700 dark:text-slate-300' : 'text-slate-400'" :title="browserSubscriptionEndpoint || 'None'">
                  {{ isBrowserSubscribed ? browserSubscriptionEndpoint : 'No active subscription' }}
                </div>
              </div>
            </div>

            <!-- Action Toolbar -->
            <div class="flex items-center justify-between gap-3 pt-1 flex-wrap">
              <div class="text-xs text-slate-500 dark:text-slate-400">
                <span v-if="isBrowserSubscribed" class="text-emerald-600 dark:text-emerald-400 font-medium">
                  ✓ This browser is registered in the recipient pool and ready for push delivery.
                </span>
                <span v-else-if="browserPermission === 'denied'" class="text-rose-600 dark:text-rose-400">
                  Notifications are blocked. Click the lock/settings icon in your browser address bar to allow.
                </span>
                <span v-else>
                  Subscribe this browser with one click to test real native desktop push notifications.
                </span>
              </div>

              <div class="flex items-center gap-2">
                <!-- Unsubscribe button if subscribed -->
                <button
                  v-if="isBrowserSubscribed"
                  type="button"
                  @click="handleUnsubscribeCurrentBrowser"
                  :disabled="isSubscribingBrowser"
                  class="px-3 py-1.5 rounded-xl text-xs font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 border border-slate-200 dark:border-slate-700 transition cursor-pointer"
                >
                  Unsubscribe
                </button>

                <!-- Send Test Push Button if subscribed -->
                <BaseButton
                  v-if="isBrowserSubscribed"
                  variant="primary"
                  size="sm"
                  :loading="isSendingTestPush"
                  :icon="Send"
                  @click="handleSendTestPushToCurrentBrowser"
                >
                  Send Test Push to This Browser
                </BaseButton>

                <!-- Subscribe button if not subscribed -->
                <BaseButton
                  v-else
                  variant="primary"
                  size="sm"
                  :loading="isSubscribingBrowser"
                  :disabled="browserPermission === 'denied' || !webpushPublicKey"
                  :icon="Bell"
                  @click="handleSubscribeCurrentBrowser"
                >
                  Subscribe & Register This Browser
                </BaseButton>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: Security Vault & Step-by-Step Documentation (4 Cols) -->
      <div class="lg:col-span-4 space-y-6">
        <!-- Security Vault Card -->
        <div class="bg-gradient-to-br from-indigo-500/10 via-purple-500/5 to-transparent dark:from-indigo-500/15 dark:via-purple-500/10 p-5 rounded-3xl border border-indigo-200/80 dark:border-indigo-500/20 shadow-xs space-y-3 text-left">
          <div class="flex items-center gap-2.5 text-indigo-700 dark:text-indigo-400">
            <ShieldCheck class="w-5 h-5" />
            <h3 class="text-xs font-bold uppercase tracking-wider">Hardware-Grade Security</h3>
          </div>
          <p class="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
            All credentials are encrypted with <strong>AES-256-GCM</strong> using tenant-isolated HMAC salts before storage in PostgreSQL.
          </p>
          <ul class="text-[11px] text-slate-500 dark:text-slate-400 space-y-1.5 list-disc list-inside">
            <li>Never stored or logged in plaintext</li>
            <li>In-memory ephemeral token derivation</li>
            <li>Audit logged on creation and rotation</li>
          </ul>
        </div>

        <!-- Dynamic Context-Aware Setup Guide -->
        <div class="bg-white dark:bg-slate-900 rounded-3xl p-5 border border-slate-200 dark:border-slate-800 shadow-xs space-y-4 text-left">
          <div class="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-1.5">
              <Info class="w-4 h-4 text-indigo-500" />
              <span>Step-by-Step Setup Guide</span>
            </h3>
          </div>

          <!-- FCM Guide -->
          <div v-if="activeTab === 'fcm'" class="space-y-3 text-xs text-slate-600 dark:text-slate-300">
            <ol class="space-y-2.5 list-decimal list-inside text-xs leading-relaxed">
              <li>Open the <a href="https://console.firebase.google.com" target="_blank" class="text-indigo-600 dark:text-indigo-400 font-medium inline-flex items-center gap-0.5 hover:underline">Firebase Console <ExternalLink class="w-3 h-3" /></a></li>
              <li>Go to <strong>Project Settings > Service accounts</strong></li>
              <li>Click <strong>Generate new private key</strong> and confirm</li>
              <li>Drag the downloaded <code class="font-mono text-[11px] bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">.json</code> file into the dropzone on the left</li>
            </ol>
          </div>

          <!-- APNs Guide -->
          <div v-if="activeTab === 'apns'" class="space-y-3 text-xs text-slate-600 dark:text-slate-300">
            <ol class="space-y-2.5 list-decimal list-inside text-xs leading-relaxed">
              <li>Sign in to <a href="https://developer.apple.com/account" target="_blank" class="text-indigo-600 dark:text-indigo-400 font-medium inline-flex items-center gap-0.5 hover:underline">Apple Developer <ExternalLink class="w-3 h-3" /></a></li>
              <li>Go to <strong>Certificates, Identifiers & Profiles > Keys</strong></li>
              <li>Create a new key with <strong>Apple Push Notifications service (APNs)</strong> enabled</li>
              <li>Download the <code class="font-mono text-[11px] bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">AuthKey_*.p8</code> file and copy your 10-char Key ID</li>
              <li>Drop the file into the upload zone to auto-fill the form</li>
            </ol>
          </div>

          <!-- Web Push Guide -->
          <div v-if="activeTab === 'webpush'" class="space-y-3 text-xs text-slate-600 dark:text-slate-300">
            <ol class="space-y-2.5 list-decimal list-inside text-xs leading-relaxed">
              <li>Click <strong>Generate VAPID Key Pair</strong> above</li>
              <li>Copy the generated <strong>Public VAPID Key</strong></li>
              <li>Pass this key to your frontend service worker's <code class="font-mono text-[11px] bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">applicationServerKey</code></li>
              <li>Click <strong>Subscribe &amp; Register This Browser</strong> above or open <router-link :to="appId ? `/apps/${appId}/notifications/new` : '/notifications/new'" class="text-indigo-600 dark:text-indigo-400 font-medium hover:underline">Compose Push</router-link> to test delivery</li>
            </ol>
          </div>
        </div>

        <!-- Testing Shortcut -->
        <div class="p-5 rounded-3xl bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 space-y-3 text-left">
          <div class="flex items-center gap-2">
            <Zap class="w-4 h-4 text-amber-500" />
            <h4 class="text-xs font-bold text-slate-900 dark:text-white">Verify Push Dispatch</h4>
          </div>
          <p class="text-xs text-slate-500 dark:text-slate-400">
            Compose a campaign or dispatch a live test notification to any registered device token.
          </p>
          <BaseButton
            variant="outline"
            size="sm"
            class="w-full justify-center"
            :icon="Send"
            @click="router.push(appId ? `/apps/${appId}/notifications/new` : '/notifications/new')"
          >
            Open Compose Push
          </BaseButton>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <ModernModal
      v-model="showDeleteModal"
      title="Remove Gateway Credential"
      subtitle="Confirm certificate revocation"
      size="sm"
    >
      <div class="space-y-3 p-1 text-left">
        <div class="p-3 bg-rose-50 dark:bg-rose-500/10 border border-rose-200 dark:border-rose-500/20 rounded-xl flex items-start gap-3">
          <AlertCircle class="w-5 h-5 text-rose-600 dark:text-rose-400 shrink-0 mt-0.5" />
          <div class="text-xs text-rose-800 dark:text-rose-300 space-y-1">
            <p class="font-semibold">Are you sure you want to remove {{ providerToDelete?.toUpperCase() }} credentials?</p>
            <p class="text-[11px] leading-relaxed">
              Push notifications targeted to devices on this provider will immediately fail until new credentials are provided.
            </p>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <BaseButton variant="outline" size="sm" @click="showDeleteModal = false">
            Cancel
          </BaseButton>
          <BaseButton
            variant="danger"
            size="sm"
            :loading="isDeleting"
            @click="confirmDeleteCredential"
          >
            Confirm Delete
          </BaseButton>
        </div>
      </template>
    </ModernModal>
  </div>
</template>
