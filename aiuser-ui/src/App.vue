<template>
  <div id="app">
    <!-- 跳到主内容（无障碍） -->
    <a href="#main-content" class="skip-link" @click.prevent="focusMain">跳到主要内容</a>

    <!-- 登录/注册页面不显示导航栏 -->
    <div v-if="$route.path === '/login' || $route.path === '/register'">
      <router-view/>
    </div>
    <!-- 其他页面显示完整布局 -->
    <el-container v-else>
      <el-header class="app-header">
        <div class="header-content">
          <h1 class="logo">AI律师公众服务</h1>
          <el-menu
            :default-active="$route.path"
            class="nav-menu"
            mode="horizontal"
            router
            background-color="#0b1f4a"
            text-color="#d7e2f3"
            active-text-color="#ffd04b">
            <el-menu-item index="/consultation/submit">我要咨询</el-menu-item>
            <el-menu-item index="/ticket">我的工单</el-menu-item>
            <el-menu-item index="/services">服务导航</el-menu-item>
            <el-menu-item index="/consultation/history">咨询历史</el-menu-item>
          </el-menu>

          <!-- F2 适老化快捷控制 -->
          <div class="care-bar" role="group" aria-label="无障碍与语言设置">
            <el-radio-group
              :value="care.state.fontScale"
              size="mini"
              aria-label="字号大小"
              @input="onFontScale">
              <el-radio-button label="normal">标准</el-radio-button>
              <el-radio-button label="large">大字</el-radio-button>
              <el-radio-button label="xlarge">超大</el-radio-button>
            </el-radio-group>
            <el-tooltip content="高对比度主题，文字更清晰" placement="bottom">
              <el-switch
                :value="care.state.highContrast"
                active-color="#ffd04b"
                inactive-color="#3a5a8c"
                active-text=""
                aria-label="高对比度模式"
                @change="onHighContrast" />
            </el-tooltip>
            <el-tooltip content="语种偏好（F1，粤语语音能力二期开通）" placement="bottom">
              <el-select
                :value="care.state.languagePreference"
                size="mini"
                aria-label="语种偏好"
                @change="onLanguageChange">
                <el-option label="普通话" value="zh-CN" />
                <el-option label="粤语" value="yue-CN" />
              </el-select>
            </el-tooltip>

            <el-dropdown v-if="isLoggedIn" @command="handleCommand">
              <span class="el-dropdown-link">
                {{ userInfo && userInfo.nickName ? userInfo.nickName : '用户' }}
                <i class="el-icon-arrow-down el-icon--right"></i>
              </span>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
            <el-button v-else type="text" class="login-link" @click="toLogin">登录</el-button>
          </div>
        </div>
      </el-header>
      <el-main>
        <router-view ref="mainView"/>
      </el-main>
      <el-footer class="app-footer">
        <p>公共法律服务热线 12348 ｜ 政务服务便民热线 12345　© 2026 AI律师话务平台</p>
      </el-footer>
    </el-container>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'

export default {
  name: 'App',
  computed: {
    ...mapGetters(['isLoggedIn', 'userInfo']),
    care() {
      return this.$care ? this.$care.state : { fontScale: 'normal', highContrast: false, languagePreference: 'zh-CN' }
    }
  },
  watch: {
    // 登录成功后把本地适老/语种偏好同步到服务端
    isLoggedIn(val) {
      if (val && this.$care) {
        this.$care.syncPreferenceToServer()
      }
    }
  },
  created() {
    // $care 已在 main.js 安装时初始化（localStorage 恢复 + 应用根类）
  },
  methods: {
    onFontScale(val) { this.$care.setFontScale(val) },
    onHighContrast(val) { this.$care.setHighContrast(val) },
    onLanguageChange(val) { this.$care.setLanguage(val) },
    focusMain() {
      const el = document.getElementById('main-content')
      if (el) {
        el.setAttribute('tabindex', '-1')
        el.focus({ preventScroll: false })
      }
    },
    handleCommand(command) {
      if (command === 'logout') {
        this.$confirm('确定要退出登录吗?', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.$store.dispatch('logout').then(() => {
            this.$router.push('/login')
            this.$message.success('退出登录成功')
          })
        })
      } else if (command === 'profile') {
        this.$message.info('个人信息功能开发中')
      }
    },
    toLogin() {
      this.$router.push('/login')
    }
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  font-size: 14px;
}

#app {
  height: 100%;
}

.el-container {
  height: 100%;
}

/* 跳到主内容链接：默认隐藏，键盘聚焦时可见 */
.skip-link {
  position: absolute;
  left: -9999px;
  top: 0;
  z-index: 9999;
  padding: 10px 18px;
  background: #ffd04b;
  color: #0b1f4a;
  font-weight: 600;
  border-radius: 0 0 6px 0;
}
.skip-link:focus {
  left: 0;
  outline: 3px solid #ffd04b;
}

/* 统一可见焦点环（无障碍） */
a:focus-visible,
button:focus-visible,
.el-button:focus-visible,
.el-menu-item:focus-visible,
.el-radio-button:focus-within,
.el-input__inner:focus-visible {
  outline: 3px solid #ffd04b;
  outline-offset: 2px;
}

.app-header {
  background-color: #0b1f4a;
  color: #fff;
  padding: 0;
  height: 60px !important;
  line-height: 60px;
  box-shadow: 0 2px 8px rgba(11, 31, 74, 0.25);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 20px;
  gap: 16px;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  color: #fff;
  white-space: nowrap;
}

