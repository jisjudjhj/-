import request from '../utils/request'

const AI_REQUEST_TIMEOUT = 90000

export function getMerchantDashboard() {
  return request.get('/merchant/dashboard')
}

export function getMerchantWorkbenchBadgeCounts(params) {
  return request.get('/merchant/workbench/badge-counts', { params })
}

export function getMerchantProducts(params) {
  return request.get('/merchant/products', { params })
}

export function createMerchantProduct(payload) {
  return request.post('/merchant/products', payload)
}

export function updateMerchantProduct(id, payload) {
  return request.put(`/merchant/products/${id}`, payload)
}

export function deleteMerchantProduct(id) {
  return request.delete(`/merchant/products/${id}`)
}

export function updateMerchantProductStatus(id, status) {
  return request.put(`/merchant/products/${id}/status`, { status })
}

export function sendMerchantAiChat(message, history = [], draft = {}) {
  return request.post('/merchant/ai/chat', { message, history, draft }, { timeout: AI_REQUEST_TIMEOUT })
}

export function generateMerchantAiProductCopy(draft = {}) {
  return request.post('/merchant/ai/product-copy', { draft }, { timeout: AI_REQUEST_TIMEOUT })
}

export function getMerchantOrders(params) {
  return request.get('/merchant/orders', { params })
}

export function shipMerchantOrder(id) {
  return request.post(`/merchant/orders/${id}/ship`)
}

export function getMerchantReviews(params) {
  return request.get('/merchant/reviews', { params })
}

export function replyMerchantReview(id, reply) {
  return request.post(`/merchant/reviews/${id}/reply`, { reply })
}

export function getMerchantRefunds(params) {
  return request.get('/merchant/refunds', { params })
}

export function getMerchantProductStats(params) {
  return request.get('/merchant/products/stats', { params })
}

export function getMerchantFinanceStats() {
  return request.get('/merchant/finance/stats')
}

export function getMerchantFinanceDetails(params) {
  return request.get('/merchant/finance/details', { params })
}

export function getMerchantFinanceTrend() {
  return request.get('/merchant/finance/trend')
}

// ==================== 秒杀活动 ====================

export function getMerchantSeckillAvailableActivities(params) {
  return request.get('/merchant/seckill/activities/available', { params })
}

export function getMerchantSeckillActivityOptions(params) {
  return request.get('/merchant/seckill/activities/available', { params })
}

export function getMerchantSeckillApplications(params) {
  return request.get('/merchant/seckill/applications', { params })
}

export function createMerchantSeckillApplication(payload) {
  return request.post('/merchant/seckill/applications', payload)
}

export function updateMerchantSeckillApplication(id, payload) {
  return request.put(`/merchant/seckill/applications/${id}`, payload)
}

export function revokeMerchantSeckillApplication(id) {
  return request.delete(`/merchant/seckill/applications/${id}`)
}

export function getMerchantBehaviorAnalytics(params) {
  return request.get('/merchant/analytics/behavior', { params })
}

export function getMerchantBehaviorUsers(params) {
  return request.get('/merchant/analytics/users', { params })
}

export function getMerchantCoupons(params) {
  return request.get('/merchant/coupons', { params })
}

export function getMerchantCouponConversionDashboard(params) {
  return request.get('/merchant/coupons/conversion-dashboard', { params })
}

export function createMerchantCoupon(payload) {
  return request.post('/merchant/coupons', payload)
}

export function issueMerchantCoupon(couponId, payload) {
  return request.post(`/merchant/coupons/${couponId}/issue`, payload)
}

export function issueMerchantCouponByFilter(couponId, payload) {
  return request.post(`/merchant/coupons/${couponId}/issue-by-filter`, payload)
}

// ==================== 商家消息中心 ====================

export function getMerchantMessages(params) {
  return request.get('/messages', { params })
}

export function getMerchantMessageUnreadCount() {
  return request.get('/messages/unread-count')
}

export function markMerchantMessageRead(id) {
  return request.put(`/messages/${id}/read`)
}

export function markMerchantMessageReadAll() {
  return request.put('/messages/read-all')
}

export function deleteMerchantMessage(id) {
  return request.delete(`/messages/${id}`)
}
