## Context

Hoje o `main.kt` do `app/desktop` cria a janela sem `WindowState`:

```kotlin
Window(onCloseRequest = ::exitApplication, title = "Finsight") { ... }
```

Consequências: tamanho padrão do Compose (800×600), posição centralizada pelo SO, nada persistido, sem tamanho mínimo. O default de 800dp cai entre os breakpoints do shell — `isWideWindow()` (≥600dp, rail) e `isExtraWideWindow()` (≥840dp, detail pane de 400dp) — então o detail pane nunca aparece no primeiro uso.

Infra já disponível:
- `Settings` (multiplatform-settings) provido em `core/common` via `single<Settings> { Settings() }`. No JVM é backed por `java.util.prefs.Preferences` (sem arquivo a gerenciar). Já usado pelo dashboard.
- Compose Desktop expõe `rememberWindowState(placement, position, size)` e, dentro do `WindowScope`, a `ComposeWindow` (`window`) do AWT.

## Goals / Non-Goals

**Goals:**
- Restaurar posição, tamanho e placement da janela entre execuções.
- Definir tamanho padrão maior (~1100×760) e posição centralizada no primeiro uso.
- Aplicar tamanho mínimo (~480×600) permitindo modo compacto.
- Fallback seguro quando a posição salva está off-screen.
- Manter `main.kt` limpo; isolar a persistência num helper testável dentro de `app/desktop`.

**Non-Goals:**
- Persistir estado por-monitor ou lembrar em qual tela abrir (só clamp de segurança).
- Qualquer mudança em Android/iOS.
- Persistir estado de janelas secundárias/diálogos — só a janela principal.
- Configuração de tamanho/posição pelo usuário via UI.

## Decisions

### 1. `WindowState` + persistência via `Settings`
Usar `rememberWindowState(size, position, placement)` como fonte de verdade da janela e persistir mudanças observando o estado com `snapshotFlow { Triple(size, position, placement) }` + `debounce` (evita gravar a cada pixel durante o arraste). Persistência via o `Settings` já existente, com chaves `window_width`, `window_height`, `window_x`, `window_y`, `window_placement`.

_Alternativa considerada_: arquivo JSON próprio em `~/.finance/`. Rejeitada — reintroduz gerenciamento de arquivo que o `Settings` já resolve, e o app já padroniza preferências nele.

### 2. Placement persistido (não bloqueado)
Persistir `WindowPlacement` (Floating/Maximized) junto de size/position. Quando maximizada, `WindowState` mantém os bounds de restauração em `size`/`position`, então salvar os três reabre corretamente maximizado com bounds válidos ao desmaximizar.

_Alternativa considerada_: bloquear o botão maximizar. Rejeitada — exige mexer em flags da `ComposeWindow` e piora a UX; persistir é mais simples e melhor.

### 3. Tamanho mínimo via `ComposeWindow.minimumSize`
`WindowState` não expõe tamanho mínimo. Aplicar `window.minimumSize = Dimension(minW, minH)` num `LaunchedEffect(Unit)` dentro do `WindowScope`. Mínimo ~480×600 fica abaixo do breakpoint de 600dp, preservando a possibilidade de encolher até o modo compacto (bottom bar).

### 4. Números padrão
- Padrão: **1100×760** — acima de 840dp, abre já com o detail pane visível.
- Mínimo: **480×600** — impede janela ilegível, permite modo compacto.
- Posição padrão: **centralizada** (`WindowPosition.Aligned(Alignment.Center)`).

### 5. Localização do código — `app/desktop`
Concern 100% desktop; não justifica cruzar para `core/common`. Um helper fino (ex.: `WindowStatePersistence`) no próprio `app/desktop`, responsável por carregar o estado inicial (com clamp/fallback) e salvar mudanças. `main.kt` só orquestra.

### 6. Clamp off-screen → centralizar
Ao carregar a posição salva, validar contra os bounds das telas disponíveis (`GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices`, unindo os `bounds` de cada `GraphicsConfiguration`). Se a posição salva não interseccionar nenhuma tela, descartar a posição e usar centralizada. Tamanho é mantido (clampado ao mínimo).

## Risks / Trade-offs

- **Escrita frequente durante arraste/resize** → `debounce` no `snapshotFlow` antes de persistir; `java.util.prefs` é leve.
- **Posição salva off-screen (monitor removido)** → clamp contra bounds de tela; fallback centralizado.
- **Tamanho salvo menor que o mínimo atual** (ex.: mínimo aumentado numa versão futura) → aplicar `coerceAtLeast(minimo)` ao carregar o tamanho.
- **Placement maximizado com bounds de restauração ausentes/ inválidos** → se faltarem bounds válidos, cair para o tamanho/posição padrão como restore.
- **Detecção de intersecção multi-monitor imperfeita** (coordenadas negativas em setups à esquerda do primário) → usar união dos bounds de todas as `GraphicsConfiguration`, cobrindo coordenadas negativas.
