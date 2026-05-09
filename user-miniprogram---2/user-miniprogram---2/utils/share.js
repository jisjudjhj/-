function normalizeImageUrl(image) {
  const value = String(image || '').trim()
  return value || undefined
}

function normalizeProductTitle(title) {
  const value = String(title || '').trim()
  return value || '数智优购好物推荐'
}

function buildProductShareMessage(res, fallback = {}) {
  const dataset = (res && res.target && res.target.dataset) || {}
  const productId = dataset.productId || fallback.id || fallback.productId || ''
  const title = normalizeProductTitle(dataset.productTitle || fallback.title || fallback.name)
  const imageUrl = normalizeImageUrl(dataset.productImage || fallback.image)
  const path = productId
    ? `/pages/product-detail/index?id=${encodeURIComponent(productId)}`
    : '/pages/home/index'

  const message = {
    title: `我发现一个好物：${title}`,
    path,
  }

  if (imageUrl) {
    message.imageUrl = imageUrl
  }

  return message
}

module.exports = {
  buildProductShareMessage,
}
