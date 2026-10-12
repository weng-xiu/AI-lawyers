import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementUI from 'element-ui'
// V1 品牌色统一：以司法蓝 #1A3C6E 重建 Element 主题（含 Design Token 变量），替换默认蓝 #409eff
import './assets/styles/element-theme.scss'
import http from './utils/http'
import careMode from './utils/careMode'

Vue.config.productionTip = false

// 全局注册ElementUI
Vue.use(ElementUI)

// F2 适老化关怀模式（三档字号/高对比，localStorage 持久化 + 登录后同步服务端）
Vue.use(careMode)

// 将http实例挂载到Vue原型上，方便全局使用
Vue.prototype.$http = http

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')