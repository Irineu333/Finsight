> Ordem deliberada: o motor (grupo 2) e o modelo (grupo 3) não alteram comportamento visível e
> podem ser mesclados sozinhos, cada um com os seus testes. O editor novo (grupos 4 a 6) substitui
> o antigo num commit só — a tela não tem meio-termo entre uma lista e duas superfícies — e o flow
> Maestro é reescrito nesse mesmo commit, porque as tags mudam com ele. Docs e verificação fecham.
>
> Cada tarefa que afirma comportamento termina com o teste que o prova; "feito" é o teste verde,
> lido, não o código escrito. O dispositivo do E2E é o do `.maestro/README.md` §2, e a entrega
> diz qual foi.

## 1. Linha de base

- [ ] 1.1 Rodar `./gradlew jvmTest` sobre a `main` limpa e registrar aqui o total de casos e falhas — é contra ele que 8.3 confere.
- [ ] 1.2 Confirmar por leitura, e registrar aqui, que o `BottomSheetScaffold` do Material 3 na versão da CMP 1.10.1 compõe a sheet no mesmo `Layout` que o corpo (sem `Popup`) e que `sheetPeekHeight` pode mudar com a sheet montada (D2). Se qualquer um dos dois falhar, D2 vai para o plano B antes do grupo 5.

## 2. `:core:dragdrop` — o motor

- [ ] 2.1 Criar o módulo sob `finsight.compose.library`, incluir em `settings.gradle.kts`, e declarar `compose.uiTest` em `commonTest` e `compose.desktop.currentOs` em `jvmTest`, como `core/designsystem`.
- [ ] 2.2 `slotIndexFor(pointerAlongAxis, visible: List<ItemBounds>): Int` como função pura, com teste cobrindo antes do primeiro, depois do último, entre dois, sobre a metade exata e lista vazia.
- [ ] 2.3 `DragAndDropState` / `DragSession` e `DragAndDropHost`: registro de alvos por bounds em janela, resolução do alvo sob o ponteiro por prioridade e ordem, e o overlay do ghost na posição do ponteiro menos o offset de pega.
- [ ] 2.4 `Modifier.dragSource(payload, trigger, ghost, onDragEnd)` com `Immediate` e `LongPress`; com mouse, `LongPress` aceita press+move.
- [ ] 2.5 `Modifier.dropTarget(priority, onEnter, onMove, onExit, onDrop)`; soltar sem alvo cancela e avisa a fonte.
- [ ] 2.6 `LazyListState.autoscrollWhile(...)`: faixa de borda em dp, velocidade por proximidade, para no fim da lista e no fim do arrasto.
- [ ] 2.7 Teste de UI (`compose.uiTest`, JVM): dois alvos e uma fonte sob o host — entrada/saída contadas uma vez cada, `onMove` com offset relativo, soltura no alvo de maior prioridade quando sobrepostos, e cancelamento ao soltar fora.
- [ ] 2.8 Montar `DragAndDropHost` em `App.kt` dentro de `DetailPaneHost`, envolvendo `SharedTransitionProvider`; `:app:shared` passa a depender de `:core:dragdrop`. Nada visível muda; `./gradlew :app:desktop:run` abre como antes.

## 3. O modelo do editor (sem UI nova)

- [ ] 3.1 `DashboardEditLayout(activeItems)` com `add(item, at)` (recusa tipo já ativo, clampa o índice), `remove(key)` e `move(from, to)`; as sentinelas e `move(fromKey, toKey)` saem. Reescrever `DashboardEditLayoutTest` para as três operações e os seus limites.
- [ ] 3.2 `DashboardUiState.Editing` perde `availableItems` e ganha `catalog`; `buildEditingState` constrói o catálogo (tipos não deprecados, com preview) uma vez. Teste: a vitrine derivada é exatamente catálogo menos ativos, na ordem do catálogo.
- [ ] 3.3 `DashboardAction`: entram `AddComponent(key, at)`, `RemoveComponent(key)`, `MoveComponent(fromIndex, toIndex)`; saem `MoveComponent(fromKey, toKey)`, `AddAllComponents`, `RemoveAllComponents`. ViewModel despacha para 3.1.
- [ ] 3.4 `DashboardDeprecatedWidgetTest` passa a ler a vitrine derivada; os seis casos continuam verdes com o mesmo significado.
- [ ] 3.5 Teste: um layout salvo antes desta mudança (fixture com `key`, `position`, `config`) reabre no editor com os mesmos itens, na mesma ordem e configuração, e confirmar sem mexer regrava o mesmo conteúdo.

