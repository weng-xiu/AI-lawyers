'use strict'

/**
 * P5① 公众端组件单测：首页 Home
 * 覆盖：首屏主路径、场景入口数量、热线兜底、欢迎播报、常见问题加载与失败静默、外链安全属性。
 */
jest.mock('@/utils/a11y', () => ({ announce: jest.fn() }))
jest.mock('@/api/portal', () => ({ getFaq: jest.fn() }))

import { mount, RouterLinkStub } from '@vue/test-utils'
import Home from '@/views/Home'
import { announce } from '@/utils/a11y'
import { getFaq } from '@/api/portal'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const mountHome = () => mount(Home, {
  stubs: { RouterLink: RouterLinkStub }
})

describe('Home', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getFaq.mockResolvedValue({ data: [] })
  })

  it('渲染首屏两条主路径与热线兜底', () => {
    const wrapper = mountHome()
    expect(wrapper.find('#home-title').text()).toContain('法律问题')
    const heroLinks = wrapper.findAll('.hero-btn')
    expect(heroLinks.at(0).text()).toContain('开始免费咨询')
    expect(heroLinks.at(1).attributes('href')).toBe('tel:12348')
  })

  it('渲染五个场景入口', () => {
    const wrapper = mountHome()
    const cards = wrapper.findAll('.scene-card')
    expect(cards).toHaveLength(5)
    expect(cards.at(0).text()).toContain('在线法律咨询')
    expect(cards.at(4).text()).toContain('长辈服务专区')
  })

  it('进入首页时播报欢迎语', () => {
    mountHome()
    expect(announce).toHaveBeenCalledWith(
      expect.stringContaining('欢迎使用 AI 律师公众服务'),
      'polite'
    )
  })

  it('按 limit=6 拉取常见问题并渲染卡片', async () => {
    getFaq.mockResolvedValue({
      data: [{ knowledgeId: 1, title: '劳动合同到期不续签', summary: '可主张经济补偿' }]
    })
    const wrapper = mountHome()
    await flushPromises()
    expect(getFaq).toHaveBeenCalledWith({ limit: 6 })
    const cards = wrapper.findAll('.faq-card')
    expect(cards).toHaveLength(1)
    expect(cards.at(0).text()).toContain('劳动合同到期不续签')
  })

  it('常见问题接口失败时静默隐藏区块', async () => {
    getFaq.mockRejectedValue(new Error('network'))
    const wrapper = mountHome()
    await flushPromises()
    expect(wrapper.vm.faqs).toEqual([])
    expect(wrapper.find('.faq-grid').exists()).toBe(false)
  })

  it('法规查询外链带 noopener noreferrer', () => {
    const wrapper = mountHome()
    const external = wrapper.findAll('.scene-card').wrappers.find(w => w.attributes('href'))
    expect(external.attributes('href')).toContain('flk.npc.gov.cn')
    expect(external.attributes('target')).toBe('_blank')
    expect(external.attributes('rel')).toBe('noopener noreferrer')
  })
})
