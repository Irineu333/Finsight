## Why

O backup local existe e funciona, e depende inteiramente de o usuário lembrar de fazê-lo. Quem
não lembrou não tem arquivo nenhum, e descobre isso no pior momento possível — depois de apagar
uma conta, depois de trocar de aparelho, depois de uma migração de schema.

Duas dessas três ameaças o usuário nem pode antecipar. Uma exclusão irreversível acontece num
toque, sem que a tela diga o tamanho do estrago (ver `DeleteFutureInvoiceUseCase`, que apaga toda
transação de uma fatura retroativa sob um texto que a chama de "fatura futura"). E uma migração de
schema reescreve o banco sozinha, na atualização do app, sem ação nem consentimento — é a única
ameaça em que o agressor é o próprio app, e a única contra a qual o usuário não tem defesa alguma
hoje.

## What Changes

**O app passa a manter cópias por conta própria** — o que ele hoje declara não fazer. Duas coisas
distintas nascem do mesmo mecanismo de captura, e a distinção entre elas é o eixo desta entrega:

- **O cofre**, para o usuário: nasce desligado, ele liga com um sim, escolhe onde as cópias ficam,
  vê o histórico e configura quantas guardar.
- **A rede**, para a operação: uma captura antes de o app rodar a cadeia de migrações. Não se liga
  nem se desliga, não se configura, não aparece no histórico. É o que permite desfazer uma
  operação que o app está prestes a fazer no banco do usuário — a mesma natureza de um rollback de
  transação, que também não pede permissão.

**Três gatilhos**, dois deles do cofre:

- **Periódico** — captura na primeira abertura depois de N dias (padrão 3). A formulação é essa e
  não "a cada N dias" porque nenhuma das três plataformas garante execução em segundo plano: o
  `BGTaskScheduler` do iOS declara que pode não executar a tarefa, e o Android suspende trabalho de
  fundo de apps hibernados.
- **Preventivo** — captura **antes** de uma ação destrutiva, nunca depois. Um backup posterior à
  exclusão registra o estado já mutilado e não devolve nada.
- **Pré-migração** — captura antes de a cadeia de migrações rodar, sempre, independente do cofre.

**Uma regra comum aos três**: só captura se algo foi **acrescentado** desde a última cópia.
Exclusões não criam necessidade de cópia nova — a cópia anterior é justamente a mais completa das
duas. É o que impede vinte arquivos para vinte exclusões seguidas, e dez arquivos idênticos de
quem abre o app todo dia sem lançar nada.

**Uma escada de dois degraus, com custo crescente e cobertura crescente**:

| | onde | cobre | não cobre |
|---|---|---|---|
| degrau 1 | armazenamento privado do app | exclusão acidental, corrupção do banco | desinstalação, limpar dados, perda do aparelho |
| degrau 2 | pasta apontada pelo usuário | \+ desinstalação, limpar dados | perda do aparelho, salvo pasta sincronizada |

O degrau 1 é o destino ao ligar o cofre, e no desktop ele já é o degrau 2 — `~/.finance/`
sobrevive à desinstalação por natureza. **A tela declara o que cada degrau não cobre**, porque um
cofre que morre junto com o app e não diz isso produz confiança sem lastro.

**O usuário aponta a pasta, e essa é a mesma máquina em três momentos**: configurar, reconectar
depois que o vínculo cair, e reencontrar o histórico após reinstalar. Os arquivos sobrevivem à
desinstalação; a permissão de acesso a eles não — nas duas plataformas móveis, por construção.

**O histórico é o que está na pasta**, lido no momento em que a tela abre, nunca uma tabela no
banco: uma tabela viajaria dentro do backup e uma restauração a faria voltar no tempo, passando a
mentir sobre a própria pasta.

**BREAKING — a promessa da tela muda em dois pontos.** O app deixa de declarar que não guarda
cópias por conta própria, e a restauração deixa de ser apresentada como irreversível quando o
preventivo está ligado. Ambos os requisitos hoje vigentes em `local-backup`.

**Não-objetivos**: cifrar o arquivo (o degrau 1 não expõe nada além do que o banco já expõe, e o
degrau 2 é escolha consciente do usuário); nuvem própria; mesclar acervos; e religar o backup
automático da plataforma — recusado por contradizer a privacidade por padrão que este desenho
estabelece, e registrado em `design.md` como pergunta aberta com o argumento preservado.

## Capabilities

### New Capabilities

- `automatic-backup`: o app mantendo cópias por conta própria — os dois gatilhos do cofre e a
  regra que decide se há o que capturar, os dois degraus de destino e o que cada um cobre, o
  vínculo com a pasta e o que fazer quando ele cai, o histórico lido da pasta, a retenção, a
  troca de pasta, e a obrigação de a tela dizer sempre quando foi o último backup que deu certo.

### Modified Capabilities

- `local-backup`: dois requisitos deixam de ser verdade. *"O app não delega backup à plataforma"*
  hoje afirma que o app não guarda cópias por conta própria e que a recuperação depende
  exclusivamente de arquivo exportado pelo usuário; passa a valer como caso base, com o cofre
  desligado. *"Restaurar substitui todo o acervo"* hoje exige que a operação seja apresentada
  como irreversível e proíbe a tela de sugerir recuperação; passa a distinguir a restauração com
  o preventivo ligado, que tem uma cópia do estado anterior, da restauração sem ele, que não tem.
- `database-snapshot`: ganha um requisito — o banco captura o próprio conteúdo antes de aplicar a
  cadeia de migrações, e essa cópia não é do produto nem depende de configuração dele. Cabe aqui
  e não em `automatic-backup` porque é o banco se protegendo de uma operação sua, no vocabulário
  do módulo que já não conhece a palavra "backup".

## Impact

**Módulos tocados**

- `feature/backup/impl` — o grosso: o destino persistido e suas três implementações de plataforma,
  os dois gatilhos do cofre, o histórico, a retenção, a tela e seus estados de saúde.
- `feature/backup/api` — o contrato pelo qual outras features pedem uma captura preventiva sem
  conhecer a implementação.
- `core/database` — a captura pré-migração, dentro do mecanismo que já existe (`captureInto`).
- `core/ledger` — `TransactionRepository.deleteTransactionById` e `deleteTransactionsByIds` ganham
  o gancho preventivo; ele fica **acima** de `useWriterConnection`, porque `VACUUM INTO` recusa
  rodar dentro de transação.
- `feature/settings/impl`, `feature/creditcards/impl` — os pontos das outras ações destrutivas
  cobertas (exclusão de moeda, de cotação).
- `core/resources` — chaves novas em `values/` e `values-en/`, ambas no mesmo commit.

**O que não é tocado**

O mecanismo de captura, verificação e substituição de `:core:database` serve inteiro como está —
`VACUUM INTO` já produz o arquivo autossuficiente que um cofre precisa. Nenhuma migração de schema
é necessária: o cofre não guarda nada no banco, por decisão (o histórico é a pasta) e as
preferências vão para `multiplatform-settings`, como toda preferência local deste app.

**Dependências novas**: nenhuma biblioteca. O acesso a pasta é `ActivityResultContracts.OpenDocumentTree`
no Android (irmão do `CreateDocument` já em uso) e `UIDocumentPickerViewController` com
`UTTypeFolder` no iOS (o mesmo controlador já usado, com outro tipo).

**Duas incertezas de plataforma** entram como spikes obrigatórios do degrau 2, com critério de
aceitação próprio, e nenhuma delas bloqueia o degrau 1: se o bookmark de pasta do iOS sobrevive a
reboot, e se uma subpasta de `Download` é selecionável no Android.
