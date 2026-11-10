import { apiClient } from './client'

export interface PresenceStats {
  appId: string
  onlineCount: number
  localSessionCount: number
}

export const presenceApi = {
  async getStats(appId: string): Promise<PresenceStats> {
    const res = await apiClient.get<PresenceStats>(`/apps/${appId}/presence/stats`)
    return res.data
  },
}
