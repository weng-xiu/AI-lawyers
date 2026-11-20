'use strict'

/**
 * 下载插件单测：覆盖公共下载、资源下载、zip 下载与错误消息解析。
 */
import axios from 'axios'
import { Loading, Message } from 'element-ui'
import { saveAs } from 'file-saver'
import { getToken } from '@/utils/auth'
import { blobValidate } from '@/utils/ruoyi'
import download from '@/plugins/download'

jest.mock('axios', () => jest.fn(() => Promise.resolve({ data: 'blob', headers: {} })))
jest.mock('element-ui', () => ({
  Loading: { service: jest.fn(() => ({ close: jest.fn() })) },
  Message: { error: jest.fn() }
}))
jest.mock('file-saver', () => ({ saveAs: jest.fn() }))
jest.mock('@/utils/auth', () => ({ getToken: jest.fn(() => 'token-abc') }))
jest.mock('@/utils/ruoyi', () => ({ blobValidate: jest.fn(() => true) }))
jest.mock('@/utils/errorCode', () => ({ 500: '服务器错误', default: '系统未知错误' }))

const flush = () => new Promise(resolve => setTimeout(resolve, 0))
// 与源码模块加载时保持一致的 baseURL
const base = process.env.VUE_APP_BASE_API

beforeEach(() => {
  axios.mockClear()
  axios.mockResolvedValue({ data: 'blob', headers: {} })
  saveAs.mockClear()
  Message.error.mockClear()
  getToken.mockClear()
  blobValidate.mockReset()
  blobValidate.mockReturnValue(true)
  Loading.service.mockClear()
})

describe('name / resource 下载', () => {
  test('name 按文件名请求 blob 并以响应头文件名保存', async () => {
    axios.mockResolvedValueOnce({
      data: 'blob',
      headers: { 'download-filename': encodeURIComponent('报表.xlsx') }
    })

    download.name('报表.xlsx')
    await flush()

    expect(axios).toHaveBeenCalledWith({
      method: 'get',
      url: base + '/common/download?fileName=' + encodeURIComponent('报表.xlsx') + '&delete=true',
      responseType: 'blob',
      headers: { Authorization: 'Bearer token-abc' }
    })
    expect(saveAs).toHaveBeenCalledWith(expect.anything(), '报表.xlsx', undefined)
  })

  test('name 传入 isDelete=false 时透传到查询参数', () => {
    download.name('a.txt', false)

    expect(axios.mock.calls[0][0].url).toContain('&delete=false')
  })

  test('resource 按资源路径请求并保存', async () => {
    axios.mockResolvedValueOnce({
      data: 'blob',
      headers: { 'download-filename': encodeURIComponent('模板.docx') }
    })

    download.resource('template/模板.docx')
    await flush()

    expect(axios.mock.calls[0][0].url).toBe(
      base + '/common/download/resource?resource=' + encodeURIComponent('template/模板.docx')
    )
    expect(saveAs).toHaveBeenCalledWith(expect.anything(), '模板.docx', undefined)
  })

  test('响应非 blob 时解析错误码并提示', async () => {
    blobValidate.mockReturnValueOnce(false)
    axios.mockResolvedValueOnce({
      data: { text: () => Promise.resolve(JSON.stringify({ code: 500 })) },
      headers: {}
    })

    download.name('a.txt')
    await flush()

    expect(Message.error).toHaveBeenCalledWith('服务器错误')
    expect(saveAs).not.toHaveBeenCalled()
  })

  test('未知错误码回退默认提示', async () => {
    const printErrMsg = jest.spyOn(download, 'printErrMsg')

    await download.printErrMsg({ text: () => Promise.resolve(JSON.stringify({ code: 999 })) })

    expect(printErrMsg).toHaveBeenCalled()
    expect(Message.error).toHaveBeenCalledWith('系统未知错误')
  })
})

describe('zip 下载', () => {
  test('展示 Loading，成功后保存 zip 并关闭 Loading', async () => {
    const instance = { close: jest.fn() }
    Loading.service.mockReturnValueOnce(instance)

    download.zip('/common/zip', 'data.zip')
    await flush()

    expect(Loading.service).toHaveBeenCalledWith(expect.objectContaining({
      text: '正在下载数据，请稍候'
    }))
    expect(axios.mock.calls[0][0].url).toBe(base + '/common/zip')
    expect(saveAs).toHaveBeenCalledWith(expect.anything(), 'data.zip', undefined)
    expect(instance.close).toHaveBeenCalled()
  })

  test('下载失败时提示错误并关闭 Loading', async () => {
    const instance = { close: jest.fn() }
    Loading.service.mockReturnValueOnce(instance)
    axios.mockRejectedValueOnce(new Error('network'))
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {})

    download.zip('/common/zip', 'data.zip')
    await flush()

    expect(Message.error).toHaveBeenCalledWith('下载文件出现错误，请联系管理员！')
    expect(instance.close).toHaveBeenCalled()
    consoleSpy.mockRestore()
  })
})

describe('saveAs', () => {
  test('直接透传给 file-saver', () => {
    const blob = new Blob(['x'])

    download.saveAs(blob, 'x.txt', { autoBom: true })

    expect(saveAs).toHaveBeenCalledWith(blob, 'x.txt', { autoBom: true })
  })
})
