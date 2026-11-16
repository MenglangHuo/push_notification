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

export interface CreatedAppResult extends AppSummary {
  defaultServerKey?: {
    id: string
    name: string
    keyType: 'SERVER_REST'
    keyPrefix: string
    rawSecretKey: string
    scopes: string[]
  }
  defaultClientSdkKey?: {
    id: string
    name: string
    keyType: 'SDK_PUBLIC'
    keyPrefix: string
    rawSecretKey: string
    scopes: string[]
  }
  vapidPublicKey?: string
}

export const appsApi = {
  async list(orgId?: string): Promise<AppSummary[]> {
    const params = orgId ? { orgId } : {}
    const res = await apiClient.get<AppSummary[]>('/users/me/accessible-apps', { params })
    return res.data
  },

  async listAccessible(orgId?: string): Promise<AppSummary[]> {
    return this.list(orgId)
  },

  async listAllForOrg(orgId: string): Promise<AppSummary[]> {
    const res = await apiClient.get<AppSummary[]>(`/organizations/${orgId}/apps`)
    return res.data
  },

  async get(appId: string): Promise<AppSummary> {
    const res = await apiClient.get<AppSummary>(`/apps/${appId}`)
    return res.data
  },

  async create(orgId: string, payload: { name: string; description?: string }): Promise<CreatedAppResult> {
    const res = await apiClient.post<CreatedAppResult>(`/organizations/${orgId}/apps`, payload)
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
