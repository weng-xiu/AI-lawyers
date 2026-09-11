<template>
  <div class="app-container blacklist-page">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="电话号码" prop="phoneNumber">
          <el-input
            v-model="queryParams.phoneNumber"
            placeholder="请输入电话号码"
            clearable
            style="width: 180px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="名单类型" prop="listType">
          <el-select v-model="queryParams.listType" placeholder="全部" clearable style="width: 140px">
            <el-option label="黑名单" value="1" />
            <el-option label="白名单" value="2" />
            <el-option label="退订名单" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" value="1" />
            <el-option label="停用" value="0" />
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
          <el-button type="success" plain icon="el-icon-circle-check" size="mini" @click="openCheckDialog">号码检查</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="blacklistList" border size="small">
        <el-table-column label="ID" align="center" prop="id" width="80" />
        <el-table-column label="电话号码" align="center" prop="phoneNumber" width="150" />
        <el-table-column label="名单类型" align="center" width="100">
          <template slot-scope="scope">
            <el-tag :type="listTypeTagType(scope.row.listType)" size="mini">
              {{ listTypeText(scope.row.listType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原因" align="center" prop="reason" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template slot-scope="scope">
            <el-switch
              v-model="scope.row.status"
              active-value="1"
              inactive-value="0"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="生效开始" align="center" prop="effectiveStart" width="160">
          <template slot-scope="scope">{{ scope.row.effectiveStart ? parseTime(scope.row.effectiveStart) : '-' }}</template>
        </el-table-column>
        <el-table-column label="生效结束" align="center" prop="effectiveEnd" width="160">
          <template slot-scope="scope">{{ scope.row.effectiveEnd ? parseTime(scope.row.effectiveEnd) : '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="160">
          <template slot-scope="scope">{{ parseTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160" fixed="right">
          <template slot-scope="scope">
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
    <el-dialog :title="title" :visible.sync="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px" size="small">
        <el-form-item label="电话号码" prop="phoneNumber">
          <el-input v-model="form.phoneNumber" placeholder="请输入电话号码" maxlength="20" />
        </el-form-item>
        <el-form-item label="名单类型" prop="listType">
          <el-radio-group v-model="form.listType">
            <el-radio label="1">黑名单</el-radio>
            <el-radio label="2">白名单</el-radio>
            <el-radio label="3">退订名单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入加入名单原因" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" active-value="1" inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="生效开始时间" prop="effectiveStart">
          <el-date-picker
            v-model="form.effectiveStart"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="选择生效开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="生效结束时间" prop="effectiveEnd">
          <el-date-picker
            v-model="form.effectiveEnd"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="选择生效结束时间"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
        <el-button size="small" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 号码检查对话框 -->
    <el-dialog title="号码检查" :visible.sync="checkOpen" width="460px" append-to-body>
      <el-form label-width="80px" size="small">
        <el-form-item label="电话号码">
          <el-input v-model="checkPhone" placeholder="请输入要检查的号码" clearable maxlength="20" @keyup.enter.native="submitCheck" />
        </el-form-item>
      </el-form>
      <div v-if="checkResult" class="check-result">
        <el-alert
          :title="checkResultTitle"
          :type="checkResultType"
          :closable="false"
          show-icon
        />
        <div class="check-detail">
          <p v-if="checkResult.listType">名单类型：<b>{{ listTypeText(checkResult.listType) }}</b></p>
          <p v-if="checkResult.status != null">状态：<b>{{ checkResult.status === '1' ? '启用' : '停用' }}</b></p>
          <p v-if="checkResult.reason">原因：{{ checkResult.reason }}</p>
          <p v-if="checkResult.effectiveStart">生效时间：{{ parseTime(checkResult.effectiveStart) }} ~ {{ checkResult.effectiveEnd ? parseTime(checkResult.effectiveEnd) : '永久' }}</p>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitCheck" :loading="checkLoading">检查</el-button>
        <el-button size="small" @click="checkOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listBlacklist, getBlacklist, addBlacklist, updateBlacklist, delBlacklist, checkPhone } from '@/api/lawyers/blacklist'

export default {
  name: 'Blacklist',
  data() {
    return {
      loading: false,
      total: 0,
      blacklistList: [],
      open: false,
      title: '',
      checkOpen: false,
      checkPhone: '',
      checkResult: null,
      checkLoading: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        phoneNumber: undefined,
        listType: undefined,
        status: undefined
      },
      form: {},
      rules: {
        phoneNumber: [{ required: true, message: '电话号码不能为空', trigger: 'blur' }],
        listType: [{ required: true, message: '请选择名单类型', trigger: 'change' }]
      }
    }
  },
  computed: {
    checkResultTitle() {
      if (!this.checkResult) return ''
      if (this.checkResult.listed) {
        return '该号码在' + this.listTypeText(this.checkResult.listType) + '中'
      }
      return '该号码未在名单中'
    },
    checkResultType() {
      if (!this.checkResult) return 'info'
      if (!this.checkResult.listed) return 'success'
      return String(this.checkResult.listType) === '1' ? 'error' : 'warning'
    }
  },
  created() {
    this.getList()
  },
  methods: {
    listTypeText(listType) {
      const t = String(listType)
      if (t === '1') return '黑名单'
      if (t === '2') return '白名单'
      if (t === '3') return '退订名单'
      return '未知'
    },
    listTypeTagType(listType) {
      const t = String(listType)
      if (t === '1') return 'danger'
      if (t === '2') return 'success'
      if (t === '3') return 'warning'
      return 'info'
    },
    getList() {
      this.loading = true
      listBlacklist(this.queryParams).then(res => {
        this.blacklistList = res.rows || []
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
      this.queryParams = { pageNum: 1, pageSize: 10, phoneNumber: undefined, listType: undefined, status: undefined }
      this.handleQuery()
    },
    reset() {
      this.form = {
        id: undefined,
        phoneNumber: undefined,
        listType: '1',
        reason: undefined,
        status: '1',
        effectiveStart: undefined,
        effectiveEnd: undefined
      }
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增名单'
    },
    handleUpdate(row) {
      this.reset()
      getBlacklist(row.id).then(res => {
        this.form = res.data || {}
        this.open = true
        this.title = '编辑名单'
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const api = this.form.id != null ? updateBlacklist : addBlacklist
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
      const data = { id: row.id, status: row.status }
      updateBlacklist(data).then(() => {
        this.$modal.msgSuccess('状态已更新')
      })
    },
    handleDelete(row) {
      this.$modal.confirm('确认删除该名单记录吗？').then(() => {
        return delBlacklist(row.id)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    },
    openCheckDialog() {
      this.checkPhone = ''
      this.checkResult = null
      this.checkOpen = true
    },
    submitCheck() {
      if (!this.checkPhone) {
        this.$modal.msgWarning('请输入电话号码')
        return
      }
      this.checkLoading = true
      checkPhone(this.checkPhone).then(res => {
        const d = res.data || {}
        // 后端可能返回 listed 字段，也可能直接返回名单对象；统一转换
        if (d.id != null) {
          this.checkResult = { listed: true, ...d }
        } else {
          this.checkResult = { listed: !!d.listed, ...d }
        }
        this.checkLoading = false
      }).catch(() => { this.checkLoading = false })
    }
  }
}
</script>

<style scoped>
.check-result { margin-top: 12px; }
.check-detail { margin-top: 12px; padding: 0 8px; color: #5A6A7E; font-size: 13px; }
.check-detail p { margin: 6px 0; }
</style>
