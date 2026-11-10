<script setup lang="ts">
import { ref, computed } from 'vue'
import { MoreHorizontal } from 'lucide-vue-next'

export interface DonutCategory {
  key: string
  label: string
  color: string
}

const props = withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    centerLabel?: string
    byPlatform?: Record<string, number>
    byStatus?: Record<string, number>
    loading?: boolean
  }>(),
  {
    title: 'Platform Distribution',
    subtitle: 'Reachable devices by operating system',
    centerLabel: 'Devices',
    loading: false,
  },
)

const activeHoverKey = ref<string | null>(null)
const pinnedKey = ref<string | null>(null)
const showTotalToggle = ref(false)

const platformMeta: DonutCategory[] = [
  { key: 'ANDROID', label: 'Android', color: '#10b981' },
  { key: 'IOS', label: 'Apple iOS', color: '#0ea5e9' },
  { key: 'WEB', label: 'Web Push', color: '#f59e0b' },
  { key: 'OTHER', label: 'Other', color: '#6366f1' },
]

const activeData = computed<Record<string, number>>(() => {
  if (props.byPlatform) {
    const res: Record<string, number> = {}
    for (const [k, v] of Object.entries(props.byPlatform)) {
      res[k.toUpperCase()] = v
    }
    return res
  }
  if (props.byStatus) {
    return props.byStatus
  }
  return { ANDROID: 0, IOS: 0, WEB: 0 }
})

const total = computed(() => {
  return Object.values(activeData.value).reduce((acc, val) => acc + (val || 0), 0)
})

const segments = computed(() => {
  const t = total.value || 1
  return platformMeta
    .map((meta) => {
      const count = activeData.value[meta.key] || activeData.value[meta.key.toLowerCase()] || 0
      const rawPercent = total.value > 0 ? (count / t) * 100 : 0
      return {
        ...meta,
        count,
        percent: Math.round(rawPercent),
        formattedPercent: rawPercent.toFixed(1) + '%',
        share: count / t,
      }
    })
    .filter((s) => s.key !== 'OTHER' || s.count > 0)
})

const dominantSegment = computed(() => {
  if (total.value === 0) return null
  return [...segments.value].sort((a, b) => b.count - a.count)[0]
})

const RADIUS = 38
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const visibleArcs = computed(() => {
  const t = total.value
  if (t === 0) return []

  const nonZero = segments.value.filter((s) => s.count > 0)
  let cumulative = 0

  return nonZero.map((seg) => {
    const arcLength = Math.max(seg.share * CIRCUMFERENCE, 1)
    const dashArray = `${arcLength} ${CIRCUMFERENCE}`
    const dashOffset = -cumulative
    cumulative += arcLength

    return {
      ...seg,
      arcLength,
      dashArray,
      dashOffset,
    }
  })
})

const firstArc = computed(() => (visibleArcs.value.length > 0 ? visibleArcs.value[0] : null))
const activeKey = computed(() => activeHoverKey.value || pinnedKey.value)

const currentCenterDisplay = computed(() => {
  if (total.value === 0) {
    return {
      value: '0',
      label: props.centerLabel,
    }
  }

  if (activeKey.value) {
    const matched = segments.value.find((s) => s.key === activeKey.value)
    if (matched) {
      return {
        value: matched.formattedPercent,
        label: matched.label,
      }
    }
  }

  if (showTotalToggle.value) {
    return {
      value: total.value > 999 ? (total.value / 1000).toFixed(1) + 'k' : `${total.value}`,
      label: props.centerLabel,
    }
  }

  if (dominantSegment.value) {
    return {
      value: dominantSegment.value.formattedPercent,
      label: dominantSegment.value.label,
    }
  }

  return {
    value: total.value > 999 ? (total.value / 1000).toFixed(1) + 'k' : `${total.value}`,
    label: props.centerLabel,
  }
})

function handleSegmentClick(key: string) {
  if (pinnedKey.value === key) {
    pinnedKey.value = null
  } else {
    pinnedKey.value = key
    showTotalToggle.value = false
  }
}

function handleCenterClick() {
  if (pinnedKey.value) {
    pinnedKey.value = null
  } else {
    showTotalToggle.value = !showTotalToggle.value
  }
}
</script>

