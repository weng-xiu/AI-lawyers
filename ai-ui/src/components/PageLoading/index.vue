<template>
  <div class="page-loading" :style="{ minHeight: minHeight }">
    <el-skeleton :rows="rows" :animated="animated" :throttle="0" />
  </div>
</template>

<script>
/**
 * V2 三态组件之「加载态」：页面/区块首次加载骨架屏
 *
 * 用法（替代"点了没反应"的空白期）：
 *   <PageLoading v-if="firstLoading" :rows="6" />
 *   <el-table v-else ...>
 */
export default {
  name: 'PageLoading',
  props: {
    // 骨架行数
    rows: {
      type: Number,
      default: 6
    },
    // 是否开启动画
    animated: {
      type: Boolean,
      default: true
    },
    // 容器最小高度，避免加载完成前后高度跳动
    minHeight: {
      type: String,
      default: '180px'
    }
  }
}
</script>

<style scoped>
.page-loading {
  padding: 20px;
  background: #fff;
}
</style>
