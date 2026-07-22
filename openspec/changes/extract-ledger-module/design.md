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

**Fallback para (a), se (b) esbarrar em problema crítico.** (b) apoia-se em premissas sobre o Room que ainda não foram confirmadas contra a ferramenta (ver D9 e a tarefa 2.0). São problemas **críticos**, que disparam o fallback: o Room não gerar implementação para os DAOs do razão de forma utilizável a partir do `AppDatabase`; o `LedgerDatabase` de verificação entrar em conflito irreconciliável com o `AppDatabase` na exportação de schema ou no código gerado; a migração em `:core:database` não conseguir referenciar o schema das tabelas declaradas em `:core:ledger`; ou o arranjo não funcionar em algum target (iOS/native/JVM). Não são críticos: verbosidade, código gerado duplicado, ou configuração de KSP mais chata.

Diante de um deles, a ordem é: **documentar o problema concreto** — qual premissa caiu, com o erro observado — e adotar (a) como fallback, `core:ledger → core:database`. O que se perde é exatamente o argumento decisivo de (b): a separação deixa de ser garantida pelo compilador e volta a ser convenção.

Para que a garantia não desapareça em silêncio junto com a direção, (a) SHALL vir acompanhada de um **mecanismo compensatório**: um teste que leia as strings SQL dos `@Query` do razão e falhe se alguma referenciar tabela que não seja do razão. É mais fraco que o compilador — roda depois, não durante — mas mantém a regra mecânica em vez de deixá-la para a revisão. O fallback MUST NOT ser adotado sem ele.

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

### D7 — `DimensionKind` é enum do razão; o nome não é o significado

`Dimension.kind` é um `enum` declarado em `:core:ledger` — `DimensionKind` — persistido pelo nome. Cada entrada carrega um único dado: `landsOn: Set<AccountType>`, o conjunto de naturezas de conta em que uma dimensão daquele kind pode pousar (`INVOICE → {LIABILITY}`, `CATEGORY → {INCOME, EXPENSE}`).

A objeção óbvia: o razão passa a enumerar nomes de fachada. A resposta é um re-escopo do princípio de D2: a opacidade protegida é **comportamental** — nenhuma query junta tabela de fachada, nenhum ramo de agregação depende do kind, e o writer valida com `account.type in kind.landsOn`, uniforme, sem `when` por kind. O que o enum contém é um rótulo e um conjunto de naturezas; `INVOICE` ali é um nome legível para humanos, não um conceito que o razão manipula. Precedente idêntico já existe no próprio razão: o SQL de `AccountDao` grava literais `'ASSET'` sem que isso faça o DAO saber o que é um ativo.

Alternativas rejeitadas:

- **`String` opaca** — o typo silencioso é exatamente a classe de defeito que o kind existe para matar (soma por dimensão errada, sem erro); perde a exaustividade do `when` e da validação; e a opacidade que preservaria é a textual, que D2 já declarou não ser o alvo.
- **value class sobre `String`** — cerimônia de tipo sem exaustividade; o typo continua compilando.
- **enum declarado fora do razão** (em `:core:model` ou registrado pelas features), com o razão guardando só o nome — o writer não validaria pouso sem receber a regra de fora, o que degenera na alternativa rejeitada em D8; e um tipo passaria a ter dois donos.
- **nomes em vocabulário de razão** (`SUBLEDGER`/`ANALYTIC` em vez de `INVOICE`/`CATEGORY`) — opacidade textual perfeita ao custo de schema ilegível, e a legibilidade do schema é metade do motivo de o kind existir (D2).

Custo aceito e registrado: uma fachada nova com dimensão exige uma linha nova no enum do razão. É o mesmo contrato que `:core:database` tem com as migrações (D1) — `:core:ledger` é a biblioteca contábil **deste** app (Context), não uma genérica.

### D8 — A regra de pouso é invariante do tipo, dado do razão

A regra de pouso mora no próprio `DimensionKind`, como `landsOn`. A feature escolhe o kind ao emitir a dimensão; ela **nunca** declara a regra. Uma regra, um dono, no razão — os consumidores a recebem pronta, no espírito do Derivation Rule.

Rejeitada a declaração pela feature na emissão (a dimensão nascendo carregando as naturezas que aceita): a regra viraria estado por linha, não invariante do tipo — duas dimensões do mesmo kind poderiam aceitar naturezas diferentes, e um bug na feature emitiria a regra errada, reabrindo um nível acima o mesmo defeito silencioso que a validação existe para matar. Exigiria ainda uma coluna extra (conjunto serializado, sem FK possível) para comprar uma opacidade que D7 já estabeleceu não ser o alvo.

Rejeitado o registro em runtime (features registram kind → regra na inicialização): troca falha de compilação por falha de runtime, adiciona ordem de inicialização como preocupação, e é exatamente o tipo de abstração que o projeto se recusa a pagar (CLAUDE.md: não aumentar complexidade vem antes de não duplicar).

D7 e D8 são uma decisão só vista de dois lados: o enum é o *portador* da regra, e é isso que torna a validação de pouso exaustiva, uniforme e sem ramo por kind.

### D9 — `finsight.room.library`: convenção Room compartilhada, e o banco de verificação do razão

