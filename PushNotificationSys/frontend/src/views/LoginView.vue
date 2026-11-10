<script setup lang="ts">
import BaseInput from '@/components/common/BaseInput.vue'
import EnvironmentBadge from '@/components/common/EnvironmentBadge.vue'
import { isProduction } from '@/config/environment'
import { useAuthStore } from '@/stores/authStore'
import { useThemeStore } from '@/stores/themeStore'
import { useToastStore } from '@/stores/toastStore'
import {
  AlertCircle,
  ArrowRight,
  BellRing,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  Mail,
  Moon,
  Sparkles,
  Sun,
  User,
} from 'lucide-vue-next'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const toastStore = useToastStore()
const themeStore = useThemeStore()

const email = ref('')
const password = ref('')
const showPassword = ref(false)

function fillDemo(role: 'admin' | 'dev' | 'readonly') {
  if (role === 'admin') {
    email.value = 'admin@pushnotify.local'
    password.value = 'Password123!'
  } else if (role === 'dev') {
    email.value = 'dev@pushnotify.local'
    password.value = 'Password123!'
  } else {
    email.value = 'viewer@pushnotify.local'
    password.value = 'Password123!'
  }
}

async function handleLogin() {
  if (!email.value || !password.value) return

  const res = await authStore.login({
    email: email.value,
    password: password.value,
  })

  if (res && res.mfaRequired) {
    toastStore.info(
      '2FA Verification Required',
      'Please enter your 6-digit TOTP code to complete sign-in.',
    )
    router.push({ path: '/mfa', query: { ticket: res.mfaTicket } })
  } else if (authStore.isAuthenticated) {
    toastStore.success(
      'Welcome to Push Platform!',
      `Signed in as ${authStore.user?.fullName || authStore.user?.email || 'Admin'}`,
    )
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } else {
    toastStore.error('Sign In Failed', authStore.error || 'Please check your credentials.')
  }
}
</script>

