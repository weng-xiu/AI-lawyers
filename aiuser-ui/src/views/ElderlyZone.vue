<template>
  <div class="portal-page elderly-zone">
    <section class="page-wrap" aria-label="长辈服务专区">
      <div class="page-head">
        <h2 class="page-title">长辈服务专区</h2>
        <p class="page-sub">字大、按钮大，常用服务一步直达。不会用可拨打 12348，专人帮您办。</p>
      </div>

      <!-- 场景化入口：每张卡片即一项高频服务 -->
      <div class="zone-grid">
        <router-link
          class="zone-card"
          :to="{ path: '/consultation/submit' }"
          aria-label="我要咨询：文字或语音提问，AI 律师解答">
          <i class="el-icon-chat-dot-round zone-icon" aria-hidden="true"></i>
          <span class="zone-name">我要咨询</span>
          <span class="zone-desc">把您的问题告诉我们，AI 律师帮您解答</span>
        </router-link>

        <router-link
          class="zone-card"
          :to="{ path: '/ticket' }"
          aria-label="查询进度：查看我提交的工单办理到哪一步">
          <i class="el-icon-view zone-icon" aria-hidden="true"></i>
          <span class="zone-name">查询进度</span>
          <span class="zone-desc">看我提交的事情办到哪一步了</span>
        </router-link>

        <a
          class="zone-card"
          href="tel:12348"
          aria-label="电话求助：拨打 12348 人工热线，专人接听">
          <i class="el-icon-phone-outline zone-icon" aria-hidden="true"></i>
          <span class="zone-name">电话求助</span>
          <span class="zone-desc">拨打 12348，专人接听帮您办</span>
        </a>

        <router-link
          class="zone-card"
          :to="{ path: '/services' }"
          aria-label="找服务机构：法律援助、公证、调解机构的地址和电话">
          <i class="el-icon-office-building zone-icon" aria-hidden="true"></i>
          <span class="zone-name">找服务机构</span>
          <span class="zone-desc">法律援助、公证、调解机构的地址和电话</span>
        </router-link>

        <router-link
          class="zone-card"
          :to="{ path: '/consultation/history' }"
          aria-label="我的咨询：查看以前咨询过的问题和答案">
          <i class="el-icon-time zone-icon" aria-hidden="true"></i>
          <span class="zone-name">我的咨询</span>
          <span class="zone-desc">以前问过的问题和答案都在这里</span>
        </router-link>
      </div>

      <!-- 热线大字速达 -->
      <el-card class="hotline-card" shadow="never">
        <div class="hotline-item">
          <span class="hotline-label">公共法律服务热线</span>
          <a class="hotline-number" href="tel:12348" aria-label="拨打公共法律服务热线 12348">12348</a>
        </div>
        <el-divider direction="vertical"></el-divider>
        <div class="hotline-item">
          <span class="hotline-label">政务服务便民热线</span>
          <a class="hotline-number" href="tel:12345" aria-label="拨打政务服务便民热线 12345">12345</a>
        </div>
      </el-card>
    </section>
  </div>
</template>

<script>
import { announce } from '@/utils/a11y'

export default {
  name: 'ElderlyZone',
  created() {
    announce('已进入长辈服务专区，点击大按钮直达常用服务；需要人工帮助请拨打 12348', 'polite')
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
.page-head { margin-bottom: 22px; }
.page-title { font-size: 30px; color: #0b1f4a; margin: 0 0 8px; }
.page-sub { color: #3d4a5c; font-size: 18px; margin: 0; line-height: 1.6; }

/* ===== 场景化大卡片：适老基线即大字 + 热区≥44px ===== */
.zone-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 18px;
  margin-bottom: 22px;
}
.zone-card {
  display: flex; flex-direction: column; align-items: flex-start;
  background: #fff; border-radius: 12px; padding: 26px 24px;
  border: 2px solid #d9e2f0;
  text-decoration: none; cursor: pointer;
  min-height: 150px;
  transition: transform .15s ease, box-shadow .15s ease, border-color .15s ease;
}
.zone-card:hover,
.zone-card:focus {
  border-color: #1A3C6E;
  box-shadow: 0 6px 18px rgba(26, 60, 110, 0.16);
  transform: translateY(-2px);
  outline: none;
}
.zone-icon { font-size: 44px; color: #1A3C6E; margin-bottom: 12px; }
.zone-name { font-size: 24px; font-weight: 700; color: #0b1f4a; margin-bottom: 8px; }
.zone-desc { font-size: 17px; color: #5b6b84; line-height: 1.6; }

/* ===== 热线大字速达 ===== */
.hotline-card {
  border-radius: 12px;
  background: linear-gradient(135deg, #0b1f4a 0%, #255A99 100%);
}
.hotline-card >>> .el-card__body {
  display: flex; align-items: center; justify-content: space-around;
  flex-wrap: wrap; gap: 12px; padding: 26px 20px;
}
.hotline-item { text-align: center; }
.hotline-label { display: block; color: #c9d8ef; font-size: 17px; margin-bottom: 6px; }
.hotline-number { font-size: 40px; font-weight: 700; color: #ffd04b; text-decoration: none; letter-spacing: 2px; }
.hotline-number:focus, .hotline-number:hover { color: #ffe08a; text-decoration: underline; }
.hotline-card .el-divider--vertical { height: 44px; }
@media (max-width: 600px) {
  .hotline-card .el-divider--vertical { display: none; }
}

/* ===== F2 适老档叠加（专区基线已大字，关怀模式再放大一档） ===== */
html.care-large .page-title { font-size: 34px; }
html.care-large .page-sub { font-size: 20px; }
html.care-large .zone-name { font-size: 28px; }
html.care-large .zone-desc { font-size: 19px; }
html.care-large .zone-icon { font-size: 50px; }
html.care-large .hotline-number { font-size: 46px; }

html.care-xlarge .page-title { font-size: 38px; }
html.care-xlarge .page-sub { font-size: 23px; }
html.care-xlarge .zone-name { font-size: 32px; }
html.care-xlarge .zone-desc { font-size: 21px; }
html.care-xlarge .zone-card { min-height: 180px; }
html.care-xlarge .zone-icon { font-size: 56px; }
html.care-xlarge .hotline-number { font-size: 52px; }

/* ===== 高对比度 ===== */
html.care-high-contrast .portal-page { background: #fff; }
html.care-high-contrast .page-title,
html.care-high-contrast .zone-name { color: #000; }
html.care-high-contrast .page-sub,
html.care-high-contrast .zone-desc { color: #000; }
html.care-high-contrast .zone-card {
  background: #fff; border-color: #000;
}
html.care-high-contrast .zone-card:hover,
html.care-high-contrast .zone-card:focus {
  border-color: #000; box-shadow: none; outline: 3px solid #ffd04b;
}
html.care-high-contrast .zone-icon { color: #000; }
html.care-high-contrast .hotline-card { background: #000; border: 3px solid #ffd04b; }
</style>
