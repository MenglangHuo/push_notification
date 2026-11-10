<script setup lang="ts">
import { computed } from 'vue'
import { Loader2, BellRing, Radio, Zap } from 'lucide-vue-next'
import { useSplashStore } from '@/stores/splashStore'

const props = withDefaults(
  defineProps<{
    visible?: boolean
    fullscreen?: boolean
    title?: string
    subtitle?: string
    message?: string
    progress?: number | null
    showProgress?: boolean
    backdropBlur?: boolean
  }>(),
  {
    visible: undefined,
    fullscreen: true,
    title: undefined,
    subtitle: undefined,
    message: undefined,
    progress: undefined,
    showProgress: true,
    backdropBlur: true,
  },
)

const emit = defineEmits<{
  (e: 'after-leave'): void
}>()

const splashStore = useSplashStore()

const isVisible = computed(() => {
  return props.visible !== undefined ? props.visible : splashStore.isVisible
})

const displayTitle = computed(() => {
  return props.title !== undefined ? props.title : splashStore.title
})

const displaySubtitle = computed(() => {
  return props.subtitle !== undefined ? props.subtitle : splashStore.subtitle
})

const displayMessage = computed(() => {
  return props.message !== undefined ? props.message : splashStore.message
})

const currentProgress = computed(() => {
  return props.progress !== undefined ? props.progress : splashStore.progress
})

const isDeterminate = computed(() => {
  return typeof currentProgress.value === 'number' && !isNaN(currentProgress.value)
})

const clampedProgress = computed(() => {
  if (!isDeterminate.value || currentProgress.value === null) return 0
  return Math.min(100, Math.max(0, currentProgress.value))
})
</script>

<template>
  <Transition name="splash-fade" @after-leave="emit('after-leave')">
    <div
      v-if="isVisible"
      data-testid="splash-screen"
      :class="[
        'flex flex-col items-center justify-center select-none overflow-hidden transition-all duration-300',
        fullscreen
          ? 'fixed inset-0 z-50 bg-slate-50/95 dark:bg-slate-950/95'
          : 'absolute inset-0 z-30 bg-white/95 dark:bg-slate-900/95 rounded-2xl',
        backdropBlur ? 'backdrop-blur-xl' : '',
      ]"
      role="status"
      aria-live="polite"
    >
      <!-- High-tech Sub-millimeter Dot Grid Texture -->
      <div
        class="absolute inset-0 opacity-[0.03] dark:opacity-[0.07] pointer-events-none"
        style="
          background-image: radial-gradient(rgba(99, 102, 241, 0.8) 1px, transparent 1px);
          background-size: 24px 24px;
        "
      ></div>

      <!-- Ambient Glow Aura -->
      <div
        class="absolute w-72 h-72 sm:w-96 sm:h-96 bg-gradient-to-tr from-indigo-500/25 via-violet-500/20 to-sky-400/20 dark:from-indigo-600/30 dark:via-violet-600/25 dark:to-sky-400/25 rounded-full blur-3xl pointer-events-none -translate-y-6 animate-pulse-subtle"
      ></div>

      <!-- Center Card Content -->
      <div class="relative z-10 flex flex-col items-center text-center px-6 max-w-md w-full">
        <!-- Beacon Radar Pulse Container -->
        <div class="relative flex items-center justify-center mb-5 w-16 h-16 sm:w-20 sm:h-20">
          <!-- 6 Concentric Round Borders Wrapping Logo -->
          <div class="splash-round-border splash-round-border-1"></div>
          <div class="splash-round-border splash-round-border-2"></div>
          <div class="splash-round-border splash-round-border-3"></div>
          <div class="splash-round-border splash-round-border-4"></div>
          <div class="splash-round-border splash-round-border-5"></div>
          <div class="splash-round-border splash-round-border-6"></div>

          <!-- Central Glowing Squircle -->
          <div
            class="relative w-16 h-16 sm:w-20 sm:h-20 rounded-2xl sm:rounded-3xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-sky-400 dark:from-indigo-600 dark:via-indigo-600 dark:to-sky-500 flex items-center justify-center shadow-xl shadow-indigo-600/35 ring-4 ring-indigo-500/20 dark:ring-indigo-400/25 z-10"
          >
            <BellRing class="w-8 h-8 sm:w-10 sm:h-10 text-white stroke-[2.2] animate-bounce-subtle" />
          </div>
        </div>

        <!-- Optional Footer / Actions Slot -->
        <slot name="footer" />
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* 6 Active Concentric Round Borders Wrapping the Logo */
.splash-round-border {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 72px;
  height: 72px;
  margin-top: -36px;
  margin-left: -36px;
  border-radius: 9999px;
  pointer-events: none;
  box-sizing: border-box;
  transform-origin: center center;
  border: 1.5px solid rgba(99, 102, 241, 0.45);
  animation: splash-radar-ripple 2.4s cubic-bezier(0.15, 0.3, 0.4, 1) infinite;
  animation-fill-mode: both;
}

.splash-round-border-1 {
  animation-delay: 0s;
}
.splash-round-border-2 {
  animation-delay: -0.4s;
}
.splash-round-border-3 {
  animation-delay: -0.8s;
}
.splash-round-border-4 {
  animation-delay: -1.2s;
}
.splash-round-border-5 {
  animation-delay: -1.6s;
}
.splash-round-border-6 {
  animation-delay: -2.0s;
}

:global(.dark) .splash-round-border,
.dark .splash-round-border {
  border-color: rgba(129, 140, 248, 0.6);
}

@keyframes splash-radar-ripple {
  0% {
    transform: scale(0.9);
    opacity: 0.95;
    border-width: 2px;
    border-color: rgba(99, 102, 241, 0.55);
  }
  35% {
    opacity: 0.7;
    border-color: rgba(99, 102, 241, 0.4);
  }
  70% {
    opacity: 0.3;
    border-color: rgba(99, 102, 241, 0.2);
  }
  100% {
    transform: scale(4.8);
    opacity: 0;
    border-width: 1px;
    border-color: rgba(99, 102, 241, 0);
  }
}

@keyframes splash-shimmer-run {
  0% {
    transform: translateX(-150%);
  }
  50% {
    transform: translateX(100%);
  }
  100% {
    transform: translateX(250%);
  }
}

.splash-shimmer-bar {
  animation: splash-shimmer-run 1.6s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

@keyframes bounce-subtle {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-2px);
  }
}

.animate-bounce-subtle {
  animation: bounce-subtle 2s ease-in-out infinite;
}

.splash-fade-enter-active,
.splash-fade-leave-active {
  transition:
    opacity 0.3s ease,
    transform 0.3s ease;
}

.splash-fade-enter-from,
.splash-fade-leave-to {
  opacity: 0;
  transform: scale(1.02);
}
</style>
