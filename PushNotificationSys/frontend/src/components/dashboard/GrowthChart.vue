<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { TrendingUp, ArrowUp, ArrowDown } from 'lucide-vue-next'
import { useThemeStore } from '@/stores/themeStore'

export interface GrowthPoint {
  date: string
  newDevices: number
  churnedDevices: number
  netChange: number
}

const props = withDefaults(
  defineProps<{
    points: GrowthPoint[]
    totalNew?: number
    totalChurned?: number
    netGrowth?: number
    loading?: boolean
  }>(),
  {
    totalNew: 0,
    totalChurned: 0,
    netGrowth: 0,
    loading: false,
  },
)

const themeStore = useThemeStore()
const chartContainer = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null

function formatDateLabel(val: string): string {
  if (!val) return ''
  if (val.includes('T')) {
    val = val.split('T')[0]
  }
  // If '2026-11-10', return '11-10'
  const parts = val.split('-')
  if (parts.length >= 3) {
    return `${parts[1]}/${parts[2]}`
  }
  return val
}

function renderChart() {
  if (!chartContainer.value) return

  const existingInstance = echarts.getInstanceByDom(chartContainer.value)
  if (existingInstance) {
    chartInstance = existingInstance
  } else {
    chartInstance = echarts.init(chartContainer.value)
  }
  initResizeObserver()

  const isDark = themeStore.isDark
  const textColor = isDark ? '#94a3b8' : '#64748b'
  const gridLineColor = isDark ? '#1e293b' : '#f1f5f9'
  const tooltipBg = isDark ? '#0f172a' : '#ffffff'
  const tooltipBorder = isDark ? '#334155' : '#e2e8f0'
  const tooltipText = isDark ? '#f8fafc' : '#0f172a'

  const dates = (props.points || []).map((p) => formatDateLabel(p.date))
  const newSubscribers = (props.points || []).map((p) => p.newDevices)
  const churned = (props.points || []).map((p) => -Math.abs(p.churnedDevices))
  const netChanges = (props.points || []).map((p) => p.netChange)

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText, fontSize: 12 },
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return ''
        const dateStr = params[0].axisValue
        let html = `<div style="font-size: 11px; line-height: 1.5;">
          <div style="font-weight: 600; margin-bottom: 3px;">${dateStr}</div>`
        params.forEach((item: any) => {
          const val = Number(item.value)
          const displayVal =
            item.seriesName === 'Churned'
              ? Math.abs(val).toLocaleString()
              : val.toLocaleString()
          const prefix =
            item.seriesName === 'New Subscribers'
              ? '+'
              : item.seriesName === 'Churned'
                ? '-'
                : val >= 0
                  ? '+'
                  : ''
          html += `<div style="display: flex; align-items: center; justify-content: space-between; gap: 12px;">
            <span><span style="color: ${item.color};">●</span> ${item.seriesName}</span>
            <span style="font-family: monospace; font-weight: 600;">${prefix}${displayVal}</span>
          </div>`
        })
        html += '</div>'
        return html
      },
    },
    legend: {
      data: ['New Subscribers', 'Churned', 'Net Growth'],
      textStyle: { color: textColor, fontSize: 11 },
      bottom: 0,
      icon: 'roundRect',
    },
    grid: {
      top: '12%',
      left: '3%',
      right: '4%',
      bottom: '14%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: isDark ? '#334155' : '#cbd5e1' } },
      axisLabel: { color: textColor, fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: gridLineColor } },
      axisLabel: {
        color: textColor,
        fontSize: 10,
        formatter: (val: number) => Math.abs(val).toLocaleString(),
      },
    },
    series: [
      {
        name: 'New Subscribers',
        type: 'line',
        smooth: true,
        data: newSubscribers,
        itemStyle: { color: '#10b981' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16, 185, 129, 0.45)' },
            { offset: 1, color: 'rgba(16, 185, 129, 0.02)' },
          ]),
        },
      },
      {
        name: 'Churned',
        type: 'line',
        smooth: true,
        data: churned,
        itemStyle: { color: '#f43f5e' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(244, 63, 94, 0.02)' },
            { offset: 1, color: 'rgba(244, 63, 94, 0.35)' },
          ]),
        },
      },
      {
        name: 'Net Growth',
        type: 'line',
        smooth: true,
        data: netChanges,
        lineStyle: { type: 'dashed', width: 2, color: '#6366f1' },
        itemStyle: { color: '#6366f1' },
      },
    ],
  }

  chartInstance.setOption(option, true)
  chartInstance.resize()
}

let resizeObserver: ResizeObserver | null = null

function initResizeObserver() {
  if (chartContainer.value && typeof ResizeObserver !== 'undefined' && !resizeObserver) {
    resizeObserver = new ResizeObserver(() => {
      chartInstance?.resize()
    })
    resizeObserver.observe(chartContainer.value)
  }
}

function handleResize() {
  chartInstance?.resize()
}

watch(
  [() => props.points, () => props.loading, () => themeStore.isDark],
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
    class="h-full bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl transition-colors flex flex-col justify-between"
  >
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
      <div>
        <h3 class="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
          <TrendingUp class="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
          <span>Subscriber Growth</span>
        </h3>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Audience acquisition vs unregistrations over time
        </p>
      </div>

      <!-- Summary Pill Badges -->
      <div class="flex items-center gap-2 text-xs font-mono">
        <!-- New Subscribers -->
        <span
          class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200/80 dark:border-emerald-500/20 font-semibold"
          title="New subscribers acquired"
        >
          <ArrowUp class="w-3 h-3 stroke-[2.5]" />
          <span>+{{ totalNew.toLocaleString() }}</span>
        </span>

        <!-- Churned Subscribers -->
        <span
          class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-200/80 dark:border-rose-500/20 font-semibold"
          title="Subscribers churned"
        >
          <ArrowDown class="w-3 h-3 stroke-[2.5]" />
          <span>-{{ totalChurned.toLocaleString() }}</span>
        </span>

        <!-- Net Growth -->
        <span
          class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 border border-indigo-200/80 dark:border-indigo-500/20 font-bold"
          title="Net audience growth"
        >
          <span>Net</span>
          <span>{{ netGrowth >= 0 ? '+' : '' }}{{ netGrowth.toLocaleString() }}</span>
        </span>
      </div>
    </div>

    <!-- Skeleton Loading -->
    <div
      v-if="loading"
      class="w-full flex-1 min-h-[290px] bg-slate-50 dark:bg-slate-800/40 rounded-xl animate-pulse flex items-center justify-center"
    >
      <div class="h-32 w-3/4 bg-slate-200/60 dark:bg-slate-700/50 rounded-lg"></div>
    </div>

    <!-- Chart Canvas -->
    <div
      v-show="!loading"
      ref="chartContainer"
      class="w-full flex-1 min-h-[290px]"
    ></div>
  </div>
</template>
