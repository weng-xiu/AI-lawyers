import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
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