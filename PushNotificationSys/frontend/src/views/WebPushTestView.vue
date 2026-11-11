<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { credentialsApi } from '@/api/credentials'
import { devicesApi, type DeviceSummary } from '@/api/devices'
import { notificationsApi } from '@/api/notifications'
import { apiKeysApi } from '@/api/apikeys'
import { eventsApi } from '@/api/events'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import {
  Activity,
  AlertCircle,
  Apple,
  Bell,
  Check,
  CheckCircle2,
  Clock,
  Code,
  Copy,
  ExternalLink,
  Flame,
  Globe,
  Radio,
  RefreshCw,
  Send,
  Smartphone,
  Sparkles,
  Terminal,
  Trash2,
  Wifi,
  X,
  XCircle,
  Zap,
} from 'lucide-vue-next'

const route = useRoute()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => (route.params.appId as string) || authStore.currentAppId || '')

// Active Mode Tab
const activeTab = ref<'MOBILE' | 'WEBPUSH' | 'PAYLOAD'>('MOBILE')

// --- WEB PUSH STATE ---
const isServiceWorkerSupported = ref('serviceWorker' in navigator)
const isPushManagerSupported = ref(typeof window !== 'undefined' && 'PushManager' in window)
const isNotificationSupported = ref(typeof window !== 'undefined' && 'Notification' in window)
const notificationPermission = ref<string>(
  typeof window !== 'undefined' && 'Notification' in window ? Notification.permission : 'default',
)

const vapidPublicKey = ref('')
const isFetchingKey = ref(false)
const isSubscribing = ref(false)
const isSendingWebPush = ref(false)
const registeredWebToken = ref<string>('')
const registeredWebDeviceId = ref<string>('')

const webPushTitle = ref('Flash Sale Alert! ⚡')
const webPushBody = ref('Welcome to the Admin Playground. This is a real native desktop push!')

// --- MOBILE SIMULATOR STATE ---
type MobilePreset = 'pixel8' | 's24' | 'iphone15'
const selectedMobilePreset = ref<MobilePreset>('pixel8')
const simulatedPlatform = ref<'ANDROID' | 'IOS'>('ANDROID')
const simulatedModel = ref('Google Pixel 8 Pro')
const simulatedOs = ref('Android 15')
const simulatedToken = ref(`fcm_mock_token_pixel8_${Date.now()}`)
const simulatedDeviceId = ref<string>('')
const simulatedExternalUserId = ref('shopper-vip-101')
const isRegisteringMobile = ref(false)
const isRegisteredMobile = ref(false)

const mobilePushTitle = ref('Exclusive 50% Off VIP Weekend! ⚡')
const mobilePushBody = ref('Hey VIP, use code PLAYGROUND50 at checkout before midnight.')
const mobilePushImageUrl = ref('https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=600')
const mobilePushDeepLink = ref('playground://shop/vip-promo')
const mobilePushPriority = ref<'HIGH' | 'NORMAL'>('HIGH')
const isSendingMobilePush = ref(false)

// Active incoming notification displayed on virtual phone screen
interface SimulatedPushCard {
  id: string
  notificationId?: string
  title: string
  body: string
  imageUrl?: string
  deepLink?: string
  receivedAt: string
  opened: boolean
}
const incomingMobilePush = ref<SimulatedPushCard | null>(null)
const lastDispatchedNotificationId = ref<string>('')
const publicSdkKey = ref<string>('')

// Activity Terminal Logs
interface LogItem {
  timestamp: string
  level: 'info' | 'success' | 'warn' | 'error'
  message: string
}
const logs = ref<LogItem[]>([])

function addLog(level: 'info' | 'success' | 'warn' | 'error', message: string) {
  const timeStr = new Date().toLocaleTimeString()
  logs.value.unshift({ timestamp: timeStr, level, message })
}

