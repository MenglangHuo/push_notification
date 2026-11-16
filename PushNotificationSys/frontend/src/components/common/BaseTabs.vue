<script setup lang="ts">
export interface TabItem {
  id: string
  label: string
  icon?: any
  badge?: string | number
}

withDefaults(
  defineProps<{
    modelValue: string
    tabs: TabItem[]
    size?: 'sm' | 'md'
  }>(),
  {
    size: 'sm',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

function selectTab(id: string) {
  emit('update:modelValue', id)
  emit('change', id)
}
</script>

<template>
  <div
    class="inline-flex items-center gap-1 p-0.5 sm:p-1 bg-slate-100 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 select-none shrink-0"
    :class="size === 'md' ? 'h-10' : 'h-9'"
  >
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      @click="selectTab(tab.id)"
      class="rounded-lg font-semibold whitespace-nowrap shrink-0 flex items-center gap-1.5 transition-all cursor-pointer"
      :class="[
        size === 'md' ? 'h-8 px-3.5 text-xs' : 'h-7.5 sm:h-8 px-2.5 sm:px-3 text-xs',
        tab.id === modelValue
          ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs font-semibold'
          : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/50 dark:hover:bg-slate-900/50',
      ]"
    >
      <component v-if="tab.icon" :is="tab.icon" class="w-3.5 h-3.5 shrink-0" />
      <span>{{ tab.label }}</span>
      <span
        v-if="tab.badge !== undefined"
        class="ml-0.5 text-[10px] px-1.5 h-4 min-w-4 inline-flex items-center justify-center rounded-full font-mono font-bold leading-none"
        :class="
          tab.id === modelValue
            ? 'bg-indigo-100 dark:bg-indigo-950/60 text-indigo-700 dark:text-indigo-300'
            : 'bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-400'
        "
      >
        {{ tab.badge }}
      </span>
    </button>
  </div>
</template>
