const request = require('./request')

const EXPOSURE_DEDUPE_TTL_MS = 5 * 60 * 1000
const RETRY_STORAGE_KEY = 'recommendationEventRetryQueue'
const trackedExposureTokens = new Map()
let eventQueue = []
let flushTimer = null

function canTrack() {
  return !!wx.getStorageSync('token')
}

function normalizeScene(scene) {
  return scene || 'guess_you_like'
}

function loadRetryQueue() {
  try {
    const stored = wx.getStorageSync(RETRY_STORAGE_KEY)
    return Array.isArray(stored) ? stored : []
  } catch (error) {
    return []
  }
}

function saveRetryQueue(queue) {
  try {
    wx.setStorageSync(RETRY_STORAGE_KEY, Array.isArray(queue) ? queue.slice(-80) : [])
  } catch (error) {}
}

function normalizeEvent(payload = {}) {
  const eventType = payload.eventType || ''
  const isOrderOnlyEvent = eventType === 'order' || eventType === 'refund'
  if (!eventType || (!payload.productId && !isOrderOnlyEvent)) {
    return null
  }
  return {
    eventType,
    productId: payload.productId || undefined,
    scene: normalizeScene(payload.scene),
    recommendationToken: payload.recommendationToken || '',
    traceId: payload.traceId || '',
    duration: payload.duration || undefined,
    orderId: payload.orderId || undefined,
    amount: payload.amount || undefined,
    metadata: payload.metadata || undefined,
  }
}

function scheduleFlush() {
  if (flushTimer) return
  flushTimer = setTimeout(() => {
    flushTimer = null
    flushEvents()
  }, 600)
}

function flushEvents() {
  if (!canTrack()) {
    return Promise.resolve()
  }
  const retryQueue = loadRetryQueue()
  const events = retryQueue.concat(eventQueue).slice(-100)
  eventQueue = []
  if (!events.length) {
    return Promise.resolve()
  }
  return request.post('/recommendations/events/batch', events, { showLoading: false }).then(() => {
    saveRetryQueue([])
  }).catch(() => {
    saveRetryQueue(events)
  })
}

function trackEvent(payload = {}) {
  if (!canTrack()) {
    return Promise.resolve()
  }
  const event = normalizeEvent(payload)
  if (!event) {
    return Promise.resolve()
  }
  eventQueue.push(event)
  if (eventQueue.length >= 8) {
    return flushEvents()
  }
  scheduleFlush()
  return Promise.resolve()
}

function trackExposure(product, scene) {
  const token = product && product.recommendationToken
  const productId = product && product.id
  const now = Date.now()
  const lastTrackedAt = trackedExposureTokens.get(token)
  if (!token || !productId || (lastTrackedAt && now - lastTrackedAt < EXPOSURE_DEDUPE_TTL_MS)) {
    return Promise.resolve()
  }
  trackedExposureTokens.set(token, now)
  return trackEvent({
    eventType: 'exposure',
    productId,
    scene,
    recommendationToken: token,
  })
}

function trackExposures(products, scene) {
  if (!Array.isArray(products) || !products.length || !canTrack()) {
    return
  }
  products.forEach(product => {
    trackExposure(product, scene)
  })
}

function trackClick(productId, recommendationToken, scene) {
  return trackEvent({
    eventType: 'click',
    productId,
    scene,
    recommendationToken,
  })
}

function trackAddCart(productId, recommendationToken, scene) {
  return trackEvent({
    eventType: 'add_cart',
    productId,
    scene,
    recommendationToken,
  })
}

module.exports = {
  trackEvent,
  trackExposure,
  trackExposures,
  trackClick,
  trackAddCart,
  flushEvents,
  loadRetryQueue,
  saveRetryQueue,
  EXPOSURE_DEDUPE_TTL_MS,
}
