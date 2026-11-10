import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

export const apiClient = axios.create({
  baseURL: '/admin/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Needed for HttpOnly refresh cookie
})

let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token))
  refreshSubscribers = []
}

export function setupAxiosInterceptors(
  getAccessToken: () => string | null,
  setAccessToken: (token: string | null) => void,
  getOrgId: () => string | null,
  getAppId: () => string | null,
  onLogout: () => void
) {
  // Request Interceptor
  apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }

    const orgId = getOrgId()
    if (orgId) {
      config.headers.set('X-Org-Id', orgId)
    }

    const appId = getAppId()
    if (appId) {
      config.headers.set('X-App-Id', appId)
    }

    return config
  })

  // Response Interceptor with Token Refresh Queue
  apiClient.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

      if (!originalRequest || !error.response || error.response.status !== 401) {
        return Promise.reject(error)
      }

      // Avoid infinite loops for login or refresh endpoints
      if (
        originalRequest.url?.includes('/auth/login') ||
        originalRequest.url?.includes('/auth/refresh')
      ) {
        return Promise.reject(error)
      }

      if (originalRequest._retry) {
        return Promise.reject(error)
      }

      originalRequest._retry = true

      if (isRefreshing) {
        return new Promise((resolve) => {
          subscribeTokenRefresh((newToken: string) => {
            originalRequest.headers.set('Authorization', `Bearer ${newToken}`)
            resolve(apiClient(originalRequest))
          })
        })
      }

      isRefreshing = true

      try {
        const refreshResponse = await axios.post<{ accessToken: string }>(
          '/admin/api/v1/auth/refresh',
          {},
          { withCredentials: true }
        )

        const newAccessToken = refreshResponse.data.accessToken
        setAccessToken(newAccessToken)
        onRefreshed(newAccessToken)

        originalRequest.headers.set('Authorization', `Bearer ${newAccessToken}`)
        return apiClient(originalRequest)
      } catch (refreshError) {
        setAccessToken(null)
        onLogout()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }
  )
}

export const ROOT_API_URL = '/admin/api/v1'
export default apiClient
