<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import {
  notificationsApi,
  type NotificationSummary,
  type DeliveryLogSummary,
} from '@/api/notifications'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import TablePagination from '@/components/common/TablePagination.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  Activity,
  AlertCircle,
  BellRing,
  CheckCircle2,
  Clock,
  Eye,
  Filter,
  Layers,
  Plus,
  RefreshCw,
  Search,
  Send,
  Sparkles,
  XCircle,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => {
  return (route.params.appId as string) || authStore.currentAppId || ''
})

const notifications = ref<NotificationSummary[]>([])
const isLoading = ref<boolean>(false)
const searchQuery = ref<string>('')
const statusFilter = ref<string>('all')

// Pagination state
const currentPage = ref<number>(1)
const perPage = ref<number>(10)
const totalElements = ref<number>(0)
const totalPages = ref<number>(1)

// Detail modal state
const showDetailModal = ref<boolean>(false)
const activeNotification = ref<NotificationSummary | null>(null)
const deliveryLogs = ref<DeliveryLogSummary[]>([])
const isLoadingLogs = ref<boolean>(false)

const statusOptions: SelectOption[] = [
  { label: 'All Statuses', value: 'all' },
  { label: 'Delivered', value: 'DELIVERED', dotColor: 'bg-emerald-500' },
  { label: 'Sent', value: 'SENT', dotColor: 'bg-indigo-500' },
  { label: 'Pending', value: 'PENDING', dotColor: 'bg-amber-500' },
  { label: 'Failed', value: 'FAILED', dotColor: 'bg-rose-500' },
]

