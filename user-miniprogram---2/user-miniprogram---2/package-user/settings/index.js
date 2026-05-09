const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    cacheSize: '0 KB',
    version: '1.0.0',
    loggedIn: false,
    userInfo: null
  },

  onLoad() {
    this.calculateCache()
    this.refreshAccountState()
  },

  onShow() {
    this.refreshAccountState()
  },

  calculateCache() {
    try {
      const res = wx.getStorageInfoSync()
      const kb = res.currentSize
      const size = kb > 1024 ? (kb / 1024).toFixed(1) + ' MB' : kb + ' KB'
      this.setData({ cacheSize: size })
    } catch (e) {
      this.setData({ cacheSize: '0 KB' })
    }
  },

  refreshAccountState() {
    const storedUserInfo = wx.getStorageSync('userInfo') || null
    this.setData({
      loggedIn: !!app.isLoggedIn(),
      userInfo: storedUserInfo
    })
  },

  handleAccountAction() {
    if (this.data.loggedIn) {
      app.navigateToPage('/pages/edit-profile/index')
      return
    }
    app.navigateToPage('/pages/login/index?redirect=%2Fpages%2Fsettings%2Findex')
  },

  clearCache() {
    wx.showModal({
      title: '清除缓存',
      content: '将清除接口缓存和临时数据，不影响账号登录，确定继续？',
      success: (res) => {
        if (res.confirm) {
          const messageUnreadCount = app.getMessageUnreadCount()
          if (request.cache && typeof request.cache.clearAll === 'function') {
            request.cache.clearAll()
          }
          app.syncMessageUnreadCount(messageUnreadCount)
          this.calculateCache()
          wx.showToast({ title: '缓存已清除', icon: 'success' })
        }
      }
    })
  },

  logout() {
    if (!this.data.loggedIn) {
      this.handleAccountAction()
      return
    }
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      confirmColor: '#ff4757',
      success: (res) => {
        if (res.confirm) {
          app.clearLoginState()
          this.refreshAccountState()
          wx.showToast({ title: '已退出', icon: 'success' })
          setTimeout(() => {
            app.navigateToPage('/pages/home/index')
          }, 1000)
        }
      }
    })
  }
})
