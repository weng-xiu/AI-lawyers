<template>
  <div class="app-container schedule-page">
    <el-tabs v-model="activeTab" type="card" @tab-click="handleTabClick">
      <!-- ========== Tab1: 班次管理 ========== -->
      <el-tab-pane label="班次管理" name="shift">
        <el-card shadow="never" class="table-card">
          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAddShift">新增班次</el-button>
            </el-col>
          </el-row>

          <el-table v-loading="shiftLoading" :data="shiftList" border size="small">
            <el-table-column label="班次名称" align="center" prop="shiftName" min-width="140" />
            <el-table-column label="上班时间" align="center" prop="startTime" width="110" />
            <el-table-column label="下班时间" align="center" prop="endTime" width="110" />
            <el-table-column label="是否跨天" align="center" width="100">
              <template slot-scope="scope">
                <el-tag :type="scope.row.crossDay === '1' || scope.row.crossDay === 1 ? 'warning' : 'info'" size="mini">
                  {{ (scope.row.crossDay === '1' || scope.row.crossDay === 1) ? '跨天' : '当日' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="休息时间(分)" align="center" prop="breakMinutes" width="110">
              <template slot-scope="scope">{{ scope.row.breakMinutes != null ? scope.row.breakMinutes : '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="90">
              <template slot-scope="scope">
                <el-switch
                  v-model="scope.row.status"
                  active-value="1"
                  inactive-value="0"
                  @change="handleShiftStatusChange(scope.row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip>
              <template slot-scope="scope">{{ scope.row.remark || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="160" fixed="right">
              <template slot-scope="scope">
                <el-button type="text" size="mini" icon="el-icon-edit" @click="handleUpdateShift(scope.row)">编辑</el-button>
                <el-button type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDeleteShift(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ========== Tab2: 排班日历 ========== -->
      <el-tab-pane label="排班管理" name="schedule">
        <el-card shadow="never" class="search-card">
          <el-form :model="scheduleQuery" ref="scheduleForm" size="small" :inline="true" label-width="80px">
            <el-form-item label="班次">
              <el-select v-model="scheduleQuery.shiftId" placeholder="全部" clearable filterable style="width:160px">
                <el-option v-for="s in enabledShiftList" :key="s.shiftId" :label="s.shiftName" :value="s.shiftId" />
              </el-select>
            </el-form-item>
            <el-form-item label="坐席">
              <el-input v-model="scheduleQuery.agentName" placeholder="坐席姓名" clearable style="width:160px" @keyup.enter.native="getScheduleList" />
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker
                v-model="scheduleDateRange"
                style="width: 260px"
                value-format="yyyy-MM-dd"
                type="daterange"
                range-separator="-"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              ></el-date-picker>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" size="mini" @click="getScheduleList">查询</el-button>
              <el-button icon="el-icon-refresh" size="mini" @click="resetScheduleQuery">重置</el-button>
              <el-button type="success" icon="el-icon-date" size="mini" @click="loadToday">今日排班</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="openBatchDialog">批量排班</el-button>
            </el-col>
          </el-row>

          <el-table v-loading="scheduleLoading" :data="scheduleList" border size="small">
            <el-table-column label="排班ID" align="center" prop="scheduleId" width="90" />
            <el-table-column label="坐席" align="center" prop="agentName" width="120">
              <template slot-scope="scope">{{ scope.row.agentName || '-' }}</template>
            </el-table-column>
            <el-table-column label="班次" align="center" prop="shiftName" width="140">
              <template slot-scope="scope">{{ scope.row.shiftName || '-' }}</template>
            </el-table-column>
            <el-table-column label="排班日期" align="center" prop="scheduleDate" width="120" />
            <el-table-column label="排班类型" align="center" width="100">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scheduleTypeTag(scope.row.scheduleType)">{{ scheduleTypeLabel(scope.row.scheduleType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="签到时间" align="center" prop="checkInTime" width="160">
              <template slot-scope="scope">{{ scope.row.checkInTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="签退时间" align="center" prop="checkOutTime" width="160">
              <template slot-scope="scope">{{ scope.row.checkOutTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="100">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scheduleStatusTag(scope.row.status)">{{ scheduleStatusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="220" fixed="right">
              <template slot-scope="scope">
                <el-button
                  v-if="!scope.row.checkInTime"
                  type="text"
                  size="mini"
                  icon="el-icon-right"
                  @click="handleCheckIn(scope.row)"
                >签到</el-button>
                <el-button
                  v-if="scope.row.checkInTime && !scope.row.checkOutTime"
                  type="text"
                  size="mini"
                  icon="el-icon-back"
                  @click="handleCheckOut(scope.row)"
                >签退</el-button>
                <el-button type="text" size="mini" icon="el-icon-edit" @click="handleUpdateSchedule(scope.row)">编辑</el-button>
                <el-button type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDeleteSchedule(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <pagination
            v-show="scheduleTotal > 0"
            :total="scheduleTotal"
            :page.sync="scheduleQuery.pageNum"
            :limit.sync="scheduleQuery.pageSize"
            @pagination="getScheduleList"
          />
        </el-card>
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

export default {
  name: 'Schedule',
  data() {
    return {
      activeTab: 'shift',
      // 班次
      shiftLoading: false,
      shiftList: [],
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
      scheduleDateRange: [],
      scheduleQuery: {
        pageNum: 1,
        pageSize: 10,
        shiftId: undefined,
        agentName: undefined
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
      updateShift({ shiftId: row.shiftId, status: row.status }).then(() => {
        this.$modal.msgSuccess('状态已更新')
        this.loadEnabledShifts()
      })
    },
    handleDeleteShift(row) {
      this.$modal.confirm('确认删除班次「' + row.shiftName + '」吗？').then(() => {
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
      this.scheduleQuery = { pageNum: 1, pageSize: 10, shiftId: undefined, agentName: undefined }
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
      this.$modal.confirm('确认删除该条排班记录吗？').then(() => {
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
