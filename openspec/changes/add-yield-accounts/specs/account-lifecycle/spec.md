## ADDED Requirements

### Requirement: Categoria de rendimentos em uso é arquivada, nunca apagada

Uma categoria identificada como a de **rendimentos** MUST NOT ser removida enquanto **alguma conta declarar que rende**. O sistema SHALL recusar a remoção com erro tipado e oferecer o arquivamento em seu lugar, exatamente como faz para os demais dependentes de uma categoria.

Esse dependente SHALL entrar como **mais um guard** na resolução de retirabilidade de categoria, ao lado dos que já existem — possuir lançamentos, estar em orçamento, ter recorrência apontando para ela. Ele MUST NOT introduzir um terceiro desfecho ao par arquivar-vs-apagar: a resposta continua sendo a mesma de qualquer categoria em uso, e nenhuma tela precisa aprender um caso novo.

A proteção SHALL ser **condicional e reversível**, e MUST NOT ser um estado de imutabilidade da categoria. Ser de sistema MUST NOT, por si só, impedir remoção: desligada a declaração de rendimento da última conta, e não havendo outro dependente, a categoria SHALL voltar a ser removível como qualquer outra. O sistema MUST NOT prover categoria que o usuário jamais possa retirar.

A recusa SHALL dizer o que precisa ser resolvido para liberá-la — desligar o rendimento das contas que o declaram —, e MUST NOT indicar um caminho que não desbloqueie a remoção.

Arquivar a categoria de rendimentos MUST NOT desligar a declaração de rendimento de conta alguma, nem impedir o lançamento de rendimento nas contas que já a declaram: quem já a usa continua usando, pela regra geral de continuidade das fachadas arquivadas.

#### Scenario: Apagar a categoria de rendimentos com conta declarada é recusado
- **WHEN** o usuário tenta apagar a categoria de rendimentos havendo alguma conta que declara render
- **THEN** o sistema recusa com erro tipado, nada é removido nem arquivado, e a interface diz que é preciso desligar o rendimento das contas antes

#### Scenario: Sem conta declarada, a categoria de rendimentos é removível
- **WHEN** o usuário apaga a categoria de rendimentos não havendo conta alguma que declare render, nem lançamento, orçamento ou recorrência que dependa dela
- **THEN** a categoria é removida como qualquer outra, junto com a sua dimensão

#### Scenario: A categoria de rendimentos com lançamentos é arquivada
- **WHEN** o usuário retira a categoria de rendimentos, não havendo conta declarada mas existindo rendimentos já lançados nela
- **THEN** ela é arquivada pelo guard de lançamentos, e as entries permanecem classificadas nela

#### Scenario: A interface oferece a ação que o domínio executará
- **WHEN** a categoria de rendimentos é exibida com a ação de retirá-la, havendo conta que declara render
- **THEN** a interface oferece arquivar, e não apagar, pela mesma derivação usada nas demais fachadas

#### Scenario: Arquivar a categoria não interrompe o rendimento
- **WHEN** a categoria de rendimentos é arquivada havendo contas que declaram render
- **THEN** essas contas continuam declarando rendimento, o lançamento de rendimento continua disponível nelas, e os rendimentos continuam classificados na categoria arquivada
