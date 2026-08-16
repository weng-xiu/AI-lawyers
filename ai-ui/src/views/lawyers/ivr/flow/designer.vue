<template>
  <div class="ivr-designer">
    <!-- 顶部工具栏 -->
    <div class="designer-header">
      <div class="header-left">
        <el-button size="mini" icon="el-icon-back" @click="goBack">返回</el-button>
        <span class="flow-title">{{ flow.flowName || 'IVR流程图设计' }}</span>
        <el-tag v-if="flow.flowCode" size="mini" type="info">{{ flow.flowCode }}</el-tag>
        <el-tag v-if="flow.status === '1'" size="mini" type="success">已发布</el-tag>
        <el-tag v-else-if="flow.status === '2'" size="mini" type="danger">已停用</el-tag>
        <el-tag v-else size="mini" type="info">草稿</el-tag>
        <span v-if="dirty" class="dirty-tip">（未保存）</span>
      </div>
      <div class="header-right">
        <el-button-group>
          <el-button size="mini" icon="el-icon-zoom-out" @click="zoomOut"></el-button>
          <el-button size="mini" disabled>{{ Math.round(zoom * 100) }}%</el-button>
          <el-button size="mini" icon="el-icon-zoom-in" @click="zoomIn"></el-button>
        </el-button-group>
        <el-button size="mini" icon="el-icon-refresh-left" @click="resetView">适应</el-button>
        <el-button type="primary" size="mini" icon="el-icon-check" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </div>

    <div class="designer-body">
      <!-- 左侧节点面板 -->
      <div class="palette">
        <div class="palette-title">节点组件</div>
        <div class="palette-tip">拖拽到画布，或点击后在画布上放置</div>
        <div
          v-for="item in paletteTypes"
          :key="item.type"
          class="palette-item"
          :class="{ active: pendingType === item.type }"
          draggable="true"
          @dragstart="onPaletteDragStart($event, item.type)"
          @click="onPaletteClick(item.type)"
        >
          <span class="palette-icon" :style="{ background: item.color }">{{ item.icon }}</span>
          <span class="palette-label">{{ item.label }}</span>
        </div>
        <div class="palette-help">
          <p>操作说明：</p>
          <p>1. 从左侧拖入节点到画布</p>
          <p>2. 拖动节点调整位置</p>
          <p>3. 从节点右侧圆点拖出连线</p>
          <p>4. 点击节点/连线配置属性</p>
          <p>5. Delete 键删除选中对象</p>
        </div>
      </div>

      <!-- 中间画布 -->
      <div class="canvas-wrap" ref="canvasWrap" @dragover.prevent @drop="onCanvasDrop" @click="onCanvasClick">
        <div class="canvas" :style="{ transform: 'scale(' + zoom + ')', transformOrigin: '0 0' }">
          <svg :width="canvasW" :height="canvasH" class="designer-svg">
            <defs>
              <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                <path d="M 0 0 L 10 5 L 0 10 z" :fill="edgeDraft ? '#409EFF' : '#909399'" />
              </marker>
            </defs>
            <rect class="canvas-bg" :width="canvasW" :height="canvasH" fill="transparent" />

            <!-- 连线 -->
            <g v-for="edge in edges" :key="'e' + edge.id" class="edge-group">
              <path
                class="edge-hit"
                :d="edgePath(edge)"
                @click.stop="selectEdge(edge)"
              />
              <path
                class="edge-line"
                :d="edgePath(edge)"
                :class="{ selected: isSelected('edge', edge.id) }"
                marker-end="url(#arrow)"
                @click.stop="selectEdge(edge)"
              />
              <g class="edge-label" :transform="edgeLabelPos(edge)" @click.stop="selectEdge(edge)">
                <rect :x="-labelW(edge) / 2" :y="-10" :width="labelW(edge)" height="20" rx="4" fill="#FFFFFF" stroke="#DCDFE6" stroke-width="1" />
                <text :x="0" :y="4" text-anchor="middle" font-size="11" fill="#606266">{{ edgeLabel(edge) }}</text>
              </g>
            </g>

            <!-- 拖拽中的临时连线 -->
            <path
              v-if="edgeDraft"
              class="edge-line draft"
              :d="draftPath"
              marker-end="url(#arrow)"
            />

            <!-- 节点 -->
            <g
              v-for="node in nodes"
              :key="'n' + node.id"
              class="node-group"
              :class="{ selected: isSelected('node', node.id), 'node-start': node.type === 'start', 'node-hangup': node.type === 'hangup' }"
              :transform="'translate(' + node.x + ',' + node.y + ')'"
              @mousedown.prevent="onNodeMouseDown($event, node)"
              @click.stop
            >
              <rect class="node-body" :width="nodeW" :height="nodeH" rx="10" :style="nodeBodyStyle(node)" />
              <rect class="node-accent" :x="0" :y="0" :width="6" :height="nodeH" rx="3" :style="{ fill: nodeColor(node) }" />
              <circle class="node-icon" :cx="22" :cy="nodeH / 2" :r="14" :style="{ fill: nodeColor(node) }" />
              <text :x="22" :y="nodeH / 2 + 5" text-anchor="middle" font-size="14" fill="#FFF">{{ nodeIcon(node) }}</text>
              <text class="node-name" :x="46" :y="nodeH / 2 + 5" font-size="13" fill="#303133">{{ nodeName(node) }}</text>
              <circle
                class="port port-in"
                :cx="0"
                :cy="nodeH / 2"
                :r="7"
                :style="portStyle(node)"
              />
              <circle
                class="port port-out"
                data-port="out"
                :cx="nodeW"
                :cy="nodeH / 2"
                :r="8"
                :style="portStyle(node)"
              />
            </g>
          </svg>
        </div>
        <div v-if="pendingType" class="pending-tip">请在画布上点击放置「{{ pendingLabel }}」节点</div>
      </div>

      <!-- 右侧属性面板 -->
      <div class="config-panel">
        <template v-if="selectedNode">
          <div class="panel-title">节点属性</div>
          <el-form size="mini" label-width="70px">
            <el-form-item label="节点类型">
              <el-tag :style="{ color: nodeColor(selectedNode), borderColor: nodeColor(selectedNode), background: nodeColor(selectedNode) + '1a' }">
                {{ nodeTypeLabel(selectedNode.type) }}
              </el-tag>
            </el-form-item>
            <el-form-item label="节点名称">
              <el-input v-model="selectedNode.name" @input="markDirty" maxlength="50" />
            </el-form-item>

            <!-- 语音播报 -->
            <template v-if="selectedNode.type === 'say'">
              <el-form-item label="播报内容">
                <el-input v-model="selectedNode.config.text" type="textarea" :rows="5" placeholder="请输入TTS播报内容" @input="markDirty" />
              </el-form-item>
              <el-form-item label="语音引擎">
                <el-select v-model="selectedNode.config.voiceEngine" placeholder="默认网关播报" clearable @change="markDirty">
                  <el-option label="通义千问 DashScope" value="dashscope" />
                  <el-option label="阿里云 NLS" value="ali" />
                  <el-option label="电信" value="dianxin" />
                </el-select>
              </el-form-item>
              <div class="panel-hint">内容支持 ${变量名} 模板，如 ${lastInput}。选择引擎后保存时由后端合成音频。</div>
            </template>

            <!-- 语音收声 -->
            <template v-if="selectedNode.type === 'answer'">
              <el-form-item label="询问语">
                <el-input v-model="selectedNode.config.jqrask" type="textarea" :rows="3" placeholder="如：请问您遇到了什么法律问题？" @input="markDirty" />
              </el-form-item>
              <el-form-item label="静默提示">
                <el-switch v-model="selectedNode.config.silencePrompt" @change="markDirty" />
              </el-form-item>
              <el-form-item v-if="selectedNode.config.silencePrompt" label="静默秒数">
                <el-input-number v-model="selectedNode.config.silenceDuration" :min="1" :max="60" @change="markDirty" />
              </el-form-item>
              <el-form-item v-if="selectedNode.config.silencePrompt" label="静默提示语">
                <el-input v-model="selectedNode.config.silenceSay" placeholder="如：我没有听清，请您再说一遍" @input="markDirty" />
              </el-form-item>
              <div class="panel-hint">识别结果写入变量 lastInput，可在连线条件中使用。</div>
            </template>

            <!-- DTMF收号 -->
            <template v-if="selectedNode.type === 'received'">
              <el-form-item label="收号语">
                <el-input v-model="selectedNode.config.jqrask" type="textarea" :rows="3" placeholder="如：请输入您的工单编号，按#号结束" @input="markDirty" />
              </el-form-item>
              <el-form-item label="结束按键">
                <el-input v-model="selectedNode.config.endKey" style="width: 120px" maxlength="1" @input="markDirty" />
              </el-form-item>
              <el-form-item label="最大位数">
                <el-input-number v-model="selectedNode.config.maxDigits" :min="1" :max="32" @change="markDirty" />
              </el-form-item>
              <div class="panel-hint">按键结果写入变量 dtmf，可在连线条件中使用。</div>
            </template>

            <!-- 按键菜单 -->
            <template v-if="selectedNode.type === 'menu'">
              <el-form-item label="提示播报">
                <el-input v-model="selectedNode.config.prompt" type="textarea" :rows="3" placeholder="菜单提示语，如：法律咨询请按1，转人工请按2" @input="markDirty" />
              </el-form-item>
              <el-form-item label="按键选项">
                <div v-for="(opt, idx) in selectedNode.config.options" :key="idx" class="menu-option-row">
                  <el-input v-model="opt.key" placeholder="按键" style="width: 70px" @input="markDirty" />
                  <el-input v-model="opt.label" placeholder="选项名称" @input="markDirty" />
                  <el-button icon="el-icon-delete" size="mini" @click="removeMenuOption(idx)"></el-button>
                </div>
                <el-button size="mini" icon="el-icon-plus" @click="addMenuOption">添加选项</el-button>
                <div class="panel-hint">连线的条件表达式请使用 dtmf == '按键'，例如 dtmf == '1'</div>
              </el-form-item>
            </template>

            <!-- 意图识别 -->
            <template v-if="selectedNode.type === 'intention'">
              <el-form-item label="意图编码">
                <el-input v-model="selectedNode.config.intentionCode" placeholder="如 LEGAL_CONSULT" @input="markDirty" />
              </el-form-item>
              <el-form-item label="识别Prompt">
                <el-input v-model="selectedNode.config.promptTemplate" type="textarea" :rows="5" placeholder="AI意图识别提示模板" @input="markDirty" />
              </el-form-item>
            </template>

            <!-- 情绪分析 -->
            <template v-if="selectedNode.type === 'sentiment'">
              <el-form-item label="分析文本">
                <el-input v-model="selectedNode.config.text" type="textarea" :rows="3" placeholder="如 ${lastInput}" @input="markDirty" />
              </el-form-item>
              <div class="panel-hint">结果写入变量 sentiment（positive/negative/neutral），可在连线条件中使用，例如 sentiment == 'negative' 转人工。</div>
            </template>

            <!-- 信息抽取 -->
            <template v-if="selectedNode.type === 'extract'">
              <el-form-item label="原文文本">
                <el-input v-model="selectedNode.config.text" type="textarea" :rows="2" placeholder="如 ${lastInput}" @input="markDirty" />
              </el-form-item>
              <el-form-item label="提取字段">
                <div v-for="(field, idx) in selectedNode.config.fields" :key="idx" class="menu-option-row">
                  <el-input v-model="field.name" placeholder="字段名" @input="markDirty" />
                  <el-input v-model="field.desc" placeholder="字段说明" @input="markDirty" />
                  <el-button icon="el-icon-delete" size="mini" @click="removeExtractField(idx)"></el-button>
                </div>
                <el-button size="mini" icon="el-icon-plus" @click="addExtractField">添加字段</el-button>
              </el-form-item>
              <el-form-item label="结果变量">
                <el-input v-model="selectedNode.config.resultVar" placeholder="extractResult" @input="markDirty" />
              </el-form-item>
              <div class="panel-hint">每个字段名会作为一个流程变量写入，供后续节点引用。</div>
            </template>

            <!-- HTTP服务调用 -->
            <template v-if="selectedNode.type === 'service'">
              <el-form-item label="请求地址">
                <el-input v-model="selectedNode.config.url" placeholder="http://xxx/api?no=${dtmf}" @input="markDirty" />
              </el-form-item>
              <el-form-item label="请求方式">
                <el-select v-model="selectedNode.config.method" style="width: 100%" @change="markDirty">
                  <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
                </el-select>
              </el-form-item>
              <el-form-item label="请求头">
                <el-input v-model="selectedNode.config.headers" type="textarea" :rows="2" placeholder='{"Content-Type":"application/json"}' @input="markDirty" />
              </el-form-item>
              <el-form-item label="请求体">
                <el-input v-model="selectedNode.config.body" type="textarea" :rows="4" placeholder='{"phone":"${dtmf}"}' @input="markDirty" />
              </el-form-item>
              <el-form-item label="取值路径">
                <el-input v-model="selectedNode.config.result" placeholder="如 data.items[0].name（留空存全部响应）" @input="markDirty" />
              </el-form-item>
              <el-form-item label="结果变量">
                <el-input v-model="selectedNode.config.resultVar" placeholder="serviceResult" @input="markDirty" />
              </el-form-item>
              <el-form-item label="超时(ms)">
                <el-input-number v-model="selectedNode.config.timeoutMs" :min="500" :step="1000" @change="markDirty" />
              </el-form-item>
            </template>

            <!-- 脚本执行 -->
            <template v-if="selectedNode.type === 'script'">
              <el-form-item label="脚本类型">
                <el-select v-model="selectedNode.config.scriptType" style="width: 100%" @change="markDirty">
                  <el-option label="JavaScript" value="js" />
                  <el-option label="Groovy（暂不支持）" value="groovy" disabled />
                </el-select>
              </el-form-item>
              <el-form-item label="脚本体">
                <el-input v-model="selectedNode.config.script" type="textarea" :rows="8" placeholder="var result = String(dtmf);\nresult;" @input="markDirty" />
              </el-form-item>
              <el-form-item label="结果变量">
                <el-input v-model="selectedNode.config.resultVar" placeholder="scriptResult" @input="markDirty" />
              </el-form-item>
              <div class="panel-hint">脚本可直接读取流程变量（如 dtmf、lastInput），最后一个表达式的值写入结果变量。</div>
            </template>

            <!-- 子流程 -->
            <template v-if="selectedNode.type === 'child'">
              <el-form-item label="子流程ID">
                <el-input-number v-model="selectedNode.config.flowId" :min="0" placeholder="子流程ID" @change="markDirty" />
              </el-form-item>
              <el-form-item label="流程编码">
                <el-input v-model="selectedNode.config.flowCode" placeholder="与ID二选一" @input="markDirty" />
              </el-form-item>
              <el-form-item label="结果变量">
                <el-input v-model="selectedNode.config.resultVar" placeholder="childResult" @input="markDirty" />
              </el-form-item>
              <div class="panel-hint">子流程执行结束后，其流程变量会合并回当前流程，最多嵌套3层。</div>
            </template>

            <!-- 智能体对话 -->
            <template v-if="selectedNode.type === 'agentChat'">
              <el-form-item label="选择智能体">
                <el-select v-model="selectedNode.config.agentId" placeholder="请选择AI智能体" filterable style="width: 100%" @change="markDirty">
                  <el-option v-for="a in agentOptions" :key="a.agentId" :label="a.agentName" :value="a.agentId" />
                </el-select>
              </el-form-item>
              <el-form-item label="欢迎语">
                <el-input v-model="selectedNode.config.welcome" type="textarea" :rows="2"
                  placeholder="首轮无输入时播报，留空则使用智能体配置的欢迎语" @input="markDirty" />
              </el-form-item>
              <el-form-item label="最大对话轮数">
                <el-input-number v-model="selectedNode.config.maxTurns" :min="1" :max="20" @change="markDirty" />
                <div class="panel-hint">达到轮数自动转人工，防止无限循环。</div>
              </el-form-item>
              <el-form-item label="回复变量名">
                <el-input v-model="selectedNode.config.resultVar" placeholder="agentReply" @input="markDirty" />
              </el-form-item>
              <el-form-item label="转人工标记变量">
                <el-input v-model="selectedNode.config.handoffVar" placeholder="agentHandoff" @input="markDirty" />
                <div class="panel-hint">连线上用表达式 #agentHandoff == true 可分支到转人工。</div>
              </el-form-item>
            </template>

            <!-- 转人工 -->
            <template v-if="selectedNode.type === 'agent'">
              <el-form-item label="分配方式">
                <el-radio-group v-model="selectedNode.config.dispatchMode" @change="markDirty" size="mini">
                  <el-radio-button label="static">静态</el-radio-button>
                  <el-radio-button label="dispatch">智能分配</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <template v-if="selectedNode.config.dispatchMode === 'dispatch'">
                <el-form-item label="技能组">
                  <el-select v-model="selectedNode.config.groupId" placeholder="按分类自动匹配" clearable filterable style="width:100%" @change="markDirty">
                    <el-option v-for="g in skillGroups" :key="g.groupId" :label="g.groupName" :value="g.groupId" />
                  </el-select>
                </el-form-item>
                <el-form-item label="分类变量">
                  <el-input v-model="selectedNode.config.categoryVar" placeholder="默认 agentCategoryId" @input="markDirty" />
                </el-form-item>
                <el-form-item label="无空闲排队">
                  <el-switch v-model="selectedNode.config.enqueueIfNoAgent" @change="markDirty" />
                </el-form-item>
                <div class="panel-hint">智能分配按 B1 智能体写入的分类（agentCategoryId）匹配技能组；排队时变量 dispatchQueued=true，可连"排队提示"节点。</div>
              </template>
              <el-form-item v-else label="技能队列">
                <el-input v-model="selectedNode.config.queue" placeholder="坐席技能队列名称" @input="markDirty" />
              </el-form-item>
            </template>

            <!-- 转外线 -->
            <template v-if="selectedNode.type === 'transfer'">
              <el-form-item label="分配方式">
                <el-radio-group v-model="selectedNode.config.dispatchMode" @change="markDirty" size="mini">
                  <el-radio-button label="static">静态</el-radio-button>
                  <el-radio-button label="dispatch">智能分配</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="selectedNode.config.dispatchMode==='dispatch'" label="技能组">
                <el-select v-model="selectedNode.config.groupId" placeholder="按分类自动匹配" clearable filterable style="width:100%" @change="markDirty">
                  <el-option v-for="g in skillGroups" :key="g.groupId" :label="g.groupName" :value="g.groupId" />
                </el-select>
              </el-form-item>
              <el-form-item v-else label="目标号码">
                <el-input v-model="selectedNode.config.targetNumber" placeholder="如 12348 或 020-12348" @input="markDirty" />
              </el-form-item>
            </template>

            <!-- 条件分支 -->
            <template v-if="selectedNode.type === 'condition'">
              <el-form-item label="条件表达式">
                <el-input v-model="selectedNode.config.expr" placeholder="如 matchedIntention == 'CONTRACT_DISPUTE'" @input="markDirty" />
              </el-form-item>
              <div class="panel-hint">根据表达式结果走不同连线，各连线可设置条件表达式</div>
            </template>

            <!-- 变量赋值 -->
            <template v-if="selectedNode.type === 'variable'">
              <el-form-item label="变量列表">
                <div v-for="(item, idx) in selectedNode.config.variables" :key="idx" class="menu-option-row">
                  <el-input v-model="item.key" placeholder="变量名" @input="markDirty" />
                  <el-input v-model="item.val" placeholder="值（支持${变量}）" @input="markDirty" />
                  <el-button icon="el-icon-delete" size="mini" @click="removeVariableItem(idx)"></el-button>
                </div>
                <el-button size="mini" icon="el-icon-plus" @click="addVariableItem">添加变量</el-button>
              </el-form-item>
            </template>

            <template v-if="selectedNode.type === 'start'">
              <div class="panel-hint">开始节点是流程的入口，一个流程只能有一个开始节点。</div>
            </template>
            <template v-if="selectedNode.type === 'hangup'">
              <div class="panel-hint">挂断节点结束当前通话。</div>
            </template>

            <el-form-item>
              <el-button type="danger" size="mini" icon="el-icon-delete" @click="deleteSelected">删除节点</el-button>
            </el-form-item>
          </el-form>
        </template>

        <template v-else-if="selectedEdge">
          <div class="panel-title">连线属性</div>
          <el-form size="mini" label-width="70px">
            <el-form-item label="连线标签">
              <el-input v-model="selectedEdge.label" placeholder="如：是 / 按1 / 匹配" @input="markDirty" />
            </el-form-item>
            <el-form-item label="条件表达式">
              <el-input v-model="selectedEdge.condition" placeholder="如 dtmf == '1'" @input="markDirty" />
            </el-form-item>
            <div class="panel-hint">条件表达式由IVR执行引擎按 SpEL 解析，可引用流程变量，如 dtmf、matchedIntention 等。</div>
            <el-form-item>
              <el-button type="danger" size="mini" icon="el-icon-delete" @click="deleteSelected">删除连线</el-button>
            </el-form-item>
          </el-form>
        </template>

        <div v-else class="panel-empty">
          <i class="el-icon-mouse"></i>
          <p>点击画布上的节点或连线</p>
          <p>在此编辑属性</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getFlow, saveDesign } from '@/api/lawyers/ivrFlow'
