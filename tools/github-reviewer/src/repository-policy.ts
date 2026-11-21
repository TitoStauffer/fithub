/**
 * Política do repositório FitHub (DEV-0117): o que difere do CashIn, de onde este pacote veio.
 *
 * - Sem SonarQube: o SonarQube Cloud gratuito só atende repositórios públicos, e o fithub é privado.
 * - Sem proteção de branch: no plano gratuito, repositório privado não tem branch protection nem
 *   rulesets (a API responde 403). A regra "merge só com READY_TO_MERGE" é escrita no AGENTS.md; o
 *   Merge Readiness não exige proteção e registra isso nas evidências.
 * - Checks de CI: por enquanto só existe o job `GitHub Reviewer`; os jobs dos serviços e apps entram
 *   na DEV-0016 e devem ser acrescentados aqui.
 */

/** Jobs do workflow `CI` que precisam passar no HEAD antes dos agentes e do merge. */
export const REQUIRED_AGENT_CHECKS = {
  // `Automation Tools` é o nome antigo do mesmo job no CashIn, aceito por compatibilidade.
  githubReviewer: (name: string) => /(^|\/\s*)(?:automation tools|github reviewer)$/i.test(name),
} as const;

export type RequiredAgentCheck = keyof typeof REQUIRED_AGENT_CHECKS;

/**
 * Jobs de teste cujas etapas verificadas nas Actions entram no contexto do QA, exigidos ou não.
 * `API`, `Web` e `Mobile` são os nomes do CashIn, mantidos como reconhecedores até a DEV-0016 definir
 * os jobs dos serviços e apps do FitHub — quando ela definir, trocar aqui e em REQUIRED_AGENT_CHECKS.
 */
export const KNOWN_TEST_JOBS: ReadonlyArray<(name: string) => boolean> = [
  ...Object.values(REQUIRED_AGENT_CHECKS),
  (name: string) => /(^|\/\s*)api(\s|$)/i.test(name),
  (name: string) => /(^|\/\s*)web(\s|$)/i.test(name),
  (name: string) => /(^|\/\s*)mobile(\s|$)/i.test(name),
];

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
