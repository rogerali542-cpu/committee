import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "outputs/project-budget-estimate";
const outputPath = `${outputDir}/临汾路街道业委会智能履职辅助项目-成本及40万元申报预算.xlsx`;
const previewDir = ".codex-sheet-review/project-previews-v2";
await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(previewDir, { recursive: true });

const wb = Workbook.create();
const summary = wb.worksheets.add("决策摘要");
const detail = wb.worksheets.add("功能及费用");
const budget = wb.worksheets.add("费用明细");
const schedule = wb.worksheets.add("周期计划");

const C = {
  navy: "#1F4E78",
  blue: "#D9EAF7",
  pale: "#EEF6FB",
  green: "#E2F0D9",
  amber: "#FFF2CC",
  red: "#FCE4D6",
  gray: "#F5F7FA",
  border: "#B7C3D0",
  dark: "#203040",
  white: "#FFFFFF",
};

function setTitle(sheet, range, value) {
  sheet.getRange(range).merge();
  const r = sheet.getRange(range);
  r.values = [[value]];
  r.format = {
    fill: C.navy,
    font: { bold: true, color: C.white, size: 16, name: "Microsoft YaHei" },
    horizontalAlignment: "center",
    verticalAlignment: "center",
  };
  r.format.rowHeight = 34;
}
function setSection(sheet, range, value) {
  sheet.getRange(range).merge();
  const r = sheet.getRange(range);
  r.values = [[value]];
  r.format = {
    fill: C.blue,
    font: { bold: true, color: C.dark, name: "Microsoft YaHei" },
    verticalAlignment: "center",
  };
  r.format.rowHeight = 24;
}
function setHeader(r) {
  r.format = {
    fill: C.navy,
    font: { bold: true, color: C.white, name: "Microsoft YaHei" },
    horizontalAlignment: "center",
    verticalAlignment: "center",
    wrapText: true,
    borders: { preset: "all", style: "thin", color: C.border },
  };
}
function setGrid(r) {
  r.format = {
    font: { name: "Microsoft YaHei" },
    verticalAlignment: "center",
    wrapText: true,
    borders: { preset: "all", style: "thin", color: C.border },
  };
}

// 功能及费用：作为成本模型的主要输入表
detail.showGridLines = false;
setTitle(detail, "A1:J1", "功能模块、预计实施成本与申报预算分配");
detail.getRange("A2:J2").merge();
detail.getRange("A2:J2").values = [[
  "说明：预计实施成本为内部交付成本测算；申报预算还需覆盖项目管理、税费、交付风险、验收整改、质保及合理利润。"
]];
detail.getRange("A2:J2").format = {
  fill: C.amber,
  font: { color: "#7F6000", name: "Microsoft YaHei" },
  horizontalAlignment: "center",
};
detail.getRange("A4:J4").values = [[
  "序号", "费用属性", "一级模块", "二级功能/费用项", "主要建设内容或交付物",
  "单位", "数量", "内部成本单价（万元）", "预计实施成本（万元）", "申报预算（万元）"
]];
setHeader(detail.getRange("A4:J4"));

