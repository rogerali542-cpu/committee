from copy import deepcopy
from pathlib import Path
from docx import Document

src = Path(r"C:\Users\Lequan\Documents\WXWork\1688855966854643\Cache\File\2026-06\new-app\new-app\.codex-doc-review\working-template.docx")
out = Path(r"C:\Users\Lequan\Documents\WXWork\1688855966854643\Cache\File\2026-06\new-app\new-app\2027年度临汾路街道业委会智能履职辅助项目预算申报表.docx")
doc = Document(src)

def set_para_text(paragraph, text):
    if paragraph.runs:
        first = paragraph.runs[0]
        props = deepcopy(first._r.rPr) if first._r.rPr is not None else None
        for run in list(paragraph.runs):
            paragraph._p.remove(run._r)
        run = paragraph.add_run(text)
        if props is not None:
            run._r.insert(0, props)
    else:
        paragraph.add_run(text)

def set_cell(cell, text):
    paras = cell.paragraphs
    if not paras:
        p = cell.add_paragraph()
    else:
        p = paras[0]
    set_para_text(p, text)
    for extra in list(cell.paragraphs[1:]):
        cell._tc.remove(extra._p)

def unique_cells(row):
    seen = set()
    result = []
    for cell in row.cells:
        key = id(cell._tc)
        if key not in seen:
            seen.add(key)
            result.append(cell)
    return result

set_para_text(doc.paragraphs[0], "2027年度静安区信息化建设项目预算申报表")

# 一、基本情况
t = doc.tables[0]
rows = [unique_cells(r) for r in t.rows]
set_cell(rows[0][1], "上海市静安区人民政府临汾路街道办事处")
set_cell(rows[1][1], "上海市静安区人民政府临汾路街道办事处")
set_cell(rows[2][1], "临汾路街道业委会智能履职辅助项目")
set_cell(rows[2][2], "☑新建  □升级改造")
set_cell(rows[3][1], "待确认")
set_cell(rows[3][3], "待确认")
set_cell(rows[4][1], "28.00（万元）")
set_cell(rows[5][1], "是")
set_cell(rows[5][3], "是")
set_cell(rows[5][5], "否")
summary = (
    "建设面向临汾路街道辖区业主委员会的智能履职辅助应用，围绕业委会会议、业主接待、"
    "业务学习、印章使用和履职档案等工作，提供流程管理、期限提醒、语音转写、纪要初稿生成、"
    "资料归集和风险留痕。会议纪要正式公示时对接街道现有可信档案馆存证，提升业委会履职规范性、"
    "工作效率和材料可追溯性。"
)
set_cell(rows[6][0], "建设目的及主要建设内容（200字以内）\n" + summary)

# 二、现状（新建项目不适用）
t = doc.tables[1]
for ri, row in enumerate(t.rows):
    cells = unique_cells(row)
    if len(cells) > 1:
        if ri == 0:
            set_cell(cells[1], "本项目为新建项目，本栏不适用。")
        elif ri == 1:
            set_cell(cells[1], "无现有正式立项建设的信息系统。前期仅开展业务需求梳理和应用原型验证。")
        elif ri == 2:
            set_cell(cells[1], "第三方评估报告：□有  ☑无")

# 三、项目建设依据
t = doc.tables[2]
labels = [
    "□本区信息化专项规划和实施计划",
    "□国家和市行业规划配套和实施计划",
    "☑各部门的发展规划和实施计划",
    "□区级层面的领导讲话、书面批示、会议纪要和相关文件",
    "□新成立机构或部门",
    "□单位搬迁",
    "□机构或部门职能增加或调整",
    "□其他",
]
for row, label in zip(t.rows, labels):
    set_cell(unique_cells(row)[0], label)

# 四、需求分析
t = doc.tables[3]
gap = (
    "目前辖区业主委员会在会议通知、过程记录、纪要整理、公示留痕、业主接待、学习培训和印章使用等方面，"
    "主要依靠纸质材料、微信群和人工台账，资料分散、格式不一，容易出现期限提醒不足、记录要素缺失、"
    "年度材料统计困难等问题。会议录音整理和纪要编写工作量较大，正式公示材料缺少统一的可信存证和验真方式。"
    "需要建设统一、轻量、易用的智能履职辅助应用，对关键工作形成规范流程、智能提醒和电子档案。"
)
set_cell(unique_cells(t.rows[0])[0], "现有信息系统与实际业务需求之间的差距：\n" + gap)

# 五、建设方案与预算
t = doc.tables[4]
module_text = (
    "主要需要实现的模块及主要功能：（特别提示：需在附件中另附建设方案，见附件信息化项目建设方案编制大纲）\n"
    "1. 业委会会议智能辅助\n"
    "支持业委会会议创建、议题和材料管理、提前7天通知提醒、通知送达记录、居委会列席记录、录音转写、"
    "纪要初稿生成、表决结果登记、待办提取和会后3日公示提醒。线下会议生成签到及表决签字表，签字后上传归档；"
    "线上会议由主任负责程序把关并填写表决结果。重大事项记录居委会列席和相关佐证。正式公示时对接可信档案馆存证。\n"
    "2. 业主接待与反馈闭环\n"
    "登记接待时间、地点、人员、业主诉求和处理结果；涉及物业管理改进的问题，记录向物业反馈及回复材料，"
    "并记录向业主反馈的结果，自动统计年度接待次数。\n"
    "3. 政策学习与业务培训\n"
    "管理内部学习、街镇培训和专项培训，记录主题、时间、参加人员及职务，上传签到表、培训材料和照片，"
    "统计年度学习次数及重点人员参训情况，提供政策资料智能检索和问答。\n"
    "4. 印章使用登记与风险留痕\n"
    "登记不同印章及保管委员，记录用印时间、用途、文件、申请人和保管人确认，上传签字登记表或盖章文件，"
    "形成可查询、可导出的用印台账。本期不包含智能印章硬件。\n"
    "5. 履职档案与可信存证\n"
    "分类归集会议、接待、培训、用印和公示材料，形成年度履职电子档案；对正式公示的会议纪要对接临汾路街道"
    "现有可信档案馆进行存证和验真。"
)
set_cell(unique_cells(t.rows[0])[0], module_text)

