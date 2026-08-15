<template>
  <div class="call-pop">
    <div class="cp-container">
      <div class="cp-banner">
        <div class="cp-banner-left">
          <div class="cp-banner-icon">
            <i class="el-icon-phone"></i>
          </div>
          <div class="cp-banner-title">
            <div class="cp-banner-main">来电接入</div>
            <div class="cp-banner-sub">
              <span class="cp-dot"></span>
              正在通话中
            </div>
          </div>
        </div>
        <div class="cp-banner-center">
          <div class="cp-banner-number">138****5678</div>
          <div class="cp-banner-info">
            <span><i class="el-icon-location-outline"></i> 广州市越秀区</span>
            <span class="cp-divider">|</span>
            <span>IVR: 婚姻家庭法律咨询</span>
          </div>
        </div>
        <div class="cp-banner-right">
          <div class="cp-banner-time">
            <div class="cp-time-label">通话时长</div>
            <div class="cp-time-value">{{ callDuration }}</div>
          </div>
          <div class="cp-banner-connect">
            <div class="cp-connect-label">接通时间</div>
            <div class="cp-connect-value">14:32:15</div>
          </div>
        </div>
      </div>

      <el-row :gutter="16" class="cp-main">
        <el-col :span="8">
          <el-card shadow="never" class="cp-card cp-caller-card">
            <div slot="header" class="cp-card-header">
              <span>来电人信息</span>
              <el-button type="text" size="mini" class="cp-edit-btn">
                <i class="el-icon-edit"></i> 编辑
              </el-button>
            </div>
            <div class="cp-caller-header">
              <div class="cp-avatar">王</div>
              <div class="cp-caller-info">
                <div class="cp-caller-name">
                  王女士
                  <el-tag size="mini" type="warning" effect="light" class="cp-call-count">第3次来电</el-tag>
                </div>
                <div class="cp-caller-phone">138****5678</div>
              </div>
            </div>
            <div class="cp-caller-detail">
              <div class="cp-detail-item">
                <span class="cp-detail-label">来电人</span>
                <span class="cp-detail-value">王女士</span>
              </div>
              <div class="cp-detail-item">
                <span class="cp-detail-label">归属地</span>
                <span class="cp-detail-value">广州市越秀区</span>
              </div>
              <div class="cp-detail-item">
                <span class="cp-detail-label">来电次数</span>
                <span class="cp-detail-value">本月3次 / 累计12次</span>
              </div>
            </div>
            <div class="cp-caller-tags">
              <el-tag size="mini" effect="dark" class="cp-tag cp-tag-yellow">高价值客户</el-tag>
              <el-tag size="mini" effect="dark" class="cp-tag cp-tag-blue">婚姻咨询</el-tag>
            </div>
          </el-card>

          <el-card shadow="never" class="cp-card cp-ai-card">
            <div slot="header" class="cp-card-header">
              <span class="cp-ai-title">
                <span class="cp-ai-badge">AI</span>
                智能来电分析
              </span>
            </div>
            <el-row :gutter="8" class="cp-ai-grid">
              <el-col :span="8">
                <div class="cp-ai-item">
                  <div class="cp-ai-label">来电意图预测</div>
                  <div class="cp-ai-value">离婚诉讼咨询</div>
                  <div class="cp-ai-confidence">
                    <el-progress :percentage="87" :show-text="false" :stroke-width="4" color="#7C3AED" />
                    <span>置信度 87%</span>
                  </div>
                </div>
              </el-col>
              <el-col :span="8">
                <div class="cp-ai-item">
                  <div class="cp-ai-label">咨询偏好</div>
                  <div class="cp-ai-value cp-ai-pref">婚姻家庭</div>
                  <div class="cp-ai-sub">婚姻财产、子女抚养</div>
                </div>
              </el-col>
              <el-col :span="8">
                <div class="cp-ai-item">
                  <div class="cp-ai-label">高频问题</div>
                  <div class="cp-ai-value cp-ai-freq">财产分割</div>
                  <div class="cp-ai-sub">提及5次</div>
                </div>
              </el-col>
            </el-row>
            <div class="cp-emotion-warning">
              <i class="el-icon-warning-outline"></i>
              <span>该用户近期来电频繁（本月3次），建议关注情绪状态</span>
            </div>
            <div class="cp-article-section">
              <div class="cp-article-title">推荐知识文章</div>
              <div class="cp-article-list">
                <div class="cp-article-item" v-for="(article, idx) in recommendArticles" :key="idx">
                  <div class="cp-article-icon">
                    <i class="el-icon-document"></i>
                  </div>
                  <div class="cp-article-content">
                    <div class="cp-article-name">{{ article.title }}</div>
                    <div class="cp-article-meta">{{ article.category }} · {{ article.views }}阅读</div>
                  </div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="16">
          <el-card shadow="never" class="cp-card cp-history-card">
            <div slot="header" class="cp-card-header">
              <el-tabs v-model="activeTab" class="cp-history-tabs">
                <el-tab-pane label="历史通话" name="call"></el-tab-pane>
                <el-tab-pane label="历史工单" name="ticket"></el-tab-pane>
                <el-tab-pane label="来电轨迹" name="track"></el-tab-pane>
                <el-tab-pane label="用户画像" name="profile"></el-tab-pane>
              </el-tabs>
            </div>
            <div class="cp-history-content">
              <div class="cp-call-list">
                <div class="cp-call-item" v-for="(record, idx) in callHistory" :key="idx">
                  <div class="cp-call-icon">
                    <i class="el-icon-phone"></i>
                  </div>
                  <div class="cp-call-main">
                    <div class="cp-call-header">
                      <span class="cp-call-title">{{ record.title }}</span>
                      <el-tag size="mini" :type="record.satisfaction === '非常满意' ? 'success' : 'primary'" effect="light">
                        {{ record.satisfaction }}
                      </el-tag>
                      <span class="cp-call-time">{{ record.time }}</span>
                    </div>
                    <div class="cp-call-footer">
                      <el-tag size="mini" type="info" effect="plain">{{ record.type }}</el-tag>
                      <span class="cp-call-duration">时长 {{ record.duration }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <div class="cp-action-bar">
        <el-button type="success" size="medium" class="cp-action-btn cp-btn-green">
          <i class="el-icon-microphone"></i>
          <span>保持通话</span>
        </el-button>
        <el-button type="primary" size="medium" class="cp-action-btn cp-btn-blue">
          <i class="el-icon-s-promotion"></i>
          <span>转移来电</span>
        </el-button>
        <el-button size="medium" class="cp-action-btn cp-btn-gray">
          <i class="el-icon-user"></i>
          <span>咨询同事</span>
        </el-button>
        <el-button size="medium" class="cp-action-btn cp-btn-gray">
          <i class="el-icon-phone-outline"></i>
          <span>三方通话</span>
        </el-button>
        <el-button type="primary" size="medium" class="cp-action-btn cp-btn-blue">
          <i class="el-icon-document"></i>
          <span>一键登记台账</span>
        </el-button>
        <el-button size="medium" class="cp-action-btn cp-btn-purple">
          <i class="el-icon-cpu"></i>
          <span>接入智能助手</span>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { getRecord, listRecord } from "@/api/lawyers/callCenter"

export default {
  name: "CallPop",
  data() {
    return {
      activeTab: 'call',
      callDuration: '00:00:00',
      timer: null,
      seconds: 0,
      currentRecord: {},
      callerInfo: {
        name: '',
        phone: '',
        address: '',
        callCount: 0,
        totalCallCount: 0,
        tags: []
      },
      recommendArticles: [],
      callHistory: []
    }
  },
  created() {
    this.startTimer()
    const recordId = this.$route.query.recordId || this.$route.params.recordId
    if (recordId) {
      this.loadCallData(recordId)
    }
  },
  beforeDestroy() {
    this.clearTimer()
  },
  methods: {
    startTimer() {
      this.timer = setInterval(() => {
        this.seconds++
        this.callDuration = this.formatTime(this.seconds)
      }, 1000)
    },
    clearTimer() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    formatTime(totalSeconds) {
      const h = Math.floor(totalSeconds / 3600)
      const m = Math.floor((totalSeconds % 3600) / 60)
      const s = totalSeconds % 60
      return `${this.padZero(h)}:${this.padZero(m)}:${this.padZero(s)}`
    },
    padZero(num) {
      return num.toString().padStart(2, '0')
    },
    loadCallData(recordId) {
      getRecord(recordId).then(res => {
        const data = res.data || {}
        this.currentRecord = data
        this.callerInfo = {
          name: data.callerName || '',
          phone: data.callerNumber || '',
          address: data.callerAddress || '',
          callCount: data.monthCallCount || 0,
          totalCallCount: data.totalCallCount || 0,
          tags: data.tags || []
        }
        if (data.callerNumber) {
          this.loadCallHistory(data.callerNumber)
        }
      }).catch(() => {})
    },
    loadCallHistory(callerNumber) {
      listRecord({ callerNumber: callerNumber, pageNum: 1, pageSize: 10 }).then(res => {
        const rows = res.rows || []
        this.callHistory = rows.map(item => ({
          title: item.content || item.summary || '通话记录',
          satisfaction: this.getSatisfactionText(item.satisfaction),
          time: item.callTime || item.createTime || '',
          type: this.getServiceType(item.category),
          duration: this.formatDuration(item.duration || 0)
        }))
      }).catch(() => {
        this.callHistory = []
      })
    },
    getSatisfactionText(val) {
      if (val >= 4) return '非常满意'
      if (val >= 3) return '满意'
      if (val >= 2) return '一般'
      return '不满意'
    },
    getServiceType(category) {
      const map = { '1': '语音咨询', '2': '图文咨询', '3': '视频咨询' }
      return map[category] || '其他咨询'
    },
    formatDuration(seconds) {
      seconds = parseInt(seconds) || 0
      if (seconds <= 0) return '0秒'
      const m = Math.floor(seconds / 60)
      const s = seconds % 60
      return m + '分' + s + '秒'
    }
  }
}
</script>

<style lang="scss" scoped>
.call-pop {
  background: #F5F8FC;
  min-height: 100vh;
  margin: 0;
  padding: 24px;
  padding-bottom: 100px;
}

.cp-container {
  max-width: 1400px;
  margin: 0 auto;
}

.cp-banner {
  background: linear-gradient(135deg, #16A34A 0%, #16A34A 100%);
  border-radius: 10px;
  padding: 28px 36px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    right: -60px;
    top: -60px;
    width: 240px;
    height: 240px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.08);
  }

  &::after {
    content: '';
    position: absolute;
    right: 80px;
    bottom: -80px;
    width: 180px;
    height: 180px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.05);
  }
}

