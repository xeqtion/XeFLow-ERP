import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const Layout = () => import('@/layout/index.vue')

/**
 * 业务路由静态注册为 Layout 的子路由 (路径与后端菜单 path 对应)。
 * 侧边栏菜单与按钮权限仍由后端菜单/权限驱动; 后端接口二次校验权限。
 */
const businessRoutes: RouteRecordRaw[] = [
  // 基础资料
  { path: '/basedata/customer', component: () => import('@/views/basedata/customer/index.vue'), meta: { title: '客户资料' } },
  { path: '/basedata/product', component: () => import('@/views/basedata/product/index.vue'), meta: { title: '产品资料' } },
  { path: '/basedata/bom', component: () => import('@/views/basedata/bom/index.vue'), meta: { title: '产品配件资料' } },
  { path: '/basedata/currency', component: () => import('@/views/basedata/currency/index.vue'), meta: { title: '汇率管理' } },
  // 销售
  { path: '/sales/standard-price', component: () => import('@/views/sales/standardPrice/index.vue'), meta: { title: '产品报价' } },
  { path: '/sales/contract-price', component: () => import('@/views/sales/contractPrice/index.vue'), meta: { title: '销售报价' } },
  { path: '/sales/pi', component: () => import('@/views/sales/pi/index.vue'), meta: { title: '销售合同PI' } },
  { path: '/sales/trace', component: () => import('@/views/trace/index.vue'), meta: { title: '单据追踪' } },
  // 订单
  { path: '/order/oem', component: () => import('@/views/order/oem/index.vue'), meta: { title: 'OEM要求' } },
  { path: '/order/po', component: () => import('@/views/order/po/index.vue'), meta: { title: 'PO采购订单' } },
  { path: '/order/shipping-instruction', component: () => import('@/views/order/shippingInstruction/index.vue'), meta: { title: '出货指示' } },
  // 出货
  { path: '/shipment/list', component: () => import('@/views/shipment/index.vue'), meta: { title: '出货单' } },
  // 财务
  { path: '/finance/entry', component: () => import('@/views/finance/entry/index.vue'), meta: { title: '收付款' } },
  { path: '/finance/receivable', component: () => import('@/views/finance/receivable/index.vue'), meta: { title: '应收账款' } },
  { path: '/finance/payable', component: () => import('@/views/finance/payable/index.vue'), meta: { title: '应付账款' } },
  // 报表
  { path: '/report/purchase', component: () => import('@/views/report/purchase/index.vue'), meta: { title: '采购报表' } },
  { path: '/report/sales', component: () => import('@/views/report/sales/index.vue'), meta: { title: '销售报表' } },
  { path: '/report/profit', component: () => import('@/views/report/profit/index.vue'), meta: { title: '财务利润报表' } },
  { path: '/report/salesman', component: () => import('@/views/report/salesman/index.vue'), meta: { title: '业务员业绩' } },
  // 库存
  { path: '/inventory/stock', component: () => import('@/views/inventory/stock/index.vue'), meta: { title: '库存查询' } },
  // 消息中心 / 资料共享
  { path: '/message', component: () => import('@/views/message/index.vue'), meta: { title: '消息中心' } },
  { path: '/fileshare', component: () => import('@/views/fileshare/index.vue'), meta: { title: '资料共享' } },
  // 系统管理
  { path: '/system/user', component: () => import('@/views/system/user/index.vue'), meta: { title: '用户管理' } },
  { path: '/system/role', component: () => import('@/views/system/role/index.vue'), meta: { title: '角色管理' } },
  { path: '/system/permission', component: () => import('@/views/system/permission/index.vue'), meta: { title: '权限菜单' } },
  { path: '/system/audit', component: () => import('@/views/system/audit/index.vue'), meta: { title: '审计日志' } }
]

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    name: 'Layout',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      },
      {
        path: 'notification',
        name: 'Notification',
        component: () => import('@/views/notification/index.vue'),
        meta: { title: '消息通知' }
      },
      ...businessRoutes
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const hasToken = !!userStore.token

  if (to.path === '/login') {
    return hasToken ? '/' : true
  }
  if (!hasToken) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 已登录但未加载用户信息/菜单 -> 加载(供侧边栏与按钮权限使用), 路由已静态注册无需重定向
  if (!userStore.loaded) {
    try {
      await userStore.loadUserInfo()
    } catch {
      userStore.reset()
      return '/login'
    }
  }
  return true
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) || ''
  document.title = title ? `${title} - XeFlow` : 'XeFlow'
})

export default router
