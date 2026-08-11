## Context

O valor do widget Saldo em Contas nasce em três linhas, sem nenhum ponto de configuração:

```kotlin
// feature/dashboard/impl/.../DashboardComponentsBuilder.kt:175-187
private suspend fun totalBalance(input: DashboardComponentsInput): DashboardComponent.TotalBalance =
    DashboardComponent.TotalBalance(
        amount = input.figure(calculateBalanceUseCase(target = input.targetMonth), DisplayAmount::natural),
    )
```

```sql
-- core/ledger/.../EntryDao.kt:225-232
SELECT e.currency AS currency, COALESCE(SUM(e.amount), 0) AS total FROM entries e
JOIN transactions o ON o.id = e.transactionId
JOIN accounts a ON a.id = e.accountId
WHERE a.type = :type AND substr(o.date, 1, 7) <= :yearMonth
GROUP BY e.currency
```

As restrições que moldam a solução:

- **`ledger-reporting` proíbe consulta paralela.** O requisito "Saldo até um mês por natureza de conta" diz textualmente: *"MUST NOT existir uma segunda consulta que derive o mesmo acumulado com a natureza fixada no seu próprio texto"*, e o cenário "Sem caminho duplicado para ativos" exige que o acumulado de `ASSET` derive *"da mesma leitura parametrizada"*. Qualquer desenho que crie um segundo caminho para esse número está fora antes de começar.
- **`ledger-reporting` proíbe o razão nomear fachada.** A leitura é expressa *"em vocabulário de razão — natureza de conta e mês"*.
- **O construtor do widget não soma conta a conta.** O código já saiu desse desenho de propósito; o comentário que substituiu a soma em linha ainda está lá (`DashboardComponentsBuilder.kt:298-300`). E somar em Kotlin perderia o `GROUP BY e.currency` que a consulta faz — a leitura é multimoeda por construção (`ledger-reporting`, "Leitura que atravessa contas é expressa por moeda").
- **As contas que a tela oferece já são as abertas.** O dashboard consome `observeAllAccounts()` (`DashboardViewModel.kt:102`), que exclui arquivadas — o método que as inclui é o `...IncludingClosed` (`AccountRepository.kt:15-29`).
- **A preferência de widget é um `Map<String, String>` livre**, serializado em `Settings` com `ignoreUnknownKeys = true` e `config` com padrão vazio. Chave nova não quebra preferência antiga.

## Goals / Non-Goals

**Goals:**

- Que o usuário decida quais contas compõem o número do Saldo em Contas, no mesmo lugar e com a mesma gramática das demais opções de widget.
- Que o perímetro seja honrado **dentro** da consulta que já produz o número, mantendo um caminho só e a expressão por moeda.
- Que o razão continue ignorante da razão da exclusão.
- Que um dashboard existente exiba exatamente o mesmo número de antes.

**Non-Goals:**

- Fazer a exclusão propagar para os widgets de fluxo do mês, para a tela de contas, para relatórios ou para orçamentos.
- Sincronizar com a exclusão do card de Contas.
- Excluir cartões, ou tratar `LIABILITY` de alguma forma nova.
- Transformar "não conta para o total" em propriedade da conta.

## Decisions

### 1. O conjunto excluído é configuração do widget, não propriedade da conta

Chave `excluded_account_ids` no mapa de config do `TOTAL_BALANCE`, num `TotalBalanceConfig` próprio, com o mesmo nome e o mesmo formato (`Long` separados por vírgula) que `AccountsOverviewConfig` já usa. Mapas de config são por instância de widget, então não há colisão.

*Alternativa considerada:* um campo em `Account` — algo como `countsTowardTotals`, ao lado de `yieldsInterest`. Rejeitada. O `core/ledger/README.md` sustenta que o razão *"sabe que uma moeda existe e nada sobre qual"*, porque qual moeda o usuário lê é opinião do app e vive acima. "Esta conta não conta para o meu patrimônio" é exatamente a mesma categoria de opinião, e pô-la em `Account` levaria uma preferência de exibição para dentro de `:core:ledger`, além de exigir migração de banco por uma decisão que o usuário toma e desfaz na tela.

