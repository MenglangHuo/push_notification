import { ref, computed } from 'vue'
const mockService = {
  getEnvMockDataStatus: () => false,
  setMockDataOverride: (_val: boolean | null) => {},
}

export type AppEnvironmentKey = 'local' | 'development' | 'production' | 'mock' | 'custom'

export interface EnvironmentProfile {
  key: AppEnvironmentKey
  name: string
  shortLabel: string
  description: string
  apiBaseUrl: string
  authBaseUrl: string
  proxyTarget?: string
  color: 'emerald' | 'amber' | 'rose' | 'purple' | 'sky'
  isProduction: boolean
  isMock: boolean
}

export const STORAGE_KEY_ENV = 'bronx_app_environment'
export const STORAGE_KEY_CUSTOM_URL = 'bronx_custom_api_url'
export const STORAGE_KEY_CUSTOM_AUTH_URL = 'bronx_custom_auth_url'

/**
 * Fallback preset URLs based on environment configurations
 */
const DEFAULT_PRESETS = {
  local: {
    key: 'local' as const,
    name: 'Local (127.0.0.1:8000)',
    shortLabel: 'Local Dev',
    description: 'Local development server with Vite reverse proxy',
    apiBaseUrl: '/api',
    authBaseUrl: '',
    proxyTarget: 'http://127.0.0.1:8000',
    color: 'emerald' as const,
    isProduction: false,
    isMock: false,
  },
  development: {
    key: 'development' as const,
    name: 'Development (UAT)',
    shortLabel: 'UAT / Dev',
    description: 'UAT testing domain with Vite reverse proxy',
    apiBaseUrl: '/api',
    authBaseUrl: '',
    proxyTarget: undefined,
    color: 'amber' as const,
    isProduction: false,
    isMock: false,
  },
  production: {
    key: 'production' as const,
    name: 'Production (Live)',
    shortLabel: 'Production',
    description: 'Live production cluster. Real push delivery and analytics data',
    apiBaseUrl: '/api',
    authBaseUrl: '',
    proxyTarget: undefined,
    color: 'rose' as const,
    isProduction: true,
    isMock: false,
  },
  mock: {
    key: 'mock' as const,
    name: 'Mock Sandbox',
    shortLabel: 'Mock Data',
    description: 'Standalone client-side mock backend (no live network calls)',
    apiBaseUrl: 'mock://in-memory',
    authBaseUrl: 'mock://in-memory',
    proxyTarget: undefined,
    color: 'purple' as const,
    isProduction: false,
    isMock: true,
  },
  custom: {
    key: 'custom' as const,
    name: 'Custom Endpoint',
    shortLabel: 'Custom',
    description: 'Self-hosted or feature branch backend URL',
    apiBaseUrl: '',
    authBaseUrl: '',
    proxyTarget: undefined,
    color: 'sky' as const,
    isProduction: false,
    isMock: false,
  },
}

/**
 * Returns the default environment configured at build / dev time via .env
 */
export function getBuildDefaultEnvironment(): AppEnvironmentKey {
  if (import.meta.env.MODE === 'test') {
    return 'development'
  }
  if (mockService.getEnvMockDataStatus()) {
    return 'mock'
  }
  const envKey = (import.meta.env.VITE_APP_ENV || import.meta.env.MODE || '').toLowerCase()
  if (envKey === 'production' || envKey === 'prod') return 'production'
  if (envKey === 'mock') return 'mock'
  if (envKey === 'local' || envKey === 'localdev') return 'local'
  if (envKey === 'development' || envKey === 'uat' || envKey === 'staging') return 'development'
  return 'local'
}

/**
 * Retrieves the stored runtime environment key, falling back to build default
 */
export function getStoredEnvironment(): AppEnvironmentKey {
  const defaultEnv = getBuildDefaultEnvironment()
  if (typeof localStorage === 'undefined') {
    return defaultEnv
  }

  if (import.meta.env.MODE !== 'test') {
    const envKey = (import.meta.env.VITE_APP_ENV || import.meta.env.MODE || '').toLowerCase()
    if (import.meta.env.PROD || envKey === 'production' || envKey === 'prod') {
      return 'production'
    }

    const stored = localStorage.getItem(STORAGE_KEY_ENV) as AppEnvironmentKey | null
    // If running in development (e.g. npm run dev), clear and ignore stale 'production' stored in localStorage
    if (
      stored === 'production' &&
      (defaultEnv === 'development' ||
        defaultEnv === 'local' ||
        envKey === 'development' ||
        envKey === 'local' ||
        import.meta.env.MODE === 'development' ||
        import.meta.env.MODE === 'localdev')
    ) {
      localStorage.removeItem(STORAGE_KEY_ENV)
      return defaultEnv
    }

    if (stored && ['local', 'development', 'production', 'mock', 'custom'].includes(stored)) {
      return stored
    }
    return defaultEnv
  }

  const stored = localStorage.getItem(STORAGE_KEY_ENV) as AppEnvironmentKey | null
  if (stored && ['local', 'development', 'production', 'mock', 'custom'].includes(stored)) {
    return stored
  }
  return defaultEnv
}

