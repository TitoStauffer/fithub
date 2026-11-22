/**
 * Política do repositório FitHub (DEV-0117): o que difere do CashIn, de onde este pacote veio.
 *
 * - Sem SonarQube: o SonarQube Cloud gratuito só atende repositórios públicos, e o fithub é privado.
 * - Sem proteção de branch: no plano gratuito, repositório privado não tem branch protection nem
 *   rulesets (a API responde 403). A regra "merge só com READY_TO_MERGE" é escrita no AGENTS.md; o
 *   Merge Readiness não exige proteção e registra isso nas evidências.
 * - Checks de CI (DEV-0016): um job por área do workflow `CI` (`GitHub Reviewer`, `Java Services`,
 *   `Apps`, `Local Infrastructure`). Todos sempre rodam; a área que não mudou termina com sucesso sem
 *   trabalhar, e isso aparece nas etapas do job.
 */

/**
 * Nomes dos jobs do workflow `CI` que precisam passar no HEAD antes dos agentes e do merge, além do
 * `GitHub Reviewer` (que tem nome antigo aceito, abaixo). Mudar um nome aqui exige mudar no ci.yml.
 */
export const REQUIRED_CI_JOBS = {
  javaServices: 'Java Services',
  apps: 'Apps',
  localInfrastructure: 'Local Infrastructure',
} as const;

function jobNamed(job: string): (name: string) => boolean {
  // Aceita o nome do job sozinho ou com o prefixo do workflow ("CI / Apps").
  const pattern = new RegExp(`(^|/\\s*)${job.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, 'i');
  return (name: string) => pattern.test(name);
}

/** Jobs exigidos, pela chave usada no resumo de checks dos agentes. */
export const REQUIRED_AGENT_CHECKS = {
  // `Automation Tools` é o nome antigo do mesmo job no CashIn, aceito por compatibilidade.
  githubReviewer: (name: string) => /(^|\/\s*)(?:automation tools|github reviewer)$/i.test(name),
  javaServices: jobNamed(REQUIRED_CI_JOBS.javaServices),
  apps: jobNamed(REQUIRED_CI_JOBS.apps),
  localInfrastructure: jobNamed(REQUIRED_CI_JOBS.localInfrastructure),
} as const;

export type RequiredAgentCheck = keyof typeof REQUIRED_AGENT_CHECKS;

/** Jobs de teste cujas etapas verificadas nas Actions entram no contexto do QA: os exigidos acima. */
export const KNOWN_TEST_JOBS: ReadonlyArray<(name: string) => boolean> = Object.values(REQUIRED_AGENT_CHECKS);

/** Status contexts (além dos jobs acima) exigidos pelo Merge Readiness. */
export const REQUIRED_STATUS_CONTEXTS = ['PR title'] as const;

/**
 * false: o plano não oferece proteção de branch para este repositório, então o Merge Readiness não a
 * consulta nem a exige. Trocar para true quando o repositório tiver proteção (GitHub Pro ou público).
 */
export const BRANCH_PROTECTION_AVAILABLE = false;

export const BRANCH_PROTECTION_UNAVAILABLE_NOTE =
  'Proteção de branch indisponível no plano atual (repositório privado gratuito): não consultada nem exigida. '
  + 'A regra de merge só com READY_TO_MERGE está no AGENTS.md.';

/**
 * Raiz do "vault" de contexto lido pelos agentes (DEV-0119): a documentação oficial do FitHub.
 * Precisa ter `README.md` (índice), `estado-atual.md` e `context-map.json` (caminhos → notas).
 */
export const VAULT_ROOT = 'docs/00-oficial/';
export const VAULT_CONTEXT_MAP_PATH = `${VAULT_ROOT}context-map.json`;

/**
 * Arquivos de configuração de CI que os agentes recebem como contexto (scripts dos pacotes e o
 * workflow `CI`), lidos no HEAD do PR (DEV-0118). Todos precisam existir: um ausente falha fechado.
 * O workflow tem os valores de `env` omitidos; um `package.json` entra só com os `scripts`; os scripts
 * `.mjs` de tools/repo-checks que decidem o que o CI roda e barram teste pulado (DEV-0016) entram
 * inteiros. Ao criar app com `package.json` próprio que tenha `scripts`, acrescente-o aqui.
 */
export const CI_WORKFLOW_PATH = '.github/workflows/ci.yml';
export const CI_SNAPSHOT_PATHS: readonly string[] = [
  CI_WORKFLOW_PATH,
  'tools/github-reviewer/package.json',
  'tools/repo-checks/ci-changes.mjs',
  'tools/repo-checks/check-skipped-tests.mjs',
  // Scripts lint, typecheck, test e build que o job Apps roda por `pnpm -r` (DEV-0020, DEV-0021, DEV-0022).
  'apps/web/package.json',
  'apps/mobile/package.json',
  'packages/api-types/package.json',
];

/**
 * Check runs da própria automação que reexecutam por evento (edição do PR, revisão publicada) e por
 * isso ficam fora da evidência dos agentes (DEV-0128): uma reexecução no meio da orquestração mudaria
 * a impressão digital do contexto entre as etapas. Todos os outros checks do HEAD entram.
 */
export const CONTEXT_EXCLUDED_CHECKS: readonly string[] = ['Sincronizar quadro', 'Validate PR metadata', 'Review event relay', 'Desmarcar aprovações do HEAD anterior'];
