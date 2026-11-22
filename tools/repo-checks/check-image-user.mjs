#!/usr/bin/env node
// Guarda das imagens dos serviços (DEV-0017): o container não roda como root.
//
//   node tools/repo-checks/check-image-user.mjs <imagem>...
//
// Lê o `User` da configuração da imagem (docker image inspect). Vazio, `root`, uid 0 ou grupo root
// são recusados: a imagem precisa declarar um usuário sem privilégio no Dockerfile (USER).
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/** true quando o usuário declarado na imagem não é root (nem vazio, que também vira root). */
export function isNonRootUser(user) {
  const value = (user ?? '').trim();
  if (!value) return false;
  const [name, group] = value.split(':');
  const isRoot = (part) => part === 'root' || /^0+$/.test(part);
  return !isRoot(name) && (group === undefined || !isRoot(group));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const images = process.argv.slice(2);
  if (images.length === 0) {
    console.error('Informe ao menos uma imagem.');
    process.exit(2);
  }
  let failed = false;
  for (const image of images) {
    const user = execFileSync('docker', ['image', 'inspect', '--format', '{{.Config.User}}', image], { encoding: 'utf8' }).trim();
    if (isNonRootUser(user)) {
      console.log(`${image}: roda como ${user}`);
    } else {
      console.error(`ERRO: ${image} roda como root (User="${user}"). Declare USER com um usuário sem privilégio no Dockerfile.`);
      failed = true;
    }
  }
  process.exit(failed ? 1 : 0);
}
