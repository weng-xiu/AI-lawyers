'use strict'

/**
 * Cache 缓存监控单测：锁定加载提示、两项图表初始化与自适应重绘。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import * as echarts from 'echarts'
import Cache from '@/views/monitor/cache/index.vue'
import { getCache } from '@/api/monitor/cache'

jest.mock('@/api/monitor/cache', () => ({ getCache: jest.fn() }))
jest.mock('echarts', () => ({
  init: jest.fn(() => ({
    setOption: jest.fn(),
    resize: jest.fn()
  }))
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const cacheData = {
  info: {
    redis_version: '7.0.0',
    redis_mode: 'standalone',
    tcp_port: 6379,
    connected_clients: 3,
    uptime_in_days: 12,
    used_memory_human: '1.5M',
    used_cpu_user_children: '0.123',
    maxmemory_human: '4G',
    aof_enabled: '0',
    rdb_last_bgsave_status: 'ok',
    instantaneous_input_kbps: 0.5,
    instantaneous_output_kbps: 0.8
  },
  dbSize: 128,
  commandStats: [{ name: 'get', value: 100 }]
}

function mountCache() {
  const loading = jest.fn()
  const closeLoading = jest.fn()
  const wrapper = mount(Cache, {
    localVue,
    mocks: { $modal: { loading, closeLoading } }
  })
  return { wrapper, loading, closeLoading }
}

describe('Cache', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getCache.mockReset()
    getCache.mockResolvedValue({ data: cacheData })
  })

  test('创建时打开加载提示并请求缓存信息', async () => {
    const { loading, closeLoading } = mountCache()
    await flush()

    expect(loading).toHaveBeenCalledWith('正在加载缓存监控数据，请稍候！')
    expect(getCache).toHaveBeenCalled()
    expect(closeLoading).toHaveBeenCalled()
  })

  test('数据返回后渲染基础信息与两张图表', async () => {
    const { wrapper } = mountCache()
    await flush()

    expect(wrapper.text()).toContain('7.0.0')
    expect(wrapper.text()).toContain('单机')
    expect(wrapper.text()).toContain('128')

    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$refs.commandstats, 'judicial')
    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$refs.usedmemory, 'judicial')
    expect(wrapper.vm.commandstats.setOption).toHaveBeenCalled()
    const gaugeOption = wrapper.vm.usedmemory.setOption.mock.calls[0][0]
    expect(gaugeOption.series[0].type).toBe('gauge')
    expect(wrapper.vm.cache.info.aof_enabled).toBe('0')
  })

  test('窗口缩放时两张图表同步重绘', async () => {
    const { wrapper } = mountCache()
    await flush()

    window.dispatchEvent(new Event('resize'))

    expect(wrapper.vm.commandstats.resize).toHaveBeenCalled()
    expect(wrapper.vm.usedmemory.resize).toHaveBeenCalled()
  })

  test('组件名为 Cache', () => {
    const { wrapper } = mountCache()

    expect(wrapper.vm.$options.name).toBe('Cache')
  })
})
