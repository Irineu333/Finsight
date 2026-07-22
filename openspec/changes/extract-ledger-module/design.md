## Context

O razão de partidas dobradas é a única fonte de verdade monetária do app, mas está distribuído por quatro módulos:

| Onde | O que |
|---|---|
| `core:model` | `Account`, `AccountType`, `Entry`, `Transaction`, `TransactionIntent`/`Leg`, `SystemAccount`, `extension/Ledger.kt` |
| `core:database` | `EntryEntity`, `AccountEntity`, `TransactionEntity`, `EntryDao`, `AccountDao` |
| `feature:transactions:api` | `IEntryRepository` (15 métodos), `ITransactionRepository`, `CalculateBalanceUseCase` |
| `feature:transactions:impl` | `EntryRepository`, `LedgerEntryWriter`, `TransactionRepository` |

Oito das nove features restantes declaram `implementation(projects.feature.transactions.api)`. Nenhuma delas quer a tela de transações.

Uma constatação orienta todo o desenho: **o SQL do razão já é razão puro.** Auditando `EntryDao`, de ~18 queries, todas menos três operam exclusivamente sobre `entries × accounts × transactions` com vocabulário `AccountType`/sinal/data. Nenhuma faz JOIN com `categories`, `invoices`, `credit_cards` ou `budgets`. As três exceções — `invoiceNaturalBalance`, `invoicePeriodTotals`, `categoryTotalsForInvoices` — tocam a coluna `entries.invoiceId`, e apenas ela.

A contaminação é, portanto, quase toda de **vocabulário e de forma de modelo**, não de mecanismo. Isso torna a extração muito menos arriscada do que o número de pontos de contato sugere, e é o que autoriza a inversão de dependência descrita adiante.

Ressalva registrada: o SQL não é "razão neutro". `accountPeriodTotals` codifica "contra-perna `LIABILITY` ⇒ pagamento de fatura" e `reportStats` codifica a exclusão de transferência interna. Isso é derivável do razão — logo, legítimo nele pelo Derivation Rule — mas é o razão *mais uma teoria de leitura própria deste app*. `:core:ledger` é a biblioteca contábil **deste** app, não uma genérica.

## Goals / Non-Goals

**Goals:**

- O razão vive num módulo próprio que não depende de nenhum módulo do app.
- A separação razão/fachada é imposta pelo compilador, não por disciplina.
- Fachadas ligam-se ao razão por identidade opaca (`accountId`, `dimensionId`), nunca por tipo.
- O plano de contas contém apenas o que é contábil.
- Uma leitura só sai do razão quando não é derivável dele.

**Non-Goals:**

- Substituir o SQL nomeado por uma linguagem de consulta genérica. Room exige SQL estático; consulta dinâmica viraria `@RawQuery` e perderia verificação em tempo de compilação. O SQL permanece nomeado no DAO — o que muda é o seu vocabulário.
- Suportar múltiplas dimensões por entry.
- Mover `installmentId`/`installmentNumber`/`recurringId`/`recurringCycle` para fora de `transactions`.
- Introduzir hierarquia de categorias, ou permitir que uma categoria cruze `INCOME`/`EXPENSE`.
- Alterar a semântica de qualquer figura exibida ao usuário. Todo número renderizado hoje deve permanecer idêntico depois.

## Decisions

### D1 — Dependência invertida: `core:database → core:ledger`

Room exige que o `AppDatabase` liste todas as entities num único lugar, o que abre duas direções possíveis:

| | Grafo | Consequência |
|---|---|---|
| (a) esperada | `core:ledger → core:database → core:model` | `core:database` contém as entities de fatura, categoria, cartão e orçamento — o razão depende de um módulo que conhece todas as fachadas |
| (b) **escolhida** | `core:database → core:ledger → core:model` | `core:ledger` declara as próprias entities e DAOs, dependendo só de Room; `core:database` monta o `AppDatabase` e as migrações |

Escolhida **(b)**. O argumento decisivo não é elegância: em (a) a independência do razão seria convenção mantida por disciplina, e este projeto impõe suas regras de módulo mecanicamente, por convention plugins. Em (b), `EntryDao` **não consegue** referenciar uma tabela de fachada porque não a enxerga em tempo de compilação. A regra de arquitetura vira regra de compilação.

O custo aparente de (b) — `core:database` declara e migra tabelas das quais não é dono — é ilusório. Migrações Room são inerentemente whole-schema: a própria v9 → v10 precisa tocar `entries`, `categories`, `accounts` e `invoices` na mesma transação. Ownership de migração nunca foi modularizável, e centralizá-la no montador do `AppDatabase` é o único lugar honesto para ela.

A viabilidade de (b) depende da constatação do Context: pós-refatoração, todo JOIN do `EntryDao` é entre tabelas do razão.

### D2 — `dimensions(id, kind)`, opaca no domínio e não no schema

