import { createHash } from 'node:crypto';
import { posix } from 'node:path';
import { GitHubApiError, requestGitHubApi, requestGitHubApiPages, requestGitHubText } from './github-api-client.js';
import { ACTIONS_APP_ID, loadActionsEvidence, loadCiSnapshots, unavailableActionsEvidence, type AgentActionsEvidence, type AgentCiSnapshot } from './agent-ci-evidence.js';
import { loadVaultContext, type VaultContextSnapshot } from './vault-context.js';
import { MAX_REVIEW_COVERAGE_FILES } from './agent-result-validation.js';
import { CONTEXT_EXCLUDED_CHECKS, KNOWN_TEST_JOBS, REQUIRED_AGENT_CHECKS, VAULT_CONTEXT_MAP_PATH, VAULT_ROOT } from './repository-policy.js';

export interface AgentReviewInputs {
  diff: string;
  changedFiles: string[];
  /** Arquivos que o Code Review precisa cobrir em evidence.changedFilesReviewed (DEV-0140). */
  reviewCoverageFiles: string[];
  checks: Record<keyof typeof REQUIRED_AGENT_CHECKS, string>;
  checkRuns: AgentCheckEvidence[];
  repositoryInstructions: string;
  sourceFiles: Array<{ path: string; revision: 'head' | 'base'; content: string }>;
  vaultContext: VaultContextSnapshot[];
  ciConfiguration?: AgentCiSnapshot[];
}

export interface AgentCheckEvidence {
  name: string;
  appId: number | null;
  headSha: string;
  status: string;
  conclusion: string | null;
  detailsUrl: string | null;
  completedAt: string | null;
  actions?: AgentActionsEvidence;
}

const MAX_SOURCE_FILES = 32;
const MAX_DEPENDENCY_FILES = 64;
const MAX_SOURCE_BYTES = 500_000;
// A lockfile read raw is summarized, never sent whole, but it is still held in memory to be parsed.
const MAX_RAW_LOCKFILE_BYTES = 20_000_000;

export async function loadAgentReviewInputs(
  fetchImpl: typeof fetch,
  repository: string,
  pullRequestNumber: number,
  pullRequest: Record<string, unknown>,
  token: string,
): Promise<AgentReviewInputs> {
  const head = asRecord(pullRequest.head);
  const base = asRecord(pullRequest.base);
  const headSha = stringValue(head?.sha);
  const baseSha = stringValue(base?.sha);
  if (!headSha || !baseSha) throw new Error('Pull Request is missing a complete head or base commit.');

  const [rawDiff, runs, instructionsPayload, filesPayload] = await Promise.all([
    readPullRequestDiff(fetchImpl, repository, pullRequestNumber, token),
    readCheckRuns(fetchImpl, repository, headSha, token),
    requestGitHubApi<Record<string, unknown>>(fetchImpl, `/repos/${repository}/contents/AGENTS.md?ref=${encodeURIComponent(baseSha)}`, token),
    requestGitHubApiPages<Record<string, unknown>>(fetchImpl, `/repos/${repository}/pulls/${pullRequestNumber}/files`, token),
  ]);
  // DEV-0137: acima de 300 arquivos o GitHub recusa o diff (406); o diff é montado da lista de arquivos.
  // A lista de arquivos do GitHub para em 3000; se ela não bater com o total do PR, a revisão seria
  // parcial sem ninguém perceber (Code Review do PR #164). Falha fechado.
  const expectedFiles = pullRequest.changed_files;
  if (typeof expectedFiles === 'number' && filesPayload.length !== expectedFiles) {
    throw new Error(`GitHub returned ${filesPayload.length} changed files but the Pull Request reports ${expectedFiles}; the review context would be incomplete.`);
  }
  // Sem o total no payload, uma lista no teto do GitHub (3000) pode estar cortada: falha fechado.
  if (typeof expectedFiles !== 'number' && filesPayload.length >= 3000) {
    throw new Error('GitHub returned the maximum changed-file list and the Pull Request total is unknown; the review context may be incomplete.');
  }
  const diff = sanitizeAgentDiff(rawDiff ?? synthesizeDiffFromFiles(filesPayload));
  const encoding = stringValue(instructionsPayload.encoding);
  const encodedInstructions = stringValue(instructionsPayload.content);
  if (encoding !== 'base64' || !encodedInstructions) throw new Error('Could not read AGENTS.md from the Pull Request base commit.');
  const repositoryInstructions = Buffer.from(encodedInstructions.replace(/\s/g, ''), 'base64').toString('utf8');
  const vaultContext = await loadVaultContext(fetchImpl, repository, filesPayload, headSha, baseSha, token);
  const vaultPaths = new Set(vaultContext.filter(({ path }) => !path.startsWith('github-evidence://')).map(({ path }) => path));
  const sourceFiles = await loadChangedSourceFiles(fetchImpl, repository, filesPayload, headSha, baseSha, token, vaultPaths);
  const checkRuns = summarizeCheckRunEvidence(runs, headSha);
  const ciChecks = latestRunsByName(runs.filter((run) => run.head_sha === headSha && isTestCheck(String(run.name ?? ''))));
  if (ciChecks.length > 4) throw new Error('Too many CI checks for the supported agent evidence context.');
  for (const check of ciChecks) {
    const summary = checkRuns.find((candidate) => candidate.name === check.name && candidate.appId === (asRecord(check.app)?.id ?? null));
    if (summary) summary.actions = await loadActionsEvidence(fetchImpl, repository, pullRequestNumber, headSha, check, token);
  }
  const ciConfiguration = checkRuns.some((check) => check.actions?.availability === 'available')
    ? await loadCiSnapshots(fetchImpl, repository, headSha, token) : [];
  const changedFiles = filesPayload.map((file) => stringValue(file.filename));
  if (changedFiles.some((path) => !path)) throw new Error('GitHub returned incomplete changed-file metadata.');
  return { diff, changedFiles: changedFiles as string[], reviewCoverageFiles: selectReviewCoverageFiles(filesPayload), checks: summarizeAgentChecks(runs, headSha), checkRuns, repositoryInstructions, sourceFiles, vaultContext, ciConfiguration };
}

