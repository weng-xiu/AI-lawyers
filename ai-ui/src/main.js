import Vue from 'vue'

import Cookies from 'js-cookie'

import Element from 'element-ui'
import './assets/styles/element-variables.scss'

import '@/assets/styles/index.scss' // global css
import '@/assets/styles/ruoyi.scss' // ruoyi css
import '@/assets/styles/dark-mode.scss' // V5 暗色/夜间值班模式（配色.md 第七部分）
import '@/assets/styles/care-mode.scss' // F2 坐席端关怀模式（字号/高对比，最后引入覆盖）
import '@/assets/styles/responsive.scss' // U6 管理端平板巡场响应式适配
import App from './App'
import store from './store'
import router from './router'
import directive from './directive' // directive
import plugins from './plugins' // plugins
import { download } from '@/utils/request'

import './assets/icons' // icon
import './permission' // permission control
import { getDicts } from "@/api/system/dict/data"
import { getConfigKey } from "@/api/system/config"
import { parseTime, resetForm, addDateRange, selectDictLabel, selectDictLabels, handleTree } from "@/utils/ruoyi"
// F2 坐席端关怀模式（三档字号/高对比，localStorage 工位级持久化）
import careMode from '@/utils/careMode'
// P9 前端可观测：错误 + Web Vitals 上报
import { installFrontendMonitor } from '@/utils/frontendMonitor'
// 分页组件
import Pagination from "@/components/Pagination"
// V2 三态组件：空态 / 骨架加载 / 错误重试
import TableEmpty from "@/components/TableEmpty"
import PageLoading from "@/components/PageLoading"
import ErrorRetry from "@/components/ErrorRetry"
// V3 ProTable：查询区 + 工具条 + 表格 + 分页 一体化封装
import ProTable from "@/components/ProTable"
import ProDialog from "@/components/ProDialog"
import ProForm from "@/components/ProForm"
import ProFormItem from "@/components/ProForm/ProFormItem"
// 自定义表格工具组件
import RightToolbar from "@/components/RightToolbar"
// 富文本组件
import Editor from "@/components/Editor"
// 文件上传组件
import FileUpload from "@/components/FileUpload"
// 图片上传组件
import ImageUpload from "@/components/ImageUpload"
// 图片预览组件
import ImagePreview from "@/components/ImagePreview"
// 系统用户选择组件（律师/服务人员统一来自 sys_user）
import UserSelect from "@/components/UserSelect"
// 字典标签组件
import DictTag from '@/components/DictTag'
// 字典数据组件
import DictData from '@/components/DictData'

// 全局方法挂载
Vue.prototype.getDicts = getDicts
Vue.prototype.getConfigKey = getConfigKey
Vue.prototype.parseTime = parseTime
Vue.prototype.resetForm = resetForm
Vue.prototype.addDateRange = addDateRange
Vue.prototype.selectDictLabel = selectDictLabel
Vue.prototype.selectDictLabels = selectDictLabels
Vue.prototype.download = download
Vue.prototype.handleTree = handleTree

// 全局组件挂载
Vue.component('DictTag', DictTag)
Vue.component('Pagination', Pagination)
Vue.component('TableEmpty', TableEmpty)
Vue.component('PageLoading', PageLoading)
Vue.component('ErrorRetry', ErrorRetry)
Vue.component('ProTable', ProTable)
Vue.component('ProDialog', ProDialog)
Vue.component('ProForm', ProForm)
Vue.component('ProFormItem', ProFormItem)
Vue.component('RightToolbar', RightToolbar)
Vue.component('Editor', Editor)
Vue.component('FileUpload', FileUpload)
Vue.component('ImageUpload', ImageUpload)
Vue.component('ImagePreview', ImagePreview)
Vue.component('UserSelect', UserSelect)

Vue.use(directive)
Vue.use(plugins)
Vue.use(careMode)
DictData.install()

// P9：安装全局错误捕获与 Web Vitals 采集（生产默认开启，开发需 VUE_APP_MONITOR_ENABLED=true）
installFrontendMonitor(Vue, router)

// V4：ECharts 司法蓝主题统一注册——所有图表 init 统一传 'judicial'（配色.md 数据可视化规范）
import * as echarts from 'echarts'
import judicialTheme from '@/assets/styles/echarts-theme'
echarts.registerTheme('judicial', judicialTheme)

/**
 * If you don't want to use mock-server
 * you want to use MockJs for mock api
 * you can execute: mockXHR()
 *
 * Currently MockJs will be used in the production environment,
 * please remove it before going online! ! !
 */

Vue.use(Element, {
  size: Cookies.get('size') || 'medium' // set element-ui default size
})

Vue.config.productionTip = false

new Vue({
  el: '#app',
  router,
  store,
  render: h => h(App)
})
