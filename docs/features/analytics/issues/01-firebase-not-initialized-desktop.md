# Issue 1 — Firebase não inicializado no Desktop

> Parte do plano: [Analytics](../plan.md)

---

## Tipo

`bug`

---

## Contexto

**Descoberto em:** execução no Desktop (JVM)
**Etapa afetada:** 03 — Identificação de usuário

---

## Comportamento observado

Crash ao iniciar o app no Desktop com a exceção:

```
java.lang.IllegalStateException: Default FirebaseApp is not initialized in this process null.
Make sure to call FirebaseApp.initializeApp(Context) first.
    at com.google.firebase.FirebaseApp.getInstance(FirebaseApp.java:179)
    at dev.gitlive.firebase.auth.android.getAuth(auth.kt:22)
    at com.neoutils.finsight.ui.screen.root.AppKt$App$1$1.invokeSuspend(App.kt:16)
```

---

## Comportamento esperado

O Desktop não usa Firebase — a chamada `Firebase.auth` não deve ocorrer nessa plataforma.

---

## Causa raiz

A etapa 03 registrou a lógica de autenticação anônima diretamente em `App.kt` (commonMain), chamando `Firebase.auth` sem abstração de plataforma. O Desktop/JVM não inicializa o Firebase, portanto a chamada lança `IllegalStateException`.

---

## Resolução

Criada abstração `AuthService` (interface em `domain/auth/`) com:
- `FirebaseAuthService` (Android e iOS) — faz `signInAnonymously()` e retorna o UID
- `NoOpAuthService` (JVM) — retorna `null` sem tocar no Firebase

O `authModule` segue o mesmo padrão `expect/actual` já usado pelo `analyticsModule`. O `App.kt` passa a injetar `AuthService` via Koin e chama `authService.getUserId()`.

**Impacto nas etapas seguintes:** nenhum — a abstração é transparente para o restante do plano.
