<template>
  <el-dialog
    :title="title"
    :visible="visible"
    :width="width"
    :top="top"
    :append-to-body="appendToBody"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :show-close="showClose"
    :before-close="handleBeforeClose"
    :custom-class="['pro-dialog', customClass].join(' ')"
    :destroy-on-close="destroyOnClose"
    @update:visible="handleVisibleChange"
    @open="onOpen"
    @opened="onOpened"
    @closed="onClosed"
  >
    <slot name="title" slot="title" />
    <slot />
    <div v-if="showFooter && !$slots.footer" slot="footer" class="pro-dialog__footer">
      <el-button
        :type="cancelType"
        :size="footerSize"
        :disabled="confirmLoading"
        @click="handleCancel"
      >{{ cancelText }}</el-button>
      <el-button
        :type="confirmType"
        :size="footerSize"
        :loading="confirmLoading"
        :disabled="confirmDisabled"
        @click="handleConfirm"
      >{{ confirmText }}</el-button>
    </div>
    <!-- 允许页面完全自定义底部（如多按钮/复选确认区） -->
    <div v-if="$slots.footer" slot="footer">
      <slot name="footer" />
    </div>
  </el-dialog>
</template>

<script>
/**
 * V3：ProDialog 通用弹窗
 *
 * 统一全站新增/编辑/查看弹窗的外壳与交互基线：
 *  1. 默认禁止点击遮罩关闭（close-on-click-modal=false），防填写中误触丢失；
 *  2. 统一底部「取消 / 确定」按钮（顺序、尺寸、loading 语义一致）；
 *  3. 提交态只锁取消按钮与关闭入口，确认按钮走 loading（confirmLoading 由页面控制，
 *     请求失败弹窗保留、成功后由页面将 visible 置 false，与既有 open 模式一致）；
 *  4. dirty 脏改动拦截：表单有未保存修改时，关闭/取消/ESC/遮罩统一二次确认，
 *     可与 U4 createDraftMixin 协同（草稿页传 :dirty 即可，不需要 localStorage）；
 *     X/ESC 路径走 Element 官方 before-close，不调 done 时框架不会清理遮罩，
 *     不会出现拒绝关闭后遮罩计数错乱；
 *  5. append-to-body 默认开启，避免多层 el-card overflow 裁剪。
 *
 * 用法：
 *   <pro-dialog
 *     v-model="open"
 *     title="新增名单"
 *     width="560px"
 *     :confirm-loading="submitting"
 *     :dirty="formDirty"
 *     @confirm="submitForm"
 *     @closed="reset">
 *     <el-form>...</el-form>
 *   </pro-dialog>
 *
 * 兼容：:visible.sync="open" 与 v-model="open" 均可（同一 visible prop）。
 */
