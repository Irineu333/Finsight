# Design

## Contexto

Correção de defeito, não capability nova. O razão já deriva a natureza de uma transação em um lugar só (`deriveTransactionLabel`, `Ledger.kt:56`) e a lista já a **exibe** por ali; só a **filtragem** ficou para trás, usando o outro vocabulário do razão — `TransactionType`, a direção de uma perna.

A confusão é compreensível porque os dois enums compartilham três nomes (`EXPENSE`, `INCOME`, `ADJUSTMENT`), e sobre a perna `ASSET` de uma despesa comum eles coincidem. Divergem exatamente nas duas formas que o filtro não alcança: transferência e pagamento, onde a perna de saída é negativa e `deriveTransactionType` a lê como "despesa".

```
TransactionType (direção, sob perspectiva)   TransactionLabel (natureza, sem perspectiva)
  EXPENSE   ← esta perna saiu                  EXPENSE     ← tem perna EXPENSE
  INCOME    ← esta perna entrou                INCOME      ← tem perna INCOME
  ADJUSTMENT← há perna EQUITY                  TRANSFER    ← duas pernas ASSET
                                               PAYMENT     ← tem perna LIABILITY
                                               ADJUSTMENT  ← tem perna EQUITY (testado 1º)
```

## Decisões

### D1 — O eixo de filtro é `TransactionLabel`, não `TransactionType`

O filtro passa a ser `filter { it.label == label }`. O dono da regra continua sendo `deriveTransactionLabel`; a feature consome, não reimplementa — a Derivation Rule aplicada literalmente.

Duas alternativas foram consideradas e descartadas na exploração:

- **Manter 3 opções e só estancar o vazamento** (filtrar `label == EXPENSE`/`INCOME` e deixar as outras naturezas sem opção). Corrige a mistura e alinha com o resumo, mas torna transferência e pagamento **invisíveis a todo filtro de tipo** — troca um defeito por outro, e a lista deixa de ser particionável.
- **Dois eixos, "natureza" + "direção"**. Overengineering para uma lista mensal: sem perspectiva de conta, a direção não acrescenta pergunta que a natureza não responda.

### D2 — A escolha é possível porque `deriveTransactionLabel` é total e exclusivo

A função é um `when` sobre o conjunto de tipos de conta, com `else`, e testa `EQUITY` primeiro justamente para que `{ASSET, EQUITY}` não caia em `TRANSFER` nem `{LIABILITY, EQUITY}` em `PAYMENT` (`Ledger.kt:46-55`, coberto por `LedgerTest`). Toda transação recebe exatamente um rótulo. Consequência: as cinco opções **particionam** a lista, e a união delas é o sem-filtro — propriedade que o requisito e o teste passam a fixar, e que o filtro atual não tem.

### D3 — Perspectiva é o critério que separa os dois usos, e a tela de faturas fica intocada

`InvoiceTransactionsViewModel.kt:254-263` faz o que **parece** o mesmo bug e não é: ele lê `deriveTransactionType` da perna `LIABILITY` — a perna do próprio cartão, que é a perspectiva daquela tela — e por isso rotula `INCOME` como "Pagamento" (`invoice_transactions_filter_type_payment`). Ali "entrou dinheiro no cartão" é a pergunta certa, e `TransactionType` é o vocabulário certo. Isso é a regra de `presentation-mapping` ("Perspectiva como argumento de mapeamento") funcionando.

A lista de transações é **neutra** — não olha por nenhuma conta —, e é justamente aí que a direção de uma perna arbitrária (a que sai) deixa de significar algo para o usuário. **A regra:** com perspectiva, direção (`TransactionType`); sem perspectiva, natureza (`TransactionLabel`). O change não toca cartão nem fatura.

### D4 — `TransactionLabel` na rota, com NavType próprio

`TransactionsRoute.filterType: TransactionType?` vira `filterLabel: TransactionLabel?`, e `TransactionLabelNavType` espelha exatamente o `TransactionTypeNavType` (serializa por `name`, `"null"` → `null`). `TransactionTypeNavType` é **removido**: o grafo desta feature era o único consumidor.