`:core:ledger` não reusa `finsight.kmp.library` puro nem ganha convenção exclusiva: nasce **`finsight.room.library`** — `configureKotlinMultiplatform()` + plugins `ksp`/`room` + `schemaDirectory` + o `room-compiler` em cada configuração KSP por target — aplicado também a `:core:database`.

Verificado em `build-logic`: `finsight.kmp.library` é só `configureKotlinMultiplatform()` (`KmpLibraryConventionPlugin.kt:6-10`), sem Compose; Compose vive apenas em `configureCompose()`, aplicado por `finsight.compose.library` — que portanto traria Compose indevidamente a um módulo de domínio+dados. Todo o encargo Room de `core/database/build.gradle.kts` são os dois plugins, o bloco `room {}` e as seis linhas `add("ksp<Target>", room.compiler)` (linhas 22–33). Esse bloco por target é justamente a parte com histórico real de quebra em upgrade de Kotlin/KSP, e sem o plugin passaria a existir idêntico em dois módulos, a manter em sincronia a cada target novo. O projeto impõe regra por plugin, não por disciplina (D1); o plugin nasce com dois consumidores e devolve ambos os `build.gradle.kts` à norma de ~5 linhas + dependências.

Junto vem um achado que refina D1: a garantia "query do razão não compila se referenciar fachada" **não** vem de graça da visibilidade Kotlin. Room valida a string SQL de um `@Query` no processamento do `@Database` que o inclui — e o `AppDatabase` enxerga as tabelas de fachada, então um `JOIN invoices` escrito no `EntryDao` validaria lá sem erro. A garantia real vem de `:core:ledger` declarar um **`LedgerDatabase` interno** listando só as entities do razão: o KSP do próprio módulo valida cada `@Query` contra um schema em que `invoices`/`categories` não existem, e a referência espúria falha na compilação do razão. O mesmo `LedgerDatabase` é o banco dos testes de query que migram para o módulo. (Refinamento, não contradição: a visibilidade Kotlin bloqueia o import da entity; o `LedgerDatabase` bloqueia o nome da tabela dentro da string SQL.)

Rejeitado reuso + bloco Room copiado no `build.gradle.kts` do módulo: duplicaria a única parte da configuração com custo de manutenção real. Rejeitado um plugin exclusivo `finsight.ledger`: teria um consumidor e conteúdo idêntico ao que `:core:database` já precisa — simetria estética, não custo real.

### D10 — As contas nominais são invisíveis por construção — constatação, não mecanismo novo

As duas contas nominais são **invisíveis ao usuário**, e isso é constatação, não decisão que exija código: a invisibilidade já é consequência dos predicados existentes. Verificado: toda listagem e seletor de conta sai de `WHERE type = 'ASSET'` (`AccountDao.kt:17-31,44-48,63`), com comentário explícito de que linhas não-ASSET "must not leak into the accounts facade" (`AccountDao.kt:13-16`); o patrimônio líquido soma apenas `ASSET`/`LIABILITY` (`netWorthCents`, `EntryDao.kt:238-243`); a UI de transação renderiza só as pernas monetárias (`Transaction.monetaryEntries`, `Transaction.kt:37`; `TransactionUiMapper.kt:23`); os escopos de relatório são contas `ASSET` ou a `LIABILITY` do cartão (`reportStats`, `EntryDao.kt:280-311`). A conta de reconciliação nunca foi escondida por nome nem por flag "de sistema" — ela é invisível porque é `EQUITY` e nenhum predicado de UI alcança contas não-monetárias. As nominais (`INCOME`/`EXPENSE`) caem nos mesmos predicados sem uma linha nova.

Consequências registradas: os nomes das nominais são chaves de lookup em `SystemAccount` (o padrão de `RECONCILIATION`, `SystemAccount.kt:10-14`), jamais renderizados; o rótulo que o usuário vê vem da fachada de categoria, e "sem categoria" vem de `dimensionId = NULL` via string de recurso — nunca do nome de conta. Rejeitada a alternativa de exibi-las (como "Despesas"/"Receitas" navegáveis): criaria um terceiro tipo de item em listas que hoje só conhecem contas e cartões, sem caso de uso que o pague — e nada impede promovê-las depois, porque é só predicado de leitura.

## Risks / Trade-offs

**A migração reescreve história contábil e é irreversível na prática.** Colapsar N contas de categoria em duas nominais reescreve o `accountId` de toda perna nominal já gravada.
→ A migração valida `Σ = 0` por transação e por moeda **antes e depois** da reescrita, como teste de migração automatizado, não como cuidado manual. Uma transação que não balanceie após a reescrita aborta a migração. `MigrationLedgerReadParityTest` ganha o par de leituras equivalentes antes/depois para cada figura exibida.

**A direção invertida apoia-se em premissas não confirmadas sobre o Room.** D1 e D9 assumem comportamentos do processador que ainda não foram verificados contra a ferramenta.
→ A tarefa 2.0 os confirma num módulo descartável, **antes** de qualquer movimentação de código. Se uma premissa cair de forma crítica, D1 tem fallback documentado para a direção (a), condicionado ao teste que verifica as strings SQL do razão — a garantia muda de mecanismo, não é abandonada.

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

Nenhuma. As quatro questões que esta seção registrava foram resolvidas em D7 (`DimensionKind` como enum do razão), D8 (regra de pouso como invariante do tipo), D9 (`finsight.room.library` + `LedgerDatabase` de verificação) e D10 (contas nominais invisíveis por construção).
