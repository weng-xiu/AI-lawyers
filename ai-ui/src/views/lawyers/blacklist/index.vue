<template>
  <div class="app-container blacklist-page">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="blacklistList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :table-attrs="{ border: true }"
      label-width="80px"
      empty-text="暂无名单记录"
      empty-hint="新增黑名单/白名单后，来电将按名单策略弹屏提示或拦截"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增</el-button>
        <el-button type="success" plain icon="el-icon-circle-check" size="mini" @click="openCheckDialog">号码检查</el-button>
      </template>
      <template #column-listType="{ row }">
        <el-tag :type="listTypeTagType(row.listType)" size="mini">
          {{ listTypeText(row.listType) }}
        </el-tag>
      </template>
      <template #column-reason="{ row }">{{ row.reason || '-' }}</template>
      <template #column-status="{ row }">
        <el-switch
          v-model="row.status"
          active-value="1"
          inactive-value="0"
          @change="handleStatusChange(row)"
        />
      </template>
      <template #column-effectiveStart="{ row }">{{ row.effectiveStart ? parseTime(row.effectiveStart) : '-' }}</template>
      <template #column-effectiveEnd="{ row }">{{ row.effectiveEnd ? parseTime(row.effectiveEnd) : '-' }}</template>
      <template #column-createTime="{ row }">{{ parseTime(row.createTime) }}</template>
      <template #column-action="{ row }">
        <el-button type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(row)">编辑</el-button>
        <el-button type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDelete(row)">删除</el-button>
      </template>
    </ProTable>

    <!-- 新增/编辑对话框（V3 ProDialog：统一外壳 + 提交 loading + 脏改动关闭拦截） -->
    <pro-dialog
      v-model="open"
      :title="title"
      width="560px"
      :confirm-loading="submitting"
      :dirty="formDirty"
      @confirm="submitForm"
      @closed="reset"
    >
      <pro-form
        ref="form"
        :model="form"
        :fields="formFields"
        label-width="100px"
      />
    </pro-dialog>

    <!-- 号码检查对话框 -->
    <el-dialog title="号码检查" :visible.sync="checkOpen" width="460px" append-to-body>
      <el-form label-width="80px" size="small">
        <el-form-item label="电话号码">
          <el-input v-model="checkPhone" placeholder="请输入要检查的号码" clearable maxlength="20" @keyup.enter.native="submitCheck" />
        </el-form-item>
      </el-form>
      <div v-if="checkResult" class="check-result">
        <el-alert
          :title="checkResultTitle"
          :type="checkResultType"
          :closable="false"
          show-icon
        />
        <div class="check-detail">
          <p v-if="checkResult.listType">名单类型：<b>{{ listTypeText(checkResult.listType) }}</b></p>
          <p v-if="checkResult.status != null">状态：<b>{{ checkResult.status === '1' ? '启用' : '停用' }}</b></p>
          <p v-if="checkResult.reason">原因：{{ checkResult.reason }}</p>
          <p v-if="checkResult.effectiveStart">生效时间：{{ parseTime(checkResult.effectiveStart) }} ~ {{ checkResult.effectiveEnd ? parseTime(checkResult.effectiveEnd) : '永久' }}</p>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitCheck" :loading="checkLoading">检查</el-button>
        <el-button size="small" @click="checkOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listBlacklist, getBlacklist, addBlacklist, updateBlacklist, delBlacklist, checkPhone } from '@/api/lawyers/blacklist'

