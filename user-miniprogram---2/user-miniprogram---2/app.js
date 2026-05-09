const request = require('./utils/request')
const { getSystemInfoCompat } = require('./utils/system-info')
const realtime = require('./utils/realtime')
const recommendationTracker = require('./utils/recommendation-tracker')

const MESSAGE_REFRESH_DEBOUNCE_MS = 220
const ROUTE_ALIAS_MAP = {
  'pages/checkout/index': 'package-orders/checkout/index',
  'pages/orders/index': 'package-orders/orders/index',
  'pages/order-detail/index': 'package-orders/order-detail/index',
  'pages/review-edit/index': 'package-orders/review-edit/index',
  'pages/refund-apply/index': 'package-orders/refund-apply/index',
  'pages/messages/index': 'package-user/messages/index',
  'pages/coupons/index': 'package-user/coupons/index',
  'pages/address/index': 'package-user/address/index',
  'pages/wallet/index': 'package-user/wallet/index',
  'pages/favorites/index': 'package-user/favorites/index',
  'pages/footprint/index': 'package-user/footprint/index',
  'pages/settings/index': 'package-user/settings/index',
  'pages/about/index': 'package-user/about/index',
  'pages/edit-profile/index': 'package-user/edit-profile/index',
  'pages/ai-assistant/index': 'package-features/ai-assistant/index',
  'pages/webview/index': 'package-features/webview/index'
}

function normalizePageUrl(url) {
  const raw = `${url || ''}`.trim()
  if (!raw) {
    return ''
  }

  const queryIndex = raw.indexOf('?')
  const rawPath = queryIndex >= 0 ? raw.slice(0, queryIndex) : raw
  const rawQuery = queryIndex >= 0 ? raw.slice(queryIndex) : ''
  const normalizedPath = rawPath.replace(/^\/+/, '')
  const aliasedPath = ROUTE_ALIAS_MAP[normalizedPath] || normalizedPath
  return `/${aliasedPath}${rawQuery}`
}

function getNavMetrics() {
  const systemInfo = getSystemInfoCompat()
  const statusBarHeight = systemInfo.statusBarHeight || 20
  let menuButtonInfo = null

  try {
    if (typeof wx.getMenuButtonBoundingClientRect === 'function') {
      const rect = wx.getMenuButtonBoundingClientRect()
      if (rect && rect.top && rect.bottom) {
        menuButtonInfo = rect
      }
    }
  } catch (err) {
    console.warn('[App] getMenuButtonBoundingClientRect failed:', err)
  }

  if (!menuButtonInfo) {
    const fallbackHeight = 32
    const fallbackGap = 8
    menuButtonInfo = {
      top: statusBarHeight + fallbackGap,
      bottom: statusBarHeight + fallbackGap + fallbackHeight,
      height: fallbackHeight,
      width: 88,
      right: (systemInfo.windowWidth || 375) - 12
    }
  }

  return {
    systemInfo,
    menuButtonInfo,
    navBarHeight: menuButtonInfo.bottom + menuButtonInfo.top - statusBarHeight
  }
}

function safeSetPageData(page, nextData) {
  if (!page || page._destroyed || typeof page.setData !== 'function' || !nextData) {
    return false
  }

  try {
    page.setData(nextData)
    return true
  } catch (err) {
    return false
  }
}

function formatRuntimeError(payload) {
  if (typeof payload === 'string') {
    return payload
  }

  if (!payload) {
    return ''
  }

  if (payload.reason) {
    return formatRuntimeError(payload.reason)
  }

  if (payload.message) {
    return `${payload.message}`
  }

  if (payload.errMsg) {
    return `${payload.errMsg}`
  }

  try {
    return JSON.stringify(payload)
  } catch (err) {
    return `${payload}`
  }
}

