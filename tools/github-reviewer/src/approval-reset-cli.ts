import { resetApprovalCheckboxes } from './approval-reset.js';

// Executado pelo workflow approval-reset (DEV-0132) a partir do código confiável da master. Lê o corpo
// do PR pela API, desmarca as aprovações dos agentes e grava de volta só se algo mudou.
const token = required('GITHUB_TOKEN');
const repository = required('GITHUB_REPOSITORY');
const prNumber = required('PR_NUMBER');
const expectedHead = required('EXPECTED_HEAD_SHA');

const api = async (path: string, init: RequestInit = {}): Promise<Record<string, unknown>> => {
  const response = await fetch(`https://api.github.com/repos/${repository}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'Content-Type': 'application/json' },
    redirect: 'error',
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new Error(`GitHub API ${init.method ?? 'GET'} ${path} respondeu ${response.status}.`);
  return await response.json() as Record<string, unknown>;
};

const pullRequest = await api(`/pulls/${encodeURIComponent(prNumber)}`);
const head = (pullRequest.head ?? {}) as Record<string, unknown>;
if (pullRequest.state !== 'open' || head.sha !== expectedHead) {
  // Outro commit chegou depois deste evento; a execução daquele commit cuida do corpo atual.
  console.log('PR fechado ou HEAD mudou desde o evento; nada a fazer.');
} else {
  const { body, changed } = resetApprovalCheckboxes(typeof pullRequest.body === 'string' ? pullRequest.body : '');
  if (!changed) {
    console.log('Nenhum checkbox de aprovação marcado; nada a fazer.');
  } else {
    await api(`/pulls/${encodeURIComponent(prNumber)}`, { method: 'PATCH', body: JSON.stringify({ body }) });
    console.log(`Checkboxes de aprovação desmarcados no PR #${prNumber} para o HEAD ${expectedHead}.`);
  }
}

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} não configurado.`);
  return value;
}
