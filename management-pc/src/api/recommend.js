import request from '../utils/request'

export function getRecommendPreview(userId, limit = 10) {
  if (!userId || userId === 'undefined') return Promise.reject(new Error('缺少用户 ID'))
  return request.get(`/admin/recommend/preview/${userId}`, { params: { limit } })
}

export function getRecommendRealtime(userId, limit = 10) {
  if (!userId || userId === 'undefined') return Promise.reject(new Error('缺少用户 ID'))
  return request.get(`/admin/recommend/realtime/${userId}`, { params: { limit } })
}

export function getRecommendCompare(userId, limit = 10) {
  if (!userId || userId === 'undefined') return Promise.reject(new Error('缺少用户 ID'))
  return request.get(`/admin/recommend/compare/${userId}`, { params: { limit } })
}

export function getUserProfile(userId) {
  if (!userId || userId === 'undefined') return Promise.reject(new Error('缺少用户 ID'))
  return request.get(`/admin/recommend/user-profile/${userId}`)
}

export function getABTestReport() {
  return request.get('/admin/recommend/abtest-report')
}

export function resetABTest() {
  return request.post('/admin/recommend/abtest-reset')
}
