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

## Git（本仓库工作流）

- 提交前先 `cd /home/user/committee`（在 `h5/` 里跑 `git add h5/src/...` 会 pathspec 不匹配）。
- 每次改动**同一 HEAD 推两个分支**：`claude/homepage-card-layout-dwi775` 与 `codex-local-work`；
  推完用 `git rev-list --left-right --count origin/codex-local-work...HEAD` 应为 `0 0`。

## 构建 / 验证

- 前端：`cd /home/user/committee/h5 && npm run build`。
- 后端（离线可编译）：`/opt/maven/bin/mvn -f /home/user/committee/backend/pom.xml -q compile -o`。
- 后端 Flyway 关闭、`ddl-auto: update`：**新增列必须可空**，启动时自动加列；迁移脚本不会跑。改了后端要重启才生效。