export default {
  name: 'ProDialog',
  model: {
    prop: 'visible',
    event: 'input'
  },
  props: {
    /** 显隐，支持 v-model 或 :visible.sync */
    visible: {
      type: Boolean,
      default: false
    },
    title: {
      type: String,
      default: ''
    },
    width: {
      type: String,
      default: '600px'
    },
    top: {
      type: String,
      default: undefined
    },
    appendToBody: {
      type: Boolean,
      default: true
    },
    /** 点击遮罩是否关闭，默认否（防误触丢失填写内容） */
    closeOnClickModal: {
      type: Boolean,
      default: false
    },
    /** ESC 是否关闭（开启后同样受 dirty 拦截保护） */
    closeOnPressEscape: {
      type: Boolean,
      default: true
    },
    showClose: {
      type: Boolean,
      default: true
    },
    destroyOnClose: {
      type: Boolean,
      default: false
    },
    customClass: {
      type: String,
      default: ''
    },
    // 底部按钮
    showFooter: {
      type: Boolean,
      default: true
    },
    confirmText: {
      type: String,
      default: '确 定'
    },
    cancelText: {
      type: String,
      default: '取 消'
    },
    confirmType: {
      type: String,
      default: 'primary'
    },
    cancelType: {
      type: String,
      default: ''
    },
    footerSize: {
      type: String,
      default: 'small'
    },
    confirmLoading: {
      type: Boolean,
      default: false
    },
    confirmDisabled: {
      type: Boolean,
      default: false
    },
    /**
     * 脏改动标记：为 true 时任何关闭路径（X / 遮罩 / ESC / 取消）都先二次确认。
     * 可直接绑表单脏检查结果；提交成功后由页面复位。
     */
    dirty: {
      type: Boolean,
      default: false
    },
    /** 脏改动关闭确认文案 */
    dirtyTip: {
      type: String,
      default: '当前内容尚未保存，确认关闭吗？'
    },
    /**
     * 自动校验：点击确定时自动调用内部注册的 ProForm.validate()，
     * 校验通过才 emit confirm；校验失败自动 scrollToError + focusField（ProForm 内置）。
     * 页面有自定义校验流程时设 false。
     */
    autoValidate: {
      type: Boolean,
      default: true
    },
    /** 自动聚焦：弹窗打开动画完成后自动聚焦内部 ProForm 第一个可输入字段 */
    autoFocus: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      /** 已注册的 ProForm 实例（ProForm mounted 时自动注册） */
      formRef: null
    }
  },
  methods: {
    onOpen() {
      this.$emit('open')
    },
    onOpened() {
      this.$emit('opened')
      // 弹窗打开动画完成后聚焦内部 ProForm 第一个可输入字段
      if (this.autoFocus && this.formRef) {
        this.formRef.focusFirstField()
      }
    },
    onClosed() {
      // el-dialog 关闭动画结束：自动清理 ProForm 校验状态
      if (this.formRef) {
        this.formRef.clearValidate()
      }
      this.$emit('closed')
    },
    /** el-dialog 自身的显隐同步（before-close 调 done 后触发） */
    handleVisibleChange(val) {
      this.$emit('update:visible', val)
      this.$emit('input', val)
    },
    handleConfirm() {
      if (this.confirmLoading || this.confirmDisabled) return
      // 有 ProForm 且自动校验：先走 validate，通过才抛 confirm；失败由 ProForm 自动定位
      if (this.autoValidate && this.formRef) {
        this.formRef.validate().then(() => {
          this.$emit('confirm')
        }).catch(() => {})
        return
      }
      this.$emit('confirm')
    },
    /** 底部「取消」按钮：不经 el-dialog before-close，自行执行同一套拦截 */
    handleCancel() {
      this.attemptClose()
    },
    /**
     * Element 官方关闭钩子：X / ESC / 遮罩 触发。
     * 不执行 done() 弹窗保持原状（含遮罩、滚动锁），无副作用。
     */
    handleBeforeClose(done) {
      if (this.confirmLoading) return
      if (!this.dirty) {
        this.$emit('cancel')
        done()
        return
      }
      // 此处不用 dangerConfirm——语义是“放弃填写”而非删除
      this.$confirm(this.dirtyTip, '关闭确认', {
        confirmButtonText: '放弃并关闭',
        cancelButtonText: '继续填写',
        type: 'warning'
      }).then(() => {
        this.$emit('discard')
        this.$emit('cancel')
        done()
      }).catch(() => {})
    },
    /** 底部取消按钮的关闭路径（需自行通知父组件收 visible） */
    attemptClose() {
      if (this.confirmLoading) return
      if (!this.dirty) {
        this.finalizeClose()
        return
      }
      this.$confirm(this.dirtyTip, '关闭确认', {
        confirmButtonText: '放弃并关闭',
        cancelButtonText: '继续填写',
        type: 'warning'
      }).then(() => {
        this.$emit('discard')
        this.finalizeClose()
      }).catch(() => {})
    },
    finalizeClose() {
      this.$emit('cancel')
      this.$emit('update:visible', false)
      this.$emit('input', false)
    },
    /** ProForm 挂载时自动注册（ProForm 沿 $parent 链找 ProDialog 并调用此方法） */
    registerForm(vm) {
      this.formRef = vm
    },
    /** ProForm 卸载时注销 */
    unregisterForm(vm) {
      if (this.formRef === vm) {
        this.formRef = null
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.pro-dialog__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
