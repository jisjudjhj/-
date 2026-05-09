const { buildSafeWebviewUrl } = require('../../utils/webview')

Page({
  data: {
    url: ''
  },

  onLoad(options = {}) {
    const safeUrl = buildSafeWebviewUrl(options.url || '')
    if (!safeUrl) {
      wx.showToast({
        title: '活动链接不可用',
        icon: 'none'
      })
      setTimeout(() => {
        this.returnHome()
      }, 150)
      return
    }

    this.setData({ url: safeUrl })
  },

  returnHome() {
    const app = getApp()
    if (app && typeof app.navigateToPage === 'function') {
      app.navigateToPage('/pages/home/index')
      return
    }
    wx.switchTab({
      url: '/pages/home/index'
    })
  },

  handleWebViewError() {
    wx.showToast({
      title: '页面加载失败',
      icon: 'none'
    })
    setTimeout(() => {
      this.returnHome()
    }, 150)
  }
})
