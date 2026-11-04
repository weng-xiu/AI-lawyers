<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="intentionList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      label-width="88px"
      empty-text="还没有意图定义"
      empty-hint="维护关键词/正则/AI 提示词后，IVR 意图节点即可按意图分流"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:ivr:intention:add']">新增</el-button>
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:ivr:intention:export']">导出</el-button>
      </template>
      <template #column-status="{ row }">
        <el-tag v-if="row.status === '0'" type="success">启用</el-tag>
        <el-tag v-else type="danger">停用</el-tag>
      </template>
      <template #column-createTime="{ row }">{{ parseTime(row.createTime) }}</template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)"
          v-hasPermi="['lawyers:ivr:intention:edit']">修改</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)"
          v-hasPermi="['lawyers:ivr:intention:remove']">删除</el-button>
      </template>
    </ProTable>

    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="意图名称" prop="intentionName">
          <el-input v-model="form.intentionName" placeholder="请输入意图名称" />
        </el-form-item>
        <el-form-item label="意图编码" prop="intentionCode">
          <el-input v-model="form.intentionCode" placeholder="请输入意图编码" />
        </el-form-item>
        <el-form-item label="匹配关键词" prop="keywords">
          <el-input v-model="form.keywords" type="textarea" :rows="2" placeholder="多个关键词用逗号分隔" />
        </el-form-item>
        <el-form-item label="正则表达式" prop="regexPattern">
          <el-input v-model="form.regexPattern" placeholder="用于复杂匹配的正则表达式" />
        </el-form-item>
        <el-form-item label="AI提示词" prop="aiPrompt">
          <el-input v-model="form.aiPrompt" type="textarea" :rows="3" placeholder="AI大模型识别此意图的系统提示词" />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="form.priority" controls-position="right" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
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
import { listIntention, getIntention, delIntention, addIntention, updateIntention } from "@/api/lawyers/ivrIntention"

export default {
  name: "IvrIntention",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      intentionList: [],
      // V3：ProTable 列定义（状态/时间/操作走插槽）
      columns: [
        { prop: 'intentionName', label: '意图名称', minWidth: 180, align: 'center', showOverflowTooltip: true },
        { prop: 'intentionCode', label: '意图编码', width: 140, align: 'center' },
        { prop: 'keywords', label: '匹配关键词', width: 200, align: 'center', showOverflowTooltip: true },
        { prop: 'priority', label: '优先级', width: 80, align: 'center' },
        { prop: 'status', label: '状态', width: 100, align: 'center', slot: true },
        { prop: 'createTime', label: '创建时间', width: 160, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 200, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        intentionName: undefined,
        intentionCode: undefined,
        status: undefined
      },
      form: {},
      rules: {
        intentionName: [
          { required: true, message: "意图名称不能为空", trigger: "blur" }
        ],
        intentionCode: [
          { required: true, message: "意图编码不能为空", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  computed: {
    queryFormItems() {
      return [
        { prop: 'intentionName', label: '意图名称', placeholder: '请输入意图名称', width: 240 },
        { prop: 'intentionCode', label: '意图编码', placeholder: '请输入意图编码', width: 200 },
        {
          prop: 'status',
          label: '状态',
          type: 'select',
          placeholder: '请选择状态',
          width: 120,
          options: [
            { label: '启用', value: '0' },
            { label: '停用', value: '1' }
          ]
        }
      ]
    }
  },
  methods: {
    getList() {
      this.loading = true
      listIntention(this.queryParams).then(response => {
        this.intentionList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        intentionId: null,
        intentionName: null,
        intentionCode: null,
        keywords: null,
        regexPattern: null,
        aiPrompt: null,
        priority: 50,
        status: "0",
        remark: null
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, intentionName: undefined, intentionCode: undefined, status: undefined }
      this.getList()
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加意图定义"
    },
    handleUpdate(row) {
      this.reset()
      getIntention(row.intentionId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改意图定义"
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.intentionId != null) {
            updateIntention(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addIntention(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      this.$modal.dangerConfirm({
        content: '将删除 IVR 意图「' + row.intentionName + '」及其识别配置',
        count: 1,
        countNoun: '个意图',
        tip: '删除后来电路由将无法再按该意图分流'
      }).then(function() {
        return delIntention(row.intentionId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/ivr/intention/export', {
        ...this.queryParams
      }, `ivr_intention_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
