import request from '@/utils/http'

// 用户登录
export function login(data) {
  return request({
    url: '/login',
    method: 'post',
    data: data
  })
}

// 获取用户信息
export function getInfo() {
  return request({
    url: '/getInfo',
    method: 'get'
  })
}

// 用户登出
export function logout() {
  return request({
    url: '/logout',
    method: 'post'
  })
}

// 获取验证码
export function getCodeImg() {
  return request({
    url: '/captchaImage',
    method: 'get'
  })
}

// 用户注册
export function register(data) {
  return request({
    url: '/register',
    method: 'post',
    data: data
  })
}

// 获取用户类型
export function getUserType() {
  return request({
    url: '/user/type',
    method: 'get'
  })
}