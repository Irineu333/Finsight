# Design

## Context

O razão é simétrico — `ASSET` e `LIABILITY` são duas naturezas do mesmo plano de contas, lidas pelo mesmo `Σ entries`. A tela de transações não é: ela lista as duas e resume uma. O `SummaryCard` responde "quanto tenho em conta", a lista abaixo responde "o que aconteceu", e as duas perguntas não se encontram em lugar nenhum.

Três assimetrias sustentam isso, em camadas diferentes:

```
UI      SummaryCard   ── shape único, linhas de ASSET
        TransactionTarget ── chip que filtra a lista e não toca no resumo
        ↑ o eixo certo, rebaixado a filtro

domínio LiabilityMonthFlows(expense, payment)   AssetMonthFlows(income, expense, adjustment)
        ↑ sem adjustment                        ↑ com

razão   balanceUpTo(target, accountId = null) → "todas as contas ASSET"
        ↑ o viés na assinatura
```

A change endireita as três, na ordem inversa: o razão ganha a leitura simétrica, o domínio ganha o ajuste que falta, e a UI ganha o escopo que governa as duas metades da tela.

O que **não** muda: nenhuma escrita, nenhuma migração, nenhum valor hoje exibido, nenhum módulo além de `core/ledger`, `core/designsystem`, `core/resources` e a própria feature. O escopo `Contas` é o card de hoje, número por número.

## Goals / Non-Goals

**Goals**

- Resumo e lista respondem sempre ao mesmo perímetro — nunca mais um resumo de conta sobre uma lista que inclui cartão.
- Os três escopos compartilham uma gramática (abertura → fluxos → fechamento) e uma identidade aritmética verificável.
- A conciliação fatura↔saldo fica visível na tela, sem texto explicativo: o pagamento aparece nos dois livros e não move o líquido.
- As leituras novas do razão são simétricas às que já existem — nenhuma leitura nova conhece "cartão", só `AccountType`.

**Non-Goals**

- Unificar o escopo desta tela com `ReportPerspective`/`scopeStats`. Menor escopo, por decisão explícita.
- Escopo por conta ou cartão individual. O enum tem três valores, não N.
- Fazer os chips de filtro afetarem o resumo.
- Mudar a rota, a `api` da feature ou qualquer navegação de origem.
- Mudar o mapeamento ou a aparência do item de lista (`core/ui`).
- Aposentar o default `balanceUpTo(accountId = null) → ASSET`, ou rever a classificação de `assetMonthTotals`.
- Intervalo livre de período. O eixo continua sendo mês.
- Alinhar o escopo `Cartões` ao ciclo de fatura. Competência de lançamento, coerente com a lista.

## Decisions

### D1 — O escopo é um perímetro de contas, e o que é interno a ele não é fluxo

Um lançamento cujas pernas estejam **todas dentro** do perímetro soma zero ali dentro (`Σ = 0` por lançamento é invariante do razão), logo não move o fechamento daquele escopo. Isso não é regra nova nem exceção por tipo: é a mesma razão pela qual a transferência entre contas já não aparece em `assetMonthTotals` (`EntryDao.kt:315-322`).

| escopo | perímetro | interno |
|---|---|---|
| Contas | `ASSET` | transferência conta→conta |
| Cartões | `LIABILITY` | nenhum lançamento do produto hoje |
| Geral | `ASSET` + `LIABILITY` | transferência **e** pagamento de fatura |

Note que o pagamento de fatura **não** é interno ao escopo Contas — ele tem perna `LIABILITY`, fora do perímetro, e por isso move o saldo. É por isso que o card de hoje tem a linha "Faturas" (`SummaryCard.kt:96-104`), e ela permanece.

Daí sai, sem decisão adicional, que "Pagamentos" no escopo Geral é informativo: ele é interno ao perímetro. É exibido — o usuário quer vê-lo — mas fora da soma e sem sinal.

Alternativa descartada: **omitir a linha** no escopo Geral, como as transferências são omitidas hoje. É mais consistente com a regra ao pé da letra, mas apaga da tela justamente o evento que o usuário não consegue conciliar. Exibir-sem-somar é o que responde à dor.

