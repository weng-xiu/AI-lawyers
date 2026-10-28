<template>
  <div id="app">
    <!-- 跳到主内容（无障碍） -->
    <a href="#main-content" class="skip-link" @click.prevent="focusMain">跳到主要内容</a>

    <!-- F2 无障碍：全局 aria-live 播报区（视觉隐藏，仅供读屏器）。
         咨询结果/排队状态/错误提示经 utils/a11y.announce() 写入此处播报 -->
    <div id="a11y-announcer" class="sr-only" aria-atomic="false">
      <span data-role="polite" aria-live="polite"></span>
      <span data-role="assertive" aria-live="assertive"></span>
    </div>

    <!-- F2 语音求助：固定悬浮入口，一键转人工热线（兜底，ASR 语音提问待 P3 公众端 ASR 接口） -->
    <a class="voice-help" href="tel:12348" aria-label="语音求助，拨打 12348 公共法律服务人工热线">
      <i class="el-icon-phone-outline"></i>
      <span class="voice-help-text">语音求助</span>
    </a>

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
            class="nav-menu desktop-only"
            mode="horizontal"
            router
            background-color="#1A3C6E"
            text-color="#d7e2f3"
            active-text-color="#C9A96E">
            <el-menu-item
              v-for="item in navItems"
              :key="item.path"
              :index="item.path"
              :class="{ 'elderly-entry': item.elderly }">{{ item.label }}</el-menu-item>
          </el-menu>

          <!-- F2 适老化快捷控制（CareControls：原生控件保证 ARIA 正确） -->
          <div class="care-bar desktop-only">
            <care-controls />

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

          <!-- U6 移动端：折叠菜单入口（<=1080px 显示） -->
          <button
            type="button"
            class="mobile-nav-toggle"
            aria-label="打开导航与设置"
            aria-controls="mobile-nav-panel"
            :aria-expanded="mobileNavOpen ? 'true' : 'false'"
            @click="toggleMobileNav">
            <i :class="mobileNavOpen ? 'el-icon-close' : 'el-icon-menu'"></i>
          </button>
        </div>
      </el-header>

      <!-- U6 移动端导航抽屉：窄屏下收纳全部菜单与适老设置，避免顶部横向溢出 -->
      <el-drawer
        title="导航与设置"
        :visible.sync="mobileNavOpen"
        direction="rtl"
        size="82%"
        custom-class="mobile-nav-drawer">
        <nav id="mobile-nav-panel" aria-label="移动端导航">
          <el-menu
            :default-active="$route.path"
            mode="vertical"
            router
            background-color="#1A3C6E"
            text-color="#d7e2f3"
            active-text-color="#C9A96E"
            @select="closeMobileNav">
            <el-menu-item
              v-for="item in navItems"
              :key="item.path"
              :index="item.path"
              :class="{ 'elderly-entry': item.elderly }">{{ item.label }}</el-menu-item>
          </el-menu>

          <div class="mobile-care">
            <care-controls layout="rows" />
            <div class="mobile-care-row">
              <span class="mobile-care-label">账号</span>
              <template v-if="isLoggedIn">
                <span class="mobile-user">{{ userInfo && userInfo.nickName ? userInfo.nickName : '用户' }}</span>
                <el-button type="text" @click="handleCommand('logout')">退出登录</el-button>
              </template>
              <el-button v-else type="text" class="login-link" @click="toLogin">登录</el-button>
            </div>
          </div>
        </nav>
      </el-drawer>
      <el-main>
        <main id="main-content" role="main" tabindex="-1" class="main-region">
          <router-view ref="mainView"/>
        </main>
      </el-main>
      <el-footer class="app-footer">
        <p>公共法律服务热线 12348 ｜ 政务服务便民热线 12345　© 2026 AI律师话务平台</p>
      </el-footer>
    </el-container>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import CareControls from '@/components/CareControls'

