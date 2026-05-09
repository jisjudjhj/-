const app = getApp()
const request = require('../../utils/request')

const SECKILL_COUNTDOWN_WINDOW_MS = 12 * 60 * 60 * 1000
const SECKILL_PAGE_LIMIT = 60
const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']
const TAB_LIST = [
  { label: '进行中', value: 'active', status: 1 },
  { label: '预告', value: 'upcoming', status: 0 },
  { label: '历史', value: 'history', status: 2 },
]
const SECKILL_STATUS = {
  UPCOMING: 0,
  ACTIVE: 1,
  ENDED: 2,
  SOLD_OUT: 3,
}

Page({
  data: {
    isScrolled: false,
    refreshing: false,
    seckillLoading: true,
    activeTab: 'active',
    tabs: TAB_LIST.map(item => ({ ...item, count: 0 })),
    displayGroups: [],
    timelineSessions: [],
    selectedGroupKey: '',
    scrollIntoView: '',
    timelineIntoView: '',
    seckillEmptyTitle: '暂无场次',
    seckillEmptyDesc: '',
  },

  onLoad() {
    this._destroyed = false
    this.loadSeckillProducts()
  },

  onShow() {
    this._destroyed = false
    if (Array.isArray(this._rawActivityGroups) && this._rawActivityGroups.length > 0) {
      this.refreshDisplayGroups(true)
      this.startSeckillTicker()
    }
  },

  onHide() {
    this._destroyed = true
    this.clearSeckillTicker()
    if (this._scrollIntoViewTimer) {
      clearTimeout(this._scrollIntoViewTimer)
      this._scrollIntoViewTimer = null
    }
  },

  onUnload() {
    this._destroyed = true
    this.clearSeckillTicker()
    if (this._scrollIntoViewTimer) {
      clearTimeout(this._scrollIntoViewTimer)
      this._scrollIntoViewTimer = null
    }
  },

  isPageInactive() {
    return !!this._destroyed
  },

  safeSetData(nextData, callback) {
    if (this.isPageInactive() || !nextData) {
      return false
    }
    try {
      this.setData(nextData, callback)
      return true
    } catch (error) {
      return false
    }
  },

  async onRefresh() {
    await this.loadSeckillProducts()
    this.safeSetData({ refreshing: false })
  },

  onScroll(e) {
    const scrollTop = Number(e.detail.scrollTop || 0)
    this._lastScrollTop = scrollTop

    const nextScrolled = scrollTop > 10
    if (nextScrolled !== this.data.isScrolled) {
      this.safeSetData({ isScrolled: nextScrolled })
    }
  },

  switchTab(e) {
    const value = e.currentTarget.dataset.value
    if (!value || value === this.data.activeTab) return
    this.safeSetData({
      activeTab: value,
      selectedGroupKey: '',
      scrollIntoView: '',
      timelineIntoView: '',
    })
    this.refreshDisplayGroups(true)
  },

  selectTimelineSession(e) {
    const groupKey = e.currentTarget.dataset.groupKey
    if (!groupKey) return
    const targetGroup = (this.data.displayGroups || []).find(item => item.groupKey === groupKey)
    if (!targetGroup) return

    if (this._scrollIntoViewTimer) {
      clearTimeout(this._scrollIntoViewTimer)
      this._scrollIntoViewTimer = null
    }

    this.safeSetData({
      selectedGroupKey: groupKey,
      scrollIntoView: targetGroup.anchorId,
      timelineIntoView: targetGroup.timelineId,
    })
    this.refreshDisplayGroups(true)

    this._scrollIntoViewTimer = setTimeout(() => {
      if (this.isPageInactive()) {
        return
      }
      this.safeSetData({ scrollIntoView: '' })
      this._scrollIntoViewTimer = null
    }, 420)
  },

  async loadSeckillProducts() {
    this.safeSetData({
      seckillLoading: true,
      seckillEmptyTitle: '暂无场次',
      seckillEmptyDesc: '',
    })
    try {
      const res = await request.get('/seckill/products', {
        limit: SECKILL_PAGE_LIMIT,
        includeHistory: true,
        groupByActivity: true,
      }, { showLoading: false })

      const moduleEnabled = !(res && res.moduleEnabled === false)
      const rawItems = Array.isArray(res)
        ? res
        : ((res && (res.items || res.records || res.list || res.data)) || [])
      const rawGroups = Array.isArray(res && res.activityGroups)
        ? res.activityGroups
        : this.buildGroupsFromItems(rawItems)
      const emptyTitle = moduleEnabled ? '暂无场次' : '未开启'
      const emptyDesc = moduleEnabled
        ? ''
        : (res.emptyReason || '')

      this._rawActivityGroups = rawGroups
      if (this.isPageInactive()) return
      this.safeSetData({
        seckillEmptyTitle: emptyTitle,
        seckillEmptyDesc: emptyDesc,
      })
      this.refreshDisplayGroups(true)
      this.startSeckillTicker()
    } catch (error) {
      console.error('加载秒杀会场失败', error)
      this._rawActivityGroups = []
      if (this.isPageInactive()) return
      this.safeSetData({
        displayGroups: [],
        timelineSessions: [],
        tabs: TAB_LIST.map(item => ({ ...item, count: 0 })),
        selectedGroupKey: '',
        scrollIntoView: '',
        timelineIntoView: '',
        seckillLoading: false,
        seckillEmptyTitle: '秒杀会场暂时不可用',
        seckillEmptyDesc: '加载失败',
      })
      this.clearSeckillTicker()
    } finally {
      if (!this.isPageInactive() && this.data.seckillLoading) {
        this.safeSetData({ seckillLoading: false })
      }
    }
  },

  buildGroupsFromItems(items = []) {
    const source = Array.isArray(items) ? items : []
    const groups = {}
    const order = []
    source.forEach((item) => {
      const activityId = Number(item.seckillActivityId || item.activityId || 0)
      const startTime = item.startTime || item.seckillStartTime || ''
      const endTime = item.endTime || item.seckillEndTime || ''
      const key = `${activityId}_${startTime}_${endTime}`
      if (!groups[key]) {
        groups[key] = {
          activityId,
          activityName: item.activityName || '秒杀场次',
          activityCoverImage: item.activityCoverImage || '',
          activityDescription: item.activityDescription || '',
          startTime,
          endTime,
          runtimeStatus: Number(item.runtimeStatus != null ? item.runtimeStatus : item.seckillStatus),
          items: [],
        }
        order.push(key)
      }
      groups[key].items.push(item)
    })
    return order.map(key => groups[key])
  },

  refreshDisplayGroups(forceUpdate = false) {
    if (this.isPageInactive()) {
      return
    }
    const rawGroups = Array.isArray(this._rawActivityGroups) ? this._rawActivityGroups : []
    const decoratedGroups = rawGroups
      .map(group => this.decorateGroup(group))
      .filter(group => group && Array.isArray(group.items) && group.items.length > 0)

    const tabs = TAB_LIST.map((tab) => ({
      ...tab,
      count: decoratedGroups.filter(group => group.runtimeStatus === tab.status).length,
    }))

    const validTabValues = tabs.filter(tab => tab.count > 0).map(tab => tab.value)
    let activeTab = this.data.activeTab
    if (!validTabValues.includes(activeTab)) {
      activeTab = validTabValues[0] || 'active'
    }

    const displayGroups = decoratedGroups.filter(group => this.mapStatusToTab(group.runtimeStatus) === activeTab)

    let selectedGroupKey = this.data.selectedGroupKey
    if (!displayGroups.some(group => group.groupKey === selectedGroupKey)) {
      selectedGroupKey = displayGroups.length ? displayGroups[0].groupKey : ''
    }

    const enhancedGroups = displayGroups.map((group, index) => ({
      ...group,
      isSelected: group.groupKey === selectedGroupKey,
      orderIndex: index + 1,
    }))

    const timelineSessions = enhancedGroups.map(group => this.buildTimelineSession(group, group.groupKey === selectedGroupKey))
    const selectedTimeline = timelineSessions.find(item => item.groupKey === selectedGroupKey)
    const timelineIntoView = selectedTimeline ? selectedTimeline.timelineId : ''

    if (forceUpdate || this.shouldUpdateDisplay(enhancedGroups, timelineSessions, tabs, activeTab, selectedGroupKey)) {
      this.safeSetData({
        tabs,
        activeTab,
        displayGroups: enhancedGroups,
        timelineSessions,
        selectedGroupKey,
        timelineIntoView,
        seckillLoading: false,
      })
      return
    }
  },

  shouldUpdateDisplay(nextGroups, nextTimeline, nextTabs, nextActiveTab, nextSelectedGroupKey) {
    if (nextActiveTab !== this.data.activeTab) return true
    if (nextSelectedGroupKey !== this.data.selectedGroupKey) return true

    const currentTabs = Array.isArray(this.data.tabs) ? this.data.tabs : []
    if (currentTabs.length !== nextTabs.length) return true
    for (let i = 0; i < nextTabs.length; i += 1) {
      if (currentTabs[i].count !== nextTabs[i].count) return true
    }

    const currentTimeline = Array.isArray(this.data.timelineSessions) ? this.data.timelineSessions : []
    if (currentTimeline.length !== nextTimeline.length) return true
    for (let i = 0; i < nextTimeline.length; i += 1) {
      const prev = currentTimeline[i]
      const next = nextTimeline[i]
      if (!prev || !next) return true
      if (prev.groupKey !== next.groupKey) return true
      if (prev.timeText !== next.timeText) return true
      if (prev.countdownText !== next.countdownText) return true
      if (!!prev.isSelected !== !!next.isSelected) return true
    }

    const currentGroups = Array.isArray(this.data.displayGroups) ? this.data.displayGroups : []
    if (currentGroups.length !== nextGroups.length) return true
    for (let i = 0; i < nextGroups.length; i += 1) {
      const prev = currentGroups[i]
      const next = nextGroups[i]
      if (!prev || !next) return true
      if (prev.groupKey !== next.groupKey) return true
      if (prev.countdownText !== next.countdownText) return true
      if (!!prev.isSelected !== !!next.isSelected) return true
      if ((prev.items || []).length !== (next.items || []).length) return true
      const prevItems = Array.isArray(prev.items) ? prev.items : []
      const nextItems = Array.isArray(next.items) ? next.items : []
      for (let j = 0; j < nextItems.length; j += 1) {
        if (prevItems[j].id !== nextItems[j].id) return true
        if (prevItems[j].countdownText !== nextItems[j].countdownText) return true
        if (prevItems[j].seckillStatus !== nextItems[j].seckillStatus) return true
      }
    }
    return false
  },

  decorateGroup(rawGroup = {}) {
    const runtimeStatus = Number(rawGroup.runtimeStatus)
    const startTime = rawGroup.startTime || ''
    const endTime = rawGroup.endTime || ''
    const statusInfo = this.getSeckillStatusLabel(runtimeStatus, startTime, endTime)
    const items = (Array.isArray(rawGroup.items) ? rawGroup.items : [])
      .map(item => this.decorateProduct(item, runtimeStatus, startTime, endTime))
      .filter(item => item.id > 0)
    const groupKey = `${rawGroup.activityId || 0}_${startTime}_${endTime}`

    return {
      groupKey,
      anchorId: this.buildAnchorId('group', groupKey),
      timelineId: this.buildAnchorId('timeline', groupKey),
      activityId: Number(rawGroup.activityId || 0),
      activityName: rawGroup.activityName || '秒杀场次',
      activityCoverImage: this.resolveActivityImage(rawGroup),
      activityDescription: rawGroup.activityDescription || '',
      startTime,
      endTime,
      runtimeStatus: statusInfo.status,
      statusLabel: statusInfo.label,
      countdownText: statusInfo.countdownText,
      timeText: this.formatTimeRange(startTime, endTime),
      dateText: this.formatCalendarDate(startTime),
      weekText: this.formatCalendarWeek(startTime),
      timeShortText: this.formatShortClock(startTime),
      productCount: items.length,
      remainingStock: items.reduce((sum, item) => sum + Number(item.remainingStock || 0), 0),
      items,
    }
  },

  buildTimelineSession(group, isSelected) {
    return {
      groupKey: group.groupKey,
      timelineId: group.timelineId,
      dateText: group.dateText,
      weekText: group.weekText,
      timeText: group.timeShortText,
      title: this.formatTimelineTitle(group.activityName),
      statusLabel: group.statusLabel,
      countdownText: group.countdownText,
      productCount: group.productCount,
      isSelected,
      runtimeStatus: group.runtimeStatus,
    }
  },

  decorateProduct(rawItem = {}, fallbackStatus, fallbackStartTime, fallbackEndTime) {
    const originalPrice = Number(rawItem.originalPrice != null
      ? rawItem.originalPrice
      : (rawItem.productPrice != null ? rawItem.productPrice : rawItem.price || 0))
    const seckillPrice = Number(rawItem.seckillPrice != null ? rawItem.seckillPrice : rawItem.price || 0)
    const stock = Number(rawItem.seckillStock != null ? rawItem.seckillStock : rawItem.stock || 0)
    const soldCount = Number(rawItem.soldCount != null ? rawItem.soldCount : rawItem.seckillSoldCount || 0)
    const remainingStock = Number(rawItem.remainingStock != null ? rawItem.remainingStock : (stock - soldCount))
    const startTime = rawItem.startTime || rawItem.seckillStartTime || fallbackStartTime || ''
    const endTime = rawItem.endTime || rawItem.seckillEndTime || fallbackEndTime || ''
    const runtimeStatus = rawItem.runtimeStatus != null ? rawItem.runtimeStatus : fallbackStatus
    const rawSeckillStatus = rawItem.seckillStatus != null ? rawItem.seckillStatus : runtimeStatus
    const safeSeckillPrice = Number.isFinite(seckillPrice) ? seckillPrice : 0
    const safeOriginalPrice = Number.isFinite(originalPrice) ? originalPrice : 0
    const safeStock = Number.isFinite(stock) ? Math.max(0, stock) : 0
    const safeSoldCount = Number.isFinite(soldCount) ? Math.max(0, soldCount) : 0
    const safeRemainingStock = Number.isFinite(remainingStock) ? Math.max(0, remainingStock) : 0
    const totalSeckillStock = Math.max(safeStock, safeSoldCount + safeRemainingStock, 1)
    const progressPercent = Math.max(8, Math.min(100, Math.round((safeSoldCount / totalSeckillStock) * 100)))
    const effectiveStatus = (runtimeStatus === SECKILL_STATUS.ACTIVE && safeRemainingStock <= 0)
      ? SECKILL_STATUS.SOLD_OUT
      : Number(rawSeckillStatus)
    const statusInfo = this.getSeckillStatusLabel(effectiveStatus, startTime, endTime)

    return {
      id: Number(rawItem.productId || rawItem.id || 0),
      image: this.resolveProductImage(rawItem),
      title: rawItem.productName || rawItem.name || '',
      categoryName: rawItem.categoryName || rawItem.category || '',
      extraInfo: this.buildSeckillInfoLine(rawItem, safeSoldCount, safeRemainingStock),
      price: this.formatMoney(safeSeckillPrice),
      salesText: safeSoldCount > 0 ? String(safeSoldCount) : '',
      tag: statusInfo.label,
      reason: `原价 ¥${this.formatMoney(safeOriginalPrice)} · ${safeRemainingStock > 0 ? `余量 ${safeRemainingStock}` : '已售罄'}`,
      seckillPrice: safeSeckillPrice,
      seckillPriceText: this.formatMoney(safeSeckillPrice),
      originalPrice: safeOriginalPrice,
      originalPriceText: this.formatMoney(safeOriginalPrice),
      seckillApplyId: rawItem.seckillApplyId || rawItem.applyId || null,
      seckillActivityId: rawItem.seckillActivityId || rawItem.activityId || null,
      seckillStartTime: startTime,
      seckillEndTime: endTime,
      seckillStatus: statusInfo.status,
      seckillStatusLabel: statusInfo.label,
      countdownText: statusInfo.countdownText,
      stock: safeStock,
      soldCount: safeSoldCount,
      remainingStock: safeRemainingStock,
      soldText: safeSoldCount > 0
        ? `已抢 ${safeSoldCount}`
        : (safeRemainingStock > 0 ? `余量 ${safeRemainingStock}` : '已售罄'),
      progressPercent,
    }
  },

  buildSeckillInfoLine(rawItem, soldCount, remainingStock) {
    const category = rawItem.categoryName || rawItem.category || ''
    const stockLeft = Number(remainingStock || 0)
    const sold = Number(soldCount || 0)
    const parts = []
    if (category) parts.push(category)
    if (Number.isFinite(stockLeft) && stockLeft > 0) {
      parts.push(`余量 ${Math.max(0, stockLeft)}`)
    } else if (Number.isFinite(sold) && sold > 0) {
      parts.push(`已抢 ${sold}`)
    }
    return parts.join(' · ') || '限时活动商品'
  },

  formatMoney(value) {
    const amount = Number(value || 0)
    if (!Number.isFinite(amount)) {
      return '0.00'
    }
    return amount.toFixed(2)
  },

  resolveActivityImage(rawGroup = {}) {
    return this.pickFirstImage([
      rawGroup.activityCoverImage,
      rawGroup.coverImage,
      rawGroup.activityImage,
      rawGroup.image,
    ])
  },

  resolveProductImage(rawItem = {}) {
    return this.pickFirstImage([
      rawItem.productImage,
      rawItem.mainImage,
      rawItem.image,
      rawItem.thumbnail,
      rawItem.thumb,
      rawItem.imageUrl,
      Array.isArray(rawItem.images) ? rawItem.images[0] : rawItem.images,
      rawItem.product && rawItem.product.image,
      rawItem.product && rawItem.product.mainImage,
      rawItem.product && Array.isArray(rawItem.product.images) ? rawItem.product.images[0] : (rawItem.product ? rawItem.product.images : ''),
    ])
  },

  pickFirstImage(candidates = []) {
    for (let i = 0; i < candidates.length; i += 1) {
      const normalized = this.normalizeImageUrl(candidates[i])
      if (normalized) {
        return normalized
      }
    }
    return ''
  },

  normalizeImageUrl(value) {
    if (!value) {
      return ''
    }

    if (Array.isArray(value)) {
      return this.pickFirstImage(value)
    }

    let next = value
    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (!trimmed) {
        return ''
      }

      if ((trimmed.startsWith('[') && trimmed.endsWith(']')) || (trimmed.startsWith('"') && trimmed.endsWith('"'))) {
        try {
          next = JSON.parse(trimmed)
        } catch (error) {
          next = trimmed
        }
      } else {
        next = trimmed
      }
    }

    if (Array.isArray(next)) {
      return this.pickFirstImage(next)
    }

    const text = String(next || '').trim()
    if (!text) {
      return ''
    }

    if (/^https?:\/\//i.test(text) || text.startsWith('/')) {
      return text
    }

    return ''
  },

  buildAnchorId(prefix, rawValue) {
    const safe = String(rawValue || 'default')
      .replace(/[^a-zA-Z0-9_-]/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-|-$/g, '')
    return `${prefix}-${safe || 'default'}`
  },

  formatTimelineTitle(title) {
    const text = String(title || '').trim()
    if (!text) return '秒杀场次'
    return text.length > 8 ? `${text.slice(0, 8)}…` : text
  },

  formatCalendarDate(value) {
    const date = this.parseDate(value)
    if (!date) return '待定'
    const month = date.getMonth() + 1
    const day = date.getDate()
    return `${month}/${String(day).padStart(2, '0')}`
  },

  formatCalendarWeek(value) {
    const date = this.parseDate(value)
    if (!date) return '场次'
    const today = new Date()
    const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
    const startOfTarget = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
    const diffDays = Math.round((startOfTarget - startOfToday) / 86400000)
    if (diffDays === 0) return '今天'
    if (diffDays === 1) return '明天'
    if (diffDays === -1) return '昨天'
    return `周${WEEK_LABELS[date.getDay()]}`
  },

  formatShortClock(value) {
    const date = this.parseDate(value)
    if (!date) return '待定'
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    return `${hours}:${minutes}`
  },

  parseDate(value) {
    if (!value) return null
    const date = new Date(String(value).replace(/-/g, '/'))
    return Number.isNaN(date.getTime()) ? null : date
  },

  mapStatusToTab(status) {
    switch (Number(status)) {
      case 1:
      case 3:
        return 'active'
      case 2:
        return 'history'
      default:
        return 'upcoming'
    }
  },

  getSeckillStatusLabel(rawStatus, startTime, endTime) {
    const now = Date.now()
    const startAt = startTime ? new Date(String(startTime).replace(/-/g, '/')).getTime() : 0
    const endAt = endTime ? new Date(String(endTime).replace(/-/g, '/')).getTime() : 0
    const normalizedStatus = Number(rawStatus)

    if (normalizedStatus === SECKILL_STATUS.SOLD_OUT) {
      return {
        status: SECKILL_STATUS.SOLD_OUT,
        label: '已售罄',
        countdownText: '已抢光',
      }
    }

    if (normalizedStatus === SECKILL_STATUS.ACTIVE || (startAt && endAt && now >= startAt && now < endAt)) {
      return {
        status: SECKILL_STATUS.ACTIVE,
        label: '进行中',
        countdownText: this.formatCountdownText(endAt, '距结束'),
      }
    }

    if (normalizedStatus === SECKILL_STATUS.UPCOMING || (startAt && now < startAt)) {
      const beforeStart = startAt ? (startAt - now) : 0
      const shouldShowCountdown = !!startAt && beforeStart > 0 && beforeStart <= SECKILL_COUNTDOWN_WINDOW_MS
      return {
        status: SECKILL_STATUS.UPCOMING,
        label: '预告',
        countdownText: shouldShowCountdown
          ? this.formatCountdownText(startAt, '距开始')
          : (startAt ? '12小时后开启倒计时' : '即将开始'),
      }
    }

    return {
      status: SECKILL_STATUS.ENDED,
      label: '历史',
      countdownText: '活动已结束',
    }
  },

  formatCountdownText(targetAt, prefix) {
    if (!targetAt || Number.isNaN(targetAt)) {
      return ''
    }
    const gap = Math.max(0, targetAt - Date.now())
    const totalSeconds = Math.floor(gap / 1000)
    const days = Math.floor(totalSeconds / 86400)
    const hours = Math.floor((totalSeconds % 86400) / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    if (days > 0) {
      return `${prefix} ${days}天${hours}时`
    }
    return `${prefix} ${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  },

  formatTimeRange(startTime, endTime) {
    const startText = this.formatDisplayTime(startTime)
    const endText = this.formatDisplayTime(endTime)
    if (!startText && !endText) return '时间待定'
    if (!startText) return `结束：${endText}`
    if (!endText) return `开始：${startText}`
    return `${startText} - ${endText}`
  },

  formatDisplayTime(value) {
    if (!value) return ''
    const text = String(value).replace('T', ' ')
    return text.length >= 16 ? text.slice(5, 16) : text
  },

  startSeckillTicker() {
    this.clearSeckillTicker()
    if (this.isPageInactive()) {
      return
    }
    if (!this.needCountdownRefresh()) {
      return
    }
    this._seckillTicker = setInterval(() => {
      if (this.isPageInactive()) {
        this.clearSeckillTicker()
        return
      }
      this.refreshDisplayGroups()
      if (!this.needCountdownRefresh()) {
        this.clearSeckillTicker()
      }
    }, 1000)
  },

  needCountdownRefresh() {
    const groups = Array.isArray(this._rawActivityGroups) ? this._rawActivityGroups : []
    return groups.some((group) => {
      const statusInfo = this.getSeckillStatusLabel(group.runtimeStatus, group.startTime, group.endTime)
      return statusInfo.status === 0 || statusInfo.status === 1
    })
  },

  clearSeckillTicker() {
    if (this._seckillTicker) {
      clearInterval(this._seckillTicker)
      this._seckillTicker = null
    }
  },

  goToSeckillProduct(e) {
    const id = e.currentTarget.dataset.id || (e.detail && e.detail.id)
    if (!id) return
    app.navigateToPage(`/pages/product-detail/index?id=${id}`)
  },

  handleProductImageError(e) {
    const groupIndex = Number(e.currentTarget.dataset.groupIndex)
    const itemIndex = Number(e.currentTarget.dataset.itemIndex)
    if (!Number.isInteger(groupIndex) || !Number.isInteger(itemIndex)) {
      return
    }
    this.safeSetData({
      [`displayGroups[${groupIndex}].items[${itemIndex}].image`]: '',
    })
  },

  handleGroupCoverError(e) {
    const groupIndex = Number(e.currentTarget.dataset.groupIndex)
    if (!Number.isInteger(groupIndex)) {
      return
    }
    this.safeSetData({
      [`displayGroups[${groupIndex}].activityCoverImage`]: '',
    })
  },
})
