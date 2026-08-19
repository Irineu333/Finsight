# 028 — Os testes de arquitetura varrem worktrees dentro do repo, e acusam o código delas

**Área:** build / testes · **Tipo:** infraestrutura · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`

## O que está errado

Os testes de arquitetura varrem a árvore de arquivos procurando padrões no código de produção. A
varredura inclui `.claude/worktrees/`, onde o harness cria worktrees git para agentes que trabalham
isolados — cada uma com uma cópia completa do repositório.

Com três worktrees presentes, **onze** testes falham de uma vez, todos acusando o código das cópias:

```
A production site re-denominates an existing row. …
  FOUND: .claude/worktrees/agent-ab30414c282207aa3/core/database/…/Migration11To12.kt

A rate is applied outside the consolidation layer.
  NEW: .claude/worktrees/agent-ab30414c282207aa3/feature/settings/…/RateResolver.kt
  NEW: .claude/worktrees/agent-ad502b35feec0499d/feature/settings/…/RateResolver.kt
```

O mesmo arquivo é contado uma vez por worktree, então um invariante do tipo "existe exatamente um
lugar que faz X" quebra por multiplicidade — e a mensagem culpa o código, que está certo.

Classes atingidas nesta ocorrência: `BaseCurrencyReachTest`, `ConsolidationBoundaryTest`,
`CurrencyRegistrySourceTest`, `CurrencyRelabelIsMigrationOnlyTest`, e mais sete asserções da mesma
família.

## Cenário de falha

Quem rodar `./gradlew jvmTest` enquanto uma worktree de agente existe recebe onze falhas que não têm
nada a ver com a mudança em curso, apontando caminhos que não fazem parte do build. O diagnóstico
custa tempo e a conclusão natural — "quebrei alguma coisa" — é falsa. Uma worktree pode ficar para
trás quando um agente termina sem limpá-la, que foi como isto apareceu.

## Correção sugerida

Excluir da varredura o que não é fonte do módulo: `.claude/`, `build/`, e qualquer diretório de
worktree. O lugar natural é o utilitário que enumera os arquivos para essas classes — a exclusão tem
um dono só, e não uma cópia por teste.

Vale conferir se a varredura já exclui `build/` e passou a incluir `.claude/` por omissão, ou se
nunca teve exclusão nenhuma: a resposta muda se a correção é acrescentar um caminho ou instalar a
noção de "o que conta como fonte".
