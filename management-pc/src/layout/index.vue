<template>
  <div class="layout-shell flex h-screen overflow-hidden bg-slate-100 dark:bg-slate-950">
    <aside
      class="layout-sidebar relative flex h-full shrink-0 flex-col overflow-hidden border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
      :class="isCollapse ? 'w-20' : 'w-60'"
    >
      <el-tooltip
        v-if="!isCompactViewport"
        :content="isCollapse ? '展开导航' : '收起导航'"
        placement="right"
        :teleported="false"
      >
        <el-button
          circle
          size="small"
          class="sidebar-edge-toggle !absolute !-right-4 !top-20 !z-20 !h-9 !w-9 !border !border-slate-200 !bg-white !text-slate-600 !shadow-lg hover:!border-blue-200 hover:!text-blue-600 dark:!border-slate-700 dark:!bg-slate-900 dark:!text-slate-200"
          @click="toggleCollapse"
        >
          <el-icon><component :is="isCollapse ? 'Expand' : 'Fold'" /></el-icon>
        </el-button>
      </el-tooltip>

      <div
        class="flex h-[60px] shrink-0 items-center border-b border-slate-200 px-4 dark:border-slate-800"
        :class="isCollapse ? 'justify-center' : 'justify-between'"
      >
        <div class="flex items-center gap-3 overflow-hidden">
          <BrandLogo
            :compact="isCollapse"
            title="数智优购"
            :subtitle="userRole === 'admin' ? '平台运营后台' : '商家经营后台'"
          />
        </div>
      </div>

      <el-scrollbar class="sidebar-scroll min-h-0 flex-1 px-3 py-4">
        <el-menu :default-active="route.path" class="sidebar-menu" :collapse="isCollapse" router>
          <template v-if="userRole === 'admin'">
            <div v-if="!isCollapse" class="menu-label">今日工作</div>
            <el-menu-item v-for="item in adminPrimaryItems" :key="item.index" :index="item.index">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>
                <el-badge
                  v-if="getMenuBadgeCount(item.index) > 0"
                  :value="getMenuBadgeCount(item.index)"
                  :hidden="isCollapse"
                  :max="99"
                >
                  <span>{{ item.title }}</span>
                </el-badge>
                <span v-else>{{ item.title }}</span>
              </template>
            </el-menu-item>

            <div v-if="!isCollapse" class="menu-label mt-4">技术展陈</div>
            <el-sub-menu index="/admin/tech">
              <template #title>
                <el-icon><Cpu /></el-icon>
                <span>技术总览</span>
              </template>
              <el-menu-item v-for="item in adminTechItems" :key="item.index" :index="item.index">
                {{ item.title }}
              </el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="/admin/bigdata">
              <template #title>
                <el-icon><TrendCharts /></el-icon>
                <span>大数据分析</span>
              </template>
              <el-menu-item v-for="item in adminBigDataItems" :key="item.index" :index="item.index">
                {{ item.title }}
              </el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="/admin/user-insight">
              <template #title>
                <el-icon><UserFilled /></el-icon>
                <span>用户洞察</span>
              </template>
              <el-menu-item v-for="item in adminUserInsightItems" :key="item.index" :index="item.index">
                {{ item.title }}
              </el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="/admin/algorithm">
              <template #title>
                <el-icon><MagicStick /></el-icon>
                <span>推荐算法</span>
              </template>
              <el-menu-item v-for="item in adminRecommendItems" :key="item.index" :index="item.index">
                {{ item.title }}
              </el-menu-item>
            </el-sub-menu>

            <div v-if="!isCollapse" class="menu-label mt-4">安全与配置</div>
            <el-sub-menu index="/admin/system">
              <template #title>
                <el-icon><Setting /></el-icon>
                <span>系统配置</span>
              </template>
              <el-menu-item v-for="item in adminSystemItems" :key="item.index" :index="item.index">
                <el-badge
                  v-if="getMenuBadgeCount(item.index) > 0"
                  :value="getMenuBadgeCount(item.index)"
                  :hidden="isCollapse"
                  :max="99"
                >
                  <span>{{ item.title }}</span>
                </el-badge>
                <span v-else>{{ item.title }}</span>
              </el-menu-item>
            </el-sub-menu>
          </template>

          <template v-else-if="userRole === 'merchant'">
            <div v-if="!isCollapse" class="menu-label">履约工作</div>
            <el-menu-item v-for="item in merchantMenuItems" :key="item.index" :index="item.index">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>
                <el-badge
                  v-if="getMenuBadgeCount(item.index) > 0"
                  :value="getMenuBadgeCount(item.index)"
                  :hidden="isCollapse"
                  :max="99"
                >
                  <span>{{ item.title }}</span>
                </el-badge>
                <span v-else>{{ item.title }}</span>
              </template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>

    </aside>

    <main class="layout-main flex h-full min-w-0 flex-1 flex-col overflow-hidden">
      <header class="layout-header flex h-[60px] shrink-0 items-center justify-between border-b border-slate-200 bg-white/95 px-4 md:px-5 dark:border-slate-800 dark:bg-slate-900/95">
        <div class="min-w-0">
          <div class="truncate text-lg font-semibold text-slate-950 dark:text-slate-100">{{ currentRouteName }}</div>
        </div>

        <div class="flex items-center gap-3">
          <el-badge :hidden="workbenchAlertCount === 0" :value="workbenchAlertCount" :max="99">
            <el-button text circle @click="goToAlertCenter">
              <el-icon><Bell /></el-icon>
            </el-button>
          </el-badge>

          <el-button text circle title="切换外观" @click="toggleTheme">
            <el-icon><component :is="isDark ? 'Sunny' : 'Moon'" /></el-icon>
          </el-button>

          <el-dropdown @command="handleCommand" trigger="click">
            <div class="flex cursor-pointer items-center gap-3 rounded-xl border border-slate-200 px-3 py-2 text-sm text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800">
              <el-avatar :size="30" class="bg-blue-600 text-white">
                {{ displayName.charAt(0).toUpperCase() }}
              </el-avatar>
              <div class="hidden text-left sm:block">
                <div class="font-medium">{{ displayName }}</div>
                <div class="text-xs text-slate-500 dark:text-slate-400">{{ userRole === 'admin' ? '平台运营' : '店铺运营' }}</div>
              </div>
              <el-icon class="text-slate-400"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">账号资料</el-dropdown-item>
                <el-dropdown-item command="settings">偏好设置</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <div class="layout-content-scroll min-h-0 flex-1 overflow-auto px-4 py-3 md:px-5 md:py-4">
        <div class="mx-auto w-full max-w-[1600px]">
          <router-view v-slot="{ Component, route: currentRoute }">
            <transition name="fade-transform" mode="out-in">
              <div
                v-if="Component"
                :key="currentRoute.fullPath"
                class="route-view-transition-shell"
              >
                <component :is="Component" />
              </div>
            </transition>
          </router-view>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { getAdminWorkbenchBadgeCounts } from '../api/admin'
