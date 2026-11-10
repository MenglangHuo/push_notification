import { apiClient } from './client'

export interface CredentialSummary {
  id: string
  providerType: 'FCM' | 'APNS' | 'WEBPUSH'
  active: boolean
  metadataMasked: Record<string, any>
  updatedAt: string
}

export const credentialsApi = {
  async list(appId: string): Promise<CredentialSummary[]> {
    const res = await apiClient.get<CredentialSummary[]>(`/apps/${appId}/credentials`)
    return res.data
  },

  async uploadFcm(appId: string, serviceAccountJson: string): Promise<CredentialSummary> {
    const res = await apiClient.post<CredentialSummary>(`/apps/${appId}/credentials/fcm`, serviceAccountJson, {
      headers: { 'Content-Type': 'application/json' },
    })
    return res.data
  },

  async saveApns(
    appId: string,
    payload: { rawCredential: string; keyId: string; teamId: string; bundleId: string }
  ): Promise<CredentialSummary> {
    const res = await apiClient.post<CredentialSummary>(`/apps/${appId}/credentials/apns`, {
      providerType: 'APNS',
      ...payload,
    })
    return res.data
  },

  async generateVapid(appId: string): Promise<{ publicKey: string }> {
    const res = await apiClient.post<{ publicKey: string }>(`/apps/${appId}/credentials/vapid/generate`)
    return res.data
  },

  async delete(appId: string, provider: 'fcm' | 'apns' | 'webpush'): Promise<void> {
    await apiClient.delete(`/apps/${appId}/credentials/${provider}`)
  },
}
