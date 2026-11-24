# Aprendizados: armazenamento de arquivos

Regras sobre arquivos guardados fora do banco (bucket S3 do SeaweedFS). Ler antes de mexer em fotos ou em qualquer
envio por URL pré-assinada.

## arquivo-enviado-direto-ao-bucket-tem-limpeza-propria

**Regra:** quando o app envia o arquivo direto ao bucket (URL pré-assinada de `PUT`), o objeto existe antes de
existir a linha no banco. Toda limpeza precisa alcançar também os objetos que nunca foram confirmados:

- exclusão (da avaliação, da pose, da conta) apaga **pelo prefixo** das chaves, não pela lista das linhas do banco;
- confirmação grava a linha nova **antes** de apagar qualquer objeto, e só então limpa os outros da mesma pose;
- um expurgo periódico apaga os objetos antigos que não são de nenhuma linha confirmada;
- a foto confirmada fica numa chave **diferente** da chave do envio, copiada no servidor: a URL pré-assinada de
  `PUT` pode ser usada de novo enquanto vale, e o tamanho não entra na assinatura;
- cada uma dessas limpezas tem teste que confere o objeto no bucket, e mutante.

Arquivo de foto do corpo é dado sensível (LGPD): um objeto órfão fere o direito de exclusão do titular.

### Ocorrências

- **Revisão:** Code Review do PR #231 em 24/11/2026 (2 MEDIUM, 1 LOW) — DEV-0048 (#40) · **PR:** #231
  - **Impacto:** um envio não confirmado (falha de rede, app fechado, nova tentativa com outro envio) ficava no
    bucket para sempre, mesmo depois de o cliente excluir a avaliação. A limpeza na exclusão da avaliação existia,
    mas sem teste: remover a chamada não derrubava nada. Na confirmação, o arquivo anterior era apagado antes de
    gravar a linha nova.
  - **Correção:** exclusão por prefixo (avaliação e pose), confirmação que grava primeiro e depois limpa a pose, e
    `PhotoPurgeScheduler`, que apaga a cada hora os envios não confirmados com mais de 1 hora.
  - **Prevenção:** testes no `PhotoApiTest` que conferem os objetos no bucket (confirmado e pendente) e mutantes M8 a
    M13 de `mutations/DEV-0048.json`.
- **Revisão:** rodada 3 do Code Review do PR #231 em 24/11/2026 (MEDIUM) — DEV-0048 (#40) · **PR:** #231
  - **Impacto:** depois de confirmar, um novo `PUT` na mesma URL (ainda válida) trocava o arquivo da foto guardada
    por um de qualquer tamanho, passando por cima do limite de 10 MB conferido na confirmação.
  - **Correção:** a confirmação copia o envio, no servidor, para uma chave `photo-<uuid>` que nenhuma URL alcança,
    confere tipo e tamanho na cópia e apaga o objeto do envio.
  - **Prevenção:** `putDeNovoNaUrlDeEnvioDepoisDeConfirmarNaoMudaAFotoGuardada` e o mutante M14.
