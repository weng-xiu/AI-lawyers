<template>
  <div class="app-container cc-page">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="坐席名称" prop="agentName">
        <el-input
          v-model="queryParams.agentName"
          placeholder="请输入坐席名称"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="绑定用户" prop="userId">
        <el-select
          v-model="queryParams.userId"
          placeholder="选择系统用户"
          clearable
          filterable
          style="width: 200px"
        >
          <el-option
            v-for="u in userOptions"
            :key="u.userId"
            :label="u.nickName + '（' + u.userName + '）'"
            :value="u.userId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="坐席状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
          <el-option label="离线" value="0" />
          <el-option label="在线" value="1" />
          <el-option label="忙碌" value="2" />
          <el-option label="休息" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:call:agent:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['lawyers:call:agent:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:call:agent:remove']">删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="agentList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="坐席工号" align="center" prop="agentId" width="90" />
      <el-table-column label="坐席名称" align="center" prop="agentName" min-width="100" />
      <el-table-column label="绑定用户" align="center" min-width="140">
        <template slot-scope="scope">
          <span v-if="scope.row.userId">
            {{ scope.row.nickName || '-' }}
            <span style="color:#909399">（{{ scope.row.userName }}）</span>
          </span>
          <span v-else style="color:#c0c4cc">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column label="所属部门" align="center" prop="deptName" min-width="120" />
      <el-table-column label="手机号" align="center" prop="phonenumber" width="120" />
      <el-table-column label="SIP分机" align="center" prop="sipExtension" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" type="info" v-if="scope.row.sipExtension">{{ scope.row.sipExtension }}</el-tag>
          <span v-else style="color:#c0c4cc">--</span>
        </template>
      </el-table-column>
      <el-table-column label="坐席状态" align="center" prop="status" width="90">
        <template slot-scope="scope">
          <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="应答模式" align="center" width="90">
        <template slot-scope="scope">
          <span>{{ scope.row.callMode === '1' ? '手动' : '自动' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)"
            v-hasPermi="['lawyers:call:agent:query']">详情</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:call:agent:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:call:agent:remove']">删除</el-button>
          <el-button v-if="scope.row.status == '1'" size="mini" type="text" icon="el-icon-switch-button"
            @click="handleLogout(scope.row)"
            v-hasPermi="['lawyers:call:agent:logout']">强制下线</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="坐席名称" prop="agentName">
          <el-input v-model="form.agentName" placeholder="请输入坐席名称（如：1002）" />
        </el-form-item>
        <el-form-item label="绑定系统用户" prop="userId">
          <el-select
            v-model="form.userId"
            placeholder="请选择系统用户（律师/服务人员）"
            filterable
            clearable
            style="width: 100%"
            @change="onUserChange"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.userId"
              :label="u.nickName + '（' + u.userName + '）'"
              :value="u.userId"
            />
          </el-select>
          <div style="color:#909399;font-size:12px;line-height:1.4;margin-top:4px">
            服务人员/律师统一在【系统管理-用户管理】中维护。一个用户只能绑定一个坐席工号。
          </div>
        </el-form-item>
        <el-form-item label="SIP分机号" prop="sipExtension">
          <el-input v-model="form.sipExtension" placeholder="如 1002，需与 FreeSWITCH 中分机号一致" maxlength="20" />
        </el-form-item>
        <el-form-item label="应答模式" prop="callMode">
          <el-radio-group v-model="form.callMode">
            <el-radio label="0">自动应答</el-radio>
            <el-radio label="1">手动应答</el-radio>
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

    <el-dialog title="坐席详情" :visible.sync="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="坐席工号">{{ detailForm.agentId }}</el-descriptions-item>
        <el-descriptions-item label="坐席名称">{{ detailForm.agentName }}</el-descriptions-item>
        <el-descriptions-item label="绑定用户">
          {{ detailForm.nickName || '-' }}<span v-if="detailForm.userName">（{{ detailForm.userName }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="所属部门">{{ detailForm.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailForm.phonenumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="SIP分机号">{{ detailForm.sipExtension || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应答模式">{{ detailForm.callMode === '1' ? '手动应答' : '自动应答' }}</el-descriptions-item>
        <el-descriptions-item label="坐席状态">
          <el-tag :type="getStatusType(detailForm.status)">{{ getStatusLabel(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="登录时间">{{ parseTime(detailForm.loginTime) }}</el-descriptions-item>
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
import { listAgent, getAgent, addAgent, updateAgent, delAgent, agentLogout } from "@/api/lawyers/callCenter"
import { listUser } from "@/api/system/user"

export default {
  name: "CallAgent",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      agentList: [],
      userOptions: [],
      title: "",
      open: false,
      detailOpen: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        agentName: undefined,
        userId: undefined,
        status: undefined
      },
      form: {},
      detailForm: {},
      rules: {
        agentName: [{ required: true, message: "坐席名称不能为空", trigger: "blur" }],
        userId: [{ required: true, message: "请选择绑定的系统用户", trigger: "change" }],
        sipExtension: [
          { required: true, message: "SIP分机号不能为空", trigger: "blur" },
          { pattern: /^[0-9a-zA-Z]{2,20}$/, message: "分机号为 2-20 位数字或字母", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
    this.loadUsers()
  },
  methods: {
    getList() {
      this.loading = true
      listAgent(this.queryParams).then(response => {
        this.agentList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    // 加载系统用户列表用于下拉选择（律师/服务人员统一来自 sys_user）
    loadUsers() {
      listUser({ pageNum: 1, pageSize: 1000, status: '0' }).then(response => {
        this.userOptions = response.rows || []
      })
    },
    onUserChange(userId) {
      // 选中用户后若未填坐席名称，自动用昵称兜底
      if (userId && !this.form.agentName) {
        const u = this.userOptions.find(x => x.userId === userId)
        if (u) this.form.agentName = u.nickName || u.userName
      }
    },
    getStatusType(status) {
      const types = { '0': 'info', '1': 'success', '2': 'warning', '3': 'danger' }
      return types[status] || 'info'
    },
    getStatusLabel(status) {
      const labels = { '0': '离线', '1': '在线', '2': '忙碌', '3': '休息' }
      return labels[status] || '未知'
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        agentId: undefined,
        agentName: undefined,
        userId: undefined,
        sipExtension: undefined,
        callMode: '0',
        status: '0',
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
      this.title = "新增坐席"
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.agentId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    handleUpdate(row) {
      this.reset()
      const agentId = row.agentId || this.ids
      getAgent(agentId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改坐席"
      })
    },
    handleDetail(row) {
      getAgent(row.agentId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    handleLogout(row) {
      this.$modal.confirm('是否确认强制下线坐席 "' + row.agentName + '"？').then(function() {
        return agentLogout({ agentId: row.agentId })
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("操作成功")
      }).catch(() => {})
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.agentId != undefined) {
            updateAgent(this.form).then(() => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addAgent(this.form).then(() => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      const agentIds = row.agentId || this.ids
      this.$modal.confirm('是否确认删除坐席编号为"' + agentIds + '"的数据项？').then(function() {
        return delAgent(agentIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
@import '~@/assets/styles/call-center-light.scss';
</style>
