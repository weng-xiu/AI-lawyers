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
            :rules="buildItemRules(item)"
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
                :disabled="item.disabled"
                v-bind="item.attrs"
                @input="val => onFieldChange(item, val)"
              />
              <el-input
                v-else-if="item.type === 'textarea'"
                v-model="model[item.prop]"
                type="textarea"
                :rows="item.rows || 3"
                :placeholder="item.placeholder || ('请输入' + item.label)"
                :maxlength="item.max"
                :show-word-limit="item.showWordLimit || !!item.max"
                :disabled="item.disabled"
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
                :disabled="item.disabled"
                v-bind="item.attrs"
              />
              <el-input-number
                v-else-if="item.type === 'number'"
                v-model="model[item.prop]"
                :placeholder="item.placeholder"
                :min="item.min"
                :max="item.max"
                :step="item.step"
                :precision="item.precision"
                :disabled="item.disabled"
                controls-position="right"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              />
              <el-select
                v-else-if="item.type === 'select'"
                v-model="model[item.prop]"
                :placeholder="item.placeholder || ('请选择' + item.label)"
                :multiple="item.multiple"
                :filterable="item.filterable"
                :clearable="item.clearable !== false"
                :disabled="item.disabled"
                :collapse-tags="item.collapseTags !== false"
                style="width: 100%"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              >
                <el-option
                  v-for="opt in item.options || []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                  :disabled="opt.disabled"
                />
              </el-select>
              <el-radio-group
                v-else-if="item.type === 'radio'"
                v-model="model[item.prop]"
                :disabled="item.disabled"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              >
                <el-radio
                  v-for="opt in item.options || []"
                  :key="opt.value"
                  :label="opt.value"
                >{{ opt.label }}</el-radio>
              </el-radio-group>
              <el-radio-group
                v-else-if="item.type === 'radio-button'"
                v-model="model[item.prop]"
                :disabled="item.disabled"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              >
                <el-radio-button
                  v-for="opt in item.options || []"
                  :key="opt.value"
                  :label="opt.value"
                >{{ opt.label }}</el-radio-button>
              </el-radio-group>
              <el-checkbox-group
                v-else-if="item.type === 'checkbox'"
                v-model="model[item.prop]"
                :disabled="item.disabled"
                v-bind="item.attrs"
                @change="val => onFieldChange(item, val)"
              >
                <el-checkbox
                  v-for="opt in item.options || []"
                  :key="opt.value"
                  :label="opt.value"
                >{{ opt.label }}</el-checkbox>
              </el-checkbox-group>
              <el-switch
                v-else-if="item.type === 'switch'"
                v-model="model[item.prop]"
                :active-value="item.activeValue"
                :inactive-value="item.inactiveValue"
                :active-text="item.activeText"
                :inactive-text="item.inactiveText"
                :disabled="item.disabled"
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
                :disabled="item.disabled"
                style="width: 100%"
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
/** 选择类控件的必填提示与校验触发器 */
const SELECT_LIKE = ['select', 'radio', 'radio-button', 'switch', 'date', 'datetime', 'daterange', 'datetimerange', 'month']

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
 *   max: 20, rows: 3,                         // 输入长度/文本域行数（max 自动开字数统计）
 *   options: [{ label, value, disabled }],    // select/radio/checkbox
 *   multiple/filterable/clearable/disabled,   // 控件常用开关
 *   activeValue/inactiveValue/activeText,     // switch
 *   min/max/step/precision,                   // number
 *   placeholder/startPlaceholder/endPlaceholder,
 *   pattern: /^1\d{10}$/, patternMessage: '手机号格式不正确',
 *   rules: [],                                // 额外 async-validator 规则
 *   trigger: 'blur',                          // 覆盖必填规则默认触发器
 *   attrs: {}                                 // 其余原生/组件属性透传
 * }
 * </pre>
 *
 * 对外方法（经 ref 调用，与 el-form API 一致）：
 * validate / validateField / resetFields / clearValidate / scrollToField
 */
export default {
  name: 'ProForm',
  components: { ProFormItem },
  props: {
    /** 表单数据对象（v-model 直改字段） */
    model: { type: Object, required: true },
    /** 表单级校验规则（自由模式/非 schema 字段使用；schema 字段规则自动挂在 item 上） */
    rules: { type: Object, default: () => ({}) },
    /** schema 字段定义 */
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
  created() {
    this.DATE_TYPES = DATE_TYPES
    this.DEFAULT_DATE_FORMAT = DEFAULT_DATE_FORMAT
  },
  methods: {
    /** 字段可见性：支持 visible:false 或 visible(model) 条件函数（联动显隐） */
    isItemVisible(item) {
      if (typeof item.visible === 'function') return item.visible(this.model)
      return item.visible !== false
    },
    isKnownType(type) {
      return !type || ['input', 'textarea', 'password', 'number', 'select', 'radio', 'radio-button', 'checkbox', 'switch']
        .concat(DATE_TYPES).indexOf(type) !== -1
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
      this.$emit('field-change', item.prop, value, item)
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
    // ---- el-form 方法代理（保持 this.$refs.form.xxx 调用习惯） ----
    validate(callback) {
      // 兼容 callback 与 Promise 两种用法
      const form = this.$refs.elForm
      if (typeof callback === 'function') {
        return form.validate(callback)
      }
      // Element 2.x validate 无 callback 时返回 Promise（部分小版本不返回，统一兜底）
      return new Promise((resolve, reject) => {
        form.validate(valid => (valid ? resolve(valid) : reject(new Error('validation failed'))))
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
    scrollToField(prop) {
      return this.$refs.elForm.scrollToField(prop)
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

// 字段旁灰色提示（数字阈值/秒数等说明），与控件同排
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
