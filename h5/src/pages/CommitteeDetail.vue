<template>
  <div class="detail-page">
    <PageNav :title="navTitle" style="margin:-12px -12px 0;">
      <!-- 左箭头统一返回来源页；通知准备页仍保留右上角“首页”作为独立入口 -->
      <template #left>
        <div class="nav-back" @click="handleDetailBack">‹</div>
      </template>
      <template #right v-if="showNoticeNav">
        <button class="nav-home" @click="goHome">首页</button>
      </template>
    </PageNav>

    <!-- AI 生成党建新闻：红色党建风工作遮罩，完成后进入独立新闻页 -->
    <AiWorkingOverlay :active="generatingNews" phase="news" theme="party" @confirm="onNewsDone" @close="onNewsClose" />
    <!-- 会议纪要在详情页后台生成；最小化后悬浮入口仍留在当前详情页 -->
    <AiWorkingOverlay v-if="minutesOverlayShown" :active="generatingMinutes" phase="gen" @confirm="onMinutesDone" @close="onMinutesClose" />
    <div v-if="detail" class="detail-body">
      <!-- 简化阶段：进行中/已结束会议也允许主任删除（清理测试数据用；准备阶段用会议头部的取消按钮） -->

      <!-- 任务横幅（结束阶段隐藏；主任准备阶段用步骤条替代） -->
      <div class="task-banner" :class="detail.taskLevel" v-if="detail.stage !== 'ended' && !(userView === 'chair' && detail.stage === 'preparing')">
        <span class="tb-title">{{ detail.taskTitle }}</span>
        <span class="tb-items" v-if="detail.taskItemsText">{{ detail.taskItemsText }}</span>
        <span class="tb-flow">当前：{{ detail.flowNodeText }}</span>
        <span class="tb-hint" v-if="detail.taskHint">{{ detail.taskHint }}</span>
      </div>

      <!-- ══════════ 主任准备阶段（卡片流布局） ══════════ -->
      <template v-if="userView === 'chair' && detail.stage === 'preparing' && !noticePackageVisible">

        <!-- 通知卡片：发给委员的核心内容，也是生成转发图片的源 -->
        <div class="notice-card">
          <div class="nc-copy">
            <div class="nc-copy-title">新会议通知：{{ detail.title || '业委会会议' }}</div>
            <div class="nc-copy-row">会议时间：{{ fmtCnDate(detail.meetingDate) }} {{ fmtHm(detail.meetingTime) }}</div>
            <div class="nc-copy-row">会议地点：<span v-if="detail.location" class="loc-inline" @click="openMap(detail.location)">{{ detail.location }}</span><span v-else class="nc-muted">待定</span></div>
            <div v-if="detail.location" class="nc-copy-row">地点导航：<span class="loc-inline" @click="openMap(detail.location)">打开地图导航</span></div>
            <div class="nc-copy-row">会议议题：{{ noticeTopicsText }}</div>
            <div class="nc-copy-note">请各位委员准时参加。</div>
            <div class="nc-copy-sign">业主委员会</div>
          </div>
        </div>

        <!-- 取消会议（弱化，避免误删） -->
        <div class="prep-cancel"><span @click="removeMeeting">取消会议</span></div>

        <!-- 通知人员：默认展开并全选，可直接调整名单 -->
        <div class="recipient-card">
          <div class="recipient-card-head" @click="recipientOpen = !recipientOpen">
            <span class="recipient-card-title">通知人员</span>
            <div class="recipient-card-right">
              <span class="recipient-summary">{{ recipientSummary }}</span>
              <span class="recipient-card-arrow" :class="{ open: recipientOpen }">›</span>
            </div>
          </div>
          <template v-if="recipientOpen">
            <div class="rcp-list page-rcp-list">
              <!-- 全选行：挪进列表顶部（收起头只留摘要） -->
              <div class="rcp-item page-rcp-item rcp-all-row" @click="toggleRecipientAll">
                <div class="rcp-check" :class="{ on: recipientAllChecked }">{{ recipientAllChecked ? '✓' : '' }}</div>
                <div class="rcp-person"><span class="rcp-name">全选</span></div>
                <span class="rcp-all-count">已选 {{ recipientSelectedCount }} / {{ recipientList.length }} 人</span>
              </div>
              <div v-for="m in recipientList" :key="m.userRoleId" class="rcp-item page-rcp-item" @click="toggleRecipient(m.userRoleId)">
                <div class="rcp-check" :class="{ on: m.checked }">{{ m.checked ? '✓' : '' }}</div>
                <div class="rcp-person">
                  <span class="rcp-name">{{ m.name }}</span>
                  <span v-if="m.role" class="rcp-role">{{ m.role }}</span>
                </div>
              </div>
              <div v-if="!recipientList.length" class="rcp-empty">暂无可通知的委员</div>
            </div>
          </template>
        </div>

        <!-- 通知记录：标题 + 历史列表（最新在前） -->
        <div class="sr-section" v-if="noticeSent">
          <div class="sr-heading-row">
            <span class="sr-heading">通知记录</span>
            <span class="sr-clear-btn" @click="clearNotices">清空</span>
          </div>
          <template v-if="detail.notificationLogs && detail.notificationLogs.length">
            <div class="send-record" v-for="(log, idx) in [...detail.notificationLogs].reverse()" :key="idx">
              <span class="sr-ic">✓</span>
              <span class="sr-text">{{ logText(log) }}</span>
            </div>
          </template>
          <div class="send-record" v-else>
            <span class="sr-ic">✓</span>
            <span class="sr-text">{{ sendRecordText }}</span>
          </div>
        </div>

      </template>

      <!-- 会议基本信息（结束阶段隐藏；主任准备阶段用上方卡片流替代） -->
      <div class="info-card" v-if="detail.stage !== 'ended' && !(userView === 'chair' && detail.stage === 'preparing')">
        <div class="info-row"><span class="info-k">时间</span>{{ detail.meetingDate }} {{ detail.meetingTime }}</div>
        <div class="info-row"><span class="info-k">地点</span><span class="loc-link" v-if="detail.location" @click="openMap(detail.location)">{{ detail.location }}<span class="loc-nav">导航 ›</span></span><span v-else>—</span></div>
        <div class="info-row" v-if="detail.description"><span class="info-k">说明</span>{{ detail.description }}</div>
      </div>

      <div class="notice-package-card" v-if="noticePackageVisible">
        <div class="npc-head">
          <div>
            <span class="npc-title">会议通知与材料</span>
            <span class="npc-sub">{{ detail.stage === 'ongoing' ? '会议已开始，仍可先查看会前送达内容' : '会前送达内容' }}</span>
          </div>
          <span class="npc-badge">已送达</span>
        </div>
        <div class="npc-section" v-if="detail.noticeDraft">
          <span class="npc-section-title">会议通知</span>
          <span class="npc-content">{{ detail.noticeDraft.content }}</span>
        </div>
        <div class="npc-section">
          <span class="npc-section-title">会议材料（{{ detail.materials ? detail.materials.length : 0 }}）</span>
          <div v-if="detail.materials && detail.materials.length">
            <div v-for="(item, index) in detail.materials" :key="item.id || item.name" class="mt-item" @click="item.url ? openMaterialViewer(item) : previewMaterial(index)">
              <img v-if="item.url && isImageFile(item.url, item.fileType)" :src="item.url" class="file-thumb" @click.stop="openMaterialViewer(item)" />
              <span class="mt-name">{{ item.name }}</span>
              <span v-if="item.ocrStatus === 'processing'" class="ocr-badge ocr-proc">识别中…</span>
              <span v-else-if="item.ocrStatus === 'done'" class="ocr-badge ocr-done">✓ 已识别</span>
              <span v-else-if="item.ocrStatus === 'failed'" class="ocr-badge ocr-fail">识别失败</span>
              <span class="mt-size">{{ item.sizeText || '' }}</span>
              <span class="mt-arrow">›</span>
            </div>
          </div>
          <span v-else class="npc-empty">暂无会议材料</span>
        </div>
      </div>

      <!-- 会议材料：委员仅在材料送达后可见（主任的材料卡在上方卡片流） -->
      <div class="materials-card" v-if="!noticePackageVisible && detail.stage === 'preparing' && userView === 'member' && detail.myDelivery && detail.myDelivery.materialDelivered && detail.materials && detail.materials.length">
        <div class="mt-head-row">
          <span class="mt-title">会议材料（{{ detail.materials ? detail.materials.length : 0 }}）</span>
        </div>
        <div v-if="detail.materials && detail.materials.length">
          <div v-for="(item, index) in detail.materials" :key="item.id || item.name" class="mt-item" @click="item.url ? openMaterialViewer(item) : previewMaterial(index)">
            <img v-if="item.url && isImageFile(item.url, item.fileType)" :src="item.url" class="file-thumb" @click.stop="openMaterialViewer(item)" />
            <span class="mt-name">{{ item.name }}</span>
            <span v-if="item.ocrStatus === 'processing'" class="ocr-badge ocr-proc">识别中…</span>
            <span v-else-if="item.ocrStatus === 'done'" class="ocr-badge ocr-done">✓ 已识别</span>
            <span v-else-if="item.ocrStatus === 'failed'" class="ocr-badge ocr-fail">识别失败</span>
            <span class="mt-size">{{ item.sizeText || '' }}</span>
            <span class="mt-arrow">›</span>
            <span v-if="userView === 'chair'" class="mt-del" @click.stop="removeMaterial(item)">×</span>
          </div>
        </div>
        <div v-else class="mt-empty">
          <span v-if="userView === 'chair'">暂未上传材料，点击上方"上传材料"添加</span>
          <span v-else>暂无会议材料</span>
        </div>
      </div>

      <!-- ══════════ 委员 和 主任/副主任 共用：个人操作 ══════════ -->
      <template v-if="userView === 'member' || userView === 'chair'">
        <!-- 准备阶段：委员个人送达状态（主任不显示个人确认卡——发送时已自动计入已读，用上方送达进度卡管理） -->
        <template v-if="userView === 'member' && !noticePackageVisible && detail.stage === 'preparing' && detail.myDelivery">
          <!-- 通知已送达 → 显示通知草稿 -->
          <div class="my-delivery-card" v-if="detail.myDelivery.noticeDelivered">
            <span class="mdc-title">📨 会议通知</span>
            <span class="mdc-big" :class="detail.mySignedIn ? 'ok' : (detail.myDeclined ? 'declined' : '')">{{ detail.mySignedIn ? '✅ 你已确认参会' : (detail.myDeclined ? '已登记：因故缺席' : '收到会议通知，请确认是否参加') }}</span>
            <div v-if="!detail.mySignedIn && !detail.noticeDraft" class="attend-actions">
              <button class="attend-btn primary" @click="confirmAttend">确认参会</button>
              <button class="attend-btn ghost" @click="declineAttend">无法参会</button>
            </div>
          </div>
          <div class="notice-draft-card" v-if="detail.myDelivery.noticeDelivered && detail.noticeDraft">
            <div class="nd-head">
              <span class="nd-title">会议通知</span>
              <span class="nd-status ok">{{ detail.mySignedIn ? '已确认参会' : (detail.myDeclined ? '因故缺席' : '待确认') }}</span>
            </div>
            <span class="nd-content">{{ detail.noticeDraft.content }}</span>
            <div v-if="!detail.mySignedIn" class="attend-actions">
              <button class="attend-btn primary" @click="confirmAttend">确认参会</button>
              <button class="attend-btn ghost" @click="declineAttend">无法参会</button>
            </div>
          </div>
          <!-- 通知未送达 -->
          <div class="my-delivery-card" v-else-if="!detail.myDelivery.noticeDelivered">
            <span class="mdc-title">会议通知</span>
            <span class="mdc-big">尚未送达，请等待主任发送通知</span>
          </div>
          <!-- 材料送达状态 -->
          <div class="my-delivery-card" v-if="detail.myDelivery.noticeDelivered && detail.materials && detail.materials.length">
            <span class="mdc-title">📎 会议材料</span>
            <span class="mdc-big" :class="detail.myDelivery.materialDelivered ? 'ok' : ''">{{ detail.myDelivery.materialDelivered ? '材料已送达' : '材料尚未送达' }}</span>
          </div>
        </template>

        <!-- 进行中：进入「会议进行」向导页 -->
        <template v-if="detail.stage === 'ongoing' && detail.record">
          <div class="live-entry" @click="enterLive">
            <div class="live-entry-main">
              <span class="live-entry-title">会议进行中</span>
              <span class="live-entry-sub">签到 → 录音转写 → 确认表决</span>
            </div>
            <span class="live-entry-arrow">进入 ›</span>
          </div>
          <div class="signin-prog" v-if="userView === 'chair' && detail.flowStats && detail.flowStats.attendance">
            <div class="sp-head">
              <span class="sp-label">签到进度</span>
              <span class="sp-count">{{ detail.flowStats.attendance.signedInCount }}/{{ detail.flowStats.attendance.total }} 人已签到</span>
            </div>
            <div class="sp-bar"><div class="sp-fill" :style="{ width: detail.flowStats.attendance.pct + '%' }"></div></div>
          </div>
          <MeetingTopicsCard :topics="detail.record ? detail.record.topics : []" :on-select="openTopicSheet" />
        </template>

      </template>

      <!-- ══════════ 结束阶段 ══════════ -->
      <template v-if="detail.stage === 'ended'">
        <!-- 会议录音存档（委员/主任可见，外部无 record 自动隐藏）-->
        <div v-if="detail.record && detail.record.recordingUrl" class="rec-archive-card">
          <div class="rac-head">
            <span class="rac-title">🎙 会议录音</span>
            <span class="rac-sub">{{ recAudioPlaying ? '播放中…' : '会议全程录音存档' }}</span>
          </div>
          <div class="rac-actions">
            <button class="btn btn-outline mini" @click="toggleRecording">{{ recAudioPlaying ? '暂停' : '试听' }}</button>
            <button class="btn btn-ghost mini" @click="downloadRecording">下载/转发</button>
          </div>
        </div>

        <!-- 委员：简洁结论 -->
        <template v-if="userView === 'member'">
          <div class="ar-verdict" :class="detail.compliance">
            <div class="arv-icon">{{ detail.compliance === 'valid' ? '✓' : detail.compliance === 'flawed' ? '!' : '✕' }}</div>
            <div class="arv-info">
              <span class="arv-title">{{ detail.title }}</span>
              <span class="arv-meta">{{ detail.meetingDate }} {{ detail.meetingTime }} · {{ detail.location }}</span>
              <span class="arv-result">{{ detail.compliance === 'valid' ? '会议有效' : detail.compliance === 'flawed' ? '有效（带说明）' : '会议无效' }} · {{ detail.publish && detail.publish.published ? '已公示归档' : detail._archived ? '已归档' : '待公示' }}</span>
              <span class="arv-reason" v-if="detail.complianceReason">{{ detail.complianceReason }}</span>
            </div>
          </div>
          <MeetingTopicsCard :topics="detail.record ? detail.record.topics : []" :on-select="openTopicSheet" />
          <div class="member-doc-actions">
            <button class="doc-btn" @click="viewMinutes">查看会议纪要</button>
            <button class="doc-btn ghost" @click="viewPublicMinutes">查看公开纪要</button>
          </div>
        </template>

        <!-- 主任：简洁会议卡（名称/时间/地点），点击展开完整详情（议题、纪要、材料、记录） -->
        <template v-if="userView === 'chair'">
          <div class="ar-card" :class="[detail.compliance, cardSizeClass]">
            <div class="arc-summary arc-summary-static">
              <span class="arch-title">{{ detail.title }}</span>
              <span class="arcs-meta">{{ detail.meetingDate }} {{ shortTime(detail.meetingTime) }}<template v-if="detail.location"> · {{ detail.location }}</template></span>
            </div>
            <MeetingTopicsCard :topics="detail.record ? detail.record.topics : []" :on-select="openTopicSheet"
              :expanded="endedDetailOpen" :on-collapse="() => { endedDetailOpen = false }" :on-expand="() => { endedDetailOpen = true }" />
            <!-- 纪要会后在此生成：未生成→「生成会议纪要」(跳纪要页看进度)；已生成→「查看会议纪要」。公示后隐藏（纪要已定稿，改看下方「查看公示内容」） -->
            <template v-if="!(detail.publish && detail.publish.published)">
              <button v-if="detail.minutesReady" class="ended-minutes-btn in-card" @click="viewMinutes">查看会议纪要</button>
              <template v-else>
              <div class="minutes-basis">
                <div class="mb-head">
                  <span class="mb-sub">将综合以下会议记录自动整理</span>
                </div>
                <!-- 录音的「试听」和「看转写」合成一行两个动作，不再出现两行同名「会议录音」 -->
                <div class="mb-row mb-row-static">
                  <span class="mb-copy"><b>会议录音</b><small>{{ hasRecordings ? (recTotalDurText || '已保存') : '暂无录音' }}</small></span>
                  <span class="mb-acts">
                    <span v-if="hasRecordings" class="mb-act mb-act-tap" @click="toggleRecPlayback">{{ recAudioPlaying ? '播放中…' : '试听' }}</span>
                    <span v-if="hasRecordings" class="mb-act-sep">·</span>
                    <span class="mb-act mb-act-tap" @click="toggleBasis">{{ basisLoading ? '加载中…' : (basisOpen ? '收起转写' : '看转写') }} ›</span>
                  </span>
                </div>
                <div class="mb-transcript" v-if="basisOpen">{{ basisTranscript || '暂无转写内容' }}</div>
                <div class="mb-row" @click="endedDetailOpen = true">
                  <span class="mb-copy"><b>议题结果</b><small>讨论、投票与意见</small></span>
                  <span class="mb-act">查看 ›</span>
                </div>
              </div>
              <button class="ended-minutes-btn in-card gen" @click="generateMinutes">生成会议纪要</button>
            </template>
            </template>
            <div class="arc-list" v-if="detail.archiveExtras && detail.archiveExtras.length">
              <div class="arcl-row" v-for="ae in detail.archiveExtras" :key="ae.id" @click="ae.url && openMaterialViewer(ae)">
                <img v-if="ae.url && isImageFile(ae.url, ae.fileType)" :src="ae.url" class="file-thumb" @click.stop="openMaterialViewer(ae)" />
                <div v-else class="arcl-icon extra">📎</div><span class="arcl-name">{{ ae.fileName }}</span><span class="arcl-arrow">留痕</span>
              </div>
            </div>
            <div class="arc-log" v-if="detail.archiveLog && detail.archiveLog.length">
              <div class="arclog-head" @click="toggleArchiveLog">
                <span class="arclog-title">操作记录（{{ detail.archiveLog.length }}）</span>
                <span class="arclog-toggle">{{ archiveLogOpen ? '收起 ▾' : '展开 ▸' }}</span>
              </div>
              <template v-if="archiveLogOpen">
              <div class="arclog-row" v-for="lg in detail.archiveLog" :key="lg.id">
                <span class="arclog-action">{{ lg.action }}</span>
                <span class="arclog-meta">{{ lg.operator }} · {{ lg.at }}</span>
                <span class="arclog-reason" v-if="lg.reason">{{ lg.reason }}</span>
              </div>
              </template>
            </div>
          </div>
          <!-- 底部操作栏（固定在页面底部）。无效会议也照常显示公示按钮；「会议无效」提示改到点击公示后再弹 -->
          <div class="arc-bottom-action">
            <!-- 公示会议：未公示态置顶为第一个主操作；已公示后 → 同位置替换为「查看公示内容」 -->
            <button v-if="isFreshEnded" class="arc-publish-main-btn" @click="publishNow">公示会议</button>
            <button v-else-if="detail.publish && detail.publish.published" class="arc-publish-main-btn" @click="viewPublicMinutes">查看公示内容</button>
            <div class="ended-btn-row">
              <!-- 「查看会议纪要」不再单列大按钮：入口收进上方会议卡的「查看详情」里 -->
              <button class="ended-news-btn" @click="onNewsBtn">{{ newsBtnLabel }}</button>
              <button class="ended-home-btn" @click="goHome">返回首页</button>
            </div>
            <div v-if="detail.publish && detail.publish.published" class="arp-done">
              <div class="arp-status">
                <span class="arp-status-main"><span class="arp-check">✓</span>已公示</span>
                <span class="arp-status-sub" v-if="detail.publish.publishDate">{{ detail.publish.publishDate }}</span>
              </div>
              <div class="arp-actions">
                <span class="ar-skip" @click="viewTodos">待办事项</span>
                <span class="ar-skip" @click="viewMinutesRevisions">版本历史</span>
                <span class="ar-skip danger" @click="withdrawPublish">撤回公示</span>
              </div>
            </div>
            <div v-else-if="detail.publish && detail.publish.withdrawn">
              <div style="color:#E74C3C;font-size:13px;margin-bottom:8px;">⚠ 公示已撤回{{ detail.publish.withdrawnBy ? '（' + detail.publish.withdrawnBy + '）' : '' }}{{ detail.publish.withdrawReason ? '：' + detail.publish.withdrawReason : '' }}</div>
              <button class="arc-publish-main-btn" @click="publishNow">修订后重新公示</button>
              <div class="arp-actions">
                <span class="ar-skip" @click="viewTodos">待办事项</span>
                <span class="ar-skip" @click="viewMinutesRevisions">版本历史</span>
                <span class="ar-skip" @click="addArchiveExtra">上传材料</span>
              </div>
            </div>
            <div v-else-if="detail._archived">
              <span class="arp-check">✓</span><span style="font-size:13px;color:#27AE60;">已归档（未公示）</span>
              <div class="arp-actions">
                <span class="ar-skip" @click="viewTodos">待办事项</span>
                <span class="ar-skip" @click="addArchiveExtra">上传材料</span>
                <span class="ar-skip danger" @click="revokeArchive">撤销归档</span>
              </div>
            </div>
            <template v-else>
              <span class="ext-hint withdrawn" v-if="detail.revokeArchiveReason">⚠ 归档已撤销：{{ detail.revokeArchiveReason }}，请确认后重新归档</span>
              <div class="arp-actions">
                <span class="ar-skip" @click="viewTodos">待办事项</span>
                <span class="ar-skip" @click="archiveDirect">直接归档</span>
                <span class="ar-skip" @click="addArchiveExtra">上传材料</span>
              </div>
            </template>
          </div>
        </template>
      </template>

      <!-- 进行中（主任/委员）统一收敛到「会议进行」向导页（live-entry 入口），此处不再内联管理 -->

      <!-- ══════════ 外部视图 ══════════ -->
      <template v-if="userView === 'owner' || userView === 'property'">
        <div class="ext-banner" :class="userView">
          <span>{{ userView === 'owner' ? '🏠 业主视角·只读' : '🏢 物业视角·只读' }}</span>
          <span class="ext-sub">仅展示对外公开信息，不可操作</span>
        </div>
        <template v-if="detail.stage === 'ended' && detail.compliance !== 'invalid' && detail.publish && detail.publish.published">
          <button class="btn btn-ghost mini" @click="viewMinutes">查看公示纪要</button>
        </template>
        <template v-else-if="detail.publish && detail.publish.withdrawn">
          <span class="ext-hint withdrawn">⚠ 该会议纪要已撤回公示</span>
        </template>
        <template v-else-if="detail.compliance === 'invalid'">
          <span class="ext-hint withdrawn">⚠ 该会议已作废</span>
        </template>
        <template v-else>
          <span class="ext-hint">会议结束公示后可查看会议纪要</span>
        </template>
      </template>
    </div>

    <!-- 准备阶段（主任）：底部固定主操作 -->
    <div class="prep-footer after-send-footer" v-if="detail && userView === 'chair' && detail.stage === 'preparing'">
      <div class="pf-after-send">
        <!-- 通知已送达全体后才浮出「开始会议」；此前先让主任二选一发通知（App内群发 / 去微信复制） -->
        <button v-if="prepareMode !== 'send'" class="pf-btn pf-btn-start-top" @click="startMeeting"><span class="pf-start-ico">▶</span>开始会议</button>
        <div class="pf-btn-row">
          <button class="pf-btn" @click="sendAppNoticeOnly">App内通知</button>
          <button class="pf-btn" @click="openWechat">去微信通知</button>
        </div>
      </div>
    </div>

    <!-- 发送通知后：转发到微信。弹窗内做格式化预览（与通知页一致：首行缩进/地点蓝色可点/落款靠右）；复制出去的纯文本保留网址供微信点开 -->
    <div v-if="forwardVisible" class="modal-mask" @click="closeForward">
      <div class="forward-sheet" @click.stop>
        <div class="fw-title">通知已发送</div>
        <div class="fw-hint">下面是通知内容，点「复制通知」粘贴到业主委员群即可</div>
        <div class="fw-preview">
          <!-- 正文与通知页同款：首行缩进，地点蓝色可点开地图，落款靠右 -->
          <div class="fw-para">各位委员：现拟于 {{ fmtCnDate(detail.meetingDate) }} {{ fmtHm(detail.meetingTime) }} 在<span v-if="detail.location" class="loc-inline" @click="openMap(detail.location)">{{ detail.location }}</span>召开本次会议，主要议题：{{ noticeTopicsText }}，请准时出席。</div>
          <div class="fw-sign">业主委员会</div>
          <div class="fw-linkrows">
            <div class="fw-linkrow" @click="openJoin"><span class="fw-lr-ico">👉</span><span class="fw-lr-txt">进入会议</span><span class="fw-lr-go">›</span></div>
            <div class="fw-linkrow" v-if="detail.location" @click="openMap(detail.location)"><span class="fw-lr-ico">📍</span><span class="fw-lr-txt">地图导航</span><span class="fw-lr-go">›</span></div>
          </div>
        </div>
        <div class="fw-actions">
          <button class="fw-btn" @click="copyShareText">复制通知内容</button>
          <button class="fw-btn" @click="openWechat">转发到微信</button>
        </div>
        <span class="fw-close" @click="closeForward">完成</span>
      </div>
    </div>

    <!-- 添加议题弹窗 -->
    <div v-if="addTopicVisible" class="modal-mask" @click="closeAddTopic">
      <div class="form-sheet" @click.stop style="padding-bottom:calc(18px + env(safe-area-inset-bottom));">
        <div class="sheet-head">
          <span class="sheet-title">添加议题</span>
          <span class="sheet-close" @click="closeAddTopic">×</span>
        </div>

        <div class="form-group">
          <span class="form-label">议题名称</span>
          <input class="form-input large" v-model="newTopicForm.title" placeholder="输入议题名称" />
        </div>

        <div class="form-group">
          <span class="form-label">表决类型</span>
          <div class="type-row">
            <span class="type-chip" :class="newTopicForm.decisionType === 'simple' ? 'on' : ''" @click="pickDecisionType('simple')">普通表决</span>
            <span class="type-chip" :class="newTopicForm.decisionType === 'multi_choice' ? 'on' : ''" @click="pickDecisionType('multi_choice')">多选一</span>
          </div>
        </div>

        <div v-if="newTopicForm.decisionType === 'multi_choice'" class="form-group">
          <span class="form-label">选项（每行一个，第一个为默认选项）</span>
          <textarea class="form-textarea" v-model="newTopicForm.optionsText" placeholder="每行写一个选项，如：&#10;完全更换为智能门禁&#10;保留现有门禁，加装人脸识别&#10;暂不改造，维持现状" style="min-height:90px;height:90px;"></textarea>
        </div>

        <div class="form-group">
          <div class="realname-row" @click="toggleRealName">
            <div>
              <span class="form-label">实名表决</span>
              <span class="realname-hint">开启后将公开每位委员的投票选择，须在投票前确定</span>
            </div>
            <div class="rn-switch" :class="newTopicForm.realName ? 'on' : ''"><div class="rn-knob"></div></div>
          </div>
        </div>

        <div class="sheet-actions weighted-actions">
          <button class="btn btn-ghost" @click="closeAddTopic">取消</button>
          <button class="btn btn-primary" @click="submitAddTopic">确认添加</button>
        </div>
      </div>
    </div>

    <!-- 代录弹层 -->
    <div v-if="proxyVisible" class="modal-mask" @click="closeProxyRecorder">
      <div class="form-sheet proxy-sheet" @click.stop>
        <div class="sheet-head">
          <span class="sheet-title">会议代录</span>
          <span class="sheet-close" @click="closeProxyRecorder">×</span>
        </div>

        <div class="proxy-tabs">
          <span class="proxy-tab" :class="proxyAction === 'signIn' ? 'on' : ''" @click="pickProxyAction('signIn')">代签到</span>
          <span class="proxy-tab" :class="proxyAction === 'vote' ? 'on' : ''" @click="pickProxyAction('vote')">代投票</span>
        </div>

        <div v-if="proxyAction === 'vote'" class="proxy-section">
          <span class="form-label">选择议题</span>
          <div class="proxy-topic-list">
            <span v-for="item in (detail && detail.record ? detail.record.topics : [])" :key="item.id" class="proxy-topic" :class="proxyTopicId == item.id ? 'on' : ''" @click="pickProxyTopic(item.id)">{{ item.title }}</span>
          </div>
          <template v-for="topic in (detail && detail.record ? detail.record.topics : [])" :key="topic.id">
            <div v-if="proxyTopicId == topic.id && topic.decisionType !== 'multi_choice'" class="proxy-choice-row">
              <span class="proxy-choice for" :class="proxyChoice === 'for_vote' ? 'on' : ''" @click="pickProxyChoice('for_vote')">赞成</span>
              <span class="proxy-choice ag" :class="proxyChoice === 'against' ? 'on' : ''" @click="pickProxyChoice('against')">反对</span>
              <span class="proxy-choice ab" :class="proxyChoice === 'abstain' ? 'on' : ''" @click="pickProxyChoice('abstain')">弃权</span>
            </div>
            <div v-if="proxyTopicId == topic.id && topic.decisionType === 'multi_choice'" class="proxy-option-list">
              <div class="proxy-option" :class="proxySelectedId == opt.id ? 'on' : ''" v-for="opt in topic.options" :key="opt.id" @click="pickProxyOption(opt.id)">
                <span>{{ opt.label }}</span>
              </div>
            </div>
          </template>
        </div>

        <div class="proxy-section">
          <span class="form-label">选择代录对象</span>
          <input class="form-input" :value="proxyKeyword" @input="onProxyKeywordInput" placeholder="搜索姓名或房号" />
          <div class="proxy-target-list" style="overflow-y:auto;">
            <div v-for="item in proxyTargets" :key="item.memberId" class="proxy-target" :class="[item.checked ? 'on' : '', item.disabled ? 'disabled' : '']" @click="toggleProxyTarget(item.memberId)">
              <div class="proxy-check">{{ item.checked ? '✓' : '' }}</div>
              <div class="proxy-person">
                <span class="proxy-name">{{ item.name }}</span>
                <span class="proxy-meta">{{ item.role }}{{ item.roomNumber ? ' · ' + item.roomNumber : '' }}</span>
              </div>
              <span v-if="item.disabledText" class="proxy-state">{{ item.disabledText }}</span>
            </div>
            <div v-if="!proxyTargets.length" class="proxy-empty">没有匹配人员</div>
          </div>
        </div>

        <div class="proxy-section">
          <span class="form-label">代录凭证</span>
          <div class="proof-picker" @click="pickProxyProof">
            <span class="proof-main">{{ proxyProofName || '上传现场照片、授权截图或纸质凭证' }}</span>
            <span class="proof-action">{{ proxyProofName ? '重新选择' : '选择图片' }}</span>
          </div>
        </div>

        <div class="proxy-submit-row">
          <span class="proxy-count">已选 {{ proxySelectedCount }} 人</span>
          <button class="btn btn-primary proxy-submit" :class="{ loading: proxySubmitting }" @click="submitProxyAction">提交代录</button>
        </div>
      </div>
    </div>

    <!-- 编辑会议弹窗 -->
    <div v-if="editVisible" class="modal-mask" @click="closeEdit">
      <div class="form-sheet" @click.stop style="padding-bottom:calc(18px + env(safe-area-inset-bottom));">
        <div class="sheet-head">
          <span class="sheet-title">编辑会议</span>
          <span class="sheet-close" @click="closeEdit">×</span>
        </div>

        <div class="form-group">
          <span class="form-label">会议标题 *</span>
          <div class="vi-row">
            <input class="form-input large" v-model="editForm.title" placeholder="会议标题" />
            <button class="vi-btn" :class="{ on: voiceTarget === 'title' }" @click.stop="startVoice('title')">🎤</button>
          </div>
        </div>

        <div class="form-row">
          <div class="form-group half">
            <span class="form-label">会议日期 *</span>
            <div class="picker-field ep-field" @click="openEditDatePicker">
              <span class="ep-text">{{ editForm.meetingDate || '点击选择' }}</span>
              <span class="ep-arrow">▾</span>
            </div>
          </div>
          <div class="form-group half">
            <span class="form-label">开始时间 *</span>
            <div class="picker-field ep-field" @click="openEditTimePicker">
              <span class="ep-text">{{ editForm.meetingTime || '点击选择' }}</span>
              <span class="ep-arrow">▾</span>
            </div>
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">会议地点</span>
          <select class="picker-field ep-loc-select" :value="editLocationPreset" @change="onEditLocationPreset">
            <option v-for="loc in commonLocations" :key="loc" :value="loc">{{ loc }}</option>
            <option value="__other__">其他地点（手动填写）</option>
          </select>
          <input v-if="editLocationPreset === '__other__'" class="form-input ep-loc-other" v-model="editForm.location" placeholder="请输入会议地点" />
        </div>

        <div class="form-group">
          <span class="form-label">主要议题 / 补充说明</span>
          <div class="vi-row" style="align-items:flex-start;">
            <textarea class="form-textarea" style="min-height:72px;height:72px;flex:1;" v-model="editForm.description" placeholder="主要议题或其他需要记录的事项"></textarea>
            <button class="vi-btn" :class="{ on: voiceTarget === 'description' }" @click.stop="startVoice('description')" style="margin-top:6rpx;">🎤</button>
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">通知正文</span>
          <span class="form-hint">默认按上面信息自动生成；如需自定义措辞可在此直接编辑。</span>
          <textarea class="form-textarea" style="min-height:160px;height:160px;" :value="editForm.content" @input="onEditContentInput" placeholder="通知正文"></textarea>
        </div>

        <div class="sheet-actions weighted-actions">
          <button class="btn btn-ghost" @click="closeEdit">取消</button>
          <button class="btn btn-primary" @click="submitEdit">保存修改<span class="btn-arrow">›</span></button>
        </div>
      </div>
    </div>

    <!-- 编辑通知：日期选择弹窗（年/月/日，与「新建会议」一致） -->
    <div v-if="editDatePickerOpen" class="ep-pop-mask" @click="editDatePickerOpen = false">
      <div class="ep-pop" @click.stop>
        <div class="ep-head">选择会议日期</div>
        <div class="ep-cols">
          <div class="ep-col">
            <div class="ep-col-label">年</div>
            <div class="ep-col-scroll">
              <span v-for="y in yearOptions" :key="y" class="ep-item" :class="{ on: y === edpYear }" @click="setEdpYear(y)">{{ y }}</span>
            </div>
          </div>
          <div class="ep-col">
            <div class="ep-col-label">月</div>
            <div class="ep-col-scroll">
              <span v-for="mo in monthOptions" :key="mo" class="ep-item" :class="{ on: mo === edpMonth }" @click="setEdpMonth(mo)">{{ mo }}</span>
            </div>
          </div>
          <div class="ep-col">
            <div class="ep-col-label">日</div>
            <div class="ep-col-scroll">
              <span v-for="d in edpDayOptions" :key="d" class="ep-item" :class="{ on: d === edpDay }" @click="edpDay = d">{{ d }}</span>
            </div>
          </div>
        </div>
        <div class="ep-actions">
          <button class="btn btn-ghost" @click="editDatePickerOpen = false">取消</button>
          <button class="btn btn-primary" @click="confirmEditDate">确定</button>
        </div>
      </div>
    </div>

    <!-- 编辑通知：时间选择弹窗（小时 9—20 / 分钟每15分钟，与「新建会议」一致） -->
    <div v-if="editTimePickerOpen" class="ep-pop-mask" @click="editTimePickerOpen = false">
      <div class="ep-pop" @click.stop>
        <div class="ep-head">选择开始时间</div>
        <div class="ep-cols">
          <div class="ep-col">
            <div class="ep-col-label">时</div>
            <div class="ep-col-scroll">
              <span v-for="h2 in hourOptions" :key="h2" class="ep-item" :class="{ on: h2 === etpHour }" @click="etpHour = h2">{{ String(h2).padStart(2, '0') }}</span>
            </div>
          </div>
          <div class="ep-col">
            <div class="ep-col-label">分</div>
            <div class="ep-col-scroll">
              <span v-for="mi in minuteOptions" :key="mi" class="ep-item" :class="{ on: mi === etpMinute }" @click="etpMinute = mi">{{ String(mi).padStart(2, '0') }}</span>
            </div>
          </div>
        </div>
        <div class="ep-actions">
          <button class="btn btn-ghost" @click="editTimePickerOpen = false">取消</button>
          <button class="btn btn-primary" @click="confirmEditTime">确定</button>
        </div>
      </div>
    </div>

    <!-- 语音输入确认弹窗 -->
    <div v-if="voiceConfirmField" class="voice-modal-mask">
      <div class="voice-modal">
        <div class="vm-head">
          <span class="vm-title">🎤 语音输入{{ voiceConfirmField === 'title' ? '标题' : '议题' }}</span>
          <span class="vm-hint">{{ voiceListening ? '正在听…' : '确认或重新输入' }}</span>
        </div>
        <div class="vm-body">
          <div class="vm-wave" v-if="voiceListening"><span></span><span></span><span></span><span></span><span></span></div>
          <div class="vm-text">
            <span v-if="voiceResult">{{ voiceResult }}</span>
            <span v-else class="vm-placeholder">正在听，请说话…</span>
          </div>
        </div>
        <div class="vm-actions">
          <button class="btn btn-ghost" @click="cancelVoice">取消</button>
          <button class="btn btn-ghost" @click="retryVoice" :disabled="voiceListening">重新输入</button>
          <button class="btn btn-primary" @click="confirmVoice" :disabled="!voiceResult">确认</button>
        </div>
      </div>
    </div>

    <!-- 编辑通知草稿弹窗 -->
    <div v-if="noticeEditVisible" class="modal-mask" @click="closeNoticeEdit">
      <div class="form-sheet" @click.stop style="padding-bottom:calc(18px + env(safe-area-inset-bottom));">
        <div class="sheet-head">
          <span class="sheet-title">编辑通知</span>
          <span class="sheet-close" @click="closeNoticeEdit">×</span>
        </div>

        <div class="form-group">
          <span class="form-label">通知标题</span>
          <input class="form-input large" v-model="noticeEditForm.title" placeholder="通知标题" />
        </div>

        <div class="form-group">
          <span class="form-label">通知正文</span>
          <textarea class="form-textarea" style="min-height:200px;height:200px;" v-model="noticeEditForm.content" placeholder="通知正文"></textarea>
        </div>

        <div class="sheet-actions weighted-actions">
          <button class="btn btn-ghost" @click="closeNoticeEdit">取消</button>
          <button class="btn btn-primary" @click="submitNoticeEdit">保存<span class="btn-arrow">›</span></button>
        </div>
      </div>
    </div>

    <!-- 议题弹层：表决 + 意见（进行中可操作，其余阶段只读查看） -->
    <TopicSheet v-if="detail && meetingIdRef" :meeting-id="meetingIdRef" :topic="sheetTopic"
                :interactive="detail.stage === 'ongoing'" :signed-in="selfSignedIn" :is-chair="userView === 'chair'"
                :has-prev="sheetHasPrev" :has-next="sheetHasNext"
                @close="sheetTopicId = null" @changed="loadDetail" @prev="gotoPrevTopic" @next="gotoNextTopic" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, h, onMounted, onActivated, onUnmounted, onDeactivated, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { meetingRecordingSession, discardMeetingRecording } from '@/composables/meetingRecordingSession'
