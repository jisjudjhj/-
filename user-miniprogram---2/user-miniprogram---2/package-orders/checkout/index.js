const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const SECKILL_STATUS = {
  UPCOMING: 0,
  ACTIVE: 1,
  ENDED: 2,
  SOLD_OUT: 3,
}

Page({
  data: {
    address: null,
    goodsList: [],
    checkoutMode: 'normal',
    seckillMeta: null,
    selectedPayment: 'balance',
    balance: '0.00',
    totalGoodsPrice: '0',
    shippingFee: '0',
    discount: '0',
    finalPrice: '0',
    loading: true,
    submitting: false,
    merchantId: null,
    availableCoupons: [],
    selectedCouponId: null,
    selectedCoupon: null,
    showCouponPicker: false,
    smartGuide: null,
    crossMerchantCheckout: false,
    selectedSplitCoupons: {},
    checkoutSignals: null,
    checkoutAttribution: null
  },

  onLoad(options = {}) {
    const recToken = options.recToken ? decodeURIComponent(options.recToken) : ''
    const recScene = options.recScene ? decodeURIComponent(options.recScene) : ''
    const preferredCouponIdRaw = options.preferredCouponId
    const preferredCouponId = preferredCouponIdRaw == null ? null : Number(preferredCouponIdRaw)
    const safePreferredCouponId = Number.isFinite(preferredCouponId) && preferredCouponId > 0
      ? preferredCouponId
      : null

    const checkoutMode = options.mode === 'seckill'
      ? 'seckill'
      : (options.mode === 'direct' ? 'direct' : 'normal')
    this.setData({
      checkoutMode,
      seckillMeta: null,
      selectedPayment: 'balance',
      smartGuide: null,
      checkoutAttribution: recToken
        ? {
            token: recToken,
            scene: recScene || 'direct',
            sourceLabel: this.resolveCheckoutSource(recScene || 'direct'),
            tokenText: `${recToken}`.slice(0, 12)
          }
        : null,
    })

    const redirectUrl = checkoutMode === 'seckill'
      ? `/pages/checkout/index?mode=seckill&productId=${options.productId || ''}&seckillApplyId=${options.seckillApplyId || ''}&quantity=${options.quantity || 1}`
      : (checkoutMode === 'direct'
        ? `/pages/checkout/index?mode=direct&productId=${options.productId || ''}&quantity=${options.quantity || 1}`
        : `/pages/checkout/index${safePreferredCouponId ? `?preferredCouponId=${safePreferredCouponId}` : ''}`)
    if (!app.requireLogin(redirectUrl)) {
      return
    }
    if (checkoutMode === 'seckill') {
      this.loadSeckillCheckoutData(options)
    } else if (checkoutMode === 'direct') {
      this.loadDirectCheckoutData(options)
    } else {
      this.loadCheckoutData(safePreferredCouponId)
    }
  },

  getErrorMessage(error) {
    if (!error) return '请求失败'
    if (typeof error === 'string') return error
    if (error.message) return error.message
    if (error.msg) return error.msg
    return '请求失败'
  },

  formatCouponOption(coupon = {}) {
    const scopeType = Number(coupon.scopeType || 0)
    const scopeLabel = scopeType === 1 ? '店铺券（仅本店）' : '平台券'
    const discount = coupon.discountAmount != null ? Number(coupon.discountAmount) : 0
    return {
      ...coupon,
      scopeType,
      scopeLabel,
      discountAmount: discount,
      displayText: `${coupon.couponName || '优惠券'} - 省¥${discount.toFixed(2)}（${scopeLabel}）`
    }
  },

  formatAmount(value) {
    const amount = Number(value || 0)
    if (!Number.isFinite(amount)) return '0.00'
    return amount.toFixed(2)
  },

  resolveCheckoutSource(scene = '') {
    const value = `${scene || ''}`.toLowerCase()
    if (value.includes('hot')) return '热榜召回'
    if (value.includes('similar')) return '内容相似'
    if (value.includes('collaborative') || value.includes('cf')) return '相似用户'
    if (value.includes('cart')) return '购物车补购'
    if (value.includes('search')) return '搜索意图'
    return '实时行为'
  },

  buildCheckoutSignals(goodsList = [], preview = {}, extra = {}) {
    const stockRiskCount = goodsList.filter((item) => item.stockEnough === false).length
    const priceChangedCount = goodsList.filter((item) => item.priceChanged || item.priceAdjusted || item.originalPrice).length
    const couponName = preview.selectedCoupon?.couponName || extra.selectedCouponName || ''
    const attribution = this.data.checkoutAttribution || this.findCheckoutAttribution(goodsList)
    return {
      couponText: couponName
        ? `已自动匹配 ${couponName}`
        : ((preview.availableCoupons || []).length ? '可选优惠券已按省钱优先排序' : '暂无可用券'),
      stockRisk: stockRiskCount > 0,
      stockText: stockRiskCount > 0
        ? `${stockRiskCount} 件商品库存发生变化`
        : '库存状态已校验',
      priceText: priceChangedCount > 0
        ? `${priceChangedCount} 件商品存在活动价或价格变动`
        : '价格预览已刷新',
      splitText: preview.crossMerchantCheckout || extra.crossMerchantCheckout
        ? '跨店订单将自动拆单并按店铺券规则结算'
        : '单店订单无需拆单',
      attribution,
    }
  },

  findCheckoutAttribution(goodsList = []) {
    const item = goodsList.find((goods) => goods.recommendationToken)
    if (!item) return null
    const token = item.recommendationToken
    const scene = item.recommendationScene || 'cart'
    return {
      token,
      scene,
      sourceLabel: this.resolveCheckoutSource(scene),
      tokenText: `${token}`.slice(0, 12)
    }
  },

  formatSmartGuide(raw = {}) {
    const bestPlan = raw.bestPlan || {}
    const alternatives = Array.isArray(raw.alternativePlans) ? raw.alternativePlans : []
    const topUpSuggestions = Array.isArray(raw.topUpSuggestions) ? raw.topUpSuggestions : []
    const recommendationId = raw.recommendationUserCouponId == null
      ? null
      : Number(raw.recommendationUserCouponId)
    const safeRecommendationId = Number.isFinite(recommendationId) && recommendationId > 0
      ? recommendationId
      : null

    const topUp = topUpSuggestions.length > 0
      ? {
          couponName: topUpSuggestions[0].couponName || '',
          amountGap: this.formatAmount(topUpSuggestions[0].amountGap),
          estimatedFinalAmount: this.formatAmount(topUpSuggestions[0].estimatedFinalAmount)
        }
      : null

    const bestDiscountNum = Number(bestPlan.discountAmount || 0)
    const minAmountNum = Number(bestPlan.minAmount || 0)
    const savingExplain = Number.isFinite(bestDiscountNum) && bestDiscountNum > 0
      ? {
          couponName: bestPlan.couponName || '优惠券',
          thresholdText: minAmountNum > 0
            ? `满 ¥${this.formatAmount(minAmountNum)} 可用`
            : '无门槛',
          discountText: this.formatAmount(bestDiscountNum),
          scopeLabel: bestPlan.scopeLabel || '平台券'
        }
      : null

    return {
      text: raw.smartGuideText || '',
      bestPlanType: bestPlan.planType || 'NO_COUPON',
      bestCouponName: bestPlan.couponName || '不使用优惠券',
      bestDiscount: this.formatAmount(bestPlan.discountAmount),
      bestFinalAmount: this.formatAmount(bestPlan.finalAmount),
      recommendedUserCouponId: safeRecommendationId,
      recommendationApplied: !!raw.recommendationApplied,
      model版本: raw.model版本 || '',
      alternatives: alternatives.slice(0, 2).map((plan) => ({
        couponName: plan.couponName || '不使用优惠券',
        finalAmount: this.formatAmount(plan.finalAmount),
        discountAmount: this.formatAmount(plan.discountAmount)
      })),
      topUp,
      savingExplain
    }
  },

  applyCheckoutPreview(preview, extra = {}) {
    const availableCoupons = (preview.availableCoupons || []).map((item) => this.formatCouponOption(item))
    const selectedCoupon = preview.selectedCoupon
      ? this.formatCouponOption({
          ...preview.selectedCoupon,
          userCouponId: preview.selectedCoupon.userCouponId || extra.selectedCouponId
        })
      : null
    const selectedSplitPlan = preview.selectedSplitPlan || {}
    const selectedSplitCoupons = preview.selectedSplitCoupons ||
      preview.recommendationSplitCoupons ||
      selectedSplitPlan.splitCoupons ||
      {}
    const goodsList = ((preview.items || [])).map((item) => ({
      id: item.productId,
      cartItemId: item.cartItemId,
      merchantId: item.merchantId,
      title: item.productName || '',
      price: item.price,
      originalPrice: item.originalPrice,
      quantity: item.quantity,
      image: resolveProductImage(item),
      stockEnough: item.stockEnough !== false,
      priceChanged: !!item.priceChanged || !!item.priceAdjusted,
      recommendationToken: item.recommendationToken || item.recToken || '',
      recommendationScene: item.recommendationScene || item.recScene || ''
    }))
    const crossMerchantCheckout = !!preview.crossMerchantCheckout

    this.setData({
      goodsList,
      merchantId: preview.merchantId || null,
      totalGoodsPrice: preview.totalAmount,
      shippingFee: preview.shippingFee || 0,
      discount: preview.discountAmount || 0,
      finalPrice: preview.finalAmount,
      availableCoupons,
      selectedCouponId: selectedCoupon ? selectedCoupon.userCouponId : null,
      selectedCoupon,
      crossMerchantCheckout,
      selectedSplitCoupons,
      checkoutSignals: this.buildCheckoutSignals(goodsList, preview, {
        selectedCouponName: selectedCoupon ? selectedCoupon.couponName : '',
        crossMerchantCheckout
      })
    })
  },

  async fetchCheckoutPreview(userCouponId) {
    const params = {}
    if (userCouponId) {
      params.userCouponId = userCouponId
    }
    return request.get('/cart/checkout-preview', params, { showLoading: false })
  },

  handleCheckoutGoodsImageError(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (!Number.isInteger(index)) {
      return
    }
    this.setData({
      [`goodsList[${index}].image`]: ''
    })
  },

  async fetchSmartGuide() {
    return request.get('/cart/smart-guide', {}, { showLoading: false })
  },

  async loadCheckoutData(preferredCouponId = null) {
    try {
      const [initialPreview, addressRes, walletRes, smartGuideRes] = await Promise.all([
        this.fetchCheckoutPreview().catch(() => null),
        request.get('/user/addresses', {}, { showLoading: false }).catch(() => null),
        request.get('/wallet/balance', {}, { showLoading: false }).catch(() => null),
        this.fetchSmartGuide().catch(() => null)
      ])

      if (!initialPreview || !initialPreview.items || initialPreview.items.length === 0) {
        this.setData({ loading: false })
        wx.showToast({ title: '没有待结算的商品', icon: 'none' })
        setTimeout(() => app.navigateToPage('/pages/cart/index'), 1200)
        return
      }

      let checkoutRes = initialPreview
      const bestCoupon = (initialPreview.availableCoupons || [])[0]
      const smartGuide = smartGuideRes ? this.formatSmartGuide(smartGuideRes) : null
      const guideCouponId = smartGuide && smartGuide.recommendedUserCouponId
        ? smartGuide.recommendedUserCouponId
        : null
      const targetCouponId = preferredCouponId || guideCouponId || (bestCoupon && bestCoupon.userCouponId)
      if (targetCouponId) {
        try {
          const previewWithCoupon = await this.fetchCheckoutPreview(targetCouponId)
          if (previewWithCoupon && previewWithCoupon.items && previewWithCoupon.items.length > 0) {
            checkoutRes = previewWithCoupon
          }
        } catch (couponErr) {
          console.warn('自动匹配优惠券失败，回退到无券预览', couponErr)
        }
      }

      const addressList = Array.isArray(addressRes) ? addressRes : []
      const defaultAddress = addressList.find((item) => item.isDefault === 1) || addressList[0] || null

      this.applyCheckoutPreview(checkoutRes, { selectedCouponId: targetCouponId || null })
      this.setData({
        balance: walletRes && walletRes.balance != null ? walletRes.balance : '0.00',
        smartGuide,
        address: defaultAddress
          ? {
              id: defaultAddress.id,
              name: defaultAddress.receiverName,
              phone: defaultAddress.receiverPhone,
              province: defaultAddress.province,
              city: defaultAddress.city,
              district: defaultAddress.district,
              detail: defaultAddress.detail
            }
          : null,
        loading: false
      })
    } catch (error) {
      console.error('获取结算信息失败', error)
      this.setData({
        goodsList: [],
        address: null,
        availableCoupons: [],
        selectedCouponId: null,
        selectedCoupon: null,
        smartGuide: null,
        loading: false
      })
      wx.showToast({ title: this.getErrorMessage(error) || '获取信息失败', icon: 'none' })
    }
  },

  async loadDirectCheckoutData(options = {}) {
    this.setData({ loading: true })
    const productId = Number(options.productId)
    const quantity = Math.max(1, Number(options.quantity || 1))
    if (!Number.isFinite(productId) || productId <= 0) {
      this.setData({ loading: false })
      wx.showToast({ title: '商品参数有误', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1200)
      return
    }

    try {
      const [productRes, addressRes, walletRes] = await Promise.all([
        request.get(`/products/detail/${productId}`, {}, { showLoading: false }),
        request.get('/user/addresses', {}, { showLoading: false }).catch(() => null),
        request.get('/wallet/balance', {}, { showLoading: false }).catch(() => null),
      ])

      const addressList = Array.isArray(addressRes) ? addressRes : []
      const defaultAddress = addressList.find((item) => item.isDefault === 1) || addressList[0] || null
      const seckillStatus = Number(productRes && productRes.seckillStatus)
      const hasSeckill = !!(productRes && (productRes.seckillApplyId || productRes.seckillActivityId))
      if (
        hasSeckill
        && (seckillStatus === SECKILL_STATUS.UPCOMING
          || seckillStatus === SECKILL_STATUS.ACTIVE
          || seckillStatus === SECKILL_STATUS.SOLD_OUT)
      ) {
        this.setData({ loading: false })
        wx.showToast({
          title: seckillStatus === SECKILL_STATUS.UPCOMING
            ? '秒杀尚未开始'
            : (seckillStatus === SECKILL_STATUS.SOLD_OUT ? '已售罄' : '请走秒杀'),
          icon: 'none'
        })
        setTimeout(() => wx.navigateBack(), 800)
        return
      }
      const price = Number(productRes && productRes.price != null ? productRes.price : 0)
      const total = Math.max(0, price * quantity)
      const detailImage = productRes && (productRes.image || (Array.isArray(productRes.images) ? productRes.images[0] : ''))
      const goodsList = [{
        id: productId,
        cartItemId: null,
        merchantId: productRes ? productRes.merchantId : null,
        title: productRes ? (productRes.name || '') : '',
        price: this.formatAmount(price),
        quantity,
        image: detailImage || '',
        stockEnough: true,
        recommendationToken: this.data.checkoutAttribution ? this.data.checkoutAttribution.token : '',
        recommendationScene: this.data.checkoutAttribution ? this.data.checkoutAttribution.scene : ''
      }]

      this.setData({
        goodsList,
        merchantId: productRes ? productRes.merchantId : null,
        totalGoodsPrice: this.formatAmount(total),
        shippingFee: this.formatAmount(0),
        discount: this.formatAmount(0),
        finalPrice: this.formatAmount(total),
        availableCoupons: [],
        selectedCouponId: null,
        selectedCoupon: null,
        balance: walletRes && walletRes.balance != null ? walletRes.balance : '0.00',
        address: defaultAddress
          ? {
              id: defaultAddress.id,
              name: defaultAddress.receiverName,
              phone: defaultAddress.receiverPhone,
              province: defaultAddress.province,
              city: defaultAddress.city,
              district: defaultAddress.district,
              detail: defaultAddress.detail
            }
          : null,
        checkoutSignals: this.buildCheckoutSignals(goodsList, {}, {}),
        loading: false
      })
    } catch (error) {
      this.setData({
        goodsList: [],
        availableCoupons: [],
        selectedCouponId: null,
        selectedCoupon: null,
        loading: false
      })
      wx.showToast({ title: this.getErrorMessage(error) || '加载结算信息失败', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1200)
    }
  },

  async refreshCheckoutPreview(userCouponId = null) {
    try {
      const preview = await this.fetchCheckoutPreview(userCouponId || undefined)
      if (!preview || !preview.items || preview.items.length === 0) {
        wx.showToast({ title: '待结算商品已失效', icon: 'none' })
        setTimeout(() => app.navigateToPage('/pages/cart/index'), 1200)
        return
      }
      this.applyCheckoutPreview(preview, { selectedCouponId: userCouponId || null })
    } catch (error) {
      wx.showToast({ title: this.getErrorMessage(error), icon: 'none' })
    }
  },

  async loadSeckillCheckoutData(options = {}) {
    this.setData({ loading: true })
    const productId = Number(options.productId)
    const quantity = Math.max(1, Number(options.quantity || 1))
    const seckillApplyId = options.seckillApplyId ? Number(options.seckillApplyId) : null
    if (!Number.isFinite(productId) || productId <= 0) {
      this.setData({ loading: false })
      wx.showToast({ title: '秒杀商品参数有误', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1200)
      return
    }

    try {
      const previewParams = { productId, quantity }
      if (seckillApplyId && Number.isFinite(seckillApplyId)) {
        previewParams.seckillApplyId = seckillApplyId
      }
      const [previewRes, addressRes, walletRes] = await Promise.all([
        request.get('/seckill/checkout-preview', previewParams, { showLoading: false }),
        request.get('/user/addresses', {}, { showLoading: false }).catch(() => null),
        request.get('/wallet/balance', {}, { showLoading: false }).catch(() => null),
      ])

      const addressList = Array.isArray(addressRes) ? addressRes : []
      const defaultAddress = addressList.find((item) => item.isDefault === 1) || addressList[0] || null

      const goods = {
        id: Number(previewRes.productId || productId),
        cartItemId: null,
        title: previewRes.productName || previewRes.name || '',
        price: previewRes.seckillPrice != null ? previewRes.seckillPrice : previewRes.price,
        originalPrice: previewRes.originalPrice != null ? previewRes.originalPrice : previewRes.price,
        quantity: previewRes.quantity || quantity,
        image: previewRes.productImage || previewRes.image || '',
        isSeckill: true,
        stockEnough: true,
      }
      const goodsList = [goods]

      this.setData({
        goodsList,
        totalGoodsPrice: previewRes.originalAmount != null ? previewRes.originalAmount : previewRes.totalAmount,
        shippingFee: previewRes.shippingFee || 0,
        discount: previewRes.discountAmount != null
          ? previewRes.discountAmount
          : (Number(goods.originalPrice || 0) - Number(goods.price || 0)) * Number(goods.quantity || 1),
        finalPrice: previewRes.finalAmount != null ? previewRes.finalAmount : previewRes.totalAmount,
        balance: walletRes && walletRes.balance != null ? walletRes.balance : '0.00',
        availableCoupons: [],
        selectedCouponId: null,
        selectedCoupon: null,
        seckillMeta: {
          seckillActivityId: previewRes.seckillActivityId || null,
          seckillApplyId: previewRes.seckillApplyId || seckillApplyId,
          seckillStartTime: previewRes.seckillStartTime || previewRes.startTime || '',
          seckillEndTime: previewRes.seckillEndTime || previewRes.endTime || '',
          limitPerUser: previewRes.seckillLimitPerUser || 1,
        },
        address: defaultAddress
          ? {
              id: defaultAddress.id,
              name: defaultAddress.receiverName,
              phone: defaultAddress.receiverPhone,
              province: defaultAddress.province,
              city: defaultAddress.city,
              district: defaultAddress.district,
              detail: defaultAddress.detail,
            }
          : null,
        checkoutSignals: this.buildCheckoutSignals(goodsList, {
          selectedCoupon: null,
          availableCoupons: [],
          crossMerchantCheckout: false
        }, {}),
        loading: false,
      })
    } catch (error) {
      console.error('加载秒杀结算失败', error)
      this.setData({
        goodsList: [],
        availableCoupons: [],
        selectedCouponId: null,
        selectedCoupon: null,
        loading: false,
      })
      wx.showToast({ title: error.message || '秒杀结算信息获取失败', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1200)
    }
  },

  chooseAddress() {
    app.navigateToPage('/pages/address/index?mode=select')
  },

  onShow() {
    const selectedAddress = app.globalData._selectedAddress
    if (selectedAddress) {
      this.setData({
        address: {
          id: selectedAddress.id,
          name: selectedAddress.receiverName,
          phone: selectedAddress.receiverPhone,
          province: selectedAddress.province,
          city: selectedAddress.city,
          district: selectedAddress.district,
          detail: selectedAddress.detail
        }
      })
      app.globalData._selectedAddress = null
    }
    request.get('/wallet/balance', {}, { showLoading: false }).then(res => {
      if (res && res.balance != null) {
        this.setData({ balance: res.balance })
      }
    }).catch(() => {})

    if (this.data.checkoutMode === 'normal' && !this.data.loading && !this.data.submitting) {
      this.refreshCheckoutSnapshotOnShow()
    }
  },

  async refreshCheckoutSnapshotOnShow() {
    try {
      const preview = await this.fetchCheckoutPreview(this.data.selectedCouponId || undefined)
      if (!preview || !preview.items || preview.items.length === 0) {
        wx.showToast({ title: '已提交', icon: 'none' })
        setTimeout(() => {
          app.navigateToPage('/pages/orders/index?type=all')
        }, 500)
        return
      }
      this.applyCheckoutPreview(preview, { selectedCouponId: this.data.selectedCouponId || null })
    } catch (error) {
      // 页面回到前台时静默刷新，失败不打断用户操作
    }
  },

  handleSelectCoupon() {
    if (this.data.checkoutMode !== 'normal') {
      wx.showToast({ title: '暂不可用券', icon: 'none' })
      return
    }
    const coupons = this.data.availableCoupons || []
    if (!coupons.length) {
      wx.showToast({ title: '暂无可用优惠券', icon: 'none' })
      return
    }
    this.setData({ showCouponPicker: true })
  },

  async applySmartGuideCoupon() {
    if (this.data.checkoutMode !== 'normal') {
      return
    }
    const guide = this.data.smartGuide
    if (!guide) {
      wx.showToast({ title: '智能方案暂不可用', icon: 'none' })
      return
    }
    if (!guide.recommendedUserCouponId) {
      await this.refreshCheckoutPreview(null)
      wx.showToast({ title: '无需用券', icon: 'none' })
      return
    }
    await this.refreshCheckoutPreview(guide.recommendedUserCouponId)
    wx.showToast({ title: '已应用最省方案', icon: 'success' })
  },

  closeCouponPicker() {
    this.setData({ showCouponPicker: false })
  },

  async selectCouponOption(e) {
    const rawId = e.currentTarget.dataset.id
    const couponId = rawId === 'none' || rawId === '' || rawId == null ? null : Number(rawId)
    this.closeCouponPicker()

    if (couponId == null) {
      await this.refreshCheckoutPreview(null)
      return
    }

    if (!Number.isFinite(couponId) || couponId <= 0) {
      return
    }
    await this.refreshCheckoutPreview(couponId)
  },

  selectPayment(e) {
    this.setData({ selectedPayment: e.currentTarget.dataset.type })
  },

  goToWallet() {
    app.navigateToPage('/pages/wallet/index')
  },

  navigateToOrderDetailReplace(orderId) {
    const url = `/pages/order-detail/index?id=${orderId}`
    wx.redirectTo({
      url,
      fail: () => {
        app.navigateToPage(url)
      }
    })
  },

  async checkOrderPaid(orderId) {
    try {
      const order = await request.get(`/orders/${orderId}`, {}, { showLoading: false })
      return !!order && Number(order.status) !== 0
    } catch (error) {
      return false
    }
  },

  async tryPayOrder(orderId) {
    wx.showLoading({ title: '支付中...', mask: true })
    try {
      await request.post(`/orders/${orderId}/pay`)
      wx.hideLoading()
      wx.showToast({ title: '支付成功', icon: 'success' })
      if (typeof app.refreshCartCount === 'function') {
        app.refreshCartCount().catch(() => {})
      }
      setTimeout(() => {
        this.navigateToOrderDetailReplace(orderId)
      }, 600)
      return true
    } catch (payErr) {
      wx.hideLoading()
      const alreadyPaid = await this.checkOrderPaid(orderId)
      if (alreadyPaid) {
        wx.showToast({ title: '订单已支付', icon: 'success' })
        setTimeout(() => {
          this.navigateToOrderDetailReplace(orderId)
        }, 500)
        return true
      }
      return false
    }
  },

  normalizeCreatedOrders(orderResult) {
    if (!orderResult) {
      return []
    }
    if (Array.isArray(orderResult)) {
      return orderResult.filter((item) => item && item.id)
    }
    if (Array.isArray(orderResult.orders)) {
      return orderResult.orders.filter((item) => item && item.id)
    }
    if (orderResult.id) {
      return [orderResult]
    }
    if (orderResult.primaryOrderId) {
      return [{ id: orderResult.primaryOrderId }]
    }
    return []
  },

  async paySplitOrders(orders = []) {
    const pendingOrders = orders.filter((item) => item && item.id)
    if (!pendingOrders.length) {
      return false
    }

    wx.showLoading({ title: '支付中...', mask: true })
    const failedOrders = []
    for (const order of pendingOrders) {
      try {
        await request.post(`/orders/${order.id}/pay`, {}, { showLoading: false })
      } catch (error) {
        const alreadyPaid = await this.checkOrderPaid(order.id)
        if (!alreadyPaid) {
          failedOrders.push(order)
        }
      }
    }
    wx.hideLoading()

    if (typeof app.refreshCartCount === 'function') {
      app.refreshCartCount().catch(() => {})
    }

    if (failedOrders.length) {
      wx.showToast({
        title: failedOrders.length === pendingOrders.length ? '支付失败' : '部分未支付',
        icon: 'none'
      })
      setTimeout(() => {
        app.navigateToPage('/pages/orders/index?type=unpaid')
      }, 1500)
      return false
    }

    wx.showToast({
      title: pendingOrders.length > 1 ? '多笔订单支付成功' : '支付成功',
      icon: 'success'
    })
    setTimeout(() => {
      app.navigateToPage(pendingOrders.length > 1
        ? '/pages/orders/index?type=unshipped'
        : `/pages/order-detail/index?id=${pendingOrders[0].id}`)
    }, 700)
    return true
  },

  async submitOrder() {
    if (this.data.submitting) {
      return
    }
    if (!this.data.address) {
      wx.showToast({ title: '请选择收货地址', icon: 'none' })
      return
    }
    const hasStockRisk = (this.data.goodsList || []).some((item) => item.stockEnough === false)
    if (hasStockRisk) {
      wx.showModal({
        title: '库存发生变化',
        content: '部分商品库存不足，已为你刷新结算信息，请确认后再提交。',
        showCancel: false
      })
      await this.loadCheckoutData(this.data.selectedCouponId || null)
      return
    }

    const balance = parseFloat(this.data.balance) || 0
    const finalPrice = parseFloat(this.data.finalPrice) || 0
    const useBalancePay = this.data.selectedPayment === 'balance'
    if (useBalancePay && balance < finalPrice) {
      wx.showModal({
        title: '余额不足',
        content: `余额 ¥${this.data.balance}，需付 ¥${this.data.finalPrice}`,
        confirmText: '去充值',
        success: (res) => {
          if (res.confirm) {
            app.navigateToPage('/pages/wallet/index')
          }
        }
      })
      return
    }

    this.setData({ submitting: true })
    wx.showLoading({ title: '创建订单中...', mask: true })

    try {
      if (this.data.checkoutMode === 'seckill') {
        await this.submitSeckillOrder()
        this.setData({ submitting: false })
        return
      }

      const items = this.data.goodsList.map((g) => ({
        productId: g.id,
        quantity: g.quantity
      }))

      const isCrossMerchantCheckout = !!this.data.crossMerchantCheckout
      const orderPayload = {
        addressId: this.data.address.id,
        items,
        remark: ''
      }

      if (this.data.checkoutMode === 'normal') {
        if (isCrossMerchantCheckout) {
          orderPayload.splitCoupons = this.data.selectedSplitCoupons || {}
        } else {
          orderPayload.userCouponId = this.data.selectedCouponId || undefined
        }
      }

      const order = await request.post('/orders', orderPayload)
      if (typeof app.refreshCartCount === 'function') {
        app.refreshCartCount().catch(() => {})
      }

      wx.hideLoading()

      const createdOrders = this.normalizeCreatedOrders(order)
      if (createdOrders.length) {
        if (useBalancePay) {
          const paid = createdOrders.length > 1
            ? await this.paySplitOrders(createdOrders)
            : await this.tryPayOrder(createdOrders[0].id)
          if (!paid) {
            wx.showToast({ title: '下单成功，请到订单中支付', icon: 'none' })
            setTimeout(() => {
              app.navigateToPage('/pages/orders/index?type=unpaid')
            }, 1500)
          }
        } else {
          wx.showToast({ title: '下单成功，请到订单中支付', icon: 'none' })
          setTimeout(() => {
            app.navigateToPage('/pages/orders/index?type=unpaid')
          }, 1500)
        }
      } else {
        wx.showToast({ title: '下单成功，请到订单中查看', icon: 'none' })
        setTimeout(() => {
          app.navigateToPage('/pages/orders/index?type=all')
        }, 1500)
      }
    } catch (error) {
      console.error('提交订单失败', error)
      wx.hideLoading()
      const message = this.getErrorMessage(error)
      const isStockError = /库存|下架|不可用|结算商品/.test(message)
      const isCouponError = /优惠券|门槛|店铺券/.test(message)
      const isSeckillRedirect = /秒杀商品请直接使用秒杀购买|秒杀库存不足|已售罄/.test(message)

      if (isSeckillRedirect) {
        wx.showModal({
          title: '活动状态变化',
          content: `${message}，请返回商品页刷新后重试。`,
          showCancel: false,
          success: () => {
            wx.navigateBack()
          }
        })
      } else if (isStockError) {
        wx.showModal({
          title: '库存变化提示',
          content: `${message}，系统已为你刷新结算信息。`,
          showCancel: false
        })
        await this.loadCheckoutData(this.data.selectedCouponId || null)
      } else if (isCouponError) {
        wx.showModal({
          title: '优惠券状态变化',
          content: `${message}，重新匹配优惠券。`,
          showCancel: false
        })
        await this.loadCheckoutData(null)
      } else {
        wx.showToast({ title: message || '提交订单失败', icon: 'none' })
      }
    } finally {
      this.setData({ submitting: false })
    }
  },

  async submitSeckillOrder() {
    const goods = this.data.goodsList[0] || {}
    const payload = {
      addressId: this.data.address.id,
      productId: goods.id,
      quantity: goods.quantity || 1,
      seckillApplyId: this.data.seckillMeta && this.data.seckillMeta.seckillApplyId
        ? this.data.seckillMeta.seckillApplyId
        : undefined,
      remark: '',
    }

    const order = await request.post('/seckill/orders', payload)
    wx.hideLoading()

    if (order && order.id) {
      const paid = await this.tryPayOrder(order.id)
      if (!paid) {
        wx.showToast({ title: '秒杀下单成功，请到订单中支付', icon: 'none' })
        setTimeout(() => {
          app.navigateToPage('/pages/orders/index?type=unpaid')
        }, 1500)
      }
    }
  }
})

