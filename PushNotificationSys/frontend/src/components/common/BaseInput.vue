<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: string | number
    type?: string
    placeholder?: string
    label?: string
    labelClass?: string
    error?: string
    icon?: any
    disabled?: boolean
    required?: boolean
    size?: 'sm' | 'md' | 'lg'
    id?: string
    name?: string
    autocomplete?: string
    enterkeyhint?: 'enter' | 'done' | 'go' | 'next' | 'previous' | 'search' | 'send'
    list?: string
  }>(),
  {
    type: 'text',
    placeholder: '',
    disabled: false,
    required: false,
    size: 'sm',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div class="w-full text-left">
    <!-- Label -->
    <div
      v-if="label || $slots.label || $slots['label-right']"
      class="flex items-center justify-between mb-1.5"
    >
      <label
        v-if="label || $slots.label"
        :for="id"
        class="block text-xs font-semibold text-slate-700 dark:text-slate-300"
        :class="labelClass"
      >
        <slot name="label">{{ label }}</slot>
        <span v-if="required" class="text-rose-500 font-bold ml-0.5">*</span>
      </label>
      <slot name="label-right" />
    </div>

    <!-- Input Wrapper -->
    <div class="relative w-full group">
      <!-- Leading Icon -->
      <component
        v-if="icon"
        :is="icon"
        class="text-slate-400 group-focus-within:text-indigo-500 absolute top-1/2 -translate-y-1/2 shrink-0 pointer-events-none transition-colors"
        :class="size === 'lg' ? 'w-5 h-5 left-4' : 'w-4 h-4 left-3.5'"
      />

      <input
        :id="id"
        :name="name"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :required="required"
        :autocomplete="autocomplete"
        :enterkeyhint="enterkeyhint"
        :list="list"
        @input="onInput"
        class="w-full bg-slate-50/70 dark:bg-slate-950/70 hover:bg-white dark:hover:bg-slate-900 focus:bg-white dark:focus:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 rounded-xl border transition-all focus:outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 shadow-2xs disabled:opacity-50 disabled:cursor-not-allowed"
        :class="[
          icon
            ? size === 'lg'
              ? 'pl-12'
              : size === 'md'
                ? 'pl-10'
                : 'pl-9.5'
            : size === 'lg'
              ? 'pl-4'
              : 'pl-3.5',
          size === 'lg'
            ? $slots.suffix
              ? 'h-12 sm:h-13 text-sm sm:text-base pr-12'
              : 'h-12 sm:h-13 text-sm sm:text-base pr-4'
            : size === 'md'
              ? $slots.suffix
                ? 'h-10 text-xs sm:text-sm pr-10'
                : 'h-10 text-xs sm:text-sm pr-4'
              : $slots.suffix
                ? 'h-9 text-xs pr-8'
                : 'h-9 text-xs pr-3.5',
          error
            ? 'border-rose-400 focus:border-rose-500 focus:ring-rose-500/15'
            : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700',
        ]"
      />

      <!-- Suffix Slot -->
      <div
        v-if="$slots.suffix"
        class="absolute top-1/2 -translate-y-1/2 flex items-center"
        :class="size === 'lg' ? 'right-3.5' : 'right-3'"
      >
        <slot name="suffix" />
      </div>
    </div>

    <!-- Error Message -->
    <p v-if="error" class="text-[11px] text-rose-500 mt-1 font-medium">
      {{ error }}
    </p>
  </div>
</template>
