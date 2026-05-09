import request from '../utils/request'

export const AI_REQUEST_TIMEOUT = 90000

export function getAiConfig() {
  return request.get('/ai/admin/config')
}

export function testAiConnection(message) {
  return request.post('/ai/admin/test', { message }, { timeout: AI_REQUEST_TIMEOUT })
}

export function getAiReviewSummary(productId) {
  return request.get(`/ai/review-summary/${productId}`, { timeout: AI_REQUEST_TIMEOUT })
}

export function askAiProductQA(productId, question) {
  return request.post('/ai/product-qa', { productId, question }, { timeout: AI_REQUEST_TIMEOUT })
}

export function getAiModuleSwitches() {
  return request.get('/admin/module-switches')
}

export function updateAiModuleSwitch(module, enabled) {
  return request.put('/admin/module-switches', { module, enabled })
}

export function sendAiChat(message, history = []) {
  return request.post('/ai/chat', { message, history }, { timeout: AI_REQUEST_TIMEOUT })
}
