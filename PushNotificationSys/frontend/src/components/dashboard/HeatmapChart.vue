<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { CalendarDays, Clock, Flame, Moon, Info, Sparkles, HelpCircle } from 'lucide-vue-next'
import { useThemeStore } from '@/stores/themeStore'

export interface HeatmapPoint {
  dayOfWeek: number
  hour: number
  value: number
}

const props = withDefaults(
  defineProps<{
    data: HeatmapPoint[]
    loading?: boolean
  }>(),
  {
    loading: false,
  },
)

const themeStore = useThemeStore()
const chartContainer = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null
const showInfoModal = ref(false)

const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const hours = [
  '12am', '1am', '2am', '3am', '4am', '5am',
  '6am', '7am', '8am', '9am', '10am', '11am',
  '12pm', '1pm', '2pm', '3pm', '4pm', '5pm',
  '6pm', '7pm', '8pm', '9pm', '10pm', '11pm',
]

const matrixData = computed(() => {
  const lookup: Record<string, number> = {}
  for (const item of props.data || []) {
    let dayIdx = 0
    if (item.dayOfWeek === 0) {
      dayIdx = 6 // Sunday
    } else if (item.dayOfWeek >= 1 && item.dayOfWeek <= 7) {
      dayIdx = item.dayOfWeek - 1 // 1=Mon(0), 7=Sun(6)
    }
    const h = Math.max(0, Math.min(23, item.hour))
    lookup[`${h}-${dayIdx}`] = (lookup[`${h}-${dayIdx}`] || 0) + (item.value || 0)
  }

  const result: [number, number, number][] = []
  for (let d = 0; d < 7; d++) {
    for (let h = 0; h < 24; h++) {
      result.push([h, d, lookup[`${h}-${d}`] || 0])
    }
  }
  return result
})

const totalDispatches = computed(() => {
  return matrixData.value.reduce((acc, cur) => acc + cur[2], 0)
})

const maxVal = computed(() => {
  const values = matrixData.value.map((m) => m[2])
  const max = Math.max(0, ...values)
  return max === 0 ? 10 : max
})

// Intelligence: Find peak day and hour
const peakSlot = computed(() => {
  let highest = { hour: 14, day: 1, count: 0 } // default Tue 2pm
  for (const item of matrixData.value) {
    if (item[2] > highest.count) {
      highest = { hour: item[0], day: item[1], count: item[2] }
    }
  }
  return highest
})

const peakDescription = computed(() => {
  if (totalDispatches.value === 0) return 'Awaiting telemetry'
  const dayName = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][peakSlot.value.day]
  const hourName = hours[peakSlot.value.hour]
  return `${dayName} at ${hourName}`
})

const bestWindowRecommendation = computed(() => {
  if (totalDispatches.value === 0) return 'Standard: Weekdays 10 AM - 3 PM'
  const h = peakSlot.value.hour
  const start = Math.max(0, h - 2)
  const end = Math.min(23, h + 2)
  return `${hours[start]} – ${hours[end]}`
})

let resizeObserver: ResizeObserver | null = null

function initResizeObserver() {
  if (chartContainer.value && typeof ResizeObserver !== 'undefined' && !resizeObserver) {
    resizeObserver = new ResizeObserver(() => {
      chartInstance?.resize()
    })
    resizeObserver.observe(chartContainer.value)
  }
}

