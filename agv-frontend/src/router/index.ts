import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/views/LayoutView.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '概览' }
        },
        {
          path: 'roadnet',
          name: 'roadnet',
          component: () => import('@/views/RoadNetView.vue'),
          meta: { title: '车间地图', subtitle: '路网编辑' }
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('@/views/TaskSetView.vue'),
          meta: { title: '任务集' }
        },
        {
          path: 'simulation',
          name: 'simulation',
          component: () => import('@/views/SimulationView.vue'),
          meta: { title: '仿真运行' }
        },
        {
          path: 'experiments',
          name: 'experiments',
          component: () => import('@/views/ExperimentView.vue'),
          meta: { title: '对比实验' }
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/views/admin/UserManageView.vue'),
          meta: { title: '用户管理', roles: ['admin'] }
        },
        {
          path: 'admin/dict',
          name: 'admin-dict',
          component: () => import('@/views/admin/DictManageView.vue'),
          meta: { title: '数字字典', roles: ['admin'] }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.token) {
    return { name: 'login' }
  }
  if (to.meta.public && auth.token && to.name === 'login') {
    return { name: 'dashboard' }
  }
  const roles = to.meta.roles as string[] | undefined
  if (roles && !roles.includes(auth.role)) {
    return { name: 'dashboard' }
  }
})

/** 单页应用路由切换后同步浏览器标签页标题，便于多标签区分与读屏播报。 */
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 排策智演` : '排策智演'
})

export default router
