import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { DEFAULT_DELAY_MS, DEFAULT_MAX_ATTEMPTS } from '../dist/ci-recovery.js';
import { REVIEW_TRACK_OUTPUT } from '../dist/review-track.js';

const mergeReadinessWorkflow = await readFile(new URL('../../../.github/workflows/merge-readiness.yml', import.meta.url), 'utf8');
const reviewSignalWorkflow = await readFile(new URL('../../../.github/workflows/pr-review-signal.yml', import.meta.url), 'utf8');
const titleWorkflow = await readFile(new URL('../../../.github/workflows/pr-title.yml', import.meta.url), 'utf8');
const ciWorkflow = await readFile(new URL('../../../.github/workflows/ci.yml', import.meta.url), 'utf8');
const orchestrationWorkflow = await readFile(new URL('../../../.github/workflows/agent-orchestration.yml', import.meta.url), 'utf8');
const recoveryWorkflow = await readFile(new URL('../../../.github/workflows/agent-report-recovery.yml', import.meta.url), 'utf8');

function assertOrchestrationCheckoutsOmitRef(workflow) {
  assert.equal([...workflow.matchAll(/uses:\s*actions\/checkout@/g)].length, 6);
  assert.doesNotMatch(workflow, /^\s+ref\s*:/m);
}

test('serializes merge-readiness publications for the same pull request', () => {
  const concurrencyGroup = mergeReadinessWorkflow.match(/^\s+group:\s*(.+)$/m)?.[1] ?? '';

  assert.match(concurrencyGroup, /github\.event\.pull_request\.number/);
  assert.match(concurrencyGroup, /github\.event\.issue\.number/);
  assert.match(concurrencyGroup, /github\.event\.workflow_run\.pull_requests\[0\]\.number/);
  assert.match(concurrencyGroup, /inputs\.pr_number/);
  assert.match(mergeReadinessWorkflow, /^\s+cancel-in-progress:\s*false\s*$/m);
});

// FitHub (DEV-0118): o CI tem o job das ferramentas de revisão (os serviços e apps entram na DEV-0016),
// sem SonarQube; o título do PR é validado só pelo workflow confiável "PR title", inclusive em edições,
// e a orquestração dos agentes começa depois do CI completo.
test('sequences the FitHub CI, the PR title validation and agent orchestration', () => {
  assert.match(ciWorkflow, /^name: CI\s*$/m);
  assert.match(ciWorkflow, /^  automation-tools:\s*\r?\n(?:\s+#.*\r?\n)*\s+name: GitHub Reviewer\s*$/m);
  assert.doesNotMatch(ciWorkflow, /sonar/i);
  assert.match(ciWorkflow, /types:\s*\[opened, reopened, synchronize, ready_for_review\]/);
  assert.doesNotMatch(ciWorkflow, /types:\s*\[[^\]]*edited/);
  assert.match(orchestrationWorkflow, /^\s+workflows: \[CI\]\s*$/m);
  assert.doesNotMatch(orchestrationWorkflow, /workflows: \[[^\]]*PR title/);
  assert.doesNotMatch(orchestrationWorkflow, /workflow_run\.actor\.login != vars\.FITHUB_APP_BOT_LOGIN/);
  assert.match(orchestrationWorkflow, /^\s+cancel-in-progress:\s*false\s*$/m);
  assert.match(orchestrationWorkflow, /runs-on: \[self-hosted, Windows, X64, fithub-agents\]/);
  assert.match(titleWorkflow, /pull_request_target:/);
  assert.match(titleWorkflow, /types:\s*\[[^\]]*\bedited\b[^\]]*\]/);
  assert.match(titleWorkflow, /context='PR title'/);
  // Workflow privilegiado: nunca faz checkout nem executa código do PR.
  assert.doesNotMatch(titleWorkflow, /actions\/checkout/);
});

test('formal review relay listens to review changes and has no permissions or privileged steps', () => {
  assert.match(reviewSignalWorkflow, /pull_request_review:\s*\n\s+types:\s*\[submitted, edited, dismissed\]/);
  assert.match(reviewSignalWorkflow, /^permissions:\s*\{\}\s*$/m);
  assert.match(reviewSignalWorkflow, /^\s+permissions:\s*\{\}\s*$/m);
  assert.doesNotMatch(reviewSignalWorkflow, /uses:\s*actions\/checkout|secrets\./);
});

