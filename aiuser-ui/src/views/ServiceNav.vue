<template>
  <div class="portal-page service-nav">
    <main id="main-content" role="main" class="page-wrap" aria-live="polite">
      <div class="page-head">
        <h2 class="page-title">法律服务导航</h2>
        <p class="page-sub">查询法律援助、人民调解、公证、12345 转办等公共法律服务机构及办事指南</p>
      </div>

      <!-- 热线速达 -->
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
        <el-divider direction="vertical"></el-divider>
        <div class="hotline-item">
          <a href="https://gd.12348.gov.cn" target="_blank" rel="noopener noreferrer" class="portal-link">
            广东法律服务网 <i class="el-icon-top-right"></i>
          </a>
        </div>
      </el-card>

      <!-- 筛选 -->
      <el-form :inline="true" class="filter-form" size="medium" @submit.native.prevent>
        <el-form-item label="服务条线">
          <el-select v-model="query.externalType" placeholder="全部条线" clearable style="width: 190px" @change="handleSearch">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="机构名称">
          <el-input v-model="query.orgName" placeholder="输入机构名称关键字" clearable style="width: 220px" @keyup.enter.native="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="big-btn" icon="el-icon-search" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>

      <div v-loading="loading">
        <div v-if="orgList.length === 0 && !loading" class="empty-box" role="status">
          <i class="el-icon-office-building"></i>
          <p>未找到符合条件的服务机构</p>
          <p class="empty-tip">可拨打 <a href="tel:12348">12348</a> 公共法律服务热线获得帮助</p>
        </div>

        <el-card v-for="org in orgList" :key="org.orgId" class="org-card" shadow="hover">
          <div class="org-head">
            <h3 class="org-name">{{ org.orgName }}</h3>
            <el-tag size="medium" type="primary" effect="dark">{{ typeText(org.externalType) }}</el-tag>
          </div>

          <ul class="org-meta">
            <li v-if="regionText(org)">
              <i class="el-icon-location-outline meta-icon"></i>
              <span>{{ regionText(org) }}{{ org.address || '' }}</span>
            </li>
            <li v-if="org.contactPhone">
              <i class="el-icon-phone-outline meta-icon"></i>
              <a :href="'tel:' + org.contactPhone" class="org-phone" :aria-label="'拨打电话' + org.contactPhone">{{ org.contactPhone }}</a>
              <span v-if="org.contactPerson" class="contact-person">（{{ org.contactPerson }}）</span>
            </li>
            <li v-if="org.serviceHours">
              <i class="el-icon-time meta-icon"></i>
              <span>{{ org.serviceHours }}</span>
            </li>
          </ul>

          <div v-if="org.applyMaterials" class="materials-box">
            <div class="materials-title"><i class="el-icon-document-checked"></i> 申请材料 / 办事须知</div>
            <p>{{ org.applyMaterials }}</p>
          </div>
        </el-card>
      </div>
    </main>
  </div>
</template>

<script>
import { serviceOrgs } from '@/api/portal'

