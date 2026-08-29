## 0. Fundações

- [x] 0.1 Declarar `implementation(projects.feature.shell.api)` nos nove `impl` que passarão a
      publicar ações — hoje só `feature/dashboard/impl` o declara, e sem isso nada do grupo 4
      compila
- [x] 0.2 Acrescentar `compose.uiTest` (commonTest) e `compose.desktop.currentOs` (jvmTest) a
      `core/designsystem/build.gradle.kts`, no molde do que `core/ui` já declara — sem isso a
      tarefa 1.5 não compila
- [x] 0.3 Acrescentar a `OverlayPriority` (`core/designsystem` — `ui/component/SharedTransitionProvider`)
      o nível do scrim, entre a chrome de navegação e o botão, e registrar o novo participante
      como `shared-element-transitions` exige

## 1. O componente, sem consumidor

- [x] 1.1 Declarar em `:core:designsystem` a interface de uma ação — `icon`, `labelRes:
      StringResource`, `testTag`, `onClick` — no molde de `BottomNavigationItem`, sem nomear
      feature alguma e sem expor `UiText`
- [x] 1.2 Implementar o componente genérico sobre essa interface. **Revisto por D12:** o botão tem
      um tamanho só — o `+` de 56dp em toda tela —, e o que é derivado do tamanho da lista é o que
      pressioná-lo faz: nenhuma ação não desenha, uma executa a ação, duas ou mais abrem o menu
- [x] 1.3 Implementar o menu: itens rotulados (nunca só ícone), fechamento ao tocar fora, e direção
      de abertura (acima ou ao lado) como parâmetro — quem decide é a casca. **Revisto por D12:** o
      menu lista **todas** as ações, a primeira inclusive, e o giro é do `+` do próprio botão (45°),
      já que não há mais controle de expansão separado
- [x] 1.4 Derivar o `contentDescription` do botão do que pressioná-lo faz: o rótulo da ação onde há
      uma só, o do menu onde ele o abre — em vez de recebê-lo ou deixá-lo nulo
- [x] 1.5 Teste de composição do componente: as três situações, o botão que abre o menu sem executar
      nada, e o toque fora que fecha sem executar

## 2. O canal na casca

- [x] 2.1 Acrescentar a `ChromeController` (`feature:shell:api`) o verbo que publica ações para uma
      identidade de destino, e o que limpa as de uma identidade
- [x] 2.2 Resolver a identidade dentro do próprio `ChromeEffect`, lendo o `NavBackStackEntry` do
      `LocalViewModelStoreOwner` — nenhuma tela declara a sua identidade à mão
- [x] 2.3 Guardar as ações no `ChromeStateHolder` num **registro por destino**, separado do
      `ChromeConfig`, de modo que o `updateTransition` da chrome continue comparando só a
      configuração
- [x] 2.4 Fazer o `onDispose` do `ChromeEffect` limpar apenas o registro da sua própria identidade,
      e não chamar um `reset` global
- [x] 2.5 Teste do canal: publicar por dois destinos e verificar que ler por um não devolve o do
      outro; que limpar um não apaga o outro; e que mudar só as ações não altera o `ChromeConfig`

## 3. A casca passa a desenhar

- [x] 3.1 Tirar o botão do slot `floatingActionButton` do `Scaffold` e desenhá-lo no `Box` da área
      de conteúdo, removendo o `size(56.dp)` e o `offset(y = 40.dp)` que só serviam ao slot
- [x] 3.2 Implementar a posição como alinhamento animado: central e ancorada à bottom bar quando ela
      está visível (usando o `calculateBottomPadding()` do `Scaffold`), no canto quando não
- [x] 3.3 Reintroduzir os insets da janela na posição de canto — a casca declara
      `contentWindowInsets = WindowInsets()`, e sem isso o botão fica sob a barra do sistema numa
      tela empilhada
- [x] 3.4 Desenhar o scrim como irmão do `Scaffold`, cobrindo a janela, para que a bottom bar não
      siga clicável com o menu aberto
- [x] 3.5 Manter o botão no `header` da rail em janela larga e desenhar o **menu fora da rail**,
      ancorado à posição do botão — a rail é um `Surface` e recorta o que transborda
- [x] 3.6 Declarar botão, menu e scrim no overlay do escopo de transição, nos níveis de 0.3
- [x] 3.7 Implementar a ação universal: a casca mantém `TransactionsEntry` e serve a criação de
      transação às telas que não publicam ação alguma
- [x] 3.8 Separar a visibilidade: a bottom bar continua restrita a `primaryTab`; o botão deixa de
      ser apagado fora das abas, e só some quando a tela publica `ContentOnly`
- [x] 3.9 `DashboardScreen` continua publicando `ContentOnly` no modo de edição, e nada mais.
      **A ação foi deixada de fora de propósito:** D5 introduziu a ação universal depois de estas
      tarefas serem escritas, e uma tela que não publica ação nenhuma já recebe exatamente essa —
      mesmo modal, mesmo `testTag`. Publicá-la aqui seria a terceira cópia da mesma construção
- [x] 3.10 `TransactionsScreen` não publica ação alguma, pelo mesmo motivo de 3.9: a ação universal
      da casca já lhe dá o botão com o `testTag` `add_transaction_fab`

