## Context

Um rendimento de CDI é, contabilmente, uma receita comum: `ASSET ← INCOME`. O razão já sabe escrevê-la e já sabe somá-la — nada no modelo precisa mudar. O que falta é **onde clicar** e **como ler**.

O estado atual empurra o usuário para o caminho errado. O único jeito de refletir um rendimento é `EditAccountBalanceModal`, que chama `AdjustBalanceUseCase` e grava `ContraLeg(AccountType.EQUITY)` — reconciliação. O saldo sobe e o valor some do relatório, porque o app o classificou como "eu tinha errado o saldo", não como "rendeu".

Duas restrições moldam tudo o que segue:

1. **`:core:ledger` não pode nomear fachada.** Ele não sabe o que é uma categoria. Qualquer separação de rendimento numa query do razão precisa ser expressa em vocabulário de razão — dimensão, natureza, período.
2. **A gramática de resumo exige partição.** `transaction-scope` determina que as linhas de fluxo particionem `Σ entries` do perímetro: toda entry pertence a exatamente uma linha. Uma linha nova não pode ser somada por cima — ela tem de **sair** de outra.

## Goals / Non-Goals

**Goals:**
- Lançar rendimento com atrito mínimo, a partir da tela onde o usuário já olha o saldo.
- Separar, na leitura, o dinheiro que o usuário ganhou do dinheiro que rendeu — em Contas e Transações.
- Fazer o rótulo de cada linha declarar o que está dentro do número, de modo que a diferença entre telas seja legível em vez de contraditória.
- Não introduzir modelo novo no razão, nem um segundo caminho de escrita.

**Non-Goals:**
- Calcular rendimento a partir de taxa ou percentual do CDI.
- Gerar o lançamento automaticamente (recorrência).
- Segregar rendimento no relatório.
- Separar patrimônio "investido" de "disponível" — a premissa é a conta **líquida**.
- Alterar o comportamento de "editar saldo", que permanece `EQUITY`.

## Decisions

### D1 — Lançamento, não ajuste

**Decisão:** o modal de rendimento escreve uma transação nova a cada uso. Não há valor-alvo.

O desenho alternativo — reaproveitar "editar saldo" e trocar a contrapartida de `EQUITY` para `INCOME` quando a conta rende — foi considerado e **rejeitado**. Um ajuste persegue um valor-alvo, e por isso `AdjustBalanceUseCase` precisa reconhecer o ajuste anterior para reescrevê-lo em vez de acumular (`AdjustBalanceUseCase:43-49`). Ele o reconhece pela forma no razão: *a transação nesta data, nesta conta, com contrapartida `EQUITY`*. Isso funciona porque **nada mais no app cria perna `EQUITY`**.

`INCOME` não tem essa propriedade. `ASSET ← INCOME` é exatamente a forma de uma receita comum — e a premissa da mudança é a conta corrente que rende, onde o salário cai no mesmo dia. Duas transações indistinguíveis, e o reajuste sobrescreveria o salário.

Um lançamento não tem esse problema porque não reconhece nada. Cada rendimento é uma transação, como qualquer receita. O efeito colateral é que "editar saldo" fica intocado — nenhum comportamento existente muda.

### D2 — `yieldsInterest: Boolean` na conta, e por que ele não entra em nenhuma query

**Decisão:** `Account.yieldsInterest: Boolean`, em `:core:ledger`, sem participação em cálculo.

A separação dos totais é feita inteiramente pela dimensão (D3). O interruptor governa apenas afordância: se a conta oferece o modal, e se a linha aparece **zerada**. A razão de ele existir mesmo assim é o **primeiro mês**: uma conta recém-marcada ainda não tem lançamento de rendimento, e uma linha derivada apenas dos lançamentos exibiria nada — sem lugar para clicar e começar. O interruptor é o que faz `Rendimentos R$ 0,00` aparecer e ser clicável.

O que ele **não** pode governar é a exibição de um rendimento já lançado. A dimensão separa independentemente dele, então a linha de entradas deixou de conter aquele valor; ocultar a linha ao desligar o interruptor tiraria o dinheiro de ambas e faria a coluna deixar de fechar. O critério de exibição é, portanto, `declarado || período contém rendimento` — e só a declaração torna a linha acionável.

Duas coisas, dois donos: o `Boolean` diz *se a linha é oferecida*; a dimensão diz *qual é o número* — e um número diferente de zero exige a sua linha, tenha sido oferecida ou não.

