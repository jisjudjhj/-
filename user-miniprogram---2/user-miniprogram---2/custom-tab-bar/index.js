Component({
  data: {
    selected: 0,
    list: [
      {
        pagePath: "/pages/home/index",
        text: "首页",
        iconClass: "icon-home",
        showBadge: false
      },
      {
        pagePath: "/pages/explore/index",
        text: "商城",
        iconClass: "icon-explore",
        showBadge: false
      },
      {
        pagePath: "/pages/cart/index",
        text: "购物车",
        iconClass: "icon-cart",
        showBadge: false,
        badgeCount: 0,
        badgeText: ''
      },
      {
        pagePath: "/pages/profile/index",
        text: "我的",
        iconClass: "icon-profile",
        showBadge: false,
        badgeCount: 0,
        badgeText: ''
      }
    ]
  },
  attached() {
    const app = getApp()
    if (app && typeof app.getMessageUnreadCount === 'function') {
      this.syncUnreadState(app.getMessageUnreadCount())
    }
    if (app && typeof app.getCartCount === 'function') {
      this.syncCartState(app.getCartCount())
    }
    if (app && typeof app.getProfileAlertCount === 'function') {
      this.syncProfileState(app.getProfileAlertCount())
    }
  },
  methods: {
    switchTab(e) {
      const data = e.currentTarget.dataset
      const url = data.path
      if (!url) {
        wx.showToast({ title: '页面入口异常', icon: 'none' })
        return
      }

      const app = getApp()
      if (app && typeof app.navigateToPage === 'function') {
        app.navigateToPage(url)
        return
      }
      wx.switchTab({ url })
    },

    syncUnreadState(count) {
      this.setData({
        ['list[3].showBadge']: Number(count) > 0
      })
    },

    syncCartState(count) {
      const safeCount = Math.max(0, Number(count || 0))
      this.setData({
        ['list[2].badgeCount']: safeCount,
        ['list[2].badgeText']: safeCount > 99 ? '99+' : String(safeCount)
      })
    },

    syncProfileState(count) {
      const safeCount = Math.max(0, Number(count || 0))
      this.setData({
        ['list[3].badgeCount']: safeCount,
        ['list[3].badgeText']: safeCount > 99 ? '99+' : String(safeCount),
        ['list[3].showBadge']: safeCount > 0
      })
    }
  }
})
