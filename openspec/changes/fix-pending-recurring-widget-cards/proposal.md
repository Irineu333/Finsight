## Why

O widget de recorrentes previstas (`balance_stats_pending`) esconde o cartão de Receitas ou o de Despesas quando aquela classe soma zero. O resultado é que um mês com apenas receita prevista mostra **um** cartão ocupando a largura inteira, e o usuário lê "não há nada previsto de despesa" como "despesa não existe aqui" — quando a resposta correta é R$ 0,00. Pior: o layout do widget muda de forma conforme os dados, então a mesma linha do dashboard aparece ora com dois cartões, ora com um, ora com nenhum, sem que a identidade do widget tenha mudado.

Nenhum outro widget de fluxo do dashboard faz isso — geral, contas e cartões sempre renderizam o par completo. A regra já existe no spec (`o conjunto de classes reportadas SHALL ser o mesmo entre os três perímetros`), mas está enunciada só para a comparação *entre* widgets; o widget de previstas a viola *dentro de si*, ao longo do tempo.

## What Changes

- O widget de recorrentes previstas passa a renderizar **sempre** os dois cartões — Receita prevista e Despesa prevista —, exibindo R$ 0,00 na classe sem valor.
- Some a condição de visibilidade por cartão em `DashboardPendingBalanceSection`, e com ela a variável `showBothCards`, que só existia para reintroduzir o par no caso em que ambos eram zero.
- Nada muda na decisão de exibir o widget **inteiro**: `hideWhenEmpty` continua sendo a única chave que o oculta, e o seu padrão (`false`) continua o mesmo. Widget presente ⇒ par completo; widget ausente ⇒ nada.
- Os valores somados não mudam: a correção é de renderização, não de leitura.

## Capabilities

### New Capabilities

_(nenhuma)_

### Modified Capabilities

- `dashboard-balance-widgets`: a regra do "mesmo conjunto de classes" passa a valer também **dentro** de um widget ao longo do tempo — um widget de fluxo presente na tela exibe o par completo de classes do seu perímetro, e uma classe que soma zero é exibida como zero, nunca omitida. A única decisão binária permitida continua sendo a do widget inteiro.

## Impact

- `feature/dashboard/impl` — `DashboardComponentContent.kt`, apenas `DashboardPendingBalanceSection`.
- Sem mudança em `DashboardComponentsBuilder`, em `DashboardComponent.PendingBalanceStats` ou na configuração padrão do tipo em `DashboardComponentType`.
- Sem migração: nenhuma preferência salva é lida ou reescrita.
