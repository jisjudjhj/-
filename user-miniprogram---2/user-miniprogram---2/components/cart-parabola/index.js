const { getSystemInfoCompat } = require('../../utils/system-info')

Component({
  properties: {},
  data: {
    show: false,
    image: '',
    startX: 0,
    startY: 0,
    translateX: 0,
    translateY: 0,
    duration: 600
  },
  lifetimes: {
    detached() {
      this.clearTimers()
    }
  },
  methods: {
    clearTimers() {
      if (this._startTimer) {
        clearTimeout(this._startTimer)
        this._startTimer = null
      }
      if (this._hideTimer) {
        clearTimeout(this._hideTimer)
        this._hideTimer = null
      }
    },
    animate(options) {
      const { startX, startY, image, duration = 600 } = options;
      const sysInfo = getSystemInfoCompat();
      this.clearTimers()
      // 目标位置，假设在底部 TabBar 的购物车图标位置 (大概是屏幕宽度的 60%，底部 20px)
      const targetX = sysInfo.windowWidth * 0.62;
      const targetY = sysInfo.windowHeight - 20;

      this.setData({
        show: true,
        image: image || '/assets/images/default-avatar.png',
        startX,
        startY,
        translateX: 0,
        translateY: 0,
        duration: 0 // 先瞬间移动到起点
      }, () => {
        // 下一帧开始动画
        this._startTimer = setTimeout(() => {
          this._startTimer = null
          this.setData({
            duration,
            translateX: targetX - startX,
            translateY: targetY - startY
          });

          // 动画结束后隐藏
          this._hideTimer = setTimeout(() => {
            this._hideTimer = null
            this.setData({ show: false });
          }, duration);
        }, 50);
      });
    }
  }
})
