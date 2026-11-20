'use strict'

/**
 * axios 请求/响应拦截器单测：锁定 token 注入、GET 参数序列化、防重复提交、
 * 401/500/601 错误分级与通用下载行为，这些是所有页面请求的公共链路。
 */
import service, { download, isRelogin } from '@/utils/request'
import { getToken } from '@/utils/auth'
import cache from '@/plugins/cache'
import { saveAs } from 'file-saver'
import { Notification, MessageBox, Message, Loading } from 'element-ui'

jest.mock('@/store', () => ({
  __esModule: true,
  default: { dispatch: jest.fn(() => Promise.resolve()) }
}))
jest.mock('@/utils/auth', () => ({ getToken: jest.fn() }))
jest.mock('@/plugins/cache', () => {
  const mem = {}
  return {
    __esModule: true,
    default: {
      session: {
        getJSON: jest.fn(key => mem[key]),
        setJSON: jest.fn((key, value) => { mem[key] = value })
      }
    }
  }
})
jest.mock('file-saver', () => ({ saveAs: jest.fn() }))
jest.mock('element-ui', () => {
  const Message = jest.fn()
  Message.error = jest.fn()
  return {
    Notification: { error: jest.fn() },
    MessageBox: { confirm: jest.fn() },
    Message,
    Loading: { service: jest.fn() }
  }
})

const requestHandler = service.interceptors.request.handlers[0].fulfilled
const responseHandler = service.interceptors.response.handlers[0].fulfilled
const responseError = service.interceptors.response.handlers[0].rejected

beforeEach(() => {
  jest.clearAllMocks()
  cache.session.setJSON('sessionObj', '')
  Loading.service.mockReturnValue({ close: jest.fn() })
  isRelogin.show = false
})

describe('请求拦截器', () => {
  test('登录后自动注入 Bearer token', async () => {
    getToken.mockReturnValue('tk-123')

    const config = await requestHandler({ method: 'get', url: '/a', headers: {} })

    expect(config.headers.Authorization).toBe('Bearer tk-123')
  })

  test('isToken=false 或未登录时不注入 token', async () => {
    getToken.mockReturnValue('tk-123')
    const skip = await requestHandler({ method: 'get', url: '/a', headers: { isToken: false } })
    expect(skip.headers.Authorization).toBeUndefined()

    getToken.mockReturnValue(null)
    const anonymous = await requestHandler({ method: 'get', url: '/a', headers: {} })
    expect(anonymous.headers.Authorization).toBeUndefined()
  })

  test('GET 参数拼接为查询串并清空 params', async () => {
    getToken.mockReturnValue(null)

    const config = await requestHandler({ method: 'get', url: '/list', params: { pageNum: 1, name: 'a' }, headers: {} })

    expect(config.url).toBe('/list?pageNum=1&name=a')
    expect(config.params).toEqual({})
  })

  test('短时间相同 POST 请求被判定为重复提交', async () => {
    getToken.mockReturnValue(null)
    const config = { method: 'post', url: '/save', data: { a: 1 }, headers: {} }

    await requestHandler({ ...config, headers: {} })
    await expect(requestHandler({ ...config, headers: {} }))
      .rejects.toThrow('数据正在处理，请勿重复提交')
  })

  test('repeatSubmit=false 时跳过防重复提交校验', async () => {
    getToken.mockReturnValue(null)
    const config = { method: 'post', url: '/save', data: { a: 1 }, headers: { repeatSubmit: false } }

    await requestHandler(config)
    const second = await requestHandler({ ...config, headers: { repeatSubmit: false } })
    expect(second).toMatchObject({ method: 'post', url: '/save' })
  })
})