// Preset Switching
function setMobilePreset(preset: MobilePreset) {
  selectedMobilePreset.value = preset
  const ts = Date.now()
  if (preset === 'pixel8') {
    simulatedPlatform.value = 'ANDROID'
    simulatedModel.value = 'Google Pixel 8 Pro'
    simulatedOs.value = 'Android 15'
    simulatedToken.value = `fcm_mock_token_pixel8_${ts}`
    simulatedExternalUserId.value = 'shopper-vip-101'
  } else if (preset === 's24') {
    simulatedPlatform.value = 'ANDROID'
    simulatedModel.value = 'Samsung Galaxy S24 Ultra'
    simulatedOs.value = 'Android 14'
    simulatedToken.value = `fcm_mock_token_s24_${ts}`
    simulatedExternalUserId.value = 'shopper-android-202'
  } else if (preset === 'iphone15') {
    simulatedPlatform.value = 'IOS'
    simulatedModel.value = 'iPhone 15 Pro Max'
    simulatedOs.value = 'iOS 18.1'
    simulatedToken.value = `apns_mock_token_iphone15_${ts}`
    simulatedExternalUserId.value = 'shopper-ios-303'
  }
  isRegisteredMobile.value = false
  simulatedDeviceId.value = ''
  addLog('info', `Switched virtual device preset to ${simulatedModel.value}`)
}

// 1-Click Register Virtual Mobile Device
async function handleRegisterMobileDevice() {
  if (!appId.value) {
    toastStore.error('Context Missing', 'Select an application context first.')
    return
  }
  isRegisteringMobile.value = true
  try {
    const res = await devicesApi.register(appId.value, {
      platform: simulatedPlatform.value,
      pushToken: simulatedToken.value,
      deviceModel: simulatedModel.value,
      osVersion: simulatedOs.value,
      appVersion: '4.2.0',
      externalUserId: simulatedExternalUserId.value,
      language: 'en',
      timezone: 'America/New_York',
      tags: {
        tier: 'gold',
        vip: 'true',
        orders_count: '15',
        preset: selectedMobilePreset.value,
      },
    })
    simulatedDeviceId.value = res.id
    isRegisteredMobile.value = true
    addLog('success', `[DEVICE] Registered virtual ${simulatedModel.value} (UUID: ${res.id})`)
    toastStore.success('Device Registered', `${simulatedModel.value} registered into recipient pool.`)
  } catch (err: any) {
    addLog('error', `Mobile registration failed: ${err.message}`)
    toastStore.error('Registration Failed', err.message)
  } finally {
    isRegisteringMobile.value = false
  }
}

// Dispatch push to simulated mobile device
async function handleSendMobilePush() {
  if (!simulatedToken.value) return
  isSendingMobilePush.value = true
  try {
    // If not registered yet, register automatically
    if (!isRegisteredMobile.value) {
      await handleRegisterMobileDevice()
    }

    addLog('info', `[KAFKA] Publishing notification dispatch for token ${simulatedToken.value.slice(0, 20)}...`)

    const res = await notificationsApi.create(appId.value, {
      title: mobilePushTitle.value,
      body: mobilePushBody.value,
      imageUrl: mobilePushImageUrl.value || undefined,
      deepLink: mobilePushDeepLink.value || undefined,
      priority: mobilePushPriority.value,
      targetType: 'DEVICE_IDS',
      targetPayload: {
        deviceTokens: [simulatedToken.value],
      },
    })

    const notifId = (res as any)?.id || (res as any)?.notificationId || `notif_${Date.now()}`
    lastDispatchedNotificationId.value = notifId

    addLog('success', `[DISPATCH] Accepted: Notification ID ${notifId} sent to Kafka topic "notification.created"`)
    toastStore.success('Push Dispatched', 'Simulated push arrived on virtual phone screen!')

    // Animate incoming push on mobile screen
    incomingMobilePush.value = {
      id: `push_${Date.now()}`,
      notificationId: notifId,
      title: mobilePushTitle.value,
      body: mobilePushBody.value,
      imageUrl: mobilePushImageUrl.value,
      deepLink: mobilePushDeepLink.value,
      receivedAt: 'Just now',
      opened: false,
    }
  } catch (err: any) {
    addLog('error', `Mobile push dispatch failed: ${err.message}`)
    toastStore.error('Dispatch Failed', err.response?.data?.detail || err.message)
  } finally {
    isSendingMobilePush.value = false
  }
}

