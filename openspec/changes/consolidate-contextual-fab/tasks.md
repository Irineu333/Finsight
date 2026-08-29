## 0. Fundações

- [ ] 0.1 Declarar `implementation(projects.feature.shell.api)` nos nove `impl` que passarão a
      publicar ações — hoje só `feature/dashboard/impl` o declara, e sem isso nada do grupo 4
      compila
- [ ] 0.2 Acrescentar `compose.uiTest` (commonTest) e `compose.desktop.currentOs` (jvmTest) a
      `core/designsystem/build.gradle.kts`, no molde do que `core/ui` já declara — sem isso a
      tarefa 1.5 não compila
- [ ] 0.3 Acrescentar a `OverlayPriority` (`core/designsystem` — `ui/component/SharedTransitionProvider`)
      o nível do scrim, entre a chrome de navegação e o botão, e registrar o novo participante
      como `shared-element-transitions` exige

## 1. O componente, sem consumidor

- [ ] 1.1 Declarar em `:core:designsystem` a interface de uma ação — `icon`, `labelRes:
      StringResource`, `testTag`, `onClick` — no molde de `BottomNavigationItem`, sem nomear
      feature alguma e sem expor `UiText`
- [ ] 1.2 Implementar o componente genérico sobre essa interface, com as três formas derivadas do
      tamanho da lista: nenhuma ação não desenha, uma desenha o botão simples sem controle de
      expansão, duas ou mais desenham corpo e controle de expansão
- [ ] 1.3 Implementar o menu: itens rotulados (nunca só ícone), fechamento ao tocar fora, giro do
      controle de expansão, e direção de abertura (acima ou ao lado) como parâmetro — quem decide
      é a casca
- [ ] 1.4 Derivar o `contentDescription` do botão do rótulo da sua ação primária, em vez de
      recebê-lo ou deixá-lo nulo
- [ ] 1.5 Teste de composição do componente: as três formas, o corpo que executa a primária sem
      abrir o menu, e o toque fora que fecha sem executar

## 2. O canal na casca

- [ ] 2.1 Acrescentar a `ChromeController` (`feature:shell:api`) o verbo que publica ações para uma
      identidade de destino, e o que limpa as de uma identidade
- [ ] 2.2 Resolver a identidade dentro do próprio `ChromeEffect`, lendo o `NavBackStackEntry` do
      `LocalViewModelStoreOwner` — nenhuma tela declara a sua identidade à mão
- [ ] 2.3 Guardar as ações no `ChromeStateHolder` num **registro por destino**, separado do
      `ChromeConfig`, de modo que o `updateTransition` da chrome continue comparando só a
      configuração
- [ ] 2.4 Fazer o `onDispose` do `ChromeEffect` limpar apenas o registro da sua própria identidade,
      e não chamar um `reset` global
- [ ] 2.5 Teste do canal: publicar por dois destinos e verificar que ler por um não devolve o do
      outro; que limpar um não apaga o outro; e que mudar só as ações não altera o `ChromeConfig`

## 3. A casca passa a desenhar

- [ ] 3.1 Tirar o botão do slot `floatingActionButton` do `Scaffold` e desenhá-lo no `Box` da área
      de conteúdo, removendo o `size(56.dp)` e o `offset(y = 40.dp)` que só serviam ao slot
- [ ] 3.2 Implementar a posição como alinhamento animado: central e ancorada à bottom bar quando ela
      está visível (usando o `calculateBottomPadding()` do `Scaffold`), no canto quando não
- [ ] 3.3 Reintroduzir os insets da janela na posição de canto — a casca declara
      `contentWindowInsets = WindowInsets()`, e sem isso o botão fica sob a barra do sistema numa
      tela empilhada
- [ ] 3.4 Desenhar o scrim como irmão do `Scaffold`, cobrindo a janela, para que a bottom bar não
      siga clicável com o menu aberto
- [ ] 3.5 Manter o botão no `header` da rail em janela larga e desenhar o **menu fora da rail**,
      ancorado à posição do botão — a rail é um `Surface` e recorta o que transborda
- [ ] 3.6 Declarar botão, menu e scrim no overlay do escopo de transição, nos níveis de 0.3
- [ ] 3.7 Implementar a ação universal: a casca mantém `TransactionsEntry` e serve a criação de
      transação às telas que não publicam ação alguma
