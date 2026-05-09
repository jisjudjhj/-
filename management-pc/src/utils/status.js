export const PRODUCT_STATUS_MAP = {
  0: '下架',
  1: '上架',
}

export const PRODUCT_STATUS_OPTIONS = [
  { label: '下架', value: 0 },
  { label: '上架', value: 1 },
]

export const ORDER_STATUS_MAP = {
  0: '待支付',
  1: '已支付',
  2: '已发货',
  3: '已完成',
  4: '已取消',
  5: '已退款',
}

export const ORDER_STATUS_OPTIONS = [
  { label: '全部', value: -1 },
  { label: '待支付', value: 0 },
  { label: '已支付', value: 1 },
  { label: '已发货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已取消', value: 4 },
  { label: '已退款', value: 5 },
]

export const ORDER_NEXT_STATUS_MAP = {
  0: [
    { label: '标记已支付', value: 1 },
    { label: '取消订单', value: 4 },
  ],
  1: [
    { label: '标记已发货', value: 2 },
    { label: '取消订单', value: 4 },
  ],
  2: [
    { label: '标记已完成', value: 3 },
  ],
  3: [],
  4: [],
  5: [],
}

export const USER_STATUS_MAP = {
  0: '禁用',
  1: '正常',
}

export const PROFILE_CHANGE_STATUS_MAP = {
  0: '待审核',
  1: '已通过',
  2: '已拒绝',
}

export const REVIEW_STATUS_MAP = {
  0: '待审核',
  1: '已通过',
  2: '已拒绝',
}

export const REFUND_STATUS_MAP = {
  0: '待审核',
  1: '已同意',
  2: '已拒绝',
  3: '已退款',
}

export const COUPON_STATUS_MAP = {
  0: '未开始',
  1: '进行中',
  2: '已结束',
}

export const COUPON_STATUS_OPTIONS = [
  { label: '未开始', value: 0 },
  { label: '进行中', value: 1 },
  { label: '已结束', value: 2 },
]

export const COUPON_TYPE_MAP = {
  1: '满减券',
  2: '折扣券',
  3: '无门槛券',
}

export const COUPON_TYPE_OPTIONS = [
  { label: '满减券', value: 1 },
  { label: '折扣券', value: 2 },
  { label: '无门槛券', value: 3 },
]

export const ROLE_MAP = {
  admin: '管理员',
  merchant: '商家',
  user: '用户',
}

export function getLabel(map, value, fallback = '-') {
  return map[value] ?? fallback
}