## 4. As configurações viram modal transitória

- [ ] 4.1 `DashboardComponentOptionsModal` passa de `AdaptiveModal` a `ModalBottomSheet`, aberta por `LocalModalManager.current.show(...)`; o rodapé de ações passa a ser composto pela própria modal. Tags `dashboard_component_options*` mantidas.
- [ ] 4.2 Verificar no desktop em janela extra-larga que a modal abre por cima do editor e o painel não é tocado.

## 5. A vitrine

- [ ] 5.1 `DashboardShowcaseContent`: lista vertical dos cards da vitrine — preview real sob o overlay inerte, `≡` com `Immediate`, corpo com `LongPress`, apagado + legenda quando `mode !in modes` (D7), estado vazio quando não há o que oferecer. Tags de D9.
- [ ] 5.2 `DashboardShowcasePane : AdaptivePane` hospedando 5.1; aberta/dispensada por efeito da tela em `Editing && isExtraWideWindow()`. O painel inteiro é `dropTarget` de remoção.
- [ ] 5.3 Sheet inline no `DashboardEditingContent` com `BottomSheetScaffold`: `sheetPeekHeight` dinâmico (metade em repouso, peek durante `session != null`), `partialExpand()`/`expand()` em torno do arrasto se estava expandida, a zona do peek como `dropTarget` de remoção com o rótulo. Medir a animação da mudança de âncora; se saltar, plano B de D2.
- [ ] 5.4 Strings pt **e** en no mesmo commit: título da vitrine, "solte aqui para remover", vazio da vitrine, legenda de largura de janela. Saem `dashboard_edit_available_section`, `dashboard_edit_active_placeholder`, `dashboard_edit_available_placeholder`, `dashboard_edit_add_all`, `dashboard_edit_remove_all` dos dois arquivos.

## 6. A lista com arrasto

- [ ] 6.1 `DashboardEditingContent` reescrito sobre o motor: itens ativos apenas, `≡` `Immediate`, corpo `LongPress`, `dropTarget` da lista com `hoverIndex` por `slotIndexFor`, `EditListEntry.Slot` renderizado no índice, `animateItem()` nos vizinhos, autoscroll de 2.6, haptics nos três momentos.
- [ ] 6.2 Soltura despacha `AddComponent(type, at)`, `MoveComponent(from, to)` ou, na vitrine, `RemoveComponent(key)`; soltar fora não despacha nada.
- [ ] 6.3 Item apagado + legenda na lista quando `mode !in modes`, ainda arrastável.
- [ ] 6.4 Remover `sh.calvin.reorderable` de `feature/dashboard/impl/build.gradle.kts`; `EditListEntry` perde `SectionHeader` e os placeholders; `rememberDashboardEditListEntries` some ou encolhe ao que sobrou.
- [ ] 6.5 Passar pelos três gestos no Android (celular) e no desktop (janela extra-larga e estreita), incluindo cruzar o breakpoint com o editor aberto e um arrasto que atravessa da vitrine para a lista; registrar aqui o que foi exercitado.

## 7. Documentação

- [ ] 7.1 `CLAUDE.md`: `:core:dragdrop` na lista de módulos `core`; `feature/README.md` se citar os módulos `core` por nome.
- [ ] 7.2 `.maestro/README.md`: a linha de `dashboard/customization` no mapa da suíte e a nota de duração passam a descrever a vitrine e a ausência de comandos em massa.

## 8. Maestro e verificação

- [ ] 8.1 Reescrever `.maestro/flows/dashboard/customization.yaml` conforme D9: reordenar por arrasto; remover pela vitrine; adicionar pela vitrine na posição do slot; cancelar restaura; o estado vazio por onze remoções e confirmação; as opções pela modal. Cada afirmação lida no dashboard, não no editor.
- [ ] 8.2 Preparar o dispositivo do `.maestro/README.md` §2 (API 36 `pixel_6`, inglês, teclado na tela), `./gradlew :app:android:installDebug`, rodar `maestro test .maestro/flows/dashboard/customization.yaml` e depois a suíte inteira; registrar aqui o dispositivo, o resultado e a duração do flow.
- [ ] 8.3 `./gradlew jvmTest` completo: registrar o total contra 1.1 — o que saiu (`DashboardEditLayoutTest` antigo) e o que entrou (motor, modelo, vitrine) contam.
- [ ] 8.4 `./gradlew :app:android:assembleDebug` e `./gradlew :app:desktop:run` verdes; iOS compila (`:app:ios` framework) — o motor é `commonMain` e não pode quebrar o target.
