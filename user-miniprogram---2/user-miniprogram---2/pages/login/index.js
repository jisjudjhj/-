const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    redirect: '/pages/home/index',
    isLogin: true,

    // 登录
    username: '',
    password: '',
    showLoginPassword: false,

    // 注册
    regPhone: '',
    regPassword: '',
    regPasswordConfirm: '',
    showRegPassword: false,
    showRegPasswordConfirm: false,
    regSmsCode: '',
    smsCountdown: 0,

    // 图形验证码（登录/注册共用）
    captchaKey: '',
    captchaImage: '',
    captchaCode: '',

    submitting: false
  },

  getErrorMessage(err, fallback = '操作失败') {
    if (!err) return fallback
    if (typeof err === 'string') return err
    if (err.message) return err.message
    if (err.msg) return err.msg
    return fallback
  },

  onLoad(options) {
    this._destroyed = false
    if (options.redirect) {
      this.setData({ redirect: decodeURIComponent(options.redirect) })
    }
    this.refreshCaptcha()
  },

  onShow() {
    this._destroyed = false
  },

  onHide() {
    this._destroyed = true
    this.clearSmsTimer()
  },

  onUnload() {
    this._destroyed = true
    this.clearSmsTimer()
  },

  isPageInactive() {
    return !!this._destroyed
  },

  safeSetData(nextData, callback) {
    if (this.isPageInactive() || !nextData) {
      return false
    }
    try {
      this.setData(nextData, callback)
      return true
    } catch (error) {
      return false
    }
  },

  clearSmsTimer() {
    if (this._smsTimer) {
      clearInterval(this._smsTimer)
      this._smsTimer = null
    }
  },

  // ========== Tab 切换 ==========

  switchToLogin() {
    this.setData({ isLogin: true, captchaCode: '' })
    this.refreshCaptcha()
  },

  switchToRegister() {
    this.setData({ isLogin: false, captchaCode: '' })
    this.refreshCaptcha()
  },

  // ========== 图形验证码 ==========

  async refreshCaptcha() {
    try {
      const data = await request.get('/captcha', {}, { showLoading: false })
      if (this.isPageInactive()) return
      this.safeSetData({
        captchaKey: data.captchaKey,
        captchaImage: data.captchaImage
      })
    } catch (err) {
      console.error('获取验证码失败', err)
    }
  },

  // ========== 登录输入 ==========

  onUsernameInput(e) {
    this.setData({ username: e.detail.value.trim() })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  onCaptchaInput(e) {
    this.setData({ captchaCode: e.detail.value.trim() })
  },

  // ========== 注册输入 ==========

  onRegPhoneInput(e) {
    const value = `${e.detail.value || ''}`.replace(/\D/g, '').slice(0, 11)
    this.setData({ regPhone: value })
  },

  onRegPasswordInput(e) {
    this.setData({ regPassword: e.detail.value })
  },

  onRegPasswordConfirmInput(e) {
    this.setData({ regPasswordConfirm: e.detail.value })
  },

  onRegSmsCodeInput(e) {
    this.setData({ regSmsCode: e.detail.value.trim() })
  },

  togglePasswordVisibility(e) {
    const key = e.currentTarget.dataset.key
    if (!key) return
    this.setData({ [key]: !this.data[key] })
  },

  // ========== 发送短信验证码 ==========

  async sendSmsCode() {
    if (this.data.smsCountdown > 0) return

    const phone = this.data.regPhone
    if (!phone || !/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入11位手机号', icon: 'none' })
      return
    }

    try {
      await request.post('/auth/send-code', { type: 'phone', phone }, { showLoading: false })
      wx.showToast({ title: '验证码已发送', icon: 'success' })

      this.clearSmsTimer()
      this.safeSetData({ smsCountdown: 60 })
      this._smsTimer = setInterval(() => {
        if (this.isPageInactive()) {
          this.clearSmsTimer()
          return
        }
        const cd = this.data.smsCountdown - 1
        if (cd <= 0) {
          this.clearSmsTimer()
        }
        this.safeSetData({ smsCountdown: Math.max(cd, 0) })
      }, 1000)
    } catch (err) {
      console.error('发送验证码失败', err)
      wx.showToast({ title: this.getErrorMessage(err, '发送失败'), icon: 'none' })
    }
  },

  // ========== 登录提交 ==========

  async submitLogin() {
    const { username, password, captchaKey, captchaCode } = this.data
    if (!username || !password) {
      wx.showToast({ title: '请输入账号和密码', icon: 'none' })
      return
    }
    if (!captchaCode) {
      wx.showToast({ title: '请输入图形验证码', icon: 'none' })
      return
    }
    if (/^\d+$/.test(username) && username.length !== 11) {
      wx.showToast({ title: '手机号必须为11位', icon: 'none' })
      return
    }
    if (this.data.submitting) return

    this.setData({ submitting: true })
    try {
      const data = await request.post('/auth/login', {
        loginType: 'password',
        username,
        password,
        captchaKey,
        captchaCode
      }, { showLoading: false })

      app.setLoginState(data)
      wx.showToast({ title: '登录成功', icon: 'success' })
      this._navigateAfterAuth()
    } catch (error) {
      console.error('登录失败', error)
      wx.showToast({ title: this.getErrorMessage(error, '登录失败'), icon: 'none' })
      this.refreshCaptcha()
      this.safeSetData({ captchaCode: '' })
    } finally {
      this.safeSetData({ submitting: false })
    }
  },

  // ========== 注册提交 ==========

  async submitRegister() {
    const { regPhone, regPassword, regPasswordConfirm, regSmsCode, captchaKey, captchaCode } = this.data

    if (!regPhone || !/^1[3-9]\d{9}$/.test(regPhone)) {
      wx.showToast({ title: '请输入11位手机号', icon: 'none' })
      return
    }
    if (!regPassword || regPassword.length < 6) {
      wx.showToast({ title: '密码至少6位', icon: 'none' })
      return
    }
    if (regPassword !== regPasswordConfirm) {
      wx.showToast({ title: '两次输入的密码不一致', icon: 'none' })
      return
    }
    if (!captchaCode) {
      wx.showToast({ title: '请输入图形验证码', icon: 'none' })
      return
    }
    if (!regSmsCode) {
      wx.showToast({ title: '请输入短信验证码', icon: 'none' })
      return
    }
    if (this.data.submitting) return

    this.setData({ submitting: true })
    try {
      await request.post('/auth/register', {
        phone: regPhone,
        password: regPassword,
        code: regSmsCode,
        captchaKey,
        captchaCode
      }, { showLoading: false })

      wx.showToast({ title: '注册成功，请登录', icon: 'success' })
      this.safeSetData({
        isLogin: true,
        username: regPhone,
        password: '',
        captchaCode: ''
      })
      this.refreshCaptcha()
    } catch (error) {
      console.error('注册失败', error)
      wx.showToast({ title: this.getErrorMessage(error, '注册失败'), icon: 'none' })
      this.refreshCaptcha()
      this.safeSetData({ captchaCode: '' })
    } finally {
      this.safeSetData({ submitting: false })
    }
  },

  // ========== 导航辅助 ==========

  _navigateAfterAuth() {
    const redirect = this.data.redirect || '/pages/home/index'

    setTimeout(() => {
      if (this.isPageInactive()) {
        return
      }
      app.navigateToPage(redirect).then((success) => {
        if (!success && redirect !== '/pages/home/index') {
          app.navigateToPage('/pages/home/index')
        }
      })
    }, 300)
  }
})
