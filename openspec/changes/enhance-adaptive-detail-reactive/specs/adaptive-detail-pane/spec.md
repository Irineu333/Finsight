## ADDED Requirements

### Requirement: Detalhes dirigidos por id com carregamento reativo

As superfícies de detalhe `view*` (operação, ajuste, categoria, orçamento, recorrência) SHALL receber **apenas o identificador** da entidade e, quando aplicável, a **configuração de apresentação não-recuperável** (ex.: a perspectiva que seleciona qual transação exibir). Elas MUST NOT receber o objeto de domínio já carregado. Cada detalhe SHALL observar a entidade **por id** e expor um estado de UI com exatamente três apresentações: **carregando**, **erro** e **conteúdo**. O estado inicial SHALL ser **carregando**. Enquanto o detalhe estiver aberto, cada mudança na entidade observada SHALL re-emitir **conteúdo** atualizado, re-renderizando o detalhe **in-place** sem fechá-lo. Este comportamento SHALL valer tanto na apresentação em painel (janela larga) quanto em bottom sheet (janela estreita).

#### Scenario: Carregando é o estado inicial
- **WHEN** um detalhe `view*` é aberto por id
- **THEN** ele exibe a apresentação de **carregando** até a primeira emissão da entidade observada

#### Scenario: Conteúdo ao obter a entidade
- **WHEN** a observação por id emite a entidade
- **THEN** o detalhe passa a exibir a apresentação de **conteúdo** com os dados da entidade

#### Scenario: Erro ao não obter a entidade
- **WHEN** a primeira emissão da observação por id é vazia (id inexistente ou falha de obtenção), sem que o detalhe tenha exibido conteúdo antes
- **THEN** o detalhe exibe a apresentação de **erro**, sem ficar preso em carregando

#### Scenario: Editar re-renderiza o detalhe in-place
- **WHEN** o usuário edita a entidade a partir de um formulário aberto sobre o detalhe e salva a edição
- **THEN** o detalhe permanece aberto e re-renderiza com os dados atualizados, sem ser dispensado

### Requirement: Auto-dispensa reativa do detalhe ao desaparecer a entidade

Um detalhe `view*` que já exibiu **conteúdo** SHALL se **auto-dispensar** quando a entidade observada desaparecer (emissão vazia após ter havido conteúdo), voltando o painel ao empty-state em janela larga ou fechando o bottom sheet em janela estreita. A auto-dispensa SHALL ser dirigida pela própria observação do detalhe e MUST NOT depender do teardown de modais transitórios (`dismissAll()`). Uma emissão vazia **antes** de qualquer conteúdo MUST NOT ser tratada como desaparecimento — SHALL levar à apresentação de **erro** (ver requisito de carregamento reativo).

#### Scenario: Excluir a entidade de dentro do detalhe fecha o detalhe
- **WHEN** o usuário exclui a entidade a partir do seu detalhe `view*` e a exclusão é efetivada
- **THEN** a observação por id emite vazio após ter havido conteúdo, e o detalhe se auto-dispensa, retornando ao empty-state em janela larga

#### Scenario: Excluir a entidade por outro caminho fecha o detalhe aberto
- **WHEN** um detalhe `view*` está aberto e a entidade observada é excluída por qualquer fluxo enquanto o detalhe permanece na tela
- **THEN** o detalhe se auto-dispensa reativamente, sem exibir dados fantasma

## REMOVED Requirements

### Requirement: Teardown de overlays inclui o painel de detalhe

**Reason**: Com os detalhes `view*` reativos por id, a exclusão da entidade é tratada de forma dirigida pela observação do `null` (auto-dispensa reativa), tornando desnecessário — e indesejável — que `dismissAll()` alcance o `DetailPaneController`. O acoplamento era a causa de o detalhe fechar ao **salvar** uma edição; removê-lo permite que a edição re-renderize o detalhe in-place.

**Migration**: `ModalManager.dismissAll()` volta a limpar **apenas** a pilha de modais transitórios; o `ModalManager` deixa de compor o `DetailPaneController` (DI volta a `single { ModalManager() }`). O fechamento do detalhe na exclusão passa a ser garantido pelo requisito "Auto-dispensa reativa do detalhe ao desaparecer a entidade". Os fluxos de exclusão e submissão de formulário continuam chamando `dismissAll()` para dispensar seus próprios modais transitórios, sem referenciar o `DetailPaneController`.
