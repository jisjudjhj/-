const MODULE_DISABLED_CODE = 423
const DEDUPE_WINDOW_MS = 1500

const { getApiBaseUrl } = require('../config/env')
const cache = require('./cache')

let lastToastMessage = ''
let lastToastAt = 0

let isRedirectingToLogin = false
let loadingCount = 0
const pendingGetRequests = {}

function showGlobalLoading() {
  loadingCount += 1
  if (loadingCount === 1) {
    wx.showLoading({
      title: '加载中...',
      mask: true
    })
  }
}

function hideGlobalLoading() {
  if (loadingCount > 0) {
    loadingCount -= 1
  }
  if (loadingCount === 0) {
    wx.hideLoading()
  }
}

function showErrorToast(message) {
  const text = message || '请求失败'
  const now = Date.now()

  if (lastToastMessage === text && now - lastToastAt < DEDUPE_WINDOW_MS) {
    return
  }

  lastToastMessage = text
  lastToastAt = now

  wx.showToast({
    title: text,
    icon: 'none'
  })
}

function shouldShowErrorToast(options) {
  return options.showErrorToast !== false && options.silentError !== true
}

function shouldSkipAuthRedirect(options) {
  return options.skipAuthRedirect === true ||
    options.authOptional === true ||
    options.background === true ||
    options.silentAuth === true
}

function clearSession() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (app && typeof app.clearLoginState === 'function') {
    app.clearLoginState()
    return
  }

  wx.removeStorageSync('token')
  wx.removeStorageSync('userInfo')
  wx.removeStorageSync('couponPopupShown')
  wx.removeStorageSync('couponPopupState')
  wx.removeStorageSync('interestPopupState')
  wx.removeStorageSync('messageUnreadCount')
}

const toQueryString = (options) => {
  if (!options) {
    return ''
  }

  return Object.keys(options)
    .map((key) => `${key}=${encodeURIComponent(options[key])}`)
    .join('&')
}

const getCurrentRouteWithQuery = () => {
  const app = typeof getApp === 'function' ? getApp() : null
  if (app && typeof app.getCurrentRoute === 'function') {
    return app.getCurrentRoute()
  }

  if (typeof getCurrentPages !== 'function') {
    return '/pages/home/index'
  }

  const pages = getCurrentPages()
  if (!pages.length) {
    return '/pages/home/index'
  }

  const current = pages[pages.length - 1]
  const route = current ? `/${current.route}` : '/pages/home/index'
  const query = toQueryString(current.options || {})
  return query ? `${route}?${query}` : route
}

const navigateToLoginWithRedirect = (redirectRoute) => {
  if (isRedirectingToLogin) {
    return
  }

  const targetRoute = redirectRoute || '/pages/home/index'
  if (targetRoute === '/pages/login/index') {
    return
  }

  isRedirectingToLogin = true
  const app = typeof getApp === 'function' ? getApp() : null
  const url = `/pages/login/index?redirect=${encodeURIComponent(targetRoute)}`

  if (app && typeof app.navigateToPage === 'function') {
    app.navigateToPage(url).finally(() => {
      isRedirectingToLogin = false
    })
    return
  }

  wx.navigateTo({
    url,
    complete: () => {
      isRedirectingToLogin = false
    }
  })
}

/**
 * 封装全局网络请求
 * @param {Object} options 请求参数
 * @param {string} options.url 请求地址
 * @param {string} [options.method='GET'] 请求方法
 * @param {Object} [options.data] 请求数据
 * @param {Object} [options.header] 请求头
 * @param {boolean} [options.showLoading] 是否显示全局加载。GET 默认静默，写操作默认显示。
 * @returns {Promise}
 */
const request = (options) => {
  return new Promise((resolve, reject) => {
    const method = (options.method || 'GET').toUpperCase()
    const shouldShowLoading = options.showLoading === true ||
      (options.showLoading !== false && method !== 'GET' && options.background !== true)
    if (shouldShowLoading) {
      showGlobalLoading()
    }

    const token = wx.getStorageSync('token')
    const header = {
      'Content-Type': 'application/json',
      ...options.header
    }

    if (token) {
      header.Authorization = `Bearer ${token}`
    }

    wx.request({
      url: getApiBaseUrl() + options.url,
      method,
      data: options.data,
      timeout: options.timeout || 25000,
      header,
      success: (res) => {
        const data = res.data || {}
        if (res.statusCode === 200 && (data.code === 200 || data.code === 0)) {
          resolve(data.data !== undefined ? data.data : null)
        } else if (res.statusCode === 401 || (data && data.code === 401)) {
          if (shouldSkipAuthRedirect(options)) {
            reject(new Error('Unauthorized'))
            return
          }

          clearSession()
          showErrorToast('请先登录')

          const redirectRoute = getCurrentRouteWithQuery()
          navigateToLoginWithRedirect(redirectRoute)

          reject(new Error('Unauthorized'))
        } else {
          if (!shouldShowErrorToast(options)) {
            // 页面自行用空态或缓存兜底展示。
          } else if (data && data.code === MODULE_DISABLED_CODE) {
            showErrorToast(data.message || '该功能已关闭')
          } else {
            showErrorToast(data.message || '请求失败')
          }
          reject(data)
        }
      },
      fail: (err) => {
        if (shouldShowErrorToast(options)) {
          showErrorToast('网络异常，请稍后重试')
        }
        reject(err)
      },
      complete: () => {
        if (shouldShowLoading) {
          hideGlobalLoading()
        }
      }
    })
  })
}

request.get = (url, data, options = {}) => {
  const cacheTtl = Number(options.cacheTtl || 0)
  const useCache = cacheTtl > 0
  const tokenScope = options.cacheByUser ? (wx.getStorageSync('token') || 'guest') : 'public'
  const cacheKey = useCache
    ? (options.cacheKey || cache.createKey('get', url, data || {}, tokenScope))
    : ''

  if (useCache && !options.forceRefresh) {
    const cached = cache.get(cacheKey, cacheTtl)
    if (cached !== null) {
      return Promise.resolve(cached)
    }
  }

  const dedupeKey = cacheKey || cache.createKey('get', url, data || {}, tokenScope)
  if (pendingGetRequests[dedupeKey]) {
    return pendingGetRequests[dedupeKey]
  }

  pendingGetRequests[dedupeKey] = request({ url, method: 'GET', data, ...options })
    .then((res) => {
      if (useCache) {
        cache.set(cacheKey, res)
      }
      return res
    })
    .catch((error) => {
      if (useCache && options.allowStaleOnError !== false) {
        const stale = cache.get(cacheKey, 0, { allowExpired: true })
        if (stale !== null) {
          return stale
        }
      }
      throw error
    })
    .finally(() => {
      delete pendingGetRequests[dedupeKey]
    })

  return pendingGetRequests[dedupeKey]
}

request.post = (url, data, options = {}) => {
  return request({ url, method: 'POST', data, ...options })
}

request.put = (url, data, options = {}) => {
  return request({ url, method: 'PUT', data, ...options })
}

request.delete = (url, data, options = {}) => {
  return request({ url, method: 'DELETE', data, ...options })
}

request.cache = cache

module.exports = request
