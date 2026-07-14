## Why

No desktop a janela nasce sempre igual: tamanho padrão do Compose (800×600), centralizada, e nada é persistido entre execuções. O usuário reposiciona e redimensiona a cada abertura, e o tamanho padrão fica **entre** os breakpoints do shell (≥600dp mostra o rail, ≥840dp mostra o detail pane) — ou seja, o desktop abre num tamanho que nunca exibe o detail pane, escondendo um recurso central da experiência desktop.

## What Changes

- A janela do desktop passa a **restaurar posição e tamanho** da última execução, persistidos via `Settings` (Koin, já disponível).
- A janela passa a **restaurar o placement** (maximizada volta maximizada) — incluído por ser trivial no `WindowState` e evitar UX ruim ao reabrir.
- **Novo tamanho padrão** (~1100×760) no primeiro uso: acima do breakpoint de 840dp, então o desktop abre já mostrando o detail pane.
- **Posição padrão centralizada** no primeiro uso (hoje já é o comportamento default do Compose, agora explícito e garantido).
- **Tamanho mínimo** (~480×600) aplicado via `ComposeWindow.minimumSize` — impede janela ilegível mas ainda permite encolher até o modo compacto (< 600dp = bottom bar).
- **Restauração segura off-screen**: se a posição salva não interseccionar nenhuma tela disponível (ex.: monitor desconectado), a janela cai para centralizada em vez de abrir fora da área visível.

## Capabilities

### New Capabilities
- `desktop-window-state`: persistência e restauração do estado da janela do desktop (posição, tamanho, placement), com tamanho/posição padrão no primeiro uso, tamanho mínimo e fallback seguro para posições off-screen.

### Modified Capabilities
<!-- Nenhuma capability existente tem requisitos alterados. -->

## Impact

- **Código**: `app/desktop` — `main.kt` (passa `WindowState`, aplica `minimumSize`, observa e persiste mudanças) + novo helper de persistência do estado da janela (concern 100% desktop, fica no próprio módulo).
- **Persistência**: reutiliza `Settings` já provido em `core/common` (`single<Settings> { Settings() }`), com novas chaves `window_*`.
- **Dependências**: nenhuma nova. Usa APIs já presentes do Compose Desktop (`rememberWindowState`, `WindowState`, `ComposeWindow.minimumSize`) e do AWT (`GraphicsEnvironment` para bounds de tela).
- **Plataformas**: apenas desktop (JVM). Android e iOS não são afetados.
