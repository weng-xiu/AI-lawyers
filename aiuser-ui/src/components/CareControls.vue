<template>
  <!-- F2 关怀设置：字号 / 高对比度 / 语种。
       使用原生 radio + checkbox 替代 el-radio-button / el-switch：
       ElementUI 2.15 的 label[role=radio] 未选中时丢失 aria-checked，el-switch 的
       role=switch 同样缺 aria-checked，且内嵌原生 input 触发 axe critical/serious。 -->
  <div class="care-controls" :class="'care-controls--' + layout" role="group" aria-label="无障碍与语言设置">
    <div class="care-row">
      <span v-if="layout === 'rows'" class="care-row-label">字号</span>
      <div class="care-seg" role="radiogroup" aria-label="字号大小">
        <label
          v-for="opt in fontScales"
          :key="opt.value"
          class="care-seg-item"
          :class="{ 'is-active': care.state.fontScale === opt.value }">
          <input
            type="radio"
            :name="groupName"
            :value="opt.value"
            :checked="care.state.fontScale === opt.value"
            @change="setFont(opt.value)">
          <span>{{ opt.label }}</span>
        </label>
      </div>
    </div>

    <div class="care-row">
      <span v-if="layout === 'rows'" class="care-row-label">高对比度</span>
      <span v-if="layout === 'rows'" class="care-switch-state" aria-hidden="true">
        {{ care.state.highContrast ? '已开启' : '已关闭' }}
      </span>
      <label class="care-switch">
        <input
          type="checkbox"
          :checked="care.state.highContrast"
          @change="setContrast($event.target.checked)">
        <span class="care-switch-track" aria-hidden="true"></span>
        <span class="sr-only">高对比度模式</span>
      </label>
    </div>

    <div class="care-row">
      <span v-if="layout === 'rows'" class="care-row-label">语种偏好</span>
      <el-select
        :value="care.state.languagePreference"
        size="mini"
        aria-label="语种偏好"
        @change="setLanguage">
        <el-option label="普通话" value="zh-CN" />
        <el-option label="粤语" value="yue-CN" />
      </el-select>
    </div>
  </div>
</template>

<script>
const FALLBACK_SCALES = [
  { value: 'normal', label: '标准' },
  { value: 'large', label: '大字' },
  { value: 'xlarge', label: '超大' }
]

export default {
  name: 'CareControls',
  props: {
    // inline：站点头部横向紧凑；rows：移动端抽屉内逐行带标签
    layout: { type: String, default: 'inline' }
  },
  computed: {
    care() {
      // 与 App.vue 一致：$care 未安装时返回同形兜底，避免渲染报错
      return this.$care || {
        state: { fontScale: 'normal', highContrast: false, languagePreference: 'zh-CN' }
      }
    },
    fontScales() {
      return (this.$care && this.$care.fontScales) || FALLBACK_SCALES
    },
    // 头部与抽屉同时渲染两组单选，name 需唯一，避免互相抢占选中态
    groupName() {
      return 'care-font-scale-' + this._uid
    }
  },
  methods: {
    setFont(value) {
      if (this.$care) this.$care.setFontScale(value)
    },
    setContrast(value) {
      if (this.$care) this.$care.setHighContrast(value)
    },
    setLanguage(value) {
      if (this.$care) this.$care.setLanguage(value)
    }
  }
}
</script>

