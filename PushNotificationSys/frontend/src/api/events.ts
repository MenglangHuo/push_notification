import axios from 'axios'

export interface SdkEventItem {
  notificationId?: string
  deviceId?: string
  eventType: 'OPENED' | 'DELIVERED' | 'DISMISSED' | 'CLICKED'
  eventName?: string
  properties?: Record<string, any>
  timestamp?: string
}

export interface IngestEventsResponse {
  acceptedCount: number
  status: string
}

export const eventsApi = {
  async ingestEvents(events: SdkEventItem[], sdkKey?: string, appId?: string): Promise<IngestEventsResponse> {
    const effectiveAppId =
      appId ||
      (typeof localStorage !== 'undefined' ? localStorage.getItem('push_current_app_id') : null) ||
      undefined

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }
    if (sdkKey) {
      headers['X-Sdk-Key'] = sdkKey
    }

    const payload = {
      appId: effectiveAppId,
      events,
    }

    const res = await axios.post<IngestEventsResponse>('/sdk/v1/events', payload, { headers })
    return res.data
  },
}
