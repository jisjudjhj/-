Component({
  properties: {
    type: {
      type: String,
      value: 'primary' // primary, secondary, accent, text
    },
    size: {
      type: String,
      value: 'normal' // large, normal, small
    },
    round: {
      type: Boolean,
      value: false
    },
    block: {
      type: Boolean,
      value: false
    },
    disabled: {
      type: Boolean,
      value: false
    },
    customStyle: {
      type: String,
      value: ''
    }
  },
  methods: {
    onClick(e) {
      if (!this.data.disabled) {
        this.triggerEvent('click', e)
      }
    }
  }
})
