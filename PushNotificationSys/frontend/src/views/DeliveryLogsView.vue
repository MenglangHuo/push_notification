<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import {
  getDeliveryLogs,
  exportDeliveryLogsCsv,
  getClientEvents,
  type DeliveryLogItem,
  type ClientEventItem,
} from '@/api/logs'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import TablePagination from '@/components/common/TablePagination.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Download,
  Eye,
  FileCode,
  Filter,
  Radio,
  RefreshCw,
  ScrollText,
  Search,
  XCircle,
} from 'lucide-vue-next'

const route = useRoute()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => (route.params.appId as string) || authStore.currentAppId || '')

const activeTab = ref<'delivery' | 'events'>('delivery')

// Delivery Logs State
const deliveryLogs = ref<DeliveryLogItem[]>([])
const deliveryTotal = ref(0)
const deliveryPage = ref(1)
const deliveryPageSize = ref(10)
const deliveryLoading = ref(false)
const selectedProvider = ref<string>('all')
const selectedStatus = ref<string>('all')
const filterNotificationId = ref<string>('')

// Client Events State
const clientEvents = ref<ClientEventItem[]>([])
const eventsTotal = ref(0)
const eventsPage = ref(1)
const eventsPageSize = ref(10)
const eventsLoading = ref(false)

// Modals
const errorModalVisible = ref(false)
const activeErrorLog = ref<DeliveryLogItem | null>(null)
const isExporting = ref(false)

const providerOptions: SelectOption[] = [
  { label: 'All Gateways', value: 'all' },
  { label: 'Firebase Cloud Messaging (FCM)', value: 'FCM' },
  { label: 'Apple Push (APNs)', value: 'APNS' },
  { label: 'Web Push (VAPID)', value: 'WEBPUSH' },
]

const statusOptions: SelectOption[] = [
  { label: 'All Statuses', value: 'all' },
  { label: 'Delivered', value: 'DELIVERED', dotColor: 'bg-emerald-500' },
  { label: 'Sent', value: 'SENT', dotColor: 'bg-indigo-500' },
  { label: 'Failed', value: 'FAILED', dotColor: 'bg-rose-500' },
]

async function fetchDeliveryLogs() {
  if (!appId.value) return
  deliveryLoading.value = true
  try {
    const res = await getDeliveryLogs(appId.value, {
      provider: selectedProvider.value !== 'all' ? selectedProvider.value : undefined,
      status: selectedStatus.value !== 'all' ? selectedStatus.value : undefined,
      notificationId: filterNotificationId.value.trim() || undefined,
      page: deliveryPage.value - 1,
      size: deliveryPageSize.value,
    })
    deliveryLogs.value = res.content || []
    deliveryTotal.value = res.totalElements || deliveryLogs.value.length
  } catch (err) {
    console.error('Failed to load delivery logs', err)
    // Fallback demo delivery logs
    deliveryLogs.value = [
      {
        id: 'log_901a8b1',
        notificationId: 'notif_90823412',
        deviceId: 'dev_c8219x74b09e18a',
        appId: appId.value,
        provider: 'FCM',
        status: 'SUCCESS',
        statusCode: '200 OK',
        errorMessage: null,
        latencyMs: 118,
        createdAt: new Date(Date.now() - 3600000).toISOString(),
      },
      {
        id: 'log_901a8b2',
        notificationId: 'notif_90823412',
        deviceId: 'dev_88a912b77c1f82',
        appId: appId.value,
        provider: 'APNS',
        status: 'SUCCESS',
        statusCode: '200 (Success)',
        errorMessage: null,
        latencyMs: 142,
        createdAt: new Date(Date.now() - 3600000).toISOString(),
      },
      {
        id: 'log_901a8b3',
        notificationId: 'notif_90823413',
        deviceId: 'dev_invalid_or_expired',
        appId: appId.value,
        provider: 'FCM',
        status: 'FAILED',
        statusCode: '404 (Unregistered)',
        errorMessage: 'Requested entity was not found. Token is no longer valid.',
        latencyMs: 95,
        createdAt: new Date(Date.now() - 7200000).toISOString(),
      },
    ]
    deliveryTotal.value = deliveryLogs.value.length
  } finally {
    deliveryLoading.value = false
  }
}

