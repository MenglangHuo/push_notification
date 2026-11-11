<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import BaseButton from '@/components/common/BaseButton.vue'
import type { MfaSetupResponse } from '@/api/types'
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Check,
  Copy,
  Download,
  Key,
  QrCode,
  ShieldAlert,
  ShieldCheck,
  X,
} from 'lucide-vue-next'

const props = defineProps<{
  isOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'success'): void
}>()

const authStore = useAuthStore()
const toastStore = useToastStore()

const currentStep = ref<1 | 2 | 3>(1)
const isLoadingSetup = ref(false)
const setupData = ref<MfaSetupResponse | null>(null)
const copiedSecret = ref(false)
const copiedBackup = ref(false)
const backupCodesConfirmed = ref(false)

// 6-digit PIN input state for Step 3
const digits = ref<string[]>(['', '', '', '', '', ''])
const inputRefs = ref<(HTMLInputElement | null)[]>([])
const isShaking = ref(false)
const verifyError = ref<string | null>(null)
const isSubmitting = ref(false)

const isComplete = computed(() => {
  return digits.value.every((d) => d.length === 1 && /^\d$/.test(d))
})

const formattedCode = computed(() => digits.value.join(''))

watch(
  () => props.isOpen,
  async (open) => {
    if (open) {
      resetState()
      await loadSetupData()
    }
  },
)

function resetState() {
  currentStep.value = 1
  setupData.value = null
  copiedSecret.value = false
  copiedBackup.value = false
  backupCodesConfirmed.value = false
  digits.value = ['', '', '', '', '', '']
  verifyError.value = null
  isSubmitting.value = false
}

async function loadSetupData() {
  isLoadingSetup.value = true
  try {
    const data = await authStore.setupMfa()
    setupData.value = data
  } catch (err: any) {
    const msg = err.response?.data?.message || err.message || 'Failed to start MFA setup'
    toastStore.error('MFA Setup Failed', msg)
    emit('close')
  } finally {
    isLoadingSetup.value = false
  }
}

async function copySecret() {
  if (!setupData.value?.secretKey) return
  try {
    await navigator.clipboard.writeText(setupData.value.secretKey)
    copiedSecret.value = true
    setTimeout(() => {
      copiedSecret.value = false
    }, 2000)
    toastStore.success('Secret Copied', 'Paste into your authenticator app')
  } catch {
    toastStore.error('Clipboard Error', 'Please select and copy the key manually')
  }
}

async function copyAllBackupCodes() {
  if (!setupData.value?.backupCodes) return
  const text = setupData.value.backupCodes.join('\n')
  try {
    await navigator.clipboard.writeText(text)
    copiedBackup.value = true
    setTimeout(() => {
      copiedBackup.value = false
    }, 2000)
    toastStore.success('Codes Copied', 'Save these in a secure password manager')
  } catch {
    toastStore.error('Clipboard Error', 'Please copy codes manually')
  }
}

function downloadBackupCodes() {
  if (!setupData.value?.backupCodes) return
  const content = [
    'Push Notification Platform - Emergency Recovery Backup Codes',
    '==============================================================',
    'Account: ' + (authStore.user?.email || 'Admin'),
    'Date: ' + new Date().toISOString(),
    '',
    'Each code can only be used once for emergency login if you lose your phone.',
    '',
    ...setupData.value.backupCodes.map((c, i) => `${i + 1}. ${c}`),
  ].join('\n')

  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `mfa-backup-codes-${authStore.user?.email || 'admin'}.txt`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  toastStore.success('Downloaded', 'Backup codes file saved')
}

function goToStep(step: 1 | 2 | 3) {
  currentStep.value = step
  if (step === 3) {
    nextTick(() => {
      inputRefs.value[0]?.focus()
    })
  }
}

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
      handleConfirm()
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
    handleConfirm()
  }
}

