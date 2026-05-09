const app = getApp()
const im = require('../../utils/im')
const IM_REFRESH_DEBOUNCE_MS = 120
const QUEUE_FALLBACK_REFRESH_MS = 45000

function roleLabel(role) {
  if (role === 'user') return '我'
  if (role === 'merchant') return '商家客服'
  if (role === 'admin') return '官方客服'
  if (role === 'ai') return '智能客服'
  return '系统'
}

function buildRequestId(prefix) {
  return `${prefix || 'msg'}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

Page({
  data: {
    loading: true,
    refreshing: false,
    sending: false,
    requestingHuman: false,
    conversationId: null,
    conversation: null,
    messages: [],
    inputValue: '',
    scrollIntoView: '',
    navTitle: '客服会话',
    queueHint: ''
  },

  onLoad(options = {}) {
    if (!app.requireLogin('/pages/customer-service/index')) {
      return
    }
    this._destroyed = false
    this.initialize(options)
  },

  onShow() {
    this._destroyed = false
    this.bindRealtime()
    this.updateQueueRealtimeRefreshStrategy(this.data.conversation)
  },

  onHide() {
    this.unbindRealtime()
    this.stopQueueFallbackRefresh()
  },

  onUnload() {
    this._destroyed = true
    this.unbindRealtime()
    this.stopQueueFallbackRefresh()
    if (this._imRealtimeTimer) {
      clearTimeout(this._imRealtimeTimer)
      this._imRealtimeTimer = null
    }
  },

  safeSetData(nextData, callback) {
    if (this._destroyed || !nextData) {
      return false
    }
    try {
      this.setData(nextData, callback)
      return true
    } catch (error) {
      return false
    }
  },

  async initialize(options = {}) {
    try {
      const conversationId = await this.ensureConversationId(options)
      if (!conversationId) {
        wx.showToast({
          title: '会话不存在',
          icon: 'none'
        })
        wx.navigateBack()
        return
      }
      this.safeSetData({ conversationId })
      await this.refreshAll({ showLoading: true })
    } catch (error) {
      console.error('初始化客服会话失败', error)
      wx.showToast({
        title: '打开会话失败',
        icon: 'none'
      })
      wx.navigateBack()
    }
  },

  async ensureConversationId(options = {}) {
    const directId = im.parsePositiveNumber(options.conversationId)
    if (directId) {
      return directId
    }

    const openType = `${options.openType || ''}`.trim()
    const productId = im.parsePositiveNumber(options.productId)
    const orderId = im.parsePositiveNumber(options.orderId)
    const merchantId = im.parsePositiveNumber(options.merchantId)

    if (openType === 'merchant') {
      const conversation = await im.openMerchantConversation({
        productId: productId || undefined,
        orderId: orderId || undefined,
        merchantId: merchantId || undefined
      }, { showLoading: true })
      return im.parsePositiveNumber(conversation && conversation.id)
    }

    if (openType === 'support') {
      const conversation = await im.openSupportConversation({
        productId: productId || undefined,
        orderId: orderId || undefined,
        issueType: 'consult',
        summary: orderId ? '订单咨询' : '商品咨询'
      }, { showLoading: true })
      return im.parsePositiveNumber(conversation && conversation.id)
    }

    return null
  },

  async onRefresh() {
    this.safeSetData({ refreshing: true })
    await this.refreshAll({ showLoading: false })
    this.safeSetData({ refreshing: false })
  },

  async refreshAll({ showLoading = false } = {}) {
    const conversationId = this.data.conversationId
    if (!conversationId) {
      return
    }
    if (showLoading) {
      this.safeSetData({ loading: true })
    }

    try {
      const [conversation, messagePage] = await Promise.all([
        im.getConversationDetail(conversationId, { showLoading: false }),
        im.getConversationMessages(conversationId, { page: 1, size: 200 }, { showLoading: false })
      ])
      const messages = im.normalizePage(messagePage).records.map((item) => this.formatMessage(item))
      const title = this.getConversationTitle(conversation)
      const lastMessage = messages.length ? messages[messages.length - 1] : null
      const queueHint = this.getQueueHint(conversation)
      this.safeSetData({
        conversation,
        messages,
        navTitle: title,
        queueHint,
        loading: false,
        scrollIntoView: lastMessage ? `msg-${lastMessage.id}` : ''
      })
      this.updateQueueRealtimeRefreshStrategy(conversation)
      im.markConversationRead(conversationId, { showLoading: false }).catch(() => {})
    } catch (error) {
      console.error('刷新会话失败', error)
      this.safeSetData({ loading: false, messages: [] })
    }
  },

  formatMessage(item) {
    const senderRole = item.senderRole || 'system'
    const payload = item && item.payload && typeof item.payload === 'object' ? item.payload : {}
    const isAi = senderRole === 'ai' || item.messageType === 'ai' || !!payload.isAi
    const isSystem = (!isAi) && (!!item.isSystem || senderRole === 'system')
    const isSelf = senderRole === 'user' && !isSystem
    return {
      id: item.id,
      senderRole,
      senderName: isAi ? '智能客服' : (item.senderName || roleLabel(senderRole)),
      content: item.content || '',
      createTime: item.createTime || '',
      isAi,
      isSystem,
      isSelf
    }
  },

  getConversationTitle(conversation) {
    if (!conversation) {
      return '客服会话'
    }
    const counterpart = conversation.counterpart || {}
    if (counterpart.name) {
      return counterpart.name
    }
    if (conversation.conversationType === 'support') {
      return conversation.status === 'ai_serving' ? '智能客服' : '官方客服'
    }
    return '商家客服'
  },

  getQueueHint(conversation) {
    if (!conversation || conversation.conversationType !== 'support') {
      return ''
    }
    const queue = conversation.queue
    if (!queue || !queue.position) {
      return ''
    }
    return `人工排队中：第 ${queue.position} 位，预计 ${queue.estimatedWaitMinutes || '--'} 分钟`
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
      this.refreshAll({ showLoading: false }).catch(() => {})
    }, IM_REFRESH_DEBOUNCE_MS)
  },

  handleImRealtime(payload = {}) {
    const eventName = `${payload.event || ''}`
    if (!eventName.startsWith('im-')) {
      return
    }
    const currentConversationId = im.parsePositiveNumber(this.data.conversationId)
    if (!currentConversationId) {
      return
    }
    const payloadConversationId = im.parsePositiveNumber(payload.conversationId)
    if (payloadConversationId && payloadConversationId !== currentConversationId) {
      return
    }
    this.scheduleImRealtimeRefresh()
  },

  updateQueueRealtimeRefreshStrategy(conversation) {
    if (
      conversation &&
      conversation.conversationType === 'support' &&
      conversation.status === 'pending_support'
    ) {
      this.startQueueFallbackRefresh()
      return
    }
    this.stopQueueFallbackRefresh()
  },

  startQueueFallbackRefresh() {
    this.stopQueueFallbackRefresh()
    this._queueFallbackTimer = setTimeout(async () => {
      this._queueFallbackTimer = null
      await this.refreshAll({ showLoading: false }).catch(() => {})
      const latestConversation = this.data.conversation
      if (
        latestConversation &&
        latestConversation.conversationType === 'support' &&
        latestConversation.status === 'pending_support'
      ) {
        this.startQueueFallbackRefresh()
      }
    }, QUEUE_FALLBACK_REFRESH_MS)
  },

  stopQueueFallbackRefresh() {
    if (this._queueFallbackTimer) {
      clearTimeout(this._queueFallbackTimer)
      this._queueFallbackTimer = null
    }
  },

  onInputChange(e) {
    this.safeSetData({ inputValue: e.detail.value || '' })
  },

  async sendMessage() {
    const text = `${this.data.inputValue || ''}`.trim()
    if (!text || this.data.sending || !this.data.conversationId) {
      return
    }

    this.safeSetData({ sending: true })
    try {
      await im.sendConversationMessage(this.data.conversationId, {
        content: text,
        messageType: 'text',
        requestId: buildRequestId('wx')
      }, { showLoading: false })
      this.safeSetData({ inputValue: '' })
      await this.refreshAll({ showLoading: false })
    } catch (error) {
      console.error('发送消息失败', error)
      wx.showToast({
        title: '发送失败',
        icon: 'none'
      })
    } finally {
      this.safeSetData({ sending: false })
    }
  },

  async requestHumanSupport() {
    const conversation = this.data.conversation
    if (!conversation || !this.data.conversationId || this.data.requestingHuman) {
      return
    }
    if (conversation.conversationType !== 'support') {
      return
    }
    if (conversation.status === 'pending_support') {
      wx.showToast({
        title: '已在人工排队中',
        icon: 'none'
      })
      return
    }
    if (conversation.status === 'open' && conversation.supportAgent) {
      wx.showToast({
        title: '人工客服已接入',
        icon: 'none'
      })
      return
    }

    const confirmed = await new Promise((resolve) => {
      wx.showModal({
        title: '转人工客服',
        content: '确认转人工后将进入排队，是否继续？',
        confirmText: '继续',
        success: (res) => resolve(!!res.confirm),
        fail: () => resolve(false)
      })
    })
    if (!confirmed) {
      return
    }

    this.safeSetData({ requestingHuman: true })
    try {
      const res = await im.requestHumanSupport(this.data.conversationId, {
        issueType: 'manual_support',
        summary: '用户请求转人工客服'
      }, { showLoading: true })
      const queue = res && res.queue ? res.queue : (res && res.conversation && res.conversation.queue ? res.conversation.queue : null)
      if (queue && queue.position) {
        wx.showToast({
          title: `已排队第${queue.position}位`,
          icon: 'none'
        })
      } else {
        wx.showToast({
          title: '已提交人工请求',
          icon: 'success'
        })
      }
      await this.refreshAll({ showLoading: false })
    } catch (error) {
      console.error('转人工失败', error)
    } finally {
      this.safeSetData({ requestingHuman: false })
    }
  },

  async handleEscalate() {
    const conversation = this.data.conversation
    if (!conversation || !this.data.conversationId) {
      return
    }
    if (conversation.conversationType === 'support' || conversation.isEscalated) {
      wx.showToast({
        title: '已接入官方客服',
        icon: 'none'
      })
      return
    }

    const confirmed = await new Promise((resolve) => {
      wx.showModal({
        title: '申请平台介入',
        content: '平台客服将进入会话。',
        confirmText: '申请介入',
        success: (res) => resolve(!!res.confirm),
        fail: () => resolve(false)
      })
    })
    if (!confirmed) {
      return
    }

    try {
      await im.escalateConversation(this.data.conversationId, {
        issueType: 'dispute',
        summary: '用户申请平台介入',
        detail: '来自小程序会话申请平台介入'
      }, { showLoading: true })
      wx.showToast({
        title: '已通知官方客服',
        icon: 'success'
      })
      await this.refreshAll({ showLoading: false })
    } catch (error) {
      console.error('申请介入失败', error)
    }
  },

  openOrderDetail() {
    const conversation = this.data.conversation
    const orderId = im.parsePositiveNumber(conversation && conversation.context && conversation.context.order && conversation.context.order.id)
    if (!orderId) {
      return
    }
    app.navigateToPage(`/pages/order-detail/index?id=${orderId}`)
  },

  openProductDetail() {
    const conversation = this.data.conversation
    const productId = im.parsePositiveNumber(conversation && conversation.context && conversation.context.product && conversation.context.product.id)
    if (!productId) {
      return
    }
    app.navigateToPage(`/pages/product-detail/index?id=${productId}`)
  }
})

