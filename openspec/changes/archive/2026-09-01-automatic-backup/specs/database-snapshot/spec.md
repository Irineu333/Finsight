## ADDED Requirements

### Requirement: O banco se copia antes de aplicar migrações, quando lhe dão um destino

Antes de aplicar migrações a um arquivo existente, o banco SHALL capturar o próprio conteúdo — com
o mesmo mecanismo que já produz um arquivo autossuficiente — **se, e somente se, um destino para
essa cópia lhe for informado**.

A captura SHALL acontecer antes de a primeira migração escrever qualquer coisa. Determinar se há
migração a aplicar SHALL ser feito lendo a versão gravada no arquivo em isolamento, sem abrir o
banco pela camada que dispara as migrações — abrir para descobrir já teria migrado.

O destino MUST NOT ser escolhido aqui, e este módulo MUST NOT consultar preferência alguma para
decidir se captura: receber um destino é a decisão, e ela pertence a quem monta o banco. Um destino
informado significa capturar; nenhum destino significa não capturar. Remover a cópia anterior é de
quem informou o destino, como toda a limpeza de arquivo deste módulo.

Uma captura que falhe MUST NOT impedir a migração de acontecer: a alternativa seria um app que não
abre. A falha SHALL ser registrada, e a migração SHALL prosseguir.

#### Scenario: Atualização com migração e destino informado
- **WHEN** o banco é aberto com um destino para a cópia e a versão do arquivo é menor que a do app
- **THEN** uma cópia do conteúdo anterior é escrita antes de a primeira migração rodar, e ela abre
  sozinha como qualquer arquivo capturado

#### Scenario: Nenhum destino informado
- **WHEN** o banco é aberto sem destino para a cópia e há migração a aplicar
- **THEN** nenhuma cópia é capturada, e a migração é aplicada normalmente

#### Scenario: Abertura sem migração pendente
- **WHEN** o banco é aberto com destino informado e a versão do arquivo é igual à do app
- **THEN** nenhuma cópia é capturada

#### Scenario: Instalação nova
- **WHEN** o banco é aberto pela primeira vez e o arquivo ainda não existe
- **THEN** nenhuma cópia é capturada, e nada é criado para ser copiado

#### Scenario: Captura que falha não trava o app
- **WHEN** a captura anterior à migração falha por falta de espaço
- **THEN** a migração é aplicada assim mesmo e o app abre
