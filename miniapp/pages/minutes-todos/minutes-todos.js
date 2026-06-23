// 会议待办事项独立页：把后端 todoListText 解析成结构化卡片清单展示。
// 兼容两种后端格式：① Markdown 表格（LLM 常用）② 编号 + “字段：值” 列表（规则兜底）。
// 两者都解析不出时整段兜底，避免比 showModal 更糟。
const api = require('../../utils/api');

function statusType(s) {
  if (!s) return 'pending';
  if (/(已完成|已办结|已处理|完成)/.test(s) && !/(待|未|进行)/.test(s)) return 'done';
  if (/(进行|处理中)/.test(s)) return 'doing';
  return 'pending';
}

// “未明确说明 / 无 / — / -” 等占位值视为空，卡片上不显示
const BLANK_RE = /^(未明确说明|未明确|未说明|暂无|无|—+|-+|\/|待定)$/;
function cellVal(v) {
  const s = (v || '').trim().replace(/\*+/g, '');
  return BLANK_RE.test(s) ? '' : s;
}

// —— Markdown 表格解析 ——
function splitCells(line) {
  let s = line.trim();
  if (s.charAt(0) === '|') s = s.slice(1);
  if (s.charAt(s.length - 1) === '|') s = s.slice(0, -1);
  return s.split('|').map(function (c) { return c.trim(); });
}
function isDivider(cells) {
  return cells.every(function (c) { return c === '' || /^:?-{2,}:?$/.test(c); });
}
function colKey(name) {
  if (/(事项|待办|任务|工作内容|内容)/.test(name)) return 'title';
  if (/(负责|责任|承办|经办)/.test(name)) return 'owner';
  if (/(截止|完成时间|时限|期限|时间)/.test(name)) return 'due';
  if (/来源/.test(name)) return 'source';
  if (/(状态|进度)/.test(name)) return 'status';
  return '';
}
function parseTable(text) {
  const rows = text.split(/\r?\n/)
    .map(function (l) { return l.trim(); })
    .filter(function (l) { return l.indexOf('|') >= 0; })
    .map(splitCells)
    .filter(function (cells) { return cells.length >= 2; });
  if (rows.length < 2) return null;
  const keys = rows[0].map(colKey);
  if (keys.indexOf('title') < 0) return null;   // 没识别出“事项”列，不按表格处理
  const cards = [];
  for (let i = 1; i < rows.length; i++) {
    const cells = rows[i];
    if (isDivider(cells)) continue;
    const card = { title: '', owner: '', due: '', status: '', source: '' };
    keys.forEach(function (k, idx) { if (k && cells[idx] != null) card[k] = cellVal(cells[idx]); });
    if (!card.title) {
      const first = cells.find(function (c) { return c && !/^:?-{2,}:?$/.test(c); });
      card.title = (first || '').replace(/\*+/g, '') || '待办事项';
    }
    if (card.title === '待办事项' && !card.owner && !card.due && !card.status && !card.source) continue;
    card.statusType = statusType(card.status);
    cards.push(card);
  }
  return cards.length ? cards : null;
}

// —— 编号 + “字段：值” 列表解析（规则兜底格式）——
const STOP = '(?=[；;]|负责人[:：]|截止时间[:：]|截止[:：]|完成时间[:：]|来源议题[:：]|来源[:：]|状态[:：]|事项[:：]|$)';
const LABELS = ['负责人', '截止时间', '截止', '完成时间', '来源议题', '来源', '状态', '事项'];
function pick(block, label) {
  const m = block.match(new RegExp(label + '[:：]\\s*(.+?)' + STOP));
  return m ? cellVal(m[1].replace(/[，,。；;]+$/, '').replace(/^[（(]+|[）)]+$/g, '')) : '';
}
function parseOne(block) {
  if (!block) return null;
  const owner = pick(block, '负责人');
  const due = pick(block, '截止时间') || pick(block, '截止') || pick(block, '完成时间');
  const status = pick(block, '状态');
  const source = pick(block, '来源议题') || pick(block, '来源');
  let title = pick(block, '事项');
  if (!title) {
    // 无“事项：”标签时，剥掉已识别字段段，剩下的主体作为标题
    let t = block.replace(/\*+/g, '');
    LABELS.forEach(function (lab) { t = t.replace(new RegExp(lab + '[:：][^；;\\n]*[；;]?', 'g'), ''); });
    title = t.replace(/[；;]+/g, ' ').trim();
  }
  title = (title || '').replace(/\*+/g, '').replace(/^[\s\-—·:：（(]+|[\s\-—·:：）)]+$/g, '').trim();
  if (!title) title = '待办事项';
  if (!owner && !due && !status && !source && title === '待办事项') return null;
  return { title: title, owner: owner, due: due, status: status, source: source, statusType: statusType(status) };
}
function parseList(text) {
  const body = text.replace(/^[#\s]*待办事项(清单)?\s*[:：]?\s*\n?/, '');
  const startRe = /^\s*(?:\d+[.、)]|[-*•])\s*/;
  const blocks = [];
  let cur = null;
  body.split(/\r?\n/).forEach(function (line) {
    if (!line.trim()) return;
    if (startRe.test(line)) {
      if (cur != null) blocks.push(cur);
      cur = line.replace(startRe, '').trim();
    } else if (cur != null) {
      cur += ' ' + line.trim();
    } else {
      cur = line.trim();
    }
  });
  if (cur != null) blocks.push(cur);
  return blocks.map(parseOne).filter(Boolean);
}

function parseTodos(text) {
  if (!text) return [];
  if (text.indexOf('|') >= 0) {
    const t = parseTable(text);
    if (t && t.length) return t;
  }
  return parseList(text);
}

Page({
  data: {
    loading: true,
    cards: [],
    rawText: '',
    emptyText: ''
  },

  onLoad(options) {
    this.meetingId = parseInt(options.meetingId);
    if (!this.meetingId) {
      this.setData({ loading: false, emptyText: '缺少会议参数' });
      return;
    }
    this.load();
  },

  async load() {
    let text = '';
    try { text = await api.committeeQuickTodos(this.meetingId); } catch (e) {}
    text = text && String(text).trim();
    this.fullText = text || '';
    if (!text || /^[#\s]*(本次会议)?\s*无(明确)?待办/.test(text)) {
      this.setData({ loading: false, cards: [], rawText: '', emptyText: '本次会议无明确待办事项。' });
      return;
    }
    const cards = parseTodos(text);
    this.setData({
      loading: false,
      cards: cards,
      rawText: cards.length ? '' : text,   // 解析不出卡片时整段兜底
      emptyText: ''
    });
  },

  copyTodos() {
    const text = this.fullText;
    if (!text) return;
    wx.setClipboardData({ data: text, success() { wx.showToast({ title: '已复制' }); } });
  }
});
