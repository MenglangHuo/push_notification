<script setup lang="ts">
import { ref, watch } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import { AlertCircle, Lock, ShieldAlert, X } from 'lucide-vue-next'

const props = defineProps<{
  isOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'success'): void
}>()

const authStore = useAuthStore()
const toastStore = useToastStore()

const password = ref('')
const isLoading = ref(false)
const errorMessage = ref<string | null>(null)

watch(
  () => props.isOpen,
  (open) => {
    if (open) {
      password.value = ''
      errorMessage.value = null
      isLoading.value = false
    }
  },
)

async function handleDisable() {
  if (!password.value) {
    errorMessage.value = 'Password is required'
    return
  }

  isLoading.value = true
  errorMessage.value = null

  try {
    await authStore.disableMfa(password.value)
    toastStore.success('MFA Disabled', 'Two-Factor Authentication has been turned off for your account.')
    emit('success')
    emit('close')
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || err.message || 'Failed to disable MFA. Please check your password.'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div
    v-if="isOpen"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-md transition-opacity duration-300"
    @click.self="emit('close')"
  >
    <div
      class="w-full max-w-md bg-white dark:bg-slate-900 rounded-3xl border border-slate-200/80 dark:border-slate-800 shadow-2xl shadow-rose-950/20 overflow-hidden"
    >
      <!-- Modal Header -->
      <div class="px-6 pt-6 pb-4 flex items-center justify-between border-b border-slate-100 dark:border-slate-800">
        <div class="flex items-center gap-3">
          <div
            class="w-10 h-10 rounded-2xl bg-rose-50 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400 flex items-center justify-center border border-rose-200/60 dark:border-rose-800/40"
          >
            <ShieldAlert class="w-5 h-5" />
          </div>
          <div>
            <h3 class="text-base font-bold text-slate-900 dark:text-white">
              Disable Two-Factor Auth
            </h3>
            <p class="text-xs text-slate-500 dark:text-slate-400">
              Confirm account password to proceed
            </p>
          </div>
        </div>

        <button
          @click="emit('close')"
          class="p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-xl transition-colors cursor-pointer"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Modal Body -->
      <div class="p-6 space-y-4">
        <p class="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
          Disabling Two-Factor Authentication reduces your account security. You will only need your email and password to log in.
        </p>

        <div class="space-y-1.5">
          <label class="text-xs font-semibold text-slate-700 dark:text-slate-300">
            Account Password
          </label>
          <BaseInput
            v-model="password"
            type="password"
            placeholder="Enter your current password"
            @keyup.enter="handleDisable"
          >
            <template #prefix>
              <Lock class="w-4 h-4 text-slate-400" />
            </template>
          </BaseInput>
        </div>

        <!-- Error Alert -->
        <div
          v-if="errorMessage"
          class="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2"
        >
          <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
          <span>{{ errorMessage }}</span>
        </div>
      </div>

      <!-- Modal Footer -->
      <div class="px-6 py-4 bg-slate-50/70 dark:bg-slate-950/40 border-t border-slate-100 dark:border-slate-800 flex items-center justify-end gap-2">
        <BaseButton
          variant="ghost"
          size="sm"
          class="text-xs"
          @click="emit('close')"
        >
          Cancel
        </BaseButton>

        <BaseButton
          variant="danger"
          size="sm"
          class="text-xs font-semibold"
          :loading="isLoading"
          :disabled="!password"
          @click="handleDisable"
        >
          Disable 2FA
        </BaseButton>
      </div>
    </div>
  </div>
</template>
