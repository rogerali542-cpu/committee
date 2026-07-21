# DOCX template execution contract

- Reference: `C:\Users\Lequan\Desktop\20250829_2026年度静安区信息化建设项目预算申报表.docx`
- Structure: one A4 portrait section; 1.25 inch left/right margins and 1 inch top/bottom margins.
- Content flow: title, seven numbered headings, and seven corresponding tables.
- Fidelity requirements: preserve the source title/heading typography, page geometry, table borders, merged-cell patterns, approval block, and overall section order.
- Editable slots: title year; basic information table; new-project status text; project basis selection; needs analysis; construction方案 narrative and all budget rows; construction-period narrative.
- Preserve-only slots: section geometry, table grid and borders, heading labels, approval signature table.
- Content source: `outputs\project-budget-estimate\临汾路街道业委会智能履职辅助项目建设内容及预算测算表.xlsx`.
- Numeric source of truth: expected implementation cost 22.20万元; proposed application budget 42.50万元; construction period 6 months.
- Visual gate: render all pages through installed Microsoft Word, inspect every page image, and correct clipping, broken rows, or poor pagination.
