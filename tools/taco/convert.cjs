// Converte a planilha 1 da TACO (4ª ed.) para o CSV versionado do nutrition-service (DEV-0075).
// Regras da legenda da própria planilha: "Tr" (traço) vira 0; "NA" (não aplicável) vira 0; "*" (em reavaliação) e
// vazio (não analisado) ficam ausentes; sem energia ou sem um dos três macros, o item fica de fora (relatado).
// Uso: node tools/taco/convert.cjs <pasta do .xlsx descompactado> <CSV de saída>
const fs = require('fs');
const rows = require('./read-sheet.cjs');

// Pratos prontos: só os conferidos um a um, pelo número da TACO, entram como veganos.
const READY_MEALS = 'Outros alimentos industrializados';
const READY_MEALS_VEGAN = new Set(['520', '521', '546', '549', '552']);
// Veganos que o nome engana (couve-manteiga, caqui-chocolate, tofu, torrada, caldo de cana...), conferidos um a um.
const VEGAN_BY_NUMBER = new Set(['19', '62', '115', '116', '122', '189', '473', '584', '587', '588']);
const ANIMAL = new Set(['Pescados e frutos do mar', 'Carnes e derivados', 'Leite e derivados', 'Ovos e derivados']);
// Ingredientes de origem animal (ou que costumam levar leite, ovo ou gordura animal), procurados na descrição.
const NOT_VEGAN = [
  'leite', 'queijo', 'ovo', 'manteiga', 'carne', 'frango', 'galinha', 'bacon', 'presunto', 'mel', 'iogurte', 'creme',
  'nata', 'requeijão', 'peixe', 'atum', 'sardinha', 'camarão', 'bacalhau', 'gelatina', 'chocolate', 'biscoito', 'bolo',
  'pão', 'torrada', 'pizza', 'lasanha', 'panqueca', 'mingau', 'pamonha', 'farofa', 'maionese', 'margarina',
  'doce de leite', 'brigadeiro', 'pudim', 'sorvete', 'chantilly', 'cappuccino', 'achocolatado', 'caldo', 'tempero',
  'hambúrguer', 'nugget', 'empada', 'coxinha', 'quibe', 'pastel', 'esfiha', 'acarajé', 'vatapá', 'feijoada', 'tutu',
  'baião', 'virado', 'salgadinho', 'cuscuz, de milho, cozido com sal', 'mortadela', 'salsicha', 'linguiça', 'toucinho',
  'banha', 'sopa', 'macarrão, instantâneo', 'com ovos', 'quindim', 'cocada', 'paçoca', 'goiabada', 'canjica',
  'curau', 'polenta', 'strogonoff', 'risoto', 'omelete', 'manjar', 'mousse', 'bombom', 'barra de cereal',
  'cereal matinal', 'granola', 'wafer', 'rosquinha', 'croissant', 'salame', 'patê', 'molho', 'pururuca', 'torresmo',
  'dobradinha', 'buchada', 'mocotó', 'vitamina', 'fórmula', 'shake', 'suplemento', 'cheeseburger', 'misto', 'sanduíche',
  // Achados na revisão item a item: gelatina, leite, ovo ou manteiga na receita usual.
  'maria mole', 'capuccino', 'farinha, láctea', 'nhoque', 'sauté', 'farinha, de rosca',
];

// Palavra inteira ("mel" não casa com "melancia"); expressão com vírgula ou espaço, por trecho.
const mentions = (text, word) =>
  /[ ,]/.test(word) ? text.includes(word) : text.split(/[^a-zà-ú-]+/).some((token) => token === word || token === word + "s");
const number = (raw) => {
  const v = (raw || '').trim();
  if (v === 'Tr' || v === 'NA') return 0;
  if (v === '' || v === '*') return undefined;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
};
const round = (n, d) => (n === undefined ? '' : (Math.round(n * 10 ** d) / 10 ** d).toFixed(d));
const csvField = (s) => (/[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s);

let category = '';
const out = [['taco_number', 'name', 'category', 'base_unit', 'energy_kcal', 'protein_g', 'carbs_g', 'fat_g', 'fiber_g', 'vegan']];
const excluded = [];
const problems = [];
for (const { cells } of rows) {
  const first = cells[0] || '';
  if (first && !/^\d+$/.test(first) && !cells[1]) {
    if (!/^Número|^Alimento|^Legenda|^[*†]/.test(first)) category = first;
    continue;
  }
  if (!/^\d+$/.test(first)) continue;
  const name = cells[1].replace(/\s+/g, ' ').trim();
  const kcal = number(cells[3]);
  const protein = number(cells[5]);
  const fat = number(cells[6]);
  const carbs = number(cells[8]);
  const fiber = number(cells[9]);
  if ([kcal, protein, fat, carbs].some((v) => v === undefined)) excluded.push(`${first} ${name}`);
  const lower = name.toLowerCase();
  if (name.length < 2) excluded.push(`${first} ${name} (descrição inválida na planilha)`);
  const vegan =
    VEGAN_BY_NUMBER.has(first) ||
    (category === READY_MEALS
      ? READY_MEALS_VEGAN.has(first)
      : !ANIMAL.has(category) && !NOT_VEGAN.some((word) => mentions(lower, word)));
  const p = Number(round(protein, 2));
  const c = Number(round(carbs, 2));
  const g = Number(round(fat, 2));
  if (c < 0) problems.push(`${first} ${name}: carboidrato por diferença negativo (${c}), o importador usa 0`);
  if (p + c + g > 100.0001 || kcal > 900 || name.length > 120) problems.push(`${first} ${name} P${p} C${c} G${g} kcal${kcal}`);
  const raw = (index, digits) => {
    const v = (cells[index] || '').trim();
    return v === 'Tr' || v === 'NA' || v === '*' || v === '' ? v : round(Number(v), digits);
  };
  out.push([first, name, category, 'G', raw(3, 1), raw(5, 2), raw(8, 2), raw(6, 2), raw(9, 2), String(vegan)]);
}
fs.writeFileSync(process.argv[3], out.map((r) => r.map(csvField).join(',')).join('\n') + '\n');
console.log('importáveis:', out.length - 1, '| fora:', excluded.length);
console.log('fora (sem energia ou macro):', excluded);
console.log('fora das regras do modelo:', problems);
