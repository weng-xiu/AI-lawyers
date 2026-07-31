<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="88px">
      <el-form-item label="流程名称" prop="flowName">
        <el-input
          v-model="queryParams.flowName"
          placeholder="请输入流程名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="流程编码" prop="flowCode">
        <el-input
          v-model="queryParams.flowCode"
          placeholder="请输入流程编码"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="草稿" value="0" />
          <el-option label="已发布" value="1" />
          <el-option label="已停用" value="2" />
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
          v-hasPermi="['lawyers:ivr:flow:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:ivr:flow:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="flowList">
      <el-table-column label="流程名称" prop="flowName" :show-overflow-tooltip="true" />
      <el-table-column label="流程编码" prop="flowCode" :show-overflow-tooltip="true" width="140" />
      <el-table-column label="版本号" prop="version" width="80" align="center" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.status === '0'" type="info">草稿</el-tag>
          <el-tag v-else-if="scope.row.status === '1'" type="success">已发布</el-tag>
          <el-tag v-else type="danger">已停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="是否默认" align="center" prop="isDefault" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.isDefault === '1'" type="success">是</el-tag>
          <span v-else>否</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="280">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit-outline"
            @click="handleDesign(scope.row)"
            v-hasPermi="['lawyers:ivr:flow:edit']"
          >设计</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:ivr:flow:edit']"
          >修改</el-button>
          <el-button
            v-if="scope.row.status === '0'"
            size="mini"
            type="text"
            icon="el-icon-check"
            @click="handlePublish(scope.row)"
            v-hasPermi="['lawyers:ivr:flow:publish']"
          >发布</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:ivr:flow:remove']"
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
        <el-form-item label="流程名称" prop="flowName">
          <el-input v-model="form.flowName" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="流程编码" prop="flowCode">
          <el-input v-model="form.flowCode" placeholder="请输入流程编码" />
        </el-form-item>
        <el-form-item label="流程描述" prop="flowDesc">
          <el-input v-model="form.flowDesc" type="textarea" :rows="3" placeholder="请输入流程描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="0">草稿</el-radio>
            <el-radio label="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设为默认" prop="isDefault">
          <el-radio-group v-model="form.isDefault">
            <el-radio label="0">否</el-radio>
            <el-radio label="1">是</el-radio>
          </el-radio-group>
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
import { listFlow, getFlow, delFlow, addFlow, updateFlow, publishFlow } from "@/api/lawyers/ivrFlow"

export default {
  name: "IvrFlow",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      flowList: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        flowName: undefined,
        flowCode: undefined,
        status: undefined
      },
      form: {},
      rules: {
        flowName: [
          { required: true, message: "流程名称不能为空", trigger: "blur" }
        ],
        flowCode: [
          { required: true, message: "流程编码不能为空", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listFlow(this.queryParams).then(response => {
        this.flowList = response.rows
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
        flowId: null,
        flowName: null,
        flowCode: null,
        flowDesc: null,
        status: "0",
        isDefault: "0"
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
      this.title = "添加IVR流程"
    },
    handleDesign(row) {
      this.$message.info("流程设计器功能：请在独立页面进行IVR流程图设计")
    },
    handleUpdate(row) {
      this.reset()
      getFlow(row.flowId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改IVR流程"
      })
    },
    handlePublish(row) {
      this.$modal.confirm('是否确认发布流程"' + row.flowName + '"？').then(function() {
        return publishFlow(row.flowId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("发布成功")
      }).catch(() => {})
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.flowId != null) {
            updateFlow(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addFlow(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      this.$modal.confirm('是否确认删除流程"' + row.flowName + '"？').then(function() {
        return delFlow(row.flowId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/ivr/flow/export', {
        ...this.queryParams
      }, `ivr_flow_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
