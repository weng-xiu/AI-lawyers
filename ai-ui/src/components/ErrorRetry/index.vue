<template>
  <div class="error-retry">
    <i class="el-icon-warning-outline error-retry__icon" aria-hidden="true"></i>
    <p class="error-retry__text">{{ message }}</p>
    <p v-if="detail" class="error-retry__detail">{{ detail }}</p>
    <el-button
      size="small"
      icon="el-icon-refresh-right"
      class="error-retry__btn"
      @click="$emit('retry')">{{ retryText }}</el-button>
  </div>
</template>

<script>
/**
 * V2 三态组件之「错误态」：数据加载失败后的统一重试入口
 *
 * 用法：
 *   <ErrorRetry v-if="loadError" @retry="getList" />
 *   <el-table v-else ...>
 */
export default {
  name: 'ErrorRetry',
  props: {
    // 错误主文案
    message: {
      type: String,
      default: '数据加载失败'
    },
    // 错误详情（接口 msg 等）
    detail: {
      type: String,
      default: ''
    },
    retryText: {
      type: String,
      default: '重新加载'
    }
  }
}
</script>

<style scoped>
.error-retry {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 42px 16px;
  color: #5a6a7e;
}
.error-retry__icon {
  font-size: 42px;
  color: #e8923a;
  margin-bottom: 12px;
}
.error-retry__text {
  margin: 0;
  font-size: 14px;
  color: #5a6a7e;
}
.error-retry__detail {
  margin: 4px 0 0;
  font-size: 12px;
  color: #b0bcca;
  max-width: 420px;
}
.error-retry__btn {
  margin-top: 12px;
  color: #1a3c6e;
  border-color: #dce2eb;
}
.error-retry__btn:hover {
  color: #255a99;
  border-color: #1a3c6e;
}
</style>
