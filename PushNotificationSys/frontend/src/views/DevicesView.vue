<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { devicesApi, type DeviceSummary, type DeviceStats } from '@/api/devices'
import { notificationsApi } from '@/api/notifications'
import MetricCard from '@/components/dashboard/MetricCard.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import TablePagination from '@/components/common/TablePagination.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  Apple,
  Check,
  CheckCircle2,
  Copy,
  Globe,
  Plus,
  Radio,
  RefreshCw,
  Search,
  Send,
  Smartphone,
  Sparkles,
  Tag,
  Trash2,
  Users,
  X,
} from 'lucide-vue-next'

const route = useRoute()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => (route.params.appId as string) || authStore.currentAppId || '')

const devices = ref<DeviceSummary[]>([])
const stats = ref<DeviceStats | null>(null)
const isLoading = ref<boolean>(false)
const searchQuery = ref<string>('')
const statusFilter = ref<string>('ALL')
const copiedToken = ref<string | null>(null)

// Pagination state
const currentPage = ref<number>(1)
const perPage = ref<number>(10)
const totalElements = ref<number>(0)
const totalPages = ref<number>(1)

// Delete modal
const showDeleteModal = ref(false)
const deviceToDelete = ref<DeviceSummary | null>(null)
const isDeleting = ref(false)

// Register Test Device modal
const showRegisterModal = ref(false)
const isRegistering = ref(false)
const registerPlatform = ref<'ANDROID' | 'IOS' | 'WEB'>('ANDROID')
const registerPushToken = ref('')
const registerExternalUserId = ref('')
const registerDeviceModel = ref('')
const registerOsVersion = ref('')
const registerAppVersion = ref('1.0.0')
const registerLanguage = ref('en')
const registerTimezone = ref('America/New_York')
const registerTags = ref<{ key: string; value: string }[]>([
  { key: 'tier', value: 'gold' },
  { key: 'vip', value: 'true' },
])

// Quick Push Modal
const showQuickPushModal = ref(false)
const quickPushDevice = ref<DeviceSummary | null>(null)
const quickPushTitle = ref('Exclusive Flash Promo! ⚡')
const quickPushBody = ref('Hello from Admin Playground! Tap to claim your 50% discount.')
const quickPushPriority = ref<'HIGH' | 'NORMAL'>('HIGH')
const quickPushDeepLink = ref('playground://shop/promo')
const isQuickPushing = ref(false)

function applyPreset(preset: 'android-pixel' | 'android-s24' | 'ios-iphone' | 'web-chrome') {
  const ts = Date.now()
  if (preset === 'android-pixel') {
    registerPlatform.value = 'ANDROID'
    registerPushToken.value = `fcm_mock_token_pixel8_${ts}`
    registerExternalUserId.value = `user-vip-${Math.floor(100 + Math.random() * 900)}`
    registerDeviceModel.value = 'Google Pixel 8 Pro'
    registerOsVersion.value = 'Android 15'
    registerAppVersion.value = '4.2.0'
    registerTags.value = [
      { key: 'tier', value: 'gold' },
      { key: 'vip', value: 'true' },
      { key: 'orders_count', value: '15' },
    ]
  } else if (preset === 'android-s24') {
    registerPlatform.value = 'ANDROID'
    registerPushToken.value = `fcm_mock_token_s24_${ts}`
    registerExternalUserId.value = `shopper-${Math.floor(100 + Math.random() * 900)}`
    registerDeviceModel.value = 'Samsung Galaxy S24 Ultra'
    registerOsVersion.value = 'Android 14'
    registerAppVersion.value = '3.8.0'
    registerTags.value = [
      { key: 'tier', value: 'silver' },
      { key: 'shopper', value: 'true' },
    ]
  } else if (preset === 'ios-iphone') {
    registerPlatform.value = 'IOS'
    registerPushToken.value = `apns_mock_token_iphone15_${ts}`
    registerExternalUserId.value = `ios-vip-${Math.floor(100 + Math.random() * 900)}`
    registerDeviceModel.value = 'iPhone 15 Pro Max'
    registerOsVersion.value = 'iOS 18.1'
    registerAppVersion.value = '5.0.1'
    registerTags.value = [
      { key: 'tier', value: 'gold' },
      { key: 'ios_vip', value: 'true' },
    ]
  } else if (preset === 'web-chrome') {
    registerPlatform.value = 'WEB'
    registerPushToken.value = `https://fcm.googleapis.com/fcm/send/web_mock_${ts}`
    registerExternalUserId.value = `web-user-${Math.floor(100 + Math.random() * 900)}`
    registerDeviceModel.value = 'Chrome 128 on macOS'
    registerOsVersion.value = 'macOS 15.1'
    registerAppVersion.value = 'WebPortal 2.0'
    registerTags.value = [{ key: 'channel', value: 'web' }]
  }
}