## 4. As nove telas que cedem o seu botão

> Em cada uma: remover o `floatingActionButton` do `Scaffold`, publicar a mesma ação com o mesmo
> `testTag`, e **preservar a condição de estado** — seis delas hoje só desenham o botão em
> `is Content` (ou `!is Loading`, em recorrentes), e publicar incondicionalmente faria o botão
> aparecer durante o carregamento.

- [x] 4.1 `AccountsScreen` — `accounts_add` (hoje incondicional)
- [x] 4.2 `CreditCardsScreen` — `credit_cards_add`, condicionado a `is Content`
- [x] 4.3 `CategoriesScreen` — `categories_add`, condicionado a `is Content`, mantendo a primária
      dependente do filtro corrente
- [x] 4.4 `BudgetsScreen` — `budgets_add`, condicionado a `is Content`
- [x] 4.5 `RecurringScreen` — `recurring_add`, condicionado a `!is Loading`
- [x] 4.6 `InstallmentsScreen` — `installments_add`, condicionado a `is Content`
- [x] 4.7 `ExchangeRatesScreen` — `exchange_rates_add`
- [x] 4.8 `CurrenciesScreen` — decidir entre introduzir o id `currencies_add` (que não existe hoje)
      ou publicar sem `testTag`
- [x] 4.9 `SupportScreen` — `support_add`, condicionado a `is Content`
- [x] 4.10 Teste estrutural, no molde de `AgentInstructionsTest`: varrer `feature/*/impl/src` e
      falhar se algum `Scaffold` declarar `floatingActionButton`

## 5. As ações novas

- [x] 5.1 Declarar o tipo selado de origem em `feature/transactions/api` e acrescentá-lo como
      parâmetro opcional de `addTransactionModal()`
- [x] 5.2 Propagar a origem até o `AddTransactionViewModel` via `parametersOf` e o binding Koin,
      resolvendo o id para o modelo de forma assíncrona sem correr contra o `target` padrão
- [x] 5.3 `AccountsScreen` publica no menu a transferência — com a conta em evidência como origem e
      a conta padrão (`isDefault`) como resguardo, e só quando há mais de uma conta — e a criação
      de transação com a conta em evidência
- [x] 5.4 `CreditCardsScreen` publica no menu a criação de transação com o cartão em foco, lido do
      índice do pager e **não** do argumento de rota
- [x] 5.5 `CategoriesScreen` publica no menu o tipo oposto ao do filtro corrente

## 6. Strings e acessibilidade

- [x] 6.1 Adicionar em `values/` e `values-en/` os rótulos de toda ação nova
- [x] 6.2 Verificar que nenhuma chave existe em apenas um dos dois arquivos

## 7. Verificação

- [x] 7.1 `./gradlew jvmTest` verde, com a saída lida
- [ ] 7.2 Rodar o app no desktop e confirmar: um único botão por janela em todas as seções, e o
      botão presente também nas telas sem ação própria (relatórios, configurações, faturas)
      — **NÃO FEITO.** O app sobe e permanece de pé (`:app:desktop:run`, processo vivo por >45s,
      sem exceção no log), mas esta máquina não concede a permissão de gravação de tela ao
      `screencapture`, que devolve só o papel de parede. A confirmação visual não foi feita
- [ ] 7.3 Rodar o app no Android e percorrer as onze telas — a posição em cada uma, o menu nas três
      que o têm, e o botão acima da barra do sistema nas telas empilhadas — **NÃO FEITO.**
      `:app:android:assembleDebug` passa; nenhum dispositivo foi percorrido
- [ ] 7.4 Rodar a suíte Maestro conforme `.maestro/README.md` §2, no dispositivo conforme.
      Dezesseis arquivos tocam os ids dos botões (seis em `subflows/`, o restante em `flows/`) e
      todos devem passar **sem edição**; reportar em qual dispositivo a execução aconteceu
      — **NÃO FEITO.** A conferência estática está feita: os nove ids que os flows dirigem
      continuam existindo, agora declarados pelas telas, e os seis `add_transaction_fab` são
      todos tocados a partir da dashboard, onde a ação universal os reemite. Nenhuma suíte foi
      executada
- [x] 7.4b Ajustar os flows Maestro ao botão de D12: `subflows/tap_action.yaml` abre o menu apenas
      quando a ação pedida ainda não está na tela, e os quatro sítios que tocam `accounts_add`,
      `credit_cards_add` e `categories_add` passam por ele. Os três `assertVisible` restantes ficam
      como estão — são estados vazios, onde o id pertence ao botão do próprio estado vazio
- [x] 7.5 As ações do menu são compostas na raiz da janela — a casca as desenha dentro do próprio
      `Box` do `ChromeHost`, e não num `Popup`, justamente para que o `Modifier.exposeTestTags()`
      do `App` as alcance sem uma chamada nova. Verificado na fonte, não em execução
- [x] 7.6 Atualizar em `issues/the-fab-covers-the-last-rows-figure-when-the-list-reaches-the-bottom.md`
      a evidência que cita o FAB duplo da rail, que deixa de existir; o defeito em si continua aberto
