'use strict'
// F2 axe 扫描专用：统一 mock 公众端后端接口（前缀 /dev-api），
// 使鉴权页在无 Java 后端的情况下也能渲染到真实页面结构。
// 仅返回最小可渲染数据（空态），无障碍违规与数据无关。

const ok = (data) => ({ code: 200, msg: '操作成功', ...data })

const routes = [
  // 登录用户信息（路由守卫要求 userType === '01' 公众用户）
  ['/aiuser/getInfo', () => ok({ user: { userId: 100, userName: 'testuser', userType: '01', phonenumber: '13900000001' } })],
  // 验证码：关闭图形验证码，避免图片请求
  ['/aiuser/captchaImage', () => ({ code: 200, captchaEnabled: false, img: '', uuid: 'mock-uuid' })],
  // 我的工单
  ['/lawyers/portal/ticket/list', () => ok({ rows: [], total: 0 })],
  // 服务机构目录（匿名页）
  ['/lawyers/portal/services/orgs', () => ok({ rows: [], total: 0, data: [] })],
  // 渠道绑定列表
  ['/lawyers/portal/channel/list', () => ok({ data: [], rows: [] })],
  // 问题分类
  ['/aiuser/consultation/categories', () => ok({ data: [] })],
  // 咨询历史
  ['/aiuser/consultation/history', () => ok({ rows: [], total: 0 })],
  // 咨询结果/详情
  [/\/aiuser\/consultation\/(result|info)\//, () => ok({ data: { id: 1, title: '测试咨询', content: '测试内容', status: 'COMPLETED' } })],
  // 评价列表
  ['/aiuser/evaluation/list', () => ok({ rows: [], total: 0 })]
]

async function mockApi(page) {
  await page.route(/\/dev-api\/.*/, async (route) => {
    const url = route.request().url()
    const path = url.replace(/^https?:\/\/[^/]+\/dev-api/, '').split('?')[0]
    for (const [matcher, handler] of routes) {
      const hit = matcher instanceof RegExp ? matcher.test(path) : path === matcher
      if (hit) {
        await route.fulfill({ status: 200, contentType: 'application/json;charset=utf-8', body: JSON.stringify(handler()) })
        return
      }
    }
    // 其余接口（偏好保存/绑定动作/提交等）统一返回成功
    await route.fulfill({ status: 200, contentType: 'application/json;charset=utf-8', body: JSON.stringify(ok({})) })
  })
}

module.exports = { mockApi }
