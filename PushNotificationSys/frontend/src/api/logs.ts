import { apiClient } from './client'

export interface DeliveryLogItem {
  id: string
  notificationId: string
  deviceId: string | null
  appId: string
  provider: string
  status: string
  statusCode: string | null
  errorMessage: string | null
  latencyMs: number | null
  createdAt: string
}

export interface ClientEventItem {
  id: string
  appId: string
  notificationId: string | null
  deviceId: string | null
  eventType: 'DELIVERED' | 'OPENED' | 'CLICKED' | 'CUSTOM'
  eventName: string | null
  properties: Record<string, any>
  clientTimestamp: string | null
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export async function getDeliveryLogs(
  appId: string,
  params: {
    provider?: string
    status?: string
    notificationId?: string
    page?: number
    size?: number
  } = {}
): Promise<PageResponse<DeliveryLogItem>> {
  const response = await apiClient.get<PageResponse<DeliveryLogItem>>(`/apps/${appId}/logs/delivery`, {
    params,
  })
  return response.data
}

export async function exportDeliveryLogsCsv(appId: string): Promise<Blob> {
  const response = await apiClient.get(`/apps/${appId}/logs/delivery/export`, {
    responseType: 'blob',
  })
  return response.data
}

export async function getClientEvents(
  appId: string,
  params: {
    notificationId?: string
    eventType?: string
    page?: number
    size?: number
  } = {}
): Promise<PageResponse<ClientEventItem>> {
  const response = await apiClient.get<PageResponse<ClientEventItem>>(`/apps/${appId}/logs/events`, {
    params,
  })
  return response.data
}

export async function exportAuditLogsCsv(orgId: string): Promise<Blob> {
  const response = await apiClient.get(`/organizations/${orgId}/audit-logs/export`, {
    responseType: 'blob',
  })
  return response.data
}
