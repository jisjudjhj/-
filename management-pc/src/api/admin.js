import request from '../utils/request'
import {
  competitionRequestConfig,
  demoAnalysisSummary,
  demoDashboard,
  demoKmeansLatestTask,
  demoKmeansSegments,
  demoKmeansSummary,
  demoKmeansTaskHistory,
  demoKmeansUserDetail,
  demoKmeansUsers,
  demoRecommendAnalytics,
  demoSeckillActivities,
  demoSeckillApplications,
  demoSeckillDiagnostics,
  demoSeckillStressResult,
  withCompetitionFallback,
} from '../utils/competitionDemoData'

export function getAdminDashboard() {
  return withCompetitionFallback(
    request.get('/admin/dashboard', competitionRequestConfig()),
    demoDashboard
  )
}

export function getAlgorithmCategoryPreference(params) {
  return request.get('/admin/algorithm/category-preference', { params })
}

export function getRecommendationQualityCheck(params) {
  return request.get('/interest-commerce/audit', { params })
}

export function getUserSegmentsAnalysis() {
  return request.get('/interest-commerce/segments')
}

export function getInterestCommerceRecommendations(params) {
  return request.get('/interest-commerce/recommendations', { params })
}

export function getBusinessAnalysis() {
  return request.get('/admin/algorithm/business-analysis')
}

export function getAdminSystemHealth() {
  return request.get('/admin/system/health')
}

export function getAdminUsers(params) {
  return request.get('/admin/users', { params })
}

export function getAdminProfileChanges(params) {
  return request.get('/admin/profile-changes', { params })
}

export function getAdminWorkbenchBadgeCounts(params) {
  return request.get('/admin/workbench/badge-counts', { params })
}

export function approveAdminProfileChange(id) {
  return request.post(`/admin/profile-changes/${id}/approve`)
}

export function rejectAdminProfileChange(id, reason) {
  return request.post(`/admin/profile-changes/${id}/reject`, { reason })
}

export function updateAdminUserStatus(id, status) {
  return request.put(`/admin/users/${id}/status`, { status })
}

export function deleteAdminUser(id) {
  return request.delete(`/admin/users/${id}`)
}

export function getAdminProducts(params) {
  return request.get('/admin/products', { params })
}

export function getAdminBanners() {
  return request.get('/admin/banners')
}

export function createAdminBanner(payload) {
  return request.post('/admin/banners', payload)
}

export function updateAdminBanner(id, payload) {
  return request.put(`/admin/banners/${id}`, payload)
}

export function deleteAdminBanner(id) {
  return request.delete(`/admin/banners/${id}`)
}

export function createAdminProduct(payload) {
  return request.post('/products', payload)
}

export function updateAdminProduct(id, payload) {
  return request.put(`/products/${id}`, payload)
}

export function updateAdminProductStatus(id, status) {
  return request.put(`/admin/products/${id}/status`, { status })
}

export function deleteAdminProduct(id) {
  return request.delete(`/admin/products/${id}`)
}

export function getAdminOrders(params) {
  return request.get('/admin/orders', { params })
}

export function getAdminMerchants(params) {
  return request.get('/admin/merchants', { params })
}

export function createAdminMerchant(payload) {
  return request.post('/admin/merchants', payload)
}

export function getAdminMerchantStats() {
  return request.get('/admin/merchants/stats')
}

export function updateAdminOrderStatus(id, status) {
  return request.put(`/admin/orders/${id}/status`, { status })
}

export function createAdminCategory(payload) {
  return request.post('/admin/categories', payload)
}

export function updateAdminCategory(id, payload) {
  return request.put(`/admin/categories/${id}`, payload)
}

export function deleteAdminCategory(id) {
  return request.delete(`/admin/categories/${id}`)
}

export function getAdminCoupons(params) {
  return request.get('/admin/coupons', { params })
}

export function createAdminCoupon(payload) {
  return request.post('/admin/coupons', payload)
}

export function updateAdminCoupon(id, payload) {
  return request.put(`/admin/coupons/${id}`, payload)
}

export function deleteAdminCoupon(id) {
  return request.delete(`/admin/coupons/${id}`)
}

