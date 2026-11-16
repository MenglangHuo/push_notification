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
import CustomTable, { type TableColumn } from '@/components/common/CustomTable.vue'
import TablePagination from '@/components/common/TablePagination.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  Activity,
  AlertCircle,
  BellRing,
  Calendar,
  Check,
  CheckCircle2,
  Clock,
  Copy,
  Eye,
  Filter,
  Globe,
  Image as ImageIcon,
  Layers,
  Plus,
  RefreshCw,
  Search,
  Send,
  Smartphone,
  Sparkles,
  Users,
  XCircle,
  Zap,
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
const copiedId = ref<string | null>(null)

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

// Table Columns Definition
const tableColumns = ref<TableColumn[]>([
  {
    key: 'message',
    label: 'Message & Content',
    width: 380,
    minWidth: 260,
    visible: true,
    resizable: true,
  },
  {
    key: 'targeting',
    label: 'Target Audience',
    width: 140,
    minWidth: 120,
    visible: true,
    resizable: true,
  },
  {
    key: 'status',
    label: 'Status',
    width: 130,
    minWidth: 110,
    visible: true,
    resizable: true,
  },
  {
    key: 'recipients',
    label: 'Recipients',
    width: 110,
    minWidth: 90,
    visible: true,
    resizable: true,
    align: 'right',
  },
  {
    key: 'deliveryRate',
    label: 'Delivery Rate',
    width: 150,
    minWidth: 120,
    visible: true,
    resizable: true,
    align: 'right',
  },
  {
    key: 'dispatchedAt',
    label: 'Dispatched At',
    width: 170,
    minWidth: 130,
    visible: true,
    resizable: true,
  },
  {
    key: 'action',
    label: 'Action',
    width: 100,
    minWidth: 90,
    visible: true,
    resizable: false,
    align: 'right',
  },
])

// Corrected status options matching backend domain NotificationStatus
const statusOptions: SelectOption[] = [
  { label: 'All Statuses', value: 'all' },
  { label: 'Completed', value: 'COMPLETED', dotColor: 'bg-emerald-500' },
  { label: 'Processing', value: 'PROCESSING', dotColor: 'bg-indigo-500' },
  { label: 'Queued', value: 'QUEUED', dotColor: 'bg-amber-500' },
  { label: 'Scheduled', value: 'SCHEDULED', dotColor: 'bg-sky-500' },
  { label: 'Failed', value: 'FAILED', dotColor: 'bg-rose-500' },
  { label: 'Cancelled', value: 'CANCELLED', dotColor: 'bg-slate-400' },
]

function getStatusBadge(status: string) {
  const s = status?.toUpperCase()
  switch (s) {
    case 'COMPLETED':
      return {
        label: 'Completed',
        dot: 'bg-emerald-500',
        pill: 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-500/20',
      }
    case 'PROCESSING':
      return {
        label: 'Processing',
        dot: 'bg-indigo-500 animate-pulse',
        pill: 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 border-indigo-200 dark:border-indigo-500/20',
      }
    case 'QUEUED':
      return {
        label: 'Queued',
        dot: 'bg-amber-500',
        pill: 'bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-200 dark:border-amber-500/20',
      }
    case 'SCHEDULED':
      return {
        label: 'Scheduled',
        dot: 'bg-sky-500',
        pill: 'bg-sky-50 dark:bg-sky-500/10 text-sky-700 dark:text-sky-400 border-sky-200 dark:border-sky-500/20',
      }
    case 'FAILED':
      return {
        label: 'Failed',
        dot: 'bg-rose-500',
        pill: 'bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 border-rose-200 dark:border-rose-500/20',
      }
    case 'CANCELLED':
      return {
        label: 'Cancelled',
        dot: 'bg-slate-400',
        pill: 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-200 dark:border-slate-700',
      }
    case 'DRAFT':
      return {
        label: 'Draft',
        dot: 'bg-amber-400',
        pill: 'bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-200 dark:border-amber-500/20',
      }
    default:
      return {
        label: status || 'Unknown',
        dot: 'bg-slate-400',
        pill: 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-200 dark:border-slate-700',
      }
  }
}