import { getMerchantWorkbenchBadgeCounts } from '../api/merchant'
import { useUserStore } from '../store/user'
import { connectRealtime, disconnectRealtime, isRealtimeConnected, subscribeRealtime } from '../utils/realtime'
import BrandLogo from '../components/BrandLogo.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const compactBreakpoint = 1280
const isCompactViewport = ref(typeof window !== 'undefined' ? window.innerWidth < compactBreakpoint : false)
const isCollapse = ref(isCompactViewport.value)
const isDark = ref(false)
const adminBadgeCounts = ref({
  profileChanges: 0,
  refunds: 0,
  reviews: 0,
  seckillApplications: 0,
})
const merchantBadgeCounts = ref({
  messages: 0,
  refunds: 0,
  reviews: 0,
  seckillApplications: 0,
})
let badgeRefreshTimer = null
let unsubscribeWorkbenchRealtime = null
const badgeRefreshLocks = new Map()
const queuedScopePayloads = new Map()
let scopedRefreshTimer = null

const withBadgeRefreshLock = (key, runner) => {
  const lockKey = String(key || '')
  if (!lockKey) {
    return Promise.resolve().then(runner)
  }
  if (badgeRefreshLocks.has(lockKey)) {
    return badgeRefreshLocks.get(lockKey)
  }
  const task = Promise.resolve().then(runner).finally(() => {
    badgeRefreshLocks.delete(lockKey)
  })
  badgeRefreshLocks.set(lockKey, task)
  return task
}