/**
 * Retrieves stored custom API Base URL
 */
export function getStoredCustomApiUrl(): string {
  if (typeof localStorage === 'undefined') return ''
  return localStorage.getItem(STORAGE_KEY_CUSTOM_URL) || ''
}

/**
 * Retrieves stored custom Auth Base URL
 */
export function getStoredCustomAuthUrl(): string {
  if (typeof localStorage === 'undefined') return ''
  return localStorage.getItem(STORAGE_KEY_CUSTOM_AUTH_URL) || ''
}

/**
 * Whether the user currently has an explicit runtime environment override stored
 */
export function hasEnvironmentOverride(): boolean {
  if (typeof localStorage === 'undefined') return false
  const defaultEnv = getBuildDefaultEnvironment()
  if (import.meta.env.MODE !== 'test') {
    const envKey = (import.meta.env.VITE_APP_ENV || import.meta.env.MODE || '').toLowerCase()
    if (import.meta.env.PROD || envKey === 'production' || envKey === 'prod') {
      return false
    }
    const stored = localStorage.getItem(STORAGE_KEY_ENV)
    if (
      stored === 'production' &&
      (defaultEnv === 'development' ||
        envKey === 'development' ||
        import.meta.env.MODE === 'development')
    ) {
      return false
    }
  }
  return localStorage.getItem(STORAGE_KEY_ENV) !== null
}

// Global reactive state
const currentEnvKeyRef = ref<AppEnvironmentKey>(getStoredEnvironment())
const customApiUrlRef = ref<string>(getStoredCustomApiUrl())
const customAuthUrlRef = ref<string>(getStoredCustomAuthUrl())

/**
 * Resolves the configuration profile for a given environment key
 */
export function resolveEnvironmentProfile(
  key: AppEnvironmentKey,
  customUrl?: string,
  customAuth?: string,
): EnvironmentProfile {
  const preset = DEFAULT_PRESETS[key]

  if (key === 'local') {
    const rawApiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim()
    const rawAuthBase = (import.meta.env.VITE_AUTH_BASE_URL as string | undefined)?.trim()
    const rawProxy = (import.meta.env.VITE_PROXY_TARGET as string | undefined)?.trim()

    return {
      ...preset,
      apiBaseUrl: rawApiBase || '/api',
      authBaseUrl: rawAuthBase || '',
      proxyTarget: rawProxy || preset.proxyTarget || 'http://127.0.0.1:8000',
    }
  }

  if (key === 'development') {
    const rawApiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim()
    const rawAuthBase = (import.meta.env.VITE_AUTH_BASE_URL as string | undefined)?.trim()
    const rawProxy = (import.meta.env.VITE_PROXY_TARGET as string | undefined)?.trim()

    return {
      ...preset,
      apiBaseUrl: rawApiBase || '/api',
      authBaseUrl: rawAuthBase || '',
      proxyTarget: rawProxy || preset.proxyTarget,
    }
  }

  if (key === 'production') {
    const rawApi = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim()
    const rawAuth = (import.meta.env.VITE_AUTH_BASE_URL as string | undefined)?.trim()
    const rawProxy = (import.meta.env.VITE_PROXY_TARGET as string | undefined)?.trim()

    const apiBase = rawApi || (rawProxy ? `${rawProxy.replace(/\/+$/, '')}/api` : preset.apiBaseUrl)

    const authBase =
      rawAuth !== undefined
        ? rawAuth
        : rawProxy
          ? rawProxy.replace(/\/+$/, '')
          : apiBase.startsWith('http')
            ? apiBase.replace(/\/api(\/.*)?$/, '')
            : ''

    return {
      ...preset,
      apiBaseUrl: apiBase,
      authBaseUrl: authBase,
      proxyTarget: rawProxy || (authBase.startsWith('http') ? authBase : undefined),
    }
  }

  if (key === 'custom') {
    const apiBase = (customUrl ?? customApiUrlRef.value).trim() || 'http://localhost:8000/api'
    const authBase =
      (customAuth ?? customAuthUrlRef.value).trim() || apiBase.replace(/\/api(\/.*)?$/, '')
    return {
      ...preset,
      apiBaseUrl: apiBase,
      authBaseUrl: authBase,
      description: `Custom target: ${apiBase}`,
    }
  }

  return preset
}

/**
 * Reactive active profile
 */