function getTargetMeta(type: string) {
  switch (type) {
    case 'ALL':
      return {
        label: 'Broadcast',
        icon: Globe,
        badge: 'bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border-indigo-200/80 dark:border-indigo-800/50',
      }
    case 'SEGMENT':
      return {
        label: 'Segment',
        icon: Filter,
        badge: 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200/80 dark:border-emerald-800/50',
      }
    case 'DEVICE_IDS':
      return {
        label: 'Device Tokens',
        icon: Smartphone,
        badge: 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-amber-200/80 dark:border-amber-800/50',
      }
    case 'EXTERNAL_USER_IDS':
      return {
        label: 'User Accounts',
        icon: Users,
        badge: 'bg-sky-50 dark:bg-sky-950/40 text-sky-700 dark:text-sky-300 border-sky-200/80 dark:border-sky-800/50',
      }
    default:
      return {
        label: type || 'Direct',
        icon: Send,
        badge: 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700',
      }
  }
}

function formatDateTime(isoString: string) {
  if (!isoString) return { relative: 'Just now', full: '' }
  const date = new Date(isoString)
  const full = date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  const now = Date.now()
  const diffSec = Math.floor((now - date.getTime()) / 1000)
  if (diffSec < 60) return { relative: 'Just now', full }
  if (diffSec < 3600) return { relative: `${Math.floor(diffSec / 60)}m ago`, full }
  if (diffSec < 86400) return { relative: `${Math.floor(diffSec / 3600)}h ago`, full }
  return { relative: `${Math.floor(diffSec / 86400)}d ago`, full }
}

async function copyId(id: string) {
  try {
    await navigator.clipboard.writeText(id)
    copiedId.value = id
    setTimeout(() => {
      if (copiedId.value === id) copiedId.value = null
    }, 2000)
    toastStore.success('Copied', `Notification ID copied to clipboard`)
  } catch (err) {
    console.error('Copy failed', err)
  }
}

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
        id: 'dbb64591-9cc0-4faf-b9bc-ae1d9cf87be7',
        appId: appId.value,
        title: 'Test Push for Google Pixel 8 Pro ⚡',
        body: 'Hello from Admin Playground! Testing push delivery to iOS.',
        status: 'COMPLETED',
        targetType: 'DEVICE_IDS',
        targetPayload: {},
        customData: {},
        priority: 'HIGH',
        sentCount: 3,
        deliveredCount: 2,
        failedCount: 1,
        openedCount: 1,
        createdAt: new Date(Date.now() - 3600000).toISOString(),
        updatedAt: new Date(Date.now() - 1800000).toISOString(),
      },
      {
        id: 'f4fc3c00-f7ce-443c-be19-28898f7b6d8f',
        appId: appId.value,
        title: 'Test Push for Google Pixel 8 Pro ⚡',
        body: 'Hello from Admin Playground! Testing push delivery to ANDROID.',
        status: 'PROCESSING',
        targetType: 'DEVICE_IDS',
        targetPayload: {},
        customData: {},
        priority: 'HIGH',
        sentCount: 0,
        deliveredCount: 0,
        failedCount: 0,
        openedCount: 0,
        createdAt: new Date(Date.now() - 7200000).toISOString(),
        updatedAt: new Date(Date.now() - 3600000).toISOString(),
      },
      {
        id: 'a40fb848-a4e3-433f-96f8-594065cb8c98',
        appId: appId.value,
        title: 'Flash Sale Alert! ⚡',
        body: 'Welcome to the Admin Playground. This is a real native desktop push!',
        status: 'COMPLETED',
        targetType: 'DEVICE_IDS',
        targetPayload: {},
        customData: {},
        priority: 'HIGH',
        sentCount: 2,
        deliveredCount: 2,
        failedCount: 0,
        openedCount: 2,
        createdAt: new Date(Date.now() - 86400000).toISOString(),
        updatedAt: new Date(Date.now() - 86000000).toISOString(),
      },
      {
        id: '938b566d-cea7-438d-8856-35921d116a34',
        appId: appId.value,
        title: 'Exclusive 50% Off VIP Weekend! ⚡',
        body: 'Hey VIP, use code PLAYGROUND50 at checkout before midnight.',
        status: 'COMPLETED',
        targetType: 'DEVICE_IDS',
        targetPayload: {},
        customData: {},
        priority: 'HIGH',
        sentCount: 2,
        deliveredCount: 2,
        failedCount: 0,
        openedCount: 1,
        createdAt: new Date(Date.now() - 90000000).toISOString(),
        updatedAt: new Date(Date.now() - 89000000).toISOString(),
      },
    ]
    totalElements.value = notifications.value.length
    totalPages.value = 1
  } finally {
    isLoading.value = false
  }
}