const adminPrimaryItems = [
  { index: '/admin/dashboard', title: '运营工作台', icon: 'DataLine' },
  { index: '/admin/products', title: '商品管理', icon: 'Goods' },
  { index: '/admin/categories', title: '类目结构', icon: 'Files' },
  { index: '/admin/coupons', title: '优惠策略', icon: 'Ticket' },
  { index: '/admin/seckill', title: '秒杀场次', icon: 'Lightning' },
  { index: '/admin/orders', title: '订单履约', icon: 'List' },
  { index: '/admin/refunds', title: '售后退款', icon: 'Money' },
  { index: '/admin/users', title: '用户资产', icon: 'User' },
  { index: '/admin/wallet', title: '资金流水', icon: 'Wallet' },
  { index: '/admin/banners', title: '首页展示', icon: 'Picture' },
  { index: '/admin/merchants', title: '商家入驻', icon: 'Shop' },
  { index: '/admin/customer-service', title: '客服工作台', icon: 'Service' },
]

const adminTechItems = [
  { index: '/admin/analytics/tech-overview', title: '技术总览' },
  { index: '/admin/analytics/realtime-stream', title: '实时流式计算' },
]

const adminBigDataItems = [
  { index: '/admin/analytics/bigdata', title: '经营洞察' },
  { index: '/admin/analytics/sales', title: '销售分析' },
]

const adminUserInsightItems = [
  { index: '/admin/analytics/behavior', title: '用户行为' },
  { index: '/admin/analytics/user-portrait', title: '用户画像' },
  { index: '/admin/analytics/user-clusters', title: '用户分群' },
]

const adminRecommendItems = [
  { index: '/admin/recommend/preview', title: '推荐预览' },
  { index: '/admin/analytics/recommend', title: '推荐效果分析' },
  { index: '/admin/recommend/abtest', title: 'A/B 实验' },
  { index: '/admin/ai', title: '智能助手' },
]

const adminSystemItems = [
  { index: '/admin/profile-changes', title: '资料审核' },
  { index: '/admin/reviews', title: '评价治理' },
  { index: '/admin/system/modules', title: '模块开关' },
  { index: '/admin/system/roles', title: '角色权限' },
  { index: '/admin/system/risk', title: '风险控制' },
  { index: '/admin/system/messages', title: '触达消息' },
  { index: '/admin/system/logs', title: '操作审计' },
]

const merchantMenuItems = [
  { index: '/merchant/dashboard', title: '店铺工作台', icon: 'DataLine' },
  { index: '/merchant/products', title: '商品货架', icon: 'Goods' },
  { index: '/merchant/seckill', title: '秒杀报名', icon: 'Lightning' },
  { index: '/merchant/messages', title: '消息待办', icon: 'Bell' },
  { index: '/merchant/customer-service', title: '客服会话', icon: 'Service' },
  { index: '/merchant/orders', title: '订单履约', icon: 'List' },
  { index: '/merchant/refunds', title: '售后处理', icon: 'Money' },
  { index: '/merchant/reviews', title: '评价维护', icon: 'ChatDotRound' },
  { index: '/merchant/analytics', title: '经营分析', icon: 'TrendCharts' },
  { index: '/merchant/finance', title: '结算资金', icon: 'Wallet' },
  { index: '/merchant/store', title: '店铺资料', icon: 'Setting' },
]

