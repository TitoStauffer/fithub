import assert from 'node:assert/strict';
import test from 'node:test';
import { resetApprovalCheckboxes } from '../dist/approval-reset.js';

const template = (codeReview, qa) => [
  '## 13. Revisão e aprovação',
  '',
  `- [${codeReview}] **Code Review aprovado** — marcado pelo Agent de Code Review somente após a aprovação.`,
  `- [${qa}] **QA aprovado** — marcado pelo Agent de QA somente após a aprovação.`,
  '- **Observações finais:**',
].join('\n');

// DEV-0132: commit novo desmarca as aprovações do HEAD anterior.
test('unchecks both agent approvals and reports the change', () => {
  const result = resetApprovalCheckboxes(template('x', 'X'));
  assert.equal(result.changed, true);
  assert.equal(result.body, template(' ', ' '));
});

test('unchecks only the approval that was checked', () => {
  const result = resetApprovalCheckboxes(template('x', ' '));
  assert.equal(result.changed, true);
  assert.equal(result.body, template(' ', ' '));
});

test('leaves an unchecked body untouched', () => {
  const body = template(' ', ' ');
  assert.deepEqual(resetApprovalCheckboxes(body), { body, changed: false });
});

test('leaves a pull request without the template untouched', () => {
  const body = 'Corpo livre, sem checklist.\n- [x] Outra tarefa marcada';
  assert.deepEqual(resetApprovalCheckboxes(body), { body, changed: false });
});

test('keeps the rest of the body byte for byte', () => {
  const body = `# PR\n\n- [x] Testes unitários.\n\n${template('x', 'x')}\n\nFim.`;
  const { body: updated } = resetApprovalCheckboxes(body);
  assert.equal(updated, `# PR\n\n- [x] Testes unitários.\n\n${template(' ', ' ')}\n\nFim.`);
});
