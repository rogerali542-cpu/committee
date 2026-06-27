// 会议文本解析 — 自 miniapp/utils/meeting-parser.js 1:1 迁移（CommonJS → ESM）。
// 从粘贴的会议通知/资料正文中提取标题、日期、时间、地点、议题等字段。

const FIELD_LABELS = {
  title: '会议标题',
  meetingDate: '会议日期',
  meetingTime: '开始时间',
  location: '会议地点',
  description: '主要议题',
  type: '大会类型',
  needsVote: '是否表决',
  hasDecision: '是否表决',
  inviteJuwei: '居委会见证'
};

function normalizeText(text) {
  return String(text || '')
    .replace(/\r/g, '\n')
    .replace(/[ \t]+/g, ' ')
    .replace(/：/g, ':')
    .trim();
}

function pad2(v) {
  return String(v).padStart(2, '0');
}

function toDateStr(year, month, day) {
  const y = Number(year) || new Date().getFullYear();
  const m = Number(month);
  const d = Number(day);
  if (!m || !d || m > 12 || d > 31) return '';
  return y + '-' + pad2(m) + '-' + pad2(d);
}

function extractDate(text) {
  let m = text.match(/(20\d{2})[年\/\-. ]{1,3}(\d{1,2})[月\/\-. ]{1,3}(\d{1,2})\s*日?/);
  if (m) return toDateStr(m[1], m[2], m[3]);

  m = text.match(/(\d{1,2})月(\d{1,2})日/);
  if (m) return toDateStr(new Date().getFullYear(), m[1], m[2]);

  return '';
}

function extractTime(text) {
  let m = text.match(/(上午|下午|晚上|晚间|中午)?\s*(\d{1,2})[:：点时](\d{1,2})?\s*分?/);
  if (!m) return '';
  let hour = Number(m[2]);
  const minute = Number(m[3] || 0);
  const period = m[1] || '';
  if ((period === '下午' || period === '晚上' || period === '晚间') && hour < 12) hour += 12;
  if (period === '中午' && hour < 11) hour += 12;
  if (hour > 23 || minute > 59) return '';
  return pad2(hour) + ':' + pad2(minute);
}

function extractLineByLabels(text, labels) {
  const lines = text.split('\n').map(line => line.trim()).filter(Boolean);
  for (const line of lines) {
    for (const label of labels) {
      const idx = line.indexOf(label + ':');
      if (idx >= 0) {
        return line.slice(idx + label.length + 1).replace(/[。；;]+$/, '').trim();
      }
    }
  }
  return '';
}

function extractTitle(text) {
  const byLabel = extractLineByLabels(text, ['会议标题', '会议名称', '大会标题', '大会名称', '通知标题']);
  if (byLabel) return byLabel;

  const lines = text.split('\n').map(line => line.trim()).filter(Boolean);
  const hit = lines.find(line => /业委会|业主大会|会议通知|例会|临时会议|讨论会/.test(line) && line.length <= 42);
  if (hit) return hit.replace(/^关于召开/, '').replace(/的?通知$/, '').trim();

  return '';
}

function extractLocation(text) {
  return extractLineByLabels(text, ['会议地点', '召开地点', '地点', '投票地点', '活动地点']);
}

function extractDescription(text) {
  const byLabel = extractLineByLabels(text, ['主要议题', '会议议题', '议题', '会议事项', '主要事项', '讨论事项', '表决事项', '会议内容', '议程']);
  if (byLabel) return byLabel;

  const lines = text.split('\n').map(line => line.trim()).filter(Boolean);
  const topicLines = lines.filter(line => /^(\d+[.、]|[一二三四五六七八九十]+[、.])/.test(line));
  if (topicLines.length) {
    return topicLines.slice(0, 5).join('\n');
  }

  return '';
}

// 从文本中提取结构化议题列表
function extractTopics(text) {
  // 先看有没有"议题："标签行
  const byLabel = extractLineByLabels(text, ['议题', '会议议题', '表决议题', '表决事项', '主要议题', '会议事项', '讨论事项']);
  if (byLabel) {
    // 按编号或换行切分
    var items = byLabel.split(/[\n;；]/).map(function (l) { return l.trim(); }).filter(Boolean);
    items = items.map(function (l) { return l.replace(/^(\d+[.、)]\s*|[（(]\d+[)）]\s*)/, '').trim(); }).filter(Boolean);
    if (items.length) {
      return items.map(function (t) {
        return { title: t, decisionType: 'simple', type: 'decision', options: [] };
      });
    }
  }
  // 从全文中找编号行
  var lines = text.split('\n').map(function (l) { return l.trim(); }).filter(Boolean);
  var numberedLines = lines.filter(function (l) { return /^(\d+[.、]|[一二三四五六七八九十]+[、.])/.test(l); });
  if (numberedLines.length) {
    return numberedLines.slice(0, 8).map(function (l) {
      var clean = l.replace(/^(\d+[.、]\s*|[一二三四五六七八九十]+[、.]\s*)/, '').trim();
      // 移除常见的后缀描述（如"方案""讨论""审议"后的说明）
      return { title: clean, decisionType: 'simple', type: 'decision', options: [] };
    });
  }
  return [];
}

export function parseMeetingText(input, options = {}) {
  const text = normalizeText(input);
  const fields = {};
  if (!text) {
    return { fields, matched: [], missing: requiredMissing(fields), matchedText: '', missingText: '会议标题、会议日期、开始时间、会议地点、主要议题' };
  }

  const title = extractTitle(text);
  const meetingDate = extractDate(text);
  const meetingTime = extractTime(text);
  const location = extractLocation(text);
  const description = extractDescription(text);
  const topics = extractTopics(text);

  if (title) fields.title = title;
  if (meetingDate) fields.meetingDate = meetingDate;
  if (meetingTime) fields.meetingTime = meetingTime;
  if (location) fields.location = location;
  if (description) fields.description = description;
  if (topics.length) fields.topics = topics;

  const noVote = /不涉及表决|无需表决|无表决/.test(text);
  const hasVote = /表决|投票|征询意见|改选|选聘|续聘|专项维修资金|维修资金/.test(text);
  if (options.kind === 'owners') {
    if (/临时业主大会|临时大会/.test(text)) fields.type = 'special';
    if (/年度|定期业主大会|定期大会/.test(text)) fields.type = 'regular';
    if (noVote) fields.needsVote = false;
    if (hasVote && !noVote) fields.needsVote = true;
  } else {
    if (noVote) fields.hasDecision = false;
    if (hasVote && !noVote) fields.hasDecision = true;
    if (/居委会|社区居委|街道|见证/.test(text)) fields.inviteJuwei = true;
  }

  const matched = Object.keys(fields).map(key => FIELD_LABELS[key] || key);
  const missing = requiredMissing(fields);
  return {
    fields,
    matched,
    missing,
    matchedText: matched.length ? matched.join('、') : '暂无',
    missingText: missing.length ? missing.join('、') : '无'
  };
}

function requiredMissing(fields) {
  return ['title', 'meetingDate', 'meetingTime', 'location', 'description']
    .filter(key => !fields[key])
    .map(key => FIELD_LABELS[key]);
}

export default { parseMeetingText };
