/**
 * V4：ECharts「judicial」司法蓝主题（依据 配色.md 数据可视化规范）。
 *
 * 主序列：#1A3C6E → #3B73B3 → #6A9FD8 → #A9C9EB（单色渐变）
 * 对比强调：#C9A96E（突出最高峰值 / 重要 KPI）
 * 辅助序列：#54A68B（绿）/ #E8923A（橙）/ #9B6EAA（紫灰）/ #D98C8C（粉棕）
 * 中性色：文本 #1F2A3A / #5A6A7E，边框 #DCE2EB，分隔线 #EDF0F5
 *
 * 注册方式（main.js）：echarts.registerTheme('judicial', judicialTheme)
 * 使用方式：echarts.init(el, 'judicial')
 */
export default {
  color: [
    '#1A3C6E', '#3B73B3', '#6A9FD8', '#A9C9EB', '#C9A96E',
    '#54A68B', '#E8923A', '#9B6EAA', '#D98C8C'
  ],
  backgroundColor: 'transparent',
  textStyle: {},
  title: {
    textStyle: { color: '#1F2A3A', fontWeight: 600 },
    subtextStyle: { color: '#5A6A7E' }
  },
  legend: {
    textStyle: { color: '#5A6A7E' },
    inactiveColor: '#B0BCCA'
  },
  tooltip: {
    backgroundColor: '#FFFFFF',
    borderColor: '#DCE2EB',
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: '#1F2A3A' },
    extraCssText: 'box-shadow: 0 2px 8px rgba(26, 60, 110, 0.15);',
    axisPointer: {
      lineStyle: { color: '#B0BCCA' },
      crossStyle: { color: '#B0BCCA' },
      shadowStyle: { color: 'rgba(26, 60, 110, 0.06)' }
    }
  },
  categoryAxis: {
    axisLine: { show: true, lineStyle: { color: '#DCE2EB' } },
    axisTick: { show: true, lineStyle: { color: '#DCE2EB' } },
    axisLabel: { color: '#5A6A7E' },
    splitLine: { show: false, lineStyle: { color: ['#EDF0F5'] } },
    splitArea: { show: false, areaStyle: { color: ['rgba(26, 60, 110, 0.02)'] } }
  },
  valueAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#5A6A7E' },
    splitLine: { show: true, lineStyle: { color: ['#EDF0F5'], type: 'dashed' } },
    splitArea: { show: false }
  },
  timeAxis: {
    axisLine: { show: true, lineStyle: { color: '#DCE2EB' } },
    axisTick: { show: false },
    axisLabel: { color: '#5A6A7E' },
    splitLine: { show: true, lineStyle: { color: ['#EDF0F5'], type: 'dashed' } }
  },
  logAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#5A6A7E' },
    splitLine: { show: true, lineStyle: { color: ['#EDF0F5'], type: 'dashed' } }
  },
  dataZoom: {
    backgroundColor: 'rgba(26, 60, 110, 0.04)',
    dataBackgroundColor: '#DCE2EB',
    fillerColor: 'rgba(26, 60, 110, 0.12)',
    handleColor: '#1A3C6E',
    textStyle: { color: '#5A6A7E' }
  },
  timeline: {
    lineStyle: { color: '#DCE2EB' },
    controlStyle: { color: '#5A6A7E', borderColor: '#B0BCCA' },
    itemStyle: { color: '#3B73B3' },
    checkpointStyle: { color: '#C9A96E', borderColor: '#1A3C6E' },
    label: { color: '#5A6A7E' }
  },
  markLine: {
    lineStyle: { type: 'dashed', color: '#E8923A' },
    label: { color: '#5A6A7E' },
    emphasis: { lineStyle: { color: '#C63D4A' } }
  },
  // 常用系列默认样式
  line: {
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: { width: 2 },
    emphasis: { focus: 'series' }
  },
  bar: {
    barMaxWidth: 32,
    itemStyle: { borderRadius: [3, 3, 0, 0] },
    emphasis: { focus: 'series' }
  },
  pie: {
    radius: ['40%', '65%'],
    itemStyle: {
      borderColor: '#FFFFFF',
      borderWidth: 2
    },
    label: { color: '#5A6A7E' },
    emphasis: {
      scaleSize: 6,
      label: { color: '#1F2A3A' }
    }
  },
  funnel: {
    label: { color: '#5A6A7E' }
  },
  gauge: {
    axisLine: { lineStyle: { color: [[1, '#DCE2EB']] } },
    axisTick: { lineStyle: { color: '#B0BCCA' } },
    axisLabel: { color: '#5A6A7E' },
    detail: { color: '#1F2A3A' }
  },
  graph: {
    lineStyle: { color: '#DCE2EB' },
    label: { color: '#1F2A3A' }
  },
  radar: {
    name: { textStyle: { color: '#5A6A7E' } },
    splitArea: { areaStyle: { color: ['#FFFFFF', '#F5F7FA'] } },
    splitLine: { lineStyle: { color: '#DCE2EB' } },
    axisLine: { lineStyle: { color: '#DCE2EB' } }
  },
  candlestick: {
    itemStyle: {
      color: '#2B8C6E',
      color0: '#C63D4A',
      borderColor: '#2B8C6E',
      borderColor0: '#C63D4A'
    }
  }
}
