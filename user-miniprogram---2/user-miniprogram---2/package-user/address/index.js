const app = getApp()
const request = require('../../utils/request')

const MUNICIPALITIES = ['北京市', '上海市', '天津市', '重庆市']
const PROVINCE_LIST = [
  '北京市',
  '天津市',
  '上海市',
  '重庆市',
  '河北省',
  '山西省',
  '辽宁省',
  '吉林省',
  '黑龙江省',
  '江苏省',
  '浙江省',
  '安徽省',
  '福建省',
  '江西省',
  '山东省',
  '河南省',
  '湖北省',
  '湖南省',
  '广东省',
  '海南省',
  '四川省',
  '贵州省',
  '云南省',
  '陕西省',
  '甘肃省',
  '青海省',
  '台湾省',
  '内蒙古自治区',
  '广西壮族自治区',
  '西藏自治区',
  '宁夏回族自治区',
  '新疆维吾尔自治区',
  '香港特别行政区',
  '澳门特别行政区'
]
const PROVINCE_ALIAS_MAP = {
  北京: '北京市',
  天津: '天津市',
  上海: '上海市',
  重庆: '重庆市',
  河北: '河北省',
  山西: '山西省',
  辽宁: '辽宁省',
  吉林: '吉林省',
  黑龙江: '黑龙江省',
  江苏: '江苏省',
  浙江: '浙江省',
  安徽: '安徽省',
  福建: '福建省',
  江西: '江西省',
  山东: '山东省',
  河南: '河南省',
  湖北: '湖北省',
  湖南: '湖南省',
  广东: '广东省',
  海南: '海南省',
  四川: '四川省',
  贵州: '贵州省',
  云南: '云南省',
  陕西: '陕西省',
  甘肃: '甘肃省',
  青海: '青海省',
  台湾: '台湾省',
  内蒙古: '内蒙古自治区',
  广西: '广西壮族自治区',
  西藏: '西藏自治区',
  宁夏: '宁夏回族自治区',
  新疆: '新疆维吾尔自治区',
  香港: '香港特别行政区',
  澳门: '澳门特别行政区'
}
const LABEL_REGEX = /(收货地址|详细地址|收货人|收件人|联系人|姓名|手机号码|手机号|电话|所在地区|地区|邮编)\s*[:：]?/g

function createEmptyForm(isDefault = false) {
  return {
    receiverName: '',
    receiverPhone: '',
    province: '',
    city: '',
    district: '',
    detail: '',
    isDefault: isDefault ? 1 : 0
  }
}