describe('响应拦截器', () => {
  test('成功响应返回业务数据体', async () => {
    const data = await responseHandler({ data: { code: 200, data: { id: 1 } }, request: {} })
    expect(data).toEqual({ code: 200, data: { id: 1 } })
  })

  test('未返回 code 时默认按 200 处理', async () => {
    const data = await responseHandler({ data: { rows: [1] }, request: {} })
    expect(data).toEqual({ rows: [1] })
  })

  test('blob/arraybuffer 直接返回二进制内容', async () => {
    expect(await responseHandler({ data: 'blob-data', request: { responseType: 'blob' } })).toBe('blob-data')
    expect(await responseHandler({ data: 'buffer', request: { responseType: 'arraybuffer' } })).toBe('buffer')
  })

  test('401 弹出重新登录确认并拒绝请求', async () => {
    MessageBox.confirm.mockReturnValue(new Promise(() => {}))

    await expect(responseHandler({ data: { code: 401 }, request: {} }))
      .rejects.toBe('无效的会话，或者会话已过期，请重新登录。')

    expect(isRelogin.show).toBe(true)
    expect(MessageBox.confirm).toHaveBeenCalled()
    isRelogin.show = false
  })

  test('500 使用错误提示气泡并拒绝', async () => {
    await expect(responseHandler({ data: { code: 500, msg: '业务失败' }, request: {} }))
      .rejects.toThrow('业务失败')
    expect(Message).toHaveBeenCalledWith({ message: '业务失败', type: 'error' })
  })

  test('601 使用警告提示', async () => {
    await expect(responseHandler({ data: { code: 601, msg: '请稍后' }, request: {} }))
      .rejects.toBe('error')
    expect(Message).toHaveBeenCalledWith({ message: '请稍后', type: 'warning' })
  })

  test('其它非 200 状态码走通知', async () => {
    await expect(responseHandler({ data: { code: 403 }, request: {} })).rejects.toBe('error')
    expect(Notification.error).toHaveBeenCalledWith({ title: '当前操作没有权限' })
  })

  test('网络异常与超时统一转换为中文提示', async () => {
    const network = new Error('Network Error')
    await expect(responseError(network)).rejects.toBe(network)
    expect(Message).toHaveBeenCalledWith({ message: '后端接口连接异常', type: 'error', duration: 5000 })

    await expect(responseError(new Error('timeout of 10000ms exceeded'))).rejects.toBeTruthy()
    expect(Message).toHaveBeenLastCalledWith({ message: '系统接口请求超时', type: 'error', duration: 5000 })

    await expect(responseError(new Error('Request failed with status code 404'))).rejects.toBeTruthy()
    expect(Message).toHaveBeenLastCalledWith({ message: '系统接口404异常', type: 'error', duration: 5000 })
  })
})

describe('download 通用下载', () => {
  test('文件流直接触发保存并关闭 loading', async () => {
    const blob = new Blob(['x'], { type: 'application/octet-stream' })
    jest.spyOn(service, 'post').mockResolvedValue(blob)

    await download('/export', { ids: '1' }, 'demo.xlsx')

    expect(service.post).toHaveBeenCalledWith('/export', { ids: '1' }, expect.objectContaining({
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      responseType: 'blob'
    }))
    expect(saveAs).toHaveBeenCalledWith(expect.any(Blob), 'demo.xlsx')
    expect(Loading.service().close).toHaveBeenCalled()
  })

  test('JSON 错误体转为错误提示', async () => {
    const json = {
      type: 'application/json',
      text: jest.fn().mockResolvedValue(JSON.stringify({ code: 500, msg: '导出失败' }))
    }
    jest.spyOn(service, 'post').mockResolvedValue(json)

    await download('/export', {}, 'demo.xlsx')

    expect(Message.error).toHaveBeenCalledWith('导出失败')
    expect(saveAs).not.toHaveBeenCalled()
  })

  test('下载请求异常时兜底提示', async () => {
    jest.spyOn(service, 'post').mockRejectedValue(new Error('网络异常'))

    await download('/export', {}, 'demo.xlsx')

    expect(Message.error).toHaveBeenCalledWith('下载文件出现错误，请联系管理员！')
  })
})
