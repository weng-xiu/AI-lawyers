<template>
  <el-col
    :span="span"
    :offset="offset"
    :push="push"
    :pull="pull"
    :xs="xs"
    :sm="sm"
    :md="md"
    :lg="lg"
    :xl="xl"
  >
    <el-form-item
      :label="label"
      :prop="prop"
      :rules="rules"
      :label-width="labelWidth"
      :required="required"
      :error="error"
      :show-message="showMessage"
      :inline-message="inlineMessage"
      :size="size"
    >
      <template #label v-if="label">
        <span>{{ label }}</span>
        <el-tooltip v-if="labelTip" :content="labelTip" placement="top" effect="dark">
          <i class="el-icon-question pro-form-item__tip" />
        </el-tooltip>
      </template>
      <slot />
    </el-form-item>
  </el-col>
</template>

<script>
/**
 * V3 ProFormItem：ProForm 自由模式下的栅格表单项（el-col + el-form-item）。
 *
 * - 与 ProForm schema 渲染出的项保持同一套栅格/间距，保证混排对齐；
 * - 透传 el-col 响应式断点与 el-form-item 校验属性，el-form 的 provide/inject
 *   自动接入，prop+rules 照常参与整表 validate / resetFields / clearValidate。
 *
 * 用法：
 * <pre>
 * &lt;pro-form :model="form" ref="form" label-width="110px"&gt;
 *   &lt;pro-form-item label="通道" prop="configName" :span="12" :required="true"&gt;
 *     &lt;el-input v-model="form.configName" /&gt;
 *   &lt;/pro-form-item&gt;
 * &lt;/pro-form&gt;
 * </pre>
 */
export default {
  name: 'ProFormItem',
  props: {
    // el-col 栅格（24 栅格制，同 Element）
    span: { type: Number, default: 24 },
    offset: { type: Number, default: 0 },
    push: { type: Number, default: 0 },
    pull: { type: Number, default: 0 },
    xs: { type: [Number, Object], default: 24 },
    // 断点缺省必须是 undefined（不能是 null）：el-col 内部以 typeof === 'object'
    // 判断对象写法，null 会误入分支导致 Object.keys(null) 渲染报错
    sm: { type: [Number, Object], default: undefined },
    md: { type: [Number, Object], default: undefined },
    lg: { type: [Number, Object], default: undefined },
    xl: { type: [Number, Object], default: undefined },
    // el-form-item
    label: { type: String, default: '' },
    /** label 右侧悬浮提示文案（问号图标） */
    labelTip: { type: String, default: '' },
    prop: { type: String, default: '' },
    rules: { type: [Object, Array], default: null },
    labelWidth: { type: String, default: undefined },
    /** 手动标记必填（星号）；一般由 rules 里的 required 自动生成，无需传 */
    required: { type: Boolean, default: undefined },
    error: { type: String, default: '' },
    showMessage: { type: Boolean, default: true },
    inlineMessage: { type: Boolean, default: false },
    size: { type: String, default: undefined }
  }
}
</script>

<style lang="scss" scoped>
.pro-form-item__tip {
  margin-left: 4px;
  color: #c0c4cc;
  cursor: help;
  font-size: 14px;
  vertical-align: middle;
  &:hover { color: #409eff; }
}
</style>
