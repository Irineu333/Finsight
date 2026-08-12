## Context

A fatura nunca é criada como intenção. Hoje ela nasce de quatro lugares, todos como efeito
colateral:

```
  criar cartão            fechar fatura           submeter lançamento
       │                       │                          │
       ▼                       ▼                          ▼
  OpenInvoiceUseCase ◄── encadeia sucessora     GetOrCreateInvoiceForMonth
   (uma OPEN)             (promove FUTURE→OPEN)           │
                                                ┌─────────┴─────────┐
                                                ▼                   ▼
                                     CreateFutureInvoice   CreateRetroactiveInvoice

  CreateInvoiceUseCase ─── morto, registrado em UseCaseModule.kt:105
```

Os dois use cases da direita são **o mesmo código**: checam colisão por `dueMonth`, derivam a
janela por `invoiceWindowFor` e inserem — diferindo apenas na constante de `Invoice.Status`
(`CreateRetroactiveInvoiceUseCase.kt:25-45`). Quem escolhe entre eles é
`GetOrCreateInvoiceForMonthUseCaseImpl.kt:44-47`, comparando `targetDueMonth` com o `dueMonth`
da fatura `OPEN`.

`CreateInvoiceUseCase` é código morto e ativamente perigoso: cria `OPEN` (o que daria duas
faturas abertas no cartão, quebrando o `LIMIT 1` de `InvoiceDao.kt:25` e a regra de reabertura de
`Invoice.kt:114-117`) e crava `openingMonth = mês atual`, `closingMonth = mês seguinte`, sem
consultar o `closingDay` do cartão — contradizendo o dono da janela especificado em
`invoice-purchase-window`. É também o use case com o nome exato que uma ação "criar fatura"
procuraria.

Do lado da interface, dois vocabulários coexistem para o mesmo conceito:

| | `InvoiceMonthNavigator` | `InvoiceSelector` |
|---|---|---|
| onde | adicionar transação, adicionar parcelamento | ajustar saldo, confirmar recorrente |
| fonte | meses (navegação infinita) | linhas do banco |
| mês inexistente | derivado, rotulado "• nova" | invisível |

A modal de criação precisa do primeiro vocabulário com o rótulo invertido: quer o mês que **não**
existe e recusa o que existe.

## Goals / Non-Goals

**Goals:**
- Um gesto explícito de criação de fatura na tela de faturas, para qualquer mês ainda inexistente.
- Um único caminho de criação por mês alvo no domínio, com a classificação de status tendo um só dono.
- Eliminar `CreateInvoiceUseCase` sem deixar o nome livre para reintroduzir o comportamento errado.

**Non-Goals:**
- Declarar o **valor** da fatura na criação — isso é o ajuste de saldo, que já existe e já alcança
  faturas `RETROACTIVE`.
- Fechar e pagar a fatura criada — o ciclo de vida existente já cobre, sem mudança.
- Consertar o cartão que nasce sem nenhuma fatura (`AddCreditCardUseCase.kt:48` descarta o
  resultado da abertura). Ver D7.
- Preencher meses intermediários. Criar março com janeiro e fevereiro inexistentes continua
  legítimo, como já é hoje ao lançar em meses salteados.

## Decisions

### D1 — Uma operação de criação, parametrizada pelo mês

`CreateFutureInvoiceUseCase`, `CreateRetroactiveInvoiceUseCase` e o `CreateInvoiceUseCase` morto
são substituídos por um único `CreateInvoiceUseCase(creditCard, dueMonth)`, que classifica o
status, deriva a janela e insere.

*Por quê:* duas operações que diferem por uma constante são duas operações cuja identidade é o
período — exatamente o padrão que a change `adjust-balance-on-a-date` acabou de eliminar do
ajuste. E a alternativa (uma terceira operação que delega às duas) colocaria a classificação em
dois lugares.

*Alternativa considerada:* manter os dois e o novo use case escolher entre eles. Rejeitada: a
regra `< dueMonth da OPEN` passaria a ter dois donos, um no `GetOrCreate` e outro na criação.

*Nome:* a operação nova ocupa o nome `CreateInvoiceUseCase`. O arquivo morto é **substituído**,
não deixado para trás — assim não sobra um nome atraente com comportamento errado.

### D2 — `GetOrCreateInvoiceForMonthUseCase` passa a delegar

Ele mantém o que é dele — achar a fatura existente e recusar a fechada
(`GetOrCreateInvoiceForMonthUseCaseImpl.kt:28-38`) — e entrega a criação a D1. A comparação com a
fatura `OPEN` sai dele.

*Por quê:* o consumidor (transação, parcelamento, recorrente) não muda de comportamento, e a
classificação fica onde a criação está. A modal e o lançamento produzem faturas indistinguíveis
porque atravessam o mesmo código, não porque duas implementações concordam.

### D3 — A referência da classificação é a fatura `OPEN`, não hoje

Mantida a regra atual: `dueMonth < dueMonth da OPEN` → `RETROACTIVE`, caso contrário `FUTURE`.