`chart-of-accounts` não é modificada por isso. Ela restringe o conjunto de `type` e proíbe categoria como linha do plano — nada sobre colunas adicionais na linha de conta. E `poupança` e `investimento` já estão nomeadas ali como `ASSET`.

### D3 — Uma categoria de sistema única, e é ela que viabiliza a segregação no agregado

**Decisão:** uma categoria `INCOME` de sistema, global, identificada por `systemKey`, criada sob demanda no primeiro interruptor ligado.

A alternativa era `yieldDimensionId: Long?` **por conta** — cada conta apontando para a sua categoria de rendimento, escolhida pelo usuário. Ela funciona no `AccountCard`, onde existe "a conta". **Não funciona no `SummaryCard`**, que agrega todas as `ASSET` do mês: não há uma conta de onde ler a dimensão, e a query teria de varrer conta a conta.

Com uma dimensão única e global, as duas leituras recebem o mesmo `Long`:

```
accountPeriodTotals(accountId, yearMonth, yieldDimensionId)   → AccountCard
assetMonthTotals(yearMonth, yieldDimensionId)                 → SummaryCard
```

O razão recebe um identificador de dimensão e continua sem saber o que ele é — a fronteira do módulo permanece intacta, pelo mesmo precedente de `DimensionKind.INVOICE`, que é rótulo legível e não conceito manipulado.

**Identificada por chave, não por nome.** A consequência que se quer é que o usuário possa renomear a categoria para "CDI" ou "Rendimento PicPay", trocar o ícone, e nada quebrar. Uma categoria de sistema que ele pode adotar como sua.

**Criada sob demanda**, no mesmo espírito de `SystemAccount`: a fronteira garante a existência quando algo a referencia, em vez de semear na instalação. Se o usuário nunca liga rendimento, a categoria nunca existe.

### D4 — Um quarto guard, não um terceiro estado de retirabilidade

**Decisão:** a proteção da categoria de rendimentos entra como mais um dependente em `CategoryRetirability`, resolvendo `MustArchive`.

A leitura ingênua de "categoria de sistema" é *imutável, não apagável, não arquivável* — e isso exigiria uma terceira resposta ao par `Deletable`/`MustArchive`, vazando para toda tela que hoje trata o par como exaustivo. Custo real, por uma garantia que ninguém pediu.

O quarto guard — *existe conta com rendimento habilitado?* — encaixa no mecanismo que já existe, irmão de `HAS_RECURRING`, com a mesma gramática de recusa ("desligue o rendimento das contas antes de excluir"). E dá uma semântica melhor: a categoria é protegida **enquanto alguém a usa**, e volta a ser comum quando o último interruptor desliga.

### D5 — A separação nas queries é um flag a mais no subselect que já existe

`accountPeriodTotals` já classifica cada entry por contrapartida, com dois `EXISTS` por transação (`eq` para `EQUITY`, `li` para `LIABILITY`). O rendimento é um terceiro do mesmo formato:

```sql
EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId
       WHERE x.transactionId = e.transactionId
         AND a.type = 'INCOME' AND x.dimensionId = :yieldDimensionId) AS yl
```

E a partição se mantém total e disjunta porque a linha de entradas **exclui** o que a nova inclui:

```
income = ... AND yl = 0 AND amount > 0     -- deixa de conter rendimento
yield  = ... AND yl = 1 AND amount > 0     -- a linha nova
```

Com `yieldDimensionId` nulo (nenhuma conta rende ainda, categoria inexistente), a comparação com `NULL` nunca é verdadeira, `yl` é sempre `0`, e os totais são bit a bit os de hoje. **Conta sem rendimento não precisa de caso especial em lugar nenhum** — nem no SQL, nem no mapeamento, nem na UI.

### D6 — A regra de vocabulário mora em `money-display`

**Decisão:** o par Entrada/Saída vs Receita/Despesa é uma regra com dono único, em `money-display`.

Ela atravessa quatro superfícies de donos diferentes — `AccountCard` (`:core:ui`), `SummaryCard` (`transaction-scope`), relatório (`ledger-reporting`) e painel (`dashboard-balance-widgets`). Alojá-la em qualquer uma delas obrigaria as outras três a reimplementá-la, que é exatamente o que a regra de derivação do projeto proíbe. `money-display` já é a spec de *como uma figura se lê*; o rótulo que a nomeia é a mesma pergunta, um passo antes do sinal.

