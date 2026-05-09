const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const im = require('../../utils/im')

const REASON_OPTIONS = [
  '商品信息描述不符',
  '商品有质量问题',
  '发货太慢不想等了',
  '买错了/不想要了',
  '收到商品破损'
]

function mapOrderStatus(status) {
  switch (status) {
    case 1:
      return '待发货'
    case 2:
      return '待收货'
    case 3:
      return '已完成'
    case 5:
      return '已退款'
    default:
      return '暂不可退'
  }
}

function canApplyRefund(status) {
  return [1, 2, 3].includes(status)
}

Page({
  data: {
    orderId: null,
    order: null,
    reason: '',
    reasonOptions: REASON_OPTIONS,
    loading: true,
    submitting: false
  },

  onLoad(options) {
    const orderId = Number(options.id)
    if (!orderId || !app.requireLogin(`/pages/refund-apply/index?id=${options.id || ''}`)) {
      return
    }

    this.setData({ orderId })
    this.loadOrderDetail(orderId)
  },

  async loadOrderDetail(orderId) {
    try {
      const res = await request.get(`/orders/${orderId}`)
      if (!canApplyRefund(res.status)) {
        wx.showToast({ title: '暂不可退', icon: 'none' })
        setTimeout(() => wx.navigateBack({ delta: 1 }), 1200)
        return
      }

      this.setData({
        order: {
          id: res.id,
          orderNo: res.orderNo,
          statusText: mapOrderStatus(res.status),
          totalPrice: res.totalAmount,
          createTime: res.createTime,
          goods: Array.isArray(res.items)
            ? res.items.map((item) => ({
                id: item.id,
                productId: item.productId || null,
                title: item.productName || '',
                image: resolveProductImage(item),
                price: item.price,
                quantity: item.quantity
              }))
            : []
        },
        loading: false
      })
    } catch (error) {
      console.error('获取退款订单详情失败', error)
      this.setData({ loading: false, order: null })
      wx.showToast({ title: '加载订单失败', icon: 'none' })
    }
  },

  onReasonInput(e) {
    this.setData({ reason: e.detail.value || '' })
  },

  chooseReason(e) {
    const reason = e.currentTarget.dataset.reason || ''
    this.setData({ reason })
  },

  async submitRefund() {
    const { orderId, reason, submitting } = this.data
    const normalizedReason = (reason || '').trim()

    if (submitting) {
      return
    }
    if (!normalizedReason) {
      wx.showToast({ title: '请先填写退款原因', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      await request.post('/refunds', {
        orderId,
        reason: normalizedReason
      })
      app.refreshProfileBadgeSummary().catch(() => {})
      wx.showToast({ title: '退款申请已提交', icon: 'success' })
      setTimeout(() => {
        app.navigateToPage('/pages/orders/index?type=aftersale')
      }, 900)
    } catch (error) {
      wx.showToast({ title: error.message || '提交退款失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  handleRefundGoodsImageError(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (!Number.isInteger(index)) {
      return
    }
    this.setData({
      [`order.goods[${index}].image`]: ''
    })
  },

  openMerchantService() {
    if (!app.requireLogin(`/pages/refund-apply/index?id=${this.data.orderId || ''}`)) {
      return
    }
    app.navigateToPage(`/pages/customer-chat/index?${this.buildServiceQuery('merchant')}`)
  },

  openOfficialService() {
    if (!app.requireLogin(`/pages/refund-apply/index?id=${this.data.orderId || ''}`)) {
      return
    }
    app.navigateToPage(`/pages/customer-chat/index?${this.buildServiceQuery('support')}`)
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
  }
})
