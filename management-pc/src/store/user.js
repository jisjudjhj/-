import { defineStore } from 'pinia'

const safeJSON = (value, fallback = {}) => {
  if (!value) {
    return fallback
  }
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

const normalizeStoredString = value => {
  const text = String(value || '').trim()
  if (!text || text === 'undefined' || text === 'null') {
    return ''
  }
  return text
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: normalizeStoredString(localStorage.getItem('token')),
    userInfo: safeJSON(localStorage.getItem('userInfo'), {}),
    role: normalizeStoredString(localStorage.getItem('role')),
    sessionChecked: false,
  }),
  actions: {
    setToken(token) {
      const normalizedToken = normalizeStoredString(token)
      this.token = normalizedToken
      if (normalizedToken) {
        localStorage.setItem('token', normalizedToken)
      } else {
        localStorage.removeItem('token')
      }
    },
    setUserInfo(info = {}) {
      this.userInfo = info
      localStorage.setItem('userInfo', JSON.stringify(info))
      if (info?.role) {
        this.setRole(info.role)
      }
    },
    setRole(role) {
      const normalizedRole = normalizeStoredString(role)
      this.role = normalizedRole
      if (normalizedRole) {
        localStorage.setItem('role', normalizedRole)
      } else {
        localStorage.removeItem('role')
      }
    },
    setLoginData(payload) {
      this.setToken(payload?.token || '')
      this.setUserInfo(payload?.user || {})
      this.setRole(payload?.user?.role || '')
    },
    logout() {
      this.token = ''
      this.userInfo = {}
      this.role = ''
      this.sessionChecked = false
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('role')
    },
  }
})
