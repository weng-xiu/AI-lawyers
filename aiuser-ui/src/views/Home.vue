<template>
  <div class="portal-page home-page">
    <!-- 主视觉：场景化第一屏，直接给出「咨询 / 热线」两条主路径 -->
    <section class="home-hero" aria-labelledby="home-title">
      <div class="hero-inner">
        <p class="hero-eyebrow">12348 公共法律服务 · 在线咨询</p>
        <h2 id="home-title" class="hero-title">法律问题，先问一声 AI 律师</h2>
        <p class="hero-sub">免费、7×24 小时在线。说清您的纠纷，AI 律师即时给出维权建议；复杂问题可转人工律师或就近公共法律服务机构。</p>
        <div class="hero-actions">
          <router-link class="hero-btn is-primary" to="/consultation/submit">开始免费咨询</router-link>
          <a class="hero-btn is-ghost" href="tel:12348" aria-label="拨打 12348 公共法律服务热线">拨打 12348 人工热线</a>
        </div>
        <ul class="hero-badges" aria-label="服务保障">
          <li><i class="el-icon-lock" aria-hidden="true"></i> 隐私加密保护</li>
          <li><i class="el-icon-s-check" aria-hidden="true"></i> 律师复核把关</li>
          <li><i class="el-icon-time" aria-hidden="true"></i> 7×24 小时在线</li>
          <li><i class="el-icon-wallet" aria-hidden="true"></i> 基础咨询免费</li>
        </ul>
      </div>
    </section>

    <!-- 场景化入口：咨询 / 查进度 / 找机构 / 法规查询 / 长辈专区 -->
    <section class="home-section" aria-labelledby="scene-title">
      <h2 id="scene-title" class="section-title">您想办什么？</h2>
      <p class="section-sub">选一个场景，直接开始；登录后可随时查看办理进度。</p>
      <div class="scene-grid">
        <router-link
          class="scene-card is-primary"
          to="/consultation/submit"
          aria-label="在线法律咨询：描述纠纷，AI 律师即时解答，可转人工">
          <span class="scene-icon"><i class="el-icon-chat-dot-round" aria-hidden="true"></i></span>
          <span class="scene-name">在线法律咨询</span>
          <span class="scene-desc">文字描述纠纷，AI 律师即时解答；复杂问题一键转人工律师。</span>
          <span class="scene-arrow">立即咨询 <i class="el-icon-arrow-right" aria-hidden="true"></i></span>
        </router-link>

        <router-link
          class="scene-card"
          to="/ticket"
          aria-label="查询工单进度：查看受理、办理、办结状态">
          <span class="scene-icon"><i class="el-icon-view" aria-hidden="true"></i></span>
          <span class="scene-name">查询工单进度</span>
          <span class="scene-desc">查看工单是否受理、由谁承办、办理到哪一步。</span>
          <span class="scene-arrow">查看进度 <i class="el-icon-arrow-right" aria-hidden="true"></i></span>
        </router-link>

        <router-link
          class="scene-card"
          to="/services"
          aria-label="找法律服务机构：法律援助、人民调解、公证机构地址电话">
          <span class="scene-icon"><i class="el-icon-office-building" aria-hidden="true"></i></span>
          <span class="scene-name">找法律服务机构</span>
          <span class="scene-desc">法律援助、人民调解、公证等机构的地址、电话与申请材料。</span>
          <span class="scene-arrow">查找机构 <i class="el-icon-arrow-right" aria-hidden="true"></i></span>
        </router-link>

        <a
          class="scene-card"
          href="https://flk.npc.gov.cn"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="法规查询：打开国家法律法规数据库（新窗口）">
          <span class="scene-icon"><i class="el-icon-reading" aria-hidden="true"></i></span>
          <span class="scene-name">法规查询</span>
          <span class="scene-desc">跳转国家法律法规数据库，查询法律、行政法规与地方性法规。</span>
          <span class="scene-arrow">查询法规 <i class="el-icon-top-right" aria-hidden="true"></i></span>
        </a>

        <router-link
          class="scene-card"
          to="/elderly"
          aria-label="长辈服务专区：大字大按钮，常用服务一步直达">
          <span class="scene-icon"><i class="el-icon-user" aria-hidden="true"></i></span>
          <span class="scene-name">长辈服务专区</span>
          <span class="scene-desc">大字、大按钮、热线优先，不会操作可拨打 12348 由专人协助。</span>
          <span class="scene-arrow">进入专区 <i class="el-icon-arrow-right" aria-hidden="true"></i></span>
        </router-link>
      </div>
    </section>

    <!-- 办事流程：四步进度可视化 -->
    <section class="home-section" aria-labelledby="flow-title">
      <h2 id="flow-title" class="section-title">办事流程</h2>
      <p class="section-sub">从提交到办结，每一步都可在“我的工单”中查看。</p>
      <ol class="flow-list">
        <li class="flow-step">
          <span class="flow-dot" aria-hidden="true">1</span>
          <span class="flow-name">描述问题</span>
          <span class="flow-desc">3 分钟填写表单，支持语音输入。</span>
        </li>
        <li class="flow-step">
          <span class="flow-dot" aria-hidden="true">2</span>
          <span class="flow-name">获得解答</span>
          <span class="flow-desc">AI 即时回复，复杂问题转人工。</span>
        </li>
        <li class="flow-step">
          <span class="flow-dot" aria-hidden="true">3</span>
          <span class="flow-name">跟踪进度</span>
          <span class="flow-desc">查看受理、承办与办结状态。</span>
        </li>
        <li class="flow-step">
          <span class="flow-dot" aria-hidden="true">4</span>
          <span class="flow-name">评价反馈</span>
          <span class="flow-desc">对服务打分，帮助我们改进。</span>
        </li>
      </ol>
    </section>

    <!-- 人工兜底：热线 + 长辈专区 -->
    <section class="home-section" aria-labelledby="help-title">
      <h2 id="help-title" class="section-title">需要人工帮助？</h2>
      <div class="help-card">
        <div class="help-main">
          <p class="help-title">拨打 12348 公共法律服务热线</p>
          <p class="help-desc">工作日专人接听；不会使用手机操作，可直接进入长辈服务专区，大字大按钮更好用。</p>
        </div>
        <div class="help-actions">
          <a class="help-btn is-primary" href="tel:12348" aria-label="拨打 12348 公共法律服务热线">拨打 12348</a>
          <router-link class="help-btn" to="/elderly">进入长辈专区</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { announce } from '@/utils/a11y'

