import { apiClient } from './client'
import type { Invitation, Member } from './types'

export const membersApi = {
  async list(orgId: string): Promise<Member[]> {
    const res = await apiClient.get<Member[]>(`/organizations/${orgId}/members`)
    return res.data
  },

  async assignRole(orgId: string, membershipId: string, payload: { roleId: string; appId?: string | null }): Promise<Member> {
    const res = await apiClient.post<Member>(`/organizations/${orgId}/members/${membershipId}/roles`, payload)
    return res.data
  },

  async removeRole(orgId: string, membershipId: string, assignmentId: string): Promise<void> {
    await apiClient.delete(`/organizations/${orgId}/members/${membershipId}/roles/${assignmentId}`)
  },

  async removeMember(orgId: string, membershipId: string): Promise<void> {
    await apiClient.delete(`/organizations/${orgId}/members/${membershipId}`)
  },

  async invite(orgId: string, payload: { email: string; roleId: string; appId?: string | null }): Promise<Invitation> {
    const res = await apiClient.post<Invitation>(`/organizations/${orgId}/invitations`, payload)
    return res.data
  },

  async acceptInvite(payload: { token: string; fullName: string; password: string }): Promise<void> {
    await apiClient.post('/invitations/accept', payload)
  },
}
