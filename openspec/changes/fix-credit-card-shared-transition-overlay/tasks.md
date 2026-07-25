## 1. Confirmar o diagnóstico antes de mexer no código

- [x] 1.1 Rodar `./gradlew :app:desktop:run` com a janela em ≥840dp e navegar do cartão da dashboard para a tela de cartões, com **três ou mais cartões cadastrados** e o pager numa página do meio
- [x] 1.2 ~~Verificar a predição de simetria: durante a animação, um cartão deve aparecer **também à direita**, sobre o `DetailPane`~~ — **predição mal formulada, descartada.** Nada aparece sobre o `DetailPane`, e não deveria mesmo: o vazamento à direita existe, mas o painel é o último filho do `Row` (`ChromeHost.kt:168-194`) e é pintado **depois** do overlay, encobrindo-o. Só o rail, que é o primeiro filho, fica exposto. Ver `design.md`
- [x] 1.3 Verificar que o cartão que sobrepõe o rail é a página **vizinha** (nome/valor diferentes do cartão tocado), não o cartão tocado — **confirmado**; é este o critério que valida o diagnóstico
- [x] 1.4 Se 1.3 falhar, **parar**: o diagnóstico do `design.md` está errado e a causa precisa ser reinvestigada antes de prosseguir — não disparou

## 2. Promoção opt-in em `:core:ui`

- [x] 2.1 Em `core/ui/.../component/CreditCardCard.kt`, extrair a construção do `sharedElement` para `@Composable fun Modifier.creditCardSharedElement(cardId: Long): Modifier`, mantendo a chave `credit_card_$cardId` como única fonte da chave
- [x] 2.2 Fazer o `Modifier` devolver `Modifier` inerte quando `LocalSharedTransitionScope` ou `LocalAnimatedVisibilityScope` forem nulos
- [x] 2.3 Remover de `CreditCardCard` a leitura dos dois `CompositionLocal`s e a aplicação implícita de `sharedModifier`; o componente passa a aplicar apenas o `modifier` recebido
- [x] 2.4 Aplicar `clipInOverlayDuringTransition = OverlayClip(shapes.large)` na promoção (fallback F1 do design — vale independentemente do bug)

## 3. Promover apenas o cartão selecionado

- [x] 3.1 Em `feature/dashboard/impl/.../DashboardComponentContent.kt`, aplicar `creditCardSharedElement` no `CreditCardCard` somente quando `page == pagerState.currentPage`
- [x] 3.2 Em `feature/creditcards/impl/.../CreditCardsScreen.kt` (`CreditCardPager`), aplicar somente quando `page == selectedIndex`
- [x] 3.3 Ajustar o call site de `feature/report/impl/.../ReportConfigScreen.kt` para compilar sem promover nada (ele já não participava, por não ter `AnimatedVisibilityScope`) — só a remoção do argumento `cardId`, agora sem uso no componente
- [x] 3.4 ~~`./gradlew :app:shared:compileKotlinDesktop`~~ → `./gradlew :app:desktop:compileKotlin` (a task nomeada não existe neste projeto); os três call sites foram os únicos afetados

## 4. Chrome acima do overlay

- [x] 4.1 Em `app/shared/.../ui/App.kt`, mover `SharedTransitionProvider` para envolver `ChromeHost`, mantendo `Modifier.padding(paddingValues)` aplicado ao `AppNavHost`
- [x] 4.2 Em `feature/shell/impl/.../ChromeHost.kt`, aplicar `Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)` no `NavigationRailBar` — via `Modifier.aboveSharedElements()` privado, aplicado no `AnimatedVisibility` que envolve a barra (o componente de `:core:designsystem` não expõe `modifier`, e o wrapper cobre também a animação de entrada/saída)
- [x] 4.3 Aplicar o mesmo no `BottomNavigationBar` e no `AddTransactionFab`
- [x] 4.4 Confirmar que `feature:shell:impl` já depende de `:core:designsystem` e que nenhuma nova dependência de módulo foi introduzida

## 5. Verificação manual

- [ ] 5.1 Desktop em janela larga: navegar dashboard → cartões e voltar; o rail permanece integralmente visível durante as duas animações
- [ ] 5.2 Desktop em janela ≥840dp: o `DetailPane` permanece integralmente visível durante as duas animações
- [ ] 5.3 Android em janela estreita: a bottom bar e o FAB permanecem visíveis durante as duas animações
- [ ] 5.4 A transição do cartão tocado continua acontecendo e é visualmente contínua (o cartão não "pisca" nem salta)
- [ ] 5.5 Sem regressão de ordenação: `ModalBottomSheet`, painel de detalhe e o `DropdownMenu` do topo da tela de cartões continuam acima do chrome
- [ ] 5.6 Testar com um único cartão cadastrado (sem vizinhos) e com o pager na primeira e na última página

## 6. Fallbacks, apenas se 5.1–5.3 falharem

- [ ] 6.1 Se o vazamento persistir, aplicar F2: `Modifier.clipToBounds()` no `Box(weight = 1f)` de `ChromeHost.kt:192`, e registrar no `design.md` que o cartão passa a ser cortado na borda
- [ ] 6.2 Se ainda persistir, aplicar F3: `Modifier.zIndex(1f)` no rail dentro do `Row`
- [ ] 6.3 Se ainda persistir, avaliar F4 (`OverlayClip` customizado ao viewport do pager) — e antes disso reabrir o diagnóstico, porque F1–F3 falhando significa que a causa é outra
- [ ] 6.4 Registrar em `design.md` qual fallback foi necessário e por quê

## 7. Fechamento

- [ ] 7.1 `./gradlew allTests`
- [ ] 7.2 Abrir issues separadas para os dois achados fora de escopo: `ChromeEffect` ausente na `CreditCardsScreen` e divergência de `EXCLUDED_CARD_IDS` entre as duas listas de cartões
- [ ] 7.3 `openspec validate fix-credit-card-shared-transition-overlay`
