<template>
  <div class="chat-container">
    <!-- 左侧：会话列表 -->
    <div class="chat-left-panel">
      <div class="chat-left-header">
        <span class="clh-title">图文会话</span>
        <el-badge :value="activeCount" class="clh-badge" v-if="activeCount > 0" />
      </div>
      <div class="chat-left-search">
        <el-input v-model="searchKeyword" size="small" placeholder="搜索会话..." prefix-icon="el-icon-search" clearable @input="filterSessions" />
      </div>
      <div class="chat-left-list" v-loading="sessionLoading">
        <div v-if="filteredSessions.length === 0" class="chat-empty">暂无会话</div>
        <div
          v-for="sess in filteredSessions"
          :key="sess.sessionId"
          class="chat-session-item"
          :class="{ 'chat-session-active': currentSession && currentSession.sessionId === sess.sessionId }"
          @click="selectSession(sess)"
        >
          <div class="csi-avatar">
            <el-avatar :size="40" :src="sess.customerAvatar" icon="el-icon-user-solid" />
            <span class="csi-dot" v-if="sess.unreadCount > 0"></span>
          </div>
          <div class="csi-body">
            <div class="csi-top">
              <span class="csi-name">{{ sess.customerName || '未知客户' }}</span>
              <span class="csi-time">{{ formatTime(sess.lastMessageTime) }}</span>
            </div>
            <div class="csi-bottom">
              <span class="csi-msg">{{ sess.lastMessage || '暂无消息' }}</span>
              <el-badge :value="sess.unreadCount" class="csi-unread" v-if="sess.unreadCount > 0" />
            </div>
          </div>
        </div>
      </div>
      <div class="chat-left-stats">
        <span>进行中：{{ activeCount }}</span>
        <span>今日新增：{{ todayCount }}</span>
      </div>
    </div>

    <!-- 中间：聊天消息区 -->
    <div class="chat-center-panel">
      <!-- 无会话选中 -->
      <div v-if="!currentSession" class="chat-center-placeholder">
        <i class="el-icon-chat-dot-square"></i>
        <p>请选择一个会话开始对话</p>
      </div>

      <template v-else>
        <!-- 顶部工具栏 -->
        <div class="chat-center-header">
          <div class="cch-left">
            <span class="cch-name">{{ currentSession.customerName || '未知客户' }}</span>
            <el-tag size="mini" :type="channelTag(currentSession.channel)">{{ channelText(currentSession.channel) }}</el-tag>
          </div>
          <div class="cch-right">
            <el-button type="text" size="mini" icon="el-icon-s-order" @click="handleTransfer">转接</el-button>
            <el-button type="text" size="mini" icon="el-icon-circle-close" @click="handleClose">结束</el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div class="chat-message-area" ref="msgArea" v-loading="msgLoading">
          <div v-if="messageList.length === 0" class="chat-empty-msg">暂无消息，开始对话吧</div>
          <div
            v-for="msg in messageList"
            :key="msg.messageId"
            class="chat-msg-item"
            :class="{ 'chat-msg-right': msg.senderType === '2', 'chat-msg-system': msg.senderType === '3' }"
          >
            <div class="cmi-avatar" v-if="msg.senderType !== '2'">
              <el-avatar :size="32" :src="currentSession.customerAvatar" icon="el-icon-user-solid" />
            </div>
            <div class="cmi-bubble" :class="'cmi-bubble-' + msg.senderType">
              <!-- 文字消息 -->
              <div v-if="msg.msgType === '1'" class="cmi-text">{{ msg.content }}</div>
              <!-- 图片消息 -->
              <el-image
                v-else-if="msg.msgType === '2'"
                :src="msg.attachmentUrl"
                fit="contain"
                :preview-src-list="[msg.attachmentUrl]"
                class="cmi-image"
              />
              <!-- 文件消息 -->
              <div v-else-if="msg.msgType === '3'" class="cmi-file">
                <i class="el-icon-document"></i>
                <a :href="msg.attachmentUrl" target="_blank" class="cmi-file-name">{{ msg.content }}</a>
              </div>
              <div class="cmi-time">{{ formatTime(msg.sendTime || msg.createTime) }}</div>
            </div>
            <div class="cmi-avatar" v-if="msg.senderType === '2'">
              <el-avatar :size="32" icon="el-icon-user-solid" />
            </div>
          </div>
        </div>

        <!-- 底部输入区 -->
        <div class="chat-input-area">
          <div class="cia-toolbar">
            <el-button type="text" size="mini" icon="el-icon-picture-outline" @click="handleUpload('image')">图片</el-button>
            <el-button type="text" size="mini" icon="el-icon-folder-opened" @click="handleUpload('file')">文件</el-button>
          </div>
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入消息，按 Enter 发送..."
            resize="none"
            @keydown.enter.native.exact="handleSend"
          />
          <div class="cia-send-row">
            <span class="cia-tip">Enter 发送</span>
            <el-button type="primary" size="small" :disabled="!inputText.trim()" @click="handleSend">发 送</el-button>
          </div>
        </div>
      </template>
    </div>

    <!-- 右侧：客户资料 -->
    <div class="chat-right-panel" v-if="currentSession">
      <div class="crp-section">
        <div class="crp-title">客户信息</div>
        <div class="crp-item"><label>姓名</label><span>{{ currentSession.customerName || '-' }}</span></div>
        <div class="crp-item"><label>电话</label><span>{{ currentSession.customerPhone || '-' }}</span></div>
        <div class="crp-item"><label>渠道</label><span>{{ channelText(currentSession.channel) }}</span></div>
        <div class="crp-item"><label>受理人</label><span>{{ currentSession.assignee || '-' }}</span></div>
      </div>
      <div class="crp-section">
        <div class="crp-title">会话信息</div>
        <div class="crp-item"><label>会话编号</label><span>{{ currentSession.sessionNo || '-' }}</span></div>
        <div class="crp-item"><label>创建时间</label><span>{{ parseTime(currentSession.createTime) }}</span></div>
        <div class="crp-item"><label>状态</label>
          <el-tag size="mini" :type="sessionStatusTag(currentSession.status)">{{ sessionStatusText(currentSession.status) }}</el-tag>
        </div>
      </div>
    </div>

    <!-- 转接弹窗 -->
    <el-dialog title="会话转接" :visible.sync="transferOpen" width="400px" append-to-body>
      <el-form label-width="60px" size="small">
        <el-form-item label="转接至">
          <el-select v-model="transferAssignee" placeholder="请选择坐席" style="width: 100%" filterable>
            <el-option
              v-for="user in userOptions"
              :key="user.userId"
              :label="user.nickName || user.userName"
              :value="user.userName"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="transferOpen = false">取 消</el-button>
        <el-button type="primary" @click="confirmTransfer" :disabled="!transferAssignee">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listChatSession, getChatSessionStats,
  listChatMessage, sendChatMessage,
  closeChatSession, transferChatSession, markRead
} from "@/api/lawyers/chat"
import { listUser } from "@/api/system/user"