const rows = [
  [1, "平台能力", "公共基础能力", "平台基础能力复用", "多角色、基础流程、档案、附件、权限及通用组件；需提供软件产品、授权或已有平台复用依据。", "项", 1, 3.0, null, 6.0],
  [2, "功能开发", "业委会会议", "会议流程", "会议创建、议题材料、提前7天通知、送达、居委会列席、线下签到及表决签字表、线上结果登记、公示归档。", "人月", 2.5, 1.2, null, 5.0],
  [3, "功能开发", "AI会议助手", "录音转写与纪要", "录音上传、语音转写、议题整理、纪要初稿和待办提取；正式内容人工审核。大模型调用由政府承担。", "人月", 2.0, 1.2, null, 4.0],
  [4, "功能开发", "业主接待", "接待与反馈闭环", "接待记录、物业沟通及回复材料、向业主反馈结果、年度接待次数统计。", "人月", 1.0, 1.2, null, 2.0],
  [5, "功能开发", "学习培训", "学习培训记录", "内部学习、街镇培训、重点人员参训、材料上传及年度次数统计。", "人月", 0.75, 1.2, null, 1.5],
  [6, "功能开发", "印章管理", "用印台账", "印章及保管人、用印时间和用途、保管委员确认、签字登记表及盖章文件归档。", "人月", 0.75, 1.2, null, 1.5],
  [7, "功能开发", "履职档案", "分类归档", "按年度和工作类型归档、查询、导出会议、接待、培训和用印资料。", "人月", 1.25, 1.2, null, 2.5],
  [8, "功能开发", "可信存证", "可信档案馆轻量对接", "正式公示纪要推送、回执保存和验真；可信档案馆为本公司已有产品，仅进行内部接口配置和联调。", "人月", 0.25, 1.2, null, 0.5],
  [9, "功能开发", "权限与安全", "角色权限与数据保护", "成员权限、敏感信息保护、操作日志和数据隔离。", "人月", 1.25, 1.2, null, 2.5],
  [10, "功能开发", "提醒与统计", "规则提醒及年度统计", "提前7天通知、会后3日公示、会议次数、接待次数、培训记录等提醒和统计。", "人月", 2.25, 1.2, null, 4.5],
  [11, "安全合规", "密码应用", "密码应用建设", "身份认证、传输保护、敏感数据保护、重要日志完整性等密码应用建设。", "项", 1, 1.2, null, 2.0],
  [12, "实施服务", "项目实施", "部署与初始化", "环境配置、系统部署、基础数据初始化和上线支持。", "项", 1, 0.9, null, 1.5],
  [13, "实施服务", "用户培训", "培训与操作手册", "一次集中培训、操作手册和常见问题说明。", "项", 1, 0.3, null, 0.5],
  [14, "第三方费用", "软件测试", "第三方软件测试", "功能、兼容性等测试及验收整改支持。", "项", 1, 2.0, null, 2.0],
  [15, "第三方费用", "密码测评", "密码应用安全性评估", "由具备相应资质的机构开展测评并出具报告。", "项", 1, 1.0, null, 1.0],
  [16, "运维服务", "运行维护", "一年运行维护", "故障处理、升级、安全修复、备份和用户支持。", "年", 1, 1.5, null, 3.0],
  [17, "政府承担", "大模型资源", "模型调用及相关消耗", "由政府统一提供并承担，不纳入本项目申报预算。", "项", 1, 0.0, null, 0.0],
  [18, "不计列", "硬件及平台", "专用硬件及可信档案馆采购", "不采购专用硬件；可信档案馆已在临汾路街道使用。", "项", 1, 0.0, null, 0.0],
];
detail.getRange("A5:J22").values = rows;
for (let r = 5; r <= 22; r++) detail.getRange(`I${r}`).formulas = [[`=G${r}*H${r}`]];
detail.getRange("A23:H23").merge();
detail.getRange("A23").values = [["合计"]];
detail.getRange("I23").formulas = [["=SUM(I5:I22)"]];
detail.getRange("J23").formulas = [["=SUM(J5:J22)"]];
setGrid(detail.getRange("A5:J23"));
detail.getRange("A23:J23").format = {
  fill: C.navy,
  font: { bold: true, color: C.white, name: "Microsoft YaHei", size: 11 },
  borders: { preset: "all", style: "thin", color: C.border },
  verticalAlignment: "center",
};
detail.getRange("G5:J23").format.numberFormat = "0.00";
detail.getRange("A5:B22").format.horizontalAlignment = "center";
detail.getRange("F5:J23").format.horizontalAlignment = "center";
detail.getRange("B5:B22").conditionalFormats.add("containsText", { text: "第三方费用", format: { fill: C.red } });
detail.getRange("B5:B22").conditionalFormats.add("containsText", { text: "政府承担", format: { fill: C.green } });
detail.getRange("B5:B22").conditionalFormats.add("containsText", { text: "功能开发", format: { fill: C.pale } });
detail.getRange("A:A").format.columnWidth = 7;
detail.getRange("B:B").format.columnWidth = 13;
detail.getRange("C:C").format.columnWidth = 16;
detail.getRange("D:D").format.columnWidth = 21;
detail.getRange("E:E").format.columnWidth = 46;
detail.getRange("F:F").format.columnWidth = 9;
detail.getRange("G:J").format.columnWidth = 16;
detail.getRange("5:22").format.rowHeight = 48;
detail.freezePanes.freezeRows(4);

