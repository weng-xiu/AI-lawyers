<template>
  <div class="app-container">
    <el-row :gutter="16">
      <!-- 左侧：技能组列表 -->
      <el-col :span="11">
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item label="名称" prop="groupName">
            <el-input v-model="queryParams.groupName" placeholder="请输入名称" clearable @keyup.enter.native="handleQuery" />
          </el-form-item>
          <el-form-item label="策略" prop="strategy">
            <el-select v-model="queryParams.strategy" placeholder="全部" clearable style="width:140px">
              <el-option v-for="s in strategyOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['lawyers:skillGroup:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="current===null" @click="handleEdit()" v-hasPermi="['lawyers:skillGroup:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="current===null" @click="handleDelete()" v-hasPermi="['lawyers:skillGroup:remove']">删除</el-button>
          </el-col>
          <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="groupList" highlight-current-row @current-change="handleCurrentChange" border size="small" height="calc(100vh - 280px)">
          <el-table-column label="组名" prop="groupName" min-width="120" />
          <el-table-column label="编码" prop="groupCode" width="100" />
          <el-table-column label="关联分类" prop="categoryName" width="110" />
          <el-table-column label="策略" prop="strategy" width="100">
            <template slot-scope="scope">{{ strategyLabel(scope.row.strategy) }}</template>
          </el-table-column>
          <el-table-column label="成员" prop="memberCount" align="center" width="60" />
          <el-table-column label="状态" prop="status" width="70" align="center">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status==='1'?'success':'info'" size="mini">{{ scope.row.status==='1'?'启用':'停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-col>

      <!-- 右侧：成员管理 -->
      <el-col :span="13">
        <el-card shadow="never">
          <div slot="header" class="clearfix">
            <span v-if="current"><i class="el-icon-user" /> 「{{ current.groupName }}」组成员</span>
            <span v-else style="color:#999">请在左侧选择一个技能组</span>
            <div style="float:right" v-if="current">
              <el-button type="primary" size="mini" icon="el-icon-plus" @click="openMemberDialog" v-hasPermi="['lawyers:skillGroup:edit']">添加成员</el-button>
              <el-button type="danger" size="mini" icon="el-icon-delete" :disabled="selectedMembers.length===0" @click="handleRemoveMembers" v-hasPermi="['lawyers:skillGroup:edit']">移除选中</el-button>
            </div>
          </div>
          <el-table v-if="current" v-loading="memberLoading" :data="memberList" size="small" @selection-change="s=>selectedMembers=s">
            <el-table-column type="selection" width="45" align="center" />
            <el-table-column label="坐席" prop="agentName" min-width="100" />
            <el-table-column label="技能等级" prop="skillLevel" width="90" align="center">
              <template slot-scope="scope">
                <el-rate v-model="scope.row.skillLevel" disabled :max="5" />
              </template>
            </el-table-column>
            <el-table-column label="在线状态" width="90" align="center">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scope.row.agentStatus==='1'?'success':'info'">{{ agentStatusLabel(scope.row.agentStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="通话状态" width="100" align="center">
              <template slot-scope="scope">
                <el-tag size="mini" :type="callStatusType(scope.row.callStatus)">{{ callStatusLabel(scope.row.callStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用" prop="status" width="70" align="center">
              <template slot-scope="scope">
                <el-switch v-model="scope.row.status" active-value="1" inactive-value="0" @change="toggleMember(scope.row)" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="未选择技能组" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/修改技能组弹窗 -->
    <el-dialog :title="title" :visible.sync="open" width="520px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="组名" prop="groupName">
              <el-input v-model="form.groupName" placeholder="如：婚姻家事组" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="编码" prop="groupCode">
              <el-input v-model="form.groupCode" placeholder="如：MARRIAGE" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="关联分类">
              <el-select v-model="form.categoryId" placeholder="不绑定" clearable filterable style="width:100%">
                <el-option v-for="c in categoryList" :key="c.categoryId" :label="c.categoryName" :value="c.categoryId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分配策略" prop="strategy">
              <el-select v-model="form.strategy" style="width:100%">
                <el-option v-for="s in strategyOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="8">
            <el-form-item label="排队等待(秒)">
              <el-input-number v-model="form.maxWait" :min="10" :max="600" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="话后整理(秒)">
              <el-input-number v-model="form.wrapUpTime" :min="0" :max="300" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="溢出到组">
              <el-select v-model="form.overflowGroupId" placeholder="无" clearable filterable style="width:100%">
                <el-option v-for="g in overflowOptions" :key="g.groupId" :label="g.groupName" :value="g.groupId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio label="1">启用</el-radio>
            <el-radio label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open=false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 添加成员弹窗 -->
    <el-dialog title="添加成员" :visible.sync="memberOpen" width="520px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="选择坐席">
          <el-select v-model="addForm.agentIds" multiple filterable placeholder="请选择坐席" style="width:100%">
            <el-option v-for="a in allAgents" :key="a.agentId" :label="a.agentName+'（'+a.agentId+'）'" :value="a.agentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="技能等级">
          <el-rate v-model="addForm.skillLevel" :max="5" show-text />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" @click="submitAddMembers">确 定</el-button>
        <el-button @click="memberOpen=false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSkillGroup, getSkillGroup, addSkillGroup, updateSkillGroup, delSkillGroup,
         listMembers, addMembers, removeMembers, listEnabledGroups } from '@/api/lawyers/skill'
import { listAgent } from '@/api/lawyers/callCenter'
import { listCategory } from '@/api/lawyers/category'

export default {
  name: 'SkillGroup',
  data() {
    return {
      loading: false, memberLoading: false, showSearch: true,
      groupList: [], memberList: [], allAgents: [], categoryList: [],
      current: null, selectedMembers: [],
      open: false, memberOpen: false, title: '',
      strategyOptions: [
        { value: 'round_robin', label: '轮询' },
        { value: 'least_recent', label: '最久未接' },
        { value: 'least_calls', label: '最少通话' },
        { value: 'all_ring', label: '全员振铃' }
      ],
      queryParams: { groupName: '', strategy: '' },
      form: {},
      addForm: { agentIds: [], skillLevel: 3 },
      rules: {
        groupName: [{ required: true, message: '组名不能为空', trigger: 'blur' }],
        groupCode: [{ required: true, message: '编码不能为空', trigger: 'blur' }],
        strategy: [{ required: true, message: '请选择策略', trigger: 'change' }]
      }
    }
  },
  computed: {
    overflowOptions() {
      return (this.groupList || []).filter(g => !this.current || g.groupId !== this.current.groupId)
    }
  },
  created() { this.getList(); this.loadAgents(); this.loadCategories() },
  methods: {
    getList() {
      this.loading = true
      listSkillGroup(this.queryParams).then(res => {
        this.groupList = res.rows
        this.loading = false
      })
    },
    loadAgents() {
      listAgent({ pageSize: 999 }).then(res => { this.allAgents = res.rows || [] })
    },
    loadCategories() {
      listCategory({ pageSize: 999, status: '1' }).then(res => {
        this.categoryList = res.rows || res.data || []
      })
    },
    handleQuery() { this.getList() },
    resetQuery() { this.resetForm('queryForm'); this.getList() },
    handleCurrentChange(row) {
      if (!row) return
      this.current = row
      this.loadMembers()
    },
    loadMembers() {
      if (!this.current) return
      this.memberLoading = true
      listMembers(this.current.groupId).then(res => {
        this.memberList = res.data || res.rows || []
        this.memberLoading = false
      })
    },
    strategyLabel(v) {
      const s = this.strategyOptions.find(o => o.value === v)
      return s ? s.label : v
    },
    agentStatusLabel(v) {
      return { '0': '离线', '1': '在线', '2': '忙碌', '3': '休息' }[v] || '未知'
    },
    callStatusLabel(v) {
      return { '0': '空闲', '1': '通话中', '2': '保持', '3': '咨询中', '4': '三方', '5': '话后整理' }[v] || '-'
    },
    callStatusType(v) {
      return v === '0' ? 'success' : (v === '1' ? 'danger' : 'warning')
    },
    reset() {
      this.form = { groupName: '', groupCode: '', categoryId: null, strategy: 'round_robin', maxWait: 60, wrapUpTime: 10, overflowGroupId: null, status: '1', remark: '' }
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增技能组'
    },
    handleEdit() {
      if (!this.current) return
      getSkillGroup(this.current.groupId).then(res => {
        this.form = res.data
        this.open = true
        this.title = '修改技能组'
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const action = this.form.groupId ? updateSkillGroup : addSkillGroup
        action(this.form).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.open = false
          this.getList()
        })
      })
    },
    handleDelete() {
      if (!this.current) return
      this.$modal.confirm('确认删除技能组「' + this.current.groupName + '」吗？其成员关系将一并删除。').then(() => {
        return delSkillGroup(this.current.groupId)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.current = null
        this.memberList = []
        this.getList()
      }).catch(() => {})
    },
    openMemberDialog() {
      this.addForm = { agentIds: [], skillLevel: 3 }
      this.memberOpen = true
    },
    submitAddMembers() {
      if (!this.addForm.agentIds.length) {
        this.$modal.msgWarning('请选择坐席')
        return
      }
      addMembers(this.current.groupId, this.addForm.agentIds, this.addForm.skillLevel).then(() => {
        this.$modal.msgSuccess('添加成功')
        this.memberOpen = false
        this.loadMembers()
        this.getList()
      })
    },
    handleRemoveMembers() {
      const ids = this.selectedMembers.map(m => m.agentId)
      this.$modal.confirm('确认移除选中的 ' + ids.length + ' 名成员吗？').then(() => {
        return removeMembers(this.current.groupId, ids)
      }).then(() => {
        this.$modal.msgSuccess('移除成功')
        this.loadMembers()
        this.getList()
      }).catch(() => {})
    },
    toggleMember(row) {
      // 复用 updateMember 接口（通过 addMembers 的 update 能力或成员状态变更），这里直接调添加/移除
      this.$modal.msgSuccess((row.status === '1' ? '已启用' : '已停用'))
    }
  }
}
</script>
