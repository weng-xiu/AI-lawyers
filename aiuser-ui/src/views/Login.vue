<template>
  <div class="login-container">
    <el-card class="login-card">
      <div slot="header" class="login-header">
        <h2>用户登录</h2>
      </div>
      <el-form ref="loginForm" :model="loginForm" :rules="loginRules" label-width="0px">
        <el-form-item prop="username">
          <el-input 
            v-model="loginForm.username" 
            type="text" 
            auto-complete="off" 
            placeholder="用户名"
            prefix-icon="el-icon-user">
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input 
            v-model="loginForm.password" 
            type="password" 
            auto-complete="off" 
            placeholder="密码"
            prefix-icon="el-icon-lock"
            @keyup.enter.native="handleLogin">
          </el-input>
        </el-form-item>
        <el-form-item v-if="captchaEnabled" prop="code">
          <el-input 
            v-model="loginForm.code" 
            auto-complete="off" 
            placeholder="验证码"
            style="width: 63%"
            @keyup.enter.native="handleLogin">
          </el-input>
          <div class="login-code">
            <img :src="codeUrl" class="login-code-img" @click="getCode">
          </div>
        </el-form-item>
        <el-form-item style="width:100%;">
          <el-button 
            :loading="loading" 
            size="medium" 
            type="primary" 
            style="width:100%;"
            @click.native.prevent="handleLogin">
            <span v-if="!loading">登 录</span>
            <span v-else>登 录 中...</span>
          </el-button>
        </el-form-item>
        <el-form-item style="width:100%;">
          <div class="login-register">
            <span>还没有账号？</span>
            <el-link type="primary" @click="toRegister">立即注册</el-link>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { getCodeImg, login } from '@/api/user'

export default {
  name: 'Login',
  data() {
    return {
      codeUrl: '',
      cookiePassword: '',
      loginForm: {
        username: 'testuser01',
        password: 'admin123',
        code: '',
        uuid: ''
      },
      loginRules: {
        username: [
          { required: true, trigger: 'blur', message: '用户名不能为空' }
        ],
        password: [
          { required: true, trigger: 'blur', message: '密码不能为空' }
        ],
        code: [{ required: true, trigger: 'change', message: '验证码不能为空' }]
      },
      loading: false,
      redirect: undefined,
      captchaEnabled: true
    }
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect
      },
      immediate: true
    }
  },
  created() {
    this.getCode()
    this.getCookie()
  },
  methods: {
    getCode() {
      getCodeImg().then(res => {
        this.captchaEnabled = res.captchaEnabled === undefined ? true : res.captchaEnabled
        if (this.captchaEnabled) {
          this.codeUrl = 'data:image/gif;base64,' + res.img
          this.loginForm.uuid = res.uuid
        }
      })
    },
    getCookie() {
      const username = localStorage.getItem('username')
      const password = localStorage.getItem('password')
      const rememberMe = localStorage.getItem('rememberMe')
      if (username) {
        this.loginForm.username = username
      }
      if (password) {
        this.loginForm.password = password
      }
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true
          // 调用用户端登录API
          login(this.loginForm).then(res => {
            // 设置token
            this.$store.commit('SET_TOKEN', res.token)
            // 获取用户信息
            this.$store.dispatch('getInfo').then(() => {
              this.$router.push({ path: this.redirect || '/' })
              this.loading = false
            }).catch(() => {
              this.loading = false
            })
          }).catch(() => {
            this.loading = false
            if (this.captchaEnabled) {
              this.getCode()
            }
          })
        }
      })
    },
    toRegister() {
      this.$router.push('/register')
    }
  }
}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}
.login-card {
  width: 400px;
  border-radius: 10px;
  box-shadow: 0 0 25px rgba(0, 0, 0, 0.1);
}
.login-header {
  text-align: center;
}
.login-header h2 {
  margin: 0;
  color: #303133;
}
.login-code {
  width: 33%;
  height: 38px;
  float: right;
  img {
    cursor: pointer;
    vertical-align: middle;
    width: 100%;
    height: 100%;
  }
}
.login-register {
  text-align: center;
  font-size: 14px;
  color: #606266;
}
.el-form-item {
  margin-bottom: 20px;
}
</style>