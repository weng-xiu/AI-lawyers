<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="videoList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :stripe="false"
      has-selection
      label-width="68px"
      empty-text="暂无视频咨询"
      empty-hint="客户发起视频咨询后在此查看，进行中的会话可点击加入"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:videoConsult:add']">新增</el-button>
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:videoConsult:remove']">删除</el-button>
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:videoConsult:export']">导出</el-button>
      </template>
      <template #query-dateRange>
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
          range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </template>
      <template #column-status="{ row }">
        <el-tag size="mini" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
      </template>
      <template #column-duration="{ row }">{{ formatDuration(row.duration) }}</template>
      <template #column-satisfaction="{ row }">{{ row.satisfaction ? row.satisfaction + '分' : '-' }}</template>
      <template #column-identityVerify="{ row }">{{ row.identityVerify === '1' ? '已核验' : '未核验' }}</template>
      <template #column-startTime="{ row }">{{ parseTime(row.startTime) }}</template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(row)"
          v-hasPermi="['lawyers:videoConsult:query']">详情</el-button>
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)"
          v-hasPermi="['lawyers:videoConsult:edit']">修改</el-button>
        <el-button size="mini" type="text" icon="el-icon-video-play" @click="handleJoin(row)"
          v-if="row.status === '1'">加入</el-button>
      </template>
    </ProTable>

    <!-- 添加/修改弹窗 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="客户姓名" prop="customerName">
          <el-input v-model="form.customerName" placeholder="请输入客户姓名" />
        </el-form-item>
        <el-form-item label="客户电话" prop="customerPhone">
          <el-input v-model="form.customerPhone" placeholder="请输入客户电话" />
        </el-form-item>
        <el-form-item label="房间号" prop="roomNo">
          <el-input v-model="form.roomNo" placeholder="请输入/自动生成房间号" />
        </el-form-item>
        <el-form-item label="身份核验">
          <el-radio-group v-model="form.identityVerify">
            <el-radio label="0">未核验</el-radio>
            <el-radio label="1">已核验</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="接待坐席" prop="agentId">
          <el-select v-model="form.agentId" placeholder="请选择接待坐席" filterable clearable style="width:100%" @change="onAgentChange">
            <el-option v-for="a in agentOptions" :key="a.agentId" :label="a.agentName+'('+a.agentId+')'" :value="a.agentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="录像地址">
          <el-input v-model="form.recordingUrl" placeholder="请输入录像地址（可选）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </div>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog title="视频咨询详情" :visible.sync="detailOpen" width="700px" append-to-body>
      <el-descriptions :column="2" border v-if="detailForm.consultId">
        <el-descriptions-item label="咨询编号">{{ detailForm.consultNo }}</el-descriptions-item>
        <el-descriptions-item label="房间号">{{ detailForm.roomNo }}</el-descriptions-item>
        <el-descriptions-item label="客户姓名">{{ detailForm.customerName }}</el-descriptions-item>
        <el-descriptions-item label="客户电话">{{ detailForm.customerPhone }}</el-descriptions-item>
        <el-descriptions-item label="坐席">{{ detailForm.agentName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="mini" :type="statusTag(detailForm.status)">{{ statusText(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ parseTime(detailForm.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ parseTime(detailForm.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="时长">{{ formatDuration(detailForm.duration) }}</el-descriptions-item>
        <el-descriptions-item label="满意度">{{ detailForm.satisfaction ? detailForm.satisfaction + '分' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份核验">{{ detailForm.identityVerify === '1' ? '已核验' : '未核验' }}</el-descriptions-item>
        <el-descriptions-item label="录像地址" :span="2">{{ detailForm.recordingUrl || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailForm.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer"><el-button @click="detailOpen = false">关 闭</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { listVideoConsult, getVideoConsult, addVideoConsult, updateVideoConsult, delVideoConsult } from "@/api/lawyers/videoConsult"
import { listAgent } from "@/api/lawyers/callCenter"

export default {
  name: "VideoConsult",
  data() {
    return {
      loading: true, ids: [], single: true, multiple: true, showSearch: true, total: 0,
      videoList: [], title: "", open: false, detailOpen: false, dateRange: [],
      // V3：ProTable 列定义（状态/时长/满意度/核验/开始时间/操作走插槽）
      columns: [
        { prop: 'consultNo', label: '咨询编号', width: 160, align: 'center' },
        { prop: 'customerName', label: '客户姓名', align: 'center' },
        { prop: 'customerPhone', label: '客户电话', width: 130, align: 'center' },
        { prop: 'agentName', label: '坐席', align: 'center' },
        { prop: 'roomNo', label: '房间号', width: 120, align: 'center' },
        { prop: 'status', label: '状态', width: 90, align: 'center', slot: true },
        { prop: 'duration', label: '时长', width: 90, align: 'center', slot: true },
        { prop: 'satisfaction', label: '满意度', width: 80, align: 'center', slot: true },
        { prop: 'identityVerify', label: '核验', width: 70, align: 'center', slot: true },
        { prop: 'startTime', label: '开始时间', width: 160, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 200, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      agentOptions: [],
      queryParams: { pageNum: 1, pageSize: 10, consultNo: undefined, customerName: undefined, customerPhone: undefined, status: undefined },
      form: { identityVerify: '0' },
      detailForm: {},
      rules: {
        customerName: [{ required: true, message: "客户姓名不能为空", trigger: "blur" }],
        customerPhone: [{ required: true, message: "客户电话不能为空", trigger: "blur" }]
      }
    }
  },
  created() { this.getList(); this.loadAgents() },
  computed: {
    queryFormItems() {
      return [
        { prop: 'consultNo', label: '咨询编号', type: 'input', placeholder: '请输入咨询编号' },
        { prop: 'customerName', label: '客户姓名', type: 'input', placeholder: '请输入客户姓名' },
        { prop: 'customerPhone', label: '客户电话', type: 'input', placeholder: '请输入客户电话' },
        {
          prop: 'status',
          label: '状态',
          type: 'select',
          placeholder: '请选择状态',
          width: 140,
          options: [
            { label: '等待中', value: '0' },
            { label: '进行中', value: '1' },
            { label: '已结束', value: '2' },
            { label: '已取消', value: '3' }
          ]
        },
        // 开始时间保留独立 dateRange + addDateRange，走查询插槽
        { prop: 'dateRange', label: '开始时间', slot: true }
      ]
    }
  },
  methods: {
    loadAgents() {
      listAgent({ pageNum: 1, pageSize: 1000 }).then(res => { this.agentOptions = res.rows || [] })
    },
    onAgentChange(val) {
      const a = this.agentOptions.find(x => x.agentId === val)
      this.$set(this.form, 'agentName', a ? a.agentName : '')
    },
    getList() {
      this.loading = true
      const params = { ...this.queryParams, ...this.addDateRange(this.queryParams, this.dateRange) }
      listVideoConsult(params).then(res => { this.videoList = res.rows; this.total = res.total; this.loading = false })
    },
    cancel() { this.open = false; this.reset() },
    reset() {
      this.form = { consultId: undefined, customerName: undefined, customerPhone: undefined, roomNo: undefined, identityVerify: '0', recordingUrl: undefined, remark: undefined }
      this.resetForm("form")
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需清空开始时间区间并重设筛选后重查
      this.dateRange = []
      this.queryParams = { pageNum: 1, pageSize: 10, consultNo: undefined, customerName: undefined, customerPhone: undefined, status: undefined }
      this.getList()
    },
    handleSelectionChange(selection) { this.ids = selection.map(item => item.consultId); this.single = selection.length !== 1; this.multiple = !selection.length },
    handleAdd() { this.reset(); this.open = true; this.title = "新增视频咨询" },
    handleUpdate(row) {
      this.reset()
      getVideoConsult(row.consultId || this.ids[0]).then(res => { this.form = res.data; this.open = true; this.title = "修改视频咨询" })
    },
    handleDetail(row) {
      getVideoConsult(row.consultId).then(res => { this.detailForm = res.data; this.detailOpen = true })
    },
    handleJoin(row) { this.$message.info('视频通话功能需要集成WebRTC，当前版本暂不支持直接进入房间') },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.consultId) {
            updateVideoConsult(this.form).then(() => { this.$modal.msgSuccess("修改成功"); this.open = false; this.getList() })
          } else {
            addVideoConsult(this.form).then(() => { this.$modal.msgSuccess("新增成功"); this.open = false; this.getList() })
          }
        }
      })
    },
    handleDelete(row) {
      const consultIds = row.consultId || this.ids
      const count = row.consultId ? 1 : (this.ids || []).length
      this.$modal.dangerConfirm({
        content: '将删除选中的视频咨询记录',
        count: count,
        countNoun: '条记录'
      }).then(() => {
        return delVideoConsult(consultIds)
      }).then(() => { this.getList(); this.$modal.msgSuccess("删除成功") }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/videoConsult/export', { ...this.queryParams }, `video_consult_${new Date().getTime()}.xlsx`)
    },
    statusText(s) { return { '0': '等待中', '1': '进行中', '2': '已结束', '3': '已取消' }[s] || '未知' },
    statusTag(s) { return { '0': 'info', '1': 'success', '2': 'warning', '3': 'danger' }[s] || 'info' },
    formatDuration(s) {
      s = parseInt(s) || 0
      if (s <= 0) return '-'
      const m = Math.floor(s / 60)
      const sec = s % 60
      if (m > 0) return m + '分' + sec + '秒'
      return sec + '秒'
    }
  }
}
</script>
