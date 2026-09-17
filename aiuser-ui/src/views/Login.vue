<template>
  <div class="login-container">
    <main id="main-content" role="main" tabindex="-1" class="login-main">
    <el-card class="login-card">
      <div slot="header" class="login-header">
        <h2>用户登录</h2>
      </div>
      <el-form ref="loginForm" :model="loginForm" :rules="loginRules" label-width="0px">
        <el-form-item prop="username">
          <label for="login-username" class="a11y-label">用户名</label>
          <el-input
            id="login-username"
            v-model="loginForm.username"
            type="text"
            auto-complete="username"
            placeholder="用户名"
            aria-label="用户名"
            prefix-icon="el-icon-user">
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <label for="login-password" class="a11y-label">密码</label>
          <el-input
            id="login-password"
            v-model="loginForm.password"
            type="password"
            auto-complete="current-password"
            placeholder="密码"
            aria-label="密码"
            prefix-icon="el-icon-lock"
            @keyup.enter.native="handleLogin">
          </el-input>
        </el-form-item>
        <el-form-item v-if="captchaEnabled" prop="code">
          <label for="login-code" class="a11y-label">验证码</label>
          <el-input
            id="login-code"
            v-model="loginForm.code"
            auto-complete="off"
            placeholder="验证码"
            aria-label="请输入图形验证码的计算结果"
            style="width: 48%"
            @keyup.enter.native="handleLogin">
          </el-input>
          <div class="login-code">
            <img :src="codeUrl" class="login-code-img" alt="图形验证码，点击可刷新"
                 role="button" tabindex="0"
                 @click="getCode" @keyup.enter="getCode">
          </div>
          <!-- F2 语音验证码：浏览器朗读算式（不含答案），不支持时隐藏 -->
          <el-button
            v-if="speechSupported"
            type="text"
            class="voice-captcha-btn"
            icon="el-icon-bell"
            aria-label="播放验证码语音，听算式后输入计算结果"
            @click="playCaptchaVoice">听验证码</el-button>
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
    </main>
  </div>
</template>

<script>
import { getCodeImg, login } from '@/api/user'
import { speak, announce, speechSupported } from '@/utils/a11y'

export default {
  name: 'Login',
  data() {
    return {
      codeUrl: '',
      captchaExpr: '',
      speechSupported,
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
          this.captchaExpr = res.expr || ''
        }
      })
    },
    // F2 语音验证码：朗读算式（如"3 加 4，等于几"），不含答案
    playCaptchaVoice() {
      if (!this.captchaExpr) {
        this.$message.info('正在获取验证码，请稍后重试')
        this.getCode()
        return
      }
      const ok = speak('语音验证码：' + this.captchaExpr + '，请输入计算结果')
      if (!ok) {
        this.$message.warning('当前浏览器不支持语音播报，请根据图形验证码输入')
      } else {
        announce('正在播放语音验证码：' + this.captchaExpr, 'polite')
      }
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
              announce('登录成功，正在进入系统', 'polite')
              this.$router.push({ path: this.redirect || '/' })
              this.loading = false
            }).catch(() => {
              this.loading = false
            })
          }).catch((err) => {
            this.loading = false
            const msg = (err && err.response && err.response.data && err.response.data.msg) || '登录失败，请检查用户名、密码与验证码'
            announce('登录失败：' + msg, 'assertive')
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
  min-height: 100vh;
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}
.login-main { outline: none; width: 100%; display: flex; justify-content: center; }
.login-card {
  width: 400px;
  max-width: 100%;
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
/* 仅供读屏器的字段标签 */
.a11y-label {
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
.login-code {
  width: 32%;
  height: 40px;
  float: right;
  img {
    cursor: pointer;
    vertical-align: middle;
    width: 100%;
    height: 100%;
    border-radius: 4px;
  }
}
.voice-captcha-btn {
  display: block;
  margin-top: 6px;
  padding: 0;
  font-size: 14px;
}
/* F2 关怀模式：登录页热区/字号 */
html.care-large .voice-captcha-btn { font-size: 17px; }
html.care-xlarge .voice-captcha-btn { font-size: 20px; }
html.care-xlarge .login-code { height: 48px; }
/* F2 高对比 */
html.care-high-contrast .login-card { border: 2px solid #000; box-shadow: none; }
html.care-high-contrast .login-code img { border: 2px solid #000; }
.login-register {
  text-align: center;
  font-size: 14px;
  color: #606266;
}
.el-form-item {
  margin-bottom: 20px;
}
</style>