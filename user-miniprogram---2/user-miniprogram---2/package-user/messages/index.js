const app = getApp()
const request = require('../../utils/request')

const TYPE_TABS = [
  { label: '全部', value: '', badgeCount: 0 },
  { label: '订单', value: 'order', badgeCount: 0 },
  { label: '系统', value: 'system', badgeCount: 0 },
  { label: '活动', value: 'promotion', badgeCount: 0 }
]
const MESSAGE_TYPES = TYPE_TABS.filter((item) => item.value).map((item) => item.value)
const BADGE_FETCH_SIZE = 200
const MESSAGE_REALTIME_REFRESH_DEBOUNCE_MS = 180

Page({
  data: {
    typeTabs: TYPE_TABS,
    activeType: '',
    messages: [],
    skeletonList: [1, 2, 3],
    unreadMessageCount: 0,
    page: 1,
    pageSize: 20,
    hasMore: true,
    loading: true,
    loadingMore: false,
    refreshing: false
  },

  onLoad() {
    if (!app.requireLogin('/pages/messages/index')) {
      return
    }

    this.bindRealtime()
    this.initializePage()
  },

  onShow() {
    this.bindRealtime()
    this.setData({
      unreadMessageCount: app.getMessageUnreadCount()
    })
    this.refreshUnreadCount().catch(() => {})
  },

  onHide() {
    this.unbindRealtime()
  },

  onUnload() {
    this.unbindRealtime()
    if (this._messageRealtimeTimer) {
      clearTimeout(this._messageRealtimeTimer)
      this._messageRealtimeTimer = null
    }
  },

  async initializePage() {
    await Promise.all([
      this.loadMessages(true),
      this.refreshUnreadCount()
    ])
  },

  async onRefresh() {
    await Promise.all([
      this.loadMessages(true),
      this.refreshUnreadCount()
    ])
    this.setData({ refreshing: false })
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingMore) {
      this.loadMessages(false)
    }
  },

  switchType(e) {
    const { type } = e.currentTarget.dataset
    if (type === this.data.activeType) {
      return
    }

    this.setData({ activeType: type || '' })
    this.loadMessages(true)
  },

  async loadMessages(refresh = false) {
    if (refresh) {
      this.setData({
        page: 1,
        hasMore: true,
        loading: true
      })
    } else if (!this.data.hasMore || this.data.loadingMore) {
      return
    }

    const page = refresh ? 1 : this.data.page

    if (!refresh) {
      this.setData({ loadingMore: true })
    }

    try {
      const res = await request.get(
        '/messages',
        {
          page,
          size: this.data.pageSize,
          type: this.data.activeType || undefined
        },
        { showLoading: false }
      )

      const records = (res && res.records) || []
      const messages = records.map((item) => this.formatMessage(item))

      this.setData({
        messages: refresh ? messages : [...this.data.messages, ...messages],
        page: page + 1,
        hasMore: records.length >= this.data.pageSize,
        loading: false,
        loadingMore: false
      })
    } catch (error) {
      console.error('获取消息列表失败', error)
      this.setData({
        messages: refresh ? [] : this.data.messages,
        hasMore: false,
        loading: false,
        loadingMore: false
      })
    }
  },

  formatMessage(item) {
    const type = item.type || 'system'
    const routeInfo = this.getMessageRouteInfo({ ...item, type })

    return {
      id: item.id,
      title: item.title || '消息通知',
      content: item.content || '暂无消息内容',
      relatedId: item.relatedId,
      isRead: Number(item.isRead) === 1,
      type,
      typeText: this.getTypeText(type),
      typeClass: `type-${type}`,
      createTimeText: this.formatTime(item.createTime),
      primaryUrl: routeInfo.primaryUrl,
      primaryActionText: routeInfo.primaryActionText,
      secondaryUrl: routeInfo.secondaryUrl,
      secondaryActionText: routeInfo.secondaryActionText
    }
  },

  getTypeText(type) {
    switch (type) {
      case 'order':
        return '订单消息'
      case 'promotion':
        return '活动提醒'
      default:
        return '系统通知'
    }
  },

  formatTime(value) {
    if (!value) {
      return ''
    }

    return String(value).replace('T', ' ').slice(0, 16)
  },

  getMessageRouteInfo(message) {
    if (message.type === 'order') {
      return this.getOrderRouteInfo(message)
    }

    return this.getGeneralRouteInfo(message)
  },

  getGeneralRouteInfo(message) {
    const text = `${message.title || ''} ${message.content || ''}`

    if (message.type === 'promotion' || /优惠券|领券|满减|折扣|活动/.test(text)) {
      return {
        primaryUrl: '/pages/coupons/index',
        primaryActionText: '查看优惠券',
        secondaryUrl: '/pages/explore/index',
        secondaryActionText: '去逛逛'
      }
    }

    if (/余额|充值|退款|钱包/.test(text)) {
      return {
        primaryUrl: '/pages/wallet/index',
        primaryActionText: '钱包明细',
        secondaryUrl: '/pages/profile/index',
        secondaryActionText: '个人中心'
      }
    }

    if (/地址|收货/.test(text)) {
      return {
        primaryUrl: '/pages/address/index',
        primaryActionText: '地址管理',
        secondaryUrl: '/pages/profile/index',
        secondaryActionText: '个人中心'
      }
    }

    if (/设置|账号|密码|安全|登录/.test(text)) {
      return {
        primaryUrl: '/pages/settings/index',
        primaryActionText: '账号设置',
        secondaryUrl: '/pages/profile/index',
        secondaryActionText: '个人中心'
      }
    }

    return {
      primaryUrl: '/pages/profile/index',
      primaryActionText: '个人中心',
      secondaryUrl: '',
      secondaryActionText: ''
    }
  },

  getOrderRouteInfo(message) {
    const listType = this.getOrderListType(message)
    const detailUrl = message.relatedId ? `/pages/order-detail/index?id=${message.relatedId}` : ''
    const listUrl = `/pages/orders/index?type=${listType}`
    const listActionText = this.getOrderListActionText(listType)

    return {
      primaryUrl: detailUrl || listUrl,
      primaryActionText: detailUrl ? '订单详情' : listActionText,
      secondaryUrl: detailUrl ? listUrl : '',
      secondaryActionText: detailUrl ? listActionText : ''
    }
  },

  getOrderListType(message) {
    const text = `${message.title || ''} ${message.content || ''}`

    if (/退款|售后/.test(text)) {
      return 'aftersale'
    }
    if (/待支付|已创建|未支付/.test(text)) {
      return 'unpaid'
    }
    if (/支付成功|已支付|待发货/.test(text)) {
      return 'unshipped'
    }
    if (/已发货|待收货/.test(text)) {
      return 'unreceived'
    }
    if (/已完成|交易完成|确认收货/.test(text)) {
      return 'completed'
    }

    return 'all'
  },

  getOrderListActionText(type) {
    switch (type) {
      case 'unpaid':
        return '待付款订单'
      case 'unshipped':
        return '待发货订单'
      case 'unreceived':
        return '待收货订单'
      case 'completed':
        return '已完成订单'
      case 'aftersale':
        return '售后列表'
      default:
        return '订单列表'
    }
  },

  async refreshUnreadCount() {
    if (!app.isLoggedIn()) {
      const nextTabs = TYPE_TABS.map((item) => ({ ...item, badgeCount: 0 }))
      this.setData({
        unreadMessageCount: 0,
        typeTabs: nextTabs
      })
      return 0
    }

    const [count, ...typeResponses] = await Promise.all([
      app.refreshMessageUnreadCount().catch(() => app.getMessageUnreadCount()),
      ...MESSAGE_TYPES.map((type) =>
        request.get(
          '/messages',
          { page: 1, size: BADGE_FETCH_SIZE, type },
          { showLoading: false }
        ).catch(() => null)
      )
    ])

    const typeCountMap = {}
    typeResponses.forEach((res, index) => {
      const type = MESSAGE_TYPES[index]
      const records = (res && Array.isArray(res.records)) ? res.records : []
      typeCountMap[type] = records.reduce(
        (sum, item) => sum + (Number(item && item.isRead) === 1 ? 0 : 1),
        0
      )
    })

    const fallbackTotal = Object.values(typeCountMap).reduce((sum, item) => sum + Number(item || 0), 0)
    const unreadMessageCount = Math.max(0, Number(count || 0), fallbackTotal)
    const nextTabs = TYPE_TABS.map((item) => ({
      ...item,
      badgeCount: item.value ? Number(typeCountMap[item.value] || 0) : unreadMessageCount
    }))

    this.setData({
      unreadMessageCount,
      typeTabs: nextTabs
    })

    if (typeof app.syncMessageUnreadCount === 'function') {
      app.syncMessageUnreadCount(unreadMessageCount)
    }

    if (typeof app.getProfileAlertDetail === 'function' && typeof app.syncProfileAlertState === 'function') {
      const detail = app.getProfileAlertDetail() || {}
      app.syncProfileAlertState({
        ...detail,
        messageUnreadCount: unreadMessageCount
      })
    }

    return unreadMessageCount
  },

  bindRealtime() {
    if (this._unsubscribeUserMessageRealtime || typeof app.onRealtimeEvent !== 'function') {
      return
    }
    this._unsubscribeUserMessageRealtime = app.onRealtimeEvent('user-message-refresh', (payload) => {
      this.handleMessageRealtime(payload)
    })
  },

  unbindRealtime() {
    if (typeof this._unsubscribeUserMessageRealtime === 'function') {
      this._unsubscribeUserMessageRealtime()
      this._unsubscribeUserMessageRealtime = null
    }
  },

  scheduleMessageRealtimeRefresh() {
    if (this._messageRealtimeTimer) {
      return
    }
    this._messageRealtimeTimer = setTimeout(() => {
      this._messageRealtimeTimer = null
      Promise.all([
        this.loadMessages(true),
        this.refreshUnreadCount()
      ]).catch(() => {})
    }, MESSAGE_REALTIME_REFRESH_DEBOUNCE_MS)
  },

  handleMessageRealtime(payload = {}) {
    const eventName = `${payload.event || ''}`
    if (!eventName.startsWith('user-message-')) {
      return
    }
    this.scheduleMessageRealtimeRefresh()
  },

  findMessageById(id) {
    return this.data.messages.find((item) => item.id === id)
  },

  async ensureMessageRead(message) {
    if (!message || message.isRead) {
      return true
    }

    try {
      await request.put(`/messages/${message.id}/read`, {}, { showLoading: false })
      this.markMessageAsRead(message.id)
      return true
    } catch (error) {
      console.error('标记消息已读失败', error)
      return false
    }
  },

  navigateByUrl(url) {
    if (!url) {
      return
    }

    app.navigateToPage(url)
  },

  async handleQuickNavigate(e) {
    const { id, url } = e.currentTarget.dataset
    const message = this.findMessageById(id)

    if (!message || !url) {
      return
    }

    await this.ensureMessageRead(message)
    this.navigateByUrl(url)
  },

  async handleMessageTap(e) {
    const { id } = e.currentTarget.dataset
    const message = this.findMessageById(id)

    if (!message) {
      return
    }

    await this.ensureMessageRead(message)

    const primaryUrl = message.primaryUrl || message.secondaryUrl
    const confirmText = message.primaryActionText || message.secondaryActionText || '我知道了'

    wx.showModal({
      title: message.title,
      content: message.content,
      confirmText,
      cancelText: primaryUrl ? '关闭' : '我知道了',
      showCancel: !!primaryUrl,
      success: (res) => {
        if (res.confirm && primaryUrl) {
          this.navigateByUrl(primaryUrl)
        }
      }
    })
  },

  markMessageAsRead(id) {
    const index = this.data.messages.findIndex((item) => item.id === id)

    if (index === -1 || this.data.messages[index].isRead) {
      return
    }

    const targetMessage = this.data.messages[index]
    const unreadMessageCount = app.syncMessageUnreadCount(this.data.unreadMessageCount - 1)
    const nextTabs = (this.data.typeTabs || TYPE_TABS).map((item) => {
      if (!item.value) {
        return {
          ...item,
          badgeCount: Math.max(0, Number(item.badgeCount || 0) - 1)
        }
      }

      if (item.value === targetMessage.type) {
        return {
          ...item,
          badgeCount: Math.max(0, Number(item.badgeCount || 0) - 1)
        }
      }

      return item
    })

    this.setData({
      [`messages[${index}].isRead`]: true,
      unreadMessageCount,
      typeTabs: nextTabs
    })

    if (typeof app.getProfileAlertDetail === 'function' && typeof app.syncProfileAlertState === 'function') {
      const detail = app.getProfileAlertDetail() || {}
      app.syncProfileAlertState({
        ...detail,
        messageUnreadCount: unreadMessageCount
      })
    }
  },

  async markAllRead() {
    if (this.data.unreadMessageCount <= 0) {
      return
    }

    try {
      await request.put('/messages/read-all', {}, { showLoading: false })

      const nextData = {
        unreadMessageCount: app.syncMessageUnreadCount(0),
        typeTabs: (this.data.typeTabs || TYPE_TABS).map((item) => ({
          ...item,
          badgeCount: 0
        }))
      }

      this.data.messages.forEach((item, index) => {
        if (!item.isRead) {
          nextData[`messages[${index}].isRead`] = true
        }
      })

      this.setData(nextData)

      if (typeof app.getProfileAlertDetail === 'function' && typeof app.syncProfileAlertState === 'function') {
        const detail = app.getProfileAlertDetail() || {}
        app.syncProfileAlertState({
          ...detail,
          messageUnreadCount: 0
        })
      }

      wx.showToast({
        title: '已全部标记已读',
        icon: 'success'
      })
    } catch (error) {
      console.error('全部已读失败', error)
    }
  }
})
