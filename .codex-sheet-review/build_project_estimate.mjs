import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outDir = "outputs/project-budget-estimate";
const outPath = `${outDir}/临汾路街道业委会智能履职辅助项目-功能清单及预算测算.xlsx`;
await fs.mkdir(outDir, { recursive: true });
await fs.mkdir(".codex-sheet-review/project-previews", { recursive: true });

const wb = Workbook.create();
const overview = wb.worksheets.add("项目概览");
const features = wb.worksheets.add("功能清单");
const budget = wb.worksheets.add("成本预算");
const schedule = wb.worksheets.add("周期计划");

const navy = "#1F4E78";
const blue = "#D9EAF7";
const paleBlue = "#EEF6FB";
const green = "#E2F0D9";
const amber = "#FFF2CC";
const light = "#F5F7FA";
const border = "#B7C3D0";
const dark = "#203040";
const white = "#FFFFFF";

function title(sheet, range, text) {
  sheet.getRange(range).merge();
  const r = sheet.getRange(range);
  r.values = [[text]];
  r.format = {
    fill: navy,
    font: { bold: true, color: white, size: 16 },
    horizontalAlignment: "center",
    verticalAlignment: "center",
  };
  r.format.rowHeight = 34;
}

function section(sheet, range, text) {
  sheet.getRange(range).merge();
  const r = sheet.getRange(range);
  r.values = [[text]];
  r.format = {
    fill: blue,
    font: { bold: true, color: dark, size: 11 },
    verticalAlignment: "center",
  };
  r.format.rowHeight = 24;
}

function header(range) {
  range.format = {
    fill: navy,
    font: { bold: true, color: white },
    horizontalAlignment: "center",
    verticalAlignment: "center",
    wrapText: true,
    borders: { preset: "all", style: "thin", color: border },
  };
}

function grid(range) {
  range.format = {
    verticalAlignment: "center",
    wrapText: true,
    borders: { preset: "all", style: "thin", color: border },
  };
}

// 项目概览
overview.showGridLines = false;
title(overview, "A1:H1", "临汾路街道业委会智能履职辅助项目");
overview.getRange("A2:H2").merge();
overview.getRange("A2:H2").values = [["功能清单、预计成本与预计周期（领导决策版）"]];
overview.getRange("A2:H2").format = {
  fill: paleBlue,
  font: { bold: true, color: navy, size: 11 },
  horizontalAlignment: "center",
};

overview.getRange("A4:B4").merge();
overview.getRange("C4:D4").merge();
overview.getRange("E4:F4").merge();
overview.getRange("G4:H4").merge();
overview.getRange("A4").values = [["建议申报金额"]];
overview.getRange("C4").values = [["压缩控制金额"]];
overview.getRange("E4").values = [["预计建设周期"]];
overview.getRange("G4").values = [["建设范围"]];
overview.getRange("A4:H4").format = {
  fill: blue,
  font: { bold: true, color: dark },
  horizontalAlignment: "center",
  borders: { preset: "all", style: "thin", color: border },
};
overview.getRange("A5:B6").merge();
overview.getRange("C5:D6").merge();
overview.getRange("E5:F6").merge();
overview.getRange("G5:H6").merge();
overview.getRange("A5").formulas = [["='成本预算'!C15"]];
overview.getRange("C5").formulas = [["='成本预算'!D15"]];
overview.getRange("E5").formulas = [["='周期计划'!B9"]];
overview.getRange("G5").values = [["临汾路街道辖区居民区及符合条件的业主委员会"]];
overview.getRange("A5:H6").format = {
  font: { bold: true, color: navy, size: 14 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "all", style: "thin", color: border },
};
overview.getRange("A5:B6").format.fill = green;
overview.getRange("C5:D6").format.fill = amber;
overview.getRange("E5:F6").format.fill = paleBlue;
overview.getRange("G5:H6").format.fill = light;
overview.getRange("A5:D6").format.numberFormat = '0.0" 万元"';
overview.getRange("E5:F6").format.numberFormat = '0.0" 个月"';

