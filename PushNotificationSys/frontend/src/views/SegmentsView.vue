<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { segmentsApi, type Segment, type FilterRule } from '@/api/segments'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseTabs, { type TabItem } from '@/components/common/BaseTabs.vue'
import CustomTable, { type TableColumn } from '@/components/common/CustomTable.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import MetricCard from '@/components/dashboard/MetricCard.vue'
import {
  Apple,
  Calendar,
  Check,
  Clock,
  Code2,
  Copy,
  Filter,
  Globe,
  Layers,
  LayoutGrid,
  List,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Send,
  SlidersHorizontal,
  Smartphone,
  Sparkles,
  Tag,
  Trash2,
  Users,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => (route.params.appId as string) || authStore.currentAppId || '')

const segments = ref<Segment[]>([])
const isLoading = ref<boolean>(false)
const searchQuery = ref<string>('')
const selectedPlatformFilter = ref<string>('ALL')

// View mode persisted in localStorage
const VIEW_MODE_KEY = 'bronx_segments_view_mode'
const viewMode = ref<'grid' | 'table'>(
  (localStorage.getItem(VIEW_MODE_KEY) as 'grid' | 'table') === 'table' ? 'table' : 'grid',
)

function setViewMode(mode: 'grid' | 'table') {
  viewMode.value = mode
  localStorage.setItem(VIEW_MODE_KEY, mode)
}

// Copy ID & AST states
const copiedSegmentId = ref<string | null>(null)
const isAstCopied = ref(false)

// AST Inspector Modal
const showAstModal = ref(false)
const selectedSegmentForAst = ref<Segment | null>(null)

// Delete Modal
const showDeleteModal = ref(false)
const segmentToDelete = ref<Segment | null>(null)
const isDeleting = ref(false)

async function fetchSegments() {
  if (!appId.value) return
  isLoading.value = true
  try {
    const list = await segmentsApi.list(appId.value)
    segments.value = list
  } catch (err: any) {
    console.error('Failed to load segments:', err)
    // Fallback demo segments
    segments.value = [
      {
        id: 'seg_vip_users',
        appId: appId.value,
        name: 'VIP High-Value Customers',
        description: 'Users with tier tag gold or platinum active within the past 7 days',
        estimatedCount: 2340,
        active: true,
        filterAst: {
          conjunction: 'AND',
          rules: [{ field: 'tag', tagKey: 'tier', op: 'IN', value: 'gold, platinum' }],
        },
        createdAt: new Date(Date.now() - 864000000).toISOString(),
        updatedAt: new Date(Date.now() - 86400000).toISOString(),
      },
      {
        id: 'seg_android_users',
        appId: appId.value,
        name: 'Android Power Users',
        description: 'Subscribers on Android OS devices targeted for FCM v1 rich push',
        estimatedCount: 4890,
        active: true,
        filterAst: { conjunction: 'AND', rules: [{ field: 'platform', op: 'EQ', value: 'ANDROID' }] },
        createdAt: new Date(Date.now() - 1200000000).toISOString(),
        updatedAt: new Date(Date.now() - 172800000).toISOString(),
      },
      {
        id: 'seg_dormant_30d',
        appId: appId.value,
        name: 'Dormant Re-Engagement Cohort',
        description: 'Users who have not opened the app or clicked notifications in 30+ days',
        estimatedCount: 1720,
        active: true,
        filterAst: { conjunction: 'AND', rules: [{ field: 'last_active_days', op: 'GTE', value: 30 }] },
        createdAt: new Date(Date.now() - 1500000000).toISOString(),
        updatedAt: new Date(Date.now() - 400000000).toISOString(),
      },
    ]
  } finally {
    isLoading.value = false
  }
}

// KPI Computations
const totalReachableAudience = computed(() => {
  return segments.value.reduce((acc, s) => acc + (s.estimatedCount || 0), 0)
})

const totalRulesCount = computed(() => {
  return segments.value.reduce((acc, s) => acc + (s.filterAst?.rules?.length || 0), 0)
})

const segmentsWithRulesCount = computed(() => {
  return segments.value.filter((s) => (s.filterAst?.rules?.length || 0) > 0).length
})

