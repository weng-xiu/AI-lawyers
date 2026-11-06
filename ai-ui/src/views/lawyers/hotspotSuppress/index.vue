<template>
  <div class="app-container hotspot-page">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="ruleList"
      :loading="loading"
      :total="total"
      :show-toolbar="false"
      :stripe="false"
      :table-attrs="{ border: true }"
      label-width="80px"
      persist-key="hotspotSuppress:rule"
      empty-text="暂无高频置底规则"
      empty-hint="新增规则后可按主叫号码或关键词拦截、置底高频来电"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增</el-button>
        <el-button type="warning" plain icon="el-icon-document" size="mini" @click="openLogDialog()">命中日志</el-button>
      </template>
      <template #column-matchType="{ row }">
        <el-tag :type="row.matchType === 'PHONE' ? '' : 'success'" size="mini">{{ matchTypeText(row.matchType) }}</el-tag>
      </template>
      <template #column-action="{ row }">
        <el-tag :type="row.action === 'REJECT' ? 'danger' : 'warning'" size="mini">{{ actionText(row.action) }}</el-tag>
      </template>
      <template #column-triggerCount="{ row }">
        <span v-if="!row.triggerCount || row.triggerCount === 0">每次命中</span>
        <span v-else>{{ row.triggerCount }} 次 / {{ row.windowSeconds > 0 ? row.windowSeconds + ' 秒' : '不限时' }}</span>
      </template>
      <template #column-status="{ row }">
        <el-switch v-model="row.status" active-value="0" inactive-value="1" @change="handleStatusChange(row)" />
      </template>
      <template #column-effectiveStart="{ row }">{{ row.effectiveStart ? parseTime(row.effectiveStart) : '立即' }}</template>
      <template #column-effectiveEnd="{ row }">{{ row.effectiveEnd ? parseTime(row.effectiveEnd) : '永久' }}</template>
      <template #column-operation="{ row }">
        <el-button type="text" size="mini" icon="el-icon-document" @click="openLogDialog(row)">日志</el-button>
        <el-button type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(row)">编辑</el-button>
        <el-button type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDelete(row)">删除</el-button>
      </template>
    </ProTable>

    <!-- 新增/编辑对话框（V3 ProDialog） -->
    <pro-dialog
      v-model="open"
      :title="title"
      width="600px"
      :confirm-loading="submitting"
      :dirty="formDirty"
      @confirm="submitForm"
      @closed="reset"
    >
      <pro-form
        ref="form"
        :model="form"
        :fields="formFields"
        label-width="110px"
      />
    </pro-dialog>

    <!-- 命中日志对话框 -->
    <el-dialog :title="logTitle" :visible.sync="logOpen" width="900px" append-to-body>
      <!-- 命中日志表同样收敛到 ProTable（独立 persist-key，弹窗内固定高度） -->
      <ProTable
        :columns="logColumns"
        :query-form-items="logQueryFormItems"
        :query-params="logQuery"
        :data="logList"
        :loading="logLoading"
        :total="logTotal"
        :show-toolbar="false"
        :stripe="false"
        :table-attrs="{ border: true, maxHeight: '420px' }"
        label-width="80px"
        persist-key="hotspotSuppress:log"
        empty-text="暂无命中日志"
        empty-hint="规则命中后在此按主叫号码与方向追溯"
        @query="handleLogQuery"
        @reset="resetLogQuery"
        @pagination="getLogList">
        <template #column-hitTime="{ row }">{{ parseTime(row.hitTime) }}</template>
        <template #column-direction="{ row }">{{ row.direction === 'INBOUND' ? '入站' : '出站' }}</template>
        <template #column-action="{ row }">
          <el-tag :type="row.action === 'REJECT' ? 'danger' : 'warning'" size="mini">{{ actionText(row.action) }}</el-tag>
        </template>
      </ProTable>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="logOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listHotspot, getHotspot, addHotspot, updateHotspot, delHotspot, listHotspotLog } from '@/api/lawyers/hotspotSuppress'