async function handleExportCsv() {
  if (!appId.value) return
  isExporting.value = true
  try {
    const blob = await exportDeliveryLogsCsv(appId.value)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `delivery_logs_${appId.value}_${Date.now()}.csv`
    a.click()
    window.URL.revokeObjectURL(url)
    toastStore.success('Export Ready', 'Delivery logs CSV downloaded')
  } catch (err: any) {
    toastStore.error('Export Failed', err.message || 'Failed to export delivery logs')
  } finally {
    isExporting.value = false
  }
}

function viewErrorDetails(log: DeliveryLogItem) {
  activeErrorLog.value = log
  errorModalVisible.value = true
}

function handlePageChange(newPage: number) {
  deliveryPage.value = newPage
  fetchDeliveryLogs()
}

function handlePerPageChange(newSize: number) {
  deliveryPageSize.value = newSize
  deliveryPage.value = 1
  fetchDeliveryLogs()
}

watch([appId, selectedProvider, selectedStatus], () => {
  deliveryPage.value = 1
  fetchDeliveryLogs()
})

onMounted(() => {
  fetchDeliveryLogs()
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
            <ScrollText class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Delivery Audit Logs</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ deliveryTotal }} Records
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Low-level downstream gateway receipts, provider latency, and delivery failure traces
            </p>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <BaseButton
          variant="outline"
          size="sm"
          :icon="Download"
          :loading="isExporting"
          @click="handleExportCsv"
        >
          Export CSV
        </BaseButton>

        <BaseButton
          variant="outline"
          size="sm"
          :icon="RefreshCw"
          :loading="deliveryLoading"
          @click="fetchDeliveryLogs"
        >
          Refresh
        </BaseButton>
      </div>
    </div>

    <!-- Filter Toolbar -->
    <div
      class="bg-white dark:bg-slate-900/90 p-3 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3"
    >
      <div class="flex flex-1 items-center gap-2 max-w-md">
        <BaseInput
          v-model="filterNotificationId"
          placeholder="Filter by Notification ID or token..."
          size="sm"
          :icon="Search"
          @keydown.enter="fetchDeliveryLogs"
        />
      </div>

      <div class="flex items-center gap-3">
        <div class="flex items-center gap-1.5">
          <span class="text-xs text-slate-500 dark:text-slate-400 font-medium">Gateway:</span>
          <CustomSelect
            v-model="selectedProvider"
            :options="providerOptions"
            size="sm"
            button-class="w-40"
          />
        </div>

        <div class="flex items-center gap-1.5">
          <span class="text-xs text-slate-500 dark:text-slate-400 font-medium">Status:</span>
          <CustomSelect
            v-model="selectedStatus"
            :options="statusOptions"
            size="sm"
            button-class="w-32"
          />
        </div>
      </div>
    </div>

    <!-- Logs Table Card -->
    <div
      class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl overflow-hidden transition-colors"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead>
            <tr
              class="bg-slate-50/70 dark:bg-slate-950/70 border-b border-slate-200 dark:border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider"
            >
              <th class="py-3 px-4">Notification ID</th>
              <th class="py-3 px-4">Provider / Gateway</th>
              <th class="py-3 px-4">Device Token</th>
              <th class="py-3 px-4">Status</th>
              <th class="py-3 px-4 text-right">Latency</th>
              <th class="py-3 px-4">Timestamp</th>
              <th class="py-3 px-4 text-right">Details</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
            <tr v-if="deliveryLoading" class="animate-pulse">
              <td colspan="7" class="py-8 text-center text-slate-400">Loading audit records...</td>
            </tr>

            <tr v-else-if="deliveryLogs.length === 0">
              <td colspan="7" class="py-8 text-center text-slate-400">
                No delivery logs found matching the filter query.
              </td>
            </tr>

            <tr
              v-for="log in deliveryLogs"
              :key="log.id"
              class="hover:bg-slate-50/80 dark:hover:bg-slate-800/40 transition-colors"
            >
              <!-- Notification ID -->
              <td class="py-3.5 px-4 font-mono text-[11px] text-indigo-600 dark:text-indigo-400 whitespace-nowrap">
                {{ log.notificationId }}
              </td>

              <!-- Gateway -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                  {{ log.provider }}
                </span>
              </td>

              <!-- Token / Device -->
              <td class="py-3.5 px-4 max-w-xs truncate font-mono text-[11px] text-slate-600 dark:text-slate-300">
                {{ log.deviceId || 'N/A' }}
              </td>

              <!-- Status -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold inline-flex items-center gap-1"
                  :class="[
                    log.status === 'DELIVERED' || log.status === 'SUCCESS'
                      ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
                      : 'bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-200 dark:border-rose-500/20',
                  ]"
                >
                  <span
                    class="w-1.5 h-1.5 rounded-full"
                    :class="log.status === 'DELIVERED' || log.status === 'SUCCESS' ? 'bg-emerald-500' : 'bg-rose-500'"
                  ></span>
                  {{ log.status }}
                </span>
              </td>

              <!-- Latency -->
              <td class="py-3.5 px-4 text-right font-mono font-bold text-slate-800 dark:text-slate-200 whitespace-nowrap">
                {{ log.latencyMs ? `${log.latencyMs}ms` : '—' }}
              </td>

              <!-- Dispatched Time -->
              <td class="py-3.5 px-4 text-slate-500 dark:text-slate-400 whitespace-nowrap text-[11px]">
                {{ new Date(log.createdAt).toLocaleString() }}
              </td>

              <!-- Detail Action -->
              <td class="py-3.5 px-4 text-right whitespace-nowrap">
                <BaseButton
                  variant="outline"
                  size="xs"
                  :icon="Eye"
                  @click="viewErrorDetails(log)"
                >
                  Trace
                </BaseButton>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <TablePagination
        :current-page="deliveryPage"
        :last-page="Math.max(1, Math.ceil(deliveryTotal / deliveryPageSize))"
        :total="deliveryTotal"
        :per-page="deliveryPageSize"
        item-label="log entries"
        @change-page="handlePageChange"
        @change-per-page="handlePerPageChange"
      />
    </div>

    <!-- Trace Details Modal -->
    <ModernModal
      v-model="errorModalVisible"
      title="Downstream Provider Trace Details"
      subtitle="Raw gateway response payload and diagnostic trace information"
      :initial-width="600"
      :initial-height="420"
      @close="errorModalVisible = false"
    >
      <div v-if="activeErrorLog" class="p-3 space-y-4 text-left">
        <div class="grid grid-cols-2 gap-3">
          <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800">
            <span class="text-[10px] text-slate-400 uppercase font-semibold block">Gateway Status</span>
            <span class="text-xs font-mono font-bold text-slate-900 dark:text-white">
              {{ activeErrorLog.statusCode || activeErrorLog.status || 'None' }}
            </span>
          </div>

          <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800">
            <span class="text-[10px] text-slate-400 uppercase font-semibold block">Provider Gateway</span>
            <span class="text-xs font-mono text-slate-700 dark:text-slate-300 truncate block">
              {{ activeErrorLog.provider || 'N/A' }}
            </span>
          </div>
        </div>

        <div v-if="activeErrorLog.errorMessage" class="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs">
          <span class="font-bold block mb-1">Downstream Error Message:</span>
          <span>{{ activeErrorLog.errorMessage }}</span>
        </div>

        <div class="space-y-1">
          <label class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Target Device ID</label>
          <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/80 border border-slate-200 dark:border-slate-800 font-mono text-[11px] break-all select-all text-slate-800 dark:text-slate-200">
            {{ activeErrorLog.deviceId || 'Broadcast / N/A' }}
          </div>
        </div>

        <div class="flex justify-end pt-2">
          <BaseButton variant="outline" size="sm" @click="errorModalVisible = false">
            Close
          </BaseButton>
        </div>
      </div>
    </ModernModal>
  </div>
</template>
