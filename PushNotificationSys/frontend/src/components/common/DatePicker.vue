<script setup lang="ts">
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string // 'YYYY-MM-DD'
    placeholder?: string
    label?: string
    inline?: boolean // If true, renders the calendar directly without popover
  }>(),
  {
    placeholder: 'Select date...',
    inline: false,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

const isOpen = ref(false)
const containerRef = ref<HTMLElement | null>(null)

// Parse the current value or default to current date
function parseDate(str: string): Date {
  if (!str) return new Date()
  const [y, m, d] = str.split('-').map(Number)
  if (!y || !m || !d) return new Date()
  return new Date(y, m - 1, d)
}

// const selectedDate = computed(() => (props.modelValue ? parseDate(props.modelValue) : null));

// Navigation month/year
const currentNavDate = ref<Date>(props.modelValue ? parseDate(props.modelValue) : new Date())

const navYear = computed(() => currentNavDate.value.getFullYear())
const navMonth = computed(() => currentNavDate.value.getMonth())

const monthNames = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

const dayNames = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']

function prevMonth() {
  currentNavDate.value = new Date(navYear.value, navMonth.value - 1, 1)
}

function nextMonth() {
  currentNavDate.value = new Date(navYear.value, navMonth.value + 1, 1)
}

interface CalendarDay {
  date: Date
  dateStr: string
  dayNumber: number
  isCurrentMonth: boolean
  isToday: boolean
  isSelected: boolean
}

const calendarDays = computed<CalendarDay[]>(() => {
  const days: CalendarDay[] = []
  const year = navYear.value
  const month = navMonth.value

  const firstDayOfMonth = new Date(year, month, 1).getDay()
  const daysInCurrentMonth = new Date(year, month + 1, 0).getDate()
  const daysInPrevMonth = new Date(year, month, 0).getDate()

  const todayStr = formatToISO(new Date())
  const selectedStr = props.modelValue

  // Previous month padding days
  for (let i = firstDayOfMonth - 1; i >= 0; i--) {
    const d = new Date(year, month - 1, daysInPrevMonth - i)
    const dateStr = formatToISO(d)
    days.push({
      date: d,
      dateStr,
      dayNumber: daysInPrevMonth - i,
      isCurrentMonth: false,
      isToday: dateStr === todayStr,
      isSelected: dateStr === selectedStr,
    })
  }

  // Current month days
  for (let i = 1; i <= daysInCurrentMonth; i++) {
    const d = new Date(year, month, i)
    const dateStr = formatToISO(d)
    days.push({
      date: d,
      dateStr,
      dayNumber: i,
      isCurrentMonth: true,
      isToday: dateStr === todayStr,
      isSelected: dateStr === selectedStr,
    })
  }

  // Next month padding days to complete 35 or 42 grid cells
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    const d = new Date(year, month + 1, i)
    const dateStr = formatToISO(d)
    days.push({
      date: d,
      dateStr,
      dayNumber: i,
      isCurrentMonth: false,
      isToday: dateStr === todayStr,
      isSelected: dateStr === selectedStr,
    })
  }

  return days
})

function formatToISO(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function selectDay(day: CalendarDay) {
  emit('update:modelValue', day.dateStr)
  emit('change', day.dateStr)
  if (!props.inline) {
    isOpen.value = false
  }
}

function handleClickOutside(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    isOpen.value = false
  }
}

onMounted(() => {
  if (!props.inline) {
    document.addEventListener('click', handleClickOutside)
  }
})

onUnmounted(() => {
  if (!props.inline) {
    document.removeEventListener('click', handleClickOutside)
  }
})
</script>

<template>
  <div
    ref="containerRef"
    :class="inline ? 'w-full' : 'relative inline-block text-left select-none'"
  >
    <!-- Trigger Button (when not inline) -->
    <div v-if="!inline">
      <label
        v-if="label"
        class="block text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 mb-1"
      >
        {{ label }}
      </label>
      <button
        type="button"
        @click="isOpen = !isOpen"
        class="flex items-center justify-between gap-2 px-3 py-1.5 rounded-xl border text-xs font-medium transition-all shadow-xs bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-slate-800 dark:text-slate-200 hover:border-slate-300 dark:hover:border-slate-700 w-full"
      >
        <div class="flex items-center gap-2 truncate">
          <CalendarIcon class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400 shrink-0" />
          <span class="truncate font-mono">{{ modelValue || placeholder }}</span>
        </div>
      </button>
    </div>

    <!-- Calendar View Container -->
    <div
      v-if="inline || isOpen"
      :class="[
        inline
          ? 'w-full p-2 bg-transparent'
          : 'absolute z-50 mt-1.5 w-64 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-xl p-3 ring-1 ring-black/5 dark:ring-white/10',
      ]"
    >
      <!-- Month & Year Navigation -->
      <div
        class="flex items-center justify-between mb-2 pb-2 border-b border-slate-100 dark:border-slate-800/80"
      >
        <button
          type="button"
          @click.stop="prevMonth"
          class="p-1 rounded-lg text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          title="Previous Month"
        >
          <ChevronLeft class="w-4 h-4" />
        </button>

        <span class="text-xs font-bold text-slate-800 dark:text-slate-200">
          {{ monthNames[navMonth] }} {{ navYear }}
        </span>

        <button
          type="button"
          @click.stop="nextMonth"
          class="p-1 rounded-lg text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          title="Next Month"
        >
          <ChevronRight class="w-4 h-4" />
        </button>
      </div>

      <!-- Day of Week Headers -->
      <div class="grid grid-cols-7 gap-1 text-center mb-1">
        <span
          v-for="d in dayNames"
          :key="d"
          class="text-[10px] font-semibold text-slate-400 dark:text-slate-500 uppercase"
        >
          {{ d }}
        </span>
      </div>

      <!-- Day Cells Grid -->
      <div class="grid grid-cols-7 gap-1">
        <button
          v-for="(day, idx) in calendarDays"
          :key="idx"
          type="button"
          @click.stop="selectDay(day)"
          class="h-7 w-7 text-xs rounded-lg font-mono flex items-center justify-center transition-all cursor-pointer mx-auto"
          :class="[
            day.isSelected
              ? 'bg-indigo-600 text-white font-bold shadow-xs'
              : day.isToday
                ? 'border border-indigo-500 text-indigo-600 dark:text-indigo-400 font-bold bg-indigo-50/50 dark:bg-indigo-950/30'
                : day.isCurrentMonth
                  ? 'text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800'
                  : 'text-slate-300 dark:text-slate-600 hover:bg-slate-50 dark:hover:bg-slate-850',
          ]"
        >
          {{ day.dayNumber }}
        </button>
      </div>
    </div>
  </div>
</template>
