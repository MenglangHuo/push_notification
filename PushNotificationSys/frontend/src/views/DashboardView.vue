<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { useAuthStore } from '@/stores/authStore'
import { useAppStore } from '@/stores/appStore'
import { useThemeStore } from '@/stores/themeStore'
import {
  getAnalyticsOverview,
  getAnalyticsTimeSeries,
  type AnalyticsOverview,
  type TimeSeriesResponse,
} from '@/api/analytics'
import MetricCard from '@/components/dashboard/MetricCard.vue'
import StatusDonut from '@/components/dashboard/StatusDonut.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import {
  Activity,
  AlertCircle,
  BarChart2,
  BellRing,
  CheckCircle2,
  Filter,
  RefreshCw,
  ScrollText,
  Send,
  Smartphone,
  Sparkles,
  Zap,
} from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()
const themeStore = useThemeStore()

const currentAppId = computed(() => authStore.currentAppId)
const selectedRange = ref<'24h' | '7d' | '30d'>('7d')
const isLoading = ref<boolean>(false)
const error = ref<string | null>(null)
const lastUpdated = ref<Date>(new Date())

const overview = ref<AnalyticsOverview | null>(null)
const timeSeries = ref<TimeSeriesResponse | null>(null)

// DOM ref for Time Series EChart
const timeSeriesChartRef = ref<HTMLDivElement | null>(null)
let timeSeriesChartInstance: echarts.ECharts | null = null

const lastUpdatedLabel = computed(() => {
  const diffSec = Math.floor((Date.now() - lastUpdated.value.getTime()) / 1000)
  if (diffSec < 10) return 'Just now'
  if (diffSec < 60) return `${diffSec}s ago`
  return `${Math.floor(diffSec / 60)}m ago`
})

async function loadData() {
  if (!currentAppId.value) {
    overview.value = null
    timeSeries.value = null
    return
  }

  isLoading.value = true
  error.value = null
  try {
    const [ov, ts] = await Promise.all([
      getAnalyticsOverview(currentAppId.value, selectedRange.value),
      getAnalyticsTimeSeries(currentAppId.value, selectedRange.value),
    ])
    overview.value = ov
    timeSeries.value = ts
    lastUpdated.value = new Date()
    await nextTick()
    renderCharts()
  } catch (err: any) {
    console.error('Failed to load analytics data', err)
    error.value = err.response?.data?.message || err.message || 'Failed to fetch analytics'
  } finally {
    isLoading.value = false
  }
}

function renderCharts() {
  renderTimeSeriesChart()
}

function renderTimeSeriesChart() {
  if (!timeSeriesChartRef.value) return
  if (!timeSeriesChartInstance) {
    timeSeriesChartInstance = echarts.init(timeSeriesChartRef.value)
  }

  const isDark = themeStore.isDark
  const textColor = isDark ? '#94a3b8' : '#64748b'
  const gridLineColor = isDark ? '#1e293b' : '#f1f5f9'
  const tooltipBg = isDark ? '#0f172a' : '#ffffff'
  const tooltipBorder = isDark ? '#334155' : '#e2e8f0'
  const tooltipText = isDark ? '#f8fafc' : '#0f172a'

  const points = timeSeries.value?.points || []
  const dates = points.map((p) => {
    const ts = p.timestamp || ''
    if (ts.includes('T')) {
      const parts = ts.split('T')
      return parts[0].slice(-5) + ' ' + parts[1].slice(0, 5)
    }
    return ts.slice(-5)
  })
  const sentData = points.map((p) => p.sent)
  const deliveredData = points.map((p) => p.delivered)
  const openedData = points.map((p) => p.opened)

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText, fontSize: 12 },
    },
    legend: {
      data: ['Sent', 'Delivered', 'Opened'],
      textStyle: { color: textColor, fontSize: 11 },
      bottom: 0,
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
      minInterval: 1,
      splitLine: { lineStyle: { color: gridLineColor } },
      axisLabel: { color: textColor, fontSize: 10 },
    },
    series: [
      {
        name: 'Sent',
        type: 'line',
        smooth: true,
        data: sentData,
        itemStyle: { color: '#6366f1' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(99, 102, 241, 0.45)' },
            { offset: 1, color: 'rgba(99, 102, 241, 0.02)' },
          ]),
        },
      },
      {
        name: 'Delivered',
        type: 'line',
        smooth: true,
        data: deliveredData,
        itemStyle: { color: '#10b981' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16, 185, 129, 0.40)' },
            { offset: 1, color: 'rgba(16, 185, 129, 0.02)' },
          ]),
        },
      },
      {
        name: 'Opened',
        type: 'line',
        smooth: true,
        data: openedData,
        itemStyle: { color: '#f59e0b' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245, 158, 11, 0.40)' },
            { offset: 1, color: 'rgba(245, 158, 11, 0.02)' },
          ]),
        },
      },
    ],
  }

  timeSeriesChartInstance.setOption(option, true)
}

