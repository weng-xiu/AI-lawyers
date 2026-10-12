<template>
  <div class="table-empty" :class="{ 'table-empty--compact': compact }">
    <i :class="icon" class="table-empty__icon" aria-hidden="true"></i>
    <p class="table-empty__text">{{ text }}</p>
    <p v-if="hint" class="table-empty__hint">{{ hint }}</p>
    <el-button
      v-if="showRetry"
      type="text"
      icon="el-icon-refresh-right"
      class="table-empty__retry"
      @click="$emit('retry')">重新加载</el-button>
  </div>
</template>

<script>
/**
 * V2 三态组件之「空态」：统一列表空数据展示
 *
 * 用法（作为 el-table 的 empty 插槽内容）：
 *   <el-table>
 *     ...
 *     <template slot="empty">
 *       <TableEmpty text="暂无话单" hint="调整时间范围后重试" />
 *     </template>
 *   </el-table>
 */
export default {
  name: 'TableEmpty',
  props: {
    // 空态主文案
    text: {
      type: String,
      default: '暂无数据'
    },
    // 次要提示（如引导下一步操作）
    hint: {
      type: String,
      default: ''
    },
    // 空态图标
    icon: {
      type: String,
      default: 'el-icon-document'
    },
    // 是否展示「重新加载」入口（查询失败后回退到空态时使用）
    showRetry: {
      type: Boolean,
      default: false
    },
    // 紧凑模式（弹屏/工作台内嵌小表格）
    compact: {
      type: Boolean,
      default: false
    }
  }
}
</script>

<style scoped>
.table-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 42px 16px;
  color: #5a6a7e;
}
.table-empty--compact {
  padding: 20px 12px;
}
.table-empty__icon {
  font-size: 42px;
  color: #b0bcca;
  margin-bottom: 12px;
}
.table-empty--compact .table-empty__icon {
  font-size: 28px;
  margin-bottom: 6px;
}
.table-empty__text {
  margin: 0;
  font-size: 14px;
  color: #5a6a7e;
}
.table-empty__hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #b0bcca;
}
.table-empty__retry {
  margin-top: 8px;
  padding: 0;
}
</style>
