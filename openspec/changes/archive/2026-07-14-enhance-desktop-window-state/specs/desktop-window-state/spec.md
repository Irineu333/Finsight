## ADDED Requirements

### Requirement: Restauração de tamanho e posição da janela

A janela principal do desktop SHALL restaurar o **tamanho** e a **posição** da última execução em que foram alterados. As mudanças de tamanho e posição feitas pelo usuário SHALL ser persistidas de forma durável entre execuções, usando o mecanismo de `Settings` já provido pela aplicação. A persistência SHALL ser aplicada apenas à janela principal.

#### Scenario: Restaurar após reabrir

- **WHEN** o usuário move e redimensiona a janela e depois fecha e reabre o app
- **THEN** a janela reabre exatamente no mesmo tamanho e na mesma posição da última sessão

#### Scenario: Persistência durável de mudanças contínuas

- **WHEN** o usuário arrasta ou redimensiona a janela continuamente
- **THEN** o estado final é persistido sem exigir gravação a cada pixel intermediário

### Requirement: Tamanho e posição padrão no primeiro uso

No primeiro uso — quando não há estado persistido — a janela SHALL abrir com um tamanho padrão de aproximadamente 1100×760, largura suficiente para exibir o detail pane (acima do breakpoint Expanded de 840dp), e SHALL abrir **centralizada** na tela.

#### Scenario: Primeiro uso abre com detail pane visível

- **WHEN** o app é aberto pela primeira vez e nenhum estado de janela foi salvo
- **THEN** a janela abre com o tamanho padrão largo o suficiente para exibir o detail pane e centralizada na tela

### Requirement: Tamanho mínimo da janela

A janela SHALL impor um tamanho mínimo de aproximadamente 480×600 via `ComposeWindow.minimumSize`. O tamanho mínimo SHALL ficar abaixo do breakpoint Medium (600dp) de largura, permitindo que o usuário encolha a janela até o modo compacto (bottom bar) sem que a janela fique ilegível. Um tamanho persistido menor que o mínimo vigente SHALL ser ajustado para pelo menos o mínimo ao restaurar.

#### Scenario: Impedir janela menor que o mínimo

- **WHEN** o usuário tenta redimensionar a janela abaixo do tamanho mínimo
- **THEN** o sistema operacional impede a redução além do mínimo definido

#### Scenario: Permitir modo compacto

- **WHEN** o usuário encolhe a janela para uma largura entre o mínimo e 600dp
- **THEN** a janela é redimensionada normalmente e o shell passa a exibir o modo compacto (bottom bar)

#### Scenario: Tamanho salvo abaixo do mínimo é ajustado

- **WHEN** o estado persistido contém um tamanho menor que o mínimo vigente
- **THEN** a janela restaura com o tamanho ajustado para pelo menos o mínimo

### Requirement: Restauração de placement (maximizada)

A janela SHALL persistir e restaurar o **placement** (Floating/Maximized). Quando a janela é fechada maximizada, ela SHALL reabrir maximizada, mantendo bounds de restauração válidos para quando o usuário desmaximizar.

#### Scenario: Reabrir maximizada

- **WHEN** o usuário maximiza a janela e fecha o app
- **THEN** ao reabrir, a janela abre maximizada

#### Scenario: Bounds de restauração válidos após desmaximizar

- **WHEN** a janela reabre maximizada e o usuário a desmaximiza
- **THEN** a janela assume um tamanho e posição válidos (os bounds de restauração persistidos ou, na ausência de bounds válidos, o padrão)

### Requirement: Fallback seguro para posição off-screen

Ao restaurar, se a posição persistida não interseccionar nenhuma tela disponível (por exemplo, um monitor foi desconectado), a janela SHALL descartar a posição salva e abrir **centralizada**, preservando o tamanho persistido (ajustado ao mínimo). A detecção SHALL considerar a união dos bounds de todas as telas disponíveis, incluindo coordenadas negativas de monitores posicionados à esquerda ou acima do primário.

#### Scenario: Monitor desconectado

- **WHEN** a posição salva pertence a um monitor que não está mais conectado
- **THEN** a janela abre centralizada em vez de abrir fora da área visível, mantendo o tamanho salvo

#### Scenario: Posição válida em setup multi-monitor

- **WHEN** a posição salva intersecciona qualquer tela conectada, inclusive uma com coordenadas negativas
- **THEN** a janela restaura na posição salva sem recentralizar