Alternativa descartada: **exibir com sinal**, `−800`. A coluna deixaria de fechar com o total logo abaixo — o defeito que a change existe para eliminar, reintroduzido em outro eixo.

### D2 — A identidade é do razão, não da UI

Para todo escopo, sendo `P` o perímetro de contas:

```
saldoNatural(P, m) = saldoNatural(P, m−1) + Σ entries de P no mês m
```

As linhas de fluxo exibidas **particionam** `Σ entries de P no mês`. Lançamentos internos contribuem zero por construção, não por exclusão. Abertura e fechamento são exibidos com o sinal de exibição da natureza — para `LIABILITY`, dívida positiva (`AccountType.displaySign`).

Instanciando:

```
Contas    Σ ASSET      = income − expense + adj − pagamento
                         ↑ as quatro linhas: Entradas, Saídas, Ajustes, Faturas

Cartões   Σ LIABILITY  = −gastos + pagamentos + adj
          exibido       devido(m) = devido(m−1) + gastos − pagamentos − adj

Geral     Σ ambos      = income − (expenseA + expenseL) + (adjA + adjL)
                         ↑ o pagamento cancela entre as duas pernas
```

**Uma versão anterior deste documento escrevia a identidade em vocabulário de UI** (`fechamento = abertura + entradas − saídas + ajustes`) e estava errada: faltava o termo do pagamento no escopo Contas e o sinal invertia em Cartões. A formulação acima é a correta, e é dela que o teste de cada escopo deriva.

### D3 — `LiabilityMonthFlows` ganha `adjustment`, por simetria e por necessidade

A query de `liabilityMonthTotals` classifica só `eq = 0` (`EntryDao.kt:275-289`): um ajuste de fatura não cai em `expense` nem em `payment`, e desaparece. A irmã `assetMonthTotals` tem o ramo `eq = 1`. Hoje a assimetria é invisível porque nenhuma tela tenta fechar a conta do cartão; com a change ela quebraria **dois** escopos — Cartões e Geral, cuja linha "Ajustes" é `adjA + adjL`.

O dono é o razão, não a tela: a correção é um `CASE WHEN eq = 1` e um campo, espelhando o que já existe do lado `ASSET`. O único consumidor atual de `LiabilityMonthFlows` (a linha "Faturas" do `SummaryCard`) não muda de valor.

**Nota de precisão:** `liabilityMonthTotals.payment` não significa literalmente "pagamento de fatura" — é *qualquer* perna `LIABILITY` positiva sem contraperna `EQUITY`. Hoje isso coincide porque `BuildTransactionUseCaseImpl` recusa lançamento em cartão que não seja despesa (`CreditCardExpenseOnly`), então não existe estorno de compra no cartão. Se um dia existir, ele entraria em `payment` — e a neutralidade do escopo Geral deixaria de valer para aquele valor, sem que o teste acusasse. A invariante mora no formulário, não no razão; registrado em Risks.

### D4 — Saldo até o mês passa a ser parametrizado por `AccountType`

`assetsBalanceUpToMonth` (`EntryDao.kt:169-176`) é a query certa com o tipo cravado em literal. Generalizá-la para receber `type` dá, de uma vez:

- a dívida de abertura e fechamento do escopo Cartões (`LIABILITY`, exibida como devido positivo);
- o líquido de abertura e fechamento do escopo Geral, como **soma** dos dois — sem query nova e sem inventar sinal.

`IEntryRepository` ganha a leitura por natureza ao lado de `balanceUpTo`, que permanece com seu default e passa a delegar à forma parametrizada com `ASSET` — um caminho só, sem duplicar o agregado.

Alternativa descartada: **query dedicada de líquido até o mês** (irmã de `netWorthCents`, que é all-time e não serve). Uma terceira consulta para o que já é a soma de duas — e cada agregado novo é mais um lugar onde a regra pode divergir.

Alternativa descartada: **reusar `scopeStats(scopeAccountIds, …)`**, que já entrega `openingBalance` e `balance` para um conjunto de contas. É a generalização certa e é para onde isso deve convergir um dia, mas exigiria resolver "todas as contas de natureza X" em ids a cada leitura. Aqui o perímetro **é** a natureza. Fora do menor escopo acordado.

