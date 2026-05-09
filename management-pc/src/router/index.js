import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'
import { getCurrentUser } from '../api/auth'

const hasDashboardAccess = (userStore) => userStore.token && ['admin', 'merchant'].includes(userStore.role)

const getDashboardPath = (userStore) => userStore.role === 'admin'
  ? '/admin/dashboard'
  : '/merchant/dashboard'

const viewModules = import.meta.glob('../views/**/*.vue')

const resolveView = (path) => viewModules[path] || viewModules['../views/NotFound.vue']

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: resolveView('../views/Login.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: resolveView('../views/NotFound.vue')
  },
  {
    path: '/dashboard',
    redirect: () => {
      const userStore = useUserStore()
      if (hasDashboardAccess(userStore)) {
        return getDashboardPath(userStore)
      }
      userStore.logout()
      return '/login'
    }
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('../layout/index.vue'),
    redirect: () => {
      const userStore = useUserStore()
      return hasDashboardAccess(userStore) ? getDashboardPath(userStore) : '/login'
    },
    children: [
      {
        path: 'admin/dashboard',
        name: 'AdminDashboard',
        component: resolveView('../views/admin/Dashboard.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/products',
        name: 'AdminProducts',
        component: resolveView('../views/admin/Products.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/categories',
        name: 'AdminCategories',
        component: resolveView('../views/admin/CategoryManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/coupons',
        name: 'AdminCoupons',
        component: resolveView('../views/admin/CouponManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/seckill',
        name: 'AdminSeckill',
        component: resolveView('../views/admin/SeckillManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/orders',
        name: 'AdminOrders',
        component: resolveView('../views/admin/Orders.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/refunds',
        name: 'AdminRefunds',
        component: resolveView('../views/admin/RefundManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: resolveView('../views/admin/Users.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/profile-changes',
        name: 'AdminProfileChanges',
        component: resolveView('../views/admin/ProfileChangeManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/wallet',
        name: 'AdminWallet',
        component: resolveView('../views/admin/WalletManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/reviews',
        name: 'AdminReviews',
        component: resolveView('../views/admin/ReviewManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/ai',
        name: 'AdminAi',
        component: resolveView('../views/admin/AiManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/customer-service',
        name: 'AdminCustomerService',
        component: resolveView('../views/admin/CustomerService.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/analytics/bigdata',
        name: 'AdminDataAnalysis',
        component: resolveView('../views/admin/DataAnalysis.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/analytics/sales',
        name: 'AdminSalesAnalytics',
        component: resolveView('../views/admin/SalesAnalytics.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/analytics/behavior',
        name: 'AdminBehaviorAnalytics',
        component: resolveView('../views/admin/BehaviorAnalytics.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/analytics/user-portrait',
        name: 'AdminUserPortrait',
        component: resolveView('../views/admin/UserPortrait.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/analytics/recommend',
        name: 'AdminRecommendAnalytics',
        component: resolveView('../views/admin/RecommendAnalytics.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/analytics/realtime-stream',
        name: 'AdminRealtimeStreamBoard',
        component: resolveView('../views/admin/RealtimeStreamBoard.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/analytics/user-clusters',
        name: 'AdminUserClusterAnalysis',
        component: resolveView('../views/admin/UserClusterAnalysis.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/analytics/tech-overview',
        name: 'AdminTechOverview',
        component: resolveView('../views/admin/TechOverview.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/recommend/abtest',
        name: 'AdminABTest',
        component: resolveView('../views/admin/ABTestPanel.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/recommend/preview',
        name: 'AdminRecommendPreview',
        component: resolveView('../views/admin/RecommendPreview.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/banners',
        name: 'AdminBanners',
        component: resolveView('../views/admin/BannerManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/merchants',
        name: 'AdminMerchants',
        component: resolveView('../views/admin/MerchantManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/system/modules',
        name: 'AdminModules',
        component: resolveView('../views/admin/ModuleManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/system/roles',
        name: 'AdminRolePermissions',
        component: resolveView('../views/admin/RolePermissionManagement.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/system/messages',
        name: 'AdminMessages',
        component: resolveView('../views/admin/MessageBroadcast.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/system/logs',
        name: 'AdminLogs',
        component: resolveView('../views/admin/OperationLog.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'admin/system/risk',
        name: 'AdminRiskControl',
        component: resolveView('../views/admin/RiskControlCenter.vue'),
        meta: { requiresAuth: true, role: 'admin' }
      },
      {
        path: 'merchant/dashboard',
        name: 'MerchantDashboard',
        component: resolveView('../views/merchant/Dashboard.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/products',
        name: 'MerchantProducts',
        component: resolveView('../views/merchant/Products.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/seckill',
        name: 'MerchantSeckill',
        component: resolveView('../views/merchant/SeckillActivities.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/messages',
        name: 'MerchantMessages',
        component: resolveView('../views/merchant/Messages.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/customer-service',
        name: 'MerchantCustomerService',
        component: resolveView('../views/merchant/CustomerService.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/orders',
        name: 'MerchantOrders',
        component: resolveView('../views/merchant/Orders.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/refunds',
        name: 'MerchantRefunds',
        component: resolveView('../views/merchant/Refunds.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/reviews',
        name: 'MerchantReviews',
        component: resolveView('../views/merchant/Reviews.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/analytics',
        name: 'MerchantAnalytics',
        component: resolveView('../views/merchant/Analytics.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/store',
        name: 'MerchantStore',
        component: resolveView('../views/merchant/StoreProfile.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'merchant/finance',
        name: 'MerchantFinance',
        component: resolveView('../views/merchant/Finance.vue'),
        meta: { requiresAuth: true, role: 'merchant' }
      },
      {
        path: 'account/profile',
        name: 'AccountProfile',
        component: resolveView('../views/account/Profile.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'account/settings',
        name: 'AccountSettings',
        component: resolveView('../views/account/Settings.vue'),
        meta: { requiresAuth: true }
      }
    ]
  }
]

const ensureSession = (() => {
  let sessionPromise = null
  return async (userStore) => {
    if (userStore.sessionChecked || !userStore.token) {
      userStore.sessionChecked = true
      return
    }
    if (sessionPromise) {
      return sessionPromise
    }

    sessionPromise = (async () => {
      try {
        const res = await getCurrentUser()
        if (res) {
          userStore.setUserInfo(res)
          if (res?.role) {
            userStore.setRole(res.role)
          }
        }
      } catch (error) {
        userStore.logout()
        throw error
      } finally {
        userStore.sessionChecked = true
        sessionPromise = null
      }
    })()

    return sessionPromise
  }
})()

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL || '/'),
  routes
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const shouldCheckSession = !!userStore.token && (to.meta.requiresAuth || to.path === '/dashboard')

  if (shouldCheckSession) {
    try {
      await ensureSession(userStore)
    } catch {
      // let guard handle redirects on failure
    }
  }
  const hasValidRole = ['admin', 'merchant'].includes(userStore.role)
  const canVisitDashboard = hasDashboardAccess(userStore) && hasValidRole
  const dashboardPath = getDashboardPath(userStore)

  if (to.meta.requiresAuth && (!userStore.token || !hasValidRole)) {
    userStore.logout()
    return '/login'
  }
  if (to.path === '/login' && userStore.token && hasValidRole) {
    return dashboardPath
  }
  if (to.path === '/dashboard') {
    if (canVisitDashboard) {
      return dashboardPath
    }
    userStore.logout()
    return '/login'
  }
  if (to.meta.requiresAuth && to.meta.role && to.meta.role !== userStore.role) {
    return dashboardPath
  }
})

export default router
