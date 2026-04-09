# Etapa 11 — Eventos: Dashboard

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics no `DashboardViewModel`, **após a ação correspondente**.

### Eventos

| Evento | Parâmetros | Quando disparar |
|---|---|---|
| `enter_dashboard_edit_mode` | — | Quando o modo de edição é ativado |
| `save_dashboard_layout` | `components`: lista ordenada dos IDs dos componentes ativos, separados por vírgula | Após salvar o layout com sucesso |

### Regras
- `enter_dashboard_edit_mode` é disparado quando o usuário entra no modo de edição — não quando sai ou cancela.
- `components` em `save_dashboard_layout` reflete o estado **final salvo**: somente componentes ativos, na ordem em que aparecem no dashboard (ex: `"balance,credit_cards,accounts"`).
- Cancelar a edição sem salvar não gera `save_dashboard_layout`.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/dashboard/DashboardViewModel.kt` — injetar `Analytics`, disparar `enter_dashboard_edit_mode` ao ativar edição e `save_dashboard_layout` após salvar
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` no `DashboardViewModel`

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Entrar no modo de edição → confirmar `enter_dashboard_edit_mode` sem parâmetros.
2. Salvar layout com dois componentes ativos em ordem → confirmar `save_dashboard_layout` com `components` listando apenas os ativos na ordem correta.
3. Cancelar edição sem salvar → confirmar que `save_dashboard_layout` não foi disparado.

**Revisão de código:**
- [ ] `enter_dashboard_edit_mode` disparado na ativação do modo de edição, não no encerramento
- [ ] `components` lista somente os ativos, na ordem do dashboard, separados por vírgula
- [ ] Cancelamento sem salvar não gera `save_dashboard_layout`

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