const pageTitleMap = {
  AdminDashboard: '运营工作台',
  AdminProducts: '商品管理',
  AdminCategories: '类目结构',
  AdminCoupons: '优惠策略',
  AdminSeckill: '秒杀场次',
  AdminOrders: '订单履约',
  AdminRefunds: '售后退款',
  AdminUsers: '用户资产',
  AdminProfileChanges: '资料审核',
  AdminWallet: '资金流水',
  AdminReviews: '评价治理',
  AdminBanners: '首页展示',
  AdminMerchants: '商家入驻',
  AdminAi: '智能助手',
  AdminCustomerService: '客服工作台',
  AdminDataAnalysis: '经营洞察',
  AdminSalesAnalytics: '销售分析',
  AdminBehaviorAnalytics: '行为分析',
  AdminUserPortrait: '用户画像',
  AdminRecommendAnalytics: '推荐效果分析',
  AdminRealtimeStreamBoard: '实时流式计算',
  AdminTechOverview: '技术总览',
  AdminABTest: 'A/B 实验',
  AdminRecommendPreview: '推荐预览',
  AdminModules: '模块开关',
  AdminRolePermissions: '角色权限',
  AdminRiskControl: '风险控制',
  AdminMessages: '触达消息',
  AdminLogs: '操作审计',
  AdminUserClusterAnalysis: '用户分群',
  MerchantDashboard: '店铺工作台',
  MerchantProducts: '商品货架',
  MerchantSeckill: '秒杀报名',
  MerchantMessages: '消息待办',
  MerchantCustomerService: '客服会话',
  MerchantOrders: '订单履约',
  MerchantRefunds: '售后处理',
  MerchantReviews: '评价维护',
  MerchantAnalytics: '经营分析',
  MerchantFinance: '结算资金',
  MerchantStore: '店铺资料',
  AccountProfile: '账号资料',
  AccountSettings: '偏好设置',
}

const userRole = computed(() => userStore.role)
const displayName = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || '用户')
const currentRouteName = computed(() => pageTitleMap[route.name] || '管理后台')
const workbenchAlertCount = computed(() => {
  if (userRole.value === 'admin') {
    return Object.values(adminBadgeCounts.value).reduce((sum, count) => sum + Number(count || 0), 0)
  }
  if (userRole.value === 'merchant') {
    return Object.values(merchantBadgeCounts.value).reduce((sum, count) => sum + Number(count || 0), 0)
  }
  return 0
})

const syncShellViewport = () => {
  const nextCompact = window.innerWidth < compactBreakpoint
  if (nextCompact !== isCompactViewport.value) {
    isCompactViewport.value = nextCompact
    isCollapse.value = nextCompact
  }
}

const toggleCollapse = () => {
  if (isCompactViewport.value) {
    return
  }
  isCollapse.value = !isCollapse.value
}

const toggleTheme = () => {
  isDark.value = !isDark.value
  if (isDark.value) {
    document.documentElement.classList.add('dark')
    localStorage.setItem('theme', 'dark')
  } else {
    document.documentElement.classList.remove('dark')
    localStorage.setItem('theme', 'light')
  }
}

const resetAdminBadgeCounts = () => {
  adminBadgeCounts.value = {
    profileChanges: 0,
    refunds: 0,
    reviews: 0,
    seckillApplications: 0,
  }
}

const resetMerchantBadgeCounts = () => {
  merchantBadgeCounts.value = {
    messages: 0,
    refunds: 0,
    reviews: 0,
    seckillApplications: 0,
  }
}

const toSafeBadgeNumber = value => {
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return 0
  }
  return Math.max(0, Math.floor(number))
}

const hasKey = (source, key) => source && typeof source === 'object' && Object.prototype.hasOwnProperty.call(source, key)

const applyAdminBadgeCounts = counts => {
  if (!counts || typeof counts !== 'object') {
    return false
  }
  let applied = false
  if (hasKey(counts, 'profileChanges')) {
    adminBadgeCounts.value.profileChanges = toSafeBadgeNumber(counts.profileChanges)
    applied = true
  }
  if (hasKey(counts, 'refunds')) {
    adminBadgeCounts.value.refunds = toSafeBadgeNumber(counts.refunds)
    applied = true
  }
  if (hasKey(counts, 'reviews')) {
    adminBadgeCounts.value.reviews = toSafeBadgeNumber(counts.reviews)
    applied = true
  }
  if (hasKey(counts, 'seckillApplications')) {
    adminBadgeCounts.value.seckillApplications = toSafeBadgeNumber(counts.seckillApplications)
    applied = true
  }
  return applied
}

const applyMerchantBadgeCounts = counts => {
  if (!counts || typeof counts !== 'object') {
    return false
  }
  let applied = false
  if (hasKey(counts, 'messages')) {
    merchantBadgeCounts.value.messages = toSafeBadgeNumber(counts.messages)
    applied = true
  }
  if (hasKey(counts, 'refunds')) {
    merchantBadgeCounts.value.refunds = toSafeBadgeNumber(counts.refunds)
    applied = true
  }
  if (hasKey(counts, 'reviews')) {
    merchantBadgeCounts.value.reviews = toSafeBadgeNumber(counts.reviews)
    applied = true
  }
  if (hasKey(counts, 'seckillApplications')) {
    merchantBadgeCounts.value.seckillApplications = toSafeBadgeNumber(counts.seckillApplications)
    applied = true
  }
  return applied
}

