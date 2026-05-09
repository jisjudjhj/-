const { getApiBaseUrl } = require('../config/env')

const FRAME_END = '\u0000'
const HEARTBEAT_MS = 10000
const RECONNECT_DELAY_MS = 4000
const CONNECT_TIMEOUT_MS = 12000

let socketTask = null
let connecting = false
let socketOpened = false
let stompConnected = false
let shouldReconnect = true
let frameBuffer = ''
let reconnectTimer = null
let heartbeatTimer = null
let connectTimeoutTimer = null
let subscriptionSeed = 0

const subscriptions = new Map()

function getToken() {
  try {
    return `${wx.getStorageSync('token') || ''}`.trim()
  } catch (error) {
    return ''
  }
}

function resolveWsUrl() {
  const apiBase = `${getApiBaseUrl() || ''}`.trim().replace(/\/+$/, '')
  if (!apiBase) {
    return ''
  }
  const withoutApi = apiBase.replace(/\/api$/i, '')
  if (/^https:\/\//i.test(withoutApi)) {
    return withoutApi.replace(/^https:\/\//i, 'wss://') + '/ws'
  }
  if (/^http:\/\//i.test(withoutApi)) {
    return withoutApi.replace(/^http:\/\//i, 'ws://') + '/ws'
  }
  return ''
}

function decodeSocketData(rawData) {
  if (typeof rawData === 'string') {
    return rawData
  }
  if (rawData instanceof ArrayBuffer) {
    try {
      if (typeof TextDecoder !== 'undefined') {
        return new TextDecoder('utf-8').decode(rawData)
      }
    } catch (error) {}
    const bytes = new Uint8Array(rawData)
    let text = ''
    for (let i = 0; i < bytes.length; i += 1) {
      text += String.fromCharCode(bytes[i])
    }
    return text
  }
  return ''
}

function parseStompFrame(frameText) {
  const normalized = `${frameText || ''}`.replace(/\r/g, '')
  if (!normalized.trim()) {
    return null
  }
  const lines = normalized.split('\n')
  const command = `${lines.shift() || ''}`.trim()
  if (!command) {
    return null
  }

  const headers = {}
  let bodyStartIndex = -1
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    if (line === '') {
      bodyStartIndex = index + 1
      break
    }
    const separatorIndex = line.indexOf(':')
    if (separatorIndex <= 0) {
      continue
    }
    const key = line.slice(0, separatorIndex).trim()
    const value = line.slice(separatorIndex + 1).trim()
    headers[key] = value
  }

  const body = bodyStartIndex >= 0 ? lines.slice(bodyStartIndex).join('\n') : ''
  return { command, headers, body }
}

function parsePayload(body) {
  const text = `${body || ''}`.trim()
  if (!text) {
    return {}
  }
  try {
    return JSON.parse(text)
  } catch (error) {
    return { raw: text }
  }
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function clearConnectTimeoutTimer() {
  if (connectTimeoutTimer) {
    clearTimeout(connectTimeoutTimer)
    connectTimeoutTimer = null
  }
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (!stompConnected || !socketTask) {
      return
    }
    try {
      socketTask.send({ data: '\n' })
    } catch (error) {}
  }, HEARTBEAT_MS)
}

function clearConnectionState() {
  connecting = false
  socketOpened = false
  stompConnected = false
  frameBuffer = ''
  stopHeartbeat()
  clearConnectTimeoutTimer()
  socketTask = null
}

function scheduleReconnect() {
  clearReconnectTimer()
  if (!shouldReconnect || !getToken()) {
    return
  }
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectRealtime()
  }, RECONNECT_DELAY_MS)
}

function sendFrame(command, headers = {}, body = '') {
  if (!socketTask || !socketOpened) {
    return false
  }
  const headerLines = Object.keys(headers)
    .filter((key) => headers[key] != null && `${headers[key]}` !== '')
    .map((key) => `${key}:${headers[key]}`)
  const frame = `${command}\n${headerLines.join('\n')}\n\n${body || ''}${FRAME_END}`
  try {
    socketTask.send({ data: frame })
    return true
  } catch (error) {
    return false
  }
}

function sendConnectFrame(token) {
  sendFrame('CONNECT', {
    'accept-version': '1.2',
    'heart-beat': `${HEARTBEAT_MS},${HEARTBEAT_MS}`,
    Authorization: `Bearer ${token}`
  })
}

function sendSubscribeFrame(entry) {
  if (!entry) {
    return
  }
  sendFrame('SUBSCRIBE', {
    id: entry.id,
    destination: entry.topic,
    ack: 'auto'
  })
}

function sendUnsubscribeFrame(id) {
  if (!id) {
    return
  }
  sendFrame('UNSUBSCRIBE', { id })
}

