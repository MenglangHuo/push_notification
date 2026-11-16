<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle, CheckCircle2, XCircle, TrendingDown } from 'lucide-vue-next'
import AnimatedCounter from './AnimatedCounter.vue'

export interface FailureCategoryItem {
  reason: string
  count: number
  percentage: number
}

const props = withDefaults(
  defineProps<{
    categories: FailureCategoryItem[]
    loading?: boolean
  }>(),
  {
    loading: false,
  },
)

const palette = [
  { bar: 'from-rose-500 to-rose-400', bg: 'bg-rose-50 dark:bg-rose-500/10', text: 'text-rose-600 dark:text-rose-400', dot: 'bg-rose-500', ring: '#f43f5e' },
  { bar: 'from-amber-500 to-amber-400', bg: 'bg-amber-50 dark:bg-amber-500/10', text: 'text-amber-600 dark:text-amber-400', dot: 'bg-amber-500', ring: '#f59e0b' },
  { bar: 'from-orange-500 to-orange-400', bg: 'bg-orange-50 dark:bg-orange-500/10', text: 'text-orange-600 dark:text-orange-400', dot: 'bg-orange-500', ring: '#f97316' },
  { bar: 'from-red-500 to-red-400', bg: 'bg-red-50 dark:bg-red-500/10', text: 'text-red-600 dark:text-red-400', dot: 'bg-red-500', ring: '#ef4444' },
  { bar: 'from-pink-500 to-pink-400', bg: 'bg-pink-50 dark:bg-pink-500/10', text: 'text-pink-600 dark:text-pink-400', dot: 'bg-pink-500', ring: '#ec4899' },
  { bar: 'from-violet-500 to-violet-400', bg: 'bg-violet-50 dark:bg-violet-500/10', text: 'text-violet-600 dark:text-violet-400', dot: 'bg-violet-500', ring: '#8b5cf6' },
]

const totalFailures = computed(() => {
  return (props.categories || []).reduce((sum, item) => sum + (item.count || 0), 0)
})

const maxCount = computed(() => {
  if (!props.categories?.length) return 1
  return Math.max(...props.categories.map((c) => c.count), 1)
})

const sortedCategories = computed(() => {
  return [...(props.categories || [])].sort((a, b) => b.count - a.count)
})

// Build SVG ring segments for the compact visual summary
const ringSegments = computed(() => {
  const cats = sortedCategories.value
  if (!cats.length) return []
  const radius = 28
  const circumference = 2 * Math.PI * radius
  let offset = 0
  return cats.map((cat, idx) => {
    const pct = totalFailures.value > 0 ? cat.count / totalFailures.value : 0
    const length = pct * circumference
    const gap = cats.length > 1 ? 3 : 0
    const segment = {
      color: palette[idx % palette.length].ring,
      dasharray: `${Math.max(0, length - gap)} ${circumference - Math.max(0, length - gap)}`,
      dashoffset: -offset,
    }
    offset += length
    return segment
  })
})

function getStyle(idx: number) {
  return palette[idx % palette.length]
}

function truncateReason(reason: string, max: number = 50): string {
  if (!reason) return 'Unknown error'
  return reason.length > max ? reason.substring(0, max) + '…' : reason
}
</script>

