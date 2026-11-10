<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Calendar, ChevronDown, Check, Clock, ArrowRight } from 'lucide-vue-next'
import DatePicker from './DatePicker.vue'

export interface DateRangeResult {
  preset: string
  date_from: string
  date_to: string
  label: string
  fromDate?: string
  toDate?: string
  days?: number
}

const props = withDefaults(
  defineProps<{
    initialPreset?: string
  }>(),
  {
    initialPreset: 'this_month',
  },
)

const emit = defineEmits<{
  (e: 'change', value: DateRangeResult): void
}>()

const isOpen = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const activePreset = ref<string>(props.initialPreset)
const customFrom = ref<string>('')
const customTo = ref<string>('')
const activeCalendarTab = ref<'from' | 'to'>('from')

const presets = [
  { id: 'this_month', label: 'This Month' },
  { id: 'last_month', label: 'Last Month' },
  { id: 'today', label: 'Today' },
  { id: 'yesterday', label: 'Yesterday' },
  { id: '7_days', label: 'Last 7 Days' },
  { id: '14_days', label: 'Last 14 Days' },
  { id: '30_days', label: 'Last 30 Days' },
  { id: 'custom', label: 'Custom Range' },
]

function formatDateString(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function computePresetRange(presetId: string): {
  date_from: string
  date_to: string
  days?: number
} {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()

  if (presetId === 'today') {
    const d = formatDateString(now)
    return { date_from: d, date_to: d, days: 1 }
  }
  if (presetId === 'yesterday') {
    const y = new Date(now)
    y.setDate(y.getDate() - 1)
    const d = formatDateString(y)
    return { date_from: d, date_to: d, days: 2 }
  }
  if (presetId === '7_days') {
    const from = new Date(now)
    from.setDate(from.getDate() - 6)
    return { date_from: formatDateString(from), date_to: formatDateString(now), days: 7 }
  }
  if (presetId === '14_days') {
    const from = new Date(now)
    from.setDate(from.getDate() - 13)
    return { date_from: formatDateString(from), date_to: formatDateString(now), days: 14 }
  }
  if (presetId === '30_days') {
    const from = new Date(now)
    from.setDate(from.getDate() - 29)
    return { date_from: formatDateString(from), date_to: formatDateString(now), days: 30 }
  }
  if (presetId === 'this_month') {
    const from = new Date(year, month, 1)
    const to = new Date(year, month + 1, 0)
    return { date_from: formatDateString(from), date_to: formatDateString(to) }
  }
  if (presetId === 'last_month') {
    const from = new Date(year, month - 1, 1)
    const to = new Date(year, month, 0)
    return { date_from: formatDateString(from), date_to: formatDateString(to) }
  }
  return { date_from: formatDateString(now), date_to: formatDateString(now) }
}

const displayLabel = computed(() => {
  if (activePreset.value === 'custom') {
    if (customFrom.value && customTo.value) {
      return `${customFrom.value} → ${customTo.value}`
    }
    return 'Custom Range'
  }
  const match = presets.find((p) => p.id === activePreset.value)
  return match ? match.label : 'This Month'
})

function selectPreset(preset: (typeof presets)[number]) {
  activePreset.value = preset.id

  if (preset.id !== 'custom') {
    const range = computePresetRange(preset.id)
    customFrom.value = range.date_from
    customTo.value = range.date_to

    emit('change', {
      preset: preset.id,
      date_from: range.date_from,
      date_to: range.date_to,
      fromDate: range.date_from,
      toDate: range.date_to,
      days: range.days,
      label: preset.label,
    })
    isOpen.value = false
  }
}

function applyCustomRange() {
  if (!customFrom.value || !customTo.value) return

  emit('change', {
    preset: 'custom',
    date_from: customFrom.value,
    date_to: customTo.value,
    fromDate: customFrom.value,
    toDate: customTo.value,
    label: `${customFrom.value} → ${customTo.value}`,
  })
  isOpen.value = false
}

function handleClickOutside(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    isOpen.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) {
    isOpen.value = false
  }
}

