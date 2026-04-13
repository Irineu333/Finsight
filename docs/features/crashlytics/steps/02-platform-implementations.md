# Etapa 02 — Implementações por plataforma

> Parte do plano: [Crashlytics](../plan.md)

---

## O que fazer

Criar as implementações concretas da interface `Crashlytics`: `FirebaseCrashlyticsImpl` para Android e iOS usando o SDK do Firebase, e `NoOpCrashlytics` para JVM que ignora silenciosamente todas as chamadas.

---

## Arquivos afetados

- `composeApp/src/androidMain/kotlin/com/neoutils/finsight/crashlytics/FirebaseCrashlyticsImpl.kt` — criar implementação com `Firebase.crashlytics.setUserId(id)` e `Firebase.crashlytics.recordException(e)`
- `composeApp/src/iosMain/kotlin/com/neoutils/finsight/crashlytics/FirebaseCrashlyticsImpl.kt` — idem para iOS
- `composeApp/src/jvmMain/kotlin/com/neoutils/finsight/crashlytics/NoOpCrashlytics.kt` — criar no-op com ambos os métodos retornando `Unit`

---

## Critério de aceite

**Validação manual:**
1. Compilação Android sem erros.
2. Compilação Desktop sem erros.

**Revisão de código:**
- [x] `FirebaseCrashlyticsImpl` implementa `Crashlytics` usando apenas imports de `dev.gitlive.firebase.crashlytics`
- [x] `NoOpCrashlytics` não tem nenhuma lógica — apenas `= Unit` em cada método
- [x] Nenhum dado pessoal ou financeiro incluído nas chamadas ao SDK

---

## Desvio

**O que era esperado:** `recordException(e: Exception)` e `setUserId(id: String?)` mapeando diretamente ao SDK.

**O que foi feito:**
- `recordException`: sem desvio — `Exception` é subtipo de `Throwable`, passado diretamente.
- `setUserId`: o SDK do gitlive aceita apenas `String` não-nulo. A implementação usa `id ?: ""` para representar ausência de usuário (limpa o userId no Crashlytics).

**Por quê:** A assinatura real da API é `setUserId(userId: String)` sem nullable — confirmado lendo o source `.jar` da dependência.

**Impacto nas etapas seguintes:** nenhum — a interface do domínio não muda.