<template>
  <div
    class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl transition-colors flex flex-col justify-between h-full"
  >
    <!-- Header: Title and Total Count on Left, Options Action on Right -->
    <div class="flex items-center justify-between pb-1">
      <div class="flex items-center gap-2">
        <h3 class="text-sm font-semibold text-slate-900 dark:text-white tracking-tight">
          {{ title }}
        </h3>
        <span
          class="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-mono"
        >
          {{ total.toLocaleString() }} Total
        </span>
      </div>

      <button
        type="button"
        title="Platform options"
        class="w-7 h-7 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
      >
        <MoreHorizontal class="w-4 h-4" />
      </button>
    </div>

    <!-- Skeleton Loading State -->
    <div
      v-if="loading"
      class="flex flex-col items-center justify-center py-6 space-y-3 animate-pulse"
    >
      <div class="w-32 h-32 rounded-full border-8 border-slate-100 dark:border-slate-800"></div>
      <div class="grid grid-cols-2 gap-3 w-full pt-2">
        <div v-for="i in 4" :key="i" class="h-4 bg-slate-100 dark:bg-slate-800 rounded"></div>
      </div>
    </div>

    <!-- Centered Donut & Bottom Legend Grid -->
    <div v-else class="flex flex-col items-center justify-center pt-1 sm:pt-2">
      <!-- Centered SVG Donut -->
      <div
        class="relative w-32 h-32 sm:w-36 sm:h-36 shrink-0 flex items-center justify-center my-0.5"
      >
        <svg viewBox="0 0 100 100" class="w-full h-full -rotate-90 transform overflow-visible">
          <!-- Background track circle -->
          <circle
            cx="50"
            cy="50"
            :r="RADIUS"
            fill="transparent"
            stroke="#e1e6f3"
            class="stroke-[#e1e6f3] dark:stroke-slate-800/80 transition-colors"
            stroke-width="13.5"
            stroke-linecap="round"
          />

          <!-- Colored overlapping rounded arc segments -->
          <circle
            v-for="seg in visibleArcs"
            :key="seg.key"
            cx="50"
            cy="50"
            :r="RADIUS"
            fill="transparent"
            :stroke="seg.color"
            :stroke-width="activeKey === seg.key ? 15 : 13.5"
            stroke-linecap="round"
            :stroke-dasharray="seg.dashArray"
            :stroke-dashoffset="seg.dashOffset"
            class="transition-all duration-300 ease-out cursor-pointer"
            :class="{
              'opacity-30': activeKey && activeKey !== seg.key,
              'opacity-100': !activeKey || activeKey === seg.key,
            }"
            @mouseenter="activeHoverKey = seg.key"
            @mouseleave="activeHoverKey = null"
            @click="handleSegmentClick(seg.key)"
          />

          <!-- Re-cap the start of the first segment -->
          <circle
            v-if="visibleArcs.length > 1 && firstArc"
            cx="50"
            cy="50"
            :r="RADIUS"
            fill="transparent"
            :stroke="firstArc.color"
            :stroke-width="activeKey === firstArc.key ? 15 : 13.5"
            stroke-linecap="round"
            :stroke-dasharray="`0.01 ${CIRCUMFERENCE}`"
            :stroke-dashoffset="0"
            class="transition-all duration-300 ease-out pointer-events-none"
            :class="{
              'opacity-30': activeKey && activeKey !== firstArc.key,
              'opacity-100': !activeKey || activeKey === firstArc.key,
            }"
          />
        </svg>

        <!-- Inner Circular Disc -->
        <button
          type="button"
          @click="handleCenterClick"
          title="Click to toggle total count or reset focus"
          class="absolute inset-0 m-auto w-[72px] h-[72px] sm:w-[80px] sm:h-[80px] rounded-full bg-white dark:bg-slate-900 shadow-[0_2px_12px_-2px_rgba(0,0,0,0.06)] dark:shadow-none border border-slate-100/90 dark:border-slate-800 flex flex-col items-center justify-center text-center cursor-pointer transition-transform duration-200 hover:scale-105 active:scale-95 focus:outline-none"
        >
          <span
            class="text-xl sm:text-2xl font-black text-slate-900 dark:text-white font-sans tracking-tight leading-none"
          >
            {{ currentCenterDisplay.value }}
          </span>
          <span
            class="text-[11px] sm:text-xs font-medium text-slate-400 dark:text-slate-500 mt-1 capitalize leading-none truncate max-w-[65px]"
          >
            {{ currentCenterDisplay.label }}
          </span>
        </button>
      </div>

      <!-- Bottom Multi-Column Legend with Colored Dots & Counts -->
      <div
        class="grid grid-cols-2 gap-x-4 gap-y-2 w-full mt-2.5 pt-2 border-t border-slate-100 dark:border-slate-800/60"
      >
        <div
          v-for="seg in segments"
          :key="seg.key"
          class="flex items-center justify-between gap-1.5 p-1 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800/40 cursor-pointer transition-all select-none"
          :class="{
            'opacity-30': activeKey && activeKey !== seg.key,
            'opacity-100': !activeKey || activeKey === seg.key,
          }"
          @mouseenter="activeHoverKey = seg.key"
          @mouseleave="activeHoverKey = null"
          @click="handleSegmentClick(seg.key)"
        >
          <div class="flex items-center gap-1.5 min-w-0">
            <span
              class="w-2.5 h-2.5 rounded-full shrink-0 transition-transform duration-200"
              :class="{ 'scale-125 ring-2 ring-offset-1 ring-slate-400': activeKey === seg.key }"
              :style="{ backgroundColor: seg.color }"
            ></span>
            <span class="text-[11px] font-bold text-slate-900 dark:text-white font-mono">
              {{ seg.percent }}%
            </span>
            <span class="text-[11px] text-slate-500 dark:text-slate-400 font-medium truncate">
              {{ seg.label }}
            </span>
          </div>
          <span class="text-[11px] font-mono font-semibold text-slate-700 dark:text-slate-300 shrink-0">
            {{ seg.count.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