function renderChart() {
  if (!chartContainer.value) return

  if (totalDispatches.value === 0) {
    if (chartInstance) {
      chartInstance.clear()
    }
    return
  }

  const existingInstance = echarts.getInstanceByDom(chartContainer.value)
  if (existingInstance) {
    chartInstance = existingInstance
  } else {
    chartInstance = echarts.init(chartContainer.value)
  }
  initResizeObserver()

  const isDark = themeStore.isDark
  const textColor = isDark ? '#94a3b8' : '#64748b'
  const tooltipBg = isDark ? '#0f172a' : '#ffffff'
  const tooltipBorder = isDark ? '#334155' : '#e2e8f0'
  const tooltipText = isDark ? '#f8fafc' : '#0f172a'
  const borderColor = isDark ? '#0f172a' : '#ffffff'

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      position: 'top',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      padding: [8, 12],
      textStyle: { color: tooltipText, fontSize: 12 },
      formatter: (params: any) => {
        const d = params.data
        const hLabel = hours[d[0]]
        const dayLabel = days[d[1]]
        const count = d[2]
        const pctOfMax = maxVal.value > 0 ? (count / maxVal.value) * 100 : 0

        let activityTier = 'Low / Idle'
        let tierColor = '#94a3b8'
        let emoji = '🌙'
        if (pctOfMax >= 75) {
          activityTier = 'Peak Audience Window (Ideal for campaigns)'
          tierColor = '#f43f5e'
          emoji = '🔥'
        } else if (pctOfMax >= 40) {
          activityTier = 'Active Dispatch Window'
          tierColor = '#6366f1'
          emoji = '⚡'
        } else if (count > 0) {
          activityTier = 'Moderate Traffic'
          tierColor = '#10b981'
          emoji = '✨'
        }

        return `<div style="font-size: 11px; line-height: 1.5; min-width: 170px;">
          <div style="font-weight: 700; font-size: 12px; color: ${tooltipText}; margin-bottom: 2px;">
            ${dayLabel}, ${hLabel}
          </div>
          <div style="display: flex; justify-content: space-between; gap: 12px; margin-top: 4px;">
            <span style="color: ${textColor};">Dispatches:</span>
            <strong style="color: #6366f1; font-family: monospace;">${count.toLocaleString()}</strong>
          </div>
          <div style="display: flex; align-items: center; gap: 4px; margin-top: 4px; font-size: 10px; color: ${tierColor}; font-weight: 600;">
            <span>${emoji}</span> ${activityTier}
          </div>
        </div>`
      },
    },
    grid: {
      top: '4%',
      left: '1%',
      right: '2%',
      bottom: '16%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: hours,
      splitArea: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: textColor,
        fontSize: 10,
        interval: 1,
        formatter: (val: string, idx: number) => {
          // Show every 2 hours cleanly
          return idx % 2 === 0 ? val : ''
        },
      },
    },
    yAxis: {
      type: 'category',
      data: days,
      inverse: true,
      splitArea: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: textColor,
        fontSize: 11,
        fontWeight: 'bold',
      },
    },
    visualMap: {
      min: 0,
      max: maxVal.value > 0 ? maxVal.value : 10,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: '0%',
      itemWidth: 10,
      itemHeight: 140,
      text: ['High Traffic', 'Quiet'],
      textStyle: {
        color: textColor,
        fontSize: 10,
      },
      inRange: {
        color: isDark
          ? ['#1e293b', '#2e1065', '#4c1d95', '#6366f1', '#a855f7', '#f43f5e']
          : ['#f1f5f9', '#e0e7ff', '#a5b4fc', '#6366f1', '#8b5cf6', '#ec4899'],
      },
    },
    series: [
      {
        name: 'Dispatches',
        type: 'heatmap',
        data: matrixData.value,
        label: { show: false },
        itemStyle: {
          borderRadius: 4,
          borderColor,
          borderWidth: 1.5,
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.4)',
          },
        },
      },
    ],
  }

  chartInstance.setOption(option, true)
  chartInstance.resize()
}

function handleResize() {
  chartInstance?.resize()
}

watch(
  [() => props.data, () => props.loading, () => themeStore.isDark],
  () => {
    nextTick(() => {
      renderChart()
      chartInstance?.resize()
    })
  },
  { deep: true },
)