const categoryCounts = computed(() => {
  let ANDROID = 0
  let IOS = 0
  let WEB = 0
  let TAGS = 0
  let RECENCY = 0

  for (const s of segments.value) {
    const rules = s.filterAst?.rules || []
    if (rules.some((r) => r.field === 'platform' && String(r.value).toUpperCase().includes('ANDROID'))) ANDROID++
    if (rules.some((r) => r.field === 'platform' && String(r.value).toUpperCase().includes('IOS'))) IOS++
    if (rules.some((r) => r.field === 'platform' && String(r.value).toUpperCase().includes('WEB'))) WEB++
    if (rules.some((r) => r.field === 'tag')) TAGS++
    if (rules.some((r) => r.field === 'last_active_days')) RECENCY++
  }

  return { ANDROID, IOS, WEB, TAGS, RECENCY }
})

const filterTabs = computed<TabItem[]>(() => [
  { id: 'ALL', label: 'All Cohorts', badge: segments.value.length },
  { id: 'ANDROID', label: 'Android', icon: Smartphone, badge: categoryCounts.value.ANDROID },
  { id: 'IOS', label: 'Apple iOS', icon: Apple, badge: categoryCounts.value.IOS },
  { id: 'WEB', label: 'Web', icon: Globe, badge: categoryCounts.value.WEB },
  { id: 'TAGS', label: 'Tags', icon: Tag, badge: categoryCounts.value.TAGS },
  { id: 'RECENCY', label: 'Recency', icon: Clock, badge: categoryCounts.value.RECENCY },
])

// Visual theme helper per segment
function getSegmentVisualMeta(seg: Segment) {
  const rules = seg.filterAst?.rules || []
  const platformRule = rules.find((r) => r.field === 'platform')
  if (platformRule && rules.length <= 2) {
    const val = String(platformRule.value || '').toUpperCase()
    if (val.includes('ANDROID')) {
      return {
        icon: Smartphone,
        boxClass:
          'bg-emerald-50 dark:bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-200/80 dark:border-emerald-500/30',
      }
    }
    if (val.includes('IOS')) {
      return {
        icon: Apple,
        boxClass:
          'bg-sky-50 dark:bg-sky-500/15 text-sky-600 dark:text-sky-400 border-sky-200/80 dark:border-sky-500/30',
      }
    }
    if (val.includes('WEB')) {
      return {
        icon: Globe,
        boxClass:
          'bg-amber-50 dark:bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-200/80 dark:border-amber-500/30',
      }
    }
  }
  if (rules.some((r) => r.field === 'last_active_days')) {
    return {
      icon: Clock,
      boxClass:
        'bg-violet-50 dark:bg-violet-500/15 text-violet-600 dark:text-violet-400 border-violet-200/80 dark:border-violet-500/30',
    }
  }
  if (rules.some((r) => r.field === 'tag')) {
    return {
      icon: Tag,
      boxClass:
        'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border-indigo-200/80 dark:border-indigo-500/30',
    }
  }
  return {
    icon: Users,
    boxClass:
      'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border-indigo-200/80 dark:border-indigo-500/30',
  }
}

export interface FormattedChip {
  type: 'platform' | 'tag' | 'recency' | 'general'
  prefix: string
  opText?: string
  value: string
  icon: any
  badgeClass: string
  iconClass: string
}

function getCriteriaChips(rules?: FilterRule[]): FormattedChip[] {
  if (!rules || rules.length === 0) return []
  return rules.map((r) => {
    if (r.field === 'platform') {
      const val = String(r.value || '').toUpperCase()
      const isAndroid = val.includes('ANDROID')
      const isIos = val.includes('IOS')
      const isWeb = val.includes('WEB')
      const displayPlatform = isAndroid
        ? 'Android'
        : isIos
        ? 'Apple iOS'
        : isWeb
        ? 'Web Push'
        : String(r.value || 'All Platforms')

      return {
        type: 'platform',
        prefix: 'Platform',
        value: displayPlatform,
        icon: isAndroid ? Smartphone : isIos ? Apple : Globe,
        badgeClass: isAndroid
          ? 'bg-emerald-50/90 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200/80 dark:border-emerald-800/60'
          : isIos
          ? 'bg-sky-50/90 dark:bg-sky-950/40 text-sky-700 dark:text-sky-300 border-sky-200/80 dark:border-sky-800/60'
          : 'bg-amber-50/90 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-amber-200/80 dark:border-amber-800/60',
        iconClass: isAndroid ? 'text-emerald-500' : isIos ? 'text-sky-500' : 'text-amber-500',
      }
    }
    if (r.field === 'tag') {
      const opSign =
        r.op === 'EQ'
          ? '='
          : r.op === 'NEQ'
          ? '≠'
          : r.op === 'IN'
          ? 'in'
          : r.op === 'EXISTS'
          ? 'exists'
          : r.op?.toLowerCase() || '='
      return {
        type: 'tag',
        prefix: r.tagKey || 'tag',
        opText: opSign,
        value: r.op === 'EXISTS' ? '' : String(r.value ?? ''),
        icon: Tag,
        badgeClass:
          'bg-indigo-50/90 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border-indigo-200/80 dark:border-indigo-800/60',
        iconClass: 'text-indigo-500',
      }
    }
    if (r.field === 'last_active_days') {
      const isLte = r.op === 'LTE'
      return {
        type: 'recency',
        prefix: isLte ? 'Active within' : 'Inactive for',
        value: isLte ? `${r.value} days` : `${r.value}+ days`,
        icon: Clock,
        badgeClass:
          'bg-violet-50/90 dark:bg-violet-950/40 text-violet-700 dark:text-violet-300 border-violet-200/80 dark:border-violet-800/60',
        iconClass: 'text-violet-500',
      }
    }
    return {
      type: 'general',
      prefix: r.field,
      opText: r.op || '=',
      value: String(r.value ?? ''),
      icon: Filter,
      badgeClass:
        'bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 border-slate-200/80 dark:border-slate-700/80',
      iconClass: 'text-slate-400',
    }
  })
}