async function loadChangedSourceFiles(
  fetchImpl: typeof fetch,
  repository: string,
  files: Array<Record<string, unknown>>,
  headSha: string,
  baseSha: string,
  token: string,
  alreadyLoadedPaths: ReadonlySet<string> = new Set(),
): Promise<AgentReviewInputs['sourceFiles']> {
  const changedFiles = files.map((file) => ({ ...file, filename: stringValue(file.filename), status: stringValue(file.status) }));
  for (const file of changedFiles) {
    if (!file.filename || !file.status) throw new Error('GitHub returned incomplete changed-file metadata.');
  }
  const sourceCandidates = changedFiles.filter(({ filename }) => {
    const path = filename as string;
    return !alreadyLoadedPaths.has(path) || !isVaultManagedDocument(path);
  });
  // DEV-0137: arquivo removido não traz conteúdo novo a revisar. Se o total passar do limite, os removidos
  // saem dos snapshots (continuam no diff pelo caminho); o limite segue valendo para novos e alterados.
  const changedOrAdded = sourceCandidates.filter(({ status }) => status !== 'removed');
  if (changedOrAdded.length > MAX_SOURCE_FILES) throw new Error('Pull Request changes too many files for a complete agent review context.');
  if (sourceCandidates.length > MAX_SOURCE_FILES) sourceCandidates.splice(0, sourceCandidates.length, ...changedOrAdded);

  const sourceFiles: AgentReviewInputs['sourceFiles'] = [];
  let totalBytes = 0;
  const changedFileCount = sourceCandidates.length;
  const pendingFiles = [...sourceCandidates];
  const seen = new Set(pendingFiles.map((file) => `${file.status === 'removed' ? 'base' : 'head'}:${file.filename}`));
  for (let index = 0; index < pendingFiles.length; index += 1) {
    const file = pendingFiles[index];
    const isChangedFile = index < changedFileCount;
    const path = stringValue(file.filename);
    const status = stringValue(file.status);
    if (!path || !status) throw new Error('GitHub returned incomplete changed-file metadata.');
    const revision = status === 'removed' ? 'base' : 'head';
    const ref = revision === 'head' ? headSha : baseSha;
    const encodedPath = path.split('/').map(encodeURIComponent).join('/');
    const payload = await requestGitHubApi<Record<string, unknown>>(
      fetchImpl,
      `/repos/${repository}/contents/${encodedPath}?ref=${encodeURIComponent(ref)}`,
      token,
    );
    // GitHub omits the body of files over 1 MB from this response (`encoding: none`). A lockfile can
    // pass that size and is summarized anyway, so it alone is read again through the raw media type.
    const rawLockfile = isLockfile(path) && payload.encoding === 'none'
      ? await requestGitHubText(fetchImpl, `/repos/${repository}/contents/${encodedPath}?ref=${encodeURIComponent(ref)}`, token, 'application/vnd.github.raw')
      : null;
    if (rawLockfile !== null && Buffer.byteLength(rawLockfile, 'utf8') > MAX_RAW_LOCKFILE_BYTES) {
      throw new Error(`Pull Request lockfile exceeds the supported raw read size: ${path}.`);
    }
    if (rawLockfile === null && (payload.encoding !== 'base64' || typeof payload.content !== 'string')) {
      throw new Error(`Could not read the full Pull Request source file: ${path}.`);
    }
    const content = rawLockfile ?? Buffer.from(String(payload.content).replace(/\s/g, ''), 'base64').toString('utf8');
    if (content.includes('\0')) throw new Error(`Pull Request includes a binary file that cannot be reviewed as source: ${path}.`);
    const reviewContent = isEncodedReviewFixture(path)
      ? encodedFixtureMarker(path, content)
      : isNpmLockfile(path) ? summarizeNpmLockfile(path, content)
        : isPnpmLockfile(path) ? summarizePnpmLockfile(path, content) : content;
    const contentBytes = Buffer.byteLength(reviewContent, 'utf8');
    if (totalBytes + contentBytes > MAX_SOURCE_BYTES) {
      if (isChangedFile) throw new Error('Pull Request source files exceed the supported review context size.');
      continue;
    }
    totalBytes += contentBytes;
    sourceFiles.push({ path, revision, content: reviewContent });
    if (isChangedFile && /\.[cm]?tsx?$/.test(path)) {
      for (const match of reviewContent.matchAll(/\bfrom\s+['"](\.[^'"]+\.js)['"]/g)) {
        const dependency = posix.normalize(posix.join(posix.dirname(path), match[1].replace(/\.js$/, '.ts')));
        const key = `${revision}:${dependency}`;
        if (dependency.startsWith('../') || seen.has(key)) continue;
        if (pendingFiles.length >= changedFileCount + MAX_DEPENDENCY_FILES) throw new Error('Pull Request dependencies exceed the supported source file limit.');
        seen.add(key);
        pendingFiles.push({ filename: dependency, status: revision === 'base' ? 'removed' : 'modified' });
      }
    }
  }
  return sourceFiles;
}

