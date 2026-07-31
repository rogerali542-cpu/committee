# 项目约定（踩过的坑，别再犯）

## UI

### 下拉/展开箭头：不要用 `⌄` `⌃` 字符
- **问题**：`⌄`(U+2304)/`⌃` 这类箭头字符在字体里基线偏低，放进胶囊/行内时会**往下坠、无法垂直居中**，靠 `position:top` 微调也压不准。本项目已在成员选择卡、学习列表「详情」等多处踩过。
- **正确做法**：用 CSS 边框画箭头，能精确居中、且旋转即可切换方向：
  ```css
  /* 向下箭头（展开态） */
  .arrow { display:inline-block; width:12rpx; height:12rpx;
    border-right:3rpx solid currentColor; border-bottom:3rpx solid currentColor;
    transform:rotate(45deg); position:relative; top:-2rpx; transition:transform .2s ease, top .2s ease; }
  /* 向上箭头（收起态）：旋转 -135deg，top 反向补偿 */
  .arrow.open { transform:rotate(-135deg); top:2rpx; }
  ```
  颜色用 `currentColor` 跟随父级文字色；容器 `display:inline-flex; align-items:center`。
- 参考实现：`h5/src/pages/LearningDetail.vue`(.nc2-arrow)、`h5/src/components/PlanDateTimeField.vue`、`h5/src/pages/Learning.vue`(.lc-more-arr)。

### 底部主操作条：必须 `position:fixed` 钉在底栏上沿，别放文档流
- **问题**：把页面底部的主操作条放进内容流（flex `order` 排最后、或 `position:sticky`），内容不足一屏时它只会停在内容末尾——**浮在半空、和底部导航之间空出一大段**。会议页(`.mtg-actionbar`)、接待页(`.rec-actions`)先后踩过同一个坑：sticky 在不滚动的短页上不会下坠；flex sticky-footer 在多层嵌套里撑不满高度。
- **正确做法**（设计规范 §7「主操作固定在拇指区、不随内容长度漂移」）：
  ```css
  .xxx-actionbar { position: fixed; left: 0; right: 0;
    bottom: calc(98rpx + env(safe-area-inset-bottom)); /* 底栏高≈102rpx，留几 rpx 叠白 */
    z-index: 90; background: #fff; box-shadow: 0 -10rpx 24rpx rgba(20,42,58,.06); }
  ```
  并且：① 内容容器加 `padding-bottom` 给「操作条+底栏」两层让位；② TabBar 对应路由加 `.merged`（去顶部描边/阴影），两片白连成整片。参考 `.mtg-actionbar`（Committee.vue 会议 tab）与 `.rec-actions`（接待 tab）。

### App 壳：滚动只在 `#app-scroll`，别监听/滚 window
- **背景**（0731）：手机浏览器 body 滚动 + fixed 底栏会被地址栏伸缩/橡皮筋带走。已改 app-shell：`html/body overflow:hidden`，`.app-shell` 锁 100% 高，滚动只发生在内层 `#app-scroll`；TabBar 是它的 flex:none 兄弟（App.vue），物理滚不走。页内操作条仍按上节 `position:fixed`（body 不滚后 fixed 天然稳）。
- **规矩**：任何「滚到顶/记滚动位置/监听滚动」都操作 `document.getElementById('app-scroll')`（`.scrollTo/.scrollTop/addEventListener('scroll')`），**window 上的滚动 API 全部失效**（router scrollBehavior 已改）。参考 Committee.vue `appScrollEl()`。

## Git（本仓库工作流）

- 提交前先 `cd /home/user/committee`（在 `h5/` 里跑 `git add h5/src/...` 会 pathspec 不匹配）。
- 每次改动**同一 HEAD 推两个分支**：`claude/homepage-card-layout-dwi775` 与 `codex-local-work`；
  推完用 `git rev-list --left-right --count origin/codex-local-work...HEAD` 应为 `0 0`。

## 构建 / 验证

- 前端：`cd /home/user/committee/h5 && npm run build`。
- 后端（离线可编译）：`/opt/maven/bin/mvn -f /home/user/committee/backend/pom.xml -q compile -o`。
- 后端 Flyway 关闭、`ddl-auto: update`：**新增列必须可空**，启动时自动加列；迁移脚本不会跑。改了后端要重启才生效。
