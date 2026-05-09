import request from '../utils/request'

export function getRefundDetail(id) {
  return request.get(`/refunds/${id}`)
}

export function approveRefund(id) {
  return request.post(`/refunds/${id}/approve`)
}

export function rejectRefund(id, rejectReason) {
  return request.post(`/refunds/${id}/reject`, { rejectReason })
}

export function resolveRefundIntervention(id, approved, reason) {
  return request.post(`/refunds/${id}/resolve-intervention`, { approved, reason })
}

export function getRefundCompetitionStats() {
  return request.get('/refunds/statistics/competition')
}
