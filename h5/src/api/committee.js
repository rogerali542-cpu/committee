// ═══════════════════════════════════════════════
// API — 委员会会议
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  committeeList: function (stage) {
    return core.request('GET', '/api/committees' + (stage ? '?stage=' + stage : ''));
  },
  // 资料库：已归档会议
  committeeArchiveList: function () {
    return core.request('GET', '/api/committees?archived=true');
  },
  // 直接归档（不公示）
  committeeArchive: function (id) {
    return core.request('POST', '/api/committees/' + id + '/archive');
  },
  // 撤销归档（仅误归档用，必填原因；已公示需先撤回公示）
  committeeRevokeArchive: function (id, reason) {
    return core.request('POST', '/api/committees/' + id + '/archive/revoke', { reason: reason });
  },
  committeeAddArchiveExtra: function (id, fileName, sizeText, fileType, reason, fileUrl) {
    var p = '?fileName=' + encodeURIComponent(fileName) + '&sizeText=' + encodeURIComponent(sizeText || '') + '&reason=' + encodeURIComponent(reason || '');
    if (fileType) p += '&fileType=' + fileType;
    if (fileUrl) p += '&fileUrl=' + encodeURIComponent(fileUrl);
    return core.request('POST', '/api/committees/' + id + '/archive-extras' + p);
  },
  committeeDetail: function (id) {
    return core.request('GET', '/api/committees/' + id);
  },
  committeeMembers: function () {
    return core.request('GET', '/api/committees/members');
  },
  committeeCreate: function (data) {
    return core.request('POST', '/api/committees', data);
  },
  committeeUpdate: function (id, data) {
    return core.request('PUT', '/api/committees/' + id, data);
  },
  committeeAdvance: function (id, action, mode) {
    return core.realRequest('POST', '/api/committees/' + id + '/advance?action=' + action + (mode ? '&mode=' + mode : ''));
  },
  committeeToggleDelivery: function (id, userRoleId, field) {
    return core.request('PUT', '/api/committees/' + id + '/delivery/' + userRoleId + '?field=' + field);
  },
  committeeSendAll: function (id, memberIds) {
    return core.request('POST', '/api/committees/' + id + '/delivery/send-all', { memberIds: memberIds || [] });
  },
  committeeMarkWechatNotified: function (id) {
    return core.request('POST', '/api/committees/' + id + '/delivery/wechat-mark');
  },
  // 清空通知记录：删本会议全部通知历史+送达、重置为未通知（0728 用户定为正式功能）
  committeeClearNotifications: function (id) {
    return core.request('POST', '/api/committees/' + id + '/delivery/clear');
  },
  // 委员打开详情时回写"已读"（仅对已送达内容生效）
  committeeMarkDeliveryRead: function (id) {
    return core.request('POST', '/api/committees/' + id + '/delivery/read');
  },
  committeeToggleAttend: function (id, userRoleId, field) {
    return core.request('PUT', '/api/committees/' + id + '/attendance/' + userRoleId + '?field=' + field);
  },
  committeeSelfToggle: function (id, field) {
    return core.realRequest('PUT', '/api/committees/' + id + '/self?field=' + field);
  },
  committeeSelfAttend: function (id, mode, authorizeProxySign) {
    return core.realRequest('PUT', '/api/committees/' + id + '/self/attendance?mode=' + encodeURIComponent(mode)
      + '&authorizeProxySign=' + (authorizeProxySign ? 'true' : 'false'));
  },
  committeeSignAll: function (id) {
    return core.realRequest('POST', '/api/committees/' + id + '/attendance/sign-all');
  },
  committeeSetOnlineAttendance: function (id, presentMemberIds) {
    return core.realRequest('PUT', '/api/committees/' + id + '/online-attendance', {
      presentMemberIds: presentMemberIds || []
    });
  },
  // 导出签到名单（返回 { fileName, content(CSV) }）
  committeeExportAttendance: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/attendance/export');
  },
  // 会后归档材料：包含全体参会名单及手写签名栏的 PDF 签到表
  committeeExportAttendanceSheet: function (id) {
    return core.download('/api/committees/' + id + '/attendance-sheet.pdf?t=' + Date.now());
  },
  // 会后原始留档材料：会议过程、意见、表决、待办及统一签字区
  committeeExportMeetingRecord: function (id) {
    return core.download('/api/committees/' + id + '/meeting-record.pdf?t=' + Date.now());
  },
  // 会议记录纯文本（DocPreview 页内预览用，与 meeting-record.pdf 同一份内容装配）
  committeeMeetingRecordText: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/meeting-record-text');
  },
  // 会前公告 PDF（向全体业主公告会议议程、征求意见，会前7天张贴公示栏）
  committeeExportPreNotice: function (id) {
    return core.download('/api/committees/' + id + '/pre-notice.pdf?t=' + Date.now());
  },
  // 会后公示 PDF（公示页一键导出打印，张贴公告栏）
  committeeExportPublicNotice: function (id) {
    return core.download('/api/committees/' + id + '/public-notice.pdf?t=' + Date.now());
  },
  // 列席人员（居委/街道/物业等非委员到会者）：会后整理页登记，进入会议记录与纪要
  committeeSetObservers: function (id, text) {
    return core.request('PUT', '/api/committees/' + id + '/observers', { text: text });
  },
  // 会议纪要 PDF（正文与 GET /minutes 同源，公文格式）
  committeeExportMinutesPdf: function (id) {
    return core.download('/api/committees/' + id + '/minutes.pdf?t=' + Date.now());
  },
  // 录音多条：获取会议全部录音列表
  committeeRecordings: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/recordings');
  },
  // 上传录音（纯存，不自动转写）；durationSec 为录音时长（秒），用于转写页展示
  committeeUploadRecording: function (id, filePath, durationSec, onProgress) {
    var q = (durationSec != null && durationSec > 0) ? ('?durationSec=' + Math.round(durationSec)) : '';
    // 服务端还会保存并转码长录音，可能明显超过默认 30 秒；这里单独放宽到 3 分钟。
    return core.uploadFile('/api/committees/' + id + '/quick/recording/upload' + q, filePath, 'file', {}, {
      timeout: 180000,
      onUploadProgress: onProgress
    });
  },
  // 删除一条录音（转写页删废录/多余段）
  committeeDeleteRecording: function (id, recordingId) {
    return core.realRequest('DELETE', '/api/committees/' + id + '/quick/recordings/' + recordingId);
  },
  // 主任选片触发转写
  committeeTranscribeRecording: function (id, recordingId) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/recordings/' + recordingId + '/transcribe');
  },
  committeeAddTopic: function (id, title, type, decisionType, optionsJson, realNameVote, content) {
    var params = '?title=' + encodeURIComponent(title) + '&type=' + type;
    if (decisionType) params += '&decisionType=' + decisionType;
    if (optionsJson) params += '&options=' + encodeURIComponent(optionsJson);
    if (realNameVote) params += '&realNameVote=true';
    if (content) params += '&content=' + encodeURIComponent(content);
    return core.realRequest('POST', '/api/committees/' + id + '/topics' + params);
  },
  committeeRemoveTopic: function (id, topicId) {
    return core.request('DELETE', '/api/committees/' + id + '/topics/' + topicId);
  },
  // 通报议题：记录本人已查看（会议参会名单全体看完自动已通报）
  committeeNoticeView: function (id, topicId) {
    return core.realRequest('POST', '/api/committees/' + id + '/topics/' + topicId + '/notice-view');
  },
  // 通报议题：已宣读 → 标记已通报
  committeeNoticeRead: function (id, topicId) {
    return core.realRequest('POST', '/api/committees/' + id + '/topics/' + topicId + '/notice-read');
  },
  committeeRenameTopic: function (id, topicId, title) {
    return core.realRequest('PUT', '/api/committees/' + id + '/topics/' + topicId + '/title?title=' + encodeURIComponent(title));
  },
  committeeVote: function (id, topicId, choice, selectedId) {
    var params = choice ? '?choice=' + choice : '';
    if (selectedId) params += (params ? '&' : '?') + 'selectedId=' + selectedId;
    return core.request('PUT', '/api/committees/' + id + '/topics/' + topicId + '/vote' + params);
  },
  // 结束表决（主任/副主任）：揭晓票数并公布结果
  committeeCloseVote: function (id, topicId) {
    return core.realRequest('POST', '/api/committees/' + id + '/topics/' + topicId + '/close-vote');
  },
  // 撤回本人投票（表决未结束前）：回到未投，可重新投票
  committeeRetractVote: function (id, topicId) {
    return core.realRequest('DELETE', '/api/committees/' + id + '/topics/' + topicId + '/vote');
  },
  // ===== 议题意见 =====
  committeeOpinions: function (id) {
    return core.request('GET', '/api/committees/' + id + '/opinions');
  },
  committeeAddOpinion: function (id, topicId, content, source) {
    return core.request('POST', '/api/committees/' + id + '/topics/' + topicId + '/opinions', { content: content, source: source || 'text' });
  },
  committeeUpdateOpinion: function (id, opinionId, content) {
    return core.request('PUT', '/api/committees/' + id + '/opinions/' + opinionId, { content: content });
  },
  committeeRemoveOpinion: function (id, opinionId) {
    return core.request('DELETE', '/api/committees/' + id + '/opinions/' + opinionId);
  },
  // 意见语音输入：短语音同步转文字（豆包 ASR 转写在服务端做，可能要十几秒）
  committeeVoiceToText: function (id, fileOrBlob) {
    return core.uploadFile('/api/committees/' + id + '/asr', fileOrBlob, 'file', {}, { timeout: 60000 });
  },
  // 认领/指派 AI 提炼的现场意见：不带 userRoleId 是本人认领；带 userRoleId 仅主任可指派
  committeeClaimOpinion: function (id, opinionId, userRoleId) {
    return core.request('PUT', '/api/committees/' + id + '/opinions/' + opinionId + '/claim', userRoleId ? { userRoleId: userRoleId } : {});
  },
  // 意见 AI 助手：mode=polish 润色已有意见 / mode=draft 按口头描述代拟发言（真调大模型，可能要十几秒）
  committeeOpinionAssist: function (id, topicId, mode, text) {
    return core.realRequest('POST', '/api/committees/' + id + '/topics/' + topicId + '/opinions/assist', { mode: mode, text: text }, { timeout: 120000 });
  },
  // 主持人修正参会状态（名单弹窗下拉）：onsite/remote/declined/none
  committeeSetAttendanceStatus: function (id, userRoleId, value) {
    return core.request('PUT', '/api/committees/' + id + '/attendance/' + userRoleId + '/status?value=' + value);
  },
  // 通用附件上传（图片/PDF）：拿到公网 URL 后再调各业务接口（如代投凭证）
  uploadAttachment: function (fileOrBlob) {
    return core.uploadFile('/api/attachments/upload', fileOrBlob, 'file', {}, { timeout: 60000 });
  },
  committeeProxyTargets: function (id, keyword) {
    var params = keyword ? '?keyword=' + encodeURIComponent(keyword) : '';
    return core.request('GET', '/api/committees/' + id + '/proxy-targets' + params);
  },
  committeeProxySubmit: function (id, data) {
    return core.request('POST', '/api/committees/' + id + '/proxy-actions', data);
  },
  committeeToggleFlag: function (id, flag) {
    return core.request('PUT', '/api/committees/' + id + '/flags?flag=' + flag);
  },
  committeeToggleJuwei: function (id) {
    return core.realRequest('POST', '/api/committees/' + id + '/juwei');
  },
  committeeAddEvidence: function (id, fileName, fileType, fileUrl) {
    return core.realRequest('POST', '/api/committees/' + id + '/evidences?fileName=' + encodeURIComponent(fileName) + '&fileType=' + fileType + (fileUrl ? '&fileUrl=' + encodeURIComponent(fileUrl) : ''));
  },
  committeeRemoveEvidence: function (id, evId) {
    return core.realRequest('DELETE', '/api/committees/' + id + '/evidences/' + evId);
  },
  committeePublish: function (id) {
    return core.request('POST', '/api/committees/' + id + '/publish');
  },
  committeeWithdrawPublish: function (id, reason) {
    return core.request('POST', '/api/committees/' + id + '/publish/withdraw', { reason: reason });
  },
  committeeMinutes: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/minutes');
  },
  // AI 生成党建新闻：发起即返回（后端 @Async 后台跑）。返回 {status,taskId,title,content}，随后轮询 committeeNewsStatus。
  committeeGenerateNews: function (id) {
    return core.realRequest('POST', '/api/committees/' + id + '/news');
  },
  // 党建新闻生成状态 + 结果（none/running/success/failed）；success 直接带回标题+正文，供切回页面查看。
  committeeNewsStatus: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/news-status');
  },
  committeeMinutesRevisions: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/minutes/revisions');
  },
  committeeUpdateMinutes: function (id, text, reason) {
    return core.realRequest('PUT', '/api/committees/' + id + '/minutes', { text: text, reason: reason });
  },
  committeeStats: function () {
    return core.request('GET', '/api/committees/stats');
  },
  // 新建会议：上传文档/拍照件，后端 OCR + 大模型识别出会议信息（返回 MeetingPrefillVO）
  // OCR + 推理模型抽取较慢（可达 30-40s），放开超时到 120s，避免默认 30s 超时误报"识别失败"
  committeeParseDocument: function (file) {
    return core.uploadFile('/api/committees/parse-document', file, 'file', {}, { timeout: 120000 });
  },
  // 新建会议：一次上传多张照片/多个文件，后端逐个 OCR + 一次大模型统一识别（返回 MeetingPrefillVO，含 files[]）
  // 多文件 OCR+抽取更慢，放开超时到 180s
  committeeParseDocuments: function (files) {
    return core.uploadFiles('/api/committees/parse-documents', files, 'files', {}, { timeout: 180000 });
  },
  committeePublishScore: function () {
    return core.request('GET', '/api/committees/publish-score');
  },
  committeeRemove: function (id) {
    return core.request('DELETE', '/api/committees/' + id);
  },
  committeeCompliance: function (id, status) {
    return core.request('PUT', '/api/committees/' + id + '/compliance?status=' + status);
  },
  committeeAddMaterial: function (id, fileName, sizeText, fileType, fileUrl) {
    var p = '?fileName=' + encodeURIComponent(fileName) + '&sizeText=' + encodeURIComponent(sizeText || '');
    if (fileType) p += '&fileType=' + fileType;
    if (fileUrl) p += '&fileUrl=' + encodeURIComponent(fileUrl);
    return core.realRequest('POST', '/api/committees/' + id + '/materials' + p);
  },
  committeeRemoveMaterial: function (id, materialId) {
    return core.request('DELETE', '/api/committees/' + id + '/materials/' + materialId);
  },
  committeeUpdateNotice: function (id, title, content) {
    return core.request('PUT', '/api/committees/' + id + '/notice-draft', { title: title, content: content });
  },
  committeeQuickUploadRecording: function (id, filePath) {
    return core.uploadFile('/api/committees/' + id + '/quick/recording/upload', filePath, 'file');
  },
  committeeQuickRecordingStatus: function (id, taskId) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/recording/status?taskId=' + encodeURIComponent(taskId));
  },
  committeeQuickExtract: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/extract');
  },
  committeeQuickTranscript: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/transcript');
  },
  // 单条录音的转写原文（录音详情里单独查看这一段）
  committeeQuickRecordingTranscript: function (id, recordingId) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/recordings/' + recordingId + '/transcript');
  },
  // 「谁在录音」在册表：心跳 / 下线 / 查询（开录前提示"XX 正在录音"用）
  committeeRecordingBeat: function (id) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/recording-live/beat');
  },
  committeeRecordingBeatStop: function (id) {
    return core.realRequest('DELETE', '/api/committees/' + id + '/quick/recording-live/beat');
  },
  committeeRecordingLive: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/recording-live');
  },
  committeeQuickConfirm: function (id, data) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/confirm', data);
  },
  committeeQuickPolish: function (id, data) {
    // 大模型生成纪要较慢，放开默认 60s 超时（后端 LLM 上限 180s）
    return core.realRequest('POST', '/api/committees/' + id + '/quick/polish', data, { timeout: 210000 });
  },
  committeeMinutesStatus: function (id) {
    // 纪要生成任务状态（none/running/success/failed）：重进会议查它 → 跨刷新/换设备/隔天都能接回入口
    return core.realRequest('GET', '/api/committees/' + id + '/quick/minutes-status');
  },
  committeeQuickTopicReport: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/topic-report');
  },
  committeeQuickTodos: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/todos');
  },
  // 结构化待办：列表（含未固化时的 raw 原文）/ 固化落库 / 更新状态
  committeeTodoList: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/todos/list');
  },
  committeeTodoInit: function (id, items) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/todos/init', items);
  },
  committeeTodoStatus: function (id, todoId, status) {
    return core.realRequest('PUT', '/api/committees/' + id + '/quick/todos/' + todoId + '/status?status=' + encodeURIComponent(status));
  },
  committeeTodoDelete: function (id, todoId) {
    return core.realRequest('DELETE', '/api/committees/' + id + '/quick/todos/' + todoId);
  },
  committeeTodoPushTicket: function (id, todoId) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/todos/' + todoId + '/ticket');
  },
  committeeQuickTopicSummary: function (id, data) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/topic-summary', data);
  },
  committeeQuickTopicSummaryTask: function (id, data) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/topic-summary/tasks', data);
  },
  committeeQuickTopicSummaryTaskStatus: function (id, taskId) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/topic-summary/tasks/' + encodeURIComponent(taskId));
  }
};
