import { apiClient } from './client'
import type { Organization } from './types'

export const orgsApi = {
  async list(): Promise<Organization[]> {
    const res = await apiClient.get<Organization[]>('/organizations')
    return res.data
  },

  async get(orgId: string): Promise<Organization> {
    const res = await apiClient.get<Organization>(`/organizations/${orgId}`)
    return res.data
  },

  async create(payload: { name: string; slug: string }): Promise<Organization> {
    const res = await apiClient.post<Organization>('/organizations', payload)
    return res.data
  },

  async update(orgId: string, payload: { name: string; tier?: string }): Promise<Organization> {
    const res = await apiClient.put<Organization>(`/organizations/${orgId}`, payload)
    return res.data
  },
}