### D5 — O resumo passa a ter três formas, tipadas

Hoje `BalanceOverview` (`TransactionsUiState.kt:39-48`) tem seis campos fixos e dois flags `mustShow*`, e o `SummaryCard` os desenha em ordem fixa. Com três escopos de linhas diferentes, esticar esse shape produziria um objeto com campos que só valem em um modo e flags cruzados — o modelo de UI decidindo o que é visível, que é o que `presentation-mapping` proíbe.

O resumo passa a ser uma **`sealed` de três variantes**, uma por escopo, cada uma com os seus campos — o conjunto de linhas de cada escopo é fixo e conhecido, então não há razão para perder tipagem. O `SummaryCard` renderiza a variante; a decisão de quais linhas existem é do mapper, não do card.

Alternativa descartada: **lista genérica de linhas** (rótulo, valor, papel). Mais flexível do que o problema exige, apaga a tipagem do estado e obriga a reescrever `SummaryRow`/`SignDisplay` junto.

Consequência boa em qualquer das formas: a linha condicional (`mustShowPayment`, `mustShowAccountAdjustment`) deixa de ser flag e vira ausência de linha.

### D6 — O escopo recorta a lista; não reinterpreta o item

O item de lista **não muda**. Ele já é identificado por natureza, não por sinal: `TransactionCard` escolhe cor e ícone por `label` para pagamento e transferência (`TransactionCard.kt:177-196`), o título de um pagamento é "Pagamento de fatura" (`:161`), e o valor exibido é `abs` da perna (`TransactionUiMapper.kt:41`) — idêntico nos dois livros, porque as pernas de um lançamento têm a mesma magnitude. Despesa comum sai sem sinal; só transferência e ajuste levam sinal (`TransactionCard.kt:142-151`).

Uma versão anterior propunha mapear o item pela perna do escopo, o que faria um pagamento ler `+500` em Cartões. Descartado pelo usuário, e a inspeção do card confirma que era solução para um problema inexistente: nenhum valor exibido mudaria, e a identidade do item já vem da natureza.

Consequência: `core/ui` não é tocado, e o recorte da lista por escopo é um predicado sobre a presença de perna da natureza do perímetro.

### D7 — Default Geral, e o chip de alvo só existe nele

Nascer em `ACCOUNTS` preservaria o resumo atual, mas **esconderia as compras de cartão da lista** — que hoje aparecem. Trocar um resumo incompleto por uma lista incompleta é regressão percebida. Com `ALL`, a lista fica igual à de hoje e o resumo passa a explicá-la: o defeito é corrigido do lado que estava errado.

`TransactionTarget` como chip sobrevive só em Geral, onde ainda tem trabalho a fazer (recortar a lista sem reconciliar o total). Em Contas/Cartões ele seria a mesma decisão em dois controles, com estados contraditórios possíveis. A regra que o justifica é a mesma que governa o layout: **o escopo reconcilia, o filtro apenas recorta.**

Como o chip permanece, `TransactionsRoute.filterTarget` continua tendo sentido e **não é tocado**. O escopo é estado de tela, não parâmetro de rota — nenhuma navegação de origem muda.

### D8 — Alcance é posicional, e por isso os controles moram no card

A tela passa a ter duas classes de controle, distinguidas por onde estão:

```
┌─ card ────────────────────────────────┐
│  [ julho 2026 ▾ ]   [ Geral ▾ ]       │  governa card + lista
│  ─────────────────────────────────    │
│  abertura · fluxos · fechamento       │
└───────────────────────────────────────┘
   categoria ▾  natureza ▾  alvo ▾  …      governa só a lista
   ───────────────────────────────────
   lista
```

A moldura do card passa a significar "isto vale para tudo daqui para baixo".

