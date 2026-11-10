import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { appsApi, type AppSummary } from '../api/apps'
import { useAuthStore } from './authStore'

export const useAppStore = defineStore('app', () => {
  const authStore = useAuthStore()
  const apps = ref<AppSummary[]>([])
  const isLoading = ref<boolean>(false)
  let inFlightPromise: Promise<AppSummary[]> | null = null
  let lastLoadedOrgId: string | null = null

  const currentApp = computed(() => {
    if (!authStore.currentAppId) return null
    return apps.value.find((a) => a.id === authStore.currentAppId) || null
  })

  async function loadApps(orgId?: string, force = false): Promise<AppSummary[]> {
    const targetOrgId = orgId || authStore.currentOrgId
    if (!targetOrgId) {
      apps.value = []
      return []
    }

    // Cache hit: already loaded for this org and not forcing refresh
    if (!force && lastLoadedOrgId === targetOrgId && apps.value.length > 0) {
      // Ensure currentAppId is set to an existing app in this org
      const exists = apps.value.some((a) => a.id === authStore.currentAppId)
      if (!authStore.currentAppId || !exists) {
        authStore.setCurrentApp(apps.value[0].id)
      }
      return apps.value
    }

    // In-flight request deduplication: return the shared promise
    if (inFlightPromise) {
      return inFlightPromise
    }

    isLoading.value = true
    inFlightPromise = (async () => {
      try {
        const data = await appsApi.list(targetOrgId)
        apps.value = data
        lastLoadedOrgId = targetOrgId

        // If no app selected or selected app not in this org, pick first app
        if (data && data.length > 0) {
          const exists = data.some((a) => a.id === authStore.currentAppId)
          if (!authStore.currentAppId || !exists) {
            authStore.setCurrentApp(data[0].id)
          }
        }
        return data
      } catch (e) {
        console.error('Failed to load apps:', e)
        apps.value = []
        return []
      } finally {
        isLoading.value = false
        inFlightPromise = null
      }
    })()

    return inFlightPromise
  }

  function selectApp(appId: string | null) {
    authStore.setCurrentApp(appId)
  }

  function clear() {
    apps.value = []
    lastLoadedOrgId = null
    inFlightPromise = null
  }

  return {
    apps,
    isLoading,
    currentApp,
    loadApps,
    selectApp,
    clear,
  }
})