section(overview, "A8:H8", "一、项目基本情况");
overview.getRange("A9:H13").values = [
  ["项目名称", "临汾路街道业委会智能履职辅助项目", null, null, "建设类型", "新建", null, null],
  ["申报单位", "上海市静安区人民政府临汾路街道办事处", null, null, "主要用户", "辖区业委会主任、副主任、委员及记录人员", null, null],
  ["建设目的", "围绕业委会会议、业主接待、业务学习、印章使用和履职档案等工作，提供规范流程、智能辅助、期限提醒、材料归集和风险留痕。", null, null, null, null, null, null],
  ["建设特点", "不建设业主大会功能；大模型资源及调用消耗由政府统一承担；会议纪要正式公示时对接临汾路街道现有可信档案馆存证。", null, null, null, null, null, null],
  ["申报建议", "优先按46万元申报；若明确要求不超过40万元，采用压缩方案，保留会议、接待、培训、印章、可信存证和密码合规等核心范围。", null, null, null, null, null, null],
];
overview.getRange("B9:D9").merge();
overview.getRange("F9:H9").merge();
overview.getRange("B10:D10").merge();
overview.getRange("F10:H10").merge();
overview.getRange("B11:H11").merge();
overview.getRange("B12:H12").merge();
overview.getRange("B13:H13").merge();
grid(overview.getRange("A9:H13"));
overview.getRange("A9:A13").format = { fill: light, font: { bold: true, color: dark }, verticalAlignment: "center" };
overview.getRange("E9:E10").format = { fill: light, font: { bold: true, color: dark }, verticalAlignment: "center" };

section(overview, "A15:H15", "二、核心功能模块");
overview.getRange("A16:H16").values = [["序号", "功能模块", "主要内容", null, null, null, "优先级", "预算口径"]];
overview.getRange("C16:F16").merge();
header(overview.getRange("A16:H16"));
const overviewModules = [
  [1, "业委会会议智能辅助", "会议创建、提前7天通知提醒、送达、居委会列席、线下签字表、线上结果登记、录音转写、纪要初稿、会后3日公示提醒。", null, null, null, "核心", "保留"],
  [2, "业主接待与反馈闭环", "接待制度和记录、物业沟通留痕、向业主反馈、年度接待次数统计。", null, null, null, "核心", "保留"],
  [3, "政策学习与业务培训", "内部学习、街镇培训、重点人员参训、资料上传、年度次数统计和政策智能检索。", null, null, null, "重要", "轻量建设"],
  [4, "印章使用登记", "印章及保管人、用印用途和时间、保管委员确认、签字登记表及盖章文件归档。", null, null, null, "重要", "轻量建设"],
  [5, "履职档案与可信存证", "会议、接待、培训、用印材料分类归档；公示纪要推送可信档案馆并保存回执和验真。", null, null, null, "核心", "保留"],
  [6, "权限、安全与密码应用", "成员权限、敏感信息保护、操作日志、密码应用建设及密评配合。", null, null, null, "核心", "保留"],
];
overview.getRange("A17:H22").values = overviewModules;
for (let r = 17; r <= 22; r++) overview.getRange(`C${r}:F${r}`).merge();
grid(overview.getRange("A17:H22"));
overview.getRange("A17:A22").format.horizontalAlignment = "center";
overview.getRange("G17:H22").format.horizontalAlignment = "center";
overview.getRange("G17:G22").format.fill = paleBlue;
overview.getRange("H17:H22").format.fill = light;

overview.getRange("A24:H25").merge();
overview.getRange("A24:H25").values = [[
  "待确认事项：实际覆盖的业委会数量；负责人和联系方式；部署环境；是否需单独开展等保测评；可信档案馆接口是否提供测试环境；平台基础能力能否提供软件产品或授权依据。"
]];
overview.getRange("A24:H25").format = {
  fill: amber,
  font: { color: "#7F6000" },
  wrapText: true,
  verticalAlignment: "center",
  borders: { preset: "outside", style: "thin", color: "#D6B656" },
};

