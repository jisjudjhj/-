import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useUserStore } from '../store/user'
import { runtimeConfig } from '../config/runtime'

const WS_ENDPOINT = runtimeConfig.wsBase

let client = null
let connected = false
let activeToken = ''
const pendingSubscriptions = new Map()
let subscriptionSeed = 0

const parseMessagePayload = frame => {
  const text = String(frame?.body || '').trim()
  if (!text) return {}
  try {
    return JSON.parse(text)
  } catch {
    return { raw: text }
  }
}

const attachSubscription = entry => {
  if (!connected || !client || !entry) {
    return
  }
  entry.runtimeSubscription?.unsubscribe?.()
  entry.runtimeSubscription = client.subscribe(entry.topic, frame => {
    entry.handler(parseMessagePayload(frame), frame)
  })
}

const rebuildSubscriptions = () => {
  pendingSubscriptions.forEach(entry => {
    attachSubscription(entry)
  })
}

const clearRuntimeSubscriptions = () => {
  pendingSubscriptions.forEach(entry => {
    entry.runtimeSubscription = null
  })
}

const createClient = token => {
  const stompClient = new Client({
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    webSocketFactory: () => new SockJS(WS_ENDPOINT),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
  })

  stompClient.onConnect = () => {
    connected = true
    rebuildSubscriptions()
  }

  stompClient.onDisconnect = () => {
    connected = false
    clearRuntimeSubscriptions()
  }

  stompClient.onWebSocketClose = () => {
    connected = false
    clearRuntimeSubscriptions()
  }

  stompClient.onWebSocketError = () => {
    connected = false
  }

  stompClient.onStompError = () => {
    connected = false
    stompClient.deactivate()
  }

  return stompClient
}

export const connectRealtime = () => {
  const userStore = useUserStore()
  const token = userStore.token
  if (!token) {
    disconnectRealtime()
    return
  }
  if (client && activeToken && activeToken !== token) {
    disconnectRealtime()
  }
  if (connected || client?.active) {
    return
  }
  activeToken = token
  client = createClient(token)
  client.activate()
}

export const disconnectRealtime = () => {
  if (!client) {
    return
  }
  try {
    client.deactivate()
  } finally {
    client = null
    connected = false
    activeToken = ''
    clearRuntimeSubscriptions()
  }
}

export const subscribeRealtime = (topic, handler) => {
  if (!topic || typeof handler !== 'function') {
    return () => {}
  }

  const id = `rt_${subscriptionSeed += 1}`
  const entry = {
    id,
    topic,
    handler,
    runtimeSubscription: null,
  }
  pendingSubscriptions.set(id, entry)
  attachSubscription(entry)

  return () => {
    const current = pendingSubscriptions.get(id)
    if (!current) {
      return
    }
    current.runtimeSubscription?.unsubscribe?.()
    pendingSubscriptions.delete(id)
  }
}

export const isRealtimeConnected = () => connected
