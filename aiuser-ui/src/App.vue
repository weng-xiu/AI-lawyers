<template>
  <div id="app">
    <!-- 登录/注册页面不显示导航栏 -->
    <div v-if="$route.path === '/login' || $route.path === '/register'">
      <router-view/>
    </div>
    <!-- 其他页面显示完整布局 -->
    <el-container v-else>
      <el-header class="app-header">
        <div class="header-content">
          <h1 class="logo">AI律师用户端</h1>
          <el-menu
            :default-active="$route.path"
            mode="horizontal"
            router
            background-color="#409EFF"
            text-color="#fff"
            active-text-color="#ffd04b">
            <el-menu-item index="/consultation/submit">提交咨询</el-menu-item>
            <el-menu-item index="/consultation/history">咨询历史</el-menu-item>
          </el-menu>
          <div class="user-info">
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
            <el-button v-else type="text" @click="toLogin">登录</el-button>
          </div>
        </div>
      </el-header>
      <el-main>
        <router-view/>
      </el-main>
      <el-footer class="app-footer">
        <p>© 2023 AI律师用户端 - 版权所有</p>
      </el-footer>
    </el-container>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'

export default {
  name: 'App',
  computed: {
    ...mapGetters(['isLoggedIn', 'userInfo'])
  },
  methods: {
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
        // 可以跳转到个人信息页面
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
}

#app {
  height: 100%;
}

.el-container {
  height: 100%;
}

.app-header {
  background-color: #409EFF;
  color: #fff;
  padding: 0;
  height: 60px !important;
  line-height: 60px;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.user-info {
  color: #fff;
}

.el-dropdown-link {
  color: #fff;
  cursor: pointer;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  color: #fff;
}

.el-menu {
  border: none;
}

.el-menu--horizontal > .el-menu-item {
  height: 60px;
  line-height: 60px;
  border-bottom: none;
}

.el-main {
  background-color: #f5f5f5;
  min-height: calc(100vh - 120px);
}

.app-footer {
  background-color: #f5f5f5;
  color: #666;
  text-align: center;
  height: 60px !important;
  line-height: 60px;
  font-size: 14px;
}
</style>0;
  padding: 0;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
}
</style>