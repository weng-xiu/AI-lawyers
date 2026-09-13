<template>
  <div class="portal-page my-tickets">
    <!-- 跳到主内容由 App.vue 提供 -->
    <main id="main-content" role="main" class="page-wrap" aria-live="polite">
      <div class="page-head">
        <h2 class="page-title">我的工单</h2>
        <p class="page-sub">这里展示与您手机号关联的来电工单和跨部门转办进度</p>
      </div>

      <!-- 状态筛选 -->
      <el-radio-group v-model="query.status" class="status-tabs" size="medium" @change="handleFilter" aria-label="工单状态筛选">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="0">待处理</el-radio-button>
        <el-radio-button label="1">处理中</el-radio-button>
        <el-radio-button label="2">已完成</el-radio-button>
        <el-radio-button label="3">已归档</el-radio-button>
      </el-radio-group>

      <div v-loading="loading">
        <div v-if="ticketList.length === 0 && !loading" class="empty-box" role="status">
          <i class="el-icon-document"></i>
          <p>暂无工单记录</p>
          <el-button type="primary" class="big-btn" @click="goConsult">去提交法律咨询</el-button>
        </div>

        <el-card v-for="item in ticketList" :key="item.ticketId" class="ticket-card" shadow="hover">
          <div class="ticket-head">
            <span class="ticket-no" :title="item.ticketNo">工单号：{{ item.ticketNo || item.ticketId }}</span>
            <el-tag :type="statusTag(item.status)" size="medium" effect="dark">{{ statusText(item.status) }}</el-tag>
          </div>
          <h3 class="ticket-title">{{ item.title || '法律咨询工单' }}</h3>
          <p v-if="item.content" class="ticket-content">{{ item.content }}</p>

          <ul class="ticket-meta">
            <li>
              <span class="meta-label">优先级</span>
              <el-tag size="mini" :type="priorityTag(item.priority)">{{ priorityText(item.priority) }}</el-tag>
            </li>
            <li>
              <span class="meta-label">承办人</span>
              <span>{{ item.assignUserName || '待分派' }}</span>
            </li>
            <li v-if="item.direction === 'IN'">
              <span class="meta-label">来源</span>
              <el-tag size="mini" type="warning">外单位转入</el-tag>
            </li>
            <li v-if="item.externalType">
              <span class="meta-label">转办渠道</span>
              <span>{{ externalTypeText(item.externalType) }}</span>
            </li>
            <li v-if="item.externalStatus">
              <span class="meta-label">外部状态</span>
              <span>{{ externalStatusText(item.externalStatus) }}</span>
            </li>
            <li>
              <span class="meta-label">承诺时限</span>
              <span :class="{ 'overtime': item.overtimeFlag === 1 }">
                {{ formatTime(item.dueTime) }}
                <el-tag v-if="item.overtimeFlag === 1" type="danger" size="mini">已超期</el-tag>
              </span>
            </li>
            <li>
              <span class="meta-label">提交时间</span>
              <span>{{ formatTime(item.createTime) }}</span>
            </li>
          </ul>

          <div v-if="item.processContent" class="process-box">
            <div class="process-title">
              <i class="el-icon-service"></i> 最新办理情况
              <span class="process-time">{{ formatTime(item.processTime) }}</span>
            </div>
            <p>{{ item.processContent }}</p>
          </div>
        </el-card>
      </div>

      <div v-if="total > 0" class="pagination-container">
        <el-pagination
          background
          @current-change="handlePageChange"
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          layout="total, prev, pager, next"
          :total="total">
        </el-pagination>
      </div>
    </main>
  </div>
</template>

<script>
import { myTickets } from '@/api/portal'