export function getAdminRefunds(params) {
  return request.get('/admin/refunds', { params })
}

export function getAdminReviews(params) {
  return request.get('/admin/reviews', { params })
}

export function updateAdminReviewStatus(id, status) {
  return request.put(`/admin/reviews/${id}/status`, { status })
}

export function deleteAdminReview(id) {
  return request.delete(`/admin/reviews/${id}`)
}

export function broadcastAdminMessage(payload) {
  return request.post('/admin/messages/broadcast', payload)
}

export function getAdminLogs(params) {
  return request.get('/admin/logs', { params })
}

export function getAdminRiskOverview() {
  return request.get('/admin/risk/overview')
}

export function getAdminRiskRules() {
  return request.get('/admin/risk/rules')
}

export function updateAdminRiskRule(routeId, payload) {
  return request.put(`/admin/risk/rules/${routeId}`, payload)
}

export function resetAdminRiskRule(routeId) {
  return request.post(`/admin/risk/rules/${routeId}/reset`)
}

export function getAdminRiskBlacklist(params) {
  return request.get('/admin/risk/blacklist', { params })
}

export function addAdminRiskBlacklist(payload) {
  return request.post('/admin/risk/blacklist', payload)
}

export function removeAdminRiskBlacklist(subjectType, subjectValue) {
  return request.delete('/admin/risk/blacklist', {
    params: { subjectType, subjectValue },
  })
}

export function getAdminModuleSwitches() {
  return request.get('/admin/module-switches')
}

export function getAdminRolePermissions() {
  return request.get('/admin/role-permissions')
}

export function getAdminCurrentRolePermissions() {
  return request.get('/admin/role-permissions/me')
}

export function updateAdminRolePermissions(role, permissions) {
  return request.put(`/admin/role-permissions/${role}`, { permissions })
}

export function resetAdminRolePermissions(role) {
  return request.put(`/admin/role-permissions/${role}/reset`)
}

export function getAdminModuleSwitchSummary() {
  return request.get('/admin/module-switches/summary')
}

export function updateAdminModuleSwitch(payload) {
  return request.put('/admin/module-switches', payload)
}

export function updateAdminModuleSwitchBatch(payload) {
  return request.put('/admin/module-switches/batch', payload)
}

// ==================== 钱包管理 ====================

export function getAdminWalletStats() {
  return request.get('/admin/wallet/stats')
}

export function getAdminWalletTransactions(params) {
  return request.get('/admin/wallet/transactions', { params })
}

export function getAdminUserBalances(params) {
  return request.get('/admin/wallet/user-balances', { params })
}

export function adjustAdminUserBalance(payload) {
  return request.post('/admin/wallet/adjust', payload)
}

export function getAdminSalesAnalytics() {
  return request.get('/admin/analytics/sales')
}

export function getAdminBehaviorAnalytics() {
  return request.get('/admin/analytics/behavior')
}

export function getAdminSearchBehaviorAnalytics(days = 14) {
  return request.get('/admin/analytics/search-behavior', {
    params: { days },
  })
}

export function getAdminRecommendAnalytics(days = 30) {
  return withCompetitionFallback(
    request.get('/admin/analytics/recommendation', competitionRequestConfig({
      params: { days },
    })),
    () => demoRecommendAnalytics(days)
  )
}

export function getAdminStreamOverview(params) {
  return request.get('/admin/stream/overview', { params })
}

export function getAdminStreamStatus() {
  return request.get('/admin/stream/status')
}

export function getAdminStreamUserSnapshot(userId) {
  return request.get(`/admin/stream/users/${userId}/snapshot`)
}

export function getAdminStreamHotProducts(params) {
  return request.get('/admin/stream/hot-products', { params })
}

export function getAdminStreamMonitor() {
  return request.get('/admin/stream/monitor')
}

// ==================== 大数据分析 ====================

export function getAnalysisSummary() {
  return withCompetitionFallback(
    request.get('/admin/analysis/summary', competitionRequestConfig()),
    demoAnalysisSummary
  )
}

export function getFunnelAnalysis() {
  return request.get('/admin/analysis/funnel')
}

export function getRfmAnalysis() {
  return request.get('/admin/analysis/rfm')
}

