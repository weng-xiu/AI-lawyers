<template>
  <div class="app-container ticket-flow-page">
    <!-- F2：可视化画布 + 规则表格双视图；顶部统一选择流程类型 -->
    <div class="flow-header">
      <div class="flow-title">
        <span>工单流程编排</span>
        <span class="flow-sub">拖拽连线配置动作，保存即生效</span>
      </div>
      <div class="flow-tools">
        <span class="tool-label">流程类型</span>
        <el-select
          v-model="flowCode"
          size="small"
          filterable
          allow-create
          default-first-option
          style="width: 150px"
          @change="handleFlowChange"
        >
          <el-option v-for="f in flowOptions" :key="f" :label="f" :value="f" />
        </el-select>
        <el-radio-group v-model="view" size="small" class="view-switch">
          <el-radio-button label="canvas">画布</el-radio-button>
          <el-radio-button label="table">规则表格</el-radio-button>
        </el-radio-group>
        <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="getList">刷新</el-button>
        <el-button size="small" type="warning" plain icon="el-icon-s-check" @click="runValidate">发布前校验</el-button>
        <el-button v-hasPermi="['lawyers:ticketFlow:add']" type="primary" plain size="small" icon="el-icon-plus" @click="handleAdd">新增规则</el-button>
      </div>
    </div>

    <p class="flow-hint">
      拖动节点右下角「＋」到目标状态即可新建动作；点击连线可编辑；节点颜色区分状态，虚线表示已停用规则。
    </p>

    <el-alert
      v-if="unknownRules.length"
      type="warning"
      :closable="false"
      show-icon
      class="unknown-tip"
      :title="'有 ' + unknownRules.length + ' 条规则引用了画布外的状态，请切换到规则表格编辑（状态：' + unknownStatuses.join('、') + '）'"
    />

    <!-- ================= 画布视图 ================= -->
    <div v-if="view === 'canvas'" class="canvas-wrap">
      <div ref="canvas" class="flow-canvas" :style="canvasStyle">
        <svg class="flow-svg" :width="canvasWidth" :height="canvasHeight">
          <defs>
            <marker id="tf-arrow" viewBox="0 0 10 10" refX="8" refY="5"
                    markerWidth="7" markerHeight="7" orient="auto">
              <path d="M0,0 L10,5 L0,10 z" fill="#909399" />
            </marker>
          </defs>
          <g
            v-for="edge in canvasEdges"
            :key="edge.key"
            class="flow-edge"
            :class="{ disabled: edge.disabled }"
            @click="handleUpdate(edge.rule)"
          >
            <path :d="edge.path" class="edge-hit" />
            <path
              :d="edge.path"
              class="edge-line"
              fill="none"
              marker-end="url(#tf-arrow)"
              :style="{ stroke: edge.disabled ? '#c0c4cc' : edge.color }"
            />
            <text
              :x="edge.labelX"
              :y="edge.labelY"
              text-anchor="middle"
              class="edge-label"
              :style="{ fill: edge.disabled ? '#a8abb2' : '#303133' }"
            >{{ edge.label }}</text>
          </g>
          <path v-if="linkingFrom" :d="linkPath" class="edge-temp" />
        </svg>

        <div
          v-for="node in statusNodes"
          :key="node.status"
          class="flow-node"
          :class="{ 'is-source': linkingFrom === node.status, 'is-target': linkTarget === node.status }"
          :style="nodeStyle(node)"
        >
          <div class="node-head" :style="{ background: node.color }">{{ node.label }}</div>
          <div class="node-body">
            <span class="node-code">状态 {{ node.status }}</span>
            <span class="node-count">{{ nodeActionCount(node.status) }} 个动作</span>
          </div>
          <div class="node-desc">{{ node.desc }}</div>
          <button
            type="button"
            class="node-link-handle"
            title="拖拽到目标状态建立动作"
            @mousedown.stop.prevent="startLink(node)"
          >＋</button>
        </div>
      </div>
    </div>

    <ProTable
      v-if="view === 'table'"
      ref="proTable"
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="pagedList"
      :loading="loading"
      :total="tableList.length"
      :show-search.sync="showSearch"
      label-width="80px"
      :table-attrs="{ border: true, size: 'small' }"
      @query="handleQuery"
      @reset="resetQuery"
    >
      <template #column-actionCode="{ row }">
        <div>{{ row.actionCode }}</div>
        <div class="sub-text">{{ row.actionName }}</div>
      </template>
      <template #column-status="{ row }">
        <el-tag :type="row.status === '0' ? 'success' : 'info'" size="mini">
          {{ row.status === '0' ? '启用' : '停用' }}
        </el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button v-hasPermi="['lawyers:ticketFlow:edit']" type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(row)">编辑</el-button>
      </template>
    </ProTable>

    <!-- ================= 规则编辑 ================= -->
    <el-dialog :title="title" :visible.sync="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px" size="small">
        <el-form-item label="流程类型" prop="flowCode">
          <el-input v-model="form.flowCode" placeholder="如 HOTLINE" maxlength="32" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="源状态" prop="statusFrom">
          <el-select v-model="form.statusFrom" placeholder="请选择" style="width: 100%" :disabled="isEdit">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作标识" prop="actionCode">
          <el-input v-model="form.actionCode" placeholder="如 start / transfer / complete / archive" maxlength="32" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="动作名称" prop="actionName">
          <el-input v-model="form.actionName" placeholder="如 受理" maxlength="32" />
        </el-form-item>
        <el-form-item label="目标状态" prop="targetStatus">
          <el-select v-model="form.targetStatus" placeholder="请选择" style="width: 100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许角色" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="逗号分隔，如 agent,leader，admin 恒放行" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序" prop="sortNo">
          <el-input-number v-model="form.sortNo" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="SLA 时限(小时)">
          <el-input-number v-model="form.slaHours" :min="0" :precision="2" :step="1" controls-position="right" style="width: 180px" />
          <span class="form-tip">留空不重算时限；填 0 清空到期时间</span>
        </el-form-item>
        <el-form-item label="必填表单 JSON">
          <el-input v-model="form.formSchema" type="textarea" :rows="3" placeholder='如 {"required":["orgId","remark"]}' />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" active-value="0" inactive-value="1" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
        <el-button size="small" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- ================= 发布前校验结果 ================= -->
    <el-dialog title="发布前校验" :visible.sync="validateOpen" width="580px" append-to-body>
      <div v-if="validation" class="validate-result">
        <el-alert
          :type="validation.valid ? 'success' : 'error'"
          :closable="false"
          show-icon
          :title="validation.valid ? '校验通过，流程可发布' : '校验未通过，请修复后再发布'"
          :description="'流程 ' + validation.flowCode + ' 共检查 ' + validation.checked + ' 条规则'"
        />
        <div v-if="validation.errors && validation.errors.length" class="check-block">
          <h4>错误（{{ validation.errors.length }}）</h4>
          <ul>
            <li v-for="(item, i) in validation.errors" :key="'err-' + i">{{ item }}</li>
          </ul>
        </div>
        <div v-if="validation.warnings && validation.warnings.length" class="check-block warn">
          <h4>提示（{{ validation.warnings.length }}）</h4>
          <ul>
            <li v-for="(item, i) in validation.warnings" :key="'warn-' + i">{{ item }}</li>
          </ul>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="validateOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTicketFlow, getTicketFlow, addTicketFlow, updateTicketFlow, validateTicketFlow } from '@/api/lawyers/ticketFlow'