.cp-banner-left {
  display: flex;
  align-items: center;
  gap: 16px;
  z-index: 1;
}

.cp-banner-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  animation: cp-ring 1.5s ease-in-out infinite;
}

@keyframes cp-ring {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

.cp-banner-title {
  .cp-banner-main {
    font-size: 22px;
    font-weight: 700;
    margin-bottom: 4px;
  }

  .cp-banner-sub {
    font-size: 13px;
    opacity: 0.9;
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.cp-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  animation: cp-blink 1s ease-in-out infinite;
}

@keyframes cp-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.cp-banner-center {
  text-align: center;
  z-index: 1;

  .cp-banner-number {
    font-size: 40px;
    font-weight: 700;
    letter-spacing: 2px;
    margin-bottom: 12px;
  }

  .cp-banner-info {
    font-size: 13px;
    opacity: 0.9;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;

    i {
      margin-right: 4px;
    }
  }
}

.cp-divider {
  opacity: 0.5;
}

.cp-banner-right {
  display: flex;
  gap: 40px;
  text-align: right;
  z-index: 1;
}

.cp-time-label,
.cp-connect-label {
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 4px;
}

.cp-time-value {
  font-size: 26px;
  font-weight: 700;
  font-family: 'Courier New', monospace;
}

.cp-connect-value {
  font-size: 18px;
  font-weight: 600;
}

.cp-main {
  margin-bottom: 24px;
}

.cp-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  margin-bottom: 20px;

  &::v-deep .el-card__body {
    padding: 24px;
  }
}

.cp-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}