const requestAdminBadgeCounts = async scope => {
  const params = scope ? { scope } : undefined
  const response = await getAdminWorkbenchBadgeCounts(params)
  return response && typeof response === 'object' ? response : {}
}

const requestMerchantBadgeCounts = async scope => {
  const params = scope ? { scope } : undefined
  const response = await getMerchantWorkbenchBadgeCounts(params)
  return response && typeof response === 'object' ? response : {}
}

const refreshAdminProfileBadgeCount = async () => withBadgeRefreshLock('admin:profileChanges', async () => {
  if (userRole.value !== 'admin') {
    adminBadgeCounts.value.profileChanges = 0
    return
  }
  try {
    const counts = await requestAdminBadgeCounts('profile-change')
    if (!applyAdminBadgeCounts(counts)) {
      adminBadgeCounts.value.profileChanges = 0
    }
  } catch {
    adminBadgeCounts.value.profileChanges = 0
  }
})

const refreshAdminRefundBadgeCount = async () => withBadgeRefreshLock('admin:refunds', async () => {
  if (userRole.value !== 'admin') {
    adminBadgeCounts.value.refunds = 0
    return
  }
  try {
    const counts = await requestAdminBadgeCounts('refund')
    if (!applyAdminBadgeCounts(counts)) {
      adminBadgeCounts.value.refunds = 0
    }
  } catch {
    adminBadgeCounts.value.refunds = 0
  }
})

const refreshAdminReviewBadgeCount = async () => withBadgeRefreshLock('admin:reviews', async () => {
  if (userRole.value !== 'admin') {
    adminBadgeCounts.value.reviews = 0
    return
  }
  try {
    const counts = await requestAdminBadgeCounts('review')
    if (!applyAdminBadgeCounts(counts)) {
      adminBadgeCounts.value.reviews = 0
    }
  } catch {
    adminBadgeCounts.value.reviews = 0
  }
})

const refreshAdminSeckillBadgeCount = async () => withBadgeRefreshLock('admin:seckillApplications', async () => {
  if (userRole.value !== 'admin') {
    adminBadgeCounts.value.seckillApplications = 0
    return
  }
  try {
    const counts = await requestAdminBadgeCounts('seckill')
    if (!applyAdminBadgeCounts(counts)) {
      adminBadgeCounts.value.seckillApplications = 0
    }
  } catch {
    adminBadgeCounts.value.seckillApplications = 0
  }
})

const refreshAdminBadgeCounts = async () => withBadgeRefreshLock('admin:all', async () => {
  if (userRole.value !== 'admin') {
    resetAdminBadgeCounts()
    return
  }
  try {
    const counts = await requestAdminBadgeCounts()
    if (!applyAdminBadgeCounts(counts)) {
      resetAdminBadgeCounts()
    }
  } catch {
    resetAdminBadgeCounts()
  }
})

const refreshMerchantMessageBadgeCount = async () => withBadgeRefreshLock('merchant:messages', async () => {
  if (userRole.value !== 'merchant') {
    merchantBadgeCounts.value.messages = 0
    return
  }
  try {
    const counts = await requestMerchantBadgeCounts('message')
    if (!applyMerchantBadgeCounts(counts)) {
      merchantBadgeCounts.value.messages = 0
    }
  } catch {
    merchantBadgeCounts.value.messages = 0
  }
})

const refreshMerchantRefundBadgeCount = async () => withBadgeRefreshLock('merchant:refunds', async () => {
  if (userRole.value !== 'merchant') {
    merchantBadgeCounts.value.refunds = 0
    return
  }
  try {
    const counts = await requestMerchantBadgeCounts('refund')
    if (!applyMerchantBadgeCounts(counts)) {
      merchantBadgeCounts.value.refunds = 0
    }
  } catch {
    merchantBadgeCounts.value.refunds = 0
  }
})

