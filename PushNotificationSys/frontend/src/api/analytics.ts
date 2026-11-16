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

// --- Dashboard Summary Types ---
export interface FunnelData {
  sent: number
  delivered: number
  opened: number
  clicked: number
}

export interface CampaignRanking {
  notificationId: string
  title: string
  sentCount: number
  deliveredCount: number
  openedCount: number
  deliveryRate: number
  openRate: number
}

export interface GrowthPoint {
  date: string
  newDevices: number
  churnedDevices: number
  netChange: number
}

export interface GrowthData {
  points: GrowthPoint[]
  totalNew: number
  totalChurned: number
  netGrowth: number
}

export interface FailureCategory {
  reason: string
  count: number
  percentage: number
}

export interface HeatmapCell {
  dayOfWeek: number
  hour: number
  value: number
}

export interface PeriodComparison {
  sentChange: number
  deliveredChange: number
  openRateChange: number
  latencyChange: number
}

export interface DashboardSummary {
  overview: AnalyticsOverview
  timeSeries: TimeSeriesResponse
  funnel: FunnelData
  topCampaigns: CampaignRanking[]
  growth: GrowthData
  failureBreakdown: FailureCategory[]
  heatmap: HeatmapCell[]
  periodComparison: PeriodComparison
}

export async function getDashboardSummary(appId: string, range: string = '7d'): Promise<DashboardSummary> {
  const response = await apiClient.get<DashboardSummary>(`/apps/${appId}/analytics/dashboard`, {
    params: { range },
  })
  return response.data
}