.cp-edit-btn {
  color: #1677FF;
}

.cp-caller-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid #F5F8FC;
  margin-bottom: 20px;
}

.cp-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1677FF 0%, #005BAC 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 600;
}

.cp-caller-info {
  .cp-caller-name {
    font-size: 16px;
    font-weight: 600;
    color: #1e293b;
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 6px;
  }

  .cp-caller-phone {
    font-size: 13px;
    color: #64748b;
  }
}

.cp-call-count {
  margin-left: 4px;
}

.cp-caller-detail {
  margin-bottom: 20px;
}

.cp-detail-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 13px;
}

.cp-detail-label {
  color: #64748b;
}

.cp-detail-value {
  color: #1e293b;
  font-weight: 500;
}

.cp-caller-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.cp-tag {
  &.cp-tag-yellow {
    background: #fef3c7;
    border-color: #f59e0b;
    color: #d97706;
  }

  &.cp-tag-blue {
    background: #D6E9FB;
    border-color: #1677FF;
    color: #005BAC;
  }
}

.cp-ai-card {
  .cp-ai-title {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #7C3AED;
  }
}

.cp-ai-badge {
  background: linear-gradient(135deg, #7C3AED 0%, #7c3aed 100%);
  color: #fff;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.cp-ai-grid {
  margin-bottom: 20px;
}

.cp-ai-item {
  background: #faf5ff;
  border-radius: 8px;
  padding: 16px 14px;
  text-align: center;
}

.cp-ai-label {
  font-size: 12px;
  color: #7C3AED;
  margin-bottom: 10px;
  font-weight: 500;
}

.cp-ai-value {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
}

.cp-ai-confidence {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;

  .el-progress {
    width: 100%;
  }

  span {
    font-size: 11px;
    color: #7C3AED;
  }
}

.cp-ai-pref {
  color: #1677FF;
}

.cp-ai-freq {
  color: #f59e0b;
}

.cp-ai-sub {
  font-size: 11px;
  color: #94a3b8;
}

.cp-emotion-warning {
  background: #fef3c7;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  font-size: 13px;
  color: #92400e;

  i {
    font-size: 18px;
    color: #f59e0b;
  }
}

.cp-article-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 16px;
}

