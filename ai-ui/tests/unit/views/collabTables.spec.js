'use strict'

/**
 * U 系列存量迁移单测：collab 协同域四个列表页统一迁移到 V3 ProTable。
 * 覆盖：初始化按默认分页拉取（不再走 el-card 四段式骨架）、ProTable 重置后清空筛选并重查、
 *       合成列/标签列的插槽渲染、按行状态展示的操作按钮差异。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import ChannelPage from '@/views/lawyers/collab/channel/index.vue'
import ExternalOrgPage from '@/views/lawyers/collab/externalOrg/index.vue'
import SlaPolicyPage from '@/views/lawyers/collab/slaPolicy/index.vue'
import TransferPage from '@/views/lawyers/collab/transfer/index.vue'
import { listChannelIdentity } from '@/api/lawyers/channelIdentity'
import { listExternalOrg } from '@/api/lawyers/externalOrg'
import { listSlaPolicy } from '@/api/lawyers/slaPolicy'
import { listTransfer } from '@/api/lawyers/ticketTransfer'

jest.mock('@/api/lawyers/channelIdentity', () => ({
  listChannelIdentity: jest.fn(),
  requestBind: jest.fn(),
  confirmBind: jest.fn(),
  unbindIdentity: jest.fn()
}))

jest.mock('@/api/lawyers/externalOrg', () => ({
  listExternalOrg: jest.fn(),
  getExternalOrg: jest.fn(),
  addExternalOrg: jest.fn(),
  updateExternalOrg: jest.fn(),
  delExternalOrg: jest.fn()
}))

jest.mock('@/api/lawyers/slaPolicy', () => ({
  listSlaPolicy: jest.fn(),
  getSlaPolicy: jest.fn(),
  addSlaPolicy: jest.fn(),
  updateSlaPolicy: jest.fn(),
  delSlaPolicy: jest.fn()
}))

jest.mock('@/api/lawyers/ticketTransfer', () => ({
  listTransfer: jest.fn(),
  getTransfer: jest.fn(),
  createTransfer: jest.fn(),
  retryTransfer: jest.fn(),
  callerTimeline: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})
// main.js 全局挂载的工具方法
localVue.prototype.parseTime = () => '2026-11-01 09:00'
localVue.prototype.resetForm = () => {}

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const modal = {
  msgSuccess: jest.fn(),
  msgError: jest.fn(),
  msgWarning: jest.fn(),
  dangerConfirm: jest.fn(() => Promise.resolve())
}

/** 全局注册组件的统一 stub，聚焦页面骨架迁移本身 */
const globalStubs = {
  'right-toolbar': { template: '<div class="rt-stub"><slot name="toolbar-right" /></div>' },
  pagination: { template: '<div class="pagination-stub" />' },
  TableEmpty: {
    template: '<div class="empty-stub">{{ text }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  }
}

function mountPage(Page) {
  return mount(Page, {
    localVue,
    stubs: globalStubs,
    mocks: { $modal: modal }
  })
}

/** 点击 ProTable 查询区的「重置」按钮（搜索在前、重置在后） */
async function clickReset(wrapper) {
  const resetBtn = wrapper.findAll('.el-form .el-button')
    .wrappers.find(btn => btn.text().includes('重置'))
  expect(resetBtn).toBeTruthy()
  await resetBtn.trigger('click')
  await flushPromises()
}

describe('collab 协同域列表页（ProTable 迁移）', () => {
  let wrapper

  beforeEach(() => {
    localStorage.clear()
    jest.clearAllMocks()
  })

  afterEach(() => {
    if (wrapper) wrapper.destroy()
    wrapper = null
  })

  describe('channel 渠道身份', () => {
    const rows = [
      { id: 1, profileId: 1001, channelType: 'WECHAT_MP', channelUid: 'openid-a', channelNickname: '张律师微信', bindStatus: '0', bindConfirmTime: '2026-10-01 10:00' },
      { id: 2, profileId: 1002, channelType: 'H5', channelUid: 'h5-b', channelNickname: '', bindStatus: '1' }
    ]

    it('初始化按默认分页加载，渠道类型经 channelText 渲染', async () => {
      listChannelIdentity.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(ChannelPage)
      await flushPromises()

      expect(listChannelIdentity).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 10,
        profileId: undefined,
        channelType: undefined,
        bindStatus: undefined
      })

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('微信公众号') // channelText 反查标签
      expect(body).toContain('张律师微信')
      expect(body).toContain('已绑定')
      expect(body).toContain('已解绑')
    })

    it('按绑定状态展示「确认绑定 / 解绑」操作按钮', async () => {
      listChannelIdentity.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(ChannelPage)
      await flushPromises()

      const bodyText = wrapper.find('.el-table__body').text()
      expect(bodyText).toContain('解绑')
      expect(bodyText).toContain('确认绑定')
    })

    it('重置后清空筛选条件并回到第一页重查', async () => {
      listChannelIdentity.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(ChannelPage)
      await flushPromises()

      wrapper.vm.queryParams.profileId = 1001
      wrapper.vm.queryParams.channelType = 'PHONE'
      wrapper.vm.queryParams.pageNum = 2
      await clickReset(wrapper)

      expect(listChannelIdentity).toHaveBeenCalledTimes(2)
      expect(listChannelIdentity.mock.calls[1][0]).toEqual({
        pageNum: 1,
        pageSize: 10,
        profileId: undefined,
        channelType: undefined,
        bindStatus: undefined
      })
      expect(wrapper.vm.queryParams.pageNum).toBe(1)
    })
  })

  describe('externalOrg 协同机构', () => {
    const rows = [
      { orgId: 1, orgName: '市法律援助中心', externalType: 'LEGAL_AID', province: '广东省', city: '广州市', district: '越秀区', accessMode: 'API', contactPhone: '020-12345678', status: '0' },
      { orgId: 2, orgName: '区人民调解委', externalType: 'MEDIATION', accessMode: 'MANUAL', status: '1' }
    ]

    it('属地列按省市县合成，缺失时降级为 -', async () => {
      listExternalOrg.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(ExternalOrgPage)
      await flushPromises()

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('广东省 / 广州市 / 越秀区')
      expect(body).toContain('法律援助')
      expect(body).toContain('启用')
      expect(body).toContain('停用')
    })

    it('多选联动的批量删除按钮可用状态', async () => {
      listExternalOrg.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(ExternalOrgPage)
      await flushPromises()

      expect(wrapper.vm.multiple).toBe(true)
      wrapper.vm.handleSelectionChange([{ orgId: 1 }, { orgId: 2 }])
      expect(wrapper.vm.ids).toEqual([1, 2])
      expect(wrapper.vm.multiple).toBe(false)
      wrapper.vm.handleSelectionChange([])
      expect(wrapper.vm.multiple).toBe(true)
    })

    it('重置后清空筛选条件并重查', async () => {
      listExternalOrg.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(ExternalOrgPage)
      await flushPromises()

      wrapper.vm.queryParams.orgName = '法援'
      wrapper.vm.queryParams.city = '广州市'
      await clickReset(wrapper)

      expect(listExternalOrg).toHaveBeenCalledTimes(2)
      expect(listExternalOrg.mock.calls[1][0]).toEqual({
        pageNum: 1,
        pageSize: 10,
        orgName: undefined,
        externalType: undefined,
        city: undefined,
        status: undefined
      })
    })
  })

  describe('slaPolicy SLA 策略', () => {
    const rows = [
      { policyId: 1, policyName: '普通工单SLA', bizType: 'TICKET', priority: '2', respondMinutes: 30, resolveMinutes: 120, warnThreshold: 80, escalateRoles: 'ai_team_leader,ai_manager', status: '0' },
      { policyId: 2, policyName: '紧急法援SLA', bizType: 'LEGAL_AID', priority: '1', respondMinutes: 10, resolveMinutes: 60, warnThreshold: 60, escalateRoles: '', status: '1' }
    ]

    it('时限列带单位、优先级标签与角色链拆分渲染', async () => {
      listSlaPolicy.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(SlaPolicyPage)
      await flushPromises()

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('热线工单') // bizText
      expect(body).toContain('30 分钟')
      expect(body).toContain('120 分钟')
      expect(body).toContain('80%')
      expect(body).toContain('紧急')
      expect(body).toContain('ai_team_leader')
      expect(body).toContain('ai_manager')
    })

    it('重置后清空筛选条件并重查', async () => {
      listSlaPolicy.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(SlaPolicyPage)
      await flushPromises()

      wrapper.vm.queryParams.policyName = 'SLA'
      wrapper.vm.queryParams.bizType = 'TICKET'
      await clickReset(wrapper)

      expect(listSlaPolicy).toHaveBeenCalledTimes(2)
      expect(listSlaPolicy.mock.calls[1][0]).toEqual({
        pageNum: 1,
        pageSize: 10,
        policyName: undefined,
        bizType: undefined,
        status: undefined
      })
    })
  })

  describe('transfer 转办流水', () => {
    const rows = [
      { transferId: 1, ticketNo: 'T202611010001', direction: 'OUT', externalType: 'LEGAL_AID', orgName: '市法援中心', externalTicketNo: 'EXT-1', externalStatus: 'DONE', transferStatus: '2', retryCount: 1, transferTime: '2026-11-01 09:00' },
      { transferId: 2, ticketNo: 'T202611010002', direction: 'IN', externalType: 'MEDIATION', orgName: '', externalTicketNo: '', externalStatus: 'PENDING', transferStatus: '1', retryCount: 0, transferTime: '2026-11-01 10:00' }
    ]

    it('方向/流水状态走标签插槽，重试按钮仅转出未完成流水展示', async () => {
      listTransfer.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(TransferPage)
      await flushPromises()

      expect(listTransfer).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 10,
        ticketNo: undefined,
        direction: undefined,
        externalType: undefined,
        transferStatus: undefined
      })

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('转出')
      expect(body).toContain('转入')
      expect(body).toContain('市法援中心')
      expect(body).toContain('法律援助')
      expect(body).toContain('失败')
      expect(body).toContain('成功')

      const retryButtons = wrapper.findAll('.el-table__body .el-button')
        .wrappers.filter(btn => btn.text().includes('重试'))
      expect(retryButtons.length).toBe(1)
    })

    it('重置后清空筛选条件并重查', async () => {
      listTransfer.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(TransferPage)
      await flushPromises()

      wrapper.vm.queryParams.ticketNo = 'T2026'
      wrapper.vm.queryParams.direction = 'OUT'
      wrapper.vm.queryParams.transferStatus = '2'
      await clickReset(wrapper)

      expect(listTransfer).toHaveBeenCalledTimes(2)
      expect(listTransfer.mock.calls[1][0]).toEqual({
        pageNum: 1,
        pageSize: 10,
        ticketNo: undefined,
        direction: undefined,
        externalType: undefined,
        transferStatus: undefined
      })
    })
  })
})