import { getNodesByFlowId } from '@/api/lawyers/ivrNode'
import { getEdgesByFlowId } from '@/api/lawyers/ivrEdge'
import { listActiveAgent } from '@/api/lawyers/agent'
import { listEnabledGroups } from '@/api/lawyers/skill'

const NODE_W = 148
const NODE_H = 58
const CANVAS_W = 2600
const CANVAS_H = 1600

const TYPE_META = {
  start: { label: '开始', color: '#67C23A', icon: '▶' },
  say: { label: '语音播报', color: '#409EFF', icon: '♪' },
  answer: { label: '语音收声', color: '#2D8CF0', icon: '♫' },
  received: { label: 'DTMF收号', color: '#FF8C00', icon: '#' },
  menu: { label: '按键菜单', color: '#F59E0B', icon: '☰' },
  intention: { label: '意图识别', color: '#8A6DE9', icon: '◎' },
  agentChat: { label: '智能体', color: '#52C41A', icon: '🤖' },
  sentiment: { label: '情绪分析', color: '#F56C6C', icon: '♡' },
  extract: { label: '信息抽取', color: '#8A6DE9', icon: 'ƒ' },
  service: { label: 'HTTP服务', color: '#409EFF', icon: '⇄' },
  script: { label: '脚本执行', color: '#13C2C2', icon: 'ƒ' },
  child: { label: '子流程', color: '#722ED1', icon: '⊞' },
  condition: { label: '条件分支', color: '#F56C6C', icon: '◇' },
  agent: { label: '转人工', color: '#13C2C2', icon: '☎' },
  transfer: { label: '转外线', color: '#722ED1', icon: '↗' },
  variable: { label: '变量赋值', color: '#67C23A', icon: '=' },
  hangup: { label: '挂断', color: '#909399', icon: '■' }
}

