## Why

Na tela de recorrências, a seção **Lançadas** desenha suas linhas com `TransactionCard`
(`core/ui`) enquanto **Pendentes**, **A lançar** e **Ignoradas** desenham com `RecurringCard`.
São dois componentes que nunca foram acertados entre si, e a lista mostra os dois lado a lado:
o card lançado mede **72dp** contra **68dp** dos demais, com chip de **48dp/raio 12** contra
**40dp/raio 8** e espaçamento interno de 16dp contra 12dp.

A divergência não é só de proporção — é de **distribuição**. No card de template a origem
(conta ou cartão) fica na coluna central e o par (figura, dia) na coluna direita; no card
lançado a data fica na coluna central e a figura ocupa a direita sozinha, sem segunda linha. As
duas linhas colocam informações análogas em lugares diferentes, e o olho lê a seção lançada
como um bloco de outra tela.

## What Changes

- A tela de recorrências passa a desenhar **todas** as suas linhas com um único componente, no
  módulo que a lista já adotou: chip 40dp/raio 8, espaçamento 12dp, coluna direita de duas
  linhas, altura de 68dp em toda variante.
- O ciclo **lançado** ganha a mesma geometria das demais linhas — a origem na coluna central,
  o par (figura, data) na direita — **sem trocar de fonte**: ele continua lendo do razão a
  figura, a identidade e a classificação do fato, jamais do template. O que muda é quem
  desenha, não o que se lê.
- A origem exibida na linha lançada é a que a **transação** registrou, não a que o template
  nomeia: confirmar um ciclo pode sobrescrever a conta e o cartão para aquele mês
  (`ConfirmRecurringUseCase`), e uma linha que mostrasse a origem do template estaria afirmando
  algo que pode nunca ter acontecido.
- A tela deixa de consumir `TransactionCard`. **`TransactionCard` não é alterado** — as outras
  sete telas que o usam (transações, cartões, faturas, parcelas, painel, contas, relatório)
  ficam intactas.

## Capabilities

### New Capabilities

Nenhuma. O assunto já tem dono: `recurring-list-row`.

### Modified Capabilities

- `recurring-list-row`: ganha o requisito de que **a lista tem uma linha só** — um módulo
  único, com a mesma altura e a mesma distribuição de colunas em toda variante, seja a linha
  lida do template ou do razão. Dois requisitos existentes são reescritos no ponto em que
  delegavam a linha lançada ao componente de transação: o da **figura sem sinal** (a política
  de magnitude passa a ser afirmada pela própria linha, não herdada de outro componente) e o da
  **marca de valor irresolvível** (segue não valendo para o ciclo lançado, agora dito de uma
  linha que é a mesma). Acresce o que a linha lançada afirma: a origem que a transação
  registrou, e a data do fato no lugar em que o template diz o dia.

## Impact

**Código**
- `feature/recurring/impl` — `RecurringCard.kt` (passa a desenhar as duas leituras),
  `RecurringScreen.kt` (deixa de ramificar entre dois componentes),
  `RecurringUiState.kt` e `RecurringViewModel.kt` (`RecurringCycleUi.Posted` passa a carregar a
  origem que a transação registrou, resolvida junto com as transações da seção — uma leitura
  para toda a seção, não uma por linha).
- `core/ui` — **intocado**. `TransactionCard` e `TransactionUi` seguem como estão; a origem é
  resolvida na feature, que é quem tem os repositórios para isso.

**Testes**
- `RecurringViewModelTest` — a resolução da origem do ciclo lançado, incluindo o caso em que a
  confirmação sobrescreveu a conta.
- Maestro: `.maestro/flows/recurring/lifecycle.yaml` não referencia `transaction_card`, então a
  suíte não depende do componente que sai. A linha lançada passa a publicar o `testTag` da
  linha de recorrência, e o flow ganha a asserção que hoje não tem.

**Strings**
- Nenhuma chave nova prevista: dia, origem indisponível e a marca de irresolvível já existem em
  `values/` e `values-en/`. Se a data do fato exigir formato próprio, a chave entra nos dois
  arquivos no mesmo commit.
