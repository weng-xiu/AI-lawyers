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
  ['/aiuser/evaluation/list', () => ok({ rows: [], total: 0 })],
  // F10 常见问题推荐（知识库只读投影）
  ['/lawyers/portal/faq', () => ok({
    data: [
      { knowledgeId: 1, title: '劳动合同到期不续签是否有补偿？', summary: '用人单位不续签劳动合同，一般应依法支付经济补偿。', categoryId: 2, viewCount: 128 },
      { knowledgeId: 2, title: '离婚时房产如何分割？', summary: '婚后共同购买的房产原则上按共同财产分割。', categoryId: 1, viewCount: 96 }
    ]
  })],
  // F10 消息中心
  ['/lawyers/portal/message/list', () => ok({
    rows: [
      {
        messageId: 1,
        msgType: '5',
        title: '工单受理提醒：法律咨询工单',
        content: '您的工单已由张律师受理，请留意后续办理进度。',
        isRead: '0',
        bizType: 'ticket',
        bizId: 1,
        createTime: '2026-10-30 10:00:00'
      }
    ],
    total: 1
  })],
  ['/lawyers/portal/message/unreadCount', () => ok({ data: 1 })],
  // F10 评价状态
  [/\/aiuser\/consultation\/\d+\/evaluated/, () => ok({ data: false })]
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
