Component({
  properties: {
    value: {
      type: Number,
      value: 0,
      observer: 'onValueChange'
    },
    duration: {
      type: Number,
      value: 600
    },
    decimals: {
      type: Number,
      value: 0
    }
  },
  data: {
    displayValue: '0'
  },
  lifetimes: {
    attached() {
      this._detached = false;
      this.currentValue = this.data.value || 0;
      this.setData({ displayValue: this.formatNumber(this.currentValue) });
    },
    detached() {
      this._detached = true;
      if (this.timer) {
        clearTimeout(this.timer);
        this.timer = null;
      }
    }
  },
  methods: {
    formatNumber(num) {
      return num.toLocaleString('zh-CN', {
        minimumFractionDigits: this.data.decimals,
        maximumFractionDigits: this.data.decimals
      });
    },
    onValueChange(newVal, oldVal) {
      if (newVal === oldVal) return;
      
      const start = this.currentValue || 0;
      const end = newVal;
      const startTime = Date.now();
      const duration = this.data.duration;
      
      const animate = () => {
        if (this._detached) {
          return;
        }
        const now = Date.now();
        const progress = Math.min((now - startTime) / duration, 1);
        
        // easeOutQuart
        const easeProgress = 1 - Math.pow(1 - progress, 4);
        
        this.currentValue = start + (end - start) * easeProgress;
        
        this.setData({
          displayValue: this.formatNumber(this.currentValue)
        });
        
        if (progress < 1) {
          this.timer = setTimeout(animate, 16); // ~60fps
        } else {
          this.currentValue = end;
          if (!this._detached) {
            this.setData({ displayValue: this.formatNumber(end) });
          }
        }
      };
      
      if (this.timer) clearTimeout(this.timer);
      animate();
    }
  }
})
