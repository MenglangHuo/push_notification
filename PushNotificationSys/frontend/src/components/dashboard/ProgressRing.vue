<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number
    size?: number
    strokeWidth?: number
    color?: string
    trackColor?: string
    label?: string
    loading?: boolean
  }>(),
  {
    size: 64,
    strokeWidth: 4,
    color: '#6366f1',
    loading: false,
  },
)

const center = computed(() => props.size / 2)
const radius = computed(() => Math.max(0, (props.size - props.strokeWidth) / 2))
const circumference = computed(() => 2 * Math.PI * radius.value)

const clampedValue = computed(() => Math.max(0, Math.min(100, props.value ?? 0)))
const strokeDashoffset = computed(() => {
  return circumference.value - (clampedValue.value / 100) * circumference.value
})

const displayText = computed(() => {
  if (props.label !== undefined) return props.label
  return `${Math.round(clampedValue.value)}%`
})
</script>

<template>
  <div
    v-if="loading"
    class="animate-pulse rounded-full border border-slate-200 dark:border-slate-800 bg-slate-100/50 dark:bg-slate-800/40 shrink-0 inline-flex items-center justify-center"
    :style="{ width: `${size}px`, height: `${size}px` }"
  ></div>
  <div
    v-else
    class="relative inline-flex items-center justify-center shrink-0 select-none"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" class="overflow-visible">
      <!-- Background track -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        :stroke-width="strokeWidth"
        fill="none"
        :stroke="trackColor || 'currentColor'"
        :class="!trackColor ? 'text-slate-100 dark:text-slate-800' : ''"
      />
      <!-- Animated Progress Circle -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        :stroke-width="strokeWidth"
        fill="none"
        :stroke="color"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="strokeDashoffset"
        :transform="`rotate(-90 ${center} ${center})`"
        class="transition-[stroke-dashoffset] duration-700 ease-out"
      />
    </svg>

    <!-- Center text label -->
    <span
      v-if="displayText"
      class="absolute inset-0 flex items-center justify-center font-bold tracking-tight text-slate-800 dark:text-slate-200 tabular-nums pointer-events-none"
      :class="size >= 48 ? 'text-xs' : 'text-[9px]'"
    >
      {{ displayText }}
    </span>
  </div>
</template>