.cp-article-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cp-article-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #F5F8FC;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #F5F8FC;
  }
}

.cp-article-icon {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  background: #EDF5FE;
  color: #1677FF;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.cp-article-content {
  flex: 1;
  min-width: 0;
}

.cp-article-name {
  font-size: 13px;
  color: #1e293b;
  font-weight: 500;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cp-article-meta {
  font-size: 11px;
  color: #94a3b8;
}

.cp-history-card {
  &::v-deep .el-card__body {
    padding-top: 0;
  }
}

.cp-history-tabs {
  &::v-deep .el-tabs__header {
    margin-bottom: 0;
  }

  &::v-deep .el-tabs__nav-wrap::after {
    display: none;
  }

  &::v-deep .el-tabs__item {
    height: 36px;
    line-height: 36px;
    font-size: 13px;
    color: #64748b;

    &.is-active {
      color: #16A34A;
      font-weight: 600;
    }
  }

  &::v-deep .el-tabs__active-bar {
    background-color: #16A34A;
  }
}

.cp-history-content {
  padding-top: 8px;
}

.cp-call-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cp-call-item {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  background: #F5F8FC;
  border-radius: 8px;
  border: 1px solid #F5F8FC;
  transition: all 0.2s;

  &:hover {
    background: #F5F8FC;
    border-color: #e2e8f0;
  }
}

.cp-call-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #E7F6EE;
  color: #16A34A;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.cp-call-main {
  flex: 1;
  min-width: 0;
}

.cp-call-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.cp-call-title {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
  flex: 1;
}

.cp-call-time {
  font-size: 12px;
  color: #94a3b8;
  flex-shrink: 0;
}

.cp-call-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cp-call-duration {
  font-size: 12px;
  color: #64748b;
}

.cp-action-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 1400px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  padding: 16px 24px;
  display: flex;
  justify-content: center;
  gap: 16px;
  z-index: 100;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.04);
}

.cp-action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 90px;
  padding: 12px 20px;
  border-radius: 8px;
  font-size: 13px;

  i {
    font-size: 20px;
  }

  &.cp-btn-green {
    background: linear-gradient(135deg, #16A34A 0%, #16A34A 100%);
    border: none;
    color: #fff;

    &:hover {
      background: linear-gradient(135deg, #16A34A 0%, #047857 100%);
    }
  }

  &.cp-btn-blue {
    background: linear-gradient(135deg, #1677FF 0%, #005BAC 100%);
    border: none;
    color: #fff;

    &:hover {
      background: linear-gradient(135deg, #005BAC 0%, #005BAC 100%);
    }
  }

  &.cp-btn-gray {
    background: #F5F8FC;
    border: 1px solid #e2e8f0;
    color: #475569;

    &:hover {
      background: #e2e8f0;
      color: #1e293b;
    }
  }

  &.cp-btn-purple {
    background: linear-gradient(135deg, #7C3AED 0%, #7c3aed 100%);
    border: none;
    color: #fff;

    &:hover {
      background: linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%);
    }
  }
}
</style>
