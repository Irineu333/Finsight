## ADDED Requirements

### Requirement: O banco se copia antes de aplicar a cadeia de migrações

Antes de aplicar migrações a um arquivo existente, o banco SHALL capturar o próprio conteúdo, com o
mesmo mecanismo de captura que já produz um arquivo autossuficiente.

A captura SHALL acontecer **antes** de a primeira migração escrever qualquer coisa. Determinar se
há migração a aplicar SHALL ser feito lendo a versão gravada no arquivo em isolamento, sem abrir o
banco pela camada que dispara as migrações — abrir para descobrir já teria migrado.

Esta cópia MUST NOT depender de configuração do produto: ela é a rede de uma operação que o próprio
app executa sobre o banco do usuário, sem que ele peça nem consinta, e existir apenas quando algo
estiver ligado a tornaria ausente exatamente para quem não ligou nada.

O destino MUST NOT ser escolhido aqui: este módulo não tem API de arquivo, e quem constrói o banco
é quem informa onde a cópia deve ser escrita e quem remove a anterior. Uma cópia por instalação
SHALL bastar — ela é substituída pela próxima migração e MUST NOT ser removida por política de
retenção alguma antes disso.

Uma captura que falhe MUST NOT impedir a migração de acontecer: a alternativa seria um app que não
abre. A falha SHALL ser registrada, e o app SHALL prosseguir sem a rede.

#### Scenario: Atualização com migração
- **WHEN** o app é aberto após uma atualização cuja versão de schema é maior que a do arquivo
- **THEN** uma cópia do conteúdo anterior é escrita antes de a primeira migração rodar, e ela abre
  sozinha como qualquer arquivo capturado

#### Scenario: Abertura sem migração pendente
- **WHEN** o app é aberto e a versão do arquivo é igual à do app
- **THEN** nenhuma cópia é capturada

#### Scenario: Instalação nova
- **WHEN** o app é aberto pela primeira vez e o arquivo do banco ainda não existe
- **THEN** nenhuma cópia é capturada, e nada é criado para ser copiado

#### Scenario: A cópia não depende de configuração
- **WHEN** uma migração é aplicada num app cujo backup automático está desligado
- **THEN** a cópia anterior à migração é capturada do mesmo modo

#### Scenario: Migração seguinte substitui a cópia
- **WHEN** uma segunda atualização com migração é aplicada
- **THEN** a cópia passa a ser a do conteúdo anterior a essa migração, e a anterior deixa de ser
  mantida

#### Scenario: Captura que falha não trava o app
- **WHEN** a captura anterior à migração falha por falta de espaço
- **THEN** a migração é aplicada assim mesmo e o app abre
