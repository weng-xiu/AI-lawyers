'use strict'

/**
 * 错误码文案单测：锁定常用 HTTP 状态码的中文提示，避免提示语误改。
 */
import errorCode from '@/utils/errorCode'

describe('错误码文案', () => {
  test('内置状态码与兜底文案完整', () => {
    expect(errorCode).toEqual({
      '401': '认证失败，无法访问系统资源',
      '403': '当前操作没有权限',
      '404': '访问资源不存在',
      default: '系统未知错误，请反馈给管理员'
    })
  })
})