const NODE_WIDTH = 170
const NODE_HEIGHT = 86
const NODE_PADDING = 6

export default {
  name: 'TicketFlow',
  data() {
    return {
      view: 'canvas',
      flowCode: 'HOTLINE',
      loading: false,
      showSearch: true,
      allList: [],
      // F2：画布固定节点（状态编码与 ai_ticket_flow_definition.status_from 对齐）
      statusNodes: [
        { status: '0', label: '待处理', desc: '新建/待受理', x: 40, y: 140, color: '#409EFF' },
        { status: '1', label: '处理中', desc: '已受理/办理中', x: 300, y: 140, color: '#E6A23C' },
        { status: '2', label: '已完成', desc: '办结待归档', x: 560, y: 140, color: '#67C23A' },
        { status: '3', label: '已归档', desc: '流程终态', x: 820, y: 140, color: '#909399' }
      ],
      nodeWidth: NODE_WIDTH,
      nodeHeight: NODE_HEIGHT,
      canvasWidth: 1050,
      canvasHeight: 360,
      // 拖拽连线状态
      linkingFrom: null,
      linkPoint: { x: 0, y: 0 },
      linkTarget: null,
      // 发布校验
      validation: null,
      validateOpen: false,
      open: false,
      title: '',
      isEdit: false,
      // V3：pageNum/pageSize 仅供 ProTable 本地分页使用，查询时不下发后端
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        statusFrom: undefined,
        actionCode: undefined,
        status: undefined
      },
      statusOptions: [
        { value: '0', label: '待处理' },
        { value: '1', label: '处理中' },
        { value: '2', label: '已完成' },
        { value: '3', label: '已归档' }
      ],
      form: {},
      rules: {
        flowCode: [{ required: true, message: '流程类型不能为空', trigger: 'blur' }],
        statusFrom: [{ required: true, message: '请选择源状态', trigger: 'change' }],
        actionCode: [{ required: true, message: '动作标识不能为空', trigger: 'blur' }],
        actionName: [{ required: true, message: '动作名称不能为空', trigger: 'blur' }],
        targetStatus: [{ required: true, message: '请选择目标状态', trigger: 'change' }],
        roleKey: [{ required: true, message: '允许角色不能为空', trigger: 'blur' }]
      }
    }
  },
  computed: {
    canvasStyle() {
      return { width: this.canvasWidth + 'px', height: this.canvasHeight + 'px' }
    },
    flowOptions() {
      const list = ['HOTLINE']
      this.allList.forEach(r => {
        if (r.flowCode && list.indexOf(r.flowCode) < 0) list.push(r.flowCode)
      })
      if (this.flowCode && list.indexOf(this.flowCode) < 0) list.push(this.flowCode)
      return list
    },
    canvasRules() {
      return this.allList.filter(r => (r.flowCode || 'HOTLINE') === this.flowCode)
    },
    unknownRules() {
      const known = this.statusNodes.map(n => n.status)
      return this.canvasRules.filter(r => known.indexOf(r.statusFrom) < 0 || known.indexOf(r.targetStatus) < 0)
    },
    unknownStatuses() {
      const known = this.statusNodes.map(n => n.status)
      const set = []
      this.unknownRules.forEach(r => {
        [r.statusFrom, r.targetStatus].forEach(s => {
          if (known.indexOf(s) < 0 && set.indexOf(s) < 0) set.push(s)
        })
      })
      return set
    },
    canvasEdges() {
      return this.buildEdges(this.canvasRules)
    },
    linkPath() {
      if (!this.linkingFrom) return ''
      const node = this.nodeByStatus(this.linkingFrom)
      if (!node) return ''
      // 拖拽起点固定为节点右侧中线，终点跟随鼠标
      const sx = node.x + this.nodeWidth
      const sy = node.y + this.nodeHeight / 2
      return 'M ' + sx + ',' + sy + ' L ' + this.linkPoint.x + ',' + this.linkPoint.y
    },
    tableList() {
      const q = this.queryParams
      return this.canvasRules.filter(r => {
        if (q.statusFrom != null && q.statusFrom !== '' && r.statusFrom !== q.statusFrom) return false
        if (q.actionCode && !(r.actionCode || '').toLowerCase().includes(q.actionCode.trim().toLowerCase())) return false
        if (q.status != null && q.status !== '' && r.status !== q.status) return false
        return true
      })
    },
    pagedList() {
      const start = (this.queryParams.pageNum - 1) * this.queryParams.pageSize
      return this.tableList.slice(start, start + this.queryParams.pageSize)
    },
    /** V3：ProTable 查询字段（源状态下拉依赖 statusOptions，用 computed 保证响应式） */
    queryFormItems() {
      return [
        { prop: 'statusFrom', label: '源状态', type: 'select', placeholder: '全部', width: '140px', options: this.statusOptions },
        { prop: 'actionCode', label: '动作', placeholder: '如 start', width: '140px' },
        { prop: 'status', label: '状态', type: 'select', placeholder: '全部', width: '110px', options: [
          { label: '启用', value: '0' }, { label: '停用', value: '1' }
        ] }
      ]
    },
    columns() {
      return [
        { prop: 'flowId', label: 'ID', width: 70 },
        { prop: 'flowCode', label: '流程类型', width: 110 },
        { prop: 'statusFrom', label: '源状态', width: 100, formatter: (row) => this.statusText(row.statusFrom) },
        { prop: 'actionCode', label: '动作', width: 110, slot: true },
        { prop: 'targetStatus', label: '目标状态', width: 100, formatter: (row) => this.statusText(row.targetStatus) },
        { prop: 'roleKey', label: '允许角色', minWidth: 140, showOverflowTooltip: true },
        { prop: 'sortNo', label: '排序', width: 70 },
        { prop: 'slaHours', label: 'SLA(小时)', width: 90, formatter: (row) => row.slaHours == null ? '—' : (row.slaHours === 0 ? '清时限' : row.slaHours) },
        { prop: 'status', label: '状态', width: 90, slot: true },
        { prop: 'action', label: '操作', width: 120, fixed: 'right', slot: true }
      ]
    }
  },
  created() {
    this.getList()
  },
  beforeDestroy() {
    this.removeLinkListeners()
  },
  methods: {
    // ===== 数据 =====

    /** 一次性拉取全量规则，画布与表格按 flowCode 本地过滤（切换流程无需再请求） */
    getList() {
      this.loading = true
      listTicketFlow({}).then(res => {
        this.allList = res.data || []
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleFlowChange() {
      this.queryParams.pageNum = 1
      this.validation = null
    },
    handleQuery() {
      this.queryParams.pageNum = 1
    },
    resetQuery() {
      this.queryParams.pageNum = 1
    },

    // ===== 画布布局与连线 =====

    nodeByStatus(status) {
      return this.statusNodes.find(n => n.status === status)
    },
    nodeStyle(node) {
      return {
        left: node.x + 'px',
        top: node.y + 'px',
        width: this.nodeWidth + 'px',
        height: this.nodeHeight + 'px',
        borderColor: node.color
      }
    },
    nodeActionCount(status) {
      return this.canvasRules.filter(r => r.statusFrom === status && r.status === '0').length
    },
    /**
     * 把规则转为带几何信息的边：
     *  - 顺向：从源节点右侧到目标节点左侧的三次贝塞尔；
     *  - 自环：节点上方环形；
     *  - 逆向：从节点下方绕回目标左侧。
     * 同一对节点间的多条动作按索引上下偏移，避免标签重叠。
     */
    buildEdges(rules) {
      const groups = {}
      rules.forEach(r => {
        if (!this.nodeByStatus(r.statusFrom) || !this.nodeByStatus(r.targetStatus)) return
        const key = r.statusFrom + '->' + r.targetStatus
        if (!groups[key]) groups[key] = []
        groups[key].push(r)
      })
      const edges = []
      let seq = 0
      Object.keys(groups).forEach(key => {
        const group = groups[key]
        group.forEach((rule, i) => {
          const from = this.nodeByStatus(rule.statusFrom)
          const to = this.nodeByStatus(rule.targetStatus)
          const sx = from.x + this.nodeWidth
          const sy = from.y + this.nodeHeight / 2
          const tx = to.x
          const ty = to.y + this.nodeHeight / 2
          const offset = (i - (group.length - 1) / 2) * 30
          let d
          let lx
          let ly
          if (rule.statusFrom === rule.targetStatus)
          {
            const cx = from.x + this.nodeWidth / 2
            d = 'M ' + (cx - 34) + ',' + from.y
              + ' C ' + (cx - 100) + ',' + (from.y - 90)
              + ' ' + (cx + 100) + ',' + (from.y - 90)
              + ' ' + (cx + 34) + ',' + from.y
            lx = cx
            ly = from.y - 66
          }
          else if (tx > sx)
          {
            const c1 = sx + 46
            const c2 = tx - 46
            d = 'M ' + sx + ',' + sy
              + ' C ' + c1 + ',' + (sy + offset)
              + ' ' + c2 + ',' + (ty + offset)
              + ' ' + tx + ',' + ty
            lx = 0.125 * sx + 0.375 * c1 + 0.375 * c2 + 0.125 * tx
            ly = 0.125 * sy + 0.375 * (sy + offset) + 0.375 * (ty + offset) + 0.125 * ty
          }
          else
          {
            d = 'M ' + sx + ',' + sy
              + ' C ' + (sx + 130) + ',' + (sy + 120 + offset)
              + ' ' + (tx - 130) + ',' + (ty + 120 + offset)
              + ' ' + tx + ',' + ty
            lx = 0.125 * sx + 0.375 * (sx + 130) + 0.375 * (tx - 130) + 0.125 * tx
            ly = 0.125 * sy + 0.375 * (sy + 120 + offset) + 0.375 * (ty + 120 + offset) + 0.125 * ty
          }
          edges.push({
            key: 'edge-' + (rule.flowId == null ? 'n' + seq : rule.flowId),
            path: d,
            labelX: lx,
            labelY: ly,
            label: rule.actionName || rule.actionCode,
            rule,
            disabled: rule.status === '1',
            color: from.color
          })
          seq++
        })
      })
      return edges
    },
    startLink(node) {
      this.removeLinkListeners()
      this.linkingFrom = node.status
      this.linkPoint = {
        x: node.x + this.nodeWidth,
        y: node.y + this.nodeHeight / 2
      }
      this.linkTarget = null
      window.addEventListener('mousemove', this.onLinkMove)
      window.addEventListener('mouseup', this.onLinkEnd)
    },
    onLinkMove(e) {
      if (!this.linkingFrom || !this.$refs.canvas) return
      const rect = this.$refs.canvas.getBoundingClientRect()
      this.linkPoint = {
        x: e.clientX - rect.left,
        y: e.clientY - rect.top
      }
      this.linkTarget = this.hitStatus(this.linkPoint)
    },
    onLinkEnd() {
      const from = this.linkingFrom
      const to = this.linkTarget
      this.removeLinkListeners()
      this.linkingFrom = null
      this.linkTarget = null
      if (from === null || to === null) return
      this.openAddWith(from, to)
    },
    removeLinkListeners() {
      window.removeEventListener('mousemove', this.onLinkMove)
      window.removeEventListener('mouseup', this.onLinkEnd)
    },
    hitStatus(point) {
      for (const node of this.statusNodes)
      {
        const inX = point.x >= node.x - NODE_PADDING
          && point.x <= node.x + this.nodeWidth + NODE_PADDING
        const inY = point.y >= node.y - NODE_PADDING
          && point.y <= node.y + this.nodeHeight + NODE_PADDING
        if (inX && inY) return node.status
      }
      return null
    },

    // ===== 发布校验 =====

    runValidate() {
      if (!this.flowCode) {
        this.$modal.msgWarning('请先选择流程类型')
        return
      }
      validateTicketFlow(this.flowCode).then(res => {
        this.validation = res.data || { flowCode: this.flowCode, valid: false, checked: 0, errors: ['校验返回为空'], warnings: [] }
        this.validateOpen = true
      }).catch(() => {
        this.$modal.msgError('校验请求失败，请稍后重试')
      })
    },

    // ===== 规则编辑 =====

    statusText(v) {
      const hit = this.statusOptions.find(s => s.value === String(v))
      return hit ? hit.label : v
    },
    reset() {
      this.form = {
        flowId: undefined,
        flowCode: this.flowCode,
        statusFrom: undefined,
        actionCode: undefined,
        actionName: undefined,
        targetStatus: undefined,
        roleKey: 'agent',
        sortNo: 0,
        slaHours: undefined,
        formSchema: undefined,
        status: '0'
      }
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleAdd() {
      this.reset()
      this.isEdit = false
      this.open = true
      this.title = '新增流程规则'
    },
    openAddWith(from, to) {
      this.reset()
      this.form.statusFrom = from
      this.form.targetStatus = to
      this.isEdit = false
      this.open = true
      this.title = '新增动作：' + this.statusText(from) + ' → ' + this.statusText(to)
    },
    handleUpdate(row) {
      this.reset()
      getTicketFlow(row.flowId).then(res => {
        this.form = res.data || {}
        this.isEdit = true
        this.open = true
        this.title = '编辑流程规则'
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.formSchema) {
          try {
            JSON.parse(this.form.formSchema)
          } catch (e) {
            this.$modal.msgError('必填表单必须是合法 JSON')
            return
          }
        }
        const api = this.form.flowId != null ? updateTicketFlow : addTicketFlow
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
    }
  }
}
</script>

<style scoped>
.ticket-flow-page {
  background: #fff;
}
.flow-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}
.flow-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}
.flow-sub {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.flow-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.tool-label {
  font-size: 13px;
  color: #606266;
}
.view-switch {
  margin-left: 4px;
}
.flow-hint {
  margin: 10px 0 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.unknown-tip {
  margin-bottom: 10px;
}
.canvas-wrap {
  overflow-x: auto;
  padding: 4px 0 12px;
}
.flow-canvas {
  position: relative;
  background-color: #fafcff;
  background-image:
    linear-gradient(rgba(64, 158, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(64, 158, 255, 0.08) 1px, transparent 1px);
  background-size: 22px 22px;
  border: 1px solid #e4ebf5;
  border-radius: 6px;
}
.flow-svg {
  position: absolute;
  left: 0;
  top: 0;
  overflow: visible;
}
.flow-edge {
  cursor: pointer;
}
.flow-edge .edge-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 16;
}
.flow-edge .edge-line {
  stroke-width: 2;
  transition: stroke-width 0.15s;
}
.flow-edge:hover .edge-line {
  stroke-width: 3;
}
.flow-edge.disabled .edge-line {
  stroke-dasharray: 6 4;
}
.edge-label {
  font-size: 12px;
  paint-order: stroke;
  stroke: #fff;
  stroke-width: 3px;
  stroke-linejoin: round;
  cursor: pointer;
}
.edge-temp {
  fill: none;
  stroke: #409eff;
  stroke-width: 2;
  stroke-dasharray: 5 4;
}
.flow-node {
  position: absolute;
  z-index: 2;
  box-sizing: border-box;
  background: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.08);
  overflow: visible;
}
.flow-node.is-source {
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.25);
}
.flow-node.is-target {
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.28);
}
.node-head {
  padding: 6px 12px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 5px 5px 0 0;
}
.node-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 12px 0;
  font-size: 12px;
  color: #606266;
}
.node-code {
  color: #909399;
}
.node-count {
  color: #1f2d3d;
  font-weight: 600;
}
.node-desc {
  padding: 4px 12px 8px;
  font-size: 12px;
  color: #909399;
}
.node-link-handle {
  position: absolute;
  right: -11px;
  bottom: -11px;
  width: 24px;
  height: 24px;
  line-height: 22px;
  text-align: center;
  padding: 0;
  border: 1px solid #409eff;
  border-radius: 50%;
  background: #fff;
  color: #409eff;
  font-size: 14px;
  font-weight: 700;
  cursor: crosshair;
  user-select: none;
}
.node-link-handle:hover {
  background: #409eff;
  color: #fff;
}
.sub-text { color: #909399; font-size: 12px; line-height: 1.2; }
.form-tip { margin-left: 10px; color: #909399; font-size: 12px; }
.validate-result .check-block {
  margin-top: 14px;
}
.validate-result .check-block h4 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #f56c6c;
}
.validate-result .check-block.warn h4 {
  color: #e6a23c;
}
.validate-result .check-block ul {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.7;
  color: #606266;
}
</style>