import perm from '@/utils/perm'
import { toast, showModal } from '@/utils/ui'
import { navigateTo, redirectTo, navigateBack } from '@/utils/navigate'
import { aiTask, startAiTask, finishAiTask, failAiTask, clearAiTask } from '@/composables/aiTask'
import { getStorage, setStorage } from '@/utils/storage'
import { pickAndUpload, humanSize } from '@/utils/upload'
import { openMaterialViewer } from '@/composables/materialViewer'
import PageNav from '@/components/PageNav.vue'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'
import TopicSheet from '@/components/TopicSheet.vue'

const route = useRoute()

// ── 同文件内子组件：会议议题卡（替代 wxml <template name="meetingTopicsCard">）──
// topics 显式作为 prop 传入；3 处引用（进行中 / 委员结束页 / 主任归档卡）均用该组件。
// 用 render 函数（h）实现，避免依赖运行时模板编译器（Vite 默认 runtime-only 构建）。
const MeetingTopicsCard = {
  name: 'MeetingTopicsCard',
  props: {
    topics: { type: Array, default: () => [] },
    onSelect: { type: Function, default: null },  // 点议题行 → 打开议题弹层（表决+意见）
    expanded: { type: Boolean, default: true },
    onCollapse: { type: Function, default: null },
    onExpand: { type: Function, default: null }
  },
  setup(props) {
    return () => {
      const topics = props.topics
      if ((!topics || !topics.length) && !props.onCollapse && !props.onExpand) return null
      return h('div', { class: 'meeting-topics-card' }, [
        h('div', { class: 'mtc-head' }, [
          h('div', [
            h('span', { class: 'mtc-title-main' }, '会议议题')
          ]),
          props.expanded && props.onCollapse ? h('button', {
            class: 'mtc-collapse',
            onClick: props.onCollapse
          }, '收起议题 ▲') : (!props.expanded && props.onExpand ? h('button', {
            class: 'mtc-collapse',
            onClick: props.onExpand
          }, '查看议题 ▾') : null)
        ]),
        ...(props.expanded ? topics.map((item, index) => h('div', {
          class: ['mtc-topic', props.onSelect ? 'tappable' : ''],
          key: item.id,
          onClick: () => { if (props.onSelect) props.onSelect(item) }
        }, [
          h('span', { class: 'mtc-no' }, index + 1),
          h('div', { class: 'mtc-body' }, [
            h('div', { class: 'mtc-title-row' }, [
              h('span', { class: 'mtc-topic-title' }, item.title),
              h('span', { class: ['mtc-status', item.statusClass] }, item.statusLabel)
            ]),
            h('div', { class: 'mtc-meta' }, [
              h('span', { class: ['mtc-chip', item.typeClass] }, item.typeLabel),
              item.realNameVote ? h('span', { class: 'mtc-chip realname' }, '实名') : null,
              item.source === 'live' ? h('span', { class: 'mtc-chip live' }, '现场新增') : null
            ])
          ]),
          props.onSelect ? h('span', { class: 'mtc-arrow' }, '›') : null
        ])) : [])
      ])
    }
  }
}

// ═══════════════════════════════════════════════
// 顶层纯函数（自 committee-detail.js 1:1 迁移）
// ═══════════════════════════════════════════════
function toNumber(value) {
  var n = Number(value)
  return isNaN(n) ? 0 : n
}

function percent(done, total) {
  return total > 0 ? Math.round(done / total * 100) : 0
}

function topicVoteCount(topic) {
  if (topic && topic.decisionType === 'multi_choice' && topic.options) {
    return topic.options.reduce(function (sum, opt) { return sum + toNumber(opt.votes) }, 0)
  }
  return toNumber(topic && topic.forVotes) + toNumber(topic && topic.agVotes) + toNumber(topic && topic.abVotes)
}

function topicTypeLabel(topic) {
  var type = String(topic && topic.type || '').toLowerCase()
  if (type === 'notice') return '通报'
  if (type === 'discussion') return '讨论'
  if (type === 'major') return '重大'
  if (topic && topic.decisionType === 'multi_choice') return '多选一'
  if (topic && topic.voteRequired === false) return '记录'
  return '表决'
}

function topicTypeClass(topic) {
  var type = String(topic && topic.type || '').toLowerCase()
  if (type === 'notice') return 'notice'
  if (type === 'discussion') return 'discussion'
  if (type === 'major') return 'major'
  if (topic && topic.decisionType === 'multi_choice') return 'multi'
  return 'decision'
}

function topicStatusLabel(topic, ended) {
  if (!topic) return '待确认'
  if (topic.voteRequired === false) return '已记录'
  if (topic.status === 'passed' || topic.passed) return '已通过'
  if (topic.status === 'failed') return '未通过'
  // 会议已结束：无明确表决结果的议题不再显示「待完成」，会已开完即为「已完成」
  return ended ? '已完成' : '待完成'
}

function topicStatusClass(topic, ended) {
  if (!topic) return 'pending'
  if (topic.voteRequired === false) return 'recorded'
  if (topic.status === 'passed' || topic.passed) return 'passed'
  if (topic.status === 'failed') return 'failed'
  return ended ? 'recorded' : 'pending'
}

function topicVoteSummary(topic) {
  if (!topic) return ''
  if (topic.voteRequired === false) return topic.text || '无需表决，已作为会议记录事项'
  if (topic.decisionType === 'multi_choice') {
    var opts = topic.options || []
    if (!opts.length) return topic.text || '多选一表决'
    return opts.map(function (opt) {
      return (opt.label || opt.name || '选项') + ' ' + toNumber(opt.votes)
    }).join(' / ') + (topic.need ? '，通过需≥' + topic.need : '')
  }
  return '同意 ' + toNumber(topic.forVotes) +
    ' / 反对 ' + toNumber(topic.agVotes) +
    ' / 弃权 ' + toNumber(topic.abVotes) +
    (topic.need ? '，通过需≥' + topic.need : '')
}

function decorateMeetingTopics(detail) {
  if (!detail || !detail.record || !detail.record.topics) return
  var ended = detail.stage === 'ended'
  detail.record.topics = detail.record.topics.map(function (topic) {
    topic.typeLabel = topicTypeLabel(topic)
    topic.typeClass = topicTypeClass(topic)
    topic.statusLabel = topicStatusLabel(topic, ended)
    topic.statusClass = topicStatusClass(topic, ended)
    topic.voteSummary = topicVoteSummary(topic)
    return topic
  })
}

function buildFlowStats(detail) {
  if (!detail) return null
  var delivery = detail.delivery || {}
  var memberDeliveries = delivery.memberDeliveries || []
  var memberTotal = detail.members && detail.members.length ? detail.members.length : 0
  var deliveryTotal = toNumber(delivery.total) || memberDeliveries.length || memberTotal
  var noticeDone = delivery.noticeDone !== undefined && delivery.noticeDone !== null
    ? toNumber(delivery.noticeDone)
    : memberDeliveries.filter(function (item) { return item.noticeDelivered }).length
  var materialDone = delivery.materialDone !== undefined && delivery.materialDone !== null
    ? toNumber(delivery.materialDone)
    : memberDeliveries.filter(function (item) { return item.materialDelivered }).length

  var record = detail.record || {}
  var attendances = record.attendances || []
  var attendanceTotal = attendances.length || memberTotal || deliveryTotal
  var signedInCount = record.signedInCount !== undefined && record.signedInCount !== null
    ? toNumber(record.signedInCount)
    : attendances.filter(function (item) { return item.signedIn }).length
  var need = attendanceTotal > 0 ? Math.floor(attendanceTotal / 2) + 1 : 0

  var topics = (record.topics || []).filter(function (topic) { return topic.voteRequired !== false })
  var topicCount = topics.length
  var passedCount = topics.filter(function (topic) { return topic.passed }).length
  var pendingCount = topics.filter(function (topic) {
    return topic.status === 'pending' || (attendanceTotal > 0 && topicVoteCount(topic) < attendanceTotal)
  }).length
  var failedCount = Math.max(0, topicCount - passedCount - pendingCount)
  var votedCount = topics.reduce(function (sum, topic) { return sum + topicVoteCount(topic) }, 0)
  var voteTotal = topicCount * attendanceTotal
  var noticePct = percent(noticeDone, deliveryTotal)
  var materialPct = percent(materialDone, deliveryTotal)

  return {
    delivery: {
      total: deliveryTotal,
      noticeDone: noticeDone,
      materialDone: materialDone,
      noticePct: noticePct,
      materialPct: materialPct,
      pct: Math.min(noticePct, materialPct),
      done: deliveryTotal > 0 && noticeDone >= deliveryTotal && materialDone >= deliveryTotal,
      statusText: deliveryTotal > 0 ? '未完成' : '未记录'
    },
    attendance: {
      total: attendanceTotal,
      signedInCount: signedInCount,
      need: need,
      pct: percent(signedInCount, attendanceTotal),
      done: attendanceTotal > 0 && signedInCount >= need,
      statusText: attendanceTotal > 0 ? (signedInCount >= need ? '已达到法定人数' : '未达到法定人数') : '暂无参会记录'
    },
    vote: {
      topicCount: topicCount,
      passedCount: passedCount,
      pendingCount: pendingCount,
      failedCount: failedCount,
      votedCount: votedCount,
      totalVoteCount: voteTotal,
      pct: percent(votedCount, voteTotal),
      done: topicCount === 0 || pendingCount === 0,
      metaText: topicCount === 0 ? '未设置表决议题' : '投票完成 ' + votedCount + '/' + voteTotal,
      statusText: topicCount === 0
        ? '本次无表决议题'
        : (pendingCount > 0 ? pendingCount + '项待完成' : passedCount + '/' + topicCount + '项通过')
    }
  }
}

// ── 工具：文件大小格式化（小程序里依赖 this.formatSize，但原文件未显式定义，
//    这里补一个与上传/补充材料调用一致的实现，便于第三期接文件能力时复用）──
function formatSize(size) {
  var n = Number(size) || 0
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + 'MB'
  if (n >= 1024) return Math.round(n / 1024) + 'KB'
  return n + 'B'
}

// ═══════════════════════════════════════════════
// data（自 Page.data 1:1 迁移到 ref/reactive）
// ═══════════════════════════════════════════════
const detail = ref(null)
const basisOpen = ref(false)
const basisLoading = ref(false)
const basisTranscript = ref('')
async function toggleBasis() {
  basisOpen.value = !basisOpen.value
  if (basisOpen.value && !basisTranscript.value && !basisLoading.value) {
    basisLoading.value = true
    try {
      const r = await api.committeeTranscript(meetingIdRef.value)
      basisTranscript.value = typeof r === 'string' ? r : ((r && (r.text || r.transcript || r.content)) || '')
    } catch (e) {} finally { basisLoading.value = false }
  }
}
const userView = ref('')

