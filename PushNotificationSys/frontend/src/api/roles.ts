import { apiClient } from './client'
import type { Permission, Role } from './types'

export const rolesApi = {
  async listRoles(orgId: string): Promise<Role[]> {
    const res = await apiClient.get<Role[]>(`/organizations/${orgId}/roles`)
    return res.data
  },

  async listPermissions(): Promise<Permission[]> {
    const res = await apiClient.get<Permission[]>('/permissions')
    return res.data
  },

  async createCustomRole(orgId: string, payload: { name: string; description?: string; permissionCodes: string[] }): Promise<Role> {
    const res = await apiClient.post<Role>(`/organizations/${orgId}/roles`, payload)
    return res.data
  },

  async updateCustomRole(orgId: string, roleId: string, payload: { name: string; description?: string; permissionCodes: string[] }): Promise<Role> {
    const res = await apiClient.put<Role>(`/organizations/${orgId}/roles/${roleId}`, payload)
    return res.data
  },
}
