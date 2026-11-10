<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from '@/components/layout/Sidebar.vue'
import Topbar from '@/components/layout/Topbar.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import ToastContainer from '@/components/layout/ToastContainer.vue'
import SplashScreen from '@/components/common/SplashScreen.vue'
import { useSplashStore } from '@/stores/splashStore'

const route = useRoute()
const splashStore = useSplashStore()
const isMobileSidebarOpen = ref<boolean>(false)

onMounted(() => {
  splashStore.finishInitialLoad()
})

const isStandaloneRoute = computed(() => {
  if (route.meta?.standalone) return true

  const currentPath = route.path || (typeof window !== 'undefined' ? window.location.pathname : '')
  const currentName = (route.name as string | undefined)?.toLowerCase()

  return (
    currentName === 'login' ||
    currentName === 'mfaverify' ||
    currentName === 'acceptinvite' ||
    currentPath === '/login' ||
    currentPath === '/mfa' ||
    currentPath === '/accept-invite'
  )
})

function toggleMobileSidebar() {
  isMobileSidebarOpen.value = !isMobileSidebarOpen.value
}

function closeMobileSidebar() {
  isMobileSidebarOpen.value = false
}
</script>

<template>
  <div
    :class="[
      'min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 transition-colors',
      isStandaloneRoute ? 'w-full block min-h-screen' : 'flex overflow-hidden',
    ]"
  >
    <!-- Global Splash & Loading Screen -->
    <SplashScreen />

    <!-- Toast notifications container -->
    <ToastContainer />

    <!-- If on Standalone page (Login / MFA / Accept Invite), render directly -->
    <template v-if="isStandaloneRoute">
      <router-view />
    </template>

    <!-- Otherwise, render Main Application Layout -->
    <template v-else>
      <!-- Mobile Backdrop -->
      <div
        v-if="isMobileSidebarOpen"
        @click="closeMobileSidebar"
        class="fixed inset-0 z-20 bg-slate-950/70 backdrop-blur-sm md:hidden"
      ></div>

      <!-- Collapsible / Responsive Sidebar -->
      <Sidebar :isMobileOpen="isMobileSidebarOpen" @closeMobile="closeMobileSidebar" />

      <!-- Main App Content Area -->
      <div class="flex-1 flex flex-col min-w-0 h-screen overflow-hidden">
        <!-- Top Navigation Bar -->
        <Topbar :isMobileOpen="isMobileSidebarOpen" @toggleMobile="toggleMobileSidebar" />

        <!-- Main View Container: Scrolls all page data smoothly -->
        <main class="flex-1 overflow-y-auto p-4 md:p-6 min-h-0 bg-slate-50/60 dark:bg-slate-950/40">
          <router-view />
        </main>

        <!-- App Footer Layout -->
        <AppFooter />
      </div>
    </template>
  </div>
</template>
