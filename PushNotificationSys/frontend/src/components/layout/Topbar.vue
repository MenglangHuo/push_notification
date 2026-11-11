<script setup lang="ts">
import CustomDropdown from '@/components/common/CustomDropdown.vue'
import EnvironmentBadge from '@/components/common/EnvironmentBadge.vue'
import OrgSwitcher from '@/components/common/OrgSwitcher.vue'
import { isProduction } from '@/config/environment'
import { useAuthStore } from '@/stores/authStore'
import { useThemeStore } from '@/stores/themeStore'
import {
  BellRing,
  Building2,
  Check,
  ChevronDown,
  Command,
  Copy,
  KeyRound,
  Layers,
  LayoutDashboard,
  LogOut,
  Mail,
  Menu,
  Moon,
  Radio,
  ScrollText,
  Search,
  Send,
  Settings,
  ShieldAlert,
  ShieldCheck,
  Smartphone,
  Sun,
  UserCheck,
  Users,
  Zap,
} from 'lucide-vue-next'
import MfaSetupModal from '@/components/auth/MfaSetupModal.vue'
import MfaDisableModal from '@/components/auth/MfaDisableModal.vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

defineProps<{
  isMobileOpen?: boolean
}>()

const emit = defineEmits<{
  (e: 'toggleMobile'): void
}>()

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const authStore = useAuthStore()

const searchInputRef = ref<HTMLInputElement | null>(null)
const globalSearch = ref<string>('')
const avatarImgError = ref<boolean>(false)
const copiedField = ref<string | null>(null)
const isMfaSetupOpen = ref<boolean>(false)
const isMfaDisableOpen = ref<boolean>(false)

const userDisplayName = computed(() => {
  const u = authStore.user
  if (u?.fullName) return u.fullName
  if (u?.email) return u.email.split('@')[0]
  return 'Push Admin'
})

const userRole = computed(() => {
  return authStore.roleLabel || 'Administrator'
})

const userInitials = computed(() => {
  const name = userDisplayName.value
  const parts = name.split(' ')
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase()
  }
  return name.slice(0, 2).toUpperCase()
})

const pageInfo = computed(() => {
  const path = route.path
  if (path === '/dashboard') {
    return { title: 'Dashboard', subtitle: 'Live Metrics & Performance', icon: LayoutDashboard }
  }
  if (path === '/apps') {
    return { title: 'Applications', subtitle: 'Multi-Tenant Applications', icon: Layers }
  }
  if (path.includes('/notifications/new')) {
    return { title: 'Compose Push', subtitle: 'Target & Dispatch Message', icon: Send }
  }
  if (path.includes('/notifications')) {
    return { title: 'Notifications', subtitle: 'History & Delivery Status', icon: BellRing }
  }
  if (path.includes('/devices')) {
    return { title: 'Device Registry', subtitle: 'Registered FCM, APNs & Web Tokens', icon: Smartphone }
  }
  if (path.includes('/segments')) {
    return { title: 'Audience Segments', subtitle: 'Dynamic Target Audiences', icon: Users }
  }
  if (path.includes('/logs')) {
    return { title: 'Delivery Logs', subtitle: 'Dispatch Audit Trail & Status', icon: ScrollText }
  }
  if (path.includes('/api-keys')) {
    return { title: 'API Keys', subtitle: 'Client & Server Credentials', icon: KeyRound }
  }
  if (path.includes('/realtime')) {
    return { title: 'Realtime Gateway', subtitle: 'Live STOMP WebSocket Monitor', icon: Zap }
  }
  if (path.includes('/web-push-test')) {
    return { title: 'Web Push Testbench', subtitle: 'Browser VAPID Push Tester', icon: Radio }
  }
  if (path === '/members') {
    return { title: 'Team Members', subtitle: 'Organization Members & Access', icon: UserCheck }
  }
  if (path === '/roles') {
    return { title: 'Roles & RBAC', subtitle: 'Role Permissions Matrix', icon: ShieldCheck }
  }
  if (path.includes('/settings')) {
    return { title: 'App Settings', subtitle: 'Push Credentials & Configuration', icon: Settings }
  }
  return { title: 'Push Platform', subtitle: 'Management Portal', icon: BellRing }
})

