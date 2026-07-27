## Why

Contas com liquidez que rendem pelo CDI — PicPay, Nubank, Inter — são hoje a conta corrente da maioria dos usuários, e o app não tem onde colocar o rendimento delas. O único caminho disponível é "editar saldo", que grava um ajuste com contrapartida `EQUITY` de reconciliação: o dinheiro entra no saldo e **desaparece do relatório**, classificado como correção de erro em vez de receita. O usuário vê o patrimônio crescer sem nenhuma linha que explique por quê.

O problema não é de modelo — um rendimento *é* uma receita, e o razão já sabe registrá-la. É de **afordância e de leitura**: não há onde lançar sem atrito, e não há como distinguir o dinheiro que o usuário ganhou do dinheiro que trabalhou sozinho.

## What Changes

- **A conta ganha um interruptor de rendimento.** `Account.yieldsInterest: Boolean` (padrão `false`), ligado no formulário da conta. Ele não participa de cálculo algum: decide apenas se a conta oferece o afordance de rendimento. Isso é deliberado — uma conta recém-marcada precisa exibir `Rendimentos R$ 0,00` **clicável** no primeiro mês, e uma linha derivada dos lançamentos não teria onde ser clicada.

- **Uma categoria de sistema "Rendimentos", criada sob demanda.** No primeiro interruptor ligado, o app garante a existência de uma categoria `INCOME` identificada por `systemKey`, não por nome — de modo que o usuário **pode renomeá-la** para "CDI" ou "Rendimento PicPay" e trocar o ícone sem quebrar nada. É a dimensão dessa categoria que separa rendimento de receita em toda leitura.

- **Lançar rendimento é um lançamento, não um ajuste.** Um modal pede **data e valor** e escreve uma transação comum `ASSET ← INCOME` carregando a dimensão de rendimentos. Não há valor-alvo, não há reconhecimento do lançamento anterior, não há contrapartida `EQUITY`. **"Editar saldo" permanece exatamente como está** — esta mudança não o toca.

- **Uma linha própria em Contas e em Transações.** `AccountCard` e `SummaryCard` (escopos *Contas* e *Geral*) ganham a linha **Rendimentos**, logo após a de entradas, com cor própria. Ela aparece quando a conta tem o interruptor ligado — e, no agregado, quando alguma conta tem. O relatório **não** segrega: lá rendimento continua dentro de receita.

- **A regra de vocabulário que torna isso legível.** Numa decomposição fixa de um total, se *Rendimentos* é uma das linhas o par é **Entrada/Saída**; se não é, o par é **Receita/Despesa** — e fica subentendido que receita engloba rendimento. O rótulo passa a responder *o que está dentro do número*, e a divergência entre telas deixa de ser incoerência para virar informação. A regra já existe no código pela metade: `summary_card_income`/`_outgoing` já diz "Entradas/Saídas" e o relatório já diz "Receitas/Despesas" — **só o `AccountCard` está fora de posição**, e é justamente a tela onde a linha nova entra.

- **A categoria de rendimentos é protegida enquanto alguma conta rende.** Não por um estado novo de imutabilidade, mas por um **quarto guard** somado aos três existentes (movimento, orçamento, recorrência): `CategoryRetirability` responde `MustArchive` quando há conta com rendimento habilitado. Desligado o último interruptor, ela volta a ser uma categoria comum. Nada é imortal por decreto.

- **A categoria padrão "Investimentos" sai da lista de templates.** Ela é conceitualmente a mesma coisa que a de rendimentos e conviveria como duplicata confusa.

- **BREAKING (texto visível, sem impacto em dados):** `accounts_income`/`accounts_expenses` passam de "Receitas/Despesas" para "Entradas/Saídas"; `balance_card_account_income`/`_expense` fazem o caminho inverso, de "Entradas/Saídas na conta" para "Receitas/Despesas na conta", porque o painel não segrega. Junto vai a correção do par misto `transactions_filter_type_income`/`_expense` ("Entrada"/"Despesa"), que já estava incoerente antes desta mudança.

