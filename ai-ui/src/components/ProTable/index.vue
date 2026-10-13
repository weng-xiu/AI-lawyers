<template>
  <div class="pro-table">
    <!-- 查询区：字段由 queryFormItems 驱动，复杂控件走 #query-{prop} 插槽 -->
    <el-form
      v-show="showSearchSync && queryFormItems.length"
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      :label-width="labelWidth"
      @submit.native.prevent
    >
      <el-form-item
        v-for="item in queryFormItems"
        :key="item.prop"
        :label="item.label"
        :prop="item.slot ? undefined : item.prop"
      >
        <!-- 自定义控件插槽 -->
        <slot v-if="item.slot" :name="'query-' + item.prop" :item="item" />
        <el-input
          v-else-if="getControlType(item) === 'input'"
          v-model="queryParams[item.prop]"
          :placeholder="item.placeholder || ('请输入' + item.label)"
          clearable
          :style="controlStyle(item)"
          @keyup.enter.native="$emit('query')"
        />
        <el-select
          v-else-if="getControlType(item) === 'select'"
          v-model="queryParams[item.prop]"
          :placeholder="item.placeholder || ('请选择' + item.label)"
          :clearable="item.clearable !== false"
          :filterable="item.filterable === true"
          :style="controlStyle(item)"
        >
          <el-option
            v-for="opt in item.options"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-date-picker
          v-else-if="getControlType(item) === 'daterange'"
          v-model="queryParams[item.prop]"
          type="daterange"
          value-format="yyyy-MM-dd"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :style="controlStyle(item)"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="$emit('query')">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="handleReset">重置</el-button>
        <slot name="query-extra"></slot>
      </el-form-item>
    </el-form>

    <!-- 工具条：左侧操作按钮 + 右侧 RightToolbar（刷新/显隐搜索/显隐列） -->
    <el-row :gutter="10" class="mb8" v-if="$slots['toolbar-left'] || showToolbar">
      <el-col :span="1.5">
        <slot name="toolbar-left"></slot>
      </el-col>
      <right-toolbar
        v-if="showToolbar"
        :show-search.sync="showSearchSync"
        :search="queryFormItems.length > 0"
        @queryTable="$emit('query')"
      >
        <slot name="toolbar-right"></slot>
      </right-toolbar>
    </el-row>

    <!-- 表格主体：loading / 空态内建，列由 columns 驱动，自定义渲染走 #column-{prop} 插槽 -->
    <el-table
      ref="table"
      v-bind="tableAttrs"
      v-loading="loading"
      :data="data"
      @selection-change="$emit('selection-change', $event)"
      @sort-change="$emit('sort-change', $event)"
      @row-click="$emit('row-click', $event)"
      @row-dblclick="$emit('row-dblclick', $event)"
    >
      <template slot="empty">
        <slot name="empty">
          <TableEmpty :text="emptyText" :hint="emptyHint" />
        </slot>
      </template>
      <el-table-column v-if="hasSelection" type="selection" width="55" align="center" />
      <el-table-column v-if="hasIndex" type="index" label="序号" width="55" align="center" />
      <el-table-column
        v-for="col in columns"
        :key="col.prop || col.label"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align || 'center'"
        :fixed="col.fixed"
        :sortable="col.sortable"
        :show-overflow-tooltip="col.showOverflowTooltip === true"
        :class-name="col.className"
      >
        <template v-if="col.slot" slot-scope="scope">
          <slot :name="'column-' + col.prop" v-bind="scope"></slot>
        </template>
        <template v-else-if="col.formatter" slot-scope="scope">
          {{ col.formatter(scope.row, scope.column, scope.row[col.prop], scope.$index) }}
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页：沿用 RuoYi Pagination 与 queryParams.pageNum/pageSize 的 .sync 语义 -->
    <pagination
      v-show="total > 0 && !hidePagination"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      :layout="paginationLayout"
      @pagination="$emit('pagination')"
    />
  </div>
</template>

<script>
/**
 * V3 ProTable：查询区 + 工具条 + 表格 + 分页 一体化封装。
 *
 * <p>收敛 RuoYi 管理页"查询表单 + el-row 工具条 + el-table + pagination"四段式
 * 重复骨架；新页面强制使用，存量页面按域逐步迁移（改一处即全端生效）。</p>
 *
 * 用法示例：
 * <pre>
 * &lt;ProTable
 *   :columns="columns"
 *   :query-form-items="queryFormItems"
 *   :query-params="queryParams"
 *   :data="recordList" :loading="loading" :total="total"
 *   @query="handleQuery" @reset="resetQuery" @pagination="getList"
 *   @selection-change="handleSelectionChange"&gt;
 *   &lt;template #toolbar-left&gt;...按钮组...&lt;/template&gt;
 *   &lt;template #column-status="{ row }"&gt;...&lt;/template&gt;
 *   &lt;template #query-dateRange&gt;...&lt;/template&gt;
 * &lt;/ProTable&gt;
 * </pre>
 */
export default {
  name: 'ProTable',
  props: {
    /** 列定义：[{ prop, label, width, minWidth, align, fixed, sortable, showOverflowTooltip, slot, formatter, className }] */
    columns: { type: Array, required: true },
    /** 查询字段：[{ prop, label, type(input/select/daterange), placeholder, width, options, clearable, filterable, slot }] */
    queryFormItems: { type: Array, default: () => [] },
    /** 查询参数对象（含 pageNum/pageSize，供分页 .sync 直改） */
    queryParams: { type: Object, required: true },
    /** 表格数据 */
    data: { type: Array, default: () => [] },
    /** 加载状态 */
    loading: { type: Boolean, default: false },
    /** 总条数（分页用） */
    total: { type: Number, default: 0 },
    /** 查询区显隐（配合 RightToolbar 切换，.sync） */
    showSearch: { type: Boolean, default: true },
    /** 是否显示 RightToolbar（刷新/搜索切换）；无查询字段时自动只保留刷新 */
    showToolbar: { type: Boolean, default: true },
    /** 多选列 */
    hasSelection: { type: Boolean, default: false },
    /** 序号列 */
    hasIndex: { type: Boolean, default: false },
    /** 隐藏分页（小表格/全量展示场景） */
    hidePagination: { type: Boolean, default: false },
    /** 查询区 label 宽度 */
    labelWidth: { type: String, default: '68px' },
    /** 分页 layout */
    paginationLayout: { type: String, default: 'total, sizes, prev, pager, next, jumper' },
    /** 内建空态文案 */
    emptyText: { type: String, default: '暂无数据' },
    /** 内建空态提示 */
    emptyHint: { type: String, default: '' },
    /** 透传 el-table 原生属性（border/stripe/size/max-height 等） */
    tableAttrs: { type: Object, default: () => ({}) }
  },
  computed: {
    showSearchSync: {
      get() {
        return this.showSearch
      },
      set(val) {
        this.$emit('update:showSearch', val)
      }
    }
  },
  methods: {
    getControlType(item) {
      return item.type || 'input'
    },
    controlStyle(item) {
      return { width: item.width || '240px' }
    },
    /**
     * 重置：表单字段复位（按 el-form-item 初始值）后通知页面处理
     * 日期范围等非表单状态并重新查询。
     */
    handleReset() {
      if (this.$refs.queryForm) {
        this.$refs.queryForm.resetFields()
      }
      this.$emit('reset')
    },
    /** 供父组件调用：this.$refs.proTable.$refs.table.clearSelection() */
  }
}
</script>

<style lang="scss" scoped>
.pro-table {
  .el-table {
    margin-bottom: 8px;
  }
}
</style>
