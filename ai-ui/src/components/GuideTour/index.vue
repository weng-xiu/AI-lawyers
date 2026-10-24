<template>
  <el-dialog
    :visible="visible"
    width="560px"
    custom-class="guide-tour-dialog"
    :close-on-click-modal="false"
    title="新手引导"
    append-to-body
    @update:visible="onVisibleChange">
    <div class="guide-body">
      <div class="guide-progress">第 {{ currentIndex + 1 }} / {{ steps.length }} 步</div>
      <div class="guide-step" role="group" :aria-label="step.title">
        <div class="guide-icon"><i :class="step.icon" aria-hidden="true"></i></div>
        <h3 class="guide-title">{{ step.title }}</h3>
        <p class="guide-desc">{{ step.desc }}</p>
        <p v-if="step.tip" class="guide-tip">{{ step.tip }}</p>
      </div>
      <ul class="guide-dots" aria-hidden="true">
        <li v-for="(s, i) in steps" :key="i" :class="{ active: i === currentIndex }"></li>
      </ul>
    </div>
    <div slot="footer" class="guide-footer">
      <el-button type="text" class="guide-skip" @click="handleSkip">跳过引导</el-button>
      <div class="guide-actions">
        <el-button v-if="currentIndex > 0" size="small" @click="prev">上一步</el-button>
        <el-button v-if="step.path" size="small" type="text" @click="goStep">去这个页面看看</el-button>
        <el-button v-if="!isLast" type="primary" size="small" @click="next">下一步</el-button>
        <el-button v-else type="primary" size="small" @click="finish">开始使用</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script>
/**
 * U7 新手引导：首次登录展示 4 步核心路径，帮助入口可随时重看。
 *
 * 只负责展示与步骤切换，是否首次登录、是否已完成由父组件用 localStorage 判定；
 * 完成/跳过通过 done/skip 事件交回父组件持久化。
 */
export const DEFAULT_GUIDE_STEPS = [
  {
    icon: 'el-icon-s-home',
    title: '工作台：先看今天要处理什么',
    desc: '进入「话务中心 → 坐席工作台」，待办工单、未接来电、公告与快捷建单都在一屏。',
    tip: '建议先处理超时工单，再回拨未接来电。',
    path: '/lawyers/callCenter/callWorkbench'
  },
  {
    icon: 'el-icon-tickets',
    title: '工单：从通话到闭环',
    desc: '来电弹屏可一键建单；在「工单管理」按状态筛选跟进，SLA 超时会自动升级。',
    tip: '工单详情可查看通话录音、时间线与协作记录。',
    path: '/lawyers/callCenter/callTicket'
  },
  {
    icon: 'el-icon-data-line',
    title: '巡场：大屏与队列监控',
    desc: '「队列监控」查看 Stream 积压与死信，大屏适合投屏值班；发现死信可直接重投。',
    tip: '平板/窄屏下页面自动折行，适合只读巡场。',
    path: '/lawyers/queueMonitor'
  },
  {
    icon: 'el-icon-reading',
    title: '知识库：让 AI 越用越准',
    desc: '知识库为空时可用「导入」批量建库，或点「AI 沉淀」从通话与工单自动生成草稿，审核后生效。',
    tip: '知识命中会直接影响坐席 Copilot 推荐与机器人回答。',
    path: '/lawyers/knowledge'
  }
]

export default {
  name: 'GuideTour',
  props: {
    visible: { type: Boolean, default: false },
    steps: { type: Array, default: () => DEFAULT_GUIDE_STEPS }
  },
  data() {
    return {
      currentIndex: 0
    }
  },
  computed: {
    step() {
      return this.steps[this.currentIndex] || {}
    },
    isLast() {
      return this.currentIndex >= this.steps.length - 1
    }
  },
  watch: {
    visible(val) {
      if (val) this.currentIndex = 0
    }
  },
  methods: {
    next() {
      if (!this.isLast) this.currentIndex += 1
    },
    prev() {
      if (this.currentIndex > 0) this.currentIndex -= 1
    },
    finish() {
      this.$emit('done')
      this.$emit('update:visible', false)
    },
    handleSkip() {
      this.$emit('skip')
      this.$emit('update:visible', false)
    },
    onVisibleChange(val) {
      if (!val) this.$emit('update:visible', false)
    },
    goStep() {
      const path = this.step.path
      if (!path) return
      this.$emit('navigate', path)
      this.$emit('update:visible', false)
      if (this.$route && this.$route.path !== path && this.$router) {
        this.$router.push(path).catch(() => {})
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.guide-body {
  padding: 4px 2px 0;
}
.guide-progress {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 10px;
}
.guide-step {
  padding: 18px 16px;
  border: 1px solid #eef1f6;
  border-radius: 8px;
  background: #fafbfd;
}
.guide-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #eaf1fb;
  color: #1a3c6e;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-bottom: 10px;
}
.guide-title {
  margin: 0 0 8px;
  font-size: 17px;
  color: #0b1f4a;
}
.guide-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #333;
}
.guide-tip {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-left: 3px solid #c9a96e;
  background: #fffaf0;
  font-size: 12px;
  line-height: 1.6;
  color: #6b5a35;
}
.guide-dots {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}
.guide-dots li {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d8dee9;
}
.guide-dots li.active {
  background: #1a3c6e;
  width: 20px;
  border-radius: 4px;
}
.guide-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.guide-skip {
  padding: 0;
  color: #8c8c8c;
}
.guide-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
