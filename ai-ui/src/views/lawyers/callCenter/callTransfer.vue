<template>
  <div class="app-container cc-page">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="来电记录ID" prop="recordId">
        <el-input
          v-model="queryParams.recordId"
          placeholder="请输入来电记录ID"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="转出坐席" prop="fromAgentId">
        <el-select v-model="queryParams.fromAgentId" placeholder="全部坐席" filterable clearable style="width: 200px">
          <el-option v-for="a in agentOptions" :key="a.agentId" :label="a.agentName+'('+a.agentId+')'" :value="a.agentId" />
        </el-select>
      </el-form-item>
      <el-form-item label="转入坐席" prop="toAgentId">
        <el-select v-model="queryParams.toAgentId" placeholder="全部坐席" filterable clearable style="width: 200px">
          <el-option v-for="a in agentOptions" :key="a.agentId" :label="a.agentName+'('+a.agentId+')'" :value="a.agentId" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:call:transfer:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['lawyers:call:transfer:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:call:transfer:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="transferList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="转接ID" align="center" prop="transferId" width="90" />
      <el-table-column label="来电记录ID" align="center" prop="recordId" width="110" />
      <el-table-column label="转出坐席" align="center" prop="fromAgentName" />
      <el-table-column label="转入坐席" align="center" prop="toAgentName" />
      <el-table-column label="转接原因" align="center" prop="reason" :show-overflow-tooltip="true" />
      <el-table-column label="转接时间" align="center" prop="transferTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.transferTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleDetail(scope.row)"
            v-hasPermi="['lawyers:call:transfer:query']"
          >详情</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:call:transfer:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="来电记录ID" prop="recordId">
          <el-input v-model="form.recordId" placeholder="请输入来电记录ID" />
        </el-form-item>
        <el-row :gutter="10">
        <el-col :span="12"><el-form-item label="转出坐席" prop="fromAgentId">
          <el-select v-model="form.fromAgentId" placeholder="请选择" filterable clearable style="width:100%" @change="onFromAgentChange">
            <el-option v-for="a in agentOptions" :key="a.agentId" :label="a.agentName+'('+a.agentId+')'" :value="a.agentId" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="12"><el-form-item label="转入坐席" prop="toAgentId">
          <el-select v-model="form.toAgentId" placeholder="请选择" filterable clearable style="width:100%" @change="onToAgentChange">
            <el-option v-for="a in agentOptions" :key="a.agentId" :label="a.agentName+'('+a.agentId+')'" :value="a.agentId" />
          </el-select>
        </el-form-item></el-col>
        </el-row>
        <el-form-item label="转接原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入转接原因" />
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

    <el-dialog title="转接详情" :visible.sync="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="转接ID">{{ detailForm.transferId }}</el-descriptions-item>
        <el-descriptions-item label="来电记录ID">{{ detailForm.recordId }}</el-descriptions-item>
        <el-descriptions-item label="转出坐席">{{ detailForm.fromAgentName }}</el-descriptions-item>
        <el-descriptions-item label="转入坐席">{{ detailForm.toAgentName }}</el-descriptions-item>
        <el-descriptions-item label="转接原因">{{ detailForm.reason }}</el-descriptions-item>
        <el-descriptions-item label="转接时间">{{ parseTime(detailForm.transferTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailForm.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTransfer, getTransfer, addTransfer, delTransfer } from "@/api/lawyers/callTransfer"
import { listAgent } from "@/api/lawyers/callCenter"

export default {
  name: "CallTransfer",
  data() {
    return {
      loading: true,
      ids: [],
      multiple: true,
      showSearch: true,
      total: 0,
      transferList: [],
      agentOptions: [],
      title: "",
      open: false,
      detailOpen: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        recordId: undefined,
        fromAgentId: undefined,
        toAgentId: undefined
      },
      form: {},
      detailForm: {},
      rules: {
        recordId: [
          { required: true, message: "来电记录ID不能为空", trigger: "blur" }
        ],
        fromAgentId: [
          { required: true, message: "转出坐席不能为空", trigger: "change" }
        ],
        toAgentId: [
          { required: true, message: "转入坐席不能为空", trigger: "change" }
        ]
      }
    }
  },
  created() {
    this.loadAgents()
    this.getList()
  },
  methods: {
    loadAgents() {
      listAgent({ pageSize: 999, status: '1' }).then(response => {
        this.agentOptions = response.rows || []
      })
    },
    onFromAgentChange(val) {
      const a = this.agentOptions.find(x => x.agentId === val)
      this.$set(this.form, 'fromAgentName', a ? a.agentName : '')
    },
    onToAgentChange(val) {
      const a = this.agentOptions.find(x => x.agentId === val)
      this.$set(this.form, 'toAgentName', a ? a.agentName : '')
    },
    getList() {
      this.loading = true
      listTransfer(this.queryParams).then(response => {
        this.transferList = response.rows
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
        transferId: undefined,
        recordId: undefined,
        fromAgentId: undefined,
        fromAgentName: undefined,
        toAgentId: undefined,
        toAgentName: undefined,
        transferTime: undefined,
        reason: undefined,
        remark: undefined
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "新增转接记录"
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.transferId)
      this.multiple = !selection.length
    },
    handleDetail(row) {
      getTransfer(row.transferId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          addTransfer(this.form).then(response => {
            this.$modal.msgSuccess("新增成功")
            this.open = false
            this.getList()
          })
        }
      })
    },
    handleDelete(row) {
      const transferIds = row.transferId || this.ids
      this.$modal.confirm('是否确认删除转接记录编号为"' + transferIds + '"的数据项？').then(function() {
        return delTransfer(transferIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/call/transfer/export', {
        ...this.queryParams
      }, `transfer_${Date.now()}.xlsx`)
    }
  }
}
</script>

<style lang="scss" scoped>
@import '~@/assets/styles/call-center-light.scss';
</style>
