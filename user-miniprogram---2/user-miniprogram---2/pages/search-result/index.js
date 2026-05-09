const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const { buildProductShareMessage } = require('../../utils/share')
const recommendationTracker = require('../../utils/recommendation-tracker')
const CACHE_TTL = {
  LIST: 2 * 60 * 1000,
  RECOMMEND: 2 * 60 * 1000
}

Page({
  data: {
    pageTitle: '搜索结果',
    keyword: '',
    categoryId: '',
    categoryName: '',
    products: [],
    totalCount: 0,
    page: 1,
    size: 10,
    hasMore: true,
    loading: true,
    refreshing: false,
    recommendProducts: [],
    imageErrorMap: {},
    recommendImageErrorMap: {},
    sortBy: 'default',
    sortOptions: [
      { label: '综合', value: 'default' },
      { label: '销量', value: 'sales' },
      { label: '价格↑', value: 'priceAsc' },
      { label: '价格↓', value: 'priceDesc' }
    ]
  },

  onLoad(options) {
    try {
      const keyword = options.keyword ? decodeURIComponent(options.keyword) : ''
      const categoryId = options.categoryId || ''
      const title = options.title ? decodeURIComponent(options.title) : ''
      const categoryName = categoryId ? title : ''

      this.setData({
        keyword,
        categoryId,
        categoryName,
        pageTitle: title || (keyword ? '搜索结果' : '商品列表')
      })

      this.loadProducts(true)
    } catch (err) {
      console.error('[search-result] onLoad error:', err)
      this.setData({ loading: false })
    }
  },

  async loadProducts(refresh = false, options = {}) {
    if (refresh) {
      this.setData({ loading: true, imageErrorMap: {} })
    }

    const nextPage = refresh ? 1 : this.data.page

    try {
      const params = {
        page: nextPage,
        size: this.data.size
      }
      if (this.data.keyword) params.keyword = this.data.keyword
      if (this.data.categoryId) params.categoryId = this.data.categoryId
      if (this.data.sortBy === 'sales') {
        params.sortField = 'salesCount'
        params.sortOrder = 'desc'
      } else if (this.data.sortBy === 'priceAsc') {
        params.sortField = 'price'
        params.sortOrder = 'asc'
      } else if (this.data.sortBy === 'priceDesc') {
        params.sortField = 'price'
        params.sortOrder = 'desc'
      } else {
        params.sortField = 'create_time'
        params.sortOrder = 'desc'
      }

      const res = await request.get('/products/list', params, {
        showLoading: false,
        cacheTtl: CACHE_TTL.LIST,
        forceRefresh: !!options.forceRefresh
      })

      const list = ((res && res.records) || []).map((item) => {
        const id = item.id
        const scene = this.data.keyword ? 'search_keyword' : 'search_category'
        return {
        id,
        title: item.name || '',
        image: resolveProductImage(item),
        price: item.price,
        tag: Array.isArray(item.tags) && item.tags.length ? item.tags[0] : '',
        salesCount: Number(item.salesCount || 0),
        salesText: this.formatSalesText(item.salesCount || 0),
        categoryName: item.categoryName || item.category || '',
        extraInfo: this.buildProductInfoLine(item),
        recommendationScene: scene,
        recommendationToken: item.recommendationToken || `search_${id}_${nextPage}`
      }
      })

      const allProducts = refresh ? list : [...this.data.products, ...list]

      this.setData({
        products: allProducts,
        totalCount: res && res.total ? res.total : allProducts.length,
        page: nextPage + 1,
        hasMore: list.length === this.data.size,
        loading: false
      })
      recommendationTracker.trackExposures(list, this.data.keyword ? 'search_keyword' : 'search_category')

      if (refresh && allProducts.length === 0) {
        this.loadRecommendations()
      }
    } catch (error) {
      console.error('[search-result] 加载失败:', error)
      this.setData({
        products: refresh ? [] : this.data.products,
        hasMore: false,
        loading: false
      })
      if (refresh) {
        this.loadRecommendations()
      }
    }
  },

  async loadRecommendations(options = {}) {
    try {
      const isPersonalized = app.isLoggedIn()
      const endpoint = isPersonalized ? '/recommendations/guess-you-like' : '/recommendations/hot'
      const res = await request.get(endpoint, { limit: 6 }, {
        showLoading: false,
        cacheTtl: CACHE_TTL.RECOMMEND,
        cacheByUser: isPersonalized,
        forceRefresh: !!options.forceRefresh
      })
      const list = (Array.isArray(res) ? res : []).map((item) => ({
        id: item.id,
        title: item.name || '',
        image: resolveProductImage(item),
        price: item.price,
        tag: Array.isArray(item.tags) && item.tags.length ? item.tags[0] : '',
        salesCount: Number(item.salesCount || 0),
        salesText: this.formatSalesText(item.salesCount || 0),
        categoryName: item.categoryName || item.category || '',
        extraInfo: this.buildProductInfoLine(item),
        reason: item.recommendReason || (
          isPersonalized
            ? '没找到想要的时，先看看这些更贴近你偏好的商品'
            : '热度补位'
        ),
        reasonTags: this.normalizeReasonTags(item),
        recommendationScene: isPersonalized ? 'search_personalized' : 'search_hot',
        recommendationToken: item.recommendationToken || `search_${item.id}_fallback`
      }))
      this.setData({ recommendProducts: list, recommendImageErrorMap: {} })
      recommendationTracker.trackExposures(list, isPersonalized ? 'search_personalized' : 'search_hot')
    } catch (e) {
      console.error('[search-result] 加载推荐失败:', e)
    }
  },

  formatSalesText(salesCount) {
    const count = Number(salesCount || 0)
    if (!Number.isFinite(count) || count <= 0) {
      return '0'
    }
    if (count >= 10000) {
      return `${(count / 10000).toFixed(1)}w`
    }
    return `${Math.round(count)}`
  },

  buildProductInfoLine(item) {
    const category = item.categoryName || item.category || ''
    const salesText = this.formatSalesText(item.salesCount || item.saleCount || item.sales || 0)
    const parts = []
    if (category) parts.push(category)
    if (salesText && salesText !== '0') parts.push(`已售 ${salesText}`)
    return parts.join(' · ') || '精选商品'
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

  async onRefresh() {
    await this.loadProducts(true, { forceRefresh: true })
    this.setData({ refreshing: false })
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadProducts(false)
    }
  },

  onSortChange(e) {
    const sortBy = e.currentTarget.dataset.value
    if (!sortBy || sortBy === this.data.sortBy) return
    this.setData({
      sortBy,
      page: 1,
      hasMore: true,
      products: [],
      imageErrorMap: {}
    })
    this.loadProducts(true)
  },

  onProductImageError(e) {
    const id = Number(e.currentTarget.dataset.id || 0)
    if (!id) return
    this.setData({ [`imageErrorMap.${id}`]: true })
  },

  onRecommendImageError(e) {
    const id = Number(e.currentTarget.dataset.id || 0)
    if (!id) return
    this.setData({ [`recommendImageErrorMap.${id}`]: true })
  },

  goToDetail(e) {
    const id = e.detail && e.detail.id ? e.detail.id : e.currentTarget.dataset.id
    const recToken = e.detail && e.detail.recToken ? e.detail.recToken : e.currentTarget.dataset.recToken
    const product = [...(this.data.products || []), ...(this.data.recommendProducts || [])]
      .find((item) => Number(item.id) === Number(id))
    const recScene = product && product.recommendationScene ? product.recommendationScene : 'search_keyword'
    if (recToken) {
      recommendationTracker.trackClick(id, recToken, recScene)
    }
    app.navigateToPage(
      recToken
        ? `/pages/product-detail/index?id=${id}&recToken=${encodeURIComponent(recToken)}&recScene=${encodeURIComponent(recScene)}`
        : `/pages/product-detail/index?id=${id}`
    )
  },

  onShareAppMessage(res) {
    return buildProductShareMessage(res)
  }
})
