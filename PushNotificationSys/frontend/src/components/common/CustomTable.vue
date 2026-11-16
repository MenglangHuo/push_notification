<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  Columns3,
  Check,
  RotateCcw,
  SlidersHorizontal,
  ChevronDown,
  ChevronUp,
  Maximize2,
  Minimize2,
  AlignJustify,
} from 'lucide-vue-next'
import CustomDropdown from '@/components/common/CustomDropdown.vue'

export interface TableColumn {
  key: string
  label: string
  width?: number
  minWidth?: number
  maxWidth?: number
  visible?: boolean
  resizable?: boolean
  sortable?: boolean
  align?: 'left' | 'center' | 'right'
  class?: string
  headerClass?: string
}

export type TableDensity = 'compact' | 'normal' | 'comfortable'

const props = withDefaults(
  defineProps<{
    tableId?: string
    columns: TableColumn[]
    items: any[]
    isLoading?: boolean
    emptyMessage?: string
    density?: TableDensity
    showToolbar?: boolean
    allowResize?: boolean
    allowColumnToggle?: boolean
    allowDensityToggle?: boolean
  }>(),
  {
    tableId: 'custom-table',
    items: () => [],
    isLoading: false,
    emptyMessage: 'No records found.',
    density: 'normal',
    showToolbar: true,
    allowResize: true,
    allowColumnToggle: true,
    allowDensityToggle: true,
  },
)

const emit = defineEmits<{
  (e: 'update:density', density: TableDensity): void
  (e: 'sort', columnKey: string, direction: 'asc' | 'desc'): void
}>()

// Active density state
const currentDensity = ref<TableDensity>(props.density)

// Column visibility state
const columnVisibility = ref<Record<string, boolean>>({})

// Column widths state (in pixels)
const columnWidths = ref<Record<string, number>>({})

// Column resizing active state
const resizingColKey = ref<string | null>(null)
const startX = ref(0)
const startWidth = ref(0)

// Storage keys
const storageKeyPrefix = computed(() => `app_table_${props.tableId}`)

// Initialize column states
function initColumns() {
  const visSaved = loadStoredVisibility()
  const widthSaved = loadStoredWidths()
  const densitySaved = loadStoredDensity()

  if (densitySaved) {
    currentDensity.value = densitySaved
  }

  props.columns.forEach((col) => {
    // Visibility
    if (visSaved && typeof visSaved[col.key] === 'boolean') {
      columnVisibility.value[col.key] = visSaved[col.key]
    } else {
      columnVisibility.value[col.key] = col.visible !== false
    }

    // Width
    if (widthSaved && typeof widthSaved[col.key] === 'number') {
      columnWidths.value[col.key] = widthSaved[col.key]
    } else {
      columnWidths.value[col.key] = col.width || getDefaultWidth(col)
    }
  })
}

function getDefaultWidth(col: TableColumn): number {
  if (col.width) return col.width
  if (col.key === 'action') return 100
  if (col.key === 'status') return 130
  if (col.key === 'targeting') return 140
  if (col.key === 'recipients') return 110
  if (col.key === 'deliveryRate') return 150
  if (col.key === 'dispatchedAt') return 160
  return 280
}

