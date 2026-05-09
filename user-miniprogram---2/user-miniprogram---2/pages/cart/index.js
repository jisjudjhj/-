const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const MAX_CART_ITEM_QUANTITY = 10
const MAX_CART_TOTAL_QUANTITY = 99

Page({
  data: {
    cartList: [],
    isAllSelected: false,
    totalPrice: 0,
    selectedCount: 0,
    distinctCount: 0,
    distinctLimit: 99,
    remainingDistinctCount: 99,
    cartLimitReached: false,
    cartCapacityText: '还可加入 99 种商品',
    startX: 0,
    startY: 0,
    loading: true,
    refreshing: false,
    smartGuideLoading: false,
    smartSavingsLoading: false,
    estimatedSavingsNum: 0,
    estimatedSavingsText: '0.00',
    savingsHintText: '勾选后计算',
    savingsHintTone: 'neutral',
    showSavingsExplain: false,
    savingsExplain: null
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 2 })
      app.applyMessageBadge()
      app.refreshMessageUnreadCount().catch(() => {})
      app.refreshCartCount().catch(() => {})
    }

    if (!app.requireLogin('/pages/cart/index')) {
      return
    }

    this.loadCartData()
  },

  onHide() {
    this.clearSavingsTimer()
  },

  onUnload() {
    this.clearSavingsTimer()
  },

  async onRefresh() {
    if (this._refreshing) return
    this._refreshing = true
    this.setData({ refreshing: true })
    try {
      await this.loadCartData()
    } finally {
      this.setData({ refreshing: false })
      this._refreshing = false
    }
  },

  async loadCartData() {
    if (this._loadCartPromise) {
      return this._loadCartPromise
    }

    this._loadCartPromise = (async () => {
    try {
      const res = await request.get('/cart')
      const list = ((res && res.items) || []).map((item) => ({
        id: item.id,
        cartItemId: item.id,
        productId: item.productId,
        title: item.productName || '',
        price: item.price,
        quantity: this.normalizeCartQuantity(item.quantity),
        image: resolveProductImage(item),
        checkoutBlocked: !!item.checkoutBlocked,
        blockedReason: item.blockedReason || '',
        selected: item.checkoutBlocked ? false : item.selected === 1,
        isTouchMove: false
      })).map((item) => this.buildCartUiFields(item))
      const distinctCount = res && res.distinctCount != null
        ? Number(res.distinctCount)
        : list.length
      const distinctLimit = res && res.distinctLimit != null
        ? Number(res.distinctLimit)
        : 99
      const remainingDistinctCount = res && res.remainingDistinctCount != null
        ? Number(res.remainingDistinctCount)
        : Math.max(distinctLimit - distinctCount, 0)
      const cartLimitReached = !!(res && res.distinctLimitReached != null
        ? res.distinctLimitReached
        : remainingDistinctCount <= 0)
      this.setData({
        cartList: list,
        loading: false,
        ...this.buildCartCapacityState({
          distinctCount,
          distinctLimit,
          remainingDistinctCount,
          cartLimitReached
        })
      })
      const totalCount = list.reduce((sum, item) => sum + Math.max(0, Number(item.quantity || 0)), 0)
      if (typeof app.syncCartCount === 'function') {
        app.syncCartCount(totalCount)
      }
      this.calculateTotal()
    } catch (error) {
      console.error('获取购物车列表失败', error)
      this.setData({
        cartList: [],
        loading: false,
        ...this.buildCartCapacityState()
      })
      this.calculateTotal()
    }
    })()

    try {
      await this._loadCartPromise
    } finally {
      this._loadCartPromise = null
    }
  },

  normalizeCartQuantity(value) {
    const quantity = Number(value || 1)
    if (!Number.isFinite(quantity)) return 1
    return Math.max(1, Math.min(MAX_CART_ITEM_QUANTITY, Math.round(quantity)))
  },

  buildCartUiFields(item = {}) {
    const quantity = this.normalizeCartQuantity(item.quantity)
    const unitPrice = Number(item.price || 0)
    const safePrice = Number.isFinite(unitPrice) ? unitPrice : 0
    return {
      ...item,
      quantity,
      quantityLabel: `x${quantity}`,
      unitPriceText: `单价 ¥${this.formatMoney(safePrice)}`,
      subtotalText: this.formatMoney(safePrice * quantity)
    }
  },

  calculateTotal() {
    let total = 0
    let count = 0
    let allSelected = true
    let selectableCount = 0
    const list = this.data.cartList

    if (list.length === 0) {
      allSelected = false
    } else {
      list.forEach(item => {
        if (item.checkoutBlocked) {
          return
        }
        selectableCount += 1
        if (item.selected) {
          total += item.price * item.quantity
          count += item.quantity
        } else {
          allSelected = false
        }
      })
    }

    if (selectableCount === 0) {
      allSelected = false
    }

    this.setData({
      totalPrice: total.toLocaleString(),
      totalPriceNum: total,
      selectedCount: count,
      isAllSelected: allSelected
    }, () => {
      this.scheduleSavingsHintRefresh()
    })
  },

  buildCartCapacityState(options = {}) {
    const distinctLimit = Math.max(1, Number(options.distinctLimit || 99))
    const distinctCount = Math.max(0, Number(options.distinctCount != null ? options.distinctCount : 0))
    const remainingDistinctCount = Math.max(
      0,
      Number(options.remainingDistinctCount != null ? options.remainingDistinctCount : (distinctLimit - distinctCount))
    )
    const cartLimitReached = options.cartLimitReached != null
      ? !!options.cartLimitReached
      : remainingDistinctCount <= 0
    return {
      distinctCount,
      distinctLimit,
      remainingDistinctCount,
      cartLimitReached,
      cartCapacityText: cartLimitReached
        ? '已到上限，删除部分商品后可继续加购'
        : `还可加入 ${remainingDistinctCount} 种商品`
    }
  },

  handleCartImageError(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (!Number.isInteger(index)) {
      return
    }
    this.setData({
      [`cartList[${index}].image`]: ''
    })
  },

  clearSavingsTimer() {
    if (this._savingsTimer) {
      clearTimeout(this._savingsTimer)
      this._savingsTimer = null
    }
  },

  getSelectedSignature() {
    const list = Array.isArray(this.data.cartList) ? this.data.cartList : []
    const selected = list
      .filter((item) => !item.checkoutBlocked && !!item.selected)
      .map((item) => `${item.cartItemId || item.id}:${Number(item.quantity || 0)}`)
      .sort()
    return selected.join('|')
  },

  getErrorText(error) {
    if (!error) return ''
    if (typeof error === 'string') return error
    if (error.message) return String(error.message)
    if (error.msg) return String(error.msg)
    if (error.errMsg) return String(error.errMsg)
    return ''
  },

  isRetryableSmartGuideError(error) {
    const text = (this.getErrorText(error) || '').toLowerCase()
    if (!text) return false
    return /network|timeout|timed out|request:fail|网络|超时/.test(text)
  },

  wait(ms) {
    return new Promise((resolve) => {
      setTimeout(resolve, ms)
    })
  },

  async fetchSmartGuideWithRetry() {
    let lastError = null
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        return await request.get('/cart/smart-guide', {}, { showLoading: false })
      } catch (error) {
        lastError = error
        if (attempt >= 1 || !this.isRetryableSmartGuideError(error)) {
          throw error
        }
        await this.wait(220)
      }
    }
    throw lastError || new Error('smart-guide request failed')
  },

  buildSavingsExplain(guide) {
    const bestPlan = guide && guide.bestPlan ? guide.bestPlan : {}
    const discount = Number(bestPlan.discountAmount || 0)
    if (!Number.isFinite(discount) || discount <= 0) {
      return null
    }
    const minAmount = Number(bestPlan.minAmount || 0)
    const thresholdText = minAmount > 0
      ? `满 ¥${this.formatMoney(minAmount)} 可用`
      : '无门槛'
    return {
      couponName: bestPlan.couponName || '优惠券',
      thresholdText,
      discountText: this.formatMoney(discount),
      scopeLabel: bestPlan.scopeLabel || '平台券'
    }
  },

  formatMoney(value) {
    const amount = Number(value || 0)
    if (!Number.isFinite(amount)) return '0.00'
    return amount.toFixed(2)
  },

  scheduleSavingsHintRefresh() {
    this.clearSavingsTimer()
    this._savingsTimer = setTimeout(() => {
      this.refreshSavingsHint()
    }, 320)
  },

  async refreshSavingsHint() {
    const selectedCount = Number(this.data.selectedCount || 0)
    if (selectedCount <= 0) {
      this._lastSavingsSignature = ''
      this._lastGuideSignature = ''
      this._lastGuidePayload = null
      this.setData({
        smartSavingsLoading: false,
        estimatedSavingsNum: 0,
        estimatedSavingsText: '0.00',
        savingsHintText: '勾选后计算',
        savingsHintTone: 'neutral',
        showSavingsExplain: false,
        savingsExplain: null
      })
      return
    }

    const signature = this.getSelectedSignature()
    if (signature && signature === this._lastSavingsSignature) {
      return
    }

    if (this._smartSavingsLoading) {
      this._pendingSavingsRefresh = true
      return
    }

    this._smartSavingsLoading = true
    this.setData({
      smartSavingsLoading: true,
      savingsHintText: '计算中',
      savingsHintTone: 'neutral',
      showSavingsExplain: false
    })

    const requestId = (this._savingsRequestId || 0) + 1
    this._savingsRequestId = requestId
    try {
      const guide = await this.fetchSmartGuideWithRetry()
      if (requestId !== this._savingsRequestId) {
        return
      }
      this._lastGuidePayload = guide || null
      this._lastGuideSignature = signature || ''
      this._lastSavingsSignature = signature || ''
      const bestPlan = guide && guide.bestPlan ? guide.bestPlan : {}
      const savings = Number(bestPlan.discountAmount || 0)
      if (Number.isFinite(savings) && savings > 0) {
        this.setData({
          smartSavingsLoading: false,
          estimatedSavingsNum: savings,
          estimatedSavingsText: savings.toFixed(2),
          savingsHintText: `预计可省 ¥${savings.toFixed(2)}`,
          savingsHintTone: 'positive',
          savingsExplain: this.buildSavingsExplain(guide)
        })
      } else {
        this.setData({
          smartSavingsLoading: false,
          estimatedSavingsNum: 0,
          estimatedSavingsText: '0.00',
          savingsHintText: '暂无优惠',
          savingsHintTone: 'neutral',
          showSavingsExplain: false,
          savingsExplain: null
        })
      }
    } catch (error) {
      if (requestId !== this._savingsRequestId) {
        return
      }
      const latestSelectedCount = Number(this.data.selectedCount || 0)
      this.setData({
        smartSavingsLoading: false,
        estimatedSavingsNum: 0,
        estimatedSavingsText: '0.00',
        savingsHintText: latestSelectedCount > 0 ? '计算失败' : '勾选后计算',
        savingsHintTone: 'neutral',
        showSavingsExplain: false,
        savingsExplain: null
      })
    } finally {
      this._smartSavingsLoading = false
      if (this._pendingSavingsRefresh) {
        this._pendingSavingsRefresh = false
        this.scheduleSavingsHintRefresh()
      }
    }
  },

  toggleSavingsExplain() {
    if (!this.data.savingsExplain || this.data.estimatedSavingsNum <= 0) {
      return
    }
    this.setData({ showSavingsExplain: !this.data.showSavingsExplain })
  },

  hideSavingsExplain() {
    if (this.data.showSavingsExplain) {
      this.setData({ showSavingsExplain: false })
    }
  },
  
  // 单选
  async toggleSelect(e) {
    const index = e.currentTarget.dataset.index
    const list = this.data.cartList
    const item = list[index]
    if (item.checkoutBlocked) {
      wx.showToast({ title: item.blockedReason || '请走秒杀', icon: 'none' })
      return
    }
    const newSelected = !item.selected
    
    try {
      await request.put(`/cart/${item.cartItemId}/selected`, { selected: newSelected ? 1 : 0 }, { showLoading: false });
      list[index].selected = newSelected
      this.setData({ cartList: list })
      this.calculateTotal()
    } catch (error) {
      wx.showToast({ title: '操作失败', icon: 'none' })
    }
  },
  
  // 全选
  async toggleSelectAll() {
    const isAllSelected = !this.data.isAllSelected

    try {
      const updates = this.data.cartList
        .map((item) => {
          const nextSelected = item.checkoutBlocked ? 0 : (isAllSelected ? 1 : 0)
          const currentSelected = item.selected ? 1 : 0
          if (currentSelected === nextSelected) {
            return null
          }
          return request.put(
            `/cart/${item.cartItemId}/selected`,
            { selected: nextSelected },
            { showLoading: false }
          )
        })
        .filter(Boolean)

      if (updates.length > 0) {
        await Promise.all(updates)
      }

      const list = this.data.cartList.map((item) => ({
        ...item,
        selected: item.checkoutBlocked ? false : isAllSelected
      }))
      this.setData({ cartList: list })
      this.calculateTotal()
    } catch (error) {
      console.error('全选操作失败', error)
      wx.showToast({ title: '操作失败', icon: 'none' })
      this.loadCartData()
    }
  },
  
  // 修改数量
  async changeQuantity(e) {
    const { index, type } = e.currentTarget.dataset
    const list = this.data.cartList
    const item = list[index]
    if (item.checkoutBlocked) {
      wx.showToast({ title: item.blockedReason || '请走秒杀', icon: 'none' })
      return
    }
    let qty = item.quantity

    if (type === 'add') {
      if (qty >= MAX_CART_ITEM_QUANTITY) {
        wx.showToast({ title: `单品最多 ${MAX_CART_ITEM_QUANTITY} 件`, icon: 'none' })
        return
      }
      
      // 检查购物车总数量
      const totalQuantity = list.reduce((sum, row) => sum + Math.max(0, Number(row.quantity || 0)), 0)
      if (totalQuantity >= MAX_CART_TOTAL_QUANTITY) {
        wx.showToast({ title: `购物车商品总数量不能超过 ${MAX_CART_TOTAL_QUANTITY} 件`, icon: 'none' })
        return
      }
      
      qty++
    } else if (type === 'minus') {
      if (qty <= 1) return
      qty--
    }

    try {
      list[index] = this.buildCartUiFields({
        ...list[index],
        quantity: qty
      })
      this.setData({ cartList: list })
      if (typeof app.syncCartCount === 'function') {
        app.syncCartCount(list.reduce((sum, row) => sum + Math.max(0, Number(row.quantity || 0)), 0))
      }
      this.calculateTotal()
      
      await request.put(`/cart/${item.cartItemId}/quantity`, {
        quantity: qty
      }, { showLoading: false })
    } catch (error) {
      console.error('更新数量失败', error)
      list[index] = this.buildCartUiFields({
        ...list[index],
        quantity: type === 'add' ? qty - 1 : qty + 1
      })
      this.setData({ cartList: list })
      if (typeof app.syncCartCount === 'function') {
        app.syncCartCount(list.reduce((sum, row) => sum + Math.max(0, Number(row.quantity || 0)), 0))
      }
      this.calculateTotal()
      wx.showToast({ title: '更新数量失败', icon: 'none' })
    }
  },
  
  // 滑动删除
  touchstart(e) {
    this.data.cartList.forEach(item => {
      if (item.isTouchMove) {
        item.isTouchMove = false
      }
    })
    this.setData({
      startX: e.changedTouches[0].clientX,
      startY: e.changedTouches[0].clientY,
      cartList: this.data.cartList
    })
  },
  touchmove(e) {
    const index = e.currentTarget.dataset.index
    const startX = this.data.startX
    const startY = this.data.startY
    const touchMoveX = e.changedTouches[0].clientX
    const touchMoveY = e.changedTouches[0].clientY
    const angle = this.angle({ X: startX, Y: startY }, { X: touchMoveX, Y: touchMoveY })
    
    this.data.cartList.forEach((item, i) => {
      item.isTouchMove = false
      if (Math.abs(angle) > 30) return
      if (i === index) {
        if (touchMoveX > startX) {
          item.isTouchMove = false
        } else {
          item.isTouchMove = true
        }
      }
    })
    this.setData({
      cartList: this.data.cartList
    })
  },
  angle(start, end) {
    const _X = end.X - start.X
    const _Y = end.Y - start.Y
    return 360 * Math.atan(_Y / _X) / (2 * Math.PI)
  },
  
  // 移出购物车项
  deleteItem(e) {
    const index = e.currentTarget.dataset.index
    const list = this.data.cartList
    const item = list[index]
    
    wx.showModal({
      title: '移出购物车',
      content: '确定要将该商品移出购物车吗？',
      confirmColor: '#FF3B30',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request.delete(`/cart/${item.cartItemId}`)
            
            list.splice(index, 1)
            this.setData({
              cartList: list,
              ...this.buildCartCapacityState({
                distinctCount: list.length,
                distinctLimit: this.data.distinctLimit
              })
            })
            if (typeof app.syncCartCount === 'function') {
              app.syncCartCount(list.reduce((sum, row) => sum + Math.max(0, Number(row.quantity || 0)), 0))
            }
            this.calculateTotal()
            wx.showToast({ title: '已移出', icon: 'success' })
          } catch (error) {
            console.error('移出购物车失败', error)
          }
        } else {
          list[index].isTouchMove = false
          this.setData({ cartList: list })
        }
      }
    })
  },
  
  goExplore() {
    app.navigateToPage('/pages/explore/index')
  },
  
  // 结算
  goToCheckout() {
    if (this.data.selectedCount === 0) return
    this.hideSavingsExplain()
    const blocked = this.data.cartList.find((item) => item.selected && item.checkoutBlocked)
    if (blocked) {
      wx.showToast({ title: blocked.blockedReason || '含秒杀商品', icon: 'none' })
      return
    }

    app.navigateToPage('/pages/checkout/index').then((ok) => {
      if (!ok) {
        wx.showToast({ title: '跳转失败', icon: 'none' })
      }
    })
  },

  async goSmartCheckout() {
    if (this.data.selectedCount === 0) {
      wx.showToast({ title: '请先勾选商品', icon: 'none' })
      return
    }
    if (this.data.smartGuideLoading) {
      return
    }

    const blocked = this.data.cartList.find((item) => item.selected && item.checkoutBlocked)
    if (blocked) {
      wx.showToast({ title: blocked.blockedReason || '含秒杀商品', icon: 'none' })
      return
    }

    this.setData({ smartGuideLoading: true })
    try {
      this.hideSavingsExplain()
      const signature = this.getSelectedSignature()
      let guide = null
      if (signature && this._lastGuidePayload && signature === this._lastGuideSignature) {
        guide = this._lastGuidePayload
      } else {
        guide = await this.fetchSmartGuideWithRetry()
        this._lastGuidePayload = guide || null
        this._lastGuideSignature = signature || ''
      }
      const bestPlan = guide && guide.bestPlan ? guide.bestPlan : {}
      const savings = Number(bestPlan.discountAmount || 0)
      const rawCouponId = guide ? guide.recommendationUserCouponId : null
      const couponId = rawCouponId == null ? null : Number(rawCouponId)
      const preferredCouponId = Number.isFinite(couponId) && couponId > 0 ? couponId : null

      if (savings > 0) {
        wx.showToast({ title: `已找到省¥${savings.toFixed(2)}方案`, icon: 'none' })
      } else {
        wx.showToast({ title: '已生成最省结算方案', icon: 'none' })
      }

      const url = preferredCouponId
        ? `/pages/checkout/index?preferredCouponId=${preferredCouponId}`
        : '/pages/checkout/index'
      app.navigateToPage(url).then((ok) => {
        if (!ok) {
          wx.showToast({ title: '跳转失败', icon: 'none' })
        }
      })
    } catch (error) {
      // request.js 已统一弹错，这里不重复提示
    } finally {
      this.setData({ smartGuideLoading: false })
    }
  }
})

