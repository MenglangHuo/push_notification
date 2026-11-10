<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { segmentsApi, type Segment } from '@/api/segments'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  AlertCircle,
  Filter,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Send,
  Sparkles,
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

// Delete modal
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
        description: 'Users with tier tag "gold" or "platinum" active in past 7 days',
        estimatedCount: 2340,
        active: true,
        filterAst: { conjunction: 'AND', rules: [{ field: 'tag', tagKey: 'tier', op: 'IN', value: 'gold,platinum' }] },
        createdAt: new Date(Date.now() - 864000000).toISOString(),
        updatedAt: new Date(Date.now() - 86400000).toISOString(),
      },
      {
        id: 'seg_android_users',
        appId: appId.value,
        name: 'Android Power Users (v2.4+)',
        description: 'Subscribers on Android OS running version 2.4.0 or higher',
        estimatedCount: 4890,
        active: true,
        filterAst: { conjunction: 'AND', rules: [{ field: 'platform', op: 'EQ', value: 'ANDROID' }] },
        createdAt: new Date(Date.now() - 1200000000).toISOString(),
        updatedAt: new Date(Date.now() - 172800000).toISOString(),
      },
      {
        id: 'seg_dormant_30d',
        appId: appId.value,
        name: 'Dormant Re-Engagement (30d+)',
        description: 'Users who have not opened notifications or visited the app in past 30 days',
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

const filteredSegments = computed(() => {
  if (!searchQuery.value.trim()) return segments.value
  const q = searchQuery.value.toLowerCase().trim()
  return segments.value.filter(
    (s) =>
      s.name.toLowerCase().includes(q) ||
      s.id.toLowerCase().includes(q) ||
      s.description?.toLowerCase().includes(q),
  )
})

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

function handleSendCampaign(segId: string) {
  router.push({
    path: appId.value ? `/apps/${appId.value}/notifications/new` : '/notifications/new',
    query: { target: 'SEGMENT', segmentId: segId },
  })
}

onMounted(() => {
  fetchSegments()
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
            <Users class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Audience Segments</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ segments.length }} Configured
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Dynamic rule-based audience segmentation using user tags, device platform, and recency
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

    <!-- Filter Bar -->
    <div
      class="bg-white dark:bg-slate-900/90 p-3 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm flex items-center justify-between gap-3"
    >
      <div class="flex-1 max-w-md">
        <BaseInput
          v-model="searchQuery"
          placeholder="Filter segments by name, description, or ID..."
          size="sm"
          :icon="Search"
        />
      </div>
    </div>

    <!-- Segments Grid -->
    <div v-if="isLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="i in 3"
        :key="i"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 animate-pulse space-y-3"
      >
        <div class="h-4 bg-slate-200 dark:bg-slate-800 rounded w-2/3"></div>
        <div class="h-3 bg-slate-100 dark:bg-slate-850 rounded w-full"></div>
      </div>
    </div>

    <div v-else-if="filteredSegments.length === 0" class="bg-white dark:bg-slate-900/90 rounded-2xl p-10 border border-slate-200 dark:border-slate-800 text-center space-y-3">
      <Users class="w-10 h-10 text-slate-400 mx-auto" />
      <h3 class="text-sm font-semibold text-slate-900 dark:text-white">
        No Audience Segments Found
      </h3>
      <p class="text-xs text-slate-500 max-w-sm mx-auto">
        Create audience segments to target high-value users, specific device OS platforms, or inactive cohorts.
      </p>
      <BaseButton
        variant="primary"
        size="sm"
        :icon="Plus"
        @click="router.push(appId ? `/apps/${appId}/segments/new` : '/segments/new')"
      >
        Create Segment
      </BaseButton>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="seg in filteredSegments"
        :key="seg.id"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl flex flex-col justify-between hover:border-indigo-300 dark:hover:border-indigo-500/40 transition-all"
      >
        <div>
          <!-- Header Pill -->
          <div class="flex items-center justify-between mb-3">
            <span
              class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30 flex items-center gap-1"
            >
              <Users class="w-3 h-3" />
              ~{{ (seg.estimatedCount || 0).toLocaleString() }} Reachable
            </span>

            <button
              @click="promptDelete(seg)"
              class="p-1 rounded-lg text-slate-400 hover:text-rose-500 transition cursor-pointer"
            >
              <Trash2 class="w-3.5 h-3.5" />
            </button>
          </div>

          <!-- Title & Description -->
          <h3 class="text-sm font-bold text-slate-900 dark:text-white mb-1">
            {{ seg.name }}
          </h3>
          <p class="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 mb-4 leading-relaxed">
            {{ seg.description || 'No description provided.' }}
          </p>

          <!-- Criteria Box -->
          <div
            class="bg-slate-50 dark:bg-slate-950/60 p-3 rounded-xl border border-slate-200 dark:border-slate-800/80 text-xs mb-4 space-y-1"
          >
            <div class="text-[10px] text-slate-400 uppercase font-semibold">Rules Criteria</div>
            <div class="font-mono text-[11px] text-slate-700 dark:text-slate-300 truncate">
              {{ JSON.stringify(seg.filterAst) }}
            </div>
          </div>
        </div>

        <!-- Action Row -->
        <div class="pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between gap-2">
          <BaseButton
            variant="outline"
            size="xs"
            :icon="Pencil"
            @click="router.push(appId ? `/apps/${appId}/segments/${seg.id}/edit` : `/segments/${seg.id}/edit`)"
          >
            Edit
          </BaseButton>

          <BaseButton
            variant="primary"
            size="xs"
            :icon="Send"
            @click="handleSendCampaign(seg.id)"
          >
            Send Push
          </BaseButton>
        </div>
      </div>
    </div>

    <!-- Delete Modal -->
    <ModernModal
      v-model="showDeleteModal"
      title="Delete Audience Segment"
      subtitle="Remove dynamic cohort from application"
      :initial-width="440"
      :initial-height="240"
      @close="showDeleteModal = false"
    >
      <div class="p-2 space-y-4 text-left">
        <p class="text-xs text-slate-600 dark:text-slate-300">
          Are you sure you want to delete segment
          <strong class="text-slate-900 dark:text-white">{{ segmentToDelete?.name }}</strong>?
          Scheduled campaigns targeting this segment will not be dispatched.
        </p>

        <div class="flex items-center justify-end gap-2 pt-2">
          <BaseButton variant="ghost" @click="showDeleteModal = false">
            Cancel
          </BaseButton>
          <BaseButton variant="danger" :loading="isDeleting" @click="confirmDelete">
            Delete Segment
          </BaseButton>
        </div>
      </div>
    </ModernModal>
  </div>
</template>
