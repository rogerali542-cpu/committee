import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";
import fs from "node:fs/promises";

const inputPath = "C:/Users/Lequan/Documents/WXWork/1688855966854643/Cache/File/2026-06/副本评分标准及得分表2025定稿-标准分解.xlsx";
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(inputPath));

const sheets = await workbook.inspect({
  kind: "sheet",
  include: "id,name",
  maxChars: 12000,
});
console.log("=== SHEETS ===");
console.log(sheets.ndjson);

for (const term of ["会议", "接待", "培训", "学习", "印章"]) {
  const result = await workbook.inspect({
    kind: "match",
    searchTerm: term,
    options: { useRegex: false, maxResults: 200 },
    maxChars: 30000,
    summary: `matches for ${term}`,
  });
  console.log(`=== MATCH ${term} ===`);
  console.log(result.ndjson);
}

for (const range of ["标准解读!A1:K11", "标准解读!A14:K22"]) {
  const result = await workbook.inspect({
    kind: "table",
    range,
    include: "values,formulas",
    tableMaxRows: 30,
    tableMaxCols: 11,
    tableMaxCellChars: 2000,
    maxChars: 50000,
  });
  console.log(`=== RANGE ${range} ===`);
  console.log(result.ndjson);
}

await fs.mkdir(".codex-sheet-review/previews", { recursive: true });
const preview = await workbook.render({
  sheetName: "标准解读",
  range: "A1:K22",
  scale: 1,
  format: "png",
});
await fs.writeFile(
  ".codex-sheet-review/previews/标准解读-A1-K22.png",
  new Uint8Array(await preview.arrayBuffer()),
);
