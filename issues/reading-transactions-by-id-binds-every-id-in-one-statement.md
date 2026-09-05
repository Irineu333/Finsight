---
area: ledger
severity: medium
type: crash
---

# A leitura por identidade em lote liga todos os ids num só statement, e estoura o teto do SQLite

## Invariante

Toda leitura por `IN (:ids)` do razão é pedida em blocos que o SQLite aceita ligar.

Hoje é falso: `TransactionRepository.getTransactionsByIds` faz duas dessas leituras passando a
coleção inteira, enquanto as outras três do mesmo arquivo passam por `readByIdentity`.

## Mecânica

O Room escreve **um host parameter por elemento** de um `IN (:ids)` e não fragmenta nada. O teto
existe e está medido contra o driver que o projeto linka: `MAX_BOUND_IDENTITIES = 900`, com a nota
de que 32 767 é onde o `sqlite-bundled` recusa com `too many SQL variables`.

`readByIdentity` é o dono desse fracionamento, e três leituras o usam — as legs em `toDomain`,
`getExistingTransactionIds`, e a leitura por identidade do `TransactionDao`. `getTransactionsByIds`
foi escrita sem ele e chama os dois DAOs direto.

A divergência tem registro no próprio código: a KDoc de `EntryDao.getByTransactionIds` afirma que
"chunking to stay under it belongs to the caller (`TransactionRepository`)". Para o chamador em
`toDomain` isso é verdade; para `getTransactionsByIds`, não.

## Evidência

- `TransactionRepository.getTransactionsByIds()` — chama `entryDao.getByTransactionIds(ids)` e
  `transactionDao.getByIds(ids)` sem fracionar
- `TransactionRepository.toDomain()` — o mesmo `entryDao::getByTransactionIds`, este via
  `readByIdentity`
- `TransactionRepository.getExistingTransactionIds()` — via `readByIdentity`
- `MAX_BOUND_IDENTITIES` (`core/ledger` — `database/BoundIdentities.kt`) — o teto e a medição
- `EntryDao.getByTransactionIds` — a KDoc que atribui o fracionamento ao chamador
- `RecurringViewModel.ledgerRowsOf()` — o único chamador de produção, com um id por ciclo lançado
  do mês

## Consequência

Passando o teto, a tela de recorrências não mostra número errado: ela lança. É o modo de falha que
`BoundIdentities` descreve — "the failure lands where the size is, and nowhere smaller ever sees it".

O alcance é o que desce a faixa um degrau: o conjunto tem um id por recorrência com ciclo lançado no
mês, de modo que chegar a 900 exige 900 templates recorrentes lançados no mesmo mês. É a
configuração rara da régua, não uma impossibilidade — e o teto continua real.

## Sugestão

Passar as duas leituras por `readByIdentity`, como as vizinhas. A leitura das legs é agrupada por
`transactionId` logo depois e não se importa com a ordem; a das linhas responde ordenada
(`date DESC, id DESC`), e concatenar blocos quebraria essa ordem — ou se reordena depois, ou se
declara na KDoc que a ordem é por bloco. Não vinculante.
