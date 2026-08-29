## 1. A origem do ciclo lançado

- [x] 1.1 Resolver, no `RecurringViewModel`, a origem que cada transação lançada registrou: a partir das pernas da transação, a conta do plano — e, quando a perna é `LIABILITY`, o cartão que a projeta, pelo `accountId`.
- [x] 1.2 Fazer essa resolução **uma vez para a seção inteira**, dentro de `ledgerRowsOf`, aproveitando as transações que ela já busca em bloco e o plano de contas que o view model já lê para denominar os templates. Nenhuma query por linha.
- [x] 1.3 Carregar a origem resolvida em `RecurringCycleUi.Posted`, com KDoc que diga por que ela vem do razão e não do template (a confirmação sobrescreve conta e cartão, e a ocorrência não guarda a escolha).

## 2. A linha única

- [x] 2.1 Dar a `RecurringCard` um contrato que exprima o que a linha **afirma** — identidade, figura, origem, linha do tempo — em vez de receber `Recurring` ou `TransactionUi` crus, de modo que o componente não saiba que existem duas fontes.
- [x] 2.2 Manter no componente o módulo já estabelecido: chip 40dp/raio 8, espaçamento 12dp, `CHIP_SIZE`/`ROW_LINE_GAP` como único dono da altura (68dp).
- [x] 2.3 Fazer a linha lançada afirmar a data do registro no slot em que a de template afirma o dia projetado, com a mesma tipografia (`labelMedium`) e no mesmo lugar da coluna direita.
- [x] 2.4 Manter na linha de template a marca de valor irresolvível e a causa; garantir que ela **não** alcança a linha lançada, cuja figura vem registrada do razão.
- [x] 2.5 Preservar o que a linha já afirma sem cor: o ícone de direção com descrição de conteúdo que nomeie a natureza, e a origem inutilizável por glifo e sentença.

## 3. A tela

- [x] 3.1 Remover de `RecurringScreen` a ramificação entre `RecurringCard` e `TransactionCard`: todas as seções emitem a mesma linha.
- [x] 3.2 Remover o import de `TransactionCard` da tela e confirmar por `grep` que `core/ui` não foi tocado e que as outras sete telas seguem consumindo o componente como antes.
- [x] 3.3 Garantir que a linha lançada publica o `testTag` da linha de recorrência (`recurring_card_amount` e afins), de modo que a seção lançada seja alcançável pelos mesmos ids das demais.
- [x] 3.4 Atualizar o KDoc de `RecurringCard`, `RecurringCycleUi` e da lista em `RecurringScreen` para descrever o estado corrente — uma linha, duas fontes —, sem narrar a mudança.

## 4. Verificação

- [x] 4.1 Estender `RecurringViewModelTest`: a origem do ciclo lançado é a da transação quando a confirmação sobrescreveu a conta; é o cartão quando o lançamento foi em cartão; a seção lançada continua caindo para a linha de template quando a transação não pode ser lida.
- [x] 4.2 Rodar `./gradlew :feature:recurring:impl:jvmTest` e ler a saída; corrigir o que quebrar.
- [x] 4.3 Rodar `./gradlew jvmTest` para confirmar que nenhuma outra feature dependia do que mudou.
- [x] 4.4 Acrescentar a `.maestro/flows/recurring/lifecycle.yaml` a asserção da seção lançada pelos ids da linha de recorrência, que hoje não existe.
- [ ] 4.5 Conferir na AVD `pixel_6` API 36 (as sete verificações de `.maestro/README.md` §2, após `./gradlew :app:android:installDebug`) que as quatro seções desenham linhas de mesma altura, e relatar em qual device a execução aconteceu.