overview.getRange("A1:H25").format.font.name = "Microsoft YaHei";
overview.getRange("A:A").format.columnWidth = 10;
overview.getRange("B:B").format.columnWidth = 23;
overview.getRange("C:F").format.columnWidth = 14;
overview.getRange("G:G").format.columnWidth = 12;
overview.getRange("H:H").format.columnWidth = 15;
overview.getRange("9:10").format.rowHeight = 30;
overview.getRange("11:13").format.rowHeight = 44;
overview.getRange("17:22").format.rowHeight = 48;
overview.freezePanes.freezeRows(2);

// 功能清单
features.showGridLines = false;
title(features, "A1:I1", "项目功能清单与工作量测算");
features.getRange("A3:I3").values = [[
  "序号", "一级模块", "二级功能", "功能说明", "关键规则/佐证", "优先级",
  "标准版人月", "压缩版人月", "压缩处理方式"
]];
header(features.getRange("A3:I3"));
const featureRows = [
  [1, "会议履职管理", "会议流程", "会议创建、议题和材料、通知送达、居委会列席、表决结果、公示和归档。", "提前7天通知；重大事项记录居委会列席；仅业委会会议。", "核心", 2, 2, "不压缩"],
  [2, "AI会议助手", "录音转写与纪要", "录音上传、语音转写、议题整理、纪要初稿和待办提取，正式内容人工审核。", "大模型调用由政府承担；线下打印签字表，线上由主任登记结果。", "核心", 2, 2, "不压缩"],
  [3, "接待管理", "反馈闭环", "登记接待事项、物业沟通记录、物业回复和向业主反馈结果。", "年度12次记录；保留物业沟通和业主反馈佐证。", "核心", 1, 1, "不压缩"],
  [4, "学习培训", "培训记录", "内部学习、街镇培训、重点人员参训、附件和年度次数统计。", "内部学习不少于2次；记录主任、副主任及印章保管委员参训。", "重要", 1, 0.75, "复用资料库和附件能力"],
  [5, "印章管理", "用印台账", "印章及保管人、使用时间、用途、保管人确认、签字表和盖章文件归档。", "不同印章分别保管；保管委员签字或确认。", "重要", 1, 0.75, "不接智能印章硬件"],
  [6, "履职档案", "分类归档", "按年度和工作类型归档、查询、导出会议、接待、培训和用印资料。", "便于规范化运作评估和年度材料汇总。", "核心", 1.5, 1.25, "减少高级检索和批量配置"],
  [7, "可信存证", "档案馆接口", "公示纪要推送、回执保存和一键验真。", "对接街道现有可信档案馆；不重复采购平台。", "核心", 1.5, 1, "仅对接纪要存证"],
  [8, "权限与安全", "角色权限", "成员权限、敏感信息保护、操作日志和数据隔离。", "密码应用建设2万元，密评1万元另列。", "核心", 1.5, 1.25, "复用平台通用权限"],
  [9, "提醒与统计", "规则提醒", "会议通知、公示、年度会议次数、接待和培训规则统计。", "提前7天通知、会后3日公示等规则。", "核心", 2, 2, "不压缩"],
];
features.getRange("A4:I12").values = featureRows;
features.getRange("A13:F13").merge();
features.getRange("A13").values = [["工作量合计"]];
features.getRange("G13").formulas = [["=SUM(G4:G12)"]];
features.getRange("H13").formulas = [["=SUM(H4:H12)"]];
features.getRange("I13").values = [[""]];
grid(features.getRange("A4:I13"));
features.getRange("A13:I13").format = { fill: blue, font: { bold: true, color: dark }, borders: { preset: "all", style: "thin", color: border } };
features.getRange("A4:A12").format.horizontalAlignment = "center";
features.getRange("F4:H13").format.horizontalAlignment = "center";
features.getRange("G4:H13").format.numberFormat = '0.00';
features.getRange("A1:I13").format.font.name = "Microsoft YaHei";
features.getRange("A:A").format.columnWidth = 7;
features.getRange("B:B").format.columnWidth = 16;
features.getRange("C:C").format.columnWidth = 18;
features.getRange("D:D").format.columnWidth = 36;
features.getRange("E:E").format.columnWidth = 31;
features.getRange("F:F").format.columnWidth = 10;
features.getRange("G:H").format.columnWidth = 12;
features.getRange("I:I").format.columnWidth = 23;
features.getRange("4:12").format.rowHeight = 54;
features.freezePanes.freezeRows(3);