*Alternativa considerada:* uma preferência de perímetro na camada de consolidação (`:core:model`), irmã de moeda base e taxas. Rejeitada **para esta change**, não em definitivo: ela só se paga quando mais de um consumidor precisar do mesmo perímetro, e hoje há um. Se e quando a exclusão precisar valer também para os fluxos do mês ou para a tela de contas, é para lá que ela deve subir — e este design não fecha essa porta, porque o razão terá ganhado a capacidade de responder por um subconjunto qualquer, seja quem for o dono do conjunto.

### 2. O perímetro entra na consulta, como conjunto de ids, na mesma `@Query`

`balanceUpToMonthByType` ganha `AND e.accountId NOT IN (:excludedAccountIds)`. Uma consulta, um caminho, e o `GROUP BY e.currency` preservado.

O filtro é por `e.accountId` e não por `a.id` — são o mesmo valor pelo `JOIN`, e usar a coluna da própria entry evita o `JOIN accounts` na cláusula onde ele não acrescenta nada.

**Conjunto vazio não precisa de ramo.** O SQLite aceita lista vazia em `IN` / `NOT IN` — é extensão documentada dele, e `NOT IN ()` é verdadeiro para toda linha. Todas as três plataformas do app rodam SQLite, então o caso "nada excluído" é a mesma consulta com o parâmetro vazio, e não um segundo caminho. Isso é premissa a **verificar por teste**, não a assumir: a suíte deve provar que excluir nada devolve exatamente o valor de hoje.

*Alternativa considerada:* `Σ(todas) − Σ(excluídas)`, usando a leitura escalar por conta que já existe. É aritmética exata — mesma moeda, sem conversão — mas cria um segundo caminho para o mesmo número, que `ledger-reporting` proíbe e que o `core/ledger/README.md` recusa em geral (*"no second way to compute a number"*).

*Alternativa considerada:* inverter para uma lista de inclusão. Rejeitada porque obrigaria o chamador a conhecer e enumerar todas as contas para pedir o comportamento padrão, e faria a leitura perder a semântica "toda conta desta natureza" que `ledger-reporting` exige que ela tenha.

### 3. O razão recebe identidades, nunca o motivo

O parâmetro se chama por aquilo que ele é — contas a excluir da soma — e a leitura não ganha nenhum vocabulário de dashboard, de widget ou de preferência. `ledger-reporting` exige que a leitura fale de natureza de conta e mês; excluir **por identidade de conta** permanece dentro desse vocabulário, porque uma conta do plano é entidade do razão. O que ficaria de fora seria um parâmetro que nomeasse fachada ou intenção.

Consequência aceita: o razão passa a poder responder por perímetros que ele não consegue justificar. É o preço de ele não opinar, e é o mesmo arranjo dos dois portos que `:core:ledger` já expõe às fachadas.

### 4. Excluir todas as contas exibe zero, e isso não custa código

`ConsolidateMoneyUseCase` já decide o que é um zero e em que moeda ele se denomina quando o razão não devolve linha nenhuma — o ramo `terms.isEmpty()` existe e está documentado, porque um mês sem movimento já produz agregado sem linhas. Excluir todas as contas cai exatamente nesse ramo.

Portanto o widget **não** ganha ramo de nulidade: ele continua sendo o único do dashboard que nunca retorna `null`, e a decisão "zero, não some" é o comportamento que sai de graça do que já existe.

*Alternativa considerada:* esconder o widget, como `AccountsOverview` faz quando fica sem contas. Rejeitada porque as duas situações não são a mesma: um card de lista sem itens não tem o que desenhar; um total sem parcelas tem — vale zero. E um widget que desaparece enquanto o usuário mexe na sua própria configuração torna a configuração difícil de operar.

### 5. Id órfão é ignorado, sem limpeza

Se a conta excluída for apagada, o id fica na preferência e deixa de casar com linha alguma. Nada acontece, e nada precisa acontecer: `NOT IN` sobre um id inexistente não exclui nada. É exatamente o que o card de Contas já faz com `id !in excludedIds`.

