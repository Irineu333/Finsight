# 007 — o log completo do agente faz três queries por linha, e repete a passada inteira a cada emissão

**Área:** mcp (UI) · **Tipo:** performance · **Severidade:** média · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

A tela de atividade mapeia cada entrada do log por meio de uma leitura de repositório por linha, e o
log guarda até 5 000 linhas. Cada uma dessas leituras são **três** queries, não uma. Qualquer insert
ou poda invalida o flow do Room e a passada inteira roda de novo.

## Evidência

`feature/mcp/impl/.../ui/screen/mcpActivity/McpActivityViewModel.kt:32-40`

```kotlin
val uiState = activityRepository.observeAll()
    .mapLatest { entries ->
        McpActivityUiState(entries = entries.map { it.toUi(transactionRepository) }, isLoading = false)
    }
    .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), …)
```

`feature/mcp/impl/.../ui/screen/mcp/McpActivityMapping.kt:27-28`

```kotlin
isTargetGone = target is McpActivityTarget.Posting &&
    transactionRepository.getTransactionById(target.transactionId) == null,
```

E essa chamada são três idas ao banco, não uma:

`core/ledger/.../TransactionRepository.kt:143-146` → `transactionDao.getById(id)` **mais**
`ledgerAccounts()` (`:61-62`, um `SELECT` de todo o plano de contas) **mais**, dentro de `toDomain`
(`:132-136`), `entryDao.getByTransactionId(id)`.

O limite do número de linhas: `core/database/.../AgentActivityRetention.kt:32` — `MAX_ENTRIES = 5_000`.

Ou seja, um log com 5 000 entradas referenciando transações custa ~15 000 queries por emissão, para
responder um booleano por linha.

## O que **não** está errado

Duas coisas que vale enunciar, para que a correção seja apontada no lugar certo:

- **O glance da seção está bem.** `McpViewModel.kt:52-53` usa
  `activityRepository.observeRecent(SECTION_PREVIEW)`, uma página pequena. Só a tela do log completo
  tem esse formato.
- **As queries não rodam na main thread.** As funções suspend de DAO do Room saltam para o dispatcher
  do próprio banco; o que fica em `Dispatchers.Main.immediate` é o coletor e uma retomada por linha.
  (É a mesma leitura registrada como item 22 de `docs/auditoria-bugs-2026-07.md`.)

## Correção sugerida

Fazer a pergunta uma vez para a página:

```kotlin
val referenced = entries.mapNotNull { (it.reference?.toTarget() as? McpActivityTarget.Posting)?.transactionId }
val alive = transactionRepository.getTransactionsByIds(referenced).mapTo(mutableSetOf()) { it.id }
```

`getTransactionsByIds` já existe (`core/ledger/.../TransactionRepository.kt:251-254`) e ao menos lê o
plano de contas uma única vez; um `SELECT id FROM transactions WHERE id IN (…)` no nível do DAO
transformaria isso numa query só, que é tudo de que se precisa aqui — o mapeamento só quer saber
quais ids ainda existem.

Aproveitar para pôr um `.flowOn` na transformação, tirando o trabalho por linha do coletor.
