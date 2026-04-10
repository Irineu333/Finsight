# Etapa 03 — Identificação de usuário

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Associar o `uid` do Firebase Auth à sessão de analytics via `analytics.setUserId(id)`.

A chamada deve acontecer na inicialização do app, em `App.kt` ou `AppNavHost.kt`, via `LaunchedEffect(Unit)`. O `uid` é obtido de `Firebase.auth.currentUser?.uid` — anônimo por enquanto, mas o Firebase Auth já gerencia persistência entre sessões.

Comportamento esperado:
- Se o usuário tiver sessão ativa (`currentUser != null`) → `setUserId(uid)`.
- Se não houver sessão → `setUserId(null)` (limpa qualquer ID anterior).
- A persistência entre sessões é responsabilidade do Firebase Auth — não há lógica adicional no app.

> Este passo é independente dos screen views e pode ser implementado em paralelo com a etapa 02, mas depende do `Analytics` da etapa 01.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/root/App.kt` — adicionar `LaunchedEffect` com `setUserId` via Firebase Auth

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Fechar e reabrir o app → confirmar que o mesmo `user_id` aparece nos eventos das duas sessões.
2. Confirmar que o `user_id` visível no DebugView corresponde ao `uid` do Firebase Auth (verificar via `Firebase.auth.currentUser?.uid` em debug).

**Revisão de código:**
- [x] `user_id` obtido de `Firebase.auth.currentUser?.uid` — não gerado manualmente
- [x] `setUserId(null)` chamado quando não há sessão ativa
- [x] Nenhuma lógica de persistência de ID implementada no app (delegado ao Firebase Auth)
- [x] A chamada não bloqueia a composição inicial do app

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