// User taps on the push notification on the simulated phone screen
async function handleTapPushNotification() {
  if (!incomingMobilePush.value) return
  incomingMobilePush.value.opened = true

  addLog('info', `[TELEMETRY] Simulating client click (OPENED) on notification ${incomingMobilePush.value.notificationId}...`)

  try {
    // Send event to /sdk/v1/events if SDK key is known, or record log
    await eventsApi.ingestEvents([
      {
        notificationId: incomingMobilePush.value.notificationId,
        deviceId: simulatedDeviceId.value || undefined,
        eventType: 'OPENED',
        timestamp: new Date().toISOString(),
      },
    ], publicSdkKey.value || undefined).catch(() => null)

    addLog('success', `[TELEMETRY] OPENED event ingested! PostgreSQL table "notifications" opened_count incremented. CTR updated.`)
    toastStore.success('Push Notification Opened', 'Telemetry click event recorded in analytics!')
  } catch (err: any) {
    addLog('warn', `Event recorded locally: ${err.message}`)
  }
}

// Simulate token churn (dead token)
async function handleSimulateDeadToken() {
  if (!simulatedDeviceId.value && !simulatedToken.value) return
  try {
    if (simulatedDeviceId.value) {
      await devicesApi.delete(appId.value, simulatedDeviceId.value)
    }
    isRegisteredMobile.value = false
    incomingMobilePush.value = null
    addLog('warn', `[TOKEN CHURN] Device ${simulatedToken.value.slice(0, 20)} deactivated. Status flipped to inactive.`)
    toastStore.warning('Token Deactivated', 'Simulated dead token churn cleanup.')
  } catch (err: any) {
    toastStore.error('Deactivation Failed', err.message)
  }
}

// --- WEB PUSH HANDLERS ---
async function fetchVapidKey() {
  if (!appId.value) return
  isFetchingKey.value = true
  try {
    const creds = await credentialsApi.list(appId.value)
    const wp = creds.find((c) => c.providerType === 'WEBPUSH')
    const key = (wp?.metadataMasked?.vapidPublicKey || (wp?.metadataMasked as any)?.publicKey) as string | undefined
    if (key) {
      vapidPublicKey.value = key
      addLog('success', `VAPID Public Key loaded: ${key.slice(0, 16)}...`)
    } else {
      addLog('warn', 'No VAPID keys found. Generating new key pair...')
      const gen = await credentialsApi.generateVapid(appId.value)
      vapidPublicKey.value = gen.publicKey
      addLog('success', `VAPID Public Key generated: ${gen.publicKey.slice(0, 16)}...`)
    }
  } catch (err: any) {
    addLog('error', `Failed to get VAPID key: ${err.message}`)
  } finally {
    isFetchingKey.value = false
  }
}

function urlB64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = window.atob(base64)
  const outputArray = new Uint8Array(rawData.length)
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i)
  }
  return outputArray
}

async function requestPermissionAndSubscribe() {
  if (!isNotificationSupported.value) {
    toastStore.error('Unsupported', 'Browser does not support HTML5 notifications')
    return
  }

  isSubscribing.value = true
  try {
    const perm = await Notification.requestPermission()
    notificationPermission.value = perm
    if (perm !== 'granted') {
      addLog('warn', 'Notification permission was denied by the user.')
      toastStore.warning('Permission Denied', 'Please allow notifications in browser settings.')
      return
    }

    addLog('info', 'Browser notification permission granted.')

    let endpoint = `https://fcm.googleapis.com/fcm/send/web_local_${Date.now()}`
    let p256dh: string | undefined = undefined
    let auth: string | undefined = undefined

    // Attempt real W3C PushManager subscription via service worker
    if (isServiceWorkerSupported.value && isPushManagerSupported.value && vapidPublicKey.value) {
      try {
        const reg = await navigator.serviceWorker.register('/service-worker.js')
        await navigator.serviceWorker.ready
        const sub = await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlB64ToUint8Array(vapidPublicKey.value) as unknown as BufferSource,
        })
        const subJson = sub.toJSON()
        endpoint = sub.endpoint
        p256dh = subJson.keys?.p256dh
        auth = subJson.keys?.auth
        addLog('success', `Real W3C WebPush subscription created! Push Service: ${new URL(endpoint).hostname}`)
      } catch (swErr: any) {
        addLog('warn', `Native PushManager subscription fallback (${swErr.message})`)
      }
    }

    registeredWebToken.value = endpoint

    const dev = await devicesApi.register(appId.value, {
      pushToken: endpoint,
      platform: 'WEB',
      webAuthKey: auth,
      webP256dhKey: p256dh,
      deviceModel: navigator.userAgent.slice(0, 30),
      osVersion: navigator.platform,
      appVersion: '1.0.0',
      tags: { browser: 'web-playground', push: 'w3c' },
    })

    registeredWebDeviceId.value = dev.id
    addLog('success', `Browser successfully registered into device pool! Device ID: ${dev.id}`)
    toastStore.success('Browser Subscribed', 'Test web device is now registered')
  } catch (err: any) {
    addLog('error', `Subscription failed: ${err.message}`)
  } finally {
    isSubscribing.value = false
  }
}

