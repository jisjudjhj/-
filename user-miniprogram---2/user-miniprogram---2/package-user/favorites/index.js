const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const { buildProductShareMessage } = require('../../utils/share')

Page({
  data: {
    list: [],
    loading: false,
    loadingMore: false,
    refreshing: false,
    noMore: false,
    page: 1,
    size: 10
  },

  onShow() {
    this.setData({ page: 1, list: [], noMore: false })
    this.loadData()
  },

  async onRefresh() {
    this.setData({ page: 1, list: [], noMore: false })
    await this.loadData()
    this.setData({ refreshing: false })
  },

  async loadData() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const res = await request.get('/user/favorites', {
        page: this.data.page,
        size: this.data.size
      })
      const records = ((res && res.records) || []).map((record) => ({
        ...record,
        product: {
          ...(record.product || {}),
          displayImage: resolveProductImage(record.product || {}),
          categoryName: (record.product && (record.product.categoryName || record.product.category)) || '',
          extraInfo: this.buildProductInfoLine(record.product || {})
        },
        cardProduct: this.buildCardProduct(record)
      }))
      const total = (res && res.total) || 0
      const newList = this.data.page === 1 ? records : this.data.list.concat(records)
      this.setData({
        list: newList,
        noMore: newList.length >= total
      })
    } catch (e) {
      console.error('加载收藏列表失败', e)
    } finally {
      this.setData({ loading: false, loadingMore: false })
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

  buildProductInfoLine(product) {
    const category = product.categoryName || product.category || ''
    const salesText = this.formatSalesText(product.salesCount || product.saleCount || product.sales || 0)
    const parts = []
    if (category) parts.push(category)
    if (salesText && salesText !== '0') parts.push(`已售 ${salesText}`)
    return parts.join(' · ') || '收藏商品'
  },

  buildCardProduct(record) {
    const product = record.product || {}
    const tags = Array.isArray(product.tags) ? product.tags.filter(Boolean) : []
    return {
      id: product.id || record.productId,
      title: product.name || '已收藏商品',
      name: product.name || '已收藏商品',
      image: resolveProductImage(product),
      price: product.price || '',
      tag: tags[0] || '收藏',
      categoryName: product.categoryName || product.category || '',
      extraInfo: this.buildProductInfoLine(product),
      reason: record.createTime ? `收藏于 ${record.createTime}` : '可继续回看和比价',
      salesText: this.formatSalesText(product.salesCount || product.saleCount || product.sales || 0)
    }
  },

  loadMore() {
    if (this.data.noMore || this.data.loadingMore) return
    this.setData({
      page: this.data.page + 1,
      loadingMore: true
    })
    this.loadData()
  },

  goDetail(e) {
    const id = e.detail && e.detail.id
    if (id) {
      app.navigateToPage(`/pages/product-detail/index?id=${id}`)
    }
  },

  async removeFav(e) {
    const { productId, index } = e.currentTarget.dataset
    try {
      await request.delete(`/user/favorites/${productId}`)
      const list = this.data.list
      list.splice(index, 1)
      this.setData({ list })
      wx.showToast({ title: '已取消收藏', icon: 'none' })
    } catch (err) {
      console.error('取消收藏失败', err)
    }
  },

  goHome() {
    app.navigateToPage('/pages/home/index')
  },

  handleFavoriteImageError(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (!Number.isInteger(index)) {
      return
    }
    this.setData({
      [`list[${index}].product.displayImage`]: ''
    })
  },

  onShareAppMessage(res) {
    return buildProductShareMessage(res)
  }
})
