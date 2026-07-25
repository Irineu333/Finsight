## ADDED Requirements

### Requirement: A lista vazia SHALL dizer o que está vazio

Quando o recorte atual da tela de transações não contém nenhum lançamento, a tela SHALL exibir
uma mensagem no lugar da lista. MUST NOT restar apenas espaço em branco abaixo do resumo.

A mensagem SHALL distinguir **duas leituras do vazio**, porque a saída de cada uma é diferente:

- **vazio de origem** — não existe nenhuma transação registrada, em nenhum mês, sob nenhum
  escopo; nenhum controle da tela pode revelar alguma. A mensagem SHALL convidar a registrar a
  primeira, apontando o comando de adicionar que a tela já oferece;
- **vazio de recorte** — existem transações registradas, mas nenhuma cabe no recorte atual
  (mês, escopo ou filtros). A mensagem SHALL indicar que o recorte é a causa.

A distinção SHALL derivar do conjunto de transações **antes** de qualquer filtro, e não da
lista já recortada. A tela MUST NOT decidir qual das duas mensagens exibir a partir de quais
controles estão ativos: com todos os filtros no neutro, um mês sem lançamentos continua sendo
vazio de recorte enquanto existir uma transação em outro mês.

#### Scenario: Nenhuma transação registrada

- **WHEN** o usuário abre a tela de transações e não existe nenhuma transação registrada
- **THEN** a tela exibe a mensagem de vazio de origem, convidando a registrar a primeira

#### Scenario: Mês sem lançamentos, mas há transações em outro mês

- **WHEN** o mês selecionado não tem lançamentos e existe ao menos uma transação em outro mês
- **THEN** a tela exibe a mensagem de vazio de recorte, e não o convite de primeira transação

#### Scenario: Filtro corta tudo

- **WHEN** o mês selecionado tem lançamentos, mas nenhum sobrevive aos filtros ativos
- **THEN** a tela exibe a mensagem de vazio de recorte

#### Scenario: Lista com itens

- **WHEN** o recorte atual contém ao menos um lançamento
- **THEN** nenhuma mensagem de vazio é exibida

### Requirement: A tela MUST NOT afirmar vazio antes da primeira leitura

Enquanto a primeira leitura do repositório de transações não chegou, a tela SHALL exibir
**nenhuma** das duas mensagens de vazio. "Ainda não sei" e "não há nada" são afirmações
diferentes e MUST NOT compartilhar a mesma aparência.

O estado inicial da tela SHALL ser distinguível do estado de lista vazia — MUST NOT ser um
valor cujos campos coincidam com os de um recorte legitimamente sem itens.

#### Scenario: Antes da primeira emissão

- **WHEN** a tela é aberta e o repositório ainda não emitiu
- **THEN** nenhuma mensagem de vazio é exibida

#### Scenario: Primeira emissão sem transações

- **WHEN** o repositório emite pela primeira vez e não há nenhuma transação
- **THEN** a mensagem de vazio de origem passa a ser exibida

### Requirement: Os controles do recorte SHALL permanecer visíveis no vazio

O resumo do escopo e os controles que governam o recorte — escopo, período e os chips de
filtro — SHALL continuar visíveis quando a lista está vazia. MUST NOT ocorrer de o vazio
substituir a tela inteira, o que removeria justamente os controles capazes de sair dele.

A mensagem de vazio SHALL ocupar o lugar da lista, abaixo desses controles.

#### Scenario: Vazio de recorte preserva os controles

- **WHEN** o recorte atual não contém lançamentos
- **THEN** o resumo do escopo e os chips de filtro continuam visíveis, e a mensagem aparece
  abaixo deles

### Requirement: O vazio de recorte SHALL oferecer limpar os filtros quando houver algum ativo

Quando a tela está em vazio de recorte e ao menos um filtro de lista está ativo — categoria,
natureza, alvo, apenas recorrentes ou apenas parcelados —, a mensagem SHALL oferecer uma ação
de **limpar os filtros**. Com todos os filtros no neutro, a ação MUST NOT ser oferecida: não
haveria nada para limpar e o botão prometeria um resultado que não pode entregar.

Limpar os filtros SHALL devolver ao neutro apenas os filtros de lista. MUST NOT alterar o mês
nem o escopo, que governam também o resumo (`transaction-scope`) — mudá-los faria a ação
reescrever números que o usuário não pediu para mudar.

O vazio de origem MUST NOT oferecer essa ação: sem nenhuma transação registrada, limpar
filtros não revelaria nada.

#### Scenario: Filtro ativo oferece limpar

- **WHEN** a tela está em vazio de recorte com um filtro de categoria ativo
- **THEN** a mensagem oferece limpar os filtros

#### Scenario: Sem filtro ativo não oferece limpar

- **WHEN** a tela está em vazio de recorte com todos os filtros no neutro (mês sem lançamentos)
- **THEN** a mensagem não oferece limpar os filtros

#### Scenario: Limpar preserva mês e escopo

- **WHEN** o usuário limpa os filtros com um mês e um escopo selecionados
- **THEN** os filtros de lista voltam ao neutro e o mês e o escopo permanecem os mesmos, com o
  resumo inalterado

#### Scenario: Vazio de origem não oferece limpar

- **WHEN** a tela está em vazio de origem
- **THEN** nenhuma ação de limpar filtros é oferecida
