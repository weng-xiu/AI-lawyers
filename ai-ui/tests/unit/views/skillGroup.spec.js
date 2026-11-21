'use strict'

/**
 * 技能组管理单测：锁定组列表、成员加载、新增修改提交、成员增删与开关回滚。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import SkillGroup from '@/views/lawyers/skill/group.vue'
import {
  listSkillGroup, getSkillGroup, addSkillGroup, updateSkillGroup, delSkillGroup,
  listMembers, addMembers, removeMembers
} from '@/api/lawyers/skill'
import { listAgent } from '@/api/lawyers/callCenter'
import { listCategory } from '@/api/lawyers/category'

jest.mock('@/api/lawyers/skill', () => ({
  listSkillGroup: jest.fn(),
  getSkillGroup: jest.fn(),
  addSkillGroup: jest.fn(),
  updateSkillGroup: jest.fn(),
  delSkillGroup: jest.fn(),
  listMembers: jest.fn(),
  addMembers: jest.fn(),
  removeMembers: jest.fn(),
  listEnabledGroups: jest.fn()
}))

jest.mock('@/api/lawyers/callCenter', () => ({ listAgent: jest.fn() }))
jest.mock('@/api/lawyers/category', () => ({ listCategory: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const groups = [
  { groupId: 1, groupName: '婚姻家事组', groupCode: 'MARRIAGE', categoryName: '婚姻家庭', strategy: 'round_robin', status: '1', memberCount: 2 },
  { groupId: 2, groupName: '劳动争议组', groupCode: 'LABOR', categoryName: '劳动争议', strategy: 'least_recent', status: '0', memberCount: 1 }
]

const members = [
  { agentId: 101, agentName: '张律师', skillLevel: 4, agentStatus: '1', callStatus: '1', status: '1' },
  { agentId: 102, agentName: '李律师', skillLevel: 3, agentStatus: '0', callStatus: '0', status: '1' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgWarning: jest.fn()
  }
  const wrapper = mount(SkillGroup, {
    localVue,
    stubs: { 'right-toolbar': true },
    mocks: { $modal: modal }
  })
  return { wrapper, modal }
}

describe('SkillGroup 技能组管理', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listSkillGroup.mockResolvedValue({ rows: groups })
    getSkillGroup.mockResolvedValue({ data: { ...groups[0], maxWait: 60, wrapUpTime: 10 } })
    addSkillGroup.mockResolvedValue({})
    updateSkillGroup.mockResolvedValue({})
    delSkillGroup.mockResolvedValue({})
    listMembers.mockResolvedValue({ data: members })
    addMembers.mockResolvedValue({})
    removeMembers.mockResolvedValue({})
    listAgent.mockResolvedValue({ rows: [{ agentId: 101, agentName: '张律师' }] })
    listCategory.mockResolvedValue({ rows: [{ categoryId: 1, categoryName: '婚姻家庭' }] })
  })

  test('创建时加载技能组、坐席与分类选项', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listSkillGroup).toHaveBeenCalledWith(wrapper.vm.queryParams)
    expect(wrapper.vm.groupList).toHaveLength(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.allAgents).toHaveLength(1)
    expect(wrapper.vm.categoryList).toHaveLength(1)
    expect(wrapper.text()).toContain('婚姻家事组')
    expect(wrapper.text()).toContain('轮询')
  })

  test('选择技能组加载成员，空选择忽略', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleCurrentChange(null)
    expect(wrapper.vm.current).toBeNull()

    wrapper.vm.handleCurrentChange(groups[1])
    await flush()
    expect(wrapper.vm.current).toBe(groups[1])
    expect(listMembers).toHaveBeenCalledWith(2)
    expect(wrapper.vm.memberList).toHaveLength(2)
    expect(wrapper.vm.memberLoading).toBe(false)

    wrapper.vm.current = null
    wrapper.vm.loadMembers()
    expect(listMembers).toHaveBeenCalledTimes(1)
  })

  test('策略与坐席/通话状态映射', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.strategyLabel('round_robin')).toBe('轮询')
    expect(wrapper.vm.strategyLabel('unknown')).toBe('unknown')
    expect(wrapper.vm.agentStatusLabel('2')).toBe('忙碌')
    expect(wrapper.vm.agentStatusLabel('9')).toBe('未知')
    expect(wrapper.vm.callStatusLabel('3')).toBe('咨询中')
    expect(wrapper.vm.callStatusLabel('9')).toBe('-')
    expect(wrapper.vm.callStatusType('0')).toBe('success')
    expect(wrapper.vm.callStatusType('1')).toBe('danger')
    expect(wrapper.vm.callStatusType('2')).toBe('warning')
  })

  test('溢出组选项排除当前技能组', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.current = groups[0]

    expect(wrapper.vm.overflowOptions).toEqual([groups[1]])

    wrapper.vm.current = null
    expect(wrapper.vm.overflowOptions).toHaveLength(2)
  })

  test('新增重置表单，修改读取技能组详情', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('新增技能组')
    expect(wrapper.vm.form.strategy).toBe('round_robin')
    expect(wrapper.vm.form.status).toBe('1')
    expect(wrapper.vm.form.maxWait).toBe(60)

    wrapper.vm.handleEdit()
    expect(getSkillGroup).not.toHaveBeenCalled()

    wrapper.vm.current = groups[0]
    wrapper.vm.handleEdit()
    await flush()
    expect(getSkillGroup).toHaveBeenCalledWith(1)
    expect(wrapper.vm.title).toBe('修改技能组')
    expect(wrapper.vm.form.groupName).toBe('婚姻家事组')
    expect(wrapper.vm.open).toBe(true)
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { groupName: '新组', groupCode: 'NEW', strategy: 'round_robin' }
    wrapper.vm.submitForm()
    await flush()
    expect(addSkillGroup).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('保存成功')
    expect(wrapper.vm.open).toBe(false)
    expect(listSkillGroup).toHaveBeenCalledTimes(2)

    wrapper.vm.form = { groupId: 1, groupName: '婚姻家事组', groupCode: 'MARRIAGE', strategy: 'round_robin' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateSkillGroup).toHaveBeenCalledWith(wrapper.vm.form)
  })

  test('删除技能组需选中并确认，成功后清空当前组', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete()
    expect(modal.dangerConfirm).not.toHaveBeenCalled()

    wrapper.vm.current = groups[0]
    wrapper.vm.memberList = members
    wrapper.vm.handleDelete()
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: '将删除技能组「婚姻家事组」，其成员关系将一并删除',
      count: 1,
      countNoun: '个技能组'
    }))
    expect(delSkillGroup).toHaveBeenCalledWith(1)
    expect(wrapper.vm.current).toBeNull()
    expect(wrapper.vm.memberList).toEqual([])
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')
  })

  test('提交添加成员校验非空并携带等级', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.current = groups[0]

    wrapper.vm.submitAddMembers()
    expect(modal.msgWarning).toHaveBeenCalledWith('请选择坐席')
    expect(addMembers).not.toHaveBeenCalled()

    wrapper.vm.openMemberDialog()
    wrapper.vm.addForm.agentIds = [101, 102]
    wrapper.vm.addForm.skillLevel = 4
    expect(wrapper.vm.memberOpen).toBe(true)

    wrapper.vm.submitAddMembers()
    await flush()
    expect(addMembers).toHaveBeenCalledWith(1, [101, 102], 4)
    expect(modal.msgSuccess).toHaveBeenCalledWith('添加成功')
    expect(wrapper.vm.memberOpen).toBe(false)
    expect(listMembers).toHaveBeenCalledWith(1)
    expect(listSkillGroup).toHaveBeenCalledTimes(2)
  })

  test('移除选中成员按数量确认', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.current = groups[0]
    wrapper.vm.selectedMembers = members

    wrapper.vm.handleRemoveMembers()
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: '将从当前技能组移除选中的坐席成员',
      count: 2,
      countNoun: '名成员'
    }))
    expect(removeMembers).toHaveBeenCalledWith(1, [101, 102])
    expect(modal.msgSuccess).toHaveBeenCalledWith('移除成功')
    expect(listMembers).toHaveBeenCalledWith(1)
  })

  test('成员开关启用/停用调用对应接口，失败回滚状态', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.current = groups[0]

    wrapper.vm.toggleMember({ agentId: 101, skillLevel: 4, status: '1' })
    await flush()
    expect(addMembers).toHaveBeenCalledWith(1, [101], 4)
    expect(modal.msgSuccess).toHaveBeenCalledWith('已启用')

    wrapper.vm.toggleMember({ agentId: 102, status: '0' })
    await flush()
    expect(removeMembers).toHaveBeenCalledWith(1, [102])
    expect(modal.msgSuccess).toHaveBeenCalledWith('已停用')

    addMembers.mockRejectedValueOnce(new Error('fail'))
    const row = { agentId: 103, skillLevel: 3, status: '1' }
    wrapper.vm.toggleMember(row)
    await flush()
    expect(row.status).toBe('0')
  })

  test('查询与重置复用列表加载', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleQuery()
    expect(listSkillGroup).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listSkillGroup).toHaveBeenCalledTimes(3)
  })
})