// 成本预算
budget.showGridLines = false;
title(budget, "A1:F1", "成本测算与申报金额建议");
budget.getRange("A3:F3").values = [["序号", "费用类别", "标准版（万元）", "压缩版（万元）", "压缩差额", "测算说明"]];
header(budget.getRange("A3:F3"));
budget.getRange("A4:F14").values = [
  [1, "平台基础能力及软件授权", 8, 6, null, "业委会多角色、基础流程、档案及权限等通用能力；需准备软件产品或授权依据。"],
  [2, "应用软件适配与完善开发", null, null, null, "按功能清单工作量×2万元/人月测算。"],
  [3, "密码应用建设", 2, 2, null, "身份认证、传输保护、敏感数据保护和日志完整性等。"],
  [4, "系统部署与初始化", 2, 1.5, null, "部署、环境配置、基础数据初始化。"],
  [5, "用户培训与操作手册", 1, 0.5, null, "压缩版合并培训场次，保留手册和一次集中培训。"],
  [6, "第三方软件测试及验收支持", 2, 2, null, "不建议取消，含测试和验收整改支持。"],
  [7, "密码应用安全性评估", 1, 1, null, "参照去年同类项目口径。"],
  [8, "一年运行维护服务", 3, 3, null, "故障处理、升级、安全修复、备份及用户支持。"],
  [9, "大模型及政府统一资源", 0, 0, null, "大模型调用及相关消耗由政府统一承担。"],
  [10, "硬件及可信档案馆平台采购", 0, 0, null, "本期不采购专用硬件；可信档案馆已在使用，仅计接口开发。"],
  [11, "其他", 0, 0, null, "暂不计列。"],
];
budget.getRange("C5").formulas = [["='功能清单'!G13*2"]];
budget.getRange("D5").formulas = [["='功能清单'!H13*2"]];
for (let r = 4; r <= 14; r++) budget.getRange(`E${r}`).formulas = [[`=C${r}-D${r}`]];
budget.getRange("A15:B15").merge();
budget.getRange("A15").values = [["项目总预算"]];
budget.getRange("C15").formulas = [["=SUM(C4:C14)"]];
budget.getRange("D15").formulas = [["=SUM(D4:D14)"]];
budget.getRange("E15").formulas = [["=C15-D15"]];
budget.getRange("F15").values = [["建议优先申报标准版；如被要求不超过40万元，采用压缩版。"]];
grid(budget.getRange("A4:F15"));
budget.getRange("A15:F15").format = {
  fill: navy,
  font: { bold: true, color: white, size: 12 },
  borders: { preset: "all", style: "thin", color: border },
  verticalAlignment: "center",
};
budget.getRange("C4:E15").format.numberFormat = '0.0';
budget.getRange("A4:A14").format.horizontalAlignment = "center";
budget.getRange("C4:E15").format.horizontalAlignment = "right";
budget.getRange("D4:D15").format.fill = amber;
budget.getRange("C15:E15").format.fill = navy;

section(budget, "A17:F17", "压缩原则");
budget.getRange("A18:F21").merge(true);
budget.getRange("A18:F21").values = [
  ["1. 不删除会议、接待、培训、印章、可信存证和密码合规等核心功能。"],
  ["2. 主要压缩平台授权、重复开发、部署和培训组织成本。"],
  ["3. 不压缩大模型费用，因为该项本来由政府统一承担，预算为0。"],
  ["4. 不建议继续压缩第三方测试、密评和一年运维，否则会影响正式交付和验收。"],
];
budget.getRange("A18:F21").format = {
  fill: light,
  wrapText: true,
  verticalAlignment: "center",
  borders: { preset: "all", style: "thin", color: border },
};
budget.getRange("A1:F21").format.font.name = "Microsoft YaHei";
budget.getRange("A:A").format.columnWidth = 7;
budget.getRange("B:B").format.columnWidth = 28;
budget.getRange("C:E").format.columnWidth = 16;
budget.getRange("F:F").format.columnWidth = 50;
budget.getRange("4:14").format.rowHeight = 36;
budget.getRange("18:21").format.rowHeight = 28;
budget.freezePanes.freezeRows(3);

