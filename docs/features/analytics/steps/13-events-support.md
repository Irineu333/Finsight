# Etapa 13 — Eventos: Support

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics nos ViewModels de suporte, **após confirmação bem-sucedida**.

### Eventos

| Evento | Parâmetros | ViewModel |
|---|---|---|
| `create_support_issue` | `type`: `bug` \| `feature` \| `question` | `SupportViewModel` |
| `send_support_reply` | `type`: `bug` \| `feature` \| `question` | `SupportIssueViewModel` |

### Regras
- `type` é o tipo do issue de suporte — não contém conteúdo do usuário.
- Disparar após sucesso do repositório/use case, não no clique.
- `send_support_reply` inclui o tipo do issue ao qual a resposta pertence.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/support/SupportViewModel.kt` — injetar `Analytics`, disparar `create_support_issue` após criação bem-sucedida
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/support/SupportIssueViewModel.kt` — injetar `Analytics`, disparar `send_support_reply` após envio bem-sucedido
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` nos dois viewModels acima

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar issue de bug → confirmar `create_support_issue` com `type: bug`.
2. Criar issue de feature request → confirmar `create_support_issue` com `type: feature`.
3. Enviar resposta em um issue → confirmar `send_support_reply` com o `type` do issue correto.

**Revisão de código:**
- [x] `type` reflete o tipo do issue (`bug`, `feature`, `question`) — sem conteúdo do usuário
- [x] Eventos disparados após sucesso do repositório/use case
- [x] `send_support_reply` inclui o `type` do issue pai

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
