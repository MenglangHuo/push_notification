import { apiClient } from './client'

export interface ProviderStat {
  provider: string
  sentCount: number
  failedCount: number
  avgLatencyMs: number
}

export interface AnalyticsOverview {
  totalSent: number
  totalDelivered: number
  totalOpened: number
  totalFailed: number
  deliveryRate: number
  openRate: number
  avgLatencyMs: number
  activeDevices: number
  platformBreakdown: Record<string, number>
  providerBreakdown: ProviderStat[]
}

export interface TimeSeriesPoint {
  timestamp: string
  sent: number
  delivered: number
  opened: number
  failed: number
}

export interface TimeSeriesResponse {
  points: TimeSeriesPoint[]
}

export async function getAnalyticsOverview(appId: string, range: string = '7d'): Promise<AnalyticsOverview> {
  const response = await apiClient.get<AnalyticsOverview>(`/apps/${appId}/analytics/overview`, {
    params: { range },
  })
  return response.data
}

export async function getAnalyticsTimeSeries(appId: string, range: string = '7d'): Promise<TimeSeriesResponse> {
  const response = await apiClient.get<TimeSeriesResponse>(`/apps/${appId}/analytics/time-series`, {
    params: { range },
  })
  return response.data
}