function loadStoredVisibility(): Record<string, boolean> | null {
  try {
    const raw = localStorage.getItem(`${storageKeyPrefix.value}_cols`)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function loadStoredWidths(): Record<string, number> | null {
  try {
    const raw = localStorage.getItem(`${storageKeyPrefix.value}_widths`)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function loadStoredDensity(): TableDensity | null {
  try {
    return localStorage.getItem(`${storageKeyPrefix.value}_density`) as TableDensity | null
  } catch {
    return null
  }
}

function saveVisibility() {
  try {
    localStorage.setItem(
      `${storageKeyPrefix.value}_cols`,
      JSON.stringify(columnVisibility.value),
    )
  } catch (e) {
    console.error('Failed to save table visibility', e)
  }
}

function saveWidths() {
  try {
    localStorage.setItem(
      `${storageKeyPrefix.value}_widths`,
      JSON.stringify(columnWidths.value),
    )
  } catch (e) {
    console.error('Failed to save table widths', e)
  }
}

function saveDensity(d: TableDensity) {
  try {
    localStorage.setItem(`${storageKeyPrefix.value}_density`, d)
  } catch (e) {
    console.error('Failed to save table density', e)
  }
}

// Visible columns list
const visibleColumns = computed(() => {
  return props.columns.filter((col) => columnVisibility.value[col.key] !== false)
})

function toggleColumn(key: string) {
  // Prevent hiding all columns
  const visibleCount = Object.values(columnVisibility.value).filter(Boolean).length
  if (columnVisibility.value[key] && visibleCount <= 1) {
    return
  }
  columnVisibility.value[key] = !columnVisibility.value[key]
  saveVisibility()
}

function resetColumns() {
  props.columns.forEach((col) => {
    columnVisibility.value[col.key] = col.visible !== false
    columnWidths.value[col.key] = col.width || getDefaultWidth(col)
  })
  saveVisibility()
  saveWidths()
}

function setDensity(d: TableDensity) {
  currentDensity.value = d
  saveDensity(d)
  emit('update:density', d)
}

// Density styling classes
const densityClasses = computed(() => {
  switch (currentDensity.value) {
    case 'compact':
      return {
        th: 'py-2 px-3 text-[10px]',
        td: 'py-2 px-3 text-[11px]',
        icon: 'w-3 h-3',
      }
    case 'comfortable':
      return {
        th: 'py-4 px-5 text-xs',
        td: 'py-4 px-5 text-sm',
        icon: 'w-4 h-4',
      }
    case 'normal':
    default:
      return {
        th: 'py-3 px-4 text-[11px]',
        td: 'py-3 px-4 text-xs',
        icon: 'w-3.5 h-3.5',
      }
  }
})

// Column Resizing Logic
function startResize(e: MouseEvent, colKey: string) {
  if (!props.allowResize) return
  resizingColKey.value = colKey
  startX.value = e.clientX
  startWidth.value = columnWidths.value[colKey] || 150

  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function onMouseMove(e: MouseEvent) {
  if (!resizingColKey.value) return
  const diff = e.clientX - startX.value
  const col = props.columns.find((c) => c.key === resizingColKey.value)
  const min = col?.minWidth || 70
  const max = col?.maxWidth || 800

  const newWidth = Math.max(min, Math.min(max, startWidth.value + diff))
  columnWidths.value[resizingColKey.value] = newWidth
}

function stopResize() {
  if (resizingColKey.value) {
    saveWidths()
  }
  resizingColKey.value = null
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function resetColumnWidth(colKey: string) {
  const col = props.columns.find((c) => c.key === colKey)
  if (col) {
    columnWidths.value[colKey] = col.width || getDefaultWidth(col)
    saveWidths()
  }
}

onMounted(() => {
  initColumns()
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', stopResize)
})

watch(
  () => props.columns,
  () => {
    initColumns()
  },
  { deep: true },
)
</script>

<template>
  <div class="space-y-3 text-left">
    <!-- Table Controls Toolbar Card -->
    <div
      v-if="showToolbar"
      class="bg-white dark:bg-slate-900/90 p-3 sm:p-3.5 rounded-2xl border border-slate-200/90 dark:border-slate-800 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3 transition-all duration-150"
    >
      <!-- Custom Toolbar Filters / Search Slot -->
      <div class="flex-1 min-w-0">
        <slot name="toolbar-left" />
      </div>

      <!-- Right Actions: Columns Toggle & Density Selector -->
      <div class="flex items-center gap-2.5 shrink-0 flex-wrap sm:flex-nowrap">
        <slot name="toolbar-middle" />

        <!-- Density / Size Preset Selector -->
        <div
          v-if="allowDensityToggle"
          class="flex items-center h-9 p-0.5 rounded-xl bg-slate-100/80 dark:bg-slate-800/80 border border-slate-200/80 dark:border-slate-700/60 shadow-2xs"
          title="Adjust table row height and size"
        >
          <button
            type="button"
            @click="setDensity('compact')"
            class="h-7.5 px-2.5 rounded-lg text-xs font-semibold transition cursor-pointer"
            :class="[
              currentDensity === 'compact'
                ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-2xs'
                : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
            ]"
            title="Compact row size"
          >
            Small
          </button>
          <button
            type="button"
            @click="setDensity('normal')"
            class="h-7.5 px-2.5 rounded-lg text-xs font-semibold transition cursor-pointer"
            :class="[
              currentDensity === 'normal'
                ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-2xs'
                : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
            ]"
            title="Normal row size"
          >
            Normal
          </button>
          <button
            type="button"
            @click="setDensity('comfortable')"
            class="h-7.5 px-2.5 rounded-lg text-xs font-semibold transition cursor-pointer"
            :class="[
              currentDensity === 'comfortable'
                ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-2xs'
                : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
            ]"
            title="Spacious row size"
          >
            Large
          </button>
        </div>

        <!-- Columns Show/Hide Dropdown -->
        <CustomDropdown
          v-if="allowColumnToggle"
          align="right"
          width="w-56"
        >
          <template #trigger="{ isOpen }">
            <button
              type="button"
              class="h-9 px-3 rounded-xl border border-slate-200/90 dark:border-slate-800 bg-slate-50/70 hover:bg-white dark:bg-slate-950/70 dark:hover:bg-slate-900 text-xs font-semibold text-slate-700 dark:text-slate-200 flex items-center gap-1.5 shadow-2xs transition-all cursor-pointer"
              :class="{ 'ring-2 ring-indigo-500/20 border-indigo-500': isOpen }"
            >
              <Columns3 class="w-3.5 h-3.5 text-slate-500 dark:text-slate-400" />
              <span>Columns</span>
              <span class="px-1.5 py-0.2 rounded-full text-[9px] font-mono bg-slate-200/70 dark:bg-slate-800 text-slate-600 dark:text-slate-400">
                {{ visibleColumns.length }}/{{ columns.length }}
              </span>
            </button>
          </template>

          <div class="p-2 space-y-1">
            <div class="flex items-center justify-between px-2 py-1 border-b border-slate-100 dark:border-slate-800 mb-1">
              <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Show / Hide Columns
              </span>
              <button
                type="button"
                @click="resetColumns"
                class="text-[10px] text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1 cursor-pointer"
                title="Reset columns and widths"
              >
                <RotateCcw class="w-2.5 h-2.5" />
                <span>Reset</span>
              </button>
            </div>

            <div class="max-h-56 overflow-y-auto space-y-0.5 custom-scroll">
              <label
                v-for="col in columns"
                :key="col.key"
                class="flex items-center justify-between px-2 py-1.5 rounded-lg text-xs hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer select-none transition"
              >
                <span class="text-slate-700 dark:text-slate-300 font-medium truncate">
                  {{ col.label }}
                </span>
                <input
                  type="checkbox"
                  :checked="columnVisibility[col.key] !== false"
                  @change="toggleColumn(col.key)"
                  class="rounded border-slate-300 dark:border-slate-700 text-indigo-600 focus:ring-indigo-500/20 cursor-pointer"
                />
              </label>
            </div>
          </div>
        </CustomDropdown>

        <slot name="toolbar-right" />
      </div>
    </div>

    <!-- Main Table Container -->
    <div
      class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200/90 dark:border-slate-800 shadow-xs overflow-hidden transition-all duration-150"
    >
      <div class="overflow-x-auto custom-scroll">
        <table class="w-full text-left table-fixed border-collapse">
          <!-- Colgroup for exact column widths -->
          <colgroup>
            <col
              v-for="col in visibleColumns"
              :key="col.key"
              :style="{ width: `${columnWidths[col.key] || getDefaultWidth(col)}px` }"
            />
          </colgroup>

          <!-- Table Header -->
          <thead>
            <tr
              class="bg-slate-50/70 dark:bg-slate-950/70 border-b border-slate-200/90 dark:border-slate-800 text-slate-400 font-semibold tracking-wider uppercase select-none"
            >
              <th
                v-for="col in visibleColumns"
                :key="col.key"
                class="relative font-bold group"
                :class="[
                  densityClasses.th,
                  col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left',
                  col.headerClass || '',
                ]"
              >
                <div class="flex items-center gap-1.5" :class="[col.align === 'right' ? 'justify-end' : col.align === 'center' ? 'justify-center' : 'justify-start']">
                  <slot :name="`header-${col.key}`" :column="col">
                    <span class="truncate">{{ col.label }}</span>
                  </slot>
                </div>

                <!-- Resizer Handle -->
                <div
                  v-if="allowResize && col.resizable !== false"
                  class="absolute right-0 top-0 bottom-0 w-2.5 cursor-col-resize flex items-center justify-center hover:bg-indigo-500/30 active:bg-indigo-600 transition-colors group-hover:opacity-100 opacity-0 z-10"
                  title="Drag to resize column (double click to reset)"
                  @mousedown.stop.prevent="startResize($event, col.key)"
                  @dblclick.stop.prevent="resetColumnWidth(col.key)"
                >
                  <div class="w-0.5 h-4 bg-slate-300 dark:bg-slate-600 group-hover:bg-indigo-500"></div>
                </div>
              </th>
            </tr>
          </thead>

          <!-- Table Body -->
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
            <!-- Loading Row -->
            <tr v-if="isLoading" class="animate-pulse">
              <td :colspan="visibleColumns.length" class="py-12 text-center text-slate-400">
                <slot name="loading">
                  <div class="flex items-center justify-center gap-2">
                    <div class="w-4 h-4 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
                    <span>Loading records...</span>
                  </div>
                </slot>
              </td>
            </tr>

            <!-- Empty Row -->
            <tr v-else-if="items.length === 0">
              <td :colspan="visibleColumns.length" class="py-12 text-center text-slate-400">
                <slot name="empty">
                  <span>{{ emptyMessage }}</span>
                </slot>
              </td>
            </tr>

            <!-- Data Rows -->
            <tr
              v-else
              v-for="(item, index) in items"
              :key="item.id || index"
              class="hover:bg-slate-50/70 dark:hover:bg-slate-800/40 transition-colors"
            >
              <td
                v-for="col in visibleColumns"
                :key="col.key"
                class="truncate"
                :class="[
                  densityClasses.td,
                  col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left',
                  col.class || '',
                ]"
              >
                <slot :name="`cell-${col.key}`" :row="item" :index="index">
                  <span class="truncate">{{ item[col.key] }}</span>
                </slot>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Optional Footer / Summary / Pagination slot -->
      <div v-if="$slots.footer" class="border-t border-slate-100 dark:border-slate-800">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>
