'use strict'

/**
 * Online 在线用户单测：锁定列表加载、查询重置与强制下线。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Online from '@/views/monitor/online/index.vue'
import Pagination from '@/components/Pagination/index.vue'
import { list, forceLogout } from '@/api/monitor/online'

jest.mock('@/api/monitor/online', () => ({
  list: jest.fn(),
  forceLogout: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('pagination', Pagination)
localVue.directive('hasPermi', {})
localVue.mixin({ methods: { parseTime: value => value } })

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const sessions = [
  { tokenId: 't1', userName: 'admin', deptName: '法务部', ipaddr: '127.0.0.1', loginTime: '2026-01-01' },
  { tokenId: 't2', userName: 'lawyer', deptName: '客服部', ipaddr: '127.0.0.2', loginTime: '2026-01-02' }
]

function mountOnline() {
  const dangerConfirm = jest.fn(() => Promise.resolve())
  const msgSuccess = jest.fn()
  const resetForm = jest.fn()
  const wrapper = mount(Online, {
    localVue,
    mocks: {
      $modal: { dangerConfirm, msgSuccess },
      resetForm
    }
  })
  return { wrapper, dangerConfirm, msgSuccess, resetForm }
}

describe('Online', () => {
  beforeEach(() => {
    list.mockReset()
    forceLogout.mockReset()
    list.mockResolvedValue({ rows: sessions, total: 2 })
  })

  test('创建时查询在线会话并结束 loading', async () => {
    const { wrapper } = mountOnline()
    await flush()

    expect(list).toHaveBeenCalledWith({ ipaddr: undefined, userName: undefined })
    expect(wrapper.vm.list).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('lawyer')
  })

  test('查询重置页码，重置同时清空表单', async () => {
    const { wrapper, resetForm } = mountOnline()
    await flush()
    wrapper.vm.pageNum = 3

    wrapper.vm.handleQuery()
    expect(wrapper.vm.pageNum).toBe(1)
    expect(list).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(resetForm).toHaveBeenCalledWith('queryForm')
    expect(list).toHaveBeenCalledTimes(3)
  })

  test('确认强退后调用接口、刷新列表并提示', async () => {
    forceLogout.mockResolvedValue({})
    const { wrapper, dangerConfirm, msgSuccess } = mountOnline()
    await flush()

    wrapper.vm.handleForceLogout(sessions[0])
    await flush()

    expect(dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ actionType: 'caution' }))
    expect(forceLogout).toHaveBeenCalledWith('t1')
    expect(list).toHaveBeenCalledTimes(2)
    expect(msgSuccess).toHaveBeenCalledWith('强退成功')
  })

  test('取消确认时不调用强退接口', async () => {
    const { wrapper, dangerConfirm, msgSuccess } = mountOnline()
    await flush()
    dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    wrapper.vm.handleForceLogout(sessions[1])
    await flush()

    expect(forceLogout).not.toHaveBeenCalled()
    expect(msgSuccess).not.toHaveBeenCalled()
  })
})
