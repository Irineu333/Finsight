---
area: app
severity: low
type: crash
confirmed: no
---

# No iOS o `startKoin` roda por `ComposeView`, não por processo

## Cenário

**DADO** o app iOS já aberto, com o Koin iniciado
**QUANDO** o sistema cria uma segunda cena — multi-window no iPad, ou uma recriação da
`WindowGroup` — e com ela um segundo `ComposeView`
**ENTÃO** `startKoin` é chamado de novo sobre um contexto já iniciado
**DEVERIA** iniciar uma vez por processo, como Android e Desktop já fazem

## Mecânica

`MainViewController()` chama `startKoin { modules(appModules) }` dentro do `configure` do
`ComposeUIViewController`, sem `GlobalContext.getOrNull()`, sem `stopKoin()` e sem flag. O
`configure` roda a cada construção do view controller, e `ComposeView.makeUIViewController`
constrói um por instância.

As outras duas plataformas amarram o início ao processo: o Android em
`AndroidApp.onCreate()`, o Desktop num `remember` dentro do `application`.

## Evidência

- `app/ios/.../MainViewController.kt` — `ComposeUIViewController(configure = { startKoin { … } })`,
  sem guarda
- `iosApp/.../ContentView.swift` — `ComposeView.makeUIViewController` chamando
  `MainViewControllerKt.MainViewController()`
- `app/android/.../AndroidApp.kt` e `app/desktop/.../main.kt` — o início por processo

## O que falta para confirmar

A ausência da guarda é fato lido no disco. **A repro não é determinística** e não foi
executada: confirmá-la pede uma segunda cena real (iPad multi-window) e a leitura do que a
versão de Koin do projeto faz num segundo `startKoin` — se lança ou se ignora.

## Sugestão

Um `if (GlobalContext.getOrNull() == null)` em volta, ou mover o início para o lado Swift,
onde há um ponto por processo. Não vinculante.
