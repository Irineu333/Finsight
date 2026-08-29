## 1. O componente, sem consumidor

- [ ] 1.1 Declarar em `:core:designsystem` o modelo de uma ação do botão — rótulo, ícone,
      identidade de teste e o que executa — sem nomear feature alguma
- [ ] 1.2 Implementar o componente com as três formas derivadas do tamanho da lista: nenhuma ação
      não desenha, uma desenha o botão simples sem controle de expansão, duas ou mais desenham
      corpo e controle de expansão
- [ ] 1.3 Implementar o menu: itens rotulados (nunca só ícone), scrim que obscurece o conteúdo,
      fechamento ao tocar fora, e o giro do controle de expansão
- [ ] 1.4 Respeitar `prefers-reduced-motion` do equivalente da plataforma e garantir foco de
      teclado visível em cada área acionável
- [ ] 1.5 Teste de composição do componente: as três formas, o corpo que executa a primária sem
      abrir o menu, e o toque fora que fecha sem executar

## 2. O canal na casca

- [ ] 2.1 Acrescentar a `ChromeController` (`feature:shell:api`) o verbo que publica as ações,
      carregando a identidade do `NavBackStackEntry` que as originou (design D2)
- [ ] 2.2 Guardar as ações no `ChromeStateHolder` num estado separado do `ChromeConfig`, de modo
      que o `updateTransition` da chrome continue comparando só a configuração (design D1)
- [ ] 2.3 Estender `ChromeEffect` para publicar ações junto com a configuração, e limpar a
      publicação no `onDispose`
- [ ] 2.4 Teste: publicar ações de um destino e verificar que a casca as descarta quando o destino
      corrente é outro

## 3. A casca passa a desenhar

- [ ] 3.1 Substituir `AddTransactionFab` pelo novo componente no `ChromeHost`, alimentado pelas
      ações publicadas e filtrado pela identidade do destino corrente
- [ ] 3.2 Implementar a posição: central e ancorada à bottom bar quando ela está visível, no canto
      da área de conteúdo quando não, `header` da rail em janela larga (design D3)
- [ ] 3.3 Fazer o menu abrir ao lado do botão em janela larga, sem ser recortado pelos limites da
      rail
- [ ] 3.4 Declarar o botão, o menu e o scrim no overlay do escopo de transição, acima da barra e
      da rail, preservando o que `shared-element-transitions` exige
- [ ] 3.5 `DashboardScreen` publica a ação de criar transação, obtida de `TransactionsEntry`, e
      publica zero ações no modo de edição
- [ ] 3.6 `TransactionsScreen` publica a ação de criar transação com o `testTag`
      `add_transaction_fab`
- [ ] 3.7 Remover a injeção de `TransactionsEntry` do `ChromeHost`
- [ ] 3.8 Desacoplar a visibilidade: o `else -> ContentOnly` do `effectiveConfig` deixa de reger o
      botão, que passa a depender só de haver ação publicada
- [ ] 3.9 Remover `isFloatingActionButtonVisible` do `ChromeConfig` e ajustar `ContentOnly` para
      significar apenas a ausência do seletor

## 4. As nove telas que cedem o seu botão

- [ ] 4.1 `AccountsScreen` — remover o `floatingActionButton` e publicar a criação de conta com o
      `testTag` `accounts_add`
- [ ] 4.2 `CreditCardsScreen` — idem, `credit_cards_add`
- [ ] 4.3 `CategoriesScreen` — idem, `categories_add`, mantendo a primária dependente do filtro
      corrente (design D8)
- [ ] 4.4 `BudgetsScreen` — idem, `budgets_add`
- [ ] 4.5 `RecurringScreen` — idem, `recurring_add`
- [ ] 4.6 `InstallmentsScreen` — idem, `installments_add`
- [ ] 4.7 `CurrenciesScreen` e `ExchangeRatesScreen` — idem, verificando que o botão aparece nesses
      sub-destinos no celular, onde hoje o chrome inteiro é apagado
- [ ] 4.8 `SupportScreen` — idem, `support_add`
- [ ] 4.9 Verificar que nenhum `Scaffold` de feature declara `floatingActionButton`

## 5. As ações novas

- [ ] 5.1 Terceiro construtor de `TransferBetweenAccountsModal`, sem argumentos, e
      `initialSourceAccount` nullable no ViewModel (design D7)
- [ ] 5.2 Fazer a resolução da origem corrente parar de cair em `accounts.firstOrNull()` quando não
      há pré-seleção, deixando a origem vazia até o usuário escolher
- [ ] 5.3 Reescrever o KDoc do modal para descrever os três modos, sem narrar a mudança
- [ ] 5.4 Teste do ViewModel: abrir sem origem, verificar que nenhuma conta vem escolhida e que o
      envio é recusado enquanto ela faltar
- [ ] 5.5 Declarar o tipo selado de origem em `feature/transactions/api` e acrescentá-lo como
      parâmetro opcional de `addTransactionModal()` (design D6)
- [ ] 5.6 Fazer `AddTransactionModal` abrir com a origem pré-selecionada quando ela é informada
- [ ] 5.7 `AccountsScreen` publica no menu a transferência e a criação de transação
- [ ] 5.8 `CreditCardsScreen` publica no menu a criação de transação com o cartão em foco
- [ ] 5.9 `CategoriesScreen` publica no menu o tipo oposto ao do filtro corrente

## 6. Strings e acessibilidade

- [ ] 6.1 Adicionar em `values/` e `values-en/` os rótulos de toda ação nova e a descrição
      acessível de cada botão primário
- [ ] 6.2 Verificar que nenhuma chave existe em apenas um dos dois arquivos

## 7. Verificação

- [ ] 7.1 `./gradlew jvmTest` verde, com a saída lida
- [ ] 7.2 Rodar o app no desktop e confirmar que existe um único botão de ação por janela em todas
      as seções
- [ ] 7.3 Rodar o app no Android e percorrer as onze telas, confirmando a posição do botão em cada
      uma e o menu nas três que o têm
- [ ] 7.4 Rodar a suíte Maestro conforme `.maestro/README.md` §2, no dispositivo conforme, e
      confirmar que os seis subflows existentes passam **sem edição**; reportar em qual
      dispositivo a execução aconteceu
- [ ] 7.5 Conferir que `issues/the-fab-covers-the-last-rows-figure-when-the-list-reaches-the-bottom.md`
      continua descrevendo o estado real do código, e atualizar a evidência que cita o FAB duplo
      da rail, que deixa de existir
