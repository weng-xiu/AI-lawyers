<template>
  <div class="call-workbench">
    <!-- 欢迎横幅 -->
    <div class="wb-welcome-banner">
      <div class="wb-welcome-left">
        <h2>欢迎回来，张律师</h2>
        <p>{{ currentDate }}</p>
      </div>
      <div class="wb-welcome-right">
        <div class="wb-welcome-stat">
          <span class="wb-welcome-num">23</span>
          <span class="wb-welcome-label">今日已服务群众</span>
        </div>
        <div class="wb-welcome-stat">
          <span class="wb-welcome-num">98.5%</span>
          <span class="wb-welcome-label">满意度</span>
        </div>
      </div>
    </div>

    <!-- 个人统计卡片 -->
    <el-row :gutter="16" class="wb-stat-row">
      <el-col :span="6" v-for="(stat, idx) in personalStats" :key="idx">
        <div class="wb-stat-card">
          <div class="wb-stat-header">
            <span class="wb-stat-title">{{ stat.title }}</span>
            <span class="wb-stat-trend" :class="'wb-trend-' + stat.trendType">
              <i :class="stat.trendType === 'up' ? 'el-icon-top' : 'el-icon-bottom'"></i>
              {{ stat.trend }}
            </span>
          </div>
          <div class="wb-stat-value">{{ stat.value }}</div>
          <div class="wb-stat-icon" :class="'wb-icon-' + stat.type">
            <i :class="stat.icon"></i>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 团队概览 -->
    <el-card shadow="never" class="wb-card wb-team-card">
      <div slot="header" class="wb-card-header">
        <span><i class="el-icon-s-custom"></i> 团队概览</span>
      </div>
      <el-row :gutter="16">
        <el-col :span="6" v-for="(item, idx) in teamStats" :key="idx">
          <div class="wb-team-item">
            <div class="wb-team-value">{{ item.value }}</div>
            <div class="wb-team-label">{{ item.label }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 公告 + 快捷入口 -->
    <el-row :gutter="16" class="wb-section">
      <el-col :span="16">
        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-header">
            <span><i class="el-icon-bell"></i> 公告通知</span>
            <el-button type="text" size="mini">查看全部</el-button>
          </div>
          <div class="wb-notice-list">
            <div class="wb-notice-item" v-for="(item, idx) in noticeList" :key="idx">
              <span class="wb-notice-date">{{ item.date }}</span>
              <span class="wb-notice-title">{{ item.title }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-header">
            <span><i class="el-icon-menu"></i> 快捷入口</span>
          </div>
          <div class="wb-quick-grid">
            <div class="wb-quick-item" v-for="(item, idx) in quickList" :key="idx" @click="handleQuickClick(item.path)">
              <div class="wb-quick-icon" :class="'wb-qicon-' + item.type">
                <i :class="item.icon"></i>
              </div>
              <span>{{ item.name }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近通话记录 -->
    <el-card shadow="never" class="wb-card">
      <div slot="header" class="wb-card-header">
        <span><i class="el-icon-phone"></i> 最近通话记录</span>
        <div style="display:flex;align-items:center;gap:12px;">
          <el-select v-model="todayFilter" size="mini" style="width:80px;">
            <el-option label="今天" value="today" />
            <el-option label="本周" value="week" />
            <el-option label="本月" value="month" />
          </el-select>
          <el-button type="text" size="mini">查看全部</el-button>
        </div>
      </div>
      <el-table :data="recentRecords" size="small" :show-header="true">
        <el-table-column label="来电号码" align="left" prop="callerNumber" width="140" />
        <el-table-column label="服务类型" align="center" prop="serviceType" width="120">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.serviceType === '语音咨询' ? 'primary' : scope.row.serviceType === '图文咨询' ? 'success' : scope.row.serviceType === '视频咨询' ? 'warning' : 'info'" effect="light">{{ scope.row.serviceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="咨询内容摘要" align="left" prop="summary" />
        <el-table-column label="通话时长" align="center" prop="duration" width="100" />
        <el-table-column label="满意度" align="center" prop="satisfaction" width="100">
          <template slot-scope="scope">
            <span :style="{ color: scope.row.satisfaction === '满意' ? '#52c41a' : '#e6a23c' }">{{ scope.row.satisfaction }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
export default {
  name: "CallWorkbench",
  data() {
    return {
      todayFilter: 'today',
      currentDate: '',
      personalStats: [
        { title: '总通话数', value: '156 次', trend: '+12%', trendType: 'up', icon: 'el-icon-phone', type: 'blue' },
        { title: '通话时长', value: '4小时32分', trend: '+8%', trendType: 'up', icon: 'el-icon-time', type: 'green' },
        { title: '服务满意度', value: '98.5%', trend: '+2.1%', trendType: 'up', icon: 'el-icon-star-on', type: 'orange' },
        { title: '在线时长', value: '6小时15分', trend: '+5%', trendType: 'up', icon: 'el-icon-user', type: 'purple' }
      ],
      teamStats: [
        { value: '1,234', label: '团队总通话' },
        { value: '3分42秒', label: '平均通话时长' },
        { value: '96.8%', label: '团队满意度' },
        { value: '7小时', label: '平均在线时长' }
      ],
      noticeList: [
        { date: '07-16', title: '关于2026年7月排班调整的通知' },
        { date: '07-15', title: '知识库更新：民法典婚姻家庭编修订要点' },
        { date: '07-14', title: '系统维护公告：7月20日凌晨2:00-4:00' }
      ],
      quickList: [
        { name: '语音咨询', icon: 'el-icon-phone', type: 'blue', path: '/lawyers/callCenter/callPanel' },
        { name: '图文咨询', icon: 'el-icon-chat-dot-round', type: 'green', path: '' },
        { name: '视频咨询', icon: 'el-icon-video-camera', type: 'yellow', path: '' },
        { name: '外呼服务', icon: 'el-icon-phone-outline', type: 'red', path: '' },
        { name: '工单登记', icon: 'el-icon-edit', type: 'blue2', path: '/lawyers/callCenter/callTicket' },
        { name: '台账填写', icon: 'el-icon-document', type: 'teal', path: '/lawyers/callCenter/callLedger' },
        { name: '知识检索', icon: 'el-icon-search', type: 'pink', path: '' },
        { name: '回访任务', icon: 'el-icon-back', type: 'purple2', path: '' }
      ],
      recentRecords: [
        { callerNumber: '138****2761', serviceType: '语音咨询', summary: '劳动者因工伤后用人单位拒绝申请工伤认定，咨询如何自行申请及所需材料', duration: '8分32秒', satisfaction: '满意' },
        { callerNumber: '136****0084', serviceType: '图文咨询', summary: '离婚后财产分割问题，婚前购买的房屋婚后共同还贷，咨询离婚时如何分割', duration: '12分15秒', satisfaction: '满意' },
        { callerNumber: '189****5523', serviceType: '语音咨询', summary: '公司未签订劳动合同，工作半年后被辞退，咨询经济补偿金计算', duration: '6分40秒', satisfaction: '满意' },
        { callerNumber: '135****8890', serviceType: '语音咨询', summary: '继承纠纷咨询，父母去世后房产继承分配问题', duration: '15分20秒', satisfaction: '非常满意' },
        { callerNumber: '187****3341', serviceType: '视频咨询', summary: '交通事故责任认定不服，咨询复核程序和时限', duration: '10分05秒', satisfaction: '满意' }
      ]
    }
  },
  created() {
    this.initDate()
  },
  methods: {
    initDate() {
      const now = new Date()
      const weekDays = ['日', '一', '二', '三', '四', '五', '六']
      const y = now.getFullYear()
      const m = now.getMonth() + 1
      const d = now.getDate()
      const w = weekDays[now.getDay()]
      this.currentDate = `${y}年${m}月${d}日 星期${w}`
    },
    handleQuickClick(path) {
      if (path) {
        this.$router.push({ path })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.call-workbench {
  background: #f1f5f9;
  min-height: 100vh;
  margin: -20px;
  padding: 20px;
}

// 欢迎横幅
.wb-welcome-banner {
  background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%);
  border-radius: 10px;
  padding: 24px 28px;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  position: relative;
  overflow: hidden;
}
.wb-welcome-banner::before {
  content: '';
  position: absolute;
  right: -40px;
  top: -40px;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: rgba(255,255,255,0.06);
}
.wb-welcome-banner::after {
  content: '';
  position: absolute;
  right: 60px;
  bottom: -60px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: rgba(255,255,255,0.04);
}
.wb-welcome-left h2 {
  margin: 0 0 6px 0;
  font-size: 22px;
  font-weight: 700;
}
.wb-welcome-left p {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
}
.wb-welcome-right {
  display: flex;
  gap: 40px;
  z-index: 1;
}
.wb-welcome-stat {
  text-align: right;
}
.wb-welcome-num {
  display: block;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}
.wb-welcome-label {
  display: block;
  font-size: 12px;
  opacity: 0.8;
  margin-top: 4px;
}

// 统计卡片
.wb-stat-row {
  margin-bottom: 20px;
}
.wb-stat-card {
  background: #fff;
  border-radius: 10px;
  padding: 18px 20px;
  position: relative;
  border: 1px solid #e2e8f0;
  transition: all 0.3s;
}
.wb-stat-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.06);
  transform: translateY(-2px);
}
.wb-stat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.wb-stat-title {
  font-size: 13px;
  color: #64748b;
}
.wb-stat-trend {
  font-size: 12px;
  font-weight: 600;
}
.wb-trend-up {
  color: #16a34a;
}
.wb-trend-down {
  color: #ef4444;
}
.wb-stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
}
.wb-stat-icon {
  position: absolute;
  right: 16px;
  bottom: 16px;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  opacity: 0.9;
}
.wb-icon-blue { background: #eff6ff; color: #3b82f6; }
.wb-icon-green { background: #f0fdf4; color: #16a34a; }
.wb-icon-orange { background: #fff7ed; color: #f97316; }
.wb-icon-purple { background: #faf5ff; color: #a855f7; }

// 通用卡片
.wb-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}
.wb-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}
.wb-card-header i {
  color: #3b82f6;
  margin-right: 6px;
}

// 团队概览
.wb-team-card {
  margin-bottom: 20px;
  .el-card__body { padding-top: 16px; padding-bottom: 16px; }
}
.wb-team-item {
  text-align: center;
  padding: 8px 0;
}
.wb-team-value {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}
.wb-team-label {
  font-size: 12px;
  color: #64748b;
}

// 公告
.wb-section {
  margin-bottom: 20px;
}
.wb-notice-list {
  padding: 4px 0;
}
.wb-notice-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}
.wb-notice-item:last-child { border-bottom: none; }
.wb-notice-date {
  color: #94a3b8;
  font-size: 12px;
  margin-right: 16px;
  width: 50px;
  flex-shrink: 0;
}
.wb-notice-title {
  color: #3b82f6;
  font-size: 13px;
  cursor: pointer;
}
.wb-notice-title:hover {
  color: #1d4ed8;
}

// 快捷入口
.wb-quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 8px;
}
.wb-quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 4px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
}
.wb-quick-item:hover {
  background: #f8fafc;
}
.wb-quick-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin-bottom: 6px;
}
.wb-qicon-blue { background: #eff6ff; color: #3b82f6; }
.wb-qicon-green { background: #f0fdf4; color: #16a34a; }
.wb-qicon-yellow { background: #fef3c7; color: #d97706; }
.wb-qicon-red { background: #fee2e2; color: #ef4444; }
.wb-qicon-blue2 { background: #e0e7ff; color: #6366f1; }
.wb-qicon-teal { background: #ccfbf1; color: #0d9488; }
.wb-qicon-pink { background: #fce7f3; color: #db2777; }
.wb-qicon-purple2 { background: #ede9fe; color: #7c3aed; }
.wb-quick-item span {
  font-size: 12px;
  color: #475569;
}

// 最近通话
::v-deep .el-table {
  th {
    background: #f8fafc !important;
    color: #64748b !important;
    font-weight: 600 !important;
    border-color: #e2e8f0 !important;
  }
  td {
    border-color: #f1f5f9 !important;
  }
  tr:hover > td {
    background: #f8fafc !important;
  }
}
</style>