O enunciado precisa de uma cautela, e ela decide dois casos limite:

> A regra vale para **decomposição fixa de um total**, onde as linhas são fluxos nomeados pelo app.

- **Filtros e formulários ficam de fora.** Ali a palavra nomeia um *tipo de transação*, não o conteúdo de uma soma, e "isso inclui rendimento?" não faz sentido num chip. Esse eixo segue `TransactionType` e é uniformemente Receita/Despesa — o que de quebra conserta o par misto `transactions_filter_type_income`/`_expense`, incoerente desde antes desta mudança.
- **Agrupamento por dimensão fica de fora.** "Receitas por Categoria" separa rendimento por construção, já que ele é uma categoria. Se contasse como segregação, todo agrupamento por categoria seria Entrada/Saída e a regra não distinguiria nada.

O ganho é que a regra é **verificável olhando a tela**, e o vocabulário passa a seguir a forma do número em vez da tela: se o relatório um dia segregar, ele se renomeia sozinho.

### D7 — Escopos que segregam

`Contas` e `Geral` ganham a linha: ambos carregam fluxos `ASSET` e ambos já usam as mesmas chaves `summary_card_income`/`_outgoing`. `Cartões` não: o perímetro é `LIABILITY`, não há rendimento, e ele já tem vocabulário próprio (gasto/pagamento).

No resumo o critério é apenas *o período contém rendimento* — sem a metade "declarada" de D2. Ali não há o que clicar, então uma linha zerada não convidaria a nada; e o resumo agrega o perímetro inteiro, onde "qual conta declarou" não é uma pergunta que a linha responda. O `AccountCard` é quem tem uma conta e um lugar para tocar, e é lá que a linha zerada serve.

## Risks / Trade-offs

- **A mesma receita lida com dois números em telas diferentes** (R$ 5.012,40 no relatório, R$ 5.000,00 + R$ 12,40 nas outras) → é o efeito pretendido, e o vocabulário de D6 é a mitigação: o rótulo declara o que está dentro. O risco residual é o rótulo ser aplicado errado numa tela futura, contra o qual valem os cenários da spec.

- **`accounts_income`/`_expenses` mudam de texto para usuários existentes** → mudança visível sem perda de dado; o número não muda de valor, só de nome, e a linha nova explica a diferença. Aceito.

- **A categoria de sistema é o primeiro objeto do app que o usuário não pode apagar livremente** → mitigado por D4: a proteção é condicional e reversível, não um decreto, e a mensagem de recusa diz exatamente o que fazer para liberá-la.

- **O usuário pode continuar registrando rendimento por "editar saldo"**, gerando `EQUITY`, sem que nada o impeça → aceito nesta mudança. O caminho novo é mais curto e está na mesma tela; se o uso mostrar que não basta, um aviso no modal de ajuste é aditivo e não exige rever nada aqui.

- **Renomear a categoria para algo genérico** ("Extra") deixa o relatório menos legível sem quebrar leitura alguma → consequência aceita de identificar por chave; é o preço da propriedade que se quis.

- **A regra de vocabulário é uma convenção de texto, não uma invariante que o compilador verifique** → mitigado por cenários de spec e por ser aplicada, hoje, a um conjunto pequeno e enumerado de chaves.

## Migration Plan

Migração v10 → v11, aditiva e sem backfill:

- `accounts.yieldsInterest INTEGER NOT NULL DEFAULT 0` — nenhuma conta existente rende até o usuário dizer.
- `categories.systemKey TEXT DEFAULT NULL` — nenhuma categoria existente é de sistema.

Nenhum dado é reinterpretado e nenhuma transação é reescrita. Ajustes `EQUITY` já gravados permanecem ajustes: eles registram o que o usuário de fato pediu na época, e reclassificá-los como rendimento seria inventar intenção. Rollback é o downgrade padrão de coluna aditiva.

A remoção do template "Investimentos" vale só para instalações futuras — quem já criou as categorias padrão mantém a sua, que segue funcionando como categoria comum.

## Open Questions

- **A cor da linha de rendimento.** Precisa ser irmã de `Income` e distinguível dela, senão a segregação não se lê. Definição para `:core:designsystem` na implementação.
- **O texto em inglês do par segregado.** `Money In / Money Out` é a proposta, com `Yield` na linha nova; a distinção pt entre "entrada" e "receita" não tem equivalente direto, e vale uma revisão de quem lê o app em inglês antes de congelar.