export default {
  name: 'Blacklist',
  data() {
    return {
      loading: false,
      total: 0,
      showSearch: true,
      blacklistList: [],
      // V3：ProTable 列定义（名单类型/状态开关/时间/操作走插槽）
      columns: [
        { prop: 'id', label: 'ID', width: 80, align: 'center' },
        { prop: 'phoneNumber', label: '电话号码', width: 150, align: 'center' },
        { prop: 'listType', label: '名单类型', width: 100, align: 'center', slot: true },
        { prop: 'reason', label: '原因', minWidth: 180, align: 'center', showOverflowTooltip: true, slot: true },
        { prop: 'status', label: '状态', width: 90, align: 'center', slot: true },
        { prop: 'effectiveStart', label: '生效开始', width: 160, align: 'center', slot: true },
        { prop: 'effectiveEnd', label: '生效结束', width: 160, align: 'center', slot: true },
        { prop: 'createTime', label: '创建时间', width: 160, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 160, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      open: false,
      title: '',
      // V3 ProDialog：提交防重锁 + 脏改动快照
      submitting: false,
      formSnapshot: '',
      checkOpen: false,
      checkPhone: '',
      checkResult: null,
      checkLoading: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        phoneNumber: undefined,
        listType: undefined,
        status: undefined
      },
      form: {},
      // V3 ProForm：新增/编辑表单 schema（校验规则随字段声明）
      formFields: [
        { prop: 'phoneNumber', label: '电话号码', type: 'input', required: true, attrs: { maxlength: 20 } },
        {
          prop: 'listType', label: '名单类型', type: 'radio', required: true,
          options: [
            { label: '黑名单', value: '1' },
            { label: '白名单', value: '2' },
            { label: '退订名单', value: '3' }
          ]
        },
        { prop: 'reason', label: '原因', type: 'textarea', rows: 3 },
        { prop: 'status', label: '状态', type: 'switch', activeValue: '1', inactiveValue: '0', activeText: '启用', inactiveText: '停用' },
        { prop: 'effectiveStart', label: '生效开始时间', type: 'datetime', placeholder: '选择生效开始时间' },
        { prop: 'effectiveEnd', label: '生效结束时间', type: 'datetime', placeholder: '选择生效结束时间' }
      ]
    }
  },
  computed: {
    /** V3：ProTable 查询字段 */
    queryFormItems() {
      return [
        { prop: 'phoneNumber', label: '电话号码', placeholder: '请输入电话号码', width: 180 },
        {
          prop: 'listType',
          label: '名单类型',
          type: 'select',
          placeholder: '全部',
          width: 140,
          options: [
            { label: '黑名单', value: '1' },
            { label: '白名单', value: '2' },
            { label: '退订名单', value: '3' }
          ]
        },
        {
          prop: 'status',
          label: '状态',
          type: 'select',
          placeholder: '全部',
          width: 120,
          options: [
            { label: '启用', value: '1' },
            { label: '停用', value: '0' }
          ]
        }
      ]
    },
    checkResultTitle() {
      if (!this.checkResult) return ''
      if (this.checkResult.listed) {
        return '该号码在' + this.listTypeText(this.checkResult.listType) + '中'
      }
      return '该号码未在名单中'
    },
    checkResultType() {
      if (!this.checkResult) return 'info'
      if (!this.checkResult.listed) return 'success'
      return String(this.checkResult.listType) === '1' ? 'error' : 'warning'
    },
    /** 表单相对打开时是否有改动（供 ProDialog 关闭拦截） */
    formDirty() {
      return this.open && this.formSnapshot !== JSON.stringify(this.form)
    }
  },
  created() {
    this.getList()
  },
  methods: {
    listTypeText(listType) {
      const t = String(listType)
      if (t === '1') return '黑名单'
      if (t === '2') return '白名单'
      if (t === '3') return '退订名单'
      return '未知'
    },
    listTypeTagType(listType) {
      const t = String(listType)
      if (t === '1') return 'danger'
      if (t === '2') return 'success'
      if (t === '3') return 'warning'
      return 'info'
    },
    getList() {
      this.loading = true
      listBlacklist(this.queryParams).then(res => {
        this.blacklistList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, phoneNumber: undefined, listType: undefined, status: undefined }
      this.getList()
    },
    reset() {
      this.form = {
        id: undefined,
        phoneNumber: undefined,
        listType: '1',
        reason: undefined,
        status: '1',
        effectiveStart: undefined,
        effectiveEnd: undefined
      }
      this.formSnapshot = JSON.stringify(this.form)
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增名单'
    },
    handleUpdate(row) {
      this.reset()
      getBlacklist(row.id).then(res => {
        this.form = res.data || {}
        this.formSnapshot = JSON.stringify(this.form)
        this.open = true
        this.title = '编辑名单'
      })
    },
    submitForm() {
      if (this.submitting) return
      this.submitting = true
      const api = this.form.id != null ? updateBlacklist : addBlacklist
      api(this.form).then(() => {
        this.$modal.msgSuccess('保存成功')
        this.open = false
        this.getList()
      }).finally(() => {
        this.submitting = false
      })
    },
    handleStatusChange(row) {
      const oldStatus = row.status === '1' ? '0' : '1'
      const text = row.status === '1' ? '启用' : '停用'
      const data = { id: row.id, status: row.status }
      updateBlacklist(data).then(() => {
        // U2：名单启停 30 秒内可撤销；失败回退开关
        this.$modal.undo({
          message: '黑名单已' + text,
          undoSuccessText: '已撤销，黑名单已' + (oldStatus === '1' ? '启用' : '停用'),
          onUndo: () => updateBlacklist({ id: row.id, status: oldStatus }).then(() => {
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
        content: '将删除号码「' + row.phone + '」的黑名单记录',
        count: 1,
        countNoun: '条名单',
        tip: '删除后来电拦截策略不再对该号码生效'
      }).then(() => {
        return delBlacklist(row.id)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    },
    openCheckDialog() {
      this.checkPhone = ''
      this.checkResult = null
      this.checkOpen = true
    },
    submitCheck() {
      if (!this.checkPhone) {
        this.$modal.msgWarning('请输入电话号码')
        return
      }
      this.checkLoading = true
      checkPhone(this.checkPhone).then(res => {
        const d = res.data || {}
        // 后端可能返回 listed 字段，也可能直接返回名单对象；统一转换
        if (d.id != null) {
          this.checkResult = { listed: true, ...d }
        } else {
          this.checkResult = { listed: !!d.listed, ...d }
        }
        this.checkLoading = false
      }).catch(() => { this.checkLoading = false })
    }
  }
}
</script>

<style scoped>
.check-result { margin-top: 12px; }
.check-detail { margin-top: 12px; padding: 0 8px; color: #5A6A7E; font-size: 13px; }
.check-detail p { margin: 6px 0; }
</style>
