<template>
  <div class="call-popup">
    <!-- 来电横幅 -->
      <div class="cp-banner" :class="direction === 'out' ? 'is-out' : 'is-in'">
      <div class="cp-banner-left">
        <div class="cp-banner-icon"><i class="el-icon-phone"></i></div>
        <div class="cp-banner-title">
          <div class="cp-banner-main">{{ direction === 'out' ? '呼出' : '来电接入' }}</div>
          <div class="cp-banner-sub"><span class="cp-dot"></span>{{ connected ? callStatusText : (callEnded ? '通话已结束' : '等待接听') }}</div>
        </div>
      </div>
      <div class="cp-banner-center">
        <div class="cp-banner-number">{{ profileData.callerNumber || '-' }}</div>
        <div class="cp-banner-info">
          <span><i class="el-icon-location-outline"></i> {{ profileData.address || '归属地未知' }}</span>
        </div>
      </div>
      <div class="cp-banner-right">
        <div class="cp-banner-time">
          <div class="cp-time-label">通话时长</div>
          <div class="cp-time-value">{{ callDuration }}</div>
        </div>
      </div>
    </div>

    <el-row :gutter="16" class="cp-main">
      <!-- 左侧：来电人信息 + 智能分析 -->
      <el-col :span="8">
        <el-card shadow="never" class="cp-card">
          <div slot="header" class="cp-card-header">
            <span>来电人信息</span>
            <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEditProfile">编辑</el-button>
          </div>
          <div class="cp-caller-header">
            <div class="cp-avatar">{{ avatarText }}</div>
            <div class="cp-caller-info">
              <div class="cp-caller-name">
                {{ profileData.callerName || '未知来电人' }}
                <el-tag size="mini" type="warning" effect="light" v-if="profileData.monthCallCount">第{{ profileData.monthCallCount }}次来电</el-tag>
              </div>
              <div class="cp-caller-phone">{{ profileData.callerNumber || '-' }}</div>
            </div>
          </div>
          <div class="cp-caller-detail">
            <div class="cp-detail-item"><span class="cp-detail-label">归属地</span><span class="cp-detail-value">{{ profileData.address || '-' }}</span></div>
            <div class="cp-detail-item"><span class="cp-detail-label">来电次数</span><span class="cp-detail-value">本月{{ profileData.monthCallCount || 0 }}次 / 累计{{ profileData.callCount || 0 }}次</span></div>
            <div class="cp-detail-item"><span class="cp-detail-label">最后来电</span><span class="cp-detail-value">{{ profileData.lastCallTime || '-' }}</span></div>
          </div>
          <div class="cp-caller-tags" v-if="profileData.tags">
            <el-tag v-for="(tag, idx) in tagsArray" :key="idx" size="mini" effect="dark" :type="tagType(idx)">{{ tag }}</el-tag>
          </div>
        </el-card>

        <!-- 智能来电分析 -->
        <el-card shadow="never" class="cp-card cp-ai-card">
          <div slot="header" class="cp-card-header">
            <span class="cp-ai-title"><span class="cp-ai-badge">AI</span> 智能来电分析</span>
          </div>
          <el-row :gutter="8" class="cp-ai-grid">
            <el-col :span="12">
              <div class="cp-ai-item">
                <div class="cp-ai-label">来电意图预测</div>
                <div class="cp-ai-value">{{ profileData.intentPrediction }}</div>
                <div class="cp-ai-confidence">
                  <el-progress :percentage="profileData.intentConfidence || 0" :show-text="false" :stroke-width="4" color="#7C3AED" />
                  <span>置信度 {{ profileData.intentConfidence || 0 }}%</span>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="cp-ai-item">
                <div class="cp-ai-label">风险等级</div>
                <div class="cp-ai-value" :class="'cp-risk-' + profileData.riskLevel">{{ riskLabel(profileData.riskLevel) }}</div>
                <div class="cp-ai-sub">{{ profileData.consultPreference || '暂无偏好数据' }}</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="cp-ai-item">
                <div class="cp-ai-label">高频问题</div>
                <div class="cp-ai-value cp-ai-freq">{{ profileData.highFreqProblem || '暂无' }}</div>
                <div class="cp-ai-sub" v-if="profileData.freqMentionCount">提及{{ profileData.freqMentionCount }}次</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="cp-ai-item">
                <div class="cp-ai-label">情绪状态</div>
                <div class="cp-ai-value">{{ profileData.emotionStatus || '平稳' }}</div>
                <div class="cp-ai-sub">{{ profileData.customerLevel || '普通客户' }}</div>
              </div>
            </el-col>
          </el-row>
          <div class="cp-emotion-warning" v-if="profileData.emotionWarning">
            <i class="el-icon-warning-outline"></i>
            <span>{{ profileData.emotionWarning }}</span>
          </div>
        </el-card>

        <!-- P3-A6：坐席实时字幕（/ws/voice，随通话接通激活，挂断自动卸载） -->
        <voice-caption v-if="connected" :session-id="currentRecordId" @final="onCaptionFinal"
                       @copilot-element="onCopilotElement"
                       @copilot-laws="onCopilotLaws"
                       @copilot-tickets="onCopilotTickets"
                       @copilot-actions="onCopilotActions" />

        <!-- 独立 AI 律师辅助面板（与人工接听链路分离，仅以 currentRecordId 关联） -->
        <ai-assist-panel v-if="connected" :record-id="currentRecordId"
                         :live-text="liveCaptionText" :copilot="copilotData" />
      </el-col>

      <!-- 右侧：5 标签页 -->
      <el-col :span="16">
        <el-card shadow="never" class="cp-card cp-history-card">
          <div slot="header" class="cp-card-header">
            <el-tabs v-model="activeTab" class="cp-history-tabs">
              <el-tab-pane label="基本信息" name="base"></el-tab-pane>
              <el-tab-pane label="历史通话" name="call"></el-tab-pane>
              <el-tab-pane label="工单记录" name="ticket"></el-tab-pane>
              <el-tab-pane label="来电轨迹" name="track"></el-tab-pane>
              <el-tab-pane label="智能分析" name="analysis"></el-tab-pane>
            </el-tabs>
          </div>
          <div class="cp-history-content" v-loading="tabLoading">
            <!-- 基本信息 -->
            <div v-if="activeTab === 'base'">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="来电号码">{{ profileData.callerNumber || '-' }}</el-descriptions-item>
                <el-descriptions-item label="来电人">{{ profileData.callerName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="性别">{{ genderLabel(profileData.callerGender) }}</el-descriptions-item>
                <el-descriptions-item label="年龄">{{ profileData.callerAge || '-' }}</el-descriptions-item>
                <el-descriptions-item label="身份证号">{{ profileData.callerIdCard || '-' }}</el-descriptions-item>
                <el-descriptions-item label="客户等级">{{ profileData.customerLevel || '-' }}</el-descriptions-item>
                <el-descriptions-item label="联系地址" :span="2">{{ profileData.address || '-' }}</el-descriptions-item>
                <el-descriptions-item label="标签" :span="2">{{ profileData.tags || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
            <!-- 历史通话 -->
            <div v-else-if="activeTab === 'call'">
              <div v-if="historyList.length === 0" class="cp-empty">暂无历史通话</div>
              <div class="cp-call-item" v-for="item in historyList" :key="item.recordId">
                <div class="cp-call-icon"><i class="el-icon-phone"></i></div>
                <div class="cp-call-main">
                  <div class="cp-call-header">
                    <span class="cp-call-title">{{ item.categoryName || '通话记录' }}</span>
                    <el-tag size="mini" :type="recordStatusTag(item.status)" :class="recordStatusClass(item.status)" effect="plain">{{ recordStatusLabel(item.status) }}</el-tag>
                    <span class="cp-call-time">{{ item.callTime }}</span>
                  </div>
                  <div class="cp-call-footer">
                    <span class="cp-call-duration">时长 {{ formatDuration(item.duration) }}</span>
                    <span class="cp-call-content">{{ item.content || '' }}</span>
                  </div>
                </div>
              </div>
            </div>
            <!-- 工单记录 -->
            <div v-else-if="activeTab === 'ticket'">
              <div v-if="ticketList.length === 0" class="cp-empty">暂无工单记录</div>
              <el-table :data="ticketList" size="small" border v-else>
                <el-table-column label="工单号" prop="ticketNo" width="140" />
                <el-table-column label="工单标题" prop="title" />
                <el-table-column label="优先级" align="center" width="80">
                  <template slot-scope="scope">{{ priorityLabel(scope.row.priority) }}</template>
                </el-table-column>
                <el-table-column label="状态" align="center" width="90">
                  <template slot-scope="scope"><el-tag size="mini" :type="ticketStatusTag(scope.row.status)" :class="ticketStatusClass(scope.row.status)">{{ ticketStatusLabel(scope.row.status) }}</el-tag></template>
                </el-table-column>
                <el-table-column label="创建时间" prop="createTime" width="160" />
              </el-table>
            </div>
            <!-- 来电轨迹 -->
            <div v-else-if="activeTab === 'track'">
              <div v-if="trackList.length === 0" class="cp-empty">暂无来电轨迹</div>
              <el-timeline v-else>
                <el-timeline-item v-for="(node, idx) in trackList" :key="idx" :timestamp="node.time" placement="top" :type="node.type === 'call' ? 'primary' : 'success'">
                  <el-tag size="mini" :type="node.type === 'call' ? 'primary' : 'success'">{{ node.type === 'call' ? '通话' : '工单' }}</el-tag>
                  <span class="cp-track-title">{{ node.title }}</span>
                  <div class="cp-track-content">{{ node.content || '' }}</div>
                </el-timeline-item>
              </el-timeline>
            </div>
            <!-- 智能分析 -->
            <div v-else-if="activeTab === 'analysis'">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="意图预测">{{ profileData.intentPrediction }}</el-descriptions-item>
                <el-descriptions-item label="意图置信度">{{ profileData.intentConfidence || 0 }}%</el-descriptions-item>
                <el-descriptions-item label="咨询偏好">{{ profileData.consultPreference || '-' }}</el-descriptions-item>
                <el-descriptions-item label="高频问题">{{ profileData.highFreqProblem || '-' }}</el-descriptions-item>
                <el-descriptions-item label="风险等级">{{ riskLabel(profileData.riskLevel) }}</el-descriptions-item>
                <el-descriptions-item label="情绪状态">{{ profileData.emotionStatus || '-' }}</el-descriptions-item>
                <el-descriptions-item label="情绪预警" :span="2">{{ profileData.emotionWarning || '无' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 通话操作按钮（U1：快捷键可在设置中自定义，界面提示实时同步） -->
    <div class="cp-action-bar">
      <el-button type="success" size="medium" class="cp-action-btn cp-btn-green" @click="handleAnswer" :disabled="connected || !incomingRinging">
        <i class="el-icon-microphone"></i><span>接听</span><kbd class="cp-kbd">{{ shortcutLabel('answer') }}</kbd>
      </el-button>
      <el-button size="medium" class="cp-action-btn cp-btn-orange" :disabled="!connected || !(callStatus === '1' || callStatus === '2')" @click="handleHold">
        <i :class="callStatus === '2' ? 'el-icon-video-play' : 'el-icon-video-pause'"></i><span>{{ callStatus === '2' ? '恢复' : '保持' }}</span><kbd class="cp-kbd">{{ shortcutLabel('hold') }}</kbd>
      </el-button>
      <el-button size="medium" class="cp-action-btn cp-btn-gray" :disabled="!connected || callStatus !== '1'" @click="handleTransfer">
        <i class="el-icon-s-promotion"></i><span>转接</span><kbd class="cp-kbd">{{ shortcutLabel('transfer') }}</kbd>
      </el-button>
      <el-button type="primary" size="medium" class="cp-action-btn cp-btn-blue" @click="handleLedger">
        <i class="el-icon-document"></i><span>一键登记台账</span><kbd class="cp-kbd">{{ shortcutLabel('ledger') }}</kbd>
      </el-button>
      <el-button size="medium" class="cp-action-btn cp-btn-purple" :disabled="!connected || !(callStatus === '1' || callStatus === '5')" @click="handleAfterWork">
        <i class="el-icon-edit-outline"></i><span>话后整理</span><kbd class="cp-kbd">{{ shortcutLabel('afterWork') }}</kbd>
      </el-button>
      <el-button v-hasPermi="['lawyers:call:ticket:add']" size="medium" class="cp-action-btn cp-btn-blue" :disabled="!profileData.callerNumber" @click="openQuickTicket">
        <i class="el-icon-tickets"></i><span>快速建单</span><kbd class="cp-kbd">{{ shortcutLabel('createTicket') }}</kbd>
      </el-button>
      <el-button v-hasPermi="['lawyers:ticketTransfer:add']" size="medium" class="cp-action-btn cp-btn-gray" :disabled="!profileData.callerNumber" @click="openTransferOut">
        <i class="el-icon-connection"></i><span>转办</span><kbd class="cp-kbd">{{ shortcutLabel('transferOut') }}</kbd>
      </el-button>
      <el-button v-hasPermi="['lawyers:knowledge:query']" size="medium" class="cp-action-btn cp-btn-purple" @click="openKnowledge">
        <i class="el-icon-search"></i><span>查知识</span><kbd class="cp-kbd">{{ shortcutLabel('knowledge') }}</kbd>
      </el-button>
      <el-button type="danger" size="medium" class="cp-action-btn cp-btn-red" :disabled="!connected" @click="handleHangup">
        <i class="el-icon-bangzhu"></i><span>挂机</span><kbd class="cp-kbd">{{ shortcutLabel('hangup') }}</kbd>
      </el-button>
      <el-button type="text" class="cp-shortcut-setting" icon="el-icon-setting" title="自定义快捷键" @click="openShortcutSettings">快捷键</el-button>
    </div>

    <!-- 编辑档案弹窗 -->
    <el-dialog title="编辑来电人档案" :visible.sync="editOpen" width="600px" append-to-body>
      <el-form ref="profileForm" :model="profileForm" label-width="100px" size="small">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="来电人"><el-input v-model="profileForm.callerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="来电号码"><el-input v-model="profileForm.callerNumber" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="性别"><el-select v-model="profileForm.callerGender" style="width:100%"><el-option label="男" value="0" /><el-option label="女" value="1" /><el-option label="未知" value="2" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="年龄"><el-input-number v-model="profileForm.callerAge" :min="0" :max="150" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="联系地址"><el-input v-model="profileForm.callerAddress" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户等级"><el-input v-model="profileForm.customerLevel" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="标签"><el-input v-model="profileForm.tags" placeholder="多个用逗号分隔" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="意图预测"><el-input v-model="profileForm.intentPrediction" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="意图置信度"><el-input-number v-model="profileForm.intentConfidence" :min="0" :max="100" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="风险等级"><el-select v-model="profileForm.riskLevel" style="width:100%"><el-option label="低" value="0" /><el-option label="中" value="1" /><el-option label="高" value="2" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="情绪状态"><el-input v-model="profileForm.emotionStatus" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="语种偏好（F1）"><el-select v-model="profileForm.languagePreference" style="width:100%"><el-option label="普通话" value="zh-CN" /><el-option label="粤语" value="yue-CN" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关怀模式（F2）"><el-switch v-model="profileForm.careMode" :active-value="1" :inactive-value="0" active-text="适老大字" inactive-text="标准" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div slot="footer">
        <el-button @click="editOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitProfile">保 存</el-button>
      </div>
    </el-dialog>

    <!-- 转接来电弹窗 -->
    <el-dialog title="转接来电" :visible.sync="transferOpen" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="目标坐席">
          <el-select v-model="transferTargetId" placeholder="请选择坐席" filterable clearable style="width:100%">
            <el-option
              v-for="a in onlineAgentOptions"
              :key="a.agentId"
              :label="a.agentName + '（' + a.agentId + '）'"
              :value="a.agentId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="transferOpen = false">取 消</el-button>
        <el-button type="primary" @click="confirmTransfer" :disabled="transferTargetId == null">转 接</el-button>
      </div>
    </el-dialog>

    <!-- U1：通话卡片内直接建单，不必离开工作台 -->
    <el-dialog title="快速建单" :visible.sync="quickTicketOpen" width="560px" append-to-body>
      <el-form ref="quickTicketForm" :model="quickTicketForm" :rules="quickTicketRules" label-width="90px" size="small">
        <el-form-item label="来电号码">
          <el-input :value="profileData.callerNumber" disabled />
        </el-form-item>
        <el-form-item label="工单标题" prop="title">
          <el-input v-model="quickTicketForm.title" maxlength="120" show-word-limit placeholder="一句话概括来电诉求" />
        </el-form-item>
        <el-form-item label="紧急程度" prop="priority">
          <el-radio-group v-model="quickTicketForm.priority">
            <el-radio label="1">一般</el-radio>
            <el-radio label="2">紧急</el-radio>
            <el-radio label="3">特急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="诉求详情" prop="content">
          <el-input v-model="quickTicketForm.content" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="记录来电人陈述、已答复内容与待办事项" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="quickTicketOpen = false">取 消</el-button>
        <el-button type="primary" :loading="quickTicketSubmitting" @click="submitQuickTicket">创建工单</el-button>
      </div>
    </el-dialog>

    <!-- U1：通话卡片内查知识，结果可复制到话后整理 -->
    <el-dialog title="知识速查" :visible.sync="knowledgeOpen" width="640px" append-to-body>
      <el-input
        ref="knowledgeInput"
        v-model="knowledgeKeyword"
        placeholder="输入关键词后回车检索（如：劳动仲裁、离婚财产分割）"
        clearable
        @keyup.enter.native="searchKnowledgeBase"
      >
        <el-button slot="append" icon="el-icon-search" @click="searchKnowledgeBase">检索</el-button>
      </el-input>
      <div class="cp-kb-tip">检索结果可直接复制到话后整理，避免切页丢失通话上下文</div>
      <div v-loading="knowledgeLoading" class="cp-kb-list">
        <div v-if="!knowledgeLoading && !knowledgeList.length" class="cp-kb-empty">未检索到相关知识，换个关键词试试</div>
        <div v-for="(k, i) in knowledgeList" :key="k.knowledgeId || i" class="cp-kb-item">
          <div class="cp-kb-head">
            <span class="cp-kb-title">{{ k.title || '未命名知识' }}</span>
            <el-tag v-if="k.categoryId" size="mini" type="info">分类 {{ k.categoryId }}</el-tag>
          </div>
          <div class="cp-kb-content">{{ k.content }}</div>
          <div class="cp-kb-actions">
            <el-button type="text" size="mini" @click="copyKnowledge(k)">复制全文</el-button>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- U1：通话卡片内转办到协同机构 -->
    <el-dialog title="工单转办（协同机构）" :visible.sync="transferOutOpen" width="520px" append-to-body>
      <el-alert
        v-if="!ticketList.length"
        type="info"
        :closable="false"
        title="该来电暂无工单，请先使用「快速建单」创建后再转办"
      />
      <el-form v-else label-width="90px" size="small">
        <el-form-item label="转办工单">
          <el-select v-model="transferOutForm.ticketId" placeholder="请选择工单" style="width:100%">
            <el-option v-for="t in ticketList" :key="t.ticketId" :label="(t.ticketNo || '') + ' ' + (t.title || '')" :value="t.ticketId" />
          </el-select>
        </el-form-item>
        <el-form-item label="协同机构">
          <el-select v-model="transferOutForm.orgId" placeholder="请选择机构" filterable style="width:100%">
            <el-option v-for="o in orgOptions" :key="o.orgId" :label="o.orgName" :value="o.orgId" />
          </el-select>
        </el-form-item>
        <el-form-item label="转办说明">
          <el-input v-model="transferOutForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="transferOutOpen = false">取 消</el-button>
        <el-button
          type="primary"
          :loading="transferOutSubmitting"
          :disabled="!ticketList.length || !transferOutForm.ticketId || !transferOutForm.orgId"
          @click="submitTransferOut"
        >确认转办</el-button>
      </div>
    </el-dialog>

    <!-- U1：快捷键自定义（本地保存，界面提示实时同步） -->
    <el-dialog title="快捷键设置" :visible.sync="shortcutOpen" width="560px" append-to-body>
      <div class="cp-kb-tip">点击右侧输入框后按下新组合键即可修改；组合键需包含 Ctrl/Alt 或 F1–F12 功能键。</div>
      <el-table :data="shortcutDraft" size="small" border max-height="360">
        <el-table-column label="动作" prop="label" />
        <el-table-column label="快捷键" width="220">
          <template slot-scope="scope">
            <el-input
              :value="scope.row.keys"
              readonly
              size="small"
              placeholder="按下组合键"
              @keydown.native="captureShortcut(scope.row, $event)"
            />
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer">
        <el-button @click="resetShortcutDraft">恢复默认</el-button>
        <el-button @click="shortcutOpen = false">取 消</el-button>
        <el-button type="primary" @click="saveShortcutSettings">保 存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getCallerProfile, getCallerHistory, getCallerTickets, getCallerTrack, updateCallerProfile } from "@/api/lawyers/callPopup"
import { autoFillLedger, addLedger, transferCall, holdCall, resumeCall, hangupCall, afterWork, listAgent, addTicket, transferOutTicket } from "@/api/lawyers/callCenter"
import { searchKnowledge } from "@/api/lawyers/knowledge"
import { listExternalOrg } from "@/api/lawyers/externalOrg"
import AiAssistPanel from "./AiAssistPanel.vue"
import VoiceCaption from "./VoiceCaption.vue"
import callSocket from "@/utils/callSocket"
import { getSipPhone } from "@/utils/webrtcSipPhone"
import { createPoller } from "@/utils/polling"
import { loadShortcuts, saveShortcuts, resetShortcuts, formatKeys, matchShortcut } from "@/utils/shortcuts"
import * as statusTag from '@/utils/statusTag'

export default {
  name: "CallPopup",
  components: { AiAssistPanel, VoiceCaption },
  data() {
    return {
      direction: 'in',
      callStatus: '0',
      connected: false,
      callEnded: false,
      activeTab: 'base',
      tabLoading: false,
      callDuration: '00:00:00',
      seconds: 0,
      timer: null,
      transferOpen: false,
      transferTargetId: null,
      onlineAgentOptions: [],
      profileData: {},
      historyList: [],
      ticketList: [],
      trackList: [],
      editOpen: false,
      profileForm: {},
      // 人工通话记录ID，仅作为人工链路与AI辅助链路的关联桥梁，不驱动人工状态
      currentRecordId: null,
      // P3-A6：实时字幕累计文本（供 AI 辅助面板联动分析）
      liveCaptionText: '',
      // F4：实时 Copilot 数据（要素/法条/相似工单/建议动作，按 seq 回合刷新）
      copilotData: { seq: 0, element: null, laws: [], tickets: [], actions: [] },
      // U1：工作台快捷键（默认 Ctrl+1~9，可在「快捷键设置」中自定义并本地持久化）
      shortcuts: loadShortcuts(),
      shortcutOpen: false,
      shortcutDraft: [],
      // U1：通话卡片内快捷动作（建单/转办/查知识，不离开工作台）
      quickTicketOpen: false,
      quickTicketSubmitting: false,
      quickTicketForm: { title: '', priority: '1', content: '' },
      quickTicketRules: {
        title: [{ required: true, message: '请填写工单标题', trigger: 'blur' }],
        content: [{ required: true, message: '请填写诉求详情', trigger: 'blur' }]
      },
      knowledgeOpen: false,
      knowledgeKeyword: '',
      knowledgeList: [],
      knowledgeLoading: false,
      transferOutOpen: false,
      transferOutSubmitting: false,
      transferOutForm: { ticketId: null, orgId: null, remark: '' },
      orgOptions: []
    }
  },
  computed: {
    agentId() {
      return this.$store.state.agent.agentId
    },
    sipCall() {
      return this.$store.state.agent.sipCall
    },
    // 浏览器侧 SIP 有振铃中的来电时，接听按钮才可点
    incomingRinging() {
      return !!(this.sipCall && this.sipCall.direction === 'incoming' && this.sipCall.state === 'ringing')
    },
    callStatusText() {
      const map = { '0': '空闲', '1': '通话中', '2': '保持', '3': '咨询中', '4': '三方通话', '5': '话后整理' }
      return map[this.callStatus] || '空闲'
    },
    avatarText() {
      const name = this.profileData.callerName
      if (name && name.length > 0) return name.charAt(0)
      return '?'
    },
    tagsArray() {
      if (!this.profileData.tags) return []
      return this.profileData.tags.split(/[,，]/).filter(t => t)
    }
  },
  watch: {
    activeTab(val) {
      if (val === 'call' && this.historyList.length === 0) this.loadHistory()
      if (val === 'ticket' && this.ticketList.length === 0) this.loadTickets()
      if (val === 'track' && this.trackList.length === 0) this.loadTrack()
    },
    // 监听浏览器侧 SIP 通话状态：全局浮层接听/拒接后同步本页状态
    sipCall(val) {
      if (!val) {
        if (this.connected) {
          this.connected = false
          this.callEnded = true
          this.callStatus = '0'
          this.clearTimer()
        }
        return
      }
      if (val.state === 'answered' && !this.connected) {
        this.connected = true
        this.callEnded = false
        this.callStatus = '1'
        this.startTimer()
      } else if (val.state === 'ringing' && this.direction !== 'out') {
        this.connected = false
        this.callEnded = false
      }
    }
  },
  created() {
    this.direction = this.$route.query.direction === 'out' ? 'out' : 'in'
    this.callStatus = this.$route.query.callStatus || '0'
    // 呼出场景直接进入通话计时；入站振铃时不自动置为 connected，需等用户点接听
    this.connected = this.direction === 'out'
    if (this.connected) {
      this.startTimer()
    }
    this.startSyncTimer()
    this.registerCallSocket()
    // 直接订阅浏览器侧 SIP 软电话事件，确保对方挂断时本页立即结束通话
    // （不依赖后端 HANGUP 广播，因为内部分机互拨/PSTN入站可能没有 dial_log 映射）
    this.sipPhone = getSipPhone()
    this.sipPhone.on('sessionEnded', this.onSipHangup)
    this.sipPhone.on('sessionFailed', this.onSipHangup)
    const callerNumber = this.$route.query.callerNumber || this.$route.params.callerNumber
    if (callerNumber) {
      this.loadProfile(callerNumber)
    }
  },
  mounted() {
    // U1：注册工作台全局快捷键监听
    document.addEventListener('keydown', this.handleKeydown)
  },
  beforeDestroy() {
    document.removeEventListener('keydown', this.handleKeydown)
    this.clearTimer()
    if (this.poller) this.poller.stop()
    this.unregisterCallSocket()
    if (this.sipPhone) {
      this.sipPhone.off('sessionEnded', this.onSipHangup)
      this.sipPhone.off('sessionFailed', this.onSipHangup)
      this.sipPhone = null
    }
  },
  methods: {
    // U1：工作台全局快捷键处理（映射可在「快捷键设置」中自定义）
    handleKeydown(e) {
      // 输入框/可编辑元素内不触发，避免与表单输入冲突
      const tag = (e.target.tagName || '').toLowerCase()
      if (['input', 'textarea', 'select'].includes(tag) || e.target.isContentEditable) return
      // 任一弹窗打开时不触发，避免在弹窗内误触通话动作
      if (this.transferOpen || this.editOpen || this.quickTicketOpen || this.knowledgeOpen
        || this.transferOutOpen || this.shortcutOpen) return
      const hit = this.shortcuts.find(s => matchShortcut(e, s.keys))
      if (!hit || typeof this[hit.method] !== 'function') return
      e.preventDefault()
      // 与按钮上的 v-hasPermi 保持一致：快捷键触发受限动作前同样校验权限
      if (hit.perm && !this.$auth.hasPermi(hit.perm)) {
        this.$modal.msgWarning('当前账号无「' + hit.label + '」权限，请联系管理员')
        return
      }
      this[hit.method]()
    },
    /** 按钮上的快捷键提示（与自定义配置实时同步） */
    shortcutLabel(id) {
      const hit = this.shortcuts.find(s => s.id === id)
      return hit ? hit.keys : ''
    },
    openShortcutSettings() {
      this.shortcutDraft = this.shortcuts.map(s => ({ ...s }))
      this.shortcutOpen = true
    },
    /** 捕获组合键：写入草稿，暂不生效，保存后统一应用 */
    captureShortcut(row, e) {
      e.preventDefault()
      e.stopPropagation()
      const keys = formatKeys(e)
      if (!keys) {
        this.$modal.msgWarning('请使用 Ctrl/Alt 组合键或 F1–F12 功能键')
        return
      }
      const dup = this.shortcutDraft.find(s => s.id !== row.id && s.keys === keys)
      if (dup) {
        this.$modal.msgWarning('该快捷键已被「' + dup.label + '」占用')
        return
      }
      this.$set(row, 'keys', keys)
    },
    resetShortcutDraft() {
      this.shortcutDraft = resetShortcuts()
      this.$modal.msgSuccess('已恢复默认快捷键，保存后生效')
    },
    saveShortcutSettings() {
      this.shortcuts = saveShortcuts(this.shortcutDraft)
      this.shortcutOpen = false
      this.$modal.msgSuccess('快捷键已保存')
    },
    // 浏览器侧 SIP 通话结束（对方挂机/拒接/失败）：立即结束本页通话状态
    onSipHangup() {
      if (!this.connected && !this.incomingRinging) return
      this.connected = false
      this.callEnded = true
      this.callStatus = '0'
      this.clearTimer()
      this.$message.info('对方已挂断')
      // 同步刷新坐席状态（后端可能未感知，前端主动拉一次）
      if (this.agentId != null) {
        this.$store.dispatch('agent/refresh').catch(() => {})
      }
    },
    registerCallSocket() {
      // 实时事件驱动通话状态，5 秒轮询作为兜底
      callSocket.on('ANSWERED', this.onWsAnswered)
      callSocket.on('HANGUP', this.onWsHangup)
      callSocket.on('CALL_END', this.onWsHangup)
      callSocket.on('CALL_START', this.onWsCallStart)
      callSocket.on('DTMF', this.onWsDtmf)
    },
    unregisterCallSocket() {
      callSocket.off('ANSWERED', this.onWsAnswered)
      callSocket.off('HANGUP', this.onWsHangup)
      callSocket.off('CALL_END', this.onWsHangup)
      callSocket.off('CALL_START', this.onWsCallStart)
      callSocket.off('DTMF', this.onWsDtmf)
    },
    isMyEvent(data) {
      if (!data) return true
      if (data.agentId == null) return true
      return String(data.agentId) === String(this.agentId)
    },
    onWsAnswered(data) {
      if (!this.isMyEvent(data)) return
      if (!this.connected) {
        this.connected = true
        this.callEnded = false
        this.startTimer()
      }
      this.callStatus = '1'
      if (data && data.recordId) this.currentRecordId = data.recordId
    },
    onWsCallStart(data) {
      if (!this.isMyEvent(data)) return
      if (data && data.recordId) this.currentRecordId = data.recordId
      if (!this.connected) {
        this.connected = true
        this.callEnded = false
        this.startTimer()
      }
    },
    onWsHangup(data) {
      if (!this.isMyEvent(data)) return
      this.connected = false
      this.callEnded = true
      this.callStatus = '0'
      this.clearTimer()
      this.liveCaptionText = ''
      this.copilotData = { seq: 0, element: null, laws: [], tickets: [] }
    },
    // P3-A6：字幕 final 文本累计，桥接给 AI 辅助面板（截断保留最近 2000 字，避免超长上行）
    onCaptionFinal(text) {
      if (!text) return
      this.liveCaptionText = (this.liveCaptionText + '\n' + text).slice(-2000)
    },
    // F4：Copilot 帧桥接——同 seq 三帧组成一个辅助回合，整体刷新给面板
    onCopilotElement(msg) {
      if (!msg || msg.seq < this.copilotData.seq) return
      if (msg.seq !== this.copilotData.seq) {
        this.copilotData = { seq: msg.seq, element: msg, laws: [], tickets: [], actions: [] }
      } else {
        this.copilotData.element = msg
      }
    },
    onCopilotLaws(msg) {
      if (!msg || msg.seq < this.copilotData.seq) return
      if (msg.seq > this.copilotData.seq) {
        this.copilotData = { seq: msg.seq, element: null, laws: msg.laws || [], tickets: [], actions: [] }
      } else {
        this.copilotData.laws = msg.laws || []
      }
    },
    onCopilotTickets(msg) {
      if (!msg || msg.seq < this.copilotData.seq) return
      if (msg.seq > this.copilotData.seq) {
        this.copilotData = { seq: msg.seq, element: null, laws: [], tickets: msg.tickets || [], actions: [] }
      } else {
        this.copilotData.tickets = msg.tickets || []
      }
    },
    onCopilotActions(msg) {
      if (!msg || msg.seq < this.copilotData.seq) return
      if (msg.seq > this.copilotData.seq) {
        this.copilotData = { seq: msg.seq, element: null, laws: [], tickets: [], actions: msg.actions || [] }
      } else {
        this.copilotData.actions = msg.actions || []
      }
    },
    onWsDtmf(data) {
      // DTMF 事件可用于扩展按键交互（如满意度评价），目前仅记录
      if (!this.isMyEvent(data)) return
    },
    startSyncTimer() {
      // P6：统一轮询——页面不可见暂停、在途防重入、恢复可见立即补拉（start 幂等）
      if (!this.poller) {
        this.poller = createPoller(() => this.syncFromAgent(), 5000)
      }
      this.poller.start()
    },
    syncFromAgent() {
      if (this.agentId == null) return
      return this.$store.dispatch('agent/refresh').then(agent => {
        if (!agent) return
        this.callStatus = agent.callStatus || '0'
        const active = this.callStatus !== '0'
        if (active && !this.connected) {
          this.connected = true
          this.callEnded = false
          this.startTimer()
        } else if (!active && this.connected) {
          this.connected = false
          this.callEnded = true
          this.clearTimer()
        }
      }).catch(() => {})
    },
    loadProfile(callerNumber) {
      getCallerProfile(callerNumber).then(res => {
        const data = res.data || {}
        this.profileData = data
        if (data.profile) {
          const p = data.profile
          this.profileData.callerName = this.profileData.callerName || p.callerName
          this.profileData.callerNumber = this.profileData.callerNumber || p.callerNumber
          this.profileData.callerGender = p.callerGender
          this.profileData.callerAge = p.callerAge
          this.profileData.callerIdCard = p.callerIdCard
          this.profileData.address = this.profileData.address || p.callerAddress
          this.profileData.profileId = p.profileId
        }
      }).catch(() => {})
    },
    loadHistory() {
      if (!this.profileData.callerNumber) return
      this.tabLoading = true
      getCallerHistory(this.profileData.callerNumber, 20).then(res => {
        this.historyList = res.data || []
        this.tabLoading = false
      }).catch(() => { this.tabLoading = false })
    },
    loadTickets() {
      if (!this.profileData.callerNumber) return
      this.tabLoading = true
      getCallerTickets(this.profileData.callerNumber).then(res => {
        this.ticketList = res.data || []
        this.tabLoading = false
      }).catch(() => { this.tabLoading = false })
    },
    loadTrack() {
      if (!this.profileData.callerNumber) return
      this.tabLoading = true
      getCallerTrack(this.profileData.callerNumber).then(res => {
        this.trackList = res.data || []
        this.tabLoading = false
      }).catch(() => { this.tabLoading = false })
    },
    handleAnswer() {
      // 手动接听：通过 WebRTC SIP 软电话接听浏览器侧的来电
      if (!this.incomingRinging) {
        this.$message.warning('当前没有振铃中的来电')
        return
      }
      const ok = this.$store.dispatch('agent/sipAnswer')
      if (ok) {
        this.connected = true
        this.callEnded = false
        this.callStatus = '1'
        this.startTimer()
        this.$message.success('正在接听来电')
      } else {
        this.$message.error('接听失败，请检查麦克风权限或分机注册状态')
      }
    },
    startTimer() {
      this.timer = setInterval(() => { this.seconds++; this.callDuration = this.formatTime(this.seconds) }, 1000)
    },
    clearTimer() { if (this.timer) { clearInterval(this.timer); this.timer = null } },
    formatTime(s) {
      const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60
      return [h, m, sec].map(n => String(n).padStart(2, '0')).join(':')
    },
    handleLedger() {
      const recordId = this.$route.query.recordId || this.currentRecordId
      if (!recordId) { this.$message.warning('无关联来电记录，请手动登记'); return }
      autoFillLedger(recordId).then(res => {
        const data = res.data || {}
        addLedger(data).then(() => {
          this.$message.success('已一键登记台账')
        }).catch(() => {})
      }).catch(() => {})
    },
    // ---------- U1：通话卡片内快捷动作（建单 / 转办 / 查知识） ----------
    openQuickTicket() {
      const p = this.profileData || {}
      this.quickTicketForm = {
        title: p.callerName ? `${p.callerName} 来电咨询` : '来电咨询',
        priority: '1',
        content: ''
      }
      this.quickTicketOpen = true
      this.$nextTick(() => {
        if (this.$refs.quickTicketForm) this.$refs.quickTicketForm.clearValidate()
      })
    },
    submitQuickTicket() {
      const form = this.$refs.quickTicketForm
      if (!form) return
      form.validate(valid => {
        if (!valid || this.quickTicketSubmitting) return
        this.quickTicketSubmitting = true
        const p = this.profileData || {}
        addTicket({
          ...this.quickTicketForm,
          callerNumber: p.callerNumber,
          callerName: p.callerName,
          recordId: this.$route.query.recordId || this.currentRecordId,
          assignUserId: this.$store.state.user.id,
          direction: this.direction
        }).then(() => {
          this.$modal.msgSuccess('工单已创建')
          this.quickTicketOpen = false
          this.activeTab = 'ticket'
          this.loadTickets()
        }).catch(() => {}).then(() => {
          this.quickTicketSubmitting = false
        })
      })
    },
    openKnowledge() {
      const p = this.profileData || {}
      this.knowledgeKeyword = p.highFreqProblem || p.intentPrediction || this.knowledgeKeyword || ''
      this.knowledgeList = []
      this.knowledgeOpen = true
      this.$nextTick(() => {
        if (this.knowledgeKeyword) {
          this.searchKnowledgeBase()
        } else if (this.$refs.knowledgeInput) {
          this.$refs.knowledgeInput.focus()
        }
      })
    },
    searchKnowledgeBase() {
      const kw = (this.knowledgeKeyword || '').trim()
      if (!kw) {
        this.$modal.msgWarning('请输入检索关键词')
        return
      }
      this.knowledgeLoading = true
      searchKnowledge(kw).then(res => {
        this.knowledgeList = (res && res.data) || []
      }).catch(() => {
        this.knowledgeList = []
      }).then(() => {
        this.knowledgeLoading = false
      })
    },
    copyKnowledge(item) {
      const text = [item && item.title, item && item.content].filter(Boolean).join('\n')
      if (!text) {
        this.$modal.msgWarning('该知识没有可复制内容')
        return
      }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text)
          .then(() => this.$modal.msgSuccess('已复制，可粘贴到话后整理'))
          .catch(() => this.copyByTextarea(text))
      } else {
        this.copyByTextarea(text)
      }
    },
    /** 非安全上下文（http/旧浏览器）降级复制 */
    copyByTextarea(text) {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      try {
        document.execCommand('copy')
        this.$modal.msgSuccess('已复制，可粘贴到话后整理')
      } catch (e) {
        this.$modal.msgError('复制失败，请手动选择文本')
      }
      document.body.removeChild(ta)
    },
    openTransferOut() {
      if (!this.ticketList.length) {
        this.loadTickets()
        this.$modal.msgWarning('该来电暂无工单，请先使用「快速建单」创建')
      }
      this.transferOutForm = { ticketId: null, orgId: null, remark: '' }
      this.transferOutOpen = true
      if (!this.orgOptions.length) {
        listExternalOrg({ pageNum: 1, pageSize: 200, status: '0' }).then(res => {
          this.orgOptions = res.rows || res.data || []
        }).catch(() => {})
      }
    },
    submitTransferOut() {
      if (this.transferOutSubmitting) return
      const { ticketId, orgId, remark } = this.transferOutForm
      if (!ticketId || !orgId) return
      this.transferOutSubmitting = true
      transferOutTicket({ ticketId, orgId, remark }).then(() => {
        this.$modal.msgSuccess('转办已发起')
        this.transferOutOpen = false
        this.loadTickets()
        this.$store.dispatch('agent/refresh').catch(() => {})
      }).catch(() => {}).then(() => {
        this.transferOutSubmitting = false
      })
    },
    handleTransfer() {
      if (this.agentId == null) {
        this.$message.warning('请先在顶部签入坐席')
        return
      }
      // 加载在线坐席列表后弹出选择框
      listAgent({ pageNum: 1, pageSize: 200, status: '1' }).then(res => {
        this.onlineAgentOptions = (res.rows || []).filter(a => a.agentId !== this.agentId)
        this.transferTargetId = null
        this.transferOpen = true
      }).catch(() => {})
    },
    confirmTransfer() {
      if (this.transferTargetId == null) return
      transferCall({ agentId: this.agentId, toAgentId: this.transferTargetId, remark: '' }).then(() => {
        this.$message.success('转接请求已提交')
        this.transferOpen = false
        this.$store.dispatch('agent/refresh').catch(() => {})
      }).catch(() => {})
    },
    handleHold() {
      if (this.agentId == null) {
        this.$message.warning('请先在顶部签入坐席')
        return
      }
      const api = this.callStatus === '2' ? resumeCall : holdCall
      api({ agentId: this.agentId }).then(() => {
        this.$message.success(this.callStatus === '2' ? '通话已恢复' : '通话已保持')
        return this.$store.dispatch('agent/refresh')
      }).then(agent => {
        if (agent) this.callStatus = agent.callStatus || this.callStatus
      }).catch(() => {})
    },
    handleAfterWork() {
      if (this.agentId == null) {
        this.$message.warning('请先在顶部签入坐席')
        return
      }
      afterWork({ agentId: this.agentId }).then(() => {
        this.$message.success('已进入话后整理')
        return this.$store.dispatch('agent/refresh')
      }).then(agent => {
        if (agent) this.callStatus = agent.callStatus || this.callStatus
      }).catch(() => {})
    },
    handleHangup() {
      if (this.agentId == null) {
        this.$message.warning('请先在顶部签入坐席')
        return
      }
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确定挂断当前通话吗？',
        tip: '挂断后当前通话无法恢复，请确认已记录必要信息'
      }).then(() => {
        hangupCall({ agentId: this.agentId }).then(() => {
          this.connected = false
          this.callEnded = true
          this.callStatus = '0'
          this.clearTimer()
          this.$message.success('通话已挂断')
          this.$store.dispatch('agent/refresh').catch(() => {})
        }).catch(() => {})
      }).catch(() => {})
    },
    handleEditProfile() {
      const p = this.profileData
      this.profileForm = {
        profileId: p.profileId, callerNumber: p.callerNumber, callerName: p.callerName,
        callerGender: p.callerGender, callerAge: p.callerAge, callerIdCard: p.callerIdCard,
        callerAddress: p.address, customerLevel: p.customerLevel, tags: p.tags,
        intentPrediction: p.intentPrediction, intentConfidence: p.intentConfidence,
        riskLevel: p.riskLevel, emotionStatus: p.emotionStatus,
        languagePreference: p.languagePreference || 'zh-CN',
        careMode: p.careMode != null ? Number(p.careMode) : 0
      }
      this.editOpen = true
    },
    submitProfile() {
      updateCallerProfile(this.profileForm).then(() => {
        this.$message.success('保存成功')
        this.editOpen = false
        if (this.profileData.callerNumber) this.loadProfile(this.profileData.callerNumber)
      }).catch(() => {})
    },
    genderLabel(g) { return { '0': '男', '1': '女', '2': '未知' }[g] || '未知' },
    riskLabel(r) { return { '0': '低风险', '1': '中风险', '2': '高风险' }[r] || '低风险' },
    // V7：状态语义色统一由 utils/statusTag 承载（修复原 type="primary" 非法值）
    recordStatusLabel(s) { return statusTag.callRecordStatusLabel(s) },
    recordStatusTag(s) { return statusTag.callRecordStatusType(s) },
    recordStatusClass(s) { return statusTag.callRecordStatusClass(s) },
    priorityLabel(p) { return { '1': '紧急', '2': '普通', '3': '低' }[p] || '普通' },
    ticketStatusLabel(s) { return statusTag.ticketStatusLabel(s) },
    ticketStatusTag(s) { return statusTag.ticketStatusType(s) },
    ticketStatusClass(s) { return statusTag.ticketStatusClass(s) },
    tagType(idx) { return ['warning', '', 'success', 'danger'][idx % 4] },
    formatDuration(seconds) {
      seconds = parseInt(seconds) || 0
      if (seconds < 60) return seconds + '秒'
      const m = Math.floor(seconds / 60), s = seconds % 60
      return m + '分' + (s > 0 ? s + '秒' : '')
    }
  }
}
</script>

<style lang="scss" scoped>
.call-popup { background: #F5F7FA; min-height: calc(100vh - 84px); padding: 24px; padding-bottom: 100px; }

.cp-banner {
  background: linear-gradient(135deg, #2B8C6E 0%, #2B8C6E 100%);
  border-radius: 10px; padding: 28px 32px; color: #fff;
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; position: relative; overflow: hidden;
  &.is-out { background: linear-gradient(135deg, #1A3C6E 0%, #255A99 100%); }
  &::before { content: ''; position: absolute; right: -40px; top: -40px; width: 200px; height: 200px; border-radius: 50%; background: rgba(255,255,255,0.08); }
  .cp-banner-left { display: flex; align-items: center; gap: 16px; z-index: 1; }
  .cp-banner-icon { width: 52px; height: 52px; border-radius: 50%; background: rgba(255,255,255,0.2); display: flex; align-items: center; justify-content: center; font-size: 26px; animation: cp-ring 1.5s ease-in-out infinite; }
  .cp-banner-main { font-size: 20px; font-weight: 700; margin-bottom: 4px; }
  .cp-banner-sub { font-size: 13px; opacity: 0.9; display: flex; align-items: center; gap: 8px; }
  .cp-dot { width: 8px; height: 8px; border-radius: 50%; background: #fff; animation: cp-blink 1s ease-in-out infinite; }
  .cp-banner-center { text-align: center; z-index: 1; .cp-banner-number { font-size: 34px; font-weight: 700; letter-spacing: 2px; } .cp-banner-info { font-size: 13px; opacity: 0.9; } }
  .cp-banner-right { z-index: 1; .cp-time-label { font-size: 12px; opacity: 0.8; } .cp-time-value { font-size: 24px; font-weight: 700; font-family: 'Courier New', monospace; } }
}
@keyframes cp-ring { 0%,100% { transform: scale(1); } 50% { transform: scale(1.05); } }
@keyframes cp-blink { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }

.cp-card { border-radius: 10px; margin-bottom: 20px; border: 1px solid #DCE2EB; ::v-deep .el-card__body { padding: 20px 24px; } }
.cp-card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; font-size: 14px; color: #1F2A3A; }
.cp-caller-header { display: flex; align-items: center; gap: 16px; padding-bottom: 16px; border-bottom: 1px solid #F5F7FA; margin-bottom: 16px; }
.cp-avatar { width: 52px; height: 52px; border-radius: 50%; background: linear-gradient(135deg, #255A99, #1A3C6E); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 600; }
.cp-caller-name { font-size: 16px; font-weight: 600; color: #1F2A3A; display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.cp-caller-phone { font-size: 13px; color: #5A6A7E; }
.cp-detail-item { display: flex; justify-content: space-between; padding: 10px 0; font-size: 13px; .cp-detail-label { color: #5A6A7E; } .cp-detail-value { color: #1F2A3A; font-weight: 500; } }
.cp-caller-tags { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }

.cp-ai-title { display: flex; align-items: center; gap: 10px; color: #7C3AED; }
.cp-ai-badge { background: linear-gradient(135deg, #7C3AED, #7c3aed); color: #fff; font-size: 11px; padding: 3px 8px; border-radius: 4px; font-weight: 600; }
.cp-ai-grid { margin-bottom: 16px; }
.cp-ai-item { background: #faf5ff; border-radius: 8px; padding: 16px; text-align: center; margin-bottom: 12px; .cp-ai-label { font-size: 12px; color: #7C3AED; margin-bottom: 8px; } .cp-ai-value { font-size: 14px; font-weight: 600; color: #1F2A3A; margin-bottom: 8px; } .cp-ai-sub { font-size: 11px; color: #94a3b8; } .cp-ai-confidence { display: flex; flex-direction: column; align-items: center; gap: 6px; .el-progress { width: 100%; } span { font-size: 11px; color: #7C3AED; } } }
.cp-ai-freq { color: #E8923A; }
.cp-risk-0 { color: #2B8C6E; } .cp-risk-1 { color: #E8923A; } .cp-risk-2 { color: #C63D4A; }
.cp-emotion-warning { background: #fef3c7; border: 1px solid #fde68a; border-radius: 8px; padding: 12px 14px; display: flex; align-items: center; gap: 12px; font-size: 13px; color: #92400e; i { font-size: 16px; color: #E8923A; } }

.cp-history-card { ::v-deep .el-card__body { padding-top: 0; } }
.cp-history-tabs { ::v-deep .el-tabs__header { margin-bottom: 0; } ::v-deep .el-tabs__nav-wrap::after { display: none; } ::v-deep .el-tabs__item { height: 36px; line-height: 36px; font-size: 13px; color: #5A6A7E; &.is-active { color: #2B8C6E; font-weight: 600; } } ::v-deep .el-tabs__active-bar { background-color: #2B8C6E; } }
.cp-history-content { padding-top: 16px; min-height: 300px; }
.cp-empty { text-align: center; color: #94a3b8; padding: 40px 0; font-size: 13px; }

.cp-call-item { display: flex; gap: 14px; padding: 16px; background: #F5F7FA; border-radius: 8px; border: 1px solid #F5F7FA; margin-bottom: 12px; &:hover { background: #F5F7FA; } .cp-call-icon { width: 36px; height: 36px; border-radius: 50%; background: #E7F6EE; color: #2B8C6E; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; } .cp-call-main { flex: 1; min-width: 0; } .cp-call-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; .cp-call-title { font-size: 14px; font-weight: 500; color: #1F2A3A; flex: 1; } .cp-call-time { font-size: 12px; color: #94a3b8; } } .cp-call-footer { display: flex; align-items: center; gap: 12px; .cp-call-duration { font-size: 12px; color: #5A6A7E; } .cp-call-content { font-size: 12px; color: #94a3b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; } } }

.cp-track-title { margin-left: 8px; font-size: 14px; font-weight: 500; color: #1F2A3A; }
.cp-track-content { font-size: 12px; color: #5A6A7E; margin-top: 4px; }

.cp-action-bar { position: fixed; bottom: 0; left: 220px; right: 0; background: #fff; border-top: 1px solid #DCE2EB; padding: 12px 24px; display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 12px; z-index: 100; box-shadow: 0 -4px 12px rgba(0,0,0,0.04); }
.cp-action-btn { display: flex; flex-direction: column; align-items: center; gap: 6px; min-width: 90px; padding: 12px 20px; border-radius: 8px; font-size: 13px; i { font-size: 20px; }
  &.cp-btn-green { background: linear-gradient(135deg, #2B8C6E, #2B8C6E); border: none; color: #fff; &:hover { background: linear-gradient(135deg, #2B8C6E, #207058); } }
  &.cp-btn-blue { background: linear-gradient(135deg, #255A99, #1A3C6E); border: none; color: #fff; &:hover { background: linear-gradient(135deg, #1A3C6E, #1A3C6E); } }
  &.cp-btn-gray { background: #F5F7FA; border: 1px solid #DCE2EB; color: #5A6A7E; &:hover { background: #DCE2EB; color: #1F2A3A; } }
  &.cp-btn-purple { background: linear-gradient(135deg, #7C3AED, #7c3aed); border: none; color: #fff; &:hover { background: linear-gradient(135deg, #7c3aed, #6d28d9); } }
  &.cp-btn-orange { background: linear-gradient(135deg, #E8923A, #d97706); border: none; color: #fff; &:hover { background: linear-gradient(135deg, #d97706, #b45309); } }
  &.cp-btn-red { background: linear-gradient(135deg, #C63D4A, #9E2F3A); border: none; color: #fff; &:hover { background: linear-gradient(135deg, #9E2F3A, #7E2530); } }
}
.cp-kbd { display: inline-block; padding: 1px 6px; font-size: 11px; font-family: Menlo, Consolas, monospace; line-height: 1.4; color: #5A6A7E; background: rgba(255,255,255,0.85); border: 1px solid #DCE2EB; border-radius: 4px; box-shadow: 0 1px 0 #DCE2EB; }
.cp-shortcut-setting { color: #909399; font-size: 12px; }
.cp-kb-tip { margin: 10px 0 6px; font-size: 12px; color: #909399; }
.cp-kb-list { max-height: 380px; overflow-y: auto; }
.cp-kb-empty { padding: 24px 0; text-align: center; color: #909399; font-size: 13px; }
.cp-kb-item { padding: 10px 0; border-bottom: 1px solid #F0F2F5; }
.cp-kb-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.cp-kb-title { font-weight: 600; color: #1F2A3A; }
.cp-kb-content { margin-top: 4px; font-size: 13px; color: #5A6A7E; line-height: 1.6; white-space: pre-wrap; max-height: 96px; overflow: hidden; }
.cp-kb-actions { margin-top: 2px; text-align: right; }
@media (max-width: 1200px) {
  .cp-action-btn { min-width: 78px; padding: 10px 12px; }
}
.cp-action-btn:not(.cp-btn-gray) .cp-kbd { color: #1A3C6E; background: rgba(255,255,255,0.9); border-color: rgba(255,255,255,0.6); }
</style>
