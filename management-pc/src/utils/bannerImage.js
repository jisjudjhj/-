const LEGACY_BANNER_PREFIX = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/seed/banners/'

const TITLE_BANNER_IMAGE_MAP = {
  '华为Mate 60 Pro 卫星通信旗舰': 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/huawei-mate-60-pro.webp',
  'iPhone 15 Pro 钛金属设计': 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/iphone-15-pro-max.webp',
  '春季焕新 服饰鞋包专场': 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/champion-hoodie.webp',
  '美妆大牌日 满300减50': 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/anessa-sunscreen.webp',
  '超值坚果礼盒 年货节特惠': 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp',
  'MacBook Pro M3 创造力无限': 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/macbook-pro-14.webp',
}

export function resolveBannerImage(title, image) {
  const fallbackImage = TITLE_BANNER_IMAGE_MAP[String(title || '').trim()]
  if (!image) {
    return fallbackImage || ''
  }
  if (String(image).startsWith(LEGACY_BANNER_PREFIX) && fallbackImage) {
    return fallbackImage
  }
  return image
}
