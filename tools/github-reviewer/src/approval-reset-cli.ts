import { runApprovalReset } from './approval-reset.js';

// Executado pelo workflow approval-reset (DEV-0132) a partir do código confiável da master.
const result = await runApprovalReset({
  token: required('GITHUB_TOKEN'),
  repository: required('GITHUB_REPOSITORY'),
  prNumber: Number(required('PR_NUMBER')),
  expectedHead: required('EXPECTED_HEAD_SHA'),
  botLogin: required('FITHUB_APP_BOT_LOGIN'),
});
console.log(result.action === 'updated'
  ? `Aprovações desmarcadas: ${result.stages.join(', ') || 'nenhuma'}.`
  : `Nada a fazer: ${result.reason}`);

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} não configurado.`);
  return value;
}
