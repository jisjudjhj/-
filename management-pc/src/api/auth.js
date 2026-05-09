import request from '../utils/request'
import { competitionMode, demoAdminUser, isCompetitionDemoToken } from '../utils/competitionDemoData'

export function getCaptcha() {
  return request.get('/captcha', {
    skipErrorNotify: true,
  })
}

export function loginByPassword(payload) {
  return request.post('/auth/login', {
    loginType: 'password',
    ...payload,
  })
}

export function getCurrentUser() {
  if (competitionMode && isCompetitionDemoToken()) {
    return Promise.resolve(demoAdminUser)
  }
  return request.get('/auth/me')
}

export function getProfileChangeStatus() {
  return request.get('/auth/profile/change-status')
}

export function updateProfile(payload) {
  return request.put('/auth/profile', payload)
}

export function changePassword(payload) {
  return request.put('/auth/password', payload)
}
