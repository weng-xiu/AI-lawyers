<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="咨询编号" prop="consultNo">
        <el-input v-model="queryParams.consultNo" placeholder="请输入咨询编号" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="客户姓名" prop="customerName">
        <el-input v-model="queryParams.customerName" placeholder="请输入客户姓名" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="客户电话" prop="customerPhone">
        <el-input v-model="queryParams.customerPhone" placeholder="请输入客户电话" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
          <el-option label="等待中" value="0" />
          <el-option label="进行中" value="1" />
          <el-option label="已结束" value="2" />
          <el-option label="已取消" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始时间">
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
          range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:videoConsult:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:videoConsult:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:videoConsult:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="videoList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="咨询编号" align="center" prop="consultNo" width="160" />
      <el-table-column label="客户姓名" align="center" prop="customerName" />
      <el-table-column label="客户电话" align="center" prop="customerPhone" width="130" />
      <el-table-column label="坐席" align="center" prop="agentName" />
      <el-table-column label="房间号" align="center" prop="roomNo" width="120" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusTag(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时长" align="center" prop="duration" width="90">
        <template slot-scope="scope">{{ formatDuration(scope.row.duration) }}</template>
      </el-table-column>
      <el-table-column label="满意度" align="center" prop="satisfaction" width="80">
        <template slot-scope="scope">{{ scope.row.satisfaction ? scope.row.satisfaction + '分' : '-' }}</template>
      </el-table-column>
      <el-table-column label="核验" align="center" prop="identityVerify" width="70">
        <template slot-scope="scope">{{ scope.row.identityVerify === '1' ? '已核验' : '未核验' }}</template>
      </el-table-column>
      <el-table-column label="开始时间" align="center" prop="startTime" width="160">
        <template slot-scope="scope">{{ parseTime(scope.row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)"
            v-hasPermi="['lawyers:videoConsult:query']">详情</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:videoConsult:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-video-play" @click="handleJoin(scope.row)"
            v-if="scope.row.status === '1'">加入</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

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

export default {
  name: "VideoConsult",
  data() {
    return {
      loading: true, ids: [], single: true, multiple: true, showSearch: true, total: 0,
      videoList: [], title: "", open: false, detailOpen: false, dateRange: [],
      queryParams: { pageNum: 1, pageSize: 10, consultNo: undefined, customerName: undefined, customerPhone: undefined, status: undefined },
      form: { identityVerify: '0' },
      detailForm: {},
      rules: {
        customerName: [{ required: true, message: "客户姓名不能为空", trigger: "blur" }],
        customerPhone: [{ required: true, message: "客户电话不能为空", trigger: "blur" }]
      }
    }
  },
  created() { this.getList() },
  methods: {
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
    resetQuery() { this.dateRange = []; this.resetForm("queryForm"); this.handleQuery() },
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
      this.$modal.confirm('确认删除编号为"' + consultIds + '"的视频咨询记录？').then(() => {
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
