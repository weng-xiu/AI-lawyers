'use strict'

/**
 * Crontab 结果面板单测：覆盖近 5 次运行时间推算、无结果/不足 5 条提示，
 * 以及表达式规则解析的基础方法。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import CrontabResult from '@/components/Crontab/result.vue'

const localVue = createLocalVue()

function mountResult(ex) {
  return mount(CrontabResult, { localVue, propsData: { ex } })
}

describe('Crontab/result', () => {
  test('合法表达式生成近 5 次运行时间', () => {
    const wrapper = mountResult('0 0 0 * * ?')

    expect(wrapper.vm.isShow).toBe(true)
    expect(wrapper.vm.resultList).toHaveLength(5)
    wrapper.vm.resultList.forEach(item => {
      expect(item).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    })
    expect(wrapper.find('.popup-result-scroll li').exists()).toBe(true)
  })

  test('100 年内不足 5 条时追加提示', () => {
    const wrapper = mountResult('0 0 0 1 6 ? 2099')

    expect(wrapper.vm.resultList).toEqual([
      '2099-06-01 00:00:00',
      '最近100年内只有上面1条结果！'
    ])
  })

  test('无合法日期时提示没有结果', () => {
    const wrapper = mountResult('0 0 0 31 2 ?')

    expect(wrapper.vm.resultList).toEqual(['没有达到条件的结果！'])
  })

  test('工作日/最后一天/星期规则均能推算结果', () => {
    expect(mountResult('0 0 0 15W * ?').vm.resultList).toHaveLength(5)
    expect(mountResult('0 0 0 L * ?').vm.resultList).toHaveLength(5)
    expect(mountResult('0 0 0 ? * 2-4').vm.resultList).toHaveLength(5)
    expect(mountResult('0 0 0 ? * 3#2').vm.resultList).toHaveLength(5)
    expect(mountResult('0 0 0 ? * 5L').vm.resultList).toHaveLength(5)
  })

  test('计算中占位文案', async () => {
    const wrapper = mountResult('0 0 0 * * ?')

    wrapper.setData({ isShow: false })
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.popup-result-scroll li').text()).toContain('计算结果中...')
  })

  test('顺序/指定/平均规则数组', () => {
    const vm = mountResult('0 0 0 * * ?').vm

    expect(vm.getOrderArr(3, 6)).toEqual([3, 4, 5, 6])
    expect(vm.getAssignArr('5,1,3')).toEqual([1, 3, 5])
    expect(vm.getAverageArr('0/15', 59)).toEqual([0, 15, 30, 45])
  })

  test('周期规则支持跨周期与从 1 开始', () => {
    const vm = mountResult('0 0 0 * * ?').vm

    expect(vm.getCycleArr('22-2', 24, true)).toEqual([0, 1, 2, 22, 23])
    expect(vm.getCycleArr('0-3', 12, false)).toEqual([1, 2, 3, 12])
  })

  test('getIndex 边界与区间定位', () => {
    const vm = mountResult('0 0 0 * * ?').vm

    expect(vm.getIndex([0, 10, 20], 0)).toBe(0)
    expect(vm.getIndex([0, 10, 20], 15)).toBe(2)
    expect(vm.getIndex([0, 10, 20], 25)).toBe(0)
  })

  test('compare 用于数字升序排序', () => {
    const vm = mountResult('0 0 0 * * ?').vm

    expect([3, 1, 2].sort(vm.compare)).toEqual([1, 2, 3])
  })

  test('formatDate 输出完整时间与 quartz 星期', () => {
    const vm = mountResult('0 0 0 * * ?').vm
    const date = new Date(2026, 0, 5, 8, 9, 10)

    expect(vm.formatDate(date)).toBe('2026-01-05 08:09:10')
    expect(vm.formatDate(date.getTime())).toBe('2026-01-05 08:09:10')
    // 2026-01-05 为周一，quartz 中周一为 2
    expect(vm.formatDate(date, 'week')).toBe(2)
  })

  test('checkDate 识别非法日期', () => {
    const vm = mountResult('0 0 0 * * ?').vm

    expect(vm.checkDate('2026-02-28 00:00:00')).toBe(true)
    expect(vm.checkDate('2026-02-30 00:00:00')).toBe(false)
  })

  test('getDayArr 解析工作日/最后一天/指定日期', () => {
    const vm = mountResult('0 0 0 * * ?').vm

    vm.getDayArr('15W')
    expect(vm.dayRule).toBe('workDay')
    expect(vm.dayRuleSup).toBe(15)
    expect(vm.dateArr[3]).toEqual([15])

    vm.getDayArr('L')
    expect(vm.dayRule).toBe('lastDay')
    expect(vm.dateArr[3]).toEqual([31])

    vm.getDayArr('2/10')
    expect(vm.dateArr[3]).toEqual([2, 12, 22])
    expect(vm.dayRuleSup).toBe('null')
  })

  test('getWeekArr 解析第几周/最后一周/星期区间', () => {
    const vm = mountResult('0 0 0 * * ?').vm

    vm.getDayArr('?')
    vm.getWeekArr('3#2')
    expect(vm.dayRule).toBe('assWeek')
    expect(vm.dayRuleSup).toEqual([2, 3])
    expect(vm.dateArr[3]).toEqual([1])

    vm.getDayArr('?')
    vm.getWeekArr('5L')
    expect(vm.dayRule).toBe('lastWeek')
    expect(vm.dayRuleSup).toBe(5)
    expect(vm.dateArr[3]).toEqual([31])

    vm.getDayArr('?')
    vm.getWeekArr('2-4')
    expect(vm.dayRule).toBe('weekDay')
    expect(vm.dayRuleSup).toEqual([2, 3, 4])
  })

  test('秒/分/时规则数组构建', () => {
    const vm = mountResult('0 0 0 * * ?').vm

    vm.getSecondArr('30')
    expect(vm.dateArr[0]).toEqual([30])

    vm.getMinArr('*')
    expect(vm.dateArr[1]).toHaveLength(60)

    vm.getHourArr('8-10')
    expect(vm.dateArr[2]).toEqual([8, 9, 10])
  })
})
