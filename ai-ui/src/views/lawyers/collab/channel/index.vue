<template>
  <div class="app-container">
    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="identityList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :table-attrs="{ border: true }"
      label-width="90px"
      empty-text="暂无渠道身份绑定记录"
      empty-hint="坐席可按来电档案发起渠道绑定，待二次确认后生效"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <template #toolbar-left>
        <el-button v-hasPermi="['lawyers:channelIdentity:unbind']" type="primary" plain icon="el-icon-link" size="mini" @click="openBind">代客发起绑定</el-button>
      </template>
      <template #column-channelType="{ row }">
        <el-tag size="mini">{{ channelText(row.channelType) }}</el-tag>
      </template>
      <template #column-channelNickname="{ row }">{{ row.channelNickname || '-' }}</template>
      <template #column-bindStatus="{ row }">
        <el-tag size="mini" :type="row.bindStatus === '0' ? 'success' : 'info'">{{ row.bindStatus === '0' ? '已绑定' : '已解绑' }}</el-tag>
      </template>
      <template #column-bindConfirmTime="{ row }">{{ row.bindConfirmTime ? parseTime(row.bindConfirmTime) : '-' }}</template>
      <template #column-unbindTime="{ row }">{{ row.unbindTime ? parseTime(row.unbindTime) : '-' }}</template>
      <template #column-action="{ row }">
        <el-button
          v-if="row.bindStatus !== '0'"
          v-hasPermi="['lawyers:channelIdentity:unbind']"
          type="text" size="mini" icon="el-icon-check" @click="handleConfirm(row)">确认绑定</el-button>
        <el-button
          v-if="row.bindStatus === '0'"
          v-hasPermi="['lawyers:channelIdentity:unbind']"
          type="text" size="mini" icon="el-icon-unlink" style="color:#C63D4A" @click="handleUnbind(row)">解绑</el-button>
      </template>
    </ProTable>

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
      showSearch: true,
      total: 0,
      identityList: [],
      // V3：ProTable 列定义（渠道类型/昵称/状态/时间/操作走插槽）
      columns: [
        { prop: 'id', label: 'ID', width: 80 },
        { prop: 'profileId', label: '档案ID', width: 100 },
        { prop: 'channelType', label: '渠道类型', width: 130, slot: true },
        { prop: 'channelUid', label: '渠道标识', minWidth: 220, showOverflowTooltip: true },
        { prop: 'channelNickname', label: '渠道昵称', width: 160, slot: true },
        { prop: 'bindStatus', label: '状态', width: 100, slot: true },
        { prop: 'bindConfirmTime', label: '绑定时间', width: 160, slot: true },
        { prop: 'unbindTime', label: '解绑时间', width: 160, slot: true },
        { prop: 'action', label: '操作', width: 170, slot: true, className: 'small-padding fixed-width' }
      ],
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
  computed: {
    /** V3：ProTable 查询字段 */
    queryFormItems() {
      return [
        { prop: 'profileId', label: '档案ID', placeholder: '来电档案ID', width: '140px' },
        { prop: 'channelType', label: '渠道类型', type: 'select', placeholder: '全部', width: '150px', options: this.channelOptions },
        { prop: 'bindStatus', label: '绑定状态', type: 'select', placeholder: '全部', width: '120px', options: [
          { label: '已绑定', value: '0' }, { label: '已解绑', value: '1' }
        ] }
      ]
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, profileId: undefined, channelType: undefined, bindStatus: undefined }
      this.getList()
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
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认完成该渠道身份的二次绑定校验吗？',
        tip: '确认后该渠道身份将绑定至当前账号，请先核对昵称与归属真实'
      }).then(() => confirmBind(row.id, { channelNickname: row.channelNickname })).then(() => {
        this.$modal.msgSuccess('绑定已确认')
        this.getList()
      }).catch(() => {})
    },
    handleUnbind(row) {
      this.$modal.dangerConfirm({
        content: '将解除该渠道身份与当前档案的关联（手机号过户/多人共用场景）',
        count: 1,
        countNoun: '条绑定关系',
        tip: '解绑后该渠道身份将无法继续关联本档案，需重新走绑定校验'
      }).then(() => unbindIdentity(row.id)).then(() => {
        this.$modal.msgSuccess('已解绑')
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>
