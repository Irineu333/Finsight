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
- [x] 4.5 Conferir na AVD `pixel_6` API 36 (as sete verificações de `.maestro/README.md` §2, após `./gradlew :app:android:installDebug`) que as quatro seções desenham linhas de mesma altura, e relatar em qual device a execução aconteceu.

## Resultado da verificação em aparelho

**Aparelho:** AVD `finsight_e2e` (`emulator-5554`) — API 36, 1080x2400, densidade 420,
`-en-rUS-` e `-nokeys-`, IME Gboard presente, `show_ime_with_hard_keyboard=0`. As sete
linhas da §2.2 conferidas à mão antes do run; um segundo emulador ligado (`Galaxy_A54`,
API 37, `qwerty`) foi descartado e o alvo fixado por serial em `adb` e em `maestro`.

**Altura das quatro seções**, medida na hierarquia de acessibilidade com as quatro no ar
(pendente, a lançar, lançada, ignorada, uma linha cada): **169px em todas**, com passo de
296px entre seções. A linha lançada afirma `01/08` no slot em que as de template afirmam
`Day 1`/`Day 30`, e nomeia `Wallet` na mesma posição.

**Suíte Maestro** (`maestro test --device emulator-5554 .maestro`, pelo workspace):
15 fluxos, **14 verdes**. `recurring_lifecycle` e `recurring_from_transaction` verdes,
este último após a correção do commit `e4c002c62` — ele lia a figura do ciclo lançado em
`transaction_card_amount` estando na tela de recorrências, nó que aquela seção deixou de
publicar. `creditcards_lifecycle` vermelho e **anterior a esta mudança**: reproduz no APK
do commit pai (`a0af083b1`), e a causa é o calendário — fechamento no dia 10 com salto de
45 dias, que rodando em 29/08 cai em 13/10 e manda a despesa para a fatura seguinte.

**Divergência encontrada e corrigida:** o KDoc de `CHIP_SIZE` afirmava que a coluna
direita governa a altura (44dp) e que o chip fica sob ela. O aparelho diz o contrário —
chip 40,0dp, coluna direita 37dp, identidade 35dp, linha 64dp — porque uma linha de
`Text` mede pelas métricas da fonte e não pelo `lineHeight` do estilo. O comentário era
anterior a esta mudança e foi corrigido junto.
