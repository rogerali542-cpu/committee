<template>
  <div class="home" :class="{ 'portal-home': welcomeVisible, 'reception-home': planTab === 'reception', 'has-mtg-bar': planTab === 'meeting' && homeLayout === 'tabs', 'has-rec-bar': planTab === 'reception' && canManageReception }">
    <!-- 顶栏：标题 -->
    <div class="hd">
      <div class="hd-left">
        <!-- 0731 设计师定：页头与底栏同名「业主接待」——底栏是用户的定位锚，页头跟着走；「中心」是无信息后缀。
             驾驶舱页头＝小区业委会全称（0731 定稿：首页说明"这是谁的系统"，比"智能助手"有信息量） -->
        <span class="hd-title">{{ homeLayout === 'portal' && planTab === 'meeting' ? ((activeRole.communityName || '阳光花园') + '业主委员会') : (planTab === 'reception' ? '业主接待' : '业委会会议') }}</span>
        <span class="hd-sub">{{ activeRole.realName }} · {{ activeRole.role }}</span>
      </div>
      <!-- 返回驾驶舱移入顶栏右上角（0730 用户定，图一骨架）；驾驶舱布局(portal)本身不显示 -->
      <button v-if="homeLayout === 'tabs'" type="button" class="hd-cockpit" @click="goCockpitFromHd">返回首页</button>
      <!-- 0731 定稿：评分竖排右上——大数字在上、「综合评分 ›」在下（用户定：文本用"综合评分"），点击进个人中心看履职统计 -->
      <!-- 评分（0731 用户定四改）：数字恢复红绿灯渐变——颜色与得分高低挂钩是它的语义，不是模块色；
           标签「当前综合评分」，数字在标签宽度内水平居中 -->
      <div v-if="isChair && planTab === 'meeting' && homeLayout === 'portal'" class="hd-score" @click="goScore">
        <span class="hd-score-num" :style="{ backgroundImage: scoreGradient }">{{ score }}</span>
        <span class="hd-score-label">当前综合评分 ›</span>
      </div>
    </div>

    <!-- 「会议｜印章」二级切换已删（0730 用户定：印章独立成底栏第五个 tab，与会议分开） -->

    <!-- 履职年历（全员可见）+ 当前会议卡 + 待办：分类横栏 开会（默认）/培训/接待。始终显示；
         日历恒为首屏主角（0716 用户定）：会议卡挪进本容器排日历下方（见下方插入位），待办再往下。
         类名 yc- 前缀（cal- 被小日历占用）。 -->
    <!-- ⚠ has-meeting 的语义在 0717 之后不准了，留着是权衡不是疏忽：
         它原意是「这个栈里有会议卡占地方 → 日历/清单压紧点」，但会议卡现已只在开会 tab 出现，
         而这个类仍按「库里有没有进行中会议」挂，于是接待/培训 tab 也会跟着压 —— 为一张它们并不显示的卡腾地方。
         没顺手改成 planTab==='meeting'&&… 的原因：实测那样会让接待 tab 高 636→703px（徽标 14→14.5px、
         日历卡头 39→52px）。压紧本身是想要的效果，只是理由挂错了；改掉反而把一路收敛来的空间又吐回去。
         正解是把 compact 态的紧凑值直接写死、不再依赖 has-meeting，那是独立的一次重构。
         现状的实际毛病：会议一结束，接待 tab 会毫无理由地长高 67px。 -->
    <div class="plan-stack" :class="{ compact: planTab !== 'meeting', 'reception-mode': planTab === 'reception', 'has-meeting': planTab === 'meeting' && currents && currents.length }">
        <!-- 登记：接待的入口动作，独立成大按钮（0716 用户定）。委员接待完来访，先用它把事情记进下面的
             清单，再逐条处理——所以位置就卡在「概览 → 登记 → 待处理清单」这个工作流顺序上。
             原先它是待办卡头里的一个小 chip，和「主要功能之一」的分量不符。 -->
        <div v-if="planTab === 'reception'" class="rec-notice-hero">
          <!-- 时间显示（0730 设计师定稿）：顶行统一灰字「M月D日 周四」；语气只落在标题
               （今天「今晚…」/明天「明晚…」/其他日子制度句「每周四 起—止 接待」） -->
          <template v-if="receptionHero.set">
            <div class="rnh-top">
              <span class="rnh-date">{{ receptionHero.dateLine }}</span>
            </div>
            <div class="rnh-title">{{ receptionHero.title }}</div>
            <!-- 地址整体不拆:放不下就整体换到第二行,不从地名中间掰断 -->
            <div v-if="recSystem && recSystem.place" class="rnh-place"><span class="rnh-place-name">{{ recSystem.place }}</span></div>
          </template>
          <template v-else>
            <div class="rnh-kicker">接待安排</div>
            <div class="rnh-time none">还没设置接待时间</div>
          </template>
          <!-- 「调整接待安排」按钮已移到底部动作区（0730 图一：卡片保持干净，只承载时间/地点信息） -->
        </div>

        <!-- 待办入口卡（0730 设计师定稿；0731 用户定：入口常驻，0 项也保留——它是「业委会待办」
             聚合页的固定入口，不随有无待办出没）：标题「待办事项 · N 项待跟进」+内容短摘要 -->
        <div v-if="planTab === 'reception'" class="rec-todo-entry" @click="goTodos()">
          <div class="rte-top">
            <b>待办事项{{ recPendingList.length ? ' · ' + recPendingList.length + ' 项待跟进' : '' }}</b>
            <i class="rte-arr"></i>
          </div>
          <div class="rte-sub">{{ recPendingList.length ? recPendingSummary : '暂无待跟进事项' }}</div>
        </div>

        <!-- 底部动作区（0730 图一）：调整接待安排(浅绿) + 登记接待(绿实心CTA)，成组落在内容末尾（order:8） -->
        <div v-if="planTab === 'reception' && canManageReception" class="rec-actions">
          <button type="button" class="rec-adjust-btn" @click="goReceptionNotice">调整接待安排</button>
          <!-- 定稿图：登记按钮单行、无对象副行 -->
          <button type="button" class="rec-register-btn" @click="openReceptionCreate">
            <span class="rrb-main">登记接待</span>
          </button>
        </div>
        <div v-if="planTab === 'reception'" class="rec-recent-card">
          <!-- 「接待记录」改称「近期接待」，「查看全部」重复入口删掉——往期/已办统一收进档案馆（0730 设计师定） -->
          <div class="rec-recent-head">
            <span>近期接待</span>
          </div>
          <div v-if="!recentReceptionRecords.length" class="rec-recent-empty">暂无接待记录</div>
          <!-- 行式照图一（spec §11）：记录只是记录，不挂状态标签——事项状态由上方待办卡承担；
               行上的删除 × 已撤（spec §10：删除移入详情页，ReceptionDetail 有「删除这条接待记录」） -->
          <div v-for="session in recentReceptionRecords" :key="session.key" class="rec-recent-session">
            <div class="rec-recent-row" @click="openRecentSession(session)">
              <div class="rec-recent-copy">
                <strong class="rec-recent-main">{{ fmtPlanDate(session.date) }}<template v-if="session.receiver"> · {{ session.receiver }}</template></strong>
                <!-- 副行改内容短摘要（0730 定稿图）：「反映 下水管返味、门禁卡失灵」比「反映 2 项」信息量大 -->
                <span class="rec-recent-sub">{{ session.noVisit ? '无业主来访' : ('反映 ' + recSummaryOf(session)) }}</span>
              </div>
              <i class="rec-recent-chev" :class="{ open: recentOpenKey === session.key && session.displayRecords.length > 1 }"></i>
            </div>
            <div v-if="recentOpenKey === session.key && session.displayRecords.length > 1" class="rec-recent-items">
              <div v-for="r in session.displayRecords" :key="r.id" class="rec-recent-item" @click="goReceptionDetail(r)">
                <div><strong>{{ r.visitorName || '来访业主' }}</strong><span>{{ r.content }}</span></div>
                <em>{{ r.done ? '已办结' : ((r.propertyTransferred || r.ticketPushed) ? '处理中' : '去处理') }}</em>
              </div>
            </div>
          </div>
          <!-- 末行档案馆入口（0730 定稿图）：「档案馆」深色、说明灰色 -->
          <div class="rec-recent-arch" @click="goArchive('reception')">
            <span class="rra-text"><b>档案馆</b> · 往期接待与已办事项</span>
            <span class="rec-recent-arch-arr">›</span>
          </div>
        </div>

        <!-- 接待日安排（0717）：按规定每月要设接待时间并公示，主任时间不定所以基本每月都要改。
             排在待跟进清单之后、日历之前——它是每月一次的事，不该跟「登记接待」（天天用）抢位置；
             但也不能藏进日历里，因为「我们的接待时间是几点」本身就是这个 tab 该回答的问题。
             卡上只读，编辑和导出打印都在 /reception-notice。
             标题带当前月份「X月接待安排」+ 补地点行 + 按钮统一「编辑」（0717 用户定）：
             月份强化「每月要更新」的节奏感；真实接待记录里时间/地点从来成对出现，缺地点老人不知道去哪。 -->
      <!-- 驾驶舱首页（0731 设计师定稿重做）：日期行 + 三模块聚合卡 + 轻列表（待办/印章/档案馆）。
           原欢迎语/智能摘要/三栏任务板/工作板块 dock 整体退役；底栏改用全局 TabBar 四项 -->
      <div v-if="planTab === 'meeting' && homeLayout === 'portal'" class="welcome">
        <div class="pt-date">{{ cockpitDateText }}</div>
        <!-- 三模块聚合卡：每节＝模块名(灰) + 主行(黑) + 副行(灰) + 状态（三级：逾期暖胶囊/今日绿/进行中蓝）；
             整节点击进对应模块页（spec §6 行点击=进入） -->
        <div class="pt-card">
          <!-- 模块名标签已删（0731 用户定三改：上色仍不够明显，索性删）：各节标题自明——
               例会/接待字样自带归属，学习空态文案改「近期无学习培训」补上下文 -->
          <div v-for="sec in portalSections" :key="sec.key" class="pt-sec" @click="sec.onTap()">
            <div class="pt-sec-row">
              <div class="pt-sec-main">
                <div class="pt-sec-title">{{ sec.title }}</div>
                <div v-if="sec.sub" class="pt-sec-sub">{{ sec.sub }}</div>
              </div>
              <span v-if="sec.badge" class="pt-badge" :class="sec.tier">{{ sec.badge }}</span>
            </div>
          </div>
        </div>
        <!-- 轻列表（spec §5：非"当下要办"的入口用透明底+分隔线）：聚合待办 / 印章 / 档案馆 -->
        <div class="pt-links">
          <!-- 计数恒显（0731 设计师定：没数字这行信息量为零，0 项也要能看出"没有事要办"） -->
          <div class="pt-link" @click="goTodos()">
            <span class="pt-link-t">待办事项<em> · {{ ptTodoCount }} 项</em></span>
            <i class="pt-arr"></i>
          </div>
          <!-- 印章管理行已删（0731 用户定：低频），入口移入个人中心菜单 -->
          <div class="pt-link" @click="goArchive()">
            <span class="pt-link-t">档案馆</span>
            <i class="pt-arr"></i>
          </div>
        </div>
      </div>

      <!-- 会议工作页：驾驶舱负责提醒和直达，这里只保留近期安排与年度记录，避免同一场会议重复出现。 -->
      <div v-if="planTab === 'meeting' && homeLayout === 'tabs'" class="plan-calendar-card mtg-flat">
        <!-- 接待/培训：整个卡头就是折叠开关（默认收起，见 ovGridFold）。标题用「全年日历」而非
             「接待概览」——概览已由上方三数字承担，这张卡里只剩 12 月宫格；且老板找的就是「日历」
             这两个字，他问起来一眼能指到这行。 -->
        <!-- 会议 tab 的「会议安排」标题已删（0730 用户定点3：信息量低、下方卡片自明，删掉让首屏更早进正题）。
             接待/培训仍保留「全年接待日历」卡头（兼折叠开关） -->
        <div v-if="planTab !== 'meeting'" class="plan-head foldable" @click="ovGridFold = !ovGridFold">
          <div class="plan-title-wrap">
            <span class="plan-title ov-title">全年接待日历</span>
          </div>
          <div class="plan-actions">
            <span class="ov-fold-chev" :class="{ open: !ovGridFold }">▾</span>
          </div>
        </div>

        <!-- 近期安排优先，后续计划弱化，已完成记录折叠。 -->
        <div v-if="planTab === 'meeting'" class="mr-list">
          <!-- 待召开卡（0730 五改）：竖排；行=左选中竖条+日期叶+标题(短名)+副行+右侧「常驻状态标签」；
               状态与选中分离——选中(底部按钮指向的最急项)用整行浅底+左竖条，状态用无边框浅底文字标签。
               整行可点进入各自流程；底部动作条默认落在最急项（欠账优先）。 -->
          <template v-if="meetingRecordList.immediate.length">
            <!-- 「待召开 N 场」小标题已删（0730 用户定：下方卡片自明是几场） -->
            <div class="mtg-due-card">
              <div v-for="row in meetingRecordList.immediate" :key="row.key"
                   class="mtg-due-row" @click="row.onTap()">
                <div class="mr-badge" :class="[row.statusClass, { range: row.range }]">
                  <b>{{ row.badgeTop }}</b><span v-if="row.badgeBot">{{ row.badgeBot }}</span>
                </div>
                <div class="mr-info">
                  <div class="mr-row-title">{{ shortMeetingName(row.title) }}</div>
                  <div class="mr-row-sub">{{ dueSubFor(row) }}</div>
                </div>
                <span class="mtg-due-status" :class="dueStatusTier(row)">{{ dueStatusText(row) }}</span>
              </div>
            </div>
          </template>
          <div class="mr-lookup">
          <!-- 接下来（0730 用户图样六改）：去白卡，只留分隔线——次要清单不与待召开卡抢层级；
               行=单行「短名 · 时段」+右侧无底色灰字状态（仍是唯一入口，0725 防误触规则不变）；
               尾行「YYYY年全年会议 | 展开▾」承接原「全年会议一览」折叠 -->
          <!-- 「接下来」→「后续会议」（0731 用户定：接下来不明确是干什么）；右侧计数已删 -->
          <div v-if="nextRows.length" class="mtg-next-head">
            <span>后续会议</span>
          </div>
          <div class="mtg-next-list">
            <!-- 截断而非折叠：默认只出最近 2 场，首屏高度恒定。
                 行式时间在前（0731 用户定：与上方待召开卡统一「时间→名称」时间轴式） -->
            <div v-for="row in visibleNextRows" :key="row.key" class="mtg-next-row">
              <span class="mtg-next-line">{{ nextWhen(row) }} · {{ shortMeetingName(row.title) }}</span>
              <button type="button" class="mtg-next-chip" @click.stop="row.onTap()">{{ row.statusLabel }}</button>
            </div>
            <!-- 「还有 N 场 ›」：一次展开其余；前 2 场始终在，绝不会点了什么都看不到 -->
            <div v-if="nextMoreCount && !nextExpanded" class="mtg-next-row mtg-next-more-row" @click="nextExpanded = true">
              <span class="mtg-next-line more">还有 {{ nextMoreCount }} 场</span>
              <span class="mtg-next-more-arr">›</span>
            </div>
            <!-- 全年场次是"至少6场"的不定数（0730 用户定）：不写具体数字，免得误导 -->
            <div class="mtg-next-foot" @click="toggleMeetingCalendar">
              <b>{{ viewYear }}年全年会议</b>
              <!-- 纯图形展开按钮（0731 用户定二改：去文字）：圆底+边框画箭头，向下=展开、向上=收起；整行仍是点击区 -->
              <span class="mtg-fold-btn"><i class="mfb-chev" :class="{ open: meetingCalendarOpen }"></i></span>
            </div>
            <!-- 档案馆入口（0730 设计师定）：全年会议是今年排期总览、可展开；档案馆是历年已归档纪要，两件事并存。
                 右侧与展开按钮同款圆底图形钮，仅方向不同（0731 用户定：右指=跳转、下指=展开） -->
            <div class="mtg-next-foot mtg-arch-foot" @click="goArchive('committee')">
              <b>档案馆 · 会议纪要</b>
              <span class="mtg-fold-btn"><i class="mfb-chev right"></i></span>
            </div>
          </div>
          <div v-if="meetingCalendarOpen" ref="calendarPanelEl" class="mr-calendar-panel">
            <div class="mr-calendar-panel-title">
              <span>{{ viewYear }}年月历</span>
            </div>
            <div class="mr-calendar-grid">
              <button v-for="mc in monthCells" :key="'meeting-month-' + mc.m" type="button"
                      class="mr-calendar-month" :class="mc.status" @click.stop="onMeetingCalendarMonth(mc.m)">
                <b>{{ mc.m }}月</b>
                <span>{{ mc.label }}</span>
              </button>
            </div>
            <template v-if="meetingRecordList.done.length">
              <div class="mr-cal-done-title">已完成 {{ meetingRecordList.done.length }} 场</div>
              <div v-for="row in meetingRecordList.done" :key="row.key" class="mr-row done mr-cal-done-row">
                <div class="mr-badge" :class="row.statusClass">
                  <b>{{ row.badgeTop }}</b><span v-if="row.badgeBot">{{ row.badgeBot }}</span>
                </div>
                <div class="mr-info">
                  <!-- 「补开」标签已删（0729 用户定）：月历已表达各期执行情况，完成列表不再另标 -->
                  <div class="mr-row-title">{{ row.title }}</div>
                  <div class="mr-row-sub">{{ row.sub }}</div>
                </div>
                <button type="button" class="mr-cta-btn" :class="row.statusClass" @click.stop="row.onTap()">查看 ›</button>
              </div>
            </template>
          </div>
          </div>
          <!-- 底部动作条（0730 点2三改）：上下叠放，主次分明且都在拇指区——
               次级「＋ 发起临时会议」浅蓝底在上（低频，一两月一次），主按钮（指向最急项）实蓝在下。
               无最急项时主按钮直接变「发起会议」、上方次级按钮省略（不重复） -->
          <div v-if="heroMeeting || canCreate" class="mtg-actionbar">
            <button v-if="heroMeeting && canCreate" type="button" class="mtg-secondary" @click="openNewMeeting()">＋ 发起临时会议</button>
            <button v-if="heroMeeting" type="button" class="mtg-primary" @click="heroMeeting.onTap()">
              <span v-if="heroBarSub" class="mtg-primary-sub">{{ heroBarSub }}</span>
              <span class="mtg-primary-main">{{ heroMeeting.statusLabel }}</span>
            </button>
            <button v-else-if="canCreate" type="button" class="mtg-primary" @click="openNewMeeting()">
              <span class="mtg-primary-main">＋ 发起会议</span>
            </button>
          </div>
        </div>

        <!-- 接待/培训 12 月履职宫格：一眼看每月该类状态；点月下钻看当月清单（数据同源 monthCells） -->
        <!-- 图例：只给接待/培训。展开态才出，收起时整张卡只剩一行标题。 -->
        <div v-if="planTab !== 'meeting' && !ovGridFold" class="ov-legend">
          <span v-for="lg in ovLegend" :key="lg.k"><i class="ov-lg-dot" :class="lg.k"></i>{{ lg.t }}</span>
        </div>
        <div v-if="planTab !== 'meeting' && !ovGridFold" class="yc-month-grid">
          <div v-for="mc in monthCells" :key="mc.m" class="yc-cell" :class="[mc.status, { sel: calMonthTapped && calMonth === mc.m }]" @click="onOvMonthTap(mc.m)">
            <span class="yc-m">{{ mc.m }}月</span>
            <span class="yc-s">{{ mc.label }}</span>
          </div>
        </div>
        <div v-if="planTab !== 'meeting' && !ovGridFold && calMonthDrill" class="yc-period-feedback">
          <div class="ypf-head">{{ calMonthDrill.title }}</div>
          <div v-if="!calMonthDrill.items.length" class="plan-empty">该月无记录</div>
          <div v-else v-for="it in calMonthDrill.items" :key="it.key" class="yc-item" @click="it.onTap()">
            <div class="yc-item-info">
              <div class="yc-item-title">{{ it.title }}</div>
              <div v-if="it.sub" class="yc-item-sub">{{ it.sub }}</div>
            </div>
            <span class="plan-badge view">查看 ›</span>
          </div>
        </div>
      </div>

        <!-- 当前会议卡片（进行中/准备中；主任另含已结束未公示）：排在日历下方（0716 用户定），点继续进入流程。
             0717 用户定：会议卡只在开会 tab 出现了。原先接待/培训 tab 顶上有条「会议进行中 · 查看▾」
             收起栏，点开能就地展开整张会议卡——现在整条链一起撤：
             收起栏删了，「点开展开」就没了入口，于是 meetCardOpen、「收起▴」也全成死代码，一并清掉。
             代价说明白：接待/培训 tab 从此不再提示「有会正在进行」，要看会得切回开会 tab。 -->
        <!-- 详情大会议卡：0724 改版后并入顶部「当前重点」横幅（HOME_V2），旧卡代码暂留便于回滚 -->
        <template v-if="currents && currents.length > 0 && !HOME_V2">
          <template v-if="planTab === 'meeting'">
            <div v-for="cur in currents" :key="cur.id" class="meet-card">
              <!-- 卡结构（0716 用户定）：状态从右上角的小胶囊提为左侧标题（进行中的会议/未开始的会议/
                   已结束的会议），会议名称降为第二行——原先「会议名 + 角落小状态」读不出这张卡是干嘛的 -->
              <div class="meet-head">
                <span class="meet-card-title" :class="cur.stage">{{ cur.stageText }}的会议</span>
              </div>
              <div class="meet-title">{{ cur.title }}</div>
              <div class="meet-info">
                <span class="meet-info-item">{{ cur.timeText }}</span>
                <span class="meet-info-sep"></span>
                <span class="meet-info-item location">{{ cur.locationText }}</span>
              </div>

              <div class="steps">
                <template v-for="(item, index) in cur.steps" :key="item.no">
                  <div v-if="index > 0" class="step-line" :class="item.state === 'todo' ? 'todo' : 'done'"></div>
                  <div class="step" :class="item.state">
                    <div class="step-dot">
                      <span v-if="item.state === 'done'">✓</span>
                      <span v-else>{{ item.no }}</span>
                    </div>
                    <span class="step-label">{{ item.label }}</span>
                  </div>
                </template>
              </div>

              <div v-if="cur.preNoticeTip" class="pre-notice-tip">📢 {{ cur.preNoticeTip }}</div>

              <div class="big-btn" @click="goCurrent(cur)">
                <div class="big-btn-inner">
                  <span v-if="cur.ctaIcon" class="big-btn-ico">{{ cur.ctaIcon }}</span>
                  <span class="big-btn-text">{{ cur.ctaLabel }}</span>
                </div>
              </div>

              <div v-if="isChair" class="meet-del" @click="removeCurrent(cur)">删除会议</div>
            </div>
          </template>
        </template>

        <!-- 待办事项：开会类同时展示本期与逾期期次；其他分类展示当前月份待办。
             0724 改版：开会 tab 隐藏——横幅已呈现第一要务、记录列表每行可点即去通知/补开，本卡重复。
             0730 定稿：接待 tab 也停用本卡——由上方「待办事项 · N 项待跟进」入口卡进业委会待办聚合页；培训仍用。 -->
        <div v-if="!(HOME_V2 && planTab === 'meeting') && planTab !== 'reception'" class="plan-todo-card yc-list"
             :class="{ flash: planTodoFlash, 'empty-compact': planTab === 'reception' && !planTodoList.length }">
          <div class="yc-list-head" :class="{ foldable: planTab === 'reception' }"
               @click="planTab === 'reception' ? (receptionTodoOpen = !receptionTodoOpen) : null">
            <span class="yc-head-title">{{ planListTitle }}</span>
            <!-- 0 时整个不显示（0716）：下面 .plan-empty 已经写着「暂无需要处理的接待事项」，
                 这里再挂个「无待办」是重复；而且筛到「本月接待」时标题是「本月接待」、计数却说
                 「无待办」，两句话对不上。.active 绑定随实心橙+脉动一起删了。 -->
            <span v-if="planTab !== 'meeting' && planTodoList.length" class="yc-list-count"
                  :class="{ danger: planTodoList.length > 4, warn: planTodoList.length >= 2 && planTodoList.length <= 4, safe: planTodoList.length <= 1 }">
              {{ planTodoList.length }}项
            </span>
          </div>
          <!-- 本期例会与其他期次同为普通条目（0716 用户定：原实心大按钮太重、与列表风格打架，已拆） -->
          <template v-if="!planTodoList.length || planTab !== 'reception' || receptionTodoOpen">
            <div v-if="!planTodoList.length" class="plan-empty">{{ planTab === 'reception' ? '暂无待办事项' : `暂无需要处理的${planTabLabel}事项` }}</div>
            <div v-else v-for="it in planTodoList" :key="it.key" class="yc-item" :class="[planTab === 'meeting' ? it.status : 'todo-plain', it.flag]"
                 @click="it.onTap()">
              <div class="yc-item-info">
                <div class="yc-item-title">{{ it.title }}</div>
                <div v-if="it.sub" class="yc-item-sub">{{ it.sub }}</div>
              </div>
              <span class="plan-badge" :class="it.status" @click.stop="it.onTap()">{{ it.badge }}</span>
            </div>
          </template>
        </div>

        <!-- 地图选点（0723 接真实接口）：腾讯 H5 选点组件弹层 -->
        <MapPicker :open="mapPickerOpen" @close="mapPickerOpen = false" @picked="onMapPicked" />

        <!-- 接待登记弹窗（0716 从已删的接待列表页搬来，字段与校验照旧） -->
        <div v-if="recCreateOpen" class="rec-mask" @click="recCreateOpen = false">
          <div class="rec-sheet" @click.stop>
            <div class="sheet-head">
              <span class="sheet-title">登记接待记录</span>
              <span class="sheet-close" @click="recCreateOpen = false">×</span>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <span class="form-label">接待日期 *</span>
                <div class="picker-field" @click="openDatePicker('reception')">{{ recForm.date ? fmtPlanDate(recForm.date) : '选择日期' }}</div>
              </div>
              <div class="form-group half">
                <span class="form-label">接待时间 *</span>
                <div class="picker-field" @click="openTimePicker('reception')">{{ recForm.time || '选择时间' }}</div>
              </div>
            </div>
            <div class="form-group">
              <span class="form-label">接待人 *</span>
              <select class="picker-select" v-model="recForm.receiver">
                <option value=""></option>
                <option v-if="recForm.receiver && !receiverItems.includes(recForm.receiver)" :value="recForm.receiver">{{ recForm.receiver }}</option>
                <option v-for="it in receiverItems" :key="it" :value="it">{{ it }}</option>
              </select>
            </div>
            <button class="no-visit-quick" type="button" :disabled="noVisitSaving" @click="submitNoVisit">
              {{ noVisitSaving ? '正在登记…' : '本次无人来访' }}
            </button>
            <div v-for="(visitor, index) in recVisitors" :key="visitor.key" class="rec-visitor-card">
              <div class="rec-visitor-head">
                <strong>来访业主 {{ index + 1 }}</strong>
                <button v-if="recVisitors.length > 1" type="button" @click="removeRecVisitor(index)">删除</button>
              </div>
              <div class="form-row">
                <div class="form-group half">
                  <span class="form-label">姓名 *</span>
                  <input class="form-input" v-model="visitor.visitorName" />
                </div>
                <div class="form-group half">
                  <span class="form-label">房号</span>
                  <input class="form-input" v-model="visitor.room" />
                </div>
              </div>
              <span class="form-label">诉求分类</span>
              <div class="type-row">
                <span class="type-chip" :class="visitor.category === 'property' ? 'on' : ''" @click="visitor.category = 'property'">物业类</span>
                <span class="type-chip" :class="visitor.category === 'public_affairs' ? 'on' : ''" @click="visitor.category = 'public_affairs'">公共事务</span>
                <span class="type-chip" :class="visitor.category === 'neighbor' ? 'on' : ''" @click="visitor.category = 'neighbor'">邻里纠纷</span>
                <span class="type-chip" :class="visitor.category === 'other' ? 'on' : ''" @click="visitor.category = 'other'">其他</span>
              </div>
              <div class="form-group">
                <span class="form-label">诉求内容 *</span>
                <textarea class="form-textarea" v-model="visitor.content"></textarea>
              </div>
            </div>
            <button class="rec-add-visitor" type="button" @click="addRecVisitor">＋ 继续添加业主</button>
            <div class="sheet-actions">
              <button class="btn btn-ghost" @click="recCreateOpen = false">取消</button>
              <button class="btn btn-primary" @click="submitReceptionCreate">确认登记</button>
            </div>
          </div>
        </div>
      </div>

    <!-- 委员且无相关会议：空闲提示 -->
    <div v-if="planTab === 'meeting' && !isChair && (!currents || !currents.length)" class="idle">
      <span class="idle-hint">暂无待处理会议</span>
    </div>

    <!-- 待发送草稿卡：发起会议填了一半返回，内容自动存草稿，放大成首页主角，突出「继续通知」 -->
    <div v-if="false && isChair && hasDraft && homeLayout === 'tabs'" class="draft-card">
      <div class="draft-card-top">
        <span class="draft-badge">通知编辑中</span>
      </div>
      <div class="draft-title">{{ draftTitle }}</div>
      <div v-if="draftSummary" class="draft-summary">{{ draftSummary }}</div>
      <div v-if="draftMaterialCount" class="draft-mat">📎 已附 {{ draftMaterialCount }} 份材料</div>
      <button class="draft-continue" @click="continueDraft">继续通知<span class="btn-arrow">›</span></button>
      <span class="draft-discard" @click.stop="discardDraft">放弃草稿</span>
    </div>

    <!-- 「更多功能」三格已删：接待/培训入口收进顶部计划卡横栏；历史记录走计划卡已开期或资料库 -->

    <!-- 「发起其他会议」已收进会议安排卡尾部(0725 用户定):原悬在卡外像孤儿 -->

    <div v-if="createVisible" class="modal-mask" @click="closeCreate">
      <div class="create-panel" @click.stop>
        <div class="create-head">
          <span class="create-back" @click="closeCreate">‹</span>
          <span class="create-title">发起业委会</span>
          <span class="create-nav-ph"></span>
        </div>

        <div class="create-body" style="overflow-y:auto;">
          <!-- 顶部分段切换：手动填写 / 拍照上传（两者平级，拍照入口更醒目） -->
          <div class="create-tabs">
            <div class="create-tab" :class="{ active: createTab === 'manual' }" @click="createTab = 'manual'">手动填写</div>
            <div class="create-tab" :class="{ active: createTab === 'scan' }" @click="createTab = 'scan'">
              {{ scanItems.length && createTab !== 'scan' ? '已识别 ' + scanItems.length + ' 份文件' : '拍照 / 上传' }}
            </div>
          </div>

          <!-- 拍照/上传面板：拍通知照片或传文件，AI 识别后在当前面板展示结果，并把识别内容预填到下方表单 -->
          <div v-show="createTab === 'scan'" class="scan-pane">
            <div class="doc-scan-bar inline">
              <!-- 缩略图预览条：图片显缩略图，PDF/其他显图标；可逐个删除 -->
              <div v-if="scanItems.length" class="ds-preview">
                <div v-for="it in scanItems" :key="it.id" class="ds-thumb">
                  <img v-if="it.isImage && it.thumbUrl" class="ds-thumb-img" :src="it.thumbUrl" :alt="it.name" @click="openScanItemPreview(it)" />
                  <span v-else class="ds-thumb-file" @click="openScanItemPreview(it)"><span class="ds-thumb-ico">{{ scanThumbIcon(it.ext) }}</span><span class="ds-thumb-ext">{{ it.ext || '文件' }}</span></span>
                  <span v-if="it.isImage && it.thumbUrl" class="ds-thumb-zoom" @click.stop="openScanItemPreview(it)">⤢</span>
                  <span class="ds-thumb-del" @click.stop="removeScanItem(it.id)">×</span>
                </div>
              </div>
              <div class="ds-cards ds-cards-2">
                <button class="ds-card" :disabled="scanRecognizing" @click="chooseImageSource">
                  <span class="ds-ico bl">🖼️</span>
                  <span class="ds-t">图片</span>
                </button>
                <button class="ds-card" :disabled="scanRecognizing" @click="startDocScan('file')">
                  <span class="ds-ico bl">📄</span>
                  <span class="ds-t">文件</span>
                </button>
              </div>
              <div class="ds-shared-hint">可上传图片或 PDF、Word 文件</div>
              <button v-if="scanItems.length" class="ds-recognize" :disabled="scanRecognizing" @click="recognizeScanItems">
                {{ scanRecognizing ? '识别中 ' + docProgress + '%' : '开始识别（' + scanItems.length + '）' }}
              </button>
            </div>
          </div>

          <!-- 会议内容表单：两个 tab 都显示；拍照/上传识别后就地填入这里 -->
          <div class="manual-pane">
          <!-- 会议信息：会议名称 + 时间地点合并为一个信息卡片 -->
          <div class="create-section meeting-info-card">
            <div class="form-group">
              <span class="field-caption caption-as-title">会议名称</span>
              <div class="title-row">
                <div class="title-input-wrap">
                  <textarea ref="titleEl" class="form-input large title-ta" :class="{ 'field-error': fieldErrors.title }" rows="1" v-model="createForm.title" :placeholder="suggestedTitle ? '' : '请输入会议名称'" @input="autoGrowTitle" @focus="clearFieldError('title')" @keydown.enter.prevent></textarea>
                  <!-- 推荐标题：半透明显示在文本框内，点文字直接填入 -->
                  <span v-if="createTab === 'manual' && suggestedTitle && !createForm.title" class="title-ghost" @click="createForm.title = suggestedTitle; clearFieldError('title')">{{ suggestedTitle }}</span>
                  <span v-show="createTab === 'manual'" class="title-clear" :class="{ dim: !createForm.title && !suggestedTitle }" @click="clearTitleOrGhost">×</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 时间/地点：单独成卡；日期+时间合并为一行，地点保留常用地点选择并单独露出地图入口 -->
          <div class="create-section meeting-info-card">
            <div class="field-list">
              <div class="field-line meeting-method-line">
                <span class="fl-label">召开方式</span>
                <div class="method-switch">
                  <button type="button" :class="{ active: createForm.meetingMethod === 'offline' }" @click="setMeetingMethod('offline')">线下会议</button>
                  <button type="button" :class="{ active: createForm.meetingMethod === 'online' }" @click="setMeetingMethod('online')">线上会议</button>
                </div>
              </div>
              <div class="field-line field-line-split" :class="{ 'field-error': fieldErrors.meetingDate || fieldErrors.meetingTime }">
                <div class="fl-part" @click="openDatePicker">
                  <span class="fl-label">日期</span>
                  <span class="fl-value" :class="{ ph: !createForm.meetingDate }">{{ createForm.meetingDate ? fmtPlanDate(createForm.meetingDate) : '' }}</span>
                  <span class="fl-arrow">›</span>
                </div>
                <div class="fl-part fl-part-time" @click="openTimePicker">
                  <span class="fl-label">时间</span>
                  <span class="fl-value" :class="{ ph: !createForm.meetingTime }">{{ createForm.meetingTime }}</span>
                  <span class="fl-arrow">›</span>
                </div>
              </div>
              <div v-if="createForm.meetingMethod !== 'online'" class="field-line field-line-location" :class="{ 'field-error': fieldErrors.location }">
                <!-- 选「其他地点」时：本行直接变输入框（不再另弹文本框）；点「地点」标签可回到常用地点选择 -->
                <template v-if="locationPreset === '__other__'">
                  <span class="fl-label fl-label-tap" @click="openLocPicker">地点</span>
                  <input class="fl-inline-input" v-model="createForm.location" placeholder="请输入会议地点" @focus="clearFieldError('location')" />
                </template>
                <div v-else class="fl-loc-main" @click="openLocPicker">
                  <span class="fl-label">地点</span>
                  <span class="fl-value" :class="{ ph: !createForm.location }">{{ createForm.location }}</span>
                  <span class="fl-arrow">›</span>
                </div>
                <button class="loc-map-btn field-map-btn" @click.stop="pickLocationOnMap" aria-label="从地图选点">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#1A73E8" d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7zm0 9.5A2.5 2.5 0 1 1 12 6a2.5 2.5 0 0 1 0 5.5z"/></svg>
                </button>
              </div>
              <div v-else class="field-line field-line-location">
                <span class="fl-label online-platform-label">线上平台</span>
                <select class="platform-select" v-model="createForm.location">
                  <option value="微信工作群">微信工作群</option>
                  <option value="腾讯会议">腾讯会议</option>
                </select>
              </div>
            </div>
          </div>

          <!-- 会议议程项（在当前卡片内逐条添加和编辑） -->
          <div class="create-section">
            <div class="section-title-row topic-head">
              <span class="section-title">会议议题</span>
            </div>
            <div v-if="createForm.topics.length" class="topic-list">
              <div v-for="(topic, idx) in createForm.topics" :key="idx" class="topic-line" @click="openEditTopic(idx)">
                <span class="topic-line-text"><b>{{ idx + 1 }}.</b> {{ topic.title }}</span>
                <span class="ts-badge topic-line-badge" :class="topicTypeClass(topic)">{{ topicTypeLabel(topic) }}</span>
                <span class="topic-line-del" @click.stop="removeCreateTopic(idx)">×</span>
              </div>
            </div>
            <div v-show="createTab === 'manual' && !topicDialogOpen" class="topic-add-trigger" :class="{ 'field-error': fieldErrors.topics }" @click="openAddTopic()">
              <span class="tat-ico">＋</span><span class="tat-text">添加议题</span>
            </div>
            <div v-if="createTab === 'manual' && topicDialogOpen" class="topic-inline-editor">
              <!-- 标题「添加议题」已删；「取消」并入「议题内容」标签行，标签+chips 同行（0723 用户定，卡片压缩） -->
              <div class="form-group">
                <div class="tie-label-row">
                  <span class="form-label">议题内容</span>
                  <button type="button" class="tie-cancel" @click="topicDialogOpen = false">取消</button>
                </div>
                <!-- 灰色占位文案已删（0723 用户定）：标签「议题内容」已经说明用途，占位字是重复噪音 -->
                <div class="td-title-row">
                  <input class="form-input large" v-model="topicDraft.title" />
                </div>
              </div>
              <div class="form-group tie-inline-row">
                <span class="form-label">议题类型</span>
                <div class="type-row">
                  <!-- 0728 用户定：事项分三类——通知 / 讨论 / 表决。通知与讨论操作一致（均不表决、只需宣读/记录），
                       仅分类不同；只有表决需要投票。底层枚举 notice / discussion / decision 一一对应。 -->
                  <span class="type-chip" :class="{ on: topicDraft.type === 'notice' }" @click="draftPickType('notice')">通知</span>
                  <span class="type-chip" :class="{ on: topicDraft.type === 'discussion' }" @click="draftPickType('discussion')">讨论</span>
                  <span class="type-chip" :class="{ on: topicDraft.type === 'decision' }" @click="draftPickType('decision')">表决</span>
                </div>
              </div>
              <!-- 「补充通知正文」入口已删（0723 用户定，卡片压缩）：底层 content→notice 映射保留，旧议题的正文编辑保存时原样带回 -->
              <div class="form-group tie-inline-row" v-if="topicDraft.type === 'decision'">
                <span class="form-label">表决方式</span>
                <div class="type-row">
                  <span class="type-chip" :class="{ on: topicDraft.decisionType === 'simple' }" @click="draftPickDecision('simple')">是 / 否</span>
                  <span class="type-chip" :class="{ on: topicDraft.decisionType === 'multi_choice' }" @click="draftPickDecision('multi_choice')">多选一</span>
                </div>
              </div>
              <div class="form-group" v-if="topicDraft.type === 'decision' && topicDraft.decisionType === 'multi_choice'">
                <span class="form-label">选项（至少两个）</span>
                <div v-for="(opt, oi) in topicDraft.options" :key="opt.id" class="ct-option-row">
                  <span class="ct-opt-num">{{ oi + 1 }}.</span>
                  <input class="form-input ct-opt-input" v-model="opt.label" />
                  <span v-if="topicDraft.options.length > 1" class="tp-del" @click="draftRemoveOption(oi)">×</span>
                </div>
                <span class="add-link tie-add-option" @click="draftAddOption">+ 添加选项</span>
              </div>
              <button type="button" class="tie-confirm-btn" @click="confirmTopic">确定添加议题</button>
            </div>
          </div>

          <!-- 居委会见证（说明式开关卡片）：初始就显示，仅标记 hasMajorIssue，不自动通知。
               0723 用户定：主标题只留「含重大事项」，两条制度要求（提前 7 天公告、居委会到场见证）小字补充 -->
          <!-- 只点右侧开关才切换（0723 用户定）：整行可点容易误触 -->
          <div class="juwei-card" :class="{ disabled: createForm.meetingMethod === 'online' }">
            <div class="juwei-text">
              <div class="juwei-title">含重大事项</div>
              <div class="juwei-sub">{{ createForm.meetingMethod === 'online'
                ? '线上会议不支持重大事项（须线下提前 7 天公告并请居委会到场见证）'
                : '需提前 7 天向业主公告，并请居委会到场见证' }}</div>
            </div>
            <span class="juwei-switch" :class="{ on: createForm.juweiWitness, disabled: createForm.meetingMethod === 'online' }"
                  role="switch" :aria-checked="createForm.juweiWitness" :aria-disabled="createForm.meetingMethod === 'online'"
                  @click="onJuweiToggle"></span>
          </div>

          <!-- 会议材料：拍照/上传识别出材料后才在底部出现（建会后自动挂到会议供委员传阅） -->
          <div v-if="pendingMaterials.length" class="create-section">
            <div class="section-title-row mat-head" @click="materialsOpen = !materialsOpen">
              <span class="section-title">会议材料（{{ pendingMaterials.length }}）</span>
              <span class="mat-toggle" :class="{ open: materialsOpen }" aria-label="展开查看材料详情">›</span>
            </div>
            <template v-if="materialsOpen">
              <div v-for="(m, idx) in pendingMaterials" :key="m.url" class="mat-card" @click="openMaterialViewer(m)">
                <span class="mat-badge" :class="'t-' + matBadge(m).cls">{{ matBadge(m).label }}</span>
                <div class="mat-info">
                  <span class="mat-fname">{{ m.fileName }}</span>
                  <span class="mat-fsize" v-if="m.sizeText">{{ m.sizeText }}</span>
                </div>
                <span class="mat-del" @click.stop="removePendingMaterial(idx)">×</span>
              </div>
            </template>
          </div>
          </div>

        </div>

        <!-- 添加议题时隐藏底部主按钮，避免真机键盘弹起时「取消/生成通知」压住「确定添加议题」 -->
        <div v-show="!topicDialogOpen" class="sheet-actions fixed">
          <button class="btn btn-ghost" @click="closeCreate">取消</button>
          <button class="btn btn-primary" @click="submitNewMeeting">生成通知<span class="btn-arrow">›</span></button>
        </div>
      </div>
    </div>

    <!-- 识别中：居中小弹窗（文档扫描动画 + 三步流程 + 动态文案；不做整页进度页） -->
    <div v-if="scanBusy" class="scan-pop-mask">
      <div class="scan-pop">
        <span class="sp-close" @click="cancelRecognize" aria-label="取消识别">×</span>
        <!-- 圆环进度：单一焦点，中心大百分比（替代原扫描图标+三步条+横进度条） -->
        <div class="sp-ring">
          <svg class="sp-ring-svg" viewBox="0 0 100 100">
            <defs>
              <linearGradient id="spGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#FFB733" />
                <stop offset="100%" stop-color="#C76A00" />
              </linearGradient>
            </defs>
            <circle class="sp-ring-track" cx="50" cy="50" r="42" />
            <circle class="sp-ring-fill" cx="50" cy="50" r="42" :style="{ strokeDashoffset: 264 * (1 - docProgress / 100) }" />
          </svg>
          <div class="sp-ring-center"><div class="sp-ring-val"><span class="sp-ring-num">{{ docProgress }}</span><span class="sp-ring-pct">%</span></div></div>
        </div>
        <span class="sp-title">AI 智能识别中</span>
        <span class="sp-say">{{ scanSay }}</span>
        <span class="sp-foot">豆包大模型 · 已用时 {{ scanSec }}s</span>
      </div>
    </div>

    <!-- AI 识别完成：定制结果卡（识别为通知/材料、已识别/待补填字段、耗时+token） -->
    <div v-if="scanResultCard" class="scan-result-mask" @click.self="closeScanResult">
      <div class="scan-result">
        <div class="sr-hero">
          <div class="sr-hero-badge"><span class="sr-check">✓</span></div>
          <div class="sr-hero-title">识别完成</div>
        </div>

        <div class="sr-body">
          <!-- 多份不同的会议通知：列出每份，让用户选正确的一份填入（或手动填写） -->
          <div v-if="scanResultCard.mode === 'multi-notice'" class="sr-card sr-multi">
            <div class="sr-row-top"><span class="sr-pill warn">多份通知</span><span class="sr-row-note">请选正确的一份</span></div>
            <div v-for="(opt, i) in scanResultCard.noticeOptions" :key="i" class="sr-notice-opt" :class="{ sel: scanResultCard.selectedNotice === i }" @click="scanResultCard.selectedNotice = i">
              <span class="sr-radio" :class="{ on: scanResultCard.selectedNotice === i }"></span>
              <div class="sr-notice-body">
                <div class="sr-notice-title">{{ opt.title || '（未填会议名称）' }}</div>
                <div class="sr-notice-meta">{{ [formatScanDateTime(opt.meetingDate, opt.meetingTime), opt.location].filter(Boolean).join(' · ') || '时间地点未填' }}</div>
              </div>
            </div>
          </div>

          <!-- 单份通知：直接展示识别字段（单份不提示类别，多份/仅材料才提示） -->
          <template v-if="scanResultCard.mode === 'notice'">
            <div class="sr-preview">
              <div v-for="(row, i) in scanNoticeRows" :key="i" class="sr-pv-row">
                <span class="sr-pv-label">{{ row.label }}</span>
                <div class="sr-pv-val-wrap">
                  <span class="sr-pv-value" :class="{ miss: row.miss }">{{ row.miss ? '未识别 · 待手填' : row.value }}</span>
                </div>
              </div>
              <!-- 材料随通知识别出时，作为同款字段行并入列表 -->
              <div v-if="scanResultCard.materialCount" class="sr-pv-row">
                <span class="sr-pv-label">会议材料</span>
                <div class="sr-pv-val-wrap">
                  <span class="sr-pv-value">{{ scanResultCard.materialCount }} 份</span>
                </div>
              </div>
            </div>
            <div v-if="scanResultCard.conflictNote" class="sr-alert">{{ scanResultCard.conflictNote }}</div>
          </template>

          <!-- 仅材料：单独成卡，并提示会议信息手填 -->
          <template v-if="scanResultCard.mode === 'material'">
            <div class="sr-cat material"><span class="sr-cat-ico">📎</span>这是会议材料</div>
            <div class="sr-card">
              <div class="sr-card-main">
                <span class="sr-ico-chip material">📎</span>
                <div class="sr-card-text">
                  <div class="sr-card-title">共 {{ scanResultCard.materialCount }} 份材料</div>
                  <div class="sr-card-sub">将随会议一起，供委员在线传阅</div>
                </div>
              </div>
            </div>
            <div class="sr-tip">会议信息请手动填写</div>
          </template>

          <!-- 多份通知：材料压成一行说清（单份通知已并入上方字段列表） -->
          <div v-if="scanResultCard.mode === 'multi-notice' && scanResultCard.materialCount" class="sr-mat-line">
            <span class="sr-mat-ico">📎</span>另有 {{ scanResultCard.materialCount }} 份材料，一并加入传阅
          </div>

          <!-- 信息冲突：材料仍会添加，仅询问是否覆盖冲突字段 -->
          <div v-if="scanResultCard.conflicts && scanResultCard.conflicts.length" class="sr-card sr-conflict">
            <div class="sr-row-top"><span class="sr-pill warn">信息冲突</span><span class="sr-row-note">与你已填的不一致，是否覆盖？</span></div>
            <div v-for="(cf, i) in scanResultCard.conflicts" :key="i" class="sr-conf-line">
              <span class="sr-conf-label">{{ cf.label }}</span>
              <span class="sr-conf-old">{{ cf.cur }}</span>
              <span class="sr-conf-arrow">→</span>
              <span class="sr-conf-new">{{ cf.nv }}</span>
            </div>
          </div>
        </div>

        <div class="sr-meta">耗时 {{ scanResultCard.seconds }}s<template v-if="scanResultCard.tokens > 0"> · 消耗 {{ scanResultCard.tokens.toLocaleString() }} token</template></div>

        <div class="sr-actions">
          <button class="sr-btn ghost" @click="onScanGhost()">{{ scanResultCard.ghostLabel }}</button>
          <button class="sr-btn primary" @click="confirmScanResult(scanResultCard.needOverwriteAsk)">{{ scanResultCard.primaryLabel }}</button>
        </div>
      </div>
    </div>

    <!-- 模拟手机相机（测试用）：取景框对着纸质通知；拍后先进照片预览（重拍/使用照片），确认后才走识别；接真机后整块可删 -->
    <div v-if="mockCameraVisible" class="mock-cam" :class="{ preview: !!mockShotUrl }">
      <div class="mc-top">
        <span class="mc-badge">{{ mockShotUrl ? '照片预览' : '模拟相机 · 测试' }}</span>
        <span v-if="scanItems.length && !mockShotUrl" class="mc-count">已拍 {{ scanItems.length }} 张</span>
        <span class="mc-close" @click="closeMockCamera">×</span>
      </div>
      <!-- 取景模式 -->
      <template v-if="!mockShotUrl">
        <div class="mc-viewport" @touchstart.passive="onVfTouchStart" @touchend.passive="onVfTouchEnd">
          <img class="mc-paper" :src="mockViewfinderUrl" alt="取景中的纸质文件" />
          <span class="mc-corner tl"></span><span class="mc-corner tr"></span>
          <span class="mc-corner bl"></span><span class="mc-corner br"></span>
          <!-- 左右切换样张（模拟不同纸质文件） -->
          <button class="mc-nav prev" @click.stop="prevSample" aria-label="上一张">‹</button>
          <button class="mc-nav next" @click.stop="nextSample" aria-label="下一张">›</button>
          <span class="mc-sample-ind">{{ mockSampleIdx + 1 }} / {{ MOCK_SAMPLES.length }} · {{ currentSampleLabel }}</span>
          <span class="mc-tip">左右滑动切换文件 · 对准取景框</span>
        </div>
        <div class="mc-bottom">
          <button v-if="lastShotThumb" class="mc-roll" @click="closeMockCamera" aria-label="查看已拍照片">
            <img class="mc-roll-img" :src="lastShotThumb" alt="上一张" />
            <span class="mc-roll-badge">{{ scanItems.length }}</span>
          </button>
          <button class="mc-shutter" @click="mockShoot" aria-label="拍照"><span class="mc-shutter-core"></span></button>
          <button v-if="scanItems.length" class="mc-done" @click="closeMockCamera">完成（{{ scanItems.length }}）</button>
        </div>
      </template>
      <!-- 拍后预览：像系统相机一样先看照片，确认后再加入 -->
      <template v-else>
        <div class="mc-viewport">
          <img class="mc-paper shot" :src="mockShotUrl" alt="刚拍的照片" />
        </div>
        <div class="mc-confirm-tip">拍清楚了吗？点「确定」加入，可继续拍下一张</div>
        <div class="mc-bottom confirm">
          <button class="mc-btn retake" @click="mockRetake">重拍</button>
          <button class="mc-btn use" @click="mockUsePhoto">确定</button>
        </div>
      </template>
      <div class="mc-flash" :class="{ on: mockCamFlash }"></div>
    </div>

    <!-- 真·相机（getUserMedia 实时取景）：拍照按钮先申请权限，授权后进这里；桌面用网络摄像头、手机自动后置摄像头 -->
    <div v-if="realCamVisible" class="mock-cam real-cam" :class="{ preview: !!realShotUrl }">
      <div class="mc-top">
        <span class="mc-badge">{{ realShotUrl ? '照片预览' : '相机 · 实时取景' }}</span>
        <span v-if="scanItems.length && !realShotUrl" class="mc-count">已拍 {{ scanItems.length }} 张</span>
        <span class="mc-close" @click="closeRealCamera">×</span>
      </div>
      <!-- 取景模式：实时视频流 -->
      <template v-if="!realShotUrl">
        <div class="mc-viewport">
          <video ref="realVideoEl" class="mc-video" autoplay muted playsinline webkit-playsinline></video>
          <span class="mc-corner tl"></span><span class="mc-corner tr"></span>
          <span class="mc-corner bl"></span><span class="mc-corner br"></span>
          <span class="mc-tip">对准纸质文件 · 点圆钮拍摄</span>
        </div>
        <div class="mc-bottom">
          <button v-if="lastShotThumb" class="mc-roll" @click="closeRealCamera" aria-label="查看已拍照片">
            <img class="mc-roll-img" :src="lastShotThumb" alt="上一张" />
            <span class="mc-roll-badge">{{ scanItems.length }}</span>
          </button>
          <button class="mc-shutter" @click="realShoot" aria-label="拍照"><span class="mc-shutter-core"></span></button>
          <button v-if="scanItems.length" class="mc-done" @click="closeRealCamera">完成（{{ scanItems.length }}）</button>
        </div>
      </template>
      <!-- 拍后预览：确认后加入暂存，可继续连拍 -->
      <template v-else>
        <div class="mc-viewport">
          <img class="mc-paper shot" :src="realShotUrl" alt="刚拍的照片" />
        </div>
        <div class="mc-confirm-tip">拍清楚了吗？点「确定」加入，可继续拍下一张</div>
        <div class="mc-bottom confirm">
          <button class="mc-btn retake" @click="realRetake">重拍</button>
          <button class="mc-btn use" @click="realUsePhoto">确定</button>
        </div>
      </template>
      <div class="mc-flash" :class="{ on: realCamFlash }"></div>
    </div>

    <!-- 日期选择弹窗：小日历（月历网格），点日期即选 -->
    <div v-if="datePickerOpen" class="picker-pop-mask" @click="datePickerOpen = false">
      <div class="cal-pop" @click.stop>
        <div class="pop-close"><span class="close-btn" @click="datePickerOpen = false">×</span></div>
        <div class="cal-head">
          <span class="cal-nav" @click="calPrevMonth">‹</span>
          <span class="cal-title">{{ dpYear }}年{{ dpMonth }}月</span>
          <span class="cal-nav" @click="calNextMonth">›</span>
        </div>
        <div class="cal-week">
          <span v-for="w in ['日','一','二','三','四','五','六']" :key="w" class="cal-wd">{{ w }}</span>
        </div>
        <div class="cal-grid">
          <span v-for="(cell, i) in calCells" :key="i" class="cal-cell"
                :class="{ empty: !cell, disabled: cell && isPastMeetingDay(cell), on: cell && isSelectedDay(cell), today: cell && !isSelectedDay(cell) && isToday(cell) }"
                @click="cell && pickCalDay(cell)">{{ cell || '' }}</span>
        </div>
        <div class="pp-actions">
          <button class="btn btn-ghost" @click="datePickerOpen = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 时间选择弹窗：大按钮点选（时 + 分），免滚动，点选即生效 -->
    <div v-if="timePickerOpen" class="picker-pop-mask" @click="timePickerOpen = false">
      <div class="picker-pop" @click.stop>
        <div class="pop-close"><span class="close-btn" @click="timePickerOpen = false">×</span></div>
        <div class="tg-cur">{{ String(tpHour).padStart(2, '0') }}:{{ String(tpMinute).padStart(2, '0') }}</div>
        <div class="tg-label">时</div>
        <div class="tg-grid">
          <span v-for="h in hourOptions" :key="h" class="tg-cell"
                :class="{ on: h === tpHour, disabled: isPastTimeOption(h, 45) }"
                @click="setTpHour(h)">{{ String(h).padStart(2, '0') }}</span>
        </div>
        <div class="tg-label">分</div>
        <div class="tg-grid tg-grid-m">
          <span v-for="m in minuteOptions" :key="m" class="tg-cell"
                :class="{ on: m === tpMinute, disabled: isPastTimeOption(tpHour, m) }"
                @click="setTpMinute(m)">{{ String(m).padStart(2, '0') }}</span>
        </div>
        <div class="pp-actions">
          <button class="btn btn-primary" @click="confirmTime">确认</button>
        </div>
      </div>
    </div>

    <!-- 流式语音输入弹窗（标题 / 议题） -->
    <div v-if="voiceTarget === 'title' || voiceTarget === 'topic'" class="voice-modal-mask">
      <div class="voice-modal">
        <div class="vm-body">
          <div class="vm-wave"><span></span><span></span><span></span><span></span><span></span></div>
          <div class="vm-text">
            <span v-if="voiceFinal || voiceInterim">{{ voiceFinal }}<span class="vm-interim">{{ voiceInterim }}</span></span>
            <span v-else class="vm-placeholder">请说话输入{{ voiceTarget === 'title' ? '标题' : '议题' }}</span>
          </div>
        </div>
        <div class="vm-actions">
          <button class="btn btn-ghost" @click="cancelVoice">取消</button>
          <button class="btn btn-ghost" @click="retryVoice">重新输入</button>
          <button class="btn btn-primary" @click="confirmVoice">确认</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { onMounted, onActivated, onUnmounted } from 'vue'
import api from '@/api'
import { meetingRecordingSession, discardMeetingRecording } from '@/composables/meetingRecordingSession'
import perm from '@/utils/perm'
import { showModal, showActionSheet, toast } from '@/utils/ui'
import { navigateTo, redirectTo, switchTab } from '@/utils/navigate'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import PageNav from '@/components/PageNav.vue'
import MapPicker from '@/components/MapPicker.vue'
import { parseMeetingText } from '@/utils/meeting-parser'
import { pickFiles, humanSize } from '@/utils/upload'
import { isWecom, chooseWecomImages, isWecomCancel } from '@/utils/wecom'
import { applyHotwords } from '@/utils/helpers'
import { openMaterialViewer } from '@/composables/materialViewer'
import { aiTask } from '@/composables/aiTask'
import { homeShell } from '@/composables/homeShell'

const props = defineProps({
  section: { type: String, default: 'meeting' }
})

const isChair = ref(false)
const isRecorder = ref(false)
const isExternal = ref(false)
// 合并后的主页：顶栏 + 当前会议卡 所需状态
const activeRole = ref({})
const canCreate = ref(false)
const canViewInternal = ref(false)
const unread = ref(0)
const currents = ref([])            // 进行中/准备中的会议卡片
const STEP_BY_STAGE = { preparing: 1, ongoing: 2, ended: 3 }
const STEP_LABELS = ['', '准备开会', '正式开会', '会后总结']
const MEETING_STAGE_TEXT = { preparing: '未开始', ongoing: '进行中', ended: '已结束' }
// —— 首页指标（占位数字，计算规则待定后再接后端，届时替换这三个值即可）——
const score = ref(92)        // 业委会综合评分
// 评分红绿灯渐变阶梯(background-clip:text)：≥90薄荷绿 / 80-89草绿 / 70-79黄绿 / 60-69琥珀 / <60朱红。
// 0716 起数字直排深橙顶栏（无白胶囊），色阶整体调亮保对比度；若挪回浅色背景需换回深色版。
const scoreGradient = computed(() => {
  const s = score.value
  if (s >= 90) return 'linear-gradient(135deg, #3ECF96 0%, #17A874 100%)'
  if (s >= 80) return 'linear-gradient(135deg, #82CE4E 0%, #4F9E22 100%)'
  if (s >= 70) return 'linear-gradient(135deg, #C6CE3E 0%, #96A017 100%)'
  if (s >= 60) return 'linear-gradient(135deg, #F0B02C 0%, #C67C08 100%)'
  return 'linear-gradient(135deg, #EF6E4C 0%, #D23F26 100%)'
})
const curMonth = new Date().getMonth() + 1
const curYear = new Date().getFullYear()
const curPeriod = Math.ceil(curMonth / 2)   // 双月一期：1-2/3-4/5-6/7-8…；7月→第4期(7-8月)
const allMeetings = ref([])                 // 全部业委会会议（loadAll 填充），按期真实统计
// 日历「查看中」的年份。切换入口已摘（0716 用户定：先堵上这条路，以后有需要再做），
// 所以它现在恒等于 curYear。两者仍分开、不合并成一个：底下全部按年参数化（buildYearPlan(year)、
// meetingPeriod(m, year)、inMonth(d, m, year)），合并会把 curYear 重新焊死进这些逻辑里，
// 以后想恢复切换就得再拆一遍。恢复方式：加回箭头 + stepYear，范围规则见 commit 0e0d15f。
const viewYear = ref(curYear)
// 例会规则：每个双月期应召开 1 次（下面 yearPlan 每期一行即体现；接后端可调规则）
function periodLabel(p) { return ((p - 1) * 2 + 1) + '-' + (p * 2) + '月' }
// 首页履职待办：会议只要已结束，就不再作为“待开/逾期”催办项展示。
// compliance 是否有效留给详情/公示判断；首页待办避免同一场已结束会议继续催办。
function isHeldMeeting(m) { return !!m && m.stage === 'ended' }
// 会议属于指定年份（缺省 = 日历正在看的年份）第几期（不属于该年→0）
function meetingPeriod(m, year) {
  if (!m) return 0
  const y = year || viewYear.value
  const titleText = String(m.title || '')
  const titleMatch = titleText.match(new RegExp(y + '年第([1-6一二三四五六])次'))
  if (titleMatch) {
    const cn = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6 }
    return cn[titleMatch[1]] || Number(titleMatch[1])
  }
  if (!m.meetingDate) return 0
  const p = String(m.meetingDate).split('-')
  if (Number(p[0]) !== y) return 0
  return Math.ceil(Number(p[1]) / 2)
}

// 年度会议计划（今年 6 个双月期）：竖向时间轴总览，一条主线贯全年，节点亮灭即进度。放首页「前置」。
// 状态：done=已召开(取该期首场有效会议)｜current=本期待开｜overdue=已过期未开｜upcoming=未到、待排
// node=时间轴圆点内的字符：已开✓、逾期!、其余显期号(第几期)
const planBadge = { done: '已开 ✓', current: '待开', overdue: '逾期', upcoming: '待排' }
const demoDonePeriods = {
  1: { title: '2026年第1次业委会例会', meetingDate: '2026-01-18' },
  2: { title: '2026年第2次业委会例会', meetingDate: '2026-03-15' }
}
// 按年算 6 个双月期的状态。抽成函数（0716）：日历跟 viewYear 走，待办/逾期红条恒取今年——
// 否则老人翻去看 2025，今年的待办和红条会跟着一起消失，看着就像 App 坏了。
function buildYearPlan(year) {
  const heldByPeriod = {}
  for (const m of (allMeetings.value || [])) {
    if (!isHeldMeeting(m)) continue
    const p = meetingPeriod(m, year)
    if (p >= 1 && p <= 6 && !heldByPeriod[p]) heldByPeriod[p] = m
  }
  if (year === 2026) {
    for (const [period, meeting] of Object.entries(demoDonePeriods)) {
      if (!heldByPeriod[period]) heldByPeriod[period] = Object.assign({ stage: 'ended', compliance: 'valid' }, meeting)
    }
  }
  // current/overdue 是「相对今天」的紧迫状态，仅看当年才成立；往年只分已开与没开过，未来只分已排与待排。
  const isThisYear = year === curYear
  const isPastYear = year < curYear
  const rows = []
  for (let p = 1; p <= 6; p++) {
    const held = heldByPeriod[p]
    let status, sub, node, active = false
    if (held) {
      status = 'done'; node = '✓'
      const d = String(held.meetingDate || '').split('-')
      sub = d.length === 3 ? (Number(d[1]) + '月' + Number(d[2]) + '日 已召开') : '已召开'
    } else if (isThisYear && activeMeetingPeriods.value.has(p)) {
      // 该期会议正在准备/进行：不再算逾期/待开，宫格走待办淡黄+「进行中」（0716 用户定，原先开着会还挂红「逾期!」）
      status = 'current'; sub = '会议进行中'; node = String(p); active = true
    } else if (isThisYear && p === curPeriod) {
      status = 'current'; sub = '本期待召开'; node = String(p)
    } else if (isThisYear && p < curPeriod) {
      status = 'overdue'; sub = '已逾期未召开'; node = '!'
    } else if (isPastYear) {
      // 往年没记录 ≠「待排」：那年都过完了，再说「按计划待召开」是胡话。只陈述事实。
      status = 'upcoming'; sub = '没有召开记录'; node = '—'
    } else {
      status = 'upcoming'; sub = '按计划待召开'; node = String(p)
    }
    rows.push({ period: p, monthLabel: periodLabel(p), status, sub, node, active, past: isPastYear, meeting: held })
  }
  return rows
}
const yearPlan = computed(() => buildYearPlan(viewYear.value))   // 日历：跟年份箭头走
const thisYearPlan = computed(() => buildYearPlan(curYear))      // 待办/逾期红条：恒今年，不受翻年影响

// ── 履职年历：按路由 section 分开会/接待两态 + 12 月宫格 + 警示条 + 点月看当月该类事项 ──
const planTab = ref(props.section === 'reception' ? 'reception' : 'meeting')
watch(() => props.section, (section) => {
  planTab.value = section === 'reception' ? 'reception' : 'meeting'
})
// 会话内记住停留 tab（0717）：去学习/培训/接待的子页再回来，落回原 tab（show() 里读）
watch(planTab, v => { try { sessionStorage.setItem('homePlanTab', v) } catch (e) {} })
const calMonth = ref(curMonth)   // 选中月，默认本月
const calRecs = ref([])          // 全部接待记录（进页拉一次）
const receptionTodoOpen = ref(false)   // 0730 图一：待办默认折叠成「待办事项 · N项」摘要行，点开看清单
const recentOpenKey = ref('')
const recentReceptionRecords = computed(() => {
  const groups = new Map()
  ;(calRecs.value || []).forEach(r => {
    // 最近记录按接待日期归档：同一天分多次补录，也只占一条并持续累加来访居民。
    const key = 'day-' + (r.date || ('legacy-' + r.id))
    if (!groups.has(key)) groups.set(key, { key, date: r.date, time: r.time, receiver: r.receiver, records: [] })
    const group = groups.get(key)
    group.records.push(r)
    // 后补登记时间更晚时，摘要显示当天最后一次登记的时间和接待人。
    if (String(r.time || '') > String(group.time || '')) {
      group.time = r.time
      group.receiver = r.receiver
    }
  })
  return Array.from(groups.values()).map(session => {
    const visitorRecords = session.records.filter(r => r.visitorName !== '无人来访')
    const unresolved = session.records.filter(r => !r.done)
    const status = unresolved.length === 0
      ? 'done'
      : (unresolved.every(r => r.propertyTransferred || r.ticketPushed) ? 'doing' : 'pending')
    // 办结满一个月后由「公示中」转「已留档」：取本场次最晚的办结时间为公示起点。
    const resolvedTs = session.records
      .map(r => (r.resolvedAt ? new Date(r.resolvedAt).getTime() : 0))
      .reduce((a, b) => Math.max(a, b), 0)
    let archived = false
    if (resolvedTs > 0) { const dd = new Date(resolvedTs); dd.setMonth(dd.getMonth() + 1); archived = Date.now() >= dd.getTime() }
    // 收起态摘要（0723 用户定）：不点开也知道是谁、什么事
    let summaryText = ''
    if (visitorRecords.length === 1) {
      const r0 = visitorRecords[0]
      const brief = String(r0.content || '').slice(0, 12)
      summaryText = (r0.visitorName || '来访业主') + (brief ? '：' + brief + (String(r0.content || '').length > 12 ? '…' : '') : '')
    } else if (visitorRecords.length > 1) {
      const names = visitorRecords.map(r => r.visitorName).filter(Boolean)
      summaryText = names.slice(0, 3).join('、') + (names.length > 3 ? ' 等' : '')
    }
    return {
      ...session,
      displayRecords: visitorRecords,
      noVisit: visitorRecords.length === 0,
      visitorCount: visitorRecords.length,
      summaryText,
      status,
      // 无人来访=登记即留档；有来访的：待处理→处理中→已办结后公示一个月→已留档。
      statusText: visitorRecords.length === 0
        ? '已留档'
        : (status === 'done' ? (archived ? '已留档' : '公示中') : (status === 'doing' ? '处理中' : '待处理'))
    }
  }).sort((a, b) =>
    (String(b.date || '') + ' ' + String(b.time || '')).localeCompare(String(a.date || '') + ' ' + String(a.time || ''))
  ).slice(0, 2)
})
const planTabLabel = computed(() => planTab.value === 'meeting' ? '会议' : '接待')
// "2026-07-05" → "7月5日"（其他格式原样返回）
function fmtPlanDate(s) {
  const p = String(s || '').split('-')
  return p.length === 3 ? (Number(p[1]) + '月' + Number(p[2]) + '日') : String(s || '')
}
// 日期是否属于指定年份（缺省今年）第 m 月。
// 会议记录要跟着年份箭头走 → 传 viewYear；接待/培训没有年份切换器，仍按今年统计，别跟着跑偏。
function inMonth(dateStr, m, year) {
  const p = String(dateStr || '').split('-')
  return p.length >= 2 && Number(p[0]) === (year || curYear) && Number(p[1]) === m
}
// 已转物业或已派工单属于「已办理」中间态，不再占用首页待处理清单；填结果后才是已办结。
function receptionNeedsAction(r) {
  return !r.done && !r.propertyTransferred && !r.ticketPushed
}
// 接待日安排（0717）：接待 tab 上那张入口卡要显示当前接待时间和地点。
// 卡上只读，编辑和导出都在 /reception-notice 里
const recSystem = ref(null)
const cockpitLearningTasks = ref([])
const receptionTimeText = computed(() => {
  return String((recSystem.value && recSystem.value.timeDesc) || '')
    .replace(/[，,、]?\s*法定节假日暂停.*$/, '')
    .trim()
})
// 接待安排卡（0730 设计师定稿）：顶行统一灰字「M月D日 周四」（下一个接待日）；
// 语气只落在标题——今天「今晚 19:00 接待」/明天「明晚…」/其他日子制度句「每周四 起—止 接待」。
const receptionHero = computed(() => {
  if (!receptionTimeText.value) return { set: false }
  const info = nextReceptionInfo()   // {days, dateText:'M月D日 周四', startTime, range}
  const hour = parseInt(String(info.startTime).split(':')[0], 10) || 19
  const part = hour >= 18 ? '晚' : (hour >= 12 ? '下午' : '上午')
  const title = info.days === 0 ? ('今' + part + ' ' + info.startTime + ' 接待')
    : info.days === 1 ? ('明' + part + ' ' + info.startTime + ' 接待')
      : ('每周四 ' + info.range + ' 接待')
  return { set: true, dateLine: info.dateText, title, days: info.days }   // days 供驾驶舱「今日/明日」状态用
})
// 待办入口卡（0730 定稿）：未办结接待事项（排除无人来访占位）计数 + 内容短摘要
const recPendingList = computed(() => (calRecs.value || []).filter(r => !r.done && r.visitorName !== '无人来访'))
const recPendingSummary = computed(() => {
  const parts = recPendingList.value.map(r => String(r.content || '').replace(/\s+/g, '').slice(0, 10)).filter(Boolean)
  return parts.slice(0, 2).join('、') + (parts.length > 2 ? ' 等' : '')
})
// 进「业委会待办」聚合页（/minutes-todos 无参＝聚合模式）；软路由坑同款硬跳兜底
function goTodos() {
  navigateTo('/minutes-todos')
  setTimeout(() => {
    if (!document.querySelector('.todos-page')) window.location.href = '/minutes-todos'
  }, 300)
}
// 近期行副行（0730 定稿图）：列内容短摘要「反映 下水管返味、门禁卡失灵」，比「反映 2 项」信息量大
function recSummaryOf(session) {
  const parts = (session.displayRecords || []).map(r => String(r.content || '').replace(/\s+/g, '').slice(0, 10)).filter(Boolean)
  const text = parts.slice(0, 3).join('、') + (parts.length > 3 ? ' 等' : '')
  return text || (session.visitorCount + ' 项')
}
// 卡标题「X月接待安排」用的当前月份。取一次就够：跨月那一刻用户不会正开着页面
const recMonth = new Date().getMonth() + 1
function goReceptionNotice() {
  // 导航新规(0725):push 进入,目标页返回=历史上一页,驾驶舱/接待首页来的都天然回来处
  navigateTo('/pages/reception-notice/reception-notice')
  // 哨兵 .recep-notice 挂在目标页根上，进页即有、不等接口（同 goReceptionDetail 的兜底）
  setTimeout(() => { if (!document.querySelector('.recep-notice')) window.location.href = '/reception-notice' }, 300)
}

function goReceptionRecords() {
  navigateTo('/pages/reception-records/reception-records')
  setTimeout(() => {
    if (!document.querySelector('.reception-records')) window.location.href = '/reception-records'
  }, 300)
}
// 档案馆入口（0730 设计师定）：会议/接待/学习历史统一进档案馆(/library)，带 tab 直达对应页签
function goArchive(tab) {
  const url = '/library' + (tab ? '?tab=' + tab : '')
  navigateTo(url)
  // 软路由偶发不切视图（本仓已知坑，同 goReceptionRecords）：0.3s 后没见到档案馆根节点就硬跳
  setTimeout(() => {
    if (!document.querySelector('.arch-page')) window.location.href = url
  }, 300)
}

async function loadCalExtras() {
  const [recs, sys] = await Promise.all([
    api.receptionRecords('all').catch(() => []),
    api.receptionSystem().catch(() => null)
  ])
  calRecs.value = recs || []
  recSystem.value = sys || null
}
async function loadCockpitLearningTasks() {
  const [internal, training] = await Promise.all([
    api.learningList('internal', null).catch(() => []),
    api.learningList('training', null).catch(() => [])
  ])
  // Array.isArray 防御：接口异常返回对象时不至于让整页崩掉（学习任务缺失可接受，白屏不可接受）
  const all = [...(Array.isArray(internal) ? internal : []), ...(Array.isArray(training) ? training : [])]
  cockpitLearningTasks.value = all.filter(item => item && item.stage !== 'ended')
  // 0731 定稿：学习节空态要写「上次 X月X日 …」——留最近一条已完成的
  lastEndedLearning.value = all.filter(item => item && item.stage === 'ended')
    .sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')))[0] || null
}
const lastEndedLearning = ref(null)
// 12 个月宫格（随分类切换，每格一眼看该月该类状态）：
// 开会=该月所在双月期例会状态（已开绿✓/本期橙/逾期红!/待排灰）
// 接待=该月接待汇总（N件待办橙/已办结绿✓/无灰—）
const monthCells = computed(() => {
  const cells = []
  for (let m = 1; m <= 12; m++) {
    let st, label
    if (planTab.value === 'meeting') {
      const row = yearPlan.value[Math.ceil(m / 2) - 1] || {}
      st = row.status || 'upcoming'
      if (st === 'done') label = m % 2 === 0 ? '已开 ✓' : ' '
      else if (st === 'current') label = m % 2 === 0 ? (row.active ? '进行中' : '待开') : ' '
      // 逾期/待开等两个月一期的状态统一放在期末月，便于按 1-2、3-4、5-6 阅读。
      else if (st === 'overdue') label = m % 2 === 0 ? '逾期 !' : ' '
      // 往年没开过的期次写「未开」，不能写「待排」——那年已经过完，没什么可排的了
      else label = m % 2 === 0 ? (row.past ? '未开' : '待排') : ' '
    } else {
      const rs = calRecs.value.filter(r => inMonth(r.date, m))
      const todo = rs.filter(receptionNeedsAction).length
      if (todo) { st = 'warn'; label = todo + '件待办' }                               // 有待办：黄
      else if (m > curMonth) { st = 'future'; label = '' }                            // 还没到：蓝灰（不显示—）
      else { st = 'done'; label = rs.length ? '已办结 ✓' : '' }                        // 过完·无待办：绿（不显示—）
    }
    cells.push({ m, status: st, label, todo: 0 })
  }
  return cells
})
const monthPairs = computed(() => {
  const rank = { overdue: 4, current: 3, done: 2, upcoming: 1 }
  const pairs = []
  for (let p = 1; p <= 6; p++) {
    const months = monthCells.value.slice((p - 1) * 2, p * 2)
    const status = months.reduce((best, mc) => (rank[mc.status] > rank[best] ? mc.status : best), 'upcoming')
    pairs.push({
      period: p,
      title: ((p - 1) * 2 + 1) + '-' + (p * 2) + '月',
      status,
      months
    })
  }
  return pairs
})
const selectedMeetingPlanRow = computed(() => {
  if (planTab.value !== 'meeting') return null
  return yearPlan.value[Math.ceil(calMonth.value / 2) - 1] || null
})
// 点月分流：已开期→下方看该期记录；逾期/待开→提示并定位待办卡；未到期→下方出「提前准备会议」
function onYcMonthTap(pair, mc) {
  calMonth.value = mc.m
  // 文字反馈全部走日历下方常驻提示行（selPeriodFeedback，0716 用户定：toast 一闪而过老人看不清）；
  // 这里只保留视觉定位辅助：本期/逾期（且该期没有进行中会议）点击时待办卡闪两下帮老人找位置
  if ((pair.status === 'overdue' || pair.status === 'current') && !activeMeetingPeriods.value.has(pair.period)) {
    highlightPlanTodo()
  }
}
// 待办卡定位高亮：滚过去 + 闪两下，让老人看清要点哪里
const planTodoFlash = ref(false)
let _todoFlashTimer = null
function highlightPlanTodo() {
  const el = document.querySelector('.plan-todo-card')
  if (el && el.scrollIntoView) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  clearTimeout(_todoFlashTimer)
  planTodoFlash.value = false
  requestAnimationFrame(() => {
    planTodoFlash.value = true
    _todoFlashTimer = setTimeout(() => { planTodoFlash.value = false }, 1800)
  })
}
// 日历下方反馈区内容（随选中月所在期次状态）：done=记录列表；其余状态=一行常驻「当月状态」提示
// （0716 用户定：toast 一闪而过老人看不清，全部改为日历卡内常驻提示行；默认选中本月=进页即见本期状态）
const selPeriodFeedback = computed(() => {
  if (planTab.value !== 'meeting') return null
  const row = selectedMeetingPlanRow.value
  if (!row) return null
  const p = Math.ceil(calMonth.value / 2)
  const label = ((p - 1) * 2 + 1) + '-' + (p * 2) + '月'
  if (row.status === 'done') {
    const months = [p * 2 - 1, p * 2]
    const records = (allMeetings.value || [])
      .filter(mt => months.some(m => inMonth(mt.meetingDate, m, viewYear.value)))
      .sort((a, b) => String(a.meetingDate || '').localeCompare(String(b.meetingDate || '')))
      .map(mt => ({
        key: 'ypf' + mt.id,
        title: mt.title || '业委会会议',
        status: mt.stage === 'ended' ? 'done' : 'current',
        badge: mt.stage === 'ended' ? '已开 ✓' : (mt.stage === 'ongoing' ? '进行中' : '待开'),
        onTap: () => openMeetingTap(mt),
        // 已开的会议给「查看详情」明确按钮（与行点击同去详情页，给老人一个显眼的可点标识）
        detailTap: mt.stage === 'ended' ? () => openMeetingTap(mt) : null
      }))
    return { kind: 'records', records }
  }
  // 往年没开过：只陈述事实，别拿今年的话术套（那年都过完了，"还没到计划时间"是胡话）
  if (row.past) {
    return { kind: 'tip', tone: 'plain', tip: viewYear.value + '年' + label + '没有会议记录' }
  }
  // 该期有进行中/准备中的会议 → 指向下方会议卡（tone 与宫格同色系：提示条颜色跟格子走）
  // 加 viewYear 判断：进行中的是"今年"的会，翻到往年/明年就不能再说"该期会议正在进行"
  if (viewYear.value === curYear && activeMeetingPeriods.value.has(row.period)) {
    return { kind: 'tip', tone: 'warn', tip: '该期（' + label + '）会议正在进行，请从下方会议卡进入' }
  }
  if (row.status === 'current') {
    // 期限临近（剩<10天）：提示条同步升级为红色并写明剩余天数
    const urgent = viewYear.value === curYear ? currentPeriodUrgency.value : null
    if (urgent) {
      return { kind: 'tip', tone: 'overdue', tip: '本期（' + label + '）例会还没开，距期限只剩 ' + urgent.daysLeft + ' 天，请尽快安排' }
    }
    return { kind: 'tip', tone: 'warn', tip: '本期（' + label + '）例会还没开，请在下方待办事项中处理' }
  }
  if (row.status === 'overdue') {
    return { kind: 'tip', tone: 'overdue', tip: label + '例会已逾期，请在下方待办事项中尽快补开' }
  }
  // upcoming 待排
  return {
    kind: 'tip',
    tone: 'plain',
    tip: canCreate.value
      ? label + '例会还没到计划时间，想提前开可点页面底部「发起其他会议」'
      : label + '例会还没到计划时间'
  }
})
// 警示条（"到期没做，赶紧补"，随分类）：例会逾期红 / 接待待跟进橙
// 恒取今年：逾期是「你现在欠的账」，跟日历翻到哪一年无关
const overduePeriodRows = computed(() => thisYearPlan.value.filter(r => r.status === 'overdue'))
// 期限临近加强提示（0723 用户定）：本期（两个月一期）到期末只剩不到 10 天、例会还没开
// 也没有会议在准备/进行 → 待办行升级红色紧急态并写明剩余天数。恒取今年（同待办口径）。
const currentPeriodUrgency = computed(() => {
  const row = thisYearPlan.value[Math.ceil(curMonth / 2) - 1]
  if (!row || row.status !== 'current') return null
  if (activeMeetingPeriods.value.has(row.period)) return null // 本期已有会在办，不催
  const today = new Date()
  const end = new Date(curYear, row.period * 2, 0) // 期末月最后一天（月序号传下月的第 0 天）
  const daysLeft = Math.floor((end - new Date(today.getFullYear(), today.getMonth(), today.getDate())) / 86400000) + 1 // 含今天
  return daysLeft < 10 ? { row: row, daysLeft: Math.max(daysLeft, 0) } : null
})

// 首页改版（0724 领导意见#1）：顶部「当前重点」横幅——只呈现此刻最要紧的一件事 + 一个直达大按钮，
// 让关键信息一眼突出；下方日历/待办把已完成、未到期的内容折叠收起。优先级从上到下取第一个命中。
const HOME_V2 = true // 详情大会议卡并入顶部横幅（旧卡暂留代码，flag 关即回滚）
// 首页布局两版并存（0724：给领导选）：tabs=记录列表版（路线甲）｜portal=工作台宫格版（路线乙）。
// 由 ?home=portal|tabs 或 localStorage 决定，默认 tabs；真机可用 URL 当场切换对比。
const homeLayout = ref('tabs')
try {
  const q = new URL(window.location.href).searchParams.get('home')
  if (q === 'portal' || q === 'tabs') { homeLayout.value = q; setStorage('home_layout', q) }
  else { const s = getStorage('home_layout', ''); if (s === 'portal' || s === 'tabs') homeLayout.value = s }
} catch (e) {}
// 欢迎引导页（路线乙）：一句标语 + 选择要进入的业务线（顶层选择）。
// 三方联席会议/联合接待另做独立软件，不纳入本 App，故欢迎页只留三条真实业务线。
// 问候语（0729 用户定二改：与日期同行，不带姓名）
const greeting = computed(() => {
  const h = new Date().getHours()
  return h < 6 ? '夜深了' : h < 11 ? '早上好' : h < 13 ? '中午好' : h < 18 ? '下午好' : '晚上好'
})
// 欢迎引导页当前是否可见（portal 布局 + 开会 tab）→ 隐藏底栏；选定业务后置回，底栏出现
const welcomeVisible = computed(() => homeLayout.value === 'portal' && planTab.value === 'meeting')
watch(welcomeVisible, (v) => { homeShell.welcomeVisible = v }, { immediate: true })
// 选业务即"进入 App"：homeLayout 置 tabs，欢迎页从此让位，底栏出现
function enterWorkArea() { homeLayout.value = 'tabs'; setStorage('home_layout', 'tabs') }
// 顶栏「返回驾驶舱」（0730：由 TabBar 浮球移入各页顶栏）
function goCockpitFromHd() {
  setStorage('home_layout', 'portal')
  window.location.replace('/main?home=portal')
}
function enterCommitteeArea() {
  // 驾驶舱 URL 仍带 home=portal 时，TabBar 会据此继续隐藏。
  // 明确进入会议工作页并同步 URL，保证底栏和“返回驾驶舱”稳定出现。
  setStorage('home_layout', 'tabs')
  window.location.assign('/main?home=tabs')
}
function enterReceptionArea() {
  // 会议与接待复用同一个页面组件。先切业务状态再换路由，避免组件复用时短暂保留会议页。
  planTab.value = 'reception'
  enterWorkArea()
  switchTab('/reception-center')
}
function portalMeetingTimeText(meeting) {
  if (!meeting || !meeting.meetingDate) return ''
  if (meeting.stage === 'ongoing') return '进行中'
  const day = String(meeting.meetingDate).slice(0, 10)
  const time = String(meeting.meetingTime || '').slice(0, 5)
  const suffix = time ? time : ''
  const now = new Date()
  if (day === formatLocalDay(now)) return '今日' + suffix
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)
  if (day === formatLocalDay(tomorrow)) return '明日' + suffix
  const parts = day.split('-')
  return parts.length === 3 ? (Number(parts[1]) + '月' + Number(parts[2]) + '日') : '待召开'
}
const portalDomains = computed(() => {
  return [
    { key: 'committee', glyph: '会', title: '业委会会议', tone: 'blue',
      onTap: enterCommitteeArea },
    { key: 'reception', glyph: '访', title: '业主接待', tone: 'green',
      onTap: enterReceptionArea },
    { key: 'learning', glyph: '学', title: '学习培训', tone: 'amber',
      // 硬跳(0725 修):原 enterWorkArea+软跳,软路由偶发不切视图,人被留在刚切出来的甲页;
      // 与 enterCommitteeArea 同款做法——写回 tabs 布局后整页跳转,必达
      onTap: () => { setStorage('home_layout', 'tabs'); window.location.assign('/learning') } },
    { key: 'seal', glyph: '章', title: '印章管理', tone: 'blue',
      // 印章已并入「业委会」，用同款蓝色卡（0729 用户定：蓝-绿-橙-蓝 三色循环）
      // 印章管理独立页 /seal（用印申请 + 用印台账），同款硬跳保证必达
      onTap: () => { setStorage('home_layout', 'tabs'); window.location.assign('/seal') } }
  ]
})

function nextReceptionInfo() {
  const now = new Date()
  const target = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  let days = (4 - now.getDay() + 7) % 7
  const rangeMatch = String(receptionTimeText.value || '').match(/(\d{1,2}:\d{2})\s*[—–-]\s*(\d{1,2}:\d{2})/)
  const startTime = rangeMatch ? rangeMatch[1] : '19:00'
  const endTime = rangeMatch ? rangeMatch[2] : '20:00'
  if (days === 0) {
    const endParts = endTime.split(':').map(Number)
    const endAt = new Date(now.getFullYear(), now.getMonth(), now.getDate(), endParts[0] || 20, endParts[1] || 0)
    if (now > endAt) days = 7
  }
  target.setDate(target.getDate() + days)
  const dateText = (target.getMonth() + 1) + '月' + target.getDate() + '日 周四'
  const shortDateText = (target.getMonth() + 1) + '月' + target.getDate() + '日'
  const reminder = days === 0 ? '今天' : (days === 1 ? '明天' : '还有' + days + '天')
  return { dateText, shortDateText, startTime, range: startTime + '—' + endTime, reminder, days }
}

function daysFromToday(value) {
  if (!value) return null
  const text = String(value).slice(0, 10)
  const parts = text.split('-').map(Number)
  if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) return null
  const today = new Date()
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const target = new Date(parts[0], parts[1] - 1, parts[2])
  return Math.round((target.getTime() - start.getTime()) / 86400000)
}

const cockpitTodos = computed(() => {
  const items = []
  const f = homeFocus.value
  // 草稿属于哪一期（标题「第N次」或日期推算）：该期的「待安排/去补开」卡由草稿卡（继续通知）代表，不重复出
  const draftPeriod = hasDraft.value ? meetingPeriod(draft.value || {}, curYear) : 0
  // 焦点卡（临期/逾期）已指向某一期时，该期不再另出「待安排/去补开」卡，避免同期双卡
  const focusPeriod = f && f.periodRow ? Number(f.periodRow.period) : 0
  // 当前双月期是持续存在的履职要求，不能随着某条会议记录被删除而消失。
  // 未完成时始终给出安排入口；完成后保留一张轻量肯定卡，明确告诉用户本期已履职。
  const currentRow = thisYearPlan.value[curPeriod - 1]
  if (currentRow && currentRow.status === 'current' && !currentRow.active
      && draftPeriod !== curPeriod && focusPeriod !== curPeriod) {
    items.push({
      key: 'committee-current-period',
      tag: '业委会',
      tone: 'blue',
      level: 'active',
      title: currentRow.monthLabel + '例会待安排',
      sub: '请安排本期业委会例会',
      cta: isChair.value ? '去安排' : '等待通知',
      timeScope: 'recent',
      summaryLabel: '会议任务',
      daysUntil: null,
      // 0725 导航审计:不再先 enterWorkArea 切甲——驾驶舱是"来处",布局保持 portal,
      // 模态关闭后自然回驾驶舱;后续深链页的返回/首页落 /main 时也按存储偏好回驾驶舱
      onTap: () => onPlanRow(currentRow)
    })
  } else if (currentRow && currentRow.status === 'done') {
    items.push({
      key: 'committee-current-period-done',
      tag: '业委会',
      tone: 'blue',
      level: 'calm',
      title: currentRow.monthLabel + '例会已完成',
      sub: '本期按时履职，继续保持',
      cta: '',
      actionable: false,
      complete: true,
      timeScope: 'recent',
      daysUntil: null,
      onTap: enterCommitteeArea
    })
  }
  if (f && (f.level === 'urgent' || f.level === 'active') && f.cta) {
    const todayKey = formatLocalDay(new Date())
    const meetingDay = f.meeting && f.meeting.meetingDate ? String(f.meeting.meetingDate).slice(0, 10) : ''
    const meetingDays = daysFromToday(meetingDay)
    items.push({ key: 'committee', tag: '业委会', tone: 'blue', level: f.level,
      title: f.title, sub: f.sub, cta: f.cta,
      timeScope: meetingDay === todayKey ? 'today' : 'recent',
      summaryLabel: meetingDays === null ? '会议任务' : '会议',
      daysUntil: meetingDays === null ? null : Math.max(0, meetingDays),
      meeting: f.meeting || null,
      periodRow: f.periodRow || null,
      draft: !!f.draft,
      // 0725 导航审计:不再先 enterWorkArea——布局留在 portal,模态关闭/深链页返回都天然回驾驶舱
      onTap: () => { if (f.onTap) f.onTap() } })
  }
  // 逾期补开是硬性履职待办，不能被单焦点的草稿/会议卡顶掉：每个逾期期次各出一张「去补开」。
  // 焦点卡已是该期（无草稿时焦点=首个逾期）或草稿正是该期的补开会议时跳过，避免同期双卡。
  for (const row of overduePeriodRows.value) {
    const p = Number(row.period)
    if (p === focusPeriod || p === draftPeriod) continue
    items.push({ key: 'committee-overdue-' + p, tag: '业委会', tone: 'blue', level: 'urgent',
      title: row.monthLabel + '例会逾期未开', sub: '例会是履职核心，请尽快补开',
      cta: isChair.value ? '去补开' : '等待通知',
      timeScope: 'recent', summaryLabel: '会议任务', daysUntil: null, periodRow: row,
      // 0725 导航审计:同上,不再预切甲
      onTap: () => onPlanRow(row) })
  }
  // 兜底（0729 领导意见：会议待办卡不能消失）：只要还有进行中的会议——含整理已完成、
  // 待公示/归档的——首页必须保留一张会议直达卡；上面各分支都没出卡时在此补。
  if (!items.some(i => i.tag === '业委会')) {
    const og = (currents.value || []).find(c => c.stage === 'ongoing')
    if (og) {
      items.push({ key: 'committee-ongoing-' + og.id, tag: '业委会', tone: 'blue', level: 'active',
        timeScope: 'recent', summaryLabel: '会议任务', daysUntil: null,
        title: og.title, sub: [og.timeText, og.locationText].filter(Boolean).join(' · '),
        cta: og.ctaLabel || '会议详情', meeting: og, onTap: () => goCurrent(og) })
    }
  }
  const pendingReceptions = (Array.isArray(calRecs.value) ? calRecs.value : []).filter(receptionNeedsAction)
  if (pendingReceptions.length > 0) {
    const first = pendingReceptions[0] || {}
    const detail = first.content || first.visitorName || '业主诉求尚未完成办理'
    items.push({ key: 'reception', tag: '接待', tone: 'green', level: 'urgent', timeScope: 'recent',
      title: pendingReceptions.length + '件接待事项待处理', sub: detail, cta: '去处理', actionable: true,
      summaryLabel: '接待任务', daysUntil: 0,
      onTap: enterReceptionArea })
  } else if (receptionTimeText.value) {
    const next = nextReceptionInfo()
    const place = String((recSystem.value && recSystem.value.place) || '').trim()
    const communityName = String((activeRole.value && activeRole.value.communityName) || '').trim()
    const compactPlace = communityName && place.startsWith(communityName)
      ? place.slice(communityName.length).trim()
      : place
    items.push({ key: 'reception', tag: '接待', tone: 'green', level: 'calm', timeScope: 'recent',
      title: '下一次业主接待',
      sub: [next.shortDateText + ' ' + next.startTime, compactPlace, next.reminder].filter(Boolean).join(' · '),
      cta: canManageReception.value ? '登记接待' : '查看安排',
      secondaryCta: canManageReception.value ? '修改安排' : '',
      actionable: false,
      summaryLabel: '业主接待', daysUntil: next.days,
      onTap: canManageReception.value ? openReceptionCreate : enterReceptionArea,
      // 「修改安排」=快速直达调整接待安排页(/reception-notice)，不是进接待首页再找一遍按钮
      onSecondaryTap: goReceptionNotice })
  } else {
    items.push({ key: 'reception', tag: '接待', tone: 'green', level: 'urgent', timeScope: 'recent',
      title: '接待安排尚未设置', sub: '请先设置固定接待时间和地点',
      summaryLabel: '接待安排任务', daysUntil: null,
      cta: isChair.value ? '去设置' : '查看', actionable: true, onTap: enterReceptionArea })
  }
  // 学习培训（0729 用户定二改：多条任务都进列表，大卡内翻页）：按日期就近排序，最多 6 条。
  // 不设 summaryLabel——顶部摘要行已单独统计学习任务，避免重复计数。
  cockpitLearningTasks.value
    .map(t => ({ t, days: daysFromToday(t.date || t.trainingDate || t.startDate) }))
    .sort((a, b) => (a.days == null ? 999 : a.days) - (b.days == null ? 999 : b.days))
    .slice(0, 6)
    .forEach((entry, i) => {
      const when = entry.days === 0 ? '今天' : entry.days === 1 ? '明天' : (entry.days > 1 ? entry.days + '天后' : '')
      items.push({ key: 'learning-' + i, tag: '学习培训', tone: 'amber', level: entry.days === 0 ? 'urgent' : 'calm',
        timeScope: entry.days === 0 ? 'today' : 'recent', daysUntil: null,
        title: entry.t.title || '学习培训任务', sub: [when, entry.t.location].filter(Boolean).join(' · '),
        cta: '去查看', actionable: true,
        onTap: () => { setStorage('home_layout', 'tabs'); window.location.assign('/learning') } })
    })
  return items
})
const committeeCockpitTodos = computed(() => {
  return cockpitTodos.value
    .filter(item => item.key !== 'reception' && String(item.key).indexOf('learning') !== 0)
    .map((item, index) => {
      if (item.periodRow && Number(item.periodRow.period)) {
        return { item, index, order: Number(item.periodRow.period) }
      }
      if (item.meeting && item.meeting.meetingDate) {
        const month = Number(String(item.meeting.meetingDate).slice(5, 7))
        return { item, index, order: month ? Math.ceil(month / 2) : 99 }
      }
      if (item.key === 'committee-current-period' || item.key === 'committee-current-period-done') {
        return { item, index, order: curPeriod }
      }
      return { item, index, order: 99 }
    })
    .sort((a, b) => a.order - b.order || a.index - b.index)
    .map(entry => entry.item)
})
const learningCockpitTodos = computed(() => cockpitTodos.value.filter(item => String(item.key).indexOf('learning') === 0))
// 任务大卡三栏（0729 用户定）：业委会/接待/学习培训各自独立翻页，显示当前一条
const boardIdx = reactive({ committee: 0, reception: 0, learning: 0 })
const cockpitBoardRows = computed(() => {
  const mk = (key, label, tone, items, emptyText, emptyCta, onEmptyTap) => {
    const len = items.length
    const idx = len ? boardIdx[key] % len : 0
    return { key, label, tone, items, index: idx, cur: len ? items[idx] : null, emptyText, emptyCta, onEmptyTap }
  }
  return [
    mk('committee', '业委会', 'blue', committeeCockpitTodos.value, '本期暂无会议待办'),
    mk('reception', '接待', 'green', cockpitTodos.value.filter(i => i.key === 'reception'), '暂无接待安排'),
    // 空态也给「去发起」入口（0729 用户定）：没有学习任务时可直接去学习培训模块发起
    mk('learning', '学习培训', 'amber', learningCockpitTodos.value, '近期暂无学习培训', '去发起',
      () => { setStorage('home_layout', 'tabs'); window.location.assign('/learning') })
  ]
})
function boardShift(key, delta) {
  const row = cockpitBoardRows.value.find(r => r.key === key)
  if (!row || row.items.length < 2) return
  const len = row.items.length
  boardIdx[key] = ((boardIdx[key] + delta) % len + len) % len
}
function formatLocalDay(value) {
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}
// 摘要行（0730 用户定二改）：综合「当前时刻 × 任务时间 × 任务状态」组句——
// 每条待办生成一个带优先级的信号（过去没办完 > 今天正点的事 > 逾期 > 明天 > 更远），
// 每类先取最要紧的一条，再全局挑前两条，按时段措辞（今晚/明天下午/前天开的会…），分号串联。
const cockpitSummaryText = computed(() => {
  const sigs = []
  const now = new Date()
  const nowMin = now.getHours() * 60 + now.getMinutes()
  const toMin = (t) => { const m = String(t || '').match(/^(\d{1,2}):(\d{2})/); return m ? (+m[1]) * 60 + (+m[2]) : null }
  const pastWord = (d) => d === -1 ? '昨天' : d === -2 ? '前天' : (-d) + '天前'
  const futWord = (d, t) => {
    const min = toMin(t)
    const tod = min == null ? '' : (min < 720 ? '上午' : min < 1080 ? '下午' : '晚上')
    if (d === 0) return tod === '晚上' ? '今晚' : '今天' + tod
    if (d === 1) return tod === '晚上' ? '明晚' : '明天' + tod
    if (d === 2) return '后天' + tod
    return d + '天后'
  }
  const items = cockpitTodos.value
  // —— 会议：区分「开完了没办完的事」「还没结束的会」「将来的会」——
  const POST_STATE = { '会后整理': '还差会后整理没做完', '整理会议记录': '会议记录还没整理', '继续生成会议纪要': '会议纪要正在生成', '会议详情': '还没归档' }
  items
    .filter(i => i.tag === '业委会' && i.meeting && i.meeting.meetingDate)
    .forEach(i => {
      const d = daysFromToday(String(i.meeting.meetingDate).slice(0, 10))
      if (d === null) return
      const min = toMin(i.meeting.meetingTime)
      const cta = i.cta || ''
      const startedPast = d < 0 || (d === 0 && min != null && nowMin > min)
      if (POST_STATE[cta] && startedPast) {
        const w = d === 0 ? '今天开完的会' : pastWord(d) + '开的会'
        sigs.push({ p: 10 + Math.max(d, -3), cat: 'meeting', text: w + POST_STATE[cta] })
      } else if (cta === '进入会议' && startedPast) {
        sigs.push({ p: 11, cat: 'meeting', text: (d === 0 ? '今天' : pastWord(d)) + '的会议还没结束' })
      } else if (d >= 0) {
        const t = i.meeting.meetingTime ? ' ' + String(i.meeting.meetingTime).slice(0, 5) : ''
        sigs.push({ p: d === 0 ? 9 : d === 1 ? 6 : 4, cat: 'meeting', text: futWord(d, i.meeting.meetingTime) + t + ' 有业委会会议' })
      }
    })
  if (items.some(i => String(i.key).indexOf('committee-overdue') === 0)) sigs.push({ p: 8, cat: 'meeting', text: '有例会逾期未开' })
  if (items.some(i => i.key === 'committee-current-period')) sigs.push({ p: 5, cat: 'meeting', text: '本期例会还没安排' })
  // —— 接待：待办结事项 > 未设置 > 临近的接待日（带钟点）——
  const rec = items.find(i => i.key === 'reception')
  if (rec && rec.actionable !== false && String(rec.title).indexOf('待处理') >= 0) {
    sigs.push({ p: 9, cat: 'reception', text: String(rec.title).replace('待处理', '还没办结') })
  } else if (rec && rec.title === '接待安排尚未设置') {
    sigs.push({ p: 6, cat: 'reception', text: '固定接待时间还没设置' })
  } else if (rec && typeof rec.daysUntil === 'number' && rec.daysUntil <= 2) {
    const info = nextReceptionInfo()
    sigs.push({ p: rec.daysUntil === 0 ? 7 : 5, cat: 'reception', text: futWord(rec.daysUntil, info.startTime) + ' ' + info.startTime + ' 有业主接待' })
  }
  // —— 学习培训：最近一场，带名称（截短防挤爆）——
  const learn = learningCockpitTodos.value[0]
  if (learn) {
    const name = String(learn.title || '').slice(0, 12)
    const m = String(learn.sub || '').match(/^(今天|明天|(\d+)天后)/)
    if (m) {
      const d = m[1] === '今天' ? 0 : m[1] === '明天' ? 1 : Number(m[2])
      sigs.push({ p: d === 0 ? 7 : d === 1 ? 5 : 3, cat: 'learning', text: m[1] + '有学习培训「' + name + '」' })
    } else {
      sigs.push({ p: 3, cat: 'learning', text: '有一场学习培训待完成' })
    }
  }
  // 每类取最要紧的一条，全局挑前两条
  const byCat = {}
  for (const s of sigs.sort((a, b) => b.p - a.p)) if (!byCat[s.cat]) byCat[s.cat] = s
  const top = Object.values(byCat).sort((a, b) => b.p - a.p).slice(0, 2)
  if (!top.length) return '近期暂无待办，工作节奏正常'
  return top.map(s => s.text).join('；')
})
const cockpitDateText = computed(() => {
  const d = new Date()
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + weekdays[d.getDay()]
})
// ── 驾驶舱三模块聚合卡（0731 设计师定稿）──
// 每节：模块名 + 主行 + 副行 + 状态（三级：逾期=暖胶囊、今日/明日=接待绿、进行中=会议蓝）
const portalSections = computed(() => {
  // 会议节：最急项（heroBarSub＝「第3次例会 · 应于 5-6月」式）；无急项给下一场计划
  let mTitle, mSub = '', mBadge = '', mTier = ''
  const h = heroMeeting.value
  if (h) {
    mTitle = heroBarSub.value
    if (h.statusClass === 'overdue') { mBadge = '逾期'; mTier = 'st-warn' }   // 副行「尚未召开」已删（0731 用户定：逾期胶囊已表达，冗余）
    else if (h.statusClass === 'ongoing') { mSub = '会议进行中'; mBadge = '进行中'; mTier = 'st-blue' }
    else { mSub = dueSubFor(h) }
  } else {
    const n = nextRows.value[0]
    mTitle = n ? (nextWhen(n) + ' · ' + shortMeetingName(n.title)) : '近期无会议安排'
    if (n) mSub = '计划中'
  }
  // 接待节
  let rTitle, rSub = '', rBadge = '', rTier = ''
  if (receptionHero.value.set) {
    rTitle = receptionHero.value.title
    rSub = String((recSystem.value && recSystem.value.place) || '')
    if (receptionHero.value.days === 0) { rBadge = '今日'; rTier = 'st-green' }
    else if (receptionHero.value.days === 1) { rBadge = '明日'; rTier = 'st-green' }
  } else { rTitle = '还没设置接待时间' }
  // 学习节：有在办给在办；没有＝「近期无安排」+「上次 X月X日 …」
  const lt = cockpitLearningTasks.value[0]
  let lTitle, lSub = ''
  if (lt) {
    lTitle = lt.title
    lSub = [fmtPlanDate(lt.date), lt.stage === 'ongoing' ? '待整理' : (lt.notified ? '已通知' : '待通知')].filter(Boolean).join(' · ')
  } else {
    // 模块名标签已删（0731），空态标题自带「学习培训」四字补上下文
    lTitle = '近期无学习培训'
    const last = lastEndedLearning.value
    if (last) lSub = '上次 ' + fmtPlanDate(last.date) + (last.title ? ' ' + last.title : '')
  }
  return [
    { key: 'meeting', title: mTitle, sub: mSub, badge: mBadge, tier: mTier, onTap: enterCommitteeArea },
    { key: 'reception', title: rTitle, sub: rSub, badge: rBadge, tier: rTier, onTap: enterReceptionArea },
    { key: 'learning', title: lTitle, sub: lSub, badge: '', tier: '', onTap: goLearningHome }
  ]
})
// 驾驶舱轻列表：聚合待办计数（会议未办 + 接待未办）
const portalTodos = ref([])
async function loadPortalTodos() {
  try { portalTodos.value = (await api.committeeTodosOverview()) || [] } catch (e) { portalTodos.value = [] }
}
const ptTodoCount = computed(() =>
  portalTodos.value.filter(t => t.status !== 'done').length + recPendingList.value.length)
// 学习入口（与原 portalDomains 同款硬跳，软路由偶发不切视图）。印章入口已移入个人中心（0731）
function goLearningHome() { setStorage('home_layout', 'tabs'); window.location.assign('/learning') }
// 评分点击进个人中心（履职统计在那里）
function goScore() {
  navigateTo('/profile')
  setTimeout(() => { if (!document.querySelector('.profile-scroll')) window.location.href = '/profile' }, 300)
}
// 欢迎页落款/日期（0724 用户定，极淡）
const welcomeFootText = computed(() => {
  const org = activeRole.value && activeRole.value.communityName ? activeRole.value.communityName + '业主委员会' : '业委会智能助手'
  return org
})
const homeFocus = computed(() => {
  if (planTab.value !== 'meeting') return null
  const list = currents.value || []
  const ongoing = list.find(c => c.stage === 'ongoing' && !c.reviewDone)
  if (ongoing) return { level: 'active', kicker: ongoing.tag || '正在进行', title: ongoing.title,
    sub: [ongoing.timeText, ongoing.locationText].filter(Boolean).join(' · '),
    cta: ongoing.ctaLabel, icon: ongoing.ctaIcon, meeting: ongoing, onTap: () => goCurrent(ongoing) }
  // 只把「还需整理/生成纪要」的已结束会议当重点；已生成纪要（按钮=查看会议）算完成，不占焦点
  const ended = list.find(c => c.stage === 'ended' && c.ctaLabel !== '查看会议')
  if (ended) return { level: 'active', kicker: ended.minutesGen ? '会议纪要生成中' : '待整理会议记录',
    title: ended.title, sub: ended.timeText, cta: ended.ctaLabel, icon: ended.ctaIcon, meeting: ended, onTap: () => goCurrent(ended) }
  if (hasDraft.value) return { level: 'active', kicker: '通知编辑中', title: draftTitle.value,
    sub: draftSummary.value || '会议通知尚未完成', cta: '继续通知', draft: true, onTap: () => continueDraft() }
  const prep = list.find(c => c.stage === 'preparing')
  if (prep) return { level: 'active', kicker: '会议通知', title: prep.title,
    sub: [prep.timeText, prep.locationText].filter(Boolean).join(' · '), cta: prep.ctaLabel, icon: prep.ctaIcon,
    meeting: prep, onTap: () => goCurrent(prep) }
  const urg = viewYear.value === curYear ? currentPeriodUrgency.value : null
  if (urg) return { level: 'urgent', kicker: '本期例会临期', title: '本期例会还没召开',
    sub: '距期限只剩 ' + urg.daysLeft + ' 天，请尽快安排会议', cta: isChair.value ? '去通知' : '等待通知',
    periodRow: urg.row, onTap: () => onPlanRow(urg.row) }
  const od = overduePeriodRows.value[0]
  if (od) return { level: 'urgent', kicker: '例会逾期', title: od.monthLabel + '例会逾期未开',
    sub: '例会是履职核心，请尽快补开', cta: isChair.value ? '去补开' : '等待通知',
    periodRow: od, onTap: () => onPlanRow(od) }
  // 表扬语（0724 用户定）：无待办时给正向反馈，不再是冷冰冰的"正常"
  const doneCount = (currents.value || []).filter(c => c.stage === 'ended').length
  return { level: 'calm', kicker: '履职状态',
    title: doneCount ? '例会按时召开，履职规范 👍' : '各项工作井然有序 👍',
    sub: '本期暂无待办事项，继续保持', cta: '', onTap: null }
})

const homeFocusItems = computed(() => {
  if (planTab.value !== 'meeting') return []
  const list = currents.value || []
  const items = []
  list.filter(c => c.stage === 'ongoing' && !c.reviewDone).forEach(c => items.push({
    key: 'ongoing-' + c.id, level: 'active', kicker: c.tag || '进行中', title: c.title,
    sub: [c.timeText, c.locationText].filter(Boolean).join(' · '),
    cta: c.ctaLabel || '进入会议', onTap: () => goCurrent(c)
  }))
  list.filter(c => c.stage === 'preparing').forEach(c => items.push({
    key: 'preparing-' + c.id, level: 'active', kicker: '待召开', title: c.title,
    sub: [c.timeText, c.locationText].filter(Boolean).join(' · '),
    cta: c.ctaLabel || '查看会议', onTap: () => goCurrent(c)
  }))
  list.filter(c => c.stage === 'ended' && c.ctaLabel !== '查看会议').forEach(c => items.push({
    key: 'ended-' + c.id, level: 'active',
    kicker: c.minutesGen ? '纪要生成中' : '待整理',
    title: c.title, sub: c.timeText, cta: c.ctaLabel, onTap: () => goCurrent(c)
  }))
  if (hasDraft.value) items.push({
    key: 'draft', level: 'active', kicker: '通知编辑中', title: draftTitle.value,
    sub: draftSummary.value || '会议通知尚未完成', cta: '继续通知', onTap: () => continueDraft()
  })
  if (items.length) return items
  const focus = homeFocus.value
  return focus ? [Object.assign({ key: 'status-focus' }, focus)] : []
})

// 首页会议记录列表（0724 领导意见#1）：原 12 格月历宫格空占版面、信息少 → 改竖排记录列表，
// 待办/待排期次常驻置顶，已完成的会议收进「已完成 N 场」折叠，点开才展。数据同源 yearPlan。
// recDoneOpen 已删(0725 方案A):已完成清单并入月历弹层,不再单独折叠
// 待召开卡（0730 五改）：底部动作条 + 列表选中高亮 = 最紧急项（欠账优先，点1）。
// 紧急度：进行中(在办) > 逾期(违规欠账) > 本期待召开 > 其它待召开 > 草稿。
const DUE_RANK = { ongoing: 0, overdue: 1, current: 2, upcoming: 3 }
const heroMeeting = computed(() => {
  const list = meetingRecordList.value.immediate
  if (!list.length) return null
  const rank = (r) => String(r.key).indexOf('mr-draft') === 0 ? 4 : (DUE_RANK[r.statusClass] ?? 3)
  return [...list].sort((a, b) => rank(a) - rank(b))[0]
})
// 短名（点6）：列表统一「第N次业委会例会」；非例会（专项议事会等）保留原名；长名只在全年一览
function shortMeetingName(title) {
  const m = String(title || '').match(/第\s*(\d+)\s*次/)
  return m ? ('第' + m[1] + '次业委会例会') : String(title || '')
}
// 右侧常驻状态标签（点2/4：无边框浅底文字，与"选中"分离）
function dueStatusText(row) {
  if (row.statusClass === 'overdue') return '逾期'
  if (row.statusClass === 'ongoing') return '进行中'
  if (String(row.key).indexOf('mr-draft') === 0) return '编辑中'
  return '待召开'
}
// 状态字三级样式（0730 设计师定，全 app 通用）——判据：这个状态是否需要用户额外做点补救？
//   ① st-warn 暖色胶囊(有底)＝异常需补救(逾期)：一屏最多一种，全页唯一跳出来的东西
//   ② st-today 蓝字(无底)＝今日要办但不异常(进行中/今日)
//   ③ st-muted 灰字(无底)＝其余全部(待召开/待确认/计划中/编辑中/已归档…)＝常态，不能给底色否则异常不显眼
function dueStatusTier(row) {
  if (row.statusClass === 'overdue') return 'st-warn'
  if (row.statusClass === 'ongoing' || dueStatusText(row) === '进行中') return 'st-today'
  return 'st-muted'
}
// 副行：具体信息/下一步提示（状态词已在右侧标签，这里不重复）
function dueSubFor(row) {
  if (String(row.key).indexOf('mr-draft') === 0) return '通知尚未填写完成'
  if (row.statusClass === 'overdue') return '已超期，请尽快补开'
  if (row.statusClass === 'ongoing') return '会议进行中，点击继续'
  const info = (row.range || !row.meetingDate) ? '本期内 · 日期未定' : (String(row.sub || '').split(' · ')[0] || '')
  return info
}
// 底部主按钮副行（0730 点5）：状态词由待召开卡的标签承担，这里只写「哪场 · 什么时候」，
// 不再出现「已逾期未召开」这类与卡内副行重复的话。有确切日期＝「9月26日 · 第5次例会」；
// 没定日期＝「第3次例会 · 应于 5-6月」（逾期）/「第4次例会 · 本期 7-8月」
const heroBarSub = computed(() => {
  const h = heroMeeting.value
  if (!h) return ''
  const m = String(h.title || '').match(/第\d+次/)
  const short = m ? m[0] + '例会' : String(h.title || '').slice(0, 10)
  if (h.range || !h.meetingDate) {
    const period = String(h.badgeTop || '') + String(h.badgeBot || '')
    const when = period ? ((h.statusClass === 'overdue' ? '应于 ' : '本期 ') + period) : ''
    return [short, when].filter(Boolean).join(' · ')
  }
  const timeSeg = (String(h.sub || '').split(' · ')[0] || '').split(' ')[0]
  return [timeSeg, short].filter(Boolean).join(' · ')
})
// 后续行的时段：有日期「8月5日」；没定日期给期次「11-12月」（0731 用户定：「预计」冗余不写——
// 右侧「计划中」已表达未定性）
function nextWhen(row) {
  const d = String(row.meetingDate || '').split('-')
  if (d.length === 3) return Number(d[1]) + '月' + Number(d[2]) + '日'
  const seg = String(row.badgeTop || '') + String(row.badgeBot || '')
  return seg || (String(row.sub || '').split(' · ')[0] || '')
}
// （全年总场次计数已删——0730 用户定：全年是"至少6场"的不定数，不写数字免误导）
// 接下来（0730 三改）：待处理已全量并入主卡横滑，这里只剩计划期次
const nextRows = computed(() => meetingRecordList.value.planned)
// 截断而非折叠（0730 设计师定）：默认只显示最近 2 场，其余由「还有 N 场 ›」一次展开。
// 首屏高度恒定（无论 2 场还是 8 场）；积压时更该让用户看到前几场，而不是收起来看不到。
const NEXT_PREVIEW = 2
const nextExpanded = ref(false)
const visibleNextRows = computed(() => nextExpanded.value ? nextRows.value : nextRows.value.slice(0, NEXT_PREVIEW))
const nextMoreCount = computed(() => Math.max(0, nextRows.value.length - NEXT_PREVIEW))
const meetingCalendarOpen = ref(false)
// （折叠态的 sessionStorage 保持机制已整体退役：0730 用户定「返回默认收起、回顶部」+ 后续计划改平铺）
// （补开判定 isMakeupHeld 已删，0729 用户定：完成列表不再标「补开」，月历已表达各期执行情况）
const meetingRecordList = computed(() => {
  const rows = yearPlan.value || []
  const toRow = (r) => {
    const current = (currents.value || []).find(c => meetingPeriod(c, viewYear.value) === r.period)
    const draftMatch = hasDraft.value && meetingPeriod(draft.value || {}, viewYear.value) === r.period
    if (current) {
      const state = current.stage === 'ongoing' ? (current.fieldEnded ? '会后整理' : '进行中')
        : current.stage === 'preparing' ? '去召开'
          : (current.minutesGen ? '纪要生成中' : (current.ctaLabel === '查看会议' ? '已完成' : '待整理'))
      return {
        key: 'mr-current-' + current.id, done: current.stage === 'ended' && current.ctaLabel === '查看会议',
        // 日历叶(日上月下)只给有确切日期的会议;没定日期的显示期次横排胶囊(range)
        badgeTop: current.meetingDate ? Number(String(current.meetingDate).split('-')[2]) + '日' : String(r.monthLabel || ''),
        badgeBot: current.meetingDate ? Number(String(current.meetingDate).split('-')[1]) + '月' : '',
        range: !current.meetingDate,
        title: current.title || ('第' + r.period + '次业委会例会'),
        sub: [current.timeText, current.locationText].filter(Boolean).join(' · '),
        statusLabel: state,
        statusClass: current.stage === 'ongoing' ? 'current' : (current.stage === 'ended' ? 'done' : 'upcoming'),
        meetingDate: current.meetingDate || '',   // 主卡倒计时用
        onTap: () => goCurrent(current)
      }
    }
    if (draftMatch) {
      return {
        key: 'mr-draft-' + r.period, done: false,
        badgeTop: String(r.monthLabel || ''), badgeBot: '', range: true,
        title: draftTitle.value,
        // 副标题从短(0725):整行已不可点,不再写"点击继续";入口是右侧「编辑中」按钮
        sub: '会议通知还没写完',
        statusLabel: '编辑中', statusClass: 'current',
        onTap: () => continueDraft()
      }
    }
    const held = r.meeting
    if (r.status === 'done' && held) {
      const d = String(held.meetingDate || '').split('-')
      return { key: 'mr-' + r.period, done: true,
        badgeTop: d.length === 3 ? Number(d[2]) + '日' : r.monthLabel,
        badgeBot: d.length === 3 ? Number(d[1]) + '月' : '',
        range: d.length !== 3,
        title: held.title || ('第' + r.period + '次业委会例会'),
        sub: '会议已完成，可查看会议记录',
        statusLabel: '已完成', statusClass: 'done',
        // demoDonePeriods 前端填充的占位会议没有 id,点击进详情会 404,只对有真实记录的放行
        onTap: () => { if (held.id) openMeetingTap(held) } }
    }
    const label = r.status === 'current' ? (r.active ? '进行中' : '去召开')
      : r.status === 'overdue' ? '去补开' : (r.past ? '未召开' : '计划中')   // 0730 二改：待排→计划中
    const upcoming = r.status === 'upcoming' || (!r.past && r.status !== 'current' && r.status !== 'overdue')
    return { key: 'mr-' + r.period, done: false,
      // 0730 二改：计划期次徽标改「11-12 / 月」两行式，与日期叶同构
      badgeTop: String(r.monthLabel || '').replace(/月$/, ''), badgeBot: '月', range: true,
      title: upcoming ? (viewYear.value + '年第' + r.period + '次业主委员会例会') : ('第' + r.period + '次业委会例会'),
      sub: upcoming ? '日期未定' : r.sub,
      statusLabel: label, statusClass: r.status,
      onTap: () => onPlanRow(r) }
  }
  const active = rows.filter(r => r.status !== 'done').map(toRow)
  return {
    active,
    immediate: active.filter(row => row.statusClass !== 'upcoming' || row.key.indexOf('mr-current-') === 0),
    planned: active.filter(row => row.statusClass === 'upcoming' && row.key.indexOf('mr-current-') !== 0),
    done: rows.filter(r => r.status === 'done').map(toRow)
  }
})

// （meetingYearSummary 已删——0730 用户定点7：标题下摘要行与下方卡片重复，移除）

// 原地展开 + 自动滚到月历(0725 用户定):当初否掉原地展开的痛点是"展开在视野外、看不全"
const calendarPanelEl = ref(null)
function toggleMeetingCalendar() {
  meetingCalendarOpen.value = !meetingCalendarOpen.value
  if (meetingCalendarOpen.value) {
    nextTick(() => {
      if (calendarPanelEl.value && calendarPanelEl.value.scrollIntoView) {
        // block:'start' 顶到视口上沿(留 scroll-margin 缓冲):nearest 只滚"刚好露头",长内容仍看不全
        calendarPanelEl.value.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    })
  }
}
function onMeetingCalendarMonth(month) {
  const row = yearPlan.value[Math.ceil(Number(month) / 2) - 1]
  if (row) onPlanRow(row)
}

const calAlert = computed(() => {
  if (planTab.value === 'meeting') {
    const rows = overduePeriodRows.value
    if (!rows.length) return null
    const labels = rows.map(r => r.monthLabel).join('、')
    return {
      level: '',
      text: rows.length === 1 ? labels + '例会逾期未开' : rows.length + '期例会逾期未开',
      sub: rows.length === 1 ? '例会是履职核心，请先补齐逾期期次' : labels + '需补齐',
      go: isChair.value ? '去补开 ›' : '',
      onTap: () => onPlanRow(rows[0])
    }
  }
  const n = calRecs.value.filter(receptionNeedsAction).length
  if (!n) return null
  return { level: 'warn', text: n + '件接待待跟进', sub: '接待事项需要闭环反馈', go: '去处理 ›', onTap: () => {} }
})
const planFocusCards = computed(() => {
  if (planTab.value === 'meeting') {
    const currentRow = yearPlan.value[Math.ceil(curMonth / 2) - 1] || {}
    const overdueRow = overduePeriodRows.value[0] || null
    const nextRow = yearPlan.value.find(r => r.status === 'current' || r.status === 'upcoming') || currentRow
    const cards = [
      {
        key: 'current',
        label: '本月',
        title: currentRow.monthLabel ? currentRow.monthLabel + '例会' : curMonth + '月例会',
        sub: currentRow.status === 'done' ? '本期已完成' : (currentRow.status === 'overdue' ? '本期已逾期' : '本期还未召开'),
        status: currentRow.status || 'upcoming',
        onTap: () => currentRow.period && onPlanRow(currentRow)
      },
      {
        key: 'overdue',
        label: '逾期',
        title: overdueRow ? overdueRow.monthLabel + '例会' : '无逾期',
        sub: overdueRow ? '优先补开' : '履职节奏正常',
        status: overdueRow ? 'overdue' : 'done',
        onTap: () => overdueRow && onPlanRow(overdueRow)
      },
      {
        key: 'next',
        label: '下一期',
        title: nextRow.monthLabel ? nextRow.monthLabel + '例会' : '待排期',
        sub: nextRow.status === 'current' ? '当前待开' : (nextRow.status === 'upcoming' ? '按计划待召开' : '已完成'),
        status: nextRow.status || 'upcoming',
        onTap: () => nextRow.period && onPlanRow(nextRow)
      }
    ]
    return cards
  }
  const nowCell = monthCells.value.find(c => c.m === curMonth) || {}
  const attention = monthCells.value.find(c => c.status === 'overdue' || c.status === 'current') || null
  const nextCell = monthCells.value.find(c => c.m >= curMonth && c.status !== 'done') || nowCell
  return [
    { key: 'current', label: '本月', title: curMonth + '月' + planTabLabel.value, sub: nowCell.label || '暂无安排', status: nowCell.status || 'upcoming', onTap: () => { calMonth.value = curMonth } },
    { key: 'attention', label: attention && attention.status === 'overdue' ? '逾期' : '待办', title: attention ? attention.m + '月' + planTabLabel.value : '无待办', sub: attention ? attention.label : '暂无需要处理', status: attention ? attention.status : 'done', onTap: () => { if (attention) calMonth.value = attention.m } },
    { key: 'next', label: '下一项', title: nextCell ? nextCell.m + '月' + planTabLabel.value : '待安排', sub: nextCell ? nextCell.label : '暂无安排', status: nextCell ? nextCell.status : 'upcoming', onTap: () => { if (nextCell) calMonth.value = nextCell.m } }
  ]
})
// 选中月该类事项清单
const calList = computed(() => {
  const m = calMonth.value
  const items = []
  if (planTab.value === 'meeting') {
    for (const mt of (allMeetings.value || [])) {
      if (!inMonth(mt.meetingDate, m)) continue
      const ended = mt.stage === 'ended'
      items.push({
        key: 'm' + mt.id, icon: '📅', date: mt.meetingDate,
        title: mt.title || '业委会会议',
        sub: fmtPlanDate(mt.meetingDate) + (mt.location ? ' · ' + mt.location : ''),
        status: ended ? 'done' : 'current',
        badge: ended ? '已开 ✓' : '待开',
        onTap: () => openMeetingTap(mt)
      })
    }
    // 该期例会还没开 → 虚拟提醒行置顶（本期待开/逾期赶紧补）
    const row = yearPlan.value[Math.ceil(m / 2) - 1]
    if (row && (row.status === 'current' || row.status === 'overdue')) {
      items.unshift({
        key: 'plan' + row.period, icon: '📅', date: '',
        title: '第' + row.period + '期例会（' + row.monthLabel + '）',
        sub: isChair.value && row.status === 'overdue' ? '请尽快补开' : '',
        status: row.status,
        badge: isChair.value ? (row.status === 'overdue' ? '去补开' : '去通知') : '等待通知',
        onTap: () => onPlanRow(row)
      })
    }
  } else {
    for (const r of calRecs.value) {
      if (!inMonth(r.date, m)) continue
      items.push({
        key: 'r' + r.id, icon: '🤝', date: r.date,
        title: (r.visitorName || '来访') + ' 来访接待',
        sub: fmtPlanDate(r.date) + ' · ' + String(r.content || '').slice(0, 14),
        status: r.done ? 'done' : 'current',
        badge: r.done ? '已办结' : '去处理',
        onTap: () => goReceptionDetail(r)
      })
    }
  }
  // 虚拟提醒行(无日期)保持最前，其余按日期升序
  return items.sort((a, b) => (a.date === '' ? -1 : b.date === '' ? 1 : String(a.date).localeCompare(String(b.date))))
})
const currentMonthTaskList = computed(() => {
  const active = calList.value.filter(it => it.status !== 'done')
  return active.length ? active : calList.value
})
// 方案A：接待 tab 改「概览」——顶部三数字 metric（数据来自进页拉取的 calRecs，真实统计）
const planOverview = computed(() => {
  if (planTab.value === 'reception') {
    const recs = calRecs.value || []
    return [
      { key: 'pending', num: recs.filter(receptionNeedsAction).length, label: '待处理', tone: 'warn' },
      { key: 'done', num: recs.filter(r => r.done).length, label: '已完成', tone: '' },
      { key: 'all', num: recs.length, label: '全部记录', tone: '' }
    ]
  }
  return []
})
// ── 接待登记（0716 从已删的接待列表页搬来；字段/校验照搬，那套是验证过的）──
// ⚠ 命名避开 createVisible/createForm —— 那俩是「发起会议」在用的，同名会串
const recCreateOpen = ref(false)
const noVisitSaving = ref(false)
const canManageReception = ref(false)
// 接待人下拉框（0717 用户定：原生 select 替代底部弹单）。名单开弹窗时拉一次并缓存
const committeeRoster = ref([])
const receiverItems = computed(() =>
  committeeRoster.value.map(m => m.name + (m.role ? '（' + m.role + '）' : '')))
async function loadCommitteeRoster() {
  if (committeeRoster.value.length) return
  try { committeeRoster.value = (await api.committeeMembers()) || [] } catch (e) { /* 静默，选项为空 */ }
}
const recForm = reactive({ date: '', time: '', receiver: '' })
const recVisitors = ref([])
let recVisitorSeq = 0
function newRecVisitor() {
  return { key: ++recVisitorSeq, visitorName: '', room: '', category: 'property', content: '' }
}
function addRecVisitor() { recVisitors.value.push(newRecVisitor()) }
function removeRecVisitor(index) { recVisitors.value.splice(index, 1) }

// 默认接待时间取接待安排的起始时刻（如「每周四晚 19:00–20:00」→ 19:00），取不到退 19:00
function defaultReceptionTime() {
  const m = String((recSystem.value && recSystem.value.timeDesc) || '').match(/(\d{1,2})[:：](\d{2})/)
  return m ? (m[1].padStart(2, '0') + ':' + m[2]) : '19:00'
}
// 当前登录人姓名：值班的就是自己，接待人默认带出
function currentUserName() {
  const role = getStorage('activeRole', null) || {}
  return role.realName || role.name || ''
}

function openReceptionCreate() {
  if (!canManageReception.value) return
  // 导航新规(0725):push 进入,登记页返回/提交完成走历史回退,驾驶舱/接待首页来的都天然回来处
  window.location.assign('/reception-create')
}

async function submitReceptionCreate() {
  if (!recForm.date || !recForm.time) {
    toast({ title: '请先选择接待日期和时间', icon: 'none' })
    return
  }
  const incomplete = recVisitors.value.some(v => !String(v.visitorName || '').trim() || !String(v.content || '').trim())
  if (!recVisitors.value.length || incomplete) {
    toast({ title: '请补全每位业主的姓名和诉求内容', icon: 'none' })
    return
  }
  try {
    await api.receptionCreateSession({ ...recForm, visitors: recVisitors.value })
    toast({ title: '本次接待已登记', icon: 'success' })
    recCreateOpen.value = false
    await loadCalExtras()   // 重拉，新记录立刻出现在下面的清单里
  } catch (e) { toast({ title: (e && e.message) || '登记失败', icon: 'none' }) }
}

async function submitNoVisit() {
  if (noVisitSaving.value) return
  if (!recForm.date || !recForm.time) {
    toast({ title: '请先选择接待日期和时间', icon: 'none' })
    return
  }
  // 值班留档必须有值班人（真实制度：值班记录+值班人签名）
  if (!String(recForm.receiver || '').trim()) {
    toast({ title: '请选择值班接待人', icon: 'none' })
    return
  }
  noVisitSaving.value = true
  try {
    await api.receptionCreateSession({
      date: recForm.date,
      time: recForm.time,
      receiver: recForm.receiver,
      noVisit: true,
      visitors: []
    })
    toast({ title: '已登记无人来访', icon: 'success' })
    recCreateOpen.value = false
    await loadCalExtras()
  } catch (e) {
    toast({ title: (e && e.message) || '登记失败', icon: 'none' })
  } finally {
    noVisitSaving.value = false
  }
}

function openRecentSession(session) {
  // 无人来访 session：进详情看值班留档（详情页有留档只读视图），不再是死点击
  if (!session.displayRecords.length) {
    if (session.records && session.records.length) goReceptionDetail(session.records[0])
    return
  }
  if (session.displayRecords.length === 1) {
    goReceptionDetail(session.displayRecords[0])
    return
  }
  recentOpenKey.value = recentOpenKey.value === session.key ? '' : session.key
}

// removeReceptionSession 已删（0730 spec §10）：列表行不放删除 ×，删除走 ReceptionDetail 的「删除这条接待记录」

// 接待：点条目直接进这一条的处理页（0716 重做）。
// 原先是「弹窗看详情 → 再点『去接待页处理』→ 落到另一个清单页」，点两次才够得着，
// 而首页本来就有清单、落地页又是清单，纯属重复。现在一步到位。
function goReceptionDetail(r) {
  navigateTo('/pages/reception-detail/reception-detail?id=' + r.id)
  // 软路由偶发不切换页面（memory: soft-router-push-intermittent-no-switch），关键跳转加硬导航兜底。
  // 哨兵 .recep-detail 挂在目标页根上（0716 页头删除后从 .dh-title 迁来，进页即有、不等接口）
  setTimeout(() => { if (!document.querySelector('.recep-detail')) window.location.href = '/reception-detail?id=' + r.id }, 300)
}

// 接待 12 月宫格：点月下钻看当月清单（数据与 monthCells 同源的 calRecs）
// 接待/培训的 12 月宫格默认收起（0716 用户定）。理由：宫格是为「有制度节奏」的事设计的——
// 例会双月一期、每期该开一次，格子空着就是欠账，那 12 格是张达标图。而接待是来一个办一个、
// 培训也没有「这月该开 N 场」的硬标准，格子里的数字既不是达标也不是欠账，只是流水，
// 老人看了得不出任何结论。真正要回答的「还有几件没办、是哪几件」由上方三数字+清单负责。
// 不删是因为日历是老板提的：留在底部，他问起来展开给他看，顺带讲清适配性问题。
const ovGridFold = ref(true)
// 接待宫格的图例（0716 用户定加回）。开会 tab 不要，是因为那边每期都写着「已开✓/待开/逾期!/待排」，
// 颜色不是唯一载体；接待十二格里大半是空标签、只剩颜色，没图例就是死题（WCAG 1.4.1）。
// ⚠ 留个底：绿档同时覆盖「有记录且都办完」和「那月压根没记录」（monthCells 里 rs.length 为 0 时
// 走同一档），所以「已完成」对空月份略微 overclaim。用户已知情并选了它——「无待办」是双重否定，
// 老人读着费劲，而两种情况对他的实际含义都是「这儿没你的事」。真要较真得改 monthCells 让空月份走中性档。
// 措辞统一三个字（0716 用户定）：「还没到」是 future 的字面意思（monthCells 里判定就是 m > curMonth，
// 单纯指那个月还没来），也是本 App 一贯的大白话口气（首页写的是「本期例会还没开」）。
const ovLegend = computed(() => [{ k: 'warn', t: '有待办' }, { k: 'done', t: '已完成' }, { k: 'future', t: '还没到' }])
const calMonthTapped = ref(false)
function onOvMonthTap(m) { calMonth.value = m; calMonthTapped.value = true }
// meetCardOpen 已删（0717）：会议卡现在只在开会 tab 出现、恒展开，没有收起态了，
// 这个开关和它的「切 tab 归位」watch 都没有读者了。
const calMonthDrill = computed(() => {
  if (planTab.value === 'meeting' || !calMonthTapped.value) return null
  const m = calMonth.value
  const items = (calRecs.value || []).filter(r => inMonth(r.date, m))
    .slice().sort((a, b) =>
      a.done !== b.done ? (a.done ? 1 : -1) : String(a.date || '').localeCompare(String(b.date || '')))   // 与待办清单同规则：未处理在上
    .map(r => ({ key: 'cr' + r.id, title: (r.visitorName || '来访') + ' 来访接待', sub: fmtPlanDate(r.date) + (r.done ? ' · 已办结' : ' · 待跟进'), onTap: () => goReceptionDetail(r) }))
  return { title: m + '月接待（' + items.length + '）', items }
})
watch(planTab, () => { calMonthTapped.value = false })

// 概览三数字兼作筛选器：点某个数字，下方列表切到该范围（接待 month/pending/year，培训 done/todo/overdue），默认待办
const ovFilter = ref('pending')
watch(planTab, () => { ovFilter.value = 'pending' })
const planListTitle = computed(() => {
  if (planTab.value === 'reception') return ovFilter.value === 'done' ? '已完成事项' : (ovFilter.value === 'all' ? '全部接待记录' : '待办事项')   // 0730 图一：待处理事项→待办事项
  return '待办事项'
})
// 接待列表：受概览筛选（ovFilter），按日期升序；每项点「查看」弹该条详情
const allPendingList = computed(() => {
  if (planTab.value === 'reception') {
    const all = calRecs.value || []
    let recs
    if (ovFilter.value === 'done') recs = all.filter(r => r.done)
    else if (ovFilter.value === 'all') recs = all
    else recs = all.filter(receptionNeedsAction)
    // 排序（0716 用户定）：未处理在上、已办结沉底——这是工作清单不是台账，先回答「还有什么没办」。
    // 组内保留时间序（从早到晚）：挂得最久的未处理排最上。生效于本月/年度两个混合视图。
    return recs.slice().sort((a, b) =>
      a.done !== b.done ? (a.done ? 1 : -1) : String(a.date || '').localeCompare(String(b.date || ''))
    ).map(r => ({
      key: 'r' + r.id,
      title: (r.visitorName || '来访') + ' 来访接待',
      sub: fmtPlanDate(r.date),
      // 徽章必须看 r.done（0716 修）：原先硬编码「去处理」，年度累计里 5 条早已办结的
      // 也挂着「去处理」——徽章在撒谎。已办结走绿色「已办结」，点进去看详情照旧。
      date: r.date, status: r.done ? 'done' : 'view', badge: r.done ? '已办结' : '去处理', onTap: () => goReceptionDetail(r)
    }))
  }
  return []
})
// 有进行中/准备中会议的期次（按会议日期算双月期）→ 从待办里剔除，避免与首页会议卡重复
const activeMeetingPeriods = computed(() => {
  const set = new Set()
  for (const c of (currents.value || [])) {
    if (c.stage !== 'preparing' && c.stage !== 'ongoing') continue
    const mm = String(c.meetingDate || '').match(/^\d{4}-(\d{2})-\d{2}/)
    if (mm) set.add(Math.ceil(Number(mm[1]) / 2))
  }
  return set
})
const planTodoList = computed(() => {
  if (planTab.value !== 'meeting') return allPendingList.value
  const rows = []
  const active = activeMeetingPeriods.value
  // 恒取今年：待办是「现在要做的事」，跟日历翻到哪一年无关
  const currentRow = thisYearPlan.value[Math.ceil(curMonth / 2) - 1]
  if (currentRow && currentRow.status === 'current' && !active.has(currentRow.period)) rows.push(currentRow)
  for (const row of overduePeriodRows.value) {
    if (active.has(row.period)) continue
    if (!rows.some(r => r.period === row.period)) rows.push(row)
  }
  const urgent = currentPeriodUrgency.value
  return rows.map(row => {
    // 期限临近（剩<10天，0723 用户定）：本期待办行标红加剩余天数，催促感升级
    const isUrgent = !!(urgent && row.status === 'current' && row.period === urgent.row.period)
    return {
      key: 'todo-meeting-' + row.period,
      icon: '📅',
      title: '第' + row.period + '期例会（' + row.monthLabel + '）',
      sub: isUrgent ? ('距期限只剩 ' + urgent.daysLeft + ' 天，请尽快安排会议') : '',
      status: row.status,
      flag: isUrgent ? 'urgent' : '',
      badge: isChair.value ? (row.status === 'overdue' ? '去补开' : '去通知') : '等待通知',
      onTap: () => onPlanRow(row)
    }
  })
})
const currentStage = ref('preparing')
const meetings = ref([])
const pending = ref([])
const others = ref([])
const stats = ref({})
const publishScore = ref({ ontime: 0, overdue: 0, pending: 0 })
const counts = ref({ preparing: 0, ongoing: 0, ended: 0 })
const roleView = ref({ title: '', intro: '' })
const createVisible = ref(false)
// createReturnPortal 已删(0725 导航审计):驾驶舱入口不再预切甲,布局保持 portal,模态关闭天然回驾驶舱
// 「去安排/去补开」进来时自动预填的「第N次例会」标题快照：用户只看一眼没填任何东西就返回，
// 预填标题不算用户输入，不生成草稿（否则待办区凭空多出一张"继续通知"卡，还顶掉补开卡）
const prefilledCreateTitle = ref('')
const createForm = reactive({
  title: '',
  meetingDate: '',
  meetingTime: '',
  location: '',
  meetingMethod: 'offline',
  description: '',
  topics: [],
  topicsText: '',
  juweiWitness: false, // 居委会见证：勾选则建会后标记 hasMajorIssue（重大事项需居委会见证）
  locationLat: null,   // 地图选点回传的经纬度（0723）：随建会落库，详情页导航用精确坐标
  locationLng: null
})
// 地点被手填/选常用地点覆盖时，清掉不再匹配的旧坐标（地图选点回填的那次除外）
let _mapJustSet = false
watch(() => createForm.location, () => {
  if (_mapJustSet) { _mapJustSet = false; return }
  createForm.locationLat = null
  createForm.locationLng = null
})
const materialPrefillOpen = ref(false)
const materialText = ref('')
const materialFiles = ref([])
const materialScanResult = ref(null)
const keyword = ref('')

// 标题推荐
const suggestedTitle = ref('')
// 会议地点下拉
const commonLocations = ['社区活动室', '社区会议室']
const locationPreset = ref('社区活动室')
function setMeetingMethod(method) {
  createForm.meetingMethod = method
  // 线上会议不支持重大事项（须线下公告 + 居委会到场见证），切到线上时强制取消勾选
  if (method === 'online') createForm.juweiWitness = false
  if (method === 'online' && (!createForm.location || commonLocations.includes(createForm.location))) {
    createForm.location = '微信工作群'
    locationPreset.value = '__other__'
  } else if (method === 'offline' && ['微信工作群', '腾讯会议', '微信工作群、腾讯会议', '腾讯会议、微信工作群'].includes(createForm.location)) {
    createForm.location = '社区活动室'
    locationPreset.value = '社区活动室'
  }
  clearFieldError('location')
}
// 含重大事项开关：线上会议锁定；点击时弹提示说明原因，否则用户不知为何点不了
function onJuweiToggle() {
  if (createForm.meetingMethod === 'online') {
    toast({ title: '线上会议不支持重大事项，须线下提前 7 天公告并请居委会到场见证', icon: 'none' })
    return
  }
  createForm.juweiWitness = !createForm.juweiWitness
}
// 自定义日期选择器（年/月/日 三列）
const datePickerOpen = ref(false)
const dpYear = ref(2026)
const dpMonth = ref(1)
const dpDay = ref(1)
const _nowYear = new Date().getFullYear()
const yearOptions = [_nowYear - 1, _nowYear, _nowYear + 1]
const monthOptions = Array.from({ length: 12 }, (_, i) => i + 1)
const dayOptions = computed(function () {
  const n = new Date(dpYear.value, dpMonth.value, 0).getDate()
  return Array.from({ length: n }, (_, i) => i + 1)
})
// 自定义时间选择器（小时左 / 分钟右）
const timePickerOpen = ref(false)
const tpHour = ref(9)
const tpMinute = ref(0)
const hourOptions = Array.from({ length: 12 }, (_, i) => i + 9) // 业委会会议时段：9点—20点
const minuteOptions = Array.from({ length: 4 }, (_, i) => i * 15)
// 议题编辑弹窗
const topicDialogOpen = ref(false)
const topicEditIdx = ref(-1)
const topicDraft = reactive({ title: '', type: 'discussion', decisionType: 'none', options: [], content: '' })

// 必填校验：红框状态（会议名称/会议议题/会议地点）。点"生成通知"缺失→弹卡片→确认后亮红框；
// 用户点进对应输入框（focus）即清除红框。
const fieldErrors = reactive({ title: false, topics: false, location: false, meetingDate: false, meetingTime: false })
function clearFieldError(k) { if (fieldErrors[k]) fieldErrors[k] = false }
// 议题只要加进去一条（打字/语音/弹窗任一路径），红框就撤掉
watch(() => createForm.topics.length, (n) => { if (n > 0) fieldErrors.topics = false })
// 时间/地点选了值就撤红框（时间靠选择器、地点靠下拉/输入，focus 不一定触发）
watch(() => createForm.meetingDate, (v) => { if (v) fieldErrors.meetingDate = false })
watch(() => createForm.meetingTime, (v) => { if (v) fieldErrors.meetingTime = false })
watch(() => createForm.location, (v) => { if (v && v.trim()) fieldErrors.location = false })

// 会议名称：自增高文本框（空/短=一行，超长自动到两行，max-height 封顶）
const titleEl = ref(null)
function autoGrowTitle() {
  const el = titleEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}
// 标题叉：有内容先清内容（推荐字重新浮现）；空但有推荐字（幽灵态）→ 把推荐字也清掉
function clearTitleOrGhost() {
  if (createForm.title) createForm.title = ''
  else suggestedTitle.value = ''
}
watch(() => createForm.title, () => nextTick(autoGrowTitle))
// 语音输入状态
const voiceTarget = ref('') // 'title' | 'topic' | 'time' | 'location' | ''
const voiceInterim = ref('') // 实时识别中（未定稿）
const voiceFinal = ref('')   // 已定稿文字
let _voiceRec = null

function show() {
  const role = getStorage('activeRole', null)
  if (!role) { redirectTo('/pages/login/login'); return }
  activeRole.value = role
  // 回首页落回来时的 tab（0717 用户定：从学习/培训/接待页回来要回到对应 tab，不是全切回开会）。
  // 优先 ?tab= 显式指定，其次会话内最后停留的 tab（sessionStorage：微信杀会话即清，
  // 新打开仍默认开会——开会是核心价值，冷启动不动它）。
  planTab.value = props.section === 'reception' ? 'reception' : 'meeting'
  // 0730 用户定（推翻 0725 的"返回保持展开并滚到日历"）：从详情等页返回首页一律回到顶部，
  // 全年会议一览默认收起——落到页面底部的日历属于导航错误
  meetingCalendarOpen.value = false
  nextExpanded.value = false   // 「接下来」回到默认截断态（只显 2 场）
  homeShell.navHidden = false   // 回首页恢复导航栏
  // （后续计划已平铺，无折叠态可恢复——0730 图一骨架）
  try { window.scrollTo(0, 0) } catch (e) { /* 忽略 */ }
  isChair.value = perm.isChair()
  isRecorder.value = perm.isRecorder()
  isExternal.value = perm.isExternal()
  canCreate.value = perm.can('committee.create')
  canViewInternal.value = perm.can('view.internal')
  canManageReception.value = perm.can('reception.manage')
  setupRoleView()
  loadUnread()
  loadAll()
  loadCalExtras() // 履职年历：接待数据（月格角标与当月清单用）
  loadCockpitLearningTasks()
  loadPortalTodos()   // 驾驶舱「待办事项 · N 项」计数（会议聚合待办）
  loadDraft()
  // 从「会议通知」页左箭头返回：以编辑模式打开该会议（一次性交接，用完即清）
  const _editId = getStorage('editMeetingId', null)
  if (_editId) { removeStorage('editMeetingId'); openMeetingForEdit(Number(_editId)) }
}

function setupRoleView() {
  if (perm.isChair()) {
    roleView.value = { title: '主任/副主任工作台', intro: '管理会议全流程：新建、推进阶段、核查合规性、公示归档。' }
  } else if (perm.isRecorder()) {
    roleView.value = { title: '委员工作台', intro: '按流程缺口优先展示，补齐送达、代录确认参会和佐证。' }
  } else if (perm.isExternal()) {
    roleView.value = { title: '公开信息', intro: '内部会议流程仅业委会成员可见，可查看已公示的会议纪要与决定。' }
  } else {
    roleView.value = { title: '我的会议工作台', intro: '按你的待办与关注事项优先展示；会议新建、开始、结束由主任/副主任操作。' }
  }
}

async function loadAll() {
  try {
    const [listRes, statsRes] = await Promise.all([
      api.committeeList(null),
      api.committeeStats()
    ])
    const all = Array.isArray(listRes) ? listRes : []
    allMeetings.value = all   // 供「本期会议」按本月真实统计（完整结束的会议计入已完成）
    const st = statsRes || {}
    const now = new Date()
    const bimonth = Math.ceil((now.getMonth() + 1) / 2)
    st.periodLabel = ((bimonth - 1) * 2 + 1) + '-' + (bimonth * 2) + '月'
    st.annualYear = now.getFullYear()
    stats.value = st

    // 当前会议卡片：进行中/准备中；主任另含"已结束未公示"（待整理纪要）
    const chair = isChair.value || isRecorder.value
    let actives = all.filter((m) => m.stage === 'preparing' || m.stage === 'ongoing')
    if (chair) {
      // 【测试阶段】历史演示例会（第1、2次）：只进「历史记录」，不在首页当前会议卡片显示。
      // 已结束会议一律保留在首页卡片（含已公示、含"会议无效"），手动删除前不消失；仅历史例会按标题挡掉。
      // ⚠ 反复报的 bug 根因：原来这里有 && m.compliance !== 'invalid'，会把"无效"会议挡掉——
      //   演示里会议多因签到不过半判 invalid，一公示/结束回首页卡片就消失。测试期去掉该条，无效会议也保留。
      // 上线前复原：删掉 HISTORY_ONLY_TITLES 排除；按产品要求再决定是否加回 && m.compliance !== 'invalid'
      //   与 && (!m.publish || !m.publish.published)。
      const HISTORY_ONLY_TITLES = ['2026年第1次业委会例会', '2026年第2次业委会例会', '2026年第3次业委会例会']
      const pend = all.filter((m) => m.stage === 'ended'
        && !HISTORY_ONLY_TITLES.includes(m.title))
      actives = [...actives, ...pend]
    }
    currents.value = actives.map((m) => decorateCurrent(m, chair))
    refreshMinutesGenStates() // 异步补查：纪要任务生成中 → 卡片按钮切「继续生成会议纪要」
  } catch (e) {
    stats.value = {}
    currents.value = []
  }
}

async function loadUnread() {
  try {
    const res = await api.notificationList()
    unread.value = (res && res.unread) || 0
  } catch (e) { /* 离线可忽略 */ }
}

// 给当前会议附加：三步进度、主操作按钮文案/图标
// 会议进行页本地快照里「现场会议已结束」的标记（与 MeetingLiveQuick 的持久化键一致，按角色隔离）
function _quickLocalFlags(meetingId) {
  try {
    const role = getStorage('activeRole', null)
    const saved = getStorage('committee_quick_meeting_state_' + meetingId + '_r' + ((role && role.id) || 0), null)
    return { fieldEnded: !!(saved && saved.fieldMeetingEnded), reviewDone: !!(saved && saved.reviewCompleted) }
  } catch (e) { return { fieldEnded: false, reviewDone: false } }
}

// 会议日期距今天数（用真实今天算倒计时，供会前公告提示）
function daysUntilMeeting(dateStr) {
  if (!dateStr) return null
  const p = String(dateStr).split('-')
  if (p.length < 3) return null
  const target = new Date(+p[0], +p[1] - 1, +p[2])
  if (isNaN(target.getTime())) return null
  const now = new Date(); now.setHours(0, 0, 0, 0)
  return Math.round((target - now) / 86400000)
}

function decorateCurrent(m, chair) {
  // 测试期会议保持 ongoing，现场结束/整理完成 两个节点靠本地快照标记推进卡片形态
  const local = (m.stage === 'ongoing' && chair) ? _quickLocalFlags(m.id) : { fieldEnded: false, reviewDone: false }
  // 纪要已生成的已结束会议 → 三步全部完成(step=4)；整理已完成 → 会后总结(3)；否则按阶段
  const step = (m.stage === 'ended' && m.minutesGenerated) ? 4 : (local.reviewDone ? 3 : (STEP_BY_STAGE[m.stage] || 1))
  const steps = [1, 2, 3].map((no) => ({
    no: no,
    label: STEP_LABELS[no],
    state: no < step ? 'done' : (no === step ? 'active' : 'todo')
  }))
  let ctaLabel, ctaIcon, tag
  let minutesGen = false
  let preNoticeTip = ''
  if (m.stage === 'preparing') {
    if (chair) {
      // 准备阶段(会议通知阶段)：卡片按钮统一「继续通知」——点回会议通知页继续发送/开始会议
      ctaLabel = '继续通知'
      ctaIcon = '📣'
      // 会前公告提示（0723，《指导规则》第39条：业委会会议应提前7天向业主公告会议）——告知性公告，非征求意见
      const d = daysUntilMeeting(m.meetingDate)
      if (d != null) {
        preNoticeTip = d > 7 ? '记得提前 7 天向全体业主公告会议时间和议程'
          : d >= 0 ? ('距会议召开 ' + d + ' 天，请尽快向业主公告会议')
            : ''
      }
    } else {
      ctaLabel = '查看会议通知'
      ctaIcon = '📋'
    }
    tag = '会议通知'
  } else if (m.stage === 'ongoing') {
    // 现场会议已结束但未归档（测试期不真正结束，靠本地快照标记）：
    // 整理已完成 → 入口「会议详情」直进详情页；仅现场结束 → 「会后整理」落整理页
    if (local.reviewDone) {
      ctaLabel = '会议详情'
      ctaIcon = ''
      tag = '会后总结'
    } else {
      // 0728：ongoing 只能由主任点「开始会议」产生（含提前开会）。原先再按计划时间否定一次，
      // 导致提前开的会全员显示「待召开」、点卡片也进不了现场——已开始的会一律按进行中处理。
      ctaLabel = local.fieldEnded ? '会后整理' : (chair ? '进入会议' : '查看会议')
      ctaIcon = local.fieldEnded ? '📝' : (chair ? '🎙️' : '')
      tag = '正在开的会'
    }
  } else {
    // ended：纪要生成中 → 继续生成会议纪要(点回纪要页)；已生成 → 查看会议；未生成 → 整理会议记录
    minutesGen = !m.minutesGenerated && aiTask.active && !!aiTask.targetPath && aiTask.targetPath.indexOf('meetingId=' + m.id) >= 0
    if (minutesGen) {
      ctaLabel = '继续生成会议纪要'
      ctaIcon = '🤖'
    } else {
      ctaLabel = m.minutesGenerated ? '查看会议' : '整理会议记录'
      ctaIcon = m.minutesGenerated ? '' : '📝'
    }
    tag = '会后总结'
  }
  return {
    id: m.id, title: m.title, meetingDate: m.meetingDate, meetingTime: (m.meetingTime || '').slice(0, 5),
    location: m.location, timeText: formatMeetingTime(m), locationText: m.location || '地点待定',
    step: step, steps: steps, stage: m.stage, stageText: MEETING_STAGE_TEXT[m.stage] || '未开始',
    ctaLabel: ctaLabel, ctaIcon: ctaIcon, tag: tag, minutesGen: minutesGen, reviewDone: local.reviewDone,
    fieldEnded: local.fieldEnded,   // 现场/线上已结束、待会后整理：卡片状态词据此显示「会后整理」
    preNoticeTip: preNoticeTip
  }
}

// 已结束未生成纪要的会议：问服务端纪要任务状态（跨刷新/换设备可靠），生成中 → 卡片按钮切「继续生成会议纪要」。
// 用户预期：生成完成前卡片都保持"回纪要页"的入口，完成后才变回"查看会议"。
async function refreshMinutesGenStates() {
  for (const c of (currents.value || [])) {
    if (c.stage !== 'ended' || c.minutesGen || c.ctaLabel !== '整理会议记录') continue
    try {
      const st = await api.committeeMinutesStatus(c.id)
      if (st && st.status === 'running') {
        c.minutesGen = true
        c.ctaLabel = '继续生成会议纪要'
        c.ctaIcon = '🤖'
      }
    } catch (e) { /* 旧后端无此接口/离线：保持默认入口 */ }
  }
}

function formatMeetingTime(m) {
  const d = m && m.meetingDate ? fmtPlanDate(m.meetingDate) : '时间待定'
  const t = m && m.meetingTime ? String(m.meetingTime).slice(0, 5) : ''
  return t ? d + ' ' + t : d
}
// 有进行中会议时，首页「去通知」按钮改为「新会议」（此时再发通知即另起一场）
const hasOngoingMeeting = computed(() => currents.value.some((c) => c.stage === 'ongoing'))

async function goCurrent(cur) {
  // 准备阶段：按"上次停留位置"重进——上次在发起/编辑页就回发起页（与点卡片本身 openMeetingTap 一致）
  if (cur.stage === 'preparing' && getStorage('meetingView:' + cur.id, '') === 'edit') {
    openMeetingForEdit(cur.id)
    return
  }
  // 纪要生成中：点卡片回纪要页看进度/结果，而不是会议详情页（带硬导航兜底）
  if (cur.minutesGen) {
    const target = '/pages/minutes-view/minutes-view?meetingId=' + cur.id + '&from=committee'
    const browserUrl = '/minutes-view?meetingId=' + cur.id + '&from=committee'
    try { await navigateTo(target) } catch (navErr) { console.error('[继续生成纪要] 软跳 reject：', navErr) }
    setTimeout(() => { if (!document.querySelector('.mv-page')) window.location.href = browserUrl }, 500)
    return
  }
  try {
    await api.committeeDetail(cur.id)
  } catch (e) {
    toast({ title: '会议已更新，正在刷新', icon: 'none' })
    await loadAll()
    return
  }
  // 进行中的会议是关键入口：主任和委员统一整页进入签到/会议流程。
  // 不再经过详情页、RouterLink 和 DOM 延时兜底，避免路由重复或组件切换竞态导致点击无响应。
  if (cur.stage === 'ongoing') {
    // 会后整理已完成（测试期本地标记）→ 直进会议详情页，不再回会议流程页
    if (cur.reviewDone) {
      window.location.assign('/committee-detail?id=' + encodeURIComponent(cur.id) + '&from=committee')
      return
    }
    window.location.assign('/meeting-live-quick?type=committee&meetingId=' + encodeURIComponent(cur.id))
    return
  }
  const chair = perm.isChair() || perm.isRecorder()
  const target = chair
    ? '/pages/committee-detail/committee-detail?id=' + cur.id
    : '/pages/my-meeting/my-meeting?id=' + cur.id
  // 硬导航兜底：navigateTo(router.push) 偶发"URL变了却不切换视图"，把用户留在主页（如"继续通知/去开会"回不到通知页）。
  // 软跳后延时校验目标页根节点是否真的挂上（详情页 .detail-page / 我的会议 .mm），没挂上就 window.location 硬跳，确保必达。
  const browserUrl = chair ? '/committee-detail?id=' + cur.id : '/my-meeting?id=' + cur.id
  const rootSel = chair ? '.detail-page' : '.mm'
  try { await navigateTo(target) } catch (navErr) { console.error('[去开会] 软跳 reject：', navErr) }
  setTimeout(() => {
    if (!document.querySelector(rootSel)) {
      console.warn('[去开会] 软跳未挂载目标页，硬导航兜底 →', browserUrl)
      window.location.href = browserUrl
    }
  }, 500)
}

async function removeCurrent(cur) {
  const isRecordingThisMeeting = meetingRecordingSession.active
    && String(meetingRecordingSession.meetingId || '') === String(cur.id)
  const res = await showModal({
    title: '删除会议',
    content: '确定删除「' + cur.title + '」？'
      + (isRecordingThisMeeting ? '\n\n该会议正在录音，删除后录音将立即停止并丢弃。' : '')
      + '\n\n删除后无法恢复。',
    confirmText: '删除',
    confirmColor: '#E74C3C'
  })
  if (!res.confirm) return
  try {
    await api.committeeRemove(cur.id)
    await discardMeetingRecording(cur.id)
    toast({ title: '已删除', icon: 'success' })
    await loadAll()
  } catch (e) {
    toast({ title: (e && e.message) || '删除失败', icon: 'none' })
  }
}

function goNotifications() { navigateTo('/pages/notifications/notifications') }
// goReception 已删（0716）：接待列表页随重做下线，点条目现在直接进 goReceptionDetail。
// 它原有的两个调用方 calAlert / calList 都是模板不引用的死代码。
function goLibrary() { navigateTo('/pages/library/library') }

// 点计划某一期：已开→看这场会议；未开的（本期/逾期/未到）→主任可发起，未到期提示「提前召开」，委员提示等待
async function onPlanRow(row) {
  if (row.status === 'done' && row.meeting) { openMeetingTap(row.meeting); return }
  // 本期/逾期：点「去通知/去补开」直接进入发起会议(通知)流程，无需二次确认
  if (row.status === 'current' || row.status === 'overdue') {
    if (isChair.value) { openNewMeeting(row.period); return }
    const tip = row.status === 'overdue'
      ? row.monthLabel + '这期还没召开例会，已逾期。'
      : '本期（' + row.monthLabel + '）还没召开例会。'
    await showModal({ title: row.monthLabel + '例会', content: tip + '请等待主任发起。', showCancel: false, confirmText: '知道了' })
    return
  }
  // 未到期（提前召开）仍二次确认，避免误触提前起会
  const tip = row.monthLabel + '例会按计划还没到时间。'
  if (isChair.value) {
    const res = await showModal({ title: row.monthLabel + '例会', content: tip + '是否提前召开？', confirmText: '提前召开', cancelText: '暂不', showCancel: true })
    if (res && res.confirm) openNewMeeting(row.period)
  } else {
    await showModal({ title: row.monthLabel + '例会', content: tip + '请等待主任发起。', showCancel: false, confirmText: '知道了' })
  }
}

function onSearch(e) { keyword.value = e.target.value; loadAll() }
function clearSearch() { keyword.value = ''; loadAll() }

function switchStage(stage) {
  currentStage.value = stage
  keyword.value = ''
  loadAll()
}

async function openDetail(id) {
  try {
    await api.committeeDetail(id)
  } catch (e) {
    toast({ title: '会议已更新，正在刷新', icon: 'none' })
    await loadAll()
    return
  }
  // from=committee:标记从业委会首页进入,详情页返回时确定性回首页(不依赖历史栈,避免真机残留历史窜接待)
  // 硬导航兜底：软路由偶发"URL变了却不切换视图"，500ms 后目标页未挂载则 location 硬跳（对齐其它关键跳转做法）
  const browserUrl = '/committee-detail?id=' + id + '&from=committee'
  try { await navigateTo('/pages/committee-detail/committee-detail?id=' + id + '&from=committee') } catch (navErr) { console.error('[进详情] 软跳 reject：', navErr) }
  setTimeout(() => { if (!document.querySelector('.detail-page')) window.location.href = browserUrl }, 500)
}

function openMeetingTap(item) {
  // 无效会议原来直接跳纪要页,没纪要时是一张空页(0725 用户反馈)。改为统一进详情页「会议结果与公示」——
  // 详情页对无效/无纪要都有完整布局(议题结果、会议记录、公示状态等),不再出现"点进去什么都没有"。
  // 准备阶段：按"上次停留位置"重进——上次在发起/编辑页就回发起页，否则进会议通知页
  if (item.stage === 'preparing' && getStorage('meetingView:' + item.id, '') === 'edit') {
    openMeetingForEdit(item.id)
    return
  }
  openDetail(item.id)
}


async function openNewMeeting(period) {
  createVisible.value = true
  createTab.value = 'manual'      // 每次进来默认手动填写面板
  editingMeetingId.value = null   // 全新会议：非编辑模式
  docPrefilled.value = false
  materialPrefillOpen.value = false
  materialText.value = ''
  materialFiles.value = []
  materialScanResult.value = null
  topicDialogOpen.value = false
  timePickerOpen.value = false
  datePickerOpen.value = false
  createForm.title = ''
  createForm.meetingDate = ''
  createForm.meetingTime = ''
  createForm.location = ''
  createForm.locationLat = null
  createForm.locationLng = null
  createForm.meetingMethod = 'offline'
  locationPreset.value = ''
  createForm.description = ''
  createForm.topics = []
  createForm.juweiWitness = false
  // 快照默认占位值：日期/时间/地点等于这些默认时视为"未填"，不参与冲突判定，可被识别值直接填入
  createInitialDefaults.value = { title: '', meetingDate: createForm.meetingDate, meetingTime: createForm.meetingTime, location: createForm.location }
  suggestedTitle.value = ''
  prefilledCreateTitle.value = ''
  pendingMaterials.value = []
  scanBusy.value = ''
  lastScanTokens.value = 0
  // 从待办/例会计划某期「去通知/去补开」进来时：直接把「第N次例会」填入名称框
  // （不是 ghost 推荐——这是排定的例会，名称已确定；用户仍可点×清空改名）
  if (period >= 1) {
    createForm.title = curYear + '年第' + period + '次业委会例会'
    prefilledCreateTitle.value = createForm.title
    nextTick(autoGrowTitle)
    return
  }
  // 「发起其他会议」入口（无 period）：这是非例会（专题/临时/联席等），不推荐「第N次业委会例会」——
  // 那是例会专用名，放这里会误导；名称留空，只显示占位符「请输入会议名称」由用户手填。
  suggestedTitle.value = ''
}

function closeCreate() {
  // 编辑已建会议时不存草稿（它是正式会议不是草稿）；新建会议才把半成品存草稿
  if (editingMeetingId.value) editingMeetingId.value = null
  else persistDraft()
  createVisible.value = false
  topicDialogOpen.value = false
  timePickerOpen.value = false
  datePickerOpen.value = false
  clearScanItems()
  scanResultCard.value = null
}

// ——— 会议草稿：发起会议填了一半返回首页时自动保存，首页出「待发送·草稿」卡片可继续 ———
// 只存可序列化内容（表单字段 + 已识别落库的材料）；未识别的暂存照片(scanItems 含 File)不入草稿。
const DRAFT_KEY = 'committee_meeting_draft'
const draft = ref(null)            // 当前草稿快照（供首页卡片展示）；null=无草稿

// 快照当前发起会议表单为可序列化对象
function snapshotDraft() {
  return {
    title: createForm.title || '',
    meetingDate: createForm.meetingDate || '',
    meetingTime: createForm.meetingTime || '',
    location: createForm.location || '',
    meetingMethod: createForm.meetingMethod || 'offline',   // 0725 修:草稿不存召开方式,线上会议草稿续编时会退回线下
    description: createForm.description || '',
    juweiWitness: !!createForm.juweiWitness,
    topics: JSON.parse(JSON.stringify(createForm.topics || [])),
    locationPreset: locationPreset.value || '',
    pendingMaterials: JSON.parse(JSON.stringify(pendingMaterials.value || [])),
    materialFiles: JSON.parse(JSON.stringify(materialFiles.value || [])),
    savedAt: Date.now()
  }
}
// 值不值得存草稿：填了会议名 / 加了议题 / 有材料才算（日期/时间/地点是开窗默认值，不算）
function draftHasContent(d) {
  if (!d) return false
  return !!(String(d.title || '').trim()
    || (d.topics && d.topics.length)
    || (d.pendingMaterials && d.pendingMaterials.length)
    || (d.materialFiles && d.materialFiles.length))
}
// 有实质内容才落盘并提示；空表单直接返回不动已有草稿
function persistDraft() {
  const snap = snapshotDraft()
  if (!draftHasContent(snap)) return
  // 标题还是进来时自动预填的「第N次例会」原样、且没加议题/材料 → 只是看了一眼，不算草稿
  const titleUntouched = prefilledCreateTitle.value
    && String(snap.title || '').trim() === prefilledCreateTitle.value
  const nothingElse = !(snap.topics && snap.topics.length)
    && !(snap.pendingMaterials && snap.pendingMaterials.length)
    && !(snap.materialFiles && snap.materialFiles.length)
  if (titleUntouched && nothingElse) return
  setStorage(DRAFT_KEY, snap)
  draft.value = snap
  toast({ title: '已保存草稿，可在首页继续', icon: 'none' })
}
function loadDraft() {
  const d = getStorage(DRAFT_KEY, null)
  draft.value = draftHasContent(d) ? d : null
}
function clearDraft() {
  removeStorage(DRAFT_KEY)
  draft.value = null
}
const hasDraft = computed(() => draftHasContent(draft.value))
// 草稿卡片摘要：会议名（缺省占位）
const draftTitle = computed(() => (draft.value && String(draft.value.title || '').trim()) || '未命名会议')
// 草稿卡片摘要：日期 时间 · 地点（有啥显啥）
const draftSummary = computed(() => {
  const d = draft.value
  if (!d) return ''
  const dt = [d.meetingDate, d.meetingTime].filter(Boolean).join(' ')
  return [dt, d.location].filter(Boolean).join(' · ')
})
const draftMaterialCount = computed(() => {
  const d = draft.value
  if (!d) return 0
  return (d.pendingMaterials ? d.pendingMaterials.length : 0) + (d.materialFiles ? d.materialFiles.length : 0)
})

// 「继续通知」：把草稿还原进表单并打开发起会议面板（不重置）
async function continueDraft() {
  const d = draft.value
  if (!d) { openNewMeeting(); return }
  createVisible.value = true
  docPrefilled.value = false
  materialPrefillOpen.value = false
  materialText.value = ''
  materialScanResult.value = null
  topicDialogOpen.value = false
  timePickerOpen.value = false
  datePickerOpen.value = false
  createForm.title = d.title || ''
  createForm.meetingDate = d.meetingDate || ''
  createForm.meetingTime = d.meetingTime || ''
  createForm.location = d.location || ''
  createForm.meetingMethod = d.meetingMethod || 'offline'   // 0725 修:草稿还原时带回召开方式
  createForm.description = d.description || ''
  createForm.topics = JSON.parse(JSON.stringify(d.topics || []))
  createForm.juweiWitness = !!d.juweiWitness
  locationPreset.value = d.locationPreset || (d.location ? '__other__' : '社区活动室')
  syncLocationPreset(createForm.location)
  pendingMaterials.value = JSON.parse(JSON.stringify(d.pendingMaterials || []))
  materialFiles.value = JSON.parse(JSON.stringify(d.materialFiles || []))
  createInitialDefaults.value = { title: '', meetingDate: createForm.meetingDate, meetingTime: createForm.meetingTime, location: createForm.location }
  prefilledCreateTitle.value = ''   // 草稿里的标题是用户已确认的内容，返回时照常续存
  scanBusy.value = ''
  lastScanTokens.value = 0
  // 草稿是「发起其他会议」的半成品，非例会：不再推荐「第N次业委会例会」。
  // 草稿已填标题的照常带回（上面已还原 createForm.title），未填则留空手填。
  suggestedTitle.value = ''
}

// 「放弃草稿」：确认后清除
async function discardDraft() {
  const r = await showModal({
    title: '放弃这份草稿？',
    content: '放弃后，' + draftTitle.value + ' 已填写的内容将被清除，且无法找回。',
    confirmText: '放弃草稿',
    cancelText: '再想想',
    emphasizeCancel: true
  })
  if (r && r.confirm) { clearDraft(); toast({ title: '已放弃草稿', icon: 'none' }) }
}

// ——— 编辑已建会议：从「会议通知」页左箭头返回时，用本表单以「编辑模式」打开该会议，保存=更新不新建 ———
const editingMeetingId = ref(null)     // 非空=当前是在编辑已存在的会议（提交走 committeeUpdate）
const editInitialJuwei = ref(false)    // 载入时的居委会见证态，提交时对比决定是否翻转标记
async function openMeetingForEdit(id) {
  try {
    const d = await api.committeeDetail(id)
    if (!d) { toast({ title: '会议信息加载失败', icon: 'none' }); return }
    createVisible.value = true
    editingMeetingId.value = id
    setStorage('meetingView:' + id, 'edit')  // 记住"上次停在发起/编辑页"，供首页卡片按上次位置重进
    docPrefilled.value = false
    materialPrefillOpen.value = false
    materialText.value = ''; materialFiles.value = []; materialScanResult.value = null
    topicDialogOpen.value = false; timePickerOpen.value = false; datePickerOpen.value = false
    createForm.title = d.title || ''
    createForm.meetingDate = d.meetingDate || ''
    createForm.meetingTime = (d.meetingTime || '').slice(0, 5)
    createForm.location = d.location || ''
    createForm.meetingMethod = d.meetingMethod || 'offline'   // 0725 修:原先编辑线上会议时表单恒显"线下"
    createForm.description = d.description || ''
    createForm.topics = (((d.record && d.record.topics) || d.topics) || []).map((t) => ({
      title: t.title || '',
      type: t.type || 'decision',
      decisionType: t.decisionType || 'none',
      options: Array.isArray(t.options) ? JSON.parse(JSON.stringify(t.options)) : [],
      content: t.content || '',
      realNameVote: !!t.realNameVote
    }))
    createForm.juweiWitness = !!(d.hasMajorIssue || d.juweiWitness)
    editInitialJuwei.value = createForm.juweiWitness
    locationPreset.value = commonLocations.indexOf(createForm.location) >= 0
      ? createForm.location : (createForm.location ? '__other__' : '社区活动室')
    createInitialDefaults.value = { title: '', meetingDate: createForm.meetingDate, meetingTime: createForm.meetingTime, location: createForm.location }
    suggestedTitle.value = ''
    pendingMaterials.value = []; scanBusy.value = ''; lastScanTokens.value = 0
  } catch (e) {
    toast({ title: (e && e.message) || '会议信息加载失败', icon: 'none' })
  }
}

function toggleMaterialPrefill() {
  materialPrefillOpen.value = !materialPrefillOpen.value
}

function onMaterialTextInput() {
  materialScanResult.value = null
}

async function chooseMaterialFile() {
  // 新建会议阶段尚无会议 id，不能直接上传后端。
  // 沿用原"资料预填"语义：仅选文件、记下文件名（供扫描预填用），并提示创建后到详情页上传。
  // 支持一次多选：全部加进材料列表，不必选一个就返回再重选。
  const files = await pickFiles()
  if (!files || !files.length) return // 用户取消
  materialFiles.value = materialFiles.value.concat(files.map((f) => ({
    name: f.name,
    sizeText: humanSize(f.size)
  })))
  materialScanResult.value = null
  toast({ title: '会议创建后可在详情页上传文件', icon: 'none' })
}

function removeMaterialFile(idx) {
  materialFiles.value = materialFiles.value.filter((_, i) => i !== idx)
  materialScanResult.value = null
}

function clearMaterialPrefill() {
  materialText.value = ''
  materialFiles.value = []
  materialScanResult.value = null
}

function scanMaterialPrefill() {
  const fileText = materialFiles.value.map(file => file.name).join('\n')
  const sourceText = [materialText.value, fileText].filter(Boolean).join('\n')
  if (!sourceText.trim()) {
    toast({ title: '请先粘贴资料正文或上传文件', icon: 'none' })
    return
  }

  const result = parseMeetingText(sourceText, { kind: 'committee' })
  materialScanResult.value = result
  Object.keys(result.fields).forEach((key) => {
    if (Object.prototype.hasOwnProperty.call(createForm, key)) {
      createForm[key] = result.fields[key]
    }
  })
  syncLocationPreset(createForm.location)
  toast({ title: result.matched.length ? '已预填，请确认' : '未识别到字段', icon: 'none' })
}

// —— 拍照/上传文档 → 后端 OCR + 大模型识别 → 回填表单（OCR/AI 未开或失败则提示并回退手填） ——
const docPrefilled = ref(false)   // 已成功预填过一次（保留状态位，供后续提示用）
const createInitialDefaults = ref({})  // 表单打开时的默认占位值快照（日期/时间/地点）
// 字段是否为"用户真正填过"（手动改或识别填过）——等于初始默认占位则视为未填
function isFieldUserSet(key) {
  const v = (createForm[key] == null ? '' : String(createForm[key])).trim()
  const def = (createInitialDefaults.value && createInitialDefaults.value[key]) || ''
  return !!v && v !== def
}

// ——— 待挂载的会议材料（供委员传阅：如上级文件精神、报价单等）———
// 「去通知」建会成功后再逐份挂到会议（届时触发 OCR）。来源：拍照/上传 AI 判类为 material 的文件。
const pendingMaterials = ref([])      // [{ url, fileName, fileType, fileSize, sizeText }]
const materialsOpen = ref(false)      // 会议材料：默认折叠只显示份数，点标题右侧图标展开看详情
const createTab = ref('manual')       // 发起会议顶部分段：manual=手动填写 / scan=拍照上传；识别完成自动切回 manual 看预填结果
function removePendingMaterial(idx) {
  pendingMaterials.value = pendingMaterials.value.filter((_, i) => i !== idx)
}
// 材料行小图标：按文件类型区分（图片/PDF/其他）
function matExt(m) {
  let t = String((m && m.fileType) || '').toLowerCase()
  if (!t && m && m.fileName && m.fileName.indexOf('.') >= 0) t = m.fileName.split('.').pop().toLowerCase()
  return t
}
// 微信式文件卡：按类型给短标签 + 配色
function matBadge(m) {
  const t = matExt(m)
  if (t === 'pdf') return { label: 'PDF', cls: 'pdf' }
  if (['doc', 'docx'].includes(t)) return { label: 'DOC', cls: 'doc' }
  if (['xls', 'xlsx', 'csv'].includes(t)) return { label: 'XLS', cls: 'xls' }
  if (['ppt', 'pptx'].includes(t)) return { label: 'PPT', cls: 'ppt' }
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(t)) return { label: 'IMG', cls: 'img' }
  if (t === 'txt') return { label: 'TXT', cls: 'txt' }
  return { label: t ? t.slice(0, 3).toUpperCase() : '文件', cls: 'file' }
}

// ——— 拍照/上传 → 攒进暂存列表（缩略图预览）→ 一起「开始 AI 识别」→ 自动分辨通知/材料 ———
const scanBusy = ref('')          // '' | 'file'：识别中（复用识别弹窗），空=空闲
const scanRecognizing = ref(false) // 正在多文件识别（用于禁用卡片/按钮）
const lastScanTokens = ref(0)     // 上次识别消耗的 token（低调显示在卡片下方）

// 暂存待识别的照片/文件：[{ id, file, name, ext, isImage, thumbUrl }]
const scanItems = ref([])
let _scanId = 0
const IMG_EXT = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp']
function addScanItem(file, thumbUrl) {
  if (!file) return
  const ext = String((file.name || '').split('.').pop() || '').toLowerCase()
  const isImage = IMG_EXT.includes(ext) || String(file.type || '').startsWith('image/')
  scanItems.value = scanItems.value.concat([{
    id: ++_scanId, file, name: file.name || '文件', ext, isImage,
    thumbUrl: thumbUrl || (isImage ? URL.createObjectURL(file) : '')
  }])
}
function removeScanItem(id) {
  const it = scanItems.value.find((x) => x.id === id)
  if (it && it.thumbUrl && it.thumbUrl.indexOf('blob:') === 0) { try { URL.revokeObjectURL(it.thumbUrl) } catch (e) {} }
  scanItems.value = scanItems.value.filter((x) => x.id !== id)
}
function clearScanItems() {
  scanItems.value.forEach((it) => { if (it.thumbUrl && it.thumbUrl.indexOf('blob:') === 0) { try { URL.revokeObjectURL(it.thumbUrl) } catch (e) {} } })
  scanItems.value = []
}
function scanThumbIcon(ext) { return ext === 'pdf' ? '📄' : '📎' }
// 相机取景界面左下角「相册」缩略图：取最近一张有缩略图的照片（像系统相机连拍）
const lastShotThumb = computed(() => {
  const arr = scanItems.value
  for (let i = arr.length - 1; i >= 0; i--) { if (arr[i].isImage && arr[i].thumbUrl) return arr[i].thumbUrl }
  return ''
})
// 点击暂存缩略图 → 复用全屏材料查看器放大看清（图片可再放大，PDF 也能预览）
function openScanItemPreview(it) {
  if (!it) return
  const url = it.thumbUrl || (it.file ? URL.createObjectURL(it.file) : '')
  if (!url) return
  openMaterialViewer({ url, name: it.name, fileType: (it.file && it.file.type) || it.ext || '' })
}

// 上传：source='image' 选图片(可多张) / 'file' 选文档(PDF/Word 等)。拆两个入口——
// 手机微信对"图片+文档混选"会退化成单选，纯 image/* 时一次多选更容易生效；选中的都攒进暂存列表(不立即识别)。
async function chooseImageSource() {
  const res = await showActionSheet({
    title: '添加图片',
    itemList: ['相机拍摄', '从相册选择']
  })
  if (!res || res.tapIndex == null || res.tapIndex < 0) return
  if (res.tapIndex === 0) await startCamera()
  else if (res.tapIndex === 1) await startDocScan('image')
}

async function startDocScan(source = 'image') {
  if (scanRecognizing.value) return
  // 企业微信里「图片」走 JS-SDK 相册多选（内置浏览器把 <input multiple> 强制单选）；
  // 用户取消就停手，配置/接口等异常则回退系统选择器，绝不打断。
  if (source === 'image' && isWecom()) {
    try {
      const files = await chooseWecomImages(9)
      files.forEach((f) => addScanItem(f))
      return
    } catch (e) {
      if (isWecomCancel(e)) return
      console.warn('[wecom] chooseImage 失败，回退系统选择器', e)
    }
  }
  const accept = source === 'file'
    ? '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,application/pdf'
    : 'image/*'
  const files = await pickFiles(accept)
  if (!files || !files.length) return // 用户取消
  files.forEach((f) => addScanItem(f))
}

// 识别中窗口点 × 取消：收起窗口、复位状态；在途请求返回后按标志丢弃，文件保留可重试
let _recognizeCancelled = false
function cancelRecognize() {
  _recognizeCancelled = true
  stopDocProgress()
  clearInterval(_scanSecTimer)
  _scanSecTimer = null
  scanBusy.value = ''
  docProgress.value = 0
  scanRecognizing.value = false
  toast({ title: '已取消识别', icon: 'none' })
}

// 「开始 AI 识别（N）」：把暂存的 N 个文件一起送后端统一识别
async function recognizeScanItems() {
  if (scanRecognizing.value || !scanItems.value.length) return
  const files = scanItems.value.map((x) => x.file)
  _recognizeCancelled = false
  scanRecognizing.value = true
  scanBusy.value = 'file'
  startDocProgress()
  scanSec.value = 0
  clearInterval(_scanSecTimer)
  _scanSecTimer = setInterval(() => { scanSec.value += 1 }, 1000)
  try {
    let res
    try {
      res = await api.committeeParseDocuments(files)
    } catch (multiError) {
      // 一份材料是最常见场景；部分旧 WebView 对 MultipartFile[] 兼容性较差，
      // 多文件入口失败时改走单文件接口重试一次，避免用户重新选择文件。
      if (files.length !== 1) throw multiError
      res = await api.committeeParseDocument(files[0])
      if (res && !Array.isArray(res.files)) {
        res.files = [{
          fileUrl: res.fileUrl || '',
          fileName: res.fileName || files[0].name || '识别文件',
          fileType: res.fileType || String((files[0].name || '').split('.').pop() || '').toLowerCase(),
          fileSize: res.fileSize || files[0].size || 0,
          category: res.category || ''
        }]
      }
    }
    if (_recognizeCancelled) return // 用户已点 × 取消：丢弃结果，保留暂存文件可重试
    stopDocProgress()
    docProgress.value = 100
    await new Promise((r) => setTimeout(r, 350))
    if (res && res.tokens > 0) lastScanTokens.value = res.tokens
    clearInterval(_scanSecTimer)
    _scanSecTimer = null
    scanBusy.value = ''
    docProgress.value = 0
    await handleMultiScanResult(res)
    clearScanItems()
  } catch (e) {
    if (_recognizeCancelled) return
    stopDocProgress()
    const message = e && e.message && !/^HTTP\s/i.test(e.message)
      ? e.message
      : '识别失败，请重试或手动填写'
    toast({ title: message, icon: 'none' })
  } finally {
    clearInterval(_scanSecTimer)
    _scanSecTimer = null
    scanBusy.value = ''
    docProgress.value = 0
    scanRecognizing.value = false
  }
}

// 识别弹窗的阶段与文案：按进度分 上传→OCR提取→AI理解 三步（模拟节奏，后端一次性返回）
const scanSec = ref(0)
let _scanSecTimer = null
const scanStage = computed(() => (docProgress.value < 25 ? 1 : docProgress.value < 70 ? 2 : 3))
const scanSay = computed(() => {
  if (docProgress.value < 25) return '正在安全上传文件…'
  if (docProgress.value < 70) return '正在解析文字内容…'
  return '正在理解内容、判断文件类型…'
})

// ——— 模拟手机相机（测试用）：全屏取景框对着一张"纸质会议通知"，按快门出照片；使用照片=加入暂存，可连拍多张 ———
const mockCameraVisible = ref(false)
const mockCamFlash = ref(false)
const mockViewfinderUrl = ref('')
const mockShotUrl = ref('')       // 拍后照片预览图（非空 = 预览确认模式）
let _mockShotCanvas = null
let _mockShotFile = null          // 预览中待确认的照片文件，点「使用照片」才送识别
const mockSampleIdx = ref(0)      // 当前取景样张下标（左右滑动/箭头切换，模拟拍不同纸质文件）
let _mockSampleCanvases = []      // 本次相机会话预生成的各样张 canvas
function buildSampleNoticeCanvas() {
  const cv = document.createElement('canvas'); cv.width = 750; cv.height = 940
  const ctx = cv.getContext('2d')
  ctx.fillStyle = '#fdfcf8'; ctx.fillRect(0, 0, 750, 940)
  ctx.fillStyle = '#111'; ctx.textAlign = 'center'
  ctx.font = 'bold 46px serif'
  ctx.fillText('会 议 通 知', 375, 96)
  ctx.font = '32px serif'; ctx.textAlign = 'left'
  const lines = [
    '各位业主委员会委员：',
    '',
    '兹定于2026年7月28日上午10:00，',
    '在小区党群服务中心二楼会议室，',
    '召开2026年第8次业委会例会。',
    '',
    '会议议题：',
    '一、审议消防设施维修改造方案；',
    '二、讨论电动车充电桩扩建选址；',
    '三、通报小区公共收益使用明细。',
    '',
    '请各位委员准时出席，不能到会者',
    '请提前向主任请假。',
    '',
    '                    业主委员会',
    '                  2026年7月2日'
  ]
  let y = 180; for (const l of lines) { ctx.fillText(l, 64, y); y += 46 }
  return cv
}
// 通用纸质文件样张：白纸 + 标题 + 正文若干行
function buildDocCanvas(title, lines) {
  const cv = document.createElement('canvas'); cv.width = 750; cv.height = 940
  const ctx = cv.getContext('2d')
  ctx.fillStyle = '#fdfcf8'; ctx.fillRect(0, 0, 750, 940)
  ctx.fillStyle = '#111'; ctx.textAlign = 'center'
  ctx.font = 'bold 44px serif'
  ctx.fillText(title, 375, 96)
  ctx.font = '30px serif'; ctx.textAlign = 'left'
  let y = 180; for (const l of lines) { ctx.fillText(l, 64, y); y += 44 }
  return cv
}
// 多个样张，供左右滑动切换（覆盖 通知/材料 多种情形，方便测试连拍多张）
const MOCK_SAMPLES = [
  { label: '会议通知', build: buildSampleNoticeCanvas },
  { label: '维保报价单', build: () => buildDocCanvas('电梯维保报价单', [
    '致：阳光家园业主委员会',
    '',
    '我司就贵小区电梯维保报价如下：',
    '一、维保范围：4部乘客电梯；',
    '二、维保周期：每月2次例行保养；',
    '三、报价：每部每年8000元；',
    '四、全年合计：32000元；',
    '五、合同期限：一年。',
    '',
    '            宏达电梯维保有限公司',
    '              2026年7月1日'
  ]) },
  { label: '施工方案', build: () => buildDocCanvas('消防设施改造施工方案', [
    '一、项目背景：',
    '   小区消防管网老化，需整体改造。',
    '二、施工内容：',
    '   更换消防主管道及喷淋头；',
    '   增设室内消防栓12处。',
    '三、工期：预计45天；',
    '四、预算：约人民币38万元；',
    '五、资金来源：专项维修资金。'
  ]) }
]
const currentSampleLabel = computed(() => (MOCK_SAMPLES[mockSampleIdx.value] || {}).label || '')
function switchSample(idx) {
  const n = _mockSampleCanvases.length
  if (!n) return
  mockSampleIdx.value = ((idx % n) + n) % n
  _mockShotCanvas = _mockSampleCanvases[mockSampleIdx.value]
  mockViewfinderUrl.value = _mockShotCanvas.toDataURL('image/png')
}
function nextSample() { switchSample(mockSampleIdx.value + 1) }
function prevSample() { switchSample(mockSampleIdx.value - 1) }
let _mcTouchX = 0
function onVfTouchStart(e) { _mcTouchX = (e.changedTouches && e.changedTouches[0] ? e.changedTouches[0].clientX : e.clientX) || 0 }
function onVfTouchEnd(e) {
  const x = (e.changedTouches && e.changedTouches[0] ? e.changedTouches[0].clientX : e.clientX) || 0
  const dx = x - _mcTouchX
  if (Math.abs(dx) < 40) return
  if (dx < 0) nextSample(); else prevSample()
}
function openMockCamera() {
  _mockSampleCanvases = MOCK_SAMPLES.map((s) => s.build())
  mockSampleIdx.value = 0
  _mockShotCanvas = _mockSampleCanvases[0]
  mockViewfinderUrl.value = _mockShotCanvas.toDataURL('image/png')
  mockShotUrl.value = ''
  _mockShotFile = null
  mockCameraVisible.value = true
}
function closeMockCamera() { mockCameraVisible.value = false; mockShotUrl.value = ''; _mockShotFile = null }
// 模拟真实相机成片：按取景框比例出竖幅照片（纸张摆在深色桌面上），预览时能铺满屏幕
function buildShotPhotoCanvas(paperCanvas) {
  const vp = document.querySelector('.mc-viewport')
  const ratio = Math.min(2.4, Math.max(1.2, vp ? vp.clientHeight / vp.clientWidth : 16 / 9))
  const W = 750, H = Math.round(W * ratio)
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H
  const ctx = cv.getContext('2d')
  const g = ctx.createLinearGradient(0, 0, 0, H)
  g.addColorStop(0, '#4A4640'); g.addColorStop(1, '#38352F')
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H)
  const pw = Math.round(W * 0.9), ph = Math.round(pw * paperCanvas.height / paperCanvas.width)
  ctx.shadowColor = 'rgba(0,0,0,0.45)'; ctx.shadowBlur = 26; ctx.shadowOffsetY = 10
  ctx.drawImage(paperCanvas, (W - pw) / 2, (H - ph) / 2, pw, ph)
  return cv
}
// 按快门：只拍照进预览确认（像系统相机），不直接识别
async function mockShoot() {
  if (!_mockShotCanvas) return
  mockCamFlash.value = true
  setTimeout(() => { mockCamFlash.value = false }, 180)
  const shot = buildShotPhotoCanvas(_mockShotCanvas)
  const blob = await new Promise((r) => shot.toBlob(r, 'image/png'))
  // demo 判类只看文件名（含「通知」→通知，否则材料）：把当前样张类别写进文件名，
  // 否则统一叫「拍照-N.png」会被判成材料。「会议通知」样张 →「会议通知-N.png」→ 正确判为通知。
  const _lbl = currentSampleLabel.value || '拍照'
  _mockShotFile = new File([blob], _lbl + '-' + (scanItems.value.length + 1) + '.png', { type: 'image/png' })
  await new Promise((r) => setTimeout(r, 260)) // 让"咔嚓"闪一下再切预览
  mockShotUrl.value = shot.toDataURL('image/png')
}
function mockRetake() { mockShotUrl.value = ''; _mockShotFile = null }
// 「使用照片」：加入暂存列表，并回到取景，可继续连拍多张（点右上角完成/× 退出）
function mockUsePhoto() {
  const file = _mockShotFile
  if (!file) return
  addScanItem(file, mockShotUrl.value) // 用拍照 dataURL 作缩略图
  mockShotUrl.value = ''
  _mockShotFile = null
  // 确定后自动切到下一张样张，连拍更顺手（仍可左右滑动手动切换）
  nextSample()
}

// ——— 真·相机（getUserMedia 实时取景 + 抓帧成图）：拍照按钮授权后进此界面，抓帧汇入识别队列 ———
// 需安全上下文（HTTPS 或 localhost）；LAN IP + http 会被浏览器拦截 getUserMedia，此时回退模拟界面。
const realCamVisible = ref(false)
const realCamFlash = ref(false)
const realShotUrl = ref('')       // 拍后预览 dataURL（非空 = 预览确认模式）
const realVideoEl = ref(null)     // <video> 模板引用
let _realStream = null
let _realShotFile = null

function stopRealStream() {
  if (_realStream) { try { _realStream.getTracks().forEach((t) => t.stop()) } catch (e) {} }
  _realStream = null
  const v = realVideoEl.value
  if (v) { try { v.srcObject = null } catch (e) {} }
}
// 把当前视频流接到 <video> 并播放。取景/预览切换时 <video> 会重新挂载，需重新绑流，否则回到取景是黑屏。
async function bindStreamToVideo() {
  if (!_realStream) return
  await nextTick()
  const v = realVideoEl.value
  if (!v) return
  try { v.srcObject = _realStream; await v.play().catch(() => {}) } catch (e) {}
}
// 统一「拍照」入口：先申请真实相机权限（浏览器弹系统授权框）——
// 授权 → 进实时取景；拒绝 / 无摄像头 / 非安全上下文(局域网 http) → 转入模拟样张界面展示。
// 注意：权限框只在“能调用相机”时才会弹；非安全上下文或没有摄像头时浏览器根本不弹，
// 会直接落到模拟——这属正常，给个 toast 说明原因，避免误以为没生效。
async function startCamera() {
  if (scanRecognizing.value) return
  const hasApi = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
  if (!hasApi || !window.isSecureContext) {
    toast({ title: '当前环境无法调用相机（需 HTTPS 或 localhost），已进入模拟拍摄', icon: 'none' })
    openMockCamera()
    return
  }
  try {
    _realStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
      audio: false
    })
    realShotUrl.value = ''
    _realShotFile = null
    realCamVisible.value = true
    await bindStreamToVideo()
  } catch (e) {
    // 权限被拒绝 / 无摄像头 / 被占用 → 转入模拟界面。多数情况浏览器不弹权限，故给提示说明。
    stopRealStream()
    const name = (e && e.name) || ''
    if (name === 'NotFoundError' || name === 'DevicesNotFoundError' || name === 'OverconstrainedError') {
      toast({ title: '未检测到摄像头，已进入模拟拍摄', icon: 'none' })
    } else if (name === 'NotReadableError' || name === 'TrackStartError') {
      toast({ title: '摄像头被其他程序占用，已进入模拟拍摄', icon: 'none' })
    } else if (name === 'NotAllowedError' || name === 'SecurityError') {
      // 曾点过“阻止”后 Chrome 会记住、不再弹框直接拒绝——提示如何解除
      toast({ title: '相机权限被禁用，点地址栏相机图标改为“允许”后重试；已进入模拟', icon: 'none', duration: 3000 })
    } else {
      toast({ title: '相机打开失败，已进入模拟拍摄', icon: 'none' })
    }
    openMockCamera()
  }
}
function closeRealCamera() {
  stopRealStream()
  realCamVisible.value = false
  realShotUrl.value = ''
  _realShotFile = null
}
// 按快门：抓当前视频帧到 canvas → 预览确认（不直接识别，和模拟/上传一致）
async function realShoot() {
  const v = realVideoEl.value
  if (!v || !v.videoWidth) return
  realCamFlash.value = true
  setTimeout(() => { realCamFlash.value = false }, 180)
  const cv = document.createElement('canvas')
  cv.width = v.videoWidth
  cv.height = v.videoHeight
  cv.getContext('2d').drawImage(v, 0, 0, cv.width, cv.height)
  const blob = await new Promise((r) => cv.toBlob(r, 'image/jpeg', 0.92))
  _realShotFile = new File([blob], '相机-' + (scanItems.value.length + 1) + '.jpg', { type: 'image/jpeg' })
  await new Promise((r) => setTimeout(r, 220)) // 让"咔嚓"闪一下再切预览
  realShotUrl.value = cv.toDataURL('image/jpeg', 0.92)
}
function realRetake() { realShotUrl.value = ''; _realShotFile = null; bindStreamToVideo() }
// 「确定」：加入暂存并回到取景，可继续连拍多张（回取景需重绑视频流）
function realUsePhoto() {
  const file = _realShotFile
  if (!file) return
  addScanItem(file, realShotUrl.value)
  realShotUrl.value = ''
  _realShotFile = null
  bindStreamToVideo()
}
// 底部导航栏随滚动收起（0730 用户定，点8）：向下滚→隐藏一级导航栏（操作条落到屏底），向上滚/近顶→复现
let _lastScrollY = 0
function _onWinScroll() {
  const y = window.scrollY || document.documentElement.scrollTop || 0
  if (y < 60) { homeShell.navHidden = false; _lastScrollY = y; return }
  if (Math.abs(y - _lastScrollY) < 10) return
  homeShell.navHidden = y > _lastScrollY
  _lastScrollY = y
}
onMounted(() => window.addEventListener('scroll', _onWinScroll, { passive: true }))
// 离开页面/组件卸载时务必释放摄像头；顺带复位欢迎页标记，避免离开后底栏一直被隐藏
onUnmounted(() => {
  stopRealStream()
  window.removeEventListener('scroll', _onWinScroll)
  homeShell.navHidden = false
  // 开发期热更新会先挂载新页面、再卸载旧页面；同在 /main 时不回写 false，避免驾驶舱底栏误显。
  if (window.location.pathname !== '/main') homeShell.welcomeVisible = false
})

// AI 识别完成结果卡（自定义精美弹层，替代通用 showModal）
const scanResultCard = ref(null)
function closeScanResult() { scanResultCard.value = null }
function confirmScanResult(overwrite) {
  const c = scanResultCard.value
  if (!c) return
  let filled = false
  if (c.mode === 'multi-notice') {
    // 用户从多份通知中选定一份 → 覆盖填入该份
    const src = (c.selectedNotice != null && c.selectedNotice >= 0) ? c.noticeOptions[c.selectedNotice] : null
    if (src) { applyNoticeFields(src, true); filled = true }
  } else if (c.mode === 'notice') {
    // 空字段总是填入；有冲突的字段仅当用户选择「覆盖」时才改写
    applyNoticeFields(c.res, !!overwrite); filled = true
  }
  if (c.materials && c.materials.length) {
    c.materials.forEach(addScannedMaterialFromInfo)
    toast({ title: '已加入 ' + c.materials.length + ' 份会议材料', icon: 'none' })
  } else if (filled) {
    toast({ title: '已自动填写，请核对', icon: 'none' })
  }
  createTab.value = 'manual'   // 识别完成后收起上传区，右侧入口显示已识别文件数
  scanResultCard.value = null
}
// 多份通知的「手动填写」：不填通知字段，但仍把材料加上
function manualFillScanResult() {
  const c = scanResultCard.value
  if (!c) return
  if (c.materials && c.materials.length) {
    c.materials.forEach(addScannedMaterialFromInfo)
    toast({ title: '已加入 ' + c.materials.length + ' 份会议材料，会议信息请手动填写', icon: 'none' })
  }
  createTab.value = 'manual'   // 识别完成 → 切回手动填写
  scanResultCard.value = null
}
// 结果卡次按钮分流：多份通知→手动填写（加材料）；冲突→保留原信息（填空+加材料）；否则→取消
function onScanGhost() {
  const c = scanResultCard.value
  if (!c) return
  if (c.mode === 'multi-notice') { manualFillScanResult(); return }
  if (c.needOverwriteAsk) { confirmScanResult(false); return }
  closeScanResult()
}
// 通知去重：过滤全空、按关键字段签名去重
function noticeSig(o) {
  return [o.title || '', o.meetingDate || '', o.meetingTime || '', o.location || '', (o.topics || []).join('｜')].join('|')
}
function dedupeNotices(arr) {
  const seen = new Set(), out = []
  for (const o of (arr || [])) {
    const sig = noticeSig(o)
    if (sig === '||||') continue
    if (seen.has(sig)) continue
    seen.add(sig); out.push(o)
  }
  return out
}

// 会议通知字段：与当前表单比对，返回冲突项（两边都有值且不同）
function noticeConflicts(res) {
  const out = []
  const cmp = (label, cur, nv) => {
    const a = (cur == null ? '' : String(cur)).trim()
    const b = (nv == null ? '' : String(nv)).trim()
    if (a && b && a !== b) out.push({ label, cur: a, nv: b })
  }
  // 仅"用户真正填过"的字段才参与冲突比对（等于默认占位值的视为未填，不冲突）
  cmp('会议名称', isFieldUserSet('title') ? createForm.title : '', res.title)
  cmp('日期', isFieldUserSet('meetingDate') ? createForm.meetingDate : '', res.meetingDate)
  cmp('时间', isFieldUserSet('meetingTime') ? createForm.meetingTime : '', res.meetingTime)
  cmp('地点', isFieldUserSet('location') ? createForm.location : '', res.location)
  const curT = (createForm.topics || []).map((t) => t.title).join('｜')
  const newT = (Array.isArray(res.topics) ? res.topics : []).map((t) => String(t)).join('｜')
  if (curT && newT && curT !== newT) out.push({ label: '议题', cur: curT, nv: newT })
  return out
}

// 应用通知字段：未填(空或仍是默认占位)的总是填；用户已填且冲突的字段仅 overwrite 时才覆盖
function applyNoticeFields(res, overwrite) {
  let changed = false
  if (res.title && (!isFieldUserSet('title') || overwrite)) { createForm.title = res.title; changed = true }
  if (res.meetingDate && (!isFieldUserSet('meetingDate') || overwrite)) { createForm.meetingDate = res.meetingDate; changed = true }
  if (res.meetingTime && (!isFieldUserSet('meetingTime') || overwrite)) { createForm.meetingTime = res.meetingTime; changed = true }
  if (res.location && (!isFieldUserSet('location') || overwrite)) { createForm.location = res.location; syncLocationPreset(res.location); changed = true }
  if (Array.isArray(res.topics) && res.topics.length && (!(createForm.topics && createForm.topics.length) || overwrite)) {
    createForm.topics = res.topics.map(normalizeRecognizedTopic)
    changed = true
  }
  if (changed) docPrefilled.value = true
  return changed
}

function normalizeRecognizedTopic(raw) {
  let title = String(raw || '').trim()
  let type = 'decision'
  const typed = title.match(/^\s*(notice|discussion|decision)\s*[|｜:]\s*(.+)$/i)
  if (typed) {
    type = typed[1].toLowerCase()
    title = typed[2].trim()
  } else if (/通知|通报|知悉|传达/.test(title)) {
    type = 'notice'
  } else if (/讨论|研讨|征求意见|意见汇总/.test(title) && !/表决|审议通过|投票/.test(title)) {
    type = 'discussion'
  }
  return {
    title,
    type,
    decisionType: type === 'decision' ? 'simple' : 'none',
    options: []
  }
}

// 多文件识别结果 → 组装结果卡：识别为通知/材料、已识别/待补填字段、耗时+token
function handleMultiScanResult(res) {
  if (!res) { toast({ title: '识别未完成，请重试或手动填写', icon: 'none' }); return }
  const filesArr = Array.isArray(res.files) ? res.files : []
  const hasPrefill = res.available && !!(res.title || res.meetingDate || res.meetingTime || res.location || (Array.isArray(res.topics) && res.topics.length))
  const materials = hasPrefill
    ? filesArr.filter((f) => f.category === 'material' && f.fileUrl)
    : filesArr.filter((f) => f.fileUrl)
  const seconds = scanSec.value || 0
  const tokens = res.tokens || 0

  // 全部是材料（没抽到通知信息）：默认添加
  if (!hasPrefill) {
    if (!materials.length) { toast({ title: '请手动填写会议信息', icon: 'none' }); return }
    scanResultCard.value = {
      mode: 'material', materialCount: materials.length,
      missingRequired: [], conflictNote: '', conflicts: [],
      needOverwriteAsk: false, primaryLabel: '加入材料', ghostLabel: '取消',
      seconds, tokens, res, materials
    }
    return
  }

  // 识别到多份"不同"的会议通知 → 让用户选一份填入（或手动填写）；材料仍会添加
  const noticeOpts = dedupeNotices(res.notices)
  if (noticeOpts.length > 1) {
    scanResultCard.value = {
      mode: 'multi-notice',
      materialCount: materials.length,
      noticeOptions: noticeOpts,
      selectedNotice: 0,
      missingRequired: [], conflictNote: '', conflicts: [],
      needOverwriteAsk: false,
      primaryLabel: '填入所选', ghostLabel: '手动填写',
      seconds, tokens, res, materials
    }
    return
  }

  // 识别为会议通知：冲突 = 新识别值与「已填写」的不同 → 才问覆盖；否则空字段直填、材料照加
  const conflicts = noticeConflicts(res)
  const hasConf = conflicts.length > 0
  const REQUIRED = [
    { label: '标题', has: !!(res.title && res.title.trim()) },
    { label: '议题', has: !!(Array.isArray(res.topics) && res.topics.length) }
  ]
  scanResultCard.value = {
    mode: 'notice',
    materialCount: materials.length,
    missingRequired: REQUIRED.filter((f) => !f.has).map((f) => f.label),
    conflictNote: (res.conflictNote || '').trim(),
    conflicts,
    needOverwriteAsk: hasConf,
    primaryLabel: hasConf ? '覆盖并填入' : '确认填入',
    ghostLabel: hasConf ? '保留原信息' : '取消',
    seconds, tokens, res, materials
  }
}
// 单份通知识别结果 → 预览行（名称/时间/地点/议题），让用户在弹窗里一眼核对
const scanNoticeRows = computed(() => {
  const c = scanResultCard.value
  if (!c || c.mode !== 'notice') return []
  const r = c.res || {}
  const topics = Array.isArray(r.topics) ? r.topics.map((t) => String(t).trim()).filter(Boolean) : []
  const dt = formatScanDateTime(r.meetingDate, r.meetingTime)
  return [
    { label: '名称', value: (r.title || '').trim(), miss: !(r.title && r.title.trim()) },
    { label: '时间', value: dt, miss: !dt },
    { label: '地点', value: (r.location || '').trim(), miss: !(r.location && r.location.trim()) },
    { label: '议题', value: topics.length ? (topics.length + ' 项') : '', miss: !topics.length, topics }
  ]
})
// 识别到的日期时间 → 友好显示：2026-07-15 14:00 → 7月15日 14:00
function formatScanDateTime(d, t) {
  const date = (d || '').trim(), time = (t || '').trim()
  const m = date.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/)
  const ds = m ? (Number(m[2]) + '月' + Number(m[3]) + '日') : date
  return [ds, time].filter(Boolean).join(' ')
}
// 把某个已落库的文件加入待挂载材料（去重：同 url 不重复加）
function addScannedMaterialFromInfo(f) {
  if (!f || !f.fileUrl) return
  if (pendingMaterials.value.some((m) => m.url === f.fileUrl)) return
  pendingMaterials.value = pendingMaterials.value.concat([{
    url: f.fileUrl, fileName: f.fileName || '识别文件', fileType: f.fileType || '',
    fileSize: f.fileSize || 0, sizeText: humanSize(f.fileSize || 0)
  }])
}
const docProgress = ref(0)
let docProgTimer = null
// 模拟进度：后端一次性返回拿不到真实百分比，定时器渐进逼近 90%（越近越慢），完成时跳 100%。
// 节奏放慢到约 12-13s 到 90%，与后端识别耗时(约 10-15s)对齐，避免前段冲太快、后段干等。
function startDocProgress() {
  docProgress.value = 0
  clearInterval(docProgTimer)
  docProgTimer = setInterval(() => {
    const p = docProgress.value
    if (p >= 90) return
    const step = p < 60 ? 5 : p < 80 ? 2 : 1
    docProgress.value = Math.min(90, p + step)
  }, 400)
}
function stopDocProgress() {
  clearInterval(docProgTimer)
  docProgTimer = null
}
// 预填/历史地点回填时同步下拉选中态：命中常用项→选它；非预设值→切"其他"并在手填框回显
function syncLocationPreset(val) {
  if (val && commonLocations.indexOf(val) >= 0) {
    locationPreset.value = val
  } else if (val) {
    locationPreset.value = '__other__'
  } else {
    locationPreset.value = '社区活动室'
  }
}

// 地点下拉：选预设直接用，选"其他"则清空等手填
function onLocationPreset(e) {
  const v = e.target.value
  locationPreset.value = v
  createForm.location = (v === '__other__') ? '' : v
  clearFieldError('location')
}

// 地点行点击：原生 action sheet 选常用地点 / 其他手填 / 从地图选点（学微信/系统的底部选择，比内嵌下拉更简洁）
async function openLocPicker() {
  clearFieldError('location')
  const items = [...commonLocations, '其他地点（手动填写）', '从地图选点']
  const res = await showActionSheet({ itemList: items })
  if (!res || res.tapIndex == null || res.tapIndex < 0) return
  const i = res.tapIndex
  if (i < commonLocations.length) {
    locationPreset.value = commonLocations[i]
    createForm.location = commonLocations[i]
  } else if (i === commonLocations.length) {
    locationPreset.value = '__other__'
    createForm.location = ''
  } else {
    pickLocationOnMap()
  }
}

// 地图选点（0723 接真实接口）：腾讯官方 H5 选点组件（MapPicker.vue，iframe+postMessage）。
// 未配置 VITE_TXMAP_KEY 时组件内给配置指引+演示地点兜底；配置后即真地图搜索/拖点。
const mapPickerOpen = ref(false)
function pickLocationOnMap() { mapPickerOpen.value = true }
function onMapPicked(p) {
  locationPreset.value = '__other__'
  // 地点名为主，详细地址跟在括号里；经纬度随建会落库 → 详情页导航用精确坐标
  _mapJustSet = true
  createForm.location = p.name ? (p.address ? p.name + '（' + p.address + '）' : p.name) : (p.address || '')
  createForm.locationLat = p.lat
  createForm.locationLng = p.lng
  toast({ title: '已选择地点', icon: 'success' })
}

// 从"其他/地图选点"切回常用地点下拉
// 日期/时间选择器由发起会议与接待登记共用（0716）：pickerTarget 决定读写哪个表单。
// ⚠ 发起会议模板是裸调用 @click="openDatePicker"，Vue 会把 MouseEvent 当第一参传进来——
// 所以判定只认字符串 'reception'，其余（含 Event 对象）一律当 meeting。
const pickerTarget = ref('meeting')
function _pickerDate() { return pickerTarget.value === 'reception' ? recForm.date : createForm.meetingDate }
// 日期选择器：点击字段任意位置弹出，小日历点日即选
function openDatePicker(target) {
  pickerTarget.value = target === 'reception' ? 'reception' : 'meeting'
  const parts = (_pickerDate() || todayStr()).split('-')
  dpYear.value = Number(parts[0]) || _nowYear
  dpMonth.value = Number(parts[1]) || 1
  dpDay.value = Number(parts[2]) || 1
  clampDpDay()
  datePickerOpen.value = true
  scrollPickerToSelected()
}
function setDpYear(y) { dpYear.value = y; clampDpDay() }
function setDpMonth(m) { dpMonth.value = m; clampDpDay() }
function clampDpDay() {
  const n = new Date(dpYear.value, dpMonth.value, 0).getDate()
  if (dpDay.value > n) dpDay.value = n
}
function confirmDate() {
  const v = dpYear.value + '-' + String(dpMonth.value).padStart(2, '0') + '-' + String(dpDay.value).padStart(2, '0')
  if (pickerTarget.value === 'reception') recForm.date = v
  else createForm.meetingDate = v
  datePickerOpen.value = false
}

// —— 小日历：用 dpYear/dpMonth 作当前查看月，点某天即选并关闭 ——
const calCells = computed(() => {
  const first = new Date(dpYear.value, dpMonth.value - 1, 1).getDay() // 0=周日
  const days = new Date(dpYear.value, dpMonth.value, 0).getDate()
  const cells = []
  for (let i = 0; i < first; i++) cells.push(0) // 月初空格
  for (let d = 1; d <= days; d++) cells.push(d)
  return cells
})
function calPrevMonth() {
  if (dpMonth.value <= 1) { dpMonth.value = 12; dpYear.value -= 1 } else { dpMonth.value -= 1 }
}
function calNextMonth() {
  if (dpMonth.value >= 12) { dpMonth.value = 1; dpYear.value += 1 } else { dpMonth.value += 1 }
}
function calDateStr(day) {
  return dpYear.value + '-' + String(dpMonth.value).padStart(2, '0') + '-' + String(day).padStart(2, '0')
}
function isSelectedDay(day) { return _pickerDate() === calDateStr(day) }
function isToday(day) { return todayStr() === calDateStr(day) }
function isPastMeetingDay(day) {
  return pickerTarget.value === 'meeting' && calDateStr(day) < todayStr()
}
function pickCalDay(day) {
  if (isPastMeetingDay(day)) {
    toast({ title: '会议日期不能早于今天', icon: 'none' })
    return
  }
  if (pickerTarget.value === 'reception') recForm.date = calDateStr(day)
  else createForm.meetingDate = calDateStr(day)
  datePickerOpen.value = false
}

// 时间选择器：常规小时（左）+ 分钟（右），点选即生效；与日期选择器同走 pickerTarget
function openTimePicker(target) {
  pickerTarget.value = target === 'reception' ? 'reception' : 'meeting'
  const cur = pickerTarget.value === 'reception' ? recForm.time : createForm.meetingTime
  const parts = (cur || '09:00').split(':')
  tpHour.value = Math.min(20, Math.max(9, Number(parts[0]) || 9)) // 夹到 9—20 点
  tpMinute.value = (Math.round((Number(parts[1]) || 0) / 15) * 15) % 60
  if (isPastTimeOption(tpHour.value, tpMinute.value)) {
    const next = firstAvailableTime()
    if (!next) {
      toast({ title: '今天已无可选时间，请选择明天', icon: 'none' })
      return
    }
    tpHour.value = next.hour
    tpMinute.value = next.minute
    applyTime()
  }
  timePickerOpen.value = true
  scrollPickerToSelected()
}
function confirmTime() {
  if (isPastTimeOption(tpHour.value, tpMinute.value)) {
    toast({ title: '会议时间不能早于当前时间', icon: 'none' })
    return
  }
  applyTime()
  timePickerOpen.value = false
}
function isPastTimeOption(hour, minute) {
  if (pickerTarget.value !== 'meeting' || createForm.meetingDate !== todayStr()) return false
  const now = new Date()
  return Number(hour) * 60 + Number(minute) <= now.getHours() * 60 + now.getMinutes()
}
function firstAvailableTime() {
  for (const hour of hourOptions) {
    for (const minute of minuteOptions) {
      if (!isPastTimeOption(hour, minute)) return { hour, minute }
    }
  }
  return null
}
// 大按钮点选：点即更新并实时写入（免"确定"那一步）
function applyTime() {
  const v = String(tpHour.value).padStart(2, '0') + ':' + String(tpMinute.value).padStart(2, '0')
  if (pickerTarget.value === 'reception') recForm.time = v
  else createForm.meetingTime = v
}
function setTpHour(h) {
  if (isPastTimeOption(h, 45)) return
  tpHour.value = h
  if (isPastTimeOption(h, tpMinute.value)) {
    const minute = minuteOptions.find(m => !isPastTimeOption(h, m))
    if (minute === undefined) return
    tpMinute.value = minute
  }
  applyTime()
}
function setTpMinute(m) {
  if (isPastTimeOption(tpHour.value, m)) return
  tpMinute.value = m
  applyTime()
}

// 打开选择器后把已选项滚到列中部（只滚动列内部，不影响页面）
function scrollPickerToSelected() {
  nextTick(function () {
    const scrolls = document.querySelectorAll('.picker-pop .pp-col-scroll')
    scrolls.forEach(function (scroll) {
      const on = scroll.querySelector('.pp-item.on')
      if (on) scroll.scrollTop = on.offsetTop - scroll.clientHeight / 2 + on.clientHeight / 2
    })
  })
}

// ── 创建表单：议题管理（弹窗式） ──

function removeCreateTopic(idx) {
  createForm.topics = createForm.topics.filter(function (_, i) { return i !== idx })
}

function topicTypeLabel(t) {
  if (!t) return ''
  if (t.type === 'notice') return '通知'
  if (t.type === 'discussion') return '讨论'
  if (t.type === 'decision') return '表决'
  return ''
}

function topicTypeClass(t) {
  if (!t) return 'badge-discussion'
  // notice 视觉并入 discussion（同名同色，badge-notice 不再产出）
  if (t.type === 'decision') return 'badge-decision'
  return 'badge-discussion'
}

function openAddTopic() {
  clearFieldError('topics')
  topicEditIdx.value = -1
  topicDraft.title = ''
  topicDraft.type = 'discussion'
  topicDraft.decisionType = 'none'
  topicDraft.options = []
  topicDraft.content = ''
  topicDialogOpen.value = true
}

function openEditTopic(idx) {
  const t = createForm.topics[idx]
  topicEditIdx.value = idx
  topicDraft.title = t.title || ''
  topicDraft.type = t.type || 'discussion'
  topicDraft.decisionType = t.decisionType || 'none'
  topicDraft.options = (t.options || []).map(function (o) { return { id: o.id, label: o.label } })
  topicDraft.content = t.content || ''
  topicDialogOpen.value = true
}

function draftPickType(type) {
  topicDraft.type = type
  topicDraft.decisionType = type === 'decision' ? 'simple' : 'none'
  topicDraft.options = []
}

function draftPickDecision(type) {
  topicDraft.decisionType = type
  topicDraft.options = type === 'multi_choice' ? [{ id: 1, label: '' }, { id: 2, label: '' }] : []
}

function draftAddOption() {
  const options = topicDraft.options || []
  const newId = options.length ? Math.max.apply(null, options.map(function (o) { return o.id })) + 1 : 1
  topicDraft.options = options.concat([{ id: newId, label: '' }])
}

function draftRemoveOption(i) {
  topicDraft.options = topicDraft.options.filter(function (_, idx) { return idx !== i })
}

function confirmTopic() {
  if (!topicDraft.title.trim()) { toast({ title: '请输入议题内容', icon: 'none' }); return }
  if (topicDraft.type === 'decision' && topicDraft.decisionType === 'multi_choice') {
    const valid = (topicDraft.options || []).filter(function (o) { return o.label.trim() })
    if (valid.length < 2) { toast({ title: '多选一议题至少需要两个选项', icon: 'none' }); return }
  }
  // 0728：三类显式落库（通知/讨论/表决），不再按有无正文自动分流；通知正文（如有）随 notice 带上
  const noticeContent = topicDraft.type === 'notice' ? (topicDraft.content || '').trim() : ''
  const nt = {
    title: topicDraft.title.trim(),
    type: topicDraft.type,
    decisionType: topicDraft.decisionType,
    options: (topicDraft.options || []).map(function (o) { return { id: o.id, label: o.label } }),
    content: noticeContent
  }
  if (topicEditIdx.value >= 0) {
    const arr = createForm.topics.slice()
    arr[topicEditIdx.value] = nt
    createForm.topics = arr
  } else {
    createForm.topics = createForm.topics.concat([nt])
  }
  topicDialogOpen.value = false
}

async function submitNewMeeting() {
  var form = createForm
  // OCR 还在识别时先别提交：此刻 createForm 可能是中间态，等识别完再去通知
  if (scanBusy.value) { toast({ title: '正在识别中，请稍候…', icon: 'none' }); return }
  // 议题：逐条添加在 createForm.topics（过滤空标题）。议题现全部经弹窗添加，提交时列表已是最终态
  var topics = (form.topics || []).filter(function (t) { return t.title && t.title.trim() })
  // 必填校验：会议名称 / 会议地点 / 会议议题。缺失 → 弹卡片列出，确认后亮红框
  fieldErrors.title = false; fieldErrors.location = false; fieldErrors.topics = false; fieldErrors.meetingDate = false; fieldErrors.meetingTime = false
  const missing = []
  if (!form.title || !form.title.trim()) missing.push('会议名称')
  if (!form.meetingDate || !form.meetingDate.trim()) missing.push('会议日期')
  if (!form.meetingTime || !form.meetingTime.trim()) missing.push('会议时间')
  if (!form.location || !form.location.trim()) missing.push('会议地点')
  if (!topics.length) missing.push('会议议题')
  if (missing.length) {
    await showModal({
      title: '还有内容没填写',
      content: '请先补全以下内容：\n' + missing.map((m) => '· ' + m).join('\n'),
      confirmText: '知道了',
      showCancel: false,
      size: 'large'
    })
    if (missing.includes('会议名称')) fieldErrors.title = true
    if (missing.includes('会议日期')) fieldErrors.meetingDate = true
    if (missing.includes('会议时间')) fieldErrors.meetingTime = true
    if (missing.includes('会议地点')) fieldErrors.location = true
    if (missing.includes('会议议题')) fieldErrors.topics = true
    return
  }
  // 新发起的会议不得选择今天以前的日期。接待补录和既有历史会议编辑不受此限制。
  if (!editingMeetingId.value && form.meetingDate < todayStr()) {
    fieldErrors.meetingDate = true
    await showModal({
      title: '会议日期不正确',
      content: '发起会议时，会议日期不能早于今天，请重新选择。',
      confirmText: '重新选择',
      showCancel: false
    })
    return
  }
  if (form.meetingDate === todayStr()) {
    const now = new Date()
    const nowMinutes = now.getHours() * 60 + now.getMinutes()
    const timeParts = form.meetingTime.split(':')
    const meetingMinutes = Number(timeParts[0]) * 60 + Number(timeParts[1])
    if (meetingMinutes <= nowMinutes) {
      fieldErrors.meetingTime = true
      await showModal({
        title: '会议时间不正确',
        content: '今天的会议时间不能早于当前时间，请重新选择。',
        confirmText: '重新选择',
        showCancel: false
      })
      return
    }
  }
  // 编辑模式：更新本会议（不新建），完成后回到该会议的「会议通知」页
  if (editingMeetingId.value) {
    const id = editingMeetingId.value
    try {
      await api.committeeUpdate(id, {
        title: form.title, meetingDate: form.meetingDate, meetingTime: form.meetingTime,
        location: form.location, meetingMethod: form.meetingMethod || 'offline',   // 0725 修:同创建,编辑时线上/线下切换原先存不进去
        description: form.description, topics: topics
      })
      // 居委会见证态若有变，翻转标记（toggle 语义：与载入态不同才切）
      if (form.juweiWitness !== editInitialJuwei.value) {
        try { await api.committeeToggleFlag(id, 'hasMajorIssue') } catch (e) {}
      }
      editingMeetingId.value = null
      createVisible.value = false
      toast({ title: '已保存修改', icon: 'success' })
      const target = '/pages/committee-detail/committee-detail?id=' + id
      const browserUrl = '/committee-detail?id=' + id
      try { await navigateTo(target) } catch (navErr) { console.error('[编辑会议] 软跳 reject：', navErr) }
      setTimeout(() => { if (!document.querySelector('.detail-page')) window.location.href = browserUrl }, 500)
    } catch (e) {
      toast({ title: (e && e.message) || '保存失败', icon: 'none' })
    }
    return
  }
  try {
    const created = await api.committeeCreate({
      title: form.title,
      meetingDate: form.meetingDate,
      meetingTime: form.meetingTime,
      location: form.location,
      locationLat: form.locationLat,
      locationLng: form.locationLng,
      meetingMethod: form.meetingMethod || 'offline',   // 0725 修:原 payload 漏了它,选「线上会议」建出来仍是线下
      description: form.description,
      topics: topics
    })
    // 居委会见证：勾选则标记本次为重大事项（hasMajorIssue）。失败不阻断建会流程。
    if (created && created.id && form.juweiWitness) {
      try { await api.committeeToggleFlag(created.id, 'hasMajorIssue') } catch (e) {}
    }
    // 会议材料：弹窗里传好的文件逐份挂到会议（供委员传阅，后端顺带触发 OCR）。单份失败不阻断建会。
    if (created && created.id && pendingMaterials.value.length) {
      for (const m of pendingMaterials.value) {
        try { await api.committeeAddMaterial(created.id, m.fileName, m.sizeText, m.fileType, m.url) } catch (e) { console.error('[材料] 挂载失败：', m.fileName, e) }
      }
    }
    console.log('[去通知] created =', JSON.stringify(created))
    createVisible.value = false
    clearDraft()   // 会议已发出，草稿完成使命，清掉首页草稿卡
    currentStage.value = 'preparing'
    if (created && created.id) {
      // 若测试过程中仍残留上一身份的暂停录音，先彻底释放；否则 beforeunload 会拦截通知页兜底跳转。
      if (meetingRecordingSession.meetingId) {
        await discardMeetingRecording(meetingRecordingSession.meetingId)
      }
      // 竞态兜底：navigateTo(router.push) 偶发被取消/重复导航会 reject，或"URL变了却不切换视图"，
      // 都会把用户留在主页（弹窗已关）。软跳后延时校验通知页(.detail-page)是否真的挂上，没挂上就
      // window.location 硬跳过去，确保必达（对齐本 app 其它关键跳转的硬导航兜底做法）。
      const target = '/pages/committee-detail/committee-detail?id=' + created.id
      const browserUrl = '/committee-detail?id=' + created.id
      console.log('[去通知] 跳转 →', target)
      try { await redirectTo(target) } catch (navErr) { console.error('[去通知] 软跳 reject：', navErr) }
      setTimeout(() => {
        if (!document.querySelector('.detail-page')) {
          console.warn('[去通知] 软跳未挂载通知页，硬导航兜底 →', browserUrl)
          window.location.replace(browserUrl)
        }
      }, 1500)
    } else {
      console.warn('[去通知] created 无 id，回列表', created)
      toast({ title: '会议已创建，请在列表中打开', icon: 'none' })
      loadAll()
    }
  } catch (e) {
    toast({ title: e.message, icon: 'none' })
  }
}

function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

// ——— 语音输入 ———（热词纠错 applyHotwords 已抽到 @/utils/helpers 与 MeetingLiveQuick 共用）

function _stopVoice() {
  if (_voiceRec) {
    try { _voiceRec.stop() } catch (e) {}
    _voiceRec = null
  }
}

function _buildRec(continuous, interimResults) {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRec) {
    toast({ title: '当前浏览器不支持语音识别', icon: 'none' })
    return null
  }
  const rec = new SpeechRec()
  rec.lang = 'zh-CN'
  rec.continuous = continuous
  rec.interimResults = interimResults
  return rec
}

// 流式模式：标题 / 议题（连续识别，显示弹窗，点确认后应用）
function startStreamingVoice(target) {
  _stopVoice()
  voiceFinal.value = ''
  voiceInterim.value = ''
  voiceTarget.value = target

  const rec = _buildRec(true, true)
  if (!rec) { voiceTarget.value = ''; return }
  _voiceRec = rec

  rec.onresult = function (e) {
    let fin = '', inter = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const t = e.results[i][0].transcript
      if (e.results[i].isFinal) fin += t
      else inter += t
    }
    if (fin) voiceFinal.value += applyHotwords(fin)
    voiceInterim.value = inter
  }
  rec.onerror = function () { _stopVoice() }
  rec.start()
}

// 直接模式：时间 / 地点（单次识别，无弹窗，自动写入）
function startDirectVoice(target) {
  _stopVoice()
  voiceTarget.value = target

  const rec = _buildRec(false, false)
  if (!rec) { voiceTarget.value = ''; return }
  _voiceRec = rec

  rec.onresult = function (e) {
    const text = (e.results[0] && e.results[0][0] && e.results[0][0].transcript) || ''
    voiceTarget.value = ''
    _voiceRec = null
    if (target === 'time') parseAndSetTime(text)
    else if (target === 'location') parseAndSetLocation(text)
  }
  rec.onerror = function () { voiceTarget.value = ''; _voiceRec = null }
  rec.onend = function () { if (voiceTarget.value === target) voiceTarget.value = '' }
  rec.start()
}

function confirmVoice() {
  const text = (voiceFinal.value + voiceInterim.value).trim()
  const target = voiceTarget.value
  _stopVoice()
  voiceTarget.value = ''
  voiceFinal.value = ''
  voiceInterim.value = ''
  if (!text) return
  if (target === 'title') {
    createForm.title = text
  } else if (target === 'topic') {
    // 议题语音输入现落在议题弹窗内 → 写入弹窗草稿标题（追加，便于分句续说）
    topicDraft.title = (topicDraft.title ? topicDraft.title + ' ' : '') + text
  }
}

function retryVoice() {
  const target = voiceTarget.value
  startStreamingVoice(target)
}

function cancelVoice() {
  _stopVoice()
  voiceTarget.value = ''
  voiceFinal.value = ''
  voiceInterim.value = ''
}

// 解析时间文字，例如 "上午十点" / "下午两点半" / "9点15分"
function parseAndSetTime(text) {
  if (!text) return
  let hour = null, minute = 0
  const pm = /下午|傍晚|晚上/.test(text)
  const am = /上午|早上|早晨/.test(text)
  // 中文数字映射
  const cnNum = { '零': 0, '一': 1, '两': 2, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9, '十': 10, '十一': 11, '十二': 12 }
  // 先尝试阿拉伯数字 "9点" / "9:30"
  const arM = text.match(/(\d{1,2})[点:](\d{0,2})/)
  if (arM) {
    hour = parseInt(arM[1])
    minute = arM[2] ? parseInt(arM[2]) : 0
  } else {
    // 中文 "十点" / "两点" / "十一点"
    for (const [k, v] of Object.entries(cnNum)) {
      if (text.indexOf(k + '点') >= 0) { hour = v; break }
    }
  }
  // 分钟
  const minM = text.match(/(\d{1,2})分/)
  if (minM) minute = parseInt(minM[1])
  const halfM = /半/.test(text)
  if (halfM) minute = 30
  // 修正 AM/PM
  if (hour !== null) {
    if (pm && hour < 12) hour += 12
    if (am && hour === 12) hour = 0
    // 没有明确上午/下午时，业委会场景默认：1-8 当下午
    if (!am && !pm && hour >= 1 && hour <= 8) hour += 12
    hour = Math.max(9, Math.min(20, hour))
    // 对齐到 0/15/30/45
    minute = [0, 15, 30, 45].reduce((p, c) => Math.abs(c - minute) < Math.abs(p - minute) ? c : p)
    tpHour.value = hour
    tpMinute.value = minute
    createForm.meetingTime = String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0')
    toast({ title: '时间已设为 ' + createForm.meetingTime, icon: 'success' })
  } else {
    toast({ title: '未能识别时间，请重试', icon: 'none' })
  }
}

// 解析地点文字，匹配预设选项或直接写入
function parseAndSetLocation(text) {
  if (!text) return
  const matched = commonLocations.find(loc => text.indexOf(loc) >= 0 || loc.indexOf(text) >= 0)
  if (matched) {
    locationPreset.value = matched
    createForm.location = matched
  } else {
    locationPreset.value = '__other__'
    createForm.location = text
  }
  toast({ title: '地点已设为 ' + (matched || text), icon: 'success' })
}

onMounted(show)
onActivated(show)
</script>

<style scoped>
.home {
  min-height: 100vh; background: var(--c-bg-page);
  /* 底部留空：清开固定 TabBar(100rpx) 再留一点缝隙；更多功能靠 margin-top:auto 贴底 */
  padding-bottom: calc(132rpx + env(safe-area-inset-bottom));
  display: flex; flex-direction: column; box-sizing: border-box;
}
/* 顶栏 */
/* 顶栏加高（0716 用户定），评分徽章 align-self:center 在栏内垂直居中 */
/* 页头三色制（规范二）：会议蓝#2f5f9e／接待绿#2f6b45（模块色仅用于页头/底栏选中/主按钮） */
.hd { display: flex; align-items: flex-end; justify-content: space-between; padding: calc(env(safe-area-inset-top) + 14rpx) 32rpx 18rpx; background: #2f5f9e; }
.reception-home > .hd { background: #2f6b45; }   /* 接待页顶栏＝模块绿 */
.reception-home .hd-sub { color: #c6ddcf; }      /* 接待页头副文字（规范） */
.hd-left { display: flex; flex-direction: column; padding-top: 4rpx; }
.hd-title { font-size: 42rpx; font-weight: 700; color: #fff; line-height: 1.25; }
.hd-sub { font-size: 28rpx; color: #c5cede; margin-top: 4rpx; line-height: 1.3; }   /* 会议页头副文字（规范） */
.hd-bell { position: relative; padding: 8rpx; align-self: center; }
.hd-bell-ico { font-size: 52rpx; }
.hd-badge { position: absolute; top: -2rpx; right: -6rpx; min-width: 34rpx; height: 34rpx; padding: 0 8rpx; background: var(--c-danger); color: #fff; font-size: 28rpx; border-radius: 17rpx; line-height: 34rpx; text-align: center; }
/* 顶栏右上角评分（0731 四改）：大数字在上并在标签宽度内水平居中、「当前综合评分 ›」在下，可点进个人中心 */
.hd-score { display: inline-flex; flex-direction: column; align-items: center; gap: 6rpx; align-self: center; cursor: pointer; }
.hd-score:active { opacity: .7; }
.hd-score-label { font-size: 25rpx; font-weight: 500; color: rgba(255,255,255,0.85); }
/* 数字随分数高低红绿灯渐变（0731 用户定：颜色与得分挂钩是评分语义，恢复；backgroundImage 由 scoreGradient 注入） */
.hd-score-num { font-size: 52rpx; font-weight: 800; line-height: 1; -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; color: transparent; }
/* 当前会议主卡片 */
/* 当前重点横幅（0724 首页改版）：整屏第一视觉。配色取沉稳低饱和的哑光色（0724 用户定：原橙红太刺眼，
   适老要柔和），纯色不用渐变、不用脉动动画——active 深藏青 / urgent 哑光砖红 / calm 沉稳墨绿。白字高对比。 */
.home-focus {
  margin: 16rpx 24rpx 8rpx; overflow: hidden; border-radius: 22rpx;
  padding: 0; background: #fff; border: 1rpx solid #e5e9ec;
  box-shadow: 0 8rpx 24rpx rgba(20,42,58,.08);
}
.hf-section-head {
  display: flex; height: 66rpx; padding: 0 24rpx; align-items: center;
  justify-content: space-between; background: #edf4f8; color: #315c73;
  font-size: 23rpx; font-weight: 700;
}
.home-focus.urgent .hf-section-head { background: #f9edeb; color: #9a5145; }
.home-focus.calm .hf-section-head { background: #edf5f0; color: #47705a; }
.hf-section-head span { display: flex; align-items: center; gap: 10rpx; }
.hf-section-head em {
  padding: 3rpx 12rpx; border-radius: 999rpx; background: rgba(49,92,115,.1);
  font-size: 19rpx; font-style: normal; font-weight: 600;
}
.hf-dot { width: 11rpx; height: 11rpx; border-radius: 50%; background: currentColor; }
.hf-item {
  display: flex; width: 100%; min-height: 112rpx; padding: 20rpx 22rpx;
  border: 0; border-bottom: 1rpx solid #edf0f2; align-items: center;
  gap: 18rpx; background: #fff; text-align: left;
}
.hf-item.primary {
  min-height: 126rpx; padding: 24rpx 22rpx;
  background: linear-gradient(90deg, #f3f8fb 0%, #fff 72%);
}
.hf-item.secondary {
  min-height: 92rpx; padding-top: 15rpx; padding-bottom: 15rpx;
  background: #fbfcfc;
}
.hf-item:last-child { border-bottom: 0; }
.hf-item:active { background: #f7f9fa; }
.hf-body { display: flex; flex: 1; min-width: 0; flex-direction: column; }
.hf-item-state {
  margin-bottom: 5rpx; color: #547488; font-size: 20rpx; line-height: 1.2;
}
.hf-title {
  overflow: hidden; color: #263741; font-size: 29rpx; font-weight: 700;
  line-height: 1.3; text-overflow: ellipsis; white-space: nowrap;
}
.hf-item.primary .hf-title { color: #203b4d; font-size: 32rpx; font-weight: 750; }
.hf-item.secondary .hf-title { color: #52616a; font-size: 25rpx; font-weight: 600; }
.hf-sub {
  overflow: hidden; margin-top: 7rpx; color: #89949b; font-size: 21rpx;
  line-height: 1.35; text-overflow: ellipsis; white-space: nowrap;
}
.hf-item.secondary .hf-item-state { margin-bottom: 3rpx; color: #8b969d; font-size: 18rpx; }
.hf-item.secondary .hf-sub { margin-top: 4rpx; color: #a0a8ad; font-size: 19rpx; }
.hf-cta {
  display: flex; flex: 0 0 auto; min-width: 116rpx; min-height: 58rpx;
  padding: 0 17rpx; border-radius: 13rpx; align-items: center; justify-content: center;
  background: #315f79; color: #fff; font-size: 22rpx; font-weight: 650;
  box-shadow: none;
}
.home-focus.urgent .hf-cta { background: #a75b4f; }
.home-focus.calm .hf-cta { background: #537a63; }
.hf-cta i { margin-left: 7rpx; font-size: 27rpx; font-style: normal; }
.hf-item.secondary .hf-cta {
  min-width: auto; min-height: 48rpx; padding: 0 5rpx;
  background: transparent; color: #71838e; font-size: 20rpx; font-weight: 550;
}
.home-focus.urgent .hf-item.secondary .hf-cta,
.home-focus.calm .hf-item.secondary .hf-cta { background: transparent; color: #71838e; }
/* 驾驶舱首页：欢迎语 + 跨条线待办聚合 + 全部业务目录。统一深蓝灰视觉语言 */
.portal-home {
  min-height: 100dvh;
  padding-bottom: 0;
  background: linear-gradient(180deg, #f5f7f9 0%, #eef2f5 100%);
}
.portal-home .hd {
  align-items: center;
  padding: calc(env(safe-area-inset-top) + 18rpx) 34rpx 22rpx;
  background: #43546F;
  box-shadow: 0 6rpx 18rpx rgba(24, 51, 76, .12);
}
.portal-home .hd-title { font-size: 38rpx; font-weight: 700; letter-spacing: .5rpx; }
.portal-home .hd-sub { margin-top: 5rpx; color: rgba(255,255,255,.72); font-size: 27rpx; }
.welcome { display: flex; flex-direction: column; min-height: calc(100dvh - 162rpx); box-sizing: border-box; padding-bottom: 150rpx; /* 给全局 TabBar 让位（ck-dock 已退役） */ }
/* ── 驾驶舱 0731 定稿：日期行 + 三模块聚合卡 + 轻列表 ── */
.pt-date { padding: 26rpx 6rpx 20rpx; font-size: 30rpx; font-weight: 500; color: #6B7280; }
.pt-card { background: #fff; border-radius: 24rpx; box-shadow: 0 2rpx 6rpx rgba(31,41,55,.05), 0 10rpx 26rpx rgba(31,41,55,.07); padding: 8rpx 30rpx; }
.pt-sec { padding: 30rpx 0; border-top: 2rpx solid #EFF1F4; cursor: pointer; }
.pt-sec:first-child { border-top: 0; }
.pt-sec:active { background: #FAFBFC; }
/* 模块名标签已删（0731 用户定三改）：标题自明归属，行内直接主行起头 */
.pt-sec-row { display: flex; align-items: flex-start; gap: 16rpx; }
.pt-sec-main { flex: 1; min-width: 0; }
.pt-sec-title { font-size: 36rpx; font-weight: 750; color: #1F2937; line-height: 1.35; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }   /* 长标题（如培训名）窄屏截断不溢出 */
.pt-sec-sub { margin-top: 8rpx; font-size: 27rpx; color: #6B7280; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
/* 状态三级（规范§四）：逾期=暖胶囊、今日/明日=接待绿、进行中=会议蓝 */
.pt-badge { flex-shrink: 0; margin-top: 6rpx; font-size: 26rpx; font-weight: 600; }
.pt-badge.st-warn { padding: 6rpx 16rpx; border-radius: 8rpx; color: #9A5B12; background: #F7E4C6; }
.pt-badge.st-green { color: #2f6b45; font-weight: 700; }
.pt-badge.st-blue { color: #2f5f9e; font-weight: 700; }
/* 轻列表（spec §5；0731 设计师定：行压矮、去粗——"轻"列表不与白卡标题抢重量，和卡片更连贯 */
.pt-links { margin-top: 20rpx; padding: 0 6rpx; }
.pt-link { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; min-height: 76rpx; border-top: 2rpx solid #E2E5EA; cursor: pointer; }
.pt-link:last-child { border-bottom: 2rpx solid #E2E5EA; }
.pt-link:active { opacity: .65; }
.pt-link-t { font-size: 31rpx; font-weight: 400; color: #1F2937; }
.pt-link-t em { font-style: normal; font-weight: 500; color: #6B7280; font-size: 28rpx; }
.pt-arr { flex-shrink: 0; display: inline-block; width: 14rpx; height: 14rpx; border-right: 3rpx solid #B4BCC7; border-bottom: 3rpx solid #B4BCC7; transform: rotate(-45deg); }
/* 头部两行（0729 用户定）：第一行 日期+问候，第二行 最近任务摘要 */
.welcome-hero { flex-shrink: 0; padding: 16rpx 10rpx 0; }
/* 0730 用户定：头行收小一档，重心让给卡片正文 */
.welcome-line1 { font-size: 34rpx; font-weight: 700; color: #2F3D56; line-height: 1.3; }
.welcome-line2 { margin-top: 8rpx; font-size: 28rpx; font-weight: 500; color: #6F7C91; }
.welcome-foot { margin-top: auto; text-align: center; padding: 8rpx 0 6rpx; font-size: 25rpx; color: #AEB6C2; letter-spacing: 1rpx; }
/* 任务大卡（0729 用户定）：三栏合一，左类别+翻页、中任务、右按钮 */
.ck-board { background: #fff; border-radius: 26rpx; padding: 6rpx 26rpx; box-shadow: 0 2rpx 6rpx rgba(20,33,61,.05), 0 12rpx 26rpx rgba(20,33,61,.08); }
/* 标签行在上、任务行在下；上下留白加大，三类任务分得开 */
.ck-board-row { display: block; padding: 32rpx 0 36rpx; border-bottom: 2rpx solid #F0F2F5; }
.ck-board-row:last-child { border-bottom: 0; }
.ck-board-head { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-bottom: 16rpx; }
.ck-board-body { display: flex; align-items: center; gap: 20rpx; }
.ck-board-tag { font-size: 26rpx; font-weight: 700; padding: 6rpx 18rpx; border-radius: 999rpx; white-space: nowrap; }
.ck-board-tag.blue { color: #3A5E92; background: #E6EDF8; }
.ck-board-tag.green { color: #3B7150; background: #E4F0E8; }
.ck-board-tag.amber { color: #2a6b73; background: #DDEBEC; }   /* 学习＝模块深青（规范） */
.ck-board-pager { display: flex; align-items: center; gap: 8rpx; font-size: 25rpx; color: #8A94A6; font-variant-numeric: tabular-nums; }
.ck-board-pager button { border: 0; background: #F2F4F7; color: #5A6473; width: 48rpx; height: 48rpx; border-radius: 10rpx; font-size: 30rpx; line-height: 1; display: inline-flex; align-items: center; justify-content: center; }
.ck-board-pager button:active { background: #E4E8ED; }
.ck-board-main { flex: 1; min-width: 0; }
/* 0730 用户定：卡片正文加大加重（页面重心） */
.ck-board-title { font-size: 37rpx; font-weight: 800; color: #232B3C; line-height: 1.35; }
.ck-board-sub { margin-top: 8rpx; font-size: 29rpx; font-weight: 500; color: #7E8899; line-height: 1.4; }
.ck-board-empty { font-size: 29rpx; color: #9AA3AD; }
.ck-board-acts { flex-shrink: 0; display: flex; flex-direction: column; align-items: stretch; gap: 8rpx; }
.ck-board-cta { min-height: 64rpx; padding: 0 24rpx; border: 0; border-radius: 12rpx; background: #A85800; color: #fff; font-size: 29rpx; font-weight: 700; white-space: nowrap; }
.ck-board-cta:active { background: #8F4A06; }
.ck-board-secondary { border: 0; background: none; color: #A85800; font-size: 26rpx; font-weight: 600; padding: 2rpx 4rpx; }
.ck-board-secondary.danger { color: #B0463A; }
.ck-board-secondary:active { opacity: .6; }
/* 工作板块底栏（0729 用户定）：四项固定页底，图标块沿用各板块色系 */
.ck-dock { position: fixed; left: 0; right: 0; bottom: 0; z-index: 40; display: flex; background: #fff; border-top: 2rpx solid #E6EAEF; padding: 12rpx 8rpx calc(10rpx + env(safe-area-inset-bottom)); box-shadow: 0 -6rpx 18rpx rgba(24,51,76,.06); }
.ck-dock-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6rpx; padding: 6rpx 0; }
.ck-dock-item:active { opacity: .65; }
.ck-dock-ico { width: 72rpx; height: 72rpx; border-radius: 18rpx; display: flex; align-items: center; justify-content: center; }
.ck-dock-ico svg { width: 42rpx; height: 42rpx; }
.ck-dock-ico.blue { color: #3A5E92; background: #E6EDF8; }
.ck-dock-ico.green { color: #3B7150; background: #E4F0E8; }
.ck-dock-ico.amber { color: #2a6b73; background: #DDEBEC; }
.ck-dock-label { font-size: 27rpx; color: #4A5560; font-weight: 600; }
/* 档案馆入口（0730 设计师定）：板块下方一条中性灰白卡，弱于工作板块、独立于四色 */
.ck-archive { display: flex; align-items: center; gap: 18rpx; margin: 6rpx 4rpx 0; padding: 22rpx 26rpx; background: #fff; border-radius: 20rpx; box-shadow: 0 2rpx 6rpx rgba(20,33,61,0.05), 0 10rpx 24rpx rgba(20,33,61,0.06); cursor: pointer; }
.ck-archive:active { background: #f6f7f9; }
.ck-archive-ico { flex-shrink: 0; width: 64rpx; height: 64rpx; border-radius: 16rpx; background: #EDEFF2; color: #4A5560; display: flex; align-items: center; justify-content: center; }
.ck-archive-ico svg { width: 34rpx; height: 34rpx; }
.ck-archive-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.ck-archive-title { font-size: 31rpx; font-weight: 700; color: #2F3D56; }
.ck-archive-sub { font-size: 25rpx; color: #8A94A6; }
.ck-archive-arrow { flex-shrink: 0; font-size: 34rpx; color: #B4BCC7; }
.ck-section { margin-top: 34rpx; }
.welcome-hero + .ck-section { margin-top: 22rpx; }
.ck-work-section { margin-top: 48rpx; }
.ck-sec-title { font-size: 29rpx; font-weight: 700; color: #6B7686; letter-spacing: 1rpx; margin: 0 8rpx 18rpx; }
/* 0729 用户定：三卡整体缩小一档（内距/字号收紧），加类别标签 */
.ck-todo { position: relative; display: block; background: #fff; border-radius: 26rpx; padding: 26rpx 26rpx 30rpx 34rpx; margin-bottom: 20rpx; box-shadow: 0 2rpx 6rpx rgba(20,33,61,0.05), 0 12rpx 26rpx rgba(20,33,61,0.08); overflow: hidden; }
.ck-todo:last-child { margin-bottom: 0; }
.ck-todo::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 12rpx; }
.ck-todo.blue::before { background: #3E6BA8; }
.ck-todo.green::before { background: #3F7C5A; }
.ck-todo.amber::before { background: #3f8189; }   /* 学习培训卡：青色条（规范学习深青系） */
.ck-todo.blue:not(.ck-todo-complete) { padding-right: 224rpx; }
.ck-todo.blue:not(.ck-todo-complete) .ck-todo-title,
.ck-todo.blue:not(.ck-todo-complete) .ck-todo-sub { max-width: 430rpx; }
/* 头部(放弃草稿/翻页)不受右侧按钮预留 224rpx 约束，用负边距贴回右上角（0729：删除会议已移走，这里只剩草稿撤销/翻页） */
.ck-todo.blue:not(.ck-todo-complete) .ck-todo-head { margin-right: -198rpx; }
/* 该卡同时有「放弃草稿」和翻页时，让翻页回到流内与之并排（否则绝对定位的翻页会压在放弃草稿上） */
.ck-todo.blue:not(.ck-todo-complete) .ck-todo-pager { position: static; top: auto; right: auto; }
.ck-todo.blue:not(.ck-todo-complete) .ck-todo-cta {
  position: absolute;
  right: 26rpx;
  top: 50%;
  width: 164rpx;
  min-height: 60rpx;
  padding: 0 14rpx;
  transform: translateY(-50%);
  font-size: 28rpx;
}
.ck-todo.blue:not(.ck-todo-complete) .ck-todo-cta:active { transform: translateY(calc(-50% + 2rpx)); }
.ck-todo-complete { padding-top: 22rpx; padding-bottom: 22rpx; background: #F8FBF9; box-shadow: 0 2rpx 5rpx rgba(20,33,61,.035), 0 8rpx 20rpx rgba(20,33,61,.05); }
.ck-todo-complete::before { background: #68A27C !important; }
.ck-todo-complete .ck-todo-tag { color: #39704E !important; background: #E4F1E8 !important; }
.ck-todo-complete .ck-todo-title { margin-top: 10rpx; font-size: 34rpx; }
.ck-todo-complete .ck-todo-foot { margin-top: 9rpx; }
.ck-todo-complete .ck-todo-sub { color: #668170; font-size: 25rpx; }
.ck-reception-todo { padding-right: 224rpx; }
.ck-reception-todo .ck-todo-title { max-width: 430rpx; font-size: 32rpx; }
.ck-reception-todo .ck-todo-sub {
  display: -webkit-box;
  max-width: 320rpx;
  overflow: hidden;
  color: #748477;
  font-size: 24rpx;
  line-height: 1.42;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.ck-reception-actions {
  position: absolute;
  right: 30rpx;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 34rpx;
  width: 174rpx;
}
.ck-reception-actions .ck-reception-secondary,
.ck-reception-actions .ck-reception-cta {
  width: 100%;
  min-height: 60rpx;
  padding: 0 14rpx;
  border: 0;
  border-radius: 13rpx;
  background: #A85800;   /* 统一深橙（0729 用户定） */
  color: #fff;
  box-shadow: 0 4rpx 10rpx rgba(168, 88, 0, .14);
  font-size: 28rpx;
  font-weight: 700;
  white-space: nowrap;
}
.ck-reception-actions .ck-reception-secondary:active,
.ck-reception-actions .ck-reception-cta:active { background: #8F4A06; color: #fff; }
.ck-reception-actions .ck-reception-cta i { margin-left: 3rpx; font-size: 27rpx; }
.ck-reception-cta { background: #A85800; box-shadow: 0 6rpx 14rpx rgba(168,88,0,.16); }
/* head 不占高度、不定位:其中的分页/删除各自已绝对定位相对整张卡浮在右上角，
   head 归零后标题不再被压低。若父容器定位，分页会以 head 为参照被压成竖排（已避免）。 */
.ck-todo-head { display: flex; align-items: center; justify-content: flex-end; min-height: 0; }
/* 业委会卡:高度随内容自适应。原 min-height:270rpx 把只有「标题+一行副标题」的卡
   (内容仅约196rpx)硬撑高、再 justify-content:center 把多出的空档平摊到上下,
   就是大片无意义空白的来源,已去掉。右侧「去安排」按钮绝对定位垂直居中,天然与文字块对齐。 */
.ck-todo.blue:not(.ck-todo-complete) { display: flex; flex-direction: column; }
.ck-todo-head-actions { display: inline-flex; align-items: center; gap: 20rpx; }
.ck-todo-tag { display: inline-block; flex-shrink: 0; font-size: 22rpx; font-weight: 700; padding: 5rpx 16rpx; border-radius: 999rpx; }
.ck-todo.blue .ck-todo-tag { color: #3A5E92; background: #E6EDF8; }
.ck-todo.green .ck-todo-tag { color: #3B7150; background: #E4F0E8; }
.ck-todo.amber .ck-todo-tag { color: #2a6b73; background: #DDEBEC; }
.ck-todo-title { margin-top: 8rpx; font-size: 34rpx; font-weight: 800; color: #2A3244; line-height: 1.3; text-wrap: balance; }
.ck-todo-foot { display: flex; align-items: center; justify-content: space-between; gap: 24rpx; margin-top: 14rpx; }
.ck-todo-sub { min-width: 0; font-size: 25rpx; color: #8A94A6; line-height: 1.38; text-wrap: balance; }
/* 按钮统一深橙（0729 用户定：原蓝卡深蓝/接待绿/去查看各一色太杂太深，全部收敛到品牌深橙） */
.ck-todo-cta { flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; min-height: 60rpx; padding: 0 22rpx; border: 0; border-radius: 14rpx; background: #A85800; color: #fff; font-size: 27rpx; font-weight: 700; white-space: nowrap; box-shadow: 0 6rpx 14rpx rgba(168, 88, 0, .16); }
.ck-todo-cta:active { background: #8F4A06; }
.ck-todo-cta:active { transform: translateY(2rpx); filter: brightness(.96); }
.ck-todo-cta i { margin-left: 5rpx; font-style: normal; font-size: 33rpx; line-height: 1; }
.ck-todo-delete { min-height: 42rpx; padding: 0; border: 0; background: transparent; color: #956B6B; font-size: 24rpx; font-weight: 500; transform: translate(7rpx, -4rpx); }
.ck-todo-delete:active { color: #C0392B; }
.ck-todo-pager { position: absolute; top: 20rpx; right: 26rpx; display: inline-flex; align-items: center; gap: 12rpx; color: #7B8799; }
.ck-todo-pager button { min-height: 42rpx; padding: 0; border: 0; background: transparent; color: #64758D; font-size: 22rpx; font-weight: 500; }
.ck-todo-pager button:active { color: #3E6BA8; }
.ck-todo-pager span { min-width: 44rpx; text-align: center; font-size: 21rpx; color: #9AA4B3; font-variant-numeric: tabular-nums; }
.ck-calm { display: flex; align-items: center; gap: 24rpx; background: #EAF4EE; border: 2rpx solid #CDE6D6; border-radius: 26rpx; padding: 40rpx 34rpx; }
.ck-calm-ico { flex-shrink: 0; width: 76rpx; height: 76rpx; border-radius: 50%; background: #3B7150; color: #fff; font-size: 46rpx; font-weight: 800; display: flex; align-items: center; justify-content: center; }
.ck-calm-text { font-size: 33rpx; font-weight: 700; color: #2E6B47; line-height: 1.42; }
.ck-lines { display: flex; flex-direction: column; gap: 20rpx; }
.ck-line { position: relative; display: flex; align-items: center; gap: 22rpx; min-height: 116rpx; background: #fff; border-radius: 22rpx; padding: 20rpx 26rpx 20rpx 38rpx; box-shadow: 0 2rpx 5rpx rgba(20,33,61,0.04), 0 9rpx 22rpx rgba(20,33,61,0.055); overflow: hidden; cursor: pointer; box-sizing: border-box; }
.ck-line::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 12rpx; }
.ck-line.blue::before { background: #3E6BA8; }
.ck-line.green::before { background: #3F7C5A; }
.ck-line.amber::before { background: #4e8f98; }
.ck-line:active { transform: translateY(2rpx); }
.ck-line-ico { flex-shrink: 0; width: 82rpx; height: 82rpx; border-radius: 21rpx; display: flex; align-items: center; justify-content: center; font-size: 43rpx; font-weight: 800; }
.ck-line-ico.blue { background: #E6EDF8; color: #3A5E92; }
.ck-line-ico.green { background: #E4F0E8; color: #3B7150; }
.ck-line-ico.amber { background: #DDEBEC; color: #2a6b73; }
.ck-line-info { flex: 1; min-width: 0; }
.ck-line-title { font-size: 32rpx; font-weight: 750; color: #2A3244; }
.ck-line-detail { margin-top: 8rpx; font-size: 25rpx; font-weight: 500; color: #708078; line-height: 1.35; }
.ck-line-desc { margin-top: 8rpx; font-size: 28rpx; color: #8A94A6; line-height: 1.35; }
.ck-chip { flex-shrink: 0; font-size: 23rpx; font-weight: 700; padding: 6rpx 18rpx; border-radius: 999rpx; }
.ck-chip.active { color: #2E5A6E; background: #E4EEF2; }
.ck-chip.urgent { color: #B4482F; background: #F7E7E2; }
.ck-chip.calm { color: #3B7150; background: #E7F2EB; }
.ck-line-enter { flex-shrink: 0; font-size: 34rpx; color: #C1C7D0; }
/* 会议工作页：驾驶舱管提醒，这里按“近期安排 / 后续计划 / 已完成”组织，避免重复主卡。 */
/* 「会议安排」卡头已整体删除（0730 用户定点3），相关 .meeting-plan-head/.meeting-year-summary 样式随之移除 */
.mr-list { margin: 0; padding: 0 28rpx 12rpx; }
.mr-group-title { padding: 26rpx 4rpx 14rpx; color: #53657A; font-size: 28rpx; font-weight: 750; letter-spacing: 1rpx; }
.mr-group-plan { padding-top: 24rpx; padding-bottom: 6rpx; border-top: 2rpx solid #EEF1F4; color: #8792A0; font-size: 23rpx; font-weight: 600; }
.mr-row { display: flex; align-items: center; gap: 14rpx; min-height: 116rpx; padding: 20rpx 8rpx; border-bottom: 2rpx solid #F1F3F5; cursor: pointer; box-sizing: border-box; }
.mr-row:last-child { border-bottom: none; }
.mr-row:active { background: #F7F9FB; }
/* 顶栏「返回驾驶舱」胶囊（0730 图一骨架）：白描边适配深色顶栏 */
.hd-cockpit { flex-shrink: 0; align-self: center; display: inline-flex; align-items: center; justify-content: center; min-height: 56rpx; padding: 0 26rpx; border: 2rpx solid rgba(255,255,255,.55); border-radius: 999rpx; background: rgba(255,255,255,.08); color: #fff; font-size: 26rpx; font-weight: 500; }
.hd-cockpit:active { background: rgba(255,255,255,.2); }

/* ── 图一骨架（0730 用户定）：主卡 + 接下来 + 底部动作条 ── */
/* 主卡横滑（0730 三改）：scroll-snap 逐页吸附，多张待处理左右滑切换 */
/* 卡中卡修复（0730，点5）：会议 tab 的外层 plan-calendar-card 去壳，内层卡直接落在页面底上 */
.plan-calendar-card.mtg-flat { border: 0; background: transparent; box-shadow: none; overflow: visible; }
/* 卡头删除后，会议 tab 列表直接起头：给点上间距，别贴着深色顶栏 */
.mr-list { padding: 16rpx 4rpx 0; }
/* 待召开卡（0730 五改）：竖排列表；状态标签与选中态分离 */
.mtg-due-card { margin-top: 4rpx; margin-bottom: 8rpx; padding: 0 22rpx; border: 2rpx solid #D6E2EC; border-radius: 22rpx; background: #fff; box-shadow: 0 9rpx 22rpx rgba(34,62,84,.06); overflow: hidden; }
/* 待召开卡放大（0730 用户+设计师定，加对地方）：行内距 26→34rpx(两行更松)；会议名(本行最重要
   信息)31→34rpx；标题↔副行间距 7→10rpx；日期块 94→108rpx / range 132→152rpx，字号同步加大 */
.mtg-due-row { position: relative; display: flex; align-items: center; gap: 22rpx; padding: 34rpx 6rpx; border-top: 2rpx solid #F0F2F5; cursor: pointer; }
.mtg-due-row:first-child { border-top: 0; }
.mtg-due-row:active { background: #F6F9FC; }
.mtg-due-row .mr-row-title { font-size: 34rpx; }
.mtg-due-row .mr-row-sub { margin-top: 10rpx; }
.mtg-due-row .mr-badge { width: 108rpx; min-height: 90rpx; }
.mtg-due-row .mr-badge b { font-size: 33rpx; }
.mtg-due-row .mr-badge span { font-size: 25rpx; }
.mtg-due-row .mr-badge.range { width: 152rpx; min-height: 64rpx; }
.mtg-due-row .mr-badge.range b { font-size: 26rpx; }
/* 选中态（点2/3）：整行浅蓝底 + 左侧竖条，不用 ✓ 框 */
/* 「选中」浅蓝底 + 左竖条已删（0730 用户定）：逾期暖色胶囊已标出要补救的那场，
   底部主按钮副行又点名了是哪场，这条高亮属重复噪音，去掉让卡片更干净 */
/* 常驻状态标签（点2/4）：无边框、浅底文字，明显不是按钮 */
/* 状态字三级样式（0730 设计师定，全 app 通用，见 dueStatusTier）：
   ① 逾期＝暖色胶囊(有底)，一屏唯一异常；② 今日/进行中＝蓝字；③ 其余＝灰字。
   常态「待召开」绝不给底色，否则异常(逾期)就不显眼了 */
.mtg-due-status { flex-shrink: 0; align-self: center; padding: 0; font-size: 25rpx; font-weight: 500; white-space: nowrap; }
.mtg-due-status.st-warn { padding: 6rpx 16rpx; border-radius: 8rpx; color: #9A5B12; background: #F7E4C6; font-weight: 600; }
.mtg-due-status.st-today { color: #2F5F9E; }
.mtg-due-status.st-muted { color: #6B7280; }
/* 文字两档制（0731 用户定收敛）：正文黑 #1F2937 / 次要灰 #6B7280，中间灰全部归档——
   节标题=灰档加粗；列表正文=黑；层级靠字号字重，不靠灰阶渐变 */
.mtg-next-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin: 0 8rpx; min-height: 84rpx; font-size: 30rpx; font-weight: 700; color: #6B7280; }
.mtg-next-head em { font-style: normal; font-size: 26rpx; font-weight: 500; color: #8A94A6; }
/* 接下来白卡（0730 二改）：行内分隔线；行满不透明（原 mr-planned 淡化不适用于卡内） */
/* 0730 图样六改（点1）：去白卡去边框——次要清单直接铺在页面底色上，只留分隔线，
   和上方待召开白卡拉开视觉层级 */
.mtg-next-list { padding: 0 8rpx; }
/* 首行去掉顶分隔线：有「接下来」头时头已作分隔，无头时（无后续场次）也不留孤零零一条线 */
.mtg-next-list > :first-child { border-top: 0 !important; }
.mtg-next-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; min-height: 96rpx; border-top: 2rpx solid #E7EBEF; }
.mtg-next-line { flex: 1; min-width: 0; font-size: 29rpx; color: #1F2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
/* 「还有 N 场 ›」截断展开行（0730 设计师定）：与「全年会议」同为页内揭示、非跳转，用深灰；
   › 示意可展开更多。前 2 场恒在，展开后本行消失 */
.mtg-next-more-row { cursor: pointer; }
.mtg-next-more-row:active { opacity: .6; }
.mtg-next-line.more { font-weight: 600; color: #4B5563; }
.mtg-next-more-arr { flex-shrink: 0; font-size: 34rpx; color: #9AA4B0; }
/* 状态签（点2 + 设计师三级③）：无底灰字，灰＝#6B7280；「计划中/待确认」都是常态 */
.mtg-next-chip { flex-shrink: 0; min-height: 88rpx; padding: 0 4rpx 0 20rpx; border: 0; background: transparent; color: #6B7280; font-size: 26rpx; font-weight: 500; white-space: nowrap; }
.mtg-next-chip:active { opacity: .55; }
.mtg-next-foot { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; min-height: 108rpx; border-top: 2rpx solid #E7EBEF; cursor: pointer; }
.mtg-next-foot:active { opacity: .7; }
/* 0731 用户定：全年会议/档案馆两行加大两号、颜色更明显（29→33rpx、灰→正文深色），行高随之加大方便点按；
   展开▾/› 辅助符仍灰。仍不用蓝——展开行非跳转，蓝只留给链接类 */
/* 尾行 33→31rpx（两档制收敛）：让「主卡会议名34 > 列表行 > 尾行31」重量顺序回正 */
.mtg-next-foot b { font-size: 31rpx; font-weight: 650; color: #1F2937; }
.mtg-next-more { display: inline-flex; align-items: center; gap: 8rpx; font-size: 27rpx; color: #8A94A6; }
/* 纯图形展开按钮（0731 用户定二改：去文字）：圆形浅底+CSS 边框箭头（见 CLAUDE.md 配方），
   向下=可展开、向上=可收起；档案馆行的 › 保持裸箭头不套壳 */
.mtg-fold-btn { flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; width: 68rpx; height: 68rpx; border-radius: 50%; background: #EEF2F7; }
.mtg-next-foot:active .mtg-fold-btn { background: #E2E8EF; }
.mfb-chev { display: inline-block; width: 16rpx; height: 16rpx; border-right: 4rpx solid #4A5B70; border-bottom: 4rpx solid #4A5B70; transform: rotate(45deg); position: relative; top: -4rpx; transition: transform .2s ease, top .2s ease; }
.mfb-chev.open { transform: rotate(-135deg); top: 4rpx; }
.mfb-chev.right { transform: rotate(-45deg); top: 0; left: -3rpx; }   /* 右指＝跳转（档案馆行），与展开钮同款仅换向 */
/* 底部动作条（0730 点4四改，回到 fixed）：flex sticky-footer 在这套嵌套下没能真正撑满，
   按钮仍浮在页面中间、下方一大片空白——索性回到最稳的 position:fixed，钉死在底栏
   （TabBar≈102rpx）上沿。白底，与底栏共用一整片白色背景（TabBar 在 /main 去掉顶部描边+
   阴影来配合，见 TabBar.vue .tabbar.merged），中间只留一条小缝。z-index 90 < 底栏 100，
   重叠的几像素落在底栏空白内边距里，白叠白无缝。 */
/* gap 16rpx＝8px（0730 用户定）：两钮原来 12rpx 贴太近、像一个大按钮，拉开到 8px 才是两块 */
.mtg-actionbar { position: fixed; left: 0; right: 0; bottom: calc(98rpx + env(safe-area-inset-bottom)); z-index: 90; display: flex; flex-direction: column; gap: 16rpx; padding: 12rpx 24rpx 16rpx; background: #fff; box-shadow: 0 -10rpx 24rpx rgba(20,42,58,.06); }
/* 次级「＋ 发起临时会议」（0730 点2三改）：浅蓝底、叠在主按钮上方，主次分明 */
/* 次级按钮压到 72rpx(≈46px，0730 用户+设计师定)：比主按钮矮一档，主按钮独占饱和实心；
   仍保浅蓝底(规范§7「次按钮浅底」)，不改白底描边 */
.mtg-secondary { min-height: 72rpx; border: 0; border-radius: 18rpx; background: #EAF0F7; color: #3E6BA8; font-size: 28rpx; font-weight: 600; display: flex; align-items: center; justify-content: center; }
.mtg-secondary:active { background: #DCE6F1; }
.mtg-primary { width: 100%; min-height: 100rpx; border: 0; border-radius: 20rpx; background: #3E6BA8; color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2rpx; box-sizing: border-box; }
.mtg-primary:active { background: #35608F; }
.mtg-primary-sub { font-size: 24rpx; opacity: .85; line-height: 1.3; }
.mtg-primary-main { font-size: 33rpx; font-weight: 800; line-height: 1.3; }
/* 会议 tab 内容区给动作条+底栏让位 */
/* 动作条回到 fixed 且叠成两钮：底部要清开「固定动作条(约218rpx) + 底栏(约102rpx)」两层 */
.home.has-mtg-bar { padding-bottom: calc(330rpx + env(safe-area-inset-bottom)); }

/* 0730 移动习惯重排：卡改纵排（信息行+通宽大按钮），全卡可点 */
.mr-featured { position: relative; display: block; margin: 0 0 24rpx; padding: 24rpx 22rpx 22rpx; border: 2rpx solid #D6E2EC; border-radius: 22rpx; background: #F8FBFD; box-shadow: 0 9rpx 22rpx rgba(34,62,84,.08); overflow: hidden; cursor: pointer; }
.mr-featured:active { background: #F1F7FB; }
.mr-feat-main { display: flex; align-items: center; gap: 26rpx; }
.mr-cta-wide { display: block; width: 100%; margin-top: 22rpx; min-height: 88rpx; border: 0; border-radius: 16rpx; background: #3E6BA8; color: #fff; font-size: 32rpx; font-weight: 700; }
.mr-cta-wide:active { background: #35608F; }
.mr-featured::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 9rpx; background: #4B77A9; }
.mr-featured:has(.mr-badge.overdue)::before { background: #C75B4B; }
.mr-featured:active { background: #F0F5F8; }
.mr-planned { min-height: 88rpx; padding: 12rpx 6rpx; gap: 16rpx; opacity: .78; cursor: default; }
.mr-planned:active { background: transparent; }   /* 整行不可点,不给按压反馈 */
/* 计划行右侧改真按钮:描边胶囊,比原纯文字「待排›」更大更明显(0725 用户定) */
.mr-plan-btn { flex-shrink: 0; min-height: 76rpx; padding: 0 28rpx; border: 2rpx solid #B9C6D4; border-radius: 999rpx; background: #fff; color: #4E6076; font-size: 28rpx; font-weight: 650; white-space: nowrap; }
.mr-plan-btn:active { background: #EEF1F5; }
/* 待处理/已完成行的右侧真按钮(0725 用户定:整行不可点,只按钮进入),描边胶囊按状态配色 */
.mr-cta-btn { flex-shrink: 0; min-height: 62rpx; padding: 0 18rpx; border: 2rpx solid #B9C6D4; border-radius: 999rpx; background: #fff; color: #4E6076; font-size: 26rpx; font-weight: 650; white-space: nowrap; }
.mr-cta-btn:active { background: #F3F5F7; }
.mr-cta-btn.overdue { color: #B0463A; border-color: #DFA79F; }
.mr-cta-btn.current { color: #345F91; border-color: #AFC3DC; }
.mr-cta-btn.upcoming { color: #345F91; border-color: #AFC3DC; }
.mr-cta-btn.done { color: #2E7D50; border-color: #A8CDB6; }
/* 整行不再可点:不给按压反馈 */
.mr-featured:active { background: #F8FBFD; }
.mr-cal-done-row { cursor: default; }
.mr-cal-done-row:active { background: transparent; }
.mr-planned .mr-badge { width: 78rpx; min-height: 70rpx; border-radius: 14rpx; }
.mr-planned .mr-badge b { font-size: 25rpx; }
.mr-planned .mr-badge span { font-size: 20rpx; }
.mr-planned .mr-row-title { font-size: 27rpx; font-weight: 650; }
.mr-planned .mr-row-sub { margin-top: 3rpx; font-size: 23rpx; }
.mr-planned .mr-status { font-size: 23rpx; font-weight: 500; }
.mr-badge { flex-shrink: 0; width: 94rpx; min-height: 82rpx; border-radius: 16rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2rpx; box-sizing: border-box; padding: 8rpx 4rpx; }
.mr-badge b { font-size: 29rpx; font-weight: 800; line-height: 1.1; white-space: nowrap; }
.mr-badge span { font-size: 22rpx; }
/* 期次区间(没定具体日期)徽标:横排胶囊「9-10月」。日历叶(日上月下)只留给有确切日期的会议——
   参照主流日历/出行类App:区间不伪装成日期。定宽 132rpx 使各行标题左缘对齐(容得下「11-12月」)。 */
.mr-badge.range, .mr-planned .mr-badge.range { width: 132rpx; min-height: 56rpx; padding: 6rpx 8rpx; border-radius: 999rpx; flex-direction: row; }
.mr-badge.range b, .mr-planned .mr-badge.range b { font-size: 23rpx; letter-spacing: 0; }
/* 待处理大卡里的期次胶囊稍放大,与 33rpx 标题的比例协调(后续计划小行仍用小号) */
.mr-featured .mr-badge.range { min-height: 62rpx; }
.mr-featured .mr-badge.range b { font-size: 25rpx; }
/* 日期块配套三级（0730 设计师定，全 app 通用）：只有逾期那场用暖底，其余（已完成/进行中/待召开）
   一律灰底——和状态字同一套「一屏唯一暖色」逻辑，扫一眼就能锁定要补救的那场 */
.mr-badge.overdue { background: #FDF4E8; color: #7A4A10; }
.mr-badge.done, .mr-badge.current, .mr-badge.upcoming { background: #F4F6F9; color: #4B5563; }
.mr-info { flex: 1; min-width: 0; }
.mr-row-title { font-size: 31rpx; font-weight: 750; color: var(--c-text-strong); line-height: 1.3; text-wrap: pretty; }  /* 0729:文字区加宽让例会名尽量一行;真折行时 pretty 填满行宽+末行不留孤字(旧 balance 会把两行均分,右侧空一大截离按钮很远) */
.mr-row-sub { font-size: 27rpx; color: #6B7280; margin-top: 7rpx; line-height: 1.35; text-wrap: balance; }  /* 兜底:真折行时两行均衡,不出孤字 */
.mr-sub-seg { display: inline-block; max-width: 100%; }   /* 段内(日期时间/地点)不拆,只在「·」处折行 */
.mr-sub-seg i { font-style: normal; }
.mr-status { flex-shrink: 0; font-size: 28rpx; font-weight: 650; }
.mr-status.done { color: #2E7D50; }
.mr-status.current { color: #345F91; }
.mr-status.overdue { color: #B0463A; }
.mr-status.upcoming { color: #8A94A0; }
.mr-fold { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; min-height: 100rpx; padding: 18rpx 8rpx; margin-top: 10rpx; font-size: 30rpx; font-weight: 600; color: #1F2937; border-top: 2rpx solid #E8ECEF; cursor: pointer; }
.mr-fold:active { background: #F6F8FA; }
.mr-fold:active { opacity: 0.7; }
.mr-fold-chev { transition: transform 0.2s; }
.mr-fold-chev.open { transform: rotate(180deg); }
/* 「查看」组(0727 用户定):后续计划 + 全年会议一览 与上方动作区(待处理卡 + 发起其他会议)拉开一段
   明显更大的间距,让「开会」和「查看」读成两块。组内首行不再叠加 10rpx 行距,组间距全由本容器承担。 */
.mr-lookup { margin-top: 24rpx; }   /* 点3：与待召开卡的间距 80→24，视觉总距约 20px，不再像断开 */
.mr-lookup > .mr-fold:first-child { margin-top: 0; }
/* 全年月历弹层(0725):原地展开在列表底部看不全,改浮层居中,看完即关 */
/* 弹窗样式(mr-cal-mask/sheet/close)已删(0725):月历改原地展开 .mr-calendar-panel */
.mr-calendar-panel { margin: 4rpx 0 24rpx; padding: 22rpx 20rpx; border: 2rpx solid #DCE5EE; border-radius: 18rpx; background: #F7F9FC; scroll-margin-top: 20rpx; }
/* 弹层内「已完成」清单:宫格下方的档案区,与宫格用分隔线区隔 */
.mr-cal-done-title { margin-top: 26rpx; padding-top: 22rpx; border-top: 2rpx solid #EEF1F4; color: #53657A; font-size: 27rpx; font-weight: 700; }
.mr-cal-done-row { padding-left: 2rpx; padding-right: 2rpx; }
.mr-cal-done-row .mr-row-sub { display: none; }   /* 每行都是同一句"可查看会议记录",抽屉里省掉,行更紧凑 */
.mr-calendar-panel-title { display: flex; align-items: baseline; justify-content: space-between; gap: 12rpx; padding: 0 2rpx 18rpx; color: #34465C; font-size: 30rpx; font-weight: 700; }
.mr-calendar-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12rpx; }
.mr-calendar-month { min-height: 86rpx; padding: 9rpx 4rpx; border: 0; border-radius: 13rpx; background: #EEF1F4; color: #627083; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5rpx; }
.mr-calendar-month b { font-size: 26rpx; line-height: 1.1; }
.mr-calendar-month span { font-size: 21rpx; line-height: 1.1; }
.mr-calendar-month.done { background: #EDF6F0; color: #397356; }
.mr-calendar-month.overdue { background: #FAEBE8; color: #A94B40; }
.mr-calendar-month.current { background: #EAF0F7; color: #3B6593; }
.mr-calendar-month:active { filter: brightness(.96); }
.meet-card { margin: 14rpx 24rpx 14rpx; background: var(--c-bg-card); border-radius: 22rpx; padding: 26rpx 26rpx 22rpx; box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.05); }
/* .meet-collapsed / .mc-ico / .mc-text / .mc-act / .meet-collapse-chip / .meet-collapse-foot
   全删（0717 用户定：会议进行中那一栏撤掉，接待日安排顶上）。
   它们是「接待/培训 tab 上把会议卡收起/展开」这套交互的全部样式，交互没了样式即死代码。
   连带作废的还有 13px 字号那两处例外（收起条的「查看▾」和卡内「收起▴」）——
   全站 <14px 的豁免清单因此少两条，只剩接待/培训清单日期那一处。
   原实现见 commit 592d7be 及之前。 */
.meet-tag { font-size: 30rpx; color: var(--c-primary-dark); font-weight: 600; }
.meet-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; }
/* 卡标题＝状态（0716 用户定）。标题字号（32rpx/800）+ 胶囊底（0716 追加：绿色胶囊背景）——
   三档同款胶囊、各自语义色，只给「进行中」穿另两档裸着会不一致。teal 档用户明确保过。 */
.meet-card-title { display: inline-flex; align-items: center; font-size: 32rpx; font-weight: 800; line-height: 1.2; padding: 8rpx 24rpx; border-radius: 999rpx; }
.meet-card-title.preparing { color: #9A5A00; background: #FFF4E5; border: 2rpx solid #F2C786; }
.meet-card-title.ongoing { color: #0F766E; background: #E7F6F3; border: 2rpx solid #B9E4DC; }
.meet-card-title.ended { color: #5F6B7A; background: #EEF1F4; border: 2rpx solid #D9DEE5; }
.meet-title { display: block; font-size: 33rpx; font-weight: 700; color: var(--c-text-strong); margin-top: 12rpx; line-height: 1.35; word-break: break-word; }
/* 灰底/边框撤销（0716 用户定）：时间地点退成素文字行，与全页「只给可点的东西上色块」一致 */
.meet-info { display: flex; align-items: center; gap: 14rpx; margin-top: 12rpx; padding: 0; }
.meet-info-item { min-width: 0; font-size: 28rpx; color: var(--c-text-mid); line-height: 1.35; }
.meet-info-item.location { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meet-info-sep { flex-shrink: 0; width: 2rpx; height: 28rpx; background: #DDE2E6; }
.meet-meta { display: block; font-size: 30rpx; color: var(--c-text-mid); margin-top: 16rpx; }
/* 三步进度 */
.steps { display: flex; align-items: flex-start; justify-content: space-between; margin: 26rpx 8rpx 22rpx; }
.step { display: flex; flex-direction: column; align-items: center; width: 120rpx; }
.step-dot { width: 42rpx; height: 42rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 700; background: #E3E5E9; color: var(--c-text-weak); }
.step-label { font-size: 22rpx; color: var(--c-text-weak); margin-top: 6rpx; }
.step.done .step-dot { background: var(--c-primary-soft); color: var(--c-primary-dark); }
.step.done .step-label { color: var(--c-text-mid); }
.step.active .step-dot { background: var(--c-primary-dark); color: #fff; }
.step.active .step-label { color: var(--c-text-strong); font-weight: 700; }
.step-line { flex: 1; height: 6rpx; border-radius: 3rpx; margin-top: 21rpx; }
.step-line.done { background: var(--c-primary); }
.step-line.todo { background: #E3E5E9; }
/* 大按钮（方案④ 浅橙卡包实心钮：外层浅橙框 + 内层深橙实心白字）*/
/* 会前公告提示：准备阶段会议卡上，提醒主任提前7天向业主公告议程 */
.pre-notice-tip { margin:14rpx 0 4rpx; padding:16rpx 20rpx; background:#FFF6E9; border:2rpx solid #F0D9AE; border-radius:14rpx; color:#8A5A1B; font-size:26rpx; line-height:1.5; }
.big-btn { display:block; padding: 10rpx; border-radius: 26rpx; background: var(--c-primary-soft); margin-top: 4rpx; box-sizing: border-box; box-shadow: 0 12rpx 84rpx 12rpx rgba(232, 140, 20, 0.26); text-decoration:none; }
.big-btn-inner { display: flex; align-items: center; justify-content: center; height: 88rpx; border-radius: 16rpx; background: var(--c-primary); }
.big-btn:active .big-btn-inner { background: var(--c-primary-strong); }
.big-btn-ico { font-size: 36rpx; margin-right: 12rpx; }
.big-btn-text { font-size: 33rpx; font-weight: 700; color: #fff; }
/* 去开会主按钮：缩窄并居中（比卡片按钮收得更多，两者看起来差不多宽） */
/* 有草稿时本按钮退为次要：整体缩小、收窄、弱化光晕，把视觉重心让给草稿卡的「继续通知」 */
.big-btn.minor { width: 60%; padding: 12rpx; box-shadow: 0 6rpx 30rpx 4rpx rgba(232, 140, 20, 0.16); }
.big-btn.minor .big-btn-inner { height: 84rpx; }
.big-btn.minor .big-btn-ico { font-size: 34rpx; margin-right: 10rpx; }
.big-btn.minor .big-btn-text { font-size: 34rpx; }
/* 卡片内"去开会"：略收窄并居中；光晕收敛（大弥散光晕留给底部灰底上的独立按钮，白卡里会外溢显脏） */
.meet-card .big-btn { width: 71%; margin-left: auto; margin-right: auto; box-shadow: 0 6rpx 22rpx rgba(232, 140, 20, 0.18); }
/* 删除会议（测试用，弱化） */
.meet-del { text-align: center; color: var(--c-danger); font-size: 27rpx; margin-top: 16rpx; padding: 4rpx; }
.meet-del:active { opacity: 0.6; }

/* 待发送草稿卡：放大成首页主角，橙调、看得见的"没写完的会议"，底部整行大按钮=继续通知 */
.draft-card { width: 94%; margin: 8rpx auto 20rpx; box-sizing: border-box; background: var(--c-bg-card, #fff); border: 2rpx solid rgba(232,140,20,0.4); border-left: 14rpx solid var(--c-primary); border-radius: 28rpx; padding: 30rpx 32rpx 34rpx; box-shadow: 0 10rpx 34rpx rgba(232,140,20,0.18); }
.draft-card-top { display: flex; align-items: center; margin-bottom: 16rpx; }
.draft-badge { font-size: 30rpx; font-weight: 700; color: var(--c-primary-strong, #c96a12); background: var(--c-primary-soft, #fdf0e0); padding: 8rpx 20rpx; border-radius: 999rpx; }
.draft-discard { display: block; text-align: center; margin: 16rpx auto 0; padding: 8rpx; font-size: 26rpx; color: var(--c-danger); font-weight: 500; }
.draft-discard:active { opacity: 0.6; }
.draft-title { font-size: 46rpx; font-weight: 700; color: #1f2329; line-height: 1.3; word-break: break-all; }
.draft-summary { font-size: 30rpx; color: #6b7075; margin-top: 10rpx; }
.draft-mat { font-size: 28rpx; color: #6b7075; margin-top: 10rpx; }
.draft-continue { width: 80%; margin: 26rpx auto 0; height: 104rpx; border: none; border-radius: 24rpx; background: var(--c-primary); color: #fff; font-size: 42rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; box-shadow: 0 8rpx 26rpx rgba(232,140,20,0.28); }
/* 「发起其他会议」收进会议安排卡尾(0725 用户定):中性灰虚线的次要"添加"语义不变 */
/* 0730 移动习惯：靶子加大（≥96rpx），宽度放到 78% */
.create-misc-entry { width: 78%; margin: 26rpx auto 14rpx; height: 96rpx; display: flex; align-items: center; justify-content: center; text-align: center; color: var(--c-text-mid); font-size: 32rpx; font-weight: 600; border: 2rpx dashed #C9D0D6; border-radius: 22rpx; background: #F5F6F8; cursor: pointer; }
.create-misc-entry:active { background: #EAEDF0; }
.draft-continue:active { background: var(--c-primary-strong); transform: scale(0.99); }
.draft-continue .btn-arrow { margin-left: 6rpx; font-size: 44rpx; }
/* 空闲态 */
.idle { margin: 64rpx 24rpx 0; display: flex; flex-direction: column; align-items: center; }
.idle-hint { font-size: 38rpx; color: var(--c-text-mid); margin-bottom: 8rpx; }
/* 「更多功能」三格已删（0709）：接待/培训入口移入计划卡横栏 */

/* 综合评分小字（占位分数）：贴近顶栏、紧凑 */

/* 今年会议计划：首页前置总览，竖向时间轴——一条主线贯全年，节点亮灭即进度 */
.plan-card { margin: 0 24rpx 22rpx; background: var(--c-bg-card); border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); overflow: hidden; }
.plan-stack { display: flex; flex-direction: column; gap: 14rpx; margin: 0 24rpx 20rpx; }
.plan-stack:not(.compact) { margin-top: 20rpx; }
.plan-stack.reception-mode { margin-top: 20rpx; }
.plan-stack.compact { gap: 12rpx; }
/* 首页有会议卡时：待办事项 + 履职年历整体缩小一档，与已缩小的会议卡协调 */
.plan-stack.has-meeting { gap: 18rpx; }
.plan-stack.has-meeting .yc-list.plan-todo-card { padding: 16rpx 24rpx 18rpx; }
.plan-stack.has-meeting .plan-todo-card .yc-list-head { font-size: 32rpx; padding-bottom: 4rpx; }
.plan-stack.has-meeting .plan-todo-card .yc-item { gap: 12rpx; padding: 8rpx 4rpx; }
/* 接待/培训列表行：不吃开会档的极限压缩，保留舒适行高 */
.plan-stack.has-meeting .plan-todo-card .yc-item.todo-plain { padding: 20rpx 4rpx; }
.plan-stack.has-meeting .plan-todo-card .yc-item-title { font-size: 31rpx; }
/* 24rpx→28rpx（12px→14px，0716）：接待清单的日期（6月24日…）老人要核对，不能比正文还小 */
.plan-stack.has-meeting .plan-todo-card .yc-item-sub { font-size: 28rpx; margin-top: 4rpx; }
.plan-stack.has-meeting .plan-todo-card .plan-badge { min-width: 128rpx; font-size: 30rpx; padding: 15rpx 22rpx; }
/* 25rpx→28rpx（12.5px→14px，0716）：「查看」是个要用手指点的按钮，字比正文还小最说不过去 */
.plan-stack.has-meeting .plan-todo-card .yc-item.todo-plain .plan-badge { min-width: 116rpx; font-size: 28rpx; padding: 12rpx 18rpx; }
.plan-stack.has-meeting .plan-title { font-size: 36rpx; }
/* 有会时日历紧凑（0716 用户定：年历收一收、给会议卡让位并加大间距，保证卡完整露出）。
   只压 has-meeting 态；无会时日历是主角，保持宽松版。 */
.plan-stack.has-meeting .yc-period-grid { gap: 14rpx; padding: 10rpx 14rpx 8rpx; }
.plan-stack.has-meeting .yc-cell.pair-cell { min-height: 92rpx; }
.plan-stack.has-meeting .plan-calendar-card .plan-head { padding: 14rpx 20rpx 8rpx; }
.plan-stack.has-meeting .yc-period-feedback { margin-top: 4rpx; padding: 4rpx 45rpx 6rpx; }
.plan-stack.has-meeting .yc-cell { padding: 8rpx 0 7rpx; }
.plan-stack.has-meeting .yc-cell.pair-cell .yc-m { font-size: 27rpx; }
.plan-switch-card, .plan-calendar-card, .plan-todo-card { background: var(--c-bg-card); border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); box-sizing: border-box; overflow: hidden; }
.plan-switch-card { padding: 10rpx; order: 0; }
/* 接待/培训(.compact)的纵向顺序：分段栏0 → 三数字1 → 登记大按钮2 → 待办清单3 → 全年日历4。
   三数字必须排在清单之前：它兼作清单的筛选器（ovFilter），排在被筛列表下方 350px 处的话，
   点了数字变化发生在视野之外，老人只会觉得「点了没反应」。
   登记卡卡在三数字与清单之间 = 工作流顺序（看概览 → 登记新来访 → 处理清单）。
   开会 tab 走下面的 :not(.compact) 覆盖，不受这里影响。 */
.plan-todo-card { margin: 0; order: 4; }
/* 日历恒在最后（compact 态）。开会 tab 走下面 :not(.compact) 的 order:2 覆盖，不受影响；
   培训 tab 也是 compact 但没有接待日那张卡，中间空一档不影响顺序 */
.plan-calendar-card { padding-top: 0; order: 5; }
/* 开会 tab 且无进行中会议：日历上移当第一重点、待办下沉（0716 用户定）。
   仅此态对调；接待/培训(.compact)与有会议(.has-meeting)保持原顺序。 */
.plan-stack:not(.compact) .plan-calendar-card { order: 2; }
/* margin-top 22rpx + 栈 gap 18rpx ≈ 20px：日历与会议卡拉开（0716 用户定），只动这一对，
   不动全局 gap——tab栏↔日历的间距不该跟着变 */
.plan-stack:not(.compact) .meet-card { order: 3; margin-top: 22rpx; }
/* 待办卡与上方日历/会议卡拉开呼吸空隙（0716 用户定，间隔约为卡间 gap 的两倍） */
.plan-stack:not(.compact) .plan-todo-card { order: 4; margin-top: 16rpx; }
/* 会议卡挪进 plan-stack 后：横向靠容器 24rpx 边距、纵向靠容器 gap，自身边距清零防双重缩进 */
.plan-stack .meet-card { margin: 0; }
/* （折叠头已删 0716：日历恒展开） */
/* 履职年历：12月宫格 + 警示条 + 当月清单。
   前缀 yc-（year calendar）：cal- 已被下方日期选择弹窗的小日历占用，同名会被其 7 列网格覆盖 */
/* 方案A：接待概览三数字（本月/待跟进/年度） */
.ov-metrics { display: flex; gap: 18rpx; padding: 26rpx 22rpx 28rpx; }
/* 独立成行版：移出日历卡。order 2→1（0716）：它是下方待办清单的筛选器（ovFilter），
   必须在清单之前。原先只顾着「排在日历上方」，没注意同时也掉到了待办清单的下方。
   1→2（0717）：接待日安排插到 1（原会议进行中收起栏的位置）。筛选器仍在被筛清单之前，不违反上面那条。 */
.ov-metrics-standalone { order: 2; background: var(--c-bg-card); border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); box-sizing: border-box; padding: 22rpx 20rpx; }

/* 接待页按真实使用频率分级：通知维护最醒目，登记来访其次，处理清单随后。 */
/* 接待页整体转绿色系（0730 用户定：接待=绿，与底栏/驾驶舱一致） */
.rec-notice-hero { order: 1; box-sizing: border-box; padding: 34rpx;
  background: linear-gradient(145deg, #FDFFFE 0%, #EFF7F1 100%);
  border: 2rpx solid #D3E4D9; border-radius: 26rpx; box-shadow: 0 10rpx 26rpx rgba(40,96,64,0.08); }
.rec-notice-hero-head { display: flex; align-items: flex-start; gap: 26rpx; }
.rnh-copy { flex: 1; min-width: 0; }
.rnh-kicker { font-size: 37rpx; line-height: 1.35; font-weight: 650; color: #3B7150; }
/* 顶行（0730 设计师定稿）：统一灰字「M月D日 周四」，语气只落在标题 */
.rnh-top { display: flex; align-items: baseline; justify-content: space-between; gap: 12rpx; }
.rnh-date { font-size: 30rpx; font-weight: 500; color: #6B7280; }
.rnh-title { margin-top: 12rpx; font-size: 43rpx; line-height: 1.3; font-weight: 700; color: var(--c-text-strong); }
.rnh-time { margin-top: 12rpx; font-size: 43rpx; line-height: 1.35; font-weight: 650;
  color: var(--c-text-strong); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rnh-time.none { color: #9A3412; }
.rnh-place { margin-top: 10rpx; overflow: hidden; display: -webkit-box; -webkit-box-orient: vertical;
  -webkit-line-clamp: 2; font-size: 34rpx; line-height: 1.45; color: var(--c-text-mid); }
.rnh-place-name { display: inline-block; max-width: 100%; }  /* 地名整体折行,不从中间掰断 */
/* 白底+深描边+投影(0725 用户定:原奶油底和卡片底融在一起,不像按钮) */
.rec-notice-primary { display: block; width: 60%; height: 84rpx; margin: 29rpx auto 0;
  border: 2rpx solid #9CC0AA; border-radius: 18rpx; background: #fff;
  color: #2F6647; font-size: 32rpx; font-weight: 650; letter-spacing: normal;
  box-shadow: 0 4rpx 12rpx rgba(47, 102, 71, 0.12); }
.rec-notice-primary:active { background: #EAF5EE; }
.rec-notice-primary:active { opacity: 0.76; }
/* 与学习页「新增学习记录」同款(0725 用户定):短20%居中、配色减淡、外圈柔光 */
.rec-register-card { order: 2; display: flex; align-items: center; gap: 16rpx; width: 80%; box-sizing: border-box;
  margin: 57rpx auto 0; padding: 20rpx 24rpx; text-align: left;
  background: linear-gradient(135deg, #FDFFFE 0%, #EFF7F1 100%); border: 2rpx solid #D3E4D9;
  border-radius: 20rpx; color: inherit;
  box-shadow: 0 6rpx 18rpx rgba(40, 96, 64, 0.10); }
.rrc-icon { display: flex; align-items: center; justify-content: center; width: 60rpx; height: 60rpx;
  border-radius: 15rpx; background: #4C8062; color: #fff; font-size: 34rpx; font-weight: 500; }
.rrc-copy { flex: 1; display: flex; flex-direction: column; gap: 3rpx; }
.rrc-when { font-size: 25rpx; font-weight: 500; color: #6E8578; font-style: normal; }   /* 对象行：今晚/明晚/下次·日期（随接待日切换） */
.rrc-copy strong { font-size: 34rpx; line-height: 1.35; color: #2F6647; font-weight: 650; }
.rrc-arrow { display: flex; align-items: center; justify-content: center; width: 50rpx; height: 50rpx;
  border-radius: 50%; background: #E4F0E8; color: #3B7150; font-size: 36rpx; font-weight: 700; }
.rec-register-card:active { opacity: 0.7; }
/* 底部动作区（0731 修正）：fixed 钉死在底栏上沿——放文档流(order/sticky)时内容不足一屏
   会浮在半空、与底栏脱开，会议页/接待页都踩过，见 CLAUDE.md「底部主操作条」约定。
   白底全宽与底栏连成整片（TabBar 在 /reception-center 加 .merged 去顶描边） */
.rec-actions { position: fixed; left: 0; right: 0; bottom: calc(98rpx + env(safe-area-inset-bottom)); z-index: 90; display: flex; flex-direction: column; gap: 16rpx; padding: 14rpx 24rpx 16rpx; background: #fff; box-shadow: 0 -10rpx 24rpx rgba(20,42,58,.06); }
/* 固定动作区(两钮约224rpx)+底栏(约102rpx)两层让位，内容不被挡 */
.home.has-rec-bar { padding-bottom: calc(340rpx + env(safe-area-inset-bottom)); }
/* 0731 设计师定：间距压到 8px(16rpx)、次级矮一档——对齐会议页既定规格（mtg-secondary 72 / mtg-primary 100），主次不只靠颜色 */
.rec-adjust-btn { min-height: 72rpx; border: 0; border-radius: 16rpx; background: #E4F0E8; color: #2F6647; font-size: 28rpx; font-weight: 600; }
.rec-adjust-btn:active { background: #D6E9DD; }
.rec-register-btn { min-height: 100rpx; border: 0; border-radius: 16rpx; background: #2f6b45; color: #fff; display: flex; align-items: center; justify-content: center; }
.rec-register-btn:active { background: #285f3d; }
.rrb-main { font-size: 33rpx; font-weight: 700; }
/* 接待 tab：待办移到近期接待上方（图一顺序 hero→待办→近期→档案→动作区） */
.reception-mode .plan-todo-card { order: 2; }
/* 近期接待轻列表化（0730 图一/spec §5）：只需知晓的记录＝透明底+分隔线，不再套白卡 */
.rec-recent-card { order: 3; margin-top: 26rpx; padding: 0 8rpx; box-sizing: border-box;
  background: transparent; border: 0; box-shadow: none; }
.rec-recent-head { display: flex; align-items: center; justify-content: space-between;
  min-height: 84rpx; padding: 0 2rpx; font-size: 30rpx; font-weight: 700; color: #6B7280; }   /* 两档制：节标题=灰档加粗 */
/* 末行档案馆入口（0730 定稿图）：「档案馆」深色粗、说明灰 */
.rec-recent-arch { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; min-height: 96rpx; border-top: 2rpx solid #E2E5EA; cursor: pointer; }
.rec-recent-arch:active { opacity: .65; }
.rra-text { font-size: 28rpx; font-weight: 500; color: #6B7280; }
.rra-text b { font-weight: 700; color: #1F2937; }
.rec-recent-arch-arr { flex-shrink: 0; font-size: 32rpx; color: #9AA4B0; }
.rec-recent-session { border-top: 2rpx solid #E2E5EA; }
.rec-recent-row { display: flex; align-items: center; gap: 18rpx; min-height: 112rpx; padding: 20rpx 2rpx; }
.rec-recent-row:active { opacity: 0.68; }
.rec-recent-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
/* 行式照图一：主行「M月D日 · 接待人」，副行「反映 N 项/无人来访」；状态标签已撤（spec §11） */
.rec-recent-main { font-size: 31rpx; line-height: 1.35; font-weight: 650; color: #1F2937;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rec-recent-sub { font-size: 27rpx; line-height: 1.4; color: #6B7280; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
/* 右侧箭头：CSS 边框画（CLAUDE.md），收起右指›、展开子列表时转下 */
.rec-recent-chev { flex-shrink: 0; display: inline-block; width: 14rpx; height: 14rpx;
  border-right: 3rpx solid #B4BCC7; border-bottom: 3rpx solid #B4BCC7;
  transform: rotate(-45deg); transition: transform .2s ease; }
.rec-recent-chev.open { transform: rotate(45deg); }
.rec-recent-items { margin: -2rpx 0 14rpx 20rpx; padding-left: 20rpx; border-left: 4rpx solid #E8ECEE; }
.rec-recent-item { display: flex; align-items: center; gap: 18rpx; padding: 17rpx 2rpx; border-top: 2rpx solid #F0F2F3; }
.rec-recent-item > div { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.rec-recent-item strong { font-size: 28rpx; color: var(--c-text-strong); }
.rec-recent-item span { font-size: 26rpx; color: var(--c-text-weak); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rec-recent-item em { flex-shrink: 0; color: var(--c-primary-dark); font-size: 27rpx; font-style: normal; }
.rec-recent-empty { padding: 22rpx 0 26rpx; border-top: 2rpx solid #EEF1F3;
  text-align: center; font-size: 27rpx; color: var(--c-text-weak); }
/* 待办入口卡（0730 设计师定稿）：白卡=要办的事；接待 tab 不再渲染 plan-todo-card，
   由这张入口卡进「业委会待办」聚合页（/minutes-todos，多会议+接待合流） */
.rec-todo-entry { order: 2; margin-top: 26rpx; padding: 28rpx 30rpx; background: #fff; border-radius: 22rpx;
  box-shadow: 0 2rpx 6rpx rgba(20,33,61,.04), 0 10rpx 24rpx rgba(20,33,61,.07); cursor: pointer; }
.rec-todo-entry:active { background: #F7F9FB; }
.rte-top { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; }
.rte-top b { font-size: 32rpx; font-weight: 700; color: #1F2937; }
/* 右箭头：CSS 边框画（CLAUDE.md），示意可进入 */
.rte-arr { flex-shrink: 0; display: inline-block; width: 14rpx; height: 14rpx;
  border-right: 3rpx solid #B4BCC7; border-bottom: 3rpx solid #B4BCC7; transform: rotate(-45deg); }
.rte-sub { margin-top: 12rpx; font-size: 27rpx; color: #6B7280; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.plan-stack.reception-mode .plan-todo-card .plan-badge.view { cursor: pointer; }
.plan-stack.reception-mode .plan-todo-card.empty-compact { padding: 0 24rpx; }
.plan-stack.reception-mode .plan-todo-card.empty-compact .yc-list-head { display: none; }
.plan-stack.reception-mode .plan-todo-card.empty-compact .plan-empty {
  padding: 24rpx 0; font-size: 28rpx; line-height: 1.4; color: var(--c-text-weak);
}

/* 接待日安排入口卡。order 4→1（0717 用户定）：接下原「会议进行中」收起栏的位置，
   即 tab 栏正下方、三数字概览之上。
   放这儿讲得通：它不是动作而是这个 tab 的前提事实——「我们的接待时间是几点」，
   下面的登记/待跟进全都围着它转，当页头比夹在清单和日历中间合适。
   仍然只描边不填色：位置越靠前越要压分量，否则会盖过「登记接待」那颗主动作。 */
/* 0717 用户定：整卡加大约 30%（纵向 padding 22→30）、字体各加一号（30→32/28→30）、
   行距和行间距同步放宽（1.3→1.4、6→12、gap 16→20）——增强呼吸感，老人一眼能看清。 */
/* .rec-notice-card/.rnc-* 死样式已删（0723）：模板改用 .rec-notice-hero 后零引用 */
.ov-metric { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; padding: 24rpx 8rpx; border-radius: 18rpx; background: #F6F7F9; cursor: pointer; }
.ov-metric:active { opacity: 0.8; }
.ov-metric.on { box-shadow: inset 0 0 0 4rpx #D97706; }
.ov-num { font-size: 50rpx; font-weight: 800; color: var(--c-text-strong); line-height: 1; }
/* 24rpx→28rpx（12px→14px，0716）：开会 tab 已无一个低于 14px 的字，这两个 tab 原有 6~10 个 */
.ov-label { font-size: 28rpx; color: var(--c-text-weak); font-weight: 600; }
/* 36→32rpx（0716 用户定：小一号）。has-meeting 那条 .plan-title 36rpx 特异性更高会反杀，补一条压住 */
.plan-title.ov-title, .plan-stack.has-meeting .plan-title.ov-title { font-size: 32rpx; font-weight: 500; }
.ov-metric.warn { background: #FFF7ED; }
.ov-metric.warn .ov-num { color: #D97706; }
.ov-metric.warn .ov-label { color: #B45309; }
.ov-metric.danger { background: #FFF4F2; }
.ov-metric.danger .ov-num { color: #D83A2E; }
.ov-metric.danger .ov-label { color: #B02A1E; }
.yc-alert-main { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; margin: 8rpx 20rpx 0; background: #FFF4F2; border: 2rpx solid #F0B8B1; border-left: 8rpx solid #D83A2E; border-radius: 16rpx; padding: 15rpx 16rpx 15rpx 16rpx; cursor: pointer; box-shadow: 0 6rpx 18rpx rgba(216,58,46,0.1); }
.yc-alert-main:active { opacity: 0.85; }
.yc-alert-main.warn { background: #FFF7EA; border-color: #F2CFA0; border-left-color: var(--c-primary); box-shadow: 0 8rpx 22rpx rgba(232,140,20,0.13); }
.yc-alert-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.yc-alert-kicker { font-size: 22rpx; font-weight: 800; color: #B02A1E; line-height: 1.2; }
.yc-alert-main.warn .yc-alert-kicker { color: var(--c-primary-dark); }
.yc-alert-title { font-size: 33rpx; font-weight: 800; color: #8F1F17; line-height: 1.2; word-break: break-all; }
.yc-alert-main.warn .yc-alert-title { color: var(--c-primary-strong); }
.yc-alert-sub { font-size: 23rpx; color: #81534F; line-height: 1.25; word-break: break-all; }
.yc-alert-main.warn .yc-alert-sub { color: #8A6428; }
.yc-alert-cta { flex-shrink: 0; align-self: stretch; display: flex; align-items: center; padding: 0 14rpx; border-radius: 12rpx; background: #D83A2E; color: #fff; font-size: 25rpx; font-weight: 800; }
.yc-alert-main.warn .yc-alert-cta { background: var(--c-primary); }
.yc-alert-main.secondary { margin-top: 8rpx; padding: 13rpx 16rpx; border-left-width: 6rpx; box-shadow: none; }
.yc-alert-main.outside { margin: -8rpx 24rpx 20rpx; }
.yc-alert-main.secondary .yc-alert-copy { gap: 2rpx; }
.yc-alert-main.secondary .yc-alert-kicker { font-size: 20rpx; }
.yc-alert-main.secondary .yc-alert-title { font-size: 27rpx; }
.yc-alert-main.secondary .yc-alert-sub { display: none; }
.yc-alert-main.secondary .yc-alert-cta { font-size: 23rpx; padding: 0 12rpx; }
.yc-alert-ok { margin: 8rpx 20rpx 0; display: flex; flex-direction: column; gap: 4rpx; background: var(--c-success-soft); border: 2rpx solid rgba(39,174,96,0.2); border-radius: 16rpx; padding: 14rpx 18rpx; }
.yc-alert-ok .yc-alert-kicker, .yc-alert-ok .yc-alert-title { color: var(--c-success); }
.yc-alert-ok .yc-alert-title { font-size: 30rpx; font-weight: 800; }
.yc-focus-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10rpx; padding: 12rpx 20rpx 4rpx; }
.yc-focus { min-width: 0; min-height: 102rpx; display: flex; flex-direction: column; justify-content: center; gap: 4rpx; padding: 12rpx 12rpx; border-radius: 14rpx; background: #F6F7F9; box-sizing: border-box; cursor: pointer; }
.yc-focus:active { opacity: 0.75; }
.yc-focus-label { font-size: 22rpx; font-weight: 700; color: var(--c-text-weak); line-height: 1.15; }
.yc-focus-title { font-size: 26rpx; font-weight: 800; color: var(--c-text-strong); line-height: 1.2; word-break: break-all; }
.yc-focus-sub { font-size: 21rpx; color: var(--c-text-weak); line-height: 1.2; word-break: break-all; }
.yc-focus.done { background: var(--c-success-soft); }
.yc-focus.done .yc-focus-title, .yc-focus.done .yc-focus-sub { color: var(--c-success); }
.yc-focus.current { background: var(--c-primary-soft); }
.yc-focus.current .yc-focus-title, .yc-focus.current .yc-focus-sub { color: var(--c-primary-dark); }
.yc-focus.overdue { background: #FDECEA; }
.yc-focus.overdue .yc-focus-title, .yc-focus.overdue .yc-focus-sub { color: #B02A1E; }
.yc-year-toggle { display: flex; align-items: center; justify-content: center; gap: 8rpx; margin: 8rpx 20rpx 0; height: 50rpx; border-radius: 12rpx; background: #F6F7F9; color: #176B87; font-size: 24rpx; font-weight: 700; cursor: pointer; }
.yc-year-toggle:active { opacity: 0.75; }
.yc-year-panel { padding-top: 4rpx; }
/* gap 10rpx → 24rpx（0716 A 案）：期次卡的边框撤掉后，「一期」全靠这道间距分组。
   要害是它与 .yc-pair-months 的 gap(7rpx) 拉开倍数——原先 10 vs 7 差 1.5px，邻近原则等于没用上。 */
.yc-period-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24rpx; padding: 16rpx 17rpx 14rpx; }
/* 接待/培训：平铺 12 月宫格（4 列 3 行） */
/* 图例（0716 用户定加回，且做大）。它在接待/培训是必需品而非装饰：宫格十二格里大半只有颜色、
   没有文字，颜色成了唯一载体。做到 32rpx(16px) 字 + 20rpx(10px) 圆点——先前开会 tab 那版是
   10px 字 + 6px 圆点，老人根本看不清，那正是它该被删的理由之一，不能在这儿重犯。
   圆点取格子的「文字色」而非「底色」：底色是 #F0FAF4 这类近白的淡色，做成 10px 圆点等于隐形。 */
.ov-legend { display: flex; flex-wrap: wrap; align-items: center; gap: 12rpx 30rpx; padding: 6rpx 20rpx 18rpx; color: var(--c-text-mid); font-size: 32rpx; font-weight: 500; }
.ov-legend span { display: inline-flex; align-items: center; gap: 10rpx; line-height: 1.2; }
.ov-lg-dot { flex-shrink: 0; width: 20rpx; height: 20rpx; border-radius: 50%; }
.ov-lg-dot.done { background: var(--c-success); }
.ov-lg-dot.warn { background: #B27407; }
.ov-lg-dot.overdue { background: #B02A1E; }
.ov-lg-dot.future { background: #71829A; }
/* 开会 tab 标题行右侧小图例（0723 用户定加回）：三色与格子状态色一一对应。
   小字定位是「颜色对照表」，真正的状态信息仍由格子里 30rpx 的「已开✓/待开/逾期」承担。 */
.yc-legend { display: inline-flex; align-items: center; gap: 20rpx; color: var(--c-text-mid); font-size: 24rpx; font-weight: 500; }
.yc-legend span { display: inline-flex; align-items: center; gap: 8rpx; line-height: 1.2; white-space: nowrap; }
.yc-lg-dot { flex-shrink: 0; width: 18rpx; height: 18rpx; border-radius: 50%; }
.yc-lg-dot.done { background: var(--c-success); }
.yc-lg-dot.current { background: #D97706; }
.yc-lg-dot.overdue { background: #D83A2E; }
/* 宫格做大（0716 用户定）：它默认收起、平时不占空间，展开就是给人细看的，没必要委屈 */
.yc-month-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14rpx; padding: 4rpx 17rpx 20rpx; }
.yc-month-grid .yc-cell { min-height: 136rpx; gap: 6rpx; }
/* 状态字是格子里唯一的信息（22rpx→30rpx = 15px），月份只是坐标 —— 与开会 tab 同一取向 */
.yc-month-grid .yc-m { font-size: 32rpx; }
.yc-month-grid .yc-s { font-size: 30rpx; }
.yc-action-card { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin: 12rpx 20rpx 2rpx; padding: 18rpx 18rpx 18rpx 20rpx; border-radius: 18rpx; background: #FFF7ED; border: 2rpx solid #FED7AA; box-shadow: 0 8rpx 20rpx rgba(217,119,6,0.1); cursor: pointer; }
.yc-action-card:active { opacity: 0.82; }
.yc-action-card.overdue { background: #FFF4F2; border-color: #F3C6C0; box-shadow: 0 8rpx 20rpx rgba(216,58,46,0.11); }
.yc-action-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5rpx; }
.yc-action-kicker { font-size: 23rpx; color: #B45309; font-weight: 800; line-height: 1.15; }
.yc-action-card.overdue .yc-action-kicker { color: #B02A1E; }
.yc-action-title { font-size: 33rpx; color: var(--c-text-strong); font-weight: 900; line-height: 1.18; word-break: break-all; }
.yc-action-btn { flex-shrink: 0; min-width: 128rpx; height: 62rpx; padding: 0 20rpx; border-radius: 999rpx; display: flex; align-items: center; justify-content: center; background: #D97706; color: #fff; font-size: 28rpx; font-weight: 900; box-shadow: 0 8rpx 16rpx rgba(217,119,6,0.22); box-sizing: border-box; }
.yc-action-card.overdue .yc-action-btn { background: #D83A2E; box-shadow: 0 8rpx 16rpx rgba(216,58,46,0.22); }
/* 期次卡的边框已撤（0716 用户定 A 案）。原先它和内部月格用的是同一个色（如 current 两边都是
   #FED7AA），隔 7rpx 画两遍，纯属重复；而本该扛分组的底色（期次卡底 vs 白卡底仅 1.03:1）和
   间距（期次间距 4.5px vs 同期两月 3.5px，差 1px）全是废的，结构 100% 压在这堆同色框上。
   现改为：「一期」靠 yc-period-grid 拉开的间距分组，「一个月」靠月格自己的边框+底色。3 层框 → 2 层框。
   底色保留：它虽扛不起分组，但和月格叠在一起能让整期透出淡淡的状态色。 */
.yc-period-card { position: relative; min-width: 0; padding: 7rpx; border-radius: 17rpx; background: #F8FAFB; box-sizing: border-box; }
.yc-period-card.done { background: #F6FCF8; }
/* 0716 色彩收敛：「待推进」蓝系→与待办事项同族的淡黄系，不再平白引入新颜色 */
.yc-period-card.current { background: #FFFBF3; }
.yc-period-card.overdue { background: #FFF4F2; }
/* .active 的阴影已撤（0716 A 案收尾）：边框拿掉后，就剩这层阴影还让选中的期次卡算「一层卡」，
   嵌套卡在 3 层下不来。而选中本来就有三重标记了——月格的 ::after 橙环 + ::before 圆点 + 自身阴影，
   期次卡这层纯属第四遍重复。撤掉后日历才真的是 白卡 > 月格 两层。 */
.yc-pair-months { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7rpx; }
.yc-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10rpx; padding: 12rpx 20rpx 6rpx; }
.yc-grid.compact { grid-template-columns: repeat(3, 1fr); gap: 12rpx; padding: 12rpx 20rpx 14rpx; }
.yc-cell { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2rpx; padding: 10rpx 0 9rpx; border-radius: 14rpx; background: #F8FAFB; border: 2rpx solid #EEF1F3; box-sizing: border-box; cursor: pointer; }
.yc-grid.compact .yc-cell { min-height: 94rpx; gap: 4rpx; padding: 15rpx 0 13rpx; border-radius: 16rpx; }
/* 图例删掉后腾出的高度还给格子（0716 用户定）：日历是首页第一重点，格子该撑起来。 */
.yc-cell.pair-cell { min-height: 104rpx; gap: 4rpx; padding: 12rpx 0 10rpx; border-radius: 14rpx; background: rgba(255,255,255,0.66); }
/* 选中反馈加强（0723 用户定，原细描边+小圆点太弱）：选中格直接用本状态色「实底+白字」，
   点到哪个月哪个月整块变实色，与未选中的浅色格形成强对比；状态含义不丢（实底仍是本状态色）。
   无状态色的月份（待排/普通月）用中性深灰蓝实底兜底。 */
.yc-cell.pair-cell.sel { background: #6B7A90; border-color: #6B7A90; box-shadow: 0 8rpx 20rpx rgba(20,42,58,0.22); }
.yc-cell.pair-cell.sel.done    { background: var(--c-success); border-color: var(--c-success); box-shadow: 0 8rpx 20rpx rgba(22,130,80,0.30); }
.yc-cell.pair-cell.sel.current { background: #D97706; border-color: #D97706; box-shadow: 0 8rpx 20rpx rgba(217,119,6,0.32); }
.yc-cell.pair-cell.sel.overdue { background: #D83A2E; border-color: #D83A2E; box-shadow: 0 8rpx 20rpx rgba(216,58,46,0.30); }
.yc-cell.pair-cell.sel .yc-m { color: #fff; font-weight: 700; }
.yc-cell.pair-cell { transition: background 0.15s, box-shadow 0.15s; }
/* 实底方案下橙环/圆点是重复标记，开会 tab 不再需要；接待/培训宫格仍用环+点 */
.yc-cell.pair-cell.sel::after, .yc-cell.pair-cell.sel::before { display: none; }
/* 0716 选中框：游离紫 #8B5CF6 → 主题深橙（--c-primary-dark，描边专用档）；全页去紫，只留品牌橙 */
.yc-cell.sel::after { content: ''; position: absolute; inset: -4rpx; border: 3rpx solid var(--c-primary-dark); border-radius: 18rpx; pointer-events: none; }
.yc-cell.sel::before { content: ''; position: absolute; top: 8rpx; right: 8rpx; width: 10rpx; height: 10rpx; border-radius: 50%; background: var(--c-primary-dark); box-shadow: 0 0 0 4rpx rgba(168,88,0,0.15); pointer-events: none; }
.yc-cell:active { opacity: 0.75; }
/* 加粗从坐标挪到信息上（0716 用户定）：月份只是坐标（1-12 顺序排，本来就好找），状态才是要看的东西。
   原先 12 个月份全 700、状态却是 400 —— 加粗加反了，且这 12 个占了全页加粗元素的 40%。 */
.yc-m { font-size: 28rpx; font-weight: 500; color: var(--c-text-strong); line-height: 1.1; }
.yc-s { font-size: 22rpx; font-weight: 600; color: var(--c-text-weak); line-height: 1.2; }
.yc-grid.compact .yc-m { font-size: 32rpx; }
.yc-grid.compact .yc-s { font-size: 22rpx; }
/* 开会 tab 格子只留月份（0723 用户定）：状态字整体去掉，颜色含义由右上角图例承担 */
.yc-cell.pair-cell .yc-m { font-size: 31rpx; }
.yc-cell.done { background: #F0FAF4; border-color: #D7EFDE; }
.yc-cell.done .yc-m, .yc-cell.done .yc-s { color: var(--c-success); }
.yc-cell.current { background: #FFF8EC; border-color: #FED7AA; }
.yc-cell.current .yc-m, .yc-cell.current .yc-s { color: #9A5A00; }
.yc-cell.overdue { background: #FDECEA; border-color: #F3C6C0; }
.yc-cell.overdue .yc-m, .yc-cell.overdue .yc-s { color: #B02A1E; }
/* 接待/培训宫格：有待办=黄，未到=蓝灰 */
.yc-cell.warn { background: #FFF6E5; border-color: #F7DDA0; }
.yc-cell.warn .yc-m, .yc-cell.warn .yc-s { color: #B27407; }
.yc-cell.future { background: #EEF2F7; border-color: #DAE1EA; }
.yc-cell.future .yc-m, .yc-cell.future .yc-s { color: #71829A; }
.yc-cell.sel { box-shadow: 0 8rpx 18rpx rgba(20,42,58,0.08); }
.yc-corner { position: absolute; top: -10rpx; right: -8rpx; min-width: 34rpx; height: 34rpx; padding: 0 8rpx; box-sizing: border-box; border-radius: 17rpx; background: var(--c-danger); color: #fff; font-size: 22rpx; font-weight: 700; line-height: 34rpx; text-align: center; }
.period-calendar { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8rpx; padding: 10rpx 20rpx 2rpx; }
.period-cell { min-height: 88rpx; display: flex; flex-direction: column; justify-content: center; gap: 2rpx; padding: 10rpx 12rpx; border-radius: 14rpx; background: #F6F7F9; border: 3rpx solid transparent; box-sizing: border-box; cursor: pointer; }
.period-cell.active { border-color: #176B87; }
.period-no { font-size: 20rpx; color: var(--c-text-weak); font-weight: 700; }
.period-month { font-size: 28rpx; color: var(--c-text-strong); font-weight: 800; line-height: 1.15; }
.period-status { font-size: 21rpx; color: var(--c-text-weak); line-height: 1.15; }
.period-cell.done { background: var(--c-success-soft); }
.period-cell.done .period-month, .period-cell.done .period-status { color: var(--c-success); }
.period-cell.current { background: var(--c-primary-soft); }
.period-cell.current .period-month, .period-cell.current .period-status { color: var(--c-primary-dark); }
.period-cell.overdue { background: #FDECEA; }
.period-cell.overdue .period-month, .period-cell.overdue .period-status { color: #B02A1E; }
.yc-list { margin: 0 20rpx 18rpx; padding: 14rpx 16rpx 12rpx; background: #F8FAFB; border-radius: 18rpx; border: 2rpx solid #EEF1F3; }
/* 0716：整张卡的淡黄底/黄边/橙阴影撤销（满屏淡黄的真正来源），继承基础白卡样式，与日历卡统一 */
.yc-list.plan-todo-card { margin: 0; padding: 12rpx 26rpx 18rpx; background: var(--c-bg-card); }
.yc-list-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; font-size: 28rpx; font-weight: 800; color: var(--c-text-strong); padding: 0 2rpx 8rpx; }
/* 卡标题加粗显眼（0716 用户定）：行内文本让位后，标题是这张卡唯一该重的东西 */
.plan-todo-card .yc-list-head { font-size: 36rpx; font-weight: 800; padding-bottom: 0; }
/* 0716：胶囊撤销，退成一句素文字。
   调研 Material 的判据：badge 是「叠在父元素上、标注导航项/图标」的通知符号，而这个是跟标题
   并排的普通计数，结构上就不是 badge；且「只有当精确数量会驱动下一步动作时才用数字」——
   委员是一条条点着处理的，是 4 是 5 不改变他做什么。卡片标题已写着「待跟进」、下面就摆着 4 行，
   这个数字不增加信息，却穿着最抢眼的衣服。Material 原话：什么都挂徽章，就没有徽章是重要的。 */
.yc-list-count { flex-shrink: 0; padding: 0; background: none; color: var(--c-text-weak);
  font-size: 28rpx; font-weight: 500; }
.yc-head-title { display: inline-flex; align-items: center; min-width: 0; }
/* .yc-list-count.active（实心橙 + todoPulse 无限脉动光环）已删（0716）：
   一个不能点、也不表示「有新东西」的计数，在那儿一直闪——正是 Material 说的通知疲劳。
   而且今天一整天在做的就是把呼吸动画从待办上摘掉（有会时 plan-badge 已 animation:none），
   这里却还留着一个。要提醒「有 4 件事」，靠的是卡标题和下面那 4 行，不是让数字发光。 */
.yc-item { display: flex; align-items: center; gap: 14rpx; padding: 13rpx 4rpx; border-top: 2rpx solid #EEF1F3; cursor: pointer; }
.plan-todo-card .yc-item { gap: 16rpx; padding: 6rpx 4rpx; }
.plan-todo-card .yc-item.current,
.plan-todo-card .yc-item.overdue { border-top: none; border-radius: 18rpx; padding: 14rpx 20rpx 16rpx; margin-top: 8rpx; border: 2rpx solid transparent; border-left-width: 10rpx; }
/* 卡片与卡片之间拉开（首卡贴标题保持紧凑）；:first-of-type 会被前面的标题 div 干扰，用相邻兄弟选择器 */
.plan-todo-card .yc-item + .yc-item.current,
.plan-todo-card .yc-item + .yc-item.overdue { margin-top: 20rpx; }
.plan-todo-card .yc-item.current { background: #FFFCF7; border-color: #FED7AA; border-left-color: #D97706; }
.plan-todo-card .yc-item.overdue { background: #FFFCFB; border-color: #F3C6C0; border-left-color: #D83A2E; }
/* 接待/培训待办：条数多，用轻列表（细分隔线，不套会议那种强调橙块），标题弱化、副标题单行省略、按钮收小，避免堆叠拥挤 */
.plan-todo-card .yc-item.todo-plain { padding: 30rpx 4rpx; }
.plan-todo-card .yc-item.todo-plain:first-of-type { border-top: none; }
/* 行文本 500→400、日期 28→26rpx 且再淡一档（0716 用户定）：标题重、行轻、日期最轻的三级层次。
   只动 todo-plain（接待/培训行），开会 tab 的「第N期例会」仍是 500 不受影响。
   ⚠ 日期 26rpx=13px，破了今天定的 14px 下限——它是行内第三级的辅助信息、且用户点名要缩，
   真机测过要是看不清再回 28rpx。 */
.plan-todo-card .yc-item.todo-plain .yc-item-title { font-weight: 400; }
.plan-todo-card .yc-item.todo-plain .yc-item-sub { font-size: 26rpx; color: #87929D; }
.plan-todo-card .yc-item.todo-plain .yc-item-sub { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.plan-todo-card .yc-item.todo-plain .plan-badge { min-width: 124rpx; font-size: 29rpx; padding: 15rpx 20rpx; }
/* 「查看」入口：不是实心操作按钮，而是浅橙描边+箭头，表示「点进去看详情」（跟进选项在详情页里） */
/* 描边 #FED7AA(1.35:1) → var(--c-primary)(3.63:1)：与「去通知」同一个病同一个方子——
   描边隐形时按钮只剩一行橙字，尤其现在列表里混着「已办结」灰绿标签，可点的必须一眼是按钮 */
.plan-todo-card .yc-item.todo-plain .plan-badge.view { background: #fff; color: #C2410C; border: 2rpx solid var(--c-primary); font-weight: 700; box-shadow: none; }
.plan-todo-card .yc-item.todo-plain .plan-badge.view::after { content: '›'; margin-left: 6rpx; }
.yc-item:active { opacity: 0.6; }
.yc-item-ico { flex-shrink: 0; font-size: 32rpx; }
.plan-todo-card .yc-item-ico { font-size: 38rpx; }
.yc-item-info { flex: 1; min-width: 0; }
.yc-item-title { font-size: 28rpx; font-weight: 600; color: var(--c-text-strong); line-height: 1.25; }
.yc-item-sub { font-size: 22rpx; color: var(--c-text-weak); margin-top: 3rpx; line-height: 1.25; }
/* 600 → 500（0716 用户定）：「第N期例会」是待办行的名字，不是要喊的东西——
   要看的是右边「去通知/去补开」那颗按钮。与 todo-plain 行的 500 也就此对齐。 */
.plan-todo-card .yc-item-title { font-size: 38rpx; font-weight: 500; }
.plan-todo-card .yc-item-sub { font-size: 28rpx; margin-top: 6rpx; }
.yc-links { display: flex; justify-content: center; gap: 48rpx; padding: 18rpx 0 4rpx; font-size: 26rpx; font-weight: 600; color: var(--c-primary-dark); }
.yc-links span:active { opacity: 0.6; }
.plan-empty { text-align: center; color: var(--c-text-weak); font-size: 28rpx; padding: 24rpx 0 24rpx; }
/* 日历下方反馈区：点选期次后就地展示（已开期记录 / 未到期提前准备）。记录行放宽有呼吸感 */
/* 0716：下钻区内容（提示语/会议记录）两侧统一缩进1.5字符（45rpx@30rpx），不顶卡片边；虚线分隔仍全宽 */
.yc-period-feedback { margin-top: 10rpx; border-top: 2rpx dashed #EFE7DA; padding: 8rpx 45rpx 8rpx; }
.ypf-head { font-size: 30rpx; font-weight: 700; color: var(--c-text-strong); padding: 4rpx 6rpx 12rpx; }
/* 已结束会议名旁的绿色「已完成」小标签（0716 用户定，替代被删的「X-X月开会记录」标题） */
.ypf-done-tag { display: inline-block; width: fit-content; margin-top: 6rpx; font-size: 22rpx; font-weight: 700; color: var(--c-success); background: var(--c-success-soft); padding: 3rpx 14rpx; border-radius: 999rpx; }
/* 常驻「当月状态」提示条（0716 调研定型：黑字裸排像正文、身份错位显怪；改 AntUI/支付宝式浅色底信息条，
   底色与宫格语义色同族——点黄格弹黄条、红格红条、灰格灰条，零新色 */
.ypf-tip { text-align: left; font-size: 29rpx; line-height: 1.55; padding: 8rpx 0 4rpx; }
.ypf-tip.warn { color: #9A5A00; }
.ypf-tip.overdue { color: #B02A1E; }
.ypf-tip.plain { color: var(--c-text-mid); }
.yc-period-feedback .yc-item:first-of-type { border-top: 0; }
.yc-period-feedback .yc-item { padding: 22rpx 0; gap: 18rpx; }
.yc-period-feedback .yc-item-title { font-size: 31rpx; line-height: 1.45; }
.yc-period-feedback .yc-item-sub { font-size: 26rpx; margin-top: 8rpx; line-height: 1.4; }
.yc-period-feedback .plan-badge { font-size: 26rpx; padding: 10rpx 20rpx; }
/* 「查看详情」小按钮：浅蓝底蓝字（0723 用户定，原白底橙描边）——查看类动作降为冷色，不与橙色主动作抢 */
.yc-period-feedback .plan-badge.ypf-view { background: #EFF6FF; color: #1B5FA8; border: 2rpx solid #BFD9F2; font-weight: 700; box-shadow: none; }
.yc-period-feedback .plan-badge.ypf-view::after { content: '›'; margin-left: 6rpx; }
/* 待办卡定位高亮：滚动到位后闪两下橙色提示 */
.plan-todo-card.flash { animation: todoFlash 0.9s ease 2; }
@keyframes todoFlash { 50% { background: #FFF1DC; box-shadow: 0 0 0 4rpx rgba(217,119,6,0.35); } }
/* .yc-year-nav / .yc-year-label 已删（0716）：「2026年」升为卡标题走 .plan-title，右上角年份标签撤销 */
/* 0716：标题/图例/宫格三段间距放宽，呼吸感 */
.plan-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; padding: 18rpx 20rpx 12rpx; }
/* 接待/培训：整条卡头即折叠开关（0716）。收起态下这张卡就只剩这一行，正是「放在底部不起眼处」的形态。 */
.plan-head.foldable { cursor: pointer; padding: 22rpx 20rpx; }
.plan-head.foldable:active { opacity: 0.7; }
.ov-fold-chev { display: inline-flex; align-items: center; justify-content: center; width: 52rpx; height: 52rpx; color: var(--c-text-mid); font-size: 30rpx; line-height: 1; transition: transform 0.2s ease; }
.ov-fold-chev.open { transform: rotate(180deg); }
/* 概览（接待/培训）标题与卡片顶部再留出一点距离；仅 compact 态生效，不动开会年历 */
.plan-stack.compact .plan-head { padding-top: 26rpx; }
/* 概览卡与上方待办卡、下方各再拉开一点间距（仅 compact 态）*/
.plan-stack.compact .plan-calendar-card { margin-top: 10rpx; margin-bottom: 17rpx; }
.plan-title-wrap { display: flex; align-items: center; gap: 12rpx; min-width: 0; }
.plan-title { font-size: 40rpx; font-weight: 800; color: var(--c-text-strong); line-height: 1.12; }
.plan-year { padding: 4rpx 12rpx; border-radius: 999rpx; background: #EAF6FF; color: #0284C7; font-size: 22rpx; font-weight: 800; line-height: 1.2; }
.plan-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10rpx; flex-shrink: 0; }
.plan-tip { font-size: 24rpx; color: var(--c-text-weak); }
/* 蓝 #0284C7 → 主色橙（0716 色彩收敛）：开会 tab 已零蓝，这里是橙色应用里最后的孤立蓝之一 */
.plan-tip.link { color: var(--c-primary); font-weight: 700; font-size: 28rpx; padding: 4rpx 6rpx; }
/* 时间轴：左侧 52rpx 轨道列（贯穿细线+节点圆点），右侧内容行。紧凑以免顶下方主按钮 */
.plan-timeline { padding: 2rpx 26rpx 10rpx; }
.tl-i { display: grid; grid-template-columns: 52rpx 1fr; gap: 18rpx; cursor: pointer; }
.tl-i:active { opacity: 0.55; }
.tl-rail { position: relative; display: flex; justify-content: center; }
.tl-rail::before { content: ''; position: absolute; top: 0; bottom: 0; width: 6rpx; background: var(--c-border); border-radius: 3rpx; }
.tl-i:first-child .tl-rail::before { top: 30rpx; }
.tl-i:last-child .tl-rail::before { bottom: calc(100% - 30rpx); }
.tl-node { position: relative; z-index: 1; width: 44rpx; height: 44rpx; margin-top: 8rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 700; color: #fff; background: var(--c-border); font-variant-numeric: tabular-nums; }
.tl-node.done { background: var(--c-success); }
.tl-node.current { background: var(--c-primary); box-shadow: 0 0 0 6rpx var(--c-primary-soft); }
.tl-node.overdue { background: #B02A1E; }
.tl-node.upcoming { background: var(--c-bg-card); border: 5rpx solid var(--c-border); color: var(--c-text-weak); }
.tl-body { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; min-width: 0; padding: 10rpx 0 16rpx; border-bottom: 2rpx solid #F1F2F4; }
.tl-i:last-child .tl-body { border-bottom: none; padding-bottom: 4rpx; }
.tl-info { min-width: 0; }
.tl-month { font-size: 30rpx; font-weight: 600; color: var(--c-text-strong); line-height: 1.2; }
.tl-sub { font-size: 24rpx; color: var(--c-text-weak); margin-top: 4rpx; line-height: 1.3; }
.tl-i.current .tl-month { color: var(--c-primary-dark); font-weight: 700; }
.tl-i.done .tl-sub { color: var(--c-success); }
.tl-i.overdue .tl-sub { color: #B02A1E; }
.plan-badge { flex-shrink: 0; font-size: 23rpx; font-weight: 700; padding: 7rpx 16rpx; border-radius: 999rpx; line-height: 1.2; }
/* 0716 降档：36/900→30/700，让位会议卡大按钮（大按钮>徽标>灰字 三级递减） */
.plan-todo-card .plan-badge { min-width: 132rpx; text-align: center; font-size: 30rpx; font-weight: 700; padding: 16rpx 24rpx; box-sizing: border-box; }
/* 已开=绿｜待开=橙｜逾期=红｜待排=灰（.current/.overdue/.upcoming 属会议 tab 待办卡，
   HOME_V2 下不渲染；接待 tab 仅出 .done「已办结」，按三级规则③改无底灰字） */
.plan-badge.done     { color: #6B7280; background: transparent; }
.plan-badge.current  { color: #fff; background: #D97706; box-shadow: 0 10rpx 22rpx rgba(217,119,6,0.34); }
.plan-badge.overdue  { color: #fff; background: #D83A2E; box-shadow: 0 10rpx 22rpx rgba(216,58,46,0.30); }
.plan-badge.upcoming { color: var(--c-text-weak); background: #EEF0F3; }
/* 方案B：待办卡里「去通知/去补开」从小胶囊升级为整行大按钮 + 缓慢呼吸光晕（核心履职动作要一眼看到） */
.plan-todo-card .yc-item.current, .plan-todo-card .yc-item.overdue { flex-direction: column; align-items: stretch; gap: 12rpx; }
.plan-todo-card .yc-item.current .yc-item-title, .plan-todo-card .yc-item.overdue .yc-item-title { font-size: 32rpx; }
.plan-todo-card .yc-item.current .plan-badge, .plan-todo-card .yc-item.overdue .plan-badge {
  width: 64%; margin: 0 auto; box-sizing: border-box;
  text-align: center; font-size: 30rpx; font-weight: 700; padding: 18rpx 0; border-radius: 18rpx; letter-spacing: 2rpx;
}
/* 实心唯一原则（0716）：有会议卡时它是全页唯一实心大按钮，待办徽标降为浅色填充（tinted）次级按钮。
   调研定型：weui 次级钮/iOS tinted/政务App「去办理」均为浅底+品牌色字——保留按钮面积感，只降色阶；
   白底描边像标签无交互感已废。字色压深至对比度≥4.5:1（适老规范）。 */
/* 有会时这颗按钮降级：淡黄底压到 40%（0716 用户定，60%→20%→40% 收敛）。
   只给 background 加 alpha、不用 opacity——opacity 会把文字和描边一起冲淡，老人就看不清了。
   描边必须 ≥3:1（现 #C76A00 = 3.6:1）：底色让到这个份上，按钮的「可点」全靠这根线撑着。
   前车之鉴：早先那版描边 #FED7AA 只有 1.31:1，肉眼等于没有，当时被判「看着不像按钮」。 */
.plan-stack.has-meeting .plan-todo-card .yc-item .plan-badge.current { color: #9A3412; background: rgba(255, 237, 213, 0.4); border: 2rpx solid #C76A00; box-shadow: none; animation: none; }
.plan-stack.has-meeting .plan-todo-card .yc-item .plan-badge.overdue { color: #B91C1C; background: #FEE2E2; border: 2rpx solid #FECACA; box-shadow: none; animation: none; }
.plan-stack.has-meeting .plan-todo-card .yc-item.current, .plan-stack.has-meeting .plan-todo-card .yc-item.overdue { background: #fff; }
.plan-todo-card .yc-item.current .plan-badge.current { animation: ctaBreathOrange 2.2s ease-in-out infinite; }
.plan-todo-card .yc-item.overdue .plan-badge.overdue { animation: ctaBreathRed 2.2s ease-in-out infinite; }
/* 期限临近紧急态（0723 用户定，剩<10天）：本期待办由橙升红——「去通知」红底白字呼吸、副行红字写剩余天数。
   比 .plan-stack.has-meeting 的淡化规则多一个 .urgent 类，紧急时压过淡化。 */
.plan-todo-card .yc-item.urgent .plan-badge.current,
.plan-stack.has-meeting .plan-todo-card .yc-item.urgent .plan-badge.current {
  color: #fff; background: #D83A2E; border: none;
  box-shadow: 0 10rpx 22rpx rgba(216,58,46,0.30);
  animation: ctaBreathRed 2.2s ease-in-out infinite;
}
.plan-todo-card .yc-item.urgent .yc-item-sub { color: #B02A1E; font-weight: 600; }
@keyframes ctaBreathOrange {
  0%, 100% { box-shadow: 0 10rpx 22rpx rgba(217,119,6,0.35); }
  50% { box-shadow: 0 12rpx 34rpx rgba(217,119,6,0.62), 0 0 0 16rpx rgba(217,119,6,0.22); }
}
@keyframes ctaBreathRed {
  0%, 100% { box-shadow: 0 10rpx 22rpx rgba(216,58,46,0.32); }
  50% { box-shadow: 0 12rpx 34rpx rgba(216,58,46,0.58), 0 0 0 16rpx rgba(216,58,46,0.22); }
}
@media (prefers-reduced-motion: reduce) {
  .plan-todo-card .yc-item.current .plan-badge.current, .plan-todo-card .yc-item.overdue .plan-badge.overdue { animation: none; }
}
.tc-progress { background: #E3E5E9; border-radius: 6rpx; height: 12rpx; overflow: hidden; margin-bottom: 10rpx; }
.tc-fill { height: 100%; border-radius: 6rpx; background: var(--c-primary); }
.tc-rule { font-size: 28rpx; color: var(--c-text-mid); line-height: 1.45; word-break: break-all; }

.role-intro { margin: 24rpx 24rpx 16rpx; padding: 20rpx 24rpx; background: #fff; border-radius: 18rpx; border-left: 8rpx solid #FFA800; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.04); }
.role-intro.public { border-left-color: #27AE60; }
.ri-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; line-height: 1.45; word-break: break-all; }
.ri-desc { font-size: 28rpx; color: #666; margin-top: 8rpx; line-height: 1.55; display: block; word-break: break-all; }

.stage-tabs { display: flex; margin: 0 24rpx 16rpx; background: #fff; border-radius: 18rpx; padding: 8rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.04); }
.tab { flex: 1; padding: 16rpx 8rpx; text-align: center; font-size: 30rpx; color: #666; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; min-width: 0; }
.tab.active { background: #FFA800; color: #fff; font-weight: 700; }
.tab-count { font-size: 28rpx; opacity: 0.85; }

.meeting-list { padding: 0 24rpx; display: flex; flex-direction: column; gap: 20rpx; }
.meeting-card { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.meeting-card:active { background: #fafbfc; }
.muted-card { opacity: 0.92; }
.mc-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; margin-bottom: 12rpx; }
.mc-title { font-size: 36rpx; font-weight: 700; color: #1f2329; line-height: 1.45; flex: 1; min-width: 0; word-break: break-all; }
.mc-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8rpx 12rpx; font-size: 28rpx; color: #666; margin-bottom: 10rpx; line-height: 1.45; }
.mc-dot { color: #666; }
.mc-summary { font-size: 28rpx; color: #C77800; line-height: 1.55; word-break: break-all; }
.mc-stage-desc { font-size: 28rpx; color: #2980B9; }
.personal-badge { font-size: 28rpx; font-weight: 600; padding: 4rpx 16rpx; border-radius: 12rpx; background: #f0f0f0; color: #666; flex-shrink: 0; line-height: 1.35; white-space: nowrap; }
.stage-pill { line-height: 1.35; flex-shrink: 0; }
.personal-badge.doing { background: #FFF3E0; color: #E67E22; }
.personal-badge.done { background: #E8F7EE; color: #27AE60; }
.pp-bar { height: 8rpx; background: #f0f0f0; border-radius: 4rpx; margin-top: 14rpx; overflow: hidden; }
.pp-fill { height: 100%; background: #FFA800; border-radius: 4rpx; }
.pp-fill.done { background: #27AE60; }
.section-header { padding: 24rpx 28rpx 8rpx; font-size: 28rpx; color: #666; font-weight: 500; line-height: 1.45; word-break: break-all; }
.section-header.muted { color: #777; }
.empty, .empty-state { padding: 56rpx 24rpx; text-align: center; font-size: 30rpx; color: #777; line-height: 1.6; word-break: break-all; }

/* 准备会议主按钮（替代原工作台卡片 + 右下角 FAB） */
.prepare-btn { display: flex; align-items: center; justify-content: center; gap: 16rpx; margin: 28rpx 24rpx 12rpx; height: 120rpx; background: linear-gradient(135deg, #FFB740, #FFA800); border-radius: 24rpx; box-shadow: 0 12rpx 28rpx rgba(255,168,0,0.34); }
.prepare-ico { font-size: 46rpx; }
.prepare-text { font-size: 40rpx; font-weight: 700; color: #fff; letter-spacing: 2rpx; }

/* 新建会议弹窗 */
.modal-mask { position: fixed; inset: 0; z-index: 200; background: #fff; display: flex; align-items: stretch; }
.create-panel { width: 100%; height: 100%; background: #fff; display: flex; flex-direction: column; overflow: hidden; }
.create-head { display: flex; align-items: center; background: var(--c-primary-dark); flex-shrink: 0; padding-top: env(safe-area-inset-top); box-sizing: border-box; height: calc(120rpx + env(safe-area-inset-top)); }
.create-back { width: 96rpx; height: 120rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 64rpx; font-weight: 700; flex-shrink: 0; }
.create-title { flex: 1; text-align: center; color: #fff; font-size: 38rpx; font-weight: 700; line-height: 1.35; }
.create-nav-ph { width: 96rpx; flex-shrink: 0; }
.close-btn { width: 60rpx; height: 60rpx; line-height: 56rpx; text-align: center; border-radius: 30rpx; color: #666; background: #f5f5f5; font-size: 40rpx; flex-shrink: 0; }
.create-body { flex: 1; min-height: 0; overflow-y: auto; padding: 14rpx 24rpx 18rpx; box-sizing: border-box; display: flex; flex-direction: column; }
.create-section { background: #fafbfc; border-radius: 16rpx; padding: 16rpx 18rpx; margin-bottom: 31rpx; border: 2rpx solid #f0f0f0; }
.create-section.meeting-info-card { margin-top: 0; margin-bottom: 31rpx; padding: 16rpx 18rpx; }
.meeting-info-card .section-title { margin-bottom: 12rpx; }
.meeting-info-card .form-group { margin-bottom: 14rpx; }
.meeting-info-card .field-caption { display: block; font-size: 26rpx; color: #61656c; font-weight: 600; margin-bottom: 8rpx; line-height: 1.4; }
/* 会议名称 caption 顶替原「会议信息」标题：放大到区块标题字号并居于卡片首行 */
.meeting-info-card .field-caption.caption-as-title { font-size: 32rpx; color: #1f2329; font-weight: 700; margin-bottom: 12rpx; }
/* 时间/地点信息收紧（仅本卡片，不影响议题输入框）：日期时间、地点框都变矮变小 */
.meeting-info-card .form-row { gap: 14rpx; }
.meeting-info-card .picker-field { min-height: 72rpx; font-size: 30rpx; border-radius: 12rpx; padding: 0 18rpx; }
.meeting-info-card .tf-text { font-size: 30rpx; }
.meeting-info-card .loc-row { margin-top: 12rpx; }
.meeting-info-card .loc-input.form-input { height: 72rpx; min-height: 72rpx; font-size: 30rpx; border-radius: 12rpx; }
.loc-input-manual { margin-top: 12rpx; }
.meeting-info-card .loc-map-btn { width: 84rpx; border-radius: 12rpx; }

.prefill-entry { background: #FFF8EA; border: 2rpx solid #FFE0A3; border-radius: 18rpx; padding: 22rpx; margin-bottom: 20rpx; display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.prefill-copy { flex: 1; min-width: 0; }
.prefill-title { display: block; font-size: 28rpx; color: #5C3D00; font-weight: 700; line-height: 1.45; word-break: break-all; }
.prefill-desc { display: block; font-size: 28rpx; color: #9A7600; margin-top: 6rpx; line-height: 1.45; word-break: break-all; }
.prefill-toggle { flex-shrink: 0; min-width: 88rpx; height: 56rpx; line-height: 56rpx; text-align: center; border-radius: 28rpx; background: #fff; color: #C77800; font-size: 28rpx; font-weight: 600; }
.material-section { background: #fffdf7; border-color: #FFE8B8; }
/* 快速填表按钮（窄·居中·描边药丸：明显但不抢"确认创建"，深字高对比） */
.ai-fill-btn { width: 64%; margin: 44rpx auto 18rpx; display: flex; align-items: center; justify-content: center; gap: 12rpx; padding: 26rpx 28rpx; background: var(--c-primary-dark); border: none; border-radius: 999rpx; box-shadow: 0 6rpx 16rpx rgba(168,88,0,0.22); box-sizing: border-box; }
.ai-fill-btn:active { background: var(--c-primary-strong); }
.ai-fill-ico { font-size: 38rpx; line-height: 1; }
.ai-fill-text { font-size: 32rpx; font-weight: 700; color: #fff; }
.ai-fill-btn:disabled, .ai-fill-btn.busy { opacity: 0.65; }
.ai-fill-hint { display: block; text-align: center; font-size: 26rpx; color: #999; margin: -8rpx 0 16rpx; }
/* 快速填表固定条：底部拇指区（创建/取消上方），进入即见、不随表单滚动 */
.quick-fill-bar { flex-shrink: 0; padding: 16rpx 26rpx 10rpx; background: var(--c-bg-page); border-top: 1rpx solid #ececec; }
.quick-fill-bar .ai-fill-btn { margin: 0 auto 10rpx; }
.quick-fill-bar .ai-fill-hint { margin: 0; }
/* 顶部分段切换：手动填写 / 拍照上传（灰底圆角胶囊 + active 橙底白字） */
.create-tabs { display: flex; gap: 8rpx; background: #F2F6F7; border-radius: 16rpx; padding: 4rpx; margin-bottom: 22rpx; }
.create-tab { flex: 1; display: flex; align-items: center; justify-content: center; padding: 14rpx 0; font-size: 36rpx; line-height: 1.2; font-weight: 700; color: #40545C; background:#E7EEF0; border:1rpx solid #D5E0E3; border-radius: 12rpx; cursor: pointer; }
.create-tab.active { background: #D97706; border-color:#D97706; color: #fff; box-shadow: 0 6rpx 16rpx rgba(217,119,6,0.2); }
.create-tab:active { opacity: 0.8; }
.ct-ico { font-size: 32rpx; line-height: 1; }
/* 拍照/上传面板 */
.scan-pane { margin-bottom: 12rpx; }
/* 时间/地点设置项列表（iOS 日历式）：标签左、值右、点整行展开选择器 */
.field-list { border: 2rpx solid #ececec; border-radius: 16rpx; overflow: hidden; background: #fff; margin-top: 6rpx; }
.field-line { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 18rpx; border-bottom: 2rpx solid #f2f2f2; }
.field-line:last-child { border-bottom: none; }
.field-line:active { background: #FAFAFA; }
.fl-label { flex-shrink: 0; width: 100rpx; font-size: 26rpx; color: #61656c; font-weight: 700; }
.fl-value { flex: 1; text-align: right; font-size: 28rpx; color: #1f2329; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fl-value.ph { color: #b7bbc0; }
.fl-arrow { flex-shrink: 0; font-size: 28rpx; color: #c4c8cd; line-height: 1; }
.field-line.field-error { background: #FFF4F4; }
.field-line-split { padding: 0; gap: 0; }
.fl-part { flex: 1; min-width: 0; display: flex; align-items: center; gap: 12rpx; padding: 16rpx 18rpx; box-sizing: border-box; }
.fl-part-time { border-left: 2rpx solid #f2f2f2; }
.fl-part .fl-label { width: auto; }
.fl-part .fl-value { text-align: right; }
.field-line-location { padding: 0 12rpx 0 18rpx; gap: 12rpx; }
.meeting-method-line { justify-content:space-between; gap:12rpx; }
.meeting-method-line > .fl-label { width:auto; min-width:132rpx; white-space:nowrap; font-size:30rpx; font-weight:800; color:#2d3137; }
.online-platform-label { width:auto; min-width:132rpx; white-space:nowrap; font-size:30rpx; font-weight:800; color:#2d3137; }
.method-switch { display:flex; flex-shrink:0; gap:8rpx; padding:6rpx; background:#f1f2f4; border-radius:14rpx; }
.method-switch button { border:0; background:transparent; color:#62676f; font-size:27rpx; padding:12rpx 24rpx; border-radius:10rpx; }
.method-switch button.active { background:#fff; color:var(--c-primary-dark); font-weight:700; box-shadow:0 2rpx 8rpx rgba(0,0,0,.08); }
.platform-select { margin-left:auto; flex:0 0 250rpx; min-width:0; height:64rpx; padding:0 54rpx 0 20rpx; border:2rpx solid #e0e3e7; border-radius:12rpx; background:#fff; color:#25292f; font-size:28rpx; font-weight:700; outline:none; }
.fl-loc-main { flex: 1; min-width: 0; display: flex; align-items: center; gap: 16rpx; padding: 16rpx 0; }
/* 「其他地点」内联输入：直接替换本行选项，无边框，视觉贴合原选项栏 */
.fl-label-tap { color: var(--c-primary-dark); }
.fl-inline-input { flex: 1; min-width: 0; padding: 16rpx 0; border: 0; background: transparent; text-align: right; font-size: 28rpx; font-weight: 700; color: #1f2329; outline: none; }
.fl-inline-input::placeholder { color: #b7bbc0; }
.meeting-info-card .field-list { border: 0; border-radius: 0; overflow: visible; background: transparent; display: flex; flex-direction: column; gap: 16rpx; }
.meeting-info-card .field-line { border: 2rpx solid #ececec; border-radius: 16rpx; background: #fff; }
.meeting-info-card .field-line:last-child { border-bottom: 2rpx solid #ececec; }
.meeting-info-card .fl-value.ph { text-align: left; font-size: 26rpx; font-weight: 500; }
.meeting-info-card .fl-inline-input { text-align: left; }
.meeting-info-card .fl-inline-input::placeholder { font-size: 26rpx; font-weight: 500; }
.field-line-location .field-map-btn { width: 72rpx; height: 60rpx; min-height: 60rpx; align-self: center; border-radius: 12rpx; padding: 0; }
.field-map-btn svg { width: 38rpx; height: 38rpx; }
/* 拍照/上传 双卡片（政务风功能入口）：图标圆 + 标题 + 两行说明整合在卡片内 */
.doc-scan-bar { flex-shrink: 0; padding: 16rpx 26rpx 10rpx; background: var(--c-bg-page); border-top: 1rpx solid #ececec; }
/* 展开在顶部入口内时：去掉底部固定栏的边框/灰底，接在入口下方成一体 */
.doc-scan-bar.inline { flex-shrink: initial; background: transparent; border: 0; border-radius: 0; padding: 10rpx 0 6rpx; }
.ds-cards { display: flex; gap: 18rpx; }
.ds-cards-2 { gap: 56rpx; justify-content:center; }
.ds-cards-2 .ds-card { flex:0 0 36%; padding: 18rpx 6rpx 16rpx; }
.ds-card { flex: 1; min-width: 0; background: #fff; border: 2rpx solid #eee; border-radius: 20rpx; padding: 20rpx 10rpx 16rpx; display: flex; flex-direction: column; align-items: center; gap: 6rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.05); }
.ds-card:active { background: #FFF8EE; border-color: #FFD79A; }
.ds-card:disabled { opacity: 0.75; }
.ds-ico { width: 58rpx; height: 58rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 22rpx; margin-bottom: 4rpx; }
.ds-ico.or { background: #FFF3E0; }
.ds-ico.bl { background: #EAF2FF; }
.ds-t { font-size: 28rpx; font-weight: 700; color: #1f2329; line-height: 1.3; }
.ds-shared-hint { margin:12rpx 4rpx 2rpx; color:#938979; font-size:26rpx; line-height:1.45; text-align:center; white-space:nowrap; }
.ds-hint { font-size: 24rpx; color: #9a9a9a; text-align: center; margin: 12rpx 2rpx 0; line-height: 1.45; }
.ds-spin { width: 68rpx; height: 68rpx; border-radius: 50%; border: 6rpx solid rgba(168,88,0,0.2); border-top-color: var(--c-primary-dark); box-sizing: border-box; animation: aiSpin 0.7s linear infinite; margin-bottom: 4rpx; }
/* 待识别缩略图预览条：横向排列，可删 */
.ds-preview { display: flex; flex-wrap: wrap; gap: 14rpx; padding: 4rpx 2rpx 16rpx; }
.ds-thumb { position: relative; width: 108rpx; height: 108rpx; border-radius: 14rpx; overflow: hidden; border: 2rpx solid #E7E2D8; background: #fff; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.05); }
.ds-thumb-img { width: 100%; height: 100%; object-fit: cover; display: block; cursor: pointer; }
.ds-thumb-file { cursor: pointer; }
/* 放大角标：提示缩略图可点击展开看清（左下角，避开右上删除×） */
.ds-thumb-zoom { position: absolute; bottom: 0; left: 0; width: 36rpx; height: 36rpx; border-radius: 0 12rpx 0 12rpx; background: rgba(0,0,0,0.5); color: #fff; font-size: 24rpx; line-height: 36rpx; text-align: center; }
.ds-thumb-file { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4rpx; background: #FAF7F1; }
.ds-thumb-ico { font-size: 44rpx; line-height: 1; }
.ds-thumb-ext { font-size: 20rpx; color: #A98; text-transform: uppercase; }
.ds-thumb-del { position: absolute; top: -2rpx; right: -2rpx; width: 38rpx; height: 38rpx; border-radius: 0 14rpx 0 14rpx; background: rgba(0,0,0,0.55); color: #fff; font-size: 30rpx; line-height: 38rpx; text-align: center; }
/* 开始 AI 识别：实心橙大按钮，攒了文件才出现 */
/* 开始 AI 识别：深青绿 + 居中变窄(70%) + 边缘淡淡发光(轻脉冲)，醒目且与橙色协调 */
.ds-recognize { display: block; width: 62%; height: 81rpx; margin: 40rpx auto 4rpx; border: none; border-radius: 16rpx; background: #0E8A7B; color: #fff; font-size: 29rpx; font-weight: 700; box-shadow: 0 6rpx 16rpx rgba(12,90,80,0.30), 0 0 14rpx rgba(30,180,155,0.5); animation: dsGlow 1.9s ease-in-out infinite; }
.ds-recognize:active { background: #0B6F63; }
.ds-recognize:disabled { opacity: 0.72; animation: none; }
@keyframes dsGlow {
  0%, 100% { box-shadow: 0 6rpx 16rpx rgba(12,90,80,0.30), 0 0 12rpx rgba(30,180,155,0.38); }
  50% { box-shadow: 0 6rpx 16rpx rgba(12,90,80,0.30), 0 0 26rpx rgba(40,200,170,0.82); }
}
/* token 消耗：低调小字，识别完成后显示 */

/* 模拟系统相机权限弹窗（仿系统样式：居中白盒 + 细线分隔双按钮） */

/* 识别中：居中小弹窗（文档扫描动画 + 三步流程 + 动态文案） */
.scan-pop-mask { position: fixed; inset: 0; z-index: 3050; background: rgba(10, 8, 4, 0.42); backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; }
.scan-pop { position: relative; width: 640rpx; max-width: 92%; background: linear-gradient(180deg, #FFFDF9 0%, #fff 30%); border: 1rpx solid rgba(255, 168, 0, 0.25); border-radius: 30rpx; padding: 48rpx 44rpx 38rpx; display: flex; flex-direction: column; align-items: center; box-shadow: 0 20rpx 60rpx rgba(120, 70, 0, 0.28), 0 0 0 6rpx rgba(255, 168, 0, 0.06); box-sizing: border-box; }
.sp-close { position: absolute; top: 10rpx; right: 16rpx; width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; font-size: 48rpx; line-height: 1; color: #B0A48E; z-index: 2; }
.sp-close:active { color: #7A6E58; }
/* 圆环进度：单一焦点，中心大百分比 */
.sp-ring { position: relative; width: 210rpx; height: 210rpx; margin-bottom: 32rpx; }
.sp-ring-svg { width: 100%; height: 100%; transform-origin: 50% 50%; animation: spSpin 2.6s linear infinite; }
.sp-ring-track { fill: none; stroke: #F1EADB; stroke-width: 8.5; }
.sp-ring-fill { fill: none; stroke: url(#spGrad); stroke-width: 8.5; stroke-linecap: round; stroke-dasharray: 264; transition: stroke-dashoffset .45s ease; }
@keyframes spSpin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .sp-ring-svg { animation: none; transform: rotate(-90deg); } }
.sp-ring-center { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; }
.sp-ring-val { display: flex; align-items: baseline; }
.sp-ring-num { font-size: 76rpx; font-weight: 800; color: #C76A00; font-variant-numeric: tabular-nums; line-height: 1; }
.sp-ring-pct { font-size: 32rpx; font-weight: 800; color: #C76A00; margin-left: 3rpx; }
@keyframes spRingGlow { 0%, 100% { filter: drop-shadow(0 0 0 rgba(255,168,0,0)); } 50% { filter: drop-shadow(0 0 5rpx rgba(255,168,0,0.55)); } }
.sp-title { font-size: 42rpx; font-weight: 700; color: #1f2329; letter-spacing: 1rpx; }
.sp-say { font-size: 32rpx; color: #B07400; margin: 14rpx 0 4rpx; min-height: 44rpx; }
.sp-foot { font-size: 23rpx; color: #C0B29A; margin-top: 26rpx; letter-spacing: 0.5rpx; }

/* AI 识别完成：精美结果卡 */
.scan-result-mask { position: fixed; inset: 0; z-index: 3060; background: rgba(10, 8, 4, 0.42); backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; padding: 40rpx; box-sizing: border-box; }
.scan-result { position: relative; width: 640rpx; max-width: 92%; background: linear-gradient(180deg, #FFFDF9 0%, #fff 24%); border: 1rpx solid rgba(255, 168, 0, 0.22); border-radius: 30rpx; padding: 40rpx 40rpx 34rpx; box-shadow: 0 20rpx 60rpx rgba(120, 70, 0, 0.28), 0 0 0 6rpx rgba(255, 168, 0, 0.05); box-sizing: border-box; display: flex; flex-direction: column; align-items: center; }
.sr-hero { display: flex; flex-direction: row; align-items: center; justify-content: center; gap: 18rpx; width: 100%; margin-bottom: 26rpx; }
.sr-hero-badge { flex-shrink: 0; width: 70rpx; height: 70rpx; border-radius: 50%; background: #ECF6F0; display: flex; align-items: center; justify-content: center; box-shadow: 0 0 0 6rpx rgba(46, 139, 87, 0.10); }
.sr-hero-badge .sr-check { font-size: 42rpx; }
.sr-hero-title { font-size: 44rpx; font-weight: 800; color: #1f2329; letter-spacing: 1rpx; }
.sr-check { color: #1E7E4E; font-size: 40rpx; font-weight: 800; line-height: 1; }
.sr-body { width: 100%; display: flex; flex-direction: column; gap: 16rpx; }
.sr-card { background: #fff; border: 1rpx solid #F1E7D6; border-radius: 18rpx; padding: 20rpx 22rpx; box-shadow: 0 4rpx 14rpx rgba(120, 70, 0, 0.06); }
/* 结果条：图标 chip + 标题 + 说明 */
.sr-card-main { display: flex; align-items: center; gap: 18rpx; }
.sr-ico-chip { flex-shrink: 0; width: 72rpx; height: 72rpx; border-radius: 18rpx; display: flex; align-items: center; justify-content: center; font-size: 38rpx; line-height: 1; }
.sr-ico-chip.notice { background: #FFF1E2; }
.sr-ico-chip.material { background: #E9F2FC; }
.sr-card-text { flex: 1; min-width: 0; }
.sr-card-title { font-size: 31rpx; font-weight: 800; color: #1f2329; line-height: 1.3; }
.sr-card-sub { font-size: 25rpx; color: #7A8590; margin-top: 6rpx; line-height: 1.4; }

/* 识别结果：类别小标签 + 字段一览（一眼核对） */
.sr-cat { align-self: flex-start; display: inline-flex; align-items: center; gap: 8rpx; font-size: 28rpx; font-weight: 700; padding: 8rpx 22rpx; border-radius: 999rpx; }
.sr-cat.notice { background: #FFF3E0; color: #A85800; }
.sr-cat.material { background: #E9F2FC; color: #2E86C1; }
.sr-cat-ico { font-size: 30rpx; line-height: 1; }
.sr-preview { width: 100%; background: #fff; border: 1rpx solid #EEE6D8; border-radius: 16rpx; overflow: hidden; }
.sr-pv-row { display: flex; align-items: flex-start; gap: 16rpx; padding: 22rpx 24rpx; }
.sr-pv-row + .sr-pv-row { border-top: 1rpx solid #F3EEE4; }
.sr-pv-label { flex-shrink: 0; width: 90rpx; color: #8A8F98; font-size: 31rpx; line-height: 1.5; }
.sr-pv-val-wrap { flex: 1; min-width: 0; }
.sr-pv-value { color: #1f2329; font-size: 34rpx; font-weight: 600; line-height: 1.5; word-break: break-all; }
.sr-pv-value.miss { color: #B0863A; }
.sr-mat-line { display: flex; align-items: center; gap: 12rpx; font-size: 30rpx; color: #4A5560; padding: 2rpx; line-height: 1.5; }
.sr-mat-ico { flex-shrink: 0; color: #2E86C1; font-size: 33rpx; line-height: 1; }
.sr-row-top { display: flex; align-items: center; gap: 14rpx; }
.sr-pill { font-size: 24rpx; font-weight: 700; color: #fff; padding: 6rpx 18rpx; border-radius: 999rpx; flex-shrink: 0; }
.sr-pill.notice { background: var(--c-primary-dark); }
.sr-pill.material { background: #2E86C1; }
.sr-pill.warn { background: #8A5800; }
.sr-row-note { font-size: 30rpx; color: #4A5560; font-weight: 600; }
/* 多份通知选择卡：逐份单选 */
.sr-multi { background: #FBF7F0; border-color: #F0E6D6; }
.sr-notice-opt { display: flex; align-items: flex-start; gap: 14rpx; margin-top: 14rpx; padding: 16rpx 16rpx; background: #fff; border: 2rpx solid #EDE4D4; border-radius: 14rpx; cursor: pointer; }
.sr-notice-opt.sel { border-color: var(--c-primary-dark); background: #FFF7EC; }
.sr-radio { flex-shrink: 0; width: 34rpx; height: 34rpx; margin-top: 4rpx; border-radius: 50%; border: 3rpx solid #C9CDD4; box-sizing: border-box; }
.sr-radio.on { border-color: var(--c-primary-dark); background: radial-gradient(circle at center, var(--c-primary-dark) 0 44%, #fff 46% 100%); }
.sr-notice-body { flex: 1; min-width: 0; }
.sr-notice-title { font-size: 32rpx; font-weight: 700; color: #1f2329; line-height: 1.35; }
.sr-notice-meta { font-size: 28rpx; color: #4A5560; margin-top: 6rpx; }
.sr-notice-topics { font-size: 25rpx; color: #6A7480; margin-top: 4rpx; line-height: 1.4; }
/* 信息冲突卡：偏红底 + 逐条「原值 → 新值」 */
.sr-conflict { background: #FFF7EA; border-color: #F0DEB6; }
.sr-conf-line { display: flex; align-items: center; flex-wrap: wrap; gap: 8rpx; margin-top: 12rpx; font-size: 29rpx; line-height: 1.4; }
.sr-conf-label { color: #7A8590; font-weight: 700; margin-right: 4rpx; }
.sr-conf-old { color: #9aa0a6; text-decoration: line-through; }
.sr-conf-arrow { color: #B0863A; }
.sr-conf-new { color: #A85800; font-weight: 700; }
/* 红字提示：仅在缺必填或多份通知时间地点冲突时出现 */
.sr-alert { margin-top: 12rpx; font-size: 29rpx; line-height: 1.5; color: #8A5800; font-weight: 600; }
.sr-tip { font-size: 30rpx; color: #6A7480; text-align: center; padding: 4rpx; }
.sr-tip.warn { color: #C0392B; font-weight: 600; }
.sr-meta { font-size: 28rpx; color: #C0A587; margin: 22rpx 0 24rpx; }
.sr-actions { display: flex; align-items: center; gap: 18rpx; width: 100%; }
.sr-btn { height: 92rpx; border: none; border-radius: 46rpx; font-size: 34rpx; font-weight: 700; white-space: nowrap; display: flex; align-items: center; justify-content: center; }
.sr-btn.ghost { flex: 0 0 auto; min-width: 172rpx; padding: 0 28rpx; background: #f2f2f2; color: #777; }
.sr-btn.ghost:active { background: #e9e9e9; }
.sr-btn.primary { flex: 1; background: var(--c-primary-dark); color: #fff; box-shadow: 0 6rpx 16rpx rgba(168, 88, 0, 0.24); }
.sr-btn.primary:active { background: var(--c-primary-strong); }

/* 模拟手机相机（测试用） */
.mock-cam { position: fixed; inset: 0; z-index: 3000; background: #000; display: flex; flex-direction: column; }
.mc-top { flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; padding: calc(20rpx + env(safe-area-inset-top)) 28rpx 16rpx; }
.mc-badge { font-size: 24rpx; color: #FFD79A; background: rgba(255, 215, 154, 0.14); border: 1rpx solid rgba(255, 215, 154, 0.4); padding: 6rpx 18rpx; border-radius: 999rpx; }
.mc-close { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 52rpx; line-height: 1; background: rgba(255,255,255,0.14); border-radius: 50%; }
.mc-viewport { flex: 1; min-height: 0; position: relative; display: flex; align-items: center; justify-content: center; padding: 30rpx 44rpx; }
.mc-paper { max-width: 100%; max-height: 100%; border-radius: 6rpx; box-shadow: 0 10rpx 50rpx rgba(0,0,0,0.8); transform: rotate(-1.2deg); }
/* 拍后预览：背景放柔（深灰替代纯黑），照片尽量占满取景区 */
.mock-cam.preview { background: #2F3136; }
.mock-cam.preview .mc-viewport { padding: 12rpx 16rpx; }
.mc-paper.shot { width: 100%; height: 100%; object-fit: contain; transform: none; box-shadow: none; border-radius: 0; }
.mc-confirm-tip { flex-shrink: 0; text-align: center; font-size: 28rpx; color: rgba(255,255,255,0.85); padding-top: 14rpx; }
.mock-cam.preview .mc-bottom { padding-top: 20rpx; }
.mc-corner { position: absolute; width: 52rpx; height: 52rpx; border: 5rpx solid #FFD34D; }
.mc-corner.tl { top: 18rpx; left: 26rpx; border-right: none; border-bottom: none; border-radius: 10rpx 0 0 0; }
.mc-corner.tr { top: 18rpx; right: 26rpx; border-left: none; border-bottom: none; border-radius: 0 10rpx 0 0; }
.mc-corner.bl { bottom: 18rpx; left: 26rpx; border-right: none; border-top: none; border-radius: 0 0 0 10rpx; }
.mc-corner.br { bottom: 18rpx; right: 26rpx; border-left: none; border-top: none; border-radius: 0 0 10rpx 0; }
.mc-tip { position: absolute; bottom: 34rpx; left: 0; right: 0; text-align: center; font-size: 26rpx; color: rgba(255,255,255,0.75); }
/* 左右切换样张：半透明圆形箭头 + 顶部样张指示 */
.mc-nav { position: absolute; top: 50%; transform: translateY(-50%); width: 72rpx; height: 72rpx; border-radius: 50%; background: rgba(0,0,0,0.4); color: #fff; font-size: 48rpx; line-height: 1; border: none; display: flex; align-items: center; justify-content: center; }
.mc-nav.prev { left: 18rpx; }
.mc-nav.next { right: 18rpx; }
.mc-nav:active { background: rgba(0,0,0,0.6); }
.mc-sample-ind { position: absolute; top: 20rpx; left: 50%; transform: translateX(-50%); font-size: 24rpx; color: #fff; background: rgba(0,0,0,0.42); padding: 6rpx 22rpx; border-radius: 999rpx; white-space: nowrap; }
.mc-bottom { flex-shrink: 0; position: relative; display: flex; justify-content: center; padding: 30rpx 0 calc(46rpx + env(safe-area-inset-bottom)); }
.mc-count { font-size: 24rpx; color: #fff; background: rgba(255,255,255,0.18); padding: 6rpx 18rpx; border-radius: 999rpx; }
.mc-done { position: absolute; right: 40rpx; top: 50%; transform: translateY(-50%); height: 78rpx; padding: 0 32rpx; border: none; border-radius: 40rpx; background: var(--c-primary-dark); color: #fff; font-size: 30rpx; font-weight: 700; }
.mc-done:active { background: var(--c-primary-strong); }
/* 左下角相册缩略图（像系统相机连拍）：显示最近一张 + 张数角标，点击=完成并回到列表查看/删除 */
.mc-roll { position: absolute; left: 40rpx; top: 50%; transform: translateY(-50%); width: 92rpx; height: 92rpx; padding: 0; border: 4rpx solid rgba(255,255,255,0.9); border-radius: 16rpx; background: rgba(0,0,0,0.3); overflow: visible; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.35); }
.mc-roll:active { transform: translateY(-50%) scale(0.92); }
.mc-roll-img { width: 100%; height: 100%; object-fit: cover; border-radius: 12rpx; display: block; }
.mc-roll-badge { position: absolute; top: -14rpx; right: -14rpx; min-width: 36rpx; height: 36rpx; padding: 0 8rpx; box-sizing: border-box; border-radius: 999rpx; background: var(--c-primary-strong); color: #fff; font-size: 24rpx; font-weight: 700; line-height: 36rpx; text-align: center; border: 3rpx solid #fff; }
.mc-shutter { width: 140rpx; height: 140rpx; border-radius: 50%; background: transparent; border: 8rpx solid #fff; display: flex; align-items: center; justify-content: center; padding: 0; }
.mc-shutter-core { width: 104rpx; height: 104rpx; border-radius: 50%; background: #fff; transition: transform .12s ease; }
.mc-shutter:active .mc-shutter-core { transform: scale(0.85); }
.mc-bottom.confirm { gap: 28rpx; padding-left: 44rpx; padding-right: 44rpx; }
/* 重拍/确定 = 35:65，突出确定 */
.mc-bottom.confirm .mc-btn.retake { flex: 0 0 35%; }
.mc-bottom.confirm .mc-btn.use { flex: 1; }
.mc-btn { flex: 1; height: 96rpx; border-radius: 48rpx; font-size: 34rpx; font-weight: 700; border: none; }
.mc-btn.retake { background: rgba(255,255,255,0.16); color: #fff; }
.mc-btn.use { background: var(--c-primary-dark); color: #fff; }
.mc-flash { position: absolute; inset: 0; background: #fff; opacity: 0; pointer-events: none; transition: opacity .1s ease; }
.mc-flash.on { opacity: 0.9; }

/* 真·相机：实时视频铺满取景区；打不开时的状态/错误提示 */
.real-cam .mc-viewport { padding: 8rpx 12rpx; }
.mc-video { width: 100%; height: 100%; object-fit: cover; background: #000; border-radius: 6rpx; }
/* 会议材料行：小图标 + 可点文件名（点开全屏预览）+ 大小 + 删除 */
/* 会议材料折叠头：标题 + 右侧小箭头，点击展开详情 */
.mat-head { cursor: pointer; }
.mat-toggle { flex-shrink: 0; font-size: 40rpx; line-height: 1; color: var(--c-primary-dark); font-weight: 700; transition: transform .2s ease; }
.mat-toggle.open { transform: rotate(90deg); }
/* 微信式文件卡：类型图标 + 文件名 + 下方大小 */
.mat-card { display: flex; align-items: center; gap: 18rpx; background: #F7F8FA; border: 1rpx solid #ECEEF1; border-radius: 14rpx; padding: 16rpx 18rpx; margin-top: 14rpx; cursor: pointer; }
.mat-card:active { background: #EEF0F3; }
.mat-badge { flex-shrink: 0; width: 72rpx; height: 72rpx; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 22rpx; font-weight: 700; letter-spacing: 1rpx; }
.mat-badge.t-pdf  { background: #E5533C; }
.mat-badge.t-doc  { background: #2B7CD3; }
.mat-badge.t-xls  { background: #1E9E5A; }
.mat-badge.t-ppt  { background: #E07B2E; }
.mat-badge.t-img  { background: #17A2A2; }
.mat-badge.t-txt  { background: #7A8598; }
.mat-badge.t-file { background: #9AA0A6; }
.mat-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.mat-fname { font-size: 28rpx; color: #1f2329; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mat-fsize { font-size: 24rpx; color: #9aa0a6; }
.mat-del { flex-shrink: 0; font-size: 40rpx; color: #c4c8cd; padding: 0 6rpx; line-height: 1; }
.mat-del:active { color: #999; }
/* 转圈圈：按钮内白色细环旋转（识别中显示，模拟进度数字在按钮文字里） */
.ai-fill-spin { width: 34rpx; height: 34rpx; border-radius: 50%; border: 5rpx solid rgba(255,255,255,0.45); border-top-color: #fff; box-sizing: border-box; animation: aiSpin 0.7s linear infinite; }
@keyframes aiSpin { to { transform: rotate(360deg); } }
/* 居委会见证（创建页，移自通知页）：白卡 + 标题/说明 + 适老化大复选框 */
/* 居委会见证：普通选项行（非卡片），标题比 section-title 小一号、无灰字注释 */
/* 居委会见证（说明式开关卡片，精简为一行：标题 + 开关） */
.juwei-card { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; background: #fff; border: 2rpx solid #f0f0f0; border-radius: 16rpx; padding: 16rpx 18rpx; margin-top: 4rpx; margin-bottom: 25rpx; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04); }
.juwei-switch { cursor: pointer; }
.juwei-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.juwei-title { min-width: 0; font-size: 28rpx; color: #1f2329; font-weight: 600; line-height: 1.4; }
/* 制度要求小字（0723）：比标题小两号但不低于适老下限，深灰而非浅灰 */
.juwei-sub { font-size: 26rpx; color: #526774; line-height: 1.45; }
/* 开关 */
.juwei-switch { flex-shrink: 0; width: 84rpx; height: 48rpx; border-radius: 999rpx; background: #D3D6DB; position: relative; transition: background .2s ease; }
.juwei-switch::after { content: ""; position: absolute; top: 5rpx; left: 5rpx; width: 38rpx; height: 38rpx; border-radius: 50%; background: #fff; box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.2); transition: left .2s ease; }
.juwei-switch.on { background: var(--c-primary-dark); }
.juwei-switch.on::after { left: 41rpx; }
.assist-row { display: flex; gap: 18rpx; margin-bottom: 18rpx; }
.assist-btn { flex: 1; height: 80rpx; line-height: 80rpx; border-radius: 40rpx; background: #fff; color: #C77800; border: 2rpx solid #FFE0A3; font-size: 28rpx; font-weight: 600; padding: 0 20rpx; margin: 0; box-sizing: border-box; display: flex; align-items: center; justify-content: center; }
.assist-btn.primary { background: #FFA800; color: #fff; border-color: #FFA800; }
.file-list { display: flex; flex-direction: column; gap: 12rpx; margin-bottom: 18rpx; }
.file-item { background: #fff; border: 2rpx solid #f0f0f0; border-radius: 14rpx; padding: 16rpx 18rpx; display: flex; align-items: flex-start; justify-content: space-between; gap: 14rpx; }
.file-name { flex: 1; min-width: 0; font-size: 28rpx; color: #333; line-height: 1.45; word-break: break-all; }
.file-size { flex-shrink: 0; font-size: 28rpx; color: #666; line-height: 1.45; }
.material-textarea { min-height: 200rpx; height: 200rpx; margin-bottom: 18rpx; }
.scan-line { display: block; font-size: 28rpx; color: #666; line-height: 1.6; word-break: break-all; }
.scan-line.ok { color: #27AE60; }
.clear-link { display: block; margin-top: 14rpx; font-size: 28rpx; color: #666; line-height: 1.5; }
.section-title-row { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-bottom: 10rpx; }
.section-title { display: block; font-size: 32rpx; color: #1f2329; font-weight: 700; margin-bottom: 8rpx; line-height: 1.4; word-break: break-all; }
.section-title-row .section-title { margin-bottom: 0; }
.required-note { font-size: 28rpx; color: #E67E22; line-height: 1.35; white-space: nowrap; }

.form-row { display: flex; gap: 18rpx; align-items: flex-start; }
.form-group { margin-bottom: 12rpx; min-width: 0; }
.form-group:last-child { margin-bottom: 0; }
.form-group.half { flex: 1; min-width: 0; }
.form-label { display: block; font-size: 28rpx; color: #777; margin-bottom: 12rpx; line-height: 1.45; word-break: break-all; }
.no-visit-quick { width: 100%; height: 84rpx; margin-bottom: 22rpx; border: 2rpx solid #D6DEE1;
  border-radius: 16rpx; background: #F7F9F9; color: var(--c-text-strong); font-size: 31rpx; font-weight: 700; }
.no-visit-quick:active { background: #EEF2F3; }
.no-visit-quick:disabled { opacity: 0.55; }
.rec-visitor-card { margin: 20rpx 0; padding: 22rpx; border: 2rpx solid #E7EAEC; border-radius: 18rpx; background: #FAFBFB; }
.rec-visitor-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.rec-visitor-head strong { font-size: 31rpx; color: var(--c-text-strong); }
.rec-visitor-head button { border: 0; background: transparent; color: #B42318; font-size: 27rpx; }
.rec-add-visitor { width: 100%; height: 78rpx; margin: 4rpx 0 18rpx; border: 2rpx dashed #BAC5C9; border-radius: 16rpx;
  background: #fff; color: var(--c-primary-dark); font-size: 29rpx; font-weight: 600; }
.rec-add-visitor:active { background: #F5F8F8; }
.form-input, .form-textarea { width: 100%; box-sizing: border-box; background: #fff; border-radius: 14rpx; font-size: 32rpx; color: #1f2329; border: 2rpx solid #eeeeee; }
.form-input { height: 88rpx; min-height: 88rpx; line-height: normal; padding: 0 20rpx; }
.form-input.large { height: 72rpx; min-height: 72rpx; line-height: normal; font-size: 30rpx; font-weight: 600; }
.form-input::placeholder, .form-textarea::placeholder { color: #666; font-size: 30rpx; line-height: 1.5; }
/* 会议标题输入框：占位用更淡的灰 + 常规字重（"请输入会议名称"作浅提示） */
.form-input.large::placeholder { color: #9a9a9a; font-weight: 400; }
.picker-field { width: 100%; min-height: 88rpx; box-sizing: border-box; background: #fff; border: 2rpx solid #eeeeee; border-radius: 14rpx; padding: 0 20rpx; font-size: 32rpx; color: #1f2329; line-height: normal; word-break: break-all; display: flex; align-items: center; }
/* 接待人原生下拉框（0717）：外观对齐 .picker-field，压掉系统箭头换统一的向下 chevron */
.picker-select { width: 100%; min-height: 88rpx; box-sizing: border-box; background-color: #fff; border: 2rpx solid #eeeeee; border-radius: 14rpx; padding: 0 68rpx 0 20rpx; font-size: 32rpx; color: #1f2329; outline: none; cursor: pointer; -webkit-appearance: none; appearance: none; background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M6 9l6 6 6-6' fill='none' stroke='%2362676F' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 20rpx center; background-size: 36rpx; }
.form-textarea { min-height: 200rpx; height: 200rpx; line-height: 1.5; padding: 18rpx 20rpx; }

.sheet-actions { display: flex; gap: 18rpx; justify-content: space-between; padding-top: 12rpx; }
.sheet-actions.fixed { padding: 20rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); background: #fff; border-top: 2rpx solid #f2f2f2; flex-shrink: 0; align-items: center; }
/* 方案D（回调版）：主次靠宽度区分为主，两按钮等高；生成通知加宽带箭头，取消收窄 */
.sheet-actions.fixed .btn-ghost { flex: 0 0 38%; height: 96rpx; }
.sheet-actions.fixed .btn-primary { flex: 1; height: 96rpx; border-radius: 48rpx; font-size: 33rpx; gap: 6rpx; }
.sheet-actions.fixed .btn-primary .btn-arrow { font-size: 40rpx; font-weight: 700; line-height: 1; margin-top: -4rpx; }
.btn, .btn-ghost, .btn-primary { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; border-radius: 44rpx; text-align: center; font-size: 32rpx; font-weight: 600; box-sizing: border-box; white-space: nowrap; padding: 0 24rpx; margin: 0; border: 0; display: flex; align-items: center; justify-content: center; }
.btn-ghost { color: #777; background: #f5f5f5; }
/* 主按钮统一深橙（与顶栏同色），按下更深 */
.btn-primary { color: #fff; background: var(--c-primary-dark); }
.btn-primary:active { background: var(--c-primary-strong); }

/* 议题构建 */
.topic-empty { text-align: center; padding: 28rpx 0; font-size: 28rpx; color: #666; line-height: 1.6; }
/* 议题列表：议题多时固定高度、内部下拉滚动，避免把下方「重大事项」挤出屏幕 */
.topic-list { max-height: 200rpx; overflow-y: auto; margin: 2rpx 0 6rpx; }
.topic-line { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 16rpx 4rpx; }
.topic-line-text { flex: 1; min-width: 0; font-size: 30rpx; color: #1f2329; line-height: 1.45; word-break: break-all; }
.topic-line-badge { flex-shrink: 0; margin: 0; }
.topic-line-del { flex-shrink: 0; font-size: 42rpx; color: #888; padding: 0 10rpx; line-height: 1; }
/* 会议议题：标题与添加条贴近一些 */
.section-title-row.topic-head { margin-bottom: 0; }
/* 添加议题触发条：点它弹出议题弹窗（输入/类型/确定都在弹窗内），单独一条大按钮，远离右下角「生成通知」防误触 */
.topic-add-trigger { display: flex; align-items: center; justify-content: center; gap: 10rpx; margin-top: 12rpx; height: 88rpx; border: 2rpx dashed #C9CDD4; border-radius: 16rpx; background: #FAFBFC; color: #55606E; font-size: 30rpx; }
.topic-add-trigger:active { background: #F1F3F5; }
.topic-add-trigger.field-error { border-color: #E5533C; background: #FFF3F1; color: #C0392B; }
/* 卡片压缩（0723 用户定）：去标题去补充正文后整体收紧,类型 chip 缩小约 40%；
   「议题内容+取消」「议题类型/表决方式+chips」各并成一行 */
.topic-inline-editor { margin-top: 16rpx; padding: 22rpx 24rpx 26rpx; border: 2rpx solid #E2E5E9; border-radius: 18rpx; background: #FAFBFC; }
.tie-label-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10rpx; }
.tie-label-row .form-label { margin-bottom: 0; }
.tie-cancel { border: 0; background: transparent; color: #7A818B; font-size: 26rpx; padding: 4rpx 0 4rpx 24rpx; }
/* 类型/表决方式 chips 靠最右（0723 用户定）：与「议题内容/取消」同一左右结构，选项更显眼 */
.tie-inline-row { display: flex; align-items: center; justify-content: space-between; gap: 24rpx; }
.tie-inline-row .form-label { margin-bottom: 0; flex-shrink: 0; }
.tie-inline-row .type-row { justify-content: flex-end; }
.topic-inline-editor .form-group { margin-bottom: 24rpx; }
.create-panel .topic-inline-editor .type-chip { min-height: 52rpx; padding: 6rpx 22rpx; font-size: 28rpx; }
.topic-inline-editor .type-row { margin-bottom: 0; }
.tie-content { box-sizing: border-box; height: auto; min-height: 140rpx; line-height: 1.6; resize: none; padding: 16rpx 20rpx; }
.tie-add-option { display: block; margin-top: 12rpx; }
.topic-title-confirm { flex: 0 0 104rpx; height: 72rpx; border: 2rpx solid #C7D8E6; border-radius: 12rpx; background: #DCE8F2; color: #3F6078; font-size: 28rpx; font-weight: 600; }
.topic-title-confirm:active { opacity: .88; }
/* 选中态加强（0723 用户定"明显一点"）：深蓝底白字，与未选的浅灰形成强对比 */
.topic-inline-editor .type-chip { border: 2rpx solid #E2E5E9; background: #fff; }
.topic-inline-editor .type-chip.on { background: #3F6078; color: #fff; border-color: #3F6078; font-weight: 700; box-shadow: 0 4rpx 10rpx rgba(63,96,120,0.25); }
.topic-inline-editor .add-link { color: #5B7C96; }
.tie-confirm { width: 100%; height: 76rpx; margin-top: 2rpx; border: 0; border-radius: 14rpx; background: #B45F18; color: #fff; font-size: 29rpx; font-weight: 600; }
.tie-confirm:active { opacity: .88; }
.tat-ico { font-size: 34rpx; font-weight: 700; line-height: 1; }
.tat-text { font-weight: 600; }
/* 议题弹窗标题行：输入框 + 语音麦克风并排（语音从这里输入，落进弹窗草稿标题） */
.td-title-row { display: flex; align-items: center; gap: 14rpx; }
.td-title-row .form-input { flex: 1; min-width: 0; }
.td-mic { width: 72rpx; height: 72rpx; }
.ct-option-row { display: flex; align-items: center; gap: 14rpx; margin-top: 12rpx; }
.ct-opt-num { font-size: 28rpx; color: #666; width: 40rpx; text-align: right; flex-shrink: 0; }
.ct-opt-input { flex: 1; min-width: 0; height: 72rpx; min-height: 72rpx; line-height: normal; font-size: 28rpx; }
/* ── 接待登记（0716 从已删的接待列表页搬来）──
   刻意不叫 .modal-mask/.form-sheet：本页的 .modal-mask 是全屏白底面板（发起会议用），
   跟接待要的「半透明遮罩 + 底部抽屉」是两回事，同名会串。 */
/* 登记按钮（0716 用户选方案 A：浅橙填充 tinted，中强调）。演进：实心深橙大卡 → 压七成删副标题 →
   浅橙底 #FFF3E5 + 深橙字 #A85800、内容居中、去箭头、平底无阴影。与待办按钮的 tinted 降级态同族。
   高度 92rpx=46px，仍在 44px 适老热区之上。 */
/* .rec-add-card/.rac-* 死样式已删（0723）：模板改用 .rec-register-card/.rrc-* 后零引用 */
/* z 50→150（0716 修）：底部 TabBar 是 z-index:100，50 会被它骑在头上、盖住「取消/确认登记」；
   150 压过 TabBar，又低于日期/时间选择弹窗的 210——选择器要能开在本弹窗之上 */
.rec-mask { position: fixed; inset: 0; z-index: 150; background: rgba(0,0,0,0.36); display: flex; align-items: flex-end; }
.rec-sheet { width: 100%; max-height: 88vh; overflow: auto; background: #fff; border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.sheet-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.sheet-close { width: 56rpx; height: 56rpx; line-height: 52rpx; text-align: center; border-radius: 28rpx;
  font-size: 40rpx; color: #666; background: #f5f5f5; flex-shrink: 0; }
.type-row { display: flex; flex-wrap: wrap; gap: 14rpx; margin-bottom: 18rpx; }
.type-chip { min-height: 60rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: #666; background: #f5f5f5; padding: 10rpx 22rpx; border-radius: 28rpx; }
.type-chip.on { color: #fff; background: #FFA800; font-weight: 600; }
.add-link { font-size: 28rpx; color: #FFA800; font-weight: 600; }
.tp-del { font-size: 38rpx; color: #666; padding: 0 8rpx; flex-shrink: 0; }

/* 搜索 */
.search-bar { position: relative; margin: 16rpx 24rpx; }
.search-input { width: 100%; height: 72rpx; background: #fff; border-radius: 36rpx; padding: 0 72rpx 0 28rpx; font-size: 30rpx; box-sizing: border-box; border: 0; }
.search-clear { position: absolute; right: 24rpx; top: 50%; transform: translateY(-50%); font-size: 34rpx; color: #666; }

/* 标题推荐 + 清空 */
.title-input-wrap { position: relative; display: flex; align-items: center; }
.title-input-wrap .form-input { padding-right: 120rpx; }
.title-input-wrap textarea.title-ta { height: auto; min-height: 64rpx; max-height: 124rpx; line-height: 1.4; padding: 11rpx 78rpx 11rpx 20rpx; resize: none; overflow-y: auto; box-sizing: border-box; display: block; }
.title-ta::-webkit-scrollbar { width: 6rpx; }
.title-ta::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.12); border-radius: 3rpx; }
.title-ta::-webkit-scrollbar-track { background: transparent; }
.title-clear { position: absolute; right: 12rpx; top: 50%; transform: translateY(-50%); font-size: 44rpx; color: #999; line-height: 1; padding: 0 8rpx; cursor: pointer; }
/* 文本框空时：叉淡化 */
.title-clear.dim { opacity: 0.25; }
/* 必填标记：灰色小字 + 红星（会议名称在文本框右上角绝对定位，会议议题在标题行右侧） */
.req-star { color: #E4572E; font-weight: 700; }
/* 必填未填的红框提醒：点进对应输入框（focus）即消失 */
.field-error { border-color: #E4572E !important; box-shadow: 0 0 0 2rpx rgba(228,87,46,0.16); }
/* 推荐标题：半透明覆盖在文本框内，点文字自动填入 */
.title-ghost { position: absolute; left: 20rpx; top: 13rpx; right: 78rpx; font-size: 32rpx; font-weight: 600; color: rgba(31, 32, 36, 0.32); line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
/* 会议名称：输入框 + 右侧圆形麦克风按钮 同排 */
.title-row { display: flex; align-items: center; gap: 16rpx; }
.title-row .title-input-wrap { flex: 1; min-width: 0; }
/* 语音输入：框右实心橙圆钮（纯图标，方案④），标题与议题共用 */
.voice-mic-btn { flex-shrink: 0; width: 72rpx; height: 72rpx; border-radius: 50%; background: #1A5F9E; border: none; padding: 0; display: flex; align-items: center; justify-content: center; box-shadow: 0 4rpx 12rpx rgba(26,95,158,0.28); }
.voice-mic-btn svg { width: 34rpx; height: 34rpx; display: block; }
.voice-mic-btn:active { background: #124B85; }
.voice-mic-btn.on { background: #2E7BC4; animation: vi-pulse 1.2s ease-in-out infinite; }

/* 会议地点下拉 */
.loc-select { width: 100%; margin-top: 16rpx; appearance: none; -webkit-appearance: none; padding-right: 60rpx; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='22' height='22' viewBox='0 0 20 20'%3E%3Cpath fill='%23999' d='M5 7l5 5 5-5z'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 20rpx center; }
.loc-row { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; }
.loc-row .loc-select { flex: 1; min-width: 0; width: auto; margin-top: 0; }
.loc-map-btn { flex-shrink: 0; width: 96rpx; align-self: stretch; display: flex; align-items: center; justify-content: center; background: #E8F0FE; border: 2rpx solid #1A73E8; border-radius: 16rpx; box-sizing: border-box; }
.loc-map-btn svg { width: 46rpx; height: 46rpx; display: block; }
.loc-map-btn:active { background: #D6E4FC; }
.loc-row .loc-input { flex: 1; min-width: 0; width: auto; }
.loc-other { margin-top: 16rpx; }

/* 日期/时间选择字段 */
.time-field, .date-field { justify-content: space-between; }
.tf-text { font-size: 32rpx; color: #1f2329; }
.tf-arrow { color: #999; font-size: 28rpx; }

/* 列选择器弹窗（日期/时间共用） */
.picker-pop-mask { position: fixed; inset: 0; z-index: 210; background: rgba(0,0,0,0.42); display: flex; align-items: center; justify-content: center; padding: 48rpx; box-sizing: border-box; }
/* 小日历（月历网格） */
.cal-pop { width: 86%; max-width: 620rpx; background: #fff; border-radius: 24rpx; padding: 28rpx 24rpx calc(20rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.cal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.cal-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.cal-nav { width: 76rpx; height: 76rpx; display: flex; align-items: center; justify-content: center; font-size: 52rpx; color: var(--c-primary-dark); font-weight: 700; }
.cal-nav:active { opacity: 0.5; }
.cal-week { display: grid; grid-template-columns: repeat(7, 1fr); margin-bottom: 8rpx; }
.cal-wd { text-align: center; font-size: 26rpx; color: #999; padding: 8rpx 0; }
.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 6rpx; }
.cal-cell { height: 78rpx; display: flex; align-items: center; justify-content: center; font-size: 32rpx; color: #1f2329; border-radius: 12rpx; }
.cal-cell.empty { visibility: hidden; }
.cal-cell.disabled { color: #C7CDD5; background: transparent; cursor: not-allowed; }
.cal-cell.today { color: var(--c-primary-dark); font-weight: 700; }
.cal-cell.on { background: #3E6BA8; color: #fff; font-weight: 700; }
.cal-cell:not(.empty):not(.on):not(.disabled):active { background: #E6EDF8; }
.picker-pop { position: relative; width: 100%; max-width: 660rpx; background: #fff; border-radius: 26rpx; padding: 28rpx 26rpx 38rpx; box-sizing: border-box; }
/* 时间：大按钮点选网格（免滚动） */
.tg-cur { text-align: center; font-size: 64rpx; font-weight: 700; color: #8B5E34; letter-spacing: 2rpx; margin: 4rpx 0 12rpx; }
.tg-label { font-size: 28rpx; color: #999; margin: 2rpx 2rpx 8rpx; }
.tg-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14rpx; }
.tg-grid-m { margin-bottom: 4rpx; }
.tg-cell { height: 88rpx; display: flex; align-items: center; justify-content: center; font-size: 36rpx; color: #1f2329; background: #f5f6f8; border-radius: 14rpx; }
.tg-cell.on { background: #E6EDF8; color: #2F5E96; font-weight: 700; box-shadow: inset 0 0 0 3rpx #3E6BA8; }
.tg-cell:not(.on):active { background: #E6EDF8; }
.tg-cell.disabled { color: #C4CAD2; background: #F7F8FA; box-shadow: none; cursor: not-allowed; }
.tg-cell.disabled:active { background: #F7F8FA; }
.pp-head { text-align: center; font-size: 52rpx; font-weight: 700; color: #1f2329; margin: 0 0 8rpx; }
.picker-pop > .pop-close { position: absolute; top: 12rpx; right: 12rpx; z-index: 2; margin: 0; }
.cal-pop > .pop-close { display: flex; justify-content: flex-end; margin-bottom: 4rpx; }
.pp-cols { display: flex; gap: 16rpx; }
.pp-col { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.pp-col-label { text-align: center; font-size: 28rpx; color: #666; margin-bottom: 10rpx; }
.pp-col-scroll { height: 460rpx; overflow-y: auto; background: #f7f8fa; border-radius: 16rpx; padding: 8rpx; box-sizing: border-box; -webkit-overflow-scrolling: touch; }
.pp-item { display: flex; align-items: center; justify-content: center; height: 76rpx; font-size: 34rpx; color: #333; border-radius: 12rpx; margin: 4rpx 0; }
.pp-item.on { color: #fff; background: #FFA800; font-weight: 700; }
.pp-actions { display: flex; gap: 18rpx; margin-top: 24rpx; justify-content: center; }
.pp-actions .btn { flex: 0 0 60%; width: 60%; }
.picker-pop .pp-actions .btn-primary { background: #3E6BA8; border-color: #3E6BA8; color: #fff; }


/* 议题摘要行 */
.topic-summary { background: #fff; border: 2rpx solid #eee; border-radius: 18rpx; padding: 22rpx 24rpx; margin-top: 16rpx; }
.ts-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-bottom: 14rpx; }
.ts-num { font-size: 26rpx; color: #8a9099; font-weight: 600; }
.ts-title { font-size: 32rpx; color: #1f2329; line-height: 1.45; word-break: break-all; }
.ts-badge { font-size: 25rpx; padding: 6rpx 18rpx; border-radius: 20rpx; white-space: nowrap; line-height: 1.5; }
.ts-badge.badge-notice { color: #1677B8; background: #E6F4FB; }
.ts-badge.badge-discussion { color: #2E8B57; background: #EAF6EE; }
.ts-badge.badge-decision { color: #D56A16; background: #FFF0E5; }
.ts-actions { display: flex; justify-content: flex-end; gap: 16rpx; margin-top: 18rpx; padding-top: 16rpx; border-top: 2rpx solid #f0f0f0; }
.ts-edit-btn { font-size: 27rpx; color: #C77800; background: #fff; border: 2rpx solid #F0A020; padding: 10rpx 30rpx; border-radius: 22rpx; white-space: nowrap; line-height: 1.5; }
.ts-edit-btn:active { background: #FFF6E5; }
.ts-del-btn { font-size: 27rpx; color: #C0392B; background: #fff; border: 2rpx solid #E6B0AA; padding: 10rpx 30rpx; border-radius: 22rpx; white-space: nowrap; line-height: 1.5; }
.ts-del-btn:active { background: #FDF2F0; }

/* 议题编辑弹窗 */
.topic-dialog-mask { position: fixed; inset: 0; z-index: 210; background: rgba(0,0,0,0.42); display: flex; align-items: flex-end; }
.topic-dialog { width: 100%; background: #fff; border-radius: 28rpx 28rpx 0 0; max-height: 88vh; display: flex; flex-direction: column; }
.td-head { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 28rpx 18rpx; border-bottom: 2rpx solid #f2f2f2; }
.td-title { font-size: 34rpx; font-weight: 700; color: #1f2329; }
.td-body { padding: 26rpx 28rpx; overflow-y: auto; }
.td-actions { display: flex; gap: 18rpx; padding: 18rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); border-top: 2rpx solid #f2f2f2; }

/* 语音按钮行 */
.vi-row { display: flex; align-items: center; gap: 14rpx; }
.vi-btn {
  flex-shrink: 0;
  width: 72rpx; height: 72rpx;
  border-radius: 50%;
  border: 2rpx solid #d0d0d0;
  background: #f7f8fa;
  font-size: 32rpx;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}
.vi-btn.on {
  background: #e8f0ff;
  border-color: #0051FF;
  animation: vi-pulse 1.2s ease-in-out infinite;
}
@keyframes vi-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0, 81, 255, 0.35); }
  50%       { box-shadow: 0 0 0 10rpx rgba(0, 81, 255, 0); }
}

/* 议题双按钮行 */
.topic-add-label { display: block; font-size: 28rpx; color: #8a9099; font-weight: 600; margin-top: 20rpx; margin-bottom: 8rpx; }
.topic-add-row { display: flex; gap: 20rpx; margin-top: 8rpx; }
.topic-add-btn { flex: 1; height: 80rpx; font-size: 30rpx; border-radius: 16rpx; }

/* 流式语音输入弹窗 */
.voice-modal-mask { position: fixed; inset: 0; z-index: 300; background: rgba(0,0,0,0.5); display: flex; align-items: flex-end; }
.voice-modal {
  width: 100%; background: #fff; border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom));
  display: flex; flex-direction: column; gap: 24rpx;
}
.vm-head { display: flex; align-items: baseline; justify-content: space-between; }
.vm-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.vm-hint { font-size: 26rpx; color: #999; }
.vm-body {
  background: #f7f8fa; border-radius: 20rpx;
  padding: 24rpx 20rpx; min-height: 160rpx;
  display: flex; flex-direction: column; align-items: center; gap: 18rpx;
}
.vm-wave { display: flex; align-items: flex-end; gap: 8rpx; height: 48rpx; }
.vm-wave span {
  width: 8rpx; border-radius: 4rpx; background: #0051FF;
  animation: vm-bar 1.1s ease-in-out infinite;
}
.vm-wave span:nth-child(1) { height: 20rpx; animation-delay: 0s; }
.vm-wave span:nth-child(2) { height: 36rpx; animation-delay: 0.15s; }
.vm-wave span:nth-child(3) { height: 48rpx; animation-delay: 0.3s; }
.vm-wave span:nth-child(4) { height: 36rpx; animation-delay: 0.45s; }
.vm-wave span:nth-child(5) { height: 20rpx; animation-delay: 0.6s; }
@keyframes vm-bar {
  0%, 100% { transform: scaleY(0.4); opacity: 0.6; }
  50%       { transform: scaleY(1);   opacity: 1;   }
}
.vm-text { font-size: 32rpx; color: #1f2329; line-height: 1.6; text-align: center; width: 100%; word-break: break-all; }
.vm-interim { color: #888; }
.vm-placeholder { color: #aaa; }
.vm-actions { display: flex; gap: 20rpx; }
.vm-actions .btn { flex: 1; height: 88rpx; font-size: 34rpx; border-radius: 18rpx; }
/* ============================================================
   发起业委会页 · 适老化（仅本弹层生效，全部以 .create-panel 收口，
   不影响首页/接待等复用同名类的地方）
   面向退休业主：正文/标签明显放大、点击区≥44px、中性灰文字≥4.5:1、行距放宽
   ============================================================ */
/* — 对比度：浅灰/半透明文字提到达标下限 — */
.create-panel .title-ghost { color: #6b7078; }                 /* 推荐标题：32%透明→实色 */
.create-panel .fl-value.ph,
.create-panel .meeting-info-card .fl-value.ph { color: #6b7078; font-size: 32rpx; } /* 选择日期/时间/地点 占位 16px */
.create-panel .title-clear { color: #6b7078; }                 /* 名称清除× 提深 */
.create-panel .fl-arrow { font-size: 30rpx; color: #8a9099; }  /* 右侧箭头 15px */
.create-panel .ds-t { font-size: 32rpx; }                      /* 拍照卡标题 16px */
.create-panel .form-label { color: #5f636b; font-size: 32rpx; }/* 议题标签 16px */
/* — 字号：展示型字段(点选/自动填，不必更大)统一 16px；标题类 17px — */
.create-panel .meeting-info-card .caption-as-title { font-size: 28rpx; }  /* 会议名称标签 14px（退为次级） */
.create-panel .section-title { font-size: 28rpx; }             /* 会议议题标签 14px（退为次级） */
.create-panel .topic-head .section-title { font-size:30rpx; }
.create-panel .fl-label { font-size: 28rpx; }                  /* 日期/时间/地点标签 14px */
.create-panel .meeting-method-line > .fl-label { font-size: 28rpx; } /* 召开方式标签 14px */
.create-panel .field-caption { font-size: 28rpx; }             /* 字段说明 14px */
.create-panel .fl-value { font-size: 32rpx; }                  /* 选中值 16px（点选，不必18px） */
.create-panel .form-input.large,
.create-panel .form-input.large::placeholder { font-size: 32rpx; } /* 名称/议题输入 16px */
.create-panel .juwei-title { font-size: 32rpx; }               /* 居委会见证主标题 16px */
.create-panel .juwei-sub { font-size: 28rpx; }                 /* 制度要求小字 14px */
.create-panel .topic-line-text { font-size: 28rpx; }           /* 已添加议题内容低于分区标题 */
.create-panel .tat-text { font-size: 32rpx; }                  /* 点此添加议题 16px */
.create-panel .create-tab { font-size: 30rpx; white-space: nowrap; min-height: 58rpx; padding:8rpx 0; }  /* Tab 总高度较原版压缩约 30% */
.create-panel .create-tab.active { background: #A85800; box-shadow: 0 6rpx 16rpx rgba(168,88,0,0.22); } /* 选中态白字对比 3.2→5.2:1，与主按钮同色(--c-primary-dark) */
/* — 视觉层级(适老修正)：标签=小(14px)·中灰(#6b7078,≥4.5:1可读)·常规；
   值=大(17px)·深黑·粗。层级靠大小/粗细差，不靠低对比洗白标签。 — */
.create-panel .fl-label,
.create-panel .meeting-method-line > .fl-label { color: #667B88; font-weight: 500; }
.create-panel .meeting-info-card .caption-as-title,
.create-panel .section-title { color: #8A540D; font-weight: 600; letter-spacing: 1rpx; }
.create-panel .juwei-title { color: #24364B; font-weight: 600; } /* 主标题短句后升格，小字负责解释 */
.create-panel .form-label { color: #667B88; font-weight: 500; }
.create-panel .fl-value { color: #24364B; font-weight: 700; font-size: 34rpx; }   /* 具体值统一深蓝黑 */
.create-panel .form-input.large { color: #24364B; font-weight: 700; }
.create-panel .topic-line-text { color: #2C3E70; font-weight: 400; }  /* 议题正文：深靛蓝(≈9:1)替代硬黑，清爽墨水感；序号仍<b>加粗保结构 */
.create-panel .topic-line-text b { color:#B46A12; }
/* — 议题「确定添加」：整宽底部按钮，蓝底(与橙色「生成通知」区分)，防误点 — */
.create-panel .tie-confirm-btn { display: block; width: 100%; height: 88rpx; margin-top: 18rpx; border: 0; border-radius: 16rpx; background: #3F6078; color: #fff; font-size: 32rpx; font-weight: 700; }
.create-panel .tie-confirm-btn:active { background: #33506A; }
.create-panel .tie-notice-toggle { display: inline-block; padding: 12rpx 0; font-size: 30rpx; color: #5B7C96; }
/* — 行距/卡片间距整体收紧，把「居委会见证」挤进短屏首屏 + 三行灰字左对齐 — */
.create-panel .field-line { padding: 14rpx 20rpx; }
.create-panel .field-line-split { padding: 0; }
.create-panel .meeting-info-card .fl-part { padding: 14rpx 20rpx; gap: 12rpx; }
.create-panel .meeting-info-card .fl-part .fl-label { width: 100rpx; flex-shrink: 0; }
.create-panel .fl-loc-main { padding: 0; gap: 12rpx; }
.create-panel .fl-loc-main .fl-label { width: 100rpx; flex-shrink: 0; }
/* 卡片间距/输入框高度收紧（省高度大头：原 section 间距 31rpx → 16rpx） */
.create-panel .create-body { padding-top: 10rpx; padding-bottom: 28rpx; }
.create-panel .create-tabs { margin-bottom: 12rpx; }
.create-panel .create-section,
.create-panel .create-section.meeting-info-card { margin-bottom: 22rpx; padding-top: 14rpx; padding-bottom: 14rpx; }
.create-panel .meeting-info-card .form-group { margin-bottom: 6rpx; }
.create-panel .meeting-info-card .caption-as-title { margin-bottom: 6rpx; }
.create-panel .meeting-info-card .field-list { gap: 10rpx; }
.create-panel .form-input.large { height: 64rpx; min-height: 64rpx; }
.create-panel .title-input-wrap textarea.title-ta { min-height: 56rpx; }
.create-panel .juwei-card { margin-top: 0; margin-bottom: 22rpx; padding: 20rpx 18rpx; }
/* — 点击区：保持 ≥44px（88rpx） — */
.create-panel .type-chip { min-height: 88rpx; padding: 10rpx 30rpx; color: #5f636b; font-size: 34rpx; } /* 44px, 17px字 */
.create-panel .method-switch button { min-height: 76rpx; padding: 14rpx 26rpx; font-size: 32rpx; }      /* 线下/线上 38px */
.create-panel .platform-select { height: 88rpx; font-size: 32rpx; flex-basis: 330rpx; padding: 0 40rpx 0 24rpx; }  /* 线上平台下拉 44px；加宽+减右留白，让「微信工作群」完整显示 */
.create-panel .field-map-btn { width: 84rpx; height: 76rpx; min-height: 76rpx; } /* 地图键 38px */
.create-panel .topic-add-trigger { min-height: 92rpx; }        /* 添加议题条 46px */
/* — bug修复：议题列表去掉内层限高(原200rpx裁掉换行议题)，交给弹层整体滚动 — */
.create-panel .topic-list { max-height: none; overflow: visible; }
/* — 精简·扁平化：浅灰底衬白卡、去边框留微阴影；字段行去内框、改细分隔线，消除「盒套盒」 — */
.create-panel .create-body { background: #f5f6f8; }
.create-panel .create-section,
.create-panel .create-section.meeting-info-card { background: #fff; border: none; box-shadow: 0 2rpx 10rpx rgba(30,40,60,0.05); }
.create-panel .juwei-card { border: none; box-shadow: 0 2rpx 10rpx rgba(30,40,60,0.05); }
.create-panel .meeting-info-card .field-list { gap: 0; }
.create-panel .meeting-info-card .field-line { border: none; border-radius: 0; background: transparent; border-bottom: 2rpx solid #eef0f2; }
.create-panel .meeting-info-card .field-line:last-child { border-bottom: none; }
/* — 三行等高，分隔线均匀（原召开方式59/日期时间37/地点52px 不齐）：统一最小高度+上下居中 — */
.create-panel .meeting-info-card .field-line { min-height: 120rpx; padding-top: 0; padding-bottom: 0; box-sizing: border-box; }
.create-panel .meeting-info-card .fl-part { padding-top: 0; padding-bottom: 0; }
/* 召开方式、地点两行各缩 5px(→55px)，日期/时间保持 60px */
.create-panel .meeting-info-card .meeting-method-line,
.create-panel .meeting-info-card .field-line-location { min-height: 110rpx; }
/* 日期/时间/地点选中值统一左对齐（贴标签，便于阅读） */
.create-panel .fl-loc-main .fl-value,
.create-panel .fl-part .fl-value { text-align: left; }
/* 重大事项(居委会见证)开关：打开后为绿色 */
.create-panel .juwei-switch.on { background: #2E8B57; }
/* 线上会议不支持重大事项：整卡置灰（开关仍可点——点了弹提示说明原因） */
.create-panel .juwei-card.disabled { opacity: 0.6; }
</style>
