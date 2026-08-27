## ADDED Requirements

### Requirement: A lista responde por um mês, e o que ela lista são ciclos

A lista da tela de recorrentes SHALL exibir os **ciclos** dos templates no mês selecionado, e
não os templates em si.

A distinção é o fundamento de tudo o que segue. Um template não tem mês — apenas a ocorrência
dele tem —, e é exatamente por isso que a lista deixa de listar templates: os estados que
organizam a tela são propriedades do ciclo, e um ciclo tem mês por definição.

O seletor de mês SHALL governar a lista e o resumo, que passam a responder pela mesma pergunta
sobre o mesmo mês.

Um template que não gera ciclo no mês selecionado MUST NOT aparecer na lista, seja porque a
série ainda não havia começado, seja porque ele está arquivado.

#### Scenario: O mês selecionado muda
- **WHEN** o usuário seleciona outro mês
- **THEN** a lista passa a exibir os ciclos daquele mês, e o resumo responde pelo mesmo mês

#### Scenario: Mês anterior ao início da série
- **WHEN** o mês selecionado é anterior ao mês de origem de um template
- **THEN** aquele template não aparece em nenhuma seção da lista

#### Scenario: Template arquivado
- **WHEN** um template está arquivado
- **THEN** ele não aparece na lista em mês algum

### Requirement: Os quatro estados de ciclo são uma partição com dono único no domínio

Todo ciclo do mês SHALL estar em exatamente um de quatro estados: **pendente**, **a lançar**,
**lançado** ou **ignorado**.

A partição SHALL ter um único dono no domínio, e todo consumidor SHALL perguntar a ele. Nenhuma
tela, componente ou outro caso de uso MUST reconstruir qualquer um dos quatro predicados por
conta própria.

O estado é derivado, nunca persistido: *lançado* e *ignorado* são o status da ocorrência do mês;
*pendente* e *a lançar* são as duas metades do que não tem ocorrência alguma.

O conjunto de ciclos tratados e o conjunto projetado como ainda devidos SHALL ser complementares
por construção, e não por dois predicados que possam discordar.

#### Scenario: Um consumidor pergunta pelos pendentes
- **WHEN** uma superfície precisa saber quais ciclos estão pendentes
- **THEN** ela obtém a resposta do dono da partição, e não de um predicado próprio

#### Scenario: Um ciclo confirmado
- **WHEN** existe ocorrência confirmada para o template no mês
- **THEN** o ciclo está em "lançado" e em nenhum dos outros três estados

#### Scenario: Um ciclo ignorado
- **WHEN** existe ocorrência ignorada para o template no mês
- **THEN** o ciclo está em "ignorado" e em nenhum dos outros três estados

### Requirement: Pendente e a lançar se separam por data, nunca por dia do mês

A separação entre um ciclo **pendente** e um **a lançar** SHALL comparar a **data efetiva do
ciclo no mês selecionado** com a data de hoje. Um ciclo cuja data efetiva já chegou está
pendente; um cuja data ainda não chegou está a lançar.

A comparação MUST NOT ser feita entre números de dia do mês. Comparar dias só é correto enquanto
o mês observado é o corrente; com um mês selecionável, resolve o dia sobre um mês e o compara
com o dia de outro.

A data efetiva SHALL respeitar meses mais curtos que o dia declarado pelo template.

#### Scenario: Mês passado
- **WHEN** o mês selecionado já terminou
- **THEN** todo ciclo sem ocorrência está pendente, e a seção "a lançar" fica vazia

#### Scenario: Mês futuro
- **WHEN** o mês selecionado ainda não começou
- **THEN** todo ciclo sem ocorrência está a lançar, e a seção "pendente" fica vazia

#### Scenario: Dia que não existe no mês
- **WHEN** o template declara o dia 31 e o mês selecionado tem 30 dias
- **THEN** a data efetiva do ciclo é o dia 30, e a separação usa essa data

### Requirement: A lista é organizada em seções por estado do ciclo

A lista SHALL agrupar os ciclos em seções, uma por estado, nesta ordem: **pendente**, **a
lançar**, **lançado**, **ignorado**.

A ordem é por quanto cada grupo pede do usuário, e não cronológica: o que está vencido e não
resolvido vem primeiro, o que vem a seguir depois, e o que já passou por último.

Cada seção SHALL exibir a **contagem** dos ciclos que contém.

Uma seção sem ciclo algum MUST NOT ser renderizada — nem o cabeçalho, nem a contagem. Uma seção
vazia é o resumo do mês afirmando de novo uma ausência que ele já afirma com mais precisão.

