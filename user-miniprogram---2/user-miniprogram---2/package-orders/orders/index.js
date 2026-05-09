const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const recommendationTracker = require('../../utils/recommendation-tracker')

function canApplyRefund(status) {
  return ['unshipped', 'unreceived', 'completed'].includes(status)
}

Page({
  data: {
    tabs: [
      { label: '全部', value: 'all', badgeCount: 0 },
      { label: '待付款', value: 'unpaid', badgeCount: 0 },
      { label: '待发货', value: 'unshipped', badgeCount: 0 },
      { label: '待收货', value: 'unreceived', badgeCount: 0 },
      { label: '已完成', value: 'completed', badgeCount: 0 },
      { label: '退换/售后', value: 'aftersale', badgeCount: 0 }
    ],
    currentTab: 'all',
    indicatorLeft: 0,
    indicatorWidth: 0,
    orderList: [],
    loading: true,
    page: 1,
    hasMore: true,
    refreshing: false
  },

  onLoad(options) {
    if (!app.requireLogin(`/pages/orders/index?type=${options.type || 'all'}`)) {
      return
    }

    const type = options.type || 'all'
    this.setData({ currentTab: type })

    setTimeout(() => {
      const index = this.data.tabs.findIndex((item) => item.value === type)
      this.updateIndicator(index !== -1 ? index : 0)
    }, 100)

    this.loadOrders(true)
  },

  onShow() {
    if (!app.isLoggedIn()) {
      return
    }
    this.refreshTabBadges()
    if (!this.data.loading) {
      this.loadOrders(true)
    }
  },

  refreshTabBadges() {
    const detail = app.getProfileAlertDetail ? app.getProfileAlertDetail() : {}
    const unpaidCount = Number(detail.unpaidCount || 0)
    const unshippedCount = Number(detail.unshippedCount || 0)
    const unreceivedCount = Number(detail.unreceivedCount || 0)
    const aftersaleCount = Number(detail.aftersaleCount || 0)
    const totalCount = unpaidCount + unshippedCount + unreceivedCount + aftersaleCount
    this.setData({
      'tabs[0].badgeCount': totalCount,
      'tabs[1].badgeCount': unpaidCount,
      'tabs[2].badgeCount': unshippedCount,
      'tabs[3].badgeCount': unreceivedCount,
      'tabs[4].badgeCount': 0,
      'tabs[5].badgeCount': aftersaleCount
    })
    app.refreshProfileBadgeSummary().then((latestDetail) => {
      const nextDetail = latestDetail || (app.getProfileAlertDetail ? app.getProfileAlertDetail() : {})
      const nextUnpaid = Number(nextDetail.unpaidCount || 0)
      const nextUnshipped = Number(nextDetail.unshippedCount || 0)
      const nextUnreceived = Number(nextDetail.unreceivedCount || 0)
      const nextAftersale = Number(nextDetail.aftersaleCount || 0)
      this.setData({
        'tabs[0].badgeCount': nextUnpaid + nextUnshipped + nextUnreceived + nextAftersale,
        'tabs[1].badgeCount': nextUnpaid,
        'tabs[2].badgeCount': nextUnshipped,
        'tabs[3].badgeCount': nextUnreceived,
        'tabs[4].badgeCount': 0,
        'tabs[5].badgeCount': nextAftersale
      })
    }).catch(() => {})
  },

  async onRefresh() {
    await this.loadOrders(true)
    this.setData({ refreshing: false })
  },

  onReachBottom() {
    if (this.data.hasMore) {
      this.loadOrders(false)
    }
  },

  async loadOrders(isRefresh = false) {
    this._reviewStateSeq = (this._reviewStateSeq || 0) + 1
    if (isRefresh) {
      this.setData({ page: 1, hasMore: true })
    }

    if (this.data.currentTab === 'aftersale') {
      return this.loadRefunds(isRefresh)
    }

    try {
      let status = -1
      switch (this.data.currentTab) {
        case 'unpaid':
          status = 0
          break
        case 'unshipped':
          status = 1
          break
        case 'unreceived':
          status = 2
          break
        case 'completed':
          status = 3
          break
      }

      const res = await request.get(
        '/orders',
        {
          status,
          page: this.data.page,
          size: 10
        },
        { showLoading: isRefresh }
      )

      const formatOrders = ((res && res.records) || []).map((item) => {
        let statusText = '未知状态'
        let tabStatus = 'all'
        switch (item.status) {
          case 0:
            statusText = '等待付款'
            tabStatus = 'unpaid'
            break
          case 1:
            statusText = '待发货'
            tabStatus = 'unshipped'
            break
          case 2:
            statusText = '待收货'
            tabStatus = 'unreceived'
            break
          case 3:
            statusText = '交易完成'
            tabStatus = 'completed'
            break
          case 4:
            statusText = '交易取消'
            tabStatus = 'cancelled'
            break
          case 5:
            statusText = '已退款'
            tabStatus = 'aftersale'
            break
        }

        return {
          id: item.id,
          orderNo: item.orderNo,
          status: tabStatus,
          statusText,
          isSeckill: !!(item.seckillActivityId || item.seckillApplyId),
          seckillDiscount: item.discountAmount != null ? item.discountAmount : 0,
          canRefund: [1, 2, 3].includes(item.status),
          isRefund: false,
          totalQuantity: Array.isArray(item.items)
            ? item.items.reduce((sum, current) => sum + current.quantity, 0)
            : 0,
          totalPrice: item.totalAmount,
          goods: Array.isArray(item.items)
            ? item.items.map((good) => ({
                id: good.id,
                productId: good.productId || null,
                title: good.productName || '',
                price: good.price,
                quantity: good.quantity,
                image: resolveProductImage(good),
                reviewChecking: item.status === 3,
                reviewed: false,
                canReview: false
              }))
            : []
        }
      })

      const newList = isRefresh ? formatOrders : [...this.data.orderList, ...formatOrders]
      this.setData({
        orderList: newList,
        hasMore: formatOrders.length === 10,
        page: this.data.page + 1,
        loading: false
      })
      this.trackCompletedOrderAttribution(newList)
      this.loadReviewStatesForOrders(newList)
    } catch (error) {
      console.error('获取订单列表失败', error)
      this.setData({ orderList: [], loading: false })
    }
  },

  trackCompletedOrderAttribution(orders = []) {
    this._trackedOrderEvents = this._trackedOrderEvents || {}
    orders.forEach((order) => {
      if (!order || order.status !== 'completed' || this._trackedOrderEvents[order.id]) {
        return
      }
      this._trackedOrderEvents[order.id] = true
      ;(order.goods || []).forEach((goods) => {
        recommendationTracker.trackEvent({
          eventType: 'order',
          productId: goods.productId,
          scene: 'order_completed',
          orderId: order.id,
          amount: goods.price
        })
      })
    })
  },

  async loadRefunds(isRefresh) {
    this._reviewStateSeq = (this._reviewStateSeq || 0) + 1
    try {
      const res = await request.get(
        '/refunds/my',
        { page: this.data.page, size: 10 },
        { showLoading: isRefresh }
      )

      const refundStatusMap = {
        0: '退款审核中',
        1: '退款已同意',
        2: '退款被拒绝',
        3: '已退款'
      }

      const formatRefunds = ((res && res.records) || []).map((item) => ({
        id: item.orderId || item.id,
        refundId: item.id,
        orderNo: item.orderNo || '-',
        status: 'aftersale',
        statusText: refundStatusMap[item.status] || '处理中',
        isRefund: true,
        totalQuantity: 0,
        totalPrice: item.amount || item.refundAmount || 0,
        refundReason: item.reason || '',
        goods: []
      }))

      const newList = isRefresh ? formatRefunds : [...this.data.orderList, ...formatRefunds]
      this.setData({
        orderList: newList,
        hasMore: formatRefunds.length === 10,
        page: this.data.page + 1,
        loading: false
      })
    } catch (error) {
      console.error('获取退款列表失败', error)
      this.setData({ orderList: [], loading: false })
    }
  },

  handleGoodsImageError(e) {
    const orderIndex = Number(e.currentTarget.dataset.orderIndex)
    const goodsIndex = Number(e.currentTarget.dataset.goodsIndex)
    if (!Number.isInteger(orderIndex) || !Number.isInteger(goodsIndex)) {
      return
    }
    this.setData({
      [`orderList[${orderIndex}].goods[${goodsIndex}].image`]: ''
    })
  },

  switchTab(e) {
    const { value, index } = e.currentTarget.dataset
    if (this.data.currentTab === value) return

    this.setData({
      currentTab: value,
      loading: true
    })

    this.updateIndicator(index)
    this.loadOrders(true)
  },

  updateIndicator(index) {
    const query = wx.createSelectorQuery()
    query.selectAll('.tab-item').boundingClientRect((rects) => {
      if (rects && rects[index]) {
        const rect = rects[index]
        const width = 24
        const left = rect.left + (rect.width - width) / 2
        this.setData({
          indicatorLeft: left,
          indicatorWidth: width
        })
      }
    }).exec()
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id
    if (!id) {
      wx.showToast({ title: '订单信息异常', icon: 'none' })
      return
    }
    app.navigateToPage(`/pages/order-detail/index?id=${id}`)
  },

  async loadReviewStatesForOrders(orderList = []) {
    const taskSeq = this._reviewStateSeq
    const source = Array.isArray(orderList) ? orderList : []
    const targets = []

    source.forEach((order, orderIndex) => {
      if (!order || order.status !== 'completed' || order.isRefund || !Array.isArray(order.goods)) {
        return
      }
      order.goods.forEach((good, goodsIndex) => {
        if (!good || !good.productId) {
          targets.push({
            orderIndex,
            goodsIndex,
            canReview: false,
            reviewed: false
          })
          return
        }
        targets.push({
          orderIndex,
          goodsIndex,
          orderId: order.id,
          productId: good.productId
        })
      })
    })

    if (!targets.length) {
      return
    }

    const results = await Promise.all(targets.map(async (target) => {
      if (!target.orderId || !target.productId) {
        return {
          ...target,
          reviewChecking: false,
          canReview: false,
          reviewed: false
        }
      }

      try {
        const canReview = await request.get('/reviews/check', {
          orderId: target.orderId,
          productId: target.productId
        }, { showLoading: false })

        return {
          ...target,
          reviewChecking: false,
          canReview: !!canReview,
          reviewed: !canReview
        }
      } catch (error) {
        return {
          ...target,
          reviewChecking: false,
          canReview: false,
          reviewed: false
        }
      }
    }))

    const updates = {}
    results.forEach((item) => {
      updates[`orderList[${item.orderIndex}].goods[${item.goodsIndex}].reviewChecking`] = !!item.reviewChecking
      updates[`orderList[${item.orderIndex}].goods[${item.goodsIndex}].canReview`] = !!item.canReview
      updates[`orderList[${item.orderIndex}].goods[${item.goodsIndex}].reviewed`] = !!item.reviewed
    })

    if (taskSeq !== this._reviewStateSeq) {
      return
    }

    if (Object.keys(updates).length) {
      this.setData(updates)
    }
  },

  goToReviewEditor(e) {
    const orderIndex = Number(e.currentTarget?.dataset?.orderIndex)
    const goodsIndex = Number(e.currentTarget?.dataset?.goodsIndex)

    if (!Number.isInteger(orderIndex) || !Number.isInteger(goodsIndex)) {
      return
    }

    const order = Array.isArray(this.data.orderList) ? this.data.orderList[orderIndex] : null
    const good = order && Array.isArray(order.goods) ? order.goods[goodsIndex] : null

    if (!order || !good || !good.productId) {
      wx.showToast({ title: '商品信息异常', icon: 'none' })
      return
    }

    if (good.reviewed || !good.canReview) {
      wx.showToast({ title: good.reviewed ? '已评价' : '暂不可评', icon: 'none' })
      return
    }

    const query = [
      `orderId=${order.id}`,
      `productId=${good.productId}`,
      `productName=${encodeURIComponent(good.title || '')}`,
      `productImage=${encodeURIComponent(good.image || '')}`,
      `productPrice=${encodeURIComponent(good.price || '')}`
    ].join('&')

    app.navigateToPage(`/pages/review-edit/index?${query}`)
  },

  goHome() {
    app.navigateToPage('/pages/home/index')
  },

  noop() {},

  handleOrderAction(e) {
    const { action, id } = e.currentTarget.dataset

    if (action === 'cancel') {
      wx.showModal({
        title: '提示',
        content: '确定要取消该订单吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              await request.post(`/orders/${id}/cancel`)
              wx.showToast({ title: '订单已取消', icon: 'success' })
              app.refreshProfileBadgeSummary().catch(() => {})
              this.loadOrders(true)
            } catch (err) {
              wx.showToast({ title: err.message || '取消失败', icon: 'none' })
            }
          }
        }
      })
    } else if (action === 'pay') {
      this.handlePay(id)
    } else if (action === 'refund') {
      const targetOrder = this.data.orderList.find((item) => item.id === id)
      if (!targetOrder || !canApplyRefund(targetOrder.status)) {
        wx.showToast({ title: '暂不可退', icon: 'none' })
        return
      }
      app.navigateToPage(`/pages/refund-apply/index?id=${id}`)
    } else if (action === 'confirm') {
      wx.showModal({
        title: '提示',
        content: '确认已收到商品？',
        success: async (res) => {
          if (res.confirm) {
            try {
              await request.post(`/orders/${id}/confirm`)
              wx.showToast({ title: '确认收货成功', icon: 'success' })
              app.refreshProfileBadgeSummary().catch(() => {})
              this.loadOrders(true)
            } catch (err) {
              wx.showToast({ title: err.message || '操作失败', icon: 'none' })
            }
          }
        }
      })
    }
  },

  async handlePay(id) {
    try {
      await request.post(`/orders/${id}/pay`)
      wx.showToast({ title: '支付成功', icon: 'success' })
      app.refreshProfileBadgeSummary().catch(() => {})
      this.loadOrders(true)
    } catch (err) {
      wx.showToast({ title: err.message || '支付失败', icon: 'none' })
    }
  }
})