*Por quê:* é a regra que já governa o lançamento, e a criação não pode divergir dela. Ela produz
um resultado que surpreende à primeira leitura — se a fatura aberta vence em julho e hoje é
outubro, agosto nasce `FUTURE` — mas trocar a referência por "hoje" criaria duas classificações
diferentes para o mesmo mês conforme o caminho, o que é pior que a surpresa.

### D4 — A modal navega meses e recusa o ocupado exibindo-o

O seletor reusa `InvoiceMonthNavigator` (`core/ui`) sobre `InvoiceMonthSelection` (`core/model`),
que já deriva a janela de meses inexistentes. Num mês ocupado, o mês continua visível e sinalizado,
e o envio fica indisponível.

*Por quê:* é o precedente do app para alvo indisponível (`AddTransactionViewModel.kt:197-198`
desabilita o envio em fatura bloqueada, sem esconder o mês).

*Alternativa considerada:* setas que saltam meses ocupados. Rejeitada: navegação que pula tira do
usuário a referência de onde ele está no calendário, e o salto seria maior quanto mais faturas o
cartão tem — pior justamente no cartão mais usado.

*Backstop:* a recusa por colisão permanece no domínio (hoje `InvoiceError.AlreadyExists`), e os
índices únicos de `InvoiceEntity.kt:34-36` continuam sendo a última linha contra corrida entre a
checagem e o insert. A modal não é a única guarda.

### D5 — A ação mora na tela de faturas, e criar navega até a fatura criada

A tela é `InvoiceTransactionsScreen`, cujo pager percorre as faturas existentes
(`pageCount = { invoices.size }`, `InvoiceTransactionsScreen.kt:439`) já ordenadas. Criar insere
uma página na pilha, e a seleção passa a apontar para ela.

*Por quê:* sem navegar, o gesto parece não ter feito nada — a fatura nova entra no meio da pilha,
possivelmente fora da tela. O ajuste de saldo já está no card do pager
(`InvoiceTransactionsScreen.kt:263-270`, `onEditInvoice`, disponível em cartão não arquivado), então
chegar lá é suficiente para o gesto seguinte.

*Cartão arquivado:* a tela é histórico somente-leitura (`if (!uiState.isArchived)`,
`InvoiceTransactionsScreen.kt:275`) e a ação de criar segue a mesma regra das demais.

### D6 — Criar não encadeia o ajuste

Após criar, nenhum formulário abre sozinho.

*Por quê:* criar e declarar o valor são intenções distintas — a separação é a razão de a criação
existir como operação própria em vez de virar responsabilidade do ajuste. Encadear reintroduziria
o acoplamento pela porta da UI. Um fluxo guiado continua possível depois, se a fricção se
mostrar real; o contrário (desfazer um encadeamento) é mais caro.

### D7 — A modal herda a dependência da fatura `OPEN`

Oferecer passado **e** futuro obriga a classificar, e classificar exige a `OPEN`
(`InvoiceError.NoOpenInvoice`). Logo, a modal não socorre o cartão que ficou sem nenhuma fatura.

*Por quê:* esse cartão é um bug, não um caso de uso — ele existe porque `AddCreditCardUseCase.kt:48`
chama `openInvoiceUseCase` dentro de um `onRight` sem `bind`, descartando a falha. O lugar de
consertar é lá. Restringir a modal ao passado dispensaria a `OPEN` e daria a recuperação de graça,
mas ao custo de recusar o futuro sem motivo de domínio.

*Registrado como risco abaixo, não como escopo.*

## Risks / Trade-offs

**[O `NoOpenInvoice` fica sem saída pela interface]** → Conhecido e aceito (D7). O conserto é uma
linha em `AddCreditCardUseCase`, em mudança própria; esta não piora a situação, apenas não a
melhora.

**[Alguém religa o comportamento antigo pelo nome]** → O arquivo morto é substituído no mesmo
commit, e o registro em `UseCaseModule.kt:105` passa a apontar para a operação nova. Não sobra
símbolo com o nome certo e o corpo errado.

**[Criar uma `FUTURE` por engano ao navegar para frente]** → Reversível: `FUTURE` é `isDeletable`
(`Invoice.kt:85-87`), e a exclusão remove a dimensão junto (`InvoiceRepository.kt:244-252`). A
fatura nasce vazia, então nada se perde.

**[Faturas não contíguas no pager]** → Já acontece hoje ao lançar em meses salteados; a modal
apenas torna o efeito mais alcançável. Não é regressão, e preencher os buracos seria criar
faturas que ninguém declarou.

**[Regressão silenciosa nos consumidores existentes]** → Transação, parcelamento e recorrente
passam a criar por um caminho novo. Os testes de `AddInstallmentUseCaseTest` e do ciclo de vida
já cobrem status resultante; a rede é reforçada com teste direto da classificação.

**[Duas `OPEN` no mesmo cartão]** → O risco que o código morto carregava. A operação nova nunca
produz `OPEN`: abrir continua sendo exclusividade de `OpenInvoiceUseCase`, chamado pelo cadastro do
cartão e pelo fechamento.