// ── 议题弹层（表决+意见）──
const meetingIdRef = ref(null)   // meetingId 的响应式镜像（弹层 prop 用）
const sheetTopicId = ref(null)
const sheetTopic = computed(() => {
  const d = detail.value
  const list = (d && d.record && d.record.topics) || []
  return list.find(t => t.id === sheetTopicId.value) || null
})
// 弹层"上一个/下一个议题"
function _sheetTopicIndex() {
  const list = (detail.value && detail.value.record && detail.value.record.topics) || []
  return { list, i: list.findIndex(t => t.id === sheetTopicId.value) }
}
const sheetHasPrev = computed(() => _sheetTopicIndex().i > 0)
const sheetHasNext = computed(() => { const { list, i } = _sheetTopicIndex(); return i >= 0 && i < list.length - 1 })
function gotoPrevTopic() { const { list, i } = _sheetTopicIndex(); if (i > 0) sheetTopicId.value = list[i - 1].id }
function gotoNextTopic() { const { list, i } = _sheetTopicIndex(); if (i >= 0 && i < list.length - 1) sheetTopicId.value = list[i + 1].id }
const selfSignedIn = computed(() => {
  const d = detail.value
  const atts = (d && d.record && d.record.attendances) || []
  const me = atts.find(a => a.isSelf)
  return !!(me && me.signedIn)
})
function openTopicSheet(item) { sheetTopicId.value = item.id }

const cardSizeClass = computed(() => {
  const n = detail.value?.record?.topics?.length ?? 0
  if (n <= 1) return 'card-sz-xl'
  if (n === 2) return 'card-sz-lg'
  if (n >= 4) return 'card-sz-sm'
  return 'card-sz-md'
})

// 结束页会议卡：已生成纪要时默认展开；未生成纪要直接结束时默认收起，避免议题抢占页面。
// 只在首次加载时决定默认值，后续刷新数据不覆盖用户手动展开/收起的选择。
const endedDetailOpen = ref(true)
const endedDetailInitialized = ref(false)
// "10:00:00" → "10:00"
function shortTime(t) { return (t || '').slice(0, 5) }

// 「未公示」态（既未公示、未撤回、未归档）→ 顶部显示「公示会议」主操作。无效会议也显示，点击后再提示。
const isFreshEnded = computed(() => {
  const d = detail.value
  if (!d) return false
  const pub = d.publish
  return !(pub && pub.published) && !(pub && pub.withdrawn) && !d._archived
})

// 顶部橙色区域标题：随会议阶段变化（创建后进入即"会议通知"——总结会议信息并向委员发送通知）
const navTitle = computed(() => {
  const d = detail.value
  if (!d) return '会议通知'
  if (d.stage === 'preparing') return '会议通知'
  if (d.stage === 'ongoing') return '会议进行中'
  return '会议详情'
})
const activeRole = ref({})
const currentStep = ref(1)
const step1Done = ref(false), step2Done = ref(false), step3Done = ref(false)
const step1Time = ref(''), step2Time = ref(''), step3Time = ref('')
const stepDone = ref(0), stepTotal = ref(2), stepAllDone = ref(false)
const prepareSteps = ref([]), prepareHint = ref(''), prepareMode = ref('')
const deliveryExpanded = ref(false)
const flowStatsOpen = ref(false)
const archiveLogOpen = ref(false)
const noticePackageVisible = ref(false)
const recAudioPlaying = ref(false)
const generatingNews = ref(false)   // AI 生成党建新闻中（驱动红色党建工作遮罩）
const generatingMinutes = ref(false)
const minutesOverlayShown = ref(false)
const quickMode = ref(false)
const step3Ready = ref(false)
// 添加议题表单
const addTopicVisible = ref(false)
const newTopicForm = reactive({
  title: '',
  decisionType: 'simple',
  type: 'decision',
  optionsText: '',
  realName: false
})
const proxyVisible = ref(false)
const proxyAction = ref('signIn')
const proxyTargets = ref([])
const proxyAllTargets = ref([])
const proxyKeyword = ref('')
const proxyProofUrl = ref('')
const proxyProofName = ref('')
const proxyTopicId = ref(null)
const proxyChoice = ref('for_vote')
const proxySelectedId = ref(null)
const proxySelectedCount = ref(0)
const proxySubmitting = ref(false)
// 编辑会议
const editVisible = ref(false)
const editForm = reactive({ title: '', meetingDate: '', meetingTime: '', location: '', description: '', content: '' })

// ── 编辑通知：会议要素选择器（与「新建会议」保持一致：地点下拉 / 日期年月日 / 时间 9—20点·每15分钟）──
const commonLocations = ['社区活动室', '社区会议室']
const editLocationPreset = ref('社区活动室')
const _epNowYear = new Date().getFullYear()
const yearOptions = [_epNowYear - 1, _epNowYear, _epNowYear + 1]
const monthOptions = Array.from({ length: 12 }, (_, i) => i + 1)
const hourOptions = Array.from({ length: 12 }, (_, i) => i + 9)   // 业委会会议时段：9点—20点
const minuteOptions = Array.from({ length: 4 }, (_, i) => i * 15) // 每15分钟
const editDatePickerOpen = ref(false)
const edpYear = ref(_epNowYear)
const edpMonth = ref(1)
const edpDay = ref(1)
const edpDayOptions = computed(() => {
  const n = new Date(edpYear.value, edpMonth.value, 0).getDate()
  return Array.from({ length: n }, (_, i) => i + 1)
})
const editTimePickerOpen = ref(false)
const etpHour = ref(9)
const etpMinute = ref(0)

// 打开选择器时把地点下拉同步到当前值：命中常用项→选它；非预设值→切"其他"并回显手填框
function syncEditLocationPreset(val) {
  if (val && commonLocations.indexOf(val) >= 0) editLocationPreset.value = val
  else if (val) editLocationPreset.value = '__other__'
  else editLocationPreset.value = '社区活动室'
}
function onEditLocationPreset(e) {
  const v = e.target.value
  editLocationPreset.value = v
  editForm.location = (v === '__other__') ? '' : v
}
function clampEdpDay() {
  const n = new Date(edpYear.value, edpMonth.value, 0).getDate()
  if (edpDay.value > n) edpDay.value = n
}
function setEdpYear(y) { edpYear.value = y; clampEdpDay() }
function setEdpMonth(m) { edpMonth.value = m; clampEdpDay() }
function openEditDatePicker() {
  const parts = (editForm.meetingDate || '').split('-')
  edpYear.value = Number(parts[0]) || _epNowYear
  edpMonth.value = Number(parts[1]) || 1
  edpDay.value = Number(parts[2]) || 1
  clampEdpDay()
  editDatePickerOpen.value = true
  scrollEditPickerToSelected()
}
function confirmEditDate() {
  editForm.meetingDate = edpYear.value + '-' + String(edpMonth.value).padStart(2, '0') + '-' + String(edpDay.value).padStart(2, '0')
  editDatePickerOpen.value = false
}
function openEditTimePicker() {
  const parts = (editForm.meetingTime || '09:00').split(':')
  etpHour.value = Math.min(20, Math.max(9, Number(parts[0]) || 9))           // 夹到 9—20 点
  etpMinute.value = (Math.round((Number(parts[1]) || 0) / 15) * 15) % 60     // 对齐到每 15 分钟
  editTimePickerOpen.value = true
  scrollEditPickerToSelected()
}
function confirmEditTime() {
  editForm.meetingTime = String(etpHour.value).padStart(2, '0') + ':' + String(etpMinute.value).padStart(2, '0')
  editTimePickerOpen.value = false
}
// 打开后把已选项滚到列中部（只滚列内部，不影响页面）
function scrollEditPickerToSelected() {
  nextTick(() => {
    document.querySelectorAll('.ep-pop .ep-col-scroll').forEach((scroll) => {
      const on = scroll.querySelector('.ep-item.on')
      if (on) scroll.scrollTop = on.offsetTop - scroll.clientHeight / 2 + on.clientHeight / 2
    })
  })
}
const noticeContentDirty = ref(false)
// 语音输入
const voiceTarget = ref('')
const voiceListening = ref(false)
const voiceResult = ref('')
const voiceConfirmField = ref('')
let _voiceRec = null

const HOTWORDS = [
  ['叶委会', '业委会'], ['夜委会', '业委会'], ['页委会', '业委会'], ['一委会', '业委会'],
  ['物业肥', '物业费'], ['物业菲', '物业费'],
  ['主人', '主任'], ['副主人', '副主任'],
  ['为员', '委员'], ['位员', '委员'], ['纬员', '委员'],
  ['记要', '纪要'], ['计要', '纪要'],
  ['意题', '议题'], ['一题', '议题'],
  ['签道', '签到'], ['前到', '签到'], ['前道', '签到'],
  ['表绝', '表决'],
  ['公探', '公摊'], ['弓摊', '公摊'],
  ['停车未', '停车位'], ['停车卫', '停车位'],
  ['物业公私', '物业公司'],
  ['维修基础', '维修基金'],
]
function applyHotwords(text) {
  let r = text
  for (const [wrong, right] of HOTWORDS) r = r.replaceAll(wrong, right)
  return r
}

function startVoice(field) {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) { toast({ title: '浏览器暂不支持语音输入', icon: 'none' }); return }
  if (voiceListening.value) {
    if (_voiceRec) { try { _voiceRec.stop() } catch (e) {} }
    voiceListening.value = false; voiceTarget.value = ''
    if (voiceConfirmField.value === field) { voiceConfirmField.value = ''; voiceResult.value = ''; return }
  }
  voiceResult.value = ''
  voiceConfirmField.value = field
  voiceTarget.value = field
  voiceListening.value = true
  _voiceRec = new SR()
  _voiceRec.lang = 'zh-CN'
  _voiceRec.interimResults = false
  _voiceRec.maxAlternatives = 1
  _voiceRec.onresult = (e) => {
    voiceResult.value = applyHotwords(e.results[0][0].transcript)
  }
  _voiceRec.onend = () => {
    voiceListening.value = false; voiceTarget.value = ''
    if (!voiceResult.value) { voiceConfirmField.value = ''; toast({ title: '没有识别到语音，请重试', icon: 'none' }) }
  }
  _voiceRec.onerror = () => {
    voiceListening.value = false; voiceTarget.value = ''
    voiceResult.value = ''; voiceConfirmField.value = ''
    toast({ title: '语音识别失败，请重试', icon: 'none' })
  }
  _voiceRec.start()
}
function confirmVoice() {
  const text = voiceResult.value
  const field = voiceConfirmField.value
  voiceResult.value = ''; voiceConfirmField.value = ''
  if (!text || !field) return
  if (field === 'title') editForm.title = text
  else if (field === 'description') editForm.description = (editForm.description ? editForm.description + '\n' : '') + text
}
function retryVoice() {
  const field = voiceConfirmField.value
  voiceResult.value = ''
  startVoice(field)
}
function cancelVoice() {
  if (_voiceRec) { try { _voiceRec.stop() } catch (e) {} _voiceRec = null }
  voiceListening.value = false; voiceTarget.value = ''
  voiceResult.value = ''; voiceConfirmField.value = ''
}
const locationOptions = ['社区活动室', '物业办公室', '社区会议室', '线上会议', '待定']
// 编辑通知
const noticeEditVisible = ref(false)
const noticeEditForm = reactive({ title: '', content: '' })
const sendSubmitting = ref(false)

// onLoad 上下文（this.meetingId / this.fromNotice）
let meetingId = null
let fromNotice = false
// 录音上下文（this._recAudio）
let _recAudio = null

// ═══════════════════════════════════════════════
// 生命周期
// ═══════════════════════════════════════════════
function syncMeetingIdFromRoute() {
  const id = parseInt(route.query.id)
  if (Number.isFinite(id) && id > 0) meetingId = id
}
function currentMeetingId() {
  const id = Number((detail.value && detail.value.id) || route.query.id || meetingId)
  return Number.isFinite(id) && id > 0 ? id : null
}
// 注意：本页用 options.id（不是 meetingId），即 route.query.id
onMounted(() => {
  syncMeetingIdFromRoute()
  fromNotice = route.query.fromNotice === '1'
  activeRole.value = getStorage('activeRole', null) || {}
  loadDetail()
  reconcileNews() // 进入即对账新闻状态，露出「查看新闻稿/生成中」入口
  if (typeof document !== 'undefined') document.addEventListener('visibilitychange', onNewsVisibility)
})
// onShow → onMounted + onActivated（本页 onShow 会重载 detail，务必保留刷新）
onActivated(() => { syncMeetingIdFromRoute(); loadDetail(); reconcileNews() })
watch(() => route.query.id, () => {
  syncMeetingIdFromRoute()
  if (meetingId) loadDetail()
})
// onUnload → onUnmounted
onUnmounted(() => {
  destroyRecAudio()
  stopNewsPoll() // 本页轮询随页销毁；服务端 @Async 继续跑，切回时 reconcileNews 再接上
  if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onNewsVisibility)
  // 本页遮罩随本页销毁 → 交还全局悬浮胶囊（新闻生成中/已生成切走后仍有入口，不被隐藏）
  if (aiTask.active || aiTask.done || aiTask.failed) aiTask.overlayShown = false
})
// onHide → onDeactivated（本页 onHide 暂停录音播放）
onDeactivated(() => { pauseRecAudio() })

// ── 会议录音存档：试听 / 下载转发 ──
// H5：用 HTMLAudioElement 替代小程序 wx.createInnerAudioContext。
function destroyRecAudio() {
  if (_recAudio) {
    try { _recAudio.pause() } catch (e) {}
    _recAudio.onplay = _recAudio.onpause = _recAudio.onended = _recAudio.onerror = null
    _recAudio = null
  }
  recAudioPlaying.value = false
}
function pauseRecAudio() {
  if (_recAudio && recAudioPlaying.value) { try { _recAudio.pause() } catch (e) {} }
}
// 纪要卡「会议录音」行的数据源：快速会议的多段录音列表（record.recordings），
// 旧字段 record.recordingUrl 是老单文件流程的，快速流程下为空。
const quickRecordings = computed(() => (detail.value && detail.value.record && detail.value.record.recordings) || [])
const hasRecordings = computed(() => quickRecordings.value.length > 0)
const recTotalDurText = computed(() => {
  const list = quickRecordings.value
  if (!list.length) return ''
  const total = list.reduce((s, r) => s + (r.durationSec || 0), 0)
  if (!total) return list.length + ' 段'
  const m = Math.floor(total / 60)
  const s = total % 60
  return list.length + ' 段 · ' + (m ? m + '分' : '') + (s || !m ? s + '秒' : '')
})
let _recPlayIdx = 0
// 试听：多段录音按顺序连播；再点=暂停，播完自动复位
function toggleRecPlayback() {
  const list = quickRecordings.value
  if (!list.length) { toast({ title: '暂无录音', icon: 'none' }); return }
  if (_recAudio && recAudioPlaying.value) { _recAudio.pause(); return }
  if (!_recAudio) {
    _recPlayIdx = 0
    _recAudio = new Audio(list[0].recordingUrl)
    _recAudio.onplay = () => { recAudioPlaying.value = true }
    _recAudio.onpause = () => { recAudioPlaying.value = false }
    _recAudio.onended = () => {
      _recPlayIdx++
      const rest = quickRecordings.value
      if (_recPlayIdx < rest.length) { _recAudio.src = rest[_recPlayIdx].recordingUrl; _recAudio.play() }
      else destroyRecAudio()
    }
    _recAudio.onerror = () => { recAudioPlaying.value = false; toast({ title: '播放失败', icon: 'none' }) }
  }
  const p = _recAudio.play()
  if (p && p.catch) p.catch(() => { recAudioPlaying.value = false; toast({ title: '播放失败', icon: 'none' }) })
}
function toggleRecording() {
  const url = detail.value && detail.value.record ? detail.value.record.recordingUrl : ''
  if (!url) { toast({ title: '暂无录音', icon: 'none' }); return }
  if (_recAudio && recAudioPlaying.value) { _recAudio.pause(); return }
  if (!_recAudio) {
    _recAudio = new Audio(url)
    _recAudio.onplay = () => { recAudioPlaying.value = true }
    _recAudio.onpause = () => { recAudioPlaying.value = false }
    _recAudio.onended = () => { recAudioPlaying.value = false }
    _recAudio.onerror = () => { recAudioPlaying.value = false; toast({ title: '播放失败', icon: 'none' }) }
  }
  const p = _recAudio.play()
  if (p && p.catch) p.catch(() => { recAudioPlaying.value = false; toast({ title: '播放失败', icon: 'none' }) })
}
// H5 无微信转发文件能力：降级为下载（拉成 blob 保证跨域也能下），提示可在微信内转发该文件。
async function downloadRecording() {
  const url = detail.value && detail.value.record ? detail.value.record.recordingUrl : ''
  if (!url) { toast({ title: '暂无录音', icon: 'none' }); return }
  try {
    const resp = await fetch(url)
    const blob = await resp.blob()
    const objUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objUrl
    a.download = '会议录音' + (url.lastIndexOf('.') >= 0 ? url.slice(url.lastIndexOf('.')) : '.mp3')
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(objUrl), 2000)
    toast({ title: '已下载，可在微信中转发该文件' })
  } catch (e) {
    window.open(url, '_blank')
    toast({ title: '已打开录音，可长按保存或转发' })
  }
}

async function loadDetail() {
  try {
    const d = await api.committeeDetail(meetingId)
    const uv = d.userView
    // 委员：进行中与主任统一走「会议进行」页（表决/意见/看录音，主任专属操作按权限隐藏）；
    // 其余阶段仍走专属极简会议页，不进操作者用的详情页（覆盖通知、待办等入口）
    if (uv === 'member') {
      if (d.stage === 'ongoing' && d.record) {
        redirectTo('/pages/meeting-live-quick/meeting-live-quick?type=committee&meetingId=' + meetingId)
        return
      }
      redirectTo('/pages/my-meeting/my-meeting?id=' + meetingId)
      return
    }
    // 进行中主任直接进入「会议进行」录音向导（替换当前页，退出即回列表）
    if (d.stage === 'ongoing' && d.record && uv === 'chair') {
      redirectTo('/pages/meeting-live-quick/meeting-live-quick?type=committee&meetingId=' + meetingId)
      return
    }
    if (d.taskItems) d.taskItemsText = d.taskItems.join(' / ')
    meetingIdRef.value = meetingId

    // Calculate step states for member & chair view
    let s1 = false, s2 = false, s3 = false
    if ((uv === 'member' || uv === 'chair') && d.record && d.record.attendances) {
      const me = d.record.attendances.find(a => a.isSelf)
      if (me) {
        s1 = me.signedIn
        s2 = s1
      }
      // Step 2: all topics voted
      if (d.record.topics) {
        s3 = d.record.topics.length > 0 &&
          d.record.topics.every(t => t.myVote)
      } else {
        s3 = true // no topics = done
      }
    }
    // 如果后端说已完成（taskLevel === 'ok'），直接全锁
    const forceDone = d.taskLevel === 'ok' && d.stage === 'ongoing'
    if (forceDone) { s1 = true; s2 = true; s3 = true }
    const sDone = [s1, s3].filter(Boolean).length
    const sAllDone = forceDone || (sDone === 2)
    // 自动设置当前步骤
    const curStep = sAllDone ? 2 : s1 ? 2 : 1

    // Pre-compute topics passed count
    if (d.record && d.record.topics) {
      decorateMeetingTopics(d)
      d.record.topicsPassed = d.record.topics.filter(function (t) { return t.passed }).length
    }

    // Pre-compute chair view stats
    if (d.record && d.record.attendances) {
      d.record.signedInCount = d.record.attendances.filter(a => a.signedIn).length
      d.record.signedCount = d.record.attendances.filter(a => a.signed).length
      d.record.signedInPct = percent(d.record.signedInCount, d.record.attendances.length)
      d.record.signedPct = percent(d.record.signedCount, d.record.attendances.length)
    }

    // Pre-compute delivery stats
    if (d.delivery && d.delivery.memberDeliveries) {
      var dels = d.delivery.memberDeliveries
      var noticeDone = dels.filter(function (x) { return x.noticeDelivered }).length
      var materialDone = dels.filter(function (x) { return x.materialDelivered }).length
      var readDone = d.delivery.readDone !== undefined && d.delivery.readDone !== null
        ? toNumber(d.delivery.readDone)
        : dels.filter(function (x) { return x.noticeRead }).length
      d.delivery.noticeDone = noticeDone
      d.delivery.materialDone = materialDone
      d.delivery.readDone = readDone
      d.delivery.noticePct = percent(noticeDone, dels.length)
      d.delivery.materialPct = percent(materialDone, dels.length)
      d.delivery.readPct = percent(readDone, dels.length)
    }
    // 确认参会人数：委员"确认参会"走的是出席(signedIn)，按应通知人数 delivery.total 统计
    if (d.delivery) {
      var atts = (d.record && d.record.attendances) ? d.record.attendances : []
      var attendConfirmed = atts.filter(function (a) { return a.signedIn }).length
      var attendDeclined = atts.filter(function (a) { return a.declined }).length
      var attendTotal = d.delivery.total || atts.length || 0
      d.delivery.attendConfirmed = attendConfirmed
      d.delivery.attendDeclined = attendDeclined
      d.delivery.attendPct = percent(attendConfirmed, attendTotal)
      var mine = atts.filter(function (a) { return a.isSelf })[0]
      d.mySignedIn = !!(mine && mine.signedIn)
      d.myDeclined = !!(mine && mine.declined)
    }
    d.flowStats = buildFlowStats(d)

    // 准备阶段（主任）：材料是可选附件，不作为必经步骤。
    let pSteps = [], pHint = '', pMode = ''
    if (uv === 'chair' && d.stage === 'preparing') {
      const del = d.delivery || {}
      const allDone = !!del.allDone
      let cur = allDone ? 2 : 1
      const st = (i, done) => done ? 'done' : (cur === i ? 'current' : 'todo')
      pSteps = [
        { label: '编辑会议', state: 'done' },
        { label: '会议通知', state: st(1, allDone) },
        { label: '参会确认', state: cur === 2 ? 'current' : 'todo' }
      ]
      pMode = allDone ? 'start' : 'send'
      pHint = allDone
        ? '已送达 ' + (del.total || 0) + ' 位委员，可以开始会议'
        : ''
    }
    // 主任在准备阶段始终用上方卡片流（含会议材料卡+上传按钮），不切到「已送达」通知包；
    // 否则上传第一份材料后 materials 由空变有，会让此包从隐藏翻转为显示，准备卡片流连同
    // 刚上传的材料卡一起消失，造成「上传成功却看不到材料」的错觉。
    const npVisible = !!fromNotice &&
      d.stage !== 'ended' &&
      (uv === 'member' || uv === 'chair') &&
      !(uv === 'chair' && d.stage === 'preparing') &&
      (!!d.noticeDraft || !!(d.materials && d.materials.length))

    // 快速会议模式（进行中时生效）：隐藏代录、添加议题等逐题表决相关入口
    const qMode = d.stage === 'ongoing' && d.meetingMode === 'quick'

    if (d.stage === 'ended' && !endedDetailInitialized.value) {
      endedDetailOpen.value = !!d.minutesReady
      endedDetailInitialized.value = true
    }

    detail.value = d
    userView.value = uv
    currentStep.value = curStep
    step1Done.value = s1
    step2Done.value = s2
    step3Done.value = s3
    stepDone.value = sDone
    stepTotal.value = 2
    stepAllDone.value = sAllDone
    quickMode.value = qMode
    prepareSteps.value = pSteps
    prepareHint.value = pHint
    prepareMode.value = pMode
    noticePackageVisible.value = npVisible
    deliveryExpanded.value = false
    if (uv === 'chair' && d.stage === 'preparing') {
      loadRecipients(false)
      // 通知人员：还没产生通知记录→默认展开；已发过通知(有通知记录)→默认收起
      recipientOpen.value = !((d.notificationLogs && d.notificationLogs.length) || d.notifiedAt)
    }
  } catch (e) {
    toast({ title: '加载失败', icon: 'none' })
  }
}

function goStep(step) {
  // 只能跳转到已解锁的步骤
  if (step === 1 || (step === 2 && step1Done.value)) {
    currentStep.value = step
  }
}

async function confirmStep1() {
  const res = await showModal({
    title: '入会签到',
    content: '请确认：你已到场参加本次会议。',
    confirmText: '签到',
    cancelText: '再看看'
  })
  if (!res.confirm) return
  const now = new Date()
  const time = now.getHours() + ':' + String(now.getMinutes()).padStart(2, '0')
  step1Done.value = true
  step2Done.value = true
  step1Time.value = time
  currentStep.value = 2
  stepDone.value = Math.max(stepDone.value, 1)
  api.committeeSelfToggle(meetingId, 'signedIn').catch(() => {})
}

function confirmStep2() {
  currentStep.value = 2
}

