<template>
  <ol class="ticket-progress" :aria-label="'工单进度：' + currentLabel">
    <li
      v-for="(step, index) in steps"
      :key="step.key"
      class="tp-step"
      :class="{ 'is-done': index < activeIndex, 'is-current': index === activeIndex }"
      :aria-current="index === activeIndex ? 'step' : null">
      <span class="tp-dot" aria-hidden="true">
        <i v-if="index < activeIndex" class="el-icon-check"></i>
        <template v-else>{{ index + 1 }}</template>
      </span>
      <span class="tp-label">{{ step.label }}</span>
      <span class="tp-desc">{{ step.desc }}</span>
    </li>
  </ol>
</template>

<script>
/**
 * V8 工单进度可视化：公众端工单卡片内展示「已提交 → 待受理 → 办理中 → 已办结」，
 * 当前节点使用 aria-current="step" 供读屏器识别；归档工单仍展示办结态，由状态标签补充「已归档」。
 */
export default {
  name: 'TicketProgress',
  props: {
    status: {
      type: [String, Number],
      default: '0'
    }
  },
  data() {
    return {
      steps: [
        { key: 'submitted', label: '已提交', desc: '咨询已送达' },
        { key: 'accepted', label: '待受理', desc: '等待承办人接单' },
        { key: 'processing', label: '办理中', desc: '承办人正在处理' },
        { key: 'done', label: '已办结', desc: '可查看办理结果' }
      ]
    }
  },
  computed: {
    activeIndex() {
      // 0 待处理→待受理；1 处理中→办理中；2 已完成 / 3 已归档→已办结
      const map = { '0': 1, '1': 2, '2': 3, '3': 3 }
      return map[String(this.status)] !== undefined ? map[String(this.status)] : 0
    },
    currentLabel() {
      return this.steps[this.activeIndex].label
    }
  }
}
</script>

<style scoped>
.ticket-progress {
  display: flex;
  align-items: flex-start;
  list-style: none;
  margin: 14px 0 4px;
  padding: 0;
}
.tp-step { position: relative; flex: 1; min-width: 0; padding-right: 10px; }
.tp-step:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 15px;
  left: 34px;
  right: 0;
  height: 2px;
  background: #d7e1ee;
}
.tp-step.is-done:not(:last-child)::after { background: #2B8C6E; }
.tp-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin-bottom: 6px;
  border: 2px solid #c3d0e2;
  border-radius: 50%;
  background: #fff;
  color: #8a94a6;
  font-size: 14px;
  font-weight: 600;
  position: relative;
  z-index: 1;
}
.tp-step.is-done .tp-dot { background: #2B8C6E; border-color: #2B8C6E; color: #fff; }
.tp-step.is-current .tp-dot {
  border-color: #1A3C6E;
  color: #1A3C6E;
  box-shadow: 0 0 0 4px rgba(26, 60, 110, .12);
}
.tp-label { display: block; color: #5b6b84; font-size: 14px; font-weight: 600; }
.tp-step.is-done .tp-label { color: #2B8C6E; }
.tp-step.is-current .tp-label { color: #1A3C6E; }
.tp-desc { display: block; margin-top: 2px; color: #8a94a6; font-size: 12px; line-height: 1.4; }

/* F2 适老档 */
html.care-large .tp-label { font-size: 17px; }
html.care-large .tp-desc { font-size: 14px; }
html.care-large .tp-dot { width: 36px; height: 36px; font-size: 17px; }
html.care-xlarge .tp-label { font-size: 20px; }
html.care-xlarge .tp-desc { font-size: 16px; }
html.care-xlarge .tp-dot { width: 42px; height: 42px; font-size: 20px; }

/* 高对比度 */
html.care-high-contrast .tp-dot { border-color: #000; background: #fff; color: #000; }
html.care-high-contrast .tp-step.is-done .tp-dot { background: #000; border-color: #000; color: #fff; }
html.care-high-contrast .tp-step.is-current .tp-dot { border-color: #000; box-shadow: none; outline: 3px solid #000; outline-offset: 1px; }
html.care-high-contrast .tp-label,
html.care-high-contrast .tp-desc { color: #000; }
html.care-high-contrast .tp-step:not(:last-child)::after { background: #000; }

/* 窄屏隐藏说明行，保留节点与状态名 */
@media (max-width: 768px) {
  .tp-desc { display: none; }
  .tp-step { padding-right: 6px; }
  .tp-step:not(:last-child)::after { left: 28px; }
  .tp-dot { width: 26px; height: 26px; font-size: 13px; }
  .tp-label { font-size: 13px; }
}
</style>
