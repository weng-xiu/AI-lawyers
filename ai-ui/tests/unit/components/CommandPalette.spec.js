'use strict'

/**
 * CommandPalette 命令面板单测：锁定菜单扁平化、快捷唤起、键盘导航与远程结果跳转。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import CommandPalette from '@/components/CommandPalette/index.vue'
import { searchPalette } from '@/api/lawyers/palette'

jest.mock('@/api/lawyers/palette', () => ({
  searchPalette: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const routes = [
  {
    path: '/business',
    meta: { title: '业务' },
    children: [{ path: 'workOrder', meta: { title: '工单', icon: 'el-icon-tickets' } }]
  },
  { path: '/a', meta: { title: '业务A' } },
  { path: '/b', meta: { title: '业务B' } },
  { path: '/hidden', hidden: true, meta: { title: '隐藏页' } }
]

function mountPalette() {
  const push = jest.fn(() => Promise.resolve())
  const wrapper = mount(CommandPalette, {
    localVue,
    mocks: {
      $store: { state: { permission: { routes } } },
      $router: { push }
    }
  })
  return { wrapper, push }
}

describe('CommandPalette', () => {
  beforeEach(() => {
    searchPalette.mockReset()
  })

  test('菜单树扁平化并拼接父级标题', () => {
    const { wrapper } = mountPalette()

    const items = wrapper.vm.menuItems
    expect(items).toHaveLength(3)
    expect(items[0]).toMatchObject({
      key: '/business/workOrder',
      title: '工单',
      icon: 'el-icon-tickets'
    })
    expect(items[1].title).toBe('业务A')
  })

  test('关键字命中本地菜单并按序扁平编号', () => {
    const { wrapper } = mountPalette()

    wrapper.vm.keyword = '业务'

    expect(wrapper.vm.groups).toHaveLength(1)
    expect(wrapper.vm.groups[0].items).toHaveLength(2)
    expect(wrapper.vm.flatItems.map(item => item.flatIndex)).toEqual([0, 1])
    expect(wrapper.vm.activeIndex).toBe(0)
  })

  test('Ctrl+K 唤起并重置状态', () => {
    const { wrapper } = mountPalette()
    const preventDefault = jest.fn()
    wrapper.vm.keyword = '旧关键字'
    wrapper.vm.remoteGroups = [{ type: 'ticket', items: [] }]

    wrapper.vm.onGlobalKey({ ctrlKey: true, key: 'k', preventDefault })
    expect(preventDefault).toHaveBeenCalled()
    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.vm.keyword).toBe('')
    expect(wrapper.vm.remoteGroups).toEqual([])

    wrapper.vm.visible = false
    wrapper.vm.onGlobalKey({ ctrlKey: false, key: 'k', preventDefault })
    expect(wrapper.vm.visible).toBe(false)
  })

  test('上下键在扁平结果内循环', () => {
    const { wrapper } = mountPalette()
    wrapper.vm.keyword = '业务'
    expect(wrapper.vm.flatItems).toHaveLength(2)

    wrapper.vm.move(1)
    expect(wrapper.vm.activeIndex).toBe(1)
    wrapper.vm.move(1)
    expect(wrapper.vm.activeIndex).toBe(0)
    wrapper.vm.move(-1)
    expect(wrapper.vm.activeIndex).toBe(1)
  })

  test('回车打开当前激活项', () => {
    const { wrapper, push } = mountPalette()
    wrapper.vm.keyword = '业务'
    wrapper.vm.activeIndex = 1

    wrapper.vm.enterGo()

    expect(push).toHaveBeenCalledWith('/b')
    expect(wrapper.vm.visible).toBe(false)
  })

  test('远程结果按类型跳转并携带查询条件', () => {
    const { wrapper, push } = mountPalette()

    wrapper.vm.go({ type: 'ticket', no: 'T001', title: 'T001' })
    expect(push).toHaveBeenCalledWith({ path: '/business/workOrder', query: { ticketNo: 'T001' } })

    wrapper.vm.go({ type: 'record', no: '', title: '13800000000' })
    expect(push).toHaveBeenLastCalledWith({ path: '/inbound/callRecord', query: { callerNumber: '13800000000' } })
  })

  test('输入关键字后防抖请求聚合接口并分组展示', async () => {
    jest.useFakeTimers()
    searchPalette.mockResolvedValue({
      data: {
        tickets: [{ id: 1, title: 'T001', sub: '张三' }],
        knowledges: [],
        records: [{ id: 2, title: '13800000000' }]
      }
    })
    const { wrapper } = mountPalette()
    wrapper.vm.keyword = 'T1'

    wrapper.vm.onKeywordInput()
    expect(searchPalette).not.toHaveBeenCalled()

    jest.advanceTimersByTime(300)
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()

    expect(searchPalette).toHaveBeenCalledWith('T1', 5)
    expect(wrapper.vm.remoteGroups.map(group => group.type)).toEqual(['ticket', 'record'])
    expect(wrapper.vm.remoteGroups[0].items[0]).toMatchObject({ title: 'T001', no: 'T001' })

    wrapper.vm.keyword = ''
    wrapper.vm.onKeywordInput()
    expect(wrapper.vm.remoteGroups).toEqual([])

    jest.useRealTimers()
  })
})