async function handleConfirm() {
  if (!isComplete.value) return
  isSubmitting.value = true
  verifyError.value = null

  try {
    await authStore.confirmMfa(formattedCode.value)
    toastStore.success('MFA Activated', 'Two-Factor Authentication is now actively protecting your account!')
    emit('success')
    emit('close')
  } catch (err: any) {
    isShaking.value = true
    setTimeout(() => {
      isShaking.value = false
    }, 500)
    verifyError.value = err.response?.data?.message || err.message || 'Invalid verification code'
    digits.value = ['', '', '', '', '', '']
    inputRefs.value[0]?.focus()
  } finally {
    isSubmitting.value = false
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
      class="w-full max-w-lg bg-white dark:bg-slate-900 rounded-3xl border border-slate-200/80 dark:border-slate-800 shadow-2xl shadow-indigo-950/20 overflow-hidden flex flex-col max-h-[90vh]"
    >
      <!-- Modal Header -->
      <div class="px-6 pt-6 pb-4 flex items-center justify-between border-b border-slate-100 dark:border-slate-800">
        <div class="flex items-center gap-3">
          <div
            class="w-10 h-10 rounded-2xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/60 dark:border-indigo-800/40"
          >
            <ShieldCheck class="w-5 h-5" />
          </div>
          <div>
            <h3 class="text-base font-bold text-slate-900 dark:text-white">
              Two-Factor Authentication Setup
            </h3>
            <p class="text-xs text-slate-500 dark:text-slate-400">
              Protect your administrative access with RFC 6238 TOTP
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

      <!-- Step Progress Bar -->
      <div class="px-6 py-3 bg-slate-50/70 dark:bg-slate-950/40 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between text-xs font-semibold">
        <div
          class="flex items-center gap-1.5 transition-colors"
          :class="currentStep >= 1 ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400'"
        >
          <span
            class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] border"
            :class="
              currentStep >= 1
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'border-slate-300 dark:border-slate-700'
            "
          >1</span>
          <span>Scan QR</span>
        </div>

        <div class="h-px w-8 bg-slate-200 dark:bg-slate-800"></div>

        <div
          class="flex items-center gap-1.5 transition-colors"
          :class="currentStep >= 2 ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400'"
        >
          <span
            class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] border"
            :class="
              currentStep >= 2
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'border-slate-300 dark:border-slate-700'
            "
          >2</span>
          <span>Backup Codes</span>
        </div>

        <div class="h-px w-8 bg-slate-200 dark:bg-slate-800"></div>

        <div
          class="flex items-center gap-1.5 transition-colors"
          :class="currentStep >= 3 ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400'"
        >
          <span
            class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] border"
            :class="
              currentStep >= 3
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'border-slate-300 dark:border-slate-700'
            "
          >3</span>
          <span>Verify</span>
        </div>
      </div>

      <!-- Modal Body -->
      <div class="p-6 overflow-y-auto space-y-4">
        <!-- Loading Spinner -->
        <div v-if="isLoadingSetup" class="py-16 text-center space-y-3">
          <div class="w-8 h-8 mx-auto border-3 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
          <p class="text-xs text-slate-500 font-medium">Generating cryptographic secret & QR code...</p>
        </div>

        <!-- STEP 1: Scan QR Code -->
        <div v-else-if="currentStep === 1 && setupData" class="space-y-4">
          <div class="text-center space-y-1">
            <h4 class="text-sm font-bold text-slate-900 dark:text-white">
              Scan this QR code with your Authenticator app
            </h4>
            <p class="text-xs text-slate-500 dark:text-slate-400">
              Open Google Authenticator, Microsoft Authenticator, or 1Password and tap <strong>Scan QR</strong>
            </p>
          </div>

          <!-- QR Code Container -->
          <div class="flex justify-center py-2">
            <div class="p-3 bg-white rounded-2xl border-2 border-indigo-500/20 shadow-xl shadow-indigo-950/10 inline-block">
              <img
                :src="setupData.qrCodeDataUrl"
                alt="2FA QR Code"
                class="w-48 h-48 block rounded-lg select-none"
              />
            </div>
          </div>

          <!-- Manual Entry Key Box -->
          <div class="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/80 dark:border-slate-800 space-y-1.5">
            <div class="flex items-center justify-between text-[11px] text-slate-400 font-semibold uppercase tracking-wider">
              <span class="flex items-center gap-1.5">
                <Key class="w-3.5 h-3.5 text-indigo-500" />
                Or enter this Secret Key manually:
              </span>
              <button
                type="button"
                @click="copySecret"
                class="flex items-center gap-1 text-indigo-600 dark:text-indigo-400 hover:underline cursor-pointer"
              >
                <Check v-if="copiedSecret" class="w-3.5 h-3.5 text-emerald-500" />
                <Copy v-else class="w-3.5 h-3.5" />
                <span>{{ copiedSecret ? 'Copied' : 'Copy' }}</span>
              </button>
            </div>
            <p class="text-xs font-mono font-bold text-slate-800 dark:text-slate-200 tracking-wider break-all select-all">
              {{ setupData.secretKey }}
            </p>
          </div>
        </div>

        <!-- STEP 2: Emergency Recovery Codes -->
        <div v-else-if="currentStep === 2 && setupData" class="space-y-4">
          <div class="p-3.5 rounded-2xl bg-amber-50/80 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-900/50 flex items-start gap-3">
            <ShieldAlert class="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
            <div class="text-xs text-amber-900 dark:text-amber-200 space-y-1">
              <p class="font-bold">Save your emergency backup recovery codes</p>
              <p class="text-amber-700 dark:text-amber-300">
                If you ever lose access to your phone or authenticator app, these one-time codes are the <strong>only way</strong> to regain access to your account.
              </p>
            </div>
          </div>

          <!-- 10 Codes Grid -->
          <div class="grid grid-cols-2 gap-2 p-3 bg-slate-50 dark:bg-slate-950/60 rounded-2xl border border-slate-200/80 dark:border-slate-800 font-mono text-xs font-bold text-slate-800 dark:text-slate-200 text-center">
            <div
              v-for="(code, idx) in setupData.backupCodes"
              :key="idx"
              class="p-2 rounded-xl bg-white dark:bg-slate-900 border border-slate-200/60 dark:border-slate-800 tracking-wider"
            >
              {{ code }}
            </div>
          </div>

          <!-- Action Buttons (Copy / Download) -->
          <div class="flex items-center gap-2">
            <BaseButton
              variant="outline"
              size="sm"
              class="flex-1 text-xs"
              @click="copyAllBackupCodes"
            >
              <Check v-if="copiedBackup" class="w-3.5 h-3.5 mr-1.5 text-emerald-500" />
              <Copy v-else class="w-3.5 h-3.5 mr-1.5" />
              <span>{{ copiedBackup ? 'All Codes Copied' : 'Copy All Codes' }}</span>
            </BaseButton>

            <BaseButton
              variant="outline"
              size="sm"
              class="flex-1 text-xs"
              @click="downloadBackupCodes"
            >
              <Download class="w-3.5 h-3.5 mr-1.5" />
              <span>Download (.txt)</span>
            </BaseButton>
          </div>

          <!-- Acknowledgment Checkbox -->
          <label class="flex items-start gap-2.5 pt-2 cursor-pointer select-none">
            <input
              type="checkbox"
              v-model="backupCodesConfirmed"
              class="mt-0.5 w-4 h-4 rounded text-indigo-600 focus:ring-indigo-500 cursor-pointer"
            />
            <span class="text-xs font-medium text-slate-700 dark:text-slate-300 leading-tight">
              I have saved or downloaded these recovery codes in a safe place.
            </span>
          </label>
        </div>

        <!-- STEP 3: Confirm Activation -->
        <div
          v-else-if="currentStep === 3"
          class="space-y-4 text-center"
          :class="{ 'animate-shake': isShaking }"
        >
          <div class="space-y-1">
            <h4 class="text-sm font-bold text-slate-900 dark:text-white">
              Verify Authenticator Code
            </h4>
            <p class="text-xs text-slate-500 dark:text-slate-400">
              Enter the 6-digit code currently showing in your authenticator app to activate 2FA
            </p>
          </div>

          <!-- 6-digit PIN Boxes -->
          <div class="flex items-center justify-center gap-2 py-4" @paste="handlePaste">
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
              class="w-11 h-13 text-center text-xl font-mono font-bold rounded-2xl border bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all shadow-inner"
              :class="[
                digit
                  ? 'border-indigo-500 ring-1 ring-indigo-500/30'
                  : 'border-slate-200 dark:border-slate-800',
              ]"
            />
          </div>

          <!-- Error Alert -->
          <div
            v-if="verifyError"
            class="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center justify-center gap-2"
          >
            <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
            <span>{{ verifyError }}</span>
          </div>
        </div>
      </div>

      <!-- Modal Footer -->
      <div class="px-6 py-4 bg-slate-50/70 dark:bg-slate-950/40 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between">
        <button
          v-if="currentStep > 1"
          type="button"
          @click="goToStep((currentStep - 1) as 1 | 2)"
          class="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors cursor-pointer"
        >
          <ArrowLeft class="w-3.5 h-3.5" />
          <span>Back</span>
        </button>
        <span v-else></span>

        <div class="flex items-center gap-2">
          <BaseButton
            variant="ghost"
            size="sm"
            class="text-xs"
            @click="emit('close')"
          >
            Cancel
          </BaseButton>

          <!-- Next Button for Step 1 -->
          <BaseButton
            v-if="currentStep === 1"
            variant="primary"
            size="sm"
            class="text-xs font-semibold"
            :disabled="isLoadingSetup"
            @click="goToStep(2)"
          >
            <span>Next: Backup Codes</span>
            <ArrowRight class="w-3.5 h-3.5 ml-1" />
          </BaseButton>

          <!-- Next Button for Step 2 -->
          <BaseButton
            v-else-if="currentStep === 2"
            variant="primary"
            size="sm"
            class="text-xs font-semibold"
            :disabled="!backupCodesConfirmed"
            @click="goToStep(3)"
          >
            <span>Next: Verify Code</span>
            <ArrowRight class="w-3.5 h-3.5 ml-1" />
          </BaseButton>

          <!-- Confirm Button for Step 3 -->
          <BaseButton
            v-else-if="currentStep === 3"
            variant="primary"
            size="sm"
            class="text-xs font-semibold"
            :disabled="!isComplete"
            :loading="isSubmitting"
            @click="handleConfirm"
          >
            <span>Activate 2FA</span>
            <Check class="w-3.5 h-3.5 ml-1" />
          </BaseButton>
        </div>
      </div>
    </div>
  </div>
</template>