onMounted(() => {
  nextTick(() => {
    renderChart()
    initResizeObserver()
    window.addEventListener('resize', handleResize)
  })
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <div
    class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl transition-colors flex flex-col justify-between"
  >
    <!-- Header with Actionable Explanation -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
      <div>
        <div class="flex items-center gap-2">
          <div class="w-7 h-7 rounded-lg bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-100 dark:border-indigo-500/20">
            <CalendarDays class="w-4 h-4" />
          </div>
          <h3 class="text-sm font-bold text-slate-900 dark:text-white">
            Optimal Send Times & Activity Heatmap
          </h3>
          <button
            type="button"
            @click="showInfoModal = !showInfoModal"
            class="text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors cursor-pointer"
            title="Click to understand this chart's purpose"
          >
            <HelpCircle class="w-3.5 h-3.5" />
          </button>
        </div>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
          Analyzes audience dispatch concentration by hour & day to reveal peak subscriber engagement windows
        </p>
      </div>

      <!-- Live Dispatches Analyzed Counter Pill -->
      <div v-if="!loading" class="flex items-center gap-1.5 self-start sm:self-auto">
        <span class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20">
          <Clock class="w-3 h-3 text-indigo-500" />
          <span>{{ totalDispatches.toLocaleString() }} events analyzed</span>
        </span>
      </div>
    </div>

    <!-- Educational Context Banner (Collapsible/Dismissible) -->
    <div
      v-if="showInfoModal"
      class="mb-4 p-3.5 rounded-xl bg-indigo-50/80 dark:bg-indigo-950/40 border border-indigo-200/80 dark:border-indigo-800/60 text-xs text-indigo-900 dark:text-indigo-200 flex items-start gap-2.5 transition-all"
    >
      <Info class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5" />
      <div class="space-y-1">
        <p class="font-bold">Why is this valuable for Marketers & Engineers?</p>
        <p class="text-[11px] text-indigo-800/90 dark:text-indigo-300/90 leading-relaxed">
          Notifications scheduled during your audience's <strong>Peak Windows</strong> achieve significantly higher click-through rates (CTR) and lower opt-out fatigue. Darker cells highlight heavy traffic periods, whereas lighter/darker navy cells represent quiet hours when users are likely asleep.
        </p>
      </div>
    </div>

    <!-- Executive Quick-Takeaway Insight Cards -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-2.5 mb-4">
      <!-- Peak Engagement Hour -->
      <div class="p-3 rounded-xl bg-slate-50/80 dark:bg-slate-800/40 border border-slate-100 dark:border-slate-800/60 flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-rose-50 dark:bg-rose-500/10 text-rose-600 dark:text-rose-400 flex items-center justify-center shrink-0">
          <Flame class="w-4 h-4" />
        </div>
        <div class="min-w-0">
          <div class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Peak Window</div>
          <div class="text-xs font-bold text-slate-900 dark:text-white truncate">
            {{ peakDescription }}
          </div>
        </div>
      </div>

      <!-- Recommended Campaign Time -->
      <div class="p-3 rounded-xl bg-slate-50/80 dark:bg-slate-800/40 border border-slate-100 dark:border-slate-800/60 flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center shrink-0">
          <Sparkles class="w-4 h-4" />
        </div>
        <div class="min-w-0">
          <div class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Optimal Blast Window</div>
          <div class="text-xs font-bold text-slate-900 dark:text-white truncate">
            {{ bestWindowRecommendation }}
          </div>
        </div>
      </div>

      <!-- Off-Peak Warning -->
      <div class="p-3 rounded-xl bg-slate-50/80 dark:bg-slate-800/40 border border-slate-100 dark:border-slate-800/60 flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-amber-50 dark:bg-amber-500/10 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0">
          <Moon class="w-4 h-4" />
        </div>
        <div class="min-w-0">
          <div class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Quiet Hours</div>
          <div class="text-xs font-bold text-slate-900 dark:text-white truncate">
            1:00 AM – 6:00 AM (Low CTR)
          </div>
        </div>
      </div>
    </div>

    <!-- Loading Skeleton -->
    <div
      v-if="loading"
      class="w-full h-72 min-h-[270px] flex items-center justify-center animate-pulse bg-slate-50 dark:bg-slate-800/40 rounded-xl"
    >
      <div class="grid grid-cols-12 gap-1 w-full p-4">
        <div
          v-for="i in 48"
          :key="i"
          class="h-5 bg-slate-200/70 dark:bg-slate-700/50 rounded"
        ></div>
      </div>
    </div>

    <!-- Empty State: Zero dispatches yet -->
    <div
      v-else-if="totalDispatches === 0"
      class="py-12 flex flex-col items-center justify-center text-center bg-slate-50/50 dark:bg-slate-800/20 rounded-xl border border-dashed border-slate-200 dark:border-slate-800"
    >
      <div class="w-12 h-12 rounded-2xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center mb-3">
        <CalendarDays class="w-6 h-6" />
      </div>
      <p class="text-sm font-bold text-slate-800 dark:text-slate-100">Awaiting Dispatch Telemetry</p>
      <p class="text-xs text-slate-500 dark:text-slate-400 max-w-sm mt-1 leading-relaxed">
        Once you compose and broadcast notifications to your audience, this heatmap will map hourly delivery volume across the week to recommend the best times to schedule campaigns.
      </p>
    </div>

    <!-- Heatmap Chart Canvas -->
    <div
      v-show="!loading && totalDispatches > 0"
      ref="chartContainer"
      class="w-full h-72 min-h-[270px]"
    ></div>

    <!-- Pro Tip Footer -->
    <div class="mt-3 pt-3 border-t border-slate-100 dark:border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 gap-2">
      <div class="flex items-center gap-1.5">
        <span class="text-amber-500">💡</span>
        <span><strong>Tip:</strong> Schedule high-importance pushes inside peak windows to maximize open rates and minimize unsubscribes.</span>
      </div>
      <span class="text-[10px] text-slate-400">All times calculated in system timezone</span>
    </div>
  </div>
</template>
