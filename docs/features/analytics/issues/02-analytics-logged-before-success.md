# Issue 2 — Evento de analytics registrado antes de confirmar sucesso

> Parte do plano: [Analytics](../plan.md)

---

## Tipo

`bug`

---

## Contexto

**Descoberto em:** revisão de código
**Etapa afetada:** 13 — Eventos: Support

---

## Comportamento observado

`SupportViewModel.createIssue` e `SupportIssueViewModel.sendReply` ignoravam o `Either` retornado por `createSupportIssueUseCase` e `addSupportReplyUseCase`, registrando o evento de analytics incondicionalmente — inclusive quando a operação falhava (ex.: título/descrição/mensagem vazios ou erro desconhecido).

---

## Comportamento esperado

Eventos `create_support_issue` e `send_support_reply` devem ser registrados apenas após confirmação de sucesso (`Either.Right`), seguindo a regra da spec:

> *Eventos de ação são disparados somente após confirmação bem-sucedida — não no clique do botão.*

---

## Causa raiz

O resultado de `createSupportIssueUseCase(draft)` e `addSupportReplyUseCase(...)` não era atribuído nem inspecionado. O `analytics.logEvent(...)` era chamado na linha seguinte, sem condicional.

---

## Resolução

Encadeado `onLeft` / `onRight` no resultado de cada use case:

- `onRight`: registra o evento de analytics
- `onLeft`: `// TODO: register exception`