<template>
  <div class="app-container home">
    <!-- 顶部欢迎区域 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <div class="welcome-section">
          <h1>AI法律咨询系统</h1>
          <p>智能法律咨询，为您提供专业的法律建议</p>
        </div>
      </el-col>
    </el-row>

    <!-- 主要功能区 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 左侧：法律咨询 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <legal-consultation ref="legalConsultation"></legal-consultation>
      </el-col>
      
      <!-- 右侧：咨询历史 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <consultation-history ref="consultationHistory"></consultation-history>
      </el-col>
    </el-row>

    <!-- 底部：系统信息 -->
    <el-divider />
    <el-row :gutter="20">
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="update-log">
          <div slot="header" class="clearfix">
            <span>系统功能</span>
          </div>
          <div class="body">
            <p>
              <i class="el-icon-s-promotion"></i> 智能法律咨询
            </p>
            <p>
              <i class="el-icon-s-data"></i> 咨询记录管理
            </p>
            <p>
              <i class="el-icon-s-claim"></i> 法律知识库
            </p>
            <p>
              <i class="el-icon-s-opportunity"></i> 咨询分类管理
            </p>
            <p>
              <i class="el-icon-setting"></i> 系统配置
            </p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="update-log">
          <div slot="header" class="clearfix">
            <span>使用指南</span>
          </div>
          <div class="body">
            <p>1. 选择问题分类</p>
            <p>2. 详细描述法律问题</p>
            <p>3. 提交咨询获取AI建议</p>
            <p>4. 查看历史咨询记录</p>
            <p>5. 管理个人咨询数据</p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="update-log">
          <div slot="header" class="clearfix">
            <span>关于系统</span>
          </div>
          <div class="body">
            <p>
              <b>当前版本:</b> <span>v{{ version }}</span>
            </p>
            <p>
              <el-tag type="danger">&yen;免费开源</el-tag>
            </p>
            <p>
              <i class="el-icon-user-solid"></i> 技术支持：AI律师团队
            </p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import LegalConsultation from "@/components/LegalConsultation";
import ConsultationHistory from "@/components/ConsultationHistory";

export default {
  name: "Index",
  components: {
    LegalConsultation,
    ConsultationHistory
  },
  data() {
    return {
      // 版本号
      version: "1.0.0"
    }
  },
  methods: {
    goTarget(href) {
      window.open(href, "_blank")
    }
  }
}
</script>

<style scoped lang="scss">
.home {
  .welcome-section {
    text-align: center;
    padding: 30px 0;
    background: linear-gradient(135deg, #409EFF, #36A3F5);
    color: white;
    border-radius: 8px;
    margin-bottom: 20px;
    
    h1 {
      font-size: 28px;
      margin: 0 0 10px 0;
      font-weight: 500;
    }
    
    p {
      font-size: 16px;
      margin: 0;
      opacity: 0.9;
    }
  }
  
  blockquote {
    padding: 10px 20px;
    margin: 0 0 20px;
    font-size: 17.5px;
    border-left: 5px solid #eee;
  }
  hr {
    margin-top: 20px;
    margin-bottom: 20px;
    border: 0;
    border-top: 1px solid #eee;
  }
  .col-item {
    margin-bottom: 20px;
  }

  ul {
    padding: 0;
    margin: 0;
  }

  font-family: "open sans", "Helvetica Neue", Helvetica, Arial, sans-serif;
  font-size: 13px;
  color: #676a6c;
  overflow-x: hidden;

  ul {
    list-style-type: none;
  }

  h4 {
    margin-top: 0px;
  }

  h2 {
    margin-top: 10px;
    font-size: 26px;
    font-weight: 100;
  }

  p {
    margin-top: 10px;

    b {
      font-weight: 700;
    }
  }

  .update-log {
    ol {
      display: block;
      list-style-type: decimal;
      margin-block-start: 1em;
      margin-block-end: 1em;
      margin-inline-start: 0;
      margin-inline-end: 0;
      padding-inline-start: 40px;
    }
  }
}
</style>