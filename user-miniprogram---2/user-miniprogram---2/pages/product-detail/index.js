const app = getApp()
const request = require('../../utils/request')
const im = require('../../utils/im')
const recommendationTracker = require('../../utils/recommendation-tracker')
const { buildProductShareMessage } = require('../../utils/share')
const SECKILL_COUNTDOWN_WINDOW_MS = 12 * 60 * 60 * 1000
const CACHE_TTL = {
  DETAIL: 0,
  SIMILAR: 5 * 60 * 1000,
  REVIEWS: 60 * 1000,
  AI_SUMMARY: 24 * 60 * 60 * 1000
}
const SECKILL_STATUS = {
  UPCOMING: 0,
  ACTIVE: 1,
  ENDED: 2,
  SOLD_OUT: 3,
}
const REVIEW_FILTER_OPTIONS = [
  { label: '全部', rating: 0 },
  { label: '5星', rating: 5 },
  { label: '4星', rating: 4 },
  { label: '3星', rating: 3 },
]

function createDecisionBoard() {
  return {
    badge: '',
    headline: '',
    subline: '',
    facts: [],
    prompts: [],
  }
}

Page({
  data: {
    navColor: '#ffffff',
    isScrolled: false,
    currentImageIndex: 0,
    productId: null,
    recommendationToken: '',
    recommendationScene: '',
    product: null,
    loading: true,
    isFavorite: false,
    similarProducts: [],
    refreshing: false,
    aiSummary: '',
    aiSummaryLoading: false,
    showAiQa: false,
    aiQaInput: '',
    aiQaLoading: false,
    aiQaAnswer: '',
    showSkuPopup: false,
    skuQuantity: 1,
    buyMode: 'cart',
    activeCommerceTab: 'goods',
    scrollIntoView: '',
    decisionBoard: createDecisionBoard(),
    recommendationInsight: null,
    showRecommendationSheet: false,
    seckill: null,
    seckillCountdownText: '',
    reviewList: [],
    reviewAverage: '5.0',
    reviewTotal: 0,
    reviewFilteredTotal: 0,
    reviewPage: 1,
    reviewHasMore: false,
    reviewLoading: false,
    reviewLoadingMore: false,
    reviewFilterRating: 0,
    reviewFilterOptions: REVIEW_FILTER_OPTIONS,
  },

  onLoad(options) {
    this._destroyed = false
    const id = Number(options.id)
    if (!Number.isFinite(id) || id <= 0) {
      wx.showToast({ title: '商品不存在', icon: 'none' })
      wx.navigateBack()
      return
    }

    this.setData({
      productId: id,
      recommendationToken: options.recToken ? decodeURIComponent(options.recToken) : '',
      recommendationScene: options.recScene ? decodeURIComponent(options.recScene) : '',
    })
    this.loadProductDetail(id)
  },

  onShow() {
    this._destroyed = false
  },

  onHide() {
    this._destroyed = true
    this.clearSeckillCountdownTimer()
    this.clearScrollIntoViewTimer()
  },

  onUnload() {
    this._destroyed = true
    this.clearSeckillCountdownTimer()
    this.clearScrollIntoViewTimer()
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
    if (this.data.productId) {
      await this.loadProductDetail(this.data.productId, { showLoading: false, forceRefresh: true })
    }
    this.safeSetData({ refreshing: false })
  },

  async loadProductDetail(id, options = {}) {
    const shouldShowLoading = options.showLoading !== false
    try {
      const res = await request.get(`/products/detail/${id}`, {}, {
        showLoading: shouldShowLoading,
        cacheTtl: CACHE_TTL.DETAIL,
        forceRefresh: !!options.forceRefresh
      })
      const galleryImages = Array.isArray(res.images) && res.images.length
        ? res.images
        : res.image
          ? [res.image]
          : []

      const product = {
        id: res.id,
        title: res.name || '',
        price: res.price,
        description: res.description || '',
        images: galleryImages,
        details: galleryImages,
        stock: res.stock || 0,
        salesCount: res.salesCount || 0,
        rating: res.rating || 0,
        merchantId: res.merchantId || null,
        tags: Array.isArray(res.tags) ? res.tags : [],
      }

      const seckill = this.parseSeckillInfo(res)

      if (this.isPageInactive()) return
      this.safeSetData({
        product,
        seckill,
        seckillCountdownText: seckill ? seckill.countdownText : '',
        activeCommerceTab: 'goods',
        scrollIntoView: '',
        decisionBoard: this.buildDecisionBoard(product, this.data.similarProducts),
        recommendationInsight: this.buildRecommendationInsight(product, res),
        loading: false,
      })

      if (seckill && (seckill.status === 0 || seckill.status === 1)) {
        this.startSeckillCountdown(seckill)
      } else {
        this.clearSeckillCountdownTimer()
      }

      if (app.isLoggedIn()) {
        this.checkFavoriteStatus(id)
        this.recordBehavior(id)
      }
      this.loadReviews(id, { refresh: true, forceRefresh: !!options.forceRefresh })
      this.loadSimilarProducts(id, { forceRefresh: !!options.forceRefresh })
      this.loadAiSummary({ forceRefresh: !!options.forceRefresh })
    } catch (error) {
      console.error('获取商品详情失败', error)
      if (this.isPageInactive()) return
      this.safeSetData({ loading: false, product: null })
      wx.showToast({ title: '获取详情失败', icon: 'none' })
    }
  },

  async checkFavoriteStatus(id) {
    try {
      const res = await request.get(`/user/favorites/check/${id}`, {}, { showLoading: false })
      this.safeSetData({ isFavorite: !!res })
    } catch (error) {
      console.warn('[ProductDetail] favorite status failed', error)
    }
  },

  onScroll(e) {
    const scrollTop = e.detail.scrollTop
    const threshold = 150

    if (scrollTop > threshold && !this.data.isScrolled) {
      this.setData({
        isScrolled: true,
        navColor: '#111111',
      })
    } else if (scrollTop <= threshold && this.data.isScrolled) {
      this.setData({
        isScrolled: false,
        navColor: '#ffffff',
      })
    }
  },

  onGalleryChange(e) {
    this.setData({
      currentImageIndex: e.detail.current,
    })
  },

  onCommerceTabTap(e) {
    const tab = e.currentTarget && e.currentTarget.dataset ? e.currentTarget.dataset.tab : ''
    if (!['goods', 'review', 'detail', 'recommend'].includes(tab)) return

    let nextTab = tab
    if (tab === 'recommend' && (!Array.isArray(this.data.similarProducts) || !this.data.similarProducts.length)) {
      nextTab = 'detail'
    }

    this.safeSetData({ activeCommerceTab: nextTab, scrollIntoView: '' })
  },

  scrollToSection(sectionId) {
    if (!sectionId) return
    this.clearScrollIntoViewTimer()
    this.safeSetData({ scrollIntoView: '' })
    this._scrollIntoViewTimer = setTimeout(() => {
      if (this.isPageInactive()) return
      this.safeSetData({ scrollIntoView: sectionId })
      this._scrollIntoViewTimer = null
    }, 24)
  },

  clearScrollIntoViewTimer() {
    if (this._scrollIntoViewTimer) {
      clearTimeout(this._scrollIntoViewTimer)
      this._scrollIntoViewTimer = null
    }
  },

  openSkuPopup(e) {
    if (!this.data.productId || !this.data.product) {
      wx.showToast({ title: '商品信息加载中', icon: 'none' })
      return
    }

    const mode = e.currentTarget.dataset.mode || 'cart'
    if (mode !== 'seckill' && Number(this.data.product.stock || 0) <= 0) {
      wx.showToast({ title: '暂时缺货', icon: 'none' })
      return
    }

    if (mode === 'seckill') {
      const seckill = this.data.seckill
      if (!seckill) {
        wx.showToast({ title: '暂无秒杀', icon: 'none' })
        return
      }
      if (seckill.status === 0) {
        wx.showToast({ title: '秒杀尚未开始', icon: 'none' })
        return
      }
      if (seckill.status !== 1) {
        wx.showToast({ title: '秒杀已结束', icon: 'none' })
        return
      }
      if (Number(seckill.stock || 0) <= 0 || seckill.status === SECKILL_STATUS.SOLD_OUT) {
        wx.showToast({ title: '已售罄', icon: 'none' })
        return
      }
    }
    this.setData({
      showSkuPopup: true,
      buyMode: mode,
      skuQuantity: Math.min(this.data.skuQuantity || 1, this.getMaxSkuQuantity(mode)),
    })
  },

  closeSkuPopup() {
    this.setData({ showSkuPopup: false })
  },

  onQuantityChange(e) {
    let val = parseInt(e.detail.value, 10)
    if (Number.isNaN(val) || val < 1) val = 1
    const maxQty = this.getMaxSkuQuantity()
    if (val > maxQty) val = maxQty
    this.setData({ skuQuantity: val })
  },

  decreaseQuantity() {
    if (this.data.skuQuantity > 1) {
      this.setData({ skuQuantity: this.data.skuQuantity - 1 })
    }
  },

  increaseQuantity() {
    if (this.data.skuQuantity < this.getMaxSkuQuantity()) {
      this.setData({ skuQuantity: this.data.skuQuantity + 1 })
    }
  },

  async confirmSku(e) {
    if (!app.requireLogin(`/pages/product-detail/index?id=${this.data.productId}`)) {
      return
    }

    try {
      if (this.data.buyMode === 'seckill') {
        this.closeSkuPopup()
        this.startSeckillCheckout()
        return
      }

      await request.post(
        '/cart',
        {
          productId: this.data.productId,
          quantity: this.data.skuQuantity,
        },
        { showLoading: true },
      )
      if (typeof app.refreshCartCount === 'function') {
        app.refreshCartCount().catch(() => {})
      }

      this.closeSkuPopup()

      if (this.data.buyMode === 'cart') {
        const touch = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || { clientX: 150, clientY: 500 }
        const { clientX, clientY } = touch
        const cartParabola = this.selectComponent('#cartParabola')
        if (cartParabola && this.data.product) {
          cartParabola.animate({
            startX: clientX,
            startY: clientY,
            image: this.data.product.images[0] || this.data.product.image,
          })
        } else {
          wx.showToast({ title: '已加入购物车', icon: 'success' })
        }
      }

      this.recordBehaviorSilent(this.data.productId, 'cart')

      if (this.data.buyMode === 'buy') {
        const recQuery = this.data.recommendationToken
          ? `&recToken=${encodeURIComponent(this.data.recommendationToken)}&recScene=${encodeURIComponent(this.data.recommendationScene || 'detail')}`
          : ''
        app.navigateToPage(
          `/pages/checkout/index?mode=direct&productId=${this.data.productId}&quantity=${this.data.skuQuantity}${recQuery}`
        )
      } else if (this.data.recommendationToken) {
        wx.showToast({ title: '偏好已更新', icon: 'success' })
      }
    } catch (error) {
      console.error('操作失败', error)
      await this.handleSkuActionError(error)
    }
  },

  async addToCart() {
    this.openSkuPopup({
      currentTarget: {
        dataset: {
          mode: 'cart',
        },
      },
    })
  },

  async handleSkuActionError(error) {
    const message = (error && (error.message || error.errMsg || error.msg)) || ''
    if (!message.includes('秒杀商品请直接使用秒杀购买')) {
      wx.showToast({
        title: (message || '操作失败').slice(0, 18),
        icon: 'none',
      })
      return
    }

    this.closeSkuPopup()

    try {
      await this.loadProductDetail(this.data.productId, { showLoading: false })
    } catch (refreshError) {
      console.warn('[ProductDetail] refresh seckill state failed', refreshError)
    }

    const activeSeckill = this.data.seckill && this.data.seckill.status === 1
    wx.showToast({
      title: activeSeckill ? '请走秒杀' : '秒杀商品',
      icon: 'none',
    })

    if (activeSeckill) {
      const quantity = Math.min(this.data.skuQuantity || 1, this.getMaxSkuQuantity('seckill'))
      setTimeout(() => {
        this.safeSetData({
          showSkuPopup: true,
          buyMode: 'seckill',
          skuQuantity: quantity,
        })
      }, 120)
    }
  },

  async buyNow() {
    this.openSkuPopup({
      currentTarget: {
        dataset: {
          mode: 'buy',
        },
      },
    })
  },

  getMaxSkuQuantity(modeOverride) {
    const mode = modeOverride || this.data.buyMode
    if (mode === 'seckill' && this.data.seckill) {
      const stock = Number(this.data.seckill.stock || 0)
      const limit = Number(this.data.seckill.limitPerUser || 1)
      if (stock <= 0) return 0
      return Math.max(1, Math.min(stock, limit > 0 ? limit : stock))
    }
    return Math.max(1, Number(this.data.product?.stock || 1))
  },

  startSeckillCheckout() {
    const seckill = this.data.seckill
    if (!seckill || seckill.status !== SECKILL_STATUS.ACTIVE) {
      wx.showToast({ title: '秒杀状态已变化，请刷新后重试', icon: 'none' })
      return
    }
    if (Number(seckill.stock || 0) <= 0) {
      wx.showToast({ title: '已售罄', icon: 'none' })
      this.loadProductDetail(this.data.productId, { showLoading: false, forceRefresh: true })
      return
    }
    const quantity = Math.max(1, this.data.skuQuantity || 1)
    const applyQuery = seckill.applyId ? `&seckillApplyId=${seckill.applyId}` : ''
    app.navigateToPage(`/pages/checkout/index?mode=seckill&productId=${this.data.productId}${applyQuery}&quantity=${quantity}`)
  },

  async toggleFavorite() {
    if (!app.requireLogin(`/pages/product-detail/index?id=${this.data.productId}`)) {
      return
    }

    try {
      if (this.data.isFavorite) {
        await request.delete(`/user/favorites/${this.data.productId}`, {}, { showLoading: false })
        wx.showToast({ title: '已取消收藏', icon: 'success' })
      } else {
        await request.post(`/user/favorites/${this.data.productId}`, {}, { showLoading: false })
        wx.showToast({ title: '已收藏', icon: 'success' })
        this.recordBehaviorSilent(this.data.productId, 'favorite')
      }
      this.setData({ isFavorite: !this.data.isFavorite })
    } catch (error) {
      console.error('收藏操作失败', error)
    }
  },

  async loadSimilarProducts(id, options = {}) {
    try {
      const res = await request.get(`/recommendations/similar/${id}`, { limit: 6 }, {
        showLoading: false,
        cacheTtl: CACHE_TTL.SIMILAR,
        cacheByUser: app.isLoggedIn(),
        forceRefresh: !!options.forceRefresh
      })
      const similarProducts = (res || []).map(item => ({
        id: item.id,
        image: item.mainImage || item.image || '',
        title: item.name || '',
        price: item.price,
        tag: Array.isArray(item.tags) && item.tags.length ? item.tags[0] : '',
        salesText: item.salesCount != null ? String(item.salesCount) : '',
        extraInfo: item.categoryName || item.category || '',
        reason: item.recommendReason || '相似推荐',
        reasonTags: this.normalizeReasonTags(item),
        recommendationToken: item.recommendationToken || '',
      }))
      if (this.isPageInactive()) return
      this.safeSetData({
        similarProducts,
        decisionBoard: this.buildDecisionBoard(this.data.product, similarProducts),
      })
      recommendationTracker.trackExposures(similarProducts, 'similar')
    } catch (error) {
      console.warn('[ProductDetail] similar products failed', error)
    }
  },

  async loadReviews(productId, options = {}) {
    if (!productId) {
      return
    }

    const refresh = !!options.refresh
    const nextPage = refresh ? 1 : this.data.reviewPage
    if (!refresh && (this.data.reviewLoading || this.data.reviewLoadingMore || !this.data.reviewHasMore)) {
      return
    }

    this.safeSetData(refresh
      ? { reviewLoading: true }
      : { reviewLoadingMore: true })

    try {
      const query = {
        page: nextPage,
        size: 4
      }

      if (Number(this.data.reviewFilterRating) > 0) {
        query.rating = Number(this.data.reviewFilterRating)
      }

      const res = await request.get(`/reviews/product/${productId}`, query, {
        showLoading: false,
        cacheTtl: CACHE_TTL.REVIEWS,
        forceRefresh: !!options.forceRefresh
      })

      if (this.isPageInactive()) return

      const pageData = res && res.reviews ? res.reviews : {}
      const records = Array.isArray(pageData.records) ? pageData.records : []
      const normalized = records.map(item => this.normalizeReviewItem(item))
      const total = Number(res && res.totalCount != null ? res.totalCount : pageData.total || 0)
      const filteredTotal = Number(pageData.total != null ? pageData.total : total)
      const avg = res && res.avgRating != null ? this.formatPrice(res.avgRating) : this.formatPrice(this.data.product?.rating || 5)
      const nextList = refresh ? normalized : this.data.reviewList.concat(normalized)

      this.safeSetData({
        reviewList: nextList,
        reviewAverage: avg || '5.0',
        reviewTotal: total,
        reviewFilteredTotal: filteredTotal,
        reviewPage: nextPage + 1,
        reviewHasMore: nextList.length < filteredTotal,
        reviewLoading: false,
        reviewLoadingMore: false,
        decisionBoard: this.buildDecisionBoard(this.data.product, this.data.similarProducts, total)
      })
    } catch (error) {
      console.warn('[ProductDetail] reviews failed', error)
      this.safeSetData({
        reviewLoading: false,
        reviewLoadingMore: false
      })
    }
  },

  normalizeReviewItem(item) {
    const rating = Math.max(1, Math.min(5, Number(item && item.rating) || 5))
    const username = item.username || '匿名用户'
    return {
      id: item.id,
      username,
      usernameInitial: `${username}`.trim().charAt(0) || '匿',
      avatar: item.avatar || '',
      rating,
      stars: Array.from({ length: 5 }, (_, index) => index < rating),
      content: item.content || '',
      images: Array.isArray(item.images) ? item.images.filter(Boolean) : [],
      tags: Array.isArray(item.tags) ? item.tags.filter(Boolean).slice(0, 3) : [],
      helpfulCount: Number(item.helpfulCount || 0),
      helpfulVoted: !!item.helpfulVoted,
      createTime: item.createTime ? `${item.createTime}`.slice(0, 10) : '',
      reply: item.reply || '',
      appendContent: item.appendContent || '',
      appendImages: Array.isArray(item.appendImages) ? item.appendImages.filter(Boolean) : []
    }
  },

  async toggleReviewHelpful(e) {
    const id = Number(e.currentTarget?.dataset?.id)
    const index = Number(e.currentTarget?.dataset?.index)
    if (!Number.isFinite(id) || id <= 0 || !Number.isInteger(index)) {
      return
    }
    if (!app.requireLogin(`/pages/product-detail/index?id=${this.data.productId}`)) {
      return
    }

    const review = this.data.reviewList[index]
    if (!review) {
      return
    }

    try {
      const latest = review.helpfulVoted
        ? await request.delete(`/reviews/${id}/helpful`, {}, { showLoading: false })
        : await request.post(`/reviews/${id}/helpful`, {}, { showLoading: false })
      this.safeSetData({
        [`reviewList[${index}]`]: this.normalizeReviewItem(latest)
      })
    } catch (error) {
      console.warn('[ProductDetail] helpful toggle failed', error)
    }
  },

  previewReviewImage(e) {
    const reviewIndex = Number(e.currentTarget?.dataset?.reviewIndex)
    const imageIndex = Number(e.currentTarget?.dataset?.imageIndex || 0)
    const group = `${e.currentTarget?.dataset?.group || 'images'}`
    const review = Number.isInteger(reviewIndex) ? this.data.reviewList[reviewIndex] : null
    const urls = review && Array.isArray(review[group]) ? review[group] : []
    const current = urls[imageIndex] || urls[0] || ''
    if (!urls.length) {
      return
    }
    wx.previewImage({
      current: current || urls[0],
      urls
    })
  },

  loadMoreReviews() {
    this.loadReviews(this.data.productId, { refresh: false })
  },

  selectReviewFilter(e) {
    const rating = Number(e.currentTarget?.dataset?.rating || 0)
    if (!Number.isFinite(rating) || rating < 0 || this.data.reviewFilterRating === rating) {
      return
    }

    this.safeSetData({
      reviewFilterRating: rating,
      reviewList: [],
      reviewPage: 1,
      reviewHasMore: false
    })
    this.loadReviews(this.data.productId, { refresh: true })
  },

  goToReviewOrders() {
    app.navigateToPage('/pages/orders/index?type=completed')
  },

  normalizeReasonTags(item) {
    const raw = Array.isArray(item && item.matchedReasonTags) ? item.matchedReasonTags : []
    return raw
      .map(tag => String(tag || '').replace('：', ':').trim())
      .filter(Boolean)
      .map(tag => {
        const index = tag.indexOf(':')
        return index >= 0 ? tag.slice(index + 1).trim() : tag
      })
      .filter(Boolean)
      .filter((tag, index, list) => list.indexOf(tag) === index)
      .slice(0, 3)
  },

  resolveRecommendationSourceLabel(rawScene) {
    const scene = `${rawScene || ''}`.toLowerCase()
    if (scene.includes('hot')) return '热榜召回'
    if (scene.includes('similar') || scene.includes('content')) return '内容相似'
    if (scene.includes('collaborative') || scene.includes('cf')) return '相似用户'
    if (scene.includes('cart')) return '购物车补购'
    if (scene.includes('search')) return '搜索意图'
    return this.data.recommendationToken ? '实时行为' : '商品详情'
  },

  buildRecommendationInsight(product, raw = {}) {
    if (!product) return null
    const sourceLabel = raw.sourceLabel || raw.recommendationSourceLabel || this.resolveRecommendationSourceLabel(this.data.recommendationScene)
    const tags = this.uniqueStrings([
      ...(Array.isArray(raw.reasonTags) ? raw.reasonTags : []),
      ...(Array.isArray(raw.matchedReasonTags) ? this.normalizeReasonTags(raw) : []),
      raw.recommendReason || raw.reason || '',
      '与你浏览过的商品相似',
      '同类用户高转化',
      '近期热销上升',
    ]).slice(0, 3)
    const token = this.data.recommendationToken || raw.recommendationToken || ''
    return {
      sourceLabel,
      reason: raw.recommendReason || raw.reason || 'score = view*1 + cart*2 + favorite*3 + purchase*8；再结合同类商品与相似用户排序。',
      tags,
      tokenText: token ? `${token}`.slice(0, 12) : '未携带',
      dimensions: [
        { label: '价格带', value: product.price ? `¥${this.formatPrice(product.price)}` : '同价位' },
        { label: '品类', value: Array.isArray(product.tags) && product.tags.length ? product.tags[0] : '相似品类' },
        { label: '归因 token', value: this.data.recommendationToken ? '已归因' : '详情浏览' },
        { label: '相似用户', value: Number(product.salesCount || 0) > 0 ? `purchase ${product.salesCount}` : 'high-interest' },
      ],
    }
  },

  openRecommendationSheet() {
    if (!this.data.recommendationInsight) {
      wx.showToast({ title: '推荐依据暂不可用', icon: 'none' })
      return
    }
    this.setData({ showRecommendationSheet: true })
  },

  closeRecommendationSheet() {
    this.setData({ showRecommendationSheet: false })
  },

  buildDecisionBoard(product, similarProducts, reviewTotalOverride) {
    if (!product) {
      return createDecisionBoard()
    }

    const safeSimilar = Array.isArray(similarProducts) ? similarProducts : []
    const hasStock = Number(product.stock || 0) > 0
    const topTag = Array.isArray(product.tags) && product.tags.length ? product.tags[0] : '商品详情'
    const reviewTotal = Number.isFinite(Number(reviewTotalOverride)) ? Number(reviewTotalOverride) : Number(this.data.reviewTotal || 0)

    return {
      badge: hasStock ? '可下单' : '暂时缺货',
      headline: topTag ? `${topTag}参考` : '购买参考',
      subline: hasStock ? '' : '库存不足',
      facts: [
        { label: '价格', value: `¥${this.formatPrice(product.price)}` },
        { label: '销量', value: `${product.salesCount || 0}` },
        { label: '评分', value: product.rating ? `${this.formatPrice(product.rating)} / 5` : '暂无' },
        { label: '评价', value: `${reviewTotal || 0} 条` },
      ],
      prompts: this.uniqueStrings([
        `${this.shortTitle(product.title)}适合谁`,
        safeSimilar.length ? '对比相似款' : '',
        '查看推荐理由',
      ]).slice(0, 3),
    }
  },

  formatPrice(value) {
    const numeric = Number(value || 0)
    if (!Number.isFinite(numeric)) return `${value || '--'}`
    return Number.isInteger(numeric)
      ? `${numeric}`
      : numeric.toFixed(2).replace(/\.?0+$/, '')
  },

  shortTitle(title) {
    const text = `${title || ''}`.replace(/\s+/g, '').trim()
    if (!text) return '这款商品'
    return text.length > 16 ? text.slice(0, 16) : text
  },

  uniqueStrings(values) {
    const source = Array.isArray(values) ? values : []
    const result = []
    source.forEach(value => {
      const text = `${value || ''}`.trim()
      if (text && result.indexOf(text) === -1) {
        result.push(text)
      }
    })
    return result
  },

  async recordBehavior(productId) {
    try {
      await request.post('/recommendations/behavior', {
        productId,
        behaviorType: 'view',
        recommendationToken: this.data.recommendationToken || '',
      }, { showLoading: false })
    } catch (error) {
      console.warn('[ProductDetail] behavior record failed', error)
    }
  },

  recordBehaviorSilent(productId, type) {
    if (type === 'cart' && this.data.recommendationToken) {
      recommendationTracker.trackAddCart(
        productId,
        this.data.recommendationToken,
        this.data.recommendationScene || 'similar'
      )
      return
    }
    request.post('/recommendations/behavior', {
      productId,
      behaviorType: type,
      recommendationToken: this.data.recommendationToken || '',
    }, { showLoading: false }).catch(() => {})
  },

  goToSimilarProduct(e) {
    const id = e.detail && e.detail.id ? e.detail.id : e.currentTarget.dataset.id
    if (!id) {
      wx.showToast({ title: '商品信息异常', icon: 'none' })
      return
    }
    const recToken = e.detail && e.detail.recToken ? e.detail.recToken : e.currentTarget.dataset.recToken
    if (recToken) {
      recommendationTracker.trackClick(id, recToken, 'similar')
    }
    app.navigateToPage(
      recToken
        ? `/pages/product-detail/index?id=${id}&recToken=${encodeURIComponent(recToken)}&recScene=similar`
        : `/pages/product-detail/index?id=${id}`
    )
  },

  goToCart() {
    app.navigateToPage('/pages/cart/index')
  },

  openServiceAction() {
    if (!app.requireLogin(`/pages/product-detail/index?id=${this.data.productId}`)) {
      return
    }
    wx.showActionSheet({
      itemList: ['联系商家', '官方客服'],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.openMerchantService()
        } else if (res.tapIndex === 1) {
          this.openOfficialService()
        }
      }
    })
  },

  openMerchantService() {
    if (!app.requireLogin(`/pages/product-detail/index?id=${this.data.productId}`)) {
      return
    }
    const query = this.buildServiceQuery('merchant')
    app.navigateToPage(`/pages/customer-chat/index?${query}`)
  },

  openOfficialService() {
    if (!app.requireLogin(`/pages/product-detail/index?id=${this.data.productId}`)) {
      return
    }
    const query = this.buildServiceQuery('support')
    app.navigateToPage(`/pages/customer-chat/index?${query}`)
  },

  buildServiceQuery(openType) {
    const queryParts = [`openType=${encodeURIComponent(openType)}`]
    const productId = im.parsePositiveNumber(this.data.productId)
    const merchantId = im.parsePositiveNumber(this.data.product && this.data.product.merchantId)

    if (productId) {
      queryParts.push(`productId=${productId}`)
    }
    if (merchantId) {
      queryParts.push(`merchantId=${merchantId}`)
    }
    return queryParts.join('&')
  },

  openAiAssistant(e) {
    if (!app.requireLogin(`/pages/product-detail/index?id=${this.data.productId}`)) {
      return
    }

    const prompt = e && e.currentTarget && e.currentTarget.dataset
      ? e.currentTarget.dataset.prompt || ''
      : `${this.shortTitle(this.data.product?.title)}值不值得买`

    app.navigateToPage(`/pages/ai-assistant/index?prompt=${encodeURIComponent(prompt)}`)
  },

  async loadAiSummary(options = {}) {
    if (this.data.aiSummaryLoading || (!options.forceRefresh && this.data.aiSummary) || !this.data.productId) return
    this.safeSetData({ aiSummaryLoading: true })
    try {
      const res = await request.get(`/ai/review-summary/${this.data.productId}`, {}, {
        showLoading: false,
        cacheTtl: CACHE_TTL.AI_SUMMARY,
        forceRefresh: !!options.forceRefresh
      })
      if (this.isPageInactive()) return
      this.safeSetData({ aiSummary: res || '暂无摘要', aiSummaryLoading: false })
    } catch (error) {
      console.error('智能 摘要失败', error)
      if (this.isPageInactive()) return
      this.safeSetData({ aiSummary: '生成失败', aiSummaryLoading: false })
    }
  },

  showAiQaPopup() {
    this.setData({ showAiQa: true, aiQaAnswer: '' })
  },

  hideAiQaPopup() {
    this.setData({ showAiQa: false })
  },

  onAiQaInput(e) {
    this.setData({ aiQaInput: e.detail.value })
  },

  askAiQuick(e) {
    const q = e.currentTarget.dataset.q
    this.setData({ aiQaInput: q })
    this.askAi()
  },

  async askAi() {
    const question = this.data.aiQaInput.trim()
    if (!question || this.data.aiQaLoading) return
    this.safeSetData({ aiQaLoading: true, aiQaAnswer: '' })
    try {
      const res = await request.post('/ai/product-qa', {
        productId: this.data.productId,
        question,
      }, { showLoading: false })
      if (this.isPageInactive()) return
      this.safeSetData({ aiQaAnswer: res || '暂无回答', aiQaLoading: false })
    } catch (error) {
      console.error('智能 问答失败', error)
      if (this.isPageInactive()) return
      this.safeSetData({ aiQaAnswer: '网络忙', aiQaLoading: false })
    }
  },

  parseSeckillInfo(rawProduct) {
    const applyId = rawProduct.seckillApplyId || rawProduct.seckill_apply_id
    const activityId = rawProduct.seckillActivityId || rawProduct.seckill_activity_id
    const seckillPrice = Number(rawProduct.seckillPrice)
    if ((!applyId && !activityId) || !Number.isFinite(seckillPrice) || seckillPrice <= 0) {
      return null
    }

    const startTime = rawProduct.seckillStartTime || ''
    const endTime = rawProduct.seckillEndTime || ''
    const startAt = startTime ? new Date(startTime.replace(/-/g, '/')).getTime() : 0
    const endAt = endTime ? new Date(endTime.replace(/-/g, '/')).getTime() : 0
    const now = Date.now()
    const statusFromApi = Number(rawProduct.seckillStatus)

    let status = SECKILL_STATUS.ENDED
    if (startAt && endAt) {
      if (now < startAt) {
        status = SECKILL_STATUS.UPCOMING
      } else if (now >= startAt && now < endAt) {
        status = SECKILL_STATUS.ACTIVE
      } else {
        status = SECKILL_STATUS.ENDED
      }
    } else if (
      statusFromApi === SECKILL_STATUS.UPCOMING
      || statusFromApi === SECKILL_STATUS.ACTIVE
      || statusFromApi === SECKILL_STATUS.ENDED
      || statusFromApi === SECKILL_STATUS.SOLD_OUT
    ) {
      status = statusFromApi
    }

    const stock = Math.max(0, Number(rawProduct.seckillStock != null ? rawProduct.seckillStock : rawProduct.stock || 0))
    const limitPerUser = Math.max(1, Number(rawProduct.seckillLimitPerUser || 1))
    if (status === SECKILL_STATUS.ACTIVE && stock <= 0) {
      status = SECKILL_STATUS.SOLD_OUT
    }
    const statusLabel = status === SECKILL_STATUS.ACTIVE
      ? '抢购中'
      : (status === SECKILL_STATUS.UPCOMING
        ? '即将开始'
        : (status === SECKILL_STATUS.SOLD_OUT ? '已售罄' : '活动已结束'))
    const countdownText = status === SECKILL_STATUS.ACTIVE
      ? (endAt ? this.formatSeckillCountdownText(endAt, '距结束') : '抢购进行中')
      : (status === SECKILL_STATUS.UPCOMING
        ? this.getSeckillPendingCountdownText(startAt)
        : (status === SECKILL_STATUS.SOLD_OUT ? '已抢光' : '活动已结束'))
    return {
      applyId: applyId || null,
      activityId: activityId || null,
      seckillPrice,
      originalPrice: Number(rawProduct.price || 0),
      startTime,
      endTime,
      status,
      stock,
      limitPerUser,
      startAt,
      endAt,
      statusLabel,
      countdownText,
    }
  },

  getSeckillPendingCountdownText(startAt) {
    if (!startAt || Number.isNaN(startAt)) {
      return '即将开始'
    }
    const diff = startAt - Date.now()
    if (diff <= 0) {
      return '即将开始'
    }
    if (diff > SECKILL_COUNTDOWN_WINDOW_MS) {
      return '12小时后开启倒计时'
    }
    return this.formatSeckillCountdownText(startAt, '距开始')
  },

  formatSeckillCountdownText(targetAt, prefix) {
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

  startSeckillCountdown(seckill) {
    this.clearSeckillCountdownTimer()
    if (this.isPageInactive()) {
      return
    }
    this._seckillTimer = setInterval(() => {
      if (this.isPageInactive()) {
        this.clearSeckillCountdownTimer()
        return
      }
      const latest = this.data.seckill
      if (!latest) {
        this.clearSeckillCountdownTimer()
        return
      }
      const now = Date.now()
      if (latest.status === SECKILL_STATUS.UPCOMING) {
        const startAt = Number(latest.startAt || 0)
        if (!startAt) {
          const fallbackText = '即将开始'
          if (fallbackText !== this.data.seckillCountdownText) {
            this.safeSetData({ seckillCountdownText: fallbackText })
          }
          return
        }
        if (now >= startAt) {
          this.clearSeckillCountdownTimer()
          if (!this.isPageInactive()) {
            this.loadProductDetail(this.data.productId, { showLoading: false })
          }
          return
        }
        const countdownText = this.getSeckillPendingCountdownText(startAt)
        if (countdownText !== this.data.seckillCountdownText) {
          this.safeSetData({ seckillCountdownText: countdownText })
        }
        return
      }

      const targetAt = Number(latest.endAt || 0)
      if (!targetAt) {
        const fallbackText = '抢购进行中'
        if (fallbackText !== this.data.seckillCountdownText) {
          this.safeSetData({ seckillCountdownText: fallbackText })
        }
        return
      }
      if (now >= targetAt) {
        this.clearSeckillCountdownTimer()
        if (!this.isPageInactive()) {
            this.safeSetData({
              seckill: {
                ...latest,
                status: SECKILL_STATUS.ENDED,
                statusLabel: '活动已结束',
                countdownText: '活动已结束',
              },
            seckillCountdownText: '活动已结束',
          })
          this.loadProductDetail(this.data.productId, { showLoading: false })
        }
        return
      }
      const countdownText = this.formatSeckillCountdownText(targetAt, '距结束')
      if (countdownText !== this.data.seckillCountdownText) {
        this.safeSetData({ seckillCountdownText: countdownText })
      }
    }, 1000)
  },

  clearSeckillCountdownTimer() {
    if (this._seckillTimer) {
      clearInterval(this._seckillTimer)
      this._seckillTimer = null
    }
  },

  onShareAppMessage(res) {
    const product = this.data.product || {}
    const image = Array.isArray(product.images) && product.images.length ? product.images[0] : ''
    return buildProductShareMessage(res, {
      id: this.data.productId || product.id,
      title: product.title || product.name,
      image,
    })
  },
})