**Atenção de implementação:** o `MonthSelector` atual **não tem** modo sem setas — `‹` e `›` são `IconButton` incondicionais (`MonthSelector.kt:52-57,98-103`), e `showPickerChevron` controla o `▾`, não elas. A tela já passa `showPickerChevron = false` hoje (`TransactionsScreen.kt:78`). O chip de período precisa do **inverso disso**: sem setas, **com** `▾` — que é o que dá simetria com o chip de escopo. Ou o componente ganha o modo, ou o chip é componente próprio.

Os dois chips SHALL ter a mesma interação (ambos abrem menu ao toque); dois chips gêmeos com afordâncias diferentes quebram a simetria tanto quanto formas diferentes.

Alternativa descartada: **segmented button de três posições** para o escopo. Torna os três modos descobríveis de imediato e custa um toque, mas quebra a simetria visual com o chip de período ao lado. Decisão de produto do usuário: simetria.

Alternativa descartada: **manter as setas de mês**. Navegar mês passa de um toque para dois. Decisão explícita do usuário, reavaliável.

## Risks / Trade-offs

- **Os controles saem de vista ao rolar** → hoje o mês vive no `topBar` e está sempre visível; dentro do card, que é `item` da `LazyColumn`, ele rola para fora — e o card fica maior que o atual. Registrado como **débito de produto** no `proposal.md`: se incomodar, card fixo acima da lista ou `topBar` colapsante. Decisão consciente de tentar dentro primeiro.

- **A neutralidade do pagamento no escopo Geral depende de uma invariante do formulário, não do razão** (D3) → se um dia existir perna `LIABILITY` positiva com contraperna nominal (estorno de compra no cartão), ela entra em `payment`, sai da soma e move o líquido sem que o teste acuse. Mitigação: o teste de neutralidade deve construir o caso a partir de um pagamento real, e a nota de D3 fica no design para quem revisitar `CreditCardExpenseOnly`.

- **Duas classes de controle na mesma tela, distinguidas só por posição** → um usuário pode esperar que filtrar por categoria mude o resumo. Mitigação: a fronteira visual do card, e o fato de que o resumo declara o seu escopo por escrito. Fazer os filtros afetarem o resumo exigiria agregados parametrizados que não existem, e somar a lista carregada é proibido por `ledger-reporting`.

- **`SummaryCard` é reescrito, e há teste de caracterização apoiado no shape atual** → `TransactionsViewModelCharacterizationTest` caracteriza o resumo de hoje. Mitigação: ele passa a ser o caso do escopo Contas e deve continuar verde valor a valor — é a rede de segurança da change, não uma vítima dela.

- **Método novo em `IEntryRepository` quebra os fakes de teste** que implementam a interface em `creditcards`, `dashboard` e `transactions` → mitigação: task própria, e a leitura por natureza pode nascer com implementação default sobre `balanceUpTo` para não quebrar ninguém de imediato.

- **A generalização de `assetsBalanceUpToMonth` toca uma query usada pelo dashboard** → mudança de assinatura, não de resultado; `balanceUpTo(target, null)` continua devolvendo `ASSET`. Mitigação: os testes existentes de saldo cobrem isso e não devem mudar de expectativa.

- **`TransactionScope` é um quarto vocabulário de escopo** (com `ReportPerspective`, `TransactionTarget` e `scopeStats`) → dívida conceitual assumida em troca de menor escopo. Mitigação: `proposal.md` a registra como fora de escopo explícito, não como omissão.

## Resolved Questions

- **Q1 — O escopo persiste entre visitas à tela? Não.** Ele é `MutableStateFlow` no
  `TransactionsViewModel`, exatamente como o mês e os filtros: vive enquanto o ViewModel
  viver e volta a `ALL` quando a tela é recriada. Manter o eixo novo com o mesmo tempo de
  vida dos que já existem evita que a tela tenha duas noções de memória; persistir só o
  escopo seria a inconsistência, não o default. Reavaliável se o uso pedir — a decisão de
  persistir vale para os três eixos juntos, não para um.
- **Q2 — Rótulo do escopo Geral: "Geral"** (`Overall` em inglês). Ao lado do chip de mês
  não gera ambiguidade, porque o chip vizinho já nomeia um período e não um perímetro. A
  alternativa "Contas e cartões" fica registrada caso o uso mostre o contrário.
