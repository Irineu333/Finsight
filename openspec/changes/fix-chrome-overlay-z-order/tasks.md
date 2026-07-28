## 1. A escala de prioridades

- [x] 1.1 Em `core/designsystem/.../component/SharedTransitionProvider.kt`, declarar a escala de prioridades do overlay com os três níveis nomeados por papel — elemento compartilhado, chrome de navegação, FAB — em valores contíguos e crescentes
- [x] 1.2 Documentar na escala por que os níveis precisam ser distintos: prioridades iguais são desempatadas pela ordem de attach, que nos slots do `Scaffold` é a inversa da ordem de desenho normal

## 2. Os participantes declaram seu nível

- [x] 2.1 Em `core/ui/.../component/CreditCardCard.kt`, fazer `creditCardSharedElement` passar explicitamente o nível do elemento compartilhado, em vez de omitir o argumento e herdar o default do Compose
- [x] 2.2 Em `feature/shell/impl/.../screen/home/ChromeHost.kt`, dar a `aboveSharedElements` um parâmetro de nível, no lugar do `1f` fixo
- [x] 2.3 Aplicar o nível do chrome de navegação nos dois call sites da barra e do rail (slot `bottomBar` e `NavigationRailBar`)
- [x] 2.4 Aplicar o nível do FAB no call site do slot `floatingActionButton`
- [x] 2.5 Atualizar o KDoc de `aboveSharedElements` para registrar que barra e FAB não compartilham prioridade, e por que unificá-los reintroduz o defeito

## 3. Verificação

- [x] 3.1 Compilar: `./gradlew :app:shared:compileDebugKotlinAndroid` (ou `./gradlew allTests` se preferir a suíte completa)
- [x] 3.2 Confirmar que nenhum `1f`/`0f` de prioridade de overlay restou espalhado — a busca por `zIndexInOverlay` deve encontrar apenas referências à escala
- [ ] 3.3 Rodar o app em janela compacta **com ao menos um cartão de crédito cadastrado** e navegar da dashboard para outra tela: o FAB permanece inteiro sobre a barra durante toda a animação
- [ ] 3.4 Repetir 3.3 no sentido inverso (voltando para a dashboard)
- [ ] 3.5 Verificar a não-regressão do requisito original: durante a transição dashboard ↔ tela de cartões, o cartão continua sendo desenhado por baixo do chrome
- [ ] 3.6 Verificar o modo wide: o FAB no `header` do rail e o rail seguem sem alteração visual
- [ ] 3.7 Verificar que, sem nenhum cartão cadastrado, a dashboard e suas transições seguem sem regressão
