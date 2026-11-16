<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { Activity, ChevronDown } from 'lucide-vue-next'
import AnimatedCounter from './AnimatedCounter.vue'

const props = withDefaults(
  defineProps<{
    sent: number
    delivered: number
    opened: number
    clicked: number
    loading?: boolean
  }>(),
  {
    sent: 0,
    delivered: 0,
    opened: 0,
    clicked: 0,
    loading: false,
  },
)

const isAnimated = ref(false)
let animTimer: ReturnType<typeof setTimeout> | null = null

const hasData = computed(() => {
  return (props.sent + props.delivered + props.opened + props.clicked) > 0
})

function triggerAnimation() {
  isAnimated.value = false
  if (animTimer) clearTimeout(animTimer)
  animTimer = setTimeout(() => {
    isAnimated.value = true
  }, 50)
}

watch(
  [() => props.sent, () => props.delivered, () => props.opened, () => props.clicked, () => props.loading],
  () => {
    if (!props.loading && hasData.value) {
      triggerAnimation()
    } else if (!hasData.value) {
      isAnimated.value = false
    }
  },
  { deep: true },
)

onMounted(() => {
  if (!props.loading && hasData.value) {
    nextTick(() => triggerAnimation())
  }
})

onUnmounted(() => {
  if (animTimer) {
    clearTimeout(animTimer)
    animTimer = null
  }
})

const stages = computed(() => {
  const max = Math.max(props.sent, props.delivered, props.opened, props.clicked, 1)

  const calcConversion = (numerator: number, denominator: number) => {
    if (denominator <= 0) return 0
    return Math.min(100, (numerator / denominator) * 100)
  }

  return [
    {
      id: 'sent',
      label: 'Sent',
      description: 'Routed via provider gateways',
      count: props.sent,
      widthPercent: props.sent > 0 ? Math.max(8, (props.sent / max) * 100) : 0,
      conversionRate: 100,
      conversionLabel: 'Baseline dispatch',
      colorBg: 'bg-indigo-600 dark:bg-indigo-500',
      colorLight: 'bg-indigo-50 dark:bg-indigo-500/10',
      textColor: 'text-indigo-600 dark:text-indigo-400',
      borderColor: 'border-indigo-200/80 dark:border-indigo-500/30',
    },
    {
      id: 'delivered',
      label: 'Delivered',
      description: 'Confirmed device receipts',
      count: props.delivered,
      widthPercent: props.delivered > 0 ? Math.max(6, (props.delivered / max) * 100) : 0,
      conversionRate: calcConversion(props.delivered, props.sent),
      conversionLabel: 'delivery rate',
      colorBg: 'bg-emerald-600 dark:bg-emerald-500',
      colorLight: 'bg-emerald-50 dark:bg-emerald-500/10',
      textColor: 'text-emerald-600 dark:text-emerald-400',
      borderColor: 'border-emerald-200/80 dark:border-emerald-500/30',
    },
    {
      id: 'opened',
      label: 'Opened',
      description: 'User view/tap engagements',
      count: props.opened,
      widthPercent: props.opened > 0 ? Math.max(4, (props.opened / max) * 100) : 0,
      conversionRate: calcConversion(props.opened, props.delivered),
      conversionLabel: 'open rate',
      colorBg: 'bg-amber-500 dark:bg-amber-400',
      colorLight: 'bg-amber-50 dark:bg-amber-500/10',
      textColor: 'text-amber-600 dark:text-amber-400',
      borderColor: 'border-amber-200/80 dark:border-amber-500/30',
    },
    {
      id: 'clicked',
      label: 'Clicked',
      description: 'Call-to-action interactions',
      count: props.clicked,
      widthPercent: props.clicked > 0 ? Math.max(3, (props.clicked / max) * 100) : 0,
      conversionRate: calcConversion(props.clicked, props.opened),
      conversionLabel: 'click-through rate',
      colorBg: 'bg-rose-500 dark:bg-rose-400',
      colorLight: 'bg-rose-50 dark:bg-rose-500/10',
      textColor: 'text-rose-600 dark:text-rose-400',
      borderColor: 'border-rose-200/80 dark:border-rose-500/30',
    },
  ]
})
</script>

