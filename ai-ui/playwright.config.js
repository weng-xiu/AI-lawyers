'use strict'
const path = require('path')

// P8 前端质量保障：管理端关键链路 Playwright E2E（登录→签入→来电→建单）
// 与公众端 axe 扫描同策略：后端接口全部由测试内 page.route mock，无需启动 Java 后端；
// WebSocket 呼叫事件由测试注入的 FakeWebSocket 推送（见 tests/e2e/key-link.spec.js）。
module.exports = {
  testDir: path.join(__dirname, 'tests/e2e'),
  timeout: 120000,
  // dev 模式路由组件按需编译，首次渲染可能超过默认 5s，放宽断言等待
  expect: { timeout: 20000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['json', { outputFile: 'tests/e2e-results/raw-results.json' }]],
  use: {
    // 80/8080/81 常被占用，固定使用 8092 启动管理端
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:8092',
    headless: true,
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    actionTimeout: 15000
  },
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } }
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --port 8092',
        url: 'http://localhost:8092',
        timeout: 300000,
        reuseExistingServer: !process.env.CI,
        env: { NODE_OPTIONS: '--openssl-legacy-provider', npm_config_port: '8092' }
      }
}