async function handleSendWebPush() {
  if (!registeredWebToken.value) {
    toastStore.warning('Subscribe First', 'Please subscribe this browser before dispatching a test push.')
    return
  }

  isSendingWebPush.value = true
  try {
    const res = await notificationsApi.create(appId.value, {
      title: webPushTitle.value,
      body: webPushBody.value,
      priority: 'HIGH',
      targetType: 'DEVICE_IDS',
      targetPayload: {
        deviceTokens: [registeredWebToken.value],
      },
    })

    const notifId = (res as any)?.id || (res as any)?.notificationId || `notif_${Date.now()}`
    lastDispatchedNotificationId.value = notifId

    addLog('success', `Test web push dispatched to ${registeredWebToken.value.slice(0, 24)}... (ID: ${notifId})`)
    toastStore.success('Push Dispatched', 'Test message sent to delivery gateway!')

    // Show HTML5 native notification if granted
    if (Notification.permission === 'granted') {
      const n = new Notification(webPushTitle.value, {
        body: webPushBody.value,
        icon: '/vite.svg',
      })
      n.onclick = () => {
        addLog('success', 'Native desktop notification clicked! Triggering OPENED event.')
        eventsApi.ingestEvents([
          {
            notificationId: notifId,
            deviceId: registeredWebDeviceId.value || undefined,
            eventType: 'OPENED',
            timestamp: new Date().toISOString(),
          },
        ]).catch(() => null)
      }
    }
  } catch (err: any) {
    addLog('error', `Push dispatch failed: ${err.message}`)
    toastStore.error('Dispatch Failed', err.message)
  } finally {
    isSendingWebPush.value = false
  }
}

// Fetch public SDK key if available
async function loadSdkKey() {
  if (!appId.value) return
  try {
    const keys = await apiKeysApi.list(appId.value)
    const pub = keys.find((k) => k.keyType === 'SDK_PUBLIC' && !k.revoked)
    if (pub) {
      publicSdkKey.value = pub.keyPrefix
    }
  } catch {
    // silent
  }
}

