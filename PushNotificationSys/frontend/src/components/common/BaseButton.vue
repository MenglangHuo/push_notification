<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
    size?: 'xs' | 'sm' | 'md' | 'lg'
    disabled?: boolean
    loading?: boolean
    icon?: any
    iconRight?: any
    type?: 'button' | 'submit' | 'reset'
  }>(),
  {
    variant: 'primary',
    size: 'sm',
    disabled: false,
    loading: false,
    type: 'button',
  },
)

const emit = defineEmits<{
  (e: 'click', event: MouseEvent): void
}>()

const variantClasses = computed(() => {
  switch (props.variant) {
    case 'secondary':
      return 'bg-slate-100 dark:bg-slate-800 text-slate-800 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700 shadow-2xs'
    case 'outline':
      return 'bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-200 border border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-700 shadow-2xs'
    case 'ghost':
      return 'bg-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 border border-transparent'
    case 'danger':
      return 'bg-rose-600 hover:bg-rose-500 text-white shadow-sm shadow-rose-600/20 border border-rose-500/30'
    case 'primary':
    default:
      return 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm shadow-indigo-600/30 border border-indigo-500/30'
  }
})

const sizeClasses = computed(() => {
  switch (props.size) {
    case 'xs':
      return 'px-2.5 py-1 text-[11px] rounded-lg gap-1.5'
    case 'md':
      return 'px-4 py-2.5 text-sm rounded-xl gap-2 font-semibold'
    case 'lg':
      return 'px-5 py-3 text-base rounded-2xl gap-2.5 font-bold'
    case 'sm':
    default:
      return 'px-3 py-1.5 text-xs rounded-xl gap-2 font-medium'
  }
})
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    @click="emit('click', $event)"
    class="inline-flex items-center justify-center font-medium transition-all select-none cursor-pointer active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none"
    :class="[variantClasses, sizeClasses]"
  >
    <!-- Loading Spinner -->
    <span
      v-if="loading"
      class="w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full animate-spin shrink-0"
    ></span>

    <!-- Leading Icon -->
    <component v-else-if="icon" :is="icon" class="w-4 h-4 shrink-0" />

    <!-- Button Text / Slot Content -->
    <slot />

    <!-- Trailing Icon -->
    <component v-if="iconRight && !loading" :is="iconRight" class="w-4 h-4 shrink-0" />
  </button>
</template>
