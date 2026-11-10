import { apiClient } from './client'

export interface CreateNotificationPayload {
  title: string
  body: string
  imageUrl?: string
  deepLink?: string
  customData?: Record<string, unknown>
  priority?: 'HIGH' | 'NORMAL'
  targetType?: 'ALL' | 'DEVICE_IDS' | 'EXTERNAL_USER_IDS' | 'SEGMENT'
  targetPayload?: Record<string, unknown>
}

export interface NotificationSummary {
  id: string
  appId: string
  title: string
  body: string
  imageUrl?: string
  deepLink?: string
  customData: Record<string, unknown>
  priority: 'HIGH' | 'NORMAL'
  targetType: 'ALL' | 'DEVICE_IDS' | 'EXTERNAL_USER_IDS' | 'SEGMENT'
  targetPayload: Record<string, unknown>
  status: 'DRAFT' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  idempotencyKey?: string
  sentCount: number
  deliveredCount: number
  failedCount: number
  openedCount: number
  createdAt: string
  updatedAt: string
}

export interface DeliveryLogSummary {
  id: string
  notificationId: string
  deviceId?: string
  appId: string
  provider: 'FCM' | 'APNS' | 'WEB_PUSH'
  status: 'SUCCESS' | 'FAILED' | 'RETRYING'
  statusCode?: string
  errorMessage?: string
  latencyMs?: number
  createdAt: string
}

export const notificationsApi = {
  async list(appId: string, params?: { page?: number; size?: number }) {
    const res = await apiClient.get<{
      content: NotificationSummary[]
      totalElements: number
      totalPages: number
      number: number
    }>(`/apps/${appId}/notifications`, { params })
    return res.data
  },

  async get(appId: string, notificationId: string): Promise<NotificationSummary> {
    const res = await apiClient.get<NotificationSummary>(`/apps/${appId}/notifications/${notificationId}`)
    return res.data
  },

  async create(appId: string, payload: CreateNotificationPayload, idempotencyKey?: string): Promise<NotificationSummary> {
    const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
    const res = await apiClient.post<NotificationSummary>(`/apps/${appId}/notifications`, payload, { headers })
    return res.data
  },

  async listLogs(appId: string, notificationId: string, params?: { page?: number; size?: number }) {
    const res = await apiClient.get<{
      content: DeliveryLogSummary[]
      totalElements: number
      totalPages: number
      number: number
    }>(`/apps/${appId}/notifications/${notificationId}/logs`, { params })
    return res.data
  },
}