A dimensão é um espaço de identidade de dono do razão. As fachadas guardam `dimensionId`, espelhando exatamente o padrão `facade.accountId` que já existe — nenhum conceito estrutural novo a aprender, e a colisão de ids entre tabelas de fachada é impossível por construção.

Considerada e rejeitada a alternativa `(dimensionKind: String, dimensionId: Long)` sem tabela: perde o FK e o `ON DELETE SET NULL`, e obriga o razão a conhecer as strings de kind.

Considerada e rejeitada a alternativa de `dimensions(id)` totalmente opaca, sem `kind`. A não-colisão entre fatura e categoria na mesma perna é hoje verdade **por disciplina do writer, não por schema**: nada impediria uma dimensão de fatura de pousar numa perna nominal, e o defeito seria silencioso — as somas por categoria sairiam erradas sem erro nenhum. Uma coluna `kind` custa zero, não altera query alguma, e permite ao writer validar a regra de pouso. A opacidade correta é no **domínio** (o razão não sabe o que `INVOICE` significa), não no **schema**.

### D3 — Um slot de dimensão por entry

Verificado que nas cinco formas de transação existentes as dimensões nunca colidem na mesma perna:

```
compra no cartão   LIABILITY(dim=fatura)  +  EXPENSE(dim=categoria)
despesa na conta   ASSET(dim=∅)           +  EXPENSE(dim=categoria)
pagto de fatura    ASSET(dim=∅)           +  LIABILITY(dim=fatura)
transferência      ASSET(dim=∅)           +  ASSET(dim=∅)
ajuste             ASSET(dim=∅)           +  EQUITY(dim=∅)
```

Considerada e adiada a tabela de junção `entry_dimensions`, que permitiria N dimensões por perna. O risco que a desaconselha agora: **`Σ` agrupado por dimensão passa a poder contar a mesma entry duas vezes** quando ela tem duas dimensões, e evitar isso exige que todo agrupamento seja sempre filtrado por um subconjunto conhecido — regra frágil de manter em cada relatório novo. A migração slot único → junção é mecânica, e é melhor pagá-la quando o segundo eixo existir de fato.

### D4 — Categoria como dimensão, e o Derivation Rule

Categoria deixa o plano de contas. Sobram duas contas nominais no app inteiro; "sem categoria" passa a ser `dimensionId = NULL`, e as contas de sistema `UNCATEGORIZED_EXPENSE`/`_INCOME` deixam de existir.

Isto é ortodoxia contábil, não invenção: sistemas reais (contas analíticas do Odoo, dimensões do Dynamics) não criam linha de razão para cada rótulo do usuário. O plano de contas passa a conter apenas o que é contábil — as contas do usuário, os cartões, duas nominais e a de reconciliação.

**O custo, registrado explicitamente.** `Category.type` deixa de ter cópia derivável de `account.type` e passa a ser estado primário da fachada; o writer passa a consultá-lo para escolher a conta nominal em que a perna posta. Isso é uma regressão de propriedade em relação ao Derivation Rule do projeto.

Aceita como **exceção documentada**, com esta justificativa: "esta categoria é de despesa" não é regra derivável do razão — é declaração do usuário no momento da criação. Hoje ela está *codificada* como tipo de conta, o que a faz parecer derivada, mas nada a deriva. É estado primário mudando de casa, não regra perdendo dono. O que muda de verdade é que o writer passa a depender de estado de feature para classificar, e é isso que o requisito deve cercar.

Registrado também: o razão fica **menos autodescritivo**. Hoje `entries + accounts` sozinhos produzem um balancete por categoria, auditável sem tabela de feature. Depois, o razão diz "Despesas: R$ X" e qualquer granularidade exige juntar a dimensão à tabela de categorias. É consequência inerente do modelo de dimensões, aceita.

### D5 — Critério de derivabilidade para o que sai do razão

Uma leitura sai do razão **apenas quando não é derivável de `AccountType`, sinal, período e dimensão**. Nome de fachada não é critério: `invoiceOwed` é `Σ entries da dimensão`, razão puro com nome ruim — renomear resolve, mover não é obrigatório.

Consequência: `accountPeriodTotals` e `reportStats` **ficam** no razão, porque a classificação por contra-perna é teoria do razão. Sob o critério ingênuo ("o nome soa a fachada") ambas teriam sido exportadas, espalhando a mesma regra contábil por dois módulos.

O que efetivamente muda é o **vocabulário** das assinaturas: `invoiceOwed(invoiceId)` → soma por dimensão; `categoryTotalsWithSiblingLeg` → totais por dimensão com perna irmã; `accountFlows.invoicePayment` → o fluxo cuja contra-perna é `LIABILITY`.

### D6 — Intenção de escrita por identidade

`TransactionLeg` e `TransactionIntent` passam a falar `accountId: Long` + `dimensionId: Long?`. Resolver fachada → id passa a ser da feature, que é quem sabe o que a fachada é.

