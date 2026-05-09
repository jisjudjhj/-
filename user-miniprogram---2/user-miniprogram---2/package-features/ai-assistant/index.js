const app = getApp()
const request = require('../../utils/request')
const { resolveProductImage } = require('../../utils/image')
const { buildProductShareMessage } = require('../../utils/share')

function createEmptySnapshot() {
  return {
    summary: '',
    personaTitle: '',
    personaSummary: '',
    strategyLabel: '',
    details: [],
    insightCards: [],
    nextActions: [],
  }
}

Page({
  data: {
    messages: [],
    inputValue: '',
    loading: false,
    scrollTarget: '',
    msgIdCounter: 0,
    welcomePrompts: [],
    shoppingSnapshot: createEmptySnapshot(),
    snapshotExpanded: false,
  },

  onLoad(options) {
    if (!app.requireLogin('/pages/ai-assistant/index')) {
      return
    }

    const presetPrompt = options && options.prompt
      ? decodeURIComponent(options.prompt)
      : ''

    this.setData({
      welcomePrompts: this.buildWelcomePrompts(),
      inputValue: presetPrompt,
    })

    if (presetPrompt) {
      setTimeout(() => this.sendMessage(), 80)
    }
  },

  sendQuickMessage(e) {
    const msg = e.currentTarget.dataset.msg
    if (!msg) {
      return
    }

    this.setData({ inputValue: msg })
    this.sendMessage()
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value })
  },

  async sendMessage() {
    const text = (this.data.inputValue || '').trim()
    if (!text || this.data.loading) {
      return
    }

    const previousMessages = this.data.messages || []
    const history = this.buildHistory(previousMessages)
    const userMsgId = this.data.msgIdCounter + 1
    const userMsg = {
      id: userMsgId,
      role: 'user',
      content: text,
    }
    const pendingMessages = [...previousMessages, userMsg]

    this.setData({
      messages: pendingMessages,
      inputValue: '',
      loading: true,
      msgIdCounter: userMsgId,
      scrollTarget: 'msg-loading',
    })

    try {
      const res = await request.post('/ai/chat', {
        message: text,
        history,
      }, { showLoading: false })

      const aiMsgId = userMsgId + 1
      const reply = res && res.reply
        ? res.reply
        : '暂无推荐，请换个关键词。'
      const cleanedReply = reply.replace(/\[([^\]]+)\]\(product:\d+\)/g, '$1')
      const products = Array.isArray(res && res.products)
        ? res.products
            .filter(product => product && product.id)
            .map(product => ({
              id: product.id,
              name: product.name || '',
              title: product.name || '',
              image: resolveProductImage(product),
              price: this.formatPrice(product.price),
              priceText: this.formatPrice(product.price),
              tag: '推荐',
              extraInfo: product.categoryName || product.category || '',
              reason: product.recommendReason || '需求匹配',
              reasonShort: this.shortenText(product.recommendReason || '需求匹配', 16),
            }))
        : []

      const intent = res && res.intent ? res.intent : null
      const clarificationQuestion = res && res.clarificationQuestion ? res.clarificationQuestion : ''
      const needClarification = !!(res && res.needClarification)
      const suggestedPrompts = this.buildSuggestedPrompts(
        res && res.suggestedPrompts,
        intent,
        needClarification,
        products,
      )

      const shoppingSnapshot = this.buildShoppingSnapshot(res)
      const contentShort = this.compactAssistantReply(cleanedReply, products, shoppingSnapshot)
      const hasMore = cleanedReply.length > contentShort.length
      const aiMsg = {
        id: aiMsgId,
        role: 'assistant',
        content: cleanedReply,
        contentShort,
        hasMore,
        collapsed: hasMore,
        focusPoints: this.buildFocusPoints(cleanedReply, products, shoppingSnapshot),
        products,
        intentTags: this.buildIntentTags(intent),
        needClarification,
        clarificationQuestion: clarificationQuestion && cleanedReply.indexOf(clarificationQuestion) === -1
          ? clarificationQuestion
          : '',
        followUpSuggestions: this.buildFollowUpSuggestions(intent, needClarification, products).slice(0, 2),
        suggestedPrompts,
        strategyLabel: shoppingSnapshot.strategyLabel || '',
        shoppingSnapshot,
      }

      this.setData({
        shoppingSnapshot,
        snapshotExpanded: false,
        messages: [...pendingMessages, aiMsg],
        loading: false,
        msgIdCounter: aiMsgId,
        scrollTarget: `msg-${aiMsgId}`,
      })
    } catch (error) {
      console.error('智能 request failed', error)
      const errMsgId = userMsgId + 1
      this.setData({
        messages: [...pendingMessages, {
          id: errMsgId,
          role: 'assistant',
          content: '网络忙，请重试。',
        }],
        loading: false,
        msgIdCounter: errMsgId,
        scrollTarget: `msg-${errMsgId}`,
      })
    }
  },

  buildHistory(messages) {
    const safeMessages = Array.isArray(messages) ? messages : []
    return safeMessages.slice(-10).map(message => ({
      role: message.role,
      content: message.content,
    }))
  },

  formatPrice(price) {
    if (price === null || price === undefined || price === '') {
      return ''
    }

    const numericPrice = Number(price)
    if (!Number.isFinite(numericPrice)) {
      return `${price}`
    }

    return Number.isInteger(numericPrice)
      ? `${numericPrice}`
      : numericPrice.toFixed(2).replace(/\.?0+$/, '')
  },

  buildIntentTags(intent) {
    if (!intent) {
      return []
    }

    const tags = []
    if (intent.categoryName) {
      tags.push(intent.categoryName)
    }

    if (intent.budgetMin != null && intent.budgetMax != null) {
      tags.push(`${this.formatPrice(intent.budgetMin)}-${this.formatPrice(intent.budgetMax)}元`)
    } else if (intent.budgetMax != null) {
      tags.push(`${this.formatPrice(intent.budgetMax)}元以内`)
    } else if (intent.budgetMin != null) {
      tags.push(`${this.formatPrice(intent.budgetMin)}元以上`)
    }

    ;(intent.keywords || []).slice(0, 2).forEach(keyword => tags.push(keyword))
    ;(intent.preferredBrands || []).slice(0, 1).forEach(brand => tags.push(brand))
    ;(intent.scenes || []).slice(0, 1).forEach(scene => tags.push(scene))

    if (intent.preferHighSales) {
      tags.push('销量优先')
    }
    if (intent.preferMajorBrand) {
      tags.push('品牌优先')
    }
    if (intent.preferAlternatives) {
      tags.push('多备选')
    }
    if (intent.preferLongTermUse) {
      tags.push('长期使用')
    }

    return this.uniqueStrings(tags).slice(0, 3)
  },

  buildSuggestedPrompts(serverPrompts, intent, needClarification, products) {
    const prompts = []

    this.uniqueStrings(serverPrompts).forEach(prompt => prompts.push(prompt))
    this.buildContextualPrompts(intent, products, needClarification).forEach(prompt => prompts.push(prompt))
    if (needClarification) {
      this.buildFollowUpSuggestions(intent, needClarification, products).forEach(prompt => prompts.push(prompt))
    }
    if (!prompts.length) {
      this.buildWelcomePrompts().forEach(prompt => prompts.push(prompt))
    }

    return this.uniquePrompts(prompts).slice(0, 2)
  },

  buildFollowUpSuggestions(intent, needClarification, products) {
    if (!needClarification || !intent) {
      return []
    }

    const prompts = []
    const categoryLabel = this.buildCategoryLabel(intent)
    const seed = this.getPromptSeed(intent, products)

    if (!intent.categoryName) {
      const topCategories = this.uniqueStrings(intent.topCategories || [])
      if (topCategories.length) {
        prompts.push(`我想买${topCategories[0]}，预算 ${this.estimateBudgetCap(intent, products)} 元以内`)
      }
      this.buildStarterPrompts(intent).forEach(prompt => prompts.push(prompt))
      return this.uniquePrompts(prompts).slice(0, 4)
    }

    this.buildBudgetPromptOptions(intent, products).forEach(prompt => prompts.push(prompt))
    if (!(intent.preferredBrands || []).length && !intent.preferMajorBrand) {
      this.buildBrandPromptOptions(intent, products).forEach(prompt => prompts.push(prompt))
    }
    if (!this.hasScene(intent, '通勤')) {
      prompts.push(this.buildCommutePrompt(intent, seed))
    }
    if (!this.hasScene(intent, '送人')) {
      prompts.push(this.buildGiftPrompt(intent, seed))
    }
    if (!intent.preferHighSales) {
      prompts.push(this.pickVariant(`${seed}:sales`, [
        `${categoryLabel}，把销量更高的排前面`,
        `${categoryLabel}，优先看销量更稳的`,
        `${categoryLabel}，销量更高优先`,
      ]))
    }
    if (!intent.preferLongTermUse) {
      prompts.push(this.pickVariant(`${seed}:long`, this.buildLongTermPromptOptions(intent)))
    }

    return this.uniquePrompts(prompts).slice(0, 2)
  },

  buildContextualPrompts(intent, products, needClarification) {
    const prompts = []
    const safeProducts = Array.isArray(products) ? products : []
    const leadProduct = safeProducts[0]
    const secondaryProduct = safeProducts[1]
    const categoryLabel = this.buildCategoryLabel(intent)
    const seed = this.getPromptSeed(intent, safeProducts)

    if (!needClarification && safeProducts.length >= 3) {
      prompts.push(intent && intent.preferAlternatives
        ? this.pickVariant(`${seed}:alt-used`, [
            '继续换一组差异更大的备选',
            '再来一批路线更分明的备选',
          ])
        : this.pickVariant(`${seed}:alt`, [
            '换一批不同侧重的备选',
            '再来一组差异更大的备选',
            '换一组路线更分明的备选',
          ]))
    }
    if (leadProduct && leadProduct.name) {
      const leadName = this.shortProductName(leadProduct.name)
      prompts.push(this.pickVariant(`${seed}:lead`, [
        `${leadName}值不值得买`,
        `${leadName}怎么样`,
      ]))
    }
    if (leadProduct && secondaryProduct && leadProduct.name && secondaryProduct.name) {
      const leftName = this.shortProductName(leadProduct.name)
      const rightName = this.shortProductName(secondaryProduct.name)
      prompts.push(this.pickVariant(`${seed}:compare`, [
        `对比 ${leftName} 和 ${rightName}`,
        `帮我比较 ${leftName} 和 ${rightName}`,
        `${leftName} 和 ${rightName} 怎么选`,
      ]))
    }
    if (!intent || !intent.preferHighSales) {
      prompts.push(this.pickVariant(`${seed}:sales-follow`, [
        `${categoryLabel}，把销量更高的排前面`,
        `${categoryLabel}，优先看销量更稳的`,
        `${categoryLabel}，销量更高优先`,
      ]))
    }
    if (!intent || (!(intent.preferredBrands || []).length && !intent.preferMajorBrand)) {
      this.buildBrandPromptOptions(intent, safeProducts).forEach(prompt => prompts.push(prompt))
    }
    if (!intent || !intent.preferLongTermUse) {
      prompts.push(this.pickVariant(`${seed}:long-follow`, this.buildLongTermPromptOptions(intent)))
    }
    if (!this.hasScene(intent, '通勤')) {
      prompts.push(this.buildCommutePrompt(intent, seed))
    }
    if (!this.hasScene(intent, '送人')) {
      prompts.push(this.buildGiftPrompt(intent, seed))
    }
    if (!needClarification) {
      this.buildBudgetPromptOptions(intent, safeProducts).slice(0, 1).forEach(prompt => prompts.push(prompt))
    }

    return this.uniquePrompts(prompts).slice(0, 3)
  },

  buildBudgetPromptOptions(intent, products) {
    if (intent && (intent.budgetMin != null || intent.budgetMax != null)) {
      return []
    }

    const categoryLabel = this.buildCategoryLabel(intent)
    if (/手机|电脑|笔记本|平板/.test(categoryLabel)) {
      return [`${categoryLabel}，预算 2000 元以内`, `${categoryLabel}，预算 3000 元左右`]
    }
    if (/耳机|键盘|鼠标|护肤|美妆/.test(categoryLabel)) {
      return [`${categoryLabel}，预算 300 元以内`, `${categoryLabel}，预算 500 元以内`]
    }
    if (/食品|零食|礼盒|生鲜|茶|咖啡/.test(categoryLabel)) {
      return [`${categoryLabel}，预算 200 元以内`, `${categoryLabel}，预算 300 元左右`]
    }
    if (/家居|家电|厨具|收纳|办公/.test(categoryLabel)) {
      return [`${categoryLabel}，预算 500 元以内`, `${categoryLabel}，预算 800 元左右`]
    }

    const cap = this.estimateBudgetCap(intent, products)
    if (!cap) {
      return []
    }

    return [
      `${categoryLabel}，预算 ${cap} 元以内`,
      `${categoryLabel}，预算 ${Math.max(200, Math.round(cap * 1.5))} 元左右`,
    ]
  },

  buildBrandPromptOptions(intent, products) {
    const categoryLabel = this.buildCategoryLabel(intent)
    const brandPrompts = this.extractLeadBrands(products).map(brand => `${categoryLabel}，优先看 ${brand}`)
    if (brandPrompts.length) {
      return brandPrompts.slice(0, 2)
    }
    if (/手机|耳机|数码|电脑/.test(categoryLabel)) {
      return [`${categoryLabel}，优先看苹果生态`, `${categoryLabel}，优先看华为`, `${categoryLabel}，优先看小米`]
    }
    if (/护肤|美妆/.test(categoryLabel)) {
      return [`${categoryLabel}，优先看兰蔻`, `${categoryLabel}，优先看欧莱雅`, `${categoryLabel}，优先看雅诗兰黛`]
    }
    return [`${categoryLabel}，优先看品牌更稳的`, `${categoryLabel}，优先看口碑更稳的`]
  },

  buildLongTermPromptOptions(intent) {
    const categoryLabel = this.buildCategoryLabel(intent)
    if (categoryLabel === '这类商品') {
      return ['更适合长期用的是哪款', '哪款更耐用更省心']
    }
    return [
      `${categoryLabel}，更适合长期使用`,
      '更适合长期用的是哪款',
      '哪款更耐用更省心',
    ]
  },

  buildCommutePrompt(intent, seed) {
    const categoryLabel = this.buildCategoryLabel(intent)
    if (/手机|耳机|电脑|数码/.test(categoryLabel)) {
      return this.pickVariant(`${seed}:commute`, ['更适合通勤的是哪款', '日常通勤更适合哪款'])
    }
    if (/护肤|美妆|防晒/.test(categoryLabel)) {
      return this.pickVariant(`${seed}:commute`, ['更适合日常通勤的是哪款', `${categoryLabel}，主要通勤使用`])
    }
    return this.pickVariant(`${seed}:commute`, [`${categoryLabel}，主要通勤使用`, '更适合通勤的是哪款'])
  },

  buildGiftPrompt(intent, seed) {
    const categoryLabel = this.buildCategoryLabel(intent)
    if (/护肤|美妆|食品/.test(categoryLabel)) {
      return this.pickVariant(`${seed}:gift`, ['更适合送礼的是哪款', `${categoryLabel}，更适合送礼`])
    }
    return this.pickVariant(`${seed}:gift`, [`${categoryLabel}，更适合送礼`, '换成更适合送人的'])
  },

  estimateBudgetCap(intent, products) {
    const safeProducts = Array.isArray(products) ? products : []
    const productWithPrice = safeProducts.find(item => {
      const value = Number(item && item.price)
      return Number.isFinite(value) && value > 0
    })

    if (productWithPrice) {
      return this.roundBudgetCap(Number(productWithPrice.price))
    }

    const categoryLabel = this.buildCategoryLabel(intent)
    if (/手机|电脑|笔记本|平板/.test(categoryLabel)) {
      return 3000
    }
    if (/耳机|键盘|鼠标|护肤|美妆/.test(categoryLabel)) {
      return 500
    }
    if (/食品|零食|礼盒|生鲜|茶|咖啡/.test(categoryLabel)) {
      return 300
    }
    if (/家居|家电|厨具|收纳|办公/.test(categoryLabel)) {
      return 800
    }
    return 1000
  },

  roundBudgetCap(price) {
    if (!Number.isFinite(price) || price <= 0) {
      return 0
    }
    if (price <= 200) {
      return Math.ceil(price / 50) * 50
    }
    if (price <= 1000) {
      return Math.ceil(price / 100) * 100
    }
    if (price <= 5000) {
      return Math.ceil(price / 500) * 500
    }
    return Math.ceil(price / 1000) * 1000
  },

  buildCategoryLabel(intent) {
    if (intent && intent.categoryName) {
      return intent.categoryName
    }
    const topCategory = intent && Array.isArray(intent.topCategories) ? intent.topCategories.find(Boolean) : ''
    return topCategory || '这类商品'
  },

  buildShoppingSnapshot(payload) {
    if (!payload || typeof payload !== 'object') {
      return createEmptySnapshot()
    }

    const shoppingBrief = payload.shoppingBrief && typeof payload.shoppingBrief === 'object'
      ? payload.shoppingBrief
      : {}
    const personaCard = payload.personaCard && typeof payload.personaCard === 'object'
      ? payload.personaCard
      : {}
    const strategyLabel = payload.strategyLabel || ''
    const insightCards = this.normalizeSnapshotCards(payload.insightCards)
    const nextActions = Array.isArray(payload.nextActions)
      ? payload.nextActions
          .filter(action => action && action.label)
          .map(action => ({
            label: action.label,
            prompt: action.prompt || action.description || action.label,
          }))
      : []
    const details = []

    if (shoppingBrief.category || shoppingBrief.budget) {
      details.push({
        title: '品类 / 预算',
        value: `${shoppingBrief.category || '未指定'} / ${shoppingBrief.budget || '未设置预算'}`,
      })
    }
    if (Array.isArray(shoppingBrief.brands) && shoppingBrief.brands.length) {
      details.push({
        title: '品牌偏好',
        value: shoppingBrief.brands.join(' / '),
      })
    }
    if (Array.isArray(shoppingBrief.scenes) && shoppingBrief.scenes.length) {
      details.push({
        title: '使用场景',
        value: shoppingBrief.scenes.join(' / '),
      })
    }

    return {
      summary: this.shortenText(shoppingBrief.summary || '', 36),
      personaTitle: personaCard.segmentName || '实时购物画像',
      personaSummary: this.shortenText(personaCard.summary || personaCard.strategyHint || '', 32),
      strategyLabel,
      details: details.slice(0, 2),
      insightCards: insightCards.slice(0, 2),
      nextActions: nextActions.slice(0, 2),
    }
  },

  normalizeSnapshotCards(cards) {
    if (!Array.isArray(cards)) {
      return []
    }

    return cards
      .filter(card => card && (card.title || card.value || card.detail))
      .map(card => ({
        title: card.title || card.label || '',
        value: card.value || card.score || '',
        detail: this.shortenText(card.description || card.detail || card.subtext || '', 24),
      }))
  },

  compactAssistantReply(reply, products, snapshot) {
    const text = `${reply || ''}`.replace(/\s+/g, ' ').trim()
    if (!text) {
      return '暂无推荐，请换个关键词。'
    }

    const firstSentence = text.split(/[。！？!?]/).find(item => item && item.trim().length >= 8)
    const lead = firstSentence ? firstSentence.trim() : text
    const productName = Array.isArray(products) && products[0] && products[0].name
      ? this.shortProductName(products[0].name)
      : ''
    const strategy = snapshot && snapshot.strategyLabel ? snapshot.strategyLabel : ''
    const prefix = productName ? `优先看 ${productName}。` : ''
    const suffix = strategy ? ` ${this.shortenText(strategy, 16)}。` : ''
    return this.shortenText(`${prefix}${lead}${suffix}`, 82)
  },

  buildFocusPoints(reply, products, snapshot) {
    const points = []
    const safeReply = `${reply || ''}`
    const safeProducts = Array.isArray(products) ? products : []
    const safeSnapshot = snapshot && typeof snapshot === 'object' ? snapshot : {}

    if (safeSnapshot.strategyLabel) {
      points.push(this.shortenText(safeSnapshot.strategyLabel, 18))
    }
    if (safeSnapshot.personaTitle) {
      points.push(this.shortenText(safeSnapshot.personaTitle, 18))
    }

    safeReply
      .split(/\n+/)
      .map(line => line.replace(/^[\d一二三四五六七八九十]+[\.、:：]\s*/, '').trim())
      .filter(line => line.length >= 6)
      .slice(0, 3)
      .forEach(line => points.push(this.shortenText(line, 20)))

    if (!points.length && safeProducts.length) {
      safeProducts.slice(0, 2).forEach(product => {
        const name = this.shortProductName(product.name || '')
        const priceText = this.formatPrice(product.price)
        points.push(priceText ? `${name} ¥${priceText}` : name)
      })
    }

    return this.uniqueStrings(points).slice(0, 2)
  },

  shortenText(text, maxLen) {
    const safeText = `${text || ''}`.replace(/\s+/g, ' ').trim()
    if (!safeText) {
      return ''
    }
    if (!maxLen || safeText.length <= maxLen) {
      return safeText
    }
    return `${safeText.slice(0, maxLen)}...`
  },

  toggleSnapshotExpand() {
    this.setData({ snapshotExpanded: !this.data.snapshotExpanded })
  },

  toggleMessageExpand(e) {
    const targetId = `${e.currentTarget.dataset.id || ''}`
    if (!targetId) {
      return
    }

    const nextMessages = (this.data.messages || []).map(message => {
      if (`${message.id}` !== targetId || !message.hasMore) {
        return message
      }
      return {
        ...message,
        collapsed: !message.collapsed,
      }
    })

    this.setData({
      messages: nextMessages,
      scrollTarget: `msg-${targetId}`,
    })
  },

  buildStarterPrompts(intent) {
    const categoryLabel = this.buildCategoryLabel(intent)
    if (categoryLabel && categoryLabel !== '这类商品') {
      if (/手机|电脑|笔记本|平板|数码|耳机/.test(categoryLabel)) {
        return [`${categoryLabel}，预算 500 元以内`, `帮我挑个适合送礼的${categoryLabel}`]
      }
      if (/护肤|美妆|防晒|个护/.test(categoryLabel)) {
        return [`300 元左右适合通勤的${categoryLabel}`, `帮我挑个更适合送礼的${categoryLabel}`]
      }
      if (/食品|零食|礼盒|生鲜|茶|咖啡/.test(categoryLabel)) {
        return [`适合送礼的${categoryLabel}`, `300 元以内更值得回购的${categoryLabel}`]
      }
      if (/家居|家电|厨具|收纳|办公/.test(categoryLabel)) {
        return [`通勤和办公都适合的${categoryLabel}`, `预算 500 元左右更实用的${categoryLabel}`]
      }
      return [`帮我挑个适合送礼的${categoryLabel}`, `预算 500 元左右的${categoryLabel}`]
    }
    return [
      '预算 300 元左右的防晒',
      '适合送礼的食品礼盒',
      '通勤和办公都适合的小家电',
      '学生党适合入手的平板',
    ]
  },

  buildWelcomePrompts() {
    const pool = [
      '预算 300 元左右的防晒',
      '适合送礼的食品礼盒',
      '通勤和办公都适合的小家电',
      '3000 元左右的拍照手机',
      '学生党适合入手的平板',
      '适合跑步用的运动装备',
      '预算 500 元左右的护肤礼盒',
      '适合日常通勤的家居好物',
    ]
    const offset = (new Date().getDate() + new Date().getHours()) % pool.length
    const rotated = pool.slice(offset).concat(pool.slice(0, offset))
    return rotated.slice(0, 3)
  },

  getPromptSeed(intent, products) {
    const parts = []
    if (intent && intent.contextMessage) {
      parts.push(intent.contextMessage)
    }
    ;(Array.isArray(products) ? products : []).slice(0, 2).forEach(product => {
      if (product && product.name) {
        parts.push(product.name)
      }
    })
    return parts.join('|') || 'assistant'
  },

  pickVariant(seed, options) {
    const list = Array.isArray(options) ? options.filter(Boolean) : []
    if (!list.length) {
      return ''
    }

    let score = 0
    const source = `${seed || ''}`
    for (let i = 0; i < source.length; i += 1) {
      score += source.charCodeAt(i)
    }
    return list[score % list.length]
  },

  shortProductName(name) {
    const text = `${name || ''}`.replace(/\s+/g, '').trim()
    if (!text) {
      return '这款商品'
    }
    return text.length <= 16 ? text : text.slice(0, 16)
  },

  extractLeadBrands(products) {
    const knownBrands = [
      '苹果', 'Apple', '华为', '小米', '荣耀', 'OPPO', 'vivo', '三星', 'Sony', '索尼',
      '联想', '戴尔', '惠普', '华硕', '兰蔻', '欧莱雅', '雅诗兰黛',
    ]
    const safeProducts = Array.isArray(products) ? products : []
    const result = []

    safeProducts.forEach(product => {
      const name = `${product && product.name ? product.name : ''}`
      const matchedBrand = knownBrands.find(brand => name.toLowerCase().indexOf(`${brand}`.toLowerCase()) !== -1)
      if (matchedBrand && result.indexOf(matchedBrand) === -1) {
        result.push(matchedBrand)
      }
    })

    return result
  },

  hasScene(intent, keyword) {
    if (!intent || !Array.isArray(intent.scenes) || !keyword) {
      return false
    }
    return intent.scenes.some(scene => `${scene || ''}`.indexOf(keyword) !== -1)
  },

  uniquePrompts(values) {
    const source = Array.isArray(values) ? values : []
    const result = []
    const seen = new Set()

    source.forEach(value => {
      const text = `${value || ''}`.trim()
      if (!text) {
        return
      }
      const key = this.promptIntentKey(text)
      if (!seen.has(key)) {
        seen.add(key)
        result.push(text)
      }
    })

    return result
  },

  promptIntentKey(text) {
    const normalized = `${text || ''}`.replace(/\s+/g, '')

    if (/备选|换一批|换一组|再来一组|再给我/.test(normalized)) {
      return 'alternatives'
    }
    if (/销量|热销|成交/.test(normalized)) {
      return 'sales'
    }
    if (/品牌|苹果|华为|小米|兰蔻|欧莱雅|雅诗兰黛/.test(normalized)) {
      return 'brand'
    }
    if (/长期|久用|耐用/.test(normalized)) {
      return 'long-term'
    }
    if (/通勤/.test(normalized)) {
      return 'scene-commute'
    }
    if (/送礼|送人/.test(normalized)) {
      return 'scene-gift'
    }
    if (/比较|对比|怎么选/.test(normalized)) {
      return 'compare'
    }
    if (/预算|元以内|元左右|元以上/.test(normalized)) {
      return `budget:${normalized.replace(/\d+/g, '#')}`
    }
    if (/值不值得|怎么样/.test(normalized)) {
      return 'product'
    }

    return normalized
  },

  uniqueStrings(values) {
    const source = Array.isArray(values) ? values : []
    const result = []

    source.forEach(value => {
      const text = `${value || ''}`.trim()
      if (text && result.indexOf(text) === -1) {
        result.push(text)
      }
    })

    return result
  },

  goToProduct(e) {
    const id = (e.detail && e.detail.id) || (e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.id)
    if (!id) {
      wx.showToast({ title: '商品信息异常', icon: 'none' })
      return
    }

    app.navigateToPage(`/pages/product-detail/index?id=${id}`)
  },

  onShareAppMessage(res) {
    return buildProductShareMessage(res)
  },
})

