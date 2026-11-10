import { apiClient } from './client'

export interface DeviceSummary {
  id: string
  appId: string
  externalUserId?: string
  platform: 'ANDROID' | 'IOS' | 'WEB'
  pushTokenMasked: string
  deviceModel?: string
  osVersion?: string
  appVersion?: string
  language: string
  timezone: string
  active: boolean
  lastActiveAt: string
  createdAt: string
  tags: Record<string, string>
}

export interface DeviceStats {
  totalDevices: number
  activeDevices: number
  androidActive: number
  iosActive: number
  webActive: number
}

export const devicesApi = {
  async list(appId: string, params?: { active?: boolean; page?: number; size?: number }) {
    const res = await apiClient.get<{
      content: DeviceSummary[]
      totalElements: number
      totalPages: number
      number: number
    }>(`/apps/${appId}/devices`, { params })
    return res.data
  },

  async stats(appId: string): Promise<DeviceStats> {
    const res = await apiClient.get<DeviceStats>(`/apps/${appId}/devices/stats`)
    return res.data
  },

  async delete(appId: string, deviceId: string): Promise<void> {
    await apiClient.delete(`/apps/${appId}/devices/${deviceId}`)
  },

  async register(
    appId: string,
    payload: {
      platform: 'ANDROID' | 'IOS' | 'WEB'
      pushToken: string
      webAuthKey?: string
      webP256dhKey?: string
      externalUserId?: string
      deviceModel?: string
      osVersion?: string
      appVersion?: string
      language?: string
      timezone?: string
      tags?: Record<string, string>
    }
  ): Promise<DeviceSummary> {
    const res = await apiClient.post<DeviceSummary>(`/apps/${appId}/devices`, payload)
    return res.data
  },
}
