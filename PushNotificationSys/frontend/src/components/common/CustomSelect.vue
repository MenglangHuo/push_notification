<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ChevronDown, Check } from 'lucide-vue-next'

export interface SelectOption {
  label: string
  value: string | number
  icon?: any
  dotColor?: string
  badgeClass?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: string | number
    options: SelectOption[]
    placeholder?: string
    size?: 'xs' | 'sm' | 'md'
    direction?: 'up' | 'down'
    disabled?: boolean
    block?: boolean
    buttonClass?: string
    menuClass?: string
  }>(),
  {
    placeholder: 'Select...',
    size: 'sm',
    direction: 'down',
    disabled: false,
    block: false,
    buttonClass: '',
    menuClass: '',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
  (e: 'change', value: string | number): void
}>()

const isOpen = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const selectedOption = computed(() => {
  return props.options.find((opt) => opt.value === props.modelValue)
})

function toggleDropdown() {
  if (props.disabled) return
  isOpen.value = !isOpen.value
}

function selectOption(option: SelectOption) {
  emit('update:modelValue', option.value)
  emit('change', option.value)
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
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div
    ref="containerRef"
    class="relative text-left select-none"
    :class="block ? 'w-full block' : 'inline-block'"
  >
    <!-- Trigger Button -->
    <button
      type="button"
      @click="toggleDropdown"
      :disabled="disabled"
      class="flex items-center justify-between gap-1.5 border font-medium transition-all shadow-2xs focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
      :class="[
        block ? 'w-full' : '',
        size === 'xs'
          ? 'h-8 px-2.5 text-xs rounded-lg'
          : size === 'md'
            ? 'h-10 px-4 text-sm rounded-xl'
            : 'h-9 px-3.5 text-xs sm:text-[13px] rounded-xl',
        isOpen
          ? 'ring-2 ring-blue-500/20 border-blue-500 text-blue-600 dark:text-blue-400'
          : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-200 hover:border-slate-300 dark:hover:border-slate-700',
        buttonClass,
      ]"
    >
      <div class="flex items-center gap-2 truncate">
        <!-- Dot if provided -->
        <span
          v-if="selectedOption?.dotColor"
          class="w-2 h-2 rounded-full shrink-0"
          :class="selectedOption.dotColor"
        ></span>

        <!-- Icon if provided -->
        <component
          v-if="selectedOption?.icon"
          :is="selectedOption.icon"
          class="w-3.5 h-3.5 shrink-0 text-slate-500 dark:text-slate-400"
        />

        <span class="truncate font-medium">
          {{ selectedOption ? selectedOption.label : placeholder }}
        </span>
      </div>

      <ChevronDown
        class="w-3.5 h-3.5 text-slate-400 transition-transform duration-200 shrink-0 ml-1"
        :class="{ 'rotate-180 text-blue-500': isOpen }"
      />
    </button>

    <!-- Dropdown Menu -->
    <Transition
      enter-active-class="transition duration-100 ease-out"
      :enter-from-class="
        direction === 'up'
          ? 'transform scale-95 opacity-0 translate-y-1'
          : 'transform scale-95 opacity-0 -translate-y-1'
      "
      enter-to-class="transform scale-100 opacity-100 translate-y-0"
      leave-active-class="transition duration-75 ease-in"
      leave-from-class="transform scale-100 opacity-100 translate-y-0"
      :leave-to-class="
        direction === 'up'
          ? 'transform scale-95 opacity-0 translate-y-1'
          : 'transform scale-95 opacity-0 -translate-y-1'
      "
    >
      <div
        v-if="isOpen"
        class="absolute z-50 max-h-60 overflow-y-auto rounded-xl bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-slate-800 shadow-xl ring-1 ring-black/5 dark:ring-white/10 p-1 focus:outline-none"
        :class="[
          direction === 'up' ? 'bottom-full mb-1.5' : 'mt-1.5',
          menuClass || 'min-w-[120px] w-full',
        ]"
      >
        <div
          v-for="opt in options"
          :key="opt.value"
          @click="selectOption(opt)"
          class="flex items-center justify-between px-2.5 py-1.5 rounded-lg text-xs cursor-pointer transition-colors font-medium select-none"
          :class="[
            opt.value === modelValue
              ? 'bg-blue-50 dark:bg-blue-950/50 text-blue-600 dark:text-blue-400 font-semibold'
              : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100/80 dark:hover:bg-slate-800/70',
          ]"
        >
          <div class="flex items-center gap-2 truncate">
            <span
              v-if="opt.dotColor"
              class="w-2 h-2 rounded-full shrink-0"
              :class="opt.dotColor"
            ></span>
            <component v-if="opt.icon" :is="opt.icon" class="w-3.5 h-3.5 shrink-0 text-slate-400" />
            <span class="truncate">{{ opt.label }}</span>
          </div>

          <Check
            v-if="opt.value === modelValue"
            class="w-3.5 h-3.5 text-blue-600 dark:text-blue-400 shrink-0 ml-2"
          />
        </div>
      </div>
    </Transition>
  </div>
</template>
