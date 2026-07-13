## Why

No painel de detalhe (janelas largas / desktop) as ações do rodapé (Cancelar/Confirmar, Excluir/Editar) hoje vivem dentro da área rolável junto do corpo. Isso gera dois defeitos de desktop: com conteúdo curto os botões "flutuam" no meio do painel de altura cheia, com vazio embaixo; com conteúdo longo os botões rolam para fora de vista. No bottom sheet (janelas estreitas / mobile) rolar as ações junto do corpo é o comportamento desejado e deve ser mantido.

## What Changes

- Introduzir um slot de **ações** separado nas superfícies adaptativas (`AdaptiveModal`): o corpo (`DetailContent`) continua rolável; as ações passam para um novo slot `DetailActions` (opcional, vazio por padrão).
- No **painel** (janela larga), a casca SHALL fixar as ações no rodapé do painel, fora da área rolável, com apenas o corpo rolando. Uma **elevação/sombra sutil** SHALL separar o rodapé fixo do corpo quando houver conteúdo rolável por baixo.
- No **bottom sheet** (janela estreita), corpo e ações SHALL continuar rolando juntos, exatamente como hoje.
- Migrar as 6 superfícies adaptativas para mover seu bloco de botões (e o divisor acima dele) de `DetailContent` para `DetailActions`. O modal de configurações do widget do dashboard eleva seu estado local de `config` para um ViewModel próprio, para que corpo e ações compartilhem o mesmo estado (os outros 5 já usam `koinViewModel`, que devolve a mesma instância nos dois slots por o `AdaptiveModal` ser o `ViewModelStoreOwner`).

## Capabilities

### New Capabilities
<!-- nenhuma -->

### Modified Capabilities
- `adaptive-detail-pane`: a rolagem passa a distinguir **corpo** de **ações** — no painel as ações ficam fixas no rodapé (só o corpo rola) com separação visual por elevação; no bottom sheet corpo e ações rolam juntos como hoje.

## Impact

- **`core:designsystem`** — `AdaptiveDetail.kt`: novo slot `DetailActions()` em `AdaptiveModal`; `DetailPane` reestrutura o layout (corpo em `weight(1f).verticalScroll`, rodapé fixo com elevação condicional ao scroll) e deixa de rolar as ações; `DetailSheetHost` mantém corpo+ações no mesmo scroll.
- **6 features (impl)** — `DashboardComponentOptionsModal` (dashboard), `ViewOperationModal`/`ViewAdjustmentModal` (transactions), `ViewBudgetModal` (budgets), `ViewCategoryModal` (categories), `ViewRecurringModal` (recurring): mover botões para `DetailActions`.
- **dashboard (impl)** — novo `DashboardComponentOptionsViewModel` (ou state holder) para hospedar o estado `config` compartilhado entre corpo e ações.
- Sem mudança de API pública de navegação nem de rotas; comportamento no mobile inalterado.
