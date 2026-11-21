'use strict'

/**
 * 缓存监控单测：锁定缓存名称/键名查询、内容查看、前缀格式化与分级清理。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import CacheList from '@/views/monitor/cache/list.vue'
import {
  listCacheName, listCacheKey, getCacheValue, clearCacheName, clearCacheKey, clearCacheAll
} from '@/api/monitor/cache'

jest.mock('@/api/monitor/cache', () => ({
  listCacheName: jest.fn(),
  listCacheKey: jest.fn(),
  getCacheValue: jest.fn(),
  clearCacheName: jest.fn(),
  clearCacheKey: jest.fn(),
  clearCacheAll: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.prototype.parseTime = value => value

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const cacheNames = [
  { cacheName: 'login_tokens:', remark: '登录令牌' },
  { cacheName: 'sys_config:', remark: '系统配置' }
]

function mountPage() {
  const modal = { msgSuccess: jest.fn() }
  const wrapper = mount(CacheList, {
    localVue,
    mocks: { $modal: modal }
  })
  return { wrapper, modal }
}

describe('CacheList 缓存监控', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listCacheName.mockResolvedValue({ data: cacheNames })
    listCacheKey.mockResolvedValue({ data: ['login_tokens:abc', 'login_tokens:def'] })
    getCacheValue.mockResolvedValue({
      data: { cacheName: 'login_tokens:', cacheKey: 'login_tokens:abc', cacheValue: '{"userId":1}' }
    })
    clearCacheName.mockResolvedValue({})
    clearCacheKey.mockResolvedValue({})
    clearCacheAll.mockResolvedValue({})
  })

  test('创建时加载缓存名称列表并计算表格高度', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listCacheName).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.cacheNames).toEqual(cacheNames)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.tableHeight).toBe(window.innerHeight - 200)
    expect(wrapper.text()).toContain('登录令牌')
  })

  test('点击缓存名称加载键名并记录当前缓存', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.getCacheKeys(cacheNames[0])
    await flush()

    expect(listCacheKey).toHaveBeenCalledWith('login_tokens:')
    expect(wrapper.vm.cacheKeys).toHaveLength(2)
    expect(wrapper.vm.nowCacheName).toBe('login_tokens:')
    expect(wrapper.vm.subLoading).toBe(false)
  })

  test('未选择缓存名称时不请求键名', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.getCacheKeys()

    expect(listCacheKey).not.toHaveBeenCalled()
  })

  test('刷新缓存名称与键名均提示成功', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.refreshCacheNames()
    expect(listCacheName).toHaveBeenCalledTimes(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('刷新缓存列表成功')

    wrapper.vm.nowCacheName = 'login_tokens:'
    wrapper.vm.refreshCacheKeys()
    await flush()
    expect(listCacheKey).toHaveBeenCalledWith('login_tokens:')
    expect(modal.msgSuccess).toHaveBeenCalledWith('刷新键名列表成功')
  })

  test('清理缓存名称与键名后提示并刷新键名', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.nowCacheName = 'login_tokens:'

    wrapper.vm.handleClearCacheName(cacheNames[0])
    await flush()
    expect(clearCacheName).toHaveBeenCalledWith('login_tokens:')
    expect(modal.msgSuccess).toHaveBeenCalledWith('清理缓存名称[login_tokens:]成功')

    wrapper.vm.handleClearCacheKey('login_tokens:abc')
    await flush()
    expect(clearCacheKey).toHaveBeenCalledWith('login_tokens:abc')
    expect(modal.msgSuccess).toHaveBeenCalledWith('清理缓存键名[login_tokens:abc]成功')
    expect(listCacheKey).toHaveBeenCalledTimes(2)
  })

  test('格式化去除缓存名称与键名前缀', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.nowCacheName = 'login_tokens:'

    expect(wrapper.vm.nameFormatter({ cacheName: 'login_tokens:' })).toBe('login_tokens')
    expect(wrapper.vm.keyFormatter('login_tokens:abc')).toBe('abc')
  })

  test('查看缓存内容并清理全部缓存', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.nowCacheName = 'login_tokens:'

    wrapper.vm.handleCacheValue('login_tokens:abc')
    await flush()
    expect(getCacheValue).toHaveBeenCalledWith('login_tokens:', 'login_tokens:abc')
    expect(wrapper.vm.cacheForm.cacheValue).toBe('{"userId":1}')

    wrapper.vm.handleClearCacheAll()
    await flush()
    expect(clearCacheAll).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('清理全部缓存成功')
  })
})
