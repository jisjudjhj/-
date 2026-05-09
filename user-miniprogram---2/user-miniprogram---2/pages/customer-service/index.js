const app = getApp()
const im = require('../../utils/im')
const IM_LIST_REFRESH_DEBOUNCE_MS = 160

const TYPE_TABS = [
  { label: '全部', value: '' },
  { label: '商家协商', value: 'merchant' },
  { label: '官方客服', value: 'support' }
]

function formatTime(value) {
  if (!value) {
    return ''
  }
  return String(value).replace('T', ' ').slice(5, 16)
}

Page({
  data: {
    tabs: TYPE_TABS,
    activeType: '',
    loading: true,
    refreshing: false,
    opening: false,
    conversations: [],
    unreadCount: 0
  },

  onLoad(options = {}) {
    if (!app.requireLogin('/pages/customer-service/index')) {
      return
    }
    this._destroyed = false
    this.launchPage(options)
  },

  onShow() {
    this._destroyed = false
    if (!app.isLoggedIn()) {
      return
    }
    this.bindRealtime()
    this.refreshUnreadCount().catch(() => {})
  },

  onHide() {
    this.unbindRealtime()
  },

  onUnload() {
    this._destroyed = true
    this.unbindRealtime()
    if (this._imRealtimeTimer) {
      clearTimeout(this._imRealtimeTimer)
      this._imRealtimeTimer = null
    }
  },

  safeSetData(nextData) {
    if (this._destroyed || !nextData) {
      return false
    }
    try {
      this.setData(nextData)
      return true
    } catch (error) {
      return false
    }
  },

  async launchPage(options = {}) {
    this.bindRealtime()
    const redirected = await this.openByScene(options)
    if (redirected) {
      return
    }
    await Promise.all([
      this.fetchConversations({ showSkeleton: true }),
      this.refreshUnreadCount()
    ])
  },

  async openByScene(options = {}) {
    const openType = `${options.openType || options.type || ''}`.trim()
    if (openType !== 'merchant' && openType !== 'support') {
      return false
    }
    if (this.data.opening) {
      return true
    }

    const productId = im.parsePositiveNumber(options.productId)
    const orderId = im.parsePositiveNumber(options.orderId)
    const merchantId = im.parsePositiveNumber(options.merchantId)

    this.safeSetData({ opening: true })
    try {
      let conversation = null
      if (openType === 'merchant') {
        conversation = await im.openMerchantConversation({
          productId: productId || undefined,
          orderId: orderId || undefined,
          merchantId: merchantId || undefined
        }, { showLoading: true })
      } else {
        conversation = await im.openSupportConversation({
          productId: productId || undefined,
          orderId: orderId || undefined,
          issueType: 'consult',
          summary: orderId ? '订单咨询' : '商品咨询'
        }, { showLoading: true })
      }

      const conversationId = im.parsePositiveNumber(conversation && conversation.id)
      if (conversationId) {
        app.navigateToPage(`/pages/customer-chat/index?conversationId=${conversationId}`)
        return true
      }
    } catch (error) {
      console.error('按场景打开客服会话失败', error)
      wx.showToast({
        title: '进入客服失败',
        icon: 'none'
      })
    } finally {
      this.safeSetData({ opening: false })
    }
    return false
  },

  async onRefresh() {
    this.safeSetData({ refreshing: true })
    await Promise.all([
      this.fetchConversations({ showSkeleton: false }),
      this.refreshUnreadCount()
    ])
    this.safeSetData({ refreshing: false })
  },

  switchType(e) {
    const type = `${e.currentTarget.dataset.type || ''}`
    if (type === this.data.activeType) {
      return
    }
    this.safeSetData({ activeType: type })
    this.fetchConversations({ showSkeleton: true })
  },

  async fetchConversations({ showSkeleton = false } = {}) {
    if (showSkeleton) {
      this.safeSetData({ loading: true })
    }
    try {
      const res = await im.getConversationList({
        page: 1,
        size: 50,
        conversationType: this.data.activeType || undefined
      }, {
        showLoading: false
      })
      const pageData = im.normalizePage(res)
      const conversations = pageData.records.map((item) => this.buildConversationCard(item))
      this.safeSetData({
        conversations,
        loading: false
      })
    } catch (error) {
      console.error('获取会话列表失败', error)
      this.safeSetData({
        conversations: [],
        loading: false
      })
    }
  },

  async refreshUnreadCount() {
    if (!app.isLoggedIn()) {
      this.safeSetData({ unreadCount: 0 })
      return 0
    }
    try {
      const res = await im.getConversationUnreadCount({ showLoading: false })
      const unreadCount = Number(res && res.count ? res.count : 0)
      this.safeSetData({ unreadCount })
      return unreadCount
    } catch (error) {
      console.error('获取客服未读数失败', error)
      this.safeSetData({ unreadCount: 0 })
      return 0
    }
  },

  bindRealtime() {
    if (this._unsubscribeImRealtime || typeof app.onRealtimeEvent !== 'function') {
      return
    }
    this._unsubscribeImRealtime = app.onRealtimeEvent('im-refresh', (payload) => {
      this.handleImRealtime(payload)
    })
  },

  unbindRealtime() {
    if (typeof this._unsubscribeImRealtime === 'function') {
      this._unsubscribeImRealtime()
      this._unsubscribeImRealtime = null
    }
  },

  scheduleImRealtimeRefresh() {
    if (this._imRealtimeTimer) {
      return
    }
    this._imRealtimeTimer = setTimeout(() => {
      this._imRealtimeTimer = null
      Promise.all([
        this.fetchConversations({ showSkeleton: false }),
        this.refreshUnreadCount()
      ]).catch(() => {})
    }, IM_LIST_REFRESH_DEBOUNCE_MS)
  },

  handleImRealtime(payload = {}) {
    const eventName = `${payload.event || ''}`
    if (!eventName.startsWith('im-')) {
      return
    }
    this.scheduleImRealtimeRefresh()
  },

  buildConversationCard(item) {
    const type = item && item.conversationType === 'support' ? 'support' : 'merchant'
    const counterpart = item && item.counterpart ? item.counterpart : {}
    const context = item && item.context ? item.context : {}
    const order = context.order || null
    const product = context.product || null
    const fallbackTitle = type === 'support' ? '官方客服' : '商家客服'
    const contextText = order
      ? `订单 ${order.orderNo || order.id || ''}`
      : (product ? `商品 ${product.name || product.id || ''}` : (item.conversationNo || '会话'))

    return {
      id: item.id,
      conversationType: type,
      typeText: type === 'support' ? '官方客服' : '商家协商',
      title: counterpart.name || fallbackTitle,
      subtitle: counterpart.subtitle || (type === 'support' ? '平台官方客服' : '与商家协商订单、物流与售后'),
      unreadCount: Number(item.unreadCount || 0),
      status: item.status || 'open',
      contextText,
      lastMessage: item.lastMessage || '暂无消息',
      lastMessageTime: formatTime(item.lastMessageTime),
      raw: item
    }
  },

  goConversation(e) {
    const id = im.parsePositiveNumber(e.currentTarget.dataset.id)
    if (!id) {
      return
    }
    app.navigateToPage(`/pages/customer-chat/index?conversationId=${id}`)
  },

  goOfficialSupport() {
    if (!app.requireLogin('/pages/customer-service/index')) {
      return
    }
    app.navigateToPage('/pages/customer-chat/index?openType=support')
  }
})
