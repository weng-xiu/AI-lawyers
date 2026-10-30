<template>
  <div class="portal-page messages-page">
    <section class="page-wrap" aria-label="消息提醒">
      <div class="page-head">
        <div>
          <h2 class="page-title">消息提醒</h2>
          <p class="page-sub">工单受理、办结、归档与系统通知会在这里提醒您</p>
        </div>
        <button
          type="button"
          class="read-all-btn"
          :disabled="unread === 0 || readAlling"
          @click="handleReadAll">
          {{ readAlling ? '处理中…' : '全部已读' }}
        </button>
      </div>

      <!-- 已读状态筛选：原生 radio 分段，保持正确的选中语义与键盘可达性 -->
      <div class="status-tabs" role="radiogroup" aria-label="消息已读状态筛选">
        <label
          v-for="opt in readOptions"
          :key="opt.value"
          class="status-tab"
          :class="{ 'is-active': query.isRead === opt.value }">
          <input
            type="radio"
            name="message-read"
            :value="opt.value"
            :checked="query.isRead === opt.value"
            @change="handleFilter(opt.value)">
          <span>{{ opt.label }}</span>
        </label>
      </div>

      <div v-loading="loading">
        <div v-if="messageList.length === 0 && !loading" class="empty-box" role="status">
          <i class="el-icon-bell"></i>
          <p>{{ query.isRead === '0' ? '没有未读消息' : '暂无消息提醒' }}</p>
          <el-button type="primary" class="big-btn" @click="goTickets">查看我的工单</el-button>
        </div>

        <article
          v-for="item in messageList"
          :key="item.messageId"
          class="message-card"
          :class="{ 'is-unread': isUnread(item) }">
          <div class="message-head">
            <span class="message-type">
              <el-tag size="mini" :type="msgTypeTag(item.msgType)">{{ msgTypeText(item.msgType) }}</el-tag>
              <span v-if="isUnread(item)" class="sr-only">未读</span>
              <span v-if="isUnread(item)" class="unread-dot" aria-hidden="true"></span>
            </span>
            <time class="message-time">{{ formatTime(item.createTime) }}</time>
          </div>
          <h3 class="message-title">{{ item.title || '系统提醒' }}</h3>
          <p v-if="item.content" class="message-content">{{ item.content }}</p>
          <div class="message-actions">
            <el-button
              v-if="item.bizType === 'ticket' && item.bizId"
              type="text"
              @click="goTicket(item)">
              查看工单进度 <i class="el-icon-arrow-right" aria-hidden="true"></i>
            </el-button>
            <el-button
              v-if="isUnread(item)"
              type="text"
              :loading="readingId === item.messageId"
              @click="handleRead(item)">
              标记已读
            </el-button>
          </div>
        </article>
      </div>

      <div v-if="total > 0" class="pagination-container">
        <el-pagination
          background
          prev-text="上一页"
          next-text="下一页"
          @current-change="handlePageChange"
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          layout="total, prev, pager, next"
          :total="total">
        </el-pagination>
      </div>
    </section>
  </div>
</template>

<script>
import { getMyMessages, getUnreadCount, readMessage, readAllMessages } from '@/api/portal'
import { announce } from '@/utils/a11y'

