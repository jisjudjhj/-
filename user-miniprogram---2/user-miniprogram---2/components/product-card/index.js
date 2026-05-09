const { resolveProductImage } = require('../../utils/image')

Component({
  data: {
    imageLoadFailed: false,
    imageRetryAttempted: false,
    displayImage: ''
  },
  properties: {
    product: {
      type: Object,
      value: {}
    },
    skeleton: {
      type: Boolean,
      value: false
    },
    showReason: {
      type: Boolean,
      value: false
    },
    showSales: {
      type: Boolean,
      value: false
    },
    showAddCart: {
      type: Boolean,
      value: false
    },
    actionMode: {
      type: String,
      value: 'cart'
    },
    showDislike: {
      type: Boolean,
      value: false
    },
    enableLongPressMenu: {
      type: Boolean,
      value: false
    },
    longPressDuration: {
      type: Number,
      value: 1500
    },
    className: {
      type: String,
      value: ''
    }
  },
  observers: {
    'product': function (product) {
      this.syncImageState(product)
    }
  },
  lifetimes: {
    attached() {
      this.syncImageState(this.data.product)
    },
    detached() {
      this.clearLongPressTimer()
    }
  },
  methods: {
    getProductId() {
      const product = this.data.product || {}
      return product.id || product.productId || null
    },
    syncImageState(product) {
      this.setData({
        imageLoadFailed: false,
        imageRetryAttempted: false,
        displayImage: resolveProductImage(product || {})
      })
    },
    clearLongPressTimer() {
      if (this._longPressTimer) {
        clearTimeout(this._longPressTimer)
        this._longPressTimer = null
      }
    },
    onImageError() {
      const currentImage = this.data.displayImage || resolveProductImage(this.data.product || {})
      if (!this.data.imageRetryAttempted && currentImage && currentImage.indexOf('?') !== -1) {
        const retryImage = currentImage.split('?')[0]
        if (retryImage && retryImage !== currentImage) {
          this.setData({
            imageRetryAttempted: true,
            imageLoadFailed: false,
            displayImage: retryImage
          })
          return
        }
      }
      if (!this.data.imageLoadFailed) {
        this.setData({ imageLoadFailed: true })
      }
      console.warn('[product-card] image load failed', {
        productId: this.data.product && this.data.product.id,
        image: currentImage
      })
    },
    onTouchStart(e) {
      if (this.data.skeleton || !this.data.enableLongPressMenu) return
      const touch = (e.touches && e.touches[0]) || {}
      this._longPressTriggered = false
      this._touchStart = {
        x: touch.clientX || touch.x || 0,
        y: touch.clientY || touch.y || 0
      }
      this.clearLongPressTimer()
      const duration = Math.max(800, Number(this.data.longPressDuration || 1500))
      this._longPressTimer = setTimeout(() => {
        this._longPressTimer = null
        this._longPressTriggered = true
        wx.vibrateShort && wx.vibrateShort({ type: 'light' })
        this.triggerEvent('longpressmenu', {
          id: this.getProductId(),
          recToken: this.data.product.recommendationToken,
          product: this.data.product
        })
      }, duration)
    },
    onTouchMove(e) {
      if (!this._touchStart || !this._longPressTimer) return
      const touch = (e.touches && e.touches[0]) || {}
      const dx = Math.abs((touch.clientX || touch.x || 0) - this._touchStart.x)
      const dy = Math.abs((touch.clientY || touch.y || 0) - this._touchStart.y)
      if (dx > 12 || dy > 12) {
        this.clearLongPressTimer()
      }
    },
    onTouchEnd() {
      this.clearLongPressTimer()
      this._touchStart = null
    },
    onTouchCancel() {
      this.clearLongPressTimer()
      this._touchStart = null
    },
    noop() {},
    onTap(e) {
      if (this.data.skeleton) return;
      if (this._longPressTriggered) {
        this._longPressTriggered = false
        return
      }
      this.triggerEvent('click', { id: this.getProductId(), recToken: this.data.product.recommendationToken });
    },
    onAddCart(e) {
      if (this.data.skeleton) return;
      const touch = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || e.detail || {}
      this.triggerEvent('addcart', { 
        id: this.getProductId(),
        recToken: this.data.product.recommendationToken,
        actionMode: this.data.actionMode || 'cart',
        clientX: touch.x || touch.clientX || 200,
        clientY: touch.y || touch.clientY || 500
      });
    },
    onDislike() {
      if (this.data.skeleton) return;
      this.triggerEvent('dislike', {
        id: this.getProductId(),
        recToken: this.data.product.recommendationToken
      });
    },
    onExplain() {
      if (this.data.skeleton) return
      this.triggerEvent('explain', {
        id: this.getProductId(),
        recToken: this.data.product.recommendationToken,
        product: this.data.product
      })
    }
  }
})
