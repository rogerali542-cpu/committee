import { defineConfig } from 'vite'
import basicSsl from '@vitejs/plugin-basic-ssl'
import { baseConfig } from './vite.config.js'

// HTTPS 开发入口（`npm run dev:https`）：手机真机测「相机」用。
// getUserMedia 只在安全上下文可用——localhost 免证书，但用局域网 IP 访问必须 HTTPS，
// 否则浏览器直接拦掉相机。basic-ssl 自动生成自签证书；手机首次访问会提示
// 证书不受信任，点「继续访问 / 仍要前往」即可（自签证书的正常提示）。
// 默认 `npm run dev` 仍是 HTTP，不受影响。
export default defineConfig({
  ...baseConfig,
  plugins: [...baseConfig.plugins, basicSsl()]
})
