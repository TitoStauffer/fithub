// Lê a planilha 1 da TACO (o .xlsx já descompactado) como linhas de células, sem dependências (ADR-011).
// Uso: node tools/taco/read-sheet.cjs <pasta descompactada> [linha inicial] [linha final]
const fs = require('fs');
const dir = process.argv[2];
const decode = (s) =>
  s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');
const sharedXml = fs.readFileSync(dir + '/xl/sharedStrings.xml', 'utf8');
const shared = [...sharedXml.matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) =>
  decode([...m[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((t) => t[1]).join('')),
);
const sheet = fs.readFileSync(dir + '/xl/worksheets/sheet1.xml', 'utf8');
const col = (ref) => ref.replace(/\d+/g, '').split('').reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0) - 1;
const rows = [];
for (const row of sheet.matchAll(/<row [^>]*r="(\d+)"[^>]*>([\s\S]*?)<\/row>/g)) {
  const cells = [];
  for (const c of row[2].matchAll(/<c r="([A-Z]+\d+)"([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
    const attrs = c[2];
    const inner = c[3] || '';
    const v = (inner.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
    const is = (inner.match(/<t[^>]*>([\s\S]*?)<\/t>/) || [])[1];
    let value = v === undefined ? (is === undefined ? '' : decode(is)) : v;
    if (/t="s"/.test(attrs) && v !== undefined) value = shared[Number(v)];
    cells[col(c[1])] = value;
  }
  rows.push({ r: Number(row[1]), cells: Array.from(cells, (x) => (x === undefined ? '' : String(x).trim())) });
}
module.exports = rows;
if (require.main === module) {
  const from = Number(process.argv[3] || 1);
  const to = Number(process.argv[4] || 15);
  for (const row of rows.filter((x) => x.r >= from && x.r <= to)) console.log(row.r, JSON.stringify(row.cells.slice(0, 14)));
  console.log('linhas:', rows.length);
}