const refreshMerchantReviewBadgeCount = async () => withBadgeRefreshLock('merchant:reviews', async () => {
  if (userRole.value !== 'merchant') {
    merchantBadgeCounts.value.reviews = 0
    return
  }
  try {
    const counts = await requestMerchantBadgeCounts('review')
    if (!applyMerchantBadgeCounts(counts)) {
      merchantBadgeCounts.value.reviews = 0
    }
  } catch {
    merchantBadgeCounts.value.reviews = 0
  }
})

const refreshMerchantSeckillBadgeCount = async () => withBadgeRefreshLock('merchant:seckillApplications', async () => {
  if (userRole.value !== 'merchant') {
    merchantBadgeCounts.value.seckillApplications = 0
    return
  }
  try {
    const counts = await requestMerchantBadgeCounts('seckill')
    if (!applyMerchantBadgeCounts(counts)) {
      merchantBadgeCounts.value.seckillApplications = 0
    }
  } catch {
    merchantBadgeCounts.value.seckillApplications = 0
  }
})

const refreshMerchantBadgeCounts = async () => withBadgeRefreshLock('merchant:all', async () => {
  if (userRole.value !== 'merchant') {
    resetMerchantBadgeCounts()
    return
  }
  try {
    const counts = await requestMerchantBadgeCounts()
    if (!applyMerchantBadgeCounts(counts)) {
      resetMerchantBadgeCounts()
    }
  } catch {
    resetMerchantBadgeCounts()
  }
})

const refreshWorkbenchBadges = async () => {
  if (userRole.value === 'admin') {
    resetMerchantBadgeCounts()
    await refreshAdminBadgeCounts()
    return
  }
  if (userRole.value === 'merchant') {
    resetAdminBadgeCounts()
    await refreshMerchantBadgeCounts()
    return
  }
  resetAdminBadgeCounts()
  resetMerchantBadgeCounts()
}

const normalizeRealtimeScope = payload => {
  const rawScope = String(payload?.scope || '').trim().toLowerCase()
  if (rawScope) {
    return rawScope
  }
  const eventName = String(payload?.event || '').trim().toLowerCase()
  if (!eventName) {
    return ''
  }
  if (eventName.includes('profile')) return 'profile-change'
  if (eventName.includes('refund')) return 'refund'
  if (eventName.includes('review')) return 'review'
  if (eventName.includes('seckill')) return 'seckill'
  if (eventName.includes('message')) return 'message'
  return ''
}

const applyRealtimeCounts = payload => {
  const counts = payload?.counts
  if (!counts || typeof counts !== 'object') {
    return false
  }
  if (userRole.value === 'admin') {
    return applyAdminBadgeCounts(counts)
  }
  if (userRole.value === 'merchant') {
    return applyMerchantBadgeCounts(counts)
  }
  return false
}

const refreshBadgesByScope = async payload => {
  if (applyRealtimeCounts(payload)) {
    return
  }

  const scope = normalizeRealtimeScope(payload)
  if (!scope) {
    await refreshWorkbenchBadges()
    return
  }

  if (userRole.value === 'admin') {
    if (scope === 'profile-change') {
      await refreshAdminProfileBadgeCount()
      return
    }
    if (scope === 'refund') {
      await refreshAdminRefundBadgeCount()
      return
    }
    if (scope === 'review') {
      await refreshAdminReviewBadgeCount()
      return
    }
    if (scope === 'seckill') {
      await refreshAdminSeckillBadgeCount()
      return
    }
    await refreshAdminBadgeCounts()
    return
  }

  if (userRole.value === 'merchant') {
    if (scope === 'message') {
      await refreshMerchantMessageBadgeCount()
      return
    }
    if (scope === 'refund') {
      await refreshMerchantRefundBadgeCount()
      return
    }
    if (scope === 'review') {
      await refreshMerchantReviewBadgeCount()
      return
    }
    if (scope === 'seckill') {
      await refreshMerchantSeckillBadgeCount()
      return
    }
    await refreshMerchantBadgeCounts()
    return
  }

  await refreshWorkbenchBadges()
}