export function getAssociationRules(params) {
  return request.get('/admin/analysis/association', { params })
}

export function getRetentionAnalysis() {
  return request.get('/admin/analysis/retention')
}

export function getSalesTrendAnalysis() {
  return request.get('/admin/analysis/sales-trend')
}

export function getActivityHeatmap() {
  return request.get('/admin/analysis/heatmap')
}

// ==================== KMeans 用户分群分析 ====================

export function getAdminKmeansLatestTask() {
  return withCompetitionFallback(
    request.get('/admin/analysis/kmeans/latest-task', competitionRequestConfig()),
    demoKmeansLatestTask
  )
}

export function getAdminKmeansSummary() {
  return withCompetitionFallback(
    request.get('/admin/analysis/kmeans/summary', competitionRequestConfig()),
    demoKmeansSummary
  )
}

export function getAdminKmeansSegments() {
  return withCompetitionFallback(
    request.get('/admin/analysis/kmeans/segments', competitionRequestConfig()),
    demoKmeansSegments
  )
}

export function getAdminKmeansTaskHistory(params) {
  return withCompetitionFallback(
    request.get('/admin/analysis/kmeans/tasks', competitionRequestConfig({ params })),
    demoKmeansTaskHistory
  )
}

export function triggerAdminKmeansTask(payload) {
  const fallback = {
    ...demoKmeansLatestTask,
    id: Date.now(),
    batchNo: `KM-DEMO-${Date.now()}`,
    status: 'SUBMITTED',
    finalStatus: 'SUBMITTED',
  }
  return withCompetitionFallback(
    request.post('/admin/analysis/kmeans/tasks/trigger', payload, competitionRequestConfig()),
    fallback
  )
}

export function getAdminKmeansUsers(params) {
  return withCompetitionFallback(
    request.get('/admin/analysis/kmeans/users', competitionRequestConfig({ params })),
    demoKmeansUsers
  )
}

export function getAdminKmeansUserDetail(userId) {
  return withCompetitionFallback(
    request.get(`/admin/analysis/kmeans/user/${userId}`, competitionRequestConfig()),
    { ...demoKmeansUserDetail, userId }
  )
}

// ==================== 秒杀活动管理 ====================

export function getAdminSeckillActivities(params) {
  return withCompetitionFallback(
    request.get('/admin/seckill/activities', competitionRequestConfig({ params })),
    demoSeckillActivities
  )
}

export function getAdminSeckillDiagnostics() {
  return withCompetitionFallback(
    request.get('/admin/seckill/diagnostics', competitionRequestConfig()),
    demoSeckillDiagnostics
  )
}

export function runAdminSeckillStressTest(payload = {}) {
  return withCompetitionFallback(
    request.post('/admin/seckill/stress-test/run', payload, competitionRequestConfig({
    timeout: 120000,
    })),
    demoSeckillStressResult
  )
}

export function createAdminSeckillActivity(payload) {
  return request.post('/admin/seckill/activities', payload)
}

export function updateAdminSeckillActivity(id, payload) {
  return request.put(`/admin/seckill/activities/${id}`, payload)
}

export function toggleAdminSeckillActivityPublish(id, published) {
  return request.put(`/admin/seckill/activities/${id}/publish`, {
    published,
    publishStatus: published ? 1 : 0,
  })
}

export function getAdminSeckillApplications(params) {
  return withCompetitionFallback(
    request.get('/admin/seckill/applications', competitionRequestConfig({ params })),
    demoSeckillApplications
  )
}

export function approveAdminSeckillApplication(id) {
  return request.post(`/admin/seckill/applications/${id}/approve`)
}

export function rejectAdminSeckillApplication(id, reason) {
  return request.post(`/admin/seckill/applications/${id}/reject`, { reason })
}

export async function scheduleAdminSeckillApplication(id, payload) {
  try {
    return await request.post(`/admin/seckill/applications/${id}/schedule`, payload)
  } catch (error) {
    const status = error?.response?.status
    if (status === 404 || status === 405) {
      return request.post(`/admin/seckill/applications/${id}/arrange`, payload)
    }
    throw error
  }
}
