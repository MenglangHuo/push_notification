import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/auth'
import type { UserProfile } from '../api/types'

export interface PendingTwoFactorData {
  username: string
  appToken: string
  user: any
  role: string
  expiresAt: number
  maskedEmail?: string
  maskedPhone?: string
}

const PENDING_2FA_STORAGE_KEY = 'bronx_pending_2fa'

export function normalizeRole(role: unknown): string {
  if (!role) return ''
  if (typeof role === 'object') {
    const r = role as any
    const name = r.role_name || r.name || r.title || r.slug || ''
    return normalizeRole(name)
  }
  return role.toString().trim().toUpperCase().replace(/[\s-]+/g, '_')
}

export function isAllowedRole(role: unknown): boolean {
  const norm = normalizeRole(role)
  if (!norm) return true // In push notification sys, don't arbitrarily reject normal roles
  if (norm.includes('BRANCH') || norm === 'NONE') {
    return false
  }
  return true
}

export function extractUserData(res: any): any {
  if (!res || typeof res !== 'object') return res
  if (res.data && typeof res.data === 'object') {
    if (res.data.user && typeof res.data.user === 'object') return extractUserData(res.data.user)
    if (res.data.profile && typeof res.data.profile === 'object') return extractUserData(res.data.profile)
    return res.data
  }
  if (res.user && typeof res.user === 'object') return extractUserData(res.user)
  return res
}

