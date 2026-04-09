# Referência: `firebase-analytics` (GitLive KMP)

Biblioteca para rastreamento de eventos e navegação via Firebase Analytics em Kotlin Multiplatform.

**Versão:** `2.1.0` (alinhada com `gitlive-firebase` já no projeto)
**Repositório:** `dev.gitlive:firebase-analytics`
**Plataformas suportadas:** Android, iOS — **sem suporte a Desktop/JVM**

---

## Dependências no projeto

```kotlin
// composeApp/build.gradle.kts
// Adicionar junto com as dependências Firebase existentes (androidMain ou commonMain com sourceSet guard)
implementation("dev.gitlive:firebase-analytics:2.1.0")
```

Como Desktop não suporta, a dependência deve ser declarada **apenas para Android e iOS** via sourceSets, ou a abstração deve prover um no-op para JVM.

---

## API principal

### Registrar evento

```kotlin
Firebase.analytics.logEvent(name = "event_name") {
    param("param_key", "param_value")
    param("numeric_key", 42.0)
}

// Sem parâmetros
Firebase.analytics.logEvent("event_name")
```

### Evento de screen view (padrão Firebase)

```kotlin
Firebase.analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
    param(FirebaseAnalytics.Param.SCREEN_NAME, "screen_name")
}
```

### Identificação de usuário

```kotlin
Firebase.analytics.setUserId("user_id")   // define
Firebase.analytics.setUserId(null)         // limpa (logout)
```

### Propriedades de usuário

```kotlin
Firebase.analytics.setUserProperty("property_name", "value")
```

---

## Restrições do Firebase Analytics

- **Nome de evento:** máx. 40 caracteres, `snake_case`, sem espaços
- **Nome de parâmetro:** máx. 40 caracteres, `snake_case`
- **Valor de parâmetro (string):** máx. 100 caracteres
- **Eventos distintos por app:** máx. 500 nomes únicos — usar nomes genéricos com parâmetros de contexto
- **Parâmetros por evento:** máx. 25

---

## Inicialização

Não requer inicialização explícita — o Firebase é inicializado via `google-services.json` (Android) e `GoogleService-Info.plist` (iOS), que já devem estar configurados para Firestore/Auth.

---

## Comportamento no Desktop

A biblioteca não existe para JVM. A abstração de analytics no projeto deve ter uma implementação no-op para Desktop que compila e não faz nada:

```kotlin
// jvmMain
class NoOpAnalytics : Analytics {
    override fun logEvent(event: AnalyticsEvent) = Unit
    override fun setUserId(id: String?) = Unit
}
```