#### Scenario: Mês com ciclos em três estados
- **WHEN** o mês tem ciclos pendentes, lançados e ignorados, e nenhum a lançar
- **THEN** três seções são exibidas, na ordem pendente, lançado, ignorado, e a seção "a lançar" não é renderizada

#### Scenario: Contagem da seção
- **WHEN** a seção "pendente" contém dois ciclos
- **THEN** o cabeçalho da seção afirma que são dois

### Requirement: Dentro da seção, a ordem é a data do ciclo

Dentro de cada seção, os ciclos SHALL ser ordenados pela **data efetiva do ciclo**, em ordem
crescente.

A ordem de criação do template não serve sob nenhum dos quatro cabeçalhos: o que ordena um ciclo
pendente é há quanto tempo ele venceu, e o que ordena um a lançar é quão perto está de vencer.
Crescente atende às duas leituras com uma regra só.

#### Scenario: Dois pendentes
- **WHEN** a seção "pendente" contém um ciclo do dia 5 e outro do dia 12
- **THEN** o do dia 5 é exibido primeiro

#### Scenario: Dois a lançar
- **WHEN** a seção "a lançar" contém um ciclo do dia 20 e outro do dia 28
- **THEN** o do dia 20 é exibido primeiro

### Requirement: A linha de um ciclo lançado afirma o que o ledger registrou

A linha de um ciclo **lançado** SHALL exibir o que a transação daquele ciclo registrou — o
valor, a identidade e a classificação —, e MUST NOT exibir o que o template previa.

Confirmar um ciclo permite sobrescrever o valor, a conta ou cartão, o título e a categoria
daquele ciclo, deixando o template como estava. Uma linha que lesse o template afirmaria sobre
aquele mês um número e um nome que podem nunca ter existido.

A identidade exibida SHALL ser derivada pela mesma regra de nomeação que nomeia o template, de
modo que a tela não passe a ter dois vocabulários de identidade.

A leitura das transações dos ciclos lançados SHALL ser feita em uma única consulta por emissão,
e MUST NOT ser feita uma consulta por linha.

#### Scenario: Ciclo confirmado com valor diferente do template
- **WHEN** um template de R$ 940 teve o ciclo do mês confirmado por R$ 865
- **THEN** a linha na seção "lançado" exibe R$ 865

#### Scenario: Ciclo confirmado com outro título
- **WHEN** um template chamado "Aluguel" teve o ciclo confirmado com o título "Aluguel + condomínio"
- **THEN** a linha na seção "lançado" exibe "Aluguel + condomínio"

#### Scenario: Ciclo lançado de um template que perdeu a conta
- **WHEN** a conta que o template nomeia foi removida depois de o ciclo ter sido confirmado
- **THEN** a linha na seção "lançado" exibe a figura registrada no razão, com a moeda em que foi registrada, e não a marca de valor irresolvível

### Requirement: A linha de um ciclo ignorado exibe o valor previsto

A linha de um ciclo **ignorado** SHALL exibir o valor do template — o único número que existe
para ela — e MUST NOT omitir a figura.

Um ciclo ignorado não tem fato (não há lançamento) nem promessa (o mês já foi resolvido), e o
valor previsto é o que responde *quanto deixou de ser lançado*.

A linha MUST NOT carregar tratamento visual próprio para afirmar que foi ignorada. O cabeçalho
da seção já é essa afirmação, feita uma vez para o grupo inteiro; repeti-la na linha é a mesma
redundância que a linha já é proibida de carregar.

#### Scenario: Ciclo ignorado
- **WHEN** o ciclo do mês de um template de R$ 120 foi ignorado
- **THEN** a linha na seção "ignorado" exibe R$ 120, sem marca própria de estado

#### Scenario: Altura constante
- **WHEN** a lista exibe seções de estados diferentes
- **THEN** as linhas de template têm a mesma altura em todas elas

### Requirement: Um mês sem ciclo algum não apaga o resumo

Quando nenhuma das quatro seções tem conteúdo, a lista SHALL exibir o vazio como **item abaixo
do resumo**, e MUST NOT substituir a tela por um estado vazio.

É precisamente quando o resumo mais tem a dizer sobre o mês, e apagá-lo deixaria o usuário sem
resposta e sem contexto.

O estado vazio de **base** — nenhum template cadastrado — permanece ocupando a tela inteira, com
a oferta de criar o primeiro.

#### Scenario: Mês sem ciclos
- **WHEN** o mês selecionado não tem ciclo algum e existem templates cadastrados
- **THEN** o resumo do mês permanece visível e o vazio é exibido abaixo dele

#### Scenario: Nenhum template cadastrado
- **WHEN** não existe template algum
- **THEN** a tela exibe o estado vazio de base, com a oferta de criar o primeiro