export function extractUserRole(user: any, fallbackRes?: any): string {
  if (!user) return ''
  return user.role || user.role_name || fallbackRes?.role || 'Admin'
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('bronx_access_token') || null)
  const token = computed({
    get: () => accessToken.value,
    set: (val: string | null) => {
      accessToken.value = val
    },
  })
  const user = ref<UserProfile | null>(null)
  const currentOrgId = ref<string | null>(localStorage.getItem('push_current_org_id'))
  const currentAppId = ref<string | null>(localStorage.getItem('push_current_app_id'))
  const isInitializing = ref<boolean>(true)
  const isLoading = ref<boolean>(false)
  const error = ref<string | null>(null)
  const remainingAttempts = ref<number>(5)
  const lockoutUntil = ref<number | null>(null)

  // Pending 2-Factor Authentication session
  const pendingTwoFactor = ref<PendingTwoFactorData | null>(
    (() => {
      try {
        const stored = sessionStorage.getItem(PENDING_2FA_STORAGE_KEY)
        if (!stored) return null
        const parsed = JSON.parse(stored) as PendingTwoFactorData
        if (parsed.expiresAt && Date.now() > parsed.expiresAt) {
          sessionStorage.removeItem(PENDING_2FA_STORAGE_KEY)
          return null
        }
        return parsed
      } catch {
        return null
      }
    })(),
  )

  const hasPendingTwoFactor = computed<boolean>(() => {
    if (!pendingTwoFactor.value) return false
    if (Date.now() > pendingTwoFactor.value.expiresAt) {
      clearPendingTwoFactor()
      return false
    }
    return true
  })

  const isAuthenticated = computed(() => !!accessToken.value && !!user.value)
  const isSuperAdmin = computed(() => user.value?.superAdmin ?? false)

  const isAuthorizedAdmin = computed(() => true)
  const isReadOnly = computed(() => false)
  const roleLabel = computed(() => user.value?.role || (isSuperAdmin.value ? 'Super Admin' : 'Admin'))

  const currentOrg = computed(() => {
    if (!user.value || !currentOrgId.value) return null
    return user.value.organizations.find((o) => o.orgId === currentOrgId.value) || null
  })

  function setAccessToken(newToken: string | null) {
    accessToken.value = newToken
    if (newToken) {
      localStorage.setItem('bronx_access_token', newToken)
    } else {
      localStorage.removeItem('bronx_access_token')
    }
  }

  function setPendingTwoFactor(data: PendingTwoFactorData) {
    pendingTwoFactor.value = data
    try {
      sessionStorage.setItem(PENDING_2FA_STORAGE_KEY, JSON.stringify(data))
    } catch (e) {
      console.warn('Unable to persist pending 2FA in sessionStorage:', e)
    }
  }

  function clearPendingTwoFactor() {
    pendingTwoFactor.value = null
    try {
      sessionStorage.removeItem(PENDING_2FA_STORAGE_KEY)
    } catch {}
  }

  function setCurrentOrg(orgId: string) {
    currentOrgId.value = orgId
    localStorage.setItem('push_current_org_id', orgId)
    // Clear selected app when changing organization
    setCurrentApp(null)
  }

  function setCurrentApp(appId: string | null) {
    currentAppId.value = appId
    if (appId) {
      localStorage.setItem('push_current_app_id', appId)
    } else {
      localStorage.removeItem('push_current_app_id')
    }
  }

  function hasPermission(permissionCode: string, _appId?: string | null): boolean {
    if (!user.value) return false
    if (user.value.superAdmin) return true

    if (!currentOrgId.value) return false
    const orgMembership = user.value.organizations.find((o) => o.orgId === currentOrgId.value)
    if (!orgMembership) return false

    return orgMembership.permissions.includes(permissionCode)
  }

  async function fetchMe() {
    try {
      const profile = await authApi.getMe()
      user.value = profile
      localStorage.setItem('bronx_user', JSON.stringify(profile))

      // Set default org if not selected or invalid
      if (profile.organizations.length > 0) {
        const hasCurrentOrg = profile.organizations.some((o) => o.orgId === currentOrgId.value)
        if (!currentOrgId.value || !hasCurrentOrg) {
          setCurrentOrg(profile.organizations[0].orgId)
        }
      }
    } catch (e) {
      user.value = null
      setAccessToken(null)
      localStorage.removeItem('bronx_user')
    }
  }

  async function login(
    credentialsOrEmail: { email?: string; username?: string; password?: string } | string,
    passwordArg?: string,
  ): Promise<boolean | any> {
    isLoading.value = true
    error.value = null
    try {
      let email = ''
      let password = ''
      if (typeof credentialsOrEmail === 'string') {
        email = credentialsOrEmail
        password = passwordArg || ''
      } else {
        email = credentialsOrEmail.email || credentialsOrEmail.username || ''
        password = credentialsOrEmail.password || ''
      }

      const res = await authApi.login({ email, password })
      if (res.mfaRequired) {
        setPendingTwoFactor({
          username: email,
          appToken: res.mfaTicket || '',
          user: res.user || ({} as any),
          role: 'ADMIN',
          expiresAt: Date.now() + 5 * 60 * 1000,
        })
        return res
      }

      if (res.accessToken) {
        setAccessToken(res.accessToken)
        if (res.user) {
          user.value = res.user
          localStorage.setItem('bronx_user', JSON.stringify(res.user))
          if (res.user.organizations.length > 0 && !currentOrgId.value) {
            setCurrentOrg(res.user.organizations[0].orgId)
          }
        } else {
          await fetchMe()
        }
      }
      return typeof credentialsOrEmail === 'string' ? true : res
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Login failed'
      error.value = msg
      return false
    } finally {
      isLoading.value = false
    }
  }

  async function verifyMfa(payload: { mfaTicket: string; totpCode: string }) {
    isLoading.value = true
    error.value = null
    try {
      const res = await authApi.verifyMfa(payload)
      if (res.accessToken) {
        setAccessToken(res.accessToken)
        clearPendingTwoFactor()
        if (res.user) {
          user.value = res.user
          localStorage.setItem('bronx_user', JSON.stringify(res.user))
          if (res.user.organizations.length > 0 && !currentOrgId.value) {
            setCurrentOrg(res.user.organizations[0].orgId)
          }
        } else {
          await fetchMe()
        }
      }
      return res
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'MFA verification failed'
      error.value = msg
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (e) {
      // Ignore network errors during logout
    } finally {
      setAccessToken(null)
      user.value = null
      clearPendingTwoFactor()
      localStorage.removeItem('bronx_user')
    }
  }

  async function initAuth() {
    isInitializing.value = true
    try {
      const storedToken = localStorage.getItem('bronx_access_token')
      const storedUser = localStorage.getItem('bronx_user')
      if (storedToken && storedUser) {
        accessToken.value = storedToken
        user.value = JSON.parse(storedUser)
      }

      // Try refresh using HttpOnly cookie or fetch latest profile
      const res = await authApi.refresh().catch(() => null)
      if (res && res.accessToken) {
        setAccessToken(res.accessToken)
        await fetchMe()
      } else if (accessToken.value) {
        await fetchMe()
      }
    } catch (e) {
      setAccessToken(null)
      user.value = null
    } finally {
      isInitializing.value = false
    }
  }

  return {
    accessToken,
    token,
    user,
    currentOrgId,
    currentAppId,
    isInitializing,
    isLoading,
    error,
    remainingAttempts,
    lockoutUntil,
    pendingTwoFactor,
    hasPendingTwoFactor,
    isAuthenticated,
    isSuperAdmin,
    isAuthorizedAdmin,
    isReadOnly,
    roleLabel,
    currentOrg,
    setAccessToken,
    setPendingTwoFactor,
    clearPendingTwoFactor,
    setCurrentOrg,
    setCurrentApp,
    hasPermission,
    fetchMe,
    login,
    verifyMfa,
    logout,
    initAuth,
  }
})
