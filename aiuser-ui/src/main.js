import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import http from './utils/http'

Vue.config.productionTip = false

// 全局注册ElementUI
Vue.use(ElementUI)

// 将http实例挂载到Vue原型上，方便全局使用
Vue.prototype.$http = http

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')