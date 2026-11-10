<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { membersApi } from '@/api/members'
import { useToastStore } from '@/stores/toastStore'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import { AlertCircle, ArrowRight, CheckCircle2, Lock, User, UserCheck } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const toastStore = useToastStore()

const token = ref((route.query.token as string) || '')
const fullName = ref('')
const password = ref('')
const confirmPassword = ref('')
const isLoading = ref(false)
const errorMessage = ref('')

async function handleAccept() {
  if (!token.value) {
    errorMessage.value = 'Missing or invalid invitation token'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMessage.value = 'Passwords do not match'
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    await membersApi.acceptInvite({
      token: token.value,
      fullName: fullName.value.trim(),
      password: password.value,
    })
    toastStore.success('Invitation Accepted', 'Account setup complete! Redirecting to sign in...')
    setTimeout(() => {
      router.push('/login')
    }, 1500)
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || err.message || 'Failed to accept invitation. Token may be expired.'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 dark:bg-slate-950 flex items-center justify-center p-4 text-center">
    <div class="w-full max-w-md bg-white dark:bg-slate-900/90 rounded-3xl p-8 border border-slate-200 dark:border-slate-800 shadow-2xl space-y-5">
      <div class="w-14 h-14 rounded-2xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30 flex items-center justify-center mx-auto">
        <UserCheck class="w-7 h-7" />
      </div>

      <div>
        <h1 class="text-xl font-bold tracking-tight text-slate-900 dark:text-white">
          Accept Workspace Invitation
        </h1>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
          Set up your full name and password to join your team
        </p>
      </div>

      <div v-if="errorMessage" class="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2 text-left">
        <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
        <span>{{ errorMessage }}</span>
      </div>

      <form @submit.prevent="handleAccept" class="space-y-4 text-left">
        <BaseInput
          v-model="fullName"
          label="Your Full Name"
          placeholder="e.g. Sarah Connor"
          required
          :icon="User"
        />

        <BaseInput
          v-model="password"
          type="password"
          label="Choose Password"
          placeholder="••••••••••••"
          required
          :icon="Lock"
        />

        <BaseInput
          v-model="confirmPassword"
          type="password"
          label="Confirm Password"
          placeholder="••••••••••••"
          required
          :icon="Lock"
        />

        <div class="pt-2">
          <BaseButton
            type="submit"
            variant="primary"
            size="md"
            class="w-full"
            :loading="isLoading"
          >
            <span>Activate Account & Sign In</span>
            <ArrowRight class="w-4 h-4 ml-1" />
          </BaseButton>
        </div>
      </form>
    </div>
  </div>
</template>
