<template>
  <div class="app-container schedule-page">
    <el-tabs v-model="activeTab" type="card" @tab-click="handleTabClick">
      <!-- ========== Tab1: 班次管理 ========== -->
      <el-tab-pane label="班次管理" name="shift">
        <!-- V3/U：班次表收敛到 ProTable（无查询区、无分页，移除原 fixed="right"） -->
        <ProTable
          :columns="shiftColumns"
          :query-params="shiftQuery"
          :data="shiftList"
          :loading="shiftLoading"
          :show-search="false"
          :stripe="false"
          :table-attrs="{ border: true }"
          hide-pagination
          persist-key="schedule:shift"
          empty-text="暂无班次"
          empty-hint="新增班次后可用于批量排班与签到签退"
          @query="getShiftList">
          <template #toolbar-left>
            <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAddShift">新增班次</el-button>
          </template>
          <template #column-crossDay="{ row }">
            <el-tag :type="row.crossDay === '1' || row.crossDay === 1 ? 'warning' : 'info'" size="mini">
              {{ (row.crossDay === '1' || row.crossDay === 1) ? '跨天' : '当日' }}
            </el-tag>
          </template>
          <template #column-breakMinutes="{ row }">{{ row.breakMinutes != null ? row.breakMinutes : '-' }}</template>
          <template #column-status="{ row }">
            <el-switch v-model="row.status" active-value="1" inactive-value="0" @change="handleShiftStatusChange(row)" />
          </template>
          <template #column-remark="{ row }">{{ row.remark || '-' }}</template>
          <template #column-action="{ row }">
            <el-button type="text" size="mini" icon="el-icon-edit" @click="handleUpdateShift(row)">编辑</el-button>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDeleteShift(row)">删除</el-button>
          </template>
        </ProTable>
      </el-tab-pane>

      <!-- ========== Tab2: 排班日历 ========== -->
      <el-tab-pane label="排班管理" name="schedule">
        <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
        <ProTable
          :columns="scheduleColumns"
          :query-form-items="scheduleQueryFormItems"
          :query-params="scheduleQuery"
          :data="scheduleList"
          :loading="scheduleLoading"
          :total="scheduleTotal"
          :stripe="false"
          :table-attrs="{ border: true }"
          label-width="80px"
          persist-key="schedule:list"
          empty-text="暂无排班记录"
          empty-hint="可按班次与坐席筛选，或使用批量排班创建"
          @query="getScheduleList"
          @reset="resetScheduleQuery"
          @pagination="getScheduleList">
          <template #query-scheduleDateRange>
            <el-date-picker
              v-model="scheduleDateRange"
              style="width: 260px"
              value-format="yyyy-MM-dd"
              type="daterange"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
            />
          </template>
          <template #query-extra>
            <el-button type="success" icon="el-icon-date" size="mini" @click="loadToday">今日排班</el-button>
          </template>
          <template #toolbar-left>
            <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="openBatchDialog">批量排班</el-button>
          </template>
          <template #column-agentName="{ row }">{{ row.agentName || '-' }}</template>
          <template #column-shiftName="{ row }">{{ row.shiftName || '-' }}</template>
          <template #column-scheduleType="{ row }">
            <el-tag size="mini" :type="scheduleTypeTag(row.scheduleType)">{{ scheduleTypeLabel(row.scheduleType) }}</el-tag>
          </template>
          <template #column-checkInTime="{ row }">{{ row.checkInTime || '-' }}</template>
          <template #column-checkOutTime="{ row }">{{ row.checkOutTime || '-' }}</template>
          <template #column-status="{ row }">
            <el-tag size="mini" :type="scheduleStatusTag(row.status)">{{ scheduleStatusLabel(row.status) }}</el-tag>
          </template>
          <template #column-action="{ row }">
            <el-button
              v-if="!row.checkInTime"
              type="text"
              size="mini"
              icon="el-icon-right"
              @click="handleCheckIn(row)"
            >签到</el-button>
            <el-button
              v-if="row.checkInTime && !row.checkOutTime"
              type="text"
              size="mini"
              icon="el-icon-back"
              @click="handleCheckOut(row)"
            >签退</el-button>
            <el-button type="text" size="mini" icon="el-icon-edit" @click="handleUpdateSchedule(row)">编辑</el-button>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDeleteSchedule(row)">删除</el-button>
          </template>
        </ProTable>
      </el-tab-pane>

      <!-- ========== Tab3: 15 分钟预测与对比验收（F9） ========== -->
      <el-tab-pane label="15分钟预测" name="forecast">
        <forecast-slot v-if="activeTab === 'forecast'" />
      </el-tab-pane>

      <!-- ========== Tab4: 换班申请与审批（F9） ========== -->
      <el-tab-pane label="换班审批" name="shiftChange">
        <shift-change v-if="activeTab === 'shiftChange'" />
      </el-tab-pane>
    </el-tabs>

    <!-- 班次新增/编辑对话框 -->
    <el-dialog :title="shiftTitle" :visible.sync="shiftOpen" width="520px" append-to-body>
      <el-form ref="shiftForm" :model="shiftForm" :rules="shiftRules" label-width="100px" size="small">
        <el-form-item label="班次名称" prop="shiftName">
          <el-input v-model="shiftForm.shiftName" placeholder="如：早班" maxlength="50" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="上班时间" prop="startTime">
              <el-time-picker
                v-model="shiftForm.startTime"
                value-format="HH:mm:ss"
                format="HH:mm"
                placeholder="上班时间"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下班时间" prop="endTime">
              <el-time-picker
                v-model="shiftForm.endTime"
                value-format="HH:mm:ss"
                format="HH:mm"
                placeholder="下班时间"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="是否跨天">
          <el-switch v-model="shiftForm.crossDay" active-value="1" inactive-value="0" active-text="跨天" inactive-text="当日" />
        </el-form-item>
        <el-form-item label="休息时长(分)">
          <el-input-number v-model="shiftForm.breakMinutes" :min="0" :max="600" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="shiftForm.status" active-value="1" inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="shiftForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitShiftForm">确 定</el-button>
        <el-button size="small" @click="shiftOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 批量排班对话框 -->
    <el-dialog :title="scheduleTitle" :visible.sync="scheduleOpen" width="560px" append-to-body>
      <el-form ref="scheduleFormRef" :model="scheduleForm" :rules="scheduleRules" label-width="100px" size="small">
        <el-form-item label="班次" prop="shiftId">
          <el-select v-model="scheduleForm.shiftId" placeholder="请选择班次" filterable style="width:100%">
            <el-option v-for="s in enabledShiftList" :key="s.shiftId" :label="s.shiftName + '（' + s.startTime + '-' + s.endTime + '）'" :value="s.shiftId" />
          </el-select>
        </el-form-item>
        <el-form-item label="坐席" prop="agentIds">
          <el-select v-model="scheduleForm.agentIds" multiple filterable placeholder="请选择坐席（可多选）" style="width:100%">
            <el-option v-for="a in agentList" :key="a.agentId" :label="a.agentName + '（' + a.agentId + '）'" :value="a.agentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围" prop="dateRange">
          <el-date-picker
            v-model="scheduleForm.dateRange"
            style="width: 100%"
            value-format="yyyy-MM-dd"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="排班类型" prop="scheduleType">
          <el-radio-group v-model="scheduleForm.scheduleType">
            <el-radio label="1">正常班</el-radio>
            <el-radio label="2">加班</el-radio>
            <el-radio label="3">调班</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="scheduleForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitScheduleForm">确 定</el-button>
        <el-button size="small" @click="scheduleOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listShift, getShift, addShift, updateShift, delShift, listEnabledShifts,
  listSchedule, getSchedule, addSchedule, updateSchedule, delSchedule,
  batchSchedule, checkIn, checkOut, todaySchedule
} from '@/api/lawyers/schedule'
import { listAgent } from '@/api/lawyers/callCenter'
import ForecastSlot from './forecastSlot.vue'
import ShiftChange from './shiftChange.vue'

