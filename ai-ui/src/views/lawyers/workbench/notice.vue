<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="noticeList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      has-selection
      label-width="80px"
      empty-text="暂无公告"
      empty-hint="新增并发布公告后，坐席工作台首页将同步展示"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:workbench:notice:add']"
        >新增</el-button>
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['lawyers:workbench:notice:remove']"
        >删除</el-button>
      </template>
      <template #column-noticeType="{ row }">
        <el-tag :type="row.noticeType === '紧急' ? 'danger' : ''">{{ row.noticeType || '普通' }}</el-tag>
      </template>
      <template #column-isTop="{ row }">
        <el-tag :type="row.isTop === 1 ? 'danger' : 'info'">{{ row.isTop === 1 ? '置顶' : '否' }}</el-tag>
      </template>
      <template #column-status="{ row }">
        <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
      </template>
      <template #column-publishTime="{ row }">{{ parseTime(row.publishTime) }}</template>
      <template #column-createTime="{ row }">{{ parseTime(row.createTime) }}</template>
      <template #column-action="{ row }">
        <el-button
          v-if="row.status === 0"
          size="mini"
          type="text"
          icon="el-icon-s-promotion"
          @click="handlePublish(row)"
        >发布</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-edit"
          @click="handleUpdate(row)"
          v-hasPermi="['lawyers:workbench:notice:edit']"
        >修改</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-delete"
          @click="handleDelete(row)"
          v-hasPermi="['lawyers:workbench:notice:remove']"
        >删除</el-button>
      </template>
    </ProTable>

    <!-- 添加或修改公告对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="680px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="noticeTitle">
          <el-input v-model="form.noticeTitle" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="类型" prop="noticeType">
          <el-select v-model="form.noticeType" placeholder="请选择公告类型">
            <el-option label="普通" value="普通" />
            <el-option label="紧急" value="紧急" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否置顶" prop="isTop">
          <el-radio-group v-model="form.isTop">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="0">草稿</el-radio>
            <el-radio :label="1">发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="noticeContent">
          <el-input v-model="form.noticeContent" type="textarea" :rows="6" placeholder="请输入公告内容" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listNotice, getNotice, addNotice, updateNotice, delNotice, publishNotice } from '@/api/lawyers/workbench'

export default {
  name: 'Notice',
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 公告表格数据
      noticeList: [],
      // V3：ProTable 列定义（类型/置顶/状态/时间/操作走插槽）
      columns: [
        { prop: 'noticeTitle', label: '公告标题', minWidth: 200, showOverflowTooltip: true },
        { prop: 'noticeType', label: '类型', width: 100, align: 'center', slot: true },
        { prop: 'isTop', label: '是否置顶', width: 100, align: 'center', slot: true },
        { prop: 'status', label: '状态', width: 100, align: 'center', slot: true },
        { prop: 'publishTime', label: '发布时间', width: 160, align: 'center', slot: true },
        { prop: 'createTime', label: '创建时间', width: 160, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 200, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      // 弹出层标题
      title: '',
      // 是否显示弹出层
      open: false,
      // 状态选项
      statusOptions: [
        { value: 0, label: '草稿' },
        { value: 1, label: '发布' }
      ],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        noticeTitle: undefined,
        status: undefined
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        noticeTitle: [
          { required: true, message: '公告标题不能为空', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  computed: {
    /** V3：ProTable 查询字段 */
    queryFormItems() {
      return [
        { prop: 'noticeTitle', label: '公告标题', placeholder: '请输入公告标题', width: '240px' },
        { prop: 'status', label: '状态', type: 'select', placeholder: '请选择状态', width: '110px', options: this.statusOptions }
      ]
    }
  },
  methods: {
    /** 查询公告列表 */
    getList() {
      this.loading = true
      listNotice(this.queryParams).then(response => {
        this.noticeList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        noticeId: null,
        noticeTitle: null,
        noticeContent: null,
        noticeType: '普通',
        publishTime: null,
        status: 0,
        isTop: 0
      }
      this.resetForm('form')
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, noticeTitle: undefined, status: undefined }
      this.getList()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.noticeId)
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增公告'
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const noticeId = row.noticeId || this.ids
      getNotice(noticeId).then(response => {
        this.form = response.data
        this.open = true
        this.title = '修改公告'
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs['form'].validate(valid => {
        if (valid) {
          if (this.form.noticeId != null) {
            updateNotice(this.form).then(() => {
              this.$modal.msgSuccess('修改成功')
              this.open = false
              this.getList()
            })
          } else {
            addNotice(this.form).then(() => {
              this.$modal.msgSuccess('新增成功')
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const noticeIds = row.noticeId || this.ids
      const count = row.noticeId ? 1 : (this.ids || []).length
      this.$modal.dangerConfirm({
        content: '将删除选中的公告',
        count: count,
        countNoun: '条公告'
      }).then(function() {
        return delNotice(noticeIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    /** 发布按钮 */
    handlePublish(row) {
      this.$modal.dangerConfirm({
        actionType: 'publish',
        content: '将发布公告「' + row.noticeTitle + '」',
        count: 1,
        countNoun: '条公告'
      }).then(function() {
        return publishNotice(row.noticeId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('发布成功')
      }).catch(() => {})
    },
    // 状态标签类型
    statusTagType(status) {
      const map = { 0: 'info', 1: 'success' }
      return map[status] || 'info'
    },
    // 状态文本
    statusLabel(status) {
      const item = this.statusOptions.find(i => i.value === status)
      return item ? item.label : '未知'
    }
  }
}
</script>
