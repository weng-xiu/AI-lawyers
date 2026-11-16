'use strict'

/**
 * P5① 公众端组件单测：长辈服务专区 ElderlyZone
 * 覆盖：五个大按钮入口、热线速达、进入专区播报。
 */
jest.mock('@/utils/a11y', () => ({ announce: jest.fn() }))

import { mount, createLocalVue, RouterLinkStub } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ElderlyZone from '@/views/ElderlyZone'
import { announce } from '@/utils/a11y'

const localVue = createLocalVue()
localVue.use(ElementUI)

const mountZone = () => mount(ElderlyZone, {
  localVue,
  stubs: { RouterLink: RouterLinkStub }
})

describe('ElderlyZone', () => {
  beforeEach(() => jest.clearAllMocks())

  it('渲染五个长辈常用服务入口', () => {
    const wrapper = mountZone()
    const cards = wrapper.findAll('.zone-card')
    expect(cards).toHaveLength(5)
    expect(cards.wrappers.map(w => w.find('.zone-name').text()))
      .toEqual(['我要咨询', '查询进度', '电话求助', '找服务机构', '我的咨询'])
  })

  it('热线卡片提供 12348 与 12345 大字拨号', () => {
    const wrapper = mountZone()
    const numbers = wrapper.findAll('.hotline-number')
    expect(numbers).toHaveLength(2)
    expect(numbers.at(0).attributes('href')).toBe('tel:12348')
    expect(numbers.at(1).attributes('href')).toBe('tel:12345')
  })

  it('进入专区时播报适老引导', () => {
    mountZone()
    expect(announce).toHaveBeenCalledWith(
      expect.stringContaining('已进入长辈服务专区'),
      'polite'
    )
  })
})
