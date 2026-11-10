import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface RealtimeNotification {
  notificationId: string
  appId?: string
  title: string
  body: string
  imageUrl?: string
  deepLink?: string
  customData?: Record<string, any>
  priority?: string
  timestamp: number
}

export interface InboundAck {
  action: string
  notificationId?: string
  serverTimestamp: number
  status: string
  message: string
}

export const useRealtimeStore = defineStore('realtime', () => {
  const isConnected = ref(false)
  const notifications = ref<RealtimeNotification[]>([])
  const unreadCount = ref(0)
  const acks = ref<InboundAck[]>([])

  let socket: WebSocket | null = null
  let heartbeatInterval: any = null
  let reconnectTimeout: any = null
  let currentToken: string | undefined = undefined
  let currentAppId: string | undefined = undefined

  function connect(token?: string, appId?: string) {
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return
    }

    currentToken = token
    currentAppId = appId

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    // Connect to backend port 8080 or current host
    const host = window.location.hostname === 'localhost' ? 'localhost:8080' : window.location.host
    const wsUrl = `${protocol}//${host}/ws`

    try {
      socket = new WebSocket(wsUrl)
    } catch (e) {
      console.error('[STOMP] WebSocket initialization failed:', e)
      scheduleReconnect()
      return
    }

    socket.onopen = () => {
      // Send STOMP CONNECT frame
      let headers = 'accept-version:1.2\nhost:/\n'
      if (currentToken) {
        headers += `Authorization:Bearer ${currentToken}\n`
      }
      if (currentAppId) {
        headers += `X-App-Id:${currentAppId}\n`
      }
      const connectFrame = `CONNECT\n${headers}\n\0`
      socket?.send(connectFrame)
    }

    socket.onmessage = (event) => {
      handleStompFrame(event.data)
    }

    socket.onclose = () => {
      isConnected.value = false
      clearInterval(heartbeatInterval)
      scheduleReconnect()
    }

    socket.onerror = (err) => {
      console.warn('[STOMP] Socket error:', err)
      socket?.close()
    }
  }

  function handleStompFrame(raw: string) {
    if (!raw) return
    const lines = raw.split('\n')
    const command = lines[0].trim()

    if (command === 'CONNECTED') {
      isConnected.value = true
      subscribeChannels()
      startHeartbeat()
      return
    }

    if (command === 'MESSAGE') {
      // Find empty line that separates headers and body
      const separatorIdx = raw.indexOf('\n\n')
      if (separatorIdx === -1) return
      let body = raw.substring(separatorIdx + 2)
      if (body.endsWith('\0')) {
        body = body.substring(0, body.length - 1)
      }

      try {
        const payload = JSON.parse(body)
        if (payload.action && (payload.action === 'PONG' || payload.action === 'ACK')) {
          acks.value.unshift(payload as InboundAck)
          if (acks.value.length > 20) acks.value.pop()
        } else if (payload.title || payload.notificationId) {
          notifications.value.unshift(payload as RealtimeNotification)
          unreadCount.value++
          if (notifications.value.length > 50) notifications.value.pop()
        }
      } catch (err) {
        console.warn('[STOMP] Failed to parse message body:', body)
      }
    }
  }

  function subscribeChannels() {
    if (!socket || socket.readyState !== WebSocket.OPEN) return

    // 1. Subscribe to personal queue
    socket.send(`SUBSCRIBE\nid:sub-user-notifs\ndestination:/user/queue/notifications\n\n\0`)
    // 2. Subscribe to personal inbound acks
    socket.send(`SUBSCRIBE\nid:sub-user-ack\ndestination:/user/queue/inbound-ack\n\n\0`)

    // 3. Subscribe to App announcements & live-feed if appId is active
    if (currentAppId) {
      socket.send(`SUBSCRIBE\nid:sub-app-announcements\ndestination:/topic/apps/${currentAppId}/announcements\n\n\0`)
      socket.send(`SUBSCRIBE\nid:sub-app-feed\ndestination:/topic/apps/${currentAppId}/live-feed\n\n\0`)
    }
  }

  function startHeartbeat() {
    clearInterval(heartbeatInterval)
    heartbeatInterval = setInterval(() => {
      sendInbound('PING')
    }, 25000)
  }

  function sendInbound(action: string, payload?: any) {
    if (!socket || socket.readyState !== WebSocket.OPEN) return

    const body = JSON.stringify({
      action,
      clientTimestamp: Date.now(),
      payload: payload || {},
    })

    const frame = `SEND\ndestination:/app/inbound\ncontent-type:application/json\n\n${body}\0`
    socket.send(frame)
  }

  function scheduleReconnect() {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = setTimeout(() => {
      if (!isConnected.value && currentToken) {
        connect(currentToken, currentAppId)
      }
    }, 5000)
  }

  function disconnect() {
    clearTimeout(reconnectTimeout)
    clearInterval(heartbeatInterval)
    if (socket) {
      socket.close()
      socket = null
    }
    isConnected.value = false
  }

  function markAllRead() {
    unreadCount.value = 0
  }

  function clear() {
    notifications.value = []
    unreadCount.value = 0
    acks.value = []
  }

  return {
    isConnected,
    notifications,
    unreadCount,
    acks,
    connect,
    disconnect,
    sendInbound,
    markAllRead,
    clear,
  }
})