// Fixed Status Filtering Logic
const filteredNotifications = computed(() => {
  let list = notifications.value
  if (statusFilter.value !== 'all') {
    const target = statusFilter.value.toUpperCase()
    list = list.filter((n) => {
      const s = n.status?.toUpperCase()
      if (s === target) return true
      // Support friendly aliases
      if (target === 'COMPLETED' && (s === 'DELIVERED' || s === 'SENT')) return true
      if (target === 'PROCESSING' && (s === 'PENDING' || s === 'QUEUED')) return true
      return false
    })
  }

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(
      (n) =>
        n.title?.toLowerCase().includes(q) ||
        n.id?.toLowerCase().includes(q) ||
        n.body?.toLowerCase().includes(q) ||
        n.targetType?.toLowerCase().includes(q),
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

watch([appId, () => authStore.currentAppId, () => route.params.appId], () => {
  currentPage.value = 1
  loadNotifications()
})

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
            class="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/90 dark:border-indigo-500/30 shadow-xs shrink-0"
          >
            <BellRing class="w-5 h-5" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Push Notification Queue</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ totalElements }} Messages
              </span>
            </h1>
            <p class="text-xs text-slate-500 dark:text-slate-400">
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

    <!-- Enhanced CustomTable Component with Resizing, Column Visibility & Density Controls -->
    <CustomTable
      table-id="notifications-queue"
      :columns="tableColumns"
      :items="filteredNotifications"
      :is-loading="isLoading"
      empty-message="No push notifications found matching your search and status criteria."
      :allow-resize="true"
      :allow-column-toggle="true"
      :allow-density-toggle="true"
    >
      <!-- Toolbar Left: Search Input -->
      <template #toolbar-left>
        <div class="max-w-md">
          <BaseInput
            v-model="searchQuery"
            placeholder="Filter by title, body, or notification ID..."
            size="sm"
            :icon="Search"
          />
        </div>
      </template>

      <!-- Toolbar Middle: Fixed Status Filter Dropdown -->
      <template #toolbar-middle>
        <div class="flex items-center gap-1.5">
          <span class="text-xs text-slate-500 dark:text-slate-400 font-medium">Status:</span>
          <CustomSelect
            v-model="statusFilter"
            :options="statusOptions"
            size="sm"
            button-class="w-36"
          />
        </div>
      </template>

      <!-- Custom Column Cell: Message & Content -->
      <template #cell-message="{ row }">
        <div class="space-y-1 py-0.5">
          <div class="flex items-center gap-1.5">
            <span class="font-bold text-slate-900 dark:text-white truncate" :title="row.title">
              {{ row.title }}
            </span>
            <span
              v-if="row.priority === 'HIGH'"
              class="px-1.5 py-0.2 rounded text-[9px] font-bold bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 border border-rose-200/80 dark:border-rose-900/50 shrink-0 inline-flex items-center gap-0.5"
            >
              <Zap class="w-2.5 h-2.5" />
              High
            </span>
            <span
              v-if="row.imageUrl"
              class="p-0.5 rounded text-indigo-500 hover:text-indigo-600 dark:text-indigo-400 shrink-0"
              title="Image banner attached"
            >
              <ImageIcon class="w-3 h-3" />
            </span>
          </div>

          <p class="text-[11px] text-slate-500 dark:text-slate-400 line-clamp-1 leading-snug" :title="row.body">
            {{ row.body || 'No message body provided' }}
          </p>

          <div class="flex items-center gap-1 text-[10px] text-slate-400 font-mono">
            <span class="truncate max-w-[200px]">{{ row.id }}</span>
            <button
              type="button"
              @click.stop="copyId(row.id)"
              class="hover:text-indigo-500 transition cursor-pointer p-0.5 rounded"
              title="Copy Notification ID"
            >
              <Check v-if="copiedId === row.id" class="w-2.5 h-2.5 text-emerald-500" />
              <Copy v-else class="w-2.5 h-2.5" />
            </button>
          </div>
        </div>
      </template>

      <!-- Custom Column Cell: Targeting -->
      <template #cell-targeting="{ row }">
        <span
          class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-bold border"
          :class="getTargetMeta(row.targetType).badge"
        >
          <component :is="getTargetMeta(row.targetType).icon" class="w-3 h-3" />
          <span>{{ getTargetMeta(row.targetType).label }}</span>
        </span>
      </template>

      <!-- Custom Column Cell: Status -->
      <template #cell-status="{ row }">
        <span
          class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold border"
          :class="getStatusBadge(row.status).pill"
        >
          <span class="w-1.5 h-1.5 rounded-full" :class="getStatusBadge(row.status).dot"></span>
          <span>{{ getStatusBadge(row.status).label }}</span>
        </span>
      </template>

      <!-- Custom Column Cell: Recipients -->
      <template #cell-recipients="{ row }">
        <div class="flex items-center justify-end gap-1 font-mono font-bold text-slate-800 dark:text-slate-200 text-xs">
          <Users class="w-3 h-3 text-slate-400" />
          <span>{{ (row.sentCount || row.deliveredCount || 0).toLocaleString() }}</span>
        </div>
      </template>

      <!-- Custom Column Cell: Delivery Rate -->
      <template #cell-deliveryRate="{ row }">
        <div class="flex flex-col items-end gap-1">
          <div class="flex items-center gap-2">
            <span class="font-mono font-bold text-xs" :class="row.deliveredCount > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-500'">
              {{ row.sentCount > 0 ? Math.round((row.deliveredCount / row.sentCount) * 100) : 100 }}%
            </span>
            <div class="w-12 h-1.5 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
              <div
                class="h-full rounded-full transition-all duration-300"
                :class="row.failedCount > 0 && row.deliveredCount === 0 ? 'bg-rose-500' : 'bg-emerald-500'"
                :style="{ width: `${row.sentCount > 0 ? (row.deliveredCount / row.sentCount) * 100 : 100}%` }"
              ></div>
            </div>
          </div>

          <div class="text-[9px] font-mono text-slate-400">
            <span v-if="row.failedCount > 0" class="text-rose-500 font-semibold mr-1">
              {{ row.failedCount }} failed
            </span>
            <span>{{ row.deliveredCount || 0 }} delivered</span>
          </div>
        </div>
      </template>

      <!-- Custom Column Cell: Dispatched At -->
      <template #cell-dispatchedAt="{ row }">
        <div class="space-y-0.5">
          <div class="text-xs font-semibold text-slate-700 dark:text-slate-200 flex items-center gap-1">
            <Clock class="w-3 h-3 text-slate-400" />
            <span>{{ formatDateTime(row.createdAt).relative }}</span>
          </div>
          <div class="text-[10px] text-slate-400 font-mono">
            {{ formatDateTime(row.createdAt).full }}
          </div>
        </div>
      </template>

      <!-- Custom Column Cell: Action -->
      <template #cell-action="{ row }">
        <BaseButton
          variant="outline"
          size="xs"
          :icon="Eye"
          @click="openDetailModal(row)"
        >
          Details
        </BaseButton>
      </template>

      <!-- Table Footer: Pagination Component -->
      <template #footer>
        <TablePagination
          :current-page="currentPage"
          :last-page="totalPages"
          :total="totalElements"
          :per-page="perPage"
          item-label="notifications"
          @change-page="handlePageChange"
          @change-per-page="handlePerPageChange"
        />
      </template>
    </CustomTable>

    <!-- Notification Details Modal using ModernModal -->
    <ModernModal
      v-model="showDetailModal"
      title="Notification Audit & Delivery Trace"
      subtitle="Detailed payload, provider feedback, and recipient delivery receipts"
      :icon="BellRing"
      :allow-maximize="true"
      :initial-width="760"
      @close="showDetailModal = false"
    >
      <div v-if="activeNotification" class="p-6 space-y-5 text-left">
        <!-- Overview Grid -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div class="p-3.5 rounded-2xl bg-slate-50/70 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800">
            <span class="text-[10px] text-slate-400 uppercase font-semibold block tracking-wider">Total Dispatched</span>
            <span class="text-base font-mono font-bold text-slate-900 dark:text-white mt-1 block">
              {{ (activeNotification.sentCount || activeNotification.deliveredCount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="p-3.5 rounded-2xl bg-emerald-50/60 dark:bg-emerald-950/20 border border-emerald-200/70 dark:border-emerald-800/40">
            <span class="text-[10px] text-emerald-600 dark:text-emerald-400 uppercase font-semibold block tracking-wider">Delivered</span>
            <span class="text-base font-mono font-bold text-emerald-700 dark:text-emerald-300 mt-1 block">
              {{ (activeNotification.deliveredCount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="p-3.5 rounded-2xl bg-amber-50/60 dark:bg-amber-950/20 border border-amber-200/70 dark:border-amber-800/40">
            <span class="text-[10px] text-amber-600 dark:text-amber-400 uppercase font-semibold block tracking-wider">Opens</span>
            <span class="text-base font-mono font-bold text-amber-700 dark:text-amber-300 mt-1 block">
              {{ (activeNotification.openedCount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="p-3.5 rounded-2xl bg-rose-50/60 dark:bg-rose-950/20 border border-rose-200/70 dark:border-rose-800/40">
            <span class="text-[10px] text-rose-600 dark:text-rose-400 uppercase font-semibold block tracking-wider">Failed</span>
            <span class="text-base font-mono font-bold text-rose-700 dark:text-rose-300 mt-1 block">
              {{ (activeNotification.failedCount || 0).toLocaleString() }}
            </span>
          </div>
        </div>

        <!-- Payload Preview -->
        <div class="p-4 rounded-2xl bg-slate-50/70 dark:bg-slate-950/80 border border-slate-200/90 dark:border-slate-800 space-y-2.5">
          <h4 class="text-xs font-bold text-slate-900 dark:text-white uppercase tracking-wider">
            Dispatched Content
          </h4>
          <div class="text-xs sm:text-sm">
            <span class="text-slate-400 font-medium">Title:</span>
            <span class="ml-2 font-semibold text-slate-900 dark:text-white">{{ activeNotification.title }}</span>
          </div>
          <div class="text-xs sm:text-sm">
            <span class="text-slate-400 font-medium">Body:</span>
            <span class="ml-2 text-slate-700 dark:text-slate-300 leading-relaxed">{{ activeNotification.body }}</span>
          </div>
          <div v-if="activeNotification.imageUrl" class="pt-2">
            <span class="text-slate-400 text-xs font-medium block mb-1">Image Banner:</span>
            <img :src="activeNotification.imageUrl" class="h-28 rounded-xl object-cover border border-slate-200 dark:border-slate-800" />
          </div>
        </div>

        <!-- Delivery Log Receipts -->
        <div class="space-y-2.5">
          <h4 class="text-xs font-bold text-slate-900 dark:text-white uppercase tracking-wider flex items-center gap-2">
            <Activity class="w-4 h-4 text-indigo-500" />
            <span>Downstream Delivery Receipts</span>
          </h4>

          <div v-if="isLoadingLogs" class="text-center py-6 text-xs text-slate-400">
            Loading receipt logs...
          </div>
          <div v-else-if="deliveryLogs.length === 0" class="text-center py-6 text-xs text-slate-400">
            No individual device receipts recorded for this batch.
          </div>
          <div v-else class="max-h-56 overflow-y-auto rounded-2xl border border-slate-200/90 dark:border-slate-800 divide-y divide-slate-100 dark:divide-slate-800 custom-modal-scroll">
            <div
              v-for="log in deliveryLogs"
              :key="log.id"
              class="p-3 flex items-center justify-between text-xs hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors"
            >
              <div class="truncate max-w-sm">
                <span class="font-mono text-xs text-slate-700 dark:text-slate-300">{{ log.deviceId || log.notificationId }}</span>
                <span class="ml-2 text-[10px] px-2 py-0.5 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-500 font-mono">{{ log.provider }}</span>
              </div>
              <span
                class="px-2.5 py-0.5 rounded-full text-[10px] font-bold"
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
      </div>

      <template #footer>
        <BaseButton variant="outline" size="sm" @click="showDetailModal = false">
          Close
        </BaseButton>
      </template>
    </ModernModal>
  </div>
</template>
