<template>
  <el-form
    ref="elForm"
    :model="model"
    :rules="rules"
    :inline="inline"
    :label-width="labelWidth"
    :label-position="labelPosition"
    :size="size"
    :disabled="disabled"
    :status-icon="statusIcon"
    :validate-on-rule-change="validateOnRuleChange"
    :hide-required-asterisk="hideRequiredAsterisk"
    @submit.native.prevent
  >
    <div v-loading="loading" class="pro-form__body">
      <!-- 栅格模式：schema 字段 + 默认插槽（自定义 ProFormItem 可混入同一行） -->
      <el-row v-if="grid && !inline" :gutter="gutter">
        <template v-for="item in fields">
          <pro-form-item
            v-if="isItemVisible(item)"
            :key="item.prop"
            :label="item.label"
            :prop="item.prop"
            :rules="fieldRulesMap[item.prop]"
            :span="item.span || defaultSpan"
            :xs="item.xs || 24"
            :sm="item.sm"
            :md="item.md"
            :lg="item.lg"
            :xl="item.xl"
          >
            <!-- 字段自定义插槽：#form-{prop}="{ item, model }" -->
            <slot
              :name="'form-' + item.prop"
              :item="item"
              :model="model"
            >
              <!-- 内置控件：覆盖录入表单 90% 场景，复杂控件走插槽 -->
              <el-input
                v-if="item.type === 'input' || !item.type"
                v-model="model[item.prop]"
                :type="item.inputType"
                :placeholder="item.placeholder || ('请输入' + item.label)"
                :maxlength="item.max"
                :show-word-limit="item.showWordLimit || !!item.max"
                :clearable="item.clearable"
                :disabled="resolveDisabled(item)"
                :readonly="resolveReadonly(item)"
                :style="controlStyle(item)"
                v-bind="item.attrs"
                @input="val => onFieldChange(item, val)"
                @keyup.enter.native="handleEnter"
              />
              <el-input
                v-else-if="item.type === 'textarea'"
                v-model="model[item.prop]"
                type="textarea"
                :rows="item.rows || 3"
                :autosize="item.autosize"
                :placeholder="item.placeholder || ('请输入' + item.label)"
                :maxlength="item.max"
                :show-word-limit="item.showWordLimit || !!item.max"
                :disabled="resolveDisabled(item)"
                :readonly="resolveReadonly(item)"
                v-bind="item.attrs"
                @input="val => onFieldChange(item, val)"
              />
              <el-input
                v-else-if="item.type === 'password'"
                v-model="model[item.prop]"
                type="password"
                show-password
                :placeholder="item.placeholder || ('请输入' + item.label)"
                :maxlength="item.max"
                :clearable="item.clearable"
                :disabled="resolveDisabled(item)"
                :readonly="resolveReadonly(item)"
                :style="controlStyle(item)"
                v-bind="item.attrs"
                @keyup.enter.native="handleEnter"
              />
              <el-input-number
                v-else-if="item.type === 'number'"
                v-model="model[item.prop]"
                :placeholder="item.placeholder"
                :min="item.min"
                :max="item.max"
                :step="item.step"
                :precision="item.precision"
                :disabled="resolveDisabled(item)"
                controls-position="right"
                :style="controlStyle(item)"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
                @keyup.enter.native="handleEnter"
              />
              <el-select
                v-else-if="item.type === 'select'"
                v-model="model[item.prop]"
                :placeholder="item.placeholder || ('请选择' + item.label)"
                :multiple="item.multiple"
                :filterable="item.filterable"
                :clearable="item.clearable !== false"
                :disabled="resolveDisabled(item)"
                :collapse-tags="item.collapseTags !== false"
                :loading="loaderState[item.prop] === 'loading'"
                :style="controlStyle(item, true)"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              >
                <el-option
                  v-for="opt in getFieldOptions(item)"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                  :disabled="opt.disabled"
                />
              </el-select>
              <el-radio-group
                v-else-if="item.type === 'radio'"
                v-model="model[item.prop]"
                :disabled="resolveDisabled(item)"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              >
                <el-radio
                  v-for="opt in getFieldOptions(item)"
                  :key="opt.value"
                  :label="opt.value"
                  :disabled="opt.disabled"
                >{{ opt.label }}</el-radio>
              </el-radio-group>
              <el-radio-group
                v-else-if="item.type === 'radio-button'"
                v-model="model[item.prop]"
                :disabled="resolveDisabled(item)"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              >
                <el-radio-button
                  v-for="opt in getFieldOptions(item)"
                  :key="opt.value"
                  :label="opt.value"
                  :disabled="opt.disabled"
                >{{ opt.label }}</el-radio-button>
              </el-radio-group>
              <el-checkbox-group
                v-else-if="item.type === 'checkbox'"
                v-model="model[item.prop]"
                :disabled="resolveDisabled(item)"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              >
                <el-checkbox
                  v-for="opt in getFieldOptions(item)"
                  :key="opt.value"
                  :label="opt.value"
                  :disabled="opt.disabled"
                >{{ opt.label }}</el-checkbox>
              </el-checkbox-group>
              <el-switch
                v-else-if="item.type === 'switch'"
                v-model="model[item.prop]"
                :active-value="item.activeValue"
                :inactive-value="item.inactiveValue"
                :active-text="item.activeText"
                :inactive-text="item.inactiveText"
                :disabled="resolveDisabled(item)"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              />
              <el-date-picker
                v-else-if="DATE_TYPES.indexOf(item.type) !== -1"
                v-model="model[item.prop]"
                :type="item.type"
                :value-format="item.valueFormat || DEFAULT_DATE_FORMAT[item.type]"
                :range-separator="item.rangeSeparator || '-'"
                :start-placeholder="item.startPlaceholder || '开始日期'"
                :end-placeholder="item.endPlaceholder || '结束日期'"
                :placeholder="item.type === 'month' ? '选择月份' : (item.placeholder || ('请选择' + item.label))"
                :disabled="resolveDisabled(item)"
                :style="controlStyle(item, true)"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              />
              <!-- 字段旁提示（仅内置控件渲染；字段插槽场景请在插槽内自行放置） -->
              <span v-if="item.tip" class="pro-form__tip">{{ item.tip }}</span>
              <!-- 未识别类型：兜底插槽为空时给出明确告警，避免静默漏渲染 -->
              <span v-else-if="!isKnownType(item.type)" class="pro-form__unsupported">
                不支持的字段类型「{{ item.type }}」，请使用 #form-{{ item.prop }} 插槽
              </span>
            </slot>
          </pro-form-item>
        </template>

        <!-- 自由栅格项：页面用 <pro-form-item> 自绘控件，与 schema 项共用同一行 -->
        <slot />
      </el-row>

      <!-- 非栅格（inline 或 grid=false）：原样承载，el-form 能力不变 -->
      <slot v-else />

      <!-- 独立页表单底部（弹窗表单请关闭此区，由 ProDialog 提供 footer） -->
      <div v-if="showFooter" class="pro-form__footer">
        <el-button
          type="primary"
          :size="footerSize"
          :loading="submitLoading"
          :disabled="submitDisabled"
          @click="handleSubmit"
        >{{ submitText }}</el-button>
        <el-button
          v-if="showReset"
          :size="footerSize"
          :disabled="submitLoading"
          @click="handleReset"
        >{{ resetText }}</el-button>
        <el-button
          v-if="showCancel"
          :size="footerSize"
          :disabled="submitLoading"
          @click="$emit('cancel')"
        >{{ cancelText }}</el-button>
      </div>
    </div>
  </el-form>