async function copyUserField(text?: string, fieldName = 'field') {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copiedField.value = fieldName
    setTimeout(() => {
      if (copiedField.value === fieldName) {
        copiedField.value = null
      }
    }, 2000)
  } catch (err) {
    console.warn('Clipboard write failed:', err)
  }
}

function handleLogout(closeDropdown?: () => void) {
  closeDropdown?.()
  authStore.logout()
  router.push('/login')
}

function handleMfaToggle(closeDropdown?: () => void) {
  closeDropdown?.()
  if (authStore.user?.mfaEnabled) {
    isMfaDisableOpen.value = true
  } else {
    isMfaSetupOpen.value = true
  }
}

function handleKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    searchInputRef.value?.focus()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <header
    class="h-16 px-3 md:px-6 bg-white/80 dark:bg-slate-950/80 backdrop-blur-md border-b border-slate-200 dark:border-slate-800/80 flex items-center justify-between sticky top-0 z-30 transition-colors"
  >
    <!-- Left Area: Mobile Toggle & Page Title -->
    <div class="flex items-center gap-3 flex-1 min-w-0">
      <button
        @click="emit('toggleMobile')"
        class="md:hidden p-2 rounded-xl text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-900 border border-slate-200 dark:border-slate-800 transition-colors shrink-0"
      >
        <Menu class="w-5 h-5" />
      </button>

      <!-- Contextual Page Title -->
      <div class="flex items-center gap-2.5 min-w-0">
        <div
          class="hidden sm:flex w-8 h-8 rounded-xl bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 items-center justify-center border border-indigo-200/80 dark:border-indigo-500/30 shrink-0"
        >
          <component :is="pageInfo.icon" class="w-4 h-4" />
        </div>
        <div class="truncate">
          <h1
            class="text-sm sm:text-base font-bold text-slate-900 dark:text-white tracking-tight truncate"
          >
            {{ pageInfo.title }}
          </h1>
          <p class="hidden md:block text-[11px] text-slate-500 dark:text-slate-400 truncate">
            {{ pageInfo.subtitle }}
          </p>
        </div>
      </div>

      <!-- Multi-Tenant Organization Switcher (Single instance responsive) -->
      <div class="flex items-center ml-2 sm:ml-4">
        <OrgSwitcher />
      </div>
    </div>

    <!-- Right Controls: Theme Toggle & Profile User -->
    <div class="flex items-center gap-2 sm:gap-3 shrink-0">

      <!-- Environment Status Badge (Hidden in production) -->
      <EnvironmentBadge v-if="!isProduction" />

      <!-- Light / Dark Mode Toggle -->
      <button
        @click="themeStore.toggleTheme"
        class="p-2 rounded-xl text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-900 border border-slate-200 dark:border-slate-800 transition-colors cursor-pointer"
        :title="themeStore.isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'"
      >
        <Sun v-if="themeStore.isDark" class="w-4 h-4 text-amber-400" />
        <Moon v-else class="w-4 h-4 text-indigo-600" />
      </button>

      <!-- Vertical Divider -->
      <div class="h-6 w-px bg-slate-200 dark:bg-slate-800"></div>

      <!-- User Profile Dropdown -->
      <CustomDropdown align="right" width="w-80 sm:w-88">
        <template #trigger="{ isOpen }">
          <button
            type="button"
            class="flex items-center gap-2 pl-1 pr-1.5 py-1 rounded-2xl hover:bg-slate-100 dark:hover:bg-slate-900 border border-transparent hover:border-slate-200 dark:border-slate-800 transition-all cursor-pointer select-none group text-left"
            :class="{
              'bg-slate-100 dark:bg-slate-900 border-slate-200 dark:border-slate-800 shadow-2xs':
                isOpen,
            }"
            title="View user details & profile menu"
          >
            <!-- User Avatar with Status Indicator -->
            <div class="relative shrink-0 flex items-center justify-center">
              <div
                class="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-sky-400 p-0.5 shadow-xs ring-1 ring-indigo-500/20 overflow-hidden flex items-center justify-center"
              >
                <span class="font-bold text-xs text-white">
                  {{ userInitials }}
                </span>
              </div>
              <span
                class="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full bg-emerald-500 ring-2 ring-white dark:ring-slate-950 shadow-xs"
                title="Active Online"
              ></span>
            </div>

            <!-- User Name & Role -->
            <div class="hidden sm:block text-left leading-tight">
              <p
                class="text-xs font-bold text-slate-900 dark:text-white tracking-tight truncate max-w-[120px]"
              >
                {{ userDisplayName }}
              </p>
              <span class="text-[10px] font-semibold text-indigo-600 dark:text-indigo-400">
                {{ userRole }}
              </span>
            </div>

            <ChevronDown
              class="w-3.5 h-3.5 text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-200 transition-transform duration-200 ml-0.5"
              :class="{ 'rotate-180': isOpen }"
            />
          </button>
        </template>

        <template #default="{ close }">
          <div class="p-1">
            <!-- Header User Card -->
            <div
              class="p-3.5 rounded-xl bg-gradient-to-br from-indigo-50/70 via-slate-50 to-sky-50/40 dark:from-slate-800/80 dark:via-slate-900/90 dark:to-indigo-950/40 border border-slate-200/80 dark:border-slate-800 mb-2"
            >
              <div class="flex items-start gap-3">
                <div
                  class="w-11 h-11 rounded-2xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-sky-400 p-0.5 shadow-md shadow-indigo-600/20 ring-1 ring-white/30 shrink-0 overflow-hidden flex items-center justify-center"
                >
                  <span class="font-bold text-sm text-white">
                    {{ userInitials }}
                  </span>
                </div>

                <div class="min-w-0 flex-1">
                  <h4 class="text-xs sm:text-sm font-bold text-slate-900 dark:text-white truncate">
                    {{ userDisplayName }}
                  </h4>

                  <p
                    v-if="authStore.user?.email"
                    class="text-[11px] font-mono text-slate-500 dark:text-slate-400 truncate"
                  >
                    {{ authStore.user.email }}
                  </p>

                  <div class="mt-1.5 flex items-center gap-1.5 flex-wrap">
                    <span
                      class="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wide uppercase bg-indigo-100 text-indigo-800 dark:bg-indigo-500/20 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30"
                    >
                      {{ userRole }}
                    </span>
                    <span
                      class="inline-flex items-center gap-1 text-[10px] font-medium text-emerald-600 dark:text-emerald-400"
                    >
                      <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                      Online
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Main Info Details Section -->
            <div class="px-2 py-1 space-y-2 text-xs">
              <!-- Email -->
              <div
                v-if="authStore.user?.email"
                class="flex items-center justify-between p-2 rounded-xl bg-slate-50/80 dark:bg-slate-950/50 border border-slate-200/70 dark:border-slate-800/80"
              >
                <div class="flex items-center gap-2 min-w-0">
                  <div
                    class="w-6 h-6 rounded-lg bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0 border border-indigo-200/60 dark:border-indigo-800/40"
                  >
                    <Mail class="w-3.5 h-3.5" />
                  </div>
                  <div class="min-w-0">
                    <div class="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                      Email
                    </div>
                    <div
                      class="text-[11px] font-medium text-slate-700 dark:text-slate-300 truncate max-w-[170px]"
                      :title="authStore.user.email"
                    >
                      {{ authStore.user.email }}
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  @click="copyUserField(authStore.user?.email, 'email')"
                  class="p-1 rounded-md text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-200/60 dark:hover:bg-slate-800 transition-colors cursor-pointer"
                  :title="copiedField === 'email' ? 'Copied!' : 'Copy email'"
                >
                  <Check v-if="copiedField === 'email'" class="w-3.5 h-3.5 text-emerald-500" />
                  <Copy v-else class="w-3.5 h-3.5" />
                </button>
              </div>

              <!-- Organization -->
              <div
                v-if="authStore.currentOrg?.name"
                class="flex items-start gap-2 p-2 rounded-xl bg-slate-50/80 dark:bg-slate-950/50 border border-slate-200/70 dark:border-slate-800/80"
              >
                <div
                  class="w-6 h-6 rounded-lg bg-purple-50 dark:bg-purple-950/60 text-purple-600 dark:text-purple-400 flex items-center justify-center shrink-0 border border-purple-200/60 dark:border-purple-800/40"
                >
                  <Building2 class="w-3.5 h-3.5" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                    Workspace
                  </div>
                  <div
                    class="text-[11px] font-medium text-slate-700 dark:text-slate-300 truncate"
                  >
                    {{ authStore.currentOrg.name }}
                  </div>
                </div>
              </div>

              <!-- Two-Factor Authentication Status & Action -->
              <div
                class="flex items-center justify-between p-2 rounded-xl bg-slate-50/80 dark:bg-slate-950/50 border border-slate-200/70 dark:border-slate-800/80"
              >
                <div class="flex items-center gap-2 min-w-0">
                  <div
                    class="w-6 h-6 rounded-lg flex items-center justify-center shrink-0 border"
                    :class="
                      authStore.user?.mfaEnabled
                        ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 border-emerald-200/60 dark:border-emerald-800/40'
                        : 'bg-amber-50 dark:bg-amber-950/60 text-amber-600 dark:text-amber-400 border-amber-200/60 dark:border-amber-800/40'
                    "
                  >
                    <ShieldCheck v-if="authStore.user?.mfaEnabled" class="w-3.5 h-3.5" />
                    <ShieldAlert v-else class="w-3.5 h-3.5" />
                  </div>
                  <div class="min-w-0">
                    <div class="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                      2-Factor Auth
                    </div>
                    <div
                      class="text-[11px] font-semibold"
                      :class="
                        authStore.user?.mfaEnabled
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : 'text-amber-600 dark:text-amber-400'
                      "
                    >
                      {{ authStore.user?.mfaEnabled ? 'Enabled' : 'Disabled' }}
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  @click="handleMfaToggle(close)"
                  class="px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-colors cursor-pointer"
                  :class="
                    authStore.user?.mfaEnabled
                      ? 'text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 border border-rose-200 dark:border-rose-900/40'
                      : 'text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-950/40 border border-indigo-200 dark:border-indigo-800/40'
                  "
                >
                  {{ authStore.user?.mfaEnabled ? 'Disable' : 'Enable' }}
                </button>
              </div>
            </div>

            <!-- Actions Row -->
            <div
              class="mt-2 pt-2 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between gap-1.5 px-1"
            >
              <button
                type="button"
                @click="router.push('/apps'); close()"
                class="flex-1 flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
              >
                <Layers class="w-3.5 h-3.5" />
                <span>Apps</span>
              </button>

              <button
                type="button"
                @click="handleLogout(close)"
                class="flex-1 flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-colors cursor-pointer"
              >
                <LogOut class="w-3.5 h-3.5" />
                <span>Sign Out</span>
              </button>
            </div>
          </div>
        </template>
      </CustomDropdown>
    </div>
  </header>

  <!-- MFA Setup and Disable Modals -->
  <MfaSetupModal
    :is-open="isMfaSetupOpen"
    @close="isMfaSetupOpen = false"
    @success="authStore.fetchMe"
  />
  <MfaDisableModal
    :is-open="isMfaDisableOpen"
    @close="isMfaDisableOpen = false"
    @success="authStore.fetchMe"
  />
</template>
