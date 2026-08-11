## Context

Três fatos do código sustentam esta mudança, e todos foram lidos no disco.

**1. A operação de ajuste já é única e já é datada.** `AdjustBalanceUseCase.kt:24-96` faz tudo:
lê o saldo corrente, exige que o alvo difira dele, encontra o ajuste existente por
`(data, conta, perna EQUITY)` (`:44-50`), e cria, atualiza ou apaga a transação conforme o novo
tamanho. Os dois "tipos" são invólucros de data:

```
AdjustOpeningBalanceUseCase ──┐  (M-1).lastDay
                              ├──► AdjustBalanceUseCase(target, date, account)
AdjustFinalBalanceUseCase ────┘  M == hoje ? hoje : M.lastDay
```

Nenhum dos dois tem teste próprio — existem `AdjustBalanceUseCaseTest` e
`BalanceAdjustmentSignTest`, ambos sobre a operação real —, e cada um tem exatamente dois
consumidores: `AccountsModule.kt:104-105` e `EditAccountBalanceViewModel.kt:115-125`.

**2. A leitura de saldo por conta é mensal, e a spec já diz que deveria ser datada.**
`EntryDao.kt:213-217` corta com `substr(o.date, 1, 7) <= :yearMonth`, enquanto
`ledger-reporting` afirma que "o corte temporal do saldo SHALL usar a data da transação como
única referência" e que a leitura devolve a soma "até a **data-alvo**". A coluna já guarda uma
data completa; o mês é uma perda de resolução aplicada na consulta.

**3. A janela de compra nunca foi invariante.** `InvoiceWriteGuard.kt:30-41` recusa por status
(`PAID`, `CLOSED`), jamais por data. A recolocação vive em dois ViewModels de formulário
(`AddTransactionViewModel.kt:145-157`, `AddInstallmentViewModel.kt:177-189`) e a própria spec
`invoice-governs-date` já a chama de **sugestão**, com a palavra final do usuário e a divergência
apenas dita. O KDoc que diz "a fatura governa a data ... o caminho inverso é impossível" descreve
a direção do fluxo de estado, não uma restrição sobre o valor.

## Goals / Non-Goals

**Goals:**
- Reduzir os três ajustes de saldo a um, com a data como único eixo de diferença.
- Dar campo de data aos dois modais, com padrão por contexto e teto único.
- Fazer o valor de referência ser lido na data escolhida, corrigindo a divergência do saldo
  inicial por construção e não por remendo.
- Estender `invoice-governs-date` ao ajuste de fatura sem inventar uma segunda hierarquia.
- Não induzir ao erro onde a interface deixa de proteger.

**Non-Goals:**
- Tornar a janela de compra invariante do razão.
- Mudar o formato do lançamento de ajuste (perna `ADJUSTMENT` + contra-perna `EQUITY`).
- Levar o corte por data às leituras por moeda, que nenhum consumidor pede por dia.
- Mexer nos modais de pagar fatura e antecipar pagamento, que são liquidação e têm piso próprio.
- Renomear "Saldo Inicial" nas telas de conta e relatório.

## Decisions

### D1 — A data é o eixo; o tipo de ajuste some

Não há "ajuste de saldo inicial" nem "ajuste de saldo final". Há **um ajuste**, e a data em que
ele acontece. Os dois pontos de entrada da tela de contas sobrevivem — são atalhos úteis — mas
passam a diferir apenas pela data com que abrem o modal.

Alternativa descartada: manter `Type` reduzido a "como calcular a data inicial". Seria o mesmo
enum com outro nome, e reintroduziria na interface a distinção que o domínio não tem. A projeção
é uma função de um mês para uma data; expressá-la como função é mais barato e mais honesto que
expressá-la como tipo.

**Onde a projeção mora:** numa função nomeada no domínio de `:feature:accounts:impl`, não na
`AccountsScreen`. "O saldo inicial de M é o saldo ao fim de M-1" é regra derivável do domínio e,
pela regra de derivação do projeto, tem um dono só. A tela escolhe **qual** atalho oferecer;
nunca **qual data** ele significa.

### D2 — Um teto, três padrões

Todos os padrões de data são a mesma expressão:

```
data_padrão = projeção_do_contexto . coerceAtMost(hoje)

  saldo inicial de M  →  (M-1).lastDay
  saldo final de M    →  M.lastDay
  ajuste de fatura    →  window.dateOn(hoje.day)
```

O teto em hoje não é uma regra por caso: é o mesmo `maxDate` do seletor de data nos dois modais.
Com ele, `FutureMonthAdjustmentException` deixa de ter como acontecer — o calendário não oferece
o que o domínio recusaria.

Sobre "saldo final": a diferença entre `hoje` literal e `min(hoje, fim do mês)` só aparece em mês
passado, e ali é grande — com `hoje` literal, ajustar o saldo final de março em agosto grava em
agosto e **março não muda**. O `min` é o que preserva o significado do gesto.

### D3 — Um ajuste não é uma compra, e por isso não tem o fechamento como teto

A assimetria "pode divergir para trás, nunca para frente" é real e é sobre **ocorrência**: uma
compra não pode se liquidar num ciclo que fechou antes de ela acontecer — daí o lançamento
retroativo do lojista cair sempre na fatura seguinte, nunca na anterior.

Um ajuste não tem esse problema porque ele não aconteceu *no* ciclo, e sim *sobre* o ciclo.
Corrigir hoje a fatura de janeiro é um evento de hoje. Forçá-lo para dentro de janeiro falsificaria
quando a correção ocorreu, que é justamente a informação que a data carrega.