export default {
  name: 'Messages',
  data() {
    return {
      loading: false,
      readAlling: false,
      readingId: null,
      messageList: [],
      total: 0,
      unread: 0,
      query: { pageNum: 1, pageSize: 10, isRead: '' },
      readOptions: [
        { value: '', label: '全部' },
        { value: '0', label: '未读' },
        { value: '1', label: '已读' }
      ]
    }
  },
  created() {
    this.loadMessages()
    this.loadUnread()
  },
  methods: {
    loadMessages() {
      this.loading = true
      getMyMessages(this.query).then(res => {
        this.messageList = res.rows || []
        this.total = res.total || 0
        announce(`消息加载完成，共 ${this.total} 条`, 'polite')
      }).catch(() => {
        announce('消息加载失败，请稍后重试', 'assertive')
      }).finally(() => {
        this.loading = false
      })
    },
    loadUnread() {
      getUnreadCount().then(res => {
        this.unread = Number(res.data || 0)
      }).catch(() => {})
    },
    handleFilter(isRead) {
      this.query.isRead = isRead
      this.query.pageNum = 1
      this.loadMessages()
    },
    handlePageChange(page) {
      this.query.pageNum = page
      this.loadMessages()
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
    isUnread(item) {
      return String(item.isRead) === '0'
    },
    handleRead(item) {
      this.readingId = item.messageId
      readMessage(item.messageId).then(() => {
        item.isRead = '1'
        this.unread = Math.max(0, this.unread - 1)
        announce('已标记为已读', 'polite')
      }).catch(() => {
        announce('标记已读失败，请稍后重试', 'assertive')
      }).finally(() => {
        this.readingId = null
      })
    },
    handleReadAll() {
      if (this.unread === 0) return
      this.readAlling = true
      readAllMessages().then(() => {
        this.messageList.forEach(item => { item.isRead = '1' })
        this.unread = 0
        this.$message.success('已全部标记为已读')
        announce('已全部标记为已读', 'polite')
        this.loadMessages()
      }).catch(() => {
        announce('全部已读失败，请稍后重试', 'assertive')
      }).finally(() => {
        this.readAlling = false
      })
    },
    goTicket(item) {
      this.$router.push('/ticket')
      announce('正在打开我的工单', 'polite')
    },
    goTickets() {
      this.$router.push('/ticket')
    },
    msgTypeText(type) {
      return {
        '1': '系统通知', '2': '待办提醒', '3': '风险预警',
        '4': '质检通知', '5': '工单通知'
      }[String(type)] || '其他消息'
    },
    msgTypeTag(type) {
      return {
        '1': 'info', '2': 'warning', '3': 'danger',
        '4': 'warning', '5': ''
      }[String(type)] || 'info'
    },
    formatTime(t) {
      if (!t) return '-'
      return String(t).replace('T', ' ').substring(0, 16)
    }
  }
}
</script>

<style scoped>
.portal-page {
  min-height: calc(100vh - 120px);
  background: linear-gradient(180deg, #f0f4fa 0%, #f7f9fc 100%);
  padding: 24px 0 40px;
}
.page-wrap { max-width: 960px; margin: 0 auto; padding: 0 20px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.page-title { font-size: 24px; color: #0b1f4a; margin: 0 0 6px; }
.page-sub { color: #5b6b84; font-size: 14px; margin: 0; }

.read-all-btn {
  flex: none; min-height: 40px; padding: 8px 18px;
  border: 1px solid #1A3C6E; border-radius: 6px;
  background: #fff; color: #1A3C6E; font-size: 14px; font-weight: 600;
  cursor: pointer;
}
.read-all-btn:hover:not(:disabled) { background: #eef3fb; }
.read-all-btn:disabled { border-color: #c4cedd; color: #8a94a6; cursor: not-allowed; }
.read-all-btn:focus-visible { outline: 3px solid #ffd04b; outline-offset: 2px; }

/* 已读状态分段控件：原生 radio 视觉隐藏，选中态与焦点环由 label 呈现 */
.status-tabs { display: inline-flex; flex-wrap: wrap; margin-bottom: 18px; }
.status-tab { position: relative; display: inline-flex; }
.status-tab input {
  position: absolute; inset: 0;
  width: 100%; height: 100%; margin: 0;
  opacity: 0; cursor: pointer;
}
.status-tab span {
  display: inline-flex; align-items: center; justify-content: center;
  min-height: 40px; padding: 10px 18px;
  border: 1px solid #dcdfe6; border-left: none;
  background: #fff; color: #44505f;
  font-size: 14px; cursor: pointer;
}
.status-tab:first-child span { border-left: 1px solid #dcdfe6; border-radius: 4px 0 0 4px; }
.status-tab:last-child span { border-radius: 0 4px 4px 0; }
.status-tab.is-active span { background: #1A3C6E; border-color: #1A3C6E; color: #fff; }
.status-tab:focus-within { z-index: 1; }
.status-tab:focus-within span { outline: 3px solid #ffd04b; outline-offset: 2px; }

.message-card {
  margin-bottom: 14px; padding: 16px 18px;
  background: #fff; border: 1px solid #dbe4f0; border-left: 4px solid #b9c7db;
  border-radius: 8px;
}
.message-card.is-unread { border-left-color: #C9A96E; box-shadow: 0 2px 10px rgba(26, 60, 110, .08); }
.message-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.message-type { display: inline-flex; align-items: center; gap: 8px; }
.unread-dot { width: 8px; height: 8px; border-radius: 50%; background: #C63D4A; }
.message-time { color: #5b6b84; font-size: 12px; }
/* 无障碍：ElementUI 默认 info 标签 #8C8C8C 在浅底仅 3.05:1，统一改为深蓝保证 4.5:1 */
.message-card ::v-deep .el-tag {
  background: #eef3fb;
  border-color: #c9d6e8;
  color: #1A3C6E;
}
.message-title { margin: 10px 0 6px; color: #0b1f4a; font-size: 17px; }
.message-content { margin: 0; color: #44505f; font-size: 14px; line-height: 1.7; }
.message-actions { display: flex; gap: 8px; padding-top: 8px; }

.empty-box { text-align: center; padding: 60px 0; color: #8a94a6; }
.empty-box i { font-size: 56px; color: #c4cedd; }
.empty-box p { margin: 12px 0 20px; font-size: 15px; }
.big-btn { min-height: 44px; padding: 10px 28px; font-size: 16px; }
.pagination-container { margin-top: 20px; text-align: center; }

/* ===== 适老化：大字 / 超大 / 高对比 均由 html 根类驱动 ===== */
html.care-large .page-title { font-size: 28px; }
html.care-large .page-sub,
html.care-large .message-content,
html.care-large .message-time { font-size: 17px; }
html.care-large .message-title { font-size: 20px; }
html.care-large .big-btn,
html.care-large .status-tab span,
html.care-large .read-all-btn { min-height: 48px; font-size: 17px; }

html.care-xlarge .page-title { font-size: 32px; }
html.care-xlarge .page-sub,
html.care-xlarge .message-content,
html.care-xlarge .message-time { font-size: 20px; }
html.care-xlarge .message-title { font-size: 24px; }
html.care-xlarge .big-btn,
html.care-xlarge .status-tab span,
html.care-xlarge .read-all-btn { min-height: 52px; font-size: 19px; padding: 12px 24px; }

html.care-high-contrast .portal-page { background: #fff; }
html.care-high-contrast .page-title,
html.care-high-contrast .message-title { color: #000; }
html.care-high-contrast .page-sub,
html.care-high-contrast .message-content,
html.care-high-contrast .message-time { color: #000; }
html.care-high-contrast .message-card { border: 2px solid #000; border-left: 6px solid #000; }
html.care-high-contrast .status-tab span,
html.care-high-contrast .read-all-btn { border-color: #000; background: #fff; color: #000; }
html.care-high-contrast .status-tab.is-active span { background: #000; border-color: #000; color: #fff; }

/* U6 移动端 */
@media (max-width: 768px) {
  .portal-page { padding: 12px 0 24px; }
  .page-wrap { padding: 0 12px; }
  .page-title { font-size: 20px; }
  .page-head { flex-direction: column; }
  .read-all-btn { width: 100%; }
  .status-tabs { display: flex; }
  .status-tab { flex: 1 0 auto; }
  .status-tab span { width: 100%; min-height: 44px; padding: 10px 8px; }
  .message-actions { flex-wrap: wrap; }
}
</style>
