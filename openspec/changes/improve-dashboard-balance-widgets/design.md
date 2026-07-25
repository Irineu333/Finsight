# Design

## D1 — O perímetro é o tipo do widget, não uma configuração dele

**Alternativa considerada:** um único `BALANCE_STATS` com `scope = all | accounts | cards`, espelhando o `ScopeChip` que o usuário já aprendeu na tela de transações. Reaproveitaria o maquinário de config que já existe (`defaultConfig`, `DashboardComponentOptionsModal`) e manteria o dashboard com um widget de fluxo em vez de três.

**Por que não:** a identidade de um widget é a `key` do **tipo**. O `DashboardViewModel` procura por ela (`indexOfFirst { it.key == fromKey }`), monta o conjunto de presentes por ela (`presentKeys`) e filtra os disponíveis por ela (`filterNot { it.key in presentKeys }`). Existe no máximo uma instância de cada tipo. Um widget único com escopo configurável entregaria **um** perímetro por vez, e contas e cartões deixariam de poder conviver na tela — que é uso corrente e foi confirmado como requisito.

Tornar a instância independente do tipo (id próprio na preferência) resolveria os dois, e destravaria repetir outros widgets também. Mas mexe no modelo de preferências, no ViewModel inteiro e exige migração — desproporcional a acrescentar um perímetro. Fica registrado como caminho futuro, não como parte desta change.

**Consequência aceita:** o dashboard passa a ter cinco widgets de saldo/balanço. Isso deixa de ser confusão na medida em que D3 dá nome honesto a cada um.

## D2 — Nenhum agregado novo no razão

O perímetro neutro precisa de `income` e `expense` de `ASSET` + `LIABILITY`. Seria natural pedir ao razão um `overallMonthFlows(mês)`.

**Por que não:** `ledger-reporting` já decidiu esta questão para o saldo — *"o saldo consolidado de `ASSET` e `LIABILITY` até um mês SHALL ser obtido pela **soma** dos saldos das duas naturezas, sem agregado adicional"*, com o cenário "Consolidado sem agregado novo". A mesma razão vale para os fluxos: o consolidado é a soma, não uma terceira consulta. A regra que o razão precisa garantir — **as duas naturezas são disjuntas quanto a despesa**, porque uma compra no cartão não tem perna `ASSET` — já é propriedade do plano de contas, não algo que uma query precise codificar.

**Consequência:** `DashboardComponentsBuilder` faz `asset.expense + liability.expense`, exatamente como o `balanceOverviewFactory` de transações já faz (`BalanceOverviewFactory.kt:59`). São duas ocorrências de uma soma sancionada pelo spec, não duas implementações de uma regra.

## D3 — "Saldo Total" vira "Saldo em Contas"

O widget lê `balanceUpTo(mês)` sem `accountId`, que o razão define como *todas as contas `ASSET`*. O rótulo "Saldo Total" promete o total do dinheiro do usuário e entrega o de metade do plano de contas.

**Alternativa considerada:** manter o nome e mudar a leitura para incluir cartões (`netWorth`). **Por que não:** mudaria em silêncio o número de um widget que o usuário já tem na tela e já interpreta — quem vê "Saldo Total: 5.000" hoje passaria a ver "1.000" sem ter pedido nada. Corrigir o nome é honesto e não move nenhum valor; expor o líquido é um widget novo, e o usuário optou por fluxo nesta rodada.

## D4 — O ajuste continua fora dos três widgets de fluxo

`AssetMonthFlows` e `LiabilityMonthFlows` trazem `adjustment`, e os widgets do dashboard o descartam. O `SummaryCard` de transações o exibe.

**Decisão do usuário:** manter fora. O widget do dashboard é um **par de fluxos**, não um resumo que fecha — ele nunca afirma `abertura + fluxos = fechamento`, então não deve nada a essa identidade. Incorporar o ajuste dentro de `Receitas`/`Despesas` faria os números baterem com transações sem mudar a forma do card, mas colocaria dentro de uma linha chamada "Despesas" algo que não é despesa.

**Consequência aceita e explícita:** num mês com ajuste de saldo, o dashboard e a tela de transações mostram números diferentes para a mesma pergunta. É divergência conhecida, não defeito silencioso — e é **consistente entre os três widgets**, o que preserva a aritmética que importa aqui: `Despesas(geral) = Despesas(contas) + Gasto no Cartão`.

## D5 — Cabeçalho ligado por padrão só no widget novo

No perímetro neutro a receita é **o mesmo número** do perímetro de contas: o razão não registra receita em perna de `LIABILITY`, então `income(geral) = income(contas)`. Só a despesa difere. Com o mesmo par de cards e os mesmos rótulos (`Receitas` / `Despesas`), os dois widgets ficam indistinguíveis empilhados — e a diferença fica por conta de o usuário reparar que um número mudou.

`SHOW_HEADER` e `showHeader()` já existem em `DashboardComponentConfig`; os widgets de fluxo apenas não os declaram. Entram no `defaultConfig` dos três, com `true` só no novo.

**Por que não diferenciar pelos rótulos dos cards** (ex.: "Saídas Totais"): não resolve a receita, que segue idêntica nos dois, e espalha vocabulário novo por um componente compartilhado (`BalanceCardConfig`) para um problema que é de composição da tela.

**Por que `false` nos dois existentes:** nenhum dashboard já montado muda de aparência. Quem quiser simetria liga no modal de opções.
