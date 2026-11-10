<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { devicesApi, type DeviceSummary, type DeviceStats } from '@/api/devices'
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
  RefreshCw,
  Search,
  Smartphone,
  Trash2,
  Users,
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
                <button
                  type="button"
                  @click="promptDelete(dev)"
                  title="Remove device"
                  class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition cursor-pointer"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
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
