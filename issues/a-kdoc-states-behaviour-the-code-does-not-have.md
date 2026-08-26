---
area: recurring
severity: low
type: data
version: 1.10.0
---

# Dois KDoc do redesenho de recorrentes afirmam o contrário do que o código faz

## Invariante

O KDoc descreve o estado atual do código.

Hoje é falso em dois pontos, ambos escritos pela change que redesenhou a tela de recorrentes,
e ambos afirmando o **oposto** do comportamento — não uma imprecisão.

## Mecânica

`RecurringSettledTotals` declara que um estorno lançado contra uma despesa recorrente "é uma
perna `EXPENSE` negativa e portanto reduz a magnitude da despesa, que é a mesma aritmética de
todo fluxo mensal do app". Um estorno neste app é **transação própria**, com crédito na mesma
dimensão, e nenhuma ocorrência aponta para ela: ele nunca entra no `JOIN` desta consulta e
nunca reduz a despesa lançada. A oração final inverte a verdade — os outros fluxos mensais
varrem `entries` por período e **pegam** o estorno; este varre por ocorrência e não pega.

`CHIP_SIZE` declara-se "o que governa a altura de toda variante da linha", com as duas linhas
de texto de cada lado "ficando abaixo dele". A coluna da direita mede 44dp — `titleMedium` 24
mais `ROW_LINE_GAP` 4 mais `labelMedium` 16 — contra os 40dp do chip: é ela que governa. O
KDoc de `ROW_LINE_GAP`, oito linhas abaixo, enuncia a regra certa.

## Evidência

- `core/database/.../dao/RecurringOccurrenceDao.kt` — KDoc de `RecurringSettledTotals`, a
  frase sobre o estorno
- `core/ledger/src/jvmTest/.../EntryCategoryQueryTest.kt` — "A refund is not a separate
  concept: it is a credit on the same dimension", a definição que essa frase contraria
- `feature/recurring/impl/.../screen/recurring/RecurringScreen.kt` — KDoc de `CHIP_SIZE`, e o
  de `ROW_LINE_GAP` logo abaixo, que diz o certo
- `core/designsystem/.../theme/Type.kt` — `titleMedium` e `labelMedium`, cujos `lineHeight`
  produzem os 44dp

## Consequência

Quem ler o primeiro construirá sobre a premissa de que a despesa fixa lançada do mês desconta
estornos, e ela não desconta. O comportamento é defensável — a leitura responde pelo que os
ciclos postaram, não pelo período —, mas o registro dele está errado, e é o registro que a
próxima pessoa vai ler.

## Sugestão

Corrigir as duas frases. A do estorno merece dizer o que de fato acontece, já que é a pergunta
que ela mesma levanta: um estorno não é somado nem subtraído aqui, porque não tem ocorrência.
Não vinculante.