## Capabilities

### New Capabilities
- `yield-accounts`: o que é uma conta com rendimento — o interruptor e o que ele governa, a categoria de sistema e como ela é identificada, o lançamento de rendimento como transação comum classificada por dimensão, e a separação do rendimento nas leituras que o segregam.

### Modified Capabilities
- `transaction-scope`: a "Gramática única de resumo por escopo" exige que as linhas de fluxo **particionem** `Σ entries` do perímetro. A linha de rendimento é uma **reparticão** — o rendimento sai da linha de entradas e passa a ter a sua —, e a spec precisa dizer que a partição continua total e disjunta, e em quais escopos ela se aplica.
- `account-lifecycle`: o guard de retirabilidade de categoria hoje se resolve por três dependentes. Soma-se o quarto — existir conta com rendimento habilitado —, sem introduzir um terceiro estado ao par `Deletable`/`MustArchive`.
- `money-display`: a spec hoje diz como o **sinal** de uma figura se lê. Soma-se a regra de como a **linha que a nomeia** se chama, e o que esse nome promete estar dentro. Cabe aqui, e não em `transaction-scope`, porque a regra atravessa quatro superfícies de donos diferentes — cartão de conta, resumo de escopo, relatório e painel — e precisa de dono único; o *Purpose* alarga de "como um valor se lê" para incluir o rótulo que o acompanha.

## Impact

- **`core/ledger`** — `Account` ganha `yieldsInterest`; `AccountEntity` ganha a coluna; `AccountPeriodTotals` e `AssetMonthTotals` ganham `yield`, e as duas queries correspondentes ganham o parâmetro `yieldDimensionId` e um terceiro flag no subselect que já classifica por contrapartida. O razão recebe um `Long` e continua sem saber o que ele é — a fronteira do módulo permanece de pé.
- **`core/database`** — migração v10 → v11: coluna `accounts.yieldsInterest` e coluna `categories.systemKey`, ambas com default seguro; `AccountMapper` e `CategoryMapper` acompanham. Sem backfill: nenhuma conta existente rende até que o usuário diga.
- **`core/model`** — `Category` ganha `systemKey`; `CategoryRetirability` ganha o motivo novo (sem caso novo).
- **`core/ui`** — `AccountCard` ganha a linha de rendimento com o `AccountSummaryRow` clicável que já existe para "Saldo Inicial" e "Saldo"; `AccountUi` ganha o campo.
- **`feature/accounts/*`** — interruptor no `AccountFormModal`, `LaunchYieldModal` novo (data + valor), o use case de lançamento, o use case que garante a categoria de sistema, e o guard consultado pela retirabilidade de categoria.
- **`feature/categories/*`** — `ICategoryRepository` ganha a busca por `systemKey`; `CreateDefaultCategoriesUseCase` perde o template "Investimentos"; a resolução de retirabilidade consulta o quarto guard.
- **`feature/transactions/impl`** — `BalanceOverview.Accounts` e `.Overall` ganham `yield`; `SummaryCard` ganha a linha nos dois corpos.
- **`core/designsystem`** — cor própria para rendimento, irmã de `Income` e distinta dela; sem uma, a segregação não se lê.
- **`core/resources`** — strings novas (linha, modal, interruptor, motivo de recusa) e as renomeações da regra de vocabulário, em pt **e** en. Em inglês a distinção não tem tradução direta: o par segregado é **Money In / Money Out**, o agregado permanece **Income / Expenses**, e a linha nova é **Yield**. Isso também conserta o par misto `summary_card_income`/`_outgoing` ("Income"/"Outgoing").
- **`core/analytics`** — evento de lançamento de rendimento.

**Fora de escopo** (dívida registrada, não tarefas desta mudança): calcular o rendimento a partir de uma taxa cadastrada ou de um percentual do CDI; gerar o lançamento automaticamente por recorrência; segregar rendimento no relatório; e separar patrimônio "investido" de "disponível" no painel — a premissa desta mudança é a conta **líquida**, cujo saldo continua integralmente disponível.