Alternativa descartada: manter `TransactionType` na rota e mapear na borda do grafo. Seria menos código, mas manteria a rota incapaz de expressar "abra a lista em Transferência" — a mesma limitação, um nível abaixo. A rota é estado de navegação, não formato persistido; nada migra.

O aviso de wire format do `TransactionType` (`TransactionType.kt:13-16`) permanece válido e intocado: analytics e `RecurringForm` continuam usando o enum, que não muda.

### D5 — Cinco cores, uma constante nova, sem reuso oportunista

Quatro já existem em `Color.kt` com os valores desejados e são as que o app já usa para essas naturezas:

| natureza | cor | constante | Tailwind |
|---|---|---|---|
| INCOME | 🟢 verde | `Income` `0xFF22C55E` | green-500 |
| EXPENSE | 🔴 vermelho | `Expense` `0xFFEF4444` | red-500 |
| TRANSFER | 🔵 azul | `Transfer` `0xFF3B82F6` **(nova)** | blue-500 |
| PAYMENT | 🟣 roxo | `InvoicePayment` `0xFF8B5CF6` | violet-500 |
| ADJUSTMENT | 🟡 amarelo | `Adjustment` `0xFFF59E0B` | amber-500 |

`0xFF3B82F6` já aparece duas vezes no arquivo, como `Info` (`:39`) e `CategoryColor` (`:49`). Constante **própria** mesmo assim: mesmo pixel, semânticas diferentes, e reusar acopla três decisões que um dia divergem. Fica na seção "Income/Expense colors", junto das irmãs.

Sobre a colisão visual com `CategoryColor`: no card de uma transferência ela não se materializa — transferência não tem perna nominal, logo não tem categoria, `lookup.categoryOf()` devolve `null` e o card cai no ícone genérico `SwapHoriz`.

### D6 — O resumo do mês é o critério de acerto

O `SummaryCard` já lê do razão: `assetMonthFlows` (receita/despesa/ajuste sobre as contas `ASSET`, **sem** transferência e **sem** pagamento) e `liabilityMonthFlows().payment`. Depois do change, cada linha do cabeçalho tem um filtro correspondente que devolve exatamente as transações que a compõem, e transferência é o único rótulo sem linha no resumo — corretamente, porque não move patrimônio. Essa paridade vira requisito e teste; hoje ela é violada de forma visível na mesma tela.

### D7 — Escolha do modal passa a ler `label`

`TransactionsScreen.kt:143-151` decide entre `ViewAdjustmentModal` e `ViewTransactionModal` por `transactionUi.direction == ADJUSTMENT`. É **equivalente** hoje (`deriveTransactionType` testa `EQUITY` primeiro, como `deriveTransactionLabel`), então não é correção de defeito — mas é uma leitura de natureza feita pelo eixo de direção, e trocar por `label == TransactionLabel.ADJUSTMENT` é uma linha que deixa a tela com um só vocabulário. Incluído por isso, não por bug.

## Riscos

- **Rename atravessa três módulos.** `filterType`/`selectedType`/`SelectType` mudam de nome e de tipo em `api`, `impl` e `feature/dashboard/impl`. Sem risco silencioso: tudo quebra em compilação, nada compila com o significado velho.
- **Koin resolve o parâmetro por tipo.** `TransactionsModule.kt:59` usa `getOrNull()`, e a chamada é `parametersOf(categoryLabel, null, target)` (`TransactionsScreen.kt:48`). A troca de `TransactionType?` por `TransactionLabel?` mantém a mecânica — mas é resolução por tipo em runtime, não checada pelo compilador: se o rename passar num lugar e não no outro, o filtro chega `null` **em silêncio** (a tela abre sem filtro em vez de falhar). O teste do parâmetro de rota cobre isso.
- **`ADJUSTMENT` segue com grão mais grosso que a exibição** (ajuste de saldo + de fatura no mesmo filtro). Registrado, não corrigido — é o comportamento atual.
- **Combinação vazia legítima:** alvo "Cartão de crédito" + tipo "Transferência" é sempre vazio por construção (transferência não tem perna `LIABILITY`). Resultado vazio honesto; não desabilitar a combinação.
