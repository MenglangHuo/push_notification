<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { credentialsApi } from '@/api/credentials'
import { devicesApi, type DeviceSummary } from '@/api/devices'
import { notificationsApi } from '@/api/notifications'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import {
  Bell,
  Check,
  CheckCircle2,
  Copy,
  Globe,
  Radio,
  RefreshCw,
  Send,
  Sparkles,
  Terminal,
  XCircle,
} from 'lucide-vue-next'

const route = useRoute()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => (route.params.appId as string) || authStore.currentAppId || '')

const isServiceWorkerSupported = ref('serviceWorker' in navigator)
const isPushManagerSupported = ref(typeof window !== 'undefined' && 'PushManager' in window)
const isNotificationSupported = ref(typeof window !== 'undefined' && 'Notification' in window)
const notificationPermission = ref<string>(
  typeof window !== 'undefined' && 'Notification' in window ? Notification.permission : 'default',
)

const vapidPublicKey = ref('')
const isFetchingKey = ref(false)
const isSubscribing = ref(false)
const isSendingPush = ref(false)
const registeredToken = ref<string>('')
const copied = ref(false)

const pushTitle = ref('Flash Test Notification ⚡')
const pushBody = ref('This is a test web push notification from the admin testbench!')

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

    // Register dummy service worker token for testing
    const simulatedToken = `https://fcm.googleapis.com/fcm/send/simulated_${Date.now()}`
    registeredToken.value = simulatedToken

    // Register token into device registry
    await devicesApi.register(appId.value, {
      pushToken: simulatedToken,
      platform: 'WEB',
      deviceModel: navigator.userAgent.slice(0, 30),
      osVersion: navigator.platform,
      appVersion: '1.0.0',
    })

    addLog('success', 'Browser successfully registered into device pool!')
    toastStore.success('Browser Subscribed', 'Test device is now registered')
  } catch (err: any) {
    addLog('error', `Subscription failed: ${err.message}`)
  } finally {
    isSubscribing.value = false
  }
}

async function handleSendTestPush() {
  if (!registeredToken.value) {
    toastStore.warning('Subscribe First', 'Please subscribe this browser before dispatching a test push.')
    return
  }

  isSendingPush.value = true
  try {
    await notificationsApi.create(appId.value, {
      title: pushTitle.value,
      body: pushBody.value,
      priority: 'HIGH',
      targetType: 'DEVICE_IDS',
      targetPayload: {
        deviceTokens: [registeredToken.value],
      },
    })

    addLog('success', `Test push dispatched to ${registeredToken.value.slice(0, 24)}...`)
    toastStore.success('Push Dispatched', 'Test message sent to device!')

    // Show HTML5 native notification if supported
    if (Notification.permission === 'granted') {
      new Notification(pushTitle.value, {
        body: pushBody.value,
        icon: '/vite.svg',
      })
    }
  } catch (err: any) {
    addLog('error', `Push dispatch failed: ${err.message}`)
    toastStore.error('Dispatch Failed', err.message)
  } finally {
    isSendingPush.value = false
  }
}

onMounted(() => {
  addLog('info', 'Web Push Testbench initialized.')
  fetchVapidKey()
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
              <span>Web Push Testbench</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                W3C Push API
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Verify browser Service Worker registration, VAPID key pairs, and trigger live desktop push alerts
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- 2 Column Testbench Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-5">
      <!-- Left Controls (7 cols) -->
      <div class="lg:col-span-7 space-y-4">
        <!-- Diagnostic Status Card -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
          <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white pb-2 border-b border-slate-100 dark:border-slate-800">
            Browser Environment Compatibility
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
              {{ registeredToken ? 'Device registered with test token' : 'Subscribe this browser to receive live test pushes' }}
            </span>
            <BaseButton
              variant="primary"
              size="sm"
              :icon="Bell"
              :loading="isSubscribing"
              @click="requestPermissionAndSubscribe"
            >
              {{ registeredToken ? 'Re-Subscribe' : 'Subscribe Browser' }}
            </BaseButton>
          </div>
        </div>

        <!-- Push Dispatch Form -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
          <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white pb-2 border-b border-slate-100 dark:border-slate-800">
            Dispatch Test Message
          </h3>

          <BaseInput
            v-model="pushTitle"
            label="Title"
            placeholder="Test title..."
            size="sm"
          />

          <div class="space-y-1">
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
              Body Message
            </label>
            <textarea
              v-model="pushBody"
              rows="2"
              class="w-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white p-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500"
            ></textarea>
          </div>

          <div class="flex justify-end pt-2">
            <BaseButton
              variant="primary"
              size="sm"
              :icon="Send"
              :loading="isSendingPush"
              :disabled="!registeredToken"
              @click="handleSendTestPush"
            >
              Dispatch Push to Myself
            </BaseButton>
          </div>
        </div>
      </div>

      <!-- Right Column: Terminal Logs (5 cols) -->
      <div class="lg:col-span-5">
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm overflow-hidden h-full flex flex-col">
          <div class="p-3 bg-slate-50/70 dark:bg-slate-950/70 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
            <div class="flex items-center gap-2 text-xs font-bold text-slate-900 dark:text-white">
              <Terminal class="w-4 h-4 text-indigo-500" />
              <span>Activity Terminal</span>
            </div>
            <button
              type="button"
              @click="logs = []"
              class="text-[10px] text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
            >
              Clear
            </button>
          </div>

          <div class="p-3 bg-slate-950 font-mono text-xs text-slate-300 flex-1 min-h-[300px] max-h-[480px] overflow-y-auto space-y-1.5">
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