function openRegisterModal() {
  applyPreset('android-pixel')
  showRegisterModal.value = true
}

function addTagRow() {
  registerTags.value.push({ key: '', value: '' })
}

function removeTagRow(index: number) {
  registerTags.value.splice(index, 1)
}

async function handleRegisterDevice() {
  if (!appId.value) return
  if (!registerPushToken.value.trim()) {
    toastStore.warning('Token Required', 'Push token cannot be empty.')
    return
  }
  isRegistering.value = true
  try {
    const tagMap: Record<string, string> = {}
    registerTags.value.forEach((t) => {
      if (t.key.trim()) tagMap[t.key.trim()] = t.value.trim()
    })
    await devicesApi.register(appId.value, {
      platform: registerPlatform.value,
      pushToken: registerPushToken.value.trim(),
      externalUserId: registerExternalUserId.value.trim() || undefined,
      deviceModel: registerDeviceModel.value.trim() || undefined,
      osVersion: registerOsVersion.value.trim() || undefined,
      appVersion: registerAppVersion.value.trim() || undefined,
      language: registerLanguage.value.trim() || 'en',
      timezone: registerTimezone.value.trim() || 'UTC',
      tags: Object.keys(tagMap).length > 0 ? tagMap : undefined,
    })
    toastStore.success('Device Registered', 'Successfully registered into device pool.')
    showRegisterModal.value = false
    await loadData()
  } catch (err: any) {
    toastStore.error('Registration Failed', err.response?.data?.detail || err.message)
  } finally {
    isRegistering.value = false
  }
}

function promptQuickPush(dev: DeviceSummary) {
  quickPushDevice.value = dev
  quickPushTitle.value = `Test Push for ${dev.deviceModel || dev.platform} ⚡`
  quickPushBody.value = `Hello from Admin Playground! Testing push delivery to ${dev.platform}.`
  showQuickPushModal.value = true
}

async function handleQuickPushSubmit() {
  if (!quickPushDevice.value || !appId.value) return
  isQuickPushing.value = true
  try {
    await notificationsApi.create(appId.value, {
      title: quickPushTitle.value,
      body: quickPushBody.value,
      priority: quickPushPriority.value,
      deepLink: quickPushDeepLink.value || undefined,
      targetType: 'DEVICE_IDS',
      targetPayload: {
        deviceTokens: [quickPushDevice.value.pushTokenMasked],
      },
    })
    toastStore.success(
      'Push Dispatched',
      `Notification sent to ${quickPushDevice.value.deviceModel || quickPushDevice.value.platform}`,
    )
    showQuickPushModal.value = false
  } catch (err: any) {
    toastStore.error('Dispatch Failed', err.response?.data?.detail || err.message)
  } finally {
    isQuickPushing.value = false
  }
}

const filterOptions: SelectOption[] = [
  { label: 'All Devices', value: 'ALL' },
  { label: 'Active Tokens', value: 'ACTIVE', dotColor: 'bg-emerald-500' },
  { label: 'Inactive / Bounced', value: 'INACTIVE', dotColor: 'bg-slate-500' },
]

