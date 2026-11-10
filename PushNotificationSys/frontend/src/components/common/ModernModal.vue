<script setup lang="ts">
import {
  Columns,
  Eye,
  Maximize2,
  Minimize2,
  Minus,
  Move,
  RotateCcw,
  X,
} from 'lucide-vue-next'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

export interface ModalPosition {
  x: number
  y: number
}

export interface ModalSize {
  width: number
  height: number
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    subtitle?: string
    initialWidth?: number
    initialHeight?: number
    minWidth?: number
    minHeight?: number
    showHeader?: boolean
    showFooter?: boolean
    hasBackdrop?: boolean
    closeOnEscape?: boolean
    closeOnBackdrop?: boolean
    allowDrag?: boolean
    allowResize?: boolean
    allowDock?: boolean
    isDockedDrawer?: boolean
    drawerPosition?: 'right' | 'left'
  }>(),
  {
    title: '',
    subtitle: '',
    initialWidth: 980,
    initialHeight: 720,
    minWidth: 540,
    minHeight: 420,
    showHeader: true,
    showFooter: false,
    hasBackdrop: true,
    closeOnEscape: true,
    closeOnBackdrop: true,
    allowDrag: true,
    allowResize: true,
    allowDock: true,
    isDockedDrawer: false,
    drawerPosition: 'right',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'close'): void
  (e: 'minimize', value: boolean): void
  (e: 'toggleDock'): void
}>()

// State
// const modalRef = ref<HTMLElement | null>(null);
const isMaximized = ref(false)
const isMinimized = ref(false)
const headerVisible = ref(props.showHeader)
const footerVisible = ref(props.showFooter)
const isDragging = ref(false)
const isResizing = ref(false)
const resizeDirection = ref<'e' | 's' | 'se' | null>(null)

// Dimensions & Coordinates
const currentWidth = ref(props.initialWidth)
const currentHeight = ref(props.initialHeight)
const position = ref<ModalPosition>({ x: 0, y: 0 })
const savedPosition = ref<ModalPosition>({ x: 0, y: 0 })
const savedSize = ref<ModalSize>({ width: props.initialWidth, height: props.initialHeight })

// Dragging internals
let dragStartX = 0
let dragStartY = 0
let initialPosX = 0
let initialPosY = 0

// Resizing internals
let resizeStartX = 0
let resizeStartY = 0
let initialResizeW = 0
let initialResizeH = 0

function centerModal() {
  if (typeof window === 'undefined') return
  const vw = window.innerWidth
  const vh = window.innerHeight
  const targetW = Math.min(props.initialWidth, vw - 40)
  const targetH = Math.min(props.initialHeight, vh - 40)

  currentWidth.value = targetW
  currentHeight.value = targetH
  position.value = {
    x: Math.max(20, Math.round((vw - targetW) / 2)),
    y: Math.max(20, Math.round((vh - targetH) / 2)),
  }
}

watch(
  () => props.modelValue,
  (isOpen) => {
    if (isOpen) {
      if (position.value.x === 0 && position.value.y === 0) {
        nextTick(() => centerModal())
      }
      isMinimized.value = false
    }
  },
  { immediate: true },
)

watch(
  () => props.showHeader,
  (val) => {
    headerVisible.value = val
  },
)

watch(
  () => props.showFooter,
  (val) => {
    footerVisible.value = val
  },
)

// Dragging logic
function startDrag(e: PointerEvent) {
  if (!props.allowDrag || isMaximized.value || props.isDockedDrawer || isMinimized.value) return

  const target = e.target as HTMLElement
  // Ignore clicks on buttons, inputs, links, dropdowns
  if (target.closest('button, input, a, select, textarea, [data-no-drag]')) return

  isDragging.value = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  initialPosX = position.value.x
  initialPosY = position.value.y

  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', stopDrag)
  e.preventDefault()
}

function onDragMove(e: PointerEvent) {
  if (!isDragging.value) return
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY

  const maxPosX = Math.max(0, window.innerWidth - currentWidth.value)
  const maxPosY = Math.max(0, window.innerHeight - 80)

  position.value = {
    x: Math.min(Math.max(10, initialPosX + dx), maxPosX),
    y: Math.min(Math.max(10, initialPosY + dy), maxPosY),
  }
}

function stopDrag() {
  isDragging.value = false
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', stopDrag)
}

// Resizing logic
function startResize(e: PointerEvent, direction: 'e' | 's' | 'se') {
  if (!props.allowResize || isMaximized.value || props.isDockedDrawer || isMinimized.value) return

  isResizing.value = true
  resizeDirection.value = direction
  resizeStartX = e.clientX
  resizeStartY = e.clientY
  initialResizeW = currentWidth.value
  initialResizeH = currentHeight.value

  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', stopResize)
  e.preventDefault()
  e.stopPropagation()
}

