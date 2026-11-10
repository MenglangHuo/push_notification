<script setup lang="ts">
export interface TabItem {
  id: string
  label: string
  icon?: any
  badge?: string | number
}

defineProps<{
  modelValue: string
  tabs: TabItem[]
  size?: 'sm' | 'md'
}>()

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
    class="inline-flex items-center gap-1 p-1 bg-slate-100 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 select-none"
  >
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      @click="selectTab(tab.id)"
      class="flex items-center gap-1.5 rounded-lg font-semibold transition-all cursor-pointer"
      :class="[
        size === 'md' ? 'px-3.5 py-2 text-xs' : 'px-3 py-1.5 text-xs',
        tab.id === modelValue
          ? 'bg-white dark:bg-slate-900 text-blue-600 dark:text-blue-400 shadow-xs font-semibold'
          : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-900/50',
      ]"
    >
      <component v-if="tab.icon" :is="tab.icon" class="w-3.5 h-3.5" />
      <span>{{ tab.label }}</span>
      <span
        v-if="tab.badge !== undefined"
        class="ml-1 text-[10px] px-1.5 py-0.2 rounded-full font-mono font-bold"
        :class="
          tab.id === modelValue
            ? 'bg-blue-100 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300'
            : 'bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-400'
        "
      >
        {{ tab.badge }}
      </span>
    </button>
  </div>
</template>