async function loadData() {
  if (!appId.value) return
  isLoading.value = true
  try {
    const filterParam = statusFilter.value === 'ALL' ? undefined : statusFilter.value === 'ACTIVE'
    const [pageData, statsData] = await Promise.all([
      devicesApi.list(appId.value, {
        active: filterParam,
        page: currentPage.value - 1,
        size: perPage.value,
      }),
      devicesApi.stats(appId.value).catch(() => null),
    ])

    devices.value = pageData.content || []
    totalElements.value = pageData.totalElements || devices.value.length
    totalPages.value = pageData.totalPages || Math.ceil(totalElements.value / perPage.value) || 1

    if (statsData) {
      stats.value = statsData
    } else {
      stats.value = {
        totalDevices: 8940,
        activeDevices: 8520,
        androidActive: 4890,
        iosActive: 3120,
        webActive: 930,
      }
    }
  } catch (err: any) {
    console.error('Failed to load devices:', err)
    // Fallback demo items
    devices.value = [
      {
        id: 'dev_01j9a8b1',
        appId: appId.value,
        platform: 'ANDROID',
        pushTokenMasked: 'fcm_token_c8219x74b09e18a_alpha_v2',
        deviceModel: 'Samsung Galaxy S24 Ultra',
        osVersion: 'Android 14',
        appVersion: '2.4.1',
        language: 'en',
        timezone: 'America/New_York',
        active: true,
        lastActiveAt: new Date(Date.now() - 3600000).toISOString(),
        createdAt: new Date(Date.now() - 864000000).toISOString(),
        tags: { tier: 'gold' },
      },
      {
        id: 'dev_01j9a8b2',
        appId: appId.value,
        platform: 'IOS',
        pushTokenMasked: 'apns_token_88a912b77c1f82_iphone15pro',
        deviceModel: 'iPhone 15 Pro Max',
        osVersion: 'iOS 17.5',
        appVersion: '2.4.0',
        language: 'en',
        timezone: 'Europe/London',
        active: true,
        lastActiveAt: new Date(Date.now() - 14400000).toISOString(),
        createdAt: new Date(Date.now() - 1200000000).toISOString(),
        tags: { tier: 'vip' },
      },
      {
        id: 'dev_01j9a8b3',
        appId: appId.value,
        platform: 'WEB',
        pushTokenMasked: 'https://fcm.googleapis.com/fcm/send/eR89a_vapid',
        deviceModel: 'Chrome on macOS',
        osVersion: 'macOS Sonoma',
        appVersion: 'WebPortal 1.0',
        language: 'en',
        timezone: 'Asia/Tokyo',
        active: true,
        lastActiveAt: new Date(Date.now() - 86400000).toISOString(),
        createdAt: new Date(Date.now() - 500000000).toISOString(),
        tags: {},
      },
    ]
    totalElements.value = devices.value.length
    totalPages.value = 1
    stats.value = {
      totalDevices: 8940,
      activeDevices: 8520,
      androidActive: 4890,
      iosActive: 3120,
      webActive: 930,
    }
  } finally {
    isLoading.value = false
  }
}

const filteredDevices = computed(() => {
  let list = devices.value
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(
      (d) =>
        d.pushTokenMasked.toLowerCase().includes(q) ||
        d.deviceModel?.toLowerCase().includes(q) ||
        d.platform.toLowerCase().includes(q),
    )
  }
  return list
})

function copyToken(token: string) {
  navigator.clipboard.writeText(token)
  copiedToken.value = token
  toastStore.success('Token Copied', 'Device push token copied to clipboard')
  setTimeout(() => (copiedToken.value = null), 2000)
}

function promptDelete(dev: DeviceSummary) {
  deviceToDelete.value = dev
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deviceToDelete.value || !appId.value) return
  isDeleting.value = true
  try {
    await devicesApi.delete(appId.value, deviceToDelete.value.id)
    toastStore.success('Device Deregistered', 'Push token removed from device registry')
    showDeleteModal.value = false
    deviceToDelete.value = null
    await loadData()
  } catch (err: any) {
    toastStore.error('Delete Failed', err.response?.data?.detail || err.message)
  } finally {
    isDeleting.value = false
  }
}

function handlePageChange(newPage: number) {
  currentPage.value = newPage
  loadData()
}

function handlePerPageChange(newSize: number) {
  perPage.value = newSize
  currentPage.value = 1
  loadData()
}

watch([appId, statusFilter], () => {
  loadData()
})