export default {
  name: 'MyTickets',
  data() {
    return {
      loading: false,
      ticketList: [],
      total: 0,
      query: { pageNum: 1, pageSize: 10, status: '' }
    }
  },
  created() {
    this.loadTickets()
  },
  methods: {
    loadTickets() {
      this.loading = true
      myTickets(this.query).then(res => {
        this.ticketList = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    handleFilter() {
      this.query.pageNum = 1
      this.loadTickets()
    },
    handlePageChange(page) {
      this.query.pageNum = page
      this.loadTickets()
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
    goConsult() {
      this.$router.push('/consultation/submit')
    },
    statusText(s) {
      return { '0': '待处理', '1': '处理中', '2': '已完成', '3': '已归档' }[String(s)] || '待处理'
    },
    statusTag(s) {
      return { '0': 'warning', '1': '', '2': 'success', '3': 'info' }[String(s)] || 'warning'
    },
    priorityText(p) {
      return { '1': '紧急', '2': '一般', '3': '低' }[String(p)] || '一般'
    },
    priorityTag(p) {
      return { '1': 'danger', '2': 'warning', '3': 'info' }[String(p)] || 'warning'
    },
    externalTypeText(t) {
      const map = {
        HOTLINE_12345: '12345政务热线',
        JUSTICE_BUREAU: '司法局',
        LEGAL_AID: '法律援助中心',
        MEDIATION: '人民调解组织',
        NOTARY: '公证机构',
        COURT: '人民法院',
        OTHER: '其他机构'
      }
      return map[t] || t
    },
    externalStatusText(s) {
      const map = {
        PENDING: '待受理', ACCEPTED: '已受理', PROCESSING: '办理中',
        DONE: '已办结', REJECTED: '不予受理', FAILED: '转办失败'
      }
      return map[s] || s
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
.page-head { margin-bottom: 18px; }
.page-title { font-size: 24px; color: #0b1f4a; margin: 0 0 6px; }
.page-sub { color: #5b6b84; font-size: 14px; margin: 0; }

.status-tabs { margin-bottom: 18px; }
.status-tabs .el-radio-button__inner { min-height: 40px; padding: 10px 18px; }

.ticket-card { margin-bottom: 16px; border-radius: 8px; border-left: 4px solid #255A99; }
.ticket-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.ticket-no { color: #8a94a6; font-size: 13px; }
.ticket-title { font-size: 17px; color: #0b1f4a; margin: 0 0 8px; }
.ticket-content { color: #44505f; font-size: 14px; line-height: 1.7; margin: 0 0 10px; }

.ticket-meta { list-style: none; padding: 0; margin: 0; display: flex; flex-wrap: wrap; gap: 6px 24px; }
.ticket-meta li { font-size: 14px; color: #3d4a5c; display: flex; align-items: center; gap: 8px; }
.meta-label { color: #8a94a6; }
.overtime { color: #C63D4A; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; }

.process-box {
  margin-top: 12px; padding: 12px 14px; background: #f3f7fc;
  border-radius: 6px; border-left: 3px solid #255A99;
}
.process-title { font-size: 14px; font-weight: 600; color: #0b1f4a; margin-bottom: 6px; }
.process-time { float: right; font-weight: 400; color: #8a94a6; font-size: 12px; }
.process-box p { margin: 0; font-size: 14px; color: #3d4a5c; line-height: 1.7; }

.empty-box { text-align: center; padding: 60px 0; color: #8a94a6; }
.empty-box i { font-size: 56px; color: #c4cedd; }
.empty-box p { margin: 12px 0 20px; font-size: 15px; }
.big-btn { min-height: 44px; padding: 10px 28px; font-size: 16px; }

.pagination-container { margin-top: 20px; text-align: center; }

/* ===== F2 适老化：大字 / 超大 / 高对比 均由 html 根类驱动 ===== */
html.care-large .page-title { font-size: 28px; }
html.care-large .page-sub,
html.care-large .ticket-content,
html.care-large .ticket-meta li,
html.care-large .process-box p,
html.care-large .process-title { font-size: 17px; }
html.care-large .ticket-title { font-size: 20px; }
html.care-large .big-btn,
html.care-large .status-tabs .el-radio-button__inner { min-height: 48px; font-size: 17px; }

html.care-xlarge .page-title { font-size: 32px; }
html.care-xlarge .page-sub,
html.care-xlarge .ticket-content,
html.care-xlarge .ticket-meta li,
html.care-xlarge .process-box p,
html.care-xlarge .process-title { font-size: 20px; }
html.care-xlarge .ticket-title { font-size: 24px; }
html.care-xlarge .ticket-meta { gap: 10px 28px; }
html.care-xlarge .big-btn,
html.care-xlarge .status-tabs .el-radio-button__inner { min-height: 52px; font-size: 19px; padding: 12px 24px; }
html.care-xlarge .ticket-card { border-left-width: 6px; }

html.care-high-contrast .portal-page { background: #fff; }
html.care-high-contrast .page-title,
html.care-high-contrast .ticket-title { color: #000; }
html.care-high-contrast .page-sub,
html.care-high-contrast .ticket-content,
html.care-high-contrast .ticket-meta li,
html.care-high-contrast .process-box p { color: #000; }
html.care-high-contrast .ticket-card { border: 2px solid #000; border-left: 6px solid #000; }
html.care-high-contrast .process-box { background: #fff; border: 2px solid #000; }
html.care-high-contrast .meta-label { color: #333; text-decoration: underline; }
</style>