export function sanitizeAgentDiff(diff: string): string {
  return diff.split(/(?=^diff --git )/m).map((section) => {
    const path = section.match(/^diff --git a\/.+ b\/(.+)$/m)?.[1]?.trim();
    if (!path) return section;
    const header = section.split(/\r?\n/, 1)[0];
    const bytes = Buffer.byteLength(section, 'utf8');
    if (isEncodedReviewFixture(path)) {
      return `${header}\n[encoded test fixture diff section omitted from model context; diffSectionBytes=${bytes}; diffSectionSha256=${hash(section)}]\n`;
    }
    if (isNpmLockfile(path)) {
      return `${header}\n[npm lockfile diff section omitted from model context; the source context carries its deterministic summary; diffSectionBytes=${bytes}; diffSectionSha256=${hash(section)}]\n`;
    }
    if (isPnpmLockfile(path)) {
      return `${header}\n[pnpm lockfile diff section omitted from model context; the source context carries its deterministic summary; diffSectionBytes=${bytes}; diffSectionSha256=${hash(section)}]\n`;
    }
    return section;
  }).join('');
}

function isEncodedReviewFixture(path: string): boolean {
  return /^tools\/github-reviewer\/test\/fixtures\/[A-Za-z0-9._-]+\.json\.gz\.base64$/.test(path);
}

