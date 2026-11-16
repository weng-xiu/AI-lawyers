'use strict'

// P5① 公众端组件单测：Home/TicketProgress/Messages/MyTickets/ElderlyZone
// 运行：npm run test:unit（Node 18+，依赖 jest 29 + @vue/vue2-jest + @vue/test-utils v1）
module.exports = {
  testEnvironment: 'jsdom',
  testEnvironmentOptions: {
    // jsdom 默认 url 为 about:blank，统一模拟公众端同源，便于路由与滚动相关断言
    url: 'http://localhost/'
  },
  moduleFileExtensions: ['js', 'json', 'vue'],
  transform: {
    '^.+\\.vue$': '@vue/vue2-jest',
    '^.+\\.js$': 'babel-jest'
  },
  transformIgnorePatterns: ['/node_modules/'],
  moduleNameMapper: {
    // 组件内 <style scoped> 由 vue2-jest 剥离；样式引用映射到空 stub
    '\\.(css|scss|sass)$': '<rootDir>/tests/unit/styleStub.js',
    '^@/(.*)$': '<rootDir>/src/$1'
  },
  testMatch: ['<rootDir>/tests/unit/**/*.spec.js'],
  verbose: true
}