export const activeProfile = computed<EnvironmentProfile>(() => {
  return resolveEnvironmentProfile(
    currentEnvKeyRef.value,
    customApiUrlRef.value,
    customAuthUrlRef.value,
  )
})

/**
 * Reactive environment key
 */
export const currentEnvironmentKey = computed<AppEnvironmentKey>(() => currentEnvKeyRef.value)

/**
 * Checks whether the application is running in production mode or actively targeting production.
 */
export function isProductionEnvironment(): boolean {
  if (activeProfile.value.isProduction) return true
  if (import.meta.env.MODE === 'production' || import.meta.env.PROD) return true
  const envKey = (import.meta.env.VITE_APP_ENV || '').toLowerCase()
  if (envKey === 'production' || envKey === 'prod') return true
  return getBuildDefaultEnvironment() === 'production'
}

/**
 * Reactive computed boolean for production environment
 */
export const isProduction = computed<boolean>(() => isProductionEnvironment())

/**
 * Whether environment switching is permitted.
 * In production mode or when running as production, environment switching is disabled.
 */
export function isEnvironmentSwitchingEnabled(): boolean {
  return !isProductionEnvironment()
}

/**
 * Returns the active API Base URL
 */
export function getActiveApiBaseUrl(): string {
  return activeProfile.value.apiBaseUrl
}

/**
 * Returns the active Auth Base URL
 */
export function getActiveAuthBaseUrl(): string {
  return activeProfile.value.authBaseUrl
}

/**
 * Switch runtime environment with complete synchronization
 */
export function setEnvironment(
  key: AppEnvironmentKey,
  options: {
    customApiUrl?: string
    customAuthUrl?: string
    clearSession?: boolean
    notify?: boolean
  } = {},
): EnvironmentProfile {
  // When running strictly in production mode (non-test), environment switching is disabled
  if (
    import.meta.env.MODE !== 'test' &&
    (import.meta.env.PROD ||
      (import.meta.env.VITE_APP_ENV || import.meta.env.MODE || '').toLowerCase() === 'production')
  ) {
    console.warn('Environment switching is disabled in production mode.')
    return activeProfile.value
  }

  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(STORAGE_KEY_ENV, key)
    if (options.customApiUrl !== undefined) {
      localStorage.setItem(STORAGE_KEY_CUSTOM_URL, options.customApiUrl)
      customApiUrlRef.value = options.customApiUrl
    }
    if (options.customAuthUrl !== undefined) {
      localStorage.setItem(STORAGE_KEY_CUSTOM_AUTH_URL, options.customAuthUrl)
      customAuthUrlRef.value = options.customAuthUrl
    }
  }

  currentEnvKeyRef.value = key

  // Sync mock data state
  if (key === 'mock') {
    mockService.setMockDataOverride(true)
  } else {
    mockService.setMockDataOverride(false)
  }

  // Clear session token if switching backend servers (prevents 401 loop)
  if (options.clearSession && typeof localStorage !== 'undefined') {
    localStorage.removeItem('bronx_access_token')
    localStorage.removeItem('bronx_user')
  }

  // Dispatch custom window event for reactive listeners
  if (typeof window !== 'undefined') {
    window.dispatchEvent(
      new CustomEvent('bronx-environment-changed', {
        detail: {
          key,
          profile: activeProfile.value,
          clearedSession: !!options.clearSession,
        },
      }),
    )
  }

  return activeProfile.value
}

/**
 * Resets runtime environment back to build-time .env default
 */
export function resetEnvironmentToDefault(clearSession = false): EnvironmentProfile {
  if (typeof localStorage !== 'undefined') {
    localStorage.removeItem(STORAGE_KEY_ENV)
    localStorage.removeItem(STORAGE_KEY_CUSTOM_URL)
    localStorage.removeItem(STORAGE_KEY_CUSTOM_AUTH_URL)
  }
  mockService.setMockDataOverride(null)
  currentEnvKeyRef.value = getBuildDefaultEnvironment()
  customApiUrlRef.value = ''
  customAuthUrlRef.value = ''

  if (clearSession && typeof localStorage !== 'undefined') {
    localStorage.removeItem('bronx_access_token')
    localStorage.removeItem('bronx_user')
  }

  if (typeof window !== 'undefined') {
    window.dispatchEvent(
      new CustomEvent('bronx-environment-changed', {
        detail: {
          key: currentEnvKeyRef.value,
          profile: activeProfile.value,
          clearedSession: clearSession,
        },
      }),
    )
  }

  return activeProfile.value
}

/**
 * List all available preset environments
 */
export function getAvailableEnvironments(): EnvironmentProfile[] {
  return (['local', 'development', 'production', 'mock', 'custom'] as AppEnvironmentKey[]).map(
    (key) => resolveEnvironmentProfile(key, customApiUrlRef.value, customAuthUrlRef.value),
  )
}
