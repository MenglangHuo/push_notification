<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { devicesApi, type DeviceSummary, type DeviceStats } from '@/api/devices'
import { appsApi, type AppSummary } from '@/api/apps'
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
  Layers,
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
const selectedAppId = ref<string>(appId.value)

// Platform filter state: ALL, ANDROID, IOS, WEB
const selectedPlatform = ref<'ALL' | 'ANDROID' | 'IOS' | 'WEB'>('ALL')

// Available Apps in the organization
const availableApps = ref<AppSummary[]>([])
const appSelectOptions = computed<SelectOption[]>(() => {
  return availableApps.value.map((a) => ({
    label: a.name,
    value: a.id,
  }))
})

function setPlatformFilter(platform: 'ALL' | 'ANDROID' | 'IOS' | 'WEB') {
  selectedPlatform.value = platform
}

function togglePlatformFilter(platform: 'ANDROID' | 'IOS' | 'WEB') {
  if (selectedPlatform.value === platform) {
    selectedPlatform.value = 'ALL'
  } else {
    selectedPlatform.value = platform
  }
}

function resetAllFilters() {
  selectedPlatform.value = 'ALL'
  statusFilter.value = 'ALL'
  searchQuery.value = ''
}

async function loadApps() {
  if (!authStore.currentOrgId) return
  try {
    const list = await appsApi.list(authStore.currentOrgId)
    availableApps.value = list
    const isValidSelection = list.some((a) => a.id === selectedAppId.value)
    if ((!selectedAppId.value || !isValidSelection) && list.length > 0) {
      selectedAppId.value = list[0].id
      authStore.setCurrentApp(list[0].id)
    } else if (list.length === 0) {
      selectedAppId.value = ''
      authStore.setCurrentApp(null)
    }
  } catch (err) {
    console.error('Failed to load apps:', err)
  }
}

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
    const idempotency =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `idemp_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`

    await notificationsApi.create(appId.value, {
      appId: appId.value,
      title: quickPushTitle.value,
      body: quickPushBody.value,
      idempotency,
      idempotencyKey: idempotency,
      priority: quickPushPriority.value,
      deepLink: quickPushDeepLink.value || undefined,
      targetType: 'DEVICE_IDS',
      targetPayload: {
        deviceIds: [quickPushDevice.value.id],
        deviceTokens: [quickPushDevice.value.pushTokenMasked],
      },
    }, idempotency)
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
  const currentTargetAppId = selectedAppId.value || appId.value
  if (!currentTargetAppId) return
  isLoading.value = true
  try {
    const filterParam = statusFilter.value === 'ALL' ? undefined : statusFilter.value === 'ACTIVE'
    const platformParam = selectedPlatform.value === 'ALL' ? undefined : selectedPlatform.value
    const [pageData, statsData] = await Promise.all([
      devicesApi.list(currentTargetAppId, {
        platform: platformParam,
        active: filterParam,
        page: currentPage.value - 1,
        size: perPage.value,
      }),
      devicesApi.stats(currentTargetAppId).catch(() => null),
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
        appId: currentTargetAppId,
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
        appId: currentTargetAppId,
        platform: 'IOS',
        pushTokenMasked: 'apns_token_88a912b77c1f82_iphone15pro',
        deviceModel: 'iPhone 15 Pro Max',
        osVersion: 'iOS 18.1',
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
        appId: currentTargetAppId,
        platform: 'WEB',
        pushTokenMasked: 'https://fcm.googleapis.com/fcm/send/eR89a_vapid',
        deviceModel: 'Mozilla/5.0 (X11; Linux x86_64)',
        osVersion: 'Linux x86_64',
        appVersion: 'WebPortal 1.0',
        language: 'en',
        timezone: 'Asia/Tokyo',
        active: true,
        lastActiveAt: new Date(Date.now() - 86400000).toISOString(),
        createdAt: new Date(Date.now() - 500000000).toISOString(),
        tags: {},
      },
      {
        id: 'dev_01j9a8b4',
        appId: currentTargetAppId,
        platform: 'ANDROID',
        pushTokenMasked: 'fcm_token_pixel8_974720938b',
        deviceModel: 'Google Pixel 8 Pro',
        osVersion: 'Android 15',
        appVersion: '4.2.0',
        language: 'en',
        timezone: 'America/Los_Angeles',
        active: true,
        lastActiveAt: new Date(Date.now() - 1800000).toISOString(),
        createdAt: new Date(Date.now() - 400000000).toISOString(),
        tags: { vip: 'true' },
      },
      {
        id: 'dev_01j9a8b5',
        appId: currentTargetAppId,
        platform: 'IOS',
        pushTokenMasked: 'apns_token_ipadpro_4879201a',
        deviceModel: 'iPad Pro 13" M4',
        osVersion: 'iPadOS 18.1',
        appVersion: '4.2.0',
        language: 'en',
        timezone: 'Europe/Paris',
        active: true,
        lastActiveAt: new Date(Date.now() - 5400000).toISOString(),
        createdAt: new Date(Date.now() - 300000000).toISOString(),
        tags: { tier: 'enterprise' },
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
  if (selectedPlatform.value !== 'ALL') {
    list = list.filter((d) => d.platform === selectedPlatform.value)
  }
  if (statusFilter.value === 'ACTIVE') {
    list = list.filter((d) => d.active)
  } else if (statusFilter.value === 'INACTIVE') {
    list = list.filter((d) => !d.active)
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(
      (d) =>
        d.pushTokenMasked.toLowerCase().includes(q) ||
        d.deviceModel?.toLowerCase().includes(q) ||
        d.platform.toLowerCase().includes(q) ||
        d.externalUserId?.toLowerCase().includes(q) ||
        d.osVersion?.toLowerCase().includes(q) ||
        d.appVersion?.toLowerCase().includes(q),
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
  const currentTargetAppId = selectedAppId.value || appId.value
  if (!deviceToDelete.value || !currentTargetAppId) return
  isDeleting.value = true
  try {
    await devicesApi.delete(currentTargetAppId, deviceToDelete.value.id)
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

watch([selectedAppId, statusFilter, selectedPlatform], () => {
  currentPage.value = 1
  loadData()
})

watch(appId, (newId) => {
  if (newId && newId !== selectedAppId.value) {
    selectedAppId.value = newId
  }
})

onMounted(async () => {
  await loadApps()
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

    <!-- KPI Metric Cards for Devices (Interactive Platform Filter) -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <div
        @click="setPlatformFilter('ALL')"
        class="cursor-pointer rounded-2xl transition-all duration-200"
        :class="[
          selectedPlatform === 'ALL'
            ? 'ring-2 ring-indigo-500 shadow-md ring-offset-2 dark:ring-offset-slate-900'
            : 'hover:opacity-90',
        ]"
        title="Show all registered devices"
      >
        <MetricCard
          title="Total Subscriptions"
          :value="stats?.totalDevices ?? 0"
          subtitle="All device registrations"
          variant="indigo"
          :icon="Users"
          :loading="isLoading"
        />
      </div>

      <div
        @click="togglePlatformFilter('ANDROID')"
        class="cursor-pointer rounded-2xl transition-all duration-200"
        :class="[
          selectedPlatform === 'ANDROID'
            ? 'ring-2 ring-emerald-500 shadow-md ring-offset-2 dark:ring-offset-slate-900'
            : 'hover:opacity-90',
        ]"
        title="Filter by Android FCM devices"
      >
        <MetricCard
          title="Active Android"
          :value="stats?.androidActive ?? 0"
          subtitle="Firebase Cloud Messaging v1"
          variant="emerald"
          :icon="Smartphone"
          :loading="isLoading"
        />
      </div>

      <div
        @click="togglePlatformFilter('IOS')"
        class="cursor-pointer rounded-2xl transition-all duration-200"
        :class="[
          selectedPlatform === 'IOS'
            ? 'ring-2 ring-sky-500 shadow-md ring-offset-2 dark:ring-offset-slate-900'
            : 'hover:opacity-90',
        ]"
        title="Filter by Apple APNs devices"
      >
        <MetricCard
          title="Active iOS"
          :value="stats?.iosActive ?? 0"
          subtitle="Apple Push Notification service"
          variant="sky"
          :icon="Apple"
          :loading="isLoading"
        />
      </div>

      <div
        @click="togglePlatformFilter('WEB')"
        class="cursor-pointer rounded-2xl transition-all duration-200"
        :class="[
          selectedPlatform === 'WEB'
            ? 'ring-2 ring-amber-500 shadow-md ring-offset-2 dark:ring-offset-slate-900'
            : 'hover:opacity-90',
        ]"
        title="Filter by Web Push VAPID devices"
      >
        <MetricCard
          title="Active Web"
          :value="stats?.webActive ?? 0"
          subtitle="Web VAPID Service Worker"
          variant="amber"
          :icon="Globe"
          :loading="isLoading"
        />
      </div>
    </div>

    <!-- Filter Toolbar: Search, Platform Segmented Tabs, App Selector, and Status -->
    <div
      class="bg-white dark:bg-slate-900/90 p-3 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm flex flex-col lg:flex-row lg:items-center justify-between gap-3"
    >
      <!-- Search Input -->
      <div class="flex-1 min-w-[240px] max-w-md">
        <BaseInput
          v-model="searchQuery"
          placeholder="Search by token, model, user ID, or OS..."
          size="sm"
          :icon="Search"
        />
      </div>

      <!-- Platform Segmented Tabs & Selectors -->
      <div class="flex items-center justify-between lg:justify-end gap-3 flex-wrap">
        <!-- Platform Segmented Controls -->
        <div class="flex items-center gap-1 p-1 bg-slate-100 dark:bg-slate-800/80 rounded-xl border border-slate-200 dark:border-slate-700/80 text-xs overflow-x-auto">
          <button
            type="button"
            @click="setPlatformFilter('ALL')"
            class="px-2.5 py-1.5 rounded-lg font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedPlatform === 'ALL'
                ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-400 shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
            ]"
          >
            <Layers class="w-3.5 h-3.5" />
            <span>All</span>
            <span
              class="px-1.5 py-0.2 rounded-full text-[10px] font-semibold"
              :class="selectedPlatform === 'ALL' ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-900/60 dark:text-indigo-300' : 'bg-slate-200/60 text-slate-500 dark:bg-slate-700 dark:text-slate-400'"
            >
              {{ (stats?.totalDevices ?? totalElements).toLocaleString() }}
            </span>
          </button>

          <button
            type="button"
            @click="setPlatformFilter('ANDROID')"
            class="px-2.5 py-1.5 rounded-lg font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedPlatform === 'ANDROID'
                ? 'bg-white dark:bg-slate-700 text-emerald-600 dark:text-emerald-400 shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:text-emerald-600 dark:hover:text-emerald-400',
            ]"
          >
            <Smartphone class="w-3.5 h-3.5" />
            <span>Android</span>
            <span
              class="px-1.5 py-0.2 rounded-full text-[10px] font-semibold"
              :class="selectedPlatform === 'ANDROID' ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/60 dark:text-emerald-300' : 'bg-slate-200/60 text-slate-500 dark:bg-slate-700 dark:text-slate-400'"
            >
              {{ (stats?.androidActive ?? 0).toLocaleString() }}
            </span>
          </button>

          <button
            type="button"
            @click="setPlatformFilter('IOS')"
            class="px-2.5 py-1.5 rounded-lg font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedPlatform === 'IOS'
                ? 'bg-white dark:bg-slate-700 text-sky-600 dark:text-sky-400 shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:text-sky-600 dark:hover:text-sky-400',
            ]"
          >
            <Apple class="w-3.5 h-3.5" />
            <span>iOS</span>
            <span
              class="px-1.5 py-0.2 rounded-full text-[10px] font-semibold"
              :class="selectedPlatform === 'IOS' ? 'bg-sky-50 text-sky-700 dark:bg-sky-900/60 dark:text-sky-300' : 'bg-slate-200/60 text-slate-500 dark:bg-slate-700 dark:text-slate-400'"
            >
              {{ (stats?.iosActive ?? 0).toLocaleString() }}
            </span>
          </button>

          <button
            type="button"
            @click="setPlatformFilter('WEB')"
            class="px-2.5 py-1.5 rounded-lg font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedPlatform === 'WEB'
                ? 'bg-white dark:bg-slate-700 text-amber-600 dark:text-amber-400 shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:text-amber-600 dark:hover:text-amber-400',
            ]"
          >
            <Globe class="w-3.5 h-3.5" />
            <span>Web</span>
            <span
              class="px-1.5 py-0.2 rounded-full text-[10px] font-semibold"
              :class="selectedPlatform === 'WEB' ? 'bg-amber-50 text-amber-700 dark:bg-amber-900/60 dark:text-amber-300' : 'bg-slate-200/60 text-slate-500 dark:bg-slate-700 dark:text-slate-400'"
            >
              {{ (stats?.webActive ?? 0).toLocaleString() }}
            </span>
          </button>
        </div>

        <!-- App Context Selector (when multiple apps exist) -->
        <div v-if="appSelectOptions.length > 1" class="flex items-center gap-1.5">
          <span class="text-xs text-slate-500 dark:text-slate-400 font-medium">App:</span>
          <CustomSelect
            v-model="selectedAppId"
            :options="appSelectOptions"
            size="sm"
            button-class="w-44"
          />
        </div>

        <!-- Status Filter -->
        <div class="flex items-center gap-1.5">
          <span class="text-xs text-slate-500 dark:text-slate-400 font-medium">Status:</span>
          <CustomSelect
            v-model="statusFilter"
            :options="filterOptions"
            size="sm"
            button-class="w-36"
          />
        </div>
      </div>
    </div>

    <!-- Active Filter Indicator Banner -->
    <div
      v-if="selectedPlatform !== 'ALL' || statusFilter !== 'ALL' || searchQuery.trim()"
      class="flex items-center justify-between px-3.5 py-2 rounded-xl bg-indigo-50/70 dark:bg-indigo-950/30 border border-indigo-100 dark:border-indigo-900/50 text-xs text-indigo-900 dark:text-indigo-200"
    >
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-semibold text-indigo-700 dark:text-indigo-300">Active Filters:</span>

        <span
          v-if="selectedPlatform !== 'ALL'"
          class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-medium bg-white dark:bg-slate-800 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800 shadow-2xs"
        >
          <Smartphone v-if="selectedPlatform === 'ANDROID'" class="w-3 h-3 text-emerald-500" />
          <Apple v-else-if="selectedPlatform === 'IOS'" class="w-3 h-3 text-sky-500" />
          <Globe v-else-if="selectedPlatform === 'WEB'" class="w-3 h-3 text-amber-500" />
          Platform: {{ selectedPlatform === 'ANDROID' ? 'Android' : selectedPlatform === 'IOS' ? 'iOS' : 'Web' }}
          <button @click="setPlatformFilter('ALL')" class="hover:text-red-500 ml-0.5 cursor-pointer">
            <X class="w-3 h-3" />
          </button>
        </span>

        <span
          v-if="statusFilter !== 'ALL'"
          class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-medium bg-white dark:bg-slate-800 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800 shadow-2xs"
        >
          Status: {{ statusFilter === 'ACTIVE' ? 'Active Tokens' : 'Inactive / Bounced' }}
          <button @click="statusFilter = 'ALL'" class="hover:text-red-500 ml-0.5 cursor-pointer">
            <X class="w-3 h-3" />
          </button>
        </span>

        <span
          v-if="searchQuery.trim()"
          class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-medium bg-white dark:bg-slate-800 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800 shadow-2xs"
        >
          Search: "{{ searchQuery.trim() }}"
          <button @click="searchQuery = ''" class="hover:text-red-500 ml-0.5 cursor-pointer">
            <X class="w-3 h-3" />
          </button>
        </span>
      </div>

      <button
        @click="resetAllFilters"
        class="text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-200 hover:underline cursor-pointer shrink-0 ml-2"
      >
        Reset Filters
      </button>
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
              <td colspan="6" class="py-12 text-center">
                <div class="flex flex-col items-center justify-center max-w-sm mx-auto space-y-2">
                  <div class="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-400 flex items-center justify-center">
                    <Search class="w-5 h-5" />
                  </div>
                  <p class="font-semibold text-slate-800 dark:text-slate-200 text-sm">No devices found</p>
                  <p class="text-xs text-slate-400">
                    No registered devices found matching your current filters.
                  </p>
                  <BaseButton
                    v-if="selectedPlatform !== 'ALL' || statusFilter !== 'ALL' || searchQuery.trim()"
                    variant="outline"
                    size="sm"
                    class="mt-2"
                    @click="resetAllFilters"
                  >
                    Clear All Filters
                  </BaseButton>
                </div>
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
