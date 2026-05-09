const app = getApp()
const request = require('../../utils/request')
const { mapCategoriesWithIconMeta } = require('../../utils/category-icons')
const { resolveProductImage } = require('../../utils/image')
const { buildProductShareMessage } = require('../../utils/share')
const CACHE_TTL = {
  STATIC: 30 * 60 * 1000,
  LIST: 3 * 60 * 1000
}

Page({
  data: {
    isScrolled: false,
    hotTags: [],
    categories: [],
    searchValue: '',
    refreshing: false,
    activeCategory: '',
    activeCategoryName: '',
    products: [],
    skeletonRows: [1, 2, 3, 4],
    loadingProducts: false,
    hasMoreProducts: true,
    productPage: 1
  },

  onLoad() {
    this.loadExploreData()
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 })
      app.applyMessageBadge()
      app.refreshMessageUnreadCount().catch(() => {})
      app.refreshCartCount().catch(() => {})
    }
  },

  async onRefresh() {
    await this.loadExploreData({ forceRefresh: true })
    this.setData({ refreshing: false })
  },

  async loadExploreData(options = {}) {
    const forceRefresh = !!options.forceRefresh
    const [tagsResult, categoriesResult] = await Promise.allSettled([
      request.get('/search/hot', { limit: 10 }, {
        showLoading: false,
        cacheTtl: CACHE_TTL.STATIC,
        forceRefresh
      }),
      request.get('/products/categories', {}, {
        showLoading: false,
        cacheTtl: CACHE_TTL.STATIC,
        forceRefresh
      })
    ])

    let hotTags = []
    let categories = []

    if (tagsResult.status === 'fulfilled') {
      hotTags = Array.isArray(tagsResult.value)
        ? tagsResult.value.map((item) => (typeof item === 'string' ? item : item.keyword || '')).filter(Boolean)
        : []
    } else {
      console.error('加载热门搜索失败', tagsResult.reason)
    }

    if (categoriesResult.status === 'fulfilled') {
      categories = mapCategoriesWithIconMeta(categoriesResult.value || []).map((item) => ({
        id: item.id,
        name: item.name || '',
        iconClass: item.iconClass
      }))
    } else {
      console.error('加载商品分类失败', categoriesResult.reason)
    }

    this.setData({ hotTags, categories })
    await this.loadProducts(true, { forceRefresh })
  },

  async loadProducts(refresh, options = {}) {
    if (this.data.loadingProducts) return
    if (!refresh && !this.data.hasMoreProducts) return

    const page = refresh ? 1 : this.data.productPage
    this.setData({ loadingProducts: true })

    try {
      const params = { page, size: 10, sortField: 'create_time', sortOrder: 'desc' }
      if (this.data.activeCategory) {
        params.categoryId = this.data.activeCategory
      }
      const res = await request.get('/products/list', params, {
        showLoading: false,
        cacheTtl: CACHE_TTL.LIST,
        forceRefresh: !!options.forceRefresh
      })
      const records = (res && res.records) || (Array.isArray(res) ? res : [])

      const mapped = records.map((item) => ({
        id: item.id,
        title: item.name || '',
        image: resolveProductImage(item),
        price: item.price,
        sales: item.salesCount || 0,
        salesText: this.formatSales(item.salesCount),
        categoryName: item.categoryName || item.category || '',
        extraInfo: [item.categoryName || item.category || '', this.formatSales(item.salesCount)].filter(Boolean).join(' · '),
        reason: item.description ? String(item.description).slice(0, 24) : '近期上新与高销量商品',
        tag: (item.salesCount || 0) > 500 ? '热卖' : ''
      }))

      const allProducts = refresh ? mapped : [...this.data.products, ...mapped]

      this.setData({
        products: allProducts,
        productPage: page + 1,
        hasMoreProducts: records.length >= 10,
        loadingProducts: false
      })
    } catch (e) {
      console.error('加载商品失败', e)
      this.setData({ loadingProducts: false })
    }
  },

  formatSales(sales) {
    if (!sales) return ''
    if (sales >= 10000) return (sales / 10000).toFixed(1) + '万+已售'
    if (sales >= 1000) return (sales / 1000).toFixed(1) + 'k已售'
    if (sales > 0) return sales + '已售'
    return ''
  },

  switchCategory(e) {
    const id = e.currentTarget.dataset.id || ''
    const name = e.currentTarget.dataset.name || ''
    if (id === this.data.activeCategory) return
    this.setData({
      activeCategory: id,
      activeCategoryName: name,
      products: [],
      hasMoreProducts: true,
      productPage: 1
    })
    this.loadProducts(true)
  },

  onReachBottom() {
    this.loadProducts(false)
  },

  onSearchInput(e) {
    this.setData({ searchValue: e.detail.value })
  },

  async onSearchConfirm() {
    const keyword = this.data.searchValue.trim()
    if (!keyword) return

    try {
      const app = getApp()
      if (app.isLoggedIn()) {
        await request.post('/search/record', { keyword }, { showLoading: false })
      }
    } catch (error) {
      console.warn('[Search] record failed', error)
    }

    app.navigateToPage(`/pages/search-result/index?keyword=${encodeURIComponent(keyword)}`).then((ok) => {
      if (!ok) {
        wx.showToast({ title: '页面跳转失败', icon: 'none' })
      }
    })
  },

  onTagClick(e) {
    const keyword = e.currentTarget.dataset.tag
    if (!keyword) return
    app.navigateToPage(`/pages/search-result/index?keyword=${encodeURIComponent(keyword)}`).then((ok) => {
      if (!ok) {
        wx.showToast({ title: '页面跳转失败', icon: 'none' })
      }
    })
  },

  onScroll(e) {
    const scrollTop = e.detail.scrollTop
    if (scrollTop > 10 && !this.data.isScrolled) {
      this.setData({ isScrolled: true })
    } else if (scrollTop <= 10 && this.data.isScrolled) {
      this.setData({ isScrolled: false })
    }
  },

  goToDetail(e) {
    const id = e.detail?.id || e.currentTarget?.dataset?.id
    if (!id) return
    app.navigateToPage(`/pages/product-detail/index?id=${id}`).then((ok) => {
      if (!ok) {
        wx.showToast({ title: '页面跳转失败', icon: 'none' })
      }
    })
  },

  onShareAppMessage(res) {
    return buildProductShareMessage(res)
  }
})
