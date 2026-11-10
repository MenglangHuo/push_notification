<script setup lang="ts">
import { computed } from 'vue'
import { activeProfile, isProduction } from '@/config/environment'
import { Sparkles } from 'lucide-vue-next'

defineProps<{
  size?: 'sm' | 'md'
}>()

const profile = computed(() => activeProfile.value)

const colorStyles = computed(() => {
  switch (profile.value.color) {
    case 'emerald':
      return {
        badge: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/30',
        dot: 'bg-emerald-500',
        ring: 'ring-emerald-500/30',
      }
    case 'amber':
      return {
        badge: 'bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/30',
        dot: 'bg-amber-500',
        ring: 'ring-amber-500/30',
      }
    case 'rose':
      return {
        badge: 'bg-rose-500/10 text-rose-700 dark:text-rose-300 border-rose-500/30',
        dot: 'bg-rose-500 animate-pulse',
        ring: 'ring-rose-500/30',
      }
    case 'purple':
      return {
        badge: 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border-purple-500/30',
        dot: 'bg-purple-500',
        ring: 'ring-purple-500/30',
      }
    case 'sky':
    default:
      return {
        badge: 'bg-sky-500/10 text-sky-700 dark:text-sky-300 border-sky-500/30',
        dot: 'bg-sky-500',
        ring: 'ring-sky-500/30',
      }
  }
})
</script>

<template>
  <div
    v-if="!isProduction"
    class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-xl text-xs font-semibold border shadow-2xs select-none pointer-events-none"
    :class="colorStyles.badge"
    :title="`Current Environment: ${profile.name}`"
  >
    <!-- Status Dot -->
    <span class="relative flex items-center justify-center shrink-0">
      <Sparkles v-if="profile.isMock" class="w-3.5 h-3.5 text-purple-500 shrink-0" />
      <span
        v-else
        class="w-2 h-2 rounded-full ring-2"
        :class="[colorStyles.dot, colorStyles.ring]"
      ></span>
    </span>

    <!-- Label -->
    <span class="truncate max-w-[110px] sm:max-w-none text-[11px] font-bold tracking-tight">
      {{ profile.shortLabel }}
    </span>
  </div>
</template>