Não há rotina de limpeza. Ela custaria observar o ciclo de vida das contas dentro da feature de dashboard para corrigir um dado que não produz sintoma — e teria o efeito perverso de que recriar uma conta com o mesmo id (possível, com `AUTOINCREMENT` desligado) a traria de volta silenciosamente excluída, que é o único caso em que a sujeira seria visível.

### 6. O rótulo do widget não muda, e a regra de honestidade é satisfeita pela autoria

`dashboard-balance-widgets` leva rótulos a sério: *"um rótulo neutro sobre uma só natureza de conta é afirmação falsa"*. Com o perímetro reduzido, "Saldo em Contas" descreve algumas contas, não todas — e a pergunta é se isso viola a mesma regra.

Não viola, e a distinção é **quem escolheu o perímetro**. A regra existente protege o usuário de um perímetro que *o sistema* escolheu por ele e que ele não tem como conhecer — daí a exigência de que o rótulo o revele. Aqui o perímetro é autoral: o usuário o definiu, ele está inteiro visível nas opções do próprio widget, e as contas de fora aparecem no card de Contas logo abaixo. Um rótulo que enumerasse o recorte descreveria ao usuário uma decisão dele.

*Alternativa considerada, e a mais forte contra esta decisão:* um indicador discreto quando o perímetro está reduzido — algo como "3 de 5 contas" —, exibido apenas quando há exclusão. O argumento a favor é bom: seis meses depois o usuário não se lembra de ter configurado, e outra pessoa olhando a tela não tem como saber. Ficou de fora por disciplina de escopo — não foi pedido, e acrescenta superfície de UI e strings a uma change que de resto não tem nenhuma. **É a decisão deste design mais barata de reverter e a que mais merece ser reconsiderada**, e nada aqui a impede depois: ela seria um requisito acrescentado, não um requisito reescrito.

### 7. Os fluxos do mês ficam de fora, e a divergência é declarada

Excluir uma conta do saldo não exclui as receitas e despesas dela dos widgets de fluxo, que leem `assetMonthFlowsByCurrency` — outra leitura, outro perímetro. O usuário pode, portanto, ver despesa de uma conta que não compõe o seu saldo.

Isso é incoerente na superfície, e é aceito com dois motivos. Primeiro, foi decidido fora de escopo. Segundo, e mais relevante: as duas leituras respondem perguntas diferentes — "quanto eu tenho" e "quanto se moveu" —, e um usuário que tira uma reserva do saldo pode legitimamente continuar querendo ver o que se moveu nela. Estender o perímetro ao fluxo seria uma afirmação mais forte sobre a intenção do usuário do que a que ele expressou ao desmarcar uma conta num widget de saldo.

Se essa divergência incomodar na prática, a saída é a decisão 1 — subir o perímetro para a camada de consolidação, com um dono só — e não replicar a exclusão widget a widget.

## Risks / Trade-offs

- **Duas exclusões que não conversam.** O card de Contas e o Saldo em Contas passam a ter, cada um, o seu "não quero essa conta aqui", e o usuário que quiser as duas coisas configura duas vezes. Decidido assim conscientemente; mitigação futura é a decisão 1, não uma terceira cópia.
- **Um número que muda de exatidão.** A figura é consolidada. Se a conta excluída for a única de uma moeda sem taxa registrada, o total deixa de ter dois termos e passa a ser exato e sem marca de aproximação. Não é defeito — é o perímetro fazendo o que promete —, mas é mudança de **forma** do número e por isso está enunciada como requisito, não deixada a descobrir.
- **A assinatura de leitura muda e a suíte inteira sente.** `IEntryRepository` é implementado como stub em muitos testes. Um valor padrão vazio no parâmetro mantém a atualização mecânica, mas ela é ampla.
- **O razão fica capaz de responder por perímetros arbitrários.** Alguém pode usar isso para reimplementar acima do razão uma regra que deveria ter dono no domínio. O contrapeso é a regra de derivação do projeto, não o compilador.
