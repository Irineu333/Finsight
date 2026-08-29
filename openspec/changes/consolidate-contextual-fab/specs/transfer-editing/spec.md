## ADDED Requirements

### Requirement: A conta de origem é pré-seleção, e não pré-requisito

Registrar uma transferência SHALL ser possível **sem** que uma conta de origem tenha sido escolhida
antes de o formulário abrir. A conta de origem é o primeiro campo da operação, e um formulário que
exige tê-la resolvida para existir obriga o usuário a chegar nele por uma conta — o que transforma
uma escolha em itinerário.

O formulário SHALL ter dois pontos de partida, e ambos SHALL levar ao mesmo formulário:

- Aberto **a partir de uma conta**, ele SHALL nascer com essa conta pré-selecionada como origem.
  A pré-seleção é uma conveniência, e MUST NOT ser congelada: trocá-la é uma edição de campo como
  qualquer outra.
- Aberto **sem conta em foco**, ele SHALL nascer com a origem vazia, e MUST NOT eleger uma conta
  por conta própria. Uma origem escolhida pelo sistema seria indistinguível de uma escolhida pelo
  usuário no momento do envio, e a operação é sobre o dinheiro dele.

A ausência de origem SHALL valer apenas para o **estado inicial** do formulário. As validações do
envio não mudam: a origem continua obrigatória, continua tendo de ser diferente do destino, e o
envio MUST NOT prosseguir sem ela.

O modo de **correção** MUST NOT admitir origem vazia. Numa correção a origem é a perna que a
operação já registra, e não uma escolha em aberto.

#### Scenario: Transferência iniciada de uma conta
- **WHEN** o usuário aciona a transferência a partir de uma conta aberta
- **THEN** o formulário abre com essa conta como origem, e o usuário pode trocá-la

#### Scenario: Transferência iniciada sem conta em foco
- **WHEN** o usuário aciona a transferência da tela de contas, sem nenhuma conta aberta
- **THEN** o formulário abre com a origem vazia, oferecendo todas as contas, e nenhuma delas vem
  escolhida

#### Scenario: Envio sem origem escolhida
- **WHEN** o usuário tenta enviar o formulário sem ter escolhido a conta de origem
- **THEN** a operação não é registrada, pela mesma validação que já rege o envio

#### Scenario: Correção de uma transferência existente
- **WHEN** o formulário é aberto para corrigir uma transferência
- **THEN** a origem vem preenchida com a perna que a operação registra, e o modo sem origem não é
  alcançável