function handleResize() {
  timeSeriesChartInstance?.resize()
}

watch([currentAppId, selectedRange], () => {
  loadData()
})

watch(() => themeStore.isDark, () => {
  nextTick(() => renderCharts())
})

onMounted(async () => {
  if (authStore.currentOrgId && !authStore.currentAppId) {
    await appStore.loadApps(authStore.currentOrgId)
  }
  await loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  timeSeriesChartInstance?.dispose()
})
</script>

<template>
  <div class="space-y-4 pb-8 text-left">
    <!-- Header with Live Status, View Controls, Refresh, and Range Picker -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-0.5">
      <div>
        <div class="flex items-center gap-2.5">
          <div
            class="w-8 h-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/90 dark:border-indigo-500/30 shadow-xs shadow-indigo-500/10 shrink-0"
          >
            <BarChart2 class="w-4 h-4" />
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white">
                Campaign Health & Analytics
              </h1>
              <span
                class="hidden sm:inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200/90 dark:border-emerald-500/20"
              >
                <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                Live Metrics
              </span>
            </div>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Real-time delivery performance, multi-channel dispatch latency, and audience engagement
            </p>
          </div>
        </div>
      </div>

      <!-- Controls: Refresh Button & Range Filter -->
      <div class="flex items-center gap-2">
        <!-- Quick Range Buttons -->
        <div class="flex items-center p-0.5 rounded-xl border border-slate-200/90 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-2xs">
          <button
            v-for="r in ['24h', '7d', '30d'] as const"
            :key="r"
            type="button"
            @click="selectedRange = r"
            class="px-2.5 py-1 rounded-lg text-xs font-semibold transition-all cursor-pointer"
            :class="[
              selectedRange === r
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
            ]"
          >
            {{ r }}
          </button>
        </div>

        <!-- Refresh Button -->
        <button
          type="button"
          @click="loadData"
          :disabled="isLoading"
          title="Refresh metrics"
          class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-semibold bg-white dark:bg-slate-900 border-slate-200/90 dark:border-slate-800/80 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-700 shadow-2xs transition-all disabled:opacity-50 cursor-pointer"
        >
          <RefreshCw
            class="w-3.5 h-3.5 text-slate-500 dark:text-slate-400 transition-transform"
            :class="{ 'animate-spin text-indigo-600 dark:text-indigo-400': isLoading }"
          />
          <span class="font-normal text-slate-500 dark:text-slate-400 text-[11px]">
            {{ lastUpdatedLabel }}
          </span>
        </button>

        <!-- Compose Action Button -->
        <BaseButton
          variant="primary"
          size="sm"
          :icon="Send"
          @click="router.push(currentAppId ? `/apps/${currentAppId}/notifications/new` : '/notifications/new')"
        >
          Compose Push
        </BaseButton>
      </div>
    </div>

    <!-- Error Banner -->
    <div
      v-if="error"
      class="p-3 rounded-xl bg-rose-50 dark:bg-rose-500/10 border border-rose-200 dark:border-rose-500/20 text-rose-800 dark:text-rose-300 flex items-center justify-between text-xs"
    >
      <div class="flex items-center gap-2">
        <AlertCircle class="w-4 h-4 text-rose-600 shrink-0" />
        <span>{{ error }}</span>
      </div>
      <button
        type="button"
        @click="loadData"
        class="px-2 py-0.5 rounded-md bg-rose-600 hover:bg-rose-500 text-white font-medium text-xs cursor-pointer"
      >
        Retry
      </button>
    </div>

    <!-- App Missing Warning Banner -->
    <div
      v-if="!currentAppId"
      class="rounded-2xl p-5 border border-amber-500/30 bg-amber-50 dark:bg-amber-500/10 flex items-center gap-3.5 text-amber-900 dark:text-amber-200 shadow-xs"
    >
      <AlertCircle class="w-5 h-5 flex-shrink-0 text-amber-600 dark:text-amber-400" />
      <div>
        <p class="text-sm font-bold">Please select an application from the top navigation bar to view live analytics.</p>
        <p class="text-xs text-amber-700/80 dark:text-amber-300/80 mt-0.5">
          Select an app using the workspace dropdown in the header to view delivery rates, throughput trends, and device metrics.
        </p>
      </div>
    </div>

    <!-- Main Dashboard Metrics & Visuals (Visible when App is Selected) -->
    <template v-else>
      <!-- Executive KPI Summary Cards using MetricCard -->
      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 sm:gap-3.5">
        <!-- Total Dispatched -->
        <MetricCard
          title="Dispatched Volume"
          :value="overview?.totalSent?.toLocaleString() ?? 0"
          subtitle="Total messages routed"
          :progressPercent="Math.round(overview?.deliveryRate ?? 0)"
          :trend="{
            value: `${overview?.deliveryRate ?? 100}% delivered`,
            isPositive: (overview?.deliveryRate ?? 100) >= 95,
          }"
          :icon="Send"
          variant="indigo"
          :loading="isLoading"
        />

        <!-- Delivery Success -->
        <MetricCard
          title="Delivery Success"
          :value="`${overview?.deliveryRate ?? 100}%`"
          subtitle="Confirmed receipts"
          :progressPercent="Math.round(overview?.deliveryRate ?? 100)"
          :trend="{
            value: `${overview?.totalDelivered?.toLocaleString() ?? 0} delivered`,
            isPositive: true,
          }"
          :icon="CheckCircle2"
          variant="emerald"
          :loading="isLoading"
        />

        <!-- Open Engagement -->
        <MetricCard
          title="Open Rate (CTR)"
          :value="`${overview?.openRate ?? 0}%`"
          subtitle="User interactions"
          :progressPercent="Math.round(overview?.openRate ?? 0)"
          :trend="{
            value: `${overview?.totalOpened?.toLocaleString() ?? 0} opens`,
            isPositive: true,
          }"
          :icon="Sparkles"
          variant="amber"
          :loading="isLoading"
        />

        <!-- Dispatch Latency -->
        <MetricCard
          title="Average Latency"
          :value="`${overview?.avgLatencyMs ?? 0}ms`"
          subtitle="Roundtrip latency"
          :progressPercent="Math.min(100, Math.round(((overview?.avgLatencyMs ?? 0) / 300) * 100))"
          :trend="{
            value: 'Ultra-low latency',
            isPositive: (overview?.avgLatencyMs ?? 0) < 250,
          }"
          :icon="Zap"
          variant="sky"
          :loading="isLoading"
        />

        <!-- Active Audience Devices -->
        <MetricCard
          title="Audience Reach"
          :value="overview?.activeDevices?.toLocaleString() ?? 0"
          subtitle="Active registered tokens"
          :progressPercent="100"
          :trend="{
            value: 'Verified devices',
            isPositive: true,
          }"
          :icon="Smartphone"
          variant="slate"
          :loading="isLoading"
        />
      </div>

      <!-- Middle Row: Interactive Trend Chart & Platform Donut Chart -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-3.5">
        <!-- Influx Trend Chart (2 cols) -->
        <div
          class="lg:col-span-2 bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl transition-colors"
        >
          <div class="flex items-center justify-between mb-2">
            <div>
              <h3 class="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
                <BarChart2 class="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
                <span>Delivery & Engagement Trend</span>
              </h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Push dispatch volume vs successful delivery receipts vs user opens
              </p>
            </div>
          </div>
          <div ref="timeSeriesChartRef" class="w-full h-64 sm:h-72"></div>
        </div>

        <!-- Platform Distribution Donut (1 col) -->
        <div class="lg:col-span-1">
          <StatusDonut
            title="Platform Distribution"
            subtitle="Reachable devices by operating system"
            centerLabel="Devices"
            :byPlatform="overview?.platformBreakdown"
            :loading="isLoading"
          />
        </div>
      </div>

      <!-- Provider Gateway Performance & Campaign Shortcuts -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-3.5">
        <!-- Provider Gateway Health Breakdown (2 cols) -->
        <div
          class="lg:col-span-2 bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl transition-colors"
        >
          <div class="flex items-center justify-between mb-4">
            <div>
              <h3 class="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
                <Activity class="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
                <span>Downstream Provider Health & Performance</span>
              </h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Active connections to Apple APNs HTTP/2, Firebase Cloud Messaging v1, and Web Push endpoints
              </p>
            </div>
            <router-link
              :to="currentAppId ? `/apps/${currentAppId}/logs` : '/logs'"
              class="text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-500 flex items-center gap-1 cursor-pointer"
            >
              <span>View Logs</span>
              <span>&rarr;</span>
            </router-link>
          </div>

          <!-- Providers Table -->
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs">
              <thead>
                <tr
                  class="border-b border-slate-100 dark:border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider"
                >
                  <th class="pb-3 font-semibold">Provider / Gateway</th>
                  <th class="pb-3 font-semibold">Status</th>
                  <th class="pb-3 font-semibold text-right">Dispatched</th>
                  <th class="pb-3 font-semibold text-right">Failed</th>
                  <th class="pb-3 font-semibold text-right">Avg Latency</th>
                  <th class="pb-3 font-semibold text-right">Success Rate</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
                <tr
                  v-for="prov in overview?.providerBreakdown || []"
                  :key="prov.provider"
                  class="hover:bg-slate-50/70 dark:hover:bg-slate-800/40 transition-colors"
                >
                  <td class="py-3.5 pr-4 text-slate-900 dark:text-white font-semibold flex items-center gap-2">
                    <span
                      class="w-2 h-2 rounded-full"
                      :class="prov.failedCount === 0 ? 'bg-emerald-500' : 'bg-amber-500'"
                    ></span>
                    {{ prov.provider }}
                  </td>
                  <td class="py-3.5 pr-4">
                    <span
                      class="px-2 py-0.5 rounded-full text-[10px] font-semibold"
                      :class="
                        prov.failedCount === 0
                          ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200/80 dark:border-emerald-500/20'
                          : 'bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200/80 dark:border-amber-500/20'
                      "
                    >
                      {{ prov.failedCount === 0 ? 'Optimal' : 'Degraded' }}
                    </span>
                  </td>
                  <td class="py-3.5 pr-4 text-right font-mono text-slate-800 dark:text-slate-200">
                    {{ prov.sentCount.toLocaleString() }}
                  </td>
                  <td class="py-3.5 pr-4 text-right font-mono" :class="prov.failedCount > 0 ? 'text-rose-600 dark:text-rose-400' : 'text-slate-400'">
                    {{ prov.failedCount.toLocaleString() }}
                  </td>
                  <td class="py-3.5 pr-4 text-right font-mono text-slate-600 dark:text-slate-300">
                    {{ prov.avgLatencyMs }}ms
                  </td>
                  <td class="py-3.5 text-right font-mono font-bold text-emerald-600 dark:text-emerald-400">
                    {{ prov.sentCount > 0 ? (((prov.sentCount - prov.failedCount) / prov.sentCount) * 100).toFixed(1) : 100 }}%
                  </td>
                </tr>
                <tr v-if="!overview?.providerBreakdown?.length">
                  <td colspan="6" class="py-6 text-center text-slate-400 dark:text-slate-500">
                    No provider delivery logs recorded yet for this application.
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Quick Action Shortcuts (1 col) -->
        <div
          class="lg:col-span-1 bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl flex flex-col justify-between transition-colors"
        >
          <div>
            <h3 class="text-sm font-semibold text-slate-900 dark:text-white mb-1">
              Campaign Shortcuts
            </h3>
            <p class="text-xs text-slate-500 dark:text-slate-400 mb-4">
              Quick access to dispatch composer and registry tools
            </p>

            <div class="space-y-2.5">
              <router-link
                :to="currentAppId ? `/apps/${currentAppId}/notifications/new` : '/notifications/new'"
                class="w-full p-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs transition flex items-center gap-2.5 shadow-sm shadow-indigo-500/20 cursor-pointer"
              >
                <BellRing class="w-4 h-4 shrink-0" />
                <span class="truncate">Compose Push Notification</span>
              </router-link>

              <router-link
                :to="currentAppId ? `/apps/${currentAppId}/segments/new` : '/segments/new'"
                class="w-full p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800 hover:bg-slate-100 dark:hover:bg-slate-700/80 text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 font-semibold text-xs transition flex items-center gap-2.5 cursor-pointer"
              >
                <Filter class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0" />
                <span class="truncate">Build Audience Segment</span>
              </router-link>

              <router-link
                :to="currentAppId ? `/apps/${currentAppId}/logs` : '/logs'"
                class="w-full p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800 hover:bg-slate-100 dark:hover:bg-slate-700/80 text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 font-semibold text-xs transition flex items-center gap-2.5 cursor-pointer"
              >
                <ScrollText class="w-4 h-4 text-sky-600 dark:text-sky-400 shrink-0" />
                <span class="truncate">View Delivery Audit Logs</span>
              </router-link>
            </div>
          </div>

          <div class="pt-4 mt-4 border-t border-slate-100 dark:border-slate-800 text-[11px] text-slate-500 dark:text-slate-400">
            Tip: Ingest client events from your mobile app via <code class="font-mono text-slate-700 dark:text-slate-300">POST /sdk/v1/events</code> to track conversions live.
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
