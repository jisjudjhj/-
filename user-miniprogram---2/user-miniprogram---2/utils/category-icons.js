const CATEGORY_ICON_SET = [
  { key: 'digital', iconText: '数', iconClass: 'icon-digital', names: ['手机数码', '3C数码'] },
  { key: 'office', iconText: '办', iconClass: 'icon-office', names: ['电脑办公'] },
  { key: 'appliance', iconText: '电', iconClass: 'icon-appliance', names: ['家用电器'] },
  { key: 'fashion', iconText: '搭', iconClass: 'icon-fashion', names: ['服饰鞋包'] },
  { key: 'beauty', iconText: '美', iconClass: 'icon-beauty', names: ['美妆护肤'] },
  { key: 'food', iconText: '鲜', iconClass: 'icon-food', names: ['食品生鲜'] },
  { key: 'book', iconText: '书', iconClass: 'icon-book', names: ['图书文具'] },
  { key: 'sport', iconText: '动', iconClass: 'icon-sport', names: ['运动户外'] },
  { key: 'baby', iconText: '宝', iconClass: 'icon-baby', names: ['母婴玩具'] },
  { key: 'home', iconText: '家', iconClass: 'icon-home', names: ['家居家装'] },
  { key: 'mobility', iconText: '行', iconClass: 'icon-mobility', names: ['智能出行'] },
  { key: 'health', iconText: '医', iconClass: 'icon-health', names: ['医疗健康'] },
  { key: 'industry', iconText: '工', iconClass: 'icon-industry', names: ['工业制造'] },
  { key: 'agri', iconText: '农', iconClass: 'icon-agri', names: ['农业生产'] },
  { key: 'edu', iconText: '学', iconClass: 'icon-edu', names: ['教育培训'] },
  { key: 'travel', iconText: '旅', iconClass: 'icon-travel', names: ['文旅服务'] },
  { key: 'business', iconText: '企', iconClass: 'icon-business', names: ['企业服务'] },
  { key: 'finance', iconText: '金', iconClass: 'icon-finance', names: ['金融保险'] },
  { key: 'energy', iconText: '能', iconClass: 'icon-energy', names: ['新能源储能'] },
  { key: 'service', iconText: '服', iconClass: 'icon-service', names: ['家政服务'] },
  { key: 'pet', iconText: '宠', iconClass: 'icon-pet', names: ['宠物产业'] },
  { key: 'creative', iconText: '创', iconClass: 'icon-creative', names: ['文化创意'] },
]

const DEFAULT_CATEGORY_META = {
  iconText: '类',
  iconClass: 'icon-default',
  iconKey: 'default',
}

function normalizeCategoryName(name) {
  return String(name || '').trim().replace(/\s+/g, '')
}

const CATEGORY_ICON_META = CATEGORY_ICON_SET.reduce((result, item) => {
  const baseMeta = {
    iconText: item.iconText,
    iconClass: item.iconClass,
    iconKey: item.key,
  }

  ;(item.names || []).forEach((name) => {
    const normalized = normalizeCategoryName(name)
    if (normalized) {
      result[normalized] = baseMeta
    }
  })
  return result
}, {})

function getCategoryMeta(name) {
  const normalized = normalizeCategoryName(name)
  if (!normalized) {
    return { ...DEFAULT_CATEGORY_META }
  }

  return CATEGORY_ICON_META[normalized] || {
    iconText: normalized.slice(0, 1),
    iconClass: 'icon-default',
    iconKey: 'default',
  }
}

function getCategoryIconClass(name) {
  return getCategoryMeta(name).iconClass
}

function mapCategoriesWithIconMeta(list, nameKey = 'name') {
  if (!Array.isArray(list)) return []
  return list.map((item) => {
    const safeItem = item && typeof item === 'object' ? item : {}
    const meta = getCategoryMeta(safeItem[nameKey])
    return {
      ...safeItem,
      iconText: meta.iconText,
      iconClass: meta.iconClass,
      iconKey: meta.iconKey,
    }
  })
}

module.exports = {
  CATEGORY_ICON_SET,
  CATEGORY_ICON_META,
  getCategoryMeta,
  getCategoryIconClass,
  mapCategoriesWithIconMeta,
}
