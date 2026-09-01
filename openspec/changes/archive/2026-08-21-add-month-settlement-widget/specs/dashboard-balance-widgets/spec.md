## ADDED Requirements

### Requirement: Um widget deprecado sai da vitrine sem sair da tela de quem o tem

O catálogo de widgets SHALL admitir marcar um tipo como **deprecado**, e deprecado SHALL
significar exatamente três coisas, nem mais:

1. O widget MUST NOT ser oferecido na vitrine de adição do modo de edição.
2. O widget MUST NOT compor o layout padrão.
3. O widget SHALL continuar sendo **construído e renderizado** para todo dashboard cuja
   preferência salva o nomeia, com a mesma aparência e a mesma configuração de antes.

Depreciar MUST NOT remover a chave do widget nem reescrever preferência alguma: uma chave salva
que não resolvesse mais para um widget faria o dashboard do usuário perder uma posição em
silêncio. Um widget deprecado que o usuário remova pela edição SHALL deixar de ser oferecido de
volta, por não estar mais na vitrine.

Depreciar SHALL ser reservado ao caso em que outro widget **contém** o que o deprecado somava;
dois widgets que apenas se assemelham MUST NOT motivar depreciação, por o usuário poder querer
os dois.

#### Scenario: Deprecado não aparece na vitrine
- **WHEN** o usuário entra no modo de edição e o widget deprecado não está no seu dashboard
- **THEN** ele não é oferecido na lista de widgets disponíveis para adicionar

#### Scenario: Deprecado continua renderizando para quem o tem salvo
- **WHEN** um dashboard cuja preferência salva nomeia o widget deprecado é carregado
- **THEN** o widget é exibido normalmente, na mesma posição e com a mesma configuração

#### Scenario: Depreciar não reescreve preferência
- **WHEN** um widget é deprecado
- **THEN** nenhuma preferência salva é alterada, e nenhuma chave deixa de resolver

#### Scenario: Removido pela edição não volta
- **WHEN** o usuário remove o widget deprecado do seu dashboard
- **THEN** ele deixa de ser oferecido para adicionar de volta

### Requirement: Trocar o layout padrão só é admissível por superconjunto

Uma alteração do layout padrão SHALL ser tratada como mudança de comportamento observável, e
MUST NOT ser feita como efeito colateral de introduzir um widget: o layout padrão é resolvido a
cada leitura para todo dashboard **sem preferência salva**, de modo que alterá-lo altera a tela
de quem nunca entrou no modo de edição.

Retirar um widget do layout padrão SHALL ser admissível **apenas** quando outro widget, entrando
no mesmo layout, **contém** a informação que o retirado exibia. Retirar sem repor MUST NOT ser
feito: o usuário perderia informação de uma tela que ele nunca pediu para mudar.

A regra de que um dashboard existente não muda continua valendo para toda **preferência salva**:
uma troca no layout padrão MUST NOT reescrever preferência alguma, e quem já editou o dashboard
SHALL continuar vendo exatamente o que via, incluindo o widget retirado do padrão.

#### Scenario: Troca por superconjunto no dashboard nunca editado
- **WHEN** um dashboard sem preferência salva é carregado depois de o padrão trocar um widget por outro que o contém
- **THEN** ele exibe o widget novo no lugar do retirado, sem perda de informação

#### Scenario: Dashboard já editado não muda
- **WHEN** um dashboard com preferência salva nomeando o widget retirado é carregado
- **THEN** ele continua exibindo aquele widget, na mesma posição e configuração

#### Scenario: Retirada sem reposição é recusada
- **WHEN** uma mudança propõe retirar um widget do layout padrão sem que outro widget do padrão contenha o que ele exibia
- **THEN** a retirada não é feita

### Requirement: Ocultar-quando-vazio distingue vazio de dados de vazio de fontes

A configuração de **ocultar-quando-vazio** de um widget SHALL governar apenas o caso em que o
perímetro configurado nada tem a somar no período. Ela MUST NOT governar o caso em que o próprio
**perímetro** foi esvaziado pela configuração do usuário.

Um widget cujo perímetro o usuário reduziu até não restar parcela alguma SHALL exibir **zero** e
permanecer na tela, **independentemente** de estar configurado para ocultar-se quando vazio. Um
widget que some enquanto o usuário opera a sua própria configuração torna a configuração
inoperável, e as duas configurações MUST NOT se compor para produzir esse efeito.

Esta regra é a forma geral do que o requisito "Total sem parcela alguma vale zero" afirma para o
widget de saldo em contas, e SHALL valer para todo widget cujo perímetro seja configurável,
qualquer que seja o eixo da configuração — quais contas compõem a figura, ou quais fontes a
compõem.

#### Scenario: Perímetro esvaziado não oculta, ainda que configurado para ocultar
- **WHEN** o usuário esvazia o perímetro de um widget que está configurado para ocultar-se quando vazio
- **THEN** o widget permanece na tela exibindo zero

#### Scenario: Ausência de dados continua ocultando
- **WHEN** o perímetro do widget não está vazio, nada há a somar no período, e o widget está configurado para ocultar-se quando vazio
- **THEN** o widget inteiro desaparece

#### Scenario: A distinção vale para qualquer eixo de configuração
- **WHEN** o perímetro esvaziado é um conjunto de fontes, e não um conjunto de contas
- **THEN** a mesma regra se aplica, e o widget permanece exibindo zero
