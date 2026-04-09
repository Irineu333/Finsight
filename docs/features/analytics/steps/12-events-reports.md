# Etapa 12 — Eventos: Reports

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics nos ViewModels de relatórios, **após confirmação bem-sucedida** de cada ação.

### Eventos

| Evento | Parâmetros | ViewModel |
|---|---|---|
| `generate_report` | `target`: `account` \| `credit_card`, `sections`: lista das seções ativas separadas por vírgula | `ReportViewerViewModel` ou `ReportConfigViewModel` |
| `share_report` | — | `ReportViewerViewModel` |
| `print_report` | — | `ReportViewerViewModel` |

### Parâmetro `sections`
Valores possíveis: `spending_by_category`, `income_by_category`, `transaction_list`.
Exemplo com duas seções ativas: `"spending_by_category,transaction_list"`.

### Regras
- `generate_report` é disparado quando o relatório é efetivamente gerado/exibido — não ao abrir a tela de configuração.
- `share_report` e `print_report` são disparados após a ação de compartilhar/imprimir, não ao tocar no botão (aguardar a intent/ação ser iniciada).
- Sem parâmetros financeiros (sem valores, períodos específicos).

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/report/viewer/ReportViewerViewModel.kt` — injetar `Analytics`, disparar `generate_report` ao carregar o relatório, `share_report` e `print_report` após as ações
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` no `ReportViewerViewModel`

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Gerar relatório de conta com duas seções ativas → confirmar `generate_report` com `target: account` e `sections` com as seções corretas.
2. Gerar relatório de cartão → confirmar `generate_report` com `target: credit_card`.
3. Compartilhar relatório → confirmar `share_report` sem parâmetros.
4. Imprimir relatório → confirmar `print_report` sem parâmetros.

**Revisão de código:**
- [ ] `generate_report` disparado ao renderizar o relatório, não ao abrir a config
- [ ] `sections` lista apenas as seções ativas, separadas por vírgula
- [ ] `target` reflete `account` ou `credit_card` conforme a perspectiva selecionada
- [ ] Nenhum parâmetro contém dados financeiros ou período

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