- [ ] 3.8 Separar a visibilidade: a bottom bar continua restrita a `primaryTab`; o botão deixa de
      ser apagado fora das abas, e só some quando a tela publica `ContentOnly`
- [ ] 3.9 `DashboardScreen` publica a criação de transação, e continua publicando `ContentOnly` no
      modo de edição
- [ ] 3.10 `TransactionsScreen` publica a criação de transação com o `testTag` `add_transaction_fab`

## 4. As nove telas que cedem o seu botão

> Em cada uma: remover o `floatingActionButton` do `Scaffold`, publicar a mesma ação com o mesmo
> `testTag`, e **preservar a condição de estado** — seis delas hoje só desenham o botão em
> `is Content` (ou `!is Loading`, em recorrentes), e publicar incondicionalmente faria o botão
> aparecer durante o carregamento.

- [ ] 4.1 `AccountsScreen` — `accounts_add` (hoje incondicional)
- [ ] 4.2 `CreditCardsScreen` — `credit_cards_add`, condicionado a `is Content`
- [ ] 4.3 `CategoriesScreen` — `categories_add`, condicionado a `is Content`, mantendo a primária
      dependente do filtro corrente
- [ ] 4.4 `BudgetsScreen` — `budgets_add`, condicionado a `is Content`
- [ ] 4.5 `RecurringScreen` — `recurring_add`, condicionado a `!is Loading`
- [ ] 4.6 `InstallmentsScreen` — `installments_add`, condicionado a `is Content`
- [ ] 4.7 `ExchangeRatesScreen` — `exchange_rates_add`
- [ ] 4.8 `CurrenciesScreen` — decidir entre introduzir o id `currencies_add` (que não existe hoje)
      ou publicar sem `testTag`
- [ ] 4.9 `SupportScreen` — `support_add`, condicionado a `is Content`
- [ ] 4.10 Teste estrutural, no molde de `AgentInstructionsTest`: varrer `feature/*/impl/src` e
      falhar se algum `Scaffold` declarar `floatingActionButton`

## 5. As ações novas

- [ ] 5.1 Declarar o tipo selado de origem em `feature/transactions/api` e acrescentá-lo como
      parâmetro opcional de `addTransactionModal()`
- [ ] 5.2 Propagar a origem até o `AddTransactionViewModel` via `parametersOf` e o binding Koin,
      resolvendo o id para o modelo de forma assíncrona sem correr contra o `target` padrão
- [ ] 5.3 `AccountsScreen` publica no menu a transferência — com a conta em evidência como origem e
      a conta padrão (`isDefault`) como resguardo, e só quando há mais de uma conta — e a criação
      de transação com a conta em evidência
- [ ] 5.4 `CreditCardsScreen` publica no menu a criação de transação com o cartão em foco, lido do
      índice do pager e **não** do argumento de rota
- [ ] 5.5 `CategoriesScreen` publica no menu o tipo oposto ao do filtro corrente

## 6. Strings e acessibilidade

- [ ] 6.1 Adicionar em `values/` e `values-en/` os rótulos de toda ação nova
- [ ] 6.2 Verificar que nenhuma chave existe em apenas um dos dois arquivos

## 7. Verificação

- [ ] 7.1 `./gradlew jvmTest` verde, com a saída lida
- [ ] 7.2 Rodar o app no desktop e confirmar: um único botão por janela em todas as seções, e o
      botão presente também nas telas sem ação própria (relatórios, configurações, faturas)
- [ ] 7.3 Rodar o app no Android e percorrer as onze telas — a posição em cada uma, o menu nas três
      que o têm, e o botão acima da barra do sistema nas telas empilhadas
- [ ] 7.4 Rodar a suíte Maestro conforme `.maestro/README.md` §2, no dispositivo conforme.
      Dezesseis arquivos tocam os ids dos botões (seis em `subflows/`, o restante em `flows/`) e
      todos devem passar **sem edição**; reportar em qual dispositivo a execução aconteceu
- [ ] 7.5 Conferir que as ações do menu são alcançáveis pelo Maestro — se a raiz que as compõe não
      for a da janela, ela precisa publicar os test tags
- [ ] 7.6 Atualizar em `issues/the-fab-covers-the-last-rows-figure-when-the-list-reaches-the-bottom.md`
      a evidência que cita o FAB duplo da rail, que deixa de existir; o defeito em si continua aberto
