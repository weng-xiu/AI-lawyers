'use strict'
const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

// P4：公众端构建优化——gzip 预压缩 + 第三方依赖分包（原 chunk-vendors 0.97MB 单包未拆分未 gzip）
const CompressionPlugin = require('compression-webpack-plugin')

const name = process.env.VUE_APP_TITLE || 'AI律师用户端' // 网页标题

const baseUrl = 'http://localhost:8080' // 后端接口

const port = process.env.port || process.env.npm_config_port || 81 // 监听端口

// vue.config.js 配置说明
//官方vue.config.js 参考文档 https://cli.vuejs.org/config/#css-loaderoptions
// 这里只列一部分，具体配置参考文档
module.exports = {
  // 部署生产环境和开发环境下的URL。
  // 默认情况下，Vue CLI 会假设你的应用是被部署在一个域名的根路径上
  // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用 publicPath 选项。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
  publicPath: process.env.NODE_ENV === "production" ? "/" : "/",
  // 在npm run build 或 yarn build 时 ，生成文件的目录名称（要和baseUrl 的生产环境路径一致）（默认dist）
  outputDir: 'dist',
  // 用于放置生成的静态资源 (js、css、img、fonts) 的；（项目打包之后，静态资源会放在这个文件夹下）
  assetsDir: 'static',
  // 如果你不需要生产环境的 source map，可以将其设置为 false 以加速生产环境构建。
  productionSourceMap: false,
  transpileDependencies: [],
  // webpack-dev-server 相关配置
  devServer: {
    host: '0.0.0.0',
    port: port,
    open: true,
    proxy: {
      // detail: https://cli.vuejs.org/config/#devserver-proxy
      [process.env.VUE_APP_BASE_API]: {
        target: baseUrl,
        changeOrigin: true,
        pathRewrite: {
          ['^' + process.env.VUE_APP_BASE_API]: ''
        }
      },
      // springdoc proxy
      '^/v3/api-docs/(.*)': {
        target: baseUrl,
        changeOrigin: true
      }
    },
    disableHostCheck: true
  },
  configureWebpack: {
    name: name,
    resolve: {
      alias: {
        '@': resolve('src')
      }
    },
    plugins: [
      // 政务外网/移动端首屏提速：构建期预生成 .gz，Nginx 开启 gzip_static 后直接下发
      new CompressionPlugin({
        cache: false,                                  // 不启用文件缓存
        test: /\.(js|css|html|jpe?g|png|gif|svg)?$/i,  // 压缩文件格式
        filename: '[path][base].gz[query]',            // 压缩后的文件名
        algorithm: 'gzip',                             // 使用gzip压缩
        minRatio: 0.8,                                 // 压缩比例小于 80% 的文件不会被压缩
        deleteOriginalAssets: false                    // 压缩后保留原文件（兼容未开 gzip_static 的服务器）
      })
    ]
  },
  chainWebpack(config) {
    // 关闭 prefetch，避免浏览器空闲时预取全部异步 chunk（公众端页面虽少，仍按需加载）
    config.plugins.delete('prefetch')

    config.when(process.env.NODE_ENV !== 'development', config => {
      // P4：第三方依赖分包——element-ui 独立成包，配合 runtimeChunk 提升缓存命中率
      config.optimization.splitChunks({
        chunks: 'all',
        cacheGroups: {
          libs: {
            name: 'chunk-libs',
            test: /[\\/]node_modules[\\/]/,
            priority: 10,
            chunks: 'initial' // 只打包初始依赖的第三方库
          },
          elementUI: {
            name: 'chunk-elementUI', // element-ui 单独拆包（占 vendor 体积大头）
            test: /[\\/]node_modules[\\/]_?element-ui(.*)/, // 兼容 cnpm 安装路径
            priority: 20 // 权重需高于 libs，否则会被打入 libs
          },
          commons: {
            name: 'chunk-commons',
            test: resolve('src/components'),
            minChunks: 2, // 至少被引用 2 次才抽取
            priority: 5,
            reuseExistingChunk: true
          }
        }
      })
      config.optimization.runtimeChunk('single')
    })
  }
}
