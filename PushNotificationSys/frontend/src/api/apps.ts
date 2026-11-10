import { apiClient } from './client'

export interface AppSummary {
  id: string
  organizationId: string
  name: string
  description?: string
  configuredProviders: ('FCM' | 'APNS' | 'WEBPUSH')[]
  createdAt: string
  updatedAt: string
}

export const appsApi = {
  async list(orgId: string): Promise<AppSummary[]> {
    const res = await apiClient.get<AppSummary[]>(`/organizations/${orgId}/apps`)
    return res.data
  },

  async get(appId: string): Promise<AppSummary> {
    const res = await apiClient.get<AppSummary>(`/apps/${appId}`)
    return res.data
  },

  async create(orgId: string, payload: { name: string; description?: string }): Promise<AppSummary> {
    const res = await apiClient.post<AppSummary>(`/organizations/${orgId}/apps`, payload)
    return res.data
  },

  async update(appId: string, payload: { name: string; description?: string }): Promise<AppSummary> {
    const res = await apiClient.put<AppSummary>(`/apps/${appId}`, payload)
    return res.data
  },

  async delete(appId: string): Promise<void> {
    await apiClient.delete(`/apps/${appId}`)
  },
}