.nav-menu {
  border: none !important;
  flex: 1;
}

.el-menu--horizontal > .el-menu-item {
  height: 60px;
  line-height: 60px;
  border-bottom: none;
  font-size: 15px;
}
.el-menu--horizontal > .el-menu-item.is-active {
  border-bottom: 3px solid #ffd04b !important;
}

.care-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}
.care-bar .el-select { width: 92px; }

.el-dropdown-link {
  color: #fff;
  cursor: pointer;
}
.login-link { color: #ffd04b !important; font-size: 15px; }

.el-main {
  background-color: #f5f7fa;
  min-height: calc(100vh - 120px);
  padding: 0;
}

.app-footer {
  background-color: #0b1f4a;
  color: #aebdd6;
  text-align: center;
  height: 60px !important;
  line-height: 60px;
  font-size: 13px;
}

/* =========================================================
 * F2 适老化全局规则（html 根类驱动，覆盖全部 ElementUI 组件）
 * 标准 >=14px / 大字 >=18px / 超大 >=22px，热区 >=44px
 * ========================================================= */
html.care-large body { font-size: 18px; }
html.care-large .el-button { font-size: 17px; min-height: 44px; padding: 10px 20px; }
html.care-large .el-input__inner,
html.care-large .el-textarea__inner { font-size: 17px; min-height: 44px; }
html.care-large .el-input__inner { height: 44px; line-height: 44px; }
html.care-large .el-form-item__label,
html.care-large .el-radio,
html.care-large .el-checkbox,
html.care-large .el-select-dropdown__item,
html.care-large .el-menu-item,
html.care-large .el-dropdown-menu__item,
html.care-large .el-table,
html.care-large .el-pagination,
html.care-large .el-dialog__title,
html.care-large .el-message-box__message,
html.care-large .el-tag { font-size: 17px; }
html.care-large .el-menu--horizontal > .el-menu-item { height: 60px; line-height: 60px; }
html.care-large .app-header { height: auto !important; min-height: 64px; line-height: 64px; }
html.care-large .el-menu--horizontal > .el-menu-item { height: 64px; line-height: 64px; }
html.care-large .logo { font-size: 22px; }
html.care-large .el-radio-button__inner { min-height: 44px; padding: 10px 16px; font-size: 16px; }
html.care-large .app-footer { font-size: 15px; }

html.care-xlarge body { font-size: 22px; }
html.care-xlarge .el-button { font-size: 20px; min-height: 52px; padding: 12px 26px; }
html.care-xlarge .el-input__inner,
html.care-xlarge .el-textarea__inner { font-size: 20px; min-height: 52px; }
html.care-xlarge .el-input__inner { height: 52px; line-height: 52px; }
html.care-xlarge .el-form-item__label,
html.care-xlarge .el-radio,
html.care-xlarge .el-checkbox,
html.care-xlarge .el-select-dropdown__item,
html.care-xlarge .el-menu-item,
html.care-xlarge .el-dropdown-menu__item,
html.care-xlarge .el-table,
html.care-xlarge .el-pagination,
html.care-xlarge .el-dialog__title,
html.care-xlarge .el-message-box__message,
html.care-xlarge .el-tag { font-size: 20px; }
html.care-xlarge .app-header { height: auto !important; min-height: 72px; line-height: 72px; }
html.care-xlarge .el-menu--horizontal > .el-menu-item { height: 72px; line-height: 72px; }
html.care-xlarge .logo { font-size: 26px; }
html.care-xlarge .el-radio-button__inner { min-height: 48px; padding: 12px 20px; font-size: 19px; }
html.care-xlarge .app-footer { font-size: 17px; }
html.care-xlarge .header-content { flex-wrap: wrap; padding-top: 8px; padding-bottom: 8px; }

/* 高对比：保证文字/背景对比度 >= 4.5:1 */
html.care-high-contrast body { background: #fff; }
html.care-high-contrast .app-header,
html.care-high-contrast .app-footer { background: #000; }
html.care-high-contrast .nav-menu { background: #000 !important; }
html.care-high-contrast .el-menu-item { color: #fff !important; }
html.care-high-contrast .el-menu-item.is-active {
  color: #ffd04b !important;
  border-bottom: 3px solid #ffd04b !important;
}
html.care-high-contrast .el-main { background: #fff; }
html.care-high-contrast .el-card {
  border: 2px solid #000 !important;
  box-shadow: none !important;
}
html.care-high-contrast .el-button--default {
  border: 2px solid #000;
  color: #000;
  background: #fff;
}
html.care-high-contrast .el-button--primary {
  background: #0000b3;
  border-color: #0000b3;
  color: #fff;
}
html.care-high-contrast .el-input__inner,
html.care-high-contrast .el-textarea__inner {
  border: 2px solid #000;
  color: #000;
}
html.care-high-contrast .el-table th.el-table__cell {
  background: #e8e8e8 !important;
  color: #000;
}
html.care-high-contrast .el-table,
html.care-high-contrast .el-table td,
html.care-high-contrast .el-table th { color: #000; }
html.care-high-contrast .el-tag--info,
html.care-high-contrast .el-tag--success,
html.care-high-contrast .el-tag--warning,
html.care-high-contrast .el-tag--danger {
  background: #fff !important;
  color: #000 !important;
  border: 2px solid #000 !important;
}
</style>
