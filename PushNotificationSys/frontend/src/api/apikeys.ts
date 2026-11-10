import { apiClient } from './client'

export interface ApiKeySummary {
  id: string
  name: string
  keyType: 'SERVER_REST' | 'SDK_PUBLIC'
  keyPrefix: string
  scopes: string[]
  allowedOrigins: string[]
  allowedBundleIds: string[]
  expiresAt?: string | null
  lastUsedAt?: string | null
  revoked: boolean
  createdAt: string
}

export interface ApiKeyCreated {
  id: string
  name: string
  keyType: 'SERVER_REST' | 'SDK_PUBLIC'
  keyPrefix: string
  rawSecretKey: string
  scopes: string[]
  allowedOrigins: string[]
  allowedBundleIds: string[]
  expiresAt?: string | null
  createdAt: string
}

export const apiKeysApi = {
  async list(appId: string): Promise<ApiKeySummary[]> {
    const res = await apiClient.get<ApiKeySummary[]>(`/apps/${appId}/api-keys`)
    return res.data
  },

  async create(
    appId: string,
    payload: {
      name: string
      keyType: 'SERVER_REST' | 'SDK_PUBLIC'
      scopes?: string[]
      allowedOrigins?: string[]
      allowedBundleIds?: string[]
      expiresAt?: string | null
    }
  ): Promise<ApiKeyCreated> {
    const res = await apiClient.post<ApiKeyCreated>(`/apps/${appId}/api-keys`, payload)
    return res.data
  },

  async rotate(appId: string, keyId: string): Promise<{ id: string; name: string; keyPrefix: string; rawSecretKey: string }> {
    const res = await apiClient.post(`/apps/${appId}/api-keys/${keyId}/rotate`)
    return res.data
  },

  async revoke(appId: string, keyId: string): Promise<void> {
    await apiClient.delete(`/apps/${appId}/api-keys/${keyId}`)
  },
}