</template>

<script>
import store from '@/store'
import { getDicts } from '@/api/system/dict/data'
import ProFormItem from './ProFormItem.vue'

/** 支持的日期类控件 type */
const DATE_TYPES = ['date', 'datetime', 'daterange', 'datetimerange', 'month']
const DEFAULT_DATE_FORMAT = {
  date: 'yyyy-MM-dd',
  datetime: 'yyyy-MM-dd HH:mm:ss',
  daterange: 'yyyy-MM-dd',
  datetimerange: 'yyyy-MM-dd HH:mm:ss',
  month: 'yyyy-MM'
}
/** 选择类控件（必填提示与触发器随此分类） */
const OPTION_TYPES = ['select', 'radio', 'radio-button']
const SELECT_LIKE = OPTION_TYPES.concat(['switch', 'checkbox']).concat(DATE_TYPES)
const KNOWN_TYPES = ['input', 'textarea', 'password', 'number', 'switch']
  .concat(OPTION_TYPES).concat(['checkbox']).concat(DATE_TYPES)

/** 字典在途请求去重（多个 ProForm 实例/快速联动时避免重复 getDicts） */
const dictInflight = {}

/**
 * V3 ProForm：录入/编辑表单（栅格 + 校验 + 提交 loading）。
 *
 * <p>与 ProTable 同一设计哲学：schema 驱动为主、字段级插槽逃生、自由模式兜底。
 * 弹窗内使用时关闭 showFooter，由 ProDialog 的确定按钮调 this.$refs.form.validate()；
 * 独立页面使用时开启 showFooter 获得统一的提交/重置/取消按钮。</p>
 *
 * schema 字段定义：
 * <pre>
 * {
 *   prop: 'phoneNumber', label: '电话号码',
 *   type: 'input|textarea|password|number|select|radio|radio-button|checkbox|switch|date|datetime|daterange|datetimerange|month',
 *   span: 12, xs: 24, sm: 12, md: 12, lg: 8,  // 24 栅格，同 el-col
 *   required: true,                            // 自动生成必填规则与星号
 *   message: '电话号码不能为空',                 // 覆盖必填默认提示
 *   max: 20, rows: 3, autosize: {min:2,max:6}, // 输入长度/文本域
 *   width: 200,                                // 控件宽度（数字按 px，也可传 '60%'；select/date 默认 100%）
 *   // 选项三选一：静态数组 / RuoYi 字典 / 远程加载
 *   options: [{ label, value, disabled }],
 *   dict: 'ai_model_type',                     // RuoYi 字典 type，自动走 Vuex 字典缓存
 *   optionsLoader: (model) => listApi(),       // 返回 Promise<数组|{rows}|{data}>
 *   loadOn: 'mounted'|'visible',               // 远程选项加载时机，默认 mounted
 *   multiple/filterable/clearable/disabled,    // 控件常用开关
 *   disabled: m => m.id != null,               // disabled/visible/readonly 均可传函数做联动
 *   visible: m => m.type === 'X',
 *   activeValue/inactiveValue/activeText,      // switch
 *   min/max/step/precision,                    // number
 *   placeholder/startPlaceholder/endPlaceholder/tip,
 *   pattern: /^1\d{10}$/, patternMessage: '手机号格式不正确',
 *   rules: [],                                // 额外 async-validator 规则
 *   trigger: 'blur',                          // 覆盖必填规则默认触发器
 *   default: '',                              // getDefaultModel() 使用
 *   attrs: {}                                 // 其余原生/组件属性透传
 * }
 * </pre>
 *
 * 事件：submit(model) / reset(model) / cancel / enter（文本框回车，弹窗页可接 @enter="submitForm"）
 *       / field-change(prop, value, item)
 *
 * 对外方法（经 ref 调用）：
 * validate / validateField / resetFields / clearValidate / scrollToField / getDefaultModel
 */