const enqueueScopedBadgeRefresh = payload => {
  const scope = normalizeRealtimeScope(payload) || '__all__'
  const previous = queuedScopePayloads.get(scope) || {}
  queuedScopePayloads.set(scope, {
    ...previous,
    ...(payload || {}),
  })
  if (scopedRefreshTimer) {
    return
  }
  scopedRefreshTimer = window.setTimeout(() => {
    const pendingPayloads = Array.from(queuedScopePayloads.values())
    queuedScopePayloads.clear()
    scopedRefreshTimer = null
    pendingPayloads.forEach(item => {
      refreshBadgesByScope(item)
    })
  }, 120)
}

const resolveScopeFromRoutePath = path => {
  const value = String(path || '').toLowerCase()
  if (!value) return ''
  if (value.includes('/profile-changes')) return 'profile-change'
  if (value.includes('/refund')) return 'refund'
  if (value.includes('/review')) return 'review'
  if (value.includes('/seckill')) return 'seckill'
  if (value.includes('/messages')) return 'message'
  return ''
}

const getMenuBadgeCount = index => {
  if (userRole.value === 'admin') {
    const adminMap = {
      '/admin/profile-changes': adminBadgeCounts.value.profileChanges,
      '/admin/refunds': adminBadgeCounts.value.refunds,
      '/admin/reviews': adminBadgeCounts.value.reviews,
      '/admin/seckill': adminBadgeCounts.value.seckillApplications,
    }
    return Number(adminMap[index] || 0)
  }
  if (userRole.value === 'merchant') {
    const merchantMap = {
      '/merchant/messages': merchantBadgeCounts.value.messages,
      '/merchant/refunds': merchantBadgeCounts.value.refunds,
      '/merchant/reviews': merchantBadgeCounts.value.reviews,
      '/merchant/seckill': merchantBadgeCounts.value.seckillApplications,
    }
    return Number(merchantMap[index] || 0)
  }
  return 0
}

const goToAlertCenter = () => {
  if (userRole.value === 'admin') {
    if (adminBadgeCounts.value.profileChanges > 0) return router.push('/admin/profile-changes')
    if (adminBadgeCounts.value.refunds > 0) return router.push('/admin/refunds')
    if (adminBadgeCounts.value.reviews > 0) return router.push('/admin/reviews')
    if (adminBadgeCounts.value.seckillApplications > 0) return router.push({ path: '/admin/seckill', query: { tab: 'applications' } })
    return router.push('/admin/dashboard')
  }
  if (userRole.value === 'merchant') {
    if (merchantBadgeCounts.value.messages > 0) return router.push('/merchant/messages')
    if (merchantBadgeCounts.value.refunds > 0) return router.push('/merchant/refunds')
    if (merchantBadgeCounts.value.reviews > 0) return router.push('/merchant/reviews')
    if (merchantBadgeCounts.value.seckillApplications > 0) return router.push({ path: '/merchant/seckill', query: { tab: 'applications' } })
    return router.push('/merchant/dashboard')
  }
}

const handleCommand = command => {
  if (command === 'profile') {
    router.push({ name: 'AccountProfile' })
    return
  }
  if (command === 'settings') {
    router.push({ name: 'AccountSettings' })
    return
  }
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }).then(() => {
      disconnectRealtime()
      userStore.logout()
      router.push('/login')
    }).catch(() => {})
  }
}

const handlePendingRefreshEvent = () => {
  enqueueScopedBadgeRefresh({ scope: 'profile-change' })
}

const handleMerchantMessageRefreshEvent = () => {
  enqueueScopedBadgeRefresh({ scope: 'message' })
}

const handleAdminWorkbenchRefreshEvent = event => {
  enqueueScopedBadgeRefresh(event?.detail || {})
}

const handleMerchantWorkbenchRefreshEvent = event => {
  enqueueScopedBadgeRefresh(event?.detail || {})
}

const clearWorkbenchRealtimeSubscription = () => {
  if (typeof unsubscribeWorkbenchRealtime === 'function') {
    unsubscribeWorkbenchRealtime()
    unsubscribeWorkbenchRealtime = null
  }
}

const setupWorkbenchRealtimeSubscription = () => {
  clearWorkbenchRealtimeSubscription()
  if (import.meta.env.VITE_COMPETITION_MODE !== 'false') {
    disconnectRealtime()
    return
  }
  if (!userStore.token) {
    disconnectRealtime()
    return
  }
  if (userRole.value !== 'admin' && userRole.value !== 'merchant') {
    disconnectRealtime()
    return
  }

  connectRealtime()
  unsubscribeWorkbenchRealtime = subscribeRealtime('/user/queue/workbench-refresh', payload => {
    enqueueScopedBadgeRefresh(payload)
  })
}