App({
  onError(msg) {
    try {
      this.globalData.lastRuntimeError = formatRuntimeError(msg)
    } catch (err) {}
  },

  onUnhandledRejection(event) {
    try {
      this.globalData.lastRuntimeError = formatRuntimeError(event && event.reason ? event.reason : event)
    } catch (err) {}

    try {
      if (event && typeof event.preventDefault === 'function') {
        event.preventDefault()
      }
    } catch (err) {}
  },

  onLaunch() {
    const { systemInfo, menuButtonInfo, navBarHeight } = getNavMetrics()

    this.globalData.systemInfo = systemInfo
    this.globalData.menuButtonInfo = menuButtonInfo
    this.globalData.navBarHeight = navBarHeight
    this.globalData.userInfo = wx.getStorageSync('userInfo') || null
    this.globalData.messageUnreadCount = Number(wx.getStorageSync('messageUnreadCount') || 0)
    this.globalData.cartCount = Number(wx.getStorageSync('cartCount') || 0)
    this.globalData.profileAlertCount = Number(wx.getStorageSync('profileAlertCount') || 0)
    this.globalData.profileAlertDetail = wx.getStorageSync('profileAlertDetail') || {
      couponAlertCount: 0,
      couponUnclaimedCount: 0,
      couponExpiringCount: 0,
      aftersaleCount: 0,
      unpaidCount: 0,
      unshippedCount: 0,
      unreceivedCount: 0,
      messageUnreadCount: 0
    }

    if (this.isLoggedIn()) {
      this.ensureRealtime()
      this.refreshMessageUnreadCount().catch(() => {})
      this.refreshCartCount().catch(() => {})
    }
  },

  onShow() {
    if (this.isLoggedIn()) {
      this.ensureRealtime()
    }
  },

  onHide() {
    recommendationTracker.flushEvents()
  },

  isLoggedIn() {
    return !!wx.getStorageSync('token')
  },

  getVisitorId() {
    const storageKey = 'guestVisitorId'
    let visitorId = wx.getStorageSync(storageKey)
    if (visitorId) {
      return visitorId
    }
    visitorId = `guest_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
    wx.setStorageSync(storageKey, visitorId)
    return visitorId
  },

  getCurrentRoute() {
    const pages = getCurrentPages()
    if (!pages.length) {
      return '/pages/home/index'
    }
    const current = pages[pages.length - 1]
    const route = `/${current.route}`
    const options = current.options || {}
    const query = Object.keys(options)
      .map((key) => `${key}=${encodeURIComponent(options[key])}`)
      .join('&')
    return query ? `${route}?${query}` : route
  },

  isTabBarPage(url) {
    const target = `${url || ''}`.split('?')[0]
    return [
      '/pages/home/index',
      '/pages/explore/index',
      '/pages/cart/index',
      '/pages/profile/index'
    ].includes(target)
  },

  _drainNavigationQueue() {
    this._navigationLock = false
    const pending = this._pendingNavigationUrl
    this._pendingNavigationUrl = ''
    if (pending && pending !== this._currentNavigatingUrl) {
      setTimeout(() => {
        this.navigateToPage(pending)
      }, 20)
    }
  },

  _notifyNavigationFailure(target) {
    try {
      wx.showToast({
        title: '页面打开失败，请重试',
        icon: 'none'
      })
    } catch (toastError) {}
    console.warn('[App] navigation failed:', target)
  },

  navigateToPage(url) {
    const target = normalizePageUrl(url)
    if (!target) {
      this._notifyNavigationFailure(target)
      return Promise.resolve(false)
    }

    if (this._navigationLock) {
      this._pendingNavigationUrl = target
      return Promise.resolve(true)
    }

    const baseUrl = target.split('?')[0]

    return new Promise((resolve) => {
      const finish = (result) => {
        if (!result) {
          this._notifyNavigationFailure(target)
        }
        this._currentNavigatingUrl = ''
        this._drainNavigationQueue()
        resolve(result)
      }

      this._navigationLock = true
      this._currentNavigatingUrl = target

      if (this.isTabBarPage(baseUrl)) {
        try {
          wx.switchTab({
            url: baseUrl,
            success: () => finish(true),
            fail: (error) => {
              console.warn('[App] switchTab failed, fallback to reLaunch:', error)
              try {
                wx.reLaunch({
                  url: baseUrl,
                  success: () => finish(true),
                  fail: (reLaunchError) => {
                    console.warn('[App] reLaunch failed:', reLaunchError)
                    finish(false)
                  }
                })
              } catch (reLaunchSyncError) {
                console.warn('[App] reLaunch sync error:', reLaunchSyncError)
                finish(false)
              }
            }
          })
        } catch (switchTabSyncError) {
          console.warn('[App] switchTab sync error:', switchTabSyncError)
          finish(false)
        }
        return
      }

      try {
        wx.navigateTo({
          url: target,
          success: () => finish(true),
          fail: (error) => {
            const errMsg = error && error.errMsg ? error.errMsg : ''
            if (/tabbar page/i.test(errMsg) || this.isTabBarPage(baseUrl)) {
              try {
                wx.switchTab({
                  url: baseUrl,
                  success: () => finish(true),
                  fail: () => finish(false)
                })
              } catch (tabSyncError) {
                console.warn('[App] switchTab sync error after navigateTo fail:', tabSyncError)
                finish(false)
              }
              return
            }

            console.warn('[App] navigateTo failed, fallback to redirectTo:', error)
            try {
              wx.redirectTo({
                url: target,
                success: () => finish(true),
                fail: (redirectError) => {
                  const redirectErrMsg = redirectError && redirectError.errMsg ? redirectError.errMsg : ''
                  if (/webview|page stack|limit|exceed/i.test(redirectErrMsg)) {
                    try {
                      wx.reLaunch({
                        url: target,
                        success: () => finish(true),
                        fail: () => finish(false)
                      })
                    } catch (reLaunchSyncError) {
                      console.warn('[App] reLaunch sync error after redirectTo fail:', reLaunchSyncError)
                      finish(false)
                    }
                    return
                  }
                  console.warn('[App] redirectTo failed:', redirectError)
                  finish(false)
                }
              })
            } catch (redirectSyncError) {
              console.warn('[App] redirectTo sync error:', redirectSyncError)
              finish(false)
            }
          }
        })
      } catch (navigateSyncError) {
        console.warn('[App] navigateTo sync error:', navigateSyncError)
        finish(false)
      }
    })
  },

  requireLogin(redirectUrl) {
    if (this.isLoggedIn()) {
      return true
    }

    const redirect = normalizePageUrl(redirectUrl || this.getCurrentRoute())
    this.navigateToPage(`/pages/login/index?redirect=${encodeURIComponent(redirect)}`)
    return false
  },

  ensureRealtime() {
    if (!this.isLoggedIn()) {
      return
    }
    if (!this._realtimeInitialized) {
      this._realtimeInitialized = true
      this._realtimeUnsubscribeUserMessage = realtime.subscribeRealtime(
        '/user/queue/user-message-refresh',
        (payload) => this.handleUserMessageRealtime(payload)
      )
      this._realtimeUnsubscribeIm = realtime.subscribeRealtime(
        '/user/queue/im-refresh',
        (payload) => this.handleImRealtime(payload)
      )
    }
    realtime.connectRealtime()
  },

  stopRealtime() {
    if (this._messageUnreadRefreshTimer) {
      clearTimeout(this._messageUnreadRefreshTimer)
      this._messageUnreadRefreshTimer = null
    }
    realtime.disconnectRealtime()
  },

  scheduleMessageUnreadRefresh() {
    if (this._messageUnreadRefreshTimer) {
      return
    }
    this._messageUnreadRefreshTimer = setTimeout(() => {
      this._messageUnreadRefreshTimer = null
      this.refreshMessageUnreadCount().catch(() => {})
    }, MESSAGE_REFRESH_DEBOUNCE_MS)
  },

  handleUserMessageRealtime(payload) {
    this.emitRealtimeEvent('user-message-refresh', payload || {})
    this.scheduleMessageUnreadRefresh()
  },

  handleImRealtime(payload) {
    this.emitRealtimeEvent('im-refresh', payload || {})
  },

  emitRealtimeEvent(eventName, payload) {
    const bucket = this._realtimeListeners && this._realtimeListeners[eventName]
    if (!bucket || !bucket.size) {
      return
    }
    bucket.forEach((handler) => {
      try {
        handler(payload || {})
      } catch (error) {}
    })
  },

  onRealtimeEvent(eventName, handler) {
    if (!eventName || typeof handler !== 'function') {
      return () => {}
    }
    if (!this._realtimeListeners) {
      this._realtimeListeners = {}
    }
    if (!this._realtimeListeners[eventName]) {
      this._realtimeListeners[eventName] = new Set()
    }
    const bucket = this._realtimeListeners[eventName]
    bucket.add(handler)
    return () => {
      if (!bucket.has(handler)) {
        return
      }
      bucket.delete(handler)
    }
  },

  setLoginState(data) {
    if (!data || !data.token) {
      return
    }
    wx.setStorageSync('token', data.token)
    if (data.user) {
      const userInfo = {
        id: data.user.id,
        nickName: data.user.nickname || data.user.username || '',
        avatarUrl: data.user.avatar || '',
        role: data.user.role || ''
      }
      wx.setStorageSync('userInfo', userInfo)
      this.globalData.userInfo = userInfo
    }

    this.ensureRealtime()
    this.refreshMessageUnreadCount().catch(() => {})
    this.refreshCartCount().catch(() => {})
    this.refreshProfileBadgeSummary().catch(() => {})
  },

  clearLoginState() {
    this.stopRealtime()
    if (request.cache && typeof request.cache.clearAll === 'function') {
      request.cache.clearAll()
    }
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
    wx.removeStorageSync('couponPopupShown')
    wx.removeStorageSync('messageUnreadCount')
    wx.removeStorageSync('cartCount')
    wx.removeStorageSync('profileAlertCount')
    wx.removeStorageSync('profileAlertDetail')
    this.globalData.userInfo = null
    this.syncMessageUnreadCount(0)
    this.syncCartCount(0)
    this.syncProfileAlertState({
      couponAlertCount: 0,
      couponUnclaimedCount: 0,
      couponExpiringCount: 0,
      aftersaleCount: 0,
      unpaidCount: 0,
      unshippedCount: 0,
      unreceivedCount: 0,
      messageUnreadCount: 0
    })
  },

  fetchCurrentUser() {
    return request.get('/auth/me', {}, { showLoading: false }).then((user) => {
      const userInfo = {
        id: user.id,
        nickName: user.nickname || user.username || '',
        avatarUrl: user.avatar || '',
        role: user.role || ''
      }
      wx.setStorageSync('userInfo', userInfo)
      this.globalData.userInfo = userInfo
      return userInfo
    })
  },

  updateUserProfile(profile) {
    return request
      .put(
        '/auth/profile',
        {
          nickname: profile.nickName,
          avatar: profile.avatarUrl
        },
        { showLoading: false }
      )
      .then(() => this.fetchCurrentUser())
  },

  getMessageUnreadCount() {
    return Math.max(0, Number(this.globalData.messageUnreadCount || 0))
  },

  getCartCount() {
    return Math.max(0, Number(this.globalData.cartCount || 0))
  },

  getProfileAlertCount() {
    return Math.max(0, Number(this.globalData.profileAlertCount || 0))
  },

  getProfileAlertDetail() {
    return this.globalData.profileAlertDetail || {
      couponAlertCount: 0,
      couponUnclaimedCount: 0,
      couponExpiringCount: 0,
      aftersaleCount: 0,
      unpaidCount: 0,
      unshippedCount: 0,
      unreceivedCount: 0,
      messageUnreadCount: 0
    }
  },

  syncMessageUnreadCount(count) {
    const unreadCount = Math.max(0, Number(count || 0))
    this.globalData.messageUnreadCount = unreadCount
    wx.setStorageSync('messageUnreadCount', unreadCount)
    this.applyMessageBadge()
    return unreadCount
  },

  syncCartCount(count) {
    const cartCount = Math.max(0, Number(count || 0))
    this.globalData.cartCount = cartCount
    wx.setStorageSync('cartCount', cartCount)
    this.applyGlobalBadges()
    return cartCount
  },

  syncProfileAlertState(detail = {}) {
    const nextDetail = {
      couponAlertCount: Math.max(0, Number(detail.couponAlertCount || 0)),
      couponUnclaimedCount: Math.max(0, Number(detail.couponUnclaimedCount || 0)),
      couponExpiringCount: Math.max(0, Number(detail.couponExpiringCount || 0)),
      aftersaleCount: Math.max(0, Number(detail.aftersaleCount || 0)),
      unpaidCount: Math.max(0, Number(detail.unpaidCount || 0)),
      unshippedCount: Math.max(0, Number(detail.unshippedCount || 0)),
      unreceivedCount: Math.max(0, Number(detail.unreceivedCount || 0)),
      messageUnreadCount: Math.max(0, Number(detail.messageUnreadCount != null ? detail.messageUnreadCount : this.getMessageUnreadCount()))
    }

    const profileAlertCount =
      nextDetail.messageUnreadCount +
      nextDetail.couponAlertCount +
      nextDetail.aftersaleCount

    this.globalData.profileAlertDetail = nextDetail
    this.globalData.profileAlertCount = profileAlertCount
    wx.setStorageSync('profileAlertDetail', nextDetail)
    wx.setStorageSync('profileAlertCount', profileAlertCount)
    this.applyGlobalBadges()
    return nextDetail
  },

  applyGlobalBadges() {
    const unreadCount = this.getMessageUnreadCount()
    const cartCount = this.getCartCount()
    const profileAlertCount = this.getProfileAlertCount()
    const profileAlertDetail = this.getProfileAlertDetail()
    const pages = getCurrentPages()
    const currentPage = pages[pages.length - 1]

    if (
      currentPage &&
      currentPage.data &&
      Object.prototype.hasOwnProperty.call(currentPage.data, 'unreadMessageCount')
    ) {
      safeSetPageData(currentPage, { unreadMessageCount: unreadCount })
    }

    if (
      currentPage &&
      currentPage.data &&
      Object.prototype.hasOwnProperty.call(currentPage.data, 'profileAlertCount')
    ) {
      safeSetPageData(currentPage, {
        profileAlertCount,
        profileAlertDetail
      })
    }

    if (currentPage && !currentPage._destroyed && typeof currentPage.getTabBar === 'function') {
      const tabBar = currentPage.getTabBar()
      if (tabBar && typeof tabBar.syncUnreadState === 'function') {
        tabBar.syncUnreadState(unreadCount)
      }
      if (tabBar && typeof tabBar.syncCartState === 'function') {
        tabBar.syncCartState(cartCount)
      }
      if (tabBar && typeof tabBar.syncProfileState === 'function') {
        tabBar.syncProfileState(profileAlertCount)
      }
    }

    return unreadCount
  },

  applyMessageBadge() {
    return this.applyGlobalBadges()
  },

  refreshMessageUnreadCount() {
    if (!this.isLoggedIn()) {
      return Promise.resolve(this.syncMessageUnreadCount(0))
    }

    return request
      .get('/messages/unread-count', {}, {
        showLoading: false,
        showErrorToast: false,
        skipAuthRedirect: true,
        timeout: 12000,
        cacheTtl: 20 * 1000,
        cacheByUser: true,
        allowStaleOnError: true
      })
      .then((res) => {
        const count = this.syncMessageUnreadCount(res && res.count != null ? res.count : 0)
        const currentDetail = this.getProfileAlertDetail()
        this.syncProfileAlertState({
          ...currentDetail,
          messageUnreadCount: count
        })
        return count
      })
      .catch(() => this.getMessageUnreadCount())
  },

  refreshCartCount() {
    if (!this.isLoggedIn()) {
      return Promise.resolve(this.syncCartCount(0))
    }

    return request
      .get('/cart', {}, {
        showLoading: false,
        showErrorToast: false,
        skipAuthRedirect: true,
        timeout: 12000,
        cacheTtl: 20 * 1000,
        cacheByUser: true,
        allowStaleOnError: true
      })
      .then((res) => {
        const totalCount = res && res.totalCount != null
          ? Number(res.totalCount)
          : ((res && Array.isArray(res.items) ? res.items : []).reduce((sum, item) => {
            return sum + Math.max(0, Number(item.quantity || 0))
          }, 0))
        return this.syncCartCount(totalCount)
      })
      .catch(() => this.getCartCount())
  },

  refreshProfileBadgeSummary() {
    if (!this.isLoggedIn()) {
      this.syncProfileAlertState({
        couponAlertCount: 0,
        couponUnclaimedCount: 0,
        couponExpiringCount: 0,
        aftersaleCount: 0,
        unpaidCount: 0,
        unshippedCount: 0,
        unreceivedCount: 0,
        messageUnreadCount: this.getMessageUnreadCount()
      })
      return Promise.resolve(this.getProfileAlertDetail())
    }

    const calcExpiringSoonCount = (records = []) => {
      const thresholdHours = 72
      return records.filter((item) => {
        const coupon = item && item.coupon ? item.coupon : {}
        const endTime = coupon.endTime
        if (!endTime) return false
        const end = new Date(endTime).getTime()
        if (!end) return false
        const diffHours = (end - Date.now()) / (1000 * 60 * 60)
        return diffHours > 0 && diffHours <= thresholdHours
      }).length
    }

    const badgeRequestOptions = {
      showLoading: false,
      showErrorToast: false,
      skipAuthRedirect: true,
      timeout: 12000,
      cacheTtl: 30 * 1000,
      cacheByUser: true,
      allowStaleOnError: true
    }

    return Promise.all([
      this.refreshMessageUnreadCount().catch(() => this.getMessageUnreadCount()),
      request.get('/coupons/has-unclaimed', {}, badgeRequestOptions).catch(() => null),
      request.get('/coupons/my', { status: 0, page: 1, size: 50 }, badgeRequestOptions).catch(() => null),
      request.get('/orders', { status: 0, page: 1, size: 1 }, badgeRequestOptions).catch(() => null),
      request.get('/orders', { status: 1, page: 1, size: 1 }, badgeRequestOptions).catch(() => null),
      request.get('/orders', { status: 2, page: 1, size: 1 }, badgeRequestOptions).catch(() => null),
      request.get('/refunds/my', { page: 1, size: 1 }, badgeRequestOptions).catch(() => null)
    ]).then(([
      unreadCount,
      unclaimedRes,
      myUsableRes,
      unpaidRes,
      unshippedRes,
      unreceivedRes,
      refundRes
    ]) => {
      const unclaimedCount = unclaimedRes && unclaimedRes.count != null ? Number(unclaimedRes.count) : 0
      const usableRecords = (myUsableRes && Array.isArray(myUsableRes.records)) ? myUsableRes.records : []
      const expiringSoonCount = calcExpiringSoonCount(usableRecords)
      const detail = {
        couponUnclaimedCount: unclaimedCount,
        couponExpiringCount: expiringSoonCount,
        couponAlertCount: unclaimedCount + expiringSoonCount,
        aftersaleCount: refundRes && refundRes.total != null ? Number(refundRes.total) : 0,
        unpaidCount: unpaidRes && unpaidRes.total != null ? Number(unpaidRes.total) : 0,
        unshippedCount: unshippedRes && unshippedRes.total != null ? Number(unshippedRes.total) : 0,
        unreceivedCount: unreceivedRes && unreceivedRes.total != null ? Number(unreceivedRes.total) : 0,
        messageUnreadCount: Number(unreadCount || 0)
      }
      return this.syncProfileAlertState(detail)
    }).catch(() => this.getProfileAlertDetail())
  },

  globalData: {
    userInfo: null,
    systemInfo: null,
    menuButtonInfo: null,
    navBarHeight: 0,
    messageUnreadCount: 0,
    cartCount: 0,
    profileAlertCount: 0,
    lastRuntimeError: '',
    profileAlertDetail: {
      couponAlertCount: 0,
      couponUnclaimedCount: 0,
      couponExpiringCount: 0,
      aftersaleCount: 0,
      unpaidCount: 0,
      unshippedCount: 0,
      unreceivedCount: 0,
      messageUnreadCount: 0
    }
  }
})