// 周期计划
schedule.showGridLines = false;
title(schedule, "A1:H1", "预计建设周期与关键节点");
schedule.getRange("A3:H3").values = [["阶段", "周期（月）", "主要工作", "主要交付物", "第1月", "第2-3月", "第4-5月", "第6月"]];
header(schedule.getRange("A3:H3"));
schedule.getRange("A4:H7").values = [
  ["项目准备", 0.5, "需求确认、范围细化、预算及采购准备。", "需求清单、实施计划、采购材料", "●", "", "", ""],
  ["设计开发", 2.5, "系统设计、功能开发、AI能力接入、可信档案馆接口和密码应用建设。", "设计文档、可运行版本、接口联调记录", "●", "●", "", ""],
  ["部署试用", 2, "部署、初始化、培训、辖区推广试用及反馈优化。", "部署记录、用户手册、培训记录、试运行报告", "", "", "●", ""],
  ["测试验收", 1, "第三方测试、密评、问题整改、验收材料整理和项目验收。", "测试报告、密评报告、验收材料", "", "", "", "●"],
];
schedule.getRange("A8:A8").merge();
schedule.getRange("A8").values = [["合计"]];
schedule.getRange("B8").formulas = [["=SUM(B4:B7)"]];
schedule.getRange("C8:H8").merge();
schedule.getRange("C8").values = [["总建设周期按6个月控制，压缩预算不压缩验收和试运行周期。"]];
grid(schedule.getRange("A4:H8"));
schedule.getRange("A8:H8").format = { fill: blue, font: { bold: true, color: dark }, borders: { preset: "all", style: "thin", color: border } };
schedule.getRange("B4:B8").format.numberFormat = '0.0';
schedule.getRange("A4:B8").format.horizontalAlignment = "center";
schedule.getRange("E4:H7").format = {
  fill: paleBlue,
  font: { bold: true, color: navy, size: 14 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  borders: { preset: "all", style: "thin", color: border },
};
schedule.getRange("A9").values = [["预计周期（用于概览引用）"]];
schedule.getRange("B9").formulas = [["=B8"]];
schedule.getRange("A9:B9").format = { font: { color: "#7F8C8D", italic: true }, numberFormat: '0.0' };
schedule.getRange("A1:H9").format.font.name = "Microsoft YaHei";
schedule.getRange("A:A").format.columnWidth = 16;
schedule.getRange("B:B").format.columnWidth = 12;
schedule.getRange("C:C").format.columnWidth = 38;
schedule.getRange("D:D").format.columnWidth = 34;
schedule.getRange("E:H").format.columnWidth = 13;
schedule.getRange("4:7").format.rowHeight = 50;
schedule.freezePanes.freezeRows(3);

const inspections = [];
inspections.push((await wb.inspect({
  kind: "table",
  range: "成本预算!A3:F15",
  include: "values,formulas",
  tableMaxRows: 20,
  tableMaxCols: 8,
  maxChars: 16000,
})).ndjson);
inspections.push((await wb.inspect({
  kind: "table",
  range: "功能清单!A3:I13",
  include: "values,formulas",
  tableMaxRows: 20,
  tableMaxCols: 12,
  maxChars: 16000,
})).ndjson);
const errors = await wb.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  maxChars: 4000,
  summary: "formula errors",
});
console.log(inspections.join("\n"));
console.log(errors.ndjson);

for (const [sheetName, range, filename] of [
  ["项目概览", "A1:H25", "overview.png"],
  ["功能清单", "A1:I13", "features.png"],
  ["成本预算", "A1:F21", "budget.png"],
  ["周期计划", "A1:H9", "schedule.png"],
]) {
  const png = await wb.render({ sheetName, range, scale: 1.2, format: "png" });
  await fs.writeFile(`.codex-sheet-review/project-previews/${filename}`, new Uint8Array(await png.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(wb);
await output.save(outPath);
console.log(outPath);