async function copySegmentId(id: string) {
  try {
    await navigator.clipboard.writeText(id)
    copiedSegmentId.value = id
    toastStore.success('Segment ID Copied', id)
    setTimeout(() => {
      if (copiedSegmentId.value === id) copiedSegmentId.value = null
    }, 2000)
  } catch (err) {
    console.error('Failed to copy ID:', err)
  }
}

function openAstInspector(seg: Segment) {
  selectedSegmentForAst.value = seg
  isAstCopied.value = false
  showAstModal.value = true
}

async function copyModalAst() {
  if (!selectedSegmentForAst.value?.filterAst) return
  try {
    await navigator.clipboard.writeText(JSON.stringify(selectedSegmentForAst.value.filterAst, null, 2))
    isAstCopied.value = true
    toastStore.success('Filter AST Copied', 'Segment JSON AST copied to clipboard')
    setTimeout(() => {
      isAstCopied.value = false
    }, 2000)
  } catch (e) {
    console.error('Failed to copy AST:', e)
  }
}

function formatDate(iso?: string) {
  if (!iso) return 'Recently'
  try {
    const d = new Date(iso)
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
  } catch {
    return 'Recently'
  }
}

const filteredSegments = computed(() => {
  let list = segments.value
  if (selectedPlatformFilter.value !== 'ALL') {
    const filter = selectedPlatformFilter.value
    list = list.filter((s) => {
      const rules = s.filterAst?.rules || []
      if (filter === 'ANDROID') {
        return rules.some((r) => r.field === 'platform' && String(r.value).toUpperCase().includes('ANDROID'))
      }
      if (filter === 'IOS') {
        return rules.some((r) => r.field === 'platform' && String(r.value).toUpperCase().includes('IOS'))
      }
      if (filter === 'WEB') {
        return rules.some((r) => r.field === 'platform' && String(r.value).toUpperCase().includes('WEB'))
      }
      if (filter === 'TAGS') {
        return rules.some((r) => r.field === 'tag')
      }
      if (filter === 'RECENCY') {
        return rules.some((r) => r.field === 'last_active_days')
      }
      return true
    })
  }

  if (!searchQuery.value.trim()) return list
  const q = searchQuery.value.toLowerCase().trim()
  return list.filter(
    (s) =>
      s.name.toLowerCase().includes(q) ||
      s.id.toLowerCase().includes(q) ||
      s.description?.toLowerCase().includes(q),
  )
})

const tableColumns: TableColumn[] = [
  { key: 'cohort', label: 'Segment Cohort', width: 250, minWidth: 200 },
  { key: 'criteria', label: 'Targeting Rules', width: 320, minWidth: 220 },
  { key: 'logic', label: 'Match Mode', width: 130, minWidth: 110 },
  { key: 'reach', label: 'Estimated Reach', width: 150, minWidth: 120 },
  { key: 'updated', label: 'Updated', width: 140, minWidth: 110 },
  { key: 'actions', label: 'Actions', width: 220, minWidth: 190, align: 'right', resizable: false },
]

