<script setup lang="ts">
import { computed } from 'vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    currentPage: number
    lastPage: number
    total: number
    perPage: number
    from?: number
    to?: number
    disabled?: boolean
    perPageOptions?: number[]
    itemLabel?: string
  }>(),
  {
    from: 0,
    to: 0,
    disabled: false,
    perPageOptions: () => [10, 20, 50, 100],
    itemLabel: 'items',
  },
)

const emit = defineEmits<{
  (e: 'change-page', page: number): void
  (e: 'change-per-page', perPage: number): void
}>()

const perPageSelectOptions = computed<SelectOption[]>(() => {
  return props.perPageOptions.map((opt) => ({
    label: String(opt),
    value: opt,
  }))
})

const calculatedFrom = computed(() => {
  if (props.total === 0) return 0
  if (props.from) return props.from
  return (props.currentPage - 1) * props.perPage + 1
})

const calculatedTo = computed(() => {
  if (props.total === 0) return 0
  if (props.to) return Math.min(props.to, props.total)
  return Math.min(props.currentPage * props.perPage, props.total)
})

// Generate smart page list with ellipsis
const pageNumbers = computed(() => {
  const current = props.currentPage
  const last = Math.max(1, props.lastPage)
  const delta = 1 // Number of pages to show around current page
  const range: (number | string)[] = []

  for (let i = 1; i <= last; i++) {
    if (i === 1 || i === last || (i >= current - delta && i <= current + delta)) {
      range.push(i)
    } else if (range[range.length - 1] !== '...') {
      range.push('...')
    }
  }

  return range
})

function onPageClick(page: number | string) {
  if (typeof page !== 'number' || props.disabled || page === props.currentPage) return
  if (page < 1 || page > props.lastPage) return
  emit('change-page', page)
}

function onPerPageChange(val: string | number) {
  const num = Number(val)
  if (!isNaN(num) && num !== props.perPage) {
    emit('change-per-page', num)
  }
}
</script>

<template>
  <div
    class="px-4 py-3.5 bg-slate-50/70 dark:bg-slate-950/70 border-t border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-slate-500 dark:text-slate-400"
  >
    <!-- Left: Per Page Selector & Summary Count -->
    <div class="flex flex-wrap items-center gap-3">
      <!-- Custom Per Page Selector Dropdown -->
      <div class="flex items-center gap-2">
        <span class="text-xs text-slate-500 dark:text-slate-400 font-medium whitespace-nowrap">
          Rows per page:
        </span>
        <CustomSelect
          :model-value="perPage"
          :options="perPageSelectOptions"
          :disabled="disabled"
          direction="up"
          size="xs"
          menu-class="min-w-[76px] w-auto"
          button-class="w-[72px]"
          @change="onPerPageChange"
        />
      </div>

      <!-- Range Information -->
      <div class="text-xs font-medium text-slate-600 dark:text-slate-400">
        Showing
        <span class="font-bold text-slate-900 dark:text-white font-mono"
          >{{ calculatedFrom }}-{{ calculatedTo }}</span
        >
        of <span class="font-bold text-slate-900 dark:text-white font-mono">{{ total }}</span>
        {{ itemLabel }}
      </div>
    </div>

    <!-- Right: Page Navigation -->
    <div class="flex items-center gap-1 self-center sm:self-auto">
      <!-- First Page -->
      <button
        type="button"
        @click="onPageClick(1)"
        :disabled="disabled || currentPage <= 1"
        title="First Page"
        class="p-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-30 disabled:pointer-events-none shadow-2xs transition-colors cursor-pointer"
      >
        <ChevronsLeft class="w-3.5 h-3.5" />
      </button>

      <!-- Previous Page -->
      <button
        type="button"
        @click="onPageClick(currentPage - 1)"
        :disabled="disabled || currentPage <= 1"
        title="Previous Page"
        class="p-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-30 disabled:pointer-events-none shadow-2xs transition-colors cursor-pointer"
      >
        <ChevronLeft class="w-3.5 h-3.5" />
      </button>

      <!-- Numbered Page Pills -->
      <div class="flex items-center gap-1 px-1">
        <template v-for="(page, idx) in pageNumbers" :key="idx">
          <span v-if="page === '...'" class="px-1 text-slate-400 text-xs font-mono select-none">
            ...
          </span>
          <button
            v-else
            type="button"
            @click="onPageClick(page)"
            :disabled="disabled"
            class="min-w-[28px] h-7 px-2 rounded-lg text-xs font-semibold transition-all shadow-2xs cursor-pointer flex items-center justify-center font-mono"
            :class="[
              page === currentPage
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            {{ page }}
          </button>
        </template>
      </div>

      <!-- Next Page -->
      <button
        type="button"
        @click="onPageClick(currentPage + 1)"
        :disabled="disabled || currentPage >= lastPage"
        title="Next Page"
        class="p-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-30 disabled:pointer-events-none shadow-2xs transition-colors cursor-pointer"
      >
        <ChevronRight class="w-3.5 h-3.5" />
      </button>

      <!-- Last Page -->
      <button
        type="button"
        @click="onPageClick(lastPage)"
        :disabled="disabled || currentPage >= lastPage"
        title="Last Page"
        class="p-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-30 disabled:pointer-events-none shadow-2xs transition-colors cursor-pointer"
      >
        <ChevronsRight class="w-3.5 h-3.5" />
      </button>
    </div>
  </div>
</template>