function onResizeMove(e: PointerEvent) {
  if (!isResizing.value) return
  const dx = e.clientX - resizeStartX
  const dy = e.clientY - resizeStartY

  const maxW = window.innerWidth - position.value.x - 20
  const maxH = window.innerHeight - position.value.y - 20

  if (resizeDirection.value === 'e' || resizeDirection.value === 'se') {
    const nextW = initialResizeW + dx
    currentWidth.value = Math.max(props.minWidth, Math.min(nextW, maxW))
  }

  if (resizeDirection.value === 's' || resizeDirection.value === 'se') {
    const nextH = initialResizeH + dy
    currentHeight.value = Math.max(props.minHeight, Math.min(nextH, maxH))
  }
}

function stopResize() {
  isResizing.value = false
  resizeDirection.value = null
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', stopResize)
}

// Window actions
function toggleMaximize() {
  if (isMaximized.value) {
    // Restore
    isMaximized.value = false
    position.value = { ...savedPosition.value }
    currentWidth.value = savedSize.value.width
    currentHeight.value = savedSize.value.height
  } else {
    // Maximize
    savedPosition.value = { ...position.value }
    savedSize.value = { width: currentWidth.value, height: currentHeight.value }
    isMaximized.value = true
  }
}

function toggleMinimize() {
  isMinimized.value = !isMinimized.value
  emit('minimize', isMinimized.value)
}

function resetSizeAndPosition() {
  isMaximized.value = false
  centerModal()
}

function close() {
  emit('update:modelValue', false)
  emit('close')
}

function handleBackdropClick() {
  if (props.closeOnBackdrop) {
    close()
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.modelValue && props.closeOnEscape && !isMinimized.value) {
    close()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  stopDrag()
  stopResize()
})

const modalContainerStyle = computed(() => {
  if (props.isDockedDrawer) return {}

  if (isMaximized.value) {
    return {
      top: '0px',
      left: '0px',
      width: '100vw',
      height: '100vh',
      borderRadius: '0px',
    }
  }

  return {
    top: `${position.value.y}px`,
    left: `${position.value.x}px`,
    width: `${currentWidth.value}px`,
    height: `${currentHeight.value}px`,
  }
})
</script>

