## 1. Configurações do widget como AdaptiveModal

- [x] 1.1 Converter `DashboardComponentOptionsModal` de `ModalBottomSheet` para `AdaptiveModal` (`DetailContent()` no lugar de `ColumnScope.BottomSheetContent()`), removendo o `verticalScroll` interno do conteúdo e delegando a rolagem ao container
- [x] 1.2 Substituir `LocalModalManager`/`modalManager.dismiss()` por `LocalDetailPaneController`/`detailController.dismiss()` nos botões Cancelar e Confirmar (Confirmar continua emitindo `DashboardAction.UpdateComponentConfig(...)` antes de dispensar)
- [x] 1.3 Em `DashboardEditingContent`, trocar `modalManager.show(DashboardComponentOptionsModal(...))` por `LocalDetailPaneController.current.show(...)` no toque de um item ativo

## 2. Rolagem consistente na apresentação em sheet

- [x] 2.1 Ajustar `DetailSheetHost` (`core/designsystem/AdaptiveDetail.kt`) para envolver o conteúdo adaptativo num `Column.verticalScroll`, espelhando o `DetailPane`, garantindo rolagem do conteúdo longo em janela estreita

## 3. Verificação

- [ ] 3.1 Janela larga (desktop/paisagem): tocar num widget em edição abre as configurações no painel à direita, com o dashboard editável visível à esquerda; Confirmar aplica e fecha; Cancelar e o X do painel dispensam sem aplicar
- [ ] 3.2 Janela estreita (telefone): tocar num widget abre as configurações como `ModalBottomSheet`, com rolagem funcional em componentes com muitas opções (contas/cartões)
- [ ] 3.3 Redimensionar a janela cruzando o breakpoint com as configurações abertas preserva o estado (sheet ↔ painel) sem reabrir
- [x] 3.4 `./gradlew check` e detalhes `view*` existentes continuam corretos em sheet e painel (sem regressão de rolagem)
