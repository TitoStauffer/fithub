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

// DEV-0132 (Code Review do PR #154): guardas do fluxo que grava no PR.
import { runApprovalReset } from '../dist/approval-reset.js';

const head = 'a'.repeat(40);
const bot = 'titostauffer-devagents[bot]';
function fakeGitHub({ state = 'open', headSha = head, body = template('x', 'x'), reviews = [], comments = [], freshBody, freshHead } = {}) {
  const calls = [];
  let reads = 0;
  const fetchImpl = async (url, init = {}) => {
    const path = new URL(url).pathname.replace('/repos/owner/repo', '');
    calls.push({ method: init.method ?? 'GET', path, body: init.body });
    if (path === '/pulls/7' && (init.method ?? 'GET') === 'GET') {
      reads += 1;
      const second = reads > 1;
      return Response.json({ state, head: { sha: second && freshHead ? freshHead : headSha }, body: second && freshBody !== undefined ? freshBody : body });
    }
    if (path === '/pulls/7' && init.method === 'PATCH') return Response.json({ body: JSON.parse(init.body).body });
    if (path === '/pulls/7/reviews') return Response.json(reviews);
    if (path === '/issues/7/comments') return Response.json(comments);
    return new Response('', { status: 404 });
  };
  return { calls, run: () => runApprovalReset({ repository: 'owner/repo', prNumber: 7, expectedHead: head, botLogin: bot, token: 't', fetchImpl }) };
}
const patches = (calls) => calls.filter(({ method }) => method === 'PATCH');

test('does not write when the pull request HEAD changed since the event', async () => {
  const gh = fakeGitHub({ headSha: 'b'.repeat(40) });
  assert.equal((await gh.run()).action, 'skipped');
  assert.equal(patches(gh.calls).length, 0);
});

test('does not write when the pull request is closed', async () => {
  const gh = fakeGitHub({ state: 'closed' });
  assert.equal((await gh.run()).action, 'skipped');
  assert.equal(patches(gh.calls).length, 0);
});

test('does not write when nothing is checked', async () => {
  const gh = fakeGitHub({ body: template(' ', ' ') });
  assert.equal((await gh.run()).action, 'skipped');
  assert.equal(patches(gh.calls).length, 0);
});

test('unchecks stale approvals and writes the expected body', async () => {
  const gh = fakeGitHub();
  assert.deepEqual(await gh.run(), { action: 'updated', stages: ['codeReview', 'qa'] });
  assert.equal(JSON.parse(patches(gh.calls)[0].body).body, template(' ', ' '));
});

test('keeps approvals the bot already gave to the current HEAD', async () => {
  const qaReport = `<!-- fithub-agent-report:v1 stage=qa sha=${head} -->\n### Codex — QA Agent\n\n**Status:** APPROVED`;
  const gh = fakeGitHub({
    reviews: [{ user: { login: bot }, state: 'APPROVED', commit_id: head }],
    comments: [{ user: { login: bot }, body: qaReport }],
  });
  assert.equal((await gh.run()).action, 'skipped');
  assert.equal(patches(gh.calls).length, 0);
});

test('keeps the code review approval for the current HEAD and unchecks only QA', async () => {
  const gh = fakeGitHub({ reviews: [{ user: { login: bot }, state: 'APPROVED', commit_id: head }] });
  assert.deepEqual(await gh.run(), { action: 'updated', stages: ['qa'] });
  assert.equal(JSON.parse(patches(gh.calls)[0].body).body, template('x', ' '));
});

test('ignores approvals from other users or for older HEADs', async () => {
  const gh = fakeGitHub({ reviews: [
    { user: { login: 'someone' }, state: 'APPROVED', commit_id: head },
    { user: { login: bot }, state: 'APPROVED', commit_id: 'c'.repeat(40) },
  ] });
  assert.deepEqual(await gh.run(), { action: 'updated', stages: ['codeReview', 'qa'] });
});

test('re-reads the pull request before writing and stops if the HEAD moved', async () => {
  const gh = fakeGitHub({ freshHead: 'd'.repeat(40) });
  assert.equal((await gh.run()).action, 'skipped');
  assert.equal(patches(gh.calls).length, 0);
});

// Code Review do PR #154, rodada 2: cada guarda do QA e a releitura têm teste que falha sem elas.
const qaReport = (status, sha = head) => `<!-- fithub-agent-report:v1 stage=qa sha=${sha} -->\n### Codex — QA Agent\n\n**Status:** ${status}`;

test('does not keep QA for a report with the HEAD marker written by another user', async () => {
  const gh = fakeGitHub({ comments: [{ user: { login: 'someone' }, body: qaReport('APPROVED') }] });
  assert.deepEqual(await gh.run(), { action: 'updated', stages: ['codeReview', 'qa'] });
});

test('does not keep QA for a bot report of the HEAD that is not APPROVED', async () => {
  const gh = fakeGitHub({ comments: [{ user: { login: bot }, body: qaReport('INCONCLUSIVE') }] });
  assert.deepEqual(await gh.run(), { action: 'updated', stages: ['codeReview', 'qa'] });
});

test('keeps only the QA approval when only QA approved the current HEAD', async () => {
  const gh = fakeGitHub({ comments: [{ user: { login: bot }, body: qaReport('APPROVED') }] });
  assert.deepEqual(await gh.run(), { action: 'updated', stages: ['codeReview'] });
  assert.equal(JSON.parse(patches(gh.calls)[0].body).body, template(' ', 'x'));
});

test('writes from the re-read body so a concurrent edit is not lost', async () => {
  const gh = fakeGitHub({ freshBody: `${template('x', 'x')}\n\nEdição concorrente.` });
  await gh.run();
  assert.equal(JSON.parse(patches(gh.calls)[0].body).body, `${template(' ', ' ')}\n\nEdição concorrente.`);
});

test('reads approvals beyond the first page of comments', async () => {
  const filler = Array.from({ length: 100 }, (_, index) => ({ user: { login: 'someone' }, body: `comentário ${index}` }));
  const fetchImpl = async (url, init = {}) => {
    const parsed = new URL(url);
    const path = parsed.pathname.replace('/repos/owner/repo', '');
    if (path === '/pulls/7' && init.method === 'PATCH') return Response.json({ body: JSON.parse(init.body).body });
    if (path === '/pulls/7') return Response.json({ state: 'open', head: { sha: head }, body: template('x', 'x') });
    if (path === '/pulls/7/reviews') return Response.json([]);
    if (path === '/issues/7/comments') return Response.json(parsed.searchParams.get('page') === '2' ? [{ user: { login: bot }, body: qaReport('APPROVED') }] : filler);
    return new Response('', { status: 404 });
  };
  const result = await runApprovalReset({ repository: 'owner/repo', prNumber: 7, expectedHead: head, botLogin: bot, token: 't', fetchImpl });
  assert.deepEqual(result, { action: 'updated', stages: ['codeReview'] });
});

test('handles a CRLF body and keeps its line endings', () => {
  const body = template('x', 'x').replace(/\n/g, '\r\n');
  const result = resetApprovalCheckboxes(body);
  assert.equal(result.changed, true);
  assert.equal(result.body, template(' ', ' ').replace(/\n/g, '\r\n'));
});

test('fails with a clear message when the approval checkbox is duplicated', () => {
  const body = `${template('x', ' ')}\n- [x] **Code Review aprovado** — duplicado`;
  assert.throws(() => resetApprovalCheckboxes(body), /mais de um checkbox "Code Review aprovado"/);
});