export default {
  name: "Chat",
  data() {
    return {
      searchKeyword: '',
      sessionLoading: false,
      msgLoading: false,
      sessionList: [],
      currentSession: null,
      messageList: [],
      inputText: '',
      activeCount: 0,
      todayCount: 0,
      transferOpen: false,
      transferAssignee: '',
      userOptions: [],
      pollTimer: null
    }
  },
  computed: {
    filteredSessions() {
      if (!this.searchKeyword) return this.sessionList
      const kw = this.searchKeyword.toLowerCase()
      return this.sessionList.filter(s =>
        (s.customerName && s.customerName.toLowerCase().includes(kw)) ||
        (s.customerPhone && s.customerPhone.includes(kw)) ||
        (s.lastMessage && s.lastMessage.toLowerCase().includes(kw))
      )
    }
  },
  created() {
    this.loadSessions()
    this.loadUsers()
  },
  mounted() {
    this.pollTimer = setInterval(() => {
      if (this.currentSession) {
        this.loadMessages()
      }
      this.loadSessions(true)
    }, 5000)
  },
  beforeDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer)
  },
  methods: {
    loadSessions(silent) {
      if (!silent) this.sessionLoading = true
      listChatSession({ pageNum: 1, pageSize: 50 }).then(res => {
        this.sessionList = res.rows || []
        if (!silent) this.sessionLoading = false
      }).catch(() => { if (!silent) this.sessionLoading = false })
      getChatSessionStats().then(res => {
        const d = res.data || {}
        this.activeCount = d.activeCount || 0
        this.todayCount = d.todayCount || 0
      }).catch(() => {})
    },
    loadUsers() {
      listUser({ pageNum: 1, pageSize: 100 }).then(res => {
        this.userOptions = res.rows || []
      }).catch(() => {})
    },
    selectSession(sess) {
      this.currentSession = sess
      this.loadMessages()
    },
    loadMessages() {
      if (!this.currentSession) return
      this.msgLoading = true
      listChatMessage(this.currentSession.sessionId).then(res => {
        this.messageList = res.rows || []
        this.msgLoading = false
        this.$nextTick(() => { this.scrollToBottom() })
      }).catch(() => { this.msgLoading = false })
      // 标记已读
      markRead(this.currentSession.sessionId).catch(() => {})
    },
    handleSend() {
      const text = this.inputText.trim()
      if (!text || !this.currentSession) return
      this.inputText = ''
      sendChatMessage({
        sessionId: this.currentSession.sessionId,
        content: text,
        msgType: '1'
      }).then(() => {
        this.loadMessages()
        this.loadSessions(true)
      }).catch(() => { this.inputText = text })
    },
    handleClose() {
      if (!this.currentSession) return
      this.$confirm('确认结束当前会话？', '结束会话', {
        confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
      }).then(() => {
        closeChatSession(this.currentSession.sessionId).then(() => {
          this.$message.success('会话已结束')
          this.currentSession = null
          this.messageList = []
          this.loadSessions()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleTransfer() {
      if (!this.currentSession) return
      this.transferAssignee = ''
      this.transferOpen = true
    },
    confirmTransfer() {
      transferChatSession({
        sessionId: this.currentSession.sessionId,
        assignee: this.transferAssignee
      }).then(() => {
        this.$message.success('转接成功')
        this.transferOpen = false
        this.loadSessions()
      }).catch(() => {})
    },
    handleUpload(type) {
      this.$message.info('文件上传功能建设中，当前仅支持文字消息')
    },
    filterSessions() {},
    scrollToBottom() {
      const el = this.$refs.msgArea
      if (el) el.scrollTop = el.scrollHeight
    },
    channelText(c) { return { '1': '图文', '2': '视频辅助' }[c] || '图文' },
    channelTag(c) { return { '1': 'primary', '2': 'warning' }[c] || 'info' },
    sessionStatusText(s) { return { '0': '进行中', '1': '已结束', '2': '转人工' }[s] || '未知' },
    sessionStatusTag(s) { return { '0': 'success', '1': 'info', '2': 'warning' }[s] || 'info' },
    formatTime(t) {
      if (!t) return ''
      const d = new Date(t)
      const now = new Date()
      const pad = n => String(n).padStart(2, '0')
      if (d.toDateString() === now.toDateString()) {
        return pad(d.getHours()) + ':' + pad(d.getMinutes())
      }
      return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
    }
  }
}
</script>

<style lang="scss" scoped>
.chat-container {
  display: flex;
  height: calc(100vh - 84px);
  background: #f1f5f9;
}

/* 左侧会话列表面板 */
.chat-left-panel {
  width: 300px;
  min-width: 300px;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}
.chat-left-header {
  padding: 18px 24px 16px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #f1f5f9;
  .clh-title { font-size: 16px; font-weight: 600; color: #1e293b; }
  .clh-badge { margin-left: 8px; }
}
.chat-left-search {
  padding: 12px 16px;
}
.chat-left-list {
  flex: 1;
  overflow-y: auto;
  .chat-empty { text-align: center; color: #94a3b8; padding: 40px 0; font-size: 13px; }
}
.chat-session-item {
  display: flex;
  align-items: center;
  padding: 14px 18px;
  cursor: pointer;
  transition: background 0.15s;
  &:hover { background: #f8fafc; }
  &.chat-session-active { background: #eff6ff; }
  .csi-avatar { position: relative; margin-right: 12px; }
  .csi-dot {
    width: 10px; height: 10px; border-radius: 50%;
    background: #ef4444; position: absolute; top: 0; right: 0;
    border: 2px solid #fff;
  }
  .csi-body { flex: 1; min-width: 0; }
  .csi-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
  .csi-name { font-size: 14px; font-weight: 500; color: #1e293b; }
  .csi-time { font-size: 11px; color: #94a3b8; }
  .csi-bottom { display: flex; align-items: center; }
  .csi-msg { font-size: 12px; color: #64748b; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .csi-unread { margin-left: 4px; }
}
.chat-left-stats {
  padding: 12px 18px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #94a3b8;
}

/* 中间聊天消息区 */
.chat-center-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-center-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  i { font-size: 64px; margin-bottom: 16px; }
  p { font-size: 14px; }
}
.chat-center-header {
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  .cch-left { display: flex; align-items: center; gap: 12px; }
  .cch-name { font-size: 15px; font-weight: 600; color: #1e293b; }
}
.chat-message-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: #f8fafc;
}
.chat-empty-msg {
  text-align: center;
  color: #94a3b8;
  padding: 60px 0;
  font-size: 14px;
}
.chat-msg-item {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
  .cmi-avatar { flex-shrink: 0; }
  &.chat-msg-right {
    justify-content: flex-end;
    .cmi-bubble { align-items: flex-end; }
    .cmi-bubble-2 {
      background: #dbeafe;
      color: #1e40af;
      border-radius: 12px 4px 12px 12px;
    }
  }
  &.chat-msg-system {
    justify-content: center;
    .cmi-bubble-3 {
      background: #f1f5f9;
      color: #64748b;
      font-size: 12px;
      padding: 6px 12px;
      border-radius: 8px;
    }
  }
}
.cmi-bubble {
  display: flex;
  flex-direction: column;
  max-width: 60%;
  padding: 12px 16px;
  margin: 0 10px;
  .cmi-bubble-1 {
    background: #fff;
    color: #334155;
    border-radius: 4px 12px 12px 12px;
  }
  .cmi-text { line-height: 1.6; word-break: break-word; font-size: 14px; }
  .cmi-time { font-size: 11px; color: #94a3b8; margin-top: 6px; }
}
.cmi-bubble-1, .cmi-bubble-2 {
  background: #fff;
  border-radius: 4px 12px 12px 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.cmi-bubble-1 .cmi-text { color: #334155; }
.cmi-image {
  max-width: 200px;
  border-radius: 8px;
  cursor: pointer;
}
.cmi-file {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: #f8fafc;
  border-radius: 6px;
  i { font-size: 20px; color: #3b82f6; }
  .cmi-file-name { font-size: 13px; color: #3b82f6; }
}

/* 底部输入区 */
.chat-input-area {
  background: #fff;
  border-top: 1px solid #e2e8f0;
  padding: 12px 16px;
  .cia-toolbar { margin-bottom: 8px; }
  ::v-deep .el-textarea__inner { border: none; box-shadow: none; padding: 4px 0; }
  .cia-send-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 10px;
    .cia-tip { font-size: 12px; color: #94a3b8; }
  }
}

/* 右侧客户资料 */
.chat-right-panel {
  width: 256px;
  min-width: 256px;
  background: #fff;
  border-left: 1px solid #e2e8f0;
  overflow-y: auto;
}
.crp-section {
  padding: 20px;
  border-bottom: 1px solid #f1f5f9;
  .crp-title { font-size: 14px; font-weight: 600; color: #1e293b; margin-bottom: 16px; }
  .crp-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    label { font-size: 12px; color: #64748b; }
    span { font-size: 13px; color: #334155; }
  }
}
</style>