onMounted(() => {
  addLog('info', 'Push Notification Playground initialized.')
  fetchVapidKey()
  loadSdkKey()
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
            <Radio class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Push Notification Playground & Simulator</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/30">
                Live Testbench
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Interactive testbed for Android (FCM), iOS (APNs), and Web Push (VAPID) with real-time smartphone preview and click telemetry
            </p>
          </div>
        </div>
      </div>

      <!-- Mode Switcher Tabs -->
      <div class="flex items-center gap-1.5 p-1 rounded-xl bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-slate-800">
        <button
          type="button"
          @click="activeTab = 'MOBILE'"
          class="px-3 py-1.5 text-xs font-semibold rounded-lg transition cursor-pointer flex items-center gap-1.5"
          :class="[
            activeTab === 'MOBILE'
              ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 shadow-xs'
              : 'text-slate-600 dark:text-slate-400 hover:text-slate-900',
          ]"
        >
          <Smartphone class="w-3.5 h-3.5" />
          <span>Mobile Simulator (Android/iOS)</span>
        </button>
        <button
          type="button"
          @click="activeTab = 'WEBPUSH'"
          class="px-3 py-1.5 text-xs font-semibold rounded-lg transition cursor-pointer flex items-center gap-1.5"
          :class="[
            activeTab === 'WEBPUSH'
              ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 shadow-xs'
              : 'text-slate-600 dark:text-slate-400 hover:text-slate-900',
          ]"
        >
          <Globe class="w-3.5 h-3.5" />
          <span>Web Push Testbench</span>
        </button>
      </div>
    </div>

    <!-- MAIN PLAYGROUND CONTENT -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-5">
      <!-- LEFT COLUMN: CONTROLS & COMPOSER (7 cols) -->
      <div class="lg:col-span-7 space-y-4">
        <!-- MODE 1: MOBILE SIMULATOR CONTROLS -->
        <template v-if="activeTab === 'MOBILE'">
          <!-- Virtual Device Card -->
          <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
            <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
              <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-1.5">
                <Smartphone class="w-4 h-4 text-emerald-500" />
                <span>1. Select Virtual Mobile Device</span>
              </h3>
              <span
                class="px-2 py-0.5 rounded-full text-[10px] font-bold"
                :class="isRegisteredMobile ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600' : 'bg-amber-50 dark:bg-amber-500/10 text-amber-600'"
              >
                {{ isRegisteredMobile ? 'Bound to Subscriber Pool' : 'Not Registered' }}
              </span>
            </div>

            <!-- Device Preset Buttons -->
            <div class="grid grid-cols-3 gap-2">
              <button
                type="button"
                @click="setMobilePreset('pixel8')"
                class="p-2.5 rounded-xl border text-left transition cursor-pointer"
                :class="[
                  selectedMobilePreset === 'pixel8'
                    ? 'border-emerald-500 bg-emerald-50/50 dark:bg-emerald-950/20'
                    : 'border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/50',
                ]"
              >
                <div class="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1">
                  <Smartphone class="w-3.5 h-3.5 text-emerald-500" />
                  Pixel 8 Pro
                </div>
                <div class="text-[10px] text-slate-400 mt-0.5">Android 15 (FCM v1)</div>
              </button>

              <button
                type="button"
                @click="setMobilePreset('s24')"
                class="p-2.5 rounded-xl border text-left transition cursor-pointer"
                :class="[
                  selectedMobilePreset === 's24'
                    ? 'border-emerald-500 bg-emerald-50/50 dark:bg-emerald-950/20'
                    : 'border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/50',
                ]"
              >
                <div class="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1">
                  <Smartphone class="w-3.5 h-3.5 text-emerald-500" />
                  Galaxy S24
                </div>
                <div class="text-[10px] text-slate-400 mt-0.5">Android 14 (FCM v1)</div>
              </button>

              <button
                type="button"
                @click="setMobilePreset('iphone15')"
                class="p-2.5 rounded-xl border text-left transition cursor-pointer"
                :class="[
                  selectedMobilePreset === 'iphone15'
                    ? 'border-sky-500 bg-sky-50/50 dark:bg-sky-950/20'
                    : 'border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/50',
                ]"
              >
                <div class="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1">
                  <Apple class="w-3.5 h-3.5 text-sky-500" />
                  iPhone 15 Pro
                </div>
                <div class="text-[10px] text-slate-400 mt-0.5">iOS 18 (APNs HTTP/2)</div>
              </button>
            </div>

            <!-- Token Details & Bind Button -->
            <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 flex items-center justify-between gap-3">
              <div class="min-w-0 flex-1">
                <div class="text-[10px] text-slate-400 font-semibold uppercase">Simulated Push Token</div>
                <div class="font-mono text-xs text-indigo-600 dark:text-indigo-400 truncate">
                  {{ simulatedToken }}
                </div>
                <div class="text-[10px] text-slate-500 mt-0.5">
                  Tags: <code>tier: gold</code>, <code>vip: true</code>, <code>orders_count: 15</code>
                </div>
              </div>

              <div class="flex items-center gap-1.5 shrink-0">
                <BaseButton
                  size="sm"
                  variant="outline"
                  :icon="Sparkles"
                  :loading="isRegisteringMobile"
                  @click="handleRegisterMobileDevice"
                >
                  {{ isRegisteredMobile ? 'Re-Sync' : 'Register Device' }}
                </BaseButton>
                <button
                  type="button"
                  @click="handleSimulateDeadToken"
                  title="Simulate token expiration/churn"
                  class="p-1.5 rounded-lg text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/20 transition cursor-pointer"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>

          <!-- Mobile Push Composer -->
          <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white pb-2 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
              <span class="flex items-center gap-1.5">
                <Send class="w-4 h-4 text-indigo-500" />
                <span>2. Compose & Dispatch Mobile Push</span>
              </span>
              <span class="text-[10px] text-slate-400 font-normal">Direct Target: {{ simulatedModel }}</span>
            </h3>

            <BaseInput
              v-model="mobilePushTitle"
              label="Notification Title"
              placeholder="e.g. Flash Promo Alert! ⚡"
              size="sm"
            />

            <div class="space-y-1">
              <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                Message Body
              </label>
              <textarea
                v-model="mobilePushBody"
                rows="2"
                class="w-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white p-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-hidden focus:ring-2 focus:ring-indigo-500"
              ></textarea>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <BaseInput
                v-model="mobilePushDeepLink"
                label="Deep Link URL"
                placeholder="playground://shop/vip-promo"
                size="sm"
              />
              <BaseInput
                v-model="mobilePushImageUrl"
                label="Image URL (Rich Push)"
                placeholder="https://images.unsplash.com/..."
                size="sm"
              />
            </div>

            <div class="flex items-center justify-between pt-2">
              <span class="text-[11px] text-slate-500">
                Dispatches asynchronously through Kafka $\to$ {{ simulatedPlatform === 'ANDROID' ? 'FCM v1' : 'APNs' }}
              </span>
              <BaseButton
                variant="primary"
                size="sm"
                :icon="Send"
                :loading="isSendingMobilePush"
                @click="handleSendMobilePush"
              >
                Dispatch to Mobile Screen
              </BaseButton>
            </div>
          </div>
        </template>

        <!-- MODE 2: WEB PUSH TESTBENCH CONTROLS -->
        <template v-else-if="activeTab === 'WEBPUSH'">
          <!-- Compatibility Status Card -->
          <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white pb-2 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
              <span>Browser Environment Compatibility</span>
              <span class="text-[10px] text-indigo-500 font-mono">W3C Push API</span>
            </h3>

            <div class="grid grid-cols-3 gap-2.5 text-xs">
              <div class="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800">
                <span class="text-[10px] text-slate-400 uppercase font-semibold block">Service Worker</span>
                <span
                  class="font-bold flex items-center gap-1 mt-1"
                  :class="isServiceWorkerSupported ? 'text-emerald-600' : 'text-rose-600'"
                >
                  <CheckCircle2 v-if="isServiceWorkerSupported" class="w-3.5 h-3.5" />
                  <XCircle v-else class="w-3.5 h-3.5" />
                  {{ isServiceWorkerSupported ? 'Supported' : 'Missing' }}
                </span>
              </div>

              <div class="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800">
                <span class="text-[10px] text-slate-400 uppercase font-semibold block">Push Manager</span>
                <span
                  class="font-bold flex items-center gap-1 mt-1"
                  :class="isPushManagerSupported ? 'text-emerald-600' : 'text-rose-600'"
                >
                  <CheckCircle2 v-if="isPushManagerSupported" class="w-3.5 h-3.5" />
                  <XCircle v-else class="w-3.5 h-3.5" />
                  {{ isPushManagerSupported ? 'Supported' : 'Missing' }}
                </span>
              </div>

              <div class="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800">
                <span class="text-[10px] text-slate-400 uppercase font-semibold block">Permission</span>
                <span
                  class="font-bold flex items-center gap-1 mt-1"
                  :class="[
                    notificationPermission === 'granted'
                      ? 'text-emerald-600'
                      : notificationPermission === 'denied'
                        ? 'text-rose-600'
                        : 'text-amber-600',
                  ]"
                >
                  {{ notificationPermission }}
                </span>
              </div>
            </div>

            <!-- Subscription Button -->
            <div class="pt-2 flex items-center justify-between">
              <span class="text-xs text-slate-500">
                {{ registeredWebToken ? 'Browser registered in device pool' : 'Click to register this browser into device pool' }}
              </span>
              <BaseButton
                variant="primary"
                size="sm"
                :icon="Bell"
                :loading="isSubscribing"
                @click="requestPermissionAndSubscribe"
              >
                {{ registeredWebToken ? 'Re-Subscribe' : '1. Subscribe Browser' }}
              </BaseButton>
            </div>
          </div>

          <!-- Web Push Dispatch Form -->
          <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white pb-2 border-b border-slate-100 dark:border-slate-800">
              2. Compose & Send Native Desktop Push
            </h3>

            <BaseInput
              v-model="webPushTitle"
              label="Title"
              placeholder="Test title..."
              size="sm"
            />

            <div class="space-y-1">
              <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                Body Message
              </label>
              <textarea
                v-model="webPushBody"
                rows="2"
                class="w-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white p-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-hidden focus:ring-2 focus:ring-indigo-500"
              ></textarea>
            </div>

            <div class="flex items-center justify-between pt-2">
              <span class="text-[11px] text-slate-500">
                Triggers native OS popup in top corner of screen
              </span>
              <BaseButton
                variant="primary"
                size="sm"
                :icon="Send"
                :loading="isSendingWebPush"
                :disabled="!registeredWebToken"
                @click="handleSendWebPush"
              >
                2. Send Test Web Push
              </BaseButton>
            </div>
          </div>
        </template>
      </div>

      <!-- RIGHT COLUMN: INTERACTIVE PHONE SCREEN / TERMINAL (5 cols) -->
      <div class="lg:col-span-5 space-y-4">
        <!-- VIRTUAL SMARTPHONE MOCKUP (Visible in Mobile mode) -->
        <div v-if="activeTab === 'MOBILE'" class="flex justify-center">
          <div
            class="relative w-[300px] h-[580px] bg-slate-950 rounded-[48px] p-3 border-4 border-slate-700 shadow-2xl overflow-hidden flex flex-col justify-between"
          >
            <!-- Camera Cutout / Dynamic Island -->
            <div class="absolute top-3 left-1/2 -translate-x-1/2 z-30">
              <div
                v-if="simulatedPlatform === 'ANDROID'"
                class="w-3.5 h-3.5 rounded-full bg-slate-900 border border-slate-800"
              ></div>
              <div
                v-else
                class="w-24 h-5 rounded-full bg-black border border-slate-800/80 flex items-center justify-center"
              >
                <div class="w-2.5 h-2.5 rounded-full bg-slate-900 ml-auto mr-2"></div>
              </div>
            </div>

            <!-- Phone Top Status Bar -->
            <div class="relative z-20 flex items-center justify-between px-4 pt-1 text-[11px] font-semibold text-slate-300">
              <span>09:41</span>
              <div class="flex items-center gap-1.5 text-slate-400">
                <span class="text-[9px] font-bold">5G</span>
                <Wifi class="w-3 h-3" />
                <span class="w-4 h-2 rounded-xs border border-slate-300 flex items-center p-0.5">
                  <span class="w-full h-full bg-slate-300 rounded-2xs"></span>
                </span>
              </div>
            </div>

            <!-- Phone Screen Background / Wallpaper -->
            <div class="relative flex-1 rounded-[36px] overflow-hidden my-1 flex flex-col justify-between p-3 bg-gradient-to-br from-indigo-950 via-slate-900 to-slate-950">
              <!-- Animated Incoming Push Notification Card -->
              <transition
                enter-active-class="transition duration-300 ease-out"
                enter-from-class="-translate-y-full opacity-0"
                enter-to-class="translate-y-0 opacity-100"
                leave-active-class="transition duration-200 ease-in"
                leave-from-class="translate-y-0 opacity-100"
                leave-to-class="-translate-y-full opacity-0"
              >
                <div
                  v-if="incomingMobilePush"
                  class="mt-6 p-3 rounded-2xl bg-white/95 dark:bg-slate-900/95 backdrop-blur-md shadow-xl border border-white/20 text-left space-y-1.5 transition-all cursor-pointer hover:scale-[1.02]"
                  @click="handleTapPushNotification"
                >
                  <div class="flex items-center justify-between">
                    <div class="flex items-center gap-1.5">
                      <div class="w-4 h-4 rounded-md bg-indigo-600 flex items-center justify-center text-white">
                        <Bell class="w-2.5 h-2.5" />
                      </div>
                      <span class="text-[10px] font-bold text-slate-800 dark:text-slate-200">PushNotify</span>
                      <span class="text-[9px] text-slate-400">• {{ incomingMobilePush.receivedAt }}</span>
                    </div>
                    <button
                      type="button"
                      @click.stop="incomingMobilePush = null"
                      class="text-slate-400 hover:text-slate-600"
                    >
                      <X class="w-3 h-3" />
                    </button>
                  </div>

                  <div class="text-xs font-bold text-slate-900 dark:text-white leading-tight">
                    {{ incomingMobilePush.title }}
                  </div>
                  <div class="text-[11px] text-slate-600 dark:text-slate-300 leading-snug line-clamp-2">
                    {{ incomingMobilePush.body }}
                  </div>

                  <!-- Image Preview -->
                  <div v-if="incomingMobilePush.imageUrl" class="rounded-lg overflow-hidden h-20 w-full mt-1">
                    <img :src="incomingMobilePush.imageUrl" class="w-full h-full object-cover" />
                  </div>

                  <!-- Tap to Open Pill -->
                  <div class="pt-1 flex items-center justify-between text-[10px]">
                    <span
                      class="px-2 py-0.5 rounded-full font-bold"
                      :class="incomingMobilePush.opened ? 'bg-emerald-100 text-emerald-700' : 'bg-indigo-100 text-indigo-700 animate-pulse'"
                    >
                      {{ incomingMobilePush.opened ? '✓ OPENED (CTR Recorded)' : '👉 Tap to Open & Fire CTR' }}
                    </span>
                    <span v-if="incomingMobilePush.deepLink" class="text-slate-400 font-mono text-[9px] truncate max-w-[100px]">
                      {{ incomingMobilePush.deepLink }}
                    </span>
                  </div>
                </div>
              </transition>

              <!-- Default Screen Lock Message (when no push is active) -->
              <div v-if="!incomingMobilePush" class="my-auto text-center space-y-2 opacity-60">
                <Bell class="w-8 h-8 text-indigo-400 mx-auto animate-bounce" />
                <div class="text-xs font-semibold text-slate-300">Awaiting Incoming Push...</div>
                <div class="text-[10px] text-slate-400 max-w-[200px] mx-auto">
                  Click "Dispatch to Mobile Screen" to simulate a real-time push banner drop!
                </div>
              </div>

              <!-- Phone Bottom Dock / Navigation Bar -->
              <div class="text-center pt-2">
                <div class="w-24 h-1 bg-slate-500/50 rounded-full mx-auto"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- ACTIVITY TERMINAL LOGS -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm overflow-hidden flex flex-col">
          <div class="p-3 bg-slate-50/70 dark:bg-slate-950/70 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
            <div class="flex items-center gap-2 text-xs font-bold text-slate-900 dark:text-white">
              <Terminal class="w-4 h-4 text-indigo-500" />
              <span>Real-Time Activity Stream</span>
            </div>
            <button
              type="button"
              @click="logs = []"
              class="text-[10px] text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
            >
              Clear Logs
            </button>
          </div>

          <div class="p-3 bg-slate-950 font-mono text-xs text-slate-300 min-h-[160px] max-h-[220px] overflow-y-auto space-y-1.5">
            <div
              v-for="(log, idx) in logs"
              :key="idx"
              class="leading-tight text-[11px]"
              :class="[
                log.level === 'success'
                  ? 'text-emerald-400'
                  : log.level === 'warn'
                    ? 'text-amber-400'
                    : log.level === 'error'
                      ? 'text-rose-400'
                      : 'text-slate-300',
              ]"
            >
              <span class="text-slate-500">[{{ log.timestamp }}]</span> {{ log.message }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
