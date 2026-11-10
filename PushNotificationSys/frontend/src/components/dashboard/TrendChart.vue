<script setup lang="ts">
import { ref, computed } from 'vue'
export interface TrendPoint {
  date: string
  count: number
}
import { TrendingUp } from 'lucide-vue-next'

interface ChartPoint extends TrendPoint {
  x: number
  y: number
}

const props = withDefaults(
  defineProps<{
    trends: TrendPoint[]
    loading?: boolean
  }>(),
  {
    loading: false,
  },
)

const hoveredIndex = ref<number | null>(null)

const maxVal = computed(() => {
  if (!props.trends || props.trends.length === 0) return 10
  const max = Math.max(...props.trends.map((t) => t.count))
  return max === 0 ? 10 : Math.ceil(max * 1.2)
})

// SVG viewBox dimensions
const width = 600
const height = 200
const paddingX = 30
const paddingY = 25

const points = computed<ChartPoint[]>(() => {
  if (!props.trends || props.trends.length === 0) return []
  const n = props.trends.length
  const stepX = (width - paddingX * 2) / (n - 1 || 1)

  return props.trends.map((t, idx) => {
    const x = paddingX + idx * stepX
    const y = height - paddingY - (t.count / maxVal.value) * (height - paddingY * 2)
    return { x, y, ...t }
  })
})

const polylinePoints = computed(() => {
  return points.value.map((p) => `${p.x},${p.y}`).join(' ')
})

const areaPoints = computed(() => {
  if (points.value.length === 0) return ''
  const first = points.value[0]
  const last = points.value[points.value.length - 1]
  if (!first || !last) return ''
  const baseY = height - paddingY
  return `${first.x},${baseY} ${polylinePoints.value} ${last.x},${baseY}`
})

const hoveredPoint = computed(() => {
  if (hoveredIndex.value === null) return null
  return points.value[hoveredIndex.value] || null
})

const startDate = computed(() => points.value[0]?.date || '')
const midDate = computed(() => points.value[Math.floor(points.value.length / 2)]?.date || '')
const endDate = computed(() => points.value[points.value.length - 1]?.date || '')
</script>

<template>
  <div
    class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl ring-1 ring-black/5 dark:ring-white/5 transition-colors"
  >
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div>
        <h3 class="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
          <TrendingUp class="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
          <span>Notification Delivery Trends</span>
        </h3>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Daily volume of push notifications dispatched across gateway providers
        </p>
      </div>

      <div v-if="hoveredPoint" class="text-right">
        <span class="text-xs font-mono font-bold text-indigo-600 dark:text-indigo-400">
          {{ hoveredPoint.count }} dispatched
        </span>
        <span class="text-[11px] text-slate-500 dark:text-slate-400 block">
          {{ hoveredPoint.date }}
        </span>
      </div>
    </div>

    <!-- Skeleton Loader -->
    <div v-if="loading" class="animate-pulse space-y-3 py-6">
      <div class="h-36 bg-slate-100 dark:bg-slate-850 rounded-xl w-full"></div>
      <div class="flex justify-between px-4">
        <div class="h-3 w-16 bg-slate-100 dark:bg-slate-850 rounded"></div>
        <div class="h-3 w-16 bg-slate-100 dark:bg-slate-850 rounded"></div>
        <div class="h-3 w-16 bg-slate-100 dark:bg-slate-850 rounded"></div>
      </div>
    </div>

    <!-- SVG Chart Container -->
    <div v-else class="relative w-full overflow-hidden">
      <svg :viewBox="`0 0 ${width} ${height}`" class="w-full h-48 overflow-visible">
        <defs>
          <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#6366f1" stop-opacity="0.35" />
            <stop offset="100%" stop-color="#6366f1" stop-opacity="0.0" />
          </linearGradient>
        </defs>

        <!-- Horizontal Guide Lines -->
        <line
          :x1="paddingX"
          :y1="paddingY"
          :x2="width - paddingX"
          :y2="paddingY"
          stroke="#94a3b8"
          stroke-opacity="0.3"
          stroke-dasharray="3 3"
          stroke-width="1"
        />
        <line
          :x1="paddingX"
          :y1="height / 2"
          :x2="width - paddingX"
          :y2="height / 2"
          stroke="#94a3b8"
          stroke-opacity="0.3"
          stroke-dasharray="3 3"
          stroke-width="1"
        />
        <line
          :x1="paddingX"
          :y1="height - paddingY"
          :x2="width - paddingX"
          :y2="height - paddingY"
          stroke="#94a3b8"
          stroke-opacity="0.4"
          stroke-width="1.5"
        />

        <!-- Area Fill -->
        <polygon :points="areaPoints" fill="url(#areaGradient)" />

        <!-- Line Stroke -->
        <polyline
          :points="polylinePoints"
          fill="none"
          stroke="#6366f1"
          stroke-width="3"
          stroke-linecap="round"
          stroke-linejoin="round"
        />

        <!-- Points & Hover Triggers -->
        <g v-for="(p, idx) in points" :key="idx">
          <!-- Active point indicator -->
          <circle
            :cx="p.x"
            :cy="p.y"
            :r="hoveredIndex === idx ? 6 : 3"
            class="transition-all duration-150"
            :fill="hoveredIndex === idx ? '#4f46e5' : '#818cf8'"
            stroke="#ffffff"
            stroke-width="2"
          />

          <!-- Invisible wider hit area for hover -->
          <rect
            :x="p.x - 15"
            :y="0"
            width="30"
            :height="height"
            fill="transparent"
            class="cursor-pointer"
            @mouseenter="hoveredIndex = idx"
            @mouseleave="hoveredIndex = null"
          />
        </g>
      </svg>

      <!-- Date Labels -->
      <div
        class="flex justify-between px-6 pt-2 text-[10px] text-slate-500 dark:text-slate-400 font-mono"
      >
        <span v-if="startDate">{{ startDate }}</span>
        <span v-if="midDate">{{ midDate }}</span>
        <span v-if="endDate">{{ endDate }}</span>
      </div>
    </div>
  </div>
</template>