<template>
  <div
    class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl transition-colors flex flex-col"
  >
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div>
        <h3 class="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
          <AlertTriangle class="w-4 h-4 text-rose-500 dark:text-rose-400" />
          <span>Failure Analysis</span>
        </h3>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Error classification & rejection breakdown
        </p>
      </div>
      <span
        v-if="totalFailures > 0 && !loading"
        class="px-2.5 py-1 rounded-full text-[11px] font-semibold bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-200/80 dark:border-rose-500/20"
      >
        {{ totalFailures.toLocaleString() }} total
      </span>
    </div>

    <!-- Skeleton Loading -->
    <div v-if="loading" class="space-y-3 flex-1">
      <div class="flex items-center gap-3 animate-pulse" v-for="i in 3" :key="i">
        <div class="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-800 shrink-0"></div>
        <div class="flex-1 space-y-1.5">
          <div class="h-3 w-2/3 bg-slate-100 dark:bg-slate-800 rounded"></div>
          <div class="h-5 w-full bg-slate-100 dark:bg-slate-800 rounded-full"></div>
        </div>
        <div class="h-4 w-10 bg-slate-100 dark:bg-slate-800 rounded shrink-0"></div>
      </div>
    </div>

    <!-- Empty State: Zero Failures -->
    <div
      v-else-if="totalFailures === 0 || !categories?.length"
      class="py-10 flex flex-col items-center justify-center text-center flex-1"
    >
      <div
        class="w-14 h-14 rounded-2xl bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mb-3 border border-emerald-200/80 dark:border-emerald-500/20"
      >
        <CheckCircle2 class="w-7 h-7" />
      </div>
      <p class="text-sm font-bold text-slate-800 dark:text-slate-100">100% Clean Dispatch</p>
      <p class="text-xs text-slate-500 dark:text-slate-400 max-w-xs mt-1">
        No delivery errors or rejected device tokens recorded in this period. All notifications delivered successfully.
      </p>
    </div>

    <!-- Failure Content -->
    <div v-else class="flex flex-col flex-1 gap-4">
      <!-- Compact Ring Summary + Top Stats -->
      <div class="flex items-center gap-4 p-3 rounded-xl bg-slate-50/80 dark:bg-slate-800/30 border border-slate-100 dark:border-slate-800/50">
        <!-- Mini SVG Ring -->
        <div class="relative shrink-0">
          <svg width="68" height="68" viewBox="0 0 68 68" class="rotate-[-90deg]">
            <!-- Track -->
            <circle cx="34" cy="34" r="28" fill="none" stroke-width="6"
              class="stroke-slate-100 dark:stroke-slate-800" />
            <!-- Segments -->
            <circle
              v-for="(seg, idx) in ringSegments" :key="idx"
              cx="34" cy="34" r="28" fill="none" stroke-width="6"
              :stroke="seg.color"
              :stroke-dasharray="seg.dasharray"
              :stroke-dashoffset="seg.dashoffset"
              stroke-linecap="round"
              class="transition-all duration-700"
            />
          </svg>
          <div class="absolute inset-0 flex items-center justify-center">
            <XCircle class="w-4 h-4 text-rose-400 dark:text-rose-500" />
          </div>
        </div>

        <!-- Summary Stats -->
        <div class="flex-1 min-w-0">
          <div class="flex items-baseline gap-1.5">
            <span class="text-2xl font-extrabold tracking-tight text-slate-900 dark:text-white tabular-nums">
              <AnimatedCounter :value="totalFailures" :duration="600" />
            </span>
            <span class="text-xs font-medium text-slate-500 dark:text-slate-400">failures</span>
          </div>
          <div class="flex items-center gap-1 mt-0.5">
            <TrendingDown class="w-3 h-3 text-rose-500" />
            <span class="text-[11px] font-medium text-rose-600 dark:text-rose-400">
              {{ sortedCategories.length }} error {{ sortedCategories.length === 1 ? 'type' : 'types' }} detected
            </span>
          </div>
        </div>
      </div>

      <!-- Horizontal Bar Breakdown -->
      <div class="space-y-2.5 flex-1">
        <div
          v-for="(cat, idx) in sortedCategories"
          :key="cat.reason || idx"
          class="group"
        >
          <!-- Reason Label Row -->
          <div class="flex items-center justify-between mb-1">
            <div class="flex items-center gap-2 min-w-0 flex-1 pr-2">
              <span
                class="w-2 h-2 rounded-full shrink-0"
                :class="getStyle(idx).dot"
              ></span>
              <span
                class="text-xs font-medium text-slate-700 dark:text-slate-300 truncate"
                :title="cat.reason"
              >
                {{ truncateReason(cat.reason) }}
              </span>
            </div>
            <div class="flex items-center gap-2 shrink-0">
              <span class="text-xs font-bold tabular-nums text-slate-900 dark:text-white">
                {{ cat.count.toLocaleString() }}
              </span>
              <span
                class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold tabular-nums border"
                :class="[getStyle(idx).bg, getStyle(idx).text, 'border-transparent']"
              >
                {{ cat.percentage.toFixed(1) }}%
              </span>
            </div>
          </div>

          <!-- Progress Bar -->
          <div class="h-2 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
            <div
              class="h-full rounded-full bg-gradient-to-r transition-all duration-700 ease-out"
              :class="getStyle(idx).bar"
              :style="{ width: `${Math.max(4, (cat.count / maxCount) * 100)}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
