<template>
  <div class="app-container hotspot-page">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input
            v-model="queryParams.ruleName"
            placeholder="请输入规则名称"
            clearable
            style="width: 180px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="匹配方式" prop="matchType">
          <el-select v-model="queryParams.matchType" placeholder="全部" clearable style="width: 140px">
            <el-option label="号码匹配" value="PHONE" />
            <el-option label="关键词匹配" value="KEYWORD" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置动作" prop="action">
          <el-select v-model="queryParams.action" placeholder="全部" clearable style="width: 140px">
            <el-option label="降权置底" value="PRIORITY" />
            <el-option label="直接挂断" value="REJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" value="0" />
            <el-option label="停用" value="1" />
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
          <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="el-icon-document" size="mini" @click="openLogDialog()">命中日志</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="ruleList" border size="small">
        <el-table-column label="ID" align="center" prop="suppressId" width="70" />
        <el-table-column label="规则名称" align="center" prop="ruleName" min-width="150" show-overflow-tooltip />
        <el-table-column label="匹配方式" align="center" width="100">
          <template slot-scope="scope">
            <el-tag :type="scope.row.matchType === 'PHONE' ? '' : 'success'" size="mini">
              {{ matchTypeText(scope.row.matchType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匹配值" align="center" prop="matchValue" min-width="140" show-overflow-tooltip />
        <el-table-column label="处置动作" align="center" width="100">
          <template slot-scope="scope">
            <el-tag :type="scope.row.action === 'REJECT' ? 'danger' : 'warning'" size="mini">
              {{ actionText(scope.row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置底优先级" align="center" prop="priorityLevel" width="100" />
        <el-table-column label="频次阈值" align="center" width="120">
          <template slot-scope="scope">
            <span v-if="!scope.row.triggerCount || scope.row.triggerCount === 0">每次命中</span>
            <span v-else>{{ scope.row.triggerCount }} 次 / {{ scope.row.windowSeconds > 0 ? scope.row.windowSeconds + ' 秒' : '不限时' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计命中" align="center" prop="hitCount" width="90" />
        <el-table-column label="状态" align="center" width="90">
          <template slot-scope="scope">
            <el-switch
              v-model="scope.row.status"
              active-value="0"
              inactive-value="1"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="生效开始" align="center" prop="effectiveStart" width="160">
          <template slot-scope="scope">{{ scope.row.effectiveStart ? parseTime(scope.row.effectiveStart) : '立即' }}</template>
        </el-table-column>
        <el-table-column label="生效结束" align="center" prop="effectiveEnd" width="160">
          <template slot-scope="scope">{{ scope.row.effectiveEnd ? parseTime(scope.row.effectiveEnd) : '永久' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" icon="el-icon-document" @click="openLogDialog(scope.row)">日志</el-button>
            <el-button type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px" size="small">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="匹配方式" prop="matchType">
          <el-radio-group v-model="form.matchType">
            <el-radio label="PHONE">号码匹配（主叫精确匹配）</el-radio>
            <el-radio label="KEYWORD">关键词匹配（文本包含）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.matchType === 'KEYWORD' ? '关键词' : '主叫号码'" prop="matchValue">
          <el-input v-model="form.matchValue" :placeholder="form.matchType === 'KEYWORD' ? '请输入关键词' : '请输入主叫号码'" maxlength="64" />
        </el-form-item>
        <el-form-item label="处置动作" prop="action">
          <el-radio-group v-model="form.action">
            <el-radio label="PRIORITY">降权置底（沉到普通来电之后，仍可接听）</el-radio>
            <el-radio label="REJECT">直接挂断</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.action === 'PRIORITY'" label="置底优先级" prop="priorityLevel">
          <el-input-number v-model="form.priorityLevel" :max="-1" :step="10" controls-position="right" />
          <span class="form-tip">负数，越小越靠后（普通来电默认 0）</span>
        </el-form-item>
        <el-form-item label="频次阈值">
          <el-input-number v-model="form.triggerCount" :min="0" controls-position="right" />
          <span class="form-tip">0 = 每次命中即处置；&gt;0 = 时间窗内累计命中达该次数才处置</span>
        </el-form-item>
        <el-form-item label="统计窗口(秒)">
          <el-input-number v-model="form.windowSeconds" :min="0" :step="60" controls-position="right" />
          <span class="form-tip">0 = 不限时间窗（按历史累计）</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" active-value="0" inactive-value="1" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="生效开始时间" prop="effectiveStart">
          <el-date-picker
            v-model="form.effectiveStart"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="留空表示立即生效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="生效结束时间" prop="effectiveEnd">
          <el-date-picker
            v-model="form.effectiveEnd"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="留空表示永久有效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
        <el-button size="small" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 命中日志对话框 -->
    <el-dialog :title="logTitle" :visible.sync="logOpen" width="900px" append-to-body>
      <el-form :inline="true" size="small" :model="logQuery" @submit.native.prevent>
        <el-form-item label="主叫号码">
          <el-input v-model="logQuery.callerNumber" placeholder="主叫号码" clearable style="width: 150px" @keyup.enter.native="getLogList" />
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="logQuery.direction" placeholder="全部" clearable style="width: 120px">
            <el-option label="入站" value="INBOUND" />
            <el-option label="出站" value="OUTBOUND" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleLogQuery">查询</el-button>
          <el-button icon="el-icon-refresh" size="mini" @click="resetLogQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="logLoading" :data="logList" border size="small" max-height="420">
        <el-table-column label="时间" align="center" prop="hitTime" width="160">
          <template slot-scope="scope">{{ parseTime(scope.row.hitTime) }}</template>
        </el-table-column>
        <el-table-column label="规则" align="center" prop="ruleName" min-width="120" show-overflow-tooltip />
        <el-table-column label="方向" align="center" width="80">
          <template slot-scope="scope">{{ scope.row.direction === 'INBOUND' ? '入站' : '出站' }}</template>
        </el-table-column>
        <el-table-column label="主叫" align="center" prop="callerNumber" width="130" />
        <el-table-column label="被叫" align="center" prop="calleeNumber" width="130" />
        <el-table-column label="动作" align="center" width="90">
          <template slot-scope="scope">
            <el-tag :type="scope.row.action === 'REJECT' ? 'danger' : 'warning'" size="mini">
              {{ actionText(scope.row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" align="center" prop="priority" width="80" />
        <el-table-column label="通道UUID" align="center" prop="channelUuid" min-width="140" show-overflow-tooltip />
      </el-table>
      <pagination
        v-show="logTotal > 0"
        :total="logTotal"
        :page.sync="logQuery.pageNum"
        :limit.sync="logQuery.pageSize"
        @pagination="getLogList"
      />
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="logOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listHotspot, getHotspot, addHotspot, updateHotspot, delHotspot, listHotspotLog } from '@/api/lawyers/hotspotSuppress'

export default {
  name: 'HotspotSuppress',
  data() {
    return {
      loading: false,
      total: 0,
      ruleList: [],
      open: false,
      title: '',
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        ruleName: undefined,
        matchType: undefined,
        action: undefined,
        status: undefined
      },
      form: {},
      rules: {
        ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
        matchType: [{ required: true, message: '请选择匹配方式', trigger: 'change' }],
        matchValue: [{ required: true, message: '匹配值不能为空', trigger: 'blur' }],
        action: [{ required: true, message: '请选择处置动作', trigger: 'change' }]
      },
      logOpen: false,
      logTitle: '命中日志',
      logLoading: false,
      logTotal: 0,
      logList: [],
      logQuery: {
        pageNum: 1,
        pageSize: 10,
        suppressId: undefined,
        callerNumber: undefined,
        direction: undefined
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    matchTypeText(t) {
      if (t === 'PHONE') return '号码匹配'
      if (t === 'KEYWORD') return '关键词匹配'
      return '未知'
    },
    actionText(a) {
      if (a === 'PRIORITY') return '降权置底'
      if (a === 'REJECT') return '直接挂断'
      return '未知'
    },
    getList() {
      this.loading = true
      listHotspot(this.queryParams).then(res => {
        this.ruleList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams = { pageNum: 1, pageSize: 10, ruleName: undefined, matchType: undefined, action: undefined, status: undefined }
      this.handleQuery()
    },
    reset() {
      this.form = {
        suppressId: undefined,
        ruleName: undefined,
        matchType: 'PHONE',
        matchValue: undefined,
        action: 'PRIORITY',
        priorityLevel: -100,
        triggerCount: 0,
        windowSeconds: 0,
        status: '0',
        effectiveStart: undefined,
        effectiveEnd: undefined,
        remark: undefined
      }
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增高频置底规则'
    },
    handleUpdate(row) {
      this.reset()
      getHotspot(row.suppressId).then(res => {
        this.form = res.data || {}
        this.open = true
        this.title = '编辑高频置底规则'
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const api = this.form.suppressId != null ? updateHotspot : addHotspot
        api(this.form).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.open = false
          this.getList()
        })
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    handleStatusChange(row) {
      updateHotspot(row).then(() => {
        this.$modal.msgSuccess('状态已更新')
      })
    },
    handleDelete(row) {
      this.$modal.confirm('确认删除规则「' + row.ruleName + '」吗？历史命中日志将保留。').then(() => {
        return delHotspot(row.suppressId)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    },
    openLogDialog(row) {
      this.logQuery = {
        pageNum: 1,
        pageSize: 10,
        suppressId: row ? row.suppressId : undefined,
        callerNumber: undefined,
        direction: undefined
      }
      this.logTitle = row ? '命中日志 - ' + row.ruleName : '全部命中日志'
      this.logOpen = true
      this.getLogList()
    },
    getLogList() {
      this.logLoading = true
      listHotspotLog(this.logQuery).then(res => {
        this.logList = res.rows || []
        this.logTotal = res.total || 0
        this.logLoading = false
      }).catch(() => { this.logLoading = false })
    },
    handleLogQuery() {
      this.logQuery.pageNum = 1
      this.getLogList()
    },
    resetLogQuery() {
      const suppressId = this.logQuery.suppressId
      this.logQuery = { pageNum: 1, pageSize: 10, suppressId: suppressId, callerNumber: undefined, direction: undefined }
      this.getLogList()
    }
  }
}
</script>

<style scoped>
.form-tip { margin-left: 10px; color: #909399; font-size: 12px; }
</style>