// An npm lockfile is generated, and most of it is integrity hashes no reader can check by eye; an
// Expo app's lockfile alone passes the whole source budget (DEV-152). What a reviewer can judge is
// where each package comes from, so the lockfile reaches the model as a deterministic summary that
// names every package resolved outside the official registry and every package without an integrity
// hash. That the lockfile matches its package.json is enforced by `npm ci` in every CI job that
// installs a package, not by the model.
//
// Everything in the lockfile is written by whoever opens the PR, so no value of it reaches the summary
// raw: a newline or a `]` in a key, a URL or `lockfileVersion` could otherwise write a whole clean-looking
// summary ahead of the real one. Integers are kept only when they are integers, and every string —
// path, location, URL, host — is emitted as a JSON string literal, cut to a bounded length first.
const NPM_REGISTRY_PREFIX = 'https://registry.npmjs.org/';
const MAX_LISTED_LOCKFILE_ANOMALIES = 50;
const MAX_LOCKFILE_VALUE_CHARS = 200;

function isNpmLockfile(path: string): boolean {
  return /(^|\/)package-lock\.json$/.test(path);
}

// DEV-0142: o FitHub usa pnpm. O pnpm-lock.yaml recebe o mesmo tratamento do package-lock.json: sai do
// diff do modelo e entra no contexto como resumo determinístico.
function isPnpmLockfile(path: string): boolean {
  return /(^|\/)pnpm-lock\.yaml$/.test(path);
}

function isLockfile(path: string): boolean {
  return isNpmLockfile(path) || isPnpmLockfile(path);
}

