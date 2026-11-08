// src/router/index.js  —— Vue Router 4（若用 React Router，路径与守卫逻辑可直接照搬）
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',                       // 首页：与本地大模型对话
    component: () => import('@/views/Home.vue'),
    meta: { requiresAuth: true, title: '首页 - 学涯规划助手' },
  },
  {
    path: '/chat/:sessionId',           // 打开某个历史会话（与首页同一组件，按 sessionId 加载历史）
    name: 'ChatSession',
    component: () => import('@/views/Home.vue'),
    meta: { requiresAuth: true, title: '对话 - 学涯规划助手' },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { guestOnly: true, title: '登录' },
  },
  {
    path: '/register',                  // 手机号 + 密码 + 验证码
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { guestOnly: true, title: '注册' },
  },
  {
    path: '/assessment',                // 注册后的测评页
    name: 'Assessment',
    component: () => import('@/views/Assessment.vue'),
    meta: { requiresAuth: true, title: '个人测评' },
  },
  {
    path: '/profile',                   // 个人画像页（含对话提取的建议）
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { requiresAuth: true, title: '我的画像' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  document.title = to.meta.title || '学涯规划助手'
  if (to.meta.requiresAuth && !token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && token) {
    return { name: 'Home' }
  }
})

export default router
