import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '@/store'

Vue.use(VueRouter)

// 路由组件懒加载
const SubmitQuestion = () => import('@/views/SubmitQuestion')
const ConsultationResult = () => import('@/views/ConsultationResult')
const ConsultationHistory = () => import('@/views/ConsultationHistory')
const EvaluationPage = () => import('@/views/EvaluationPage')
const Login = () => import('@/views/Login')
const Register = () => import('@/views/Register')

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '用户登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { title: '用户注册' }
  },
  {
    path: '/',
    redirect: '/consultation/submit'
  },
  {
    path: '/consultation',
    component: {
      render: (c) => c('router-view')
    },
    children: [
      {
        path: 'submit',
        name: 'SubmitQuestion',
        component: SubmitQuestion,
        meta: { title: '提交法律咨询', requiresAuth: true }
      },
      {
        path: 'result',
        name: 'ConsultationResult',
        component: ConsultationResult,
        meta: { title: '咨询结果', requiresAuth: true }
      },
      {
        path: 'history',
        name: 'ConsultationHistory',
        component: ConsultationHistory,
        meta: { title: '咨询历史', requiresAuth: true }
      },
      {
        path: 'evaluation',
        name: 'EvaluationPage',
        component: EvaluationPage,
        meta: { title: '咨询评价', requiresAuth: true }
      }
    ]
  }
]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})

// 导航守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - AI律师用户端` : 'AI律师用户端'
  
  // 获取token
  const hasToken = store.getters.token
  
  if (to.path === '/login' || to.path === '/register') {
    if (hasToken) {
      // 如果已登录，重定向到首页
      next({ path: '/' })
    } else {
      next()
    }
  } else {
    if (hasToken) {
      // 检查是否需要验证用户信息
      if (store.getters.user && Object.keys(store.getters.user).length > 0) {
        next()
      } else {
        // 获取用户信息
        store.dispatch('getInfo').then(() => {
          next()
        }).catch(error => {
          // 获取用户信息失败，清除token并重定向到登录页
          store.dispatch('logout').then(() => {
            next({ path: `/login?redirect=${to.path}` })
          })
        })
      }
    } else {
      // 没有token，重定向到登录页
      if (to.meta.requiresAuth !== false) {
        next({ path: `/login?redirect=${to.path}` })
      } else {
        next()
      }
    }
  }
})

export default router