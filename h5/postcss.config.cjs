// 把小程序的 rpx 单位自动换算成 vw：750rpx = 100vw（与小程序 750 设计基准一致）
// 这样从 .wxss 搬过来的样式（大量 rpx）无需手改，视觉 1:1 且自适应屏宽。
module.exports = {
  plugins: {
    'postcss-px-to-viewport-8-plugin': {
      unitToConvert: 'rpx',
      viewportWidth: 750,
      unitPrecision: 5,
      propList: ['*'],
      viewportUnit: 'vw',
      fontViewportUnit: 'vw',
      selectorBlackList: [],
      minPixelValue: 1,
      mediaQuery: false,
      replace: true,
      exclude: [/node_modules/]
    }
  }
}
