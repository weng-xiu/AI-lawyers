'use strict'

/**
 * Crontab 子面板单测：覆盖秒/分/时/日/月/周/年面板的表达式拼装、
 * 数值范围收敛与跨字段联动更新。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import CrontabSecond from '@/components/Crontab/second.vue'
import CrontabMin from '@/components/Crontab/min.vue'
import CrontabHour from '@/components/Crontab/hour.vue'
import CrontabDay from '@/components/Crontab/day.vue'
import CrontabMonth from '@/components/Crontab/month.vue'
import CrontabWeek from '@/components/Crontab/week.vue'
import CrontabYear from '@/components/Crontab/year.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

// 与 Crontab/index.vue 中 checkNumber 完全一致的取整/限界逻辑
function checkNumber(value, minLimit, maxLimit) {
  value = Math.floor(value)
  if (value < minLimit) {
    value = minLimit
  } else if (value > maxLimit) {
    value = maxLimit
  }
  return value
}

function mountPart(component, propsData = {}) {
  return mount(component, {
    localVue,
    propsData: Object.assign({ check: checkNumber, cron: { day: '?', week: '?' } }, propsData)
  })
}

describe('Crontab 子面板', () => {
  describe('second.vue', () => {
    test('默认周期与平均表达式', () => {
      const wrapper = mountPart(CrontabSecond)

      expect(wrapper.vm.cycleTotal).toBe('1-2')
      expect(wrapper.vm.averageTotal).toBe('0/1')
      expect(wrapper.vm.checkboxString).toBe('*')
    })

    test('通配符模式携带来源参数 second', () => {
      const wrapper = mountPart(CrontabSecond)

      wrapper.vm.radioChange()

      expect(wrapper.emitted('update')).toEqual([['second', '*', 'second']])
    })

    test('周期/平均/指定三种取值拼装', () => {
      const cycleWrapper = mountPart(CrontabSecond)
      cycleWrapper.vm.radioValue = 2
      cycleWrapper.vm.cycle01 = 5
      cycleWrapper.vm.cycle02 = 10
      cycleWrapper.vm.radioChange()
      expect(cycleWrapper.emitted('update')).toEqual([['second', '5-10']])

      const averageWrapper = mountPart(CrontabSecond)
      averageWrapper.vm.radioValue = 3
      averageWrapper.vm.average01 = 5
      averageWrapper.vm.average02 = 10
      averageWrapper.vm.radioChange()
      expect(averageWrapper.emitted('update')).toEqual([['second', '5/10']])

      const checkboxWrapper = mountPart(CrontabSecond)
      checkboxWrapper.vm.radioValue = 4
      checkboxWrapper.vm.checkboxList = [1, 2, 3]
      checkboxWrapper.vm.radioChange()
      expect(checkboxWrapper.emitted('update')).toEqual([['second', '1,2,3']])
    })

    test('radioParent 变化时同步单选值', async () => {
      const wrapper = mountPart(CrontabSecond)

      wrapper.setProps({ radioParent: 3 })
      await wrapper.vm.$nextTick()

      expect(wrapper.vm.radioValue).toBe(3)
    })
  })

  describe('min.vue', () => {
    test('各取值均携带来源参数 min', () => {
      const wrapper = mountPart(CrontabMin)
      wrapper.vm.radioValue = 2
      wrapper.vm.cycle01 = 3
      wrapper.vm.cycle02 = 8
      wrapper.vm.radioChange()

      expect(wrapper.emitted('update')).toEqual([['min', '3-8', 'min']])
    })

    test('平均表达式按起始值与步长拼装', () => {
      const wrapper = mountPart(CrontabMin)
      wrapper.vm.radioValue = 3
      wrapper.vm.average01 = 0
      wrapper.vm.average02 = 15
      wrapper.vm.radioChange()

      expect(wrapper.emitted('update')).toEqual([['min', '0/15', 'min']])
    })
  })

  describe('hour.vue', () => {
    test('切换小时规则时同步收敛分/秒', () => {
      const wrapper = mountPart(CrontabHour, { cron: { min: '*', second: '*' } })

      wrapper.vm.radioChange()

      expect(wrapper.emitted('update')).toEqual([
        ['min', '0', 'hour'],
        ['second', '0', 'hour'],
        ['hour', '*']
      ])
    })

    test('周期与平均表达式', () => {
      const cycleWrapper = mountPart(CrontabHour)
      cycleWrapper.vm.radioValue = 2
      cycleWrapper.vm.cycle01 = 8
      cycleWrapper.vm.cycle02 = 18
      cycleWrapper.vm.radioChange()
      expect(cycleWrapper.emitted('update')).toEqual([['hour', '8-18']])

      const averageWrapper = mountPart(CrontabHour)
      averageWrapper.vm.radioValue = 3
      averageWrapper.vm.average01 = 2
      averageWrapper.vm.average02 = 4
      averageWrapper.vm.radioChange()
      expect(averageWrapper.emitted('update')).toEqual([['hour', '2/4']])
    })

    test('指定小时多选拼装', () => {
      const wrapper = mountPart(CrontabHour)
      wrapper.vm.radioValue = 4
      wrapper.vm.checkboxList = [0, 12]
      wrapper.vm.radioChange()

      expect(wrapper.emitted('update')).toEqual([['hour', '0,12']])
    })
  })

  describe('day.vue', () => {
    test('星期未指定时联动改为 ?', () => {
      const wrapper = mountPart(CrontabDay, { cron: { week: '*' } })
      wrapper.vm.radioValue = 3
      wrapper.vm.cycle01 = 1
      wrapper.vm.cycle02 = 5
      wrapper.vm.radioChange()

      expect(wrapper.emitted('update')).toEqual([
        ['week', '?', 'day'],
        ['day', '1-5']
      ])
    })

    test('通配符/不指定/最近工作日/最后一天取值', () => {
      const wildcard = mountPart(CrontabDay)
      wildcard.vm.radioChange()
      expect(wildcard.emitted('update')).toEqual([['day', '*']])

      const none = mountPart(CrontabDay)
      none.vm.radioValue = 2
      none.vm.radioChange()
      expect(none.emitted('update')).toEqual([['day', '?']])

      const workday = mountPart(CrontabDay)
      workday.vm.radioValue = 5
      workday.vm.workday = 3
      workday.vm.radioChange()
      expect(workday.emitted('update')).toEqual([['day', '3W']])

      const lastDay = mountPart(CrontabDay)
      lastDay.vm.radioValue = 6
      lastDay.vm.radioChange()
      expect(lastDay.emitted('update')).toEqual([['day', 'L']])
    })

    test('平均与指定日期拼装', () => {
      const averageWrapper = mountPart(CrontabDay)
      averageWrapper.vm.radioValue = 4
      averageWrapper.vm.average01 = 2
      averageWrapper.vm.average02 = 3
      averageWrapper.vm.radioChange()
      expect(averageWrapper.emitted('update')).toEqual([['day', '2/3']])

      const checkboxWrapper = mountPart(CrontabDay)
      checkboxWrapper.vm.radioValue = 7
      checkboxWrapper.vm.checkboxList = [1, 3]
      checkboxWrapper.vm.radioChange()
      expect(checkboxWrapper.emitted('update')).toEqual([['day', '1,3']])
    })
  })

  describe('month.vue', () => {
    test('四种月份规则取值', () => {
      const wildcard = mountPart(CrontabMonth)
      wildcard.vm.radioChange()
      expect(wildcard.emitted('update')).toEqual([['month', '*']])

      const cycleWrapper = mountPart(CrontabMonth)
      cycleWrapper.vm.radioValue = 2
      cycleWrapper.vm.cycle01 = 3
      cycleWrapper.vm.cycle02 = 6
      cycleWrapper.vm.radioChange()
      expect(cycleWrapper.emitted('update')).toEqual([['month', '3-6']])

      const averageWrapper = mountPart(CrontabMonth)
      averageWrapper.vm.radioValue = 3
      averageWrapper.vm.average01 = 2
      averageWrapper.vm.average02 = 3
      averageWrapper.vm.radioChange()
      expect(averageWrapper.emitted('update')).toEqual([['month', '2/3']])

      const checkboxWrapper = mountPart(CrontabMonth)
      checkboxWrapper.vm.radioValue = 4
      checkboxWrapper.vm.checkboxList = [1, 4]
      checkboxWrapper.vm.radioChange()
      expect(checkboxWrapper.emitted('update')).toEqual([['month', '1,4']])
    })
  })

  describe('week.vue', () => {
    test('默认不指定星期', () => {
      const wrapper = mountPart(CrontabWeek)

      expect(wrapper.vm.radioValue).toBe(2)
      wrapper.vm.radioChange()

      expect(wrapper.emitted('update')).toEqual([['week', '?']])
    })

    test('日期未指定时联动改为 ?', () => {
      const wrapper = mountPart(CrontabWeek, { cron: { day: '*' } })
      wrapper.vm.radioValue = 5
      wrapper.vm.weekday = 6
      wrapper.vm.radioChange()

      expect(wrapper.emitted('update')).toEqual([
        ['day', '?', 'week'],
        ['week', '6L']
      ])
    })

    test('周期/第几周/最后一周/指定取值', () => {
      const cycleWrapper = mountPart(CrontabWeek)
      cycleWrapper.vm.radioValue = 3
      cycleWrapper.vm.cycle01 = 2
      cycleWrapper.vm.cycle02 = 5
      cycleWrapper.vm.radioChange()
      expect(cycleWrapper.emitted('update')).toEqual([['week', '2-5']])

      const averageWrapper = mountPart(CrontabWeek)
      averageWrapper.vm.radioValue = 4
      averageWrapper.vm.average01 = 2
      averageWrapper.vm.average02 = 4
      averageWrapper.vm.radioChange()
      expect(averageWrapper.emitted('update')).toEqual([['week', '4#2']])

      const checkboxWrapper = mountPart(CrontabWeek)
      checkboxWrapper.vm.radioValue = 6
      checkboxWrapper.vm.checkboxList = ['2', '5']
      checkboxWrapper.vm.radioChange()
      expect(checkboxWrapper.emitted('update')).toEqual([['week', '2,5']])
    })
  })

  describe('year.vue', () => {
    test('挂载后以当前年份作为默认区间起点', () => {
      const wrapper = mountPart(CrontabYear)
      const currentYear = new Date().getFullYear()

      expect(wrapper.vm.fullYear).toBe(currentYear)
      expect(wrapper.vm.cycle01).toBe(currentYear)
      expect(wrapper.vm.average01).toBe(currentYear)
    })

    test('不填与每年取值', () => {
      const empty = mountPart(CrontabYear)
      empty.vm.radioChange()
      expect(empty.emitted('update')).toEqual([['year', '']])

      const everyYear = mountPart(CrontabYear)
      everyYear.vm.radioValue = 2
      everyYear.vm.radioChange()
      expect(everyYear.emitted('update')).toEqual([['year', '*']])
    })

    test('周期/平均/指定年份取值', () => {
      const currentYear = new Date().getFullYear()

      const cycleWrapper = mountPart(CrontabYear)
      cycleWrapper.vm.radioValue = 3
      cycleWrapper.vm.cycle01 = currentYear
      cycleWrapper.vm.cycle02 = currentYear + 4
      cycleWrapper.vm.radioChange()
      expect(cycleWrapper.emitted('update')).toEqual([['year', `${currentYear}-${currentYear + 4}`]])

      const averageWrapper = mountPart(CrontabYear)
      averageWrapper.vm.radioValue = 4
      averageWrapper.vm.average01 = currentYear
      averageWrapper.vm.average02 = 3
      averageWrapper.vm.radioChange()
      expect(averageWrapper.emitted('update')).toEqual([['year', `${currentYear}/3`]])

      const checkboxWrapper = mountPart(CrontabYear)
      checkboxWrapper.vm.radioValue = 5
      checkboxWrapper.vm.checkboxList = [currentYear, currentYear + 1]
      checkboxWrapper.vm.radioChange()
      expect(checkboxWrapper.emitted('update')).toEqual([['year', `${currentYear},${currentYear + 1}`]])
    })

    test('未勾选年份时为空串而非通配符', () => {
      const wrapper = mountPart(CrontabYear)

      expect(wrapper.vm.checkboxString).toBe('')
    })
  })
})
