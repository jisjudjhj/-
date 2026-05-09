import request from '../utils/request'

const buildRequestId = prefix => `${prefix || 'msg'}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`

export function getImConversations(params) {
  return request.get('/im/conversations', { params })
}

export function getImConversationUnreadCount() {
  return request.get('/im/conversations/unread-count')
}

export function getImConversationDetail(id) {
  return request.get(`/im/conversations/${id}`)
}

export function getImConversationMessages(id, params) {
  return request.get(`/im/conversations/${id}/messages`, { params })
}

export function openImMerchantConversation(payload) {
  return request.post('/im/conversations/open-merchant', payload)
}

export function openImSupportConversation(payload) {
  return request.post('/im/conversations/open-support', payload)
}

export function sendImMessage(id, payload) {
  const safePayload = {
    ...(payload || {}),
    requestId: payload && payload.requestId ? payload.requestId : buildRequestId('pc')
  }
  return request.post(`/im/conversations/${id}/messages`, safePayload)
}

export function markImConversationRead(id) {
  return request.put(`/im/conversations/${id}/read`)
}

export function updateImConversationStatus(id, payload = {}) {
  return request.put(`/im/conversations/${id}/status`, payload)
}

export function escalateImConversation(id, payload = {}) {
  return request.post(`/im/conversations/${id}/escalate`, payload)
}

export function getImSupportTickets(params) {
  return request.get('/im/support/tickets', { params })
}

export function getImSupportMetrics() {
  return request.get('/im/support/metrics')
}

export function assignImSupportTicket(id, payload = {}) {
  return request.put(`/im/support/tickets/${id}/assign`, payload)
}

export function updateImSupportTicketStatus(id, payload = {}) {
  return request.put(`/im/support/tickets/${id}/status`, payload)
}

export function getImSupportAgents() {
  return request.get('/im/support/agents')
}

export function saveImSupportAgent(payload) {
  return request.post('/im/support/agents', payload)
}
