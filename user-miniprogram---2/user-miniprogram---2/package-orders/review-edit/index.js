const app = getApp()
const request = require('../../utils/request')
const { getApiBaseUrl } = require('../../config/env')

const MAX_IMAGE_COUNT = 6
const REVIEW_TAGS = ['与描述一致', '做工不错', '性价比高', '包装完整', '发货很快', '值得回购']

Page({
  data: {
    orderId: null,
    productId: null,
    productName: '',
    productImage: '',
    productPrice: '',
    loading: true,
    checking: false,
    checkFailed: false,
    canReview: true,
    rating: 5,
    content: '',
    selectedTags: [],
    images: [],
    submitting: false,
    uploading: false,
    tagOptions: REVIEW_TAGS
  },

  onLoad(options) {
    const orderId = Number(options.orderId)
    const productId = Number(options.productId)
    const redirect = `/pages/review-edit/index?orderId=${options.orderId || ''}&productId=${options.productId || ''}`

    if (!Number.isFinite(orderId) || orderId <= 0 || !Number.isFinite(productId) || productId <= 0) {
      wx.showToast({ title: '信息不完整', icon: 'none' })
      setTimeout(() => wx.navigateBack({ delta: 1 }), 500)
      return
    }

    if (!app.requireLogin(redirect)) {
      return
    }

    this.setData({
      orderId,
      productId,
      productName: options.productName ? decodeURIComponent(options.productName) : '',
      productImage: options.productImage ? decodeURIComponent(options.productImage) : '',
      productPrice: options.productPrice ? decodeURIComponent(options.productPrice) : ''
    })

    this.bootstrapPage()
  },

  async bootstrapPage() {
    this.setData({ loading: true, checking: true, checkFailed: false })
    try {
      const [canReview] = await Promise.all([
        request.get('/reviews/check', {
          orderId: this.data.orderId,
          productId: this.data.productId
        }, { showLoading: false }),
        this.ensureProductSnapshot()
      ])

      this.setData({
        canReview: !!canReview,
        loading: false,
        checking: false,
        checkFailed: false
      })

      if (!canReview) {
        wx.showToast({ title: '已评价', icon: 'none' })
      }
    } catch (error) {
      console.error('[review-edit] bootstrap failed', error)
      this.setData({
        loading: false,
        checking: false,
        checkFailed: true,
        canReview: true
      })
      wx.showToast({ title: '校验失败，提交时再重试', icon: 'none' })
    }
  },

  async recheckReviewable() {
    try {
      const canReview = await request.get('/reviews/check', {
        orderId: this.data.orderId,
        productId: this.data.productId
      }, { showLoading: false })

      this.setData({
        canReview: !!canReview,
        checkFailed: false
      })

      if (!canReview) {
        wx.showToast({ title: '已评价', icon: 'none' })
        return false
      }

      return true
    } catch (error) {
      console.error('[review-edit] recheck failed', error)
      this.setData({ checkFailed: true })
      wx.showToast({ title: '校验失败', icon: 'none' })
      return false
    }
  },

  async ensureProductSnapshot() {
    if (this.data.productName && this.data.productImage) {
      return
    }
    try {
      const res = await request.get(`/products/detail/${this.data.productId}`, {}, { showLoading: false })
      this.setData({
        productName: this.data.productName || res.name || '',
        productImage: this.data.productImage || res.image || (Array.isArray(res.images) && res.images.length ? res.images[0] : ''),
        productPrice: this.data.productPrice || this.formatPrice(res.price)
      })
    } catch (error) {
      console.warn('[review-edit] product snapshot failed', error)
    }
  },

  formatPrice(value) {
    const numeric = Number(value || 0)
    if (!Number.isFinite(numeric)) return `${value || ''}`
    return Number.isInteger(numeric) ? `${numeric}` : numeric.toFixed(2).replace(/\.?0+$/, '')
  },

  selectRating(e) {
    const rating = Number(e.currentTarget.dataset.value)
    if (!Number.isFinite(rating) || rating < 1 || rating > 5) {
      return
    }
    this.setData({ rating })
  },

  onContentInput(e) {
    const value = `${e.detail.value || ''}`.slice(0, 300)
    this.setData({ content: value })
  },

  toggleTag(e) {
    const tag = `${e.currentTarget.dataset.tag || ''}`.trim()
    if (!tag) return

    const selected = Array.isArray(this.data.selectedTags) ? [...this.data.selectedTags] : []
    const index = selected.indexOf(tag)
    if (index >= 0) {
      selected.splice(index, 1)
    } else if (selected.length < 3) {
      selected.push(tag)
    } else {
      wx.showToast({ title: '最多选择 3 个标签', icon: 'none' })
      return
    }

    this.setData({ selectedTags: selected })
  },

  chooseImages() {
    if (this.data.uploading) {
      return
    }

    const remain = MAX_IMAGE_COUNT - this.data.images.length
    if (remain <= 0) {
      wx.showToast({ title: '最多上传 6 张图片', icon: 'none' })
      return
    }

    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: async (res) => {
        const tempFiles = Array.isArray(res.tempFiles) ? res.tempFiles : []
        if (!tempFiles.length) {
          return
        }

        this.setData({ uploading: true })
        wx.showLoading({ title: '上传中...', mask: true })
        try {
          const uploaded = []
          for (let i = 0; i < tempFiles.length; i += 1) {
            const tempPath = tempFiles[i].tempFilePath
            const url = await this.uploadSingleImage(tempPath)
            if (url) {
              uploaded.push(url)
            }
          }
          const nextImages = this.data.images.concat(uploaded).slice(0, MAX_IMAGE_COUNT)
          this.setData({ images: nextImages, uploading: false })
        } catch (error) {
          console.error('[review-edit] upload images failed', error)
          this.setData({ uploading: false })
          wx.showToast({ title: '图片上传失败', icon: 'none' })
        } finally {
          wx.hideLoading()
        }
      }
    })
  },

  uploadSingleImage(filePath) {
    const token = wx.getStorageSync('token')

    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url: `${getApiBaseUrl()}/upload/image`,
        filePath,
        name: 'file',
        header: token ? { Authorization: `Bearer ${token}` } : {},
        success: (res) => {
          try {
            const data = JSON.parse(res.data)
            if ((data.code === 200 || data.code === 0) && data.data && data.data.url) {
              resolve(data.data.url)
              return
            }
            reject(new Error(data.message || '上传失败'))
          } catch (error) {
            reject(error)
          }
        },
        fail: reject
      })
    })
  },

  removeImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (!Number.isInteger(index) || index < 0) {
      return
    }
    const nextImages = [...this.data.images]
    nextImages.splice(index, 1)
    this.setData({ images: nextImages })
  },

  previewImage(e) {
    const index = Number(e.currentTarget.dataset.index || 0)
    const urls = this.data.images || []
    if (!urls.length) return
    wx.previewImage({
      current: urls[index] || urls[0],
      urls
    })
  },

  async submitReview() {
    if (this.data.submitting || this.data.loading || this.data.uploading) {
      return
    }

    const content = `${this.data.content || ''}`.trim()
    if (!content && !this.data.images.length) {
      wx.showToast({ title: '写点使用感受吧', icon: 'none' })
      return
    }

    if (!this.data.canReview) {
      wx.showToast({ title: '已评价', icon: 'none' })
      return
    }

    if (this.data.checkFailed) {
      const canSubmit = await this.recheckReviewable()
      if (!canSubmit) {
        return
      }
    }

    this.setData({ submitting: true })
    try {
      await request.post('/reviews', {
        orderId: this.data.orderId,
        productId: this.data.productId,
        rating: this.data.rating,
        content,
        images: this.data.images,
        tags: this.data.selectedTags
      })

      wx.showToast({ title: '评价成功', icon: 'success' })
      setTimeout(() => {
        wx.navigateBack({ delta: 1 })
      }, 600)
    } catch (error) {
      console.error('[review-edit] submit failed', error)
    } finally {
      this.setData({ submitting: false })
    }
  }
})
