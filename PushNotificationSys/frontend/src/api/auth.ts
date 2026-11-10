import { apiClient } from './client'
import type { LoginResponse, UserProfile } from './types'

export const authApi = {
  async login(payload: { email: string; password: string }): Promise<LoginResponse> {
    const res = await apiClient.post<LoginResponse>('/auth/login', payload)
    return res.data
  },

  async verifyMfa(payload: { mfaTicket: string; totpCode: string }): Promise<LoginResponse> {
    const res = await apiClient.post<LoginResponse>('/auth/mfa/verify', payload)
    return res.data
  },

  async refresh(): Promise<{ accessToken: string }> {
    const res = await apiClient.post<{ accessToken: string }>('/auth/refresh')
    return res.data
  },

  async logout(): Promise<void> {
    await apiClient.post('/auth/logout')
  },

  async getMe(): Promise<UserProfile> {
    const res = await apiClient.get<UserProfile>('/auth/me')
    return res.data
  },
}