export default {
  name: 'ProForm',
  components: { ProFormItem },
  props: {
    /** 表单数据对象（v-model 直改字段） */
    model: { type: Object, required: true },
    /** 表单级校验规则（自由模式/非 schema 字段使用；schema 字段规则自动挂在 item 上） */
    rules: { type: Object, default: () => ({}) },
    /** schema 字段定义（支持 computed 数组，联动场景直接重建） */
    fields: { type: Array, default: () => [] },
    labelWidth: { type: String, default: '100px' },
    labelPosition: { type: String, default: undefined },
    size: { type: String, default: 'small' },
    inline: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    statusIcon: { type: Boolean, default: false },
    /** 关闭默认的「规则变更即校验」，避免编辑回显时整表报红 */
    validateOnRuleChange: { type: Boolean, default: false },
    hideRequiredAsterisk: { type: Boolean, default: false },
    /** 栅格开关（false 时不渲染 el-row/el-col，适合纯行内表单） */
    grid: { type: Boolean, default: true },
    gutter: { type: Number, default: 20 },
    /** 字段默认栅格占比（弹窗窄表单默认整行，双列表单传 12） */
    defaultSpan: { type: Number, default: 24 },
    /** 数据加载遮罩（编辑回显等异步场景） */
    loading: { type: Boolean, default: false },
    /** 校验失败时自动滚动到第一个错误字段（Element 2.15 无内置能力，ProForm 自行实现） */
    scrollToError: { type: Boolean, default: true },
    // 底部按钮（独立页表单使用）
    showFooter: { type: Boolean, default: false },
    footerSize: { type: String, default: 'small' },
    submitText: { type: String, default: '确 定' },
    resetText: { type: String, default: '重 置' },
    cancelText: { type: String, default: '取 消' },
    showReset: { type: Boolean, default: false },
    showCancel: { type: Boolean, default: false },
    submitLoading: { type: Boolean, default: false },
    submitDisabled: { type: Boolean, default: false }
  },
  data() {
    return {
      /** optionsLoader 远程选项结果：{ [prop]: array } */
      loaderOptions: {},
      /** optionsLoader 状态：{ [prop]: 'loading'|'done'|'error' } */
      loaderState: {}
    }
  },
  computed: {
    /** 访问 Vuex 字典表（响应式：字典异步拉取后自动驱动选项重渲染） */
    dictMap() {
      return store.getters.dict || {}
    },
    /** 字段规则映射：随 fields 重建一次，避免每次 render 重新生成数组引用 */
    fieldRulesMap() {
      const map = {}
      this.fields.forEach(item => {
        const rules = this.buildItemRules(item)
        if (rules) map[item.prop] = rules
      })
      return map
    }
  },
  watch: {
    fields: {
      immediate: true,
      handler() {
        this.ensureDicts()
        this.ensureLoaders('mounted')
      }
    }
  },
  created() {
    this.DATE_TYPES = DATE_TYPES
    this.DEFAULT_DATE_FORMAT = DEFAULT_DATE_FORMAT
  },
  methods: {
    // ---- 字段状态：布尔值或 (model) => boolean 联动函数 ----
    isItemVisible(item) {
      if (typeof item.visible === 'function') return item.visible(this.model)
      return item.visible !== false
    },
    resolveDisabled(item) {
      return typeof item.disabled === 'function' ? !!item.disabled(this.model) : !!item.disabled
    },
    resolveReadonly(item) {
      return typeof item.readonly === 'function' ? !!item.readonly(this.model) : !!item.readonly
    },
    isKnownType(type) {
      return !type || KNOWN_TYPES.indexOf(type) !== -1
    },
    /** 控件宽度：item.width 数字按 px；select/date 默认撑满列宽 */
    controlStyle(item, fullByDefault) {
      const style = {}
      if (item.width) style.width = typeof item.width === 'number' ? item.width + 'px' : item.width
      else if (fullByDefault) style.width = '100%'
      return Object.keys(style).length ? style : null
    },

    // ---- 选项：静态 options > RuoYi 字典 > optionsLoader ----
    getFieldOptions(item) {
      if (Array.isArray(item.options)) return item.options
      if (item.dict) {
        const dictList = this.dictMap[item.dict]
        if (Array.isArray(dictList)) {
          // RuoYi 字典项为 {dictLabel, dictValue, ...}，统一映射成 {label, value}
          return dictList.map(d => ({ label: d.dictLabel, value: d.dictValue, listClass: d.listClass, raw: d }))
        }
        return []
      }
      if (item.optionsLoader) return this.loaderOptions[item.prop] || []
      return []
    },
    /** 收集 schema 声明的字典 type，未命中 Vuex 缓存的走 getDicts 并回填缓存 */
    ensureDicts() {
      const types = Array.from(new Set(
        this.fields.filter(f => f.dict).map(f => f.dict)
      ))
      types.forEach(type => {
        if (this.dictMap[type] || dictInflight[type]) return
        dictInflight[type] = getDicts(type).then(res => {
          store.dispatch('dict/setDict', { key: type, value: res.data })
        }).catch(() => {}).then(() => {
          delete dictInflight[type]
        })
      })
    },
    /** 加载远程选项；trigger: mounted 立即加载，visible 字段首次可见时加载 */
    ensureLoaders(trigger) {
      this.fields.forEach(item => {
        if (!item.optionsLoader) return
        const when = item.loadOn || 'mounted'
        if (when !== trigger) return
        if (this.loaderState[item.prop]) return
        if (trigger === 'visible' && !this.isItemVisible(item)) return
        this.$set(this.loaderState, item.prop, 'loading')
        Promise.resolve(item.optionsLoader(this.model)).then(res => {
          // 兼容 数组 / {rows} 分页响应 / {data} 三种返回形态
          const list = Array.isArray(res) ? res : (res.rows || res.data || [])
          this.$set(this.loaderOptions, item.prop, list.map(this.normalizeOption))
          this.$set(this.loaderState, item.prop, 'done')
        }).catch(() => {
          this.$set(this.loaderState, item.prop, 'error')
        })
      })
    },
    normalizeOption(opt) {
      // 已是 {label,value} 形态直接透传；实体对象（如机构/坐席）常用 name/id
      if (opt.label !== undefined && opt.value !== undefined) return opt
      return {
        label: opt.label != null ? opt.label : (opt.name || opt.agentName || opt.orgName),
        value: opt.value != null ? opt.value : (opt.id != null ? opt.id : (opt.agentId || opt.orgId)),
        raw: opt
      }
    },

    /** 组装字段级规则：必填自动规则 + 正则 + 页面补充规则 */
    buildItemRules(item) {
      const rules = []
      if (item.required) {
        rules.push({
          required: true,
          message: item.message || ((SELECT_LIKE.indexOf(item.type) !== -1 ? '请选择' : '请输入') + item.label),
          trigger: item.trigger || (SELECT_LIKE.indexOf(item.type) !== -1 ? 'change' : 'blur')
        })
      }
      if (item.pattern) {
        rules.push({
          pattern: item.pattern,
          message: item.patternMessage || item.label + '格式不正确',
          trigger: item.trigger || 'blur'
        })
      }
      if (Array.isArray(item.rules) && item.rules.length) {
        rules.push(...item.rules)
      }
      return rules.length ? rules : undefined
    },
    onFieldChange(item, value) {
      // visible 型 loader：字段首次因联动变为可见时补加载
      if (item.optionsLoader && (item.loadOn === 'visible') && !this.loaderState[item.prop] && this.isItemVisible(item)) {
        this.ensureLoaders('visible')
      }
      this.$emit('field-change', item.prop, value, item)
    },
    /** 文本框回车：独立页表单直接提交；弹窗表单抛 enter 由页面接 submitForm */
    handleEnter() {
      if (this.showFooter) {
        this.handleSubmit()
      } else {
        this.$emit('enter')
      }
    },
    /** 底部提交按钮：校验通过后抛 submit（弹窗表单由 ProDialog 直接调 validate） */
    handleSubmit() {
      if (this.submitLoading || this.submitDisabled) return
      this.validate().then(() => {
        this.$emit('submit', this.model)
      }).catch(() => {})
    },
    handleReset() {
      this.resetFields()
      this.$emit('reset', this.model)
    },
    /**
     * 按 schema 生成默认数据对象（供页面 reset() 收敛样板）：
     * default 优先；多选/checkbox=[]；switch=inactiveValue；number=undefined；
     * 单选/radio=null；其余输入类=''。不覆盖页面已有的额外字段。
     */
    getDefaultModel() {
      const obj = {}
      this.fields.forEach(item => {
        if (item.default !== undefined) {
          obj[item.prop] = typeof item.default === 'object'
            ? JSON.parse(JSON.stringify(item.default))
            : item.default
        } else if (item.multiple || item.type === 'checkbox') {
          obj[item.prop] = []
        } else if (item.type === 'switch') {
          obj[item.prop] = item.inactiveValue !== undefined ? item.inactiveValue : false
        } else if (item.type === 'number') {
          obj[item.prop] = undefined
        } else if (item.type === 'radio' || item.type === 'radio-button' ||
          (item.type === 'select' && !item.multiple) || DATE_TYPES.indexOf(item.type) !== -1) {
          obj[item.prop] = null
        } else {
          obj[item.prop] = ''
        }
      })
      return obj
    },
    // ---- el-form 方法代理 ----
    validate(callback) {
      const form = this.$refs.elForm
      if (typeof callback === 'function') {
        return form.validate(callback)
      }
      // Element 2.15 validate 无 callback 返回 Promise；统一以 callback 形式拿到 invalidFields
      return new Promise((resolve, reject) => {
        form.validate((valid, invalidFields) => {
          if (valid) {
            resolve(true)
          } else {
            if (this.scrollToError) {
              const firstProp = invalidFields && Object.keys(invalidFields)[0]
              if (firstProp) this.scrollToField(firstProp)
            }
            reject(new Error('validation failed'))
          }
        })
      })
    },
    validateField(props, cb) {
      return this.$refs.elForm.validateField(props, cb)
    },
    resetFields() {
      return this.$refs.elForm.resetFields()
    },
    clearValidate(props) {
      return this.$refs.elForm.clearValidate(props)
    },
    /**
     * 滚动到指定字段。Element 2.15 的 el-form 未提供此方法，
     * 通过内部 fields 注册列表（el-form-item 实例）定位 $el 后滚动。
     */
    scrollToField(prop) {
      const field = (this.$refs.elForm.fields || []).find(f => f.prop === prop)
      if (field && field.$el && field.$el.scrollIntoView) {
        field.$el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.pro-form__body {
  width: 100%;
}

.pro-form__footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

// 字段旁灰色提示（数字阈值/计费单位等说明），与控件同排
.pro-form__tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
  line-height: 32px;
  white-space: nowrap;
}

.pro-form__unsupported {
  color: #f56c6c;
  font-size: 12px;
}
</style>
