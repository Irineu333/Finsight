## 1. Persistência do estado da janela (app/desktop)

- [ ] 1.1 Criar um modelo serializável do estado da janela (largura, altura, x, y, placement)
- [ ] 1.2 Criar `WindowStatePersistence` em `app/desktop` recebendo o `Settings`, com `load()` e `save(...)` usando as chaves `window_*`
- [ ] 1.3 Definir as constantes de tamanho padrão (~1100×760) e mínimo (~480×600)
- [ ] 1.4 No `load()`, aplicar `coerceAtLeast(mínimo)` ao tamanho persistido
- [ ] 1.5 No `load()`, computar a união dos bounds das telas (`GraphicsEnvironment` → `screenDevices` → `GraphicsConfiguration.bounds`, incluindo coordenadas negativas) e, se a posição salva não interseccionar nenhuma, descartar a posição (fallback centralizado)
- [ ] 1.6 No `load()`, garantir bounds de restauração válidos quando placement = Maximized (fallback ao padrão se ausentes/ inválidos)

## 2. Integração no main.kt

- [ ] 2.1 Obter `Settings` do Koin e instanciar `WindowStatePersistence` após `startKoin`
- [ ] 2.2 Construir o `WindowState` inicial via `rememberWindowState(size, position, placement)` a partir do estado carregado (posição centralizada quando não há estado salvo ou no fallback)
- [ ] 2.3 Passar o `state` para o `Window(...)`
- [ ] 2.4 Aplicar `window.minimumSize = Dimension(mínimo)` num `LaunchedEffect(Unit)` dentro do `WindowScope`
- [ ] 2.5 Observar mudanças com `snapshotFlow { size/position/placement }` + `debounce` e persistir via `WindowStatePersistence.save(...)`

## 3. Verificação

- [ ] 3.1 Testar `WindowStatePersistence`: round-trip load/save, clamp ao mínimo, fallback off-screen (posição fora de todos os bounds → centralizado), placement maximizado com/sem bounds válidos
- [ ] 3.2 Rodar `./gradlew :app:desktop:run` e validar manualmente: primeiro uso abre ~1100×760 centralizado com detail pane visível; mover/redimensionar e reabrir restaura; maximizar e reabrir restaura maximizado; encolher até <600dp mostra bottom bar; não é possível reduzir abaixo do mínimo
- [ ] 3.3 Rodar `./gradlew check`
