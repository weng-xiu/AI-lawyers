<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="88px">
      <el-form-item label="意图名称" prop="intentionName">
        <el-input
          v-model="queryParams.intentionName"
          placeholder="请输入意图名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="意图编码" prop="intentionCode">
        <el-input
          v-model="queryParams.intentionCode"
          placeholder="请输入意图编码"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="启用" value="0" />
          <el-option label="停用" value="1" />
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
          v-hasPermi="['lawyers:ivr:intention:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:ivr:intention:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="intentionList">
      <el-table-column label="意图名称" prop="intentionName" :show-overflow-tooltip="true" />
      <el-table-column label="意图编码" prop="intentionCode" width="140" />
      <el-table-column label="匹配关键词" prop="keywords" :show-overflow-tooltip="true" width="200" />
      <el-table-column label="优先级" prop="priority" width="80" align="center" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.status === '0'" type="success">启用</el-tag>
          <el-tag v-else type="danger">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:ivr:intention:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:ivr:intention:remove']"
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
      this.resetForm("queryForm")
      this.handleQuery()
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
      this.$modal.confirm('是否确认删除意图"' + row.intentionName + '"？').then(function() {
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
