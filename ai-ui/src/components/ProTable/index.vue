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

    <!-- 工具条：左侧操作按钮 + 右侧 RightToolbar（刷新/显隐搜索）+ 密度/列设置（U5） -->
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
      <div v-if="showToolbar" class="table-tools">
        <!-- U5：表格密度切换（宽松/中等/紧凑），选择持久化到 localStorage -->
        <el-dropdown trigger="click" @command="handleSizeCommand">
          <el-button size="mini" icon="el-icon-s-grid" circle plain title="表格密度" />
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="default" :class="{ 'is-active-option': tableSize === 'default' }">宽松</el-dropdown-item>
            <el-dropdown-item command="medium" :class="{ 'is-active-option': tableSize === 'medium' }">中等</el-dropdown-item>
            <el-dropdown-item command="small" :class="{ 'is-active-option': tableSize === 'small' }">紧凑</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
        <!-- U5：列设置（显隐列），选择持久化到 localStorage -->
        <el-popover placement="bottom-end" width="180" trigger="click">
          <el-button slot="reference" size="mini" icon="el-icon-set-up" circle plain title="列设置" />
          <el-checkbox-group v-model="visibleCols" class="col-setting">
            <el-checkbox v-for="col in columns" :key="colKey(col)" :label="colKey(col)">{{ col.label }}</el-checkbox>
          </el-checkbox-group>
          <el-button type="text" size="mini" class="col-reset" @click="resetColumns">重置</el-button>
        </el-popover>
      </div>
    </el-row>

    <!-- 表格主体：loading / 空态内建，列由 columns 驱动，自定义渲染走 #column-{prop} 插槽 -->
    <el-table
      ref="table"
      :stripe="stripe"
      v-bind="tableAttrs"
      v-loading="loading"
      :size="tableSize"
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
        v-for="col in displayColumns"
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
        <!-- P8 单测回归：slot-scope 模板合并后 $scopedSlots.default 恒存在，
             会抑制 el-table-column 默认文本渲染，普通列必须自带兜底（否则单元格空白） -->
        <template slot-scope="scope">
          <slot v-if="col.slot" :name="'column-' + col.prop" v-bind="scope"></slot>
          <span v-else-if="col.formatter">{{ col.formatter(scope.row, scope.column, scope.row[col.prop], scope.$index) }}</span>
          <span v-else>{{ scope.row[col.prop] }}</span>
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
    /** 透传 el-table 原生属性（border/stripe/size/max-height 等）；stripe 在其中可覆盖此默认值 */
    tableAttrs: { type: Object, default: () => ({}) },
    /** V7：默认斑马纹（#FAFBFD，配色.md 工单列表规范）；:stripe="false" 或 tableAttrs.stripe 可关 */
    stripe: { type: Boolean, default: true },
    /** U5 持久化标识：列设置/密度/筛选存档的 key，默认取当前路由路径 */
    persistKey: { type: String, default: '' },
    /** U5 筛选条件保存开关：开启后查询参数（除分页）自动持久化并在下次进入时恢复 */
    persistQuery: { type: Boolean, default: false }
  },
  data() {
    return {
      // U5：表格密度（default 宽松 / medium 中等 / small 紧凑）
      tableSize: 'default',
      // U5：当前显示列 key 集合（全量默认显示）
      visibleCols: []
    }
  },
  computed: {
    showSearchSync: {
      get() {
        return this.showSearch
      },
      set(val) {
        this.$emit('update:showSearch', val)
      }
    },
    /** 持久化命名空间：页面显式 persistKey 优先，否则用路由路径 */
    persistId() {
      return this.persistKey || (this.$route && this.$route.path) || 'default'
    },
    /** 列设置过滤后的渲染列 */
    displayColumns() {
      if (!this.visibleCols.length) return this.columns
      return this.columns.filter(c => this.visibleCols.indexOf(this.colKey(c)) !== -1)
    }
  },
  watch: {
    columns: {
      immediate: true,
      handler() {
        // 列定义变化：剔除已不存在的 key；首次/新增列默认显示
        const keys = this.columns.map(this.colKey)
        const signature = keys.join()
        this.visibleCols = this.visibleCols.filter(k => keys.indexOf(k) !== -1)
        if (this._colKeysInit !== signature) {
          keys.forEach(k => {
            if (this.visibleCols.indexOf(k) === -1) this.visibleCols.push(k)
          })
          this._colKeysInit = signature
        }
      }
    },
    tableSize() {
      this.persistTools()
    },
    visibleCols() {
      this.persistTools()
    },
    queryParams: {
      deep: true,
      handler() {
        // U5：筛选条件防抖持久化（剔除分页字段）
        if (!this.persistQuery) return
        clearTimeout(this._queryTimer)
        this._queryTimer = setTimeout(() => this.saveQuery(), 600)
      }
    }
  },
  created() {
    this.restoreTools()
    if (this.persistQuery) this.restoreQuery()
  },
  methods: {
    colKey(col) {
      return col.prop || col.label
    },
    /** U5：密度切换 */
    handleSizeCommand(size) {
      this.tableSize = size
    },
    /** U5：列设置恢复全显 */
    resetColumns() {
      this.visibleCols = this.columns.map(this.colKey)
    },
    /** U5：密度/列设置持久化 */
    persistTools() {
      try {
        localStorage.setItem('proTable:tools:' + this.persistId, JSON.stringify({
          size: this.tableSize,
          cols: this.visibleCols
        }))
      } catch (e) { /* 存储满忽略 */ }
    },
    restoreTools() {
      try {
        const raw = localStorage.getItem('proTable:tools:' + this.persistId)
        if (!raw) return
        const saved = JSON.parse(raw)
        if (saved && ['default', 'medium', 'small'].indexOf(saved.size) !== -1) {
          this.tableSize = saved.size
        }
        if (saved && Array.isArray(saved.cols) && saved.cols.length) {
          const keys = this.columns.map(this.colKey)
          // 仅保留仍存在的列 key，至少保证一列显示
          const valid = saved.cols.filter(k => keys.indexOf(k) !== -1)
          if (valid.length) this.visibleCols = valid
        }
      } catch (e) { /* 损坏存档忽略 */ }
    },
    /** U5：筛选条件持久化 */
    saveQuery() {
      try {
        const q = Object.assign({}, this.queryParams)
        delete q.pageNum
        delete q.pageSize
        localStorage.setItem('proTable:query:' + this.persistId, JSON.stringify(q))
      } catch (e) { /* 存储满忽略 */ }
    },
    /** U5：恢复筛选条件（仅覆盖 queryParams 已有 key），恢复后通知页面重查 */
    restoreQuery() {
      try {
        const raw = localStorage.getItem('proTable:query:' + this.persistId)
        if (!raw) return
        const saved = JSON.parse(raw)
        if (!saved) return
        let changed = false
        Object.keys(saved).forEach(k => {
          if (k in this.queryParams) {
            this.$set(this.queryParams, k, saved[k])
            changed = true
          }
        })
        if (changed) this.$emit('persist-restored')
      } catch (e) { /* 损坏存档忽略 */ }
    },
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

  // U5：密度/列设置工具组（贴工具条右侧，RightToolbar 之前）
  .table-tools {
    float: right;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-right: 10px;
    margin-left: 10px;

    ::v-deep .el-button.is-circle {
      padding: 7px;
    }
  }

  // U5：列设置弹层
  .col-setting {
    display: flex;
    flex-direction: column;

    ::v-deep .el-checkbox {
      margin-right: 0;
      line-height: 24px;

      .el-checkbox__label {
        font-size: 13px;
      }
    }
  }

  .col-reset {
    width: 100%;
    text-align: center;
    border-top: 1px solid #ebeef5;
    padding-top: 6px;
    margin-top: 4px;
  }

  ::v-deep .el-dropdown-menu__item.is-active-option {
    color: #1a3c6e;
    font-weight: 600;
    background-color: #f0f4fa;
  }
}
</style>
