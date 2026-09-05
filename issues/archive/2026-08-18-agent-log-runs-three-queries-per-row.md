---
area: mcp
severity: medium
type: performance
verdict: fixed
---

# O log completo do agente faz três queries por linha, e repete a passada inteira a cada emissão

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
## Desfecho

A pergunta passou a ser feita uma vez para a página. `toUi` deixou de ser de `AgentActivity` e passou
a ser de `List<AgentActivity>`: reúne os ids referenciados, pergunta uma vez quais ainda existem, e
mapeia cada linha contra esse conjunto. O mapeamento por linha virou puro.

A leitura nova é de **identidades, não de linhas** — `getExistingTransactionIds`, sobre um
`SELECT id FROM transactions WHERE id IN (:ids)`. É o que o mapeamento precisa saber, e nunca toca o
plano de contas, que era metade do custo das três queries por linha.

O glance da seção (`McpViewModel`), que já estava correto por ler uma página pequena, passou a usar o
mesmo mapeamento: 5 linhas, 1 leitura em vez de 5.

## O teto de host parameters, medido

Um `IN (:ids)` sem limite trocaria este defeito por um pior: estouraria exatamente no ledger grande
que torna o lote necessário. O teto foi **medido** contra o driver real do projeto
(`BundledSQLiteDriver`), por busca binária, não assumido:

```
PROBE size=32768 FAILED: too many SQL variables
PROBE RESULT: largest accepted = 32766, smallest rejected = 32767
```

O fatiamento ficou em **900**, em `BoundIdentities.kt` — 36× abaixo do medido, e abaixo dos 999 que
SQLite anteriores ao 3.32 compilam, para que o número sobreviva ao app um dia ler por um driver de
plataforma. O log de 5 000 linhas custa 6 queries, não 15 000.

`TransactionIdentityReadTest` prova os dois lados: 120 000 identidades são respondidas, e uma leitura
mais larga que uma fatia devolve cada linha exatamente uma vez. Conferido que morde — sem o
fatiamento, as duas falham com `SQLiteException`.

## O teste veio antes

`McpActivityViewModelTest` foi escrito **antes da correção**, contra o cenário de falha desta issue, e
nasceu vermelho: 200 linhas custavam 200 leituras contra 1 para uma linha só. Ele afirma a forma do
custo e não a da solução — não nomeia lote, nem `IN`, nem método —, e um terceiro teste impede que a
economia venha de parar de fazer a pergunta.

Duas correções foram feitas no próprio teste, e nenhuma nas asserções:

- ele lia `uiState.value` de forma síncrona, o que o tornava falsificável por qualquer `flowOn` para
  um dispatcher real — o estado inicial responderia `0 == 0` e as duas asserções de custo passariam
  medindo nada. Agora espera o estado mapeado, e checa que ele **foi** mapeado antes de medir;
- os ViewModels sobreviviam ao teste que os criava, e o `WhileSubscribed(5000)` os fazia ressuscitar
  depois do `resetMain`, vazando `UncaughtExceptionsBeforeTest` para o teste seguinte. O escopo agora
  é cancelado.

Com o teste agnóstico ao dispatcher, o `flowOn` ficou em `Dispatchers.Default`: `Unconfined` roda o
mapeamento onde o log emitir, o que faz de "fora da main thread" uma propriedade do upstream em vez
de uma garantia feita aqui.

## Onde esta issue estava errada

A "Correção sugerida" afirma que `getTransactionsByIds` *"já existe"* em `TransactionRepository.kt:251-254`.
**Não existe** — não há nenhuma ocorrência desse nome no repositório, e a interface só declara
`getTransactionById`. O que existe por ali é `deleteTransactionsByIds`, que é escrita. A abordagem
sugerida foi escrita contra um método que não está no disco.
