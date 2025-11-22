import request from '@/utils/http'

// 用户登录
export function login(data) {
  return request({
    url: '/aiuser/login',
    method: 'post',
    data: data
  })
}

// 获取用户信息
export function getInfo() {
  return request({
    url: '/aiuser/getInfo',
    method: 'get'
  })
}

// 用户登出
export function logout() {
  return request({
    url: '/aiuser/logout',
    method: 'post'
  })
}

// 获取验证码
export function getCodeImg() {
  return request({
    url: '/aiuser/captchaImage',
    method: 'get'
  })
}

// 用户注册
export function register(data) {
  return request({
    url: '/aiuser/register',
    method: 'post',
    data: data
  })
}

// 获取用户类型
export function getUserType() {
  return request({
    url: '/aiuser/user/type',
    method: 'get'
  })
}