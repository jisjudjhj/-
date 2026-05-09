const app = getApp()
const { getSystemInfoCompat } = require('../../utils/system-info')

Component({
  options: {
    multipleSlots: true
  },
  properties: {
    title: {
      type: String,
      value: ''
    },
    showBack: {
      type: Boolean,
      value: true
    },
    placeholder: {
      type: Boolean,
      value: true
    },
    color: {
      type: String,
      value: '#111111'
    },
    backgroundColor: {
      type: String,
      value: 'transparent'
    },
    isScrolled: {
      type: Boolean,
      value: false
    },
    transparentTitle: {
      type: Boolean,
      value: false
    }
  },
  data: {
    navBarHeight: 0,
    statusBarHeight: 0,
    menuButtonHeight: 0
  },
  lifetimes: {
    attached() {
      const systemInfo = (app && app.globalData && app.globalData.systemInfo) || getSystemInfoCompat()
      const cachedMenuButtonInfo = app && app.globalData ? app.globalData.menuButtonInfo : null
      let menuButtonInfo = cachedMenuButtonInfo

      if (!menuButtonInfo) {
        try {
          if (typeof wx.getMenuButtonBoundingClientRect === 'function') {
            const rect = wx.getMenuButtonBoundingClientRect()
            if (rect && rect.top && rect.bottom) {
              menuButtonInfo = rect
            }
          }
        } catch (err) {
          console.warn('[custom-nav-bar] getMenuButtonBoundingClientRect failed:', err)
        }
      }

      const statusBarHeight = systemInfo.statusBarHeight || 20
      if (!menuButtonInfo) {
        const fallbackHeight = 32
        const fallbackGap = 8
        menuButtonInfo = {
          top: statusBarHeight + fallbackGap,
          bottom: statusBarHeight + fallbackGap + fallbackHeight,
          height: fallbackHeight
        }
      }

      const navBarHeight = menuButtonInfo.bottom + menuButtonInfo.top - statusBarHeight

      this.setData({
        navBarHeight,
        statusBarHeight,
        menuButtonHeight: menuButtonInfo.height
      })
    }
  },
  methods: {
    goBack() {
      wx.navigateBack({
        delta: 1,
        fail: () => {
          if (app && typeof app.navigateToPage === 'function') {
            app.navigateToPage('/pages/home/index')
            return
          }
          wx.switchTab({ url: '/pages/home/index' })
        }
      })
    }
  }
})