export default {
  name: 'HotspotSuppress',
  data() {
    return {
      loading: false,
      total: 0,
      ruleList: [],
      // V3：ProTable 列定义（匹配方式/处置动作/频次阈值/状态/生效时间/操作走插槽，移除原 fixed="right"）
      columns: [
        { prop: 'suppressId', label: 'ID', width: 70 },
        { prop: 'ruleName', label: '规则名称', minWidth: 150, showOverflowTooltip: true },
        { prop: 'matchType', label: '匹配方式', width: 100, slot: true },
        { prop: 'matchValue', label: '匹配值', minWidth: 140, showOverflowTooltip: true },
        { prop: 'action', label: '处置动作', width: 100, slot: true },
        { prop: 'priorityLevel', label: '置底优先级', width: 100 },
        { prop: 'triggerCount', label: '频次阈值', width: 120, slot: true },
        { prop: 'hitCount', label: '累计命中', width: 90 },
        { prop: 'status', label: '状态', width: 90, slot: true },
        { prop: 'effectiveStart', label: '生效开始', width: 160, slot: true },
        { prop: 'effectiveEnd', label: '生效结束', width: 160, slot: true },
        { prop: 'operation', label: '操作', width: 180, slot: true, className: 'small-padding fixed-width' }
      ],
      // 命中日志表列定义（时间/方向/动作走插槽）
      logColumns: [
        { prop: 'hitTime', label: '时间', width: 160, slot: true },
        { prop: 'ruleName', label: '规则', minWidth: 120, showOverflowTooltip: true },
        { prop: 'direction', label: '方向', width: 80, slot: true },
        { prop: 'callerNumber', label: '主叫', width: 130 },
        { prop: 'calleeNumber', label: '被叫', width: 130 },
        { prop: 'action', label: '动作', width: 90, slot: true },
        { prop: 'priority', label: '优先级', width: 80 },
        { prop: 'channelUuid', label: '通道UUID', minWidth: 140, showOverflowTooltip: true }
      ],
      open: false,
      title: '',
      // V3 ProDialog：提交防重锁 + 脏改动快照
      submitting: false,
      formSnapshot: '',
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        ruleName: undefined,
        matchType: undefined,
        action: undefined,
        status: undefined
      },
      form: {},
      logOpen: false,
      logTitle: '命中日志',
      logLoading: false,
      logTotal: 0,
      logList: [],
      logQuery: {
        pageNum: 1,
        pageSize: 10,
        suppressId: undefined,
        callerNumber: undefined,
        direction: undefined
      }
    }
  },
  computed: {
    /** V3：ProTable 查询项（匹配方式/处置动作/状态为字典化下拉） */
    queryFormItems() {
      return [
        { prop: 'ruleName', label: '规则名称', type: 'input', placeholder: '请输入规则名称', width: 180 },
        {
          prop: 'matchType', label: '匹配方式', type: 'select', placeholder: '全部', width: 140,
          options: [
            { label: '号码匹配', value: 'PHONE' },
            { label: '关键词匹配', value: 'KEYWORD' }
          ]
        },
        {
          prop: 'action', label: '处置动作', type: 'select', placeholder: '全部', width: 140,
          options: [
            { label: '降权置底', value: 'PRIORITY' },
            { label: '直接挂断', value: 'REJECT' }
          ]
        },
        {
          prop: 'status', label: '状态', type: 'select', placeholder: '全部', width: 120,
          options: [
            { label: '启用', value: '0' },
            { label: '停用', value: '1' }
          ]
        }
      ]
    },
    /** 命中日志表查询项 */
    logQueryFormItems() {
      return [
        { prop: 'callerNumber', label: '主叫号码', type: 'input', placeholder: '主叫号码', width: 150 },
        {
          prop: 'direction', label: '方向', type: 'select', placeholder: '全部', width: 120,
          options: [
            { label: '入站', value: 'INBOUND' },
            { label: '出站', value: 'OUTBOUND' }
          ]
        }
      ]
    },
    /** 表单相对打开时是否有改动（供 ProDialog 关闭拦截） */
    formDirty() {
      return this.open && this.formSnapshot !== JSON.stringify(this.form)
    },
    /** V3 ProForm：新增/编辑表单 schema（matchValue 随匹配方式切换语义；priorityLevel 仅降权置底时出现） */
    formFields() {
      const isKeyword = this.form.matchType === 'KEYWORD'
      return [
        { prop: 'ruleName', label: '规则名称', type: 'input', required: true, message: '规则名称不能为空', attrs: { maxlength: 64 } },
        {
          prop: 'matchType', label: '匹配方式', type: 'radio', required: true,
          options: [
            { label: '号码匹配（主叫精确匹配）', value: 'PHONE' },
            { label: '关键词匹配（文本包含）', value: 'KEYWORD' }
          ]
        },
        {
          prop: 'matchValue',
          label: isKeyword ? '关键词' : '主叫号码',
          type: 'input', required: true, message: '匹配值不能为空',
          placeholder: isKeyword ? '请输入关键词' : '请输入主叫号码',
          attrs: { maxlength: 64 }
        },
        {
          prop: 'action', label: '处置动作', type: 'radio', required: true,
          options: [
            { label: '降权置底（沉到普通来电之后，仍可接听）', value: 'PRIORITY' },
            { label: '直接挂断', value: 'REJECT' }
          ]
        },
        { prop: 'priorityLevel', label: '置底优先级', type: 'number', max: -1, step: 10, tip: '负数，越小越靠后（普通来电默认 0）', visible: m => m.action === 'PRIORITY' },
        { prop: 'triggerCount', label: '频次阈值', type: 'number', min: 0, tip: '0 = 每次命中即处置；>0 = 时间窗内累计命中达该次数才处置' },
        { prop: 'windowSeconds', label: '统计窗口(秒)', type: 'number', min: 0, step: 60, tip: '0 = 不限时间窗（按历史累计）' },
        { prop: 'status', label: '状态', type: 'switch', activeValue: '0', inactiveValue: '1', activeText: '启用', inactiveText: '停用' },
        { prop: 'effectiveStart', label: '生效开始时间', type: 'datetime', placeholder: '留空表示立即生效' },
        { prop: 'effectiveEnd', label: '生效结束时间', type: 'datetime', placeholder: '留空表示永久有效' },
        { prop: 'remark', label: '备注', type: 'textarea', rows: 2 }
      ]
    }
  },
  created() {
    this.getList()
  },
  methods: {
    matchTypeText(t) {
      if (t === 'PHONE') return '号码匹配'
      if (t === 'KEYWORD') return '关键词匹配'
      return '未知'
    },
    actionText(a) {
      if (a === 'PRIORITY') return '降权置底'
      if (a === 'REJECT') return '直接挂断'
      return '未知'
    },
    getList() {
      this.loading = true
      listHotspot(this.queryParams).then(res => {
        this.ruleList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // ProTable 已复位查询表单，这里只重设查询参数并重查（避免重复 resetForm）
      this.queryParams = { pageNum: 1, pageSize: 10, ruleName: undefined, matchType: undefined, action: undefined, status: undefined }
      this.getList()
    },
    reset() {
      this.form = {
        suppressId: undefined,
        ruleName: undefined,
        matchType: 'PHONE',
        matchValue: undefined,
        action: 'PRIORITY',
        priorityLevel: -100,
        triggerCount: 0,
        windowSeconds: 0,
        status: '0',
        effectiveStart: undefined,
        effectiveEnd: undefined,
        remark: undefined
      }
      this.formSnapshot = JSON.stringify(this.form)
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增高频置底规则'
    },
    handleUpdate(row) {
      this.reset()
      getHotspot(row.suppressId).then(res => {
        this.form = res.data || {}
        this.formSnapshot = JSON.stringify(this.form)
        this.open = true
        this.title = '编辑高频置底规则'
      })
    },
    submitForm() {
      if (this.submitting) return
      this.submitting = true
      const api = this.form.suppressId != null ? updateHotspot : addHotspot
      api(this.form).then(() => {
        this.$modal.msgSuccess('保存成功')
        this.open = false
        this.getList()
      }).finally(() => {
        this.submitting = false
      })
    },
    handleStatusChange(row) {
      const oldStatus = row.status === '0' ? '1' : '0'
      const text = row.status === '0' ? '启用' : '停用'
      updateHotspot(row).then(() => {
        // U2：置底规则启停 30 秒内可撤销；失败回退开关
        this.$modal.undo({
          message: '置底规则已' + text,
          undoSuccessText: '已撤销，规则已' + (oldStatus === '0' ? '启用' : '停用'),
          onUndo: () => updateHotspot({ ...row, status: oldStatus }).then(() => {
            row.status = oldStatus
          })
        })
      }).catch(() => {
        row.status = oldStatus
        this.$modal.msgError('状态更新失败，已恢复原状态')
      })
    },
    handleDelete(row) {
      this.$modal.dangerConfirm({
        content: '将删除高频置底规则「' + row.ruleName + '」',
        count: 1,
        countNoun: '条规则',
        tip: '删除后该号码/区域不再被置底，历史命中日志保留'
      }).then(() => {
        return delHotspot(row.suppressId)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    },
    openLogDialog(row) {
      this.logQuery = {
        pageNum: 1,
        pageSize: 10,
        suppressId: row ? row.suppressId : undefined,
        callerNumber: undefined,
        direction: undefined
      }
      this.logTitle = row ? '命中日志 - ' + row.ruleName : '全部命中日志'
      this.logOpen = true
      this.getLogList()
    },
    getLogList() {
      this.logLoading = true
      listHotspotLog(this.logQuery).then(res => {
        this.logList = res.rows || []
        this.logTotal = res.total || 0
        this.logLoading = false
      }).catch(() => { this.logLoading = false })
    },
    handleLogQuery() {
      this.logQuery.pageNum = 1
      this.getLogList()
    },
    resetLogQuery() {
      const suppressId = this.logQuery.suppressId
      this.logQuery = { pageNum: 1, pageSize: 10, suppressId: suppressId, callerNumber: undefined, direction: undefined }
      this.getLogList()
    }
  }
}
</script>
