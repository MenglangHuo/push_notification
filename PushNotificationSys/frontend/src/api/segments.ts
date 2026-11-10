import { apiClient } from './client'

export interface FilterRule {
  field: 'tag' | 'platform' | 'language' | 'timezone' | 'is_active' | 'last_active_days' | 'app_version' | 'device_model'
  tagKey?: string
  op: 'EQ' | 'NEQ' | 'CONTAINS' | 'IN' | 'EXISTS' | 'NOT_EXISTS' | 'GTE' | 'LTE'
  value?: any
}

export interface FilterAst {
  conjunction: 'AND' | 'OR'
  rules: FilterRule[]
  groups?: FilterAst[]
}

export interface Segment {
  id: string
  appId: string
  name: string
  description?: string
  filterAst: FilterAst
  active: boolean
  estimatedCount: number
  createdAt: string
  updatedAt: string
}

export interface CreateSegmentPayload {
  name: string
  description?: string
  filterAst: FilterAst
}

export interface UpdateSegmentPayload {
  name: string
  description?: string
  filterAst: FilterAst
  active?: boolean
}

export interface AudienceEstimateResponse {
  totalCount: number
  androidCount: number
  iosCount: number
  webCount: number
}

export const segmentsApi = {
  async list(appId: string): Promise<Segment[]> {
    const res = await apiClient.get<Segment[]>(`/apps/${appId}/segments`)
    return res.data
  },

  async get(appId: string, segmentId: string): Promise<Segment> {
    const res = await apiClient.get<Segment>(`/apps/${appId}/segments/${segmentId}`)
    return res.data
  },

  async create(appId: string, payload: CreateSegmentPayload): Promise<Segment> {
    const res = await apiClient.post<Segment>(`/apps/${appId}/segments`, payload)
    return res.data
  },

  async update(appId: string, segmentId: string, payload: UpdateSegmentPayload): Promise<Segment> {
    const res = await apiClient.put<Segment>(`/apps/${appId}/segments/${segmentId}`, payload)
    return res.data
  },

  async delete(appId: string, segmentId: string): Promise<void> {
    await apiClient.delete(`/apps/${appId}/segments/${segmentId}`)
  },

  async estimateAudience(appId: string, filterAst: FilterAst): Promise<AudienceEstimateResponse> {
    const res = await apiClient.post<AudienceEstimateResponse>(`/apps/${appId}/segments/estimate`, { filterAst })
    return res.data
  },

  async getTagKeys(appId: string): Promise<string[]> {
    const res = await apiClient.get<string[]>(`/apps/${appId}/segments/tag-keys`)
    return res.data
  }
}