<template>
  <div
    class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl transition-colors"
  >
    <!-- Header -->
    <div class="flex items-center justify-between mb-5">
      <div>
        <h3 class="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
          <Activity class="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
          <span>Delivery Funnel</span>
        </h3>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          End-to-end conversion from notification dispatch to audience click-through
        </p>
      </div>
      <div v-if="!loading && hasData" class="text-[11px] font-semibold text-slate-400 dark:text-slate-500">
        Live Funnel Pipeline
      </div>
    </div>

    <!-- Skeleton Loading State -->
    <div v-if="loading" class="space-y-4 py-2">
      <div v-for="i in 4" :key="i" class="space-y-2">
        <div class="flex items-center justify-between">
          <div class="h-4 w-24 bg-slate-100 dark:bg-slate-800 rounded animate-pulse"></div>
          <div class="h-4 w-16 bg-slate-100 dark:bg-slate-800 rounded animate-pulse"></div>
        </div>
        <div class="h-8 bg-slate-100 dark:bg-slate-800 rounded-xl animate-pulse"></div>
      </div>
    </div>

    <!-- Funnel Visualization -->
    <div v-else class="space-y-3">
      <div v-for="(stage, idx) in stages" :key="stage.id" class="group">
        <!-- Stage Info Header -->
        <div class="flex items-baseline justify-between mb-1 text-xs">
          <div class="flex items-center gap-2">
            <span class="font-semibold text-slate-900 dark:text-slate-100">{{ stage.label }}</span>
            <span class="text-[11px] text-slate-400 dark:text-slate-500 hidden sm:inline">
              {{ stage.description }}
            </span>
          </div>
          <div class="flex items-center gap-2.5">
            <span class="font-mono font-bold text-slate-900 dark:text-white text-sm">
              <AnimatedCounter
                :value="(isAnimated && hasData) ? stage.count : (hasData ? 0 : stage.count)"
                :duration="900 + idx * 120"
              />
            </span>
            <span
              v-if="idx > 0"
              class="px-2 py-0.5 rounded-full text-[10px] font-semibold border transition-all duration-700"
              :class="[
                stage.colorLight,
                stage.textColor,
                stage.borderColor,
                (isAnimated && hasData) ? 'opacity-100 scale-100' : 'opacity-0 scale-95'
              ]"
            >
              <AnimatedCounter
                :value="(isAnimated && hasData) ? stage.conversionRate : 0"
                :decimals="1"
                suffix="%"
                :duration="900 + idx * 120"
              />
            </span>
          </div>
        </div>

        <!-- Horizontal Bar Container -->
        <div
          class="h-8 w-full bg-slate-100 dark:bg-slate-800/70 rounded-xl p-1 flex items-center overflow-hidden"
        >
          <div
            class="h-full rounded-lg flex items-center justify-end pr-2.5 shadow-xs relative overflow-hidden transition-all"
            :class="stage.colorBg"
            :style="{
              width: (isAnimated && hasData) ? `${stage.widthPercent}%` : '0%',
              transitionDuration: '1000ms',
              transitionTimingFunction: 'cubic-bezier(0.16, 1, 0.3, 1)',
              transitionDelay: `${idx * 120}ms`,
            }"
          >
            <!-- Subtle gradient shine while animating -->
            <div
              v-if="isAnimated && hasData && stage.widthPercent > 0"
              class="absolute inset-0 bg-gradient-to-r from-transparent via-white/15 to-transparent pointer-events-none"
            ></div>

            <span
              v-if="stage.widthPercent > 20"
              class="text-[10px] font-bold text-white/90 drop-shadow-xs truncate relative z-10 transition-opacity duration-500"
              :class="(isAnimated && hasData) ? 'opacity-100' : 'opacity-0'"
            >
              <AnimatedCounter
                :value="(isAnimated && hasData) ? stage.conversionRate : 0"
                :decimals="1"
                suffix="%"
                :duration="1000 + idx * 120"
              />
            </span>
          </div>
        </div>

        <!-- Transition Arrow Between Stages -->
        <div
          v-if="idx < stages.length - 1"
          class="flex items-center gap-2 pl-4 py-1 text-[11px] text-slate-400 dark:text-slate-500 font-medium transition-opacity duration-700"
          :class="(isAnimated && hasData) ? 'opacity-100' : 'opacity-50'"
        >
          <ChevronDown class="w-3.5 h-3.5 text-slate-400 dark:text-slate-500 shrink-0" />
          <span class="text-[11px]">
            <span class="font-semibold text-slate-700 dark:text-slate-300">
              <AnimatedCounter
                :value="(isAnimated && hasData) ? stages[idx + 1].conversionRate : 0"
                :decimals="1"
                suffix="%"
                :duration="1000 + (idx + 1) * 120"
              />
            </span>
            {{ stages[idx + 1].conversionLabel }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
