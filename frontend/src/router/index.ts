import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { setUnauthorizedHandler } from '@/api/http'
import { Role } from '@/api/types'

/**
 * 路由表：分「用户端」与「管理后台」两区。
 * - 用户端挂 {@link UserLayout}，需登录；
 * - 管理后台挂 {@link AdminLayout}，需 role=2（meta.requireAdmin）；
 * - 登录/注册为公开页，已登录访问会被重定向到首页。
 *
 * 视图组件用动态 import 懒加载，按需分包。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    redirect: '/songs',
    children: [
      {
        path: 'songs',
        name: 'songs',
        component: () => import('@/views/user/SongsView.vue'),
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/user/ProfileView.vue'),
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { requireAdmin: true },
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
      },
    ],
  },
  // 兜底：未匹配路由回首页
  { path: '/:pathMatch(.*)*', redirect: '/songs' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 全局前置守卫：鉴权与角色门禁。
 * - 公开页（login/register）：已登录则重定向到首页，否则放行；
 * - 受保护页：未登录跳 /login（带 redirect 回跳）；
 * - requireAdmin 页：非管理员拒入并提示。
 */
router.beforeEach((to) => {
  const auth = useAuthStore()
  const isPublic = to.meta.public === true

  if (isPublic) {
    // 已登录用户不必再看登录/注册页
    return auth.isLoggedIn ? { path: '/songs' } : true
  }

  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requireAdmin && auth.user?.role !== Role.ADMIN) {
    ElMessage.error('需要管理员权限')
    return { path: '/songs' }
  }

  return true
})

/**
 * 注入 401 处理：http 拦截器遇 401 时清登录态并跳登录页。
 * 放此处是因为需要 router 实例；用 setTimeout 规避守卫期间的重复跳转。
 */
setUnauthorizedHandler(() => {
  const auth = useAuthStore()
  auth.clear()
  if (router.currentRoute.value.name !== 'login') {
    router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})

export default router
