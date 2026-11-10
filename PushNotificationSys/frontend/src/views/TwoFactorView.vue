<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useThemeStore } from '@/stores/themeStore'
import { useToastStore } from '@/stores/toastStore'
import BaseButton from '@/components/common/BaseButton.vue'
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Clock,
  Moon,
  ShieldCheck,
  Sun,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const toastStore = useToastStore()

const digits = ref<string[]>(['', '', '', '', '', ''])
const inputRefs = ref<(HTMLInputElement | null)[]>([])
const isShaking = ref(false)

const mfaTicket = computed(() => {
  return (
    (route.query.ticket as string) ||
    authStore.pendingTwoFactor?.appToken ||
    ''
  )
})

const isComplete = computed(() => {
  return digits.value.every((d) => d.length === 1 && /^\d$/.test(d))
})

const formattedCode = computed(() => digits.value.join(''))

onMounted(() => {
  nextTick(() => {
    inputRefs.value[0]?.focus()
  })
})

function handleInput(index: number, event: Event) {
  const target = event.target as HTMLInputElement
  const val = target.value

  if (val.length > 1) {
    handlePasteDirect(val, index)
    return
  }

  if (/^\d$/.test(val)) {
    digits.value[index] = val
    if (index < 5) {
      inputRefs.value[index + 1]?.focus()
    } else if (isComplete.value) {
      handleVerify()
    }
  } else {
    digits.value[index] = ''
  }
}

function handleKeydown(index: number, event: KeyboardEvent) {
  if (event.key === 'Backspace') {
    if (!digits.value[index] && index > 0) {
      digits.value[index - 1] = ''
      inputRefs.value[index - 1]?.focus()
    } else {
      digits.value[index] = ''
    }
  } else if (event.key === 'ArrowLeft' && index > 0) {
    inputRefs.value[index - 1]?.focus()
  } else if (event.key === 'ArrowRight' && index < 5) {
    inputRefs.value[index + 1]?.focus()
  }
}

function handlePaste(event: ClipboardEvent) {
  event.preventDefault()
  const pasted = event.clipboardData?.getData('text') || ''
  handlePasteDirect(pasted, 0)
}

function handlePasteDirect(text: string, startIndex: number) {
  const clean = text.replace(/\D/g, '')
  if (!clean) return

  for (let i = 0; i < clean.length && startIndex + i < 6; i++) {
    digits.value[startIndex + i] = clean[i]
  }

  const nextFocusIndex = Math.min(5, startIndex + clean.length)
  inputRefs.value[nextFocusIndex]?.focus()

  if (isComplete.value) {
    handleVerify()
  }
}

async function handleVerify() {
  if (!isComplete.value) return

  try {
    await authStore.verifyMfa({
      mfaTicket: mfaTicket.value,
      totpCode: formattedCode.value,
    })

    toastStore.success('MFA Verified', 'Signed in successfully!')
    router.push('/dashboard')
  } catch (err: any) {
    isShaking.value = true
    setTimeout(() => {
      isShaking.value = false
    }, 500)
    toastStore.error('Verification Failed', authStore.error || 'Invalid TOTP code')
    digits.value = ['', '', '', '', '', '']
    inputRefs.value[0]?.focus()
  }
}

function returnToLogin() {
  authStore.clearPendingTwoFactor()
  router.push('/login')
}
</script>

<template>
  <div
    class="min-h-screen w-full flex flex-col justify-between items-center p-4 sm:p-6 lg:p-8 relative overflow-hidden bg-slate-50 dark:bg-[#0b0f19] text-slate-900 dark:text-slate-100 transition-colors duration-300"
  >
    <!-- Top-Right Theme Toggle -->
    <header class="w-full max-w-7xl flex items-center justify-end absolute z-30 top-6 right-6">
      <button
        @click="themeStore.toggleTheme"
        class="p-2.5 rounded-xl border border-slate-200/80 dark:border-slate-800 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md text-slate-600 dark:text-slate-300 hover:text-indigo-600 dark:hover:text-white shadow-2xs transition-all cursor-pointer"
        :title="themeStore.isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'"
      >
        <Sun v-if="themeStore.isDark" class="w-4 h-4 text-amber-400" />
        <Moon v-else class="w-4 h-4 text-indigo-600" />
      </button>
    </header>

    <!-- Main Card -->
    <main class="w-full max-w-[480px] my-auto py-8 relative z-20 flex flex-col justify-center">
      <div
        class="w-full bg-white/95 dark:bg-slate-900/95 backdrop-blur-2xl rounded-3xl p-8 sm:p-10 border border-slate-200/80 dark:border-slate-800/80 shadow-2xl shadow-indigo-950/10 dark:shadow-slate-950/80 transition-all text-center"
        :class="{ 'animate-shake': isShaking }"
      >
        <div
          class="inline-flex items-center justify-center w-16 h-16 rounded-2xl sm:rounded-3xl bg-gradient-to-tr from-indigo-600 via-indigo-600 to-violet-600 text-white shadow-xl shadow-indigo-600/25 ring-4 ring-indigo-500/10 mb-4"
        >
          <ShieldCheck class="w-8 h-8 text-white stroke-[2.2]" />
        </div>

        <h1 class="text-2xl font-extrabold tracking-tight text-slate-900 dark:text-white">
          Two-Factor Authentication
        </h1>
        <p class="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1.5 font-medium">
          Enter the 6-digit TOTP verification code from your authenticator app
        </p>

        <!-- 6 Box Code Entry -->
        <div class="flex items-center justify-center gap-2 sm:gap-3 my-8" @paste="handlePaste">
          <input
            v-for="(digit, idx) in digits"
            :key="idx"
            :ref="(el) => (inputRefs[idx] = el as HTMLInputElement)"
            type="text"
            inputmode="numeric"
            pattern="[0-9]*"
            maxlength="1"
            :value="digit"
            @input="handleInput(idx, $event)"
            @keydown="handleKeydown(idx, $event)"
            class="w-11 h-13 sm:w-13 sm:h-15 text-center text-xl sm:text-2xl font-mono font-bold rounded-2xl border bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all shadow-inner"
            :class="[
              digit
                ? 'border-indigo-500 ring-1 ring-indigo-500/30'
                : 'border-slate-200 dark:border-slate-800',
            ]"
          />
        </div>

        <!-- Error Message -->
        <div
          v-if="authStore.error"
          class="p-3 mb-6 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center justify-center gap-2"
        >
          <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
          <span>{{ authStore.error }}</span>
        </div>

        <!-- Submit Button -->
        <BaseButton
          variant="primary"
          size="lg"
          class="w-full h-12 text-sm font-semibold mb-3"
          :loading="authStore.isLoading"
          :disabled="!isComplete"
          @click="handleVerify"
        >
          <span>Verify & Access Dashboard</span>
          <ArrowRight class="w-4 h-4 ml-1.5" />
        </BaseButton>

        <button
          type="button"
          @click="returnToLogin"
          class="text-xs font-semibold text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 transition-colors inline-flex items-center gap-1.5 cursor-pointer pt-2"
        >
          <ArrowLeft class="w-3.5 h-3.5" />
          <span>Back to Sign In</span>
        </button>
      </div>
    </main>

    <footer class="w-full text-center py-4 text-xs text-slate-400">
      &copy; 2026 Push Notification Platform
    </footer>
  </div>
</template>
