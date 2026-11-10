<script setup lang="ts">
import {
  GripVertical,
  Maximize2,
  Minimize2,
  Minus,
  RotateCcw,
  X,
} from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, ref, useSlots, watch } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'

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
    icon?: any
    iconClass?: string
    size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
    initialWidth?: number
    initialHeight?: number
    minWidth?: number
    minHeight?: number
    showHeader?: boolean
    showFooter?: boolean
    showClose?: boolean
    bodyClass?: string
    hasBackdrop?: boolean
    closeOnEscape?: boolean
    closeOnBackdrop?: boolean
    allowDrag?: boolean
    allowResize?: boolean
    allowMaximize?: boolean
    allowMinimize?: boolean
    allowReset?: boolean
    allowDock?: boolean
    isDockedDrawer?: boolean
    drawerPosition?: 'right' | 'left'
    confirmText?: string
    cancelText?: string
    confirmLoading?: boolean
    confirmDisabled?: boolean
    confirmVariant?: 'primary' | 'danger'
  }>(),
  {
    title: '',
    subtitle: '',
    icon: undefined,
    iconClass: '',
    size: 'lg',
    initialWidth: undefined,
    initialHeight: undefined,
    minWidth: 380,
    minHeight: 200,
    showHeader: true,
    showFooter: false,
    showClose: true,
    bodyClass: '',
    hasBackdrop: true,
    closeOnEscape: true,
    closeOnBackdrop: true,
    allowDrag: true, // Movable by default per user request
    allowResize: true, // Resizable by default per user request
    allowMaximize: false,
    allowMinimize: false,
    allowReset: true,
    allowDock: false,
    isDockedDrawer: false,
    drawerPosition: 'right',
    confirmText: '',
    cancelText: '',
    confirmLoading: false,
    confirmDisabled: false,
    confirmVariant: 'primary',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'close'): void
  (e: 'confirm'): void
  (e: 'minimize', value: boolean): void
  (e: 'toggleDock'): void
}>()

const slots = useSlots()
const modalRef = ref<HTMLElement | null>(null)

// State
const isMaximized = ref(false)
const isMinimized = ref(false)
const headerVisible = ref(props.showHeader)
const footerVisible = ref(props.showFooter)
const isDragging = ref(false)
const isResizing = ref(false)

// Tracking whether user has customized position or size
const hasUserMoved = ref(false)
const hasUserResized = ref(false)

// Preset fallback widths when initialWidth is not explicitly supplied
const fallbackWidth = computed(() => {
  if (props.initialWidth) return props.initialWidth
  switch (props.size) {
    case 'sm':
      return 440
    case 'md':
      return 520
    case 'xl':
      return 840
    case '2xl':
      return 1040
    case 'full':
      return 1280
    case 'lg':
    default:
      return 640
  }
})

const currentWidth = ref(fallbackWidth.value)
const currentHeight = ref(props.initialHeight || 480)
const position = ref<ModalPosition>({ x: 0, y: 0 })

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

watch(
  () => props.modelValue,
  (isOpen) => {
    if (isOpen) {
      hasUserMoved.value = false
      hasUserResized.value = false
      isMinimized.value = false
      isMaximized.value = false
    }
  },
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

// Automatically detect whether footer should render
const hasFooter = computed(() => {
  return (
    props.showFooter ||
    footerVisible.value ||
    Boolean(slots.footer) ||
    Boolean(props.confirmText) ||
    Boolean(props.cancelText)
  )
})

// Dragging logic
function startDrag(e: PointerEvent) {
  if (!props.allowDrag || isMaximized.value || props.isDockedDrawer || isMinimized.value) return

  const target = e.target as HTMLElement
  if (target.closest('button, input, a, select, textarea, [data-no-drag]')) return
  if (!modalRef.value) return

  // Lock in current rendered geometry before beginning coordinate translation
  if (!hasUserMoved.value) {
    const rect = modalRef.value.getBoundingClientRect()
    position.value = {
      x: Math.round(rect.left),
      y: Math.round(rect.top),
    }
    if (!hasUserResized.value) {
      currentWidth.value = Math.round(rect.width)
      currentHeight.value = Math.round(rect.height)
    }
    hasUserMoved.value = true
  }

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

  const deltaX = e.clientX - dragStartX
  const deltaY = e.clientY - dragStartY

  const vw = window.innerWidth
  const vh = window.innerHeight

  const newX = Math.max(8, Math.min(vw - 120, initialPosX + deltaX))
  const newY = Math.max(8, Math.min(vh - 60, initialPosY + deltaY))

  position.value = { x: newX, y: newY }
}

function stopDrag() {
  if (isDragging.value) {
    isDragging.value = false
    window.removeEventListener('pointermove', onDragMove)
    window.removeEventListener('pointerup', stopDrag)
  }
}

// Resizing logic
function startResize(e: PointerEvent, direction: 'e' | 's' | 'se') {
  if (!props.allowResize || isMaximized.value || props.isDockedDrawer || isMinimized.value) return
  if (!modalRef.value) return

  const rect = modalRef.value.getBoundingClientRect()
  if (!hasUserMoved.value) {
    position.value = {
      x: Math.round(rect.left),
      y: Math.round(rect.top),
    }
    hasUserMoved.value = true
  }

  currentWidth.value = Math.round(rect.width)
  currentHeight.value = Math.round(rect.height)
  hasUserResized.value = true

  isResizing.value = true
  resizeStartX = e.clientX
  resizeStartY = e.clientY
  initialResizeW = currentWidth.value
  initialResizeH = currentHeight.value

  const onResizeMove = (ev: PointerEvent) => {
    if (!isResizing.value) return
    const deltaX = ev.clientX - resizeStartX
    const deltaY = ev.clientY - resizeStartY

    const vw = window.innerWidth
    const vh = window.innerHeight

    if (direction === 'e' || direction === 'se') {
      const nextW = Math.max(props.minWidth, initialResizeW + deltaX)
      currentWidth.value = Math.min(vw - position.value.x - 16, nextW)
    }

    if (direction === 's' || direction === 'se') {
      const nextH = Math.max(props.minHeight, initialResizeH + deltaY)
      currentHeight.value = Math.min(vh - position.value.y - 16, nextH)
    }
  }

  const stopResize = () => {
    isResizing.value = false
    window.removeEventListener('pointermove', onResizeMove)
    window.removeEventListener('pointerup', stopResize)
  }

  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', stopResize)
  e.preventDefault()
}

function toggleMaximize() {
  isMaximized.value = !isMaximized.value
}

function toggleMinimize() {
  isMinimized.value = !isMinimized.value
  emit('minimize', isMinimized.value)
}

function resetSizeAndPosition() {
  isMaximized.value = false
  hasUserMoved.value = false
  hasUserResized.value = false
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
})