// 费用明细：分类汇总+差额解释
budget.showGridLines = false;
setTitle(budget, "A1:G1", "成本与40万元申报预算汇总");
budget.getRange("A3:G3").values = [[
  "费用分类", "预计实施成本（万元）", "申报预算（万元）", "差额（万元）",
  "申报占比", "是否可压缩", "说明"
]];
setHeader(budget.getRange("A3:G3"));
const cats = [
  ["平台能力", null, null, null, null, "可适度压缩", "复用已有能力，申报预算6万元。"],
  ["功能开发", null, null, null, null, "不建议再压", "12人月，申报按2万元/人月测算。"],
  ["安全合规", null, null, null, null, "不建议压缩", "密码应用建设2万元。"],
  ["实施服务", null, null, null, null, "已压缩", "部署1.5万元、培训0.5万元。"],
  ["第三方费用", null, null, null, null, "不可随意压缩", "软件测试2万元、密评1万元。"],
  ["运维服务", null, null, null, null, "不建议压缩", "一年运维申报3万元。"],
  ["政府承担", null, null, null, null, "不计申报", "大模型资源由政府承担。"],
  ["不计列", null, null, null, null, "不计申报", "硬件及可信档案馆平台采购为0。"],
];
budget.getRange("A4:G11").values = cats;
for (let r = 4; r <= 11; r++) {
  budget.getRange(`B${r}`).formulas = [[`=SUMIF('功能及费用'!$B$5:$B$22,A${r},'功能及费用'!$I$5:$I$22)`]];
  budget.getRange(`C${r}`).formulas = [[`=SUMIF('功能及费用'!$B$5:$B$22,A${r},'功能及费用'!$J$5:$J$22)`]];
  budget.getRange(`D${r}`).formulas = [[`=C${r}-B${r}`]];
  budget.getRange(`E${r}`).formulas = [[`=C${r}/$C$12`]];
}
budget.getRange("A12:A12").merge();
budget.getRange("A12").values = [["合计"]];
budget.getRange("B12").formulas = [["=SUM(B4:B11)"]];
budget.getRange("C12").formulas = [["=SUM(C4:C11)"]];
budget.getRange("D12").formulas = [["=SUM(D4:D11)"]];
budget.getRange("E12").formulas = [["=SUM(E4:E11)"]];
budget.getRange("F12:G12").merge();
budget.getRange("F12").values = [["成本与申报金额之间的差额不等同于净利润。"]];
setGrid(budget.getRange("A4:G12"));
budget.getRange("A12:G12").format = {
  fill: C.navy,
  font: { bold: true, color: C.white, name: "Microsoft YaHei" },
  borders: { preset: "all", style: "thin", color: C.border },
};
budget.getRange("B4:D12").format.numberFormat = "0.00";
budget.getRange("E4:E12").format.numberFormat = "0.0%";
budget.getRange("B4:E12").format.horizontalAlignment = "right";
budget.getRange("F4:F11").format.horizontalAlignment = "center";

