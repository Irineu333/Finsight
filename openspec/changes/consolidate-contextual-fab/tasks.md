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
- [x] 7.3 Rodado no emulador `sdk_gphone16k_arm64` (1080×2340, 480dpi, 360×780dp), com as três
      posições medidas em pixels sobre a captura, não estimadas:
      - aba primária: botão de 56dp, centrado, **afundado 23,7dp na barra** de 104dp — a mesma
        geometria de antes, restabelecida depois de a remoção do `offset(y = 40.dp)` o ter
        deixado 16dp acima dela
      - tela empilhada: 56dp, 16dp da direita e **40dp do fundo** = 24dp de inset gestual + 16dp
        de margem, que é onde o `Scaffold` das nove telas o punha
      - menu (Contas): `+` vira `×`, as duas ações sobem rotuladas — a primeira inclusive —, e o
        scrim escurece o conteúdo para 0,68× do valor original em cada canal
      Não percorridas as onze: três telas cobrem as três posições, e as outras oito repetem a de
      tela empilhada
- [ ] 7.4 Rodar a suíte Maestro conforme `.maestro/README.md` §2, no dispositivo conforme.
      Dezesseis arquivos tocam os ids dos botões (seis em `subflows/`, o restante em `flows/`) e
      todos devem passar **sem edição**; reportar em qual dispositivo a execução aconteceu
      — **NÃO FEITO.** A conferência estática está feita: os nove ids que os flows dirigem
      continuam existindo, agora declarados pelas telas, e os seis `add_transaction_fab` são
      todos tocados a partir da dashboard, onde a ação universal os reemite. Nenhuma suíte foi
      executada
- [x] 7.5 As ações do menu são compostas na raiz da janela — a casca as desenha dentro do próprio
      `Box` do `ChromeHost`, e não num `Popup`, justamente para que o `Modifier.exposeTestTags()`
      do `App` as alcance sem uma chamada nova. Verificado na fonte, não em execução
- [x] 7.6 Atualizar em `issues/the-fab-covers-the-last-rows-figure-when-the-list-reaches-the-bottom.md`
      a evidência que cita o FAB duplo da rail, que deixa de existir; o defeito em si continua aberto

## 8. Ajustes depois de ver o app rodando

> Três correções que só apareceram com o app na mão, e que a verificação do grupo 7 não teria
> pego: duas são de forma — nada quebra, nada falha, o resultado é só pior — e a terceira é um
> componente que aceitava o toque sem dizer nada. Estão aqui, e não emendadas no grupo em que
> caberiam, porque a ordem em que se descobre uma coisa é informação.

- [x] 8.1 **O botão tem um tamanho só** (D12). O botão dividido — corpo de 56dp mais controle de
      expansão de 48dp, 104dp ao todo — ficou grande demais, e fazia a silhueta variar com a
      contagem de ações. Ele volta a ser o `+` de 56dp em toda tela; com duas ou mais ações,
      pressioná-lo abre o menu, e o `+` gira 45° enquanto ele está aberto
- [x] 8.2 **O menu passa a listar todas as ações**, a primeira inclusive — o botão deixou de
      executá-la, e uma ação fora do menu não teria como ser alcançada. O botão ganha identidade
      própria (`floating_action_expand`) onde abre o menu, e mantém a da ação onde há uma só
- [x] 8.3 Reescrever no spec o requisito da forma do botão, que exigia a ação primária a um toque:
      ele existia para justificar o corpo executável, que é o que 8.1 remove. O requisito que
      entra no lugar é o que estava sendo pedido — mesmo tamanho em toda tela —, e o custo
      (dois toques nas três telas com menu) fica dito no proposal e em D12
- [x] 8.4 `subflows/tap_action.yaml`: os ids das três telas com menu passaram a viver dentro dele,
      então os flows ganham o toque que o abre. O subflow o dá **apenas quando a ação ainda não
      está na tela** — a mesma tela publica uma ação num estado e três noutro, e os botões de
      estado vazio carregam esses mesmos ids de propósito
- [x] 8.5 **O ripple das ações do menu.** `Modifier.clickable` entregue ao `Surface` de fora fica
      antes do `.surface(...)` na mesma cadeia, então o ripple era pintado sob um fundo opaco e
      fora do recorte: o item aceitava o toque sem responder nada. Passou a usar a sobrecarga
      clicável do próprio `Surface`, que tem a ordem inversa — e que traz
      `minimumInteractiveComponentSize()` junto
- [x] 8.6 **O ancoramento na bottom bar.** Tirar o botão do slot do `Scaffold` levou junto o
      afundamento de 24dp na barra, que o `offset(y = 40.dp)` produzia por correção da aritmética
      do slot. Ele é dito diretamente agora (`alturaDaBarra - 24dp`, e `safeDrawing.bottom + 16dp`
      sem barra), com piso em zero no alvo e no uso — a âncora é uma mola, e mola que cai a zero
      passa por baixo
- [x] 8.7 **A volta não refazia o caminho da ida.** A ida ficou perfeita e a volta descia até a
      borda antes de subir, porque a altura da barra era lida enquanto ela animava: a entrada
      relata a subida inteira (1dp, 5dp, 36dp…) e a saída relata um zero final já com a transição
      assentada. Passa a ser lida só com a chrome parada e com altura não nula
- [x] 8.8 Conferir no aparelho, medindo sobre os pixels da captura em vez de estimar: ver 7.3, mais
      a trajetória do botão nas duas transições, amostrada com `animator_duration_scale` em 10× e o
      botão isolado por componente conexo. Ida (180,80) → (206,72) → (247,60,0) → (278,51) →
      (316,40); volta (316,40) → (289,48) → (247,**60,3**) → (218,69) → (180,80) — mesmos pontos,
      sentido inverso
