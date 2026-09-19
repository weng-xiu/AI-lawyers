'use strict'
const path = require('path')

// F2 无障碍 axe 自动化扫描配置（公众端 aiuser-ui）
// 后端接口由测试内 page.route 统一 mock，无需启动 Java 后端。
module.exports = {
  testDir: path.join(__dirname, 'tests/a11y'),
  timeout: 60000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['json', { outputFile: 'tests/a11y-results/raw-results.json' }]],
  use: {
    // 81/8081 端口常被其他旧服务占用，固定使用 8091 启动本工程，避免误扫
    baseURL: process.env.A11Y_BASE_URL || 'http://localhost:8091',
    headless: true,
    viewport: { width: 1280, height: 800 }
  },
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } }
  ],
  webServer: process.env.A11Y_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --port 8091',
        url: 'http://localhost:8091',
        timeout: 240000,
        reuseExistingServer: false,
        env: { NODE_OPTIONS: '--openssl-legacy-provider', npm_config_port: '8091' }
      }
}
