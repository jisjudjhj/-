const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const im = require('../../utils/im')
const recommendationTracker = require('../../utils/recommendation-tracker')

function canApplyRefund(status) {
  return ['unshipped', 'unreceived', 'completed'].includes(status)
}

Page({
  data: {
    orderId: null,
    order: null,
    logistics: null,
    completionInsight: null,
    repurchaseProducts: [],
    loading: true,
    refreshing: false
  },

  onLoad(options) {
    const id = Number(options.id)
    if (!Number.isFinite(id) || id <= 0 || !app.requireLogin(`/pages/order-detail/index?id=${options.id || ''}`)) {
      return
    }

    this.setData({ orderId: id })
    this.loadOrderDetail(id)
  },

  onShow() {
    if (this.data.orderId && !this.data.loading) {
      this.loadOrderDetail(this.data.orderId)
    }
  },

  async onRefresh() {
    if (this.data.orderId) {
      await this.loadOrderDetail(this.data.orderId)
    }
    this.setData({ refreshing: false })
  },

  async loadOrderDetail(id) {
    try {
      const res = await request.get(`/orders/${id}`)

      let statusText = '未知状态'
      let tabStatus = 'all'
      switch (res.status) {
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

      const order = {
        id: res.id,
        orderNo: res.orderNo,
        status: tabStatus,
        statusText,
        isSeckill: !!(res.seckillActivityId || res.seckillApplyId),
        canRefund: [1, 2, 3].includes(res.status),
        createTime: res.createTime,
        payTime: res.payTime,
        address: {
          name: res.receiverName,
          phone: res.receiverPhone,
          fullAddress: res.address || ''
        },
        goods: Array.isArray(res.items)
          ? res.items.map((good) => ({
              id: good.id,
              productId: good.productId || null,
              title: good.productName || '',
              price: good.price,
              quantity: good.quantity,
              image: resolveProductImage(good),
              reviewChecking: res.status === 3,
              reviewed: false,
              canReview: false
            }))
          : [],
        totalGoodsPrice: res.originalAmount || res.totalAmount,
        seckillDiscount: res.discountAmount || 0,
        shippingFee: '0.00',
        totalPrice: res.totalAmount
      }
      const completionInsight = this.buildCompletionInsight(order)

      this.setData({
        order,
        logistics: null,
        completionInsight,
        loading: false
      })

      if (tabStatus === 'completed' && order.goods.length) {
        this.trackOrderCompletion(order)
        this.loadReviewStates(order)
        this.loadCompletionRecommendations(order)
      }
    } catch (error) {
      console.error('获取订单详情失败', error)
      this.setData({ order: null, loading: false })
      wx.showToast({ title: '获取详情失败', icon: 'none' })
    }
  },

  copyOrderNo() {
    wx.setClipboardData({
      data: this.data.order.orderNo,
      success: () => {
        wx.showToast({ title: '复制成功', icon: 'none' })
      }
    })
  },

  handleDetailGoodsImageError(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (!Number.isInteger(index)) {
      return
    }
    this.setData({
      [`order.goods[${index}].image`]: ''
    })
  },

  goToProductDetail(e) {
    const productId = Number(e.currentTarget?.dataset?.productId)
    if (!Number.isFinite(productId) || productId <= 0) {
      return
    }
    app.navigateToPage(`/pages/product-detail/index?id=${productId}`)
  },

  goToRecommendProduct(e) {
    const id = e.detail && e.detail.id ? e.detail.id : e.currentTarget.dataset.id
    const recToken = e.detail && e.detail.recToken ? e.detail.recToken : e.currentTarget.dataset.recToken
    if (!id) {
      wx.showToast({ title: '商品信息异常', icon: 'none' })
      return
    }
    if (recToken) {
      recommendationTracker.trackClick(id, recToken, 'order_completed')
    }
    app.navigateToPage(
      recToken
        ? `/pages/product-detail/index?id=${id}&recToken=${encodeURIComponent(recToken)}&recScene=order_completed`
        : `/pages/product-detail/index?id=${id}`
    )
  },

  buildCompletionInsight(order) {
    if (!order || order.status !== 'completed') {
      return null
    }
    const firstGoods = Array.isArray(order.goods) && order.goods.length ? order.goods[0] : null
    return {
      sourceLabel: '推荐归因',
      headline: '订单偏好已更新',
      feedbackText: '转化记录已同步',
      recommendationText: '复购与搭配建议已刷新',
      tokenText: order.id ? `order_${order.id}` : 'order_token',
      productName: firstGoods ? firstGoods.title : '本次商品',
      steps: [
        { label: '曝光', value: '已记录' },
        { label: '点击', value: '已匹配' },
        { label: '成交', value: '已归因' }
      ]
    }
  },

  trackOrderCompletion(order) {
    if (!order || !Array.isArray(order.goods) || !order.goods.length) {
      return
    }
    this._trackedCompletionOrders = this._trackedCompletionOrders || {}
    if (this._trackedCompletionOrders[order.id]) {
      return
    }
    this._trackedCompletionOrders[order.id] = true
    order.goods.forEach((good) => {
      recommendationTracker.trackEvent({
        eventType: 'order',
        productId: good.productId,
        scene: 'order_completed',
        orderId: order.id,
        amount: good.price,
        metadata: {
          source: 'order_detail_completion'
        }
      })
    })
  },

  async loadCompletionRecommendations(order) {
    const firstGoods = order && Array.isArray(order.goods) ? order.goods.find((item) => item.productId) : null
    if (!firstGoods) {
      this.setData({ repurchaseProducts: [] })
      return
    }
    try {
      const res = await request.get(`/recommendations/similar/${firstGoods.productId}`, { limit: 4 }, {
        showLoading: false,
        cacheTtl: 3 * 60 * 1000,
        cacheByUser: true
      })
      const repurchaseProducts = (Array.isArray(res) ? res : []).map((item) => ({
        id: item.id,
        image: resolveProductImage(item),
        title: item.name || '',
        price: item.price,
        tag: Array.isArray(item.tags) && item.tags.length ? item.tags[0] : '搭配推荐',
        salesText: item.salesCount != null ? String(item.salesCount) : '',
        extraInfo: item.categoryName || item.category || '相关搭配',
        reason: item.recommendReason || '基于本次订单生成复购和搭配推荐',
        reasonTags: this.normalizeReasonTags(item),
        sourceLabel: '订单完成召回',
        recommendationToken: item.recommendationToken || '',
        recommendationScene: 'order_completed'
      })).slice(0, 4)
      this.setData({ repurchaseProducts })
      recommendationTracker.trackExposures(repurchaseProducts, 'order_completed')
    } catch (error) {
      console.warn('[order-detail] completion recommendations failed', error)
      this.setData({ repurchaseProducts: [] })
    }
  },

  normalizeReasonTags(item) {
    const raw = Array.isArray(item && item.matchedReasonTags) ? item.matchedReasonTags : []
    const tags = raw
      .map(tag => String(tag || '').replace('：', ':').trim())
      .filter(Boolean)
      .map(tag => {
        const index = tag.indexOf(':')
        return index >= 0 ? tag.slice(index + 1).trim() : tag
      })
      .filter(Boolean)
      .filter((tag, index, list) => list.indexOf(tag) === index)
      .slice(0, 3)
    return tags.length ? tags : ['复购推荐', '相关搭配']
  },

  async loadReviewStates(order) {
    if (!order || !Array.isArray(order.goods) || !order.goods.length) {
      return
    }

    try {
      const checks = await Promise.all(order.goods.map(async (good) => {
        if (!good.productId) {
          return { productId: null, canReview: false, reviewed: false }
        }
        try {
          const canReview = await request.get('/reviews/check', {
            orderId: order.id,
            productId: good.productId
          }, { showLoading: false })
          return {
            productId: good.productId,
            canReview: !!canReview,
            reviewed: !canReview
          }
        } catch (error) {
          return {
            productId: good.productId,
            canReview: false,
            reviewed: false
          }
        }
      }))

      const nextGoods = order.goods.map((good) => {
        const state = checks.find((item) => item.productId === good.productId) || {}
        return {
          ...good,
          reviewChecking: false,
          canReview: !!state.canReview,
          reviewed: !!state.reviewed
        }
      })

      this.setData({
        'order.goods': nextGoods
      })
    } catch (error) {
      console.warn('[order-detail] load review states failed', error)
      const nextGoods = order.goods.map((good) => ({
        ...good,
        reviewChecking: false
      }))
      this.setData({
        'order.goods': nextGoods
      })
    }
  },

  goToReviewEditor(e) {
    const index = Number(e.currentTarget?.dataset?.index)
    if (!Number.isInteger(index) || !this.data.order || !Array.isArray(this.data.order.goods)) {
      return
    }

    const good = this.data.order.goods[index]
    if (!good || !good.productId) {
      wx.showToast({ title: '商品信息异常', icon: 'none' })
      return
    }
    if (good.reviewed || !good.canReview) {
      wx.showToast({ title: good.reviewed ? '已评价' : '暂不可评', icon: 'none' })
      return
    }

    const query = [
      `orderId=${this.data.order.id}`,
      `productId=${good.productId}`,
      `productName=${encodeURIComponent(good.title || '')}`,
      `productImage=${encodeURIComponent(good.image || '')}`,
      `productPrice=${encodeURIComponent(good.price || '')}`
    ].join('&')

    app.navigateToPage(`/pages/review-edit/index?${query}`)
  },

  openMerchantService() {
    if (!app.requireLogin(`/pages/order-detail/index?id=${this.data.orderId}`)) {
      return
    }
    const query = this.buildServiceQuery('merchant')
    app.navigateToPage(`/pages/customer-chat/index?${query}`)
  },

  openOfficialService() {
    if (!app.requireLogin(`/pages/order-detail/index?id=${this.data.orderId}`)) {
      return
    }
    const query = this.buildServiceQuery('support')
    app.navigateToPage(`/pages/customer-chat/index?${query}`)
  },

  buildServiceQuery(openType) {
    const queryParts = [`openType=${encodeURIComponent(openType)}`]
    const orderId = im.parsePositiveNumber(this.data.orderId)
    const goods = Array.isArray(this.data.order && this.data.order.goods) ? this.data.order.goods : []
    const firstProductId = im.parsePositiveNumber(goods.length ? goods[0].productId : null)

    if (orderId) {
      queryParts.push(`orderId=${orderId}`)
    }
    if (firstProductId) {
      queryParts.push(`productId=${firstProductId}`)
    }
    return queryParts.join('&')
  },

  handleAction(e) {
    const action = e.currentTarget.dataset.action
    const id = this.data.orderId

    if (action === 'cancel') {
      wx.showModal({
        title: '提示',
        content: '确定要取消该订单吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              await request.post(`/orders/${id}/cancel`)
              wx.showToast({ title: '订单已取消', icon: 'success' })
              setTimeout(() => wx.navigateBack(), 1200)
            } catch (err) {
              wx.showToast({ title: err.message || '取消失败', icon: 'none' })
            }
          }
        }
      })
    } else if (action === 'pay') {
      this.handlePay(id)
    } else if (action === 'refund') {
      if (!this.data.order || !canApplyRefund(this.data.order.status)) {
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
              this.loadOrderDetail(id)
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
      const walletRes = await request.get('/wallet/balance', {}, { showLoading: false })
      const balance = walletRes && walletRes.balance != null ? parseFloat(walletRes.balance) : 0
      const totalPrice = parseFloat(this.data.order.totalPrice) || 0

      if (balance < totalPrice) {
        wx.showModal({
          title: '余额不足',
          content: `余额 ¥${balance.toFixed(2)}，需付 ¥${totalPrice.toFixed(2)}`,
          confirmText: '去充值',
          success: (res) => {
            if (res.confirm) {
              app.navigateToPage('/pages/wallet/index')
            }
          }
        })
        return
      }

      wx.showModal({
        title: '确认支付',
        content: `扣除 ¥${totalPrice.toFixed(2)}，余额 ¥${balance.toFixed(2)}`,
        confirmText: '确认支付',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await request.post(`/orders/${id}/pay`)
            wx.showToast({ title: '支付成功', icon: 'success' })
            this.loadOrderDetail(id)
          } catch (err) {
            wx.showToast({ title: err.message || '支付失败', icon: 'none' })
          }
        }
      })
    } catch (err) {
      wx.showToast({ title: err.message || '支付失败', icon: 'none' })
    }
  }
})