setSection(budget, "A14:G14", "成本与申报金额差额的用途说明");
budget.getRange("A15:G19").merge(true);
budget.getRange("A15:G19").values = [
  ["1. 项目管理与沟通：需求确认、会议、进度管理、跨单位协调和文档整理。"],
  ["2. 税费与公司间接成本：财务、行政、办公环境、公共技术支持及税费。"],
  ["3. 交付与验收风险：需求调整、接口不确定性、测试整改和验收材料补充。"],
  ["4. 质保和服务风险：一年服务承诺、故障响应、安全修复和人员替补。"],
  ["5. 合理利润：保障项目持续交付和后续服务，不应把全部差额理解为净利润。"],
];
budget.getRange("A15:G19").format = {
  fill: C.gray,
  font: { name: "Microsoft YaHei" },
  wrapText: true,
  verticalAlignment: "center",
  borders: { preset: "all", style: "thin", color: C.border },
};
setSection(budget, "A21:G21", "申报金额结论");
budget.getRange("A22:G23").merge();
budget.getRange("A22:G23").formulas = [[
  `="建议申报金额："&TEXT(C12,"0.00")&"万元；预计实施成本："&TEXT(B12,"0.00")&"万元；差额："&TEXT(D12,"0.00")&"万元。当前40万元已是压缩方案，不建议继续压缩核心功能、第三方测试、密评和一年运维。"`
]];
budget.getRange("A22:G23").format = {
  fill: C.green,
  font: { bold: true, color: "#375623", name: "Microsoft YaHei", size: 12 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "outside", style: "medium", color: "#70AD47" },
};
budget.getRange("A:A").format.columnWidth = 17;
budget.getRange("B:D").format.columnWidth = 19;
budget.getRange("E:E").format.columnWidth = 13;
budget.getRange("F:F").format.columnWidth = 18;
budget.getRange("G:G").format.columnWidth = 43;
budget.getRange("4:11").format.rowHeight = 34;
budget.getRange("15:19").format.rowHeight = 28;
budget.freezePanes.freezeRows(3);

// 决策摘要
summary.showGridLines = false;
setTitle(summary, "A1:H1", "临汾路街道业委会智能履职辅助项目");
summary.getRange("A2:H2").merge();
summary.getRange("A2:H2").values = [["功能清单、预计实施成本、申报预算及建设周期"]];
summary.getRange("A2:H2").format = {
  fill: C.pale,
  font: { bold: true, color: C.navy, name: "Microsoft YaHei" },
  horizontalAlignment: "center",
};

for (const range of ["A4:B4", "C4:D4", "E4:F4", "G4:H4", "A5:B6", "C5:D6", "E5:F6", "G5:H6"]) {
  summary.getRange(range).merge();
}
summary.getRange("A4").values = [["预计实施成本"]];
summary.getRange("C4").values = [["建议申报金额"]];
summary.getRange("E4").values = [["成本与申报差额"]];
summary.getRange("G4").values = [["预计建设周期"]];
summary.getRange("A4:H4").format = {
  fill: C.blue,
  font: { bold: true, color: C.dark, name: "Microsoft YaHei" },
  horizontalAlignment: "center",
  borders: { preset: "all", style: "thin", color: C.border },
};
summary.getRange("A5").formulas = [["='费用明细'!B12"]];
summary.getRange("C5").formulas = [["='费用明细'!C12"]];
summary.getRange("E5").formulas = [["='费用明细'!D12"]];
summary.getRange("G5").formulas = [["='周期计划'!B10"]];
summary.getRange("A5:H6").format = {
  font: { bold: true, color: C.navy, name: "Microsoft YaHei", size: 15 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "all", style: "thin", color: C.border },
};
summary.getRange("A5:B6").format.fill = C.pale;
summary.getRange("C5:D6").format.fill = C.green;
summary.getRange("E5:F6").format.fill = C.amber;
summary.getRange("G5:H6").format.fill = C.gray;
summary.getRange("A5:F6").format.numberFormat = '0.00" 万元"';
summary.getRange("G5:H6").format.numberFormat = '0.0" 个月"';

