const request = require('./request')

function parsePositiveNumber(value) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return null
  }
  return Math.floor(numeric)
}

function normalizePage(payload) {
  if (!payload || typeof payload !== 'object') {
    return { records: [], total: 0, current: 1, size: 0 }
  }

  return {
    records: Array.isArray(payload.records) ? payload.records : [],
    total: Number(payload.total || 0),
    current: Number(payload.current || 1),
    size: Number(payload.size || 0)
  }
}

function buildRequestId(prefix) {
  return `${prefix || 'msg'}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

function getConversationList(params = {}, options = {}) {
  return request.get('/im/conversations', params, {
    showLoading: options.showLoading === true
  })
}

function getConversationUnreadCount(options = {}) {
  return request.get('/im/conversations/unread-count', {}, {
    showLoading: options.showLoading === true
  }).then((res) => ({
    count: Number(res && res.count ? res.count : 0),
    conversationCount: Number(res && res.conversationCount ? res.conversationCount : 0)
  }))
}

function getConversationDetail(conversationId, options = {}) {
  const id = parsePositiveNumber(conversationId)
  if (!id) {
    return Promise.reject(new Error('会话不存在'))
  }
  return request.get(`/im/conversations/${id}`, {}, {
    showLoading: options.showLoading === true
  })
}

function getConversationMessages(conversationId, params = {}, options = {}) {
  const id = parsePositiveNumber(conversationId)
  if (!id) {
    return Promise.reject(new Error('会话不存在'))
  }
  return request.get(`/im/conversations/${id}/messages`, params, {
    showLoading: options.showLoading === true
  })
}

function openMerchantConversation(payload = {}, options = {}) {
  return request.post('/im/conversations/open-merchant', payload, {
    showLoading: options.showLoading !== false
  })
}

function openSupportConversation(payload = {}, options = {}) {
  return request.post('/im/conversations/open-support', payload, {
    showLoading: options.showLoading !== false
  })
}

function sendConversationMessage(conversationId, payload = {}, options = {}) {
  const id = parsePositiveNumber(conversationId)
  if (!id) {
    return Promise.reject(new Error('会话不存在'))
  }
  const safePayload = {
    ...payload,
    requestId: payload && payload.requestId ? payload.requestId : buildRequestId('wx')
  }
  return request.post(`/im/conversations/${id}/messages`, safePayload, {
    showLoading: options.showLoading !== false
  })
}

function markConversationRead(conversationId, options = {}) {
  const id = parsePositiveNumber(conversationId)
  if (!id) {
    return Promise.reject(new Error('会话不存在'))
  }
  return request.put(`/im/conversations/${id}/read`, {}, {
    showLoading: options.showLoading === true
  })
}

function escalateConversation(conversationId, payload = {}, options = {}) {
  const id = parsePositiveNumber(conversationId)
  if (!id) {
    return Promise.reject(new Error('会话不存在'))
  }
  return request.post(`/im/conversations/${id}/escalate`, payload, {
    showLoading: options.showLoading !== false
  })
}

function requestHumanSupport(conversationId, payload = {}, options = {}) {
  const id = parsePositiveNumber(conversationId)
  if (!id) {
    return Promise.reject(new Error('会话不存在'))
  }
  return request.post(`/im/conversations/${id}/request-human-support`, payload, {
    showLoading: options.showLoading !== false
  })
}

module.exports = {
  normalizePage,
  parsePositiveNumber,
  getConversationList,
  getConversationUnreadCount,
  getConversationDetail,
  getConversationMessages,
  openMerchantConversation,
  openSupportConversation,
  sendConversationMessage,
  markConversationRead,
  escalateConversation,
  requestHumanSupport
}
