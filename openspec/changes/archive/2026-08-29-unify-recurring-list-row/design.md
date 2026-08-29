## Context

A lista de recorrências organiza os ciclos do mês em quatro seções — pendente, a lançar,
lançada, ignorada — e desenha três delas com um componente e a quarta com outro
(`RecurringScreen.kt:293-310`). A ramificação existe por um motivo legítimo, registrado em
`RecurringCycleUi`: um ciclo sem fato só pode ser descrito pelo template que o projeta, e um
ciclo lançado é descrito pelo razão, porque confirmar aceita sobrescrever valor, identidade e
classificação para aquele mês apenas. O erro não está em ler de duas fontes — está em ter
deixado que **duas fontes virassem dois desenhos**.

Medido no código de hoje:

| | `RecurringCard` (template) | `TransactionCard` (lançado) |
|---|---|---|
| padding do card | 12dp | 12dp |
| espaçamento horizontal | 12dp | 16dp |
| chip | 40dp, raio 8 | 48dp, `shapes.medium` |
| coluna central | `titleSmall` 20 + 4 + `bodySmall` 16 = 40dp | 16sp + 14sp, ambos com `lineHeight` 24 herdado de `bodyLarge`, sem gap = 48dp |
| coluna direita | `titleMedium` 24 + 4 + `labelMedium` 16 = **44dp** | figura única, centrada |
| **altura** | **68dp** | **72dp** |

O `TransactionCard` é consumido por oito telas (`grep TransactionCard(` — transações, cartões,
faturas, parcelas, painel, contas, relatório e esta). Ele não é o problema: é o card de uma
lista de razão, e a tela de recorrências é uma lista de **regras que o usuário mantém**. O que
falhou foi emprestá-lo para uma seção de uma tela que já tinha a sua própria linha.

## Goals / Non-Goals

**Goals:**
- Uma linha só na tela: mesma altura, mesmo chip, mesmo espaçamento e a mesma geometria de
  colunas para as quatro seções.
- A linha lançada segue lendo do razão — figura, identidade, classificação e agora também a
  **origem** que a transação registrou.
- Nenhuma outra tela do app muda de aparência.

**Non-Goals:**
- Alterar `TransactionCard` ou `TransactionUi` em `core/ui`. A avaliação de que ele usa `sp`
  cru onde o resto do app usa tokens de tipografia é verdadeira e fica registrada aqui, mas
  padronizá-lo é mudança das outras sete telas e não desta.
- Mudar o que a seção lançada **lê**. A fonte continua sendo o razão.
- Mexer no card de resumo do mês acima da lista.

## Decisions

### D1 — `RecurringCard` desenha as duas leituras, e a tela não ramifica mais

O componente passa a receber o que a linha **afirma**, e não o `Recurring` ou o `TransactionUi`
crus: identidade, figura, origem e a linha do tempo (o dia projetado ou a data do fato). A
escolha entre template e razão fica onde já está — no view model, que constrói
`RecurringCycleUi` — e o componente deixa de saber que existem duas fontes.

*Alternativa considerada:* manter dois componentes e apenas igualar as constantes de tamanho.
Rejeitada: acerta a proporção e deixa a distribuição divergente (origem no centro num, data no
centro no outro), e mantém duas definições da mesma altura que voltam a divergir no primeiro
ajuste de um dos lados. A altura tem de ter um dono só.

### D2 — A origem da linha lançada vem da transação, não do template

`ConfirmRecurringUseCase` resolve o destino como `account ?: recurring.account` e
`creditCard ?: recurring.creditCard` (linhas 93 e 124): a conta e o cartão são
sobrescrevíveis por ciclo. A ocorrência não guarda essa escolha —
`RecurringOccurrenceEntity` tem apenas `transactionId` —, então a origem verdadeira está nas
pernas da transação, e é de lá que ela é lida.

Uma linha lançada que mostrasse `recurring.account` estaria afirmando a regra onde o usuário lê
o fato, e erraria exatamente no caso em que a informação importa: o mês em que ele mandou o
aluguel sair de outra conta.

*Alternativa considerada:* acrescentar a origem a `TransactionUi`. Rejeitada por ora — o DTO
serve oito telas e nenhuma outra pede esse campo; e o mapeamento de uma perna `LIABILITY` ao
cartão que a projeta depende do repositório de cartões, que a feature tem e o mapper de
`core/ui` não.

### D3 — A resolução é uma leitura para a seção inteira

`ledgerRowsOf` já busca todas as transações da seção numa chamada só
(`RecurringViewModel.kt:166-193`), pelo motivo que o próprio KDoc registra: o `combine` tem
cinco fontes e refaz esse caminho a cada emissão. A resolução da origem entra no mesmo lugar e
sob a mesma regra — o plano de contas é lido uma vez e a origem de cada linha vira consulta num
mapa, jamais uma query por linha.

### D4 — A linha lançada afirma a data do fato onde o template afirma o dia

É o mesmo slot: a segunda linha da coluna direita, embaixo da figura. O template projeta ("dia
5"), o fato aconteceu (a data registrada). Um ciclo confirmado fora do dia projetado passa a
dizer isso sozinho, o que hoje só o detalhe conta.

### D5 — A altura fica com um dono declarado

O par (`CHIP_SIZE`, `ROW_LINE_GAP`) já governa a altura do `RecurringCard`, e o KDoc explica
qual dos dois lados a decide: a coluna direita mede 44dp e passa por cima do chip de 40dp. Com
uma linha só, essa passa a ser a altura de toda a lista, e a invariante a verificar é uma:
**toda variante mede o mesmo** — com categoria e sem, denominada e não, template e lançada —
para que `animateItem()` reordene sem salto.

## Risks / Trade-offs

- **A tela perde o reuso de `TransactionCard`** → É o custo aceito, e é pequeno: o componente
  não some do app, some de uma seção que não era a sua. Em troca, a tela de recorrências passa
  a ter uma linha só, com um dono só para a altura.
- **Duplicar a leitura de "qual conta/cartão" na feature** → Mitigado por D3: uma resolução
  para a seção, no view model que já lê o plano de contas para denominar os templates.
- **A origem lida do razão pode nomear a conta de sistema em vez do cartão** — uma compra no
  cartão posta na perna `LIABILITY` do cartão, e o nome dessa conta do plano não é o nome do
  facade → A resolução mapeia a conta ao cartão que a projeta via `accountId`, que é o caminho
  que o resto do app já usa; a verificação disso é tarefa explícita, com teste.
- **O `testTag` da linha lançada muda** de `transaction_card*` para o da linha de recorrência →
  Nenhum flow Maestro de recorrências referencia `transaction_card`
  (`.maestro/flows/recurring/lifecycle.yaml` usa `recurring_card_amount` e
  `recurring_section_*`), então nada quebra; a seção lançada ganha a asserção que hoje não tem.
- **O spec `recurring-list-row` tem dois requisitos que delegam a linha lançada ao componente
  de transação** → São reescritos nesta change. O conteúdo normativo dos dois — magnitude sem
  sinal, e a marca de irresolvível que não vale para o lançado — continua valendo palavra por
  palavra; o que sai é a atribuição a outro componente.
