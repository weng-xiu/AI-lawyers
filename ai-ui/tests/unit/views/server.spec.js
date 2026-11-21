'use strict'

/**
 * 服务监控单测：锁定加载提示、服务器信息回显与高占用告警样式。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Server from '@/views/monitor/server/index.vue'
import { getServer } from '@/api/monitor/server'

jest.mock('@/api/monitor/server', () => ({ getServer: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const serverData = {
  cpu: { cpuNum: 8, used: 20, sys: 30, free: 50 },
  mem: { total: 16, used: 12, free: 4, usage: 75 },
  jvm: { total: 512, used: 480, free: 32, usage: 93, name: 'OpenJDK', version: '17', startTime: '2026-01-01', runTime: '10天', home: '/usr/lib/jvm', inputArgs: '-Xmx512m' },
  sys: { computerName: 'law-server', computerIp: '10.0.0.1', osName: 'Linux', osArch: 'amd64', userDir: '/opt/app' },
  sysFiles: [
    { dirName: '/', sysTypeName: 'ext4', typeName: '本地磁盘', total: '100G', free: '40G', used: '60G', usage: 60 },
    { dirName: '/data', sysTypeName: 'ext4', typeName: '本地磁盘', total: '200G', free: '10G', used: '190G', usage: 95 }
  ]
}

function mountPage() {
  const modal = {
    loading: jest.fn(),
    closeLoading: jest.fn()
  }
  const wrapper = mount(Server, {
    localVue,
    mocks: { $modal: modal }
  })
  return { wrapper, modal }
}

describe('Server 服务监控', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getServer.mockResolvedValue({ data: serverData })
  })

  test('创建时打开加载层并在数据返回后关闭', async () => {
    const { wrapper, modal } = mountPage()

    expect(modal.loading).toHaveBeenCalledWith('正在加载服务监控数据，请稍候！')

    await flush()
    expect(getServer).toHaveBeenCalledTimes(1)
    expect(modal.closeLoading).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.server).toBe(serverData)
  })

  test('回显 CPU、内存与服务器基础信息', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('law-server')
    expect(wrapper.text()).toContain('OpenJDK')
    expect(wrapper.text()).toContain('/opt/app')
    expect(wrapper.text()).toContain('16G')
  })

  test('内存、JVM 与磁盘使用率超过阈值时标记告警', async () => {
    const { wrapper } = mountPage()
    await flush()

    const danger = wrapper.findAll('.text-danger')
    // JVM 使用率 93%，/data 磁盘使用率 95%，合计两处告警
    expect(danger).toHaveLength(2)
    expect(danger.at(0).text()).toBe('93%')
    expect(danger.at(1).text()).toBe('95%')
  })

  test('磁盘状态按数据条数渲染明细行', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.text()).toContain('/data')
    expect(wrapper.text()).toContain('ext4')
    expect(wrapper.text()).toContain('200G')
    expect(wrapper.text()).toContain('190G')
  })
})