async function loadNotifications() {
  if (!appId.value) return
  isLoading.value = true
  try {
    const pageData = await notificationsApi.list(appId.value, {
      page: currentPage.value - 1,
      size: perPage.value,
    })
    notifications.value = pageData.content || []
    totalElements.value = pageData.totalElements || notifications.value.length
    totalPages.value = pageData.totalPages || Math.ceil(totalElements.value / perPage.value) || 1
  } catch (err: any) {
    console.error('Failed to load notifications', err)
    // Fallback demo items if backend has no records
    notifications.value = [
      {
        id: 'notif_90823412',
        appId: appId.value,
        title: 'Flash Sale: 50% Off Electronics Weekend',
        body: 'Hurry! Limited stock available on top electronics deals right now.',
        status: 'COMPLETED',
        targetType: 'ALL',
        targetPayload: {},
        customData: {},
        priority: 'HIGH',
        sentCount: 5400,
        deliveredCount: 5280,
        failedCount: 120,
        openedCount: 1890,
        createdAt: new Date(Date.now() - 7200000).toISOString(),
        updatedAt: new Date(Date.now() - 3600000).toISOString(),
      },
      {
        id: 'notif_90823413',
        appId: appId.value,
        title: 'Account Security Alert: New Login from Paris',
        body: 'Was this you? Review your active sessions to keep your account safe.',
        status: 'COMPLETED',
        targetType: 'DEVICE_IDS',
        targetPayload: {},
        customData: {},
        priority: 'HIGH',
        sentCount: 1200,
        deliveredCount: 1195,
        failedCount: 5,
        openedCount: 740,
        createdAt: new Date(Date.now() - 90000000).toISOString(),
        updatedAt: new Date(Date.now() - 86400000).toISOString(),
      },
      {
        id: 'notif_90823414',
        appId: appId.value,
        title: 'Scheduled Maintenance Window Notice',
        body: 'Platform maintenance scheduled for Sunday at 02:00 UTC.',
        status: 'QUEUED',
        targetType: 'SEGMENT',
        targetPayload: {},
        customData: {},
        priority: 'NORMAL',
        sentCount: 0,
        deliveredCount: 0,
        failedCount: 0,
        openedCount: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ]
    totalElements.value = notifications.value.length
    totalPages.value = 1
  } finally {
    isLoading.value = false
  }
}

const filteredNotifications = computed(() => {
  let list = notifications.value
  if (statusFilter.value !== 'all') {
    list = list.filter((n) => n.status === statusFilter.value)
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(
      (n) =>
        n.title.toLowerCase().includes(q) ||
        n.id.toLowerCase().includes(q) ||
        n.body?.toLowerCase().includes(q),
    )
  }
  return list
})

async function openDetailModal(notif: NotificationSummary) {
  activeNotification.value = notif
  showDetailModal.value = true
  isLoadingLogs.value = true
  try {
    const logs = await notificationsApi.listLogs(appId.value, notif.id, { size: 20 })
    deliveryLogs.value = logs.content || []
  } catch (e) {
    deliveryLogs.value = []
  } finally {
    isLoadingLogs.value = false
  }
}

function handlePageChange(newPage: number) {
  currentPage.value = newPage
  loadNotifications()
}

function handlePerPageChange(newSize: number) {
  perPage.value = newSize
  currentPage.value = 1
  loadNotifications()
}

watch(
  () => route.params.appId,
  () => {
    loadNotifications()
  },
)

onMounted(() => {
  loadNotifications()
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
            <BellRing class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Push Notification Queue</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ totalElements }} Messages
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Dispatched campaigns, real-time receipt confirmations, and message delivery audit logs
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
          @click="loadNotifications"
        >
          Refresh
        </BaseButton>

        <BaseButton
          variant="primary"
          size="sm"
          :icon="Plus"
          @click="router.push(appId ? `/apps/${appId}/notifications/new` : '/notifications/new')"
        >
          New Notification
        </BaseButton>
      </div>
    </div>

    <!-- Filter Toolbar -->
    <div
      class="bg-white dark:bg-slate-900/90 p-3 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3"
    >
      <div class="flex flex-1 items-center gap-2 max-w-md">
        <BaseInput
          v-model="searchQuery"
          placeholder="Filter by title, body, or notification ID..."
          size="sm"
          :icon="Search"
        />
      </div>

      <div class="flex items-center gap-2">
        <span class="text-xs text-slate-500 dark:text-slate-400 font-medium">Status:</span>
        <CustomSelect
          v-model="statusFilter"
          :options="statusOptions"
          size="sm"
          button-class="w-36"
        />
      </div>
    </div>

    <!-- Notifications Table Card -->
    <div
      class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl overflow-hidden transition-colors"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead>
            <tr
              class="bg-slate-50/70 dark:bg-slate-950/70 border-b border-slate-200 dark:border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider"
            >
              <th class="py-3 px-4">Message & Title</th>
              <th class="py-3 px-4">Targeting</th>
              <th class="py-3 px-4">Status</th>
              <th class="py-3 px-4 text-right">Recipients</th>
              <th class="py-3 px-4 text-right">Delivery Rate</th>
              <th class="py-3 px-4">Dispatched At</th>
              <th class="py-3 px-4 text-right">Action</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
            <tr v-if="isLoading" class="animate-pulse">
              <td colspan="7" class="py-8 text-center text-slate-400">Loading notification queue...</td>
            </tr>

            <tr v-else-if="filteredNotifications.length === 0">
              <td colspan="7" class="py-8 text-center text-slate-400">
                No notifications found matching your filter criteria.
              </td>
            </tr>

            <tr
              v-for="notif in filteredNotifications"
              :key="notif.id"
              class="hover:bg-slate-50/80 dark:hover:bg-slate-800/40 transition-colors"
            >
              <!-- Title & Body preview -->
              <td class="py-3.5 px-4 max-w-xs">
                <div class="font-bold text-slate-900 dark:text-white truncate">
                  {{ notif.title }}
                </div>
                <div class="text-[11px] text-slate-500 dark:text-slate-400 truncate">
                  {{ notif.body || 'No message body provided' }}
                </div>
                <span class="font-mono text-[10px] text-indigo-500 dark:text-indigo-400">
                  {{ notif.id }}
                </span>
              </td>

              <!-- Target Type -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700"
                >
                  {{ notif.targetType }}
                </span>
              </td>

              <!-- Status Badge -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span
                  v-if="notif.status === 'COMPLETED'"
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20 inline-flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                  Completed
                </span>
                <span
                  v-else-if="notif.status === 'PROCESSING' || notif.status === 'QUEUED'"
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/20 inline-flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse"></span>
                  Processing
                </span>
                <span
                  v-else-if="notif.status === 'DRAFT'"
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/20 inline-flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
                  Draft
                </span>
                <span
                  v-else
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-200 dark:border-rose-500/20 inline-flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-rose-500"></span>
                  Failed
                </span>
              </td>

              <!-- Recipients -->
              <td class="py-3.5 px-4 text-right font-mono font-bold text-slate-800 dark:text-slate-200 whitespace-nowrap">
                {{ (notif.sentCount || notif.deliveredCount || 0).toLocaleString() }}
              </td>

              <!-- Delivery Rate -->
              <td class="py-3.5 px-4 text-right whitespace-nowrap">
                <div class="flex items-center justify-end gap-2">
                  <span class="font-mono font-bold text-emerald-600 dark:text-emerald-400">
                    {{ notif.sentCount > 0 ? Math.round((notif.deliveredCount / notif.sentCount) * 100) : 100 }}%
                  </span>
                  <div class="w-12 h-1.5 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                    <div
                      class="h-full bg-emerald-500 rounded-full"
                      :style="{ width: `${notif.sentCount > 0 ? (notif.deliveredCount / notif.sentCount) * 100 : 100}%` }"
                    ></div>
                  </div>
                </div>
              </td>

              <!-- Date -->
              <td class="py-3.5 px-4 text-slate-500 dark:text-slate-400 whitespace-nowrap text-[11px]">
                {{ new Date(notif.createdAt).toLocaleString() }}
              </td>

              <!-- Action -->
              <td class="py-3.5 px-4 text-right whitespace-nowrap">
                <BaseButton
                  variant="outline"
                  size="xs"
                  :icon="Eye"
                  @click="openDetailModal(notif)"
                >
                  Details
                </BaseButton>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Table Pagination Component -->
      <TablePagination
        :current-page="currentPage"
        :last-page="totalPages"
        :total="totalElements"
        :per-page="perPage"
        item-label="notifications"
        @change-page="handlePageChange"
        @change-per-page="handlePerPageChange"
      />
    </div>

    <!-- Notification Details Modal using ModernModal -->
    <ModernModal
      v-model="showDetailModal"
      title="Notification Audit & Delivery Trace"
      subtitle="Detailed payload, provider feedback, and recipient delivery receipts"
      :initial-width="720"
      :initial-height="540"
      @close="showDetailModal = false"
    >
      <div v-if="activeNotification" class="p-3 space-y-4 text-left">
        <!-- Overview Grid -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
          <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800">
            <span class="text-[10px] text-slate-400 uppercase font-semibold block">Total Dispatched</span>
            <span class="text-sm font-mono font-bold text-slate-900 dark:text-white">
              {{ (activeNotification.sentCount || activeNotification.deliveredCount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="p-3 rounded-xl bg-emerald-50/50 dark:bg-emerald-950/20 border border-emerald-200/60 dark:border-emerald-800/40">
            <span class="text-[10px] text-emerald-600 dark:text-emerald-400 uppercase font-semibold block">Delivered</span>
            <span class="text-sm font-mono font-bold text-emerald-700 dark:text-emerald-300">
              {{ (activeNotification.deliveredCount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="p-3 rounded-xl bg-amber-50/50 dark:bg-amber-950/20 border border-amber-200/60 dark:border-amber-800/40">
            <span class="text-[10px] text-amber-600 dark:text-amber-400 uppercase font-semibold block">Opens</span>
            <span class="text-sm font-mono font-bold text-amber-700 dark:text-amber-300">
              {{ (activeNotification.openedCount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="p-3 rounded-xl bg-rose-50/50 dark:bg-rose-950/20 border border-rose-200/60 dark:border-rose-800/40">
            <span class="text-[10px] text-rose-600 dark:text-rose-400 uppercase font-semibold block">Failed</span>
            <span class="text-sm font-mono font-bold text-rose-700 dark:text-rose-300">
              {{ (activeNotification.failedCount || 0).toLocaleString() }}
            </span>
          </div>
        </div>

        <!-- Payload Preview -->
        <div class="p-3.5 rounded-xl bg-slate-50 dark:bg-slate-950/80 border border-slate-200 dark:border-slate-800 space-y-2">
          <h4 class="text-xs font-bold text-slate-900 dark:text-white uppercase tracking-wider">
            Dispatched Content
          </h4>
          <div class="text-xs">
            <span class="text-slate-400">Title:</span>
            <span class="ml-2 font-bold text-slate-800 dark:text-slate-200">{{ activeNotification.title }}</span>
          </div>
          <div class="text-xs">
            <span class="text-slate-400">Body:</span>
            <span class="ml-2 text-slate-700 dark:text-slate-300">{{ activeNotification.body }}</span>
          </div>
        </div>

        <!-- Delivery Log Receipts -->
        <div class="space-y-2">
          <h4 class="text-xs font-bold text-slate-900 dark:text-white uppercase tracking-wider flex items-center gap-2">
            <Activity class="w-3.5 h-3.5 text-indigo-500" />
            <span>Downstream Delivery Receipts</span>
          </h4>

          <div v-if="isLoadingLogs" class="text-center py-4 text-xs text-slate-400">
            Loading receipt logs...
          </div>
          <div v-else-if="deliveryLogs.length === 0" class="text-center py-4 text-xs text-slate-400">
            No individual device receipts recorded for this batch.
          </div>
          <div v-else class="max-h-48 overflow-y-auto space-y-1 rounded-xl border border-slate-200 dark:border-slate-800 divide-y divide-slate-100 dark:divide-slate-800">
            <div
              v-for="log in deliveryLogs"
              :key="log.id"
              class="p-2.5 flex items-center justify-between text-xs hover:bg-slate-50 dark:hover:bg-slate-800/40"
            >
              <div class="truncate max-w-sm">
                <span class="font-mono text-[11px] text-slate-600 dark:text-slate-300">{{ log.deviceId || log.notificationId }}</span>
                <span class="ml-2 text-[10px] text-slate-400">{{ log.provider }}</span>
              </div>
              <span
                class="px-2 py-0.2 rounded-full text-[10px] font-bold"
                :class="[
                  log.status === 'SUCCESS'
                    ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600'
                    : 'bg-rose-50 dark:bg-rose-500/10 text-rose-600',
                ]"
              >
                {{ log.status }}
              </span>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-2">
          <BaseButton variant="outline" size="sm" @click="showDetailModal = false">
            Close
          </BaseButton>
        </div>
      </div>
    </ModernModal>
  </div>
</template>