function promptDelete(seg: Segment) {
  segmentToDelete.value = seg
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!segmentToDelete.value || !appId.value) return
  isDeleting.value = true
  try {
    await segmentsApi.delete(appId.value, segmentToDelete.value.id)
    toastStore.success('Segment Deleted', `Segment "${segmentToDelete.value.name}" removed`)
    showDeleteModal.value = false
    segmentToDelete.value = null
    await fetchSegments()
  } catch (err: any) {
    toastStore.error('Delete Failed', err.response?.data?.detail || err.message)
  } finally {
    isDeleting.value = false
  }
}

function editSegment(segId: string) {
  router.push(appId.value ? `/apps/${appId.value}/segments/${segId}/edit` : `/segments/${segId}/edit`)
}

function handleSendCampaign(segId: string) {
  router.push({
    path: appId.value ? `/apps/${appId.value}/notifications/new` : '/notifications/new',
    query: { target: 'SEGMENT', segmentId: segId },
  })
}

watch(appId, () => {
  fetchSegments()
})

onMounted(() => {
  fetchSegments()
})
</script>

<template>
  <div class="space-y-5 text-left">
    <!-- Page Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
      <div class="flex items-center gap-3">
        <div
          class="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/90 dark:border-indigo-500/30 shadow-xs shrink-0"
        >
          <Users class="w-5 h-5" />
        </div>
        <div>
          <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <span>Audience Segments</span>
            <span
              class="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30"
            >
              {{ segments.length }} Configured
            </span>
          </h1>
          <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Create and manage dynamic user cohorts by device platform, attributes, tags, and engagement recency
          </p>
        </div>
      </div>

      <div class="flex items-center gap-2.5">
        <BaseButton
          variant="outline"
          size="sm"
          :icon="RefreshCw"
          :loading="isLoading"
          @click="fetchSegments"
        >
          Refresh
        </BaseButton>

        <BaseButton
          variant="primary"
          size="sm"
          :icon="Plus"
          @click="router.push(appId ? `/apps/${appId}/segments/new` : '/segments/new')"
        >
          Create Segment
        </BaseButton>
      </div>
    </div>

    <!-- Executive KPI Strip (Reusing MetricCard) -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <MetricCard
        title="Active Segments"
        :value="segments.length"
        :icon="Layers"
        variant="indigo"
        subtitle="Dynamic audience cohorts"
        :loading="isLoading"
        :progress-percent="segments.length > 0 ? 100 : 10"
      />

      <MetricCard
        title="Reachable Subscribers"
        :value="`~${totalReachableAudience.toLocaleString()}`"
        :icon="Users"
        variant="emerald"
        subtitle="Estimated matching devices"
        :loading="isLoading"
        :trend="{ value: 'Live EST', isPositive: true }"
        :progress-percent="totalReachableAudience > 0 ? 85 : 8"
      />

      <MetricCard
        title="Evaluating Filter Rules"
        :value="totalRulesCount"
        :icon="SlidersHorizontal"
        variant="sky"
        :subtitle="`Across ${segmentsWithRulesCount} rule-based cohorts`"
        :loading="isLoading"
        :progress-percent="totalRulesCount > 0 ? 75 : 10"
      />
    </div>

    <!-- Executive Search, Category Tabs & View Mode Toolbar -->
    <div
      class="bg-white dark:bg-slate-900/90 p-3 rounded-2xl border border-slate-200/90 dark:border-slate-800 shadow-xs flex flex-col xl:flex-row xl:items-center justify-between gap-3"
    >
      <div class="w-full xl:w-72 2xl:w-80 shrink-0">
        <BaseInput
          v-model="searchQuery"
          placeholder="Search segments by name, description, or ID..."
          size="sm"
          :icon="Search"
        />
      </div>

      <div class="flex items-center gap-2.5 overflow-x-auto no-scrollbar max-w-full justify-between xl:justify-end">
        <div class="overflow-x-auto no-scrollbar shrink-0">
          <BaseTabs v-model="selectedPlatformFilter" :tabs="filterTabs" size="sm" />
        </div>

        <!-- View Switcher: Grid vs Table -->
        <div
          class="h-9 flex items-center p-0.5 sm:p-1 rounded-xl bg-slate-100 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 shrink-0"
        >
          <button
            type="button"
            @click="setViewMode('grid')"
            class="h-7.5 sm:h-8 px-2.5 sm:px-3 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap transition-all cursor-pointer"
            :class="[
              viewMode === 'grid'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
            ]"
            title="Card Grid View"
          >
            <LayoutGrid class="w-3.5 h-3.5 shrink-0" />
            <span class="hidden sm:inline">Grid</span>
          </button>

          <button
            type="button"
            @click="setViewMode('table')"
            class="h-7.5 sm:h-8 px-2.5 sm:px-3 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap transition-all cursor-pointer"
            :class="[
              viewMode === 'table'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
            ]"
            title="Data Table View"
          >
            <List class="w-3.5 h-3.5 shrink-0" />
            <span class="hidden sm:inline">Table</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="isLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4.5">
      <div
        v-for="i in 3"
        :key="i"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 animate-pulse space-y-4"
      >
        <div class="flex items-center justify-between">
          <div class="h-5 bg-slate-200 dark:bg-slate-800 rounded-full w-28"></div>
          <div class="h-6 bg-slate-100 dark:bg-slate-850 rounded-lg w-12"></div>
        </div>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 bg-slate-200 dark:bg-slate-800 rounded-xl shrink-0"></div>
          <div class="space-y-1.5 flex-1">
            <div class="h-4 bg-slate-200 dark:bg-slate-800 rounded w-3/4"></div>
            <div class="h-3 bg-slate-100 dark:bg-slate-850 rounded w-1/2"></div>
          </div>
        </div>
        <div class="h-24 bg-slate-50 dark:bg-slate-950 rounded-xl"></div>
        <div class="grid grid-cols-2 gap-2.5 pt-2">
          <div class="h-9 bg-slate-100 dark:bg-slate-800 rounded-xl"></div>
          <div class="h-9 bg-slate-200 dark:bg-slate-700 rounded-xl"></div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="filteredSegments.length === 0"
      class="bg-white dark:bg-slate-900/90 rounded-2xl p-12 border border-slate-200/90 dark:border-slate-800 text-center space-y-3 shadow-xs"
    >
      <div
        class="w-12 h-12 rounded-2xl bg-slate-100 dark:bg-slate-800 text-slate-400 flex items-center justify-center mx-auto"
      >
        <Users class="w-6 h-6" />
      </div>
      <h3 class="text-sm font-bold text-slate-900 dark:text-white">
        {{ segments.length === 0 ? 'No Audience Segments Configured' : 'No Segments Match Your Filter' }}
      </h3>
      <p class="text-xs text-slate-500 dark:text-slate-400 max-w-sm mx-auto leading-relaxed">
        {{
          segments.length === 0
            ? 'Create audience segments to target high-value subscribers, specific OS platforms, or dormant re-engagement cohorts.'
            : 'Try clearing the search query or selecting a different filter tab.'
        }}
      </p>
      <div class="pt-1 flex items-center justify-center gap-2">
        <BaseButton
          v-if="segments.length > 0"
          variant="outline"
          size="sm"
          @click="searchQuery = ''; selectedPlatformFilter = 'ALL'"
        >
          Clear Filters
        </BaseButton>
        <BaseButton
          variant="primary"
          size="sm"
          :icon="Plus"
          @click="router.push(appId ? `/apps/${appId}/segments/new` : '/segments/new')"
        >
          Create Segment
        </BaseButton>
      </div>
    </div>

    <!-- 1. GRID VIEW (Default) -->
    <div v-else-if="viewMode === 'grid'" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4.5">
      <div
        v-for="seg in filteredSegments"
        :key="seg.id"
        class="group bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200/90 dark:border-slate-800 shadow-xs hover:shadow-md hover:border-indigo-300/80 dark:hover:border-indigo-500/40 transition-all duration-200 flex flex-col justify-between"
      >
        <div>
          <!-- Top Status Pill & Utility Icons -->
          <div class="flex items-center justify-between gap-2 mb-4">
            <span
              v-if="(seg.estimatedCount || 0) > 0"
              class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200/80 dark:border-emerald-500/20"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
              <span>~{{ (seg.estimatedCount || 0).toLocaleString() }} Reachable</span>
            </span>
            <span
              v-else
              class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200/80 dark:border-amber-500/20"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
              <span>0 Reachable</span>
            </span>

            <div class="flex items-center gap-1">
              <button
                type="button"
                @click="openAstInspector(seg)"
                title="Inspect Filter AST"
                class="p-1.5 rounded-lg text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition-colors cursor-pointer"
              >
                <Code2 class="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                @click="promptDelete(seg)"
                title="Delete Segment"
                class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition-colors cursor-pointer"
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          <!-- Cohort Identity Block -->
          <div class="flex items-start gap-3">
            <div
              class="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border shadow-2xs transition-transform duration-200 group-hover:scale-105"
              :class="getSegmentVisualMeta(seg).boxClass"
            >
              <component :is="getSegmentVisualMeta(seg).icon" class="w-4.5 h-4.5" />
            </div>

            <div class="min-w-0 flex-1">
              <h3
                class="text-sm sm:text-[15px] font-bold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors truncate leading-snug"
                :title="seg.name"
              >
                {{ seg.name }}
              </h3>

              <button
                type="button"
                @click.stop="copySegmentId(seg.id)"
                class="inline-flex items-center gap-1 text-[11px] font-mono text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors mt-0.5 cursor-pointer max-w-full"
                title="Copy Segment ID"
              >
                <span class="truncate">{{ seg.id }}</span>
                <Check v-if="copiedSegmentId === seg.id" class="w-3 h-3 text-emerald-500 shrink-0" />
                <Copy v-else class="w-3 h-3 opacity-60 shrink-0" />
              </button>
            </div>
          </div>

          <!-- Description -->
          <p class="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 leading-relaxed mt-2.5 min-h-[36px]">
            {{ seg.description || 'Dynamic cohort targeting specified subscriber and device attributes.' }}
          </p>

          <!-- Inset Targeting Rules Summary Box -->
          <div
            class="mt-3.5 bg-slate-50/80 dark:bg-slate-950/60 rounded-xl p-3.5 border border-slate-200/80 dark:border-slate-800/80 space-y-2.5"
          >
            <!-- Rules Header + Match Logic -->
            <div class="flex items-center justify-between gap-2">
              <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 flex items-center gap-1">
                <Filter class="w-3 h-3" />
                Targeting Rules
              </span>

              <span
                class="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 border border-slate-200/80 dark:border-slate-800 shadow-2xs"
                :title="seg.filterAst?.conjunction === 'OR' ? 'Matches if ANY rule is satisfied' : 'Matches only if ALL rules are satisfied'"
              >
                <template v-if="!seg.filterAst?.rules || seg.filterAst.rules.length === 0">
                  All Devices
                </template>
                <template v-else-if="seg.filterAst.rules.length === 1">
                  1 Active Rule
                </template>
                <template v-else>
                  Match {{ seg.filterAst?.conjunction === 'OR' ? 'ANY' : 'ALL' }} · {{ seg.filterAst.rules.length }} rules
                </template>
              </span>
            </div>

            <!-- Structured Criteria Pills -->
            <div class="flex flex-wrap gap-1.5 min-h-[30px] items-center">
              <span
                v-for="(chip, cIdx) in getCriteriaChips(seg.filterAst?.rules).slice(0, 3)"
                :key="cIdx"
                class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[11px] border shadow-2xs transition-colors"
                :class="chip.badgeClass"
              >
                <component :is="chip.icon" class="w-3 h-3 shrink-0" :class="chip.iconClass" />
                <span class="opacity-80 font-medium">{{ chip.prefix }}</span>
                <span v-if="chip.opText" class="opacity-60 font-mono text-[10px]">{{ chip.opText }}</span>
                <span v-if="chip.value" class="font-semibold">{{ chip.value }}</span>
              </span>

              <button
                v-if="(seg.filterAst?.rules?.length || 0) > 3"
                type="button"
                @click="openAstInspector(seg)"
                class="px-2 py-1 rounded-lg text-[11px] font-semibold bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-800 hover:border-indigo-300 transition cursor-pointer"
              >
                +{{ (seg.filterAst?.rules?.length || 0) - 3 }} more
              </button>

              <span
                v-if="!seg.filterAst?.rules || seg.filterAst.rules.length === 0"
                class="text-[11px] text-slate-400 dark:text-slate-500 italic flex items-center gap-1.5"
              >
                <Sparkles class="w-3.5 h-3.5 text-amber-500 shrink-0" />
                <span>All registered devices match (broadcast)</span>
              </span>
            </div>

            <!-- Metadata Footer Inside Inset Box -->
            <div
              class="pt-2.5 border-t border-slate-200/70 dark:border-slate-800/70 flex items-center justify-between text-[11px]"
            >
              <span class="text-slate-400 dark:text-slate-500 flex items-center gap-1.5">
                <Calendar class="w-3 h-3" />
                <span>{{ formatDate(seg.updatedAt || seg.createdAt) }}</span>
              </span>

              <button
                type="button"
                @click="openAstInspector(seg)"
                class="inline-flex items-center gap-1 font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 transition-colors cursor-pointer"
              >
                <Code2 class="w-3 h-3" />
                <span>AST JSON</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Clean Balanced Card Footer Actions -->
        <div class="pt-4 mt-4 border-t border-slate-100 dark:border-slate-800 grid grid-cols-2 gap-2.5">
          <BaseButton
            variant="outline"
            size="sm"
            class="w-full"
            :icon="Pencil"
            @click="editSegment(seg.id)"
          >
            Edit Rules
          </BaseButton>

          <BaseButton
            variant="primary"
            size="sm"
            class="w-full"
            :icon="Send"
            @click="handleSendCampaign(seg.id)"
          >
            Send Push
          </BaseButton>
        </div>
      </div>
    </div>

    <!-- 2. TABLE VIEW -->
    <CustomTable
      v-else
      table-id="segments-table"
      :columns="tableColumns"
      :items="filteredSegments"
      :is-loading="isLoading"
      empty-message="No audience segments found."
    >
      <template #cell-cohort="{ row }">
        <div class="flex items-center gap-3">
          <div
            class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border"
            :class="getSegmentVisualMeta(row).boxClass"
          >
            <component :is="getSegmentVisualMeta(row).icon" class="w-4 h-4" />
          </div>
          <div class="min-w-0">
            <div class="font-bold text-slate-900 dark:text-white truncate max-w-[180px]" :title="row.name">
              {{ row.name }}
            </div>
            <div class="text-[11px] font-mono text-slate-400 truncate max-w-[180px]">
              {{ row.id }}
            </div>
          </div>
        </div>
      </template>

      <template #cell-criteria="{ row }">
        <div class="flex flex-wrap gap-1">
          <span
            v-for="(chip, cIdx) in getCriteriaChips(row.filterAst?.rules).slice(0, 2)"
            :key="cIdx"
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] border"
            :class="chip.badgeClass"
          >
            <component :is="chip.icon" class="w-3 h-3 shrink-0" :class="chip.iconClass" />
            <span class="opacity-80">{{ chip.prefix }}</span>
            <span v-if="chip.opText" class="opacity-60 font-mono text-[10px]">{{ chip.opText }}</span>
            <span v-if="chip.value" class="font-semibold">{{ chip.value }}</span>
          </span>
          <span
            v-if="(row.filterAst?.rules?.length || 0) > 2"
            class="text-[11px] text-slate-500 font-semibold px-1.5 py-0.5"
          >
            +{{ (row.filterAst?.rules?.length || 0) - 2 }} more
          </span>
          <span
            v-if="!row.filterAst?.rules || row.filterAst.rules.length === 0"
            class="text-xs text-slate-400 italic"
          >
            Broadcast (All Devices)
          </span>
        </div>
      </template>

      <template #cell-logic="{ row }">
        <span
          class="px-2 py-0.5 rounded-md text-[11px] font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700"
        >
          Match {{ row.filterAst?.conjunction === 'OR' ? 'ANY' : 'ALL' }}
        </span>
      </template>

      <template #cell-reach="{ row }">
        <span
          v-if="(row.estimatedCount || 0) > 0"
          class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200/80 dark:border-emerald-500/20"
        >
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
          ~{{ (row.estimatedCount || 0).toLocaleString() }}
        </span>
        <span
          v-else
          class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200/80 dark:border-amber-500/20"
        >
          0
        </span>
      </template>

      <template #cell-updated="{ row }">
        <span class="text-xs text-slate-500 dark:text-slate-400">
          {{ formatDate(row.updatedAt || row.createdAt) }}
        </span>
      </template>

      <template #cell-actions="{ row }">
        <div class="flex items-center justify-end gap-1.5">
          <BaseButton variant="ghost" size="xs" :icon="Code2" title="Inspect AST" @click="openAstInspector(row)" />
          <BaseButton variant="outline" size="xs" :icon="Pencil" @click="editSegment(row.id)">
            Edit
          </BaseButton>
          <BaseButton variant="primary" size="xs" :icon="Send" @click="handleSendCampaign(row.id)">
            Send
          </BaseButton>
          <button
            type="button"
            @click="promptDelete(row)"
            title="Delete Segment"
            class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition cursor-pointer"
          >
            <Trash2 class="w-3.5 h-3.5" />
          </button>
        </div>
      </template>
    </CustomTable>

    <!-- AST Inspector Modal -->
    <ModernModal
      v-model="showAstModal"
      :title="`${selectedSegmentForAst?.name || 'Segment'} — Filter AST`"
      subtitle="Declarative targeting specification & rule evaluation tree"
      :icon="Code2"
      :initial-width="640"
      size="lg"
      @close="showAstModal = false"
    >
      <div v-if="selectedSegmentForAst" class="p-6 space-y-5 text-left">
        <!-- Overview Bar -->
        <div
          class="grid grid-cols-3 gap-3 p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800"
        >
          <div>
            <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">Estimated Reach</span>
            <span class="text-sm font-bold text-emerald-600 dark:text-emerald-400">
              ~{{ (selectedSegmentForAst.estimatedCount || 0).toLocaleString() }} devices
            </span>
          </div>
          <div>
            <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">Match Logic</span>
            <span class="text-sm font-bold text-slate-900 dark:text-white">
              {{ selectedSegmentForAst.filterAst?.conjunction === 'OR' ? 'Match ANY (OR)' : 'Match ALL (AND)' }}
            </span>
          </div>
          <div>
            <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">Active Rules</span>
            <span class="text-sm font-bold text-slate-900 dark:text-white">
              {{ selectedSegmentForAst.filterAst?.rules?.length || 0 }} configured
            </span>
          </div>
        </div>

        <!-- Parsed Rules List -->
        <div class="space-y-2">
          <h4 class="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            Evaluated Criteria
          </h4>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="(chip, idx) in getCriteriaChips(selectedSegmentForAst.filterAst?.rules)"
              :key="idx"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs border shadow-2xs"
              :class="chip.badgeClass"
            >
              <component :is="chip.icon" class="w-3.5 h-3.5 shrink-0" :class="chip.iconClass" />
              <span class="opacity-80 font-medium">{{ chip.prefix }}</span>
              <span v-if="chip.opText" class="opacity-60 font-mono text-[11px]">{{ chip.opText }}</span>
              <span v-if="chip.value" class="font-semibold">{{ chip.value }}</span>
            </span>
            <span
              v-if="!selectedSegmentForAst.filterAst?.rules || selectedSegmentForAst.filterAst.rules.length === 0"
              class="text-xs text-slate-400 italic"
            >
              No filter rules configured — targets all active subscribers.
            </span>
          </div>
        </div>

        <!-- JSON Terminal Box -->
        <div class="rounded-2xl bg-slate-950 border border-slate-800 overflow-hidden shadow-inner">
          <div class="flex items-center justify-between px-4 py-2.5 bg-slate-900/90 border-b border-slate-800">
            <span class="text-[11px] font-mono text-slate-300 flex items-center gap-2">
              <Code2 class="w-3.5 h-3.5 text-indigo-400" />
              filter_ast.json
            </span>
            <BaseButton
              variant="ghost"
              size="xs"
              :icon="isAstCopied ? Check : Copy"
              class="text-slate-300 hover:text-white hover:bg-slate-800"
              @click="copyModalAst"
            >
              {{ isAstCopied ? 'Copied!' : 'Copy JSON' }}
            </BaseButton>
          </div>
          <div class="p-4 text-xs font-mono text-emerald-400 overflow-x-auto max-h-64 scrollbar-thin">
            <pre class="leading-relaxed">{{ JSON.stringify(selectedSegmentForAst.filterAst, null, 2) }}</pre>
          </div>
        </div>
      </div>

      <template #footer>
        <BaseButton variant="ghost" @click="showAstModal = false">
          Close
        </BaseButton>
        <BaseButton
          v-if="selectedSegmentForAst"
          variant="primary"
          :icon="Pencil"
          @click="showAstModal = false; editSegment(selectedSegmentForAst.id)"
        >
          Edit Rules
        </BaseButton>
      </template>
    </ModernModal>

    <!-- Delete Confirmation Modal -->
    <ModernModal
      v-model="showDeleteModal"
      title="Delete Audience Segment"
      subtitle="Remove dynamic cohort from application"
      :icon="Trash2"
      icon-class="text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-500/10 border-rose-200 dark:border-rose-500/20"
      :initial-width="480"
      @close="showDeleteModal = false"
    >
      <div class="p-6 space-y-4 text-left">
        <p class="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
          Are you sure you want to delete segment
          <strong class="text-slate-900 dark:text-white font-semibold">{{ segmentToDelete?.name }}</strong>?
          Scheduled campaigns targeting this segment will not be dispatched.
        </p>
      </div>

      <template #footer>
        <BaseButton variant="ghost" @click="showDeleteModal = false">
          Cancel
        </BaseButton>
        <BaseButton variant="danger" :loading="isDeleting" @click="confirmDelete">
          Delete Segment
        </BaseButton>
      </template>
    </ModernModal>
  </div>
</template>