export default {
  name: 'App',
  components: { CareControls },
  data() {
    return {
      // U6 移动端：桌面横向菜单与抽屉菜单共用同一份导航配置
      mobileNavOpen: false,
      navItems: [
        { path: '/', label: '首页' },
        { path: '/consultation/submit', label: '我要咨询' },
        { path: '/ticket', label: '我的工单' },
        { path: '/channels', label: '渠道绑定' },
        { path: '/services', label: '服务导航' },
        { path: '/consultation/history', label: '咨询历史' },
        { path: '/elderly', label: '长辈专区', elderly: true }
      ]
    }
  },
  computed: {
    ...mapGetters(['isLoggedIn', 'userInfo'])
  },
  watch: {
    // 登录成功后把本地适老/语种偏好同步到服务端
    isLoggedIn(val) {
      if (val && this.$care) {
        this.$care.syncPreferenceToServer()
      }
      // 登录后用户菜单才渲染，需重新纠正 ARIA
      this.$nextTick(this.fixDropdownAria)
    },
    // 路由变化后收起移动端抽屉
    $route() {
      this.mobileNavOpen = false
    }
  },
  mounted() {
    this.$nextTick(this.fixDropdownAria)
  },
  methods: {
    toggleMobileNav() { this.mobileNavOpen = !this.mobileNavOpen },
    closeMobileNav() { this.mobileNavOpen = false },
    // ElementUI 2.15 的 el-dropdown 把 aria-haspopup 固定写成非法值 "list"，
    // 导致 axe critical(aria-valid-attr-value)；这里纠正为合法的 "menu"
    fixDropdownAria() {
      const trigger = this.$el.querySelector('.el-dropdown-link')
      if (trigger) trigger.setAttribute('aria-haspopup', 'menu')
    },
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
  background: var(--color-brand-gold, #C9A96E);
  color: var(--color-brand-deep, #1A3C6E);
  font-weight: 600;
  border-radius: 0 0 6px 0;
}
.skip-link:focus {
  left: 0;
  outline: 3px solid #ffd04b;
}

/* sr-only：视觉隐藏但读屏器可感知（aria-live 播报区） */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* 主内容区去除聚焦轮廓（由 skip-link 编程聚焦时不显示突兀黑框） */
.main-region { outline: none; }

/* F2 语音求助悬浮入口：热区 >= 44px，固定右下角 */
.voice-help {
  position: fixed;
  right: 20px;
  bottom: 76px;
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 48px;
  padding: 0 18px;
  background: #c8372d;
  color: #fff;
  border-radius: 24px;
  box-shadow: 0 4px 14px rgba(200, 55, 45, 0.4);
  text-decoration: none;
  font-size: 16px;
  font-weight: 600;
}
.voice-help:hover { color: #fff; text-decoration: none; background: #d9443a; }
.voice-help .el-icon-phone-outline { font-size: 20px; }
html.care-large .voice-help { min-height: 56px; font-size: 19px; padding: 0 24px; }
html.care-xlarge .voice-help { min-height: 64px; font-size: 22px; padding: 0 30px; border-radius: 32px; }
@media (max-width: 600px) {
  .voice-help-text { display: none; }
  .voice-help { width: 52px; height: 52px; justify-content: center; padding: 0; }
}

/* 统一可见焦点环（无障碍） */
a:focus-visible,
button:focus-visible,
.el-button:focus-visible,
.el-menu-item:focus-visible,
.el-input__inner:focus-visible {
  outline: 3px solid #ffd04b;
  outline-offset: 2px;
}

.app-header {
  background-color: var(--color-brand-deep, #1A3C6E);
  color: #fff;
  padding: 0;
  height: 60px !important;
  line-height: 60px;
  box-shadow: 0 2px 8px rgba(26, 60, 110, 0.25);
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
  border-bottom: 3px solid var(--color-brand-gold, #C9A96E) !important;
}
/* V6 长辈专区入口：法治金高亮，与普通菜单区分 */
.el-menu--horizontal > .el-menu-item.elderly-entry {
  color: var(--color-brand-gold, #C9A96E) !important;
  font-weight: 600;
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
.login-link { color: var(--color-brand-gold, #C9A96E) !important; font-size: 15px; }

.el-main {
  background-color: #f5f7fa;
  min-height: calc(100vh - 120px);
  padding: 0;
}

.app-footer {
  background-color: var(--color-brand-deep, #1A3C6E);
  color: #aebdd6;
  text-align: center;
  height: 60px !important;
  line-height: 60px;
  font-size: 13px;
}

/* 无障碍对比度修复：主题 $--color-info(#8C8C8C) 用于字数统计/提示文案，
   在白/浅灰底上仅 3.0~3.4:1；主题 CSS 后置导致同优先级覆盖失效，故显式提高优先级 */
.el-textarea .el-input__count,
.el-input .el-input__count,
.el-input .el-input__count .el-input__count-inner { color: #5b6b84 !important; }
.el-alert.el-alert--info .el-alert__title,
.el-alert.el-alert--info .el-alert__description { color: #44505f; }

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

/* =========================================================
 * U6 响应式与移动端：公众端移动优先（断点 1080 / 768 / 480）
 * 桌面导航在窄屏收纳进抽屉，页面表单/卡片改为单列大热区
 * ========================================================= */
.mobile-nav-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex: none;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 6px;
  background: transparent;
  color: #fff;
  font-size: 20px;
  cursor: pointer;
}
.mobile-nav-toggle:focus-visible { outline: 3px solid #ffd04b; outline-offset: 2px; }

.mobile-nav-drawer .el-drawer__body { padding: 0; }
.mobile-nav-drawer .el-menu { border-right: none; }
.mobile-nav-drawer .el-menu-item { height: 52px; line-height: 52px; font-size: 16px; }
.mobile-care { padding: 16px 20px 24px; border-top: 1px solid #eef1f6; }
.mobile-care-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 0; }
.mobile-care-label { color: #5b6b84; font-size: 14px; }
.mobile-care .el-select { width: 110px; }
.mobile-user { color: #0b1f4a; font-size: 15px; margin-right: 8px; }

@media (max-width: 1080px) {
  .desktop-only { display: none !important; }
  .mobile-nav-toggle { display: inline-flex; }
  .header-content { padding: 0 12px; gap: 10px; }
  .logo { font-size: 17px; overflow: hidden; text-overflow: ellipsis; }
}

@media (max-width: 768px) {
  /* 弹窗/消息框占满可用宽度，避免百分比宽度在窄屏过窄 */
  .el-dialog:not(.is-fullscreen) {
    width: calc(100vw - 32px) !important;
    max-width: 520px;
    margin-top: 8vh !important;
  }
  .el-message-box { width: calc(100vw - 32px) !important; max-width: 420px; }

  /* 行内查询表单竖排，控件占满整行 */
  .el-form--inline .el-form-item { display: block; margin-right: 0; width: 100%; }
  .el-form--inline .el-form-item__content { width: 100%; display: block; margin-left: 0 !important; }
  .el-form--inline .el-form-item__content .el-input,
  .el-form--inline .el-form-item__content .el-select,
  .el-form--inline .el-form-item__content .el-date-editor { width: 100% !important; }

  /* 咨询提交：标签改为顶部对齐，避免 100px 固定标签挤压输入区 */
  .submit-question .el-form-item__label { width: auto !important; float: none; text-align: left; padding: 0 0 6px; line-height: 1.5; }
  .submit-question .el-form-item__content { margin-left: 0 !important; }

  /* 咨询历史筛选区：三列改为单列 */
  .consultation-history .filter-container .el-col { width: 100% !important; margin-bottom: 8px; }

  /* 渠道绑定：表单控件占满整行，渠道卡片纵向折行 */
  .my-channels .bind-form-card .el-select,
  .my-channels .bind-form-card .el-input { width: 100% !important; }
  .my-channels .channel-row { flex-wrap: wrap; }
  .my-channels .channel-info { flex: 1 1 180px; }

  /* 分页在窄屏允许换行 */
  .el-pagination { white-space: normal; }
  .el-pagination .el-pagination__sizes .el-input { width: 90px; }

  .el-footer { height: auto !important; line-height: 1.6 !important; padding: 10px 12px; }
  .app-footer p { line-height: 1.6; }
}

@media (max-width: 480px) {
  .logo { font-size: 15px; }
  .voice-help { right: 14px; bottom: 70px; }
}
</style>