export default {
  name: 'Schedule',
  components: { ForecastSlot, ShiftChange },
  data() {
    return {
      activeTab: 'shift',
      // 班次
      shiftLoading: false,
      shiftList: [],
      // 班次表无查询区/分页，仅为满足 ProTable 的 queryParams 契约
      shiftQuery: { pageNum: 1, pageSize: 10 },
      // V3：班次表列定义（跨天/休息时长/状态/备注/操作走插槽）
      shiftColumns: [
        { prop: 'shiftName', label: '班次名称', minWidth: 140 },
        { prop: 'startTime', label: '上班时间', width: 110 },
        { prop: 'endTime', label: '下班时间', width: 110 },
        { prop: 'crossDay', label: '是否跨天', width: 100, slot: true },
        { prop: 'breakMinutes', label: '休息时间(分)', width: 110, slot: true },
        { prop: 'status', label: '状态', width: 90, slot: true },
        { prop: 'remark', label: '备注', minWidth: 120, showOverflowTooltip: true, slot: true },
        { prop: 'action', label: '操作', width: 160, slot: true }
      ],
      enabledShiftList: [],
      shiftOpen: false,
      shiftTitle: '',
      shiftForm: {},
      shiftRules: {
        shiftName: [{ required: true, message: '班次名称不能为空', trigger: 'blur' }],
        startTime: [{ required: true, message: '请选择上班时间', trigger: 'change' }],
        endTime: [{ required: true, message: '请选择下班时间', trigger: 'change' }]
      },
      // 排班
      scheduleLoading: false,
      scheduleList: [],
      scheduleTotal: 0,
      // V3：排班表列定义（坐席/班次/类型/签到签退/状态/操作走插槽，移除原 fixed="right"）
      scheduleColumns: [
        { prop: 'scheduleId', label: '排班ID', width: 90 },
        { prop: 'agentName', label: '坐席', width: 120, slot: true },
        { prop: 'shiftName', label: '班次', width: 140, slot: true },
        { prop: 'scheduleDate', label: '排班日期', width: 120 },
        { prop: 'scheduleType', label: '排班类型', width: 100, slot: true },
        { prop: 'checkInTime', label: '签到时间', width: 160, slot: true },
        { prop: 'checkOutTime', label: '签退时间', width: 160, slot: true },
        { prop: 'status', label: '状态', width: 100, slot: true },
        { prop: 'action', label: '操作', width: 220, slot: true }
      ],
      scheduleDateRange: [],
      scheduleQuery: {
        pageNum: 1,
        pageSize: 10,
        shiftId: undefined,
        agentId: undefined
      },
      scheduleOpen: false,
      scheduleTitle: '',
      scheduleForm: {},
      scheduleRules: {
        shiftId: [{ required: true, message: '请选择班次', trigger: 'change' }],
        agentIds: [{ required: true, message: '请选择坐席', trigger: 'change', type: 'array' }],
        dateRange: [{ required: true, message: '请选择日期范围', trigger: 'change', type: 'array' }],
        scheduleType: [{ required: true, message: '请选择排班类型', trigger: 'change' }]
      },
      agentList: []
    }
  },
  computed: {
    /** V3：排班查询项（班次/坐席下拉取自启用班次与坐席清单，日期范围走查询插槽） */
    scheduleQueryFormItems() {
      return [
        {
          prop: 'shiftId', label: '班次', type: 'select', placeholder: '全部', width: 160, filterable: true,
          options: (this.enabledShiftList || []).map(s => ({ label: s.shiftName, value: s.shiftId }))
        },
        {
          prop: 'agentId', label: '坐席', type: 'select', placeholder: '全部坐席', width: 160, filterable: true,
          options: (this.agentList || []).map(a => ({ label: a.agentName, value: a.agentId }))
        },
        { prop: 'scheduleDateRange', label: '日期范围', slot: true }
      ]
    }
  },
  created() {
    this.getShiftList()
    this.loadEnabledShifts()
    this.loadAgents()
    this.initDefaultDateRange()
    this.getScheduleList()
  },
  methods: {
    handleTabClick() {},
    initDefaultDateRange() {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 7)
      const fmt = d => {
        const y = d.getFullYear()
        const m = (d.getMonth() + 1).toString().padStart(2, '0')
        const day = d.getDate().toString().padStart(2, '0')
        return y + '-' + m + '-' + day
      }
      this.scheduleDateRange = [fmt(start), fmt(end)]
    },
    // ==================== 班次 ====================
    getShiftList() {
      this.shiftLoading = true
      listShift({ pageNum: 1, pageSize: 100 }).then(res => {
        this.shiftList = res.rows || []
        this.shiftLoading = false
      }).catch(() => { this.shiftLoading = false })
    },
    loadEnabledShifts() {
      listEnabledShifts().then(res => {
        this.enabledShiftList = res.data || res.rows || []
      })
    },
    resetShiftForm() {
      this.shiftForm = {
        shiftId: undefined,
        shiftName: undefined,
        startTime: '09:00:00',
        endTime: '18:00:00',
        crossDay: '0',
        breakMinutes: 60,
        status: '1',
        remark: undefined
      }
      this.$nextTick(() => { this.$refs.shiftForm && this.$refs.shiftForm.clearValidate() })
    },
    handleAddShift() {
      this.resetShiftForm()
      this.shiftOpen = true
      this.shiftTitle = '新增班次'
    },
    handleUpdateShift(row) {
      this.resetShiftForm()
      getShift(row.shiftId).then(res => {
        this.shiftForm = res.data || {}
        this.shiftOpen = true
        this.shiftTitle = '编辑班次'
      })
    },
    submitShiftForm() {
      this.$refs.shiftForm.validate(valid => {
        if (!valid) return
        const api = this.shiftForm.shiftId != null ? updateShift : addShift
        api(this.shiftForm).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.shiftOpen = false
          this.getShiftList()
          this.loadEnabledShifts()
        })
      })
    },
    handleShiftStatusChange(row) {
      const oldStatus = row.status === '1' ? '0' : '1'
      const text = row.status === '1' ? '启用' : '停用'
      updateShift({ shiftId: row.shiftId, status: row.status }).then(() => {
        // U2：班次启停 30 秒内可撤销；失败回退开关
        this.$modal.undo({
          message: '班次已' + text,
          undoSuccessText: '已撤销，班次已' + (oldStatus === '1' ? '启用' : '停用'),
          onUndo: () => updateShift({ shiftId: row.shiftId, status: oldStatus }).then(() => {
            row.status = oldStatus
            this.loadEnabledShifts()
          })
        })
        this.loadEnabledShifts()
      }).catch(() => {
        row.status = oldStatus
        this.$modal.msgError('状态更新失败，已恢复原状态')
      })
    },
    handleDeleteShift(row) {
      this.$modal.dangerConfirm({
        content: '将删除班次「' + row.shiftName + '」',
        count: 1,
        countNoun: '个班次',
        tip: '已使用该班次的历史排班记录不受影响'
      }).then(() => {
        return delShift(row.shiftId)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getShiftList()
        this.loadEnabledShifts()
      }).catch(() => {})
    },
    // ==================== 排班 ====================
    getScheduleList() {
      this.scheduleLoading = true
      const params = {
        ...this.scheduleQuery,
        ...this.addDateRange(this.scheduleQuery, this.scheduleDateRange)
      }
      listSchedule(params).then(res => {
        this.scheduleList = res.rows || []
        this.scheduleTotal = res.total || 0
        this.scheduleLoading = false
      }).catch(() => { this.scheduleLoading = false })
    },
    resetScheduleQuery() {
      this.scheduleQuery = { pageNum: 1, pageSize: 10, shiftId: undefined, agentId: undefined }
      this.initDefaultDateRange()
      this.getScheduleList()
    },
    loadAgents() {
      listAgent({ pageSize: 999, status: '1' }).then(res => {
        this.agentList = res.rows || []
      })
    },
    openBatchDialog() {
      this.scheduleForm = {
        shiftId: undefined,
        agentIds: [],
        dateRange: this.scheduleDateRange && this.scheduleDateRange.length ? [...this.scheduleDateRange] : [],
        scheduleType: '1',
        remark: undefined
      }
      this.scheduleTitle = '批量排班'
      this.scheduleOpen = true
      this.$nextTick(() => { this.$refs.scheduleFormRef && this.$refs.scheduleFormRef.clearValidate() })
    },
    handleUpdateSchedule(row) {
      // 简化：复用批量弹窗，仅用于修改单条记录的班次/类型
      getSchedule(row.scheduleId).then(res => {
        const d = res.data || row
        this.scheduleForm = {
          scheduleId: d.scheduleId,
          shiftId: d.shiftId,
          agentIds: [d.agentId],
          dateRange: [d.scheduleDate, d.scheduleDate],
          scheduleType: d.scheduleType || '1',
          remark: d.remark
        }
        this.scheduleTitle = '编辑排班'
        this.scheduleOpen = true
      })
    },
    submitScheduleForm() {
      this.$refs.scheduleFormRef.validate(valid => {
        if (!valid) return
        // 编辑单条
        if (this.scheduleForm.scheduleId != null) {
          updateSchedule({
            scheduleId: this.scheduleForm.scheduleId,
            shiftId: this.scheduleForm.shiftId,
            scheduleType: this.scheduleForm.scheduleType,
            remark: this.scheduleForm.remark
          }).then(() => {
            this.$modal.msgSuccess('修改成功')
            this.scheduleOpen = false
            this.getScheduleList()
          })
          return
        }
        // 批量排班：给每个坐席每一天创建记录
        const [startDate, endDate] = this.scheduleForm.dateRange
        const payload = {
          shiftId: this.scheduleForm.shiftId,
          agentIds: this.scheduleForm.agentIds,
          startDate: startDate,
          endDate: endDate,
          scheduleType: this.scheduleForm.scheduleType,
          remark: this.scheduleForm.remark
        }
        batchSchedule(payload).then(() => {
          this.$modal.msgSuccess('排班成功')
          this.scheduleOpen = false
          this.getScheduleList()
        })
      })
    },
    handleDeleteSchedule(row) {
      this.$modal.dangerConfirm({
        content: '将删除该条排班记录（坐席当日班次安排）',
        count: 1,
        countNoun: '条排班'
      }).then(() => {
        return delSchedule(row.scheduleId)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getScheduleList()
      }).catch(() => {})
    },
    handleCheckIn(row) {
      checkIn(row.scheduleId).then(() => {
        this.$modal.msgSuccess('签到成功')
        this.getScheduleList()
      })
    },
    handleCheckOut(row) {
      checkOut(row.scheduleId).then(() => {
        this.$modal.msgSuccess('签退成功')
        this.getScheduleList()
      })
    },
    loadToday() {
      todaySchedule().then(res => {
        const data = res.data || res.rows || []
        this.scheduleList = Array.isArray(data) ? data : [data]
        this.scheduleTotal = this.scheduleList.length
        this.$modal.msgSuccess('已加载今日排班')
      })
    },
    scheduleTypeLabel(t) {
      return { '1': '正常班', '2': '加班', '3': '调班' }[t] || '正常班'
    },
    scheduleTypeTag(t) {
      return { '1': '', '2': 'warning', '3': 'success' }[t] || ''
    },
    scheduleStatusLabel(s) {
      return { '0': '待签到', '1': '已签到', '2': '已签退', '3': '缺勤', '4': '请假' }[s] || '待签到'
    },
    scheduleStatusTag(s) {
      return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger', '4': 'info' }[s] || 'info'
    }
  }
}
</script>

<style scoped>
.schedule-page { padding: 16px; }
</style>