setSection(summary, "A8:H8", "一、项目基本情况");
summary.getRange("A9:H13").values = [
  ["项目名称", "临汾路街道业委会智能履职辅助项目", null, null, "建设类型", "新建", null, null],
  ["申报单位", "上海市静安区人民政府临汾路街道办事处", null, null, "建设范围", "全街道，实际业委会数量待核实", null, null],
  ["现状与问题", "目前会议、接待、学习、印章和履职资料主要依靠纸质材料、微信群及人工台账，存在期限提醒不足、纪要整理工作量大、佐证材料分散和年度汇总困难等问题。", null, null, null, null, null, null],
  ["建设目标", "形成覆盖会议、接待、培训、印章和履职档案的统一工作入口，通过智能辅助、规则提醒、材料归集和可信存证，提高规范履职效率。", null, null, null, null, null, null],
  ["建设特点", "以业委会规范化运作评价要求为业务主线，将会议、接待、培训、用印等工作形成可操作流程和完整佐证；重要内容坚持人工确认，年度履职材料自动归集；界面和操作面向业委会成员，强调轻量、易用和可追溯。", null, null, null, null, null, null],
];
summary.getRange("B9:D9").merge();
summary.getRange("F9:H9").merge();
summary.getRange("B10:D10").merge();
summary.getRange("F10:H10").merge();
summary.getRange("B11:H11").merge();
summary.getRange("B12:H12").merge();
summary.getRange("B13:H13").merge();
setGrid(summary.getRange("A9:H13"));
summary.getRange("A9:A13").format = { fill: C.gray, font: { bold: true, name: "Microsoft YaHei" } };
summary.getRange("E9:E10").format = { fill: C.gray, font: { bold: true, name: "Microsoft YaHei" } };

setSection(summary, "A15:H15", "二、建设内容与申报预算分配");
summary.getRange("A16:H16").values = [["类别", "主要内容", null, null, null, "预计成本", "申报预算", "预算占比"]];
summary.getRange("B16:E16").merge();
setHeader(summary.getRange("A16:H16"));
const sumRows = [
  ["平台能力", "基础流程、档案、附件、权限和公共组件", null, null, null, null, null, null],
  ["功能开发", "会议、AI会议助手、接待、培训、印章、履职档案、可信存证、权限安全、提醒统计", null, null, null, null, null, null],
  ["安全及第三方", "密码应用建设、软件测试和密码应用安全性评估", null, null, null, null, null, null],
  ["实施及运维", "部署初始化、用户培训及一年运行维护", null, null, null, null, null, null],
  ["政府承担/不计列", "大模型资源、专用硬件及可信档案馆平台采购", null, null, null, null, null, null],
];
summary.getRange("A17:H21").values = sumRows;
for (let r = 17; r <= 21; r++) summary.getRange(`B${r}:E${r}`).merge();
summary.getRange("F17").formulas = [["='费用明细'!B4"]];
summary.getRange("G17").formulas = [["='费用明细'!C4"]];
summary.getRange("F18").formulas = [["='费用明细'!B5"]];
summary.getRange("G18").formulas = [["='费用明细'!C5"]];
summary.getRange("F19").formulas = [["='费用明细'!B6+'费用明细'!B8"]];
summary.getRange("G19").formulas = [["='费用明细'!C6+'费用明细'!C8"]];
summary.getRange("F20").formulas = [["='费用明细'!B7+'费用明细'!B9"]];
summary.getRange("G20").formulas = [["='费用明细'!C7+'费用明细'!C9"]];
summary.getRange("F21").formulas = [["='费用明细'!B10+'费用明细'!B11"]];
summary.getRange("G21").formulas = [["='费用明细'!C10+'费用明细'!C11"]];
for (let r = 17; r <= 21; r++) summary.getRange(`H${r}`).formulas = [[`=G${r}/'费用明细'!$C$12`]];
setGrid(summary.getRange("A17:H21"));
summary.getRange("F17:G21").format.numberFormat = "0.00";
summary.getRange("H17:H21").format.numberFormat = "0.0%";
summary.getRange("F17:H21").format.horizontalAlignment = "right";

summary.getRange("A23:H24").merge();
summary.getRange("A23:H24").formulas = [[
  `="申报结论：建议按"&TEXT('费用明细'!C12,"0.00")&"万元申报。预计实施成本约"&TEXT('费用明细'!B12,"0.00")&"万元，申报差额用于项目管理、税费、风险、验收整改、质保服务及合理利润。"`
]];
summary.getRange("A23:H24").format = {
  fill: C.green,
  font: { bold: true, color: "#375623", name: "Microsoft YaHei", size: 12 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "outside", style: "medium", color: "#70AD47" },
};
summary.getRange("A:A").format.columnWidth = 12;
summary.getRange("B:E").format.columnWidth = 16;
summary.getRange("F:H").format.columnWidth = 14;
summary.getRange("11:13").format.rowHeight = 46;
summary.getRange("17:21").format.rowHeight = 38;
summary.freezePanes.freezeRows(2);