async function confirmStep3() {
  const topics = detail.value && detail.value.record ? (detail.value.record.topics || []) : []
  if (topics.length && topics.some(t => !t.myVote)) {
    toast({ title: '请先完成全部议题投票', icon: 'none' })
    return
  }
  const res = await showModal({
    title: '确认提交',
    content: '提交后不能修改。请确认你的选择已经完成。',
    confirmText: '确认提交',
    cancelText: '再看看'
  })
  if (!res.confirm) return
  const now = new Date()
  const time = now.getHours() + ':' + String(now.getMinutes()).padStart(2, '0')
  step3Done.value = true
  step3Time.value = time
  stepDone.value = 2
  stepAllDone.value = true
}

async function vote(topicId, choice, selectedId) {
  if (!step1Done.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  const d = detail.value
  const topic = d && d.record && d.record.topics
    ? d.record.topics.find(t => String(t.id) === String(topicId))
    : null
  const selectedOption = topic && selectedId
    ? (topic.options || []).find(function (o) { return String(o.id) === String(selectedId) })
    : null
  const choiceLabel = selectedOption
    ? selectedOption.label
    : (choice === 'for_vote' ? '同意' : choice === 'against' ? '不同意' : '弃权')
  const res = await showModal({
    title: '确认你的选择',
    content: '你选择了「' + choiceLabel + '」。确认后这项不能修改。',
    confirmText: '确认选择',
    cancelText: '再看看'
  })
  if (!res.confirm) return
  await _submitVote(topicId, choice, selectedId)
}

async function _submitVote(topicId, choice, selectedId) {
  const d = detail.value
  if (d && d.record && d.record.topics) {
    const topics = d.record.topics.map(t => {
      if (t.id == topicId) {
        t.myVote = choice || selectedId
        if (selectedId) {
          var opt = (t.options || []).find(function (o) { return String(o.id) === String(selectedId) })
          if (opt) t.myVoteLabel = { id: opt.id, label: opt.label }
        }
      }
      return t
    })
    const allVoted = topics.every(t => t.myVote)
    d.record.topics = topics
    if (allVoted) step3Ready.value = true
  }
  try { await api.committeeVote(meetingId, topicId, choice, selectedId) } catch (e) {}
}

async function openProxyRecorder() {
  const d = detail.value
  if (!d || d.stage !== 'ongoing') {
    toast({ title: '会议进行中才可代录', icon: 'none' })
    return
  }
  proxyVisible.value = true
  proxyAction.value = 'signIn'
  proxyKeyword.value = ''
  proxyProofUrl.value = ''
  proxyProofName.value = ''
  proxyTopicId.value = d.record && d.record.topics && d.record.topics[0] ? d.record.topics[0].id : null
  proxyChoice.value = 'for_vote'
  proxySelectedId.value = null
  proxySelectedCount.value = 0
  await loadProxyTargets()
}

function closeProxyRecorder() {
  proxyVisible.value = false
}

async function loadProxyTargets() {
  try {
    const targets = await api.committeeProxyTargets(meetingId, proxyKeyword.value)
    proxyAllTargets.value = targets || []
    refreshProxyTargets()
  } catch (e) {
    toast({ title: e.message || '代录名单加载失败', icon: 'none' })
  }
}

function refreshProxyTargets() {
  const action = proxyAction.value
  const topicId = proxyTopicId.value
  const keyword = (proxyKeyword.value || '').trim().toLowerCase()
  let selectedCount = 0
  const targets = (proxyAllTargets.value || [])
    .filter(function (item) {
      if (!keyword) return true
      const name = (item.name || '').toLowerCase()
      const room = (item.roomNumber || '').toLowerCase()
      return name.indexOf(keyword) >= 0 || room.indexOf(keyword) >= 0
    })
    .map(function (item) {
      const votedTopicIds = item.votedTopicIds || []
      let disabled = false
      let disabledText = ''
      if (action === 'signIn') {
        disabled = !!item.signedIn
        disabledText = item.signedIn ? (item.signInByProxy ? '已代录签到' : '已签到') : ''
      } else {
        if (!topicId) {
          disabled = true
          disabledText = '请选择议题'
        } else if (!item.signedIn) {
          disabled = true
          disabledText = '未签到'
        } else if (votedTopicIds.indexOf(Number(topicId)) >= 0) {
          disabled = true
          disabledText = '已投票'
        }
      }
      const checked = disabled ? false : !!item.checked
      if (checked) selectedCount += 1
      return Object.assign({}, item, { disabled: disabled, disabledText: disabledText, checked: checked })
    })
  proxyTargets.value = targets
  proxySelectedCount.value = selectedCount
}

function onProxyKeywordInput(e) {
  proxyKeyword.value = e.target.value || ''
  refreshProxyTargets()
}

function pickProxyAction(action) {
  proxyAction.value = action
  proxyChoice.value = 'for_vote'
  proxySelectedId.value = null
  refreshProxyTargets()
}

function pickProxyTopic(topicId) {
  proxyTopicId.value = Number(topicId)
  proxyChoice.value = 'for_vote'
  proxySelectedId.value = null
  refreshProxyTargets()
}

function pickProxyChoice(choice) {
  proxyChoice.value = choice
  proxySelectedId.value = null
}

function pickProxyOption(selectedId) {
  proxySelectedId.value = Number(selectedId)
  proxyChoice.value = ''
}

function toggleProxyTarget(memberId) {
  memberId = Number(memberId)
  const targets = (proxyTargets.value || []).map(function (item) {
    if (Number(item.memberId) === memberId && !item.disabled) {
      return Object.assign({}, item, { checked: !item.checked })
    }
    return item
  })
  const selectedIds = targets.filter(function (item) { return item.checked }).map(function (item) { return item.memberId })
  const allTargets = (proxyAllTargets.value || []).map(function (item) {
    return Object.assign({}, item, { checked: selectedIds.indexOf(item.memberId) >= 0 })
  })
  proxyTargets.value = targets
  proxyAllTargets.value = allTargets
  proxySelectedCount.value = selectedIds.length
}

// (2) 代录凭证：只选图上传，结果写入 proxyProofUrl / proxyProofName，供 submitProxyAction 使用
async function pickProxyProof() {
  try {
    const r = await pickAndUpload('image/*')
    if (!r) return // 用户取消
    proxyProofUrl.value = r.url
    proxyProofName.value = r.fileName
    toast({ title: '已上传', icon: 'success' })
  } catch (e) {
    toast({ title: e.message || '上传失败', icon: 'none' })
  }
}

async function submitProxyAction() {
  if (proxySubmitting.value) return
  const selectedIds = (proxyTargets.value || [])
    .filter(function (item) { return item.checked && !item.disabled })
    .map(function (item) { return item.memberId })
  if (!selectedIds.length) {
    toast({ title: '请选择代录对象', icon: 'none' })
    return
  }
  if (!proxyProofUrl.value) {
    toast({ title: '请上传凭证', icon: 'none' })
    return
  }

  const payload = {
    actionType: proxyAction.value,
    memberIds: selectedIds,
    proofUrl: proxyProofUrl.value,
    proofName: proxyProofName.value
  }
  if (proxyAction.value === 'vote') {
    if (!proxyTopicId.value) {
      toast({ title: '请选择投票议题', icon: 'none' })
      return
    }
    payload.topicId = proxyTopicId.value
    const topic = (detail.value.record.topics || []).find(t => Number(t.id) === Number(proxyTopicId.value))
    if (topic && topic.decisionType === 'multi_choice') {
      if (!proxySelectedId.value) {
        toast({ title: '请选择投票选项', icon: 'none' })
        return
      }
      payload.selectedId = proxySelectedId.value
    } else {
      payload.choice = proxyChoice.value || 'for_vote'
    }
  }

  proxySubmitting.value = true
  try {
    await api.committeeProxySubmit(meetingId, payload)
    toast({ title: '代录已提交', icon: 'success' })
    proxyVisible.value = false
    proxySubmitting.value = false
    loadDetail()
  } catch (e) {
    proxySubmitting.value = false
    toast({ title: e.message || '提交失败', icon: 'none' })
  }
}

// 开始会议：固定使用快速模式，传统逐题表决入口不再开放
async function startMeeting() {
  try {
    await api.committeeAdvance(meetingId, 'start', 'quick')
    toast({ title: '会议已开始', icon: 'success' })
    goLive() // 进入「会议进行」录音向导（带硬导航兜底）
  } catch (e) { toast({ title: (e && e.message) || '操作失败', icon: 'none' }) }
}

async function resolveLiveMeetingId(id) {
  try {
    await api.committeeDetail(id)
    return id
  } catch (e) {
    if (!String((e && e.message) || '').includes('会议不存在')) throw e
    const list = await api.committeeList('ongoing').catch(() => [])
    const hit = (list || []).find((m) => m && m.stage === 'ongoing') || (list || [])[0]
    if (hit && hit.id) return hit.id
    throw e
  }
}

// 进入「会议进行」全屏向导页：仅快速模式（软路由偶发"URL变了却不切换视图"——加硬导航兜底确保必达，
// 对齐 Committee.vue「去通知」的做法；软跳后延时校验录音页 .live-page 是否真挂上，没挂上就 window.location 硬跳）
async function goLive() {
  let liveId = meetingId
  try {
    liveId = await resolveLiveMeetingId(meetingId)
    if (String(liveId) !== String(meetingId)) {
      toast({ title: '会议已更新，正在进入最新会议', icon: 'none' })
    }
  } catch (e) {
    toast({ title: (e && e.message) || '会议无法打开', icon: 'none' })
    goHome()
    return
  }
  const target = '/pages/meeting-live-quick/meeting-live-quick?type=committee&meetingId=' + liveId
  const browserUrl = '/meeting-live-quick?type=committee&meetingId=' + liveId
  try { redirectTo(target) } catch (navErr) { console.error('[开始会议] 软跳 reject：', navErr) }
  setTimeout(() => {
    if (!document.querySelector('.live-page')) {
      console.warn('[开始会议] 软跳未挂载录音页，硬导航兜底 →', browserUrl)
      window.location.href = browserUrl
    }
  }, 500)
}

// 进入「会议进行」全屏向导页：仅快速模式
function enterLive() { goLive() }

async function endMeeting() {
  try {
    await api.committeeAdvance(meetingId, 'end')
    toast({ title: '会议已结束', icon: 'success' })
    loadDetail()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function toggleFlag(flag) {
  try { await api.committeeToggleFlag(meetingId, flag); loadDetail() }
  catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function toggleJuwei() {
  try { await api.committeeToggleJuwei(meetingId); loadDetail() }
  catch (e) { toast({ title: e.message, icon: 'none' }) }
}

function toggleDeliveryList() {
  deliveryExpanded.value = !deliveryExpanded.value
}

function toggleFlowStats() {
  flowStatsOpen.value = !flowStatsOpen.value
}

function toggleArchiveLog() {
  archiveLogOpen.value = !archiveLogOpen.value
}

// 顶栏左箭头按明确来源返回，避免 replace/硬跳后历史栈只剩首页。
const showNoticeNav = computed(() => !!detail.value && userView.value === 'chair' && detail.value.stage === 'preparing')
// 软路由偶发不切换 → 统一硬导航兜底（真机曾表现为"返回点了没反应"）
function backWithFallback(target, browserUrl) {
  redirectTo(target)
  setTimeout(() => {
    if (document.querySelector('.detail-page')) window.location.replace(browserUrl)
  }, 400)
}
function handleDetailBack() {
  const source = String(route.query.from || '')
  // from=minutes 是「纪要页保存/返回后落到详情」的标记——纪要那边已经办完事了，
  // 再跳回纪要页会形成 详情↔纪要 互踢死循环（真机踩过），返回一律出到首页。
  if (source === 'meeting-live-quick') {
    // 会议已结束时进行页只会渲染成无意义的签到步，回首页；进行中才回进行页
    if (detail.value && detail.value.stage === 'ongoing') {
      backWithFallback('/pages/meeting-live-quick/meeting-live-quick?type=committee&meetingId=' + meetingId,
        '/meeting-live-quick?type=committee&meetingId=' + meetingId)
      return
    }
    backWithFallback('/main', '/main')
    return
  }
  if (source === 'todo') {
    backWithFallback('/pages/todo/todo', '/todo')
    return
  }
  if (route.query.fromNotice === '1' || source === 'notifications') {
    backWithFallback('/pages/notifications/notifications', '/notifications')
    return
  }
  // 会议列表是详情页最常见来源，也是无来源/刷新/from=minutes 后的安全落点。
  backWithFallback('/main', '/main')
}
function backToEditInfo() {
  setStorage('editMeetingId', meetingId)   // 交给 /main 的发起会议表单以「编辑模式」打开
  redirectTo('/main')
}
function goHome() { redirectTo('/main') }

// ——— 发送通知：接收对象前置到会议通知页（默认收起、全体委员默认全选） ———
const recipientOpen = ref(false)
const recipientList = ref([])   // [{ userRoleId, name, role, checked }]
const recipientSelectedCount = computed(() => recipientList.value.filter((x) => x.checked).length)
const recipientAllChecked = computed(() => recipientList.value.length > 0 && recipientList.value.every((x) => x.checked))
// 折叠头右侧摘要：全选→「全体委员 · N人」；部分→「已选 M/N人」；一个没选→「未选择」
const recipientSummary = computed(() => {
  const total = recipientList.value.length
  const sel = recipientSelectedCount.value
  if (!total) return '暂无委员'
  if (sel >= total) return '全体委员 · ' + total + '人'
  if (sel === 0) return '未选择'
  return '已选 ' + sel + '/' + total + '人'
})

async function loadRecipients(force) {
  if (!force && recipientList.value.length) return true
  try {
    const members = await api.committeeMembers()
    const checkedMap = new Map(recipientList.value.map((x) => [x.userRoleId, x.checked]))
    const list = (members || [])
      .map((m) => {
        const id = Number(m.userRoleId)
        return { userRoleId: id, name: m.name || '委员', role: m.role || '', checked: checkedMap.has(id) ? checkedMap.get(id) : true }
      })
      .filter((x) => x.userRoleId)
    if (!list.length) { recipientList.value = []; return false }
    recipientList.value = list
    return true
  } catch (e) {
    toast({ title: (e && e.message) || '加载委员名单失败', icon: 'none' })
    return false
  }
}

async function openRecipients() {
  if (sendSubmitting.value) return
  const ok = await loadRecipients(false)
  if (!ok || !recipientList.value.length) { toast({ title: '暂无可通知的委员', icon: 'none' }); return }
  confirmSendRecipients()
}
async function sendAppNoticeOnly() {
  if (sendSubmitting.value) return
  const ok = await loadRecipients(false)
  if (!ok || !recipientList.value.length) { toast({ title: '暂无可通知的委员', icon: 'none' }); return }
  const ids = recipientList.value.filter((x) => x.checked).map((x) => x.userRoleId)
  doSend(ids, { quietForward: true })
}
function toggleRecipient(id) {
  const it = recipientList.value.find((x) => x.userRoleId === id)
  if (it) it.checked = !it.checked
}
function toggleRecipientAll() {
  const target = !recipientAllChecked.value
  recipientList.value.forEach((x) => { x.checked = target })
}
function confirmSendRecipients() {
  const ids = recipientList.value.filter((x) => x.checked).map((x) => x.userRoleId)
  doSend(ids)
}

// 真正发送：把选中的委员 id 发给后端
async function doSend(ids, options) {
  if (sendSubmitting.value) return
  if (!ids || !ids.length) { toast({ title: '请至少选择一位委员', icon: 'none' }); return }
  const id = currentMeetingId()
  if (!id) { toast({ title: '当前会议信息异常，请返回首页重新进入', icon: 'none' }); return }
  sendSubmitting.value = true
  try {
    await api.committeeSendAll(id, ids)
    if (!(options && options.silent)) {
      toast({ title: options && options.quietForward ? 'App内已通知' : '通知已发送', icon: 'success' })
    }
    recipientOpen.value = false
    await loadDetail()
    if (!(options && options.quietForward)) openForward()
  } catch (e) {
    toast({ title: (e && e.message) || '发送失败', icon: 'none' })
  } finally {
    sendSubmitting.value = false
  }
}

// ——— 通知卡片数据 + 发送记录 ———
const noticeTopicsText = computed(() => {
  const ts = (detail.value && detail.value.record && detail.value.record.topics) || []
  const titles = ts.map((t) => (t && t.title) || '').filter(Boolean)
  if (!titles.length) return '（待定）'
  if (titles.length <= 2) return titles.join('、')
  return titles.slice(0, 2).join('、') + ' 等'
})
const noticeSent = computed(() => {
  const d = detail.value || {}
  return !!(d.notifiedAt || (d.notificationLogs && d.notificationLogs.length) || (d.delivery && d.delivery.total > 0))
})
const noticeSentTime = computed(() => fmtSendTime(detail.value && detail.value.notifiedAt))
const sendRecordText = computed(() => {
  const by = detail.value && detail.value.notifiedByName
  const prefix = by ? ('已由 ' + by + ' 发送') : '通知已发送'
  return prefix + (noticeSentTime.value ? ' · ' + noticeSentTime.value : '')
})
function fmtHm(t) { return String(t || '').slice(0, 5) }
function fmtSendTime(s) { return s ? String(s).replace('T', ' ').slice(0, 16) : '' }
function logText(log) {
  const action = log && log.channel === 'wechat' ? '通过微信通知' : 'App内通知'
  const prefix = log.sentByName ? ('已由 ' + log.sentByName + ' ' + action) : (log && log.channel === 'wechat' ? '已通过微信通知' : '已发送App内通知')
  return prefix + (log.sentAt ? ' · ' + fmtSendTime(log.sentAt) : '')
}
function fmtCnDate(s) {
  const m = String(s || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  return m ? (Number(m[1]) + '年' + Number(m[2]) + '月' + Number(m[3]) + '日') : (s || '')
}
// 通知正文：微信口吻的一段话（替代行列式的 时间/地点/议题）
const noticeText = computed(() => {
  const d = detail.value || {}
  return '各位委员：现拟于 ' + fmtCnDate(d.meetingDate) + ' ' + fmtHm(d.meetingTime) +
    ' 在' + (d.location || '') + '召开本次会议，主要议题：' + noticeTopicsText.value + '，请准时出席。'
})

// ——— 发送通知后的"转发到微信"弹层（可复制文本 + 进会/地图链接） ———
const forwardVisible = ref(false)
function openForward() {
  forwardVisible.value = true
}
function closeForward() { forwardVisible.value = false }

// 会议地点地图搜索链接（默认高德；关键词搜索，无需经纬度）。
// callnative=1：手机上优先直接唤起高德App并定位到该地点，未装App则落到高德H5地图直接显示该地点
function mapSearchUrl(loc) {
  return 'https://uri.amap.com/search?keyword=' + encodeURIComponent(loc || '') + '&callnative=1'
}
// 点击会议地点 → 打开高德地图
function openMap(loc) {
  if (!loc) return
  const url = mapSearchUrl(loc)
  try { window.open(url, '_blank') } catch (e) { window.location.href = url }
}
// 进会/地点导航链接（转发文本 + 弹窗预览复用）。进会为普通链接：委员本机登录过会自动带身份直达，否则先登录再落到该会议
const joinUrl = computed(() => (typeof location !== 'undefined' ? location.origin : '') + '/committee-detail?id=' + (currentMeetingId() || ''))
const mapNavUrl = computed(() => (detail.value && detail.value.location) ? mapSearchUrl(detail.value.location) : '')
// 转发到微信的纯文本：学腾讯会议邀请——「会议主题/时间/地点/议题」字段各占一行、链接单独成行，清晰。
// 注：微信聊天是纯文本，只有完整网址能自动变蓝可点，无法把"地名"做成链接——故保留网址供群里点开
const shareText = computed(() => {
  const d = detail.value || {}
  const time = (fmtCnDate(d.meetingDate) + ' ' + fmtHm(d.meetingTime)).trim()
  const lines = ['新会议通知：' + (d.title || '业委会会议'), '']
  if (time) lines.push('会议时间：' + time)
  if (d.location) lines.push('会议地点：' + d.location)
  lines.push('会议议题：' + noticeTopicsText.value)
  lines.push('', '点击链接入会：', joinUrl.value)
  if (mapNavUrl.value) lines.push('', '地点导航：', mapNavUrl.value)
  lines.push('', '请各位委员准时参加，点击上方会议链接即可进入')
  return lines.join('\n')
})
// 弹窗预览里点"进入会议"
function openJoin() {
  const url = joinUrl.value
  try { window.open(url, '_blank') } catch (e) { window.location.href = url }
}
// 把通知文本写进剪贴板（返回 Promise，供"复制通知内容"和"转发到微信"共用）
function writeShareToClipboard() {
  const text = shareText.value
  if (navigator.clipboard && navigator.clipboard.writeText) {
    return navigator.clipboard.writeText(text)
  }
  return new Promise((resolve, reject) => {
    try {
      const ta = document.createElement('textarea')
      ta.value = text; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
      resolve()
    } catch (e) { reject(e) }
  })
}
function copyShareText() {
  writeShareToClipboard()
    .then(() => toast({ title: '已复制，去微信粘贴', icon: 'success' }))
    .catch(() => toast({ title: '复制失败，请长按文本手动复制', icon: 'none' }))
}
// 转发到微信：先复制通知内容，再"尽力"唤起微信（安卓多能跳转；iOS 常无效但不影响使用），到群里直接粘贴即可。
// ⚠ 严禁用 window.location.href='weixin://' 顶层跳转——那会把当前 H5 页面 unload（真机表现为"网页被自动关闭"）。
// 改用隐藏 iframe 唤起 scheme：唤得起就跳微信，唤不起也只是无效，当前页始终不被关闭/重置。
async function openWechat() {
  // ① 复制通知文本到剪贴板（粘贴到业主群）
  try {
    await writeShareToClipboard()
    toast({ title: '已复制通知，请到微信粘贴到业主群', icon: 'none' })
  } catch (e) {
    toast({ title: '请长按下方文本手动复制后到微信粘贴', icon: 'none' })
  }
  // ② 尝试拉起微信
  try {
    const ifr = document.createElement('iframe')
    ifr.style.cssText = 'display:none;width:0;height:0;border:0'
    ifr.src = 'weixin://'
    document.body.appendChild(ifr)
    setTimeout(() => { try { document.body.removeChild(ifr) } catch (e) {} }, 1500)
  } catch (e) {}
  // ③ 微信通知只做留痕，不改 App 内送达状态；App 内通知仍由「App内通知」按钮单独完成。
  if (prepareMode.value === 'send') {
    try {
      await api.committeeMarkWechatNotified(currentMeetingId())
      await loadDetail()
    } catch (e) {
      toast({ title: (e && e.message) || '微信通知记录失败', icon: 'none' })
    }
  }
}

// 委员"确认参会"：标记本人出席(signedIn)，与主任的确认参会人数统计、「我的会议」页保持一致
async function confirmAttend() {
  try {
    await api.committeeSelfToggle(meetingId, 'signedIn')
    toast({ title: '已确认参会', icon: 'success' })
    loadDetail()
  } catch (e) { toast({ title: e.message || '操作失败', icon: 'none' }) }
}

// 委员"无法参会"：标记因故缺席(declined)
async function declineAttend() {
  try {
    await api.committeeSelfToggle(meetingId, 'declined')
    toast({ title: '已登记：无法参会', icon: 'none' })
    loadDetail()
  } catch (e) { toast({ title: e.message || '操作失败', icon: 'none' }) }
}

// "取消参会"：回到未响应（从确认参会人数中移除）
async function cancelAttend() {
  const res = await showModal({
    title: '取消参会',
    content: '确定取消本人参会？取消后将从确认参会人数中移除。',
    confirmText: '取消参会',
    cancelText: '再想想'
  })
  if (!res.confirm) return
  try {
    await api.committeeSelfToggle(meetingId, 'cancel')
    toast({ title: '已取消参会', icon: 'none' })
    loadDetail()
  } catch (e) { toast({ title: e.message || '操作失败', icon: 'none' }) }
}

async function toggleDelivery(userRoleId, field) {
  try { await api.committeeToggleDelivery(meetingId, userRoleId, field); loadDetail() }
  catch (err) { toast({ title: err.message, icon: 'none' }) }
}

async function signAll() {
  try { await api.committeeSignAll(meetingId); loadDetail() }
  catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function markCompliance(status) {
  try { await api.committeeCompliance(meetingId, status); loadDetail() }
  catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function archiveDirect() {
  const res = await showModal({
    title: '直接归档',
    content: '归档后该会议将移出会议管理，进入资料库供查阅。确认归档？',
    confirmText: '确认归档'
  })
  if (!res.confirm) return
  try {
    await api.committeeArchive(meetingId)
    toast({ title: '已归档', icon: 'success' })
    loadDetail()
  } catch (e) { toast({ title: e.message || '归档失败', icon: 'none' }) }
}

async function publishNow() {
  // 无效会议：点击后先提示「会议无效」，确认后再公示（详情页不再常驻这条警告）
  if (detail.value && detail.value.compliance === 'invalid') {
    const res = await showModal({
      title: '会议无效，仍要公示吗？',
      content: '本次会议签到不过半，已判定为「会议无效」。确认仍要公示归档吗？',
      confirmText: '仍要公示',
      cancelText: '再想想'
    })
    if (!res.confirm) return
  }
  try { await api.committeePublish(meetingId); toast({ title: '已公示', icon: 'success' }); loadDetail() }
  catch (e) { toast({ title: e.message, icon: 'none' }) }
}

// 撤销归档：仅误归档用，必填原因，留痕；已公示需先撤回公示
async function revokeArchive() {
  const res = await showModal({
    title: '撤销归档',
    content: '仅用于误归档（归错会议/误点归档/未结束就归档）。撤销后会议回到归档前阶段，需重新归档。',
    editable: true,
    placeholderText: '请填写撤销原因（必填）'
  })
  if (!res.confirm) return
  var reason = (res.content || '').trim()
  if (!reason) { toast({ title: '撤销必须填写原因', icon: 'none' }); return }
  try {
    await api.committeeRevokeArchive(meetingId, reason)
    toast({ title: '已撤销归档', icon: 'success' })
    loadDetail()
  } catch (e) { toast({ title: e.message || '撤销失败', icon: 'none' }) }
}

async function withdrawPublish() {
  const res = await showModal({
    title: '撤回公示',
    content: '撤回后业主将看到"已撤回"状态，且需重新公示。',
    editable: true,
    placeholderText: '请填写撤回原因（必填）'
  })
  if (!res.confirm) return
  var reason = (res.content || '').trim()
  if (!reason) { toast({ title: '撤回必须填写原因', icon: 'none' }); return }
  try {
    await api.committeeWithdrawPublish(meetingId, reason)
    toast({ title: '已撤回公示', icon: 'success' })
    loadDetail()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function viewMinutesRevisions() {
  try {
    var list = await api.committeeMinutesRevisions(meetingId)
    if (!list || !list.length) { toast({ title: '暂无修订记录', icon: 'none' }); return }
    var lines = list.map(function (r) {
      return 'v' + r.versionNo + ' · ' + (r.editorName || '—') + ' · ' + (r.createdAt || '')
    })
    await showModal({
      title: '纪要修订历史',
      content: lines.join('\n'),
      showCancel: false,
      confirmText: '关闭'
    })
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function removeMeeting() {
  const isRecordingThisMeeting = meetingRecordingSession.active
    && String(meetingRecordingSession.meetingId || '') === String(meetingId)
  const res = await showModal({
    title: '确认取消',
    content: '确定取消该会议？'
      + (isRecordingThisMeeting ? '\n\n该会议正在录音，取消后录音将立即停止并丢弃。' : '')
  })
  if (res.confirm) {
    try { await api.committeeRemove(meetingId); await discardMeetingRecording(meetingId); navigateBack() }
    catch (e) { toast({ title: e.message, icon: 'none' }) }
  }
}

// 清空通知记录（测试用）：确认后删本会议全部通知历史+送达、重置为「未通知」，再刷新详情
async function clearNotices() {
  const res = await showModal({
    title: '清空通知记录',
    content: '仅供测试：将删除本会议的全部通知记录，并重置为「未通知」。确定清空？'
  })
  if (!res.confirm) return
  try {
    await api.committeeClearNotifications(currentMeetingId())
    toast({ title: '已清空通知记录', icon: 'success' })
    await loadDetail()
  } catch (e) { toast({ title: (e && e.message) || '清空失败', icon: 'none' }) }
}

function openAddTopic() {
  addTopicVisible.value = true
  newTopicForm.title = ''
  newTopicForm.decisionType = 'simple'
  newTopicForm.type = 'decision'
  newTopicForm.optionsText = ''
  newTopicForm.realName = false
}
function closeAddTopic() {
  addTopicVisible.value = false
}
function pickDecisionType(type) {
  newTopicForm.decisionType = type
}
function toggleRealName() {
  newTopicForm.realName = !newTopicForm.realName
}
async function submitAddTopic() {
  const f = newTopicForm
  if (!f.title.trim()) {
    toast({ title: '请输入议题名称', icon: 'none' })
    return
  }
  var optionsJson = null
  if (f.decisionType === 'multi_choice' && f.optionsText.trim()) {
    var labels = f.optionsText.split('\n').filter(function (l) { return l.trim() })
    var opts = labels.map(function (l, i) { return { id: i + 1, label: l.trim() } })
    optionsJson = JSON.stringify(opts)
  }
  try {
    await api.committeeAddTopic(meetingId, f.title.trim(), f.type, f.decisionType, optionsJson, f.realName)
    toast({ title: '议题已添加', icon: 'success' })
    addTopicVisible.value = false
    loadDetail()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}
async function removeTopic(topicId) {
  const res = await showModal({ title: '删除议题', content: '确认删除该议题？' })
  if (res.confirm) {
    try { await api.committeeRemoveTopic(meetingId, topicId); await loadDetail() }
    catch (e) { toast({ title: e.message, icon: 'none' }) }
  }
}
// 在新标签页打开/预览文件（H5）
function openFile(url) {
  if (!url) return
  window.open(url, '_blank')
}

// 判断是否图片（按 fileType 或扩展名）
function isImageFile(url, fileType) {
  var s = ((fileType || '') + ' ' + (url || '')).toLowerCase()
  return /\.(jpg|jpeg|png|gif|webp)(\?|$)/.test(s) || /(jpg|jpeg|png|gif|webp|图片|照片)/.test((fileType || '').toLowerCase())
}

// (1) 议题证据：选图/PDF 上传 → committeeAddEvidence → 刷新
async function addEvidence() {
  try {
    const r = await pickAndUpload('image/*,application/pdf')
    if (!r) return // 用户取消
    await api.committeeAddEvidence(meetingId, r.fileName, r.fileType, r.url)
    toast({ title: '已上传', icon: 'success' })
    loadDetail()
  } catch (e) {
    toast({ title: e.message || '上传失败', icon: 'none' })
  }
}
async function removeEvidence(evId) {
  try { await api.committeeRemoveEvidence(meetingId, evId); await loadDetail() }
  catch (e) { toast({ title: e.message, icon: 'none' }) }
}

function viewMinutes() {
  // 会议详情专用的只读纪要页（独立于录音页那套 Minutes.vue），返回固定回会议详情
  // 软路由偶发"URL变了却不切换视图" → 硬导航兜底，确保必达
  const q = 'meetingId=' + meetingId
  navigateTo('/pages/minutes-view/minutes-view?' + q)
  setTimeout(() => {
    if (document.querySelector('.detail-page')) window.location.href = '/minutes-view?' + q
  }, 300)
}

// 会后生成会议纪要：任务直接在会议详情页发起，最小化弹窗后也不提前跳转纪要页。
async function generateMinutes() {
  if (generatingMinutes.value) return
  generatingMinutes.value = true
  minutesOverlayShown.value = true
  aiTask.overlayShown = true
  startAiTask({
    label: '会议纪要生成中…',
    originPath: '/pages/committee-detail/committee-detail?id=' + meetingId,
    targetPath: '/pages/minutes-view/minutes-view?meetingId=' + meetingId
  })
  let text = ''
  try {
    const result = await api.committeeQuickPolish(meetingId)
    text = (result && (result.minutesMarkdown || result.minutes)) || ''
    if (!text) text = await api.committeeMinutes(meetingId)
    if (!text || !String(text).trim()) throw new Error('未生成有效的会议纪要内容')
    finishAiTask({ doneLabel: '会议纪要已生成' })
    generatingMinutes.value = false // 弹窗切换到“已完成”，等待用户主动查看
    await loadDetail()
  } catch (e) {
    generatingMinutes.value = false
    minutesOverlayShown.value = false
    aiTask.overlayShown = false
    failAiTask({ failLabel: '会议纪要生成失败' })
    toast({ title: (e && e.message) || '生成失败，请稍后重试', icon: 'none' })
  }
}

function onMinutesDone() {
  minutesOverlayShown.value = false
  aiTask.overlayShown = false
  clearAiTask()
  viewMinutes()
}

// 关闭弹窗只收起本页入口；后端已发起的生成请求仍会继续完成。
function onMinutesClose() {
  minutesOverlayShown.value = false
  aiTask.overlayShown = false
}

// AI 生成党建新闻：红色党建遮罩开跑 → 调大模型生成 → 缓存 → 完成后进独立新闻页
// 新闻生成状态（服务端权威，切回自动对账）：none/running/success/failed
const newsState = ref('none')
let _newsPollTimer = null
let _newsPolling = false
function stopNewsPoll() { if (_newsPollTimer) { clearTimeout(_newsPollTimer); _newsPollTimer = null } _newsPolling = false }

// 用户点「AI生成新闻稿」：后端 @Async 后台生成，前端发起即返回后轮询状态。
// 退出微信 / 切到别的 App / 锁屏都不影响后台跑到底并落库；切回来对账即见结果。
async function generateNews() {
  if (generatingNews.value) return
  generatingNews.value = true
  // overlayShown=true → 本页全屏遮罩盖着任务时隐藏全局悬浮胶囊（免重复）；关遮罩/切走时再交还胶囊。
  aiTask.overlayShown = true
  startAiTask({ label: '党建新闻生成中…', originPath: window.location.pathname, targetPath: '/pages/news/news?meetingId=' + meetingId })
  try {
    const st = await api.committeeGenerateNews(meetingId) // 发起（30s 内已有 running 则去重）
    if (st && st.status === 'success' && st.content) { onNewsSuccess(st); return }
    if (st && st.status === 'failed') { onNewsFailed(); return }
    newsState.value = 'running'
    watchNews()
  } catch (e) {
    onNewsFailed(e)
  }
}

// 轮询服务端新闻状态直到 success/failed（3s/次）；本页在前台就一直转，切走(unmount)则停、由服务端继续跑
function watchNews() {
  if (_newsPolling) return
  _newsPolling = true
  const tick = async () => {
    _newsPollTimer = null
    try {
      const st = await api.committeeNewsStatus(meetingId)
      if (st && st.status === 'success' && st.content) return onNewsSuccess(st)
      if (st && st.status === 'failed') return onNewsFailed()
      newsState.value = (st && st.status) || 'running'
    } catch (e) {}
    _newsPollTimer = setTimeout(tick, 3000)
  }
  _newsPollTimer = setTimeout(tick, 3000)
}

function onNewsSuccess(st) {
  stopNewsPoll()
  newsState.value = 'success'
  try { sessionStorage.setItem('committee_news_' + meetingId, JSON.stringify({ title: st.title, content: st.content })) } catch (e) {}
  finishAiTask({ doneLabel: '党建新闻已生成' })
  generatingNews.value = false // 遮罩转「完成」态，等用户点「查看新闻稿」
}
function onNewsFailed(e) {
  stopNewsPoll()
  newsState.value = 'failed'
  failAiTask({ failLabel: '党建新闻生成失败' })
  if (!newsOverlayGone()) toast({ title: (e && e.message) || '生成失败，请重试', icon: 'none' })
  generatingNews.value = false
}
// 遮罩此刻是否已不在用户眼前（切走了详情页 / 关了遮罩）→ 决定失败是否需 toast（遮罩自己会显失败态）
function newsOverlayGone() { return !document.querySelector('.detail-page') || !generatingNews.value }

// 切回页面 / 进入已结束详情：对账服务端新闻状态，露出「查看新闻稿 / 生成中」入口；running 时接着轮询兜底
async function reconcileNews() {
  if (!meetingId) return
  try {
    const st = await api.committeeNewsStatus(meetingId)
    newsState.value = (st && st.status) || 'none'
    if (st && st.status === 'success' && st.content) {
      try { sessionStorage.setItem('committee_news_' + meetingId, JSON.stringify({ title: st.title, content: st.content })) } catch (e) {}
      if (aiTask.active) finishAiTask({ doneLabel: '党建新闻已生成' })
    } else if (st && st.status === 'running') {
      // 服务端仍在后台生成 → 全局悬浮胶囊兜底「生成中」入口，并接着轮询到完成
      if (!aiTask.active && !aiTask.done && !aiTask.failed) {
        startAiTask({ label: '党建新闻生成中…', originPath: '/pages/committee-detail/committee-detail?id=' + meetingId, targetPath: '/pages/news/news?meetingId=' + meetingId })
      }
      watchNews()
    }
  } catch (e) {}
}
// 页面重新可见（从微信后台切回 / 解锁）→ 对账一次，保证「切回有查看入口」
function onNewsVisibility() { if (typeof document !== 'undefined' && document.visibilityState === 'visible') reconcileNews() }

// 新闻按钮文案随状态：已生成→查看；生成中→去新闻页看进度；否则→生成
const newsBtnLabel = computed(() => newsState.value === 'success' ? '查看新闻稿' : (newsState.value === 'running' ? '新闻生成中·查看' : 'AI生成新闻稿'))
function onNewsBtn() {
  if (newsState.value === 'success' || newsState.value === 'running') return onNewsDone()
  generateNews()
}

// 遮罩「查看新闻稿」/按钮查看：进入独立党建新闻页（带软路由不切换的硬导航兜底）。已查看→清掉全局任务。
function onNewsDone() {
  aiTask.overlayShown = false
  clearAiTask()
  const q = 'meetingId=' + meetingId
  navigateTo('/pages/news/news?' + q)
  setTimeout(() => { if (document.querySelector('.detail-page')) window.location.href = '/news?' + q }, 500)
}
// 关闭遮罩：交还给全局悬浮胶囊（生成中→「生成中」胶囊；已完成→「已生成·查看」胶囊，不丢任务）
function onNewsClose() { generatingNews.value = false; aiTask.overlayShown = false }

// 面向民众的公开纪要：只读正式纪要正文，与内部工作视图分开。
// ⚠ 软路由偶发不切换（见记忆 soft-router-push-intermittent-no-switch）→ 加硬导航兜底，否则「查看公示内容」偶尔点了没反应
function viewPublicMinutes() {
  const target = '/minutes-public?meetingId=' + meetingId
  navigateTo('/pages/minutes-public/minutes-public?meetingId=' + meetingId)
  setTimeout(() => { if (document.querySelector('.detail-page')) window.location.href = target }, 300)
}

// 内部总结（内部 AI 议题报告详细版 + 决议），仅供业委会内部查看。同上加硬导航兜底
function viewInternalReport() {
  const target = '/minutes-internal?meetingId=' + meetingId
  navigateTo('/pages/minutes-internal/minutes-internal?meetingId=' + meetingId)
  setTimeout(() => { if (document.querySelector('.detail-page')) window.location.href = target }, 300)
}

// 会议待办事项独立页
function viewTodos() {
  // 软路由偶发不切换 → 硬导航兜底
  const target = '/minutes-todos?meetingId=' + meetingId
  navigateTo('/pages/minutes-todos/minutes-todos?meetingId=' + meetingId)
  setTimeout(() => {
    if (document.querySelector('.detail-page')) window.location.href = target
  }, 300)
}

// (4) 补充归档材料：先填补充原因，再选文件上传 → committeeAddArchiveExtra → 刷新
async function addArchiveExtra() {
  const res = await showModal({
    title: '补充归档材料',
    content: '请先填写补充原因，再选择文件上传。',
    editable: true,
    placeholderText: '请填写补充原因（必填）'
  })
  if (!res.confirm) return
  const reason = (res.content || '').trim()
  if (!reason) { toast({ title: '请填写补充原因', icon: 'none' }); return }
  try {
    const r = await pickAndUpload('*/*')
    if (!r) return // 用户取消
    await api.committeeAddArchiveExtra(meetingId, r.fileName, humanSize(r.fileSize), r.fileType, reason, r.url)
    toast({ title: '已上传', icon: 'success' })
    loadDetail()
  } catch (e) {
    toast({ title: e.message || '上传失败', icon: 'none' })
  }
}

function removeArchiveExtra() {
  toast({ title: '归档补充材料已留痕，不支持直接删除', icon: 'none' })
}

async function previewMaterial(idx) {
  var materials = detail.value && detail.value.materials
  if (!materials || !materials[idx]) return
  var m = materials[idx]
  var content = m.content || '（暂无预览内容）'
  await showModal({
    title: m.name,
    content: content,
    showCancel: false,
    confirmText: '关闭'
  })
}

// (3) 会议材料：选任意文件上传 → committeeAddMaterial → 刷新
async function uploadMaterial() {
  try {
    const r = await pickAndUpload('*/*')
    if (!r) return // 用户取消
    await api.committeeAddMaterial(meetingId, r.fileName, humanSize(r.fileSize), r.fileType, r.url)
    toast({ title: '已上传', icon: 'success' })
    loadDetail()
  } catch (e) {
    toast({ title: e.message || '上传失败', icon: 'none' })
  }
}

async function removeMaterial(item) {
  if (!item) return
  const res = await showModal({
    title: '删除材料',
    content: '确认删除「' + item.name + '」？'
  })
  if (res.confirm) {
    try { await api.committeeRemoveMaterial(meetingId, item.id); loadDetail() }
    catch (err) { toast({ title: err.message, icon: 'none' }) }
  }
}

async function previewArchiveMaterials() {
  var materials = detail.value && detail.value.materials
  if (!materials || !materials.length) return
  var list = materials.map(function (m, i) { return (i + 1) + '. ' + m.name }).join('\n')
  await showModal({
    title: '会议材料（' + materials.length + '份）',
    content: list,
    showCancel: false,
    confirmText: '关闭'
  })
}

// ── 编辑会议 ──

async function openEdit() {
  var d = detail.value
  // 规则8：已通知后修改重大信息须重新通知全体成员
  if (d.coreLocked) {
    const res = await showModal({
      title: '会议已通知',
      content: '会议已通知全体成员。修改日期/时间/地点等重大信息后，需重新通知。是否继续？',
      confirmText: '继续修改'
    })
    if (res.confirm) _doOpenEdit(d)
    return
  }
  _doOpenEdit(d)
}

function _doOpenEdit(d) {
  editVisible.value = true
  noticeContentDirty.value = false
  editForm.title = d.title || ''
  if (d.meetingDate) {
    editForm.meetingDate = d.meetingDate
    editForm.meetingTime = (d.meetingTime || '').slice(0, 5) // 去掉秒（"09:00:00"→"09:00"），与新建会议一致
  } else {
    const t = new Date()
    t.setDate(t.getDate() + 1)
    const mo = String(t.getMonth() + 1).padStart(2, '0')
    const day = String(t.getDate()).padStart(2, '0')
    editForm.meetingDate = t.getFullYear() + '-' + mo + '-' + day
    editForm.meetingTime = '10:00'
  }
  editForm.location = d.location || ''
  syncEditLocationPreset(editForm.location)
  // 主要议题：按准备会议时添加的议题标题，逐条编号列出；无议题则回退到补充说明
  editForm.description = buildTopicsText(d) || d.description || ''
  editForm.content = (d.noticeDraft && d.noticeDraft.content) || ''
}

// 把会议议题标题拼成"1.xxx\n2.xxx"
function buildTopicsText(d) {
  const topics = (d && d.record && d.record.topics) || []
  return topics
    .filter(t => (t.title || '').trim())
    .map((t, i) => (i + 1) + '.' + t.title.trim())
    .join('\n')
}

function closeEdit() {
  editVisible.value = false
}

function onEditContentInput(e) {
  editForm.content = e.target.value
  noticeContentDirty.value = true
}

function pickEditLocation(location) {
  editForm.location = location
}

async function submitEdit() {
  var form = editForm
  if (!form.title || !form.meetingDate || !form.meetingTime || !form.location) {
    toast({ title: '请补全标题、时间和地点', icon: 'none' })
    return
  }
  try {
    // 先存会议要素（会按新要素自动重生成通知草稿）
    await api.committeeUpdate(meetingId, { ...form })
    // 若手动改过通知正文，再覆盖保存（保留自定义措辞）
    if (noticeContentDirty.value && form.content) {
      await api.committeeUpdateNotice(meetingId, form.title, form.content)
    }
    toast({ title: '已保存', icon: 'success' })
    editVisible.value = false
    loadDetail()
  } catch (e) {
    toast({ title: e.message || '保存失败', icon: 'none' })
  }
}

// ── 编辑通知草稿 ──

function editNoticeDraft() {
  var draft = detail.value && detail.value.noticeDraft
  noticeEditVisible.value = true
  noticeEditForm.title = draft ? draft.title : ''
  noticeEditForm.content = draft ? draft.content : ''
}

function closeNoticeEdit() {
  noticeEditVisible.value = false
}

async function submitNoticeEdit() {
  var form = noticeEditForm
  if (!form.title || !form.content) {
    toast({ title: '标题和正文不能为空', icon: 'none' })
    return
  }
  try {
    await api.committeeUpdateNotice(meetingId, form.title, form.content)
    toast({ title: '通知已更新', icon: 'success' })
    noticeEditVisible.value = false
    loadDetail()
  } catch (e) {
    toast({ title: e.message || '保存失败', icon: 'none' })
  }
}

function showWip() { toast({ title: '功能开发中', icon: 'none' }) }
</script>

<style scoped>
:deep(.page-nav) { background: var(--c-primary-dark); }
.del-meeting-link { color:#ccc; font-size:12px; border:1px solid #e8e8e8; border-radius:6px; padding:3px 10px; cursor:pointer; }
.del-meeting-link:active { background:#f5f5f5; }
.detail-page { min-height:100vh; background:#f4f5f7; padding:12px 0 280px; display:flex; flex-direction:column; box-sizing:border-box; }
.detail-body { flex:1 0 auto; }

/* Task banner */
.task-banner { padding:10px 14px; border-radius:12px; margin-bottom:12px; }
.task-banner.todo { background:#EBF5FB; }
.task-banner.warn { background:#FFF4E5; }
.task-banner.ok { background:#EAFAF1; }
.task-banner.readonly { background:#F8F9FA; }
.tb-title { font-size: 28rpx; font-weight:600; display:block; }
.tb-items { font-size: 28rpx; color:#666; display:block; margin-top:4px; }
.tb-flow { font-size: 28rpx; color:#666; margin-top:4px; display:block; }
.tb-hint { font-size: 28rpx; color:#666; margin-top:4px; display:block; }

/* Info */
.info-card { background:#fff; border-radius:16px; padding:16px; margin-bottom:12px; box-shadow:0 1px 3px rgba(0,0,0,0.04); }
.info-row { font-size: 28rpx; color:#555; margin-bottom:6px; display:flex; align-items:flex-start; gap:8px; }
.info-k { font-size: 28rpx; color:#666; flex-shrink:0; }
/* 会议地点：可点跳地图 */
.loc-link { color:#0051FF; display:inline-flex; align-items:center; gap:8rpx; flex-wrap:wrap; cursor:pointer; }
.loc-nav { font-size:22rpx; color:#0051FF; background:#EAF0FF; padding:2rpx 12rpx; border-radius:10rpx; white-space:nowrap; }
.proxy-entry {
  background:#fff; border-radius:16px; padding:14px 16px; margin-bottom:12px;
  box-shadow:0 1px 3px rgba(0,0,0,0.04);
  display:flex; align-items:center; justify-content:space-between; gap:12px;
}
.proxy-entry-copy { flex:1; min-width:0; }
.proxy-entry-title { display:block; font-size: 28rpx; font-weight:700; color:#333; }
.proxy-entry-desc { display:block; font-size: 28rpx; color:#666; margin-top:3px; line-height:1.4; }
.proxy-entry-btn {
  width:78px; height:36px; line-height:36px; margin:0; padding:0;
  border-radius:18px; border:0; background:var(--c-primary-dark); color:#fff;
  font-size: 28rpx; font-weight:700; flex-shrink:0;
}
.proxy-entry-btn.disabled { background:#e8e8e8; color:#666; }
.notice-draft-card { background:#FFF8EA; border:1px solid #FFE0A3; border-radius:16px; padding:16px; margin-bottom:12px; }
.nd-head { display:flex; align-items:center; justify-content:space-between; gap:10px; margin-bottom:8px; }
.nd-title { font-size: 28rpx; font-weight:700; color:#5C3D00; line-height:1.45; }
.nd-status { flex-shrink:0; font-size: 28rpx; color:#D88900; background:#fff; border-radius:12px; padding:3px 9px; line-height:1.35; }
.nd-status.ok { color:#1D9E75; background:#E1F5EE; }
.nd-content { display:block; font-size: 28rpx; color:#6B5300; line-height:1.7; white-space:pre-wrap; word-break:break-all; }
.nd-confirm-btn { margin-top:14px; width:100%; min-height:44px; padding:11px; background:var(--c-primary-dark); color:#fff; font-size: 30rpx; font-weight:700; border:none; border-radius:12px; }
.notice-package-card {
  background:#fff; border-radius:16px; padding:16px; margin-bottom:12px;
  border:1px solid #FFE0A3; box-shadow:0 1px 3px rgba(0,0,0,0.04);
}
.npc-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; margin-bottom:12px; }
.npc-title { display:block; font-size: 32rpx; font-weight:700; color:#333; line-height:1.4; }
.npc-sub { display:block; font-size: 28rpx; color:#666; margin-top:2px; line-height:1.45; }
.npc-badge { flex-shrink:0; font-size: 28rpx; color:#27AE60; background:#EAF7EF; border-radius:12px; padding:3px 9px; line-height:1.35; }
.npc-section { padding-top:10px; border-top:1px dashed #f0f0f0; margin-top:10px; }
.npc-section:first-of-type { padding-top:0; border-top:0; margin-top:0; }
.npc-section-title { display:block; font-size: 28rpx; font-weight:700; color:#5C3D00; margin-bottom:8px; line-height:1.45; }
.npc-content { display:block; font-size: 28rpx; color:#555; line-height:1.75; white-space:pre-wrap; word-break:break-all; }
.npc-empty { display:block; font-size: 28rpx; color:#666; padding:8px 0; }

/* Member: delivery card */
.my-delivery-card { background:#fff; border-radius:16px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,0.04); margin-bottom:12px; }
.mdc-title { font-size: 28rpx; font-weight:600; color:#333; margin-bottom:10px; display:block; }
.mdc-row { display:flex; align-items:center; justify-content:space-between; padding:8px 0; border-bottom:1px solid #f0f0f0; }
.mdc-label { font-size: 28rpx; color:#666; }
.mdc-status { font-size: 28rpx; font-weight:600; }
.mdc-status.ok { color:#27AE60; }
.mdc-status.no { color:#666; }
.mdc-tip { font-size: 28rpx; color:#666; margin-top:8px; line-height:1.5; display:block; }
.mdc-big { display:block; font-size: 28rpx; font-weight:600; color:#E67E22; margin-top:4px; }
.mdc-big.ok { color:#27AE60; }

/* 会议材料 */
.materials-card {
  background:#fff; border-radius:16px; padding:16px; margin-bottom:12px;
  box-shadow:0 1px 3px rgba(0,0,0,0.04);
}
.mt-title { font-size: 28rpx; font-weight:600; color:#333; display:block; margin-bottom:8px; }
.mt-item { display:flex; align-items:center; justify-content:space-between; padding:8px 0; border-bottom:1px solid #f0f0f0; }
.mt-item:last-child { border-bottom:none; }
.mt-name { font-size: 28rpx; color:#444; flex:1; }
.mt-size { font-size: 28rpx; color:#666; }

/* Steps card */
.steps-card { background:#fff; border-radius:16px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,0.04); margin-bottom:12px; }
.msc-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
.msc-title { font-size: 28rpx; font-weight:600; color:#333; }
.msc-progress { font-size: 28rpx; color:#FFA800; font-weight:600; }

.step-item { display:flex; gap:12px; }
.step-item:not(.last) .step-body { padding-bottom:16px; }
.step-item:not(.last) .step-num-wrap { padding-bottom:16px; }
.step-num-wrap { display:flex; flex-direction:column; align-items:center; flex-shrink:0; width:28px; }
.step-num {
  width:28px; height:28px; border-radius:50%;
  background:#f0f0f0; color:#666;
  display:flex; align-items:center; justify-content:center;
  font-size: 28rpx; font-weight:700;
}
.step-item.done .step-num { background:#27AE60; color:#fff; }
.step-item.active .step-num { background:#FFA800; color:#fff; }
.step-line { width:2px; flex:1; min-height:16px; background:#f0f0f0; margin:4px 0; }
.step-item.done .step-line { background:#27AE60; }

.step-body { flex:1; min-width:0; }
.step-title { font-size: 28rpx; font-weight:600; color:#333; display:block; }
.step-done-text { font-size: 28rpx; color:#27AE60; font-weight:500; display:block; margin-top:4px; }
.step-locked-text { font-size: 28rpx; color:#666; display:block; margin-top:6px; }
.step-sub-text { font-size: 28rpx; color:#666; display:block; margin:6px 0; }
.step-btn { margin-top:6px; }
.step-btn.primary { background:var(--c-primary-dark); color:#fff; border:none; border-radius:20px; padding:8px 16px; font-size: 28rpx; }

.topic-card { background:#fafafa; border-radius:14px; padding:16px; margin-top:12px; }
.tp-head { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.tp-type { font-size: 28rpx; padding:2px 7px; border-radius:6px; }
.tp-type.decision { background:#FFF6E5; color:#FFA800; }
.tp-type.major { background:#FDECEA; color:#E74C3C; }
.tp-title { font-size: 28rpx; font-weight:600; color:#333; flex:1; }

.vote-btns { display:flex; gap:10px; margin:12px 0 8px; }
.vbtn {
  flex:1;
  min-height:52px;
  box-sizing:border-box;
  font-size: 32rpx;
  font-weight:700;
  padding:14px 8px;
  border-radius:14px;
  background:#f0f0f0;
  color:#555;
  display:flex;
  align-items:center;
  justify-content:center;
  text-align:center;
}
.vbtn.for.on { background:#E8F7EE; color:#27AE60; font-weight:600; }
.vbtn.ag.on { background:#FDECEA; color:#E74C3C; font-weight:600; }
.vbtn.ab.on { background:#EBF5FB; color:#2980B9; font-weight:600; }
.vote-mine { font-size: 28rpx; color:#666; margin-top:4px; display:block; }
.vote-bars { margin-top:6px; }
.vote-locked { margin-top:6px; }
.vote-mine-done { font-size: 28rpx; color:#27AE60; font-weight:600; }
.vb-line { font-size: 28rpx; color:#666; }

.completion-card { background:#EAF7EF; border-radius:16px; padding:16px; display:flex; flex-direction:column; align-items:center; gap:12px; margin-bottom:12px; }
.cc-icon { width:32px; height:32px; border-radius:50%; background:#27AE60; color:#fff; display:flex; align-items:center; justify-content:center; font-size: 32rpx; font-weight:700; }
.cc-title { font-size: 28rpx; font-weight:600; color:#27AE60; }
.cc-sub { font-size: 28rpx; color:#52BE80; }

.perm-note { display:flex; align-items:flex-start; gap:7px; padding:9px 10px; border-radius:10px; background:#F8F9FA; border:1px solid #f0f0f0; font-size: 28rpx; color:#666; margin-top:10px; }
.pn-icon { width:16px; height:16px; border-radius:50%; background:#EAF2FB; color:#2980B9; font-size: 28rpx; font-weight:700; display:flex; align-items:center; justify-content:center; flex-shrink:0; }

/* Chair */
.chair-delivery-card { background:#fff; border-radius:16px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,0.04); margin-bottom:12px; }
.cdc-title { font-size: 28rpx; font-weight:600; color:#333; margin-bottom:10px; display:block; }
.cdc-row { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
.cdc-label { font-size: 28rpx; color:#666; width:68px; flex-shrink:0; }
.cdc-bar { flex:1; height:7px; background:#f0f0f0; border-radius:4px; overflow:hidden; }
.cdc-fill { height:100%; background:linear-gradient(90deg,#FFCC44,#FFA800); border-radius:4px; }
.cdc-fill.material { background:linear-gradient(90deg,#7FB3F5,#2980B9); }
.cdc-count { font-size: 28rpx; color:#666; width:28px; text-align:right; flex-shrink:0; }
.cdc-detail { display:block; font-size: 28rpx; color:#666; margin-top:6px; }
.cdc-status { display:block; font-size: 28rpx; font-weight:600; color:#E67E22; margin-top:6px; }
.cdc-status.ok { color:#27AE60; }
.cdc-footer { font-size: 28rpx; color:#666; margin-top:6px; display:block; }
.delivery-member-list { margin-top:12px; display:flex; flex-direction:column; gap:8px; }
.delivery-member { display:flex; align-items:flex-start; justify-content:space-between; gap:10px; padding:9px 10px; background:#fafafa; border-radius:10px; }
.delivery-member-main { flex:1; min-width:0; }
.delivery-member-name { display:block; font-size: 28rpx; color:#333; font-weight:600; line-height:1.45; word-break:break-all; }
.delivery-member-meta { display:block; font-size: 28rpx; color:#666; margin-top:2px; line-height:1.35; }
.delivery-tags { display:flex; flex-direction:column; gap:4px; flex-shrink:0; align-items:flex-end; }
.delivery-tag { font-size: 28rpx; color:#E67E22; background:#FFF3E0; border-radius:8px; padding:2px 7px; line-height:1.35; white-space:nowrap; }
.delivery-tag.ok { color:#27AE60; background:#EAF7EF; }
.threshold-note { text-align:center; padding:6px 0; font-size: 28rpx; color:#E67E22; }
.threshold-note.ok { color:#27AE60; }
.btn-disabled { background:#e8e8e8; color:#666; }

.chair-overview { background:#fff; border-radius:16px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,0.04); margin-bottom:12px; }
.co-title { font-size: 28rpx; font-weight:600; color:#333; margin-bottom:10px; display:block; }
.co-row { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
.co-label { font-size: 28rpx; color:#666; width:28px; flex-shrink:0; }
.co-bar { flex:1; height:7px; background:#f0f0f0; border-radius:4px; overflow:hidden; }
.co-fill { height:100%; border-radius:4px; }
.co-fill.ok { background:linear-gradient(90deg,#27AE60,#2ECC71); }
.co-fill.no { background:linear-gradient(90deg,#E74C3C,#EC7063); }
.co-count { font-size: 28rpx; color:#666; width:28px; text-align:right; flex-shrink:0; }
.co-checks { margin-top:8px; padding-top:8px; border-top:1px dashed #f0f0f0; }
.co-check-row { display:flex; align-items:center; gap:7px; padding:4px 0; }
.co-check-icon { width:16px; height:16px; border-radius:50%; flex-shrink:0; display:flex; align-items:center; justify-content:center; font-size: 28rpx; font-weight:700; color:#fff; }
.co-check-icon.ok { background:#27AE60; }
.co-check-icon.no { background:#E74C3C; }
.co-check-label { flex:1; font-size: 28rpx; color:#555; }
.co-check-detail { font-size: 28rpx; }
.co-check-detail.ok { color:#27AE60; }
.co-check-detail.no { color:#E74C3C; }

.chair-control { background:#fff; border-radius:16px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,0.04); }
.cc2-title { font-size: 28rpx; font-weight:600; color:#333; display:block; margin-bottom:6px; }
.cc2-note { font-size: 28rpx; color:#666; margin-bottom:12px; line-height:1.5; display:block; }
.cc2-btns { display:flex; gap:8px; justify-content:flex-end; }

/* Compliance */
.compliance-block { padding:10px 14px; border-radius:12px; margin-bottom:8px; }
.compliance-block.valid { background:#EAF7EF; }
.compliance-block.flawed { background:#FFF4E5; }
.compliance-block.invalid { background:#FDECEA; }
.cb-head { font-size: 28rpx; font-weight:600; }

.publish-block { padding:10px 14px; border-radius:12px; margin-bottom:8px; display:flex; align-items:center; justify-content:space-between; }
.publish-block.pending { background:#EBF5FB; }
.publish-block.ontime { background:#EAF7EF; }
.publish-block.late { background:#FFF4E5; }
.publish-block.overdue { background:#FDECEA; }
.pb-main { font-size: 28rpx; color:#333; flex:1; }

/* 合规判定 */
.compliance-pending {
  background:#fff; border-radius:16px; padding:16px; margin-bottom:12px;
  box-shadow:0 1px 3px rgba(0,0,0,0.04); text-align:center;
}
.cp-title { display:block; font-size: 28rpx; font-weight:600; color:#333; margin-bottom:12px; }
.cp-btns { display:flex; gap:8px; justify-content:center; }
.cb-auto { display:block; font-size: 28rpx; color:#666; margin-top:4px; }
.override-row { display:flex; align-items:center; gap:6px; padding:8px 0; }
.or-label { font-size: 28rpx; color:#666; }
.or-chip { font-size: 28rpx; color:#666; padding:2px 8px; border-radius:8px; background:#f5f5f5; }
.or-chip.on { color:#fff; background:#FFA800; font-weight:600; }

/* ===== 结束阶段 ===== */

/* 委员简洁结论 */
.ar-verdict {
  display:flex; align-items:flex-start; gap:12px; padding:16px; border-radius:16px; margin-bottom:12px;
}
.ar-verdict.valid { background:#E8F7EE; }
.ar-verdict.flawed { background:#FFF8E8; }
.ar-verdict.invalid { background:#FDECEA; }
.arv-icon { font-size: 36rpx; font-weight:700; width:32px; height:32px; border-radius:50%; display:flex; align-items:center; justify-content:center; flex-shrink:0; color:#fff; margin-top:2px; }
.ar-verdict.valid .arv-icon { background:#27AE60; }
.ar-verdict.flawed .arv-icon { background:#E67E22; }
.ar-verdict.invalid .arv-icon { background:#E74C3C; }
.arv-info { flex:1; }
.arv-title { display:block; font-size: 26rpx; font-weight:700; color:#333; }
.arv-meta { display:block; font-size: 24rpx; color:#666; margin-top:2px; }
.arv-result { display:block; font-size: 24rpx; color:#666; margin-top:4px; font-weight:500; }
.arv-reason { display:block; font-size: 24rpx; color:#666; margin-top:2px; }
.arch-reason { display:block; font-size: 28rpx; color:#666; margin-top:2px; }

/* 主任归档卡片 */
.ar-card { background:#fff; border-radius:16px; margin:12px 12px 0; box-shadow:0 2px 10px rgba(0,0,0,0.06); overflow:hidden; }
.arc-minutes-link { display:flex; align-items:center; justify-content:space-between; padding:16px 20px; border-top:1px solid #f0f0f0; cursor:pointer; }
.arc-minutes-link span:first-child { font-size:18px; font-weight:700; color:var(--c-primary-dark); }
.arc-minutes-arrow { font-size:22px; color:var(--c-primary-dark); }
.ar-card.valid { border-top:3px solid #27AE60; }
.ar-card.flawed { border-top:3px solid #E67E22; }
.ar-card.invalid { border-top:3px solid #E74C3C; }

.arc-head { display:flex; flex-direction:column; align-items:center; gap:10px; padding:20px 16px 24px; text-align:center; }
/* 按议题数量动态调整字号 */
.ar-card.card-sz-xl .arc-head { padding:32px 16px 36px; }
.ar-card.card-sz-xl .arch-title { font-size:34px; }
.ar-card.card-sz-lg .arc-head { padding:26px 16px 30px; }
.ar-card.card-sz-lg .arch-title { font-size:30px; }
.ar-card.card-sz-sm .arc-head { padding:16px 16px 20px; }
.ar-card.card-sz-sm .arch-title { font-size:22px; }
.arch-info { flex:1; }
.arch-title { display:block; margin-top:10px; font-size:26px; font-weight:700; color:#333; line-height:1.35; text-align:center; }
/* 简洁会议卡（结束页默认态）：名称+时间地点+「查看详情」，整卡可点展开 */
.arc-summary { display:flex; flex-direction:column; align-items:center; gap:8px; padding:22px 16px 18px; text-align:center; cursor:pointer; }
.arc-summary .arch-title { margin-top:0; }
.arc-summary-static { gap:4px; padding:14px 16px 10px; cursor:default; }
.arc-summary-static:active { background:transparent; }
.arcs-meta { font-size:15px; color:#8A8F98; line-height:1.5; }
.arcs-caret { font-size:15px; color:var(--c-primary-dark); font-weight:600; margin-top:4px; }
.arc-summary:active { background:#FCF8F3; }
/* 展开区里的「查看会议纪要」按钮（从底部操作栏挪进详情） */
.ended-minutes-btn.in-card { margin:8px auto 16px; }
.arch-result { display:block; font-size: 28rpx; color:#666; margin-top:3px; font-weight:500; }


.flow-stats-card { background:#fff; border-radius:16px; padding:14px; margin-bottom:12px; box-shadow:0 1px 3px rgba(0,0,0,0.04); }
.flow-stats-panel { margin:8px 16px 12px; padding:12px 0 4px; border-top:1px solid #f0f0f0; }
.fsc-head { display:flex; align-items:flex-end; justify-content:space-between; gap:10px; margin-bottom:4px; }
.fsc-title { font-size: 28rpx; font-weight:700; color:#333; line-height:1.4; }
.fsc-sub { font-size: 28rpx; color:#666; line-height:1.4; text-align:right; }
.fss-row { display:flex; align-items:flex-start; gap:10px; padding:10px 0; }
.fss-row + .fss-row { border-top:1px dashed #f0f0f0; }
.fss-mark { width:30px; height:30px; border-radius:10px; display:flex; align-items:center; justify-content:center; flex-shrink:0; font-size: 28rpx; font-weight:700; }
.fss-mark.delivery { background:#EBF5FB; color:#2980B9; }
.fss-mark.attend { background:#EAF7EF; color:#27AE60; }
.fss-mark.vote { background:#FFF3DC; color:#E67E22; }
.fss-main { flex:1; min-width:0; }
.fss-line { display:flex; align-items:center; justify-content:space-between; gap:8px; }
.fss-name { font-size: 28rpx; color:#333; font-weight:600; line-height:1.4; }
.fss-num { font-size: 28rpx; color:#333; font-weight:700; line-height:1.4; flex-shrink:0; }
.fss-bar { height:6px; background:#f0f0f0; border-radius:4px; overflow:hidden; margin:5px 0 4px; }
.fss-fill { height:100%; border-radius:4px; transition:width .18s ease; }
.fss-fill.ok { background:linear-gradient(90deg,#27AE60,#2ECC71); }
.fss-fill.warn { background:linear-gradient(90deg,#E67E22,#F5B041); }
.fss-fill.bad { background:linear-gradient(90deg,#E74C3C,#EC7063); }
.fss-meta { display:block; font-size: 28rpx; color:#666; line-height:1.35; }
.fss-status { width:72px; flex-shrink:0; text-align:right; font-size: 28rpx; line-height:1.35; padding-top:2px; }
.fss-status.ok { color:#27AE60; }
.fss-status.warn { color:#E67E22; }
.fss-status.bad { color:#E74C3C; }

/* 会议议题卡（meeting-topics-card）样式见文件末尾的非 scoped <style> 块——
   该卡由 render 函数子组件 MeetingTopicsCard 渲染，其内部节点不带父组件 scope 属性，
   故须放在非 scoped 块中才能命中。 */

.arc-list { border-top:1px solid #f0f0f0; margin:0 16px; padding-top:6px; }
.arcl-row { display:flex; align-items:center; gap:10px; padding:10px 0; border-bottom:1px solid #f0f0f0; }
.arcl-row:last-child { border-bottom:none; }
.arcl-icon { font-size: 32rpx; width:34px; height:34px; border-radius:10px; display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.arcl-icon.notice { background:#FFF3DC; }
.arcl-icon.mat { background:#FFF8E8; }
.arcl-icon.ev { background:#E8F8F5; }
.arcl-icon.min { background:#FDEBD0; }
.arcl-icon.extra { background:#f5f5f5; }
.arcl-info { flex:1; min-width:0; }
.arcl-name { display:block; font-size: 28rpx; color:#333; }
.arcl-meta { display:block; font-size: 28rpx; color:#666; margin-top:2px; }
.arcl-arrow { font-size: 36rpx; color:#666; flex-shrink:0; }
.arcl-del { font-size: 32rpx; color:#666; padding:0 4px; flex-shrink:0; }
.arc-log { border-top:1px solid #f0f0f0; padding:10px 12px; }
.arclog-title { display:block; font-size: 28rpx; color:#666; margin-bottom:6px; }
.arclog-row { display:flex; flex-wrap:wrap; align-items:baseline; gap:6px; padding:4px 0; }
.arclog-action { font-size: 28rpx; color:#E74C3C; font-weight:600; }
.arclog-meta { font-size: 28rpx; color:#666; }
.arclog-reason { font-size: 28rpx; color:#666; width:100%; }

/* 公示（卡内主操作区） */
/* 底部固定操作栏 */
.arc-bottom-action { position:fixed; bottom:0; left:0; right:0; background:#f4f5f7; box-shadow:0 -6px 16px rgba(0,0,0,0.06); padding:12px 20px calc(14px + env(safe-area-inset-bottom,0px)); z-index:20; }
/* 结束页三个按钮统一成一组（方案A）：同宽同高同字号；公示=实心主操作，查看纪要/AI新闻=描边辅助 */
.ended-btn-row { display:flex; flex-direction:column; align-items:center; gap:8px; margin-bottom:8px; }
/* 查看会议纪要：描边橙（辅助） */
.ended-minutes-btn { display:flex; align-items:center; justify-content:center; width:73.1%; height:44px; margin:0 auto; border-radius:12px; background:#fff; color:var(--c-primary-dark); font-size:17px; font-weight:700; border:1.5px solid #E0A96A; cursor:pointer; }
.ended-minutes-btn:active { background:#FDF3E7; }
/* 未生成纪要时的「生成会议纪要」：实心橙主行动，比「查看」更醒目（会后在此补生成） */
.ended-minutes-btn.gen { background:var(--c-primary-dark); color:#fff; border-color:var(--c-primary-dark); box-shadow:0 3px 12px rgba(199,106,0,0.22); }
.ended-minutes-btn.gen:active { background:#A85800; }
/* AI生成新闻稿：描边红（辅助） */
.ended-news-btn { display:flex; align-items:center; justify-content:center; width:73.1%; height:44px; margin:0 auto; border-radius:12px; background:#fff; color:#C0141B; font-size:17px; font-weight:700; border:1.5px solid #E39B95; cursor:pointer; }
.ended-news-btn:active { background:#FDECEC; }
/* 返回首页：描边灰（中性辅助），与上面三个同尺寸同风格 */
.ended-home-btn { display:flex; align-items:center; justify-content:center; width:73.1%; height:44px; margin:0 auto; border-radius:12px; background:#fff; color:#555; font-size:17px; font-weight:700; border:1.5px solid #CCC; cursor:pointer; }
.ended-home-btn:active { background:#F2F2F2; }
/* 公示会议：描边政务蓝（官方公告动作专属色，白底与生成新闻稿同风格），与上面两个同尺寸 */
.arc-publish-main-btn { display:flex; align-items:center; justify-content:center; margin:0 auto 8px; width:73.1%; height:44px; border-radius:12px; background:#fff; border:1.5px solid #8FB3DC; color:#2464B4; font-size:18px; font-weight:700; cursor:pointer; }
.arc-publish-main-btn:active { background:#EAF2FB; }
.arc-invalid-note { padding:12px 0; color:#888; font-size:13px; text-align:center; border-bottom:1px solid #f0f0f0; margin-bottom:4px; }
/* 三个横排快捷入口 */
.arc-quick-links { display:flex; padding:16px 0 14px; border-top:1px solid #f0f0f0; margin-top:4px; }
.aql-item { flex:1; display:flex; align-items:center; justify-content:center; font-size:17px; color:var(--c-primary-dark); font-weight:700; cursor:pointer; padding:6px 4px; border-right:1px solid #e8e8e8; }
.aql-item:last-child { border-right:none; }
.aql-item:active { opacity:0.6; }
.arp-done { display:flex; align-items:center; gap:14px; font-size:15px; color:#27AE60; }
/* 左侧状态栏（已公示 + 日期），右侧功能小字并排 */
.arp-status { flex-shrink:0; display:flex; flex-direction:column; gap:2px; }
.arp-status-main { display:flex; align-items:center; gap:5px; font-size:17px; font-weight:700; color:#27AE60; }
.arp-status-sub { font-size:12px; color:#999; }
.arp-done .arp-actions { flex:1; width:auto; margin-top:0; }
.arp-check { width:20px; height:20px; border-radius:50%; background:#27AE60; color:#fff; display:flex; align-items:center; justify-content:center; font-size: 24rpx; }
.ar-skip { display:block; text-align:center; font-size:15px; color:var(--c-primary-dark); margin-top:10px; font-weight:700; }
.ar-skip.danger { color:#E74C3C; }

/* 实名表决开关（添加议题表单） */
.realname-row { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.realname-hint { display:block; font-size: 28rpx; color:#666; margin-top:2px; }
.rn-switch { width:44px; height:24px; border-radius:12px; background:#ddd; flex:0 0 auto; position:relative; transition:background .2s; }
.rn-switch.on { background:#27AE60; }
.rn-knob { width:20px; height:20px; border-radius:50%; background:#fff; position:absolute; top:2px; left:2px; transition:left .2s; }
.rn-switch.on .rn-knob { left:22px; }

/* 议题留痕 + 实名标记 */
.tp-meta { display:flex; align-items:center; flex-wrap:wrap; gap:6px; margin:4px 0 2px; }
.tp-meta-text { font-size: 28rpx; color:#666; }
.tp-tag { font-size: 28rpx; padding:1px 6px; border-radius:8px; }
.tp-tag.live { color:#E67E22; background:#FDF2E3; }
.tp-tag.realname { color:#2980B9; background:#EAF2F8; }

/* 语音输入 */
.vi-row { display:flex; align-items:center; gap:12rpx; }
.vi-btn { flex-shrink:0; width:76rpx; height:76rpx; border-radius:50%; background:#f0f2f5; border:2rpx solid #ddd; font-size:32rpx; display:flex; align-items:center; justify-content:center; cursor:pointer; line-height:1; padding:0; }
.vi-btn.on { background:#FFA800; border-color:#FFA800; animation:vi-pulse 1.2s ease-in-out infinite; }
@keyframes vi-pulse { 0%,100% { box-shadow:0 0 0 0 rgba(255,168,0,0.4); } 50% { box-shadow:0 0 0 14rpx rgba(255,168,0,0); } }

/* 进行中签到进度（主任视图） */
.signin-prog { background:#fff; border-radius:12px; padding:14px 16px; margin-bottom:12px; box-shadow:0 1px 3px rgba(0,0,0,0.04); }
.sp-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:10px; }
.sp-label { font-size:28rpx; color:#666; }
.sp-count { font-size:28rpx; font-weight:700; color:#27AE60; }
.sp-bar { height:10px; background:#f0f0f0; border-radius:5px; overflow:hidden; }
.sp-fill { height:100%; background:linear-gradient(90deg,#4FD0A6,#27AE60); border-radius:5px; transition:width 0.3s ease; min-width:4px; }
.voter-list { margin-top:8px; padding:8px 10px; background:#F7F9FA; border-radius:8px; }
.vl-title { display:block; font-size: 28rpx; color:#2980B9; margin-bottom:4px; }
.vl-item { display:block; font-size: 28rpx; color:#555; line-height:1.6; }
.arp-done.withdrawn { flex-direction:column; align-items:stretch; color:#E67E22; font-size:15px; gap:4px; }
.arp-actions { display:flex; width:100%; margin-top:6px; }
.arp-actions .ar-skip { flex:1; margin-top:0; border-right:1px solid #e8e8e8; padding:4px 0; }
.arp-actions .ar-skip:last-child { border-right:none; }
.arc-bottom-action .arp-actions { margin-top:16px; }
.ext-hint.withdrawn { color:#E67E22; }

/* Recorder */
.notice-block { background:#fff; border-radius:16px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,0.04); margin-bottom:12px; }
.nb-title { font-size: 28rpx; font-weight:600; display:block; margin-bottom:10px; }
.np-row { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
.np-label { font-size: 28rpx; color:#777; width:60px; }
.np-count2 { font-size: 28rpx; color:#666; width:40px; text-align:right; }

.flag-row { margin-bottom:12px; background:#fff; border-radius:16px; padding:16px; }
.flag-item { display:flex; align-items:center; justify-content:space-between; padding:8px 0; border-bottom:1px solid #f0f0f0; font-size: 28rpx; color:#555; }

.juwei-row { display:flex; align-items:center; justify-content:space-between; background:#EBF5FB; border-radius:10px; padding:10px 14px; margin-bottom:10px; font-size: 28rpx; }

.fu-chip { font-size: 28rpx; padding:3px 10px; border-radius:12px; border:1px solid #ddd; color:#666; }
.fu-chip.on { background:#E8F7EE; border-color:#27AE60; color:#27AE60; font-weight:600; }

/* External */
.ext-banner { padding:10px 14px; border-radius:12px; margin-bottom:10px; }
.ext-banner.owner { background:#EAFAF1; border-left:3px solid #27AE60; font-size: 28rpx; color:#27AE60; }
.ext-banner.property { background:#EBF5FB; border-left:3px solid #2980B9; font-size: 28rpx; color:#2980B9; }
.ext-sub { font-size: 28rpx; opacity:0.7; margin-top:2px; display:block; }
.ext-hint { text-align:center; color:#666; font-size: 28rpx; padding:16px 0; display:block; }

/* Wizard Stepper */
.live-entry { display:flex; align-items:center; gap:12px; background:linear-gradient(135deg,#FFCC44,#FFA800); border-radius:16px; padding:18px 16px; margin-bottom:12px; box-shadow:0 2px 8px rgba(255,168,0,0.25); }
.live-entry-main { flex:1; }
.live-entry-title { display:block; font-size: 32rpx; font-weight:700; color:#fff; line-height:1.4; }
.live-entry-sub { display:block; font-size: 28rpx; color:rgba(255,255,255,0.85); margin-top:4px; line-height:1.5; }
.live-entry-arrow { flex-shrink:0; font-size: 28rpx; font-weight:700; color:#fff; background:rgba(255,255,255,0.2); border-radius:16px; padding:6px 14px; }

/* 准备阶段步骤条 */
.prep-steps { display:flex; background:#fff; border-radius:12px; padding:14px 6px; margin-bottom:18px; border:0.5px solid #ECECEF; }
.prep-step { flex:1 1 0; min-width:0; text-align:center; position:relative; }
.prep-step::before { content:""; position:absolute; top:10px; left:-50%; right:50%; height:2px; background:#DEDEE3; z-index:0; }
.prep-step:first-child::before { display:none; }
.prep-step.done::before, .prep-step.current::before { background:#27AE60; }
.prep-dot { position:relative; z-index:1; display:inline-flex; align-items:center; justify-content:center; width:22px; height:22px; border-radius:50%; font-size: 28rpx; font-weight:600; background:#fff; color:#666; border:1px solid #DEDEE3; box-sizing:border-box; }
.prep-step.done .prep-dot { background:#27AE60; color:#fff; border-color:#27AE60; }
.prep-step.current .prep-dot { background:#FFA800; color:#fff; border-color:#FFA800; }
.prep-lbl { display:block; font-size:30rpx; margin-top:6px; color:#1F2024; font-weight:700; }
.prep-step.done .prep-lbl { color:#1F2024; }
.prep-step.current .prep-lbl { color:#D88900; font-weight:700; }

.action-row.secondary { display:flex; gap:10px; margin-top:4px; }

/* 准备阶段底部固定主操作 */
.prep-footer { position:fixed; bottom:0; left:0; right:0; z-index:100; box-sizing:border-box; background:#fff; border-top:1px solid #ECECEF; border-radius:18px 18px 0 0; box-shadow:0 -4px 20px rgba(0,0,0,0.10); padding:12px 16px calc(12px + env(safe-area-inset-bottom)); }
.prep-footer.after-send-footer { background:transparent; border-top:none; border-radius:0; box-shadow:none; padding:0 16px calc(12px + env(safe-area-inset-bottom)); pointer-events:none; }
/* 主按钮：与创建页 .btn-primary 一致（纯深橙药丸，高 88rpx / 圆角 44rpx / 字 32rpx·600） */
/* 按钮整体缩 10%（高度/字号），通知页内容多时不显拥挤 */
.pf-btn { display:flex; align-items:center; justify-content:center; height:80rpx; border:0; border-radius:40rpx; background: var(--c-primary-dark); color:#fff; font-size:29rpx; font-weight:600; line-height:1; box-sizing:border-box; padding:0 18rpx; }
.pf-btn:active { background: var(--c-primary-strong); }
.pf-btn-single { width:78%; margin:0 auto; }        /* 发送通知：单按钮，窄一点、居中 */
/* 已发送：再次通知 + 开始会议 并排，同色同等重要——稍矮、浅一点(亮橙)、拉开间距+两侧留缝，不拥挤 */
.pf-btn-row { display:flex; gap:36rpx; padding:0 20rpx; }
.pf-btn-row .pf-btn { flex:1; min-width:0; height:72rpx; font-size:27rpx; background: var(--c-primary-dark); }
.pf-btn-row .pf-btn:active { background: var(--c-primary-strong); }
.pf-after-send { display:flex; flex-direction:column; gap:14rpx; pointer-events:auto; }
.after-send-footer .pf-btn-row { padding:0 20rpx; }
.pf-btn-start-top { align-self:center; width:60%; height:78rpx; background:#0F766E; color:#fff; font-size:29rpx; font-weight:700; box-shadow:0 6rpx 18rpx rgba(15,118,110,0.28); animation:startPulse 2.2s ease-in-out infinite; }
.pf-btn-start-top:active { background:#0B5F59; animation:none; }
.pf-start-ico { font-size:24rpx; margin-right:10rpx; line-height:1; }
@keyframes startPulse { 0%, 100% { box-shadow:0 6rpx 16rpx rgba(15,118,110,0.22); } 50% { box-shadow:0 8rpx 24rpx rgba(15,118,110,0.44), 0 0 0 5rpx rgba(15,118,110,0.12); } }
@media (prefers-reduced-motion: reduce) { .pf-btn-start-top { animation:none; } }
.pf-hint { display:block; text-align:center; font-size: 24rpx; color:#666; margin-top:7px; }

/* ——— 通知页（精简版）：通知卡片 / 发送记录 / 取消会议 / 转发微信弹层 ——— */
/* 通知卡片整体收紧一档：有通知后页面内容多，卡片小一点不拥挤 */
.notice-card { background:#fff; border-radius:18px; overflow:hidden; box-shadow:0 6rpx 22rpx rgba(0,0,0,0.07); margin-top:24rpx; margin-bottom:16rpx; }
.nc-copy { padding:26rpx 28rpx; display:flex; flex-direction:column; gap:10rpx; font-size:28rpx; line-height:1.65; color:#1a1a1a; }
.nc-copy-title { font-size:31rpx; font-weight:700; line-height:1.5; word-break:break-all; }
.nc-copy-row { font-size:28rpx; color:#333; line-height:1.6; word-break:break-all; }
.nc-copy-note { margin-top:4rpx; font-size:27rpx; color:#555; line-height:1.6; }
.nc-copy-sign { margin-top:6rpx; text-align:right; font-size:28rpx; font-weight:700; color:#1a1a1a; line-height:1.65; }
.nc-banner { background:#C76A00; color:#fff; text-align:center; font-size:34rpx; font-weight:700; letter-spacing:6rpx; padding:24rpx 0; }
/* 强化语气：卡片以「新会议通知」橙色标签开头，替代原来偏弱的邀请口吻 */
.nc-kicker { text-align:center; padding:34rpx 40rpx 0; color:#C76A00; font-size:26rpx; font-weight:700; letter-spacing:8rpx; }
.nc-title { font-size:42rpx; font-weight:700; color:#1a1a1a; text-align:center; padding:10rpx 40rpx 10rpx; line-height:1.4; }
.nc-fields { padding:16rpx 44rpx 8rpx; display:flex; flex-direction:column; gap:20rpx; }
.nc-field { display:flex; align-items:flex-start; gap:20rpx; font-size:34rpx; line-height:1.5; }
.nc-label { flex-shrink:0; width:140rpx; color:#8A8F98; font-weight:600; }
.nc-value { flex:1; min-width:0; color:#1a1a1a; font-weight:600; word-break:break-all; }
.nc-muted { color:#9aa0a6; font-weight:400; }
/* 正文里的会议地点：蓝色可点超链接，点开高德地图导航 */
.loc-inline { color:#1A73E8; text-decoration:underline; cursor:pointer; }
.loc-inline:active { opacity:0.6; }
.nc-info { padding:10rpx 44rpx 6rpx; }
.nc-line { display:flex; font-size:30rpx; line-height:1.6; padding:7rpx 0; }
.nc-k { flex-shrink:0; width:92rpx; color:#C76A00; font-weight:600; }
.nc-v { flex:1; color:#333; word-break:break-all; }
.nc-body { padding:18rpx 44rpx 6rpx; font-size:30rpx; color:#333; line-height:1.7; }
.nc-sign { padding:6rpx 44rpx 34rpx; text-align:right; font-size:34rpx; font-weight:700; color:#1a1a1a; }
/* 通知人员：前置到通知页，默认收起，避免长通知正文后再弹二次确认 */
.recipient-card { background:#fff; border:2rpx solid #EEF0F3; border-radius:18px; box-shadow:0 4rpx 16rpx rgba(0,0,0,0.05); margin:0 0 16rpx; overflow:hidden; }
.recipient-card-head { display:flex; align-items:center; justify-content:space-between; gap:16rpx; padding:18rpx 24rpx; }
.recipient-card-head:active { background:#FAFAFA; }
.recipient-card-title { display:block; font-size:28rpx; color:#1f2329; font-weight:700; line-height:1.35; }
.recipient-card-sub { display:block; margin-top:4rpx; font-size:21rpx; color:#8A9099; line-height:1.35; }
.recipient-card-right { flex-shrink:0; display:flex; align-items:center; gap:10rpx; }
.recipient-summary { font-size:24rpx; color:#4A5560; white-space:nowrap; }
.recipient-card-arrow { color:#A4A9B0; font-size:32rpx; line-height:1; transform:rotate(90deg); transition:transform .18s ease; }
.recipient-card-arrow.open { transform:rotate(-90deg); }
.page-rcp-list { margin:0; max-height:420rpx; overflow-y:auto; border-top:1px solid #F0F0F2; }
.page-rcp-item { padding:20rpx 28rpx; }
/* 通知记录：标题 + 记录 */
.sr-section { margin:8rpx 6rpx 0; }
.sr-heading-row { display:flex; align-items:center; justify-content:space-between; gap:12rpx; padding:2rpx 2rpx 12rpx; }
.sr-heading { font-size:32rpx; font-weight:700; color:#1f2329; }
/* 清空通知记录：测试用弱化小按钮（灰描边胶囊） */
.sr-clear-btn { flex-shrink:0; font-size:25rpx; color:#8A9099; padding:5rpx 18rpx; border:2rpx solid #E3E5E9; border-radius:999rpx; line-height:1.3; }
.sr-clear-btn:active { background:#F2F3F5; color:#6A7480; }
/* 通知记录：去底色，纯绿色文字、加大一号并加粗 */
.send-record { display:flex; align-items:center; gap:12rpx; margin:0; padding:8rpx 2rpx; }
.send-record + .send-record { margin-top:20rpx; }   /* 多条记录之间间隔加大约 10px */
.sr-ic { color:#2E9E5B; font-weight:700; font-size:32rpx; }
.sr-text { font-size:32rpx; color:#2E7D46; font-weight:700; }
.prep-cancel { text-align:center; margin:-4rpx 0 16rpx; }
.prep-cancel span { font-size:26rpx; color:#bbb; padding:10rpx 18rpx; }
.forward-sheet { position:relative; width:100%; max-width:480px; margin:0 auto; background:#fff; border-radius:24rpx 24rpx 0 0; padding:30rpx 28rpx calc(36rpx + env(safe-area-inset-bottom)); max-height:88vh; overflow-y:auto; box-sizing:border-box; }
.fw-title { font-size:34rpx; font-weight:700; color:#1a1a1a; text-align:center; }
.fw-hint { font-size:26rpx; color:#888; text-align:center; margin:10rpx 0 20rpx; line-height:1.5; }
/* 转发预览：与通知页一致的格式化通知（首行缩进/地点蓝链/落款靠右）+ 附带链接行 */
.fw-preview { box-sizing:border-box; margin:0 0 24rpx; border:2rpx solid #eee; border-radius:16rpx; padding:24rpx 26rpx; background:#FAFAFA; }
.fw-para { font-size:30rpx; color:#1a1a1a; line-height:1.8; text-align:left; text-indent:2em; }
.fw-sign { text-align:right; font-size:30rpx; font-weight:700; color:#1a1a1a; margin-top:6rpx; }
.fw-linkrows { margin-top:14rpx; padding-top:10rpx; border-top:1px dashed #e2e2e2; }
.fw-linkrow { display:flex; align-items:center; gap:12rpx; padding:12rpx 2rpx; }
.fw-lr-ico { font-size:30rpx; flex-shrink:0; }
.fw-lr-txt { flex:1; min-width:0; font-size:28rpx; color:#1A73E8; text-decoration:underline; word-break:break-all; }
.fw-lr-go { flex-shrink:0; color:#1A73E8; font-weight:700; }
.fw-actions { display:flex; gap:18rpx; }
/* 复制通知 / 打开微信：统一浅橙底 + 深橙字 + 淡橙描边（两个按钮同款） */
.fw-btn { flex:1; height:84rpx; border:2rpx solid #F0D9B8; border-radius:42rpx; font-size:30rpx; font-weight:600; background:var(--c-primary-soft, #FFF3E0); color:var(--c-primary-dark, #A85800); }
.fw-btn:active { background:#FDE8CC; }
.fw-close { display:block; text-align:center; margin-top:16rpx; font-size:28rpx; color:#999; padding:8rpx; }

/* 准备阶段会议头部 */
.prep-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; background:#fff; border:0.5px solid #ECECEF; border-radius:12px; padding:16px 16px; margin-top:14px; margin-bottom:18px; }
.ph-main { flex:1; min-width:0; }
.ph-title { display:block; font-size: 34rpx; font-weight:700; color:#1F2024; line-height:1.35; word-break:break-all; }
.ph-meta { display:flex; flex-wrap:wrap; gap:4px 12px; margin-top:7px; }
.ph-meta-item { font-size:12.5px; color:#6B6E76; line-height:1.35; }
.mi-k { color:#666; margin-right:5px; }
.ph-actions { display:flex; align-items:center; gap:8px; flex-shrink:0; padding-top:1px; }
.ph-action { font-size: 28rpx; color:#8A5A0B; background:#FEF4E2; border:0.5px solid #F4D08A; border-radius:14px; padding:4px 9px; line-height:1.35; }
.ph-action.danger { color:#C0392B; background:#FFF5F3; border-color:#F3C7BF; padding:6px 14px; font-weight:600; }

/* 准备阶段通用卡片 */
.prep-card { background:#fff; border-radius:12px; padding:16px 16px; margin-bottom:18px; border:0.5px solid #ECECEF; }
.pc-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:10px; }
.pc-title { font-size: 34rpx; font-weight:700; color:#1F2024; }
.pc-desc { display:block; font-size: 28rpx; color:#1F2024; margin-top:2px; line-height:1.4; }
.pc-sub { font-size:12.5px; color:#666; }
.pc-sub.link { color:#D88900; }
.pc-head-actions { display:flex; align-items:center; gap:8px; flex-shrink:0; }
.pc-link { color:#D88900; font-size:12.5px; line-height:1.35; }
.pc-title-wrap { display:flex; align-items:center; gap:10px; min-width:0; }
.pc-edit-btn { font-size:26rpx; color:#fff; background:var(--c-primary-dark); border:0; border-radius:16px; padding:6px 18px; line-height:1.3; flex-shrink:0; font-weight:600; box-shadow:0 4rpx 12rpx rgba(255,168,0,0.3); }
.upload-btn { font-size:28rpx; color:#fff; background:var(--c-primary-dark); border-radius:18px; padding:8px 22px; font-weight:600; line-height:1.3; flex-shrink:0; box-shadow:0 4rpx 12rpx rgba(255,168,0,0.3); }
.pc-badge { font-size: 26rpx; font-weight:700; padding:2px 9px; border-radius:20px; }
.pc-badge.warn { color:#8A5A0B; background:#FEF4E2; border:0.5px solid #F4D08A; }
.pc-badge.ok { color:#1D9E75; background:#E1F5EE; }
.nt-titlewrap { display:flex; align-items:center; gap:10px; min-width:0; }
.notice-status { font-size:24rpx; font-weight:600; display:inline-flex; align-items:center; gap:6rpx; flex-shrink:0; }
.notice-status::before { content:""; width:12rpx; height:12rpx; border-radius:50%; background:currentColor; }
.notice-status.warn { color:#C77800; }
.notice-status.ok { color:#1D9E75; }
.pc-draft { display:block; font-size: 28rpx; color:#6B6E76; line-height:1.7; white-space:pre-wrap; word-break:break-all; background:#F6F6F8; border-radius:8px; padding:12px 14px; }
.notice-extra { display:flex; align-items:center; justify-content:flex-start; gap:22rpx; margin-top:18rpx; padding-top:18rpx; border-top:2rpx solid #f0f0f0; }
.ne-text { flex:0 1 auto; min-width:0; }
.ne-title { display:block; font-size:28rpx; color:#1f2329; font-weight:600; line-height:1.4; word-break:break-all; }
.ne-desc { display:block; font-size:26rpx; color:#888; margin-top:6rpx; line-height:1.45; word-break:break-all; }
.pc-ghost { margin-top:10px; padding:9px; border:0.5px solid #DEDEE3; border-radius:8px; color:#1F2024; font-size:13.5px; text-align:center; }
.pc-chev { color:#666; }

/* 材料空态 / 文件行 */
.pc-upload-empty { border:1px dashed #DEDEE3; border-radius:8px; padding:14px; text-align:center; }
.material-desc { margin-top:10px; margin-bottom:2px; }
.material-empty-inline { padding:2px 0 2px; color:#1F2024; font-size:28rpx; line-height:1.45; }
/* 参会情况统计卡 */
.as-row { display:flex; align-items:baseline; justify-content:space-between; gap:12px; margin-bottom:12px; }
.as-label { font-size:28rpx; color:#1F2024; font-weight:600; }
.as-num { font-size:28rpx; color:#666; }
.as-num b { font-size:40rpx; color:#FFA800; font-weight:700; margin-right:2rpx; }
.as-bar { height:16rpx; background:#EEF0F2; border-radius:8rpx; overflow:hidden; }
.as-fill { height:100%; background:linear-gradient(90deg,#FFC23D,#FFA800); border-radius:8rpx; transition:width 0.3s ease; min-width:8rpx; }
.as-declined { display:block; font-size:26rpx; color:#E67E22; margin-top:10px; }
.ue-ic { display:block; font-size: 40rpx; color:#666; }
.ue-txt { display:block; font-size:12.5px; color:#6B6E76; margin:4px 0 10px; }
.ue-btn { display:inline-block; padding:8px 20px; background:#fff; border:0.5px solid #DEDEE3; border-radius:8px; color:#1F2024; font-size:13.5px; }
.pc-file { display:flex; align-items:center; gap:10px; padding:9px 0; border-top:0.5px solid #ECECEF; font-size:13.5px; }
.pc-file:first-child { border-top:none; }
.pf-ic { color:#B96F12; font-size: 36rpx; }
.pf-name { flex:1; color:#1F2024; font-size:28rpx; }
.pf-size { font-size: 28rpx; color:#1F2024; }
.pf-del { color:#666; font-size: 36rpx; padding:0 4px; }
/* 材料 OCR 文字识别状态徽标（适老化：字大、词清楚、颜色直观） */
.ocr-badge { flex:none; font-size:24rpx; font-weight:600; line-height:1.2; padding:5rpx 14rpx; border-radius:16rpx; white-space:nowrap; }
.ocr-proc { color:#2563EB; background:#EAF1FF; }
.ocr-done { color:#1D9E75; background:#E1F5EE; }
.ocr-fail { color:#C0392B; background:#FDECEC; }
/* 上传文件缩略图：适老化——尺寸够大、可点 */
.file-thumb { width:64px; height:64px; object-fit:cover; border-radius:8px; border:1px solid #eee; flex:none; cursor:pointer; }

/* 送达进度（双列） */
.pc-progress { display:flex; gap:14px; }
.pp-col { flex:1; }
.pp-l { display:flex; justify-content:space-between; font-size: 28rpx; color:#6B6E76; margin-bottom:5px; }
.pp-bar { height:6px; border-radius:4px; background:#ECECEF; overflow:hidden; }
.pp-fill { display:block; height:100%; background:linear-gradient(90deg,#FFCC44,#FFA800); border-radius:4px; transition:width .4s; }
.pp-fill.read { background:linear-gradient(90deg,#4FD0A6,#1D9E75); }
.pc-deadline { display:block; font-size:11.5px; color:#666; margin-top:10px; }
.juwei-prep-row {
  display:flex; align-items:center; justify-content:space-between; gap:12px;
  padding:10px 12px; border-radius:8px; background:#EBF5FB;
  color:#355C7D; font-size: 28rpx; line-height:1.45;
}

/* 送达名单（折叠） */
.pc-members { margin-top:12px; border-top:0.5px solid #ECECEF; padding-top:8px; }
.pcm-row { display:flex; align-items:center; justify-content:space-between; padding:7px 0; }
.pcm-main { display:flex; flex-direction:column; }
.pcm-name { font-size: 28rpx; color:#1F2024; }
.pcm-role { font-size: 28rpx; color:#666; }
.pcm-tags { display:flex; gap:6px; }
.pcm-tag { font-size: 28rpx; padding:3px 9px; border-radius:14px; background:#F6F6F8; color:#666; border:0.5px solid #ECECEF; }
.pcm-tag.ok { color:#1D9E75; background:#E1F5EE; border-color:#E1F5EE; }

.wizard-stepper { display:flex; align-items:center; justify-content:center; padding:16px; background:#fff; border-radius:16px; margin-bottom:12px; }
.ws-step { display:flex; flex-direction:column; align-items:center; gap:4px; }
.ws-num { width:32px; height:32px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size: 28rpx; font-weight:700; }
.ws-label { font-size: 28rpx; color:#666; }
.ws-step.active .ws-num { background:#FFA800; color:#fff; }
.ws-step.active .ws-label { color:#FFA800; font-weight:600; }
.ws-step.done .ws-num { background:#27AE60; color:#fff; }
.ws-step.done .ws-label { color:#27AE60; }
.ws-step.locked .ws-num { background:#eee; color:#666; }
.ws-step.locked .ws-label { color:#666; }
.ws-line { flex:1; height:2px; background:#eee; margin:0 4px; margin-bottom:16px; }
.ws-line.done { background:#27AE60; }

/* Wizard Card */
.wizard-card { background:#fff; border-radius:16px; padding:18px; margin-bottom:12px; box-shadow:0 1px 3px rgba(0,0,0,0.04); }
.guide-step { display:block; font-size: 28rpx; color:#D88900; font-weight:700; margin-bottom:8px; }
.wc-title { font-size: 40rpx; font-weight:700; color:#222; display:block; margin-bottom:8px; line-height:1.35; }
.wc-desc { font-size: 30rpx; color:#666; display:block; margin-bottom:16px; line-height:1.6; }
.wc-info { background:#fafafa; border-radius:10px; padding:10px 14px; margin-bottom:14px; display:flex; flex-direction:column; gap:6px; }
.wc-info span { font-size: 28rpx; color:#555; line-height:1.55; }
.wc-check { color:#27AE60 !important; font-weight:600; }
.wc-empty { text-align:center; color:#666; font-size: 30rpx; padding:22px 0; display:block; }
.wc-confirm { width:100%; min-height:54px; padding:14px; background:var(--c-primary-dark); color:#fff; font-size: 34rpx; font-weight:700; border:none; border-radius:14px; margin-top:10px; }
.wc-done-banner { text-align:center; background:#EAF7EF; padding:14px; border-radius:12px; font-size: 30rpx; color:#27AE60; font-weight:700; line-height:1.5; }

/* 会议录音存档卡 */
.rec-archive-card { background:#fff; border-radius:16px; padding:14px 16px; margin-bottom:12px; box-shadow:0 1px 3px rgba(0,0,0,0.04); }
.rac-head { display:flex; flex-direction:column; gap:3px; margin-bottom:10px; }
.rac-title { font-size: 30rpx; font-weight:700; color:#1F2024; }
.rac-sub { font-size: 28rpx; color:#666; }
.rac-actions { display:flex; gap:10px; }

/* Completion */
.cc-time { font-size: 28rpx; color:#666; margin-top:4px; }

.add-link { font-size: 28rpx; color:#FFA800; font-weight:600; margin-left:8px; }
.tp-del { font-size: 32rpx; color:#666; margin-left:auto; padding:0 4px; }
.ev-item { display:flex; align-items:center; justify-content:space-between; padding:8px 10px; background:#fafafa; border-radius:8px; margin-bottom:4px; font-size: 28rpx; }

/* Actions */
.action-row { display:flex; gap:8px; margin-top:10px; }

/* ===== 多选一表决 ===== */
.tp-type.multi { background:#F5EEF8; color:#8E44AD; }
.option-list { display:flex; flex-direction:column; gap:8px; margin:8px 0; }
.opt-row {
  display:flex; align-items:center; gap:10px;
  padding:10px 12px; background:#fafafa; border-radius:10px;
  border:1px solid #f0f0f0;
}
.opt-radio {
  width:20px; height:20px; border-radius:50%;
  border:2px solid #ddd; display:flex; align-items:center; justify-content:center;
  flex-shrink:0;
}
.opt-radio.on { border-color:#FFA800; }
.opt-dot { width:10px; height:10px; border-radius:50%; background:#FFA800; }
.opt-label { font-size: 28rpx; color:#333; line-height:1.4; }
.option-bars { display:flex; flex-wrap:wrap; gap:4px 10px; margin-top:6px; }
.ob-line { font-size: 28rpx; color:#666; }

/* ===== 添加议题弹窗 ===== */
.form-sheet {
  width:100%; max-height:92vh; overflow:auto;
  background:#fff; border-radius:18px 18px 0 0;
  padding:16px 16px 0; box-sizing:border-box;
}
.sheet-head { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:14px; }
.sheet-title { font-size: 34rpx; font-weight:700; color:#333; }
.sheet-close { width:32px; height:32px; line-height:30px; text-align:center; border-radius:16px; font-size: 44rpx; color:#666; background:#f5f5f5; flex-shrink:0; }
.type-row { display:flex; flex-wrap:wrap; gap:8px; }
.type-chip {
  min-height:34px; line-height:18px; box-sizing:border-box;
  display:flex; align-items:center; justify-content:center;
  font-size: 28rpx; color:#666; background:#f5f5f5;
  padding:7px 12px; border-radius:16px;
}
.type-chip.on { color:#fff; background:#FFA800; font-weight:600; }
.form-group { margin-bottom:12px; min-width:0; }
.form-label { display:block; font-size: 28rpx; color:#777; margin-bottom:7px; line-height:1.45; }
.form-hint { display:block; font-size: 28rpx; color:#666; margin:-3px 0 7px; line-height:1.5; }
.form-input {
  width:100%; box-sizing:border-box; background:#f7f7f7;
  border-radius:12px; font-size: 28rpx; color:#333;
  height:44px; min-height:44px; line-height:normal; padding:0 12px;
  border:0;
}
.form-input.large { height:48px; min-height:48px; line-height:normal; font-size: 30rpx; font-weight:600; }
.form-input::placeholder { color:#666; font-size: 28rpx; line-height:1.5; }
.form-textarea::placeholder { color:#666; font-size: 28rpx; line-height:1.5; }
.form-textarea {
  width:100%; box-sizing:border-box; background:#f7f7f7;
  border-radius:12px; font-size: 28rpx; color:#333;
  min-height:128px; height:128px; line-height:1.5; padding:11px 12px;
  border:0;
}
.picker-field {
  width:100%; box-sizing:border-box; background:#f7f7f7;
  border-radius:12px; font-size: 28rpx; color:#333;
  height:44px; min-height:44px; line-height:normal; padding:0 12px;
  border:0;
}
/* 编辑通知：会议要素选择器（与「新建会议」一致） */
.ep-field { display:flex; align-items:center; justify-content:space-between; cursor:pointer; }
.ep-text { font-size:28rpx; color:#1f2329; }
.ep-arrow { color:#999; font-size:26rpx; }
.ep-loc-select { appearance:none; -webkit-appearance:none; padding-right:60rpx; background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='22' height='22' viewBox='0 0 20 20'%3E%3Cpath fill='%23999' d='M5 7l5 5 5-5z'/%3E%3C/svg%3E"); background-repeat:no-repeat; background-position:right 20rpx center; }
.ep-loc-other { margin-top:16rpx; }
.ep-pop-mask { position:fixed; inset:0; z-index:120; background:rgba(0,0,0,0.42); display:flex; align-items:center; justify-content:center; padding:48rpx; box-sizing:border-box; }
.ep-pop { width:100%; max-width:660rpx; background:#fff; border-radius:26rpx; padding:28rpx 26rpx 24rpx; box-sizing:border-box; }
.ep-head { text-align:center; font-size:34rpx; font-weight:700; color:#1f2329; margin-bottom:20rpx; }
.ep-cols { display:flex; gap:16rpx; }
.ep-col { flex:1; min-width:0; display:flex; flex-direction:column; }
.ep-col-label { text-align:center; font-size:28rpx; color:#666; margin-bottom:10rpx; }
.ep-col-scroll { height:460rpx; overflow-y:auto; background:#f7f8fa; border-radius:16rpx; padding:8rpx; box-sizing:border-box; -webkit-overflow-scrolling:touch; }
.ep-item { display:flex; align-items:center; justify-content:center; height:76rpx; font-size:34rpx; color:#333; border-radius:12rpx; margin:4rpx 0; }
.ep-item.on { color:#fff; background:#FFA800; font-weight:700; }
.ep-actions { display:flex; gap:18rpx; margin-top:24rpx; }
.ep-actions .btn { flex:1; height:44px; line-height:44px; border-radius:22px; font-size:28rpx; font-weight:600; border:0; display:flex; align-items:center; justify-content:center; }
.sheet-actions { display:flex; gap:10px; justify-content:space-between; padding:12px 0 calc(18px + env(safe-area-inset-bottom)); }
.sheet-actions .btn {
  flex:1; min-width:0; height:44px; line-height:44px;
  border-radius:22px; font-size: 28rpx; font-weight:600;
  padding:0 16px; margin:0; border:0;
  display:flex; align-items:center; justify-content:center; box-sizing:border-box;
}
.btn-ghost { color:#777; background:#f5f5f5; }
.btn-primary { color:#fff; background:var(--c-primary-dark); }

.modal-mask {
  position:fixed; inset:0; z-index:100;
  background:rgba(0,0,0,0.36);
  display:flex; align-items:flex-end;
}

/* 通知页顶栏：左箭头(回发起业委会重新编辑) + 右上首页按钮。插槽内容走父级作用域，故此处自定义样式 */
/* 三者同一行居中：箭头字号收敛、line-height:1 消除字形下沉，与标题/首页按钮同高 */
.nav-back { width:96rpx; height:64rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:52rpx; font-weight:700; line-height:1; }
.nav-back:active { opacity:0.7; }
.nav-home { display:inline-flex; align-items:center; height:64rpx; margin-right:20rpx; padding:0 24rpx; border:2rpx solid rgba(255,255,255,0.6); border-radius:34rpx; background:rgba(255,255,255,0.12); color:#fff; font-size:30rpx; font-weight:600; line-height:1; }
.nav-home:active { background:rgba(255,255,255,0.28); }

/* 发送通知——接收对象选择弹窗 */
.rcp-sheet { position:relative; width:100%; max-width:480px; margin:0 auto; background:#fff; border-radius:24rpx 24rpx 0 0; padding:30rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); max-height:82vh; display:flex; flex-direction:column; box-sizing:border-box; }
.rcp-title { font-size:34rpx; font-weight:700; color:#1a1a1a; text-align:center; }
.rcp-hint { font-size:26rpx; color:#7a5b1e; background:#FFF6E5; border-radius:14rpx; text-align:center; line-height:1.7; margin-top:14rpx; padding:16rpx 18rpx; }
.rcp-hint b { color:#C77800; font-weight:700; }
.rcp-allrow { display:flex; align-items:center; gap:16rpx; margin-top:20rpx; padding:20rpx 22rpx; background:var(--c-primary-soft, #fdf0e0); border-radius:18rpx; }
.rcp-all-label { font-size:32rpx; font-weight:700; color:#1f2329; }
.rcp-count { margin-left:auto; font-size:26rpx; color:var(--c-primary-strong, #c96a12); font-weight:600; }
.rcp-list { margin-top:8rpx; flex:1; min-height:100rpx; }
.rcp-item { display:flex; align-items:center; gap:14rpx; padding:15rpx 22rpx; border-bottom:1px solid #f0f0f2; }
.rcp-item:active { background:#fafafa; }
/* 全选行：挪到展开列表顶部，浅橙底 + 橙字，与成员行区分 */
.rcp-all-row { background:#FFFCF7; }
.rcp-all-row .rcp-name { color:#A85800; }
.rcp-all-count { margin-left:auto; flex-shrink:0; font-size:21rpx; color:#8A9099; white-space:nowrap; }
.rcp-person { display:flex; flex-direction:column; gap:2rpx; }
.rcp-name { font-size:28rpx; color:#1f2329; font-weight:600; }
.rcp-role { font-size:20rpx; color:#9aa0a6; }
.rcp-empty { text-align:center; color:#9aa0a6; font-size:24rpx; padding:32rpx 0; }
.rcp-check { flex-shrink:0; width:38rpx; height:38rpx; border-radius:50%; border:3rpx solid #cfd4da; display:flex; align-items:center; justify-content:center; color:#fff; font-size:24rpx; font-weight:700; box-sizing:border-box; }
.rcp-check.mini { width:28rpx; height:28rpx; border-width:2rpx; font-size:18rpx; }
.rcp-check.on { background:var(--c-primary); border-color:var(--c-primary); }
.rcp-actions { display:flex; gap:20rpx; margin-top:20rpx; }
.rcp-btn { flex:1; height:92rpx; border:none; border-radius:20rpx; font-size:34rpx; font-weight:700; }
.rcp-btn.ghost { flex:0 0 34%; background:#f0f1f3; color:#555; }
.rcp-btn.primary { background:var(--c-primary); color:#fff; }
.rcp-btn.primary:disabled { opacity:0.5; }
.rcp-btn:active { opacity:0.88; }
.proxy-sheet { max-height:88vh; padding-bottom:calc(18px + env(safe-area-inset-bottom)); }
.proxy-tabs { display:flex; gap:8px; margin-bottom:12px; }
.proxy-tab {
  flex:1; text-align:center; padding:10px 0; border-radius:18px;
  background:#f5f5f5; color:#777; font-size: 28rpx; font-weight:600;
}
.proxy-tab.on { background:#FFA800; color:#fff; }
.proxy-section { margin-bottom:14px; }
.proxy-topic-list { display:flex; flex-wrap:wrap; gap:8px; }
.proxy-topic {
  max-width:100%; box-sizing:border-box; padding:8px 10px; border-radius:12px;
  background:#f7f7f7; color:#666; font-size: 28rpx; line-height:1.35;
}
.proxy-topic.on { background:#FFF3DC; color:#B36B00; font-weight:700; }
.proxy-choice-row { display:flex; gap:8px; margin-top:10px; }
.proxy-choice {
  flex:1; text-align:center; padding:9px 0; border-radius:16px;
  background:#f5f5f5; color:#777; font-size: 28rpx; font-weight:600;
}
.proxy-choice.for.on { background:#E8F7EE; color:#27AE60; }
.proxy-choice.ag.on { background:#FDECEA; color:#E74C3C; }
.proxy-choice.ab.on { background:#EBF5FB; color:#2980B9; }
.proxy-option-list { display:flex; flex-direction:column; gap:8px; margin-top:10px; }
.proxy-option {
  padding:10px 12px; border-radius:12px; background:#f7f7f7;
  color:#555; font-size: 28rpx; line-height:1.4; border:1px solid transparent;
}
.proxy-option.on { background:#FFF3DC; border-color:#FFA800; color:#B36B00; font-weight:700; }
.proxy-target-list { max-height:260px; margin-top:10px; }
.proxy-target {
  display:flex; align-items:center; gap:10px; padding:10px 0;
  border-bottom:1px solid #f2f2f2;
}
.proxy-target:last-child { border-bottom:none; }
.proxy-target.disabled { opacity:0.45; }
.proxy-target.on .proxy-check { background:#27AE60; border-color:#27AE60; color:#fff; }
.proxy-check {
  width:22px; height:22px; border-radius:50%; border:1px solid #ddd;
  display:flex; align-items:center; justify-content:center;
  color:#fff; font-size: 28rpx; font-weight:700; flex-shrink:0;
}
.proxy-person { flex:1; min-width:0; }
.proxy-name { display:block; font-size: 28rpx; color:#333; font-weight:600; }
.proxy-meta { display:block; font-size: 28rpx; color:#666; margin-top:2px; }
.proxy-state { font-size: 28rpx; color:#666; flex-shrink:0; }
.proxy-empty { text-align:center; color:#666; font-size: 28rpx; padding:18px 0; }
.proof-picker {
  display:flex; align-items:center; justify-content:space-between; gap:12px;
  background:#f7f7f7; border-radius:12px; padding:12px;
}
.proof-main { flex:1; min-width:0; font-size: 28rpx; color:#555; word-break:break-all; }
.proof-action { font-size: 28rpx; color:#FFA800; font-weight:700; flex-shrink:0; }
.proxy-submit-row {
  display:flex; align-items:center; justify-content:space-between; gap:12px;
  padding-top:4px;
}
.proxy-count { font-size: 28rpx; color:#666; flex-shrink:0; }
.proxy-submit {
  flex:1; height:44px; line-height:44px; border-radius:22px;
  margin:0; padding:0; font-size: 28rpx; font-weight:700;
}

/* —— 适老化补充：委员纪要按钮 + 归档页折叠头 —— */
.member-doc-actions { display:flex; gap:16rpx; margin-bottom:12px; }
.doc-btn { flex:1; height:72rpx; line-height:72rpx; border-radius:36rpx; background:var(--c-primary-dark); color:#fff; font-size:26rpx; font-weight:600; margin:0; border:0; }
.doc-btn.ghost { background:#fff; color:#C77800; border:2rpx solid #FFA800; }
.fsc-toggle { font-size: 28rpx; color:#C77800; font-weight:600; flex-shrink:0; white-space:nowrap; }
.arclog-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:6px; }
.arclog-toggle { font-size: 28rpx; color:#C77800; font-weight:600; }

/* —— 委员确认参会 / 无法参会 —— */
.attend-actions { display:flex; flex-direction:column; gap:20rpx; margin-top:24rpx; }
.attend-btn { border:none; margin:0; }
.attend-btn.primary { width:100%; min-height:104rpx; line-height:104rpx; border-radius:18rpx; font-size:34rpx; font-weight:700; background:var(--c-primary-dark); color:#fff; box-shadow:0 8rpx 22rpx rgba(255,168,0,0.4); }
.attend-btn.ghost { align-self:center; width:auto; min-height:84rpx; line-height:84rpx; padding:0 72rpx; border-radius:42rpx; font-size:32rpx; font-weight:600; background:#eef0f3; color:#5b6673; }
.mdc-big.declined { color:#E67E22; }
.pp-declined { display:block; font-size: 28rpx; color:#E67E22; margin-top:10rpx; }
.my-attend-tip { display:block; font-size:30rpx; color:#4a5560; margin-bottom:16rpx; line-height:1.5; }
.my-attend-tip.declined { color:#E67E22; }
.my-attend-tip.ok { color:#27AE60; font-weight:600; margin-bottom:0; }
.my-attend-cancel { display:block; text-align:center; margin-top:24rpx; font-size:30rpx; color:#666; padding:8rpx; }

/* 语音输入确认弹窗 */
.voice-modal-mask { position: fixed; inset: 0; z-index: 200; background: rgba(0,0,0,0.5); display: flex; align-items: flex-end; }
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
  padding: 24rpx 20rpx; min-height: 120rpx;
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
.vm-placeholder { color: #aaa; }
.vm-actions { display: flex; gap: 20rpx; }
.vm-actions .btn { flex: 1; height: 88rpx; font-size: 34rpx; border-radius: 18rpx; }
.vm-actions .btn:disabled { opacity: 0.4; }
</style>

<!-- 会议议题卡：由 render 函数子组件 MeetingTopicsCard 渲染，其节点不带父 scope 属性，
     故这些规则放在非 scoped 块（类名与原 .wxss 完全一致，1:1 还原）。 -->
<style>
.meeting-topics-card {
  background:#fff;
  border-radius:16px;
  padding:14px;
  margin-bottom:12px;
  box-shadow:0 1px 3px rgba(0,0,0,0.04);
}
.ar-card .meeting-topics-card {
  margin:8px 16px 12px;
  padding:12px 0 4px;
  border-radius:0;
  box-shadow:none;
  border-top:1px solid #f0f0f0;
}
.mtc-head {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:10px;
  margin-bottom:12px;
}
.mtc-title-main {
  display:block;
  font-size:20px;
  font-weight:700;
  color:#333;
  line-height:1.4;
}
.mtc-collapse {
  flex-shrink:0;
  margin:0;
  padding:3px 2px;
  border:0;
  background:transparent;
  color:var(--c-primary-dark, #A85800);
  font-size:15px;
  font-weight:600;
  line-height:1.4;
  cursor:pointer;
}
.mtc-collapse:active { opacity:.65; }
.mtc-sub {
  display:block;
  font-size: 28rpx;
  color:#666;
  line-height:1.4;
  margin-top:1px;
}
.mtc-count {
  flex-shrink:0;
  font-size:14px;
  color:#888;
  background:#f5f5f5;
  border-radius:8px;
  padding:2px 7px;
  line-height:1.35;
}
.mtc-topic {
  display:flex;
  gap:10px;
  padding:16px 0;
  border-top:1px dashed #f0f0f0;
}
.mtc-head + .mtc-topic,
.mtc-topic:first-of-type { border-top:none; }
.mtc-no {
  width:30px;
  height:30px;
  border-radius:8px;
  background:#F6F6F8;
  color:#666;
  font-size:19px;
  font-weight:700;
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0;
  margin-top:1px;
}
.mtc-body { flex:1; min-width:0; }
.mtc-title-row {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:8px;
}
.mtc-topic-title {
  flex:1;
  min-width:0;
  font-size:20px;
  font-weight:600;
  color:#333;
  line-height:1.45;
  word-break:break-all;
}
.mtc-status {
  flex-shrink:0;
  font-size:19px;
  line-height:1.35;
  padding-top:2px;
}
.mtc-status.passed,
.mtc-status.recorded { color:#27AE60; }
.mtc-status.pending { color:#E67E22; }
.mtc-status.failed { color:#E74C3C; }
.mtc-meta {
  display:flex;
  align-items:center;
  flex-wrap:wrap;
  gap:6px;
  margin-top:10px;
}
.mtc-chip {
  font-size:16px;
  padding:2px 7px;
  border-radius:10px;
  line-height:1.35;
  color:#666;
  background:#F6F6F8;
}
.mtc-chip.decision { color:#E67E22; background:#FFF3DC; }
.mtc-chip.multi { color:#8E44AD; background:#F5EEF8; }
.mtc-chip.notice { color:#2980B9; background:#EBF5FB; }
.mtc-chip.discussion { color:#27AE60; background:#EAF7EF; }
.mtc-chip.major { color:#E74C3C; background:#FDECEA; }
.mtc-chip.realname { color:#2980B9; background:#EAF2F8; }
.mtc-chip.live { color:#B96F12; background:#FEF4E2; }
.mtc-chip.opinions { color:#C77700; background:#FFF3E0; }
.mtc-arrow { flex-shrink:0; font-size:22px; color:#C2C6CC; align-self:center; }
.mtc-topic.tappable:active { background:#FAFAFA; }
.mtc-summary {
  display:block;
  margin-top:10px;
  font-size:19px;
  color:#666;
  line-height:1.45;
  word-break:break-all;
}

/* 详情页议题正文整体缩两号（md=3议题基准，无 size 类走这里；xl/lg/sm 见下方各自覆盖，均已同步下调） */
.ar-card .mtc-title-main { font-size:17px; }
.ar-card .mtc-topic-title { font-size:17px; }
.ar-card .mtc-status { font-size:15px; }
.ar-card .mtc-summary { font-size:16px; }
.ar-card .mtc-no { width:26px; height:26px; font-size:16px; }

/* 按议题数量动态调整议题卡字号 */
.ar-card.card-sz-xl .mtc-title-main { font-size:22px; }
.ar-card.card-sz-xl .mtc-topic-title { font-size:22px; }
.ar-card.card-sz-xl .mtc-status { font-size:20px; }
.ar-card.card-sz-xl .mtc-no { font-size:20px; width:32px; height:32px; }
.ar-card.card-sz-xl .mtc-chip { font-size:16px; padding:3px 8px; }
.ar-card.card-sz-xl .mtc-topic { padding:24px 0; }
.ar-card.card-sz-xl .mtc-head { margin-bottom:18px; }

.ar-card.card-sz-lg .mtc-title-main { font-size:20px; }
.ar-card.card-sz-lg .mtc-topic-title { font-size:20px; }
.ar-card.card-sz-lg .mtc-status { font-size:18px; }
.ar-card.card-sz-lg .mtc-no { font-size:18px; width:30px; height:30px; }
.ar-card.card-sz-lg .mtc-chip { font-size:15px; padding:2px 7px; }
.ar-card.card-sz-lg .mtc-topic { padding:20px 0; }
.ar-card.card-sz-lg .mtc-head { margin-bottom:14px; }

.ar-card.card-sz-sm .mtc-title-main { font-size:16px; }
.ar-card.card-sz-sm .mtc-topic-title { font-size:16px; }
.ar-card.card-sz-sm .mtc-status { font-size:15px; }
.ar-card.card-sz-sm .mtc-no { font-size:15px; width:24px; height:24px; }
.ar-card.card-sz-sm .mtc-chip { font-size:13px; padding:2px 6px; }
.ar-card.card-sz-sm .mtc-topic { padding:12px 0; }
.ar-card.card-sz-sm .mtc-head { margin-bottom:10px; }
.minutes-basis { margin:10px 16px 18px; background:#FFF9F1; border:1px solid #F4D9B5; border-radius:16px; padding:16px; }
.mb-head { display:flex; flex-direction:column; gap:3px; margin-bottom:13px; }
.mb-sub { font-size:14px; color:#8A7258; line-height:1.45; }
.mb-row { display:flex; align-items:center; gap:11px; min-height:54px; padding:8px 10px; margin-top:8px; border:1px solid #F1E2CE; border-radius:12px; background:#fff; cursor:pointer; }
.mb-row:active { background:#FFF4E5; }
/* 录音行：整行不再是单一动作，点击落在右侧「试听/看转写」两个动作上 */
.mb-row-static { cursor:default; }
.mb-row-static:active { background:#fff; }
.mb-acts { flex-shrink:0; display:flex; align-items:center; gap:2px; }
.mb-act-sep { color:#D8C3AB; font-size:14px; }
.mb-act-tap { padding:10px 8px; margin:-10px 0; cursor:pointer; }
.mb-act-tap:active { opacity:0.6; }
.mb-ico { display:flex; align-items:center; justify-content:center; width:34px; height:34px; border-radius:10px; flex-shrink:0; font-size:16px; font-weight:700; }
.mb-ico.audio { color:#C76A00; background:#FFF0D9; }
.mb-ico.transcript { color:#3976B9; background:#EAF3FD; }
.mb-ico.vote { color:#34865A; background:#EAF7EF; }
.mb-copy { display:flex; flex:1; min-width:0; flex-direction:column; gap:2px; }
.mb-copy b { font-size:15px; color:#2F3338; line-height:1.35; }
.mb-copy small { font-size:13px; color:#8A8F98; line-height:1.35; }
.mb-act { flex-shrink:0; font-size:14px; color:#B56508; font-weight:600; }
.mb-transcript { font-size:14px; color:#444; line-height:1.7; background:#fff; border:1px solid #F0E2CC; border-radius:12px; padding:12px 14px; margin:8px 0 2px; white-space:pre-wrap; max-height:260px; overflow-y:auto; }
</style>