<template>
  <Teleport to="body">
    <!-- Backdrop with blur outside modal and drawer (hidden when minimized) -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="modelValue && hasBackdrop && !isMinimized"
        class="fixed inset-0 z-40 bg-slate-950/45 backdrop-blur-sm transition-opacity"
        @click="handleBackdropClick"
      />
    </Transition>

    <!-- Minimized Floating Taskbar Pill -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-y-4"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-4"
    >
      <div
        v-if="modelValue && isMinimized"
        class="fixed bottom-4 right-6 z-50 flex items-center gap-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-2.5 shadow-2xl ring-1 ring-black/5 dark:ring-white/10"
      >
        <slot name="minimized" :restore="toggleMinimize" :close="close">
          <div class="flex items-center gap-2.5 pl-2 cursor-pointer" @click="toggleMinimize">
            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span>
            <div
              class="text-xs font-semibold text-slate-800 dark:text-slate-100 max-w-[200px] truncate"
            >
              {{ title || 'Modal Window' }}
            </div>
          </div>
          <div class="flex items-center gap-1 border-l border-slate-200 dark:border-slate-800 pl-2">
            <button
              type="button"
              @click="toggleMinimize"
              class="p-1.5 rounded-lg text-slate-500 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
              title="Restore Window"
            >
              <Maximize2 class="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              @click="close"
              class="p-1.5 rounded-lg text-slate-500 hover:text-rose-600 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
              title="Close"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>
        </slot>
      </div>
    </Transition>

    <!-- Main Modal / Window Container -->
    <div
      v-if="modelValue && !isMinimized"
      ref="modalRef"
      class="fixed z-50 flex flex-col bg-white dark:bg-slate-900 shadow-2xl border border-slate-200 dark:border-slate-800 select-text overflow-hidden transition-all duration-75"
      :class="[
        isDockedDrawer
          ? drawerPosition === 'left'
            ? 'inset-y-0 left-0 w-full max-w-2xl sm:max-w-3xl border-r'
            : 'inset-y-0 right-0 w-full max-w-2xl sm:max-w-3xl border-l'
          : isMaximized
            ? 'rounded-none'
            : 'rounded-2xl sm:rounded-3xl',
        isDragging ? 'cursor-move select-none ring-2 ring-indigo-500/40 shadow-indigo-500/10' : '',
      ]"
      :style="modalContainerStyle"
    >
      <!-- Draggable Header -->
      <div
        v-if="headerVisible"
        @pointerdown="startDrag"
        class="border-b border-slate-200 dark:border-slate-800 bg-slate-50/90 dark:bg-slate-950/80 backdrop-blur-md px-4 sm:px-6 py-3 flex items-center justify-between gap-3 shrink-0 select-none relative z-30"
        :class="{ 'cursor-move': allowDrag && !isMaximized && !isDockedDrawer }"
      >
        <!-- Header Left / Title Slot -->
        <div class="flex items-center gap-3 min-w-0">
          <slot name="header">
            <div class="flex items-center gap-2 truncate">
              <span
                v-if="allowDrag && !isMaximized && !isDockedDrawer"
                class="text-slate-400 hover:text-slate-600 cursor-move"
              >
                <Move class="w-3.5 h-3.5" />
              </span>
              <div>
                <h3 class="text-sm font-bold text-slate-900 dark:text-white truncate">
                  {{ title }}
                </h3>
                <p v-if="subtitle" class="text-[11px] text-slate-500 dark:text-slate-400 truncate">
                  {{ subtitle }}
                </p>
              </div>
            </div>
          </slot>
        </div>

        <!-- Header Right / Window Controls -->
        <div class="flex items-center gap-1 shrink-0" data-no-drag>
          <slot name="header-actions" />

          <!-- Toggle Header visibility button -->

          <!-- Toggle Footer visibility button -->
          <button
            v-if="showFooter"
            type="button"
            @click="footerVisible = !footerVisible"
            class="hidden sm:inline-flex p-1.5 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            :title="footerVisible ? 'Hide Footer' : 'Show Footer'"
          >
            <Columns class="w-3.5 h-3.5" />
          </button>

          <!-- Reset Size / Position (When customized) -->
          <button
            v-if="!isDockedDrawer && !isMaximized"
            type="button"
            @click="resetSizeAndPosition"
            class="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            title="Reset position & size"
          >
            <RotateCcw class="w-3.5 h-3.5" />
          </button>

          <!-- Switch to Docked Drawer Mode -->
          <!-- <button
            v-if="allowDock"
            type="button"
            @click="emit('toggleDock')"
            class="p-1.5 rounded-lg transition-colors cursor-pointer"
            :class="
              isDockedDrawer
                ? 'text-indigo-600 bg-indigo-50 dark:bg-indigo-950/60 dark:text-indigo-400'
                : 'text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800'
            "
            :title="isDockedDrawer ? 'Undock to Floating Modal' : 'Dock to Side Drawer'"
          >
            <PanelRight class="w-3.5 h-3.5" />
          </button> -->

          <!-- Minimize button -->
          <button
            type="button"
            @click="toggleMinimize"
            class="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            title="Minimize to bottom taskbar"
          >
            <Minus class="w-3.5 h-3.5" />
          </button>

          <!-- Maximize / Restore button -->
          <button
            v-if="!isDockedDrawer"
            type="button"
            @click="toggleMaximize"
            class="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            :title="isMaximized ? 'Restore Window Size' : 'Maximize Fullscreen'"
          >
            <Minimize2 v-if="isMaximized" class="w-3.5 h-3.5" />
            <Maximize2 v-else class="w-3.5 h-3.5" />
          </button>

          <!-- Close button -->
          <button
            type="button"
            @click="close"
            class="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-200/60 dark:hover:bg-slate-800 transition-colors cursor-pointer ml-0.5"
            title="Close (Esc)"
          >
            <X class="w-4 h-4" />
          </button>
        </div>
      </div>

      <!-- Quick Floating Reveal Header Icon if Header was hidden -->
      <button
        v-else
        type="button"
        @click="headerVisible = true"
        class="absolute top-2 right-2 z-10 p-1.5 rounded-full bg-slate-900/80 text-white shadow-lg hover:bg-slate-950 transition-colors cursor-pointer"
        title="Restore Header Bar"
      >
        <Eye class="w-3.5 h-3.5" />
      </button>

      <!-- Scrollable Modal Body -->
      <div class="flex-1 overflow-y-auto min-h-0 relative">
        <slot />
      </div>

      <!-- Optional Footer -->
      <div
        v-if="footerVisible"
        class="border-t border-slate-200 dark:border-slate-800 bg-slate-50/90 dark:bg-slate-950/80 px-4 sm:px-6 py-3 shrink-0 flex items-center justify-between gap-3"
      >
        <slot name="footer" />
      </div>

      <!-- Resizable Handles (Right edge, Bottom edge, Bottom-Right corner) -->
      <template v-if="allowResize && !isMaximized && !isDockedDrawer && !isMinimized">
        <!-- East handle (right) -->
        <div
          @pointerdown="startResize($event, 'e')"
          class="absolute top-0 right-0 w-2 h-full cursor-ew-resize hover:bg-indigo-500/30 transition-colors"
          title="Drag to resize width"
        />
        <!-- South handle (bottom) -->
        <div
          @pointerdown="startResize($event, 's')"
          class="absolute bottom-0 left-0 h-2 w-full cursor-ns-resize hover:bg-indigo-500/30 transition-colors"
          title="Drag to resize height"
        />
        <!-- South-East corner handle -->
        <div
          @pointerdown="startResize($event, 'se')"
          class="absolute bottom-0 right-0 w-4 h-4 cursor-nwse-resize group flex items-end justify-end p-0.5"
          title="Drag to resize"
        >
          <div
            class="w-2 h-2 border-r-2 border-b-2 border-slate-400 dark:border-slate-500 group-hover:border-indigo-500 transition-colors"
          />
        </div>
      </template>
    </div>
  </Teleport>
</template>