test('recovers only an existing agent artifact without calling Codex', () => {
  assert.match(recoveryWorkflow, /workflow_dispatch:/);
  assert.match(recoveryWorkflow, /node dist\/agent-recovery-cli\.js/);
  assert.match(recoveryWorkflow, /run-id:\s*\$\{\{ inputs\.source_run_id \}\}/);
  assert.match(recoveryWorkflow, /artifact-ids:\s*\$\{\{ steps\.source\.outputs\.artifact_id \}\}/);
  assert.match(recoveryWorkflow, /merge-multiple:\s*true/);
  assert.match(recoveryWorkflow, /node dist\/agent-publish-cli\.js/);
  assert.ok(recoveryWorkflow.indexOf('node dist/agent-recovery-cli.js') < recoveryWorkflow.indexOf('uses: actions/download-artifact@'));
  assert.match(recoveryWorkflow, /ref:\s*master/);
  assert.match(recoveryWorkflow, /persist-credentials:\s*false/);
  assert.doesNotMatch(recoveryWorkflow, /agent-stage-cli|codex\s+exec|runs-on:\s*\[self-hosted/);
  assert.doesNotMatch(recoveryWorkflow, /git\s+(?:commit|push)|contents:\s*write/);
});

// DEV-116: the light track skips QA, the only model stage on the serialized self-hosted runner. Every
// condition below has to fail closed, because the consequence of a wrong one is a merge without QA.
test('skips QA only on a confirmed light track and keeps the Coordinator on both', () => {
  // The repository stores workflows with CRLF, so every line break here has to allow the carriage
  // return: a bare \n silently matches nothing and the assertions below would never run.
  const condition = (job, terminator) => orchestrationWorkflow
    .match(new RegExp(`^  ${job}:[\\s\\S]*?^\\s+if: >-\\r?\\n([\\s\\S]*?)^\\s+${terminator}`, 'm'))?.[1] ?? '';
  const qaCondition = condition('qa', 'runs-on:');
  const gateCondition = condition('quality-gate', 'runs-on:');
  const finalCondition = condition('final-status', 'run: exit 1');
  // Guards against the silent-empty-match trap above.
  for (const [name, value] of [['qa', qaCondition], ['quality-gate', gateCondition], ['final-status', finalCondition]]) {
    assert.ok(value.trim().length > 0, `condição do job ${name} não foi localizada no workflow`);
  }

  // The producer and the consumer are in different languages, so the key is asserted from the shared
  // constant the CLI writes with. Renaming it on one side alone used to keep this suite green while
  // production got an empty output: QA would run, the Coordinator would be skipped for an unknown
  // track and the final confirmation would fail — closed, but stranding every Pull Request.
  assert.equal(REVIEW_TRACK_OUTPUT, 'review_track');
  assert.ok(orchestrationWorkflow.includes(`steps.stage.outputs.${REVIEW_TRACK_OUTPUT}`), REVIEW_TRACK_OUTPUT);
  assert.ok(orchestrationWorkflow.includes(`needs.code-review.outputs.${REVIEW_TRACK_OUTPUT}`), REVIEW_TRACK_OUTPUT);

  // The track is produced by the trusted CLI as a job output, never read from the PR body.
  assert.match(orchestrationWorkflow, /^\s+review_track: \$\{\{ steps\.stage\.outputs\.review_track \}\}\s*$/m);
  assert.doesNotMatch(orchestrationWorkflow, /review_track:\s*\$\{\{\s*github\.event[^}]*body/);

  // QA is skipped only when the track is a confirmed 'light'; anything else runs it.
  assert.match(qaCondition, /needs\.code-review\.outputs\.review_track != 'light'/);
  assert.doesNotMatch(qaCondition, /review_track == 'full'/);

  // The Coordinator runs on both tracks, so it needs !cancelled() to survive a skipped need, and on the
  // light track it demands that QA was genuinely skipped rather than failed.
  assert.match(gateCondition, /!cancelled\(\)/);
  assert.match(gateCondition, /review_track == 'light' && needs\.qa\.result == 'skipped'/);
  assert.match(gateCondition, /review_track == 'full' && needs\.publish-qa\.result == 'success' && needs\.qa\.outputs\.decision == 'APPROVED'/);

  // The QA artifact only exists on the full track.
  assert.match(orchestrationWorkflow, /^\s+- name: Download QA result\s*\n\s+if: needs\.code-review\.outputs\.review_track == 'full'\s*$/m);

  // Final confirmation: QA required when full, genuinely skipped when light, and an absent or unknown
  // track fails closed instead of reading as "not full".
  assert.match(finalCondition, /review_track == 'full' &&\s*\n?\s*\(needs\.qa\.outputs\.decision != 'APPROVED'/);
  assert.match(finalCondition, /review_track == 'light' &&\s*\n?\s*\(needs\.qa\.result != 'skipped'/);
  assert.match(finalCondition, /review_track != 'full' &&\s*\n?\s*needs\.code-review\.outputs\.review_track != 'light'/);
  assert.match(finalCondition, /needs\.quality-gate\.outputs\.decision != 'APPROVED'/);
});

// DEV-127: on the light track the qa job is skipped, and a job whose if has no status function gets an
// implicit success() that is false when any ancestor was skipped. The Coordinator report must still publish.
test('publishes the Coordinator report on the light track, where QA is skipped', () => {
  const publish = orchestrationWorkflow.split('  publish-quality-gate:')[1].split(/\r?\n  [a-z-]+:\r?\n/)[0];
  const condition = publish.match(/^    if: (.+?)\r?$/m)?.[1] ?? '';
  assert.match(condition, /^always\(\) && /, 'publish-quality-gate must start its condition with always()');
  assert.match(condition, /needs\.quality-gate\.result == 'success'/);
  assert.match(condition, /needs\.quality-gate\.outputs\.decision != ''/);
});

test('pins orchestration runtime and isolates deterministic consolidation from Codex', () => {
  assertOrchestrationCheckoutsOmitRef(orchestrationWorkflow);
  const workflowWithExplicitRef = orchestrationWorkflow.replace(/^\s+token:/m, '          ref: master\n          token:');
  assert.throws(() => assertOrchestrationCheckoutsOmitRef(workflowWithExplicitRef), { name: 'AssertionError' });
  assert.doesNotMatch(orchestrationWorkflow, /github\.workflow_sha/);
  const coordinator = orchestrationWorkflow.split('  quality-gate:')[1].split('  publish-quality-gate:')[0];
  assert.match(coordinator, /runs-on: ubuntu-24.04/);
  assert.doesNotMatch(coordinator, /self-hosted|secrets\./);
  assert.match(orchestrationWorkflow, /retry_inconclusive:[\s\S]*?default: false/);
  assert.equal([...orchestrationWorkflow.matchAll(/cache-dependency-path:\s*tools\/github-reviewer\/package-lock\.json/g)].length, 6);
  assert.match(orchestrationWorkflow, /Fail when Code Review produced no structured result/);
  assert.match(orchestrationWorkflow, /Fail when QA produced no structured result/);
  assert.match(orchestrationWorkflow, /Fail when Quality Gate produced no structured result/);
  assert.equal([...orchestrationWorkflow.matchAll(/steps\.stage\.outcome == 'failure'[\s\S]*?steps\.stage\.outputs\.decision == ''/g)].length, 3);
});

// DEV-0152: com o repositório público, qualquer pessoa abre PR por fork. Os estágios com modelo rodam no
// runner self-hosted, com a assinatura do dono, e só podem atender PR de branch do próprio repositório
// cujo CI o dono disparou.
test('keeps the self-hosted model stages to pull requests from this repository and its owner', () => {
  const normalized = orchestrationWorkflow.replace(/\r\n/g, '\n');
  const jobs = normalized.split('\njobs:\n')[1].split(/\n(?=  [a-z-]+:\n)/);
  const codeReview = jobs.find((job) => job.startsWith('  code-review:\n')) ?? '';
  const condition = codeReview.match(/^ {4}if: >-\n([\s\S]*?)^ {4}runs-on:/m)?.[1].replace(/\s+/g, ' ') ?? '';
  assert.ok(condition.trim().length > 0, 'condição do job code-review não foi localizada no workflow');

  const [dispatch, workflowRun] = condition.split(' || ');
  assert.match(dispatch, /github\.event_name == 'workflow_dispatch'/);
  assert.match(dispatch, /github\.triggering_actor == github\.repository_owner/);
  assert.match(workflowRun, /github\.event_name == 'workflow_run'/);
  assert.match(workflowRun, /github\.event\.workflow_run\.head_repository\.full_name == github\.repository/);
  assert.match(workflowRun, /github\.event\.workflow_run\.actor\.login == github\.repository_owner/);
  assert.equal(condition.split(' || ').length, 2, 'nenhum caminho extra pode pular a trava');

  // Todo outro job no runner self-hosted depende do code-review, então herda a trava.
  for (const job of jobs.filter((candidate) => /runs-on: \[self-hosted/.test(candidate) && !candidate.startsWith('  code-review:\n'))) {
    const name = job.match(/^ {2}([a-z-]+):/)?.[1];
    assert.match(job, /^ {4}needs: (\[[^\]]*\bcode-review\b[^\]]*\]|code-review)$/m, `job ${name} roda no self-hosted sem depender do code-review`);
  }
});