export default {
  name: 'ServiceNav',
  data() {
    return {
      loading: false,
      orgList: [],
      query: { externalType: '', orgName: '' },
      typeOptions: [
        { value: 'HOTLINE_12345', label: '12345政务热线' },
        { value: 'JUSTICE_BUREAU', label: '司法局' },
        { value: 'LEGAL_AID', label: '法律援助中心' },
        { value: 'MEDIATION', label: '人民调解组织' },
        { value: 'NOTARY', label: '公证机构' },
        { value: 'COURT', label: '人民法院' },
        { value: 'OTHER', label: '其他机构' }
      ]
    }
  },
  created() {
    this.loadOrgs()
  },
  methods: {
    loadOrgs() {
      this.loading = true
      const params = {}
      if (this.query.externalType) params.externalType = this.query.externalType
      if (this.query.orgName) params.orgName = this.query.orgName
      serviceOrgs(params).then(res => {
        this.orgList = res.data || []
      }).finally(() => { this.loading = false })
    },
    handleSearch() {
      this.loadOrgs()
    },
    typeText(t) {
      const hit = this.typeOptions.find(o => o.value === t)
      return hit ? hit.label : (t || '公共服务机构')
    },
    regionText(org) {
      return [org.province, org.city, org.district].filter(Boolean).join(' / ')
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

.hotline-card {
  margin-bottom: 18px; border-radius: 8px;
  background: linear-gradient(135deg, #0b1f4a 0%, #255A99 100%);
}
.hotline-card >>> .el-card__body {
  display: flex; align-items: center; justify-content: space-around;
  flex-wrap: wrap; gap: 12px; padding: 22px 20px;
}
.hotline-item { text-align: center; }
.hotline-label { display: block; color: #c9d8ef; font-size: 14px; margin-bottom: 4px; }
.hotline-number { font-size: 30px; font-weight: 700; color: #ffd04b; text-decoration: none; letter-spacing: 2px; }
.hotline-number:focus, .hotline-number:hover { color: #ffe08a; text-decoration: underline; }
.portal-link { color: #fff; font-size: 16px; text-decoration: underline; }
.hotline-card .el-divider--vertical { height: 36px; }
@media (max-width: 600px) {
  .hotline-card .el-divider--vertical { display: none; }
}

.filter-form { background: #fff; padding: 16px 16px 0; border-radius: 8px; margin-bottom: 16px; }
.big-btn { min-height: 40px; padding: 9px 22px; }

.org-card { margin-bottom: 14px; border-radius: 8px; }
.org-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 10px; }
.org-name { font-size: 17px; color: #0b1f4a; margin: 0; }
.org-meta { list-style: none; padding: 0; margin: 0; }
.org-meta li { display: flex; align-items: flex-start; gap: 8px; font-size: 14px; color: #3d4a5c; line-height: 1.9; }
.meta-icon { margin-top: 5px; color: #255A99; }
.org-phone { color: #255A99; font-weight: 600; font-size: 16px; text-decoration: none; }
.org-phone:hover, .org-phone:focus { text-decoration: underline; }
.contact-person { color: #8a94a6; }

.materials-box { margin-top: 10px; padding: 10px 14px; background: #f3f7fc; border-radius: 6px; border-left: 3px solid #255A99; }
.materials-title { font-size: 14px; font-weight: 600; color: #0b1f4a; margin-bottom: 4px; }
.materials-box p { margin: 0; font-size: 14px; color: #3d4a5c; line-height: 1.7; white-space: pre-line; }

.empty-box { text-align: center; padding: 60px 0; color: #8a94a6; }
.empty-box i { font-size: 56px; color: #c4cedd; }
.empty-box p { margin: 12px 0 4px; font-size: 15px; }
.empty-tip { font-size: 14px; }
.empty-tip a { color: #255A99; font-weight: 600; }

/* ===== F2 适老化 ===== */
html.care-large .page-title { font-size: 28px; }
html.care-large .page-sub,
html.care-large .org-meta li,
html.care-large .materials-box p { font-size: 17px; }
html.care-large .org-name { font-size: 20px; }
html.care-large .org-phone { font-size: 19px; }
html.care-large .hotline-number { font-size: 36px; }
html.care-large .big-btn { min-height: 48px; font-size: 17px; }
html.care-large .el-input__inner,
html.care-large .el-input.is-medium .el-input__inner,
html.care-large .el-select .el-input.is-medium .el-input__inner { height: 44px; line-height: 44px; }

html.care-xlarge .page-title { font-size: 32px; }
html.care-xlarge .page-sub,
html.care-xlarge .org-meta li,
html.care-xlarge .materials-box p { font-size: 20px; }
html.care-xlarge .org-name { font-size: 24px; }
html.care-xlarge .org-phone { font-size: 22px; }
html.care-xlarge .hotline-number { font-size: 44px; }
html.care-xlarge .big-btn { min-height: 52px; font-size: 19px; padding: 12px 26px; }
html.care-xlarge .el-input__inner,
html.care-xlarge .el-select .el-input__inner { height: 48px !important; line-height: 48px !important; font-size: 18px; }

html.care-high-contrast .portal-page { background: #fff; }
html.care-high-contrast .page-title,
html.care-high-contrast .org-name { color: #000; }
html.care-high-contrast .page-sub,
html.care-high-contrast .org-meta li,
html.care-high-contrast .materials-box p { color: #000; }
html.care-high-contrast .org-card,
html.care-high-contrast .filter-form { border: 2px solid #000; }
html.care-high-contrast .org-phone,
html.care-high-contrast .empty-tip a,
html.care-high-contrast .portal-link { color: #0000EE; text-decoration: underline; }
html.care-high-contrast .materials-box { background: #fff; border: 2px solid #000; }
html.care-high-contrast .hotline-card { background: #000; border: 3px solid #ffd04b; }
</style>