function dispatchMessage(frame) {
  const payload = parsePayload(frame.body)
  const subscriptionId = `${frame.headers.subscription || ''}`.trim()
  const destination = `${frame.headers.destination || ''}`.trim()
  const entry = subscriptionId ? subscriptions.get(subscriptionId) : null

  if (entry && typeof entry.handler === 'function') {
    try {
      entry.handler(payload, frame)
    } catch (error) {}
    return
  }

  subscriptions.forEach((item) => {
    if (item.topic !== destination || typeof item.handler !== 'function') {
      return
    }
    try {
      item.handler(payload, frame)
    } catch (error) {}
  })
}

function trimLeadingHeartbeats() {
  while (frameBuffer.startsWith('\n')) {
    frameBuffer = frameBuffer.slice(1)
  }
}

function processFrames() {
  trimLeadingHeartbeats()
  let endIndex = frameBuffer.indexOf(FRAME_END)
  while (endIndex >= 0) {
    const frameText = frameBuffer.slice(0, endIndex).replace(/^\n+/, '')
    frameBuffer = frameBuffer.slice(endIndex + 1)
    const frame = parseStompFrame(frameText)
    if (frame) {
      if (frame.command === 'CONNECTED') {
        clearConnectTimeoutTimer()
        connecting = false
        stompConnected = true
        startHeartbeat()
        subscriptions.forEach((entry) => sendSubscribeFrame(entry))
      } else if (frame.command === 'MESSAGE') {
        dispatchMessage(frame)
      } else if (frame.command === 'ERROR') {
        try {
          socketTask && socketTask.close({ code: 4000, reason: 'stomp-error' })
        } catch (error) {}
      }
    }
    trimLeadingHeartbeats()
    endIndex = frameBuffer.indexOf(FRAME_END)
  }
}

function bindSocketEvents(token) {
  if (!socketTask) {
    return
  }
  socketTask.onOpen(() => {
    socketOpened = true
    sendConnectFrame(token)
    clearConnectTimeoutTimer()
    connectTimeoutTimer = setTimeout(() => {
      connectTimeoutTimer = null
      if (!stompConnected && socketTask) {
        try {
          socketTask.close({ code: 4001, reason: 'connect-timeout' })
        } catch (error) {}
      }
    }, CONNECT_TIMEOUT_MS)
  })

  socketTask.onMessage((event) => {
    const chunk = decodeSocketData(event && event.data)
    if (!chunk) {
      return
    }
    frameBuffer += chunk
    trimLeadingHeartbeats()
    processFrames()
  })

  socketTask.onClose(() => {
    clearConnectionState()
    scheduleReconnect()
  })

  socketTask.onError(() => {
    clearConnectionState()
    scheduleReconnect()
  })
}

function connectRealtime() {
  shouldReconnect = true
  if (stompConnected || connecting || socketTask) {
    return
  }
  if (typeof wx !== 'object' || typeof wx.connectSocket !== 'function') {
    return
  }
  const token = getToken()
  const wsUrl = resolveWsUrl()
  if (!token || !wsUrl) {
    return
  }

  clearReconnectTimer()
  connecting = true
  frameBuffer = ''
  try {
    socketTask = wx.connectSocket({
      url: wsUrl,
      protocols: ['v12.stomp', 'v11.stomp']
    })
    bindSocketEvents(token)
  } catch (error) {
    clearConnectionState()
    scheduleReconnect()
  }
}

function disconnectRealtime() {
  shouldReconnect = false
  clearReconnectTimer()
  clearConnectTimeoutTimer()
  stopHeartbeat()
  if (socketTask) {
    try {
      if (stompConnected) {
        sendFrame('DISCONNECT', { receipt: `bye_${Date.now()}` })
      }
      socketTask.close({ code: 1000, reason: 'manual-close' })
    } catch (error) {}
  }
  clearConnectionState()
}

function subscribeRealtime(topic, handler) {
  if (!topic || typeof handler !== 'function') {
    return () => {}
  }
  const id = `wx_rt_${subscriptionSeed += 1}`
  const entry = { id, topic, handler }
  subscriptions.set(id, entry)
  if (stompConnected) {
    sendSubscribeFrame(entry)
  } else {
    connectRealtime()
  }

  return () => {
    const current = subscriptions.get(id)
    if (!current) {
      return
    }
    subscriptions.delete(id)
    if (stompConnected) {
      sendUnsubscribeFrame(id)
    }
  }
}

function isRealtimeConnected() {
  return stompConnected
}

module.exports = {
  connectRealtime,
  disconnectRealtime,
  subscribeRealtime,
  isRealtimeConnected
}