const PNPM_SUPPORTED_LOCKFILE_VERSION = /^(?:'9\.\d+'|9\.\d+)$/;
// Chave de pacote nua na seção packages: com aspas simples (o pnpm usa para @scope) ou sem aspas, sem
// espaço, sem valor na mesma linha. Aspas duplas, `?`, `{`, `#` e `&`/`*` (âncoras) ficam de fora.
const PNPM_PACKAGE_KEY = /^ {2}('(?:[^']|'')+'|[^\s'"?{}#&*!|>%@,[\]][^\s{}#,[\]]*|@[^\s{}#,[\]]+):$/;
// Nome de pacote npm (com escopo opcional) seguido de @ e da versão ou especificador, sem espaço nem aspas.
const PNPM_PACKAGE_NAME = /^(@[a-z0-9][a-z0-9._~-]*\/)?[a-z0-9][a-z0-9._~-]*@[^\s'"@][^\s'"]*$/i;
// Campos que a resolution de um pacote pode ter no formato 9. Chave fora da lista, entre aspas, valor
// com espaço ou estrutura aninhada falham fechado: o resumo só lista anomalias que ele sabe ler.
const PNPM_RESOLUTION_FIELDS = new Set(['integrity', 'tarball', 'type', 'repo', 'commit', 'directory', 'path']);

function parsePnpmResolution(body: string, fail: () => never): Map<string, string> {
  const fields = new Map<string, string>();
  for (const field of body.split(', ')) {
    const match = /^([a-z]+): ('[^',{}]*'|[^\s'",{}[\]]+)$/.exec(field);
    if (!match || !PNPM_RESOLUTION_FIELDS.has(match[1]) || fields.has(match[1])) fail();
    fields.set(match![1], match![2].replace(/^'(.*)'$/, '$1'));
  }
  return fields;
}

/**
 * Resumo do pnpm-lock.yaml (formato 9, um ou mais documentos YAML). Lido por linha, sem parser de YAML:
 * cada documento tem `packages:` no nível zero, cada pacote é uma chave com dois espaços de recuo, e a
 * `resolution` do pacote é um mapa de uma linha. O pacote que não vem do registry padrão traz `tarball`,
 * `type` (git, directory), `repo` ou `directory`; o que vem tem `integrity`. Como no resumo do npm,
 * nenhum valor sai cru: tudo passa por `quoted`. Versão de formato desconhecida falha fechado, porque o
 * leitor por linha só vale para o formato que ele conhece.
 */
export function summarizePnpmLockfile(path: string, content: string): string {
  const header = `[pnpm lockfile summarized for the model context; path=${quoted(path)}; sourceBytes=${Buffer.byteLength(content, 'utf8')}; sourceSha256=${hash(content)}`;
  // Code Review do PR #185, rodada 2: o YAML do pnpm quebra linha também em CR solto (e o YAML 1.1 em
  // NEL, LS e PS), e o leitor só separa por \n e \r\n. Uma quebra que só o pnpm enxerga esconderia um
  // pacote dentro de uma linha que o leitor ignora; por isso ela, e todo controle além de \t e \n, falham.
  const hiddenBreak = new RegExp('\\r(?!\\n)|[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u0085\\u2028\\u2029]').exec(content);
  if (hiddenBreak) {
    throw new Error(`Unsupported pnpm lockfile format in ${path}: control character U+${hiddenBreak[0].codePointAt(0)!.toString(16).toUpperCase().padStart(4, '0')}.`);
  }
  const lines = content.split(/\r?\n/);
  const versions = lines.filter((line) => line.startsWith('lockfileVersion:')).map((line) => line.slice('lockfileVersion:'.length).trim());
  if (versions.length === 0 || versions.some((version) => !PNPM_SUPPORTED_LOCKFILE_VERSION.test(version))) {
    throw new Error(`Unsupported pnpm lockfile format in ${path}: lockfileVersion ${versions.map(quoted).join(', ') || '(missing)'}.`);
  }

  // Code Review do PR #185: o leitor por linha só é seguro se nada fora do formato conhecido passar em
  // silêncio. Na seção packages:, toda linha é uma chave de pacote nua, uma propriedade do pacote com
  // quatro ou mais espaços, ou vazia; qualquer outra coisa (valor flow na linha da chave, chave complexa,
  // comentário, tabulação, recuo torto, resolution em bloco ou repetida) falha fechado.
  const unsupported = (reason: string, line: string): never => {
    throw new Error(`Unsupported pnpm lockfile format in ${path}: ${reason}: ${quoted(line)}.`);
  };
  const resolutions = new Map<string, Map<string, string> | null>();
  let inPackages = false;
  let current: string | null = null;
  for (const line of lines) {
    if (line.trim() === '') continue;
    if (/^\S/.test(line)) {
      if (line.startsWith('packages') && line !== 'packages:') unsupported('packages section not in block style', line);
      inPackages = line === 'packages:';
      current = null;
      continue;
    }
    if (!inPackages) continue;
    if (/\t/.test(line) || /^\s*#/.test(line)) unsupported('tab or comment in packages', line);
    const indent = line.length - line.trimStart().length;
    if (indent === 2) {
      const key = PNPM_PACKAGE_KEY.exec(line);
      if (!key) unsupported('package entry not written as a bare key', line);
      current = key![1].startsWith("'") ? key![1].slice(1, -1).replace(/''/g, "'") : key![1];
      // Só nome@versão: nada de `<<` (merge do YAML, que funde outro mapa na seção) nem chave solta.
      if (!PNPM_PACKAGE_NAME.test(current)) unsupported('package key is not name@version', line);
      if (resolutions.has(current)) unsupported('repeated package key', line);
      resolutions.set(current, null);
      continue;
    }
    if (indent < 4 || current === null) unsupported('unexpected indentation in packages', line);
    if (indent === 4 && /^ {4}resolution\b/.test(line)) {
      const resolution = /^ {4}resolution: \{(.*)\}$/.exec(line);
      if (!resolution) unsupported('resolution not written as a one-line map', line);
      if (resolutions.get(current!) !== null) unsupported('repeated resolution', line);
      resolutions.set(current!, parsePnpmResolution(resolution![1], () => unsupported('unknown resolution field', line)));
    }
  }
  if (resolutions.size === 0) throw new Error(`Unsupported pnpm lockfile format in ${path}: no packages section.`);

  const outsideRegistry: string[] = [];
  const withoutIntegrity: string[] = [];
  for (const [name, fields] of resolutions) {
    if (fields === null) {
      outsideRegistry.push(`${quoted(name)} -> (no resolution)`);
      withoutIntegrity.push(quoted(name));
      continue;
    }
    const tarball = fields.get('tarball');
    const external = fields.has('type') || fields.has('repo') || fields.has('directory') || fields.has('path')
      || (tarball !== undefined && !tarball.startsWith(NPM_REGISTRY_PREFIX));
    if (external) {
      const shown = [...fields.entries()].map(([key, value]) => `${key}: ${value}`).join(', ');
      outsideRegistry.push(`${quoted(name)} -> ${quoted(`{${shown}}`)}`);
    }
    if (!/^sha(256|384|512)-/.test(fields.get('integrity') ?? '')) withoutIntegrity.push(quoted(name));
  }
  return [
    `${header}; lockfileVersion=${quoted(versions[0].replace(/'/g, ''))}; packages=${resolutions.size}`,
    `packagesResolvedOutsideRegistry (${outsideRegistry.length}): ${listAnomalies(outsideRegistry)}`,
    `packagesWithoutIntegrity (${withoutIntegrity.length}): ${listAnomalies(withoutIntegrity)}]`,
  ].join('\n');
}

function quoted(value: string): string {
  const bounded = value.length > MAX_LOCKFILE_VALUE_CHARS ? `${value.slice(0, MAX_LOCKFILE_VALUE_CHARS)}…` : value;
  return JSON.stringify(bounded);
}

export function summarizeNpmLockfile(path: string, content: string): string {
  const header = `[npm lockfile summarized for the model context; path=${quoted(path)}; sourceBytes=${Buffer.byteLength(content, 'utf8')}; sourceSha256=${hash(content)}`;
  let lockfile: unknown;
  try {
    lockfile = JSON.parse(content);
  } catch {
    return `${header}; parse=INVALID_JSON]`;
  }
  const packages = asRecord(asRecord(lockfile)?.packages);
  if (!packages) return `${header}; parse=NO_PACKAGES_OBJECT]`;

  const hosts = new Map<string, number>();
  const outsideRegistry: string[] = [];
  const withoutIntegrity: string[] = [];
  let installed = 0;
  for (const [location, entry] of Object.entries(packages)) {
    // The empty key is the project itself; links point at local folders and carry no tarball.
    const record = asRecord(entry);
    if (location === '' || !record || record.link === true) continue;
    installed += 1;
    const resolved = typeof record.resolved === 'string' ? record.resolved : '';
    const host = resolvedHost(resolved);
    hosts.set(host, (hosts.get(host) ?? 0) + 1);
    if (!resolved.startsWith(NPM_REGISTRY_PREFIX)) outsideRegistry.push(`${quoted(location)} -> ${resolved ? quoted(resolved) : '(no resolved)'}`);
    if (typeof record.integrity !== 'string' || !/^sha(256|384|512)-/.test(record.integrity)) withoutIntegrity.push(quoted(location));
  }
  const hostList = [...hosts.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([host, count]) => `${quoted(host)}=${count}`).join(', ');
  const version = asRecord(lockfile)?.lockfileVersion;
  return [
    `${header}; lockfileVersion=${Number.isSafeInteger(version) ? String(version) : 'invalid'}; installedPackages=${installed}`,
    `resolvedHosts: ${hostList || '(none)'}`,
    `packagesResolvedOutsideRegistry (${outsideRegistry.length}): ${listAnomalies(outsideRegistry)}`,
    `packagesWithoutIntegrity (${withoutIntegrity.length}): ${listAnomalies(withoutIntegrity)}]`,
  ].join('\n');
}

function resolvedHost(resolved: string): string {
  if (!resolved) return '(no resolved)';
  try {
    const url = new URL(resolved);
    return `${url.protocol}//${url.host}`;
  } catch {
    return '(not a URL)';
  }
}

function listAnomalies(entries: string[]): string {
  if (entries.length === 0) return 'none';
  const listed = entries.slice(0, MAX_LISTED_LOCKFILE_ANOMALIES).join('; ');
  return entries.length > MAX_LISTED_LOCKFILE_ANOMALIES ? `${listed}; ... and ${entries.length - MAX_LISTED_LOCKFILE_ANOMALIES} more` : listed;
}

function encodedFixtureMarker(path: string, content: string): string {
  return `[encoded test fixture source omitted from model context; path=${path}; sourceBytes=${Buffer.byteLength(content, 'utf8')}; sourceSha256=${hash(content)}]`;
}

function isVaultManagedDocument(path: string): boolean {
  return path === VAULT_CONTEXT_MAP_PATH
    || (path.startsWith(VAULT_ROOT) && path.endsWith('.md'));
}

export function summarizeAgentChecks(runs: Array<Record<string, unknown>>, expectedHeadSha?: string): AgentReviewInputs['checks'] {
  const currentRuns = expectedHeadSha ? runs.filter((run) => run.head_sha === expectedHeadSha) : runs;
  const status = (matcher: (name: string) => boolean, appId?: number): string => {
    const matches = latestRunsByName(currentRuns.filter((run) => matcher(String(run.name ?? ''))
      && (appId === undefined || asRecord(run.app)?.id === appId)));
    if (matches.length === 0) return 'MISSING';
    if (matches.some((run) => run.status !== 'completed')) return 'PENDING';
    if (matches.some((run) => run.conclusion !== 'success')) return 'FAILED';
    return 'PASSED';
  };
  // FitHub (DEV-0117, DEV-0016): sem SonarQube; um estado por job exigido, sempre das Actions.
  return Object.fromEntries(Object.entries(REQUIRED_AGENT_CHECKS)
    .map(([key, matcher]) => [key, status(matcher, ACTIONS_APP_ID)])) as AgentReviewInputs['checks'];
}

export function fingerprintAgentReviewInputs(inputs: AgentReviewInputs): Record<string, unknown> {
  return {
    diffSha256: hash(inputs.diff),
    changedFiles: inputs.changedFiles ?? [],
    checks: inputs.checks,
    checkRuns: (inputs.checkRuns ?? []).map(({ name, appId, headSha, status, conclusion, actions }) => ({
      name, appId, headSha, status, conclusion,
      actions: actions?.availability === 'available'
        ? { availability: actions.availability, repository: actions.repository, headSha: actions.headSha, workflowPath: actions.workflowPath, runStatus: actions.runStatus, runConclusion: actions.runConclusion, steps: actions.steps }
        : actions,
    })),
    repositoryInstructionsSha256: hash(inputs.repositoryInstructions),
    vaultContext: (inputs.vaultContext ?? []).map(({ path, revision, content }) => ({ path, revision, sha256: hash(content) })),
    sourceFiles: inputs.sourceFiles.map(({ path, revision, content }) => ({ path, revision, sha256: hash(content) })),
    ciConfiguration: (inputs.ciConfiguration ?? []).map(({ path, headSha, originalSha256, content }) => ({ path, headSha, originalSha256, sha256: hash(content) })),
  };
}

export function summarizeCheckRunEvidence(runs: Array<Record<string, unknown>>, expectedHeadSha: string): AgentCheckEvidence[] {
  // FitHub (DEV-0128): todos os checks do HEAD entram, exceto os voláteis da automação; as etapas
  // das Actions continuam só para os jobs de teste (abaixo).
  const relevantRuns = latestRunsByName(runs.filter((run) => run.head_sha === expectedHeadSha
    && !CONTEXT_EXCLUDED_CHECKS.includes(String(run.name ?? ''))));
  return relevantRuns
    .map((run) => ({
      name: String(run.name ?? 'Unnamed check'),
      appId: typeof asRecord(run.app)?.id === 'number' ? asRecord(run.app)!.id as number : null,
      headSha: expectedHeadSha,
      status: String(run.status ?? 'unknown'),
      conclusion: typeof run.conclusion === 'string' ? run.conclusion : null,
      detailsUrl: typeof run.details_url === 'string' ? run.details_url : null,
      completedAt: typeof run.completed_at === 'string' ? run.completed_at : null,
      ...(isTestCheck(String(run.name ?? '')) ? { actions: unavailableActionsEvidence() } : {}),
    }))
    .sort((left, right) => left.name.localeCompare(right.name) || (left.appId ?? 0) - (right.appId ?? 0));
}

async function readPullRequestDiff(fetchImpl: typeof fetch, repository: string, pullRequestNumber: number, token: string): Promise<string | undefined> {
  try {
    return await requestGitHubText(fetchImpl, `/repos/${repository}/pulls/${pullRequestNumber}`, token, 'application/vnd.github.v3.diff');
  } catch (error) {
    // 406: o diff passou do limite do GitHub (300 arquivos). Qualquer outro erro continua falhando fechado.
    if (error instanceof GitHubApiError && error.status === 406) return undefined;
    throw error;
  }
}

/**
 * Diff equivalente montado a partir de `/pulls/N/files` (DEV-0137): patch de cada arquivo novo ou alterado;
 * arquivos removidos viram uma linha cada (caminho e linhas removidas) num bloco inicial, sem o conteúdo apagado.
 */
export function synthesizeDiffFromFiles(files: Array<Record<string, unknown>>): string {
  // DEV-0138: removido não traz conteúdo novo, e o cabeçalho de 5 linhas por arquivo estourava o teto do
  // diff numa remoção em massa. Os removidos vão num bloco compacto, uma linha por caminho, ANTES de
  // qualquer seção "diff --git": depois dela, o sanitizeAgentDiff o trataria como parte daquela seção.
  const isRemoved = (file: Record<string, unknown>) => file.status === 'removed';
  const removed = files.filter(isRemoved);
  const removedBlock = removed.length === 0 ? [] : [[
    `[arquivos removidos: ${removed.length}; conteúdo omitido do contexto]`,
    ...removed.map((file) => `D ${String(file.filename ?? '')} (${Number(file.deletions ?? 0)} linha(s) removida(s))`),
  ].join('\n')];
  const sections = files.filter((file) => !isRemoved(file)).map((file) => {
    const path = String(file.filename ?? '');
    const status = String(file.status ?? '');
    const previous = typeof file.previous_filename === 'string' ? file.previous_filename : path;
    const header = `diff --git a/${status === 'renamed' ? previous : path} b/${path}`;
    const patch = typeof file.patch === 'string' ? file.patch : '[patch omitido pelo GitHub (arquivo grande ou binário)]';
    const from = status === 'added' ? '/dev/null' : `a/${previous}`;
    return `${header}
--- ${from}
+++ b/${path}
${patch}`;
  });
  return [...removedBlock, ...sections].join('\n');
}

/**
 * DEV-0140: a cobertura do Code Review cabe em MAX_REVIEW_COVERAGE_FILES itens. Acima disso, os removidos
 * saem da matriz: não trazem conteúdo novo e já entram no diff pelo bloco de removidos. Novos e alterados
 * continuam obrigatórios em qualquer tamanho de PR; a classificação do trilho segue usando a lista completa.
 */
export function selectReviewCoverageFiles(files: Array<Record<string, unknown>>): string[] {
  const all = files.map((file) => String(file.filename ?? ''));
  if (all.length <= MAX_REVIEW_COVERAGE_FILES) return all;
  return files.filter((file) => file.status !== 'removed').map((file) => String(file.filename ?? ''));
}

function isTestCheck(name: string): boolean {
  // Jobs de teste reconhecidos (repository-policy.ts): o QA vê as etapas verificadas de cada um.
  return KNOWN_TEST_JOBS.some((matches) => matches(name));
}

function latestRunsByName(runs: Array<Record<string, unknown>>): Array<Record<string, unknown>> {
  const latest = new Map<string, Record<string, unknown>>();
  for (const run of runs) {
    const app = asRecord(run.app);
    const key = `${String(run.name ?? 'Unnamed check')}:${String(app?.id ?? '')}`;
    const prior = latest.get(key);
    const timestamp = String(run.started_at ?? run.completed_at ?? run.created_at ?? '');
    const priorTimestamp = String(prior?.started_at ?? prior?.completed_at ?? prior?.created_at ?? '');
    if (!prior || timestamp.localeCompare(priorTimestamp) >= 0) latest.set(key, run);
  }
  return [...latest.values()];
}

function readCheckRuns(fetchImpl: typeof fetch, repository: string, sha: string, token: string): Promise<Array<Record<string, unknown>>> {
  return readCheckRunPages(fetchImpl, repository, sha, token);
}

async function readCheckRunPages(fetchImpl: typeof fetch, repository: string, sha: string, token: string): Promise<Array<Record<string, unknown>>> {
  const runs: Array<Record<string, unknown>> = [];
  for (let page = 1; page <= 100; page += 1) {
    const payload = await requestGitHubApi<{ check_runs?: Array<Record<string, unknown>> }>(
      fetchImpl,
      `/repos/${repository}/commits/${sha}/check-runs?per_page=100&page=${page}`,
      token,
    );
    const batch = payload.check_runs ?? [];
    runs.push(...batch);
    if (batch.length < 100) return runs;
  }
  throw new Error('GitHub check-run pagination exceeded the supported page limit.');
}

function hash(value: string): string {
  return createHash('sha256').update(value).digest('hex');
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null ? value as Record<string, unknown> : undefined;
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}
