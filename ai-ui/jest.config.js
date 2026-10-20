'use strict'

// P8 前端质量保障：V3 三件套（ProTable/ProForm/ProDialog）组件单测
// 运行：npm run test:unit（Node 18+，依赖 jest 29 + @vue/vue2-jest + @vue/test-utils v1）
module.exports = {
  testEnvironment: 'jsdom',
  testEnvironmentOptions: {
    // jsdom 默认 url 为 about:blank，统一模拟管理端同源，便于 localStorage 持久化测试
    url: 'http://localhost/'
  },
  moduleFileExtensions: ['js', 'json', 'vue'],
  transform: {
    '^.+\\.vue$': '@vue/vue2-jest',
    '^.+\\.js$': 'babel-jest'
  },
  transformIgnorePatterns: ['/node_modules/'],
  moduleNameMapper: {
    // 组件内 <style lang="scss"> 由 vue2-jest 剥离；样式引用映射到空 stub
    '\\.(css|scss|sass)$': '<rootDir>/tests/unit/styleStub.js',
    '^@/(.*)$': '<rootDir>/src/$1'
  },
  testMatch: ['<rootDir>/tests/unit/**/*.spec.js'],
  // 防止 V3 组件内 console.error（未知组件/属性）淹没断言输出
  verbose: true
}
