const app = getApp()
const request = require('../../utils/request')

const EXPIRING_SOON_HOURS = 72

function createDefaultUsageGuide() {
  return {
    target: 'explore',
    actionText: '去逛逛',
    description: '结算自动匹配'
  }
}

Page({
  data: {
    activeTab: 'available',
    availableCoupons: [],
    availableLoading: false,
    myCoupons: [],
    myLoading: false,
    myStatus: 0,
    refreshing: false,
    summary: {
      hasUnclaimed: false,
      unclaimedCount: 0,
      soonExpiringCount: 0,
      soonExpiringNames: []
    },
    usageGuide: createDefaultUsageGuide()
  },

  onLoad() {
    if (!app.requireLogin('/pages/coupons/index')) {
      return
    }

    this.refreshAllData()
  },

  onShow() {
    if (!app.isLoggedIn()) {
      return
    }

    this.refreshCurrentTabData()
    this.loadSummary()
    this.loadUsageGuide()
    app.refreshProfileBadgeSummary().catch(() => {})
  },

  async onRefresh() {
    await Promise.all([
      this.refreshCurrentTabData(),
      this.loadSummary(),
      this.loadUsageGuide()
    ])
    this.setData({ refreshing: false })
  },

  async refreshAllData() {
    await Promise.all([
      this.refreshCurrentTabData(),
      this.loadSummary(),
      this.loadUsageGuide()
    ])
  },

  refreshCurrentTabData() {
    if (this.data.activeTab === 'available') {
      return this.loadAvailableCoupons()
    }
    return this.loadMyCoupons()
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.activeTab) {
      return
    }

    this.setData({ activeTab: tab })
    this.refreshCurrentTabData()
    this.loadSummary()
  },

  switchMyStatus(e) {
    const status = parseInt(e.currentTarget.dataset.status, 10)
    if (status === this.data.myStatus) {
      return
    }

    this.setData({ myStatus: status })
    this.loadMyCoupons()
  },

  async loadAvailableCoupons() {
    this.setData({ availableLoading: true })
    try {
      const list = await request.get('/coupons', {}, { showLoading: false })
      const coupons = (list || []).map((coupon) => this.formatAvailableCoupon(coupon))
      this.setData({ availableCoupons: coupons })
    } catch (err) {
      console.error('获取可领取优惠券失败', err)
    } finally {
      this.setData({ availableLoading: false })
    }
  },

  async loadMyCoupons() {
    this.setData({ myLoading: true })
    try {
      const res = await request.get('/coupons/my', {
        status: this.data.myStatus,
        page: 1,
        size: 50
      }, { showLoading: false })
      const records = (res && res.records) || []
      const coupons = records.map((userCoupon) => this.formatMyCoupon(userCoupon))
      this.setData({ myCoupons: coupons })
    } catch (err) {
      console.error('获取我的优惠券失败', err)
    } finally {
      this.setData({ myLoading: false })
    }
  },

  async loadSummary() {
    try {
      const [unclaimedRes, myUsableRes] = await Promise.all([
        request.get('/coupons/has-unclaimed', {}, { showLoading: false }).catch(() => null),
        request.get('/coupons/my', { status: 0, page: 1, size: 50 }, { showLoading: false }).catch(() => null)
      ])

      const usableRecords = ((myUsableRes && myUsableRes.records) || []).map((item) => this.formatMyCoupon(item))
      const expiringSoonCoupons = usableRecords.filter((item) => item.expiringSoon)

      this.setData({
        summary: {
          hasUnclaimed: !!(unclaimedRes && unclaimedRes.hasUnclaimed),
          unclaimedCount: unclaimedRes && unclaimedRes.count != null ? Number(unclaimedRes.count) : 0,
          soonExpiringCount: expiringSoonCoupons.length,
          soonExpiringNames: expiringSoonCoupons.slice(0, 2).map((item) => item.coupon.name)
        }
      })
    } catch (err) {
      console.error('获取优惠券提醒失败', err)
    }
  },

  async loadUsageGuide() {
    try {
      const res = await request.get('/cart', {}, { showLoading: false }).catch(() => null)
      const totalCount = res && res.totalCount != null ? Number(res.totalCount) : 0
      const selectedCount = res && res.selectedCount != null ? Number(res.selectedCount) : 0

      this.setData({
        usageGuide: this.buildUsageGuide(totalCount, selectedCount)
      })
    } catch (err) {
      console.error('获取用券引导失败', err)
      this.setData({ usageGuide: createDefaultUsageGuide() })
    }
  },

  buildUsageGuide(totalCount, selectedCount) {
    if (selectedCount > 0) {
      return {
        target: 'checkout',
        actionText: '去结算',
        description: `购物车已选 ${selectedCount} 件商品，可直接结算使用优惠券`
      }
    }

    if (totalCount > 0) {
      return {
        target: 'cart',
        actionText: '去购物车',
        description: `购物车还有 ${totalCount} 件商品，勾选后结算即可用券`
      }
    }

    return createDefaultUsageGuide()
  },

  handleUseCoupon() {
    const target = (this.data.usageGuide && this.data.usageGuide.target) || 'explore'

    if (target === 'checkout') {
      app.navigateToPage('/pages/checkout/index')
      return
    }

    if (target === 'cart') {
      app.navigateToPage('/pages/cart/index')
      return
    }

    app.navigateToPage('/pages/explore/index')
  },

  async claimCoupon(e) {
    const couponId = e.currentTarget.dataset.id
    try {
      await request.post(`/coupons/${couponId}/claim`, {}, { showLoading: false })
      wx.showToast({ title: '领取成功', icon: 'success' })
      await Promise.all([
        this.loadAvailableCoupons(),
        this.loadSummary(),
        app.refreshProfileBadgeSummary().catch(() => null)
      ])
    } catch (err) {
      console.error('领取优惠券失败', err)
    }
  },

  formatAvailableCoupon(coupon) {
    const source = this.resolveCouponSource(coupon)
    return {
      ...coupon,
      startTimeStr: this.formatDate(coupon.startTime),
      endTimeStr: this.formatDate(coupon.endTime),
      badgeText: Number(coupon.userCouponStatus) === 1 ? '已领取' : '未领取',
      sourceLabel: source.label,
      sourceClass: source.className
    }
  },

  formatMyCoupon(userCoupon) {
    const coupon = userCoupon.coupon || {}
    const expiringInfo = this.getExpiringInfo(coupon.endTime)
    const source = this.resolveCouponSource(coupon)

    return {
      ...userCoupon,
      coupon: {
        ...coupon,
        startTimeStr: this.formatDate(coupon.startTime),
        endTimeStr: this.formatDate(coupon.endTime),
        sourceLabel: source.label,
        sourceClass: source.className
      },
      expiringSoon: expiringInfo.expiringSoon,
      expiryText: expiringInfo.expiryText
    }
  },

  resolveCouponSource(coupon) {
    const scopeType = Number(coupon && coupon.scopeType != null ? coupon.scopeType : 0)
    if (scopeType === 1) {
      return {
        label: '店铺券（仅本店）',
        className: 'merchant'
      }
    }
    return {
      label: '平台券',
      className: 'platform'
    }
  },

  formatDate(dateStr) {
    if (!dateStr) {
      return ''
    }
    return String(dateStr).slice(0, 10)
  },

  getExpiringInfo(endTime) {
    if (!endTime) {
      return { expiringSoon: false, expiryText: '' }
    }

    const end = new Date(endTime).getTime()
    if (!end) {
      return { expiringSoon: false, expiryText: '' }
    }

    const diffHours = (end - Date.now()) / (1000 * 60 * 60)
    if (diffHours <= 0) {
      return { expiringSoon: false, expiryText: '已过期' }
    }
    if (diffHours > EXPIRING_SOON_HOURS) {
      return { expiringSoon: false, expiryText: '' }
    }
    if (diffHours <= 24) {
      return { expiringSoon: true, expiryText: '24小时内到期' }
    }

    return {
      expiringSoon: true,
      expiryText: `${Math.ceil(diffHours / 24)}天内到期`
    }
  }
})
