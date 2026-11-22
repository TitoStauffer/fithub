import { updateApprovalCheckbox, type ApprovalChecklistStage } from './approval-checklist.js';

/**
 * DEV-0132: um commit novo invalida as aprovações dos agentes para o HEAD anterior, então os
 * checkboxes "Code Review aprovado" e "QA aprovado" do corpo do PR voltam a ficar desmarcados até cada
 * agente aprovar o HEAD atual. PR sem o checkbox (fora do template) fica como está.
 */
const STAGES: readonly ApprovalChecklistStage[] = ['codeReview', 'qa'];
const LABELS: Record<ApprovalChecklistStage, string> = { codeReview: 'Code Review aprovado', qa: 'QA aprovado' };

export function resetApprovalCheckboxes(
  body: string,
  stages: readonly ApprovalChecklistStage[] = STAGES,
): { body: string; changed: boolean } {
  let updated = body;
  for (const stage of stages) {
    if (!hasCheckedBox(updated, LABELS[stage])) continue;
    // Reaproveita a regra do publicador: exige exatamente um checkbox do estágio no corpo.
    updated = updateApprovalCheckbox(updated, stage, false);
  }
  return { body: updated, changed: updated !== body };
}

export interface ApprovalResetOptions {
  repository: string;
  prNumber: number;
  expectedHead: string;
  botLogin: string;
  token: string;
  fetchImpl?: typeof fetch;
}

export type ApprovalResetResult =
  | { action: 'updated'; stages: ApprovalChecklistStage[] }
  | { action: 'skipped'; reason: string };

/**
 * Desmarca as aprovações que não valem para o HEAD atual. Guardas:
 * - PR fechado ou HEAD diferente do evento → não escreve (outra execução cuida do HEAD novo).
 * - Aprovação do bot que já vale para o HEAD atual é preservada: o reset pode rodar depois de o agente
 *   aprovar o commit novo (corrida apontada no Code Review do PR #154).
 * - Relê o PR antes de gravar e confere a resposta.
 */
export async function runApprovalReset(options: ApprovalResetOptions): Promise<ApprovalResetResult> {
  const call = github(options);
  const prPath = `/pulls/${options.prNumber}`;
  const initial = await call(prPath);
  const skip = guard(initial, options.expectedHead);
  if (skip) return skip;

  const keep = new Set<ApprovalChecklistStage>();
  const reviews = await call(`/pulls/${options.prNumber}/reviews?per_page=100`) as unknown as Array<Record<string, unknown>>;
  if (reviews.some((review) => login(review) === options.botLogin && review.state === 'APPROVED' && review.commit_id === options.expectedHead)) {
    keep.add('codeReview');
  }
  const comments = await call(`/issues/${options.prNumber}/comments?per_page=100`) as unknown as Array<Record<string, unknown>>;
  const qaMarker = `fithub-agent-report:v1 stage=qa sha=${options.expectedHead.toLowerCase()}`;
  if (comments.some((comment) => login(comment) === options.botLogin && typeof comment.body === 'string'
    && comment.body.includes(qaMarker) && /^\*\*Status:\*\* APPROVED$/m.test(comment.body))) {
    keep.add('qa');
  }
  const stages = STAGES.filter((stage) => !keep.has(stage));
  if (stages.length === 0) return { action: 'skipped', reason: 'As duas aprovações já valem para o HEAD atual.' };

  // Relê o PR para não sobrescrever uma edição feita entre a primeira leitura e a gravação.
  const fresh = await call(prPath);
  const freshSkip = guard(fresh, options.expectedHead);
  if (freshSkip) return freshSkip;
  const { body, changed } = resetApprovalCheckboxes(typeof fresh.body === 'string' ? fresh.body : '', stages);
  if (!changed) return { action: 'skipped', reason: 'Nenhuma aprovação a desmarcar.' };

  const saved = await call(prPath, { method: 'PATCH', body: JSON.stringify({ body }) });
  if (saved.body !== body) throw new Error('O GitHub não confirmou o corpo atualizado do PR.');
  const unchecked = stages.filter((stage) => hasCheckedBox(typeof fresh.body === 'string' ? fresh.body : '', LABELS[stage]));
  return { action: 'updated', stages: unchecked };
}

function guard(pullRequest: Record<string, unknown>, expectedHead: string): ApprovalResetResult | undefined {
  if (pullRequest.state !== 'open') return { action: 'skipped', reason: 'PR não está aberto.' };
  const head = (pullRequest.head ?? {}) as Record<string, unknown>;
  if (head.sha !== expectedHead) return { action: 'skipped', reason: 'O HEAD mudou desde o evento.' };
  return undefined;
}

function github(options: ApprovalResetOptions) {
  const fetchImpl = options.fetchImpl ?? fetch;
  return async (path: string, init: RequestInit = {}): Promise<Record<string, unknown>> => {
    const response = await fetchImpl(`https://api.github.com/repos/${options.repository}${path}`, {
      ...init,
      headers: { Authorization: `Bearer ${options.token}`, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'Content-Type': 'application/json' },
      redirect: 'error',
      signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok) throw new Error(`GitHub API ${init.method ?? 'GET'} ${path} respondeu ${response.status}.`);
    return await response.json() as Record<string, unknown>;
  };
}

function login(entry: Record<string, unknown>): string | undefined {
  const user = entry.user as Record<string, unknown> | undefined;
  return typeof user?.login === 'string' ? user.login : undefined;
}

function hasCheckedBox(body: string, label: string): boolean {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^\\s*[-*]\\s+\\[[xX]\\]\\s+\\*\\*${escaped}\\*\\*`, 'm').test(body);
}