<template>
  <div
    class="min-h-screen w-full flex flex-col justify-between items-center p-4 sm:p-6 lg:p-8 relative overflow-hidden bg-slate-50 dark:bg-[#0b0f19] text-slate-900 dark:text-slate-100 transition-colors duration-300 selection:bg-indigo-500 selection:text-white"
  >
    <!-- Ambient Background Glows -->
    <div
      class="absolute -top-24 -left-24 w-96 sm:w-[520px] h-96 sm:h-[520px] bg-indigo-500/12 dark:bg-indigo-600/15 rounded-full blur-[110px] pointer-events-none"
    ></div>
    <div
      class="absolute -bottom-24 -right-24 w-96 sm:w-[520px] h-96 sm:h-[520px] bg-violet-500/12 dark:bg-violet-600/15 rounded-full blur-[110px] pointer-events-none"
    ></div>

    <!-- Subtle Dot Texture -->
    <div
      class="absolute inset-0 opacity-[0.03] dark:opacity-[0.05] pointer-events-none"
      style="
        background-image: radial-gradient(rgba(99, 102, 241, 0.7) 1px, transparent 1px);
        background-size: 24px 24px;
      "
    ></div>

    <!-- Top Floating Bar: Environment & Theme -->
    <header class="w-full max-w-7xl flex items-center justify-end absolute z-30 top-6 right-6 sm:right-8">
      <div class="flex items-center gap-2">
        <EnvironmentBadge v-if="!isProduction" />
        <button
          @click="themeStore.toggleTheme"
          class="p-2.5 rounded-xl border border-slate-200/80 dark:border-slate-800 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md text-slate-600 dark:text-slate-300 hover:text-indigo-600 dark:hover:text-white shadow-2xs transition-all cursor-pointer hover:scale-105 active:scale-95"
          :title="themeStore.isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'"
        >
          <Sun v-if="themeStore.isDark" class="w-4 h-4 text-amber-400" />
          <Moon v-else class="w-4 h-4 text-indigo-600" />
        </button>
      </div>
    </header>

    <!-- Main Authentication Card -->
    <main
      class="w-full max-w-[540px] my-auto py-8 relative z-20 flex flex-col justify-center"
    >
      <div
        class="w-full bg-white/95 dark:bg-slate-900/95 backdrop-blur-2xl rounded-3xl p-8 sm:p-10 border border-slate-200/80 dark:border-slate-800/80 shadow-2xl shadow-indigo-950/10 dark:shadow-slate-950/80 relative transition-all"
      >
        <!-- Header / Brand Icon -->
        <div class="text-center pt-2 pb-6">
          <div
            class="inline-flex items-center justify-center w-16 h-16 sm:w-20 sm:h-20 rounded-2xl sm:rounded-3xl bg-gradient-to-tr from-indigo-600 via-indigo-600 to-violet-600 text-white shadow-xl shadow-indigo-600/25 ring-4 ring-indigo-500/10 dark:ring-indigo-400/10 mb-4 transition-transform hover:scale-105 duration-200"
          >
            <BellRing class="w-8 h-8 sm:w-10 sm:h-10 text-white stroke-[2.2]" />
          </div>
          <h1
            class="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white"
          >
            Push Platform
          </h1>
          <p class="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1.5 font-medium">
            Sign in to Multi-Tenant Push Notification Admin
          </p>
        </div>

        <!-- Login Form -->
        <form @submit.prevent="handleLogin" class="space-y-4 text-left">
          <BaseInput
            id="email"
            name="email"
            v-model="email"
            label="Email or Username"
            type="text"
            required
            placeholder="admin@pushnotify.local"
            size="lg"
            :icon="Mail"
          />

          <BaseInput
            id="password"
            name="password"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            label="Password"
            required
            placeholder="••••••••••••"
            size="lg"
            :icon="Lock"
          >
            <template #suffix>
              <button
                type="button"
                @click="showPassword = !showPassword"
                class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1.5 cursor-pointer transition-colors rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800"
                :title="showPassword ? 'Hide password' : 'Show password'"
              >
                <EyeOff v-if="showPassword" class="w-5 h-5" />
                <Eye v-else class="w-5 h-5" />
              </button>
            </template>
          </BaseInput>

          <!-- Error Alert -->
          <div
            v-if="authStore.error"
            class="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs sm:text-sm flex items-center gap-3 shadow-xs text-left animate-in fade-in"
          >
            <AlertCircle class="w-5 h-5 shrink-0 text-rose-500" />
            <span class="font-medium leading-relaxed">{{ authStore.error }}</span>
          </div>

          <!-- Quick Test Accounts Helper -->
          <div
            class="p-3.5 rounded-2xl bg-indigo-50/70 dark:bg-slate-800/60 border border-indigo-100 dark:border-slate-700/80 text-xs space-y-2 text-left"
          >
            <div class="flex items-center justify-between">
              <span class="font-semibold flex items-center gap-1.5 text-slate-800 dark:text-slate-200">
                <Sparkles class="w-3.5 h-3.5 text-indigo-500" />
                Quick Test Credentials
              </span>
              <span class="text-[10px] font-mono px-1.5 py-0.2 rounded bg-indigo-100 dark:bg-indigo-500/20 text-indigo-700 dark:text-indigo-300 font-bold">
                DEV
              </span>
            </div>
            <div class="grid grid-cols-3 gap-2 pt-1">
              <button
                type="button"
                @click="fillDemo('admin')"
                class="py-1.5 px-2 rounded-xl bg-white dark:bg-slate-900 hover:bg-indigo-50 dark:hover:bg-indigo-950/50 text-indigo-900 dark:text-indigo-200 text-xs font-semibold transition-all text-center cursor-pointer shadow-2xs border border-slate-200 dark:border-slate-700"
              >
                Super Admin
              </button>
              <button
                type="button"
                @click="fillDemo('dev')"
                class="py-1.5 px-2 rounded-xl bg-white dark:bg-slate-900 hover:bg-indigo-50 dark:hover:bg-indigo-950/50 text-indigo-900 dark:text-indigo-200 text-xs font-semibold transition-all text-center cursor-pointer shadow-2xs border border-slate-200 dark:border-slate-700"
              >
                Developer
              </button>
              <button
                type="button"
                @click="fillDemo('readonly')"
                class="py-1.5 px-2 rounded-xl bg-white dark:bg-slate-900 hover:bg-indigo-50 dark:hover:bg-indigo-950/50 text-indigo-900 dark:text-indigo-200 text-xs font-semibold transition-all text-center cursor-pointer shadow-2xs border border-slate-200 dark:border-slate-700"
              >
                Auditor
              </button>
            </div>
          </div>

          <!-- Sign-In Button -->
          <button
            type="submit"
            :disabled="authStore.isLoading"
            class="w-full h-12 sm:h-13 px-5 rounded-xl font-semibold text-sm sm:text-base text-white bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 dark:bg-indigo-600 dark:hover:bg-indigo-500 shadow-md shadow-indigo-600/25 hover:shadow-indigo-600/40 active:scale-[0.99] transition-all flex items-center justify-center gap-2.5 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed mt-4"
          >
            <Loader2 v-if="authStore.isLoading" class="w-5 h-5 animate-spin" />
            <span>{{ authStore.isLoading ? 'Authenticating...' : 'Sign In to Portal' }}</span>
            <ArrowRight v-if="!authStore.isLoading" class="w-5 h-5" />
          </button>
        </form>
      </div>
    </main>

    <!-- Bottom Footer -->
    <footer class="w-full text-center py-4 text-xs text-slate-400">
      &copy; 2026 Push Notification Platform. All rights reserved.
    </footer>
  </div>
</template>
