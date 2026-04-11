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
- [ ] `FirebaseCrashlyticsImpl` implementa `Crashlytics` usando apenas imports de `dev.gitlive.firebase.crashlytics`
- [ ] `NoOpCrashlytics` não tem nenhuma lógica — apenas `= Unit` em cada método
- [ ] Nenhum dado pessoal ou financeiro incluído nas chamadas ao SDK

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.

**Possível:** A API do gitlive pode diferir do esperado (ex: método se chama diferente). Registrar aqui se isso acontecer.
