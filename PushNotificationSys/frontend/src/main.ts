import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { i18n } from './i18n'
import { vCan } from './directives/vCan'
import { setupAxiosInterceptors } from './api/client'
import { useAuthStore } from './stores/authStore'
import { useThemeStore } from './stores/themeStore'

import './assets/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(i18n)

// Register custom v-can permission directive
app.directive('can', vCan)

// Initialize Theme
const themeStore = useThemeStore()
themeStore.initTheme()

// Initialize AuthStore and link Axios interceptors
const authStore = useAuthStore()
setupAxiosInterceptors(
  () => authStore.accessToken,
  (token) => authStore.setAccessToken(token),
  () => authStore.currentOrgId,
  () => authStore.currentAppId,
  () => {
    authStore.logout()
    router.push('/login')
  }
)

// Wait until router is ready before mounting to avoid template flashes
router.isReady().then(() => {
  app.mount('#app')
})
