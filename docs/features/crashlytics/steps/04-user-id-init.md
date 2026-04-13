# Etapa 04 — Inicialização do user ID

> Parte do plano: [Crashlytics](../plan.md)

---

## O que fazer

Em `App.kt`, injetar `Crashlytics` e associar o user ID do Firebase Auth à sessão do Crashlytics no startup, junto com a chamada já existente do Analytics. Segue o comportamento especificado: "Dado que o app é iniciado, Quando o Crashlytics é inicializado, Então o user ID do Firebase Auth é associado à sessão".

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/root/App.kt` — injetar `Crashlytics` via `koinInject()` e adicionar `crashlytics.setUserId(authService.getUserId())` no `LaunchedEffect(Unit)` existente, junto com a chamada do Analytics

---

## Critério de aceite

**Validação manual:**
1. Abrir um relatório de crash no Firebase Console → confirmar que o `user_id` está presente e corresponde ao UID do Firebase Auth.
2. App Desktop inicia normalmente (no-op executado sem erros).

**Revisão de código:**
- [x] `user_id` obtido de `authService.getUserId()` — não gerado manualmente
- [x] Chamada feita dentro do `LaunchedEffect(Unit)` existente (sem criar novo efeito)
- [x] Nenhum dado pessoal ou financeiro passado para `setUserId`

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
