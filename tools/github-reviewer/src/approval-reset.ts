import { updateApprovalCheckbox, type ApprovalChecklistStage } from './approval-checklist.js';

/**
 * DEV-0132: um commit novo invalida as aprovações dos agentes para o HEAD anterior, então os
 * checkboxes "Code Review aprovado" e "QA aprovado" do corpo do PR voltam a ficar desmarcados até cada
 * agente aprovar o HEAD atual. PR sem o checkbox (fora do template) fica como está.
 */
const STAGES: readonly ApprovalChecklistStage[] = ['codeReview', 'qa'];
const LABELS: Record<ApprovalChecklistStage, string> = { codeReview: 'Code Review aprovado', qa: 'QA aprovado' };

export function resetApprovalCheckboxes(body: string): { body: string; changed: boolean } {
  let updated = body;
  for (const stage of STAGES) {
    if (!hasCheckedBox(updated, LABELS[stage])) continue;
    // Reaproveita a regra do publicador: exige exatamente um checkbox do estágio no corpo.
    updated = updateApprovalCheckbox(updated, stage, false);
  }
  return { body: updated, changed: updated !== body };
}

function hasCheckedBox(body: string, label: string): boolean {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^\\s*[-*]\\s+\\[[xX]\\]\\s+\\*\\*${escaped}\\*\\*`, 'm').test(body);
}