O limite, portanto, é por natureza da perna:

| perna | piso | teto |
|---|---|---|
| `EXPENSE` (compra, parcela) | livre — pode anteceder a janela | `min(hoje, closingDate)` |
| liquidação (pagar, antecipar) | `closingDate` / `openingDate` | `min(hoje, dueDate)` |
| `ADJUSTMENT` | livre | `hoje` |

As duas primeiras linhas descrevem o que já existe (`PayInvoiceModal.kt:79`,
`AdvancePaymentModal.kt:76`, `AddTransactionViewModel.kt:154`). A terceira é o que esta mudança
acrescenta.

### D4 — A janela continua política de interface, e o aviso é o que impede a armadilha

Nada no boundary de escrita recusa uma perna fora da janela, e esta mudança não altera isso. A
consequência é que a proteção tem de ser informativa, e ela difere entre os dois modais:

- **Conta** — sai de graça. O valor de referência passa a ser lido na data (D5), então mudar a
  data move o rótulo de diferença na hora. O campo se explica sozinho.
- **Fatura** — precisa de gesto explícito, porque o valor **não** depende da data: o razão leva
  a correção à fatura pela dimensão, e `CalculateInvoiceUseCase` não tem corte temporal. Sem
  aviso, datar em agosto uma correção de janeiro não produz nenhum sinal visível.

O aviso já existe e tem dono: `InvoiceMonthSelection.diverges` (`InvoiceMonthSelection.kt:33`),
consumido como `isDateOutsideInvoice` em três `UiState` e renderizado como `supportingText`. O
modal de ajuste passa a ser o quarto consumidor, com a string existente
`transaction_date_outside_invoice`. Discreto, nunca erro, nunca bloqueio — como
`invoice-governs-date` já exige.

### D5 — O valor de referência é lido na data, e o "mês alvo" desaparece

Hoje o modal carrega dois relógios: `targetMonth`, que decide o pré-preenchimento, e a data
derivada do tipo, que decide a gravação. Eles concordam em `FINAL` e discordam em `INITIAL` — é
literalmente a divergência descrita no *Why*.

Com o campo de data, `targetMonth` perde a razão de existir: o modal tem uma data, e o valor de
referência é o saldo naquela data. O subtítulo de mês some junto, substituído pelo campo que
agora diz a mesma coisa com mais precisão.

Isso é o que exige D6.

### D6 — O corte por data só na leitura escalar por conta

`balanceUpToMonth(M)` **é** `balanceUpToDate(M.lastDay)`. Manter as duas como implementações
independentes seria um segundo caminho para o mesmo número, que `ledger-reporting` proíbe em
letra. Então a datada é a leitura real e a mensal deriva dela.

O corte **não** desce para a família por moeda (`balanceUpToByCurrency`,
`naturalBalanceUpToByCurrency`). Nenhum consumidor delas pergunta por dia, e alargá-las agora
seria escopo inventado. A assimetria é deliberada e fica registrada aqui: a porta está aberta e a
forma já está decidida se alguém precisar.

### D7 — A idempotência por data fica visível, e isso é correto

A chave de "já existe um ajuste" é `(data, conta, EQUITY)` e `(data, dimensão, EQUITY)`
(`AdjustBalanceUseCase.kt:44-50`, `AdjustInvoiceUseCase.kt:38-43`). Com data fixa, isso significava
"reajustar hoje edita o ajuste de hoje". Com data livre, o modelo mental passa a ser: **cada
ajuste é um evento datado; reabrir a mesma data edita aquele evento, outra data cria outro.**

Nada disso muda no código — o que muda é o usuário poder alcançá-lo. Vale enunciar a consequência
que vem junto e é inerente a partidas dobradas: um ajuste é um **delta**, não um alvo permanente.
Fixar "saldo = 200 hoje" e depois alterar janeiro leva o saldo de hoje a 250; a transação de hoje
continua sendo "+X", não "vira 200". Já é assim; a mudança apenas torna frequente o encontro.

## Risks / Trade-offs

- **Datas erradas ficam possíveis onde antes eram impossíveis.** É o preço da liberdade pedida, e
  a mitigação é D4: padrão certo, e divergência dita. Uma data errada num ajuste não corrompe
  saldo nem fatura — desloca a linha na lista de transações.
- **A leitura datada muda o desempenho da consulta.** `o.date <= :date` compara a coluna inteira
  em vez de um prefixo de 7 caracteres; é a mesma varredura, sem `substr` por linha. Não se espera
  regressão, mas a mudança toca a leitura de saldo mais usada do app.
- **A assimetria de D6** deixa `:core:ledger` com uma leitura datada e uma família mensal ao lado.
  É incoerência de superfície, deliberada, e o registro está em D6 para que não seja lida como
  esquecimento.
- **O E2E de contas** alcança o modal por `edit_account_balance_amount` e
  `edit_account_balance_save`, tags que permanecem. O risco é de layout: um campo a mais pode
  deslocar o alvo sob teclado aberto. Exige reexecução do fluxo, não reescrita presumida.

## Migration Plan

Não há migração de dados. Nenhuma tabela, coluna ou formato de lançamento muda, e os ajustes já
gravados continuam sendo o que sempre foram — transações com perna `ADJUSTMENT` e contra-perna
`EQUITY`, datadas. A mudança é de superfície de escrita e de leitura, não de estado persistido.

## Open Questions

Nenhuma. As decisões de teto, projeção, escopo da janela e granularidade da leitura estão fixadas
em D2, D3, D4 e D6.