const PALETTE_ORDER = ['start', 'say', 'answer', 'received', 'menu', 'intention', 'agentChat', 'sentiment', 'extract', 'service', 'script', 'child', 'condition', 'variable', 'agent', 'transfer', 'hangup']

function defaultConfig(type) {
  switch (type) {
    case 'say':
      return { text: '', voiceEngine: '', tts: {}, format: 'wav', sampleRate: 8000 }
    case 'answer':
      return { jqrask: '', silencePrompt: false, silenceDuration: 3, silenceSay: '' }
    case 'received':
      return { jqrask: '', endKey: '#', maxDigits: 11 }
    case 'menu':
      return { prompt: '', options: [{ key: '1', label: '选项一' }] }
    case 'intention':
      return { intentionCode: '', promptTemplate: '' }
    case 'agentChat':
      return { agentId: null, welcome: '', maxTurns: 5, resultVar: 'agentReply', handoffVar: 'agentHandoff' }
    case 'sentiment':
      return { text: '${lastInput}' }
    case 'extract':
      return { text: '${lastInput}', fields: [{ name: '', desc: '' }], resultVar: 'extractResult' }
    case 'service':
      return { url: '', method: 'GET', headers: '{}', body: '', result: '', resultVar: 'serviceResult', timeoutMs: 10000 }
    case 'script':
      return { scriptType: 'js', script: '', resultVar: 'scriptResult' }
    case 'child':
      return { flowId: null, flowCode: '', resultVar: 'childResult' }
    case 'agent':
      return { dispatchMode: 'static', groupId: null, categoryVar: 'agentCategoryId', enqueueIfNoAgent: true, queue: '' }
    case 'transfer':
      return { dispatchMode: 'static', groupId: null, targetNumber: '' }
    case 'condition':
      return { expr: '' }
    case 'variable':
      return { variables: [{ key: '', val: '' }] }
    default:
      return {}
  }
}

