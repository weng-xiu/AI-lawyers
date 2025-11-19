<template>
  <div class="register-container">
    <el-card class="register-card">
      <div slot="header" class="register-header">
        <h2>用户注册</h2>
      </div>
      <el-form ref="registerForm" :model="registerForm" :rules="registerRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="请输入用户名"></el-input>
        </el-form-item>
        <el-form-item label="昵称" prop="nickName">
          <el-input v-model="registerForm.nickName" placeholder="请输入昵称"></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="请输入密码"></el-input>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请确认密码"></el-input>
        </el-form-item>
        <el-form-item label="手机号" prop="phonenumber">
          <el-input v-model="registerForm.phonenumber" placeholder="请输入手机号"></el-input>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="registerForm.email" placeholder="请输入邮箱"></el-input>
        </el-form-item>
        <el-form-item label="性别" prop="sex">
          <el-radio-group v-model="registerForm.sex">
            <el-radio label="0">男</el-radio>
            <el-radio label="1">女</el-radio>
            <el-radio label="2">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="captchaEnabled" label="验证码" prop="code">
          <el-row :gutter="20">
            <el-col :span="16">
              <el-input v-model="registerForm.code" placeholder="请输入验证码"></el-input>
            </el-col>
            <el-col :span="8">
              <img :src="codeUrl" class="register-code-img" @click="getCode">
            </el-col>
          </el-row>
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading" type="primary" @click="submitForm">注 册</el-button>
          <el-button @click="resetForm">重 置</el-button>
          <el-button type="text" @click="toLogin">返回登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { getCodeImg, register } from '@/api/user'

export default {
  name: 'Register',
  data() {
    // 自定义验证规则：确认密码
    const equalToPassword = (rule, value, callback) => {
      if (this.registerForm.password !== value) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }
    return {
      codeUrl: '',
      registerForm: {
        username: '',
        nickName: '',
        password: '',
        confirmPassword: '',
        phonenumber: '',
        email: '',
        sex: '2',
        code: '',
        uuid: ''
      },
      registerRules: {
        username: [
          { required: true, trigger: 'blur', message: '用户名不能为空' },
          { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' }
        ],
        nickName: [
          { required: true, trigger: 'blur', message: '昵称不能为空' },
          { min: 2, max: 20, message: '昵称长度在 2 到 20 个字符', trigger: 'blur' }
        ],
        password: [
          { required: true, trigger: 'blur', message: '密码不能为空' },
          { min: 5, max: 20, message: '密码长度在 5 到 20 个字符', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, trigger: 'blur', message: '确认密码不能为空' },
          { required: true, validator: equalToPassword, trigger: 'blur' }
        ],
        phonenumber: [
          { pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, message: '请输入正确的手机号码', trigger: 'blur' }
        ],
        email: [
          { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] }
        ],
        code: [{ required: true, trigger: 'change', message: '验证码不能为空' }]
      },
      loading: false,
      captchaEnabled: true
    }
  },
  created() {
    this.getCode()
  },
  methods: {
    getCode() {
      getCodeImg().then(res => {
        this.captchaEnabled = res.captchaEnabled === undefined ? true : res.captchaEnabled
        if (this.captchaEnabled) {
          this.codeUrl = 'data:image/gif;base64,' + res.img
          this.registerForm.uuid = res.uuid
        }
      })
    },
    submitForm() {
      this.$refs.registerForm.validate(valid => {
        if (valid) {
          this.loading = true
          // 设置用户类型为普通用户
          this.registerForm.userType = '01'
          register(this.registerForm).then(res => {
            this.$message.success('注册成功，请登录')
            this.$router.push('/login')
            this.loading = false
          }).catch(() => {
            this.loading = false
            if (this.captchaEnabled) {
              this.getCode()
            }
          })
        }
      })
    },
    resetForm() {
      this.$refs.registerForm.resetFields()
      this.getCode()
    },
    toLogin() {
      this.$router.push('/login')
    }
  }
}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}
.register-card {
  width: 500px;
  border-radius: 10px;
  box-shadow: 0 0 25px rgba(0, 0, 0, 0.1);
}
.register-header {
  text-align: center;
}
.register-header h2 {
  margin: 0;
  color: #303133;
}
.register-code-img {
  height: 38px;
  cursor: pointer;
  vertical-align: middle;
  width: 100%;
}
.el-form-item {
  margin-bottom: 20px;
}
</style>