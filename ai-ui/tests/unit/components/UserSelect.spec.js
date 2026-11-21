'use strict'

/**
 * UserSelect 用户选择单测：锁定用户加载参数、选项文案与回传值形态。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import UserSelect from '@/components/UserSelect/index.vue'
import { listUser } from '@/api/system/user'

jest.mock('@/api/system/user', () => ({
  listUser: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const users = [
  { userId: 1, userName: 'zhangsan', nickName: '张三' },
  { userId: 2, userName: 'lisi', nickName: '' }
]

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

async function mountSelect(propsData = {}) {
  const wrapper = mount(UserSelect, { localVue, propsData })
  await flush()
  return wrapper
}

describe('UserSelect', () => {
  beforeEach(() => {
    listUser.mockReset()
    listUser.mockResolvedValue({ rows: users })
  })

  test('挂载后默认按正常状态加载用户并渲染选项文案', async () => {
    const wrapper = await mountSelect()

    expect(listUser).toHaveBeenCalledWith({ pageNum: 1, pageSize: 1000, status: '0' })
    expect(wrapper.vm.users).toHaveLength(2)

    const options = wrapper.findAllComponents({ name: 'ElOption' })
    expect(options.at(0).props('label')).toBe('张三（zhangsan）')
    expect(options.at(1).props('label')).toBe('lisi')
  })

  test('lawyer 开启时附加律师标记，status 为空时不过滤状态', async () => {
    await mountSelect({ lawyer: true, status: null })

    expect(listUser).toHaveBeenCalledWith({ pageNum: 1, pageSize: 1000, lawyerFlag: '1' })
  })

  test('输入与清空事件按约定回传', async () => {
    const wrapper = await mountSelect()

    wrapper.vm.onInput(1)
    expect(wrapper.emitted('input')).toEqual([[1]])

    wrapper.vm.onClear()
    expect(wrapper.emitted('change').pop()).toEqual([null, null])
  })

  test('单选返回匹配的用户对象', async () => {
    const wrapper = await mountSelect({ value: 1 })

    wrapper.vm.onChange(1)

    expect(wrapper.emitted('change').pop()).toEqual([1, users[0]])
  })

  test('多选返回命中的用户数组', async () => {
    const wrapper = await mountSelect({ multiple: true, value: [1, 2] })

    wrapper.vm.onChange([1, 2])

    expect(wrapper.emitted('change').pop()).toEqual([[1, 2], users])
  })
})
