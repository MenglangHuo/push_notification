<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    data: number[]
    color?: string
    fillColor?: string
    width?: number
    height?: number
    strokeWidth?: number
    loading?: boolean
  }>(),
  {
    color: '#6366f1',
    width: 80,
    height: 32,
    strokeWidth: 1.5,
    loading: false,
  },
)

const rawId = useId()
const gradientId = computed(() => `sparkline-grad-${rawId.replace(/[^a-zA-Z0-9_-]/g, '')}`)
const effectiveFillColor = computed(() => props.fillColor || props.color)

const points = computed(() => {
  if (!props.data || props.data.length === 0) return []
  const n = props.data.length
  const min = Math.min(...props.data)
  const max = Math.max(...props.data)
  const range = max - min

  const padY = props.strokeWidth
  const usableHeight = Math.max(1, props.height - padY * 2)
  const usableWidth = Math.max(1, props.width - props.strokeWidth * 2)

  return props.data.map((val, idx) => {
    const x = n > 1 ? props.strokeWidth + (idx / (n - 1)) * usableWidth : props.width / 2
    const y =
      range === 0 ? props.height / 2 : props.height - padY - ((val - min) / range) * usableHeight
    return { x: Number(x.toFixed(2)), y: Number(y.toFixed(2)) }
  })
})

const polylinePoints = computed(() => {
  return points.value.map((p) => `${p.x},${p.y}`).join(' ')
})

const polygonPoints = computed(() => {
  if (points.value.length === 0) return ''
  const first = points.value[0]
  const last = points.value[points.value.length - 1]
  const baseLineY = props.height
  return `${first.x},${baseLineY} ${polylinePoints.value} ${last.x},${baseLineY}`
})
</script>

<template>
  <div
    v-if="loading"
    class="animate-pulse rounded bg-slate-100 dark:bg-slate-800"
    :style="{ width: `${width}px`, height: `${height}px` }"
  ></div>
  <svg
    v-else
    :width="width"
    :height="height"
    :viewBox="`0 0 ${width} ${height}`"
    class="overflow-visible inline-block shrink-0"
  >
    <defs>
      <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" :stop-color="effectiveFillColor" stop-opacity="0.35" />
        <stop offset="100%" :stop-color="effectiveFillColor" stop-opacity="0.0" />
      </linearGradient>
    </defs>
    <polygon
      v-if="polygonPoints"
      :points="polygonPoints"
      :fill="`url(#${gradientId})`"
    />
    <polyline
      v-if="polylinePoints"
      :points="polylinePoints"
      fill="none"
      :stroke="color"
      :stroke-width="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>