# 租赁硬件：无
set_cell(unique_cells(t.rows[3])[0], "1")
set_cell(unique_cells(t.rows[3])[1], "无")
set_cell(unique_cells(t.rows[3])[2], "本期不租赁专用硬件")
set_cell(unique_cells(t.rows[3])[3], "0")
set_cell(unique_cells(t.rows[3])[4], "0")
set_cell(unique_cells(t.rows[3])[5], "0")
set_cell(unique_cells(t.rows[4])[1], "0")

# 产品软件/服务
products = [
    ("业委会会议智能辅助", "会议流程、语音转写、纪要初稿、期限提醒和材料归档", "4", "1", "4"),
    ("业主接待与反馈管理", "接待登记、物业反馈留痕、业主反馈和年度统计", "2", "1", "2"),
    ("政策学习与智能知识服务", "学习培训记录、政策资料检索和智能问答", "2", "1", "2"),
    ("印章使用登记", "印章保管、用印记录、保管人确认和台账导出", "1", "1", "1"),
    ("履职档案与可信存证", "履职材料归档、可信档案馆存证及验真", "4", "1", "4"),
    ("模型及语音资源服务", "大模型、语音识别、存储等一年资源服务", "2", "1", "2"),
]
for idx, row_idx in enumerate(range(7, 13)):
    vals = products[idx]
    cells = unique_cells(t.rows[row_idx])
    set_cell(cells[0], str(idx + 1))
    for ci, value in enumerate(vals):
        set_cell(cells[ci + 1], value)
set_cell(unique_cells(t.rows[13])[1], "15")

# 应用软件开发
devs = [
    ("会议履职管理", "会议流程配置", "会议创建、通知送达、列席、表决、公示及归档流程", "1", "1", "1"),
    ("会议履职管理", "时限提醒", "提前7天通知、会后3日公示及年度会议次数提醒", "1", "1", "1"),
    ("AI会议助手", "录音转写与纪要", "录音转写、议题整理、纪要初稿和待办提取", "1", "2", "2"),
    ("接待管理", "反馈闭环", "接待登记、物业沟通记录、业主反馈和附件留痕", "1", "1", "1"),
    ("学习培训", "培训记录", "学习、培训、参训人员和年度次数统计", "1", "1", "1"),
    ("印章管理", "用印台账", "印章保管人、用印记录、确认及附件归档", "1", "1", "1"),
    ("履职档案", "分类归档", "按年度和工作类型归档、查询及导出", "1", "1", "1"),
    ("可信存证", "档案馆接口", "公示纪要推送、回执保存和一键验真", "1", "1", "1"),
    ("权限与安全", "角色权限", "成员权限、敏感信息保护和操作留痕", "1", "1", "1"),
]
for idx, row_idx in enumerate(range(16, 25)):
    cells = unique_cells(t.rows[row_idx])
    set_cell(cells[0], str(idx + 1))
    for ci, value in enumerate(devs[idx]):
        set_cell(cells[ci + 1], value)
set_cell(unique_cells(t.rows[25])[1], "10")

# 密码模块不单列
cells = unique_cells(t.rows[28])
for ci, value in enumerate(("1", "本期不单列密码功能模块", "0", "0", "0")):
    set_cell(cells[ci], value)
set_cell(unique_cells(t.rows[29])[1], "0")

# 其他费用：本期费用含在软件与实施报价内，避免重复计价
others = [
    ("系统部署与初始化", "1"),
    ("软件测试与验收支持", "0.8"),
    ("用户培训与操作手册", "0.6"),
    ("一年运行维护服务", "0.6"),
    ("安全配置与数据备份", "0"),
    ("其他", "0"),
]
for idx, row_idx in enumerate(range(32, 38)):
    cells = unique_cells(t.rows[row_idx])
    set_cell(cells[0], str(idx + 1))
    set_cell(cells[1], others[idx][0])
    set_cell(cells[2], others[idx][1])
set_cell(unique_cells(t.rows[38])[1], "3")
set_cell(unique_cells(t.rows[39])[1], "28")

# 六、建设周期
t = doc.tables[5]
schedule = (
    "项目建设周期预计为6个月。\n"
    "1、项目准备阶段（0.5个月）：完成需求确认、建设内容细化、资金落实及采购准备。\n"
    "2、设计开发阶段（2.5个月）：完成系统设计、功能开发、AI能力接入及可信档案馆接口开发。\n"
    "3、部署试用阶段（2个月）：完成系统部署、基础资料初始化、用户培训和辖区推广试用，并根据反馈优化。\n"
    "4、验收阶段（1个月）：完成软件测试、材料整理、问题整改和项目验收。"
)
set_cell(unique_cells(t.rows[0])[0], "项目建设的关键节点（图或者文字）\n" + schedule)

doc.save(out)
print(out)