function normalizeClipboardText(text) {
  return String(text || '')
    .replace(/[\r\n]+/g, ' ')
    .replace(/[，,；;]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function cleanClipboardText(text) {
  return normalizeClipboardText(text)
    .replace(LABEL_REGEX, ' ')
    .replace(/(?:^|\s)地址\s*[:：]?/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function escapeRegExp(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function isLikelyName(text) {
  const value = String(text || '').trim()
  return !!value &&
    /^[A-Za-z\u4e00-\u9fa5·]{2,20}$/.test(value) &&
    !/[省市区县旗镇乡路街道号栋室单元]/.test(value)
}

function extractRegionFromCompactText(text) {
  const source = String(text || '').trim()
  if (!source) {
    return {
      province: '',
      city: '',
      district: '',
      detail: '',
      prefix: ''
    }
  }

  let province = PROVINCE_LIST.find((item) => source.includes(item))
  let matchedKeyword = province

  if (!province) {
    matchedKeyword = Object.keys(PROVINCE_ALIAS_MAP).find((item) => source.includes(item))
    province = matchedKeyword ? PROVINCE_ALIAS_MAP[matchedKeyword] : ''
  }

  if (!province || !matchedKeyword) {
    return {
      province: '',
      city: '',
      district: '',
      detail: '',
      prefix: ''
    }
  }

  const provinceIndex = source.indexOf(matchedKeyword)
  let rest = source.slice(provinceIndex + matchedKeyword.length)
  let city = ''

  if (MUNICIPALITIES.includes(province)) {
    city = province
    if (rest.startsWith(province)) {
      rest = rest.slice(province.length)
    }
  } else {
    const cityMatch = rest.match(/^(.{1,12}?(?:市|自治州|地区|盟))/)
    if (cityMatch) {
      city = cityMatch[1]
      rest = rest.slice(city.length)
    }
  }

  let district = ''
  const districtMatch = rest.match(/^(.{1,12}?(?:区|县|旗|市))/)
  if (districtMatch) {
    district = districtMatch[1]
    rest = rest.slice(district.length)
  }

  return {
    province,
    city,
    district,
    detail: rest,
    prefix: source.slice(0, provinceIndex)
  }
}

function parseClipboardAddress(text) {
  const normalized = normalizeClipboardText(text)
  const cleaned = cleanClipboardText(text)
  const phoneMatch = normalized.match(/1[3-9]\d{9}/)
  const phone = phoneMatch ? phoneMatch[0] : ''
  const compact = cleaned.replace(/\s+/g, '')
  const compactWithoutPhone = phone ? compact.replace(phone, '') : compact
  const regionInfo = extractRegionFromCompactText(compactWithoutPhone)
  const labeledNameMatch = normalized.match(/(?:收货人|收件人|联系人|姓名)\s*[:：]?\s*([A-Za-z\u4e00-\u9fa5·]{2,20})/)

  let name = labeledNameMatch ? labeledNameMatch[1] : ''
  if (!name && isLikelyName(regionInfo.prefix)) {
    name = regionInfo.prefix
  }

  let residual = cleaned
  if (phone) {
    residual = residual.replace(phone, ' ')
  }

  ;[regionInfo.province, regionInfo.city, regionInfo.district]
    .filter(Boolean)
    .forEach((part) => {
      residual = residual.replace(new RegExp(escapeRegExp(part), 'g'), ' ')
    })

  if (name) {
    residual = residual.replace(new RegExp(escapeRegExp(name), 'g'), ' ')
  }

  residual = residual.replace(/\s+/g, ' ').trim()

  if (!name) {
    const tokens = residual.split(' ').filter(Boolean)
    const nameIndex = tokens.findIndex((item) => isLikelyName(item))

    if (nameIndex !== -1) {
      name = tokens[nameIndex]
      tokens.splice(nameIndex, 1)
      residual = tokens.join(' ')
    }
  }

  return {
    receiverName: name,
    receiverPhone: phone,
    province: regionInfo.province,
    city: regionInfo.city,
    district: regionInfo.district,
    detail: (regionInfo.detail || residual).replace(/\s+/g, ''),
    regionComplete: !!(regionInfo.province && regionInfo.city && regionInfo.district)
  }
}

Page({
  data: {
    addressList: [],
    loading: true,
    refreshing: false,
    selectMode: false,
    hasDefault: false,
    editingId: null,
    showForm: false,
    submitting: false,
    regionValue: [],
    parseTip: '',
    form: createEmptyForm(false)
  },

  onLoad(options) {
    const redirect = options.mode === 'select'
      ? '/pages/address/index?mode=select'
      : '/pages/address/index'

    if (!app.requireLogin(redirect)) {
      return
    }

    if (options.mode === 'select') {
      this.setData({ selectMode: true })
    }
  },

  onShow() {
    this.loadAddresses()
  },

  async onRefresh() {
    await this.loadAddresses()
    this.setData({ refreshing: false })
  },

  async loadAddresses() {
    try {
      const res = await request.get('/user/addresses', {}, { showLoading: false })
      const addressList = Array.isArray(res) ? res : []
      this.setData({
        addressList,
        hasDefault: addressList.some((item) => Number(item.isDefault) === 1),
        loading: false
      })
    } catch (e) {
      console.error('加载地址失败', e)
      this.setData({ addressList: [], hasDefault: false, loading: false })
    }
  },

  handleAddressTap(e) {
    const item = e.currentTarget.dataset.item
    if (!item || !this.data.selectMode) {
      return
    }
    this.selectAddress(item)
  },

  selectAddress(address) {
    const item = address && address.currentTarget ? address.currentTarget.dataset.item : address
    if (!item) {
      return
    }

    app.globalData._selectedAddress = item
    wx.showToast({ title: '已选择该地址', icon: 'success', duration: 800 })
    setTimeout(() => {
      wx.navigateBack({ fail: () => {} })
    }, 400)
  },

  openAddForm() {
    const isFirstAddress = this.data.addressList.length === 0
    this.setData({
      showForm: true,
      editingId: null,
      regionValue: [],
      parseTip: '',
      form: createEmptyForm(isFirstAddress)
    })
  },

  openAddFormAndPaste() {
    this.openAddForm()
    setTimeout(() => {
      this.pasteFromClipboard()
    }, 60)
  },

  openAddFormAndLocate() {
    this.openAddForm()
    setTimeout(() => {
      this.chooseLocationForAddress()
    }, 60)
  },

  openEditForm(e) {
    const item = e.currentTarget.dataset.item
    if (!item) {
      return
    }

    this.setData({
      showForm: true,
      editingId: item.id,
      regionValue: [item.province || '', item.city || '', item.district || ''],
      parseTip: '',
      form: {
        receiverName: item.receiverName || '',
        receiverPhone: item.receiverPhone || '',
        province: item.province || '',
        city: item.city || '',
        district: item.district || '',
        detail: item.detail || '',
        isDefault: Number(item.isDefault) || 0
      }
    })
  },

  closeForm() {
    if (this.data.submitting) {
      return
    }
    this.setData({ showForm: false, parseTip: '' })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  onRegionChange(e) {
    const value = e.detail.value || []
    this.setData({
      regionValue: value,
      'form.province': value[0] || '',
      'form.city': value[1] || '',
      'form.district': value[2] || ''
    })
  },

  async chooseLocationForAddress() {
    if (!this.data.showForm) {
      this.openAddForm()
    }

    try {
      const location = await new Promise((resolve, reject) => {
        wx.chooseLocation({
          success: resolve,
          fail: reject
        })
      })

      const parsed = parseClipboardAddress(`${location.address || ''}${location.name || ''}`)
      if (!parsed.regionComplete) {
        wx.showToast({ title: '未识别到省市区，请手动选择', icon: 'none' })
        return
      }

      this.setData({
        'form.province': parsed.province,
        'form.city': parsed.city,
        'form.district': parsed.district,
        regionValue: [parsed.province, parsed.city, parsed.district],
        parseTip: '已通过定位带入省市区，请补充详细门牌后保存'
      })
      wx.showToast({ title: '定位已带入', icon: 'success' })
    } catch (e) {
      const errMsg = String((e && (e.errMsg || e.message)) || '')
      if (errMsg.includes('cancel')) {
        return
      }
      console.error('选择位置失败', e)
      wx.showToast({ title: '无法打开定位选择', icon: 'none' })
    }
  },

  toggleDefault() {
    this.setData({ 'form.isDefault': this.data.form.isDefault ? 0 : 1 })
  },

  buildParseTip(parsed) {
    const fields = []

    if (parsed.receiverName) {
      fields.push('姓名')
    }
    if (parsed.receiverPhone) {
      fields.push('手机号')
    }
    if (parsed.regionComplete) {
      fields.push('省市区')
    }
    if (parsed.detail) {
      fields.push('详细地址')
    }

    if (!fields.length) {
      return ''
    }

    return parsed.regionComplete
      ? `已识别${fields.join('、')}，检查无误后可直接保存`
      : `已识别${fields.join('、')}，还需要手动补全省市区`
  },

  async pasteFromClipboard() {
    if (!this.data.showForm) {
      this.openAddForm()
    }

    try {
      const res = await new Promise((resolve, reject) => {
        wx.getClipboardData({
          success: resolve,
          fail: reject
        })
      })

      const clipboardText = String((res && res.data) || '').trim()
      if (!clipboardText) {
        wx.showToast({ title: '剪贴板里还没有内容', icon: 'none' })
        return
      }

      const parsed = parseClipboardAddress(clipboardText)
      const hasRecognizedValue = parsed.receiverName || parsed.receiverPhone || parsed.detail || parsed.regionComplete

      if (!hasRecognizedValue) {
        wx.showToast({ title: '未识别到可用地址信息', icon: 'none' })
        return
      }

      const nextForm = {
        ...this.data.form,
        receiverName: parsed.receiverName || this.data.form.receiverName,
        receiverPhone: parsed.receiverPhone || this.data.form.receiverPhone,
        province: parsed.regionComplete ? parsed.province : this.data.form.province,
        city: parsed.regionComplete ? parsed.city : this.data.form.city,
        district: parsed.regionComplete ? parsed.district : this.data.form.district,
        detail: parsed.detail || this.data.form.detail
      }

      this.setData({
        form: nextForm,
        regionValue: parsed.regionComplete
          ? [parsed.province, parsed.city, parsed.district]
          : this.data.regionValue,
        parseTip: this.buildParseTip(parsed)
      })

      wx.showToast({ title: '识别成功', icon: 'success' })
    } catch (e) {
      console.error('读取剪贴板失败', e)
      wx.showToast({ title: '读取剪贴板失败', icon: 'none' })
    }
  },

  validateForm() {
    const form = {
      receiverName: (this.data.form.receiverName || '').trim(),
      receiverPhone: (this.data.form.receiverPhone || '').trim(),
      province: (this.data.form.province || '').trim(),
      city: (this.data.form.city || '').trim(),
      district: (this.data.form.district || '').trim(),
      detail: (this.data.form.detail || '').trim(),
      isDefault: this.data.form.isDefault ? 1 : 0
    }

    if (!form.receiverName) {
      wx.showToast({ title: '请填写收货人', icon: 'none' })
      return null
    }
    if (!/^1[3-9]\d{9}$/.test(form.receiverPhone)) {
      wx.showToast({ title: '请输入正确手机号', icon: 'none' })
      return null
    }
    if (!form.province || !form.city || !form.district) {
      wx.showToast({ title: '请选择省市区', icon: 'none' })
      return null
    }
    if (!form.detail) {
      wx.showToast({ title: '请填写详细地址', icon: 'none' })
      return null
    }

    return form
  },

  async submitForm() {
    const payload = this.validateForm()
    if (!payload) {
      return
    }

    this.setData({ submitting: true })

    try {
      if (this.data.editingId) {
        await request.put(`/user/addresses/${this.data.editingId}`, payload)
        wx.showToast({ title: '修改成功', icon: 'success' })
      } else {
        await request.post('/user/addresses', payload)
        wx.showToast({ title: '添加成功', icon: 'success' })
      }

      this.setData({ showForm: false, parseTip: '' })
      await this.loadAddresses()
    } catch (e) {
      console.error('保存地址失败', e)
    } finally {
      this.setData({ submitting: false })
    }
  },

  async setDefault(e) {
    const id = e.currentTarget.dataset.id
    try {
      await request.put(`/user/addresses/${id}/default`)
      wx.showToast({ title: '已设为默认地址', icon: 'success' })
      await this.loadAddresses()
    } catch (e) {
      console.error('设置默认地址失败', e)
    }
  },

  deleteAddress(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这条收货地址吗？',
      success: async (res) => {
        if (!res.confirm) {
          return
        }

        try {
          await request.delete(`/user/addresses/${id}`)
          wx.showToast({ title: '已删除', icon: 'success' })
          await this.loadAddresses()
        } catch (e) {
          console.error('删除地址失败', e)
        }
      }
    })
  }
})
