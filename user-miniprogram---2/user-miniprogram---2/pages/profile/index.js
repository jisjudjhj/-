const app = getApp()
const request = require('../../utils/request')
const im = require('../../utils/im')

Page({
  data: {
    userInfo: null,
    balance: '0.00',
    unreadMessageCount: 0,
    customerServiceUnreadCount: 0,
    profileAlertCount: 0,
    profileAlertDetail: {
      couponAlertCount: 0,
      couponUnclaimedCount: 0,
      couponExpiringCount: 0,
      aftersaleCount: 0,
      unpaidCount: 0,
      unshippedCount: 0,
      unreceivedCount: 0,
      messageUnreadCount: 0
    },
    orderCount: {
      unpaid: 0,
      unshipped: 0,
      unreceived: 0,
      aftersale: 0
    },
    stats: {
      favorite: 0,
      footprint: 0,
      coupon: 0
    },
    refreshing: false
  },

  onLoad() {
    this._destroyed = false
  },

  onShow() {
    this._destroyed = false
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 3 })
      app.applyMessageBadge()
      app.refreshCartCount().catch(() => {})
    }
    this.safeSetData({
      unreadMessageCount: app.getMessageUnreadCount(),
      profileAlertCount: app.getProfileAlertCount(),
      profileAlertDetail: app.getProfileAlertDetail()
    })
    this.loadProfileData()
    Promise.all([
      app.refreshMessageUnreadCount(),
      app.refreshProfileBadgeSummary(),
      this.refreshCustomerServiceUnreadCount()
    ]).then(([count, detail, customerServiceUnreadCount]) => {
      this.safeSetData({
        unreadMessageCount: count,
        customerServiceUnreadCount,
        profileAlertCount: app.getProfileAlertCount(),
        profileAlertDetail: detail || app.getProfileAlertDetail()
      })
    }).catch(() => {})
  },

  onHide() {
    this._destroyed = true
  },

  onUnload() {
    this._destroyed = true
  },

  isPageInactive() {
    return !!this._destroyed
  },

  safeSetData(nextData, callback) {
    if (this.isPageInactive() || !nextData) {
      return false
    }
    try {
      this.setData(nextData, callback)
      return true
    } catch (error) {
      return false
    }
  },

  async onRefresh() {
    if (this.data.refreshing) return
    this.safeSetData({ refreshing: true })
    try {
      await this.loadProfileData()
    } finally {
      this.safeSetData({ refreshing: false })
      wx.stopPullDownRefresh()
    }
  },

  async onPullDownRefresh() {
    await this.onRefresh()
  },

  async loadProfileData() {
    if (!app.isLoggedIn()) {
      app.syncMessageUnreadCount(0)
      this.safeSetData({
        userInfo: null,
        balance: '0.00',
        unreadMessageCount: 0,
        customerServiceUnreadCount: 0,
        profileAlertCount: 0,
        profileAlertDetail: {
          couponAlertCount: 0,
          couponUnclaimedCount: 0,
          couponExpiringCount: 0,
          aftersaleCount: 0,
          unpaidCount: 0,
          unshippedCount: 0,
          unreceivedCount: 0,
          messageUnreadCount: 0
        },
        orderCount: { unpaid: 0, unshipped: 0, unreceived: 0, aftersale: 0 },
        stats: { favorite: 0, footprint: 0, coupon: 0 }
      })
      return
    }

    try {
      const [
        userRes,
        walletRes,
        favoritesRes,
        historyRes,
        couponRes
      ] = await Promise.all([
        request.get('/auth/me', {}, { showLoading: false }).catch(() => null),
        request.get('/wallet/balance', {}, { showLoading: false }).catch(() => null),
        request.get('/user/favorites', { page: 1, size: 1 }, { showLoading: false }).catch(() => null),
        request.get('/user/history', { page: 1, size: 1 }, { showLoading: false }).catch(() => null),
        request.get('/coupons/my', { status: 0, page: 1, size: 1 }, { showLoading: false }).catch(() => null)
      ])

      const userInfo = userRes
        ? {
            id: userRes.id,
            nickName: userRes.nickname || userRes.username || '',
            avatarUrl: userRes.avatar || '',
            role: userRes.role || ''
          }
        : null

      if (userInfo) {
        wx.setStorageSync('userInfo', userInfo)
        app.globalData.userInfo = userInfo
      }

      const couponUsableCount = couponRes ? couponRes.total || 0 : 0
      const unreadMessageCount = app.getMessageUnreadCount()
      const profileAlertDetail = app.getProfileAlertDetail ? app.getProfileAlertDetail() : {}

      this.safeSetData({
        userInfo,
        balance: walletRes && walletRes.balance != null ? walletRes.balance : '0.00',
        unreadMessageCount,
        customerServiceUnreadCount: this.data.customerServiceUnreadCount,
        profileAlertCount: app.getProfileAlertCount ? app.getProfileAlertCount() : 0,
        profileAlertDetail: app.getProfileAlertDetail ? app.getProfileAlertDetail() : profileAlertDetail,
        orderCount: {
          unpaid: profileAlertDetail.unpaidCount || 0,
          unshipped: profileAlertDetail.unshippedCount || 0,
          unreceived: profileAlertDetail.unreceivedCount || 0,
          aftersale: profileAlertDetail.aftersaleCount || 0
        },
        stats: {
          favorite: favoritesRes ? favoritesRes.total || 0 : 0,
          footprint: historyRes ? historyRes.total || 0 : 0,
          coupon: couponUsableCount
        }
      })
    } catch (error) {
      console.error('获取个人中心数据失败', error)
    }
  },

  handleLogin() {
    if (!this.data.userInfo) {
      app.navigateToPage('/pages/login/index?redirect=%2Fpages%2Fprofile%2Findex')
    }
  },

  goToOrders(e) {
    if (!app.requireLogin('/pages/profile/index')) {
      return
    }

    const type = (e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.type) || 'all'
    app.navigateToPage(`/pages/orders/index?type=${type}`)
  },

  goToFavorites() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/favorites/index')
  },

  goToFootprint() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/footprint/index')
  },

  goToCoupons() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/coupons/index')
  },

  goToMessages() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/messages/index')
  },

  async refreshCustomerServiceUnreadCount() {
    if (!app.isLoggedIn()) {
      this.safeSetData({ customerServiceUnreadCount: 0 })
      return 0
    }
    try {
      const res = await im.getConversationUnreadCount({ showLoading: false })
      const count = Number(res && res.count ? res.count : 0)
      this.safeSetData({ customerServiceUnreadCount: count })
      return count
    } catch (error) {
      console.error('获取客服未读数失败', error)
      this.safeSetData({ customerServiceUnreadCount: 0 })
      return 0
    }
  },

  goToCustomerService() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/customer-service/index')
  },

  goToAddress() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/address/index')
  },

  goToWallet() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/wallet/index')
  },

  goToEditProfile() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/edit-profile/index')
  },

  handleProfileHero() {
    if (!this.data.userInfo) {
      app.navigateToPage('/pages/login/index?redirect=%2Fpages%2Fprofile%2Findex')
      return
    }
    this.goToEditProfile()
  },

  goToAiAssistant() {
    if (!app.requireLogin('/pages/profile/index')) return
    app.navigateToPage('/pages/ai-assistant/index')
  },

  goToAbout() {
    app.navigateToPage('/pages/about/index')
  },

  goToSettings() {
    app.navigateToPage('/pages/settings/index')
  }
})
