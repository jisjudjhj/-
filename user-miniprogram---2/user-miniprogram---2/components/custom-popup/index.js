Component({
  properties: {
    show: {
      type: Boolean,
      value: false
    },
    position: {
      type: String,
      value: 'bottom' // bottom, top, center
    },
    round: {
      type: Boolean,
      value: true
    },
    closeable: {
      type: Boolean,
      value: false
    },
    closeOnClickOverlay: {
      type: Boolean,
      value: true
    },
    zIndex: {
      type: Number,
      value: 1000
    },
    customStyle: {
      type: String,
      value: ''
    }
  },
  methods: {
    onClickMask() {
      if (this.data.closeOnClickOverlay) {
        this.onClose()
      }
    },
    onClose() {
      this.triggerEvent('close')
    }
  }
})