function defaultName(type, count) {
  const base = TYPE_META[type] ? TYPE_META[type].label : type
  const same = count[type] || 0
  count[type] = (count[type] || 0) + 1
  return same === 0 ? base : base + (same + 1)
}

function parseConfig(str) {
  if (!str) return {}
  try {
    const obj = JSON.parse(str)
    return obj && typeof obj === 'object' ? obj : {}
  } catch (e) {
    return {}
  }
}

export default {
  name: 'IvrFlowDesigner',
  data() {
    return {
      flowId: null,
      flow: {},
      nodes: [],
      edges: [],
      selectedKind: null,
      selectedId: null,
      zoom: 1,
      pendingType: null,
      tempId: -1,
      loading: false,
      saving: false,
      dirty: false,
      dragState: null,
      edgeDraft: null,
      nodeW: NODE_W,
      nodeH: NODE_H,
      canvasW: CANVAS_W,
      canvasH: CANVAS_H,
      agentOptions: [],
      skillGroups: []
    }
  },
  computed: {
    paletteTypes() {
      return PALETTE_ORDER.map(t => ({ type: t, ...TYPE_META[t] }))
    },
    pendingLabel() {
      return this.pendingType && TYPE_META[this.pendingType] ? TYPE_META[this.pendingType].label : ''
    },
    selectedNode() {
      if (this.selectedKind !== 'node') return null
      return this.nodes.find(n => n.id === this.selectedId) || null
    },
    selectedEdge() {
      if (this.selectedKind !== 'edge') return null
      return this.edges.find(e => e.id === this.selectedId) || null
    },
    draftPath() {
      if (!this.edgeDraft) return ''
      const x1 = this.edgeDraft.x
      const y1 = this.edgeDraft.y
      const x2 = this.edgeDraft.mx
      const y2 = this.edgeDraft.my
      const dx = Math.max(60, Math.abs(x2 - x1) / 2)
      return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
    }
  },
  created() {
    this.flowId = Number(this.$route.query.flowId)
  },
  mounted() {
    window.addEventListener('mousemove', this.onWindowMouseMove)
    window.addEventListener('mouseup', this.onWindowMouseUp)
    window.addEventListener('keydown', this.onKeyDown)
    this.loadAgents()
    this.loadDesign()
  },
  beforeDestroy() {
    window.removeEventListener('mousemove', this.onWindowMouseMove)
    window.removeEventListener('mouseup', this.onWindowMouseUp)
    window.removeEventListener('keydown', this.onKeyDown)
  },
  methods: {
    // ---------- 加载 ----------
    loadAgents() {
      listActiveAgent().then(res => {
        this.agentOptions = res.data || []
      }).catch(() => {
        this.agentOptions = []
      })
      listEnabledGroups().then(res => {
        this.skillGroups = res.data || []
      }).catch(() => {
        this.skillGroups = []
      })
    },
    async loadDesign() {
      if (!this.flowId) {
        this.$message.warning('缺少流程ID参数')
        this.goBack()
        return
      }
      this.loading = true
      try {
        const flowRes = await getFlow(this.flowId)
        this.flow = flowRes.data || {}
        const nodeRes = await getNodesByFlowId(this.flowId)
        const edgeRes = await getEdgesByFlowId(this.flowId)
        this.nodes = (nodeRes.data || []).map(n => ({
          id: n.nodeId,
          type: n.nodeType,
          name: n.nodeName,
          x: n.positionX || 0,
          y: n.positionY || 0,
          config: { ...defaultConfig(n.nodeType), ...parseConfig(n.nodeConfig) }
        }))
        this.edges = (edgeRes.data || []).map(e => ({
          id: e.edgeId,
          source: e.sourceNodeId,
          target: e.targetNodeId,
          label: e.edgeLabel || '',
          condition: e.conditionExpr || ''
        }))
        this.tempId = -1
        if (!this.nodes.length) {
          this.seedDefault()
        }
        this.clearSelection()
        this.dirty = false
      } catch (e) {
        this.$message.error('加载流程设计失败')
      } finally {
        this.loading = false
      }
    },

    seedDefault() {
      const count = {}
      this.nodes.push({
        id: this.nextId(),
        type: 'start',
        name: defaultName('start', count),
        x: 400,
        y: 250,
        config: {}
      })
      this.dirty = true
    },

    // ---------- 节点操作 ----------
    nextId() {
      return this.tempId--
    },

    addNode(type, x, y) {
      if (type === 'start' && this.nodes.some(n => n.type === 'start')) {
        this.$message.warning('一个流程只能有一个开始节点')
        return false
      }
      const count = {}
      this.nodes.forEach(n => {
        count[n.type] = (count[n.type] || 0) + 1
      })
      const node = {
        id: this.nextId(),
        type,
        name: defaultName(type, count),
        x: Math.max(0, Math.min(x, CANVAS_W - NODE_W)),
        y: Math.max(0, Math.min(y, CANVAS_H - NODE_H)),
        config: defaultConfig(type)
      }
      this.nodes.push(node)
      this.selectNode(node)
      this.markDirty()
      return true
    },

    removeNode(node) {
      this.edges = this.edges.filter(e => e.source !== node.id && e.target !== node.id)
      this.nodes = this.nodes.filter(n => n.id !== node.id)
      this.clearSelection()
      this.markDirty()
    },

    onPaletteClick(type) {
      this.pendingType = this.pendingType === type ? null : type
    },

    onPaletteDragStart(e, type) {
      e.dataTransfer.setData('text/ivr-node-type', type)
      e.dataTransfer.effectAllowed = 'copy'
      this.pendingType = null
    },

    onCanvasDrop(e) {
      e.preventDefault()
      const type = e.dataTransfer.getData('text/ivr-node-type')
      if (!type || !TYPE_META[type]) return
      const p = this.getPoint(e)
      this.addNode(type, p.x - NODE_W / 2, p.y - NODE_H / 2)
    },

    onCanvasClick(e) {
      if (this.pendingType) {
        const p = this.getPoint(e)
        if (this.addNode(this.pendingType, p.x - NODE_W / 2, p.y - NODE_H / 2)) {
          this.pendingType = null
        }
      } else {
        this.clearSelection()
      }
    },

    // ---------- 节点选中/拖动 ----------
    selectNode(node) {
      this.selectedKind = 'node'
      this.selectedId = node.id
    },

    selectEdge(edge) {
      this.selectedKind = 'edge'
      this.selectedId = edge.id
    },

    clearSelection() {
      this.selectedKind = null
      this.selectedId = null
    },

    isSelected(kind, id) {
      return this.selectedKind === kind && this.selectedId === id
    },

    onNodeMouseDown(e, node) {
      if (e.button !== 0) return
      const port = e.target && e.target.getAttribute ? e.target.getAttribute('data-port') : null
      if (port === 'out') {
        this.startEdge(node)
        return
      }
      this.selectNode(node)
      this.pendingType = null
      this.dragState = {
        node,
        startX: e.clientX,
        startY: e.clientY,
        origX: node.x,
        origY: node.y,
        moved: false
      }
    },

    onWindowMouseMove(e) {
      if (this.dragState) {
        const dx = (e.clientX - this.dragState.startX) / this.zoom
        const dy = (e.clientY - this.dragState.startY) / this.zoom
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
          this.dragState.moved = true
        }
        this.dragState.node.x = Math.max(0, Math.min(CANVAS_W - NODE_W, this.dragState.origX + dx))
        this.dragState.node.y = Math.max(0, Math.min(CANVAS_H - NODE_H, this.dragState.origY + dy))
      }
      if (this.edgeDraft) {
        const p = this.getPoint(e)
        this.edgeDraft.mx = p.x
        this.edgeDraft.my = p.y
      }
    },

    onWindowMouseUp() {
      if (this.dragState) {
        if (this.dragState.moved) {
          this.markDirty()
        }
        this.dragState = null
      }
      if (this.edgeDraft) {
        const p = { x: this.edgeDraft.mx, y: this.edgeDraft.my }
        const target = this.findNodeAt(p)
        if (target && target.id !== this.edgeDraft.source) {
          this.createEdge(this.edgeDraft.source, target.id)
        }
        this.edgeDraft = null
      }
    },

    startEdge(node) {
      if (node.type === 'hangup') {
        this.$message.warning('挂断节点是终止节点，不能继续连线')
        return
      }
      this.edgeDraft = {
        source: node.id,
        x: node.x + NODE_W,
        y: node.y + NODE_H / 2,
        mx: node.x + NODE_W + 60,
        my: node.y + NODE_H / 2
      }
    },

    createEdge(source, target) {
      if (this.edges.some(e => e.source === source && e.target === target)) {
        this.$message.warning('这两个节点之间已存在连线')
        return
      }
      const edge = {
        id: this.nextId(),
        source,
        target,
        label: '',
        condition: ''
      }
      this.edges.push(edge)
      this.selectEdge(edge)
      this.markDirty()
    },

    findNodeAt(p) {
      for (let i = this.nodes.length - 1; i >= 0; i--) {
        const n = this.nodes[i]
        if (p.x >= n.x - 12 && p.x <= n.x + NODE_W + 12 && p.y >= n.y - 12 && p.y <= n.y + NODE_H + 12) {
          return n
        }
      }
      return null
    },

    // ---------- 渲染辅助 ----------
    getPoint(e) {
      const wrap = this.$refs.canvasWrap
      const rect = wrap.getBoundingClientRect()
      return {
        x: (e.clientX - rect.left + wrap.scrollLeft) / this.zoom,
        y: (e.clientY - rect.top + wrap.scrollTop) / this.zoom
      }
    },

    nodeById(id) {
      return this.nodes.find(n => n.id === id) || null
    },

    nodeColor(node) {
      return TYPE_META[node.type] ? TYPE_META[node.type].color : '#409EFF'
    },

    nodeIcon(node) {
      return TYPE_META[node.type] ? TYPE_META[node.type].icon : '?'
    },

    nodeTypeLabel(type) {
      return TYPE_META[type] ? TYPE_META[type].label : type
    },

    nodeName(node) {
      return node.name || TYPE_META[node.type].label
    },

    nodeBodyStyle(node) {
      const c = this.nodeColor(node)
      return {
        fill: '#FFFFFF',
        stroke: c,
        strokeWidth: this.isSelected('node', node.id) ? 2.5 : 1.5,
        filter: this.isSelected('node', node.id) ? 'drop-shadow(0 2px 6px rgba(0,0,0,0.15))' : 'none'
      }
    },

    portStyle(node) {
      return {
        fill: '#FFFFFF',
        stroke: this.nodeColor(node),
        strokeWidth: 2
      }
    },

    edgePath(edge) {
      const s = this.nodeById(edge.source)
      const t = this.nodeById(edge.target)
      if (!s || !t) return ''
      const x1 = s.x + NODE_W
      const y1 = s.y + NODE_H / 2
      const x2 = t.x
      const y2 = t.y + NODE_H / 2
      const dx = Math.max(60, Math.abs(x2 - x1) / 2)
      return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
    },

    edgeLabelPos(edge) {
      const s = this.nodeById(edge.source)
      const t = this.nodeById(edge.target)
      if (!s || !t) return 'translate(0,0)'
      const x1 = s.x + NODE_W
      const y1 = s.y + NODE_H / 2
      const x2 = t.x
      const y2 = t.y + NODE_H / 2
      return `translate(${(x1 + x2) / 2}, ${(y1 + y2) / 2})`
    },

    edgeLabel(edge) {
      return edge.label || edge.condition || ''
    },

    labelW(edge) {
      const len = (edge.label || edge.condition || '').length
      return Math.max(24, len * 12 + 14)
    },

    // ---------- 删除 ----------
    deleteSelected() {
      if (this.selectedKind === 'node' && this.selectedNode) {
        const node = this.selectedNode
        this.$confirm(`确定删除节点「${node.name}」及其所有连线吗？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.removeNode(node)
        }).catch(() => {})
      } else if (this.selectedKind === 'edge' && this.selectedEdge) {
        this.edges = this.edges.filter(e => e.id !== this.selectedEdge.id)
        this.clearSelection()
        this.markDirty()
      }
    },

    onKeyDown(e) {
      const tag = document.activeElement && document.activeElement.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA') return
      if ((e.key === 'Delete' || e.key === 'Backspace') && this.selectedKind) {
        e.preventDefault()
        if (this.selectedKind === 'edge') {
          this.edges = this.edges.filter(e => e.id !== this.selectedId)
          this.clearSelection()
          this.markDirty()
        } else if (this.selectedNode) {
          this.removeNode(this.selectedNode)
        }
      }
      if (e.key === 'Escape') {
        this.edgeDraft = null
        this.dragState = null
        this.pendingType = null
        this.clearSelection()
      }
    },

    // ---------- 菜单选项 ----------
    addMenuOption() {
      if (!this.selectedNode || this.selectedNode.type !== 'menu') return
      const options = this.selectedNode.config.options || []
      options.push({ key: String(options.length + 1), label: '选项' + (options.length + 1) })
      this.markDirty()
    },

    removeMenuOption(idx) {
      if (!this.selectedNode || this.selectedNode.type !== 'menu') return
      this.selectedNode.config.options.splice(idx, 1)
      this.markDirty()
    },

    addExtractField() {
      if (!this.selectedNode || this.selectedNode.type !== 'extract') return
      this.selectedNode.config.fields.push({ name: '', desc: '' })
      this.markDirty()
    },

    removeExtractField(idx) {
      if (!this.selectedNode || this.selectedNode.type !== 'extract') return
      this.selectedNode.config.fields.splice(idx, 1)
      this.markDirty()
    },

    addVariableItem() {
      if (!this.selectedNode || this.selectedNode.type !== 'variable') return
      this.selectedNode.config.variables.push({ key: '', val: '' })
      this.markDirty()
    },

    removeVariableItem(idx) {
      if (!this.selectedNode || this.selectedNode.type !== 'variable') return
      this.selectedNode.config.variables.splice(idx, 1)
      this.markDirty()
    },

    // ---------- 缩放 ----------
    zoomIn() {
      this.zoom = Math.min(2, Math.round((this.zoom + 0.1) * 10) / 10)
    },

    zoomOut() {
      this.zoom = Math.max(0.5, Math.round((this.zoom - 0.1) * 10) / 10)
    },

    resetView() {
      this.zoom = 1
      this.$nextTick(() => {
        const wrap = this.$refs.canvasWrap
        if (wrap) {
          wrap.scrollLeft = 0
          wrap.scrollTop = 0
        }
      })
    },

    // ---------- 保存 ----------
    markDirty() {
      this.dirty = true
    },

    validateDesign() {
      if (!this.nodes.length) {
        this.$message.warning('流程图中至少需要一个节点')
        return false
      }
      if (!this.nodes.some(n => n.type === 'start')) {
        this.$message.warning('流程图中必须包含一个开始节点')
        return false
      }
      const ids = this.nodes.map(n => n.id)
      for (const e of this.edges) {
        if (!ids.includes(e.source) || !ids.includes(e.target)) {
          this.$message.warning('存在无效连线，请检查后再保存')
          return false
        }
      }
      return true
    },

    buildPayload() {
      const nodes = this.nodes.map((n, i) => ({
        nodeId: n.id,
        nodeType: n.type,
        nodeName: n.name || TYPE_META[n.type].label,
        nodeConfig: JSON.stringify(n.config || {}),
        positionX: Math.round(n.x),
        positionY: Math.round(n.y),
        sortOrder: i
      }))
      const edges = this.edges.map((e, i) => ({
        sourceNodeId: e.source,
        targetNodeId: e.target,
        edgeLabel: e.label,
        conditionExpr: e.condition,
        sortOrder: i
      }))
      const flowData = {
        nodes: nodes.map(n => ({
          id: String(n.nodeId),
          type: n.nodeType,
          x: n.positionX,
          y: n.positionY,
          text: { value: n.nodeName },
          properties: JSON.parse(n.nodeConfig)
        })),
        edges: edges.map(e => ({
          id: String(e.sourceNodeId) + '-' + String(e.targetNodeId),
          type: 'polyline',
          sourceNodeId: String(e.sourceNodeId),
          targetNodeId: String(e.targetNodeId),
          text: { value: e.edgeLabel || e.conditionExpr || '' },
          properties: { conditionExpr: e.conditionExpr || '' }
        }))
      }
      return {
        flowId: this.flowId,
        flowData: JSON.stringify(flowData),
        nodes,
        edges
      }
    },

    async handleSave() {
      if (!this.validateDesign()) return
      this.saving = true
      try {
        await saveDesign(this.buildPayload())
        this.$modal.msgSuccess('保存成功')
        this.dirty = false
        await this.loadDesign()
      } catch (e) {
        this.$message.error('保存失败，请稍后重试')
      } finally {
        this.saving = false
      }
    },

    goBack() {
      if (this.dirty) {
        this.$confirm('当前有未保存的修改，确定离开吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.$router.push('/ivrFlow/flow')
        }).catch(() => {})
      } else {
        this.$router.push('/ivrFlow/flow')
      }
    }
  }
}
</script>

<style scoped>
.ivr-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  background: #f5f7fa;
  overflow: hidden;
}

.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
}

.flow-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 10px;
}

.dirty-tip {
  color: #F59E0B;
  font-size: 12px;
  margin-left: 8px;
}

.designer-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* 左侧面板 */
.palette {
  width: 216px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  padding: 16px 14px;
  overflow-y: auto;
}

.palette-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.palette-tip {
  font-size: 11px;
  color: #909399;
  margin-bottom: 10px;
}

.palette-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: grab;
  background: #fff;
  transition: all 0.2s;
}

.palette-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.palette-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.palette-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #fff;
  font-size: 13px;
  margin-right: 10px;
  flex-shrink: 0;
}

.palette-label {
  font-size: 13px;
  color: #303133;
}

.palette-help {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 11px;
  color: #909399;
  line-height: 1.8;
}

.palette-help p {
  margin: 0;
}

/* 画布 */
.canvas-wrap {
  flex: 1;
  position: relative;
  overflow: auto;
  background: #f5f7fa;
  background-image: radial-gradient(circle, #d9dee3 1px, transparent 1px);
  background-size: 24px 24px;
  user-select: none;
}

.canvas {
  width: 2600px;
  height: 1600px;
  position: relative;
}

.designer-svg {
  display: block;
  position: absolute;
  top: 0;
  left: 0;
}

.edge-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 12;
  cursor: pointer;
}

.edge-line {
  fill: none;
  stroke: #909399;
  stroke-width: 2;
  cursor: pointer;
}

.edge-line.selected {
  stroke: #409eff;
  stroke-width: 3;
}

.edge-line.draft {
  stroke: #409eff;
  stroke-width: 2;
  stroke-dasharray: 6 4;
}

.edge-label {
  cursor: pointer;
}

.node-group {
  cursor: move;
}

.node-body {
  cursor: move;
}

.node-name {
  font-weight: 600;
}

.port {
  cursor: crosshair;
}

.port-in {
  cursor: default;
}

.pending-tip {
  position: sticky;
  bottom: 16px;
  left: 16px;
  display: inline-block;
  padding: 6px 12px;
  background: #409eff;
  color: #fff;
  border-radius: 4px;
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.4);
  z-index: 10;
}

/* 右侧面板 */
.config-panel {
  width: 340px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  padding: 16px 18px;
  overflow-y: auto;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.panel-hint {
  font-size: 11px;
  color: #909399;
  line-height: 1.6;
  margin: 4px 0 8px;
}

.panel-empty {
  margin-top: 120px;
  text-align: center;
  color: #c0c4cc;
}

.panel-empty i {
  font-size: 36px;
}

.panel-empty p {
  margin: 8px 0 0;
  font-size: 13px;
}

.menu-option-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
</style>