// 周期计划
schedule.showGridLines = false;
setTitle(schedule, "A1:H1", "预计建设周期与关键节点");
schedule.getRange("A3:H3").values = [["阶段", "周期（月）", "主要工作", "主要交付物", "第1月", "第2-5月", "第6-10月", "第11-12月"]];
setHeader(schedule.getRange("A3:H3"));
schedule.getRange("A4:H8").values = [
  ["项目准备", 1.0, "需求确认、范围细化、预算及采购准备。", "需求清单、实施计划、采购材料", "●", "", "", ""],
  ["设计开发", 4.0, "详细设计、功能开发、智能能力接入、可信档案馆轻量对接及密码应用建设。", "设计文档、阶段版本、接口联调记录", "", "●", "", ""],
  ["部署试点", 3.0, "系统部署、基础数据初始化、用户培训和部分业委会试点。", "部署记录、用户手册、培训记录、试点报告", "", "", "●", ""],
  ["推广优化", 2.0, "根据试点反馈优化，并逐步推广至全街道相关业委会。", "优化版本、推广记录、问题闭环清单", "", "", "●", ""],
  ["测试验收", 2.0, "第三方测试、密评、问题整改、材料整理和项目验收。", "测试报告、密评报告、验收材料", "", "", "", "●"],
];
schedule.getRange("A9").values = [["合计"]];
schedule.getRange("B9").formulas = [["=SUM(B4:B8)"]];
schedule.getRange("C9:H9").merge();
schedule.getRange("C9").values = [["总周期按12个月控制，包含试点、推广优化、第三方测试、密评和验收。"]];
setGrid(schedule.getRange("A4:H9"));
schedule.getRange("A9:H9").format = {
  fill: C.blue,
  font: { bold: true, color: C.dark, name: "Microsoft YaHei" },
  borders: { preset: "all", style: "thin", color: C.border },
};
schedule.getRange("E4:H8").format = {
  fill: C.pale,
  font: { bold: true, color: C.navy, name: "Microsoft YaHei", size: 14 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  borders: { preset: "all", style: "thin", color: C.border },
};
schedule.getRange("B4:B9").format.numberFormat = "0.0";
schedule.getRange("A4:B9").format.horizontalAlignment = "center";
schedule.getRange("A10").values = [["概览引用"]];
schedule.getRange("B10").formulas = [["=B9"]];
schedule.getRange("A10:B10").format = { font: { color: "#7F8C8D", italic: true, name: "Microsoft YaHei" } };
schedule.getRange("A:A").format.columnWidth = 16;
schedule.getRange("B:B").format.columnWidth = 12;
schedule.getRange("C:C").format.columnWidth = 38;
schedule.getRange("D:D").format.columnWidth = 34;
schedule.getRange("E:H").format.columnWidth = 13;
schedule.getRange("4:8").format.rowHeight = 50;
schedule.freezePanes.freezeRows(3);

// Compact verification
for (const range of ["功能及费用!A4:J23", "费用明细!A3:G12", "决策摘要!A4:H24", "周期计划!A3:H10"]) {
  const result = await wb.inspect({
    kind: "table",
    range,
    include: "values,formulas",
    tableMaxRows: 30,
    tableMaxCols: 12,
    maxChars: 18000,
  });
  console.log(result.ndjson);
}
const errors = await wb.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  maxChars: 4000,
  summary: "formula errors",
});
console.log(errors.ndjson);

for (const [sheetName, range, file] of [
  ["决策摘要", "A1:H24", "summary.png"],
  ["功能及费用", "A1:J23", "detail.png"],
  ["费用明细", "A1:G23", "budget.png"],
  ["周期计划", "A1:H10", "schedule.png"],
]) {
  const png = await wb.render({ sheetName, range, scale: 1.15, format: "png" });
  await fs.writeFile(`${previewDir}/${file}`, new Uint8Array(await png.arrayBuffer()));
}

const xlsx = await SpreadsheetFile.exportXlsx(wb);
await xlsx.save(outputPath);
console.log(outputPath);
