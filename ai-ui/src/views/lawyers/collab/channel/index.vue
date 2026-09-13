<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="90px">
        <el-form-item label="档案ID" prop="profileId">
          <el-input v-model="queryParams.profileId" placeholder="来电档案ID" clearable style="width: 140px" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="渠道类型" prop="channelType">
          <el-select v-model="queryParams.channelType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定状态" prop="bindStatus">
          <el-select v-model="queryParams.bindStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="已绑定" value="0" />
            <el-option label="已解绑" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button v-hasPermi="['lawyers:channelIdentity:unbind']" type="primary" plain icon="el-icon-link" size="mini" @click="openBind">代客发起绑定</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="identityList" border size="small">
        <el-table-column label="ID" align="center" prop="id" width="80" />
        <el-table-column label="档案ID" align="center" prop="profileId" width="100" />
        <el-table-column label="渠道类型" align="center" width="130">
          <template slot-scope="scope">
            <el-tag size="mini">{{ channelText(scope.row.channelType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="渠道标识" align="center" prop="channelUid" min-width="220" show-overflow-tooltip />
        <el-table-column label="渠道昵称" align="center" prop="channelNickname" width="160" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.channelNickname || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.bindStatus === '0' ? 'success' : 'info'">{{ scope.row.bindStatus === '0' ? '已绑定' : '已解绑' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="绑定时间" align="center" prop="bindConfirmTime" width="160">
          <template slot-scope="scope">{{ scope.row.bindConfirmTime ? parseTime(scope.row.bindConfirmTime) : '-' }}</template>
        </el-table-column>
        <el-table-column label="解绑时间" align="center" prop="unbindTime" width="160">
          <template slot-scope="scope">{{ scope.row.unbindTime ? parseTime(scope.row.unbindTime) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="170" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-if="scope.row.bindStatus !== '0'"
              v-hasPermi="['lawyers:channelIdentity:unbind']"
              type="text" size="mini" icon="el-icon-check" @click="handleConfirm(scope.row)">确认绑定</el-button>
            <el-button
              v-if="scope.row.bindStatus === '0'"
              v-hasPermi="['lawyers:channelIdentity:unbind']"
              type="text" size="mini" icon="el-icon-unlink" style="color:#C63D4A" @click="handleUnbind(scope.row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog title="代客发起渠道绑定（待二次确认）" :visible.sync="bindOpen" width="480px" append-to-body>
      <el-form ref="bindForm" :model="bindForm" :rules="bindRules" label-width="100px" size="small">
        <el-form-item label="档案ID" prop="profileId">
          <el-input-number v-model="bindForm.profileId" :min="1" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item label="渠道类型" prop="channelType">
          <el-select v-model="bindForm.channelType" placeholder="请选择" style="width:100%">
            <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道标识" prop="channelUid">
          <el-input v-model="bindForm.channelUid" placeholder="openid / unionid / 账号" maxlength="100" />
        </el-form-item>
        <el-form-item label="渠道昵称">
          <el-input v-model="bindForm.channelNickname" maxlength="50" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" size="small" @click="submitBind">发 起</el-button>
        <el-button size="small" @click="bindOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listChannelIdentity, requestBind, confirmBind, unbindIdentity } from '@/api/lawyers/channelIdentity'

export default {
  name: 'CollabChannelIdentity',
  data() {
    return {
      loading: false,
      total: 0,
      identityList: [],
      queryParams: { pageNum: 1, pageSize: 10, profileId: undefined, channelType: undefined, bindStatus: undefined },
      channelOptions: [
        { value: 'PHONE', label: '电话' },
        { value: 'WECHAT_MP', label: '微信公众号' },
        { value: 'WECHAT_MINI', label: '微信小程序' },
        { value: 'H5', label: 'H5' },
        { value: 'WEB', label: '网页' }
      ],
      bindOpen: false,
      bindForm: { profileId: undefined, channelType: undefined, channelUid: undefined, channelNickname: undefined },
      bindRules: {
        profileId: [{ required: true, message: '请输入档案ID', trigger: 'blur' }],
        channelType: [{ required: true, message: '请选择渠道类型', trigger: 'change' }],
        channelUid: [{ required: true, message: '请输入渠道标识', trigger: 'blur' }]
      }
    }
  },
  created() { this.getList() },
  methods: {
    channelText(v) { const hit = this.channelOptions.find(i => i.value === v); return hit ? hit.label : (v || '-') },
    getList() {
      this.loading = true
      listChannelIdentity(this.queryParams).then(res => {
        this.identityList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams = { pageNum: 1, pageSize: 10, profileId: undefined, channelType: undefined, bindStatus: undefined }
      this.handleQuery()
    },
    openBind() {
      this.bindForm = { profileId: undefined, channelType: undefined, channelUid: undefined, channelNickname: undefined }
      this.$nextTick(() => { this.$refs.bindForm && this.$refs.bindForm.clearValidate() })
      this.bindOpen = true
    },
    submitBind() {
      this.$refs.bindForm.validate(valid => {
        if (!valid) return
        requestBind(this.bindForm).then(() => {
          this.$modal.msgSuccess('绑定申请已发起，待二次确认')
          this.bindOpen = false
          this.getList()
        })
      })
    },
    handleConfirm(row) {
      this.$modal.confirm('确认完成该渠道身份的二次绑定校验吗？').then(() => confirmBind(row.id, { channelNickname: row.channelNickname })).then(() => {
        this.$modal.msgSuccess('绑定已确认')
        this.getList()
      }).catch(() => {})
    },
    handleUnbind(row) {
      this.$modal.confirm('解绑后该渠道身份将无法继续关联本档案，确认解绑吗？（手机号过户/多人共用场景）').then(() => unbindIdentity(row.id)).then(() => {
        this.$modal.msgSuccess('已解绑')
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>