onMounted(() => {
  const initial = computePresetRange(activePreset.value)
  customFrom.value = initial.date_from
  customTo.value = initial.date_to

  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div ref="containerRef" class="relative inline-block text-left select-none">
    <!-- Trigger Button -->
    <button
      type="button"
      @click="isOpen = !isOpen"
      class="flex items-center gap-2 px-3 py-2 rounded-xl border text-xs font-semibold bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-200 hover:border-slate-300 dark:hover:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 shadow-xs transition-all focus:outline-none focus:ring-2 focus:ring-indigo-500/30 cursor-pointer"
    >
      <Calendar class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400 shrink-0" />
      <span class="truncate font-medium">{{ displayLabel }}</span>
      <ChevronDown
        class="w-3.5 h-3.5 text-slate-400 transition-transform duration-200 shrink-0"
        :class="{ 'rotate-180': isOpen }"
      />
    </button>

    <!-- Popover -->
    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="transform scale-95 opacity-0 -translate-y-1"
      enter-to-class="transform scale-100 opacity-100 translate-y-0"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="transform scale-100 opacity-100 translate-y-0"
      leave-to-class="transform scale-95 opacity-0 -translate-y-1"
    >
      <div
        v-if="isOpen"
        class="absolute right-0 z-50 mt-1.5 w-76 sm:w-84 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-2xl ring-1 ring-black/5 dark:ring-white/10 p-4 focus:outline-none"
      >
        <div
          class="flex items-center justify-between pb-3 mb-3 border-b border-slate-100 dark:border-slate-800"
        >
          <div class="flex items-center gap-2">
            <Clock class="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
            <span class="text-xs font-bold text-slate-900 dark:text-white uppercase tracking-wider">
              Date Range Presets
            </span>
          </div>
        </div>

        <!-- Presets Grid -->
        <div class="grid grid-cols-2 gap-1.5 mb-3">
          <button
            v-for="p in presets"
            :key="p.id"
            type="button"
            @click="selectPreset(p)"
            class="flex items-center justify-between px-2.5 py-1.5 rounded-lg text-xs font-medium transition-all text-left cursor-pointer"
            :class="[
              activePreset === p.id
                ? 'bg-indigo-600 text-white font-semibold shadow-xs'
                : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <span>{{ p.label }}</span>
            <Check v-if="activePreset === p.id" class="w-3 h-3 text-white shrink-0 ml-1" />
          </button>
        </div>

        <!-- Custom Range Calendar using custom DatePicker component -->
        <div
          v-if="activePreset === 'custom'"
          class="pt-3 border-t border-slate-100 dark:border-slate-800 space-y-3 animate-in fade-in duration-150"
        >
          <!-- Start / End Date Selector Tabs -->
          <div class="grid grid-cols-2 gap-1.5 p-1 bg-slate-100 dark:bg-slate-950 rounded-xl">
            <button
              type="button"
              @click="activeCalendarTab = 'from'"
              class="py-1 px-2 rounded-lg text-xs font-medium transition-all flex flex-col items-center"
              :class="
                activeCalendarTab === 'from'
                  ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-white shadow-xs font-bold'
                  : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
              "
            >
              <span class="text-[10px] text-slate-400 uppercase">From</span>
              <span class="font-mono text-[11px]">{{ customFrom || 'Select' }}</span>
            </button>
            <button
              type="button"
              @click="activeCalendarTab = 'to'"
              class="py-1 px-2 rounded-lg text-xs font-medium transition-all flex flex-col items-center"
              :class="
                activeCalendarTab === 'to'
                  ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-white shadow-xs font-bold'
                  : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
              "
            >
              <span class="text-[10px] text-slate-400 uppercase">To</span>
              <span class="font-mono text-[11px]">{{ customTo || 'Select' }}</span>
            </button>
          </div>

          <!-- Embedded Custom DatePicker -->
          <div
            class="bg-slate-50/70 dark:bg-slate-950/60 rounded-xl border border-slate-200 dark:border-slate-800/80 p-1"
          >
            <DatePicker
              v-if="activeCalendarTab === 'from'"
              v-model="customFrom"
              :inline="true"
              @change="activeCalendarTab = 'to'"
            />
            <DatePicker v-else v-model="customTo" :inline="true" />
          </div>

          <!-- Apply Range Action Button -->
          <button
            type="button"
            @click="applyCustomRange"
            class="w-full py-2 px-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-sm transition-all flex items-center justify-center gap-1.5 cursor-pointer"
          >
            <span>Apply Selected Range</span>
            <ArrowRight class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>
