const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const { buildProductShareMessage } = require('../../utils/share')

Page({
  data: {
    list: [],
    groupedList: [],
    loading: false,
    loadingMore: false,
    refreshing: false,
    noMore: false,
    page: 1,
    size: 30,
    displayCount: 0,
  },

  onShow() {
    this.setData({ page: 1, list: [], groupedList: [], noMore: false, displayCount: 0 })
    this.loadData()
  },

  async onRefresh() {
    this.setData({ page: 1, list: [], groupedList: [], noMore: false, displayCount: 0 })
    await this.loadData()
    this.setData({ refreshing: false })
  },

  async loadData() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const res = await request.get('/user/history', {
        page: this.data.page,
        size: this.data.size
      })
      const records = ((res && res.records) || []).map((record) => {
        const product = record.product || {}
        return {
          ...record,
          product: {
            ...product,
            image: resolveProductImage(product),
          },
          browseTime: this.formatBrowseTime(record.createTime),
          cardProduct: {
            id: product.id || record.productId,
            name: product.name || record.productName || '已下架',
            title: product.name || record.productName || '已下架',
            image: resolveProductImage(product),
            price: product.price || '',
            tag: Array.isArray(product.tags) && product.tags.length ? product.tags[0] : '',
            categoryName: product.categoryName || product.category || '',
            extraInfo: this.buildProductInfoLine(product),
            reason: record.createTime ? `浏览于 ${record.createTime}` : '最近浏览过',
            salesText: product.salesCount ? String(product.salesCount) : '',
          }
        }
      })
      const total = (res && res.total) || 0
      const newList = this.data.page === 1 ? records : this.data.list.concat(records)
      const groupedList = this.groupByDate(newList)
      this.setData({
        list: newList,
        groupedList,
        displayCount: groupedList.reduce((sum, group) => sum + group.records.length, 0),
        noMore: newList.length >= total
      })
    } catch (e) {
      console.error('加载浏览足迹失败', e)
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  buildProductInfoLine(product) {
    const category = product.categoryName || product.category || ''
    const salesCount = Number(product.salesCount || product.saleCount || product.sales || 0)
    const parts = []
    if (category) parts.push(category)
    if (Number.isFinite(salesCount) && salesCount > 0) parts.push(`已售 ${salesCount}`)
    return parts.join(' · ') || '浏览商品'
  },

  groupByDate(list) {
    const map = {}
    const today = this.formatDate(new Date())
    const yesterday = this.formatDate(new Date(Date.now() - 86400000))

    list.forEach(item => {
      const date = (item.createTime || '').substring(0, 10) || '未知日期'
      if (!map[date]) {
        map[date] = {
          byProduct: {},
          records: [],
        }
      }

      const product = item.product || {}
      const productKey = `${product.id || item.productId || product.name || item.productName || item.id || ''}`
      const key = productKey || `record-${item.id || map[date].records.length}`
      const existing = map[date].byProduct[key]
      if (!existing) {
        const merged = {
          ...item,
          browseCount: 1,
        }
        map[date].byProduct[key] = merged
        map[date].records.push(merged)
        return
      }

      existing.browseCount += 1
      if (this.getTimeValue(item.createTime) >= this.getTimeValue(existing.createTime)) {
        Object.assign(existing, {
          ...item,
          browseCount: existing.browseCount,
        })
      }
    })

    return Object.keys(map).sort((a, b) => b.localeCompare(a)).map(date => {
      let dateLabel = date
      if (date === today) dateLabel = '今天'
      else if (date === yesterday) dateLabel = '昨天'
      const records = map[date].records.sort((a, b) => this.getTimeValue(b.createTime) - this.getTimeValue(a.createTime))
      return { date, dateLabel, records }
    })
  },

  getTimeValue(value) {
    const text = `${value || ''}`.replace(/-/g, '/')
    const time = text ? new Date(text).getTime() : 0
    return Number.isFinite(time) ? time : 0
  },

  formatDate(d) {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  },

  formatBrowseTime(value) {
    const text = `${value || ''}`
    if (!text) {
      return '刚刚浏览'
    }
    const time = text.length >= 16 ? text.substring(11, 16) : text
    return time || text
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
    const id = e.currentTarget.dataset.id
    if (id) {
      app.navigateToPage(`/pages/product-detail/index?id=${id}`)
    }
  },

  goCardDetail(e) {
    const id = e.detail && e.detail.id
    if (id) {
      app.navigateToPage(`/pages/product-detail/index?id=${id}`)
    }
  },

  async clearAll() {
    try {
      const res = await new Promise((resolve, reject) => {
        wx.showModal({
          title: '确认清空',
          content: '确定要清空所有浏览记录吗？',
          success: r => resolve(r),
          fail: reject
        })
      })
      if (!res.confirm) return
      await request.delete('/user/history')
      this.setData({ list: [], groupedList: [] })
      wx.showToast({ title: '已清空', icon: 'none' })
    } catch (err) {
      console.error('清空失败', err)
    }
  },

  goHome() {
    app.navigateToPage('/pages/home/index')
  },

  onShareAppMessage(res) {
    return buildProductShareMessage(res)
  }
})
