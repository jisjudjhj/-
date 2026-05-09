const app = getApp()
const request = require('../../utils/request')

const TYPE_TABS = [
  { label: '\u5168\u90e8', value: '' },
  { label: '\u5145\u503c', value: 'recharge' },
  { label: '\u652f\u4ed8', value: 'pay' },
  { label: '\u9000\u6b3e', value: 'refund' }
]

Page({
  data: {
    typeTabs: TYPE_TABS,
    activeType: '',
    balance: '0.00',
    transactions: [],
    page: 1,
    hasMore: true,
    loading: true,
    refreshing: false,
    showRecharge: false,
    rechargeAmount: 100,
    customAmount: '',
    amountOptions: [50, 100, 200, 500, 1000, 5000]
  },

  onLoad() {
    if (!app.requireLogin('/pages/wallet/index')) return
    this.loadBalance()
    this.loadTransactions(true)
  },

  async onRefresh() {
    await Promise.all([
      this.loadBalance(),
      this.loadTransactions(true)
    ])
    this.setData({ refreshing: false })
  },

  async loadBalance() {
    try {
      const res = await request.get('/wallet/balance', {}, { showLoading: false })
      this.setData({ balance: res && res.balance != null ? res.balance : '0.00' })
    } catch (e) {
      console.error('获取余额失败', e)
    }
  },

  async loadTransactions(refresh) {
    const nextPage = refresh ? 1 : this.data.page
    try {
      const res = await request.get('/wallet/transactions', {
        page: nextPage,
        size: 15,
        type: this.data.activeType || undefined
      }, { showLoading: false })

      const list = ((res && res.records) || []).map((item) => this.formatTransaction(item))
      this.setData({
        transactions: refresh ? list : [...this.data.transactions, ...list],
        page: nextPage + 1,
        hasMore: list.length === 15,
        loading: false
      })
    } catch (e) {
      console.error('获取交易记录失败', e)
      this.setData({ loading: false })
    }
  },

  loadMore() {
    if (this.data.hasMore) {
      this.loadTransactions(false)
    }
  },

  switchType(e) {
    const { type } = e.currentTarget.dataset
    if (type === this.data.activeType) {
      return
    }

    this.setData({
      activeType: type || '',
      loading: true
    })
    this.loadTransactions(true)
  },

  openRechargePopup() {
    this.setData({ showRecharge: true, rechargeAmount: 100, customAmount: '' })
  },

  closeRechargePopup() {
    this.setData({ showRecharge: false })
  },

  selectAmount(e) {
    this.setData({ rechargeAmount: e.currentTarget.dataset.amount, customAmount: '' })
  },

  onCustomInput(e) {
    const val = e.detail.value
    this.setData({
      customAmount: val,
      rechargeAmount: val ? parseFloat(val) || 0 : 0
    })
  },

  async confirmRecharge() {
    const amount = this.data.rechargeAmount
    if (!amount || amount <= 0) {
      wx.showToast({ title: '\u8bf7\u8f93\u5165\u6709\u6548\u91d1\u989d', icon: 'none' })
      return
    }
    if (amount > 50000) {
      wx.showToast({ title: '\u5355\u6b21\u5145\u503c\u4e0d\u8d85\u8fc750000', icon: 'none' })
      return
    }

    try {
      const res = await request.post('/wallet/recharge', { amount })
      wx.showToast({ title: '\u5145\u503c\u6210\u529f', icon: 'success' })
      this.setData({
        showRecharge: false,
        balance: res && res.balance != null ? res.balance : this.data.balance
      })
      this.loadTransactions(true)
    } catch (e) {
      console.error('充值失败', e)
    }
  },

  formatTransaction(item) {
    const type = item.type || ''
    const amount = Number(item.amount || 0)
    const amountText = item.amount == null ? '0.00' : String(item.amount)
    const isIncome = type === 'recharge' || type === 'refund' || amount > 0

    return {
      ...item,
      type,
      typeText: this.getTransactionTypeText(type),
      amountText: `${isIncome ? '+' : ''}${amountText}`,
      directionClass: isIncome ? 'income' : 'expense'
    }
  },

  getTransactionTypeText(type) {
    switch (type) {
      case 'recharge':
        return '\u5145\u503c'
      case 'pay':
        return '\u652f\u4ed8'
      case 'refund':
        return '\u9000\u6b3e'
      default:
        return '\u4ea4\u6613'
    }
  }
})