<style scoped>
.care-controls { display: flex; align-items: center; gap: 10px; }
.care-row { display: flex; align-items: center; gap: 8px; }
.care-row-label { color: #5b6b84; font-size: 14px; }

/* ===== 字号分段控件 ===== */
.care-seg {
  display: inline-flex;
  border: 1px solid rgba(255, 255, 255, .36);
  border-radius: 4px;
  overflow: hidden;
}
.care-seg-item { position: relative; display: inline-flex; align-items: center; }
.care-seg-item + .care-seg-item { border-left: 1px solid rgba(255, 255, 255, .36); }
/* 原生 radio 铺满 label：保留键盘可达，视觉由 span 呈现 */
.care-seg-item input {
  position: absolute; inset: 0;
  width: 100%; height: 100%; margin: 0;
  opacity: 0; cursor: pointer;
}
.care-seg-item span {
  display: inline-flex; align-items: center; justify-content: center;
  min-height: 28px; padding: 0 10px;
  color: #fff; font-size: 12px; line-height: 1;
}
.care-seg-item.is-active { background: #fff; }
.care-seg-item.is-active span { color: #1A3C6E; font-weight: 600; }
.care-seg-item:focus-within { outline: 3px solid #ffd04b; outline-offset: 2px; z-index: 1; }

/* ===== 高对比度开关 ===== */
.care-switch { position: relative; display: inline-flex; align-items: center; cursor: pointer; }
.care-switch input {
  position: absolute; inset: 0;
  width: 100%; height: 100%; margin: 0;
  opacity: 0; cursor: pointer;
}
.care-switch-track {
  position: relative;
  width: 40px; height: 20px;
  border-radius: 10px;
  background: rgba(255, 255, 255, .32);
  transition: background-color .2s ease;
}
.care-switch-track::after {
  content: '';
  position: absolute; top: 2px; left: 2px;
  width: 16px; height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: transform .2s ease;
}
.care-switch input:checked + .care-switch-track { background: #C9A96E; }
.care-switch input:checked + .care-switch-track::after { transform: translateX(20px); }
.care-switch:focus-within .care-switch-track { outline: 3px solid #ffd04b; outline-offset: 2px; }
.care-switch-state { color: #5b6b84; font-size: 13px; }

/* ===== 移动端抽屉：白底逐行布局 ===== */
.care-controls--rows { flex-direction: column; align-items: stretch; gap: 0; }
.care-controls--rows .care-row { justify-content: space-between; gap: 10px; padding: 10px 0; }
.care-controls--rows .care-seg { border-color: #dcdfe6; }
.care-controls--rows .care-seg-item + .care-seg-item { border-left-color: #dcdfe6; }
.care-controls--rows .care-seg-item span { color: #44505f; }
.care-controls--rows .care-seg-item.is-active { background: #1A3C6E; }
.care-controls--rows .care-seg-item.is-active span { color: #fff; }
.care-controls--rows .care-seg-item:focus-within { outline-color: #1A3C6E; }
/* 轨道/滑块非文字对比度 >= 3:1 */
.care-controls--rows .care-switch-track { background: #5b6b84; }
.care-controls--rows .care-switch input:checked + .care-switch-track { background: #1A3C6E; }
.care-controls--rows .care-switch:focus-within .care-switch-track { outline-color: #1A3C6E; }

/* ===== 适老档：放大字号与热区 ===== */
html.care-large .care-seg-item span { min-height: 40px; padding: 0 14px; font-size: 16px; }
html.care-large .care-switch-track { width: 52px; height: 26px; border-radius: 13px; }
html.care-large .care-switch-track::after { width: 22px; height: 22px; }
html.care-large .care-switch input:checked + .care-switch-track::after { transform: translateX(26px); }
html.care-large .care-row-label,
html.care-large .care-switch-state { font-size: 17px; }

html.care-xlarge .care-seg-item span { min-height: 44px; padding: 0 16px; font-size: 18px; }
html.care-xlarge .care-switch-track { width: 58px; height: 30px; border-radius: 15px; }
html.care-xlarge .care-switch-track::after { width: 26px; height: 26px; }
html.care-xlarge .care-switch input:checked + .care-switch-track::after { transform: translateX(28px); }
html.care-xlarge .care-row-label,
html.care-xlarge .care-switch-state { font-size: 19px; }

/* ===== 高对比度：抽屉内改为黑白高对比 ===== */
html.care-high-contrast .care-controls--rows .care-row-label,
html.care-high-contrast .care-controls--rows .care-switch-state { color: #000; }
html.care-high-contrast .care-controls--rows .care-seg { border-color: #000; }
html.care-high-contrast .care-controls--rows .care-seg-item + .care-seg-item { border-left-color: #000; }
html.care-high-contrast .care-controls--rows .care-seg-item span { color: #000; }
html.care-high-contrast .care-controls--rows .care-seg-item.is-active { background: #000; }
html.care-high-contrast .care-controls--rows .care-seg-item.is-active span { color: #fff; }
</style>
