<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number
    duration?: number
    prefix?: string
    suffix?: string
    decimals?: number
    locale?: boolean
  }>(),
  {
    duration: 800,
    prefix: '',
    suffix: '',
    decimals: 0,
    locale: true,
  },
)

const displayValue = ref<number>(0)
let animationFrameId: number | null = null

function easeOutExpo(x: number): number {
  return x === 1 ? 1 : 1 - Math.pow(2, -10 * x)
}

function animate(startVal: number, targetVal: number, duration: number) {
  if (animationFrameId !== null) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }

  if (duration <= 0 || startVal === targetVal) {
    displayValue.value = targetVal
    return
  }

  const startTime = performance.now()

  const step = (now: number) => {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    const easedProgress = easeOutExpo(progress)
    displayValue.value = startVal + (targetVal - startVal) * easedProgress

    if (progress < 1) {
      animationFrameId = requestAnimationFrame(step)
    } else {
      displayValue.value = targetVal
      animationFrameId = null
    }
  }

  animationFrameId = requestAnimationFrame(step)
}

watch(
  () => props.value,
  (newVal, oldVal) => {
    const start = oldVal !== undefined ? displayValue.value : 0
    animate(start, newVal ?? 0, props.duration)
  },
)

onMounted(() => {
  animate(0, props.value ?? 0, props.duration)
})

onUnmounted(() => {
  if (animationFrameId !== null) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
})

const formattedValue = computed(() => {
  const num = displayValue.value
  let formattedNumber: string
  if (props.locale) {
    formattedNumber = num.toLocaleString(undefined, {
      minimumFractionDigits: props.decimals,
      maximumFractionDigits: props.decimals,
    })
  } else {
    formattedNumber = num.toFixed(props.decimals)
  }
  return `${props.prefix}${formattedNumber}${props.suffix}`
})
</script>

<template>
  <span class="tabular-nums">{{ formattedValue }}</span>
</template>