Isto é a metade mais valiosa da limpeza, e não um efeito colateral: é onde morrem os 4 campos de fachada de `TransactionLeg`, os 6 de `Transaction`, as duas dependências de DAO de fachada do writer, e o enum `TransactionTarget` — que em termos de razão é apenas `type ∈ {ASSET, LIABILITY}`.

`ensureSystemAccount` (reconciliação) permanece no writer sem problema: `accounts` é tabela do razão.

## Risks / Trade-offs

**A migração reescreve história contábil e é irreversível na prática.** Colapsar N contas de categoria em duas nominais reescreve o `accountId` de toda perna nominal já gravada.
→ A migração valida `Σ = 0` por transação e por moeda **antes e depois** da reescrita, como teste de migração automatizado, não como cuidado manual. Uma transação que não balanceie após a reescrita aborta a migração. `MigrationLedgerReadParityTest` ganha o par de leituras equivalentes antes/depois para cada figura exibida.

**Duas mudanças de risco muito diferente numa só migração.** Extrair o módulo é quase mecânico; converter categoria em dimensão reescreve dados. Foram deliberadamente empacotadas juntas por decisão do dono do projeto.
→ Ordenar a implementação em fatias que compilam, com a extração do módulo inteiramente concluída e verificada antes de a conversão de categoria começar. A verificação de `Σ = 0` é o portão entre as duas.

**Leituras que operam por `accountId` e passam a precisar de par por dimensão:** `closedLegBlockingChange` (`Ledger.kt`), `hasEntries` e `entryCountInMonth`.
→ Cada uma recebe decisão explícita e registrada: variante por dimensão, ou constatação de que categoria não a usa. `hasEntries` alimenta "posso remover ou só fechar", que é regra de conta permanente e não se aplica a categoria; `entryCountInMonth` é usada para categoria e precisa de variante por dimensão. `closedLegBlockingChange` filtra por `type.isPermanent`, e categoria nunca foi permanente — segue correta sem par.

**O razão fica menos autodescritivo.** Um balancete por categoria deixa de ser produzível só com `entries + accounts`.
→ Aceito como consequência inerente do modelo de dimensões (D4). Mitigado pela coluna `kind`, que mantém o schema legível.

**Dois mecanismos onde havia um.** Toda query de categoria passa a ser bidimensional — conta nominal mais dimensão, com o caso `NULL` presente em todo `GROUP BY`.
→ Reduzido por `kind` no schema e pela validação de pouso no writer, que impedem a classe de defeito silencioso. Não é eliminável: é o preço do modelo escolhido.

**Superfície de mudança ampla:** oito features trocam de dependência e passam a resolver a própria fachada → id.
→ A troca é mecânica e verificada pelo compilador em cada fatia; nenhuma feature muda de comportamento.

## Migration Plan

Ordem da migração v9 → v10, numa única transação:

1. Criar `dimensions(id, kind)`.
2. Emitir uma dimensão `INVOICE` por fatura; preencher `invoices.dimensionId`.
3. Emitir uma dimensão `CATEGORY` por categoria; preencher `categories.dimensionId`.
4. Adicionar `entries.dimensionId` (FK → `dimensions`, `SET NULL`).
5. **Verificar `Σ = 0` por transação e por moeda.** Falha aborta.
6. Copiar `entries.invoiceId` → `dimensionId` via `invoices.dimensionId`; remover `entries.invoiceId`.
7. Garantir as duas contas nominais (`EXPENSE`, `INCOME`).
8. Reescrever cada perna cujo `accountId` é conta de categoria: `accountId` ← nominal do tipo correspondente, `dimensionId` ← dimensão da categoria.
9. Reescrever as pernas em `UNCATEGORIZED_EXPENSE`/`_INCOME`: `accountId` ← nominal, `dimensionId` ← `NULL`.
10. Adicionar `categories.isArchived`, preenchendo a partir de `accounts.isArchived` pelo `accountId` antigo; remover `categories.accountId`.
11. Remover do plano as contas de categoria e as `UNCATEGORIZED_*`, agora sem entries.
12. Remover `transactions.categoryId`.
13. **Verificar `Σ = 0` por transação e por moeda novamente**, e verificar paridade de leitura para cada figura exibida. Falha aborta.

Rollback: não há caminho automático de v10 → v9. A prevenção é o par de verificações nos passos 5 e 13, que aborta a transação inteira antes de qualquer gravação parcial.

## Open Questions

- O `kind` da dimensão é um `enum` do razão ou uma `String` opaca? Um `enum` dá exaustividade ao writer na validação de pouso, mas obriga o razão a enumerar os tipos de fachada existentes — voltando a saber o que é uma fatura. Uma `String` mantém a opacidade do domínio mas perde a exaustividade.
- A regra de pouso ("qual `kind` pode pousar em qual perna") é dado do razão ou é declarada pela feature ao criar a dimensão?
- `:core:ledger` precisa de convention plugin próprio em `build-logic`, ou reusa `finsight.kmp.library` acrescido de Room?
- As duas contas nominais recebem nome exibível ao usuário, ou tornam-se invisíveis na UI como a de reconciliação?