watch(
  () => route.fullPath,
  fullPath => {
    const scope = resolveScopeFromRoutePath(fullPath)
    if (scope) {
      enqueueScopedBadgeRefresh({ scope })
      return
    }
    enqueueScopedBadgeRefresh({})
  }
)

watch(
  [() => userRole.value, () => userStore.token],
  () => {
    setupWorkbenchRealtimeSubscription()
  }
)

onMounted(() => {
  syncShellViewport()
  const savedTheme = localStorage.getItem('theme')
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  if (savedTheme === 'dark' || (!savedTheme && prefersDark)) {
    isDark.value = true
    document.documentElement.classList.add('dark')
  }

  refreshWorkbenchBadges()
  setupWorkbenchRealtimeSubscription()
  window.addEventListener('resize', syncShellViewport)
  window.addEventListener('profile-change-pending-refresh', handlePendingRefreshEvent)
  window.addEventListener('merchant-message-refresh', handleMerchantMessageRefreshEvent)
  window.addEventListener('admin-workbench-refresh', handleAdminWorkbenchRefreshEvent)
  window.addEventListener('merchant-workbench-refresh', handleMerchantWorkbenchRefreshEvent)
  badgeRefreshTimer = window.setInterval(() => {
    if (!isRealtimeConnected()) {
      refreshWorkbenchBadges()
    }
  }, 300000)
})

onUnmounted(() => {
  window.removeEventListener('resize', syncShellViewport)
  window.removeEventListener('profile-change-pending-refresh', handlePendingRefreshEvent)
  window.removeEventListener('merchant-message-refresh', handleMerchantMessageRefreshEvent)
  window.removeEventListener('admin-workbench-refresh', handleAdminWorkbenchRefreshEvent)
  window.removeEventListener('merchant-workbench-refresh', handleMerchantWorkbenchRefreshEvent)
  clearWorkbenchRealtimeSubscription()
  if (scopedRefreshTimer) {
    window.clearTimeout(scopedRefreshTimer)
    scopedRefreshTimer = null
  }
  queuedScopePayloads.clear()
  disconnectRealtime()
  if (badgeRefreshTimer) {
    window.clearInterval(badgeRefreshTimer)
    badgeRefreshTimer = null
  }
})
</script>

<style scoped>
.menu-label {
  margin: 0 12px 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #8a96a8;
}

.sidebar-edge-toggle {
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.sidebar-edge-toggle:hover {
  transform: scale(1.05);
}

:deep(.sidebar-menu) {
  border-right: none;
  background: transparent;
}

:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  height: 40px;
  margin-bottom: 3px;
  border-radius: 10px;
  color: #516071;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover) {
  background: rgba(0, 122, 255, 0.08);
  color: #007aff;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: #007aff;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 10px 22px rgba(0, 122, 255, 0.18);
}

.dark :deep(.sidebar-menu .el-menu-item),
.dark :deep(.sidebar-menu .el-sub-menu__title) {
  color: #cbd5e1;
}

.dark :deep(.sidebar-menu .el-menu-item:hover),
.dark :deep(.sidebar-menu .el-sub-menu__title:hover) {
  background: rgba(96, 165, 250, 0.16);
  color: #dbeafe;
}

.dark :deep(.sidebar-menu .el-menu-item.is-active) {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #0f172a;
}

.layout-sidebar {
  box-shadow: 1px 0 0 rgba(15, 23, 42, 0.03);
  overscroll-behavior: contain;
}

.layout-header {
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.03);
  backdrop-filter: blur(14px);
}

.layout-content-scroll {
  overscroll-behavior: contain;
}

:deep(.sidebar-scroll .el-scrollbar__wrap) {
  overscroll-behavior: contain;
}

:deep(.el-avatar) {
  background: #007aff !important;
}

.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fade-transform-enter-from,
.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.route-view-transition-shell {
  min-width: 0;
}

@media (max-width: 768px) {
  .layout-sidebar {
    display: none !important;
  }

  .layout-header {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
