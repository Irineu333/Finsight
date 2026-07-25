## ADDED Requirements

### Requirement: Cada lista de lançamentos SHALL dizer o que está vazio

A tela SHALL exibir uma mensagem no lugar da lista quando a lista de lançamentos das telas de
**contas**, **cartões** ou **faturas** não contém nenhum item. MUST NOT restar apenas espaço
em branco abaixo do cartão do topo e dos chips.

A mensagem SHALL distinguir **duas leituras do vazio**, porque a saída de cada uma é diferente:

- **vazio de origem** — o recorte-raiz da tela não tem nenhum lançamento; nenhum filtro pode
  revelar algum. O recorte-raiz é, por tela: os lançamentos da conta selecionada em qualquer
  mês (contas), os lançamentos da fatura corrente do cartão selecionado (cartões), os
  lançamentos da fatura selecionada (faturas);
- **vazio de recorte** — o recorte-raiz tem lançamentos, mas nenhum sobrevive ao mês (contas)
  ou aos filtros ativos. A mensagem SHALL indicar que o recorte é a causa.

A distinção SHALL derivar do recorte-raiz, e não da lista já filtrada nem de quais controles
estão ativos.

#### Scenario: Conta sem nenhum lançamento

- **WHEN** a conta selecionada não tem nenhum lançamento em nenhum mês
- **THEN** a tela de contas exibe a mensagem de vazio de origem

#### Scenario: Mês sem lançamentos numa conta que movimenta

- **WHEN** o mês selecionado não tem lançamentos e a conta selecionada tem lançamentos em outro
  mês
- **THEN** a tela de contas exibe a mensagem de vazio de recorte, e não a de origem

#### Scenario: Fatura corrente do cartão sem lançamentos

- **WHEN** a fatura corrente do cartão selecionado não tem nenhum lançamento
- **THEN** a tela de cartões exibe a mensagem de vazio de origem

#### Scenario: Fatura selecionada sem lançamentos

- **WHEN** a fatura selecionada no pager não tem nenhum lançamento
- **THEN** a tela de faturas exibe a mensagem de vazio de origem

#### Scenario: Filtro corta tudo

- **WHEN** o recorte-raiz de qualquer uma das três telas tem lançamentos e nenhum sobrevive aos
  filtros ativos
- **THEN** a tela exibe a mensagem de vazio de recorte

#### Scenario: Lista com itens

- **WHEN** o recorte atual contém ao menos um lançamento
- **THEN** nenhuma mensagem de vazio é exibida

### Requirement: O vazio de origem MUST NOT instruir a usar um comando que a tela não oferece

A mensagem de vazio de origem SHALL constatar o estado do recorte-raiz e MUST NOT apontar um
botão de adicionar lançamento, nem oferecer ação alguma — nenhuma das três telas oferece o
comando de registrar um lançamento.

#### Scenario: Vazio de origem não oferece ação

- **WHEN** qualquer uma das três telas está em vazio de origem
- **THEN** a mensagem não oferece nenhuma ação

### Requirement: A tela de faturas MUST NOT afirmar vazio antes da primeira leitura

Enquanto a primeira leitura ainda não chegou, a tela de faturas SHALL exibir **nenhuma** das
duas mensagens de vazio. "Ainda não sei" e "não há nada" são afirmações diferentes e MUST NOT
compartilhar a mesma aparência.

O estado inicial da tela SHALL ser distinguível do estado de lista vazia — MUST NOT ser um
valor cujos campos coincidam com os de uma fatura legitimamente sem lançamentos.

Trocar de fatura ou de filtro MUST NOT devolver a tela ao estado de carregamento: o recorte é
feito sobre dados já observados.

#### Scenario: Antes da primeira emissão

- **WHEN** a tela de faturas é aberta e os repositórios ainda não emitiram
- **THEN** nenhuma mensagem de vazio é exibida

#### Scenario: Primeira emissão com fatura sem lançamentos

- **WHEN** a primeira emissão chega e a fatura selecionada não tem lançamentos
- **THEN** a mensagem de vazio de origem passa a ser exibida

#### Scenario: Trocar de fatura não volta ao carregamento

- **WHEN** o usuário seleciona outra fatura no pager depois da primeira leitura
- **THEN** a tela exibe imediatamente a lista ou a mensagem de vazio da nova fatura, sem passar
  pelo estado de carregamento

### Requirement: Os controles do recorte SHALL permanecer visíveis no vazio

SHALL continuar visíveis quando a lista está vazia: o cartão do topo — pager de contas, de
cartões ou de faturas —, as ações que o acompanham e os chips de filtro. MUST NOT ocorrer de o vazio
da lista substituir a tela inteira, o que removeria justamente os controles capazes de sair
dele.

A mensagem de vazio SHALL ocupar o lugar da lista, abaixo desses controles.

Esta regra vale para o vazio **da lista de lançamentos**. O vazio de *nenhum cartão
cadastrado*, que ocupa a tela de cartões inteira, MUST NOT ser afetado: sem cartão não há pager
nem chips a preservar.

#### Scenario: Vazio preserva o pager e os chips

- **WHEN** a lista de lançamentos de qualquer uma das três telas está vazia
- **THEN** o cartão do topo, suas ações e os chips de filtro continuam visíveis, e a mensagem
  aparece abaixo deles

#### Scenario: Nenhum cartão cadastrado continua ocupando a tela

- **WHEN** não há nenhum cartão cadastrado
- **THEN** a tela de cartões continua exibindo o seu vazio de tela inteira, com o convite a
  criar o primeiro cartão

### Requirement: O vazio de recorte SHALL oferecer limpar os filtros quando houver algum ativo

A mensagem SHALL oferecer uma ação de **limpar os filtros** quando uma das três telas está em
vazio de recorte e ao menos um filtro de lista está ativo. Os filtros de lista são: categoria,
tipo e apenas recorrentes (contas); categoria, tipo, apenas recorrentes e apenas parcelados
(cartões e faturas).

Com todos os filtros no neutro, a ação MUST NOT ser oferecida: não haveria nada para limpar e o
botão prometeria um resultado que não pode entregar.

Limpar os filtros SHALL devolver ao neutro apenas os filtros de lista. MUST NOT alterar o mês
selecionado (contas) nem a conta, o cartão ou a fatura selecionados — eles governam também os
números do cartão do topo, e mudá-los faria a ação reescrever o que o usuário não pediu para
mudar.

#### Scenario: Filtro ativo oferece limpar

- **WHEN** a tela está em vazio de recorte com um filtro de categoria ativo
- **THEN** a mensagem oferece limpar os filtros

#### Scenario: Mês sem lançamentos e filtros no neutro não oferece limpar

- **WHEN** a tela de contas está em vazio de recorte porque o mês selecionado não tem
  lançamentos, com todos os filtros no neutro
- **THEN** a mensagem não oferece limpar os filtros

#### Scenario: Limpar preserva mês e seleção

- **WHEN** o usuário limpa os filtros
- **THEN** os filtros de lista voltam ao neutro e o mês, a conta, o cartão e a fatura
  selecionados permanecem os mesmos, com os números do cartão do topo inalterados