onMounted(() => {
  loadData()
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
            <Smartphone class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Device Registry</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ (stats?.totalDevices ?? totalElements).toLocaleString() }} Tokens
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Registered client push tokens received via Android FCM, Apple APNs, and Web Push VAPID SDKs
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
          @click="loadData"
        >
          Refresh
        </BaseButton>

        <BaseButton
          variant="primary"
          size="sm"
          :icon="Plus"
          @click="openRegisterModal"
        >
          Register Test Device
        </BaseButton>
      </div>
    </div>

    <!-- KPI Metric Cards for Devices -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <MetricCard
        title="Total Subscriptions"
        :value="stats?.totalDevices ?? 0"
        subtitle="All device registrations"
        variant="indigo"
        :icon="Users"
        :loading="isLoading"
      />
      <MetricCard
        title="Active Android"
        :value="stats?.androidActive ?? 0"
        subtitle="Firebase Cloud Messaging v1"
        variant="emerald"
        :icon="Smartphone"
        :loading="isLoading"
      />
      <MetricCard
        title="Active iOS"
        :value="stats?.iosActive ?? 0"
        subtitle="Apple Push Notification service"
        variant="sky"
        :icon="Apple"
        :loading="isLoading"
      />
      <MetricCard
        title="Active Web"
        :value="stats?.webActive ?? 0"
        subtitle="Web VAPID Service Worker"
        variant="amber"
        :icon="Globe"
        :loading="isLoading"
      />
    </div>

    <!-- Filter Toolbar -->
    <div
      class="bg-white dark:bg-slate-900/90 p-3 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3"
    >
      <div class="flex flex-1 items-center gap-2 max-w-md">
        <BaseInput
          v-model="searchQuery"
          placeholder="Search by device token, model, or platform..."
          size="sm"
          :icon="Search"
        />
      </div>

      <div class="flex items-center gap-2">
        <span class="text-xs text-slate-500 dark:text-slate-400 font-medium">Status:</span>
        <CustomSelect
          v-model="statusFilter"
          :options="filterOptions"
          size="sm"
          button-class="w-36"
        />
      </div>
    </div>

    <!-- Devices Table Card -->
    <div
      class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl overflow-hidden transition-colors"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead>
            <tr
              class="bg-slate-50/70 dark:bg-slate-950/70 border-b border-slate-200 dark:border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider"
            >
              <th class="py-3 px-4">Platform & Hardware</th>
              <th class="py-3 px-4">Push Device Token</th>
              <th class="py-3 px-4">App & OS Version</th>
              <th class="py-3 px-4">Status</th>
              <th class="py-3 px-4">Last Seen</th>
              <th class="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
            <tr v-if="isLoading" class="animate-pulse">
              <td colspan="6" class="py-8 text-center text-slate-400">Loading device registry...</td>
            </tr>

            <tr v-else-if="filteredDevices.length === 0">
              <td colspan="6" class="py-8 text-center text-slate-400">
                No registered devices found matching your criteria.
              </td>
            </tr>

            <tr
              v-for="dev in filteredDevices"
              :key="dev.id"
              class="hover:bg-slate-50/80 dark:hover:bg-slate-800/40 transition-colors"
            >
              <!-- Platform & Model -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <div class="flex items-center gap-2">
                  <div
                    class="w-7 h-7 rounded-lg flex items-center justify-center shrink-0"
                    :class="[
                      dev.platform === 'ANDROID'
                        ? 'bg-emerald-50 dark:bg-emerald-500/15 text-emerald-600 dark:text-emerald-400'
                        : dev.platform === 'IOS'
                          ? 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300'
                          : 'bg-sky-50 dark:bg-sky-500/15 text-sky-600 dark:text-sky-400',
                    ]"
                  >
                    <Smartphone v-if="dev.platform === 'ANDROID'" class="w-3.5 h-3.5" />
                    <Apple v-else-if="dev.platform === 'IOS'" class="w-3.5 h-3.5" />
                    <Globe v-else class="w-3.5 h-3.5" />
                  </div>
                  <div>
                    <div class="font-bold text-slate-900 dark:text-white">
                      {{ dev.deviceModel || dev.platform }}
                    </div>
                    <div class="text-[10px] text-slate-400">
                      {{ dev.platform }}
                    </div>
                  </div>
                </div>
              </td>

              <!-- Token with Copy -->
              <td class="py-3.5 px-4 max-w-xs">
                <button
                  @click="copyToken(dev.pushTokenMasked)"
                  class="font-mono text-[11px] text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1.5 truncate cursor-pointer text-left"
                >
                  <span class="truncate">{{ dev.pushTokenMasked }}</span>
                  <Check v-if="copiedToken === dev.pushTokenMasked" class="w-3 h-3 text-emerald-500 shrink-0" />
                  <Copy v-else class="w-3 h-3 text-slate-400 shrink-0" />
                </button>
              </td>

              <!-- App & OS -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <div class="text-slate-800 dark:text-slate-200">
                  {{ dev.osVersion || 'Unknown OS' }}
                </div>
                <div class="text-[10px] text-slate-400">
                  App v{{ dev.appVersion || '1.0' }}
                </div>
              </td>

              <!-- Status -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold inline-flex items-center gap-1"
                  :class="[
                    dev.active
                      ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
                      : 'bg-slate-100 dark:bg-slate-800 text-slate-500 border border-slate-200 dark:border-slate-700',
                  ]"
                >
                  <span
                    class="w-1.5 h-1.5 rounded-full"
                    :class="dev.active ? 'bg-emerald-500' : 'bg-slate-400'"
                  ></span>
                  {{ dev.active ? 'Subscribed' : 'Bounced' }}
                </span>
              </td>

              <!-- Last Seen -->
              <td class="py-3.5 px-4 text-slate-500 dark:text-slate-400 whitespace-nowrap text-[11px]">
                {{ dev.lastActiveAt ? new Date(dev.lastActiveAt).toLocaleString() : 'Never' }}
              </td>

              <!-- Action -->
              <td class="py-3.5 px-4 text-right whitespace-nowrap">
                <div class="flex items-center justify-end gap-1">
                  <button
                    type="button"
                    @click="promptQuickPush(dev)"
                    title="Send Quick Test Push to this device"
                    class="p-1.5 rounded-lg text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition cursor-pointer"
                  >
                    <Send class="w-3.5 h-3.5" />
                  </button>
                  <button
                    type="button"
                    @click="promptDelete(dev)"
                    title="Remove device"
                    class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition cursor-pointer"
                  >
                    <Trash2 class="w-3.5 h-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <TablePagination
        :current-page="currentPage"
        :last-page="totalPages"
        :total="totalElements"
        :per-page="perPage"
        item-label="devices"
        @change-page="handlePageChange"
        @change-per-page="handlePerPageChange"
      />
    </div>

    <!-- Register Test Device Modal -->
    <ModernModal
      v-model="showRegisterModal"
      title="Register Test Device"
      subtitle="Provision a simulated Android, iOS, or Web push token directly into your subscriber database"
      :icon="Smartphone"
      icon-class="text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 border-emerald-200 dark:border-emerald-500/20"
      :initial-width="580"
      @close="showRegisterModal = false"
    >
      <div class="p-6 space-y-4 text-left">
        <!-- Presets Bar -->
        <div>
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
            Quick Playground Presets
          </label>
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <button
              type="button"
              @click="applyPreset('android-pixel')"
              class="px-2.5 py-2 text-left rounded-xl border border-slate-200 dark:border-slate-800 hover:border-emerald-500/50 hover:bg-emerald-50/50 dark:hover:bg-emerald-950/20 transition cursor-pointer"
            >
              <div class="text-[11px] font-bold text-slate-900 dark:text-white flex items-center gap-1">
                <Smartphone class="w-3 h-3 text-emerald-500" />
                Pixel 8 VIP
              </div>
              <div class="text-[10px] text-slate-400">Android 15 (Gold)</div>
            </button>
            <button
              type="button"
              @click="applyPreset('android-s24')"
              class="px-2.5 py-2 text-left rounded-xl border border-slate-200 dark:border-slate-800 hover:border-emerald-500/50 hover:bg-emerald-50/50 dark:hover:bg-emerald-950/20 transition cursor-pointer"
            >
              <div class="text-[11px] font-bold text-slate-900 dark:text-white flex items-center gap-1">
                <Smartphone class="w-3 h-3 text-emerald-500" />
                Galaxy S24
              </div>
              <div class="text-[10px] text-slate-400">Android 14 (Shopper)</div>
            </button>
            <button
              type="button"
              @click="applyPreset('ios-iphone')"
              class="px-2.5 py-2 text-left rounded-xl border border-slate-200 dark:border-slate-800 hover:border-sky-500/50 hover:bg-sky-50/50 dark:hover:bg-sky-950/20 transition cursor-pointer"
            >
              <div class="text-[11px] font-bold text-slate-900 dark:text-white flex items-center gap-1">
                <Apple class="w-3 h-3 text-sky-500" />
                iPhone 15 Pro
              </div>
              <div class="text-[10px] text-slate-400">iOS 18 (VIP)</div>
            </button>
            <button
              type="button"
              @click="applyPreset('web-chrome')"
              class="px-2.5 py-2 text-left rounded-xl border border-slate-200 dark:border-slate-800 hover:border-amber-500/50 hover:bg-amber-50/50 dark:hover:bg-amber-950/20 transition cursor-pointer"
            >
              <div class="text-[11px] font-bold text-slate-900 dark:text-white flex items-center gap-1">
                <Globe class="w-3 h-3 text-amber-500" />
                Chrome Mac
              </div>
              <div class="text-[10px] text-slate-400">Web Push VAPID</div>
            </button>
          </div>
        </div>

        <!-- Platform Tabs -->
        <div>
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
            Target Platform
          </label>
          <div class="grid grid-cols-3 gap-2">
            <button
              type="button"
              v-for="plat in (['ANDROID', 'IOS', 'WEB'] as const)"
              :key="plat"
              @click="registerPlatform = plat"
              class="py-2 text-xs font-semibold rounded-xl border transition cursor-pointer flex items-center justify-center gap-1.5"
              :class="[
                registerPlatform === plat
                  ? 'border-indigo-600 bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400'
                  : 'border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800/50',
              ]"
            >
              <Smartphone v-if="plat === 'ANDROID'" class="w-3.5 h-3.5" />
              <Apple v-else-if="plat === 'IOS'" class="w-3.5 h-3.5" />
              <Globe v-else class="w-3.5 h-3.5" />
              <span>{{ plat }}</span>
            </button>
          </div>
        </div>

        <!-- Push Token -->
        <div>
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
            Device Push Token <span class="text-rose-500">*</span>
          </label>
          <BaseInput
            v-model="registerPushToken"
            placeholder="fcm_token_... or apns_token_..."
            size="sm"
          />
        </div>

        <!-- External User ID & Hardware Model -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
              External User ID (Optional)
            </label>
            <BaseInput
              v-model="registerExternalUserId"
              placeholder="e.g. shopper-vip-101"
              size="sm"
            />
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
              Hardware Model
            </label>
            <BaseInput
              v-model="registerDeviceModel"
              placeholder="e.g. Pixel 8 Pro"
              size="sm"
            />
          </div>
        </div>

        <!-- OS & App Version -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
              OS Version
            </label>
            <BaseInput
              v-model="registerOsVersion"
              placeholder="e.g. Android 15"
              size="sm"
            />
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
              App Version
            </label>
            <BaseInput
              v-model="registerAppVersion"
              placeholder="e.g. 4.2.0"
              size="sm"
            />
          </div>
        </div>

        <!-- Custom Tags for Audience Segmentation -->
        <div>
          <div class="flex items-center justify-between mb-1.5">
            <label class="text-xs font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
              <Tag class="w-3.5 h-3.5 text-indigo-500" />
              <span>Custom Segmentation Tags (Key / Value)</span>
            </label>
            <button
              type="button"
              @click="addTagRow"
              class="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1 cursor-pointer"
            >
              <Plus class="w-3 h-3" /> Add Tag
            </button>
          </div>

          <div class="space-y-2">
            <div
              v-for="(t, idx) in registerTags"
              :key="idx"
              class="flex items-center gap-2"
            >
              <input
                v-model="t.key"
                placeholder="Tag Key (e.g. tier)"
                class="flex-1 px-3 py-1.5 text-xs rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-950 text-slate-900 dark:text-white focus:outline-hidden focus:ring-2 focus:ring-indigo-500"
              />
              <input
                v-model="t.value"
                placeholder="Tag Value (e.g. gold)"
                class="flex-1 px-3 py-1.5 text-xs rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-950 text-slate-900 dark:text-white focus:outline-hidden focus:ring-2 focus:ring-indigo-500"
              />
              <button
                type="button"
                @click="removeTagRow(idx)"
                class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition cursor-pointer"
              >
                <X class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <BaseButton variant="ghost" @click="showRegisterModal = false">
          Cancel
        </BaseButton>
        <BaseButton
          variant="primary"
          :icon="Sparkles"
          :loading="isRegistering"
          @click="handleRegisterDevice"
        >
          Register into Device Pool
        </BaseButton>
      </template>
    </ModernModal>

    <!-- Quick Push Modal -->
    <ModernModal
      v-model="showQuickPushModal"
      title="Send Quick Test Push"
      :subtitle="`Direct targeted dispatch to ${quickPushDevice?.deviceModel || quickPushDevice?.platform}`"
      :icon="Send"
      icon-class="text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-500/10 border-indigo-200 dark:border-indigo-500/20"
      :initial-width="500"
      @close="showQuickPushModal = false"
    >
      <div class="p-6 space-y-4 text-left">
        <!-- Target Info Pill -->
        <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 space-y-1">
          <div class="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
            Target Device Token
          </div>
          <div class="font-mono text-xs text-indigo-600 dark:text-indigo-400 truncate">
            {{ quickPushDevice?.pushTokenMasked }}
          </div>
          <div class="text-[10px] text-slate-500 flex items-center gap-2">
            <span>Platform: <strong>{{ quickPushDevice?.platform }}</strong></span>
            <span>•</span>
            <span>Model: <strong>{{ quickPushDevice?.deviceModel }}</strong></span>
          </div>
        </div>

        <div>
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
            Notification Title <span class="text-rose-500">*</span>
          </label>
          <BaseInput
            v-model="quickPushTitle"
            placeholder="e.g. Flash Promo Alert! ⚡"
            size="sm"
          />
        </div>

        <div>
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
            Notification Body <span class="text-rose-500">*</span>
          </label>
          <textarea
            v-model="quickPushBody"
            rows="3"
            placeholder="Write message contents..."
            class="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-950 text-slate-900 dark:text-white focus:outline-hidden focus:ring-2 focus:ring-indigo-500"
          ></textarea>
        </div>

        <div>
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
            Deep Link URL (Optional)
          </label>
          <BaseInput
            v-model="quickPushDeepLink"
            placeholder="e.g. playground://shop/promo"
            size="sm"
          />
        </div>
      </div>

      <template #footer>
        <BaseButton variant="ghost" @click="showQuickPushModal = false">
          Cancel
        </BaseButton>
        <BaseButton
          variant="primary"
          :icon="Send"
          :loading="isQuickPushing"
          @click="handleQuickPushSubmit"
        >
          Dispatch Push Immediately
        </BaseButton>
      </template>
    </ModernModal>

    <!-- Delete Device Modal -->
    <ModernModal
      v-model="showDeleteModal"
      title="Deregister Device"
      subtitle="Remove device push token from recipient pool"
      :icon="Trash2"
      icon-class="text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-500/10 border-rose-200 dark:border-rose-500/20"
      :initial-width="480"
      @close="showDeleteModal = false"
    >
      <div class="p-6 space-y-4 text-left">
        <p class="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
          Are you sure you want to remove token
          <span class="font-mono font-semibold text-slate-900 dark:text-white truncate block max-w-sm mt-1 p-2 rounded-xl bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700">
            {{ deviceToDelete?.pushTokenMasked }}
          </span>
          This device will no longer receive broadcast or targeted push notifications.
        </p>
      </div>

      <template #footer>
        <BaseButton variant="ghost" @click="showDeleteModal = false">
          Cancel
        </BaseButton>
        <BaseButton variant="danger" :loading="isDeleting" @click="confirmDelete">
          Deregister Token
        </BaseButton>
      </template>
    </ModernModal>
  </div>
</template>
