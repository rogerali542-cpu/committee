import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';

const file = 'C:/Users/Lequan/Documents/WXWork/1688855966854643/Cache/File/2026-06/副本评分标准及得分表2025定稿-标准分解.xlsx';
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(file));
const sheets = await workbook.inspect({ kind: 'sheet', include: 'id,name', maxChars: 4000 });
console.log('SHEETS');
console.log(sheets.ndjson);
for (const term of ['出席', '参会', '到场', '线上', '签到', '过半', '半数', '会议']) {
  const result = await workbook.inspect({
    kind: 'match',
    searchTerm: term,
    options: { useRegex: false, maxResults: 100 },
    maxChars: 12000,
    summary: `matches for ${term}`,
  });
  console.log(`TERM:${term}`);
  console.log(result.ndjson);
}
