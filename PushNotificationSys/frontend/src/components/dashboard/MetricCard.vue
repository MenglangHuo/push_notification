<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    value: string | number
    subtitle?: string
    icon?: any
    loading?: boolean
    progressPercent?: number
    trend?: {
      value: string
      isPositive?: boolean
    }
    variant?: 'indigo' | 'emerald' | 'amber' | 'rose' | 'slate' | 'sky'
  }>(),
  {
    loading: false,
    progressPercent: 40,
    variant: 'indigo',
  },
)

const variantStyles = computed(() => {
  switch (props.variant) {
    case 'sky':
      return {
        iconBg: 'bg-sky-50 dark:bg-sky-500/10 text-sky-600 dark:text-sky-400',
        progressBar: 'bg-sky-500',
        trendColor: 'text-sky-600 dark:text-sky-400',
      }
    case 'emerald':
      return {
        iconBg: 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
        progressBar: 'bg-emerald-500',
        trendColor: 'text-emerald-600 dark:text-emerald-400',
      }
    case 'amber':
      return {
        iconBg: 'bg-amber-50 dark:bg-amber-500/10 text-amber-600 dark:text-amber-400',
        progressBar: 'bg-amber-500',
        trendColor: 'text-amber-600 dark:text-amber-400',
      }
    case 'rose':
      return {
        iconBg: 'bg-rose-50 dark:bg-rose-500/10 text-rose-600 dark:text-rose-400',
        progressBar: 'bg-rose-500',
        trendColor: 'text-rose-600 dark:text-rose-400',
      }
    case 'slate':
      return {
        iconBg: 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300',
        progressBar: 'bg-slate-500',
        trendColor: 'text-slate-600 dark:text-slate-400',
      }
    case 'indigo':
    default:
      return {
        iconBg: 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400',
        progressBar: 'bg-indigo-600',
        trendColor: 'text-indigo-600 dark:text-indigo-400',
      }
  }
})

const clampedProgress = computed(() => {
  const p = props.progressPercent ?? 0
  return Math.max(4, Math.min(100, p))
})
</script>

<template>
  <div
    class="bg-white dark:bg-slate-900 rounded-2xl p-3.5 sm:p-4 border border-slate-100/90 dark:border-slate-800/60 shadow-[0_4px_25px_-4px_rgba(0,0,0,0.05)] dark:shadow-none hover:shadow-md transition-all duration-200 flex flex-col justify-between group"
  >
    <!-- Top Row: Title (Left) + Pastel Icon Box (Right) -->
    <div class="flex items-center justify-between gap-2">
      <span
        class="text-[11px] sm:text-xs font-semibold text-slate-500 dark:text-slate-400 truncate"
      >
        {{ title }}
      </span>

      <div
        v-if="icon"
        class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 transition-transform duration-200 group-hover:scale-105"
        :class="variantStyles.iconBg"
      >
        <component :is="icon" class="w-4 h-4" />
      </div>
    </div>

    <!-- Numeric Value -->
    <div class="mt-1">
      <div
        v-if="loading"
        class="animate-pulse h-6 w-20 bg-slate-100 dark:bg-slate-800 rounded"
      ></div>
      <div
        v-else
        class="text-xl sm:text-2xl font-extrabold tracking-tight text-slate-900 dark:text-white font-sans"
      >
        {{ value }}
      </div>
    </div>

    <!-- Horizontal Colored Progress Bar -->
    <div class="mt-2.5">
      <div class="h-1.5 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
        <div
          v-if="!loading"
          class="h-full rounded-full transition-all duration-500 ease-out"
          :class="variantStyles.progressBar"
          :style="{ width: `${clampedProgress}%` }"
        ></div>
        <div
          v-else
          class="h-full w-1/3 bg-slate-200 dark:bg-slate-700 animate-pulse rounded-full"
        ></div>
      </div>
    </div>

    <!-- Bottom Row: Subtitle (Left) + Trend / Metric Tag (Right) -->
    <div
      class="mt-2 flex items-center justify-between text-[11px] font-medium text-slate-400 dark:text-slate-500"
    >
      <span class="truncate">
        {{ subtitle || '' }}
      </span>

      <div
        v-if="trend"
        class="flex items-center gap-0.5 shrink-0 font-semibold"
        :class="variantStyles.trendColor"
      >
        <svg
          v-if="trend.isPositive !== false"
          class="w-3 h-3 stroke-[2.5]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
        >
          <path d="M7 17L17 7M17 7H7M17 7V17" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <svg
          v-else
          class="w-3 h-3 stroke-[2.5]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
        >
          <path d="M7 7L17 17M17 17H7M17 17V7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span>{{ trend.value }}</span>
      </div>
    </div>
  </div>
</template>