// Dynamic styling based on content, movement, and resizing
const modalContainerStyle = computed(() => {
  if (props.isDockedDrawer) return {}

  if (isMaximized.value) {
    return {
      top: '0px',
      left: '0px',
      width: '100vw',
      height: '100vh',
      maxWidth: 'none',
      maxHeight: 'none',
      borderRadius: '0px',
    }
  }

  const style: Record<string, string> = {}

  // Positioning: User moved or dynamically centered
  if (hasUserMoved.value) {
    style.top = `${position.value.y}px`
    style.left = `${position.value.x}px`
    style.transform = 'none'
  } else {
    style.top = '50%'
    style.left = '50%'
    style.transform = 'translate(-50%, -50%)'
  }

  // Sizing: User resized or dynamically sized by body content
  if (hasUserResized.value) {
    style.width = `${currentWidth.value}px`
    style.height = `${currentHeight.value}px`
  } else {
    const w = props.initialWidth ? `${props.initialWidth}px` : `${fallbackWidth.value}px`
    style.width = `min(${w}, calc(100vw - 32px))`
    style.height = 'auto'
    style.maxHeight = 'min(90vh, calc(100dvh - 32px))'
  }

  return style
})
</script>

<template>
  <Teleport to="body">
    <!-- Backdrop with blur outside modal (hidden when minimized) -->
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
        class="fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm transition-opacity"
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

    <!-- Main Modal Container -->
    <Transition
      appear
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="modelValue && !isMinimized"
        ref="modalRef"
        class="fixed z-50 flex flex-col bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-slate-800 rounded-3xl shadow-2xl ring-1 ring-black/5 dark:ring-white/10 select-text overflow-hidden transition-shadow"
        :class="[
          isDockedDrawer
            ? drawerPosition === 'left'
              ? 'inset-y-0 left-0 w-full max-w-2xl sm:max-w-3xl border-r rounded-none'
              : 'inset-y-0 right-0 w-full max-w-2xl sm:max-w-3xl border-l rounded-none'
            : isMaximized
              ? 'inset-0 rounded-none w-screen h-screen max-w-none max-h-none border-0'
              : '',
          isDragging ? 'ring-2 ring-indigo-500/40 shadow-indigo-500/20' : '',
        ]"
        :style="modalContainerStyle"
      >
        <!-- Header (Draggable Handle) -->
        <div
          v-if="headerVisible"
          @pointerdown="startDrag"
          class="border-b border-slate-100 dark:border-slate-800/80 bg-slate-50/70 dark:bg-slate-950/50 backdrop-blur-md px-5 sm:px-6 py-4 flex items-center justify-between gap-3 shrink-0 select-none relative z-30"
          :class="{ 'cursor-grab active:cursor-grabbing': allowDrag && !isMaximized && !isDockedDrawer }"
        >
          <!-- Left: Grip + Icon + Title -->
          <div class="flex items-center gap-3 min-w-0">
            <div
              v-if="allowDrag && !isMaximized && !isDockedDrawer"
              class="text-slate-300 dark:text-slate-600 hover:text-slate-500 dark:hover:text-slate-400 cursor-grab active:cursor-grabbing shrink-0 -ml-1"
              title="Drag to move"
            >
              <GripVertical class="w-4 h-4" />
            </div>

            <slot name="header-icon">
              <div
                v-if="icon"
                class="w-10 h-10 rounded-2xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-200/80 dark:border-indigo-500/20 flex items-center justify-center shrink-0 shadow-xs"
                :class="iconClass"
              >
                <component :is="icon" class="w-5 h-5 stroke-[2.2]" />
              </div>
            </slot>

            <slot name="header">
              <div class="min-w-0">
                <h3 class="text-base font-bold text-slate-900 dark:text-white tracking-tight truncate">
                  {{ title }}
                </h3>
                <p v-if="subtitle" class="text-xs text-slate-500 dark:text-slate-400 mt-0.5 truncate leading-relaxed">
                  {{ subtitle }}
                </p>
              </div>
            </slot>
          </div>

          <!-- Right: Controls -->
          <div class="flex items-center gap-1.5 shrink-0" data-no-drag>
            <slot name="header-actions" />

            <!-- Reset Size / Position (When customized) -->
            <button
              v-if="(hasUserMoved || hasUserResized) && !isDockedDrawer && !isMaximized"
              type="button"
              @click="resetSizeAndPosition"
              class="p-2 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
              title="Reset position & size"
            >
              <RotateCcw class="w-4 h-4" />
            </button>

            <!-- Maximize Button -->
            <button
              v-if="allowMaximize && !isDockedDrawer"
              type="button"
              @click="toggleMaximize"
              class="p-2 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
              :title="isMaximized ? 'Restore Window Size' : 'Maximize Fullscreen'"
            >
              <Minimize2 v-if="isMaximized" class="w-4 h-4" />
              <Maximize2 v-else class="w-4 h-4" />
            </button>

            <!-- Close Button -->
            <button
              v-if="showClose"
              type="button"
              @click="close"
              class="p-2 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
              title="Close (Esc)"
            >
              <X class="w-4 h-4" />
            </button>
          </div>
        </div>

        <!-- Scrollable Modal Body -->
        <div
          class="flex-1 overflow-y-auto min-h-0 relative text-left custom-modal-scroll"
          :class="bodyClass"
        >
          <slot />
        </div>

        <!-- Sticky Footer -->
        <div
          v-if="hasFooter"
          class="border-t border-slate-100 dark:border-slate-800/80 bg-slate-50/70 dark:bg-slate-950/50 backdrop-blur-md px-5 sm:px-6 py-4 shrink-0 flex items-center justify-between gap-3 select-none"
        >
          <slot name="footer-left">
            <span class="text-xs text-slate-400 hidden sm:inline">
              Press <kbd class="px-1.5 py-0.5 rounded bg-slate-200 dark:bg-slate-800 font-mono text-[10px]">Esc</kbd> to close
            </span>
          </slot>

          <div class="flex items-center gap-2.5 w-full sm:w-auto justify-end">
            <slot name="footer">
              <BaseButton v-if="cancelText" variant="ghost" @click="close">
                {{ cancelText }}
              </BaseButton>
              <BaseButton
                v-if="confirmText"
                :variant="confirmVariant"
                :loading="confirmLoading"
                :disabled="confirmDisabled"
                @click="emit('confirm')"
              >
                {{ confirmText }}
              </BaseButton>
            </slot>
          </div>
        </div>

        <!-- Resizable Handles (Right edge, Bottom edge, Bottom-Right corner) -->
        <template v-if="allowResize && !isMaximized && !isDockedDrawer && !isMinimized">
          <!-- East handle -->
          <div
            @pointerdown="startResize($event, 'e')"
            class="absolute top-0 right-0 w-3 h-full cursor-ew-resize hover:bg-indigo-500/10 active:bg-indigo-500/20 transition-colors z-40 select-none"
            title="Drag to resize width"
          />

          <!-- South handle -->
          <div
            @pointerdown="startResize($event, 's')"
            class="absolute bottom-0 left-0 h-3 w-full cursor-ns-resize hover:bg-indigo-500/10 active:bg-indigo-500/20 transition-colors z-40 select-none"
            title="Drag to resize height"
          />

          <!-- South-East corner handle -->
          <div
            @pointerdown="startResize($event, 'se')"
            class="absolute bottom-0 right-0 w-6 h-6 cursor-nwse-resize group flex items-end justify-end p-1.5 z-50 select-none"
            title="Drag to resize"
          >
            <div
              class="w-2.5 h-2.5 border-r-2 border-b-2 border-slate-300 dark:border-slate-600 group-hover:border-indigo-500 transition-colors rounded-br-xs"
            />
          </div>
        </template>
      </div>
    </Transition>
  </Teleport>
</template>