export default {
  name: 'Home',
  created() {
    announce('欢迎使用 AI 律师公众服务，可选择在线咨询、查询进度、查找服务机构或法规查询', 'polite')
  }
}
</script>

<style scoped>
.home-page { padding-bottom: 72px; background: #f5f7fa; }

/* ===== 主视觉 ===== */
.home-hero {
  background: linear-gradient(135deg, #0b1f4a 0%, #1A3C6E 55%, #255A99 100%);
  color: #fff;
  padding: 52px 20px 46px;
}
.hero-inner { max-width: 1120px; margin: 0 auto; }
.hero-eyebrow { margin: 0 0 14px; color: #C9A96E; font-size: 14px; font-weight: 600; letter-spacing: 3px; }
.hero-title { margin: 0 0 16px; font-size: 42px; font-weight: 700; line-height: 1.25; letter-spacing: 1px; }
.hero-sub { margin: 0; max-width: 720px; color: #dbe6f5; font-size: 17px; line-height: 1.75; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 26px; }
.hero-btn {
  display: inline-flex; align-items: center; justify-content: center;
  min-height: 52px; padding: 0 28px; border-radius: 8px;
  font-size: 17px; font-weight: 600; text-decoration: none;
  transition: background-color .15s ease, border-color .15s ease, transform .15s ease;
}
.hero-btn.is-primary { background: #C9A96E; color: #0b1f4a; }
.hero-btn.is-primary:hover { background: #d8bb82; color: #0b1f4a; text-decoration: none; transform: translateY(-1px); }
.hero-btn.is-ghost { border: 2px solid rgba(255, 255, 255, .8); color: #fff; }
.hero-btn.is-ghost:hover { background: rgba(255, 255, 255, .12); color: #fff; text-decoration: none; }
.hero-btn:focus-visible { outline: 3px solid #ffd04b; outline-offset: 2px; }
.hero-badges {
  display: flex; flex-wrap: wrap; gap: 12px 26px;
  list-style: none; margin: 26px 0 0; padding: 0;
  color: #dbe6f5; font-size: 14px;
}
.hero-badges li { display: flex; align-items: center; gap: 6px; }
.hero-badges i { color: #C9A96E; font-size: 16px; }

/* ===== 通用区块 ===== */
.home-section { max-width: 1120px; margin: 0 auto; padding: 42px 20px 0; }
.section-title { margin: 0 0 6px; color: #0b1f4a; font-size: 26px; }
.section-sub { margin: 0 0 20px; color: #5b6b84; font-size: 15px; line-height: 1.6; }

/* ===== 场景卡：大字号 + 大热区 ===== */
.scene-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.scene-card.is-primary { grid-column: span 2; }
.scene-card {
  display: flex; flex-direction: column;
  min-height: 190px; padding: 22px 20px;
  background: #fff; border: 1px solid #dbe4f0; border-radius: 12px;
  text-decoration: none;
  transition: transform .15s ease, box-shadow .15s ease, border-color .15s ease;
}
.scene-card:hover { border-color: #1A3C6E; box-shadow: 0 8px 22px rgba(26, 60, 110, .14); transform: translateY(-2px); }
.scene-card:focus-visible { outline: 3px solid #1A3C6E; outline-offset: 2px; }
.scene-card.is-primary { border: 2px solid #1A3C6E; background: linear-gradient(160deg, #ffffff 0%, #eef4ff 100%); }
.scene-icon {
  display: flex; align-items: center; justify-content: center;
  width: 52px; height: 52px; margin-bottom: 14px;
  border-radius: 12px; background: #eef3fb; color: #1A3C6E; font-size: 26px;
}
.scene-card.is-primary .scene-icon { background: #1A3C6E; color: #fff; }
.scene-name { margin-bottom: 6px; color: #0b1f4a; font-size: 20px; font-weight: 700; }
.scene-desc { color: #5b6b84; font-size: 15px; line-height: 1.65; }
.scene-arrow { margin-top: auto; padding-top: 14px; color: #1A3C6E; font-size: 14px; font-weight: 600; }
.scene-arrow i { margin-left: 2px; }

/* ===== 办事流程：四步进度可视化 ===== */
.flow-list {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 24px; list-style: none; margin: 22px 0 0; padding: 0;
}
.flow-step { position: relative; padding-right: 4px; }
.flow-step:not(:last-child)::after {
  content: ''; position: absolute; top: 22px; right: -24px;
  width: 24px; height: 2px; background: #cfdaea;
}
.flow-dot {
  display: flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; margin-bottom: 12px;
  border: 2px solid #1A3C6E; border-radius: 50%;
  background: #fff; color: #1A3C6E;
  font-size: 18px; font-weight: 700;
  position: relative; z-index: 1;
}
.flow-step:first-child .flow-dot { background: #1A3C6E; color: #fff; }
.flow-name { display: block; margin-bottom: 4px; color: #0b1f4a; font-size: 17px; font-weight: 600; }
.flow-desc { color: #5b6b84; font-size: 14px; line-height: 1.6; }

/* ===== 人工兜底 ===== */
.help-card {
  display: flex; align-items: center; justify-content: space-between; gap: 20px;
  padding: 24px 26px;
  background: #fff; border: 1px solid #dbe4f0; border-radius: 12px;
}
.help-title { margin: 0 0 6px; color: #0b1f4a; font-size: 19px; font-weight: 700; }
.help-desc { margin: 0; color: #5b6b84; font-size: 15px; line-height: 1.7; }
.help-actions { display: flex; flex-wrap: wrap; gap: 12px; }
.help-btn {
  display: inline-flex; align-items: center; justify-content: center;
  min-height: 46px; padding: 0 22px;
  border: 1px solid #1A3C6E; border-radius: 8px;
  color: #1A3C6E; font-size: 16px; font-weight: 600; text-decoration: none;
}
.help-btn.is-primary { background: #1A3C6E; border-color: #1A3C6E; color: #fff; }
.help-btn:hover { background: #eef3fb; color: #0b1f4a; text-decoration: none; }
.help-btn.is-primary:hover { background: #255A99; color: #fff; }
.help-btn:focus-visible { outline: 3px solid #1A3C6E; outline-offset: 2px; }

/* ===== F2 适老档叠加 ===== */
html.care-large .hero-title { font-size: 46px; }
html.care-large .hero-sub { font-size: 19px; }
html.care-large .hero-btn { min-height: 58px; font-size: 19px; }
html.care-large .section-title { font-size: 30px; }
html.care-large .section-sub,
html.care-large .scene-desc,
html.care-large .flow-desc,
html.care-large .help-desc { font-size: 18px; }
html.care-large .scene-name { font-size: 23px; }
html.care-large .scene-arrow,
html.care-large .flow-name,
html.care-large .help-title { font-size: 19px; }
html.care-large .help-btn { min-height: 52px; font-size: 18px; }

html.care-xlarge .hero-title { font-size: 52px; }
html.care-xlarge .hero-sub { font-size: 21px; }
html.care-xlarge .hero-btn { min-height: 64px; padding: 0 32px; font-size: 21px; }
html.care-xlarge .section-title { font-size: 34px; }
html.care-xlarge .section-sub,
html.care-xlarge .scene-desc,
html.care-xlarge .flow-desc,
html.care-xlarge .help-desc { font-size: 20px; }
html.care-xlarge .scene-name { font-size: 26px; }
html.care-xlarge .scene-arrow,
html.care-xlarge .flow-name,
html.care-xlarge .help-title { font-size: 21px; }
html.care-xlarge .help-btn { min-height: 58px; font-size: 20px; }
html.care-xlarge .scene-card { min-height: 210px; }

/* ===== 高对比度 ===== */
html.care-high-contrast .home-page { background: #fff; }
html.care-high-contrast .home-hero { background: #000; }
html.care-high-contrast .hero-eyebrow { color: #ffd04b; }
html.care-high-contrast .hero-sub,
html.care-high-contrast .hero-badges { color: #fff; }
html.care-high-contrast .hero-badges i { color: #ffd04b; }
html.care-high-contrast .hero-btn.is-primary { background: #ffd04b; color: #000; }
html.care-high-contrast .hero-btn.is-ghost { border-color: #fff; color: #fff; }
html.care-high-contrast .section-title,
html.care-high-contrast .scene-name,
html.care-high-contrast .flow-name,
html.care-high-contrast .help-title { color: #000; }
html.care-high-contrast .section-sub,
html.care-high-contrast .scene-desc,
html.care-high-contrast .flow-desc,
html.care-high-contrast .help-desc { color: #000; }
html.care-high-contrast .scene-card,
html.care-high-contrast .help-card { border: 2px solid #000; background: #fff; }
html.care-high-contrast .scene-card.is-primary { border-width: 3px; }
html.care-high-contrast .scene-icon { background: #fff; border: 2px solid #000; color: #000; }
html.care-high-contrast .scene-card.is-primary .scene-icon { background: #000; color: #fff; border-color: #000; }
html.care-high-contrast .scene-arrow,
html.care-high-contrast .help-btn { color: #000; border-color: #000; }
html.care-high-contrast .help-btn.is-primary { background: #000; color: #fff; }
html.care-high-contrast .flow-dot { border-color: #000; background: #fff; color: #000; }
html.care-high-contrast .flow-step:first-child .flow-dot { background: #000; color: #fff; }
html.care-high-contrast .flow-step:not(:last-child)::after { background: #000; }

/* ===== 响应式：平板两列、手机单列 ===== */
@media (max-width: 900px) {
  .scene-grid { grid-template-columns: repeat(2, 1fr); }
  .flow-list { grid-template-columns: repeat(2, 1fr); gap: 22px 24px; }
  .flow-step:nth-child(2)::after { display: none; }
}
@media (max-width: 768px) {
  .home-hero { padding: 36px 16px 32px; }
  .hero-title { font-size: 30px; }
  .hero-sub { font-size: 16px; }
  .hero-btn { flex: 1 1 100%; }
  .home-section { padding: 30px 12px 0; }
  .section-title { font-size: 22px; }
  .scene-grid { grid-template-columns: 1fr; gap: 12px; }
  .scene-card.is-primary { grid-column: auto; }
  .scene-card { min-height: 0; padding: 18px 16px; }
  .help-card { flex-direction: column; align-items: stretch; padding: 20px 18px; }
  .help-actions { width: 100%; }
  .help-btn { flex: 1 1 100%; }
}
@media (max-width: 480px) {
  .flow-list { grid-template-columns: 1fr; gap: 18px; }
  .flow-step:not(:last-child)::after { display: none; }
}
</style>
