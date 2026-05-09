const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    isReady: false
  },

  onLoad() {
    this.preloadData()
  },

  async preloadData() {
    const startTime = Date.now()
    
    try {
      // 并行预加载数据
      const promises = []
      
      // 如果已登录，预加载用户相关数据
      if (app.isLoggedIn()) {
        promises.push(
          app.refreshMessageUnreadCount().catch(() => {}),
          app.refreshCartCount().catch(() => {})
        )
      }
      
      // 预加载首页数据（可选）
      promises.push(
        this.preloadHomeData().catch(() => {})
      )
      
      // 等待所有预加载完成
      await Promise.all(promises)
      
    } catch (error) {
      console.error('预加载数据失败', error)
    }
    
    // 确保至少显示2秒启动页
    const elapsed = Date.now() - startTime
    const remainingTime = Math.max(0, 2000 - elapsed)
    
    setTimeout(() => {
      this.navigateToHome()
    }, remainingTime)
  },

  async preloadHomeData() {
    try {
      // 预加载首页横幅和推荐数据
      const [banners, recommendations] = await Promise.all([
        request.get('/products/banners', {}, { showLoading: false }).catch(() => null),
        request.get('/recommendations/hot', { limit: 10 }, { showLoading: false }).catch(() => null)
      ])
      
      // 将数据缓存到本地存储
      if (banners) {
        wx.setStorageSync('cache_home_banners', {
          data: banners,
          timestamp: Date.now()
        })
      }
      
      if (recommendations) {
        wx.setStorageSync('cache_home_recommendations', {
          data: recommendations,
          timestamp: Date.now()
        })
      }
    } catch (error) {
      console.error('预加载首页数据失败', error)
    }
  },

  navigateToHome() {
    wx.reLaunch({
      url: '/pages/home/index',
      fail: (error) => {
        console.error('跳转首页失败', error)
        // 如果跳转失败，再试一次
        setTimeout(() => {
          wx.switchTab({
            url: '/pages/home/index'
          })
        }, 100)
      }
    })
  }
})
