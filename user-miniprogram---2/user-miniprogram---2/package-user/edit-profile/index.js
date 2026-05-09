const app = getApp()
const request = require('../../utils/request')
const { getApiBaseUrl } = require('../../config/env')

Page({
  data: {
    currentAvatar: '',
    currentNickname: '',
    newAvatar: '',
    newNickname: '',
    canModify: true,
    cooldownReason: '',
    nextChangeDate: '',
    pendingRequest: null,
    hasChanges: false,
    submitting: false
  },

  onLoad() {
    if (!app.requireLogin('/pages/edit-profile/index')) return
    this.loadData()
  },

  async loadData() {
    try {
      const [userRes, statusRes] = await Promise.all([
        request.get('/auth/me', {}, { showLoading: false }),
        request.get('/auth/profile/change-status', {}, { showLoading: false })
      ])

      const currentAvatar = userRes ? userRes.avatar || '' : ''
      const currentNickname = userRes ? userRes.nickname || '' : ''

      let canModify = true
      let cooldownReason = ''
      let nextChangeDate = ''
      let pendingRequest = null

      if (statusRes) {
        canModify = statusRes.canModify
        cooldownReason = statusRes.reason || ''
        pendingRequest = statusRes.latestRequest

        if (statusRes.lastChange && cooldownReason.includes('7天')) {
          const d = new Date(statusRes.lastChange.replace(/-/g, '/'))
          d.setDate(d.getDate() + 7)
          nextChangeDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
        }
      }

      this.setData({
        currentAvatar,
        currentNickname,
        newNickname: currentNickname,
        canModify,
        cooldownReason,
        nextChangeDate,
        pendingRequest
      })
    } catch (err) {
      console.error('加载资料信息失败', err)
    }
  },

  onNicknameInput(e) {
    const val = e.detail.value
    this.setData({
      newNickname: val,
      hasChanges: val !== this.data.currentNickname || !!this.data.newAvatar
    })
  },

  chooseAvatar() {
    if (!this.data.canModify) {
      wx.showToast({ title: this.data.cooldownReason || '暂不可改', icon: 'none' })
      return
    }

    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        const tempPath = res.tempFiles[0].tempFilePath
        this.uploadAvatar(tempPath)
      }
    })
  },

  uploadAvatar(filePath) {
    wx.showLoading({ title: '上传中...', mask: true })
    const token = wx.getStorageSync('token')

    wx.uploadFile({
      url: `${getApiBaseUrl()}/upload/avatar`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${token}` },
      success: (res) => {
        wx.hideLoading()
        try {
          const data = JSON.parse(res.data)
          if (data.code === 200 && data.data && data.data.url) {
            this.setData({
              newAvatar: data.data.url,
              hasChanges: true
            })
            wx.showToast({ title: '头像已选择', icon: 'success' })
          } else {
            wx.showToast({ title: data.message || '上传失败', icon: 'none' })
          }
        } catch (e) {
          wx.showToast({ title: '上传失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.hideLoading()
        wx.showToast({ title: '网络异常', icon: 'none' })
      }
    })
  },

  async submitChange() {
    if (this.data.submitting || !this.data.hasChanges) return
    this.setData({ submitting: true })

    const params = {}
    if (this.data.newNickname && this.data.newNickname !== this.data.currentNickname) {
      if (this.data.newNickname.length < 2) {
        wx.showToast({ title: '昵称至少2个字符', icon: 'none' })
        this.setData({ submitting: false })
        return
      }
      params.nickname = this.data.newNickname
    }
    if (this.data.newAvatar) {
      params.avatar = this.data.newAvatar
    }

    if (!params.nickname && !params.avatar) {
      wx.showToast({ title: '未做任何修改', icon: 'none' })
      this.setData({ submitting: false })
      return
    }

    try {
      await request.put('/auth/profile', params)
      wx.showToast({ title: '已提交审核', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 1200)
    } catch (err) {
      console.error('提交修改失败', err)
    } finally {
      this.setData({ submitting: false })
    }
  }
})
