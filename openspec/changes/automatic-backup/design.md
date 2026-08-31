## Context

O backup local está pronto e testado: `VACUUM INTO` produz um arquivo autossuficiente
(`DatabaseCapture.kt`), o `CandidateVerifier` reprova um arquivo inválido antes de tocar o banco em
uso, e `replaceContentFrom` troca o conteúdo numa transação sem derrubar quem observa. Nada disso
muda aqui. **O que falta é tudo o que está em volta**: um destino que sobreviva à sessão, uma razão
para capturar sem alguém pedir, e uma tela que diga a verdade sobre o que está guardado onde.

Três fatos do código de hoje moldam a entrega:

1. **Não existe destino persistido.** O `BackupFileService` tem quatro operações
   (`BackupFileService.kt:37,43,57,71`) e as duas que alcançam o mundo externo abrem um seletor.
   A interface inteira pressupõe um humano na frente.
2. **Não existe relógio nem ciclo de vida.** Grep por `ProcessLifecycleOwner`,
   `LifecycleEventObserver`, `ON_START`/`ON_STOP` em `app/`, `feature/` e `core/`: zero ocorrências.
   O único precedente de estado de manutenção persistido é `RateSyncStateRepository`, e a
   sincronização que ele acompanha é disparada por botão.
3. **O nome de arquivo colide.** `backupFileName` tem granularidade de dia
   (`BackupFileName.kt:17`). Hoje é inofensivo porque o seletor pergunta ao usuário sobre
   substituir; um cofre não tem a quem perguntar.

E um fato do domínio que reduz o escopo do gatilho preventivo pela metade: **o app já se defende**.
`DeleteAccountUseCaseImpl.kt:24-27` recusa apagar conta com entries — o KDoc é explícito, *"the
guard, not merely a hint to the UI"* — e `ResolveCategoryRetirabilityUseCase.kt:30-33` devolve
`MustArchive` para categoria com lançamento, orçamento, recorrente ou conta rendendo. O mesmo em
cartão e recorrente. Exclusão de fachada só é oferecida quando não destrói nada, e arquivar é um
`UPDATE isArchived` com inverso implementado nos cinco casos.

## Goals / Non-Goals

**Goals**

- O app mantém cópias sem que o usuário lembre, quando o usuário liga isso.
- O usuário aponta onde as cópias ficam, e o app volta lá sem pedir nada de novo.
- Uma ação destrutiva deixa de ser irreversível — porque a cópia é feita **antes** dela.
- Uma atualização que reescreva o banco deixa de ser irreversível, para quem ligou o cofre.
- A tela diz sempre quando foi o último backup que deu certo, e o que o degrau escolhido não cobre.

**Non-Goals**

- Cifrar ou proteger o arquivo por senha. Non-goal do design de `local-backup`, mantido: o degrau 1
  não expõe nada além do que o banco já expõe, e o degrau 2 é uma pasta que o usuário escolheu.
- Nuvem própria. O container iCloud do app foi avaliado e recusado (D3).
- Backup em segundo plano de verdade — `BGTaskScheduler`, `WorkManager` periódico (D5).
- Mesclar acervos, exportar planilha, restaurar seletivamente. Herdados de `local-backup`.
- Religar o backup automático da plataforma (D14, e Q3).

## Decisions

### D1 — Tudo o que o cofre faz obedece ao interruptor, sem exceção

Os três gatilhos são do cofre e valem a partir do mesmo sim. Cofre desligado, o app não escreve
cópia alguma — nem antes de migrar.

A alternativa foi considerada e recusada: uma captura pré-migração **sempre** ligada, apresentada
como rede da operação e não como backup do usuário, com o argumento de que ninguém pede permissão
para poder fazer *rollback* de uma transação. Três coisas a derrubaram.

**A cópia invisível não protege ninguém.** Se ela não aparece no histórico, o usuário nunca a vê e
nunca a restaura — e o único cenário em que ela salva é justamente o que se descobre dias depois.
Torná-la visível a transformaria em backup, e a distinção que a justificava desapareceria.

**O Room reverte a cadeia inteira.** `RoomConnectionManager.configureDatabase` abre uma
`BEGIN EXCLUSIVE TRANSACTION`, chama `onMigrate` para todo o intervalo de versões, escreve o
`user_version` e só então dá `END TRANSACTION`; qualquer falha cai no `ROLLBACK`. Uma migração que
lança não deixa banco pela metade: os dados ficam intactos na versão antiga, e a versão seguinte do
app, com a migração corrigida, migra normalmente. A cópia não é necessária nesse cenário.

**Os invariantes já barram outra fatia.** As migrações encerram verificando o razão — o mesmo
código que `CandidateVerifier` reutiliza —, e uma migração que desequilibre o razão aborta, o que
dispara o `ROLLBACK` acima. Sobra a corrupção que conclui sem erro e não viola invariante — o caso
do `Migration12To13`, que reescreve a moeda de contrapartida de todas as cotações sem condição. É
um buraco real e estreito, e cobri-lo não vale uma exceção na única promessa que o produto faz.

Consequência na fala: `local-backup` deixa de prometer que o app não guarda cópias por conta
própria, e passa a dizer que **não guarda enquanto o cofre está desligado**. Uma frase, sem
ressalva.

### D2 — O destino é um objeto opaco de plataforma, nunca um caminho de texto

Esta é a decisão que mais custa se for tomada errada, e ela é forçada pelo iOS: uma URL
*security-scoped* carrega a permissão **dentro do objeto**, e a Apple documenta que converter para
texto e de volta destrói o escopo. Um destino modelado como `String` não é inconveniente no iOS —
é impossível.

Então `BackupDestination` é uma interface de `commonMain` com três operações — colocar um arquivo
já capturado, listar o que há, remover um — e três `actual` que guardam o que cada plataforma
precisa guardar: um tree `Uri` no Android, um bookmark resolvido em `NSURL` no iOS, um caminho no
desktop. **Nenhuma delas devolve caminho para quem chama.**

O que continua sendo `String` é o arquivo temporário do próprio app, e está certo: `VACUUM INTO` só
sabe escrever num caminho, e o fluxo já é *capturar no temporário → entregar ao destino → apagar o
temporário* (`BackupViewModel.captureInto`). A arquitetura atual não trava nada; só o destino muda
de natureza.

*Alternativa recusada:* um `String` com um esquema (`saf://`, `bookmark://`). Reintroduz o
round-trip exatamente onde ele é proibido.

### D3 — Dois degraus, e o degrau 1 é o destino ao ligar

O degrau 1 é o armazenamento privado do app; o degrau 2 é a pasta que o usuário aponta. O degrau 1
não sobrevive à desinstalação em nenhuma plataforma móvel — `getExternalFilesDir()` é documentado
como removido na desinstalação, e o sandbox do iOS é apagado com o app. O que sobrevive no iOS
sobrevive **por estar fora do sandbox**, não por exceção dentro dele — o container iCloud do app
(recusado adiante), o banco privado do CloudKit, os containers de *app group* —, e o keychain não
entra nessa conta como garantia: a Apple nunca o prometeu como contrato, e Quinn descreve a
sobrevivência como *"an artefact of the implementation rather than a designed-in feature"*. Nada do
cofre se apoia nisso. No desktop os dois degraus coincidem: `~/.finance/`
(`Database.jvm.kt:12`) sobrevive por natureza.

Isso é escada de proteção com custo crescente, e a tela nomeia os degraus. O ganho de escopo é o
argumento decisivo: **o produto inteiro — os três gatilhos, a regra da cópia, o histórico, a
retenção e a tela — cabe no degrau 1**, onde o destino é trivial, nada pode ser revogado e tudo é
testável em `jvmTest`. O degrau 2 é uma implementação de `BackupDestination`, não uma reescrita —
desde que D2 valha desde o primeiro dia.

*Alternativas recusadas, com evidência:* `MediaStore.Downloads` — o arquivo sobrevive mas a posse
não, e o app reinstalado precisa de diálogo do sistema por operação para apagar o que ele mesmo
criou; em Android 13+ com `targetSdk 36` não existe permissão que devolva acesso a um `.db`, porque
as granulares cobrem imagem, vídeo e áudio. `MANAGE_EXTERNAL_STORAGE` — a política do Google exige
*core functionality* ("sem a qual o app está quebrado"), que um app de finanças não satisfaz.

**Container iCloud do app — recusado, e vale registrar por quê, porque o mecanismo funciona.** Ele
alcança o objetivo do degrau 2 sem seletor, sem bookmark e sem a pergunta do reboot:
`URLForUbiquityContainerIdentifier:` (`NSFileManager.h:315`, `ios(5.0)`) devolve a raiz do container
a partir do identificador, e é a primeira chamada que faz o sistema estender o sandbox do app para
incluí-lo; publicando `Documents/` com `NSUbiquitousContainerIsDocumentScopePublic`, as cópias
aparecem para a pessoa no app Arquivos. Não há vínculo a cair porque não há vínculo — há um
identificador, e ele encontra a pasta.

O que o recusa é o preço somado à incógnita. **O preço:** três entitlements; conta **paga** de
desenvolvedor, porque o nível gratuito não carrega a capability; perfil de provisionamento regerado,
porque um App ID modificado invalida os perfis existentes; os 5 GB de iCloud da própria pessoa,
disputados com o resto do aparelho dela; e a pessoa logada, com o interruptor do app ligado. Contra
isso, um projeto que hoje **não tem arquivo de entitlements algum** — `iosApp/project.yml` não
declara nenhum, e `iosApp/iosApp/Info.plist` não traz `UIFileSharingEnabled` nem
`LSSupportsOpeningDocumentsInPlace`. Seria o primeiro degrau a custar dinheiro para existir. **E a
incógnita não some com o pagamento:** se os arquivos do container sobrevivem a apagar e reinstalar o
app não está documentado — nem que sobrevivem, nem que não —, de modo que ele não escapa da mesma
lacuna que o degrau 1 já tem. Por fim, ele tensiona o argumento com que D14 e D16 aceitam nuvem,
*"quem espalha é o usuário, escolhendo uma pasta externa"*: o container é a nuvem **do próprio app**,
não uma que a pessoa apontou. *Do que está acima, foram lidos na fonte o símbolo
(`NSFileManager.h:315`) e o estado do projeto; entitlements, conta paga e reemissão de perfil vêm de
relato, não de leitura direta da documentação.*

Se Q1 voltar negativa em aparelho real, esta recusa merece ser reexaminada — e o container é o
primeiro lugar a olhar.

### D4 — Apontar a pasta é uma máquina só, usada em três momentos

Configurar, reconectar depois que o vínculo cair, e reencontrar o histórico após reinstalar são a
mesma tela. Isso não é economia de código: é o reconhecimento de que **os arquivos sobrevivem e o
vínculo não**, nas duas plataformas móveis, por construção — a permissão persistida do Android é
removida com o pacote, e o bookmark do iOS morre com o sandbox.

O terceiro momento é o de maior valor do produto inteiro: alguém acabou de perder tudo, num
aparelho novo. É por isso que "escolher a pasta", que chegou como requisito negociável, não é
negociável — sem essa máquina, os arquivos sobrevivem e ninguém os encontra.

O app escreve as cópias direto na pasta apontada, sem subpasta alguma no caminho. Reencontrar o
acervo depende de duas coisas só: apontar de novo para a mesma pasta, e reconhecer o que está nela
pelo nome (`isBackupFileName`, design `local-backup`) — é essa dupla que sustenta os três momentos,
e uma pasta própria dentro da escolhida não somaria nada a ela.

A retenção continua sem chegar perto de um arquivo do usuário, e sem subpasta para se esconder
atrás disso: o nome primeiro reduz a listagem a um punhado de candidatos, e antes de qualquer
remoção o conteúdo tem que se provar um banco deste app, pelo mesmo verificador que o fluxo de
restauração já usa (`CandidateVerifier`, via `OwnCopyCheck`). O que essa leitura prova é que o
arquivo é um banco do **schema** deste app — nunca que foi **esta instalação** que o escreveu, e
não precisa provar isso: um arquivo trazido de outra instalação que apontou a mesma pasta, ou
importado por um `ArchiveImport`, passa pelo mesmo gate e é tratado como cópia igual. O que o gate
recusa é exatamente o que não é um banco deste app, e um arquivo do usuário nunca chega a essa
prova, esteja a pasta cheia de outras coisas ou vazia.

*Alternativa recusada: uma subpasta própria dentro da escolhida.* Nenhum dos dois motivos que a
sustentariam se confirma. Reencontrar o acervo não precisa dela — o nome já é a máquina inteira, e
uma pasta apontada de novo entrega o mesmo resultado com ou sem uma pasta a mais no caminho. E a
retenção não precisa dela — o gate por conteúdo barra um arquivo do usuário do mesmo jeito, com ou
sem uma pasta só do app para se retirar para dentro. Sobra não poluir uma pasta usada para outra
coisa, e isso deixou de ser problema do app para resolver por conta própria: escolher uma pasta que
não está em uso para mais nada é trabalho de quem escolhe, como é em qualquer outro app com o mesmo
tipo de destino. O que a subpasta comprava e fica sem substituto é listagem mais barata — uma
consulta sobre um punhado de arquivos vira uma consulta com filtro sobre uma pasta que pode ter
mais. E, no desktop, uma pasta que o app mesmo criava era prova de que a pasta apontada existia de
verdade: `java.io.File.isDirectory` não distingue uma pasta vazia de um ponto de montagem local que
sobrou de um volume de rede desconectado, e a subpasta distinguia. Sem ela, o vínculo pode ler
`LINKED` e uma captura pode aterrissar no stub — nada é apagado, e as cópias reais seguem no volume
que as guarda, mas alguém pode se achar protegido por um arquivo escrito no lugar errado. Esses
dois custos são o que a subpasta comprava, e juntos não a sustentavam.

Qual pasta o seletor abre primeiro é conveniência, não correção. No Android, uma subpasta de
`Download` serve tão bem quanto `Documents/`: o que o seletor deixa escolher, ele deixa usar, e
nenhum destino aceito falha em silêncio depois (Q2). Sugerir uma pasta poupa quem esbarraria no
bloqueio do `Download` em si — não é o que separa funcionar de não funcionar.

### D5 — O periódico roda na abertura; nada em segundo plano

A promessa é *"na primeira abertura depois de N dias"*, e não *"a cada N dias"*, porque a segunda é
uma frase que o app não pode cumprir: o `BGTaskScheduler` do iOS declara que a data pedida só
garante que a tarefa **não** começará antes, e a Apple recomenda não pedir mais de uma semana
porque *"podemos escolher não executar sua tarefa"*; no Android, um app hibernado por meses deixa
de executar trabalho de fundo, em silêncio.

Capturar na abertura tem uma propriedade boa: reflete o fim da sessão anterior, que já terminou.

O padrão é **3 dias**. A escolha de frequência alta com histórico curto vem de uma consequência da
spec, não de gosto: restaurar é tudo-ou-nada (`local-backup` proíbe mesclar), então um backup de
três semanas atrás custa três semanas de lançamentos e ninguém o usará. **O valor de um backup
decai rápido**, e isso derruba retenção escalonada e validade em meses.

### D6 — O preventivo é antes, e o gancho fica acima da transação

Um backup posterior à exclusão registra o estado já mutilado. Só o anterior devolve o que foi
apagado.

Onde instalar é decidido por uma restrição do mecanismo: **`VACUUM INTO` recusa rodar dentro de uma
transação** (documentado e classificado em `DatabaseCapture.kt:110-116`), e a remoção acontece
dentro de `immediateTransaction`. O gancho fica nos métodos públicos —
`TransactionRepository.deleteTransactionById` e `deleteTransactionsByIds` — antes do
`useWriterConnection`.

Isso também descarta os dois candidatos que o nome sugeriria. `TransactionRemovalHook` roda
**depois** da remoção e recebe a transação já removida — é hook de correção, não de prevenção. E
`LedgerEntryWriter` não participa de remoção alguma: `removeRow` chama o DAO direto e as entries
somem por cascata.

Não existe ponto único para as demais. São três territórios, e as seis ações que importam (D7) se
distribuem em quatro lugares: os dois métodos do `TransactionRepository` (cobrem exclusão de
transação, de parcelamento e de fatura), a exclusão de moeda, a remoção de cotação, e o
`BackupViewModel` antes de `replaceContentFrom`.

### D7 — A classificação vive no domínio; a tela tem um interruptor

A regra do repositório decide isto. `CLAUDE.md`: *"A consumer decides **whether** it applies a rule
— never **which** rule it is."* Uma configuração por classificação faria a tela escolher **qual**
regra vale; um interruptor faz a tela escolher **se** ela vale.

A evidência confirma que a configuração não teria consequência: as cinco exclusões de fachada são
inofensivas por guarda de domínio, então as duas opções produziriam o mesmo resultado para
praticamente todo mundo.

Das treze ações destrutivas inventariadas, o preventivo cobre as seis que destroem trabalho que o
usuário não redigita: **restaurar backup** (o acervo), **excluir parcelamento** (N transações),
**excluir fatura** (N transações reais quando `RETROACTIVE` — ver Riscos), **excluir transação**,
**excluir moeda** (N cotações observadas) e **remover cotação**. Ficam de fora as cinco de fachada
(as guardas bastam), **editar transação** (reescreve as entries anteriores, mas é frequente demais
para justificar captura a cada edição) e **o ajuste que zera** (o valor anterior é derivado).

Uma exclusão nova nasce dentro de uma classe e é protegida sem que ninguém altere a tela.

### D8 — Uma cópia serve enquanto nada foi acrescentado depois dela

A pré-condição é a mesma para os três gatilhos, e não é tempo: **exclusões não criam necessidade de
cópia nova** — a cópia anterior é justamente a mais completa das duas. Só inclusões e edições
criam.

```
apaga · apaga · apaga              → 1 cópia
apaga · lança 3 · apaga            → 2 cópias (a 2ª protege as 3 novas)
abre o app 10 dias sem lançar nada → 0 cópias novas
```

Isso resolve três coisas de uma vez: vinte exclusões seguidas não produzem vinte arquivos; a
exclusão de fatura, que apaga transação a transação em laço, captura uma vez; e quem abre o app
todo dia sem lançar nada não acumula arquivos idênticos. E torna a abrangência do preventivo
barata, o que sustenta D7.

*Plano B, se distinguir inclusão de exclusão sair caro:* a condição conservadora *"houve qualquer
escrita desde a última cópia"* nunca deixa buraco e só custa arquivos a mais no caso de exclusões em
sequência.

### D9 — O histórico é a pasta; o `snapshot_meta` é a verdade, o nome é dica

O histórico **não** é uma tabela. Uma tabela viajaria dentro do arquivo — o backup contém todo o
acervo — e uma restauração a faria voltar no tempo, passando a mentir sobre a própria pasta. Ela
também nunca saberia que o usuário apagou arquivos pelo gerenciador de arquivos, e uma pasta que
sobrevive à desinstalação é, por definição, uma pasta que o usuário enxerga e mexe.

O nome ganha data e hora, para unicidade e ordenação. Mas **o nome não é autoridade**: no Android,
`DocumentsProvider.createDocument` pode alterar o nome pedido para evitar conflito, e o app pode
receber `… (1).db`. A verdade sobre um arquivo está no `snapshot_meta` dentro dele, que é lido no
momento em que o usuário toca um item para restaurar — que é quando a confirmação já precisa
daqueles dados de qualquer forma. A lista mostra o que o sistema de arquivos entrega: nome, data,
tamanho.

A retenção usa o nome como filtro barato e **confirma pelo conteúdo antes de apagar**, com o mesmo
verificador do fluxo de restauração — o mesmo gate que D4 descreve. O que ele prova é que o arquivo
é um banco do schema deste app, nunca que foi esta instalação que o escreveu; um arquivo do usuário
não chega a essa prova, esteja a pasta cheia de outras coisas ou não.

E uma listagem não é prova de ausência. No Android, a consulta pelos filhos de uma pasta pode
devolver um cursor não-nulo e vazio para uma pasta que existe e aceita escrita no instante seguinte
(Q2). O que decide não é o cursor vazio, e sim **se a pasta já foi confirmada alcançável antes de
perguntar pelos filhos dela** — é o que `FolderLink` responde, e é ele que corre primeiro em cada
operação. Zero itens depois disso é ausência, e a tela mostra o estado vazio. Uma pasta apagada,
renomeada ou desmontada não sobrevive a essa confirmação — ela some, o vínculo cai, e toda operação
recusa antes de perguntar por um filho, sem custar o único estado que a confirmação não sabe
distinguir de um cursor legítimo: uma pasta que a pessoa acabou de apontar, vazia porque nada foi
capturado nela ainda. **Nada que apague ou crie faz parte de uma listagem tomada como completa.**

O gatilho de origem não é persistido nesta entrega. O `formatVersion` do `snapshot_meta` existe
justamente para permitir acrescentá-lo depois sem falhar de modo obscuro.

### D10 — Retenção por contagem, e o mais recente nunca vence

Contagem, não idade: o espaço fica previsível, nunca chega a zero por construção, e é o que a
pessoa quer dizer quando pede que backups parem de acumular.

**O limite é o mesmo nos dois degraus: 5 · 10 · 20 · tudo, padrão 20.** O espaço é do usuário nos
dois casos, e um limite que a tela mostra é um limite que ela deixa escolher. "Tudo" transforma a
retenção em algo que ele desliga em vez de sofrer.

A limpeza roda **depois de uma captura bem-sucedida**, nunca na abertura por conta própria: assim
ela está sempre ancorada num momento em que provadamente existe uma cópia nova, e o usuário nunca
fica com zero.

A cópia anterior a uma migração **não entra na contagem** e é substituída apenas pela próxima
migração. Se entrasse, três capturas periódicas depois ela sumiria — justamente no cenário em que
ela é a única coisa que salva: a migração concluiu sem erro técnico e escreveu dado errado, e isso
se descobre dias depois.

### D11 — A captura anterior à migração vive em `:core:database`, e recebe o destino de fora

O mecanismo é do banco, no vocabulário do módulo que já não conhece a palavra "backup" (D7 do
design de `local-backup`, que continua valendo).

A ordem importa: a captura precisa acontecer **antes** de o Room abrir o banco e aplicar as
migrações — abrir para descobrir já teria migrado. A versão gravada no arquivo é lida sem Room, com
uma conexão descartável do driver, exatamente como o `CandidateVerifier` já faz com um candidato.
Se a versão do arquivo for menor que a do app, captura; senão, não há o que proteger.

**O destino vem de fora, e é assim que o cofre governa sem ser conhecido.** `:core:database` não
tem API de arquivo e não vai ganhar uma — a decisão D7 de `local-backup` é explícita, inclusive na
parte em que remover o arquivo é de quem escolheu o caminho. Ele recebe, ou não recebe, um caminho
onde escrever antes de migrar: **um caminho, e captura; nenhum, e não captura**. Quem monta o banco
é quem consulta a preferência do cofre e decide o que passar, e o módulo continua sem saber que
existe cofre.

É o que torna D1 implementável sem que `:core:database` leia configuração de produto alguma, e sem
uma segunda condição espalhada.

### D12 — Quando o vínculo cair, avisa, oferece, e não deixa buraco

O vínculo **vai** cair, e isso não é defeito: no iOS o usuário pode revogar o acesso em
*Ajustes › Privacidade › Arquivos e Pastas*, e nas duas plataformas mover ou apagar a pasta o
invalida. Corre também o relato de que um update maior do sistema expira bookmarks — de que a Apple
teria force-expirado todos os bookmarks no iOS 14 e fechado o caso como "funcionando conforme
esperado". *Esse precedente está sem fonte rastreável: ninguém o releu na origem, e ele não deve
sustentar sozinho nenhuma decisão nova.* O que sustenta esta decisão são a revogação e o caminho da
pasta, que não dependem dele.

Somado ao fato de que trabalho de fundo pode parar em silêncio, isso reposiciona a tela: **o
elemento mais importante dela não é o interruptor, é a linha que diz quando foi o último backup
bem-sucedido**. É o único mecanismo pelo qual a pessoa descobre que a proteção parou. Um app que mostra
"ativado" enquanto não grava nada há sete meses é pior do que um app sem cofre, porque produz
confiança sem lastro.

O comportamento: o app **verifica o vínculo na abertura**, não só na hora de gravar, para não
descobrir a queda dias depois. Ao cair, ele **avisa e oferece** — *reconectar a pasta* ou *manter
dentro do app* — e, enquanto a decisão não vem, grava no degrau 1 **provisoriamente**, dizendo que
é provisório. A pergunta continua sendo feita; ninguém fica desprotegido esperando ler um aviso.

### D13 — Trocar de pasta copia, não move

Migrar é copiar para o destino novo e **deixar a origem intacta**. Se falhar no meio, os arquivos
estão nos dois lugares: o pior caso é duplicata, nunca perda. Apagar a origem depois de copiar
transformaria qualquer falha num jeito de perder o histórico.

Migra apenas os N mais recentes que a retenção do destino comporta — copiar quarenta para
apagar vinte em seguida é tráfego jogado fora.

E o fluxo de troca **funciona sem acesso à origem**, porque o caso mais comum a longo prazo é
justamente trocar de pasta *porque* o acesso à anterior caiu (D12).

### D14 — O backup automático da plataforma continua desligado

Auto Backup no Android e backup do iCloud continuam recusados, e `PlatformBackupIsOffTest`
continua valendo como está.

O argumento técnico original **não se aplica** ao arquivo capturado, e isso precisa ficar
registrado: o motivo de excluir o banco é que em WAL ele são três arquivos copiados sem coordenação
transacional, e *"o que volta pode não somar um banco"* (`Database.ios.kt:37-42`). Um arquivo de
`VACUUM INTO` é um arquivo só, consistente e autossuficiente — exatamente o que se pode copiar
assim.

O que sustenta a recusa é outro argumento, e é de postura: com o cofre desligado por padrão e o
degrau 1 no armazenamento privado, o app não espalha dados financeiros sozinho — quem espalha é o
usuário, escolhendo uma pasta externa. Religar o Auto Backup faria todo backup do degrau 1 subir
para a conta Google **por padrão, sem ninguém decidir**, que é precisamente o que este desenho
evita e o que `local-backup` lista como não-objetivo.

O custo da recusa é concreto e mensurável, e está nos Riscos. Se um dia for reaberta, é como
opt-in explícito — nunca como padrão (Q3).

### D15 — O histórico é uma tela, não uma seção da tela de backup

A tela de backup é de configuração — tiles, interruptores, escolhas. Um histórico é uma lista de
conteúdo, e o app tem um padrão próprio para isso: `LazyColumn` com itens keyed e `animateItem()`,
cabeçalho de data em 18.sp Bold, estado vazio próprio. Misturar os dois aperta os dois.

Três coisas decidem, e nenhuma é estética. A lista **cresce com a retenção configurada** — vinte
itens, ou todos, se o usuário escolher não remover nada. Cada item **tem ações** — restaurar,
entregar a um destino, remover — e ações por item numa tela de configuração viram menus escondidos.
E é a tela do **reencontro**: depois de reinstalar e reapontar a pasta, o que a pessoa faz ali é
escolher entre quarenta cópias, não ler configurações.

**A tela responde duas perguntas, não uma: onde as cópias estão, e o que fazer para haver mais.**
O cabeçalho nomeia o destino em vigor e abre a escolha entre os dois degraus (D3); ao lado da
lista, capturar agora e importar um arquivo são as duas portas por onde uma cópia entra nela.
Nenhuma das duas é uma configuração do cofre — a tela de backup guarda o interruptor, os gatilhos,
e as operações que saem do app por um seletor — e as duas pertencem à mesma pergunta que a lista
responde: o que há no destino, agora.

A rota é **interna ao `impl`**, e o subgrafo já estava esperando: `BackupGraph` declara-se como *"the
node the backup destinations hang from… even though the feature starts with a single screen"*. A
convenção do projeto reserva a `api` para as rotas externamente navegáveis, e ninguém fora do backup
precisa navegar direto para o histórico.

Uma consequência que a tela paga sozinha: **entregar uma cópia guardada a um destino**, pelo mesmo
caminho da exportação manual e sem capturar de novo. É o que dá saída ao usuário de Android quando o
seletor de pastas não oferece raiz sincronizada alguma (D16) — a cópia já existe, e só precisa de um
jeito de sair do aparelho.

### D16 — O app aceita a pasta que a pessoa escolher, e não julga o provedor

O destino do degrau 2 não é filtrado pelo app: ele recebe do seletor a árvore que a pessoa apontou e
a usa. Não pergunta de quem é a raiz, não separa nuvem de local, não recusa.

O sinal em que um bloqueio se apoiaria não serve. O DocumentsUI oferece as raízes que declaram
`Root.FLAG_SUPPORTS_IS_CHILD`, e essa bandeira é declarada **por cada provedor sobre si mesmo** (Q2)
— erra nos dois sentidos: uma raiz de nuvem pode aparecer, e uma pasta local pode não aparecer. Um
bloqueio construído sobre ela trocaria uma frase verdadeira e incerta por uma frase falsa e segura.

**D14 sustenta isto, não o contrário.** O que aquela decisão recusa é subir dados *por padrão, sem
ninguém decidir*; o caminho que ela nomeia como aceitável é o oposto — *"quem espalha é o usuário,
escolhendo uma pasta externa"*. Uma pasta escolhida no seletor é exatamente esse opt-in explícito.

E é a razão de existir do degrau 2. O backup de um app de finanças existe para o aparelho perdido,
roubado ou quebrado, e uma pasta sincronizada é o único destino do degrau 2 que cobre isso. Recusá-la
esvaziaria o próprio degrau.

Na tela isso não acrescenta frase alguma, e retira uma: a cobertura é dita por destino, não por
plataforma, e a do degrau 2 já diz que cobrir a perda do aparelho depende de a pasta ser sincronizada
por algum serviço (`backup_coverage_folder`). Não há ramo por plataforma a escrever, e não há
promessa de que o cofre seja local a manter.

*Direção, não decisão:* no momento da escolha o sistema sabe qual provedor a pessoa abriu, e sabe
dizer o nome dele. Mostrar esse nome é honesto e barato, onde o app adivinhar "local ou nuvem" erra
nos dois sentidos. Ninguém construiu isso.

## Risks / Trade-offs

- **No Android, cobrir a perda do aparelho depende do que o seletor de pastas oferecer, e isso varia
  por aparelho.** O DocumentsUI lista as raízes que declaram `Root.FLAG_SUPPORTS_IS_CHILD`, bandeira
  que cada provedor declara sobre si — medido com um provedor escrito para a medição, que apareceu,
  foi selecionável e teve o tree URI persistido (Q2). Proton Drive, MEGA e ownCloud não a declaram,
  confirmado no código-fonte; Nextcloud, Seafile e wrappers rclone aparecem, mas exigem um app extra
  fora da Play Store; de Google Drive, OneDrive e Dropbox não há leitura direta — no aparelho medido
  nenhum dos três contribuiu raiz, o Drive por não ter conta conectada.
  → Fora do alcance do app, que aceita a pasta apontada sem julgar a origem dela (D16). Onde nenhuma
  raiz sincronizada for oferecida, o degrau 2 guarda no próprio aparelho — e é isso, e não uma
  promessa, que a frase de cobertura diz.
- **A cobertura pode diferir entre plataformas**, o que `local-backup` evitou de propósito
  (D11 daquele design). No iOS o iCloud Drive aparece no seletor de pasta; no Android o que aparece
  depende dos provedores instalados, e os de maior alcance podem não aparecer.
  → Trade-off aceito, e a tela não o traduz em fala por plataforma: uma frase por destino, que
  descreve a pasta escolhida em vez de prometer o que a plataforma não garante (D16).
- **`DeleteFutureInvoiceUseCase` apaga transações reais sob um texto que chama a fatura de
  "futura".** Verificado: `Invoice.Status.isDeletable` inclui `RETROACTIVE` (`Invoice.kt:85-87`),
  o use case apaga toda transação da dimensão da fatura (`:28-32`), e a confirmação não diz
  quantas (`strings.xml:448`), ao contrário do modal de parcelamento.
  → É a ação onde o preventivo mais faz falta, e o preventivo a cobre. **A correção do texto é
  outra entrega** — não se conserta uma mentira de UI dentro de uma proposta de backup.
- **Remover cotação não pede confirmação nenhuma** — apaga no toque
  (`ExchangeRateFormModal.kt:348-349`). É a única exclusão de dado do usuário sem confirmação no
  app, e está entre as seis do preventivo.
  → Idem: coberta aqui, corrigida noutra entrega.
- **O bookmark do iOS sobreviveu a um reboot num aparelho real, e a documentação continua sem dizer
  nada, em nenhuma direção.** A Apple documenta persistência entre *lançamentos* do app e não afirma
  nem nega nada sobre reinício do aparelho; a única frase que fala em reboot é um teto sobre o
  escopo implícito, não uma previsão de duração (Q1). A medição cobriu um aparelho, uma vez, pelo
  caminho de resolução que a implementação usa — o outro lado do par não foi exercitado, e aparelho,
  versão de iOS e provedor da pasta não foram registrados. Um caso já se sabe negativo — pasta em
  volume externo ou removível, que quebra na remontagem por bug reconhecido da Apple —, e ele cai
  dentro do desenho aceito (D16), não contra ele.
  → Q1 fechada: o critério que pararia a entrega não disparou, e o degrau 2 no iOS segue como
  desenhado. Um aparelho respondendo uma vez não vira garantia de plataforma.
- **Uma pasta do iCloud Drive com cópias evictadas pode esconder o próprio histórico do app.** O iOS
  pode representar localmente um arquivo cuja cópia foi liberada como um *placeholder* oculto,
  `.nome-do-arquivo.icloud`; a listagem do degrau 2 no iOS pula arquivos ocultos
  (`NSDirectoryEnumerationSkipsHiddenFiles`, `IosFolderBackupDestination.kt:140`) e o nome do
  placeholder também não passaria pelo filtro (`isBackupFileName` exige o prefixo
  `finsight-backup-`, `BackupFileName.kt:53-54`). Não medido.
  → É o modo de falha mais provável do degrau 2 no iOS — mais provável que o reboot —, e é a leitura
  que D9 proíbe: "sem cópias" sobre um acervo que está lá, só que evictado. Fica registrado como
  risco em aberto contra o degrau da pasta.
- **O gatilho preventivo entra no caminho crítico de uma exclusão.** `VACUUM INTO` precisa de
  espaço livre da ordem do tamanho do banco, e a captura acontece antes de o botão responder.
  → D8 reduz a frequência real a quase nada. Uma captura que falha por falta de espaço precisa
  decidir se deixa a exclusão prosseguir — decisão de produto, registrada como requisito na spec.
- **A tela de backup deixa de ser uma tela de configuração** e passa a ser um painel de saúde, com
  estados que não existiam (vínculo caído, provisório, último backup envelhecendo).
  → É o preço de um cofre que pode parar em silêncio, e é o que impede confiança sem lastro.
- **`DocumentFile` custa 1 + 3N queries** por listagem, e o próprio javadoc recomenda
  `DocumentsContract` direto.
  → A listagem usa `buildChildDocumentsUriUsingTree` com projeção completa, uma consulta.
- **Um vazamento de escopo no iOS derruba o sandbox do app até o relaunch**, se um
  `startAccessingSecurityScopedResource` não for balanceado.
  → O padrão já existe no `IosBackupFileService` (`NonCancellable` + `finally`) e é o que o
  destino segue.

## Migration Plan

Não há migração de schema: nenhuma entidade é acrescentada e o histórico não vive no banco (D9).

O que muda para quem já usa o app:

1. Nada, até que o usuário ligue o cofre. Ele nasce desligado, e nenhum dos três gatilhos escreve
   coisa alguma antes disso.
2. Ligado o cofre, a primeira atualização que trouxer migração passa a ser precedida de uma cópia —
   uma só, fora da contagem da retenção, substituída pela migração seguinte.
3. A tela de backup ganha o cofre e a linha do último backup, e reescreve as duas frases que
   deixaram de ser verdade.

**Rollback**: desligar o cofre para de capturar e não apaga nada; os arquivos já escritos continuam
sendo bancos SQLite válidos e restauráveis pelo fluxo manual, que não muda. Reverter a mudança
inteira deixa os arquivos onde estão, restauráveis um a um pelo seletor.

**Ordem de entrega.** O degrau 1 é a primeira entrega inteira — os três gatilhos, a regra da cópia,
o histórico, a retenção e a tela — e não depende de nenhuma das perguntas abertas. O degrau 2 é a
segunda, e começa pelos dois spikes.

## Open Questions

### Q1 — O bookmark de pasta do iOS sobrevive a reboot?

**O que se mede é a resolução, não a criação.** No iOS não existem duas variantes de criação a
comparar: `NSURLBookmarkCreationWithSecurityScope` é `API_UNAVAILABLE(ios, watchos, tvos)`
(`NSURL.h:425`, SDK do iOS 18.5) — não é uma opção que o iOS ofereça e descarte às vezes, é um
símbolo que não existe fora do macOS. A única opção de criação que o iOS aceita e que mexe em escopo
é `NSURLBookmarkCreationWithoutImplicitSecurityScope` (`NSURL.h:427`, `ios(5.0)`), e ela **suprime**
o escopo implícito: passá-la é garantir a falha, não medi-la. O eixo real está do outro lado.
`NSURLBookmarkResolutionWithoutImplicitStartAccessing` (`NSURL.h:434`, `ios(14.2)`) existe porque
resolver no iOS **inicia o acesso implicitamente**, a menos que a opção seja passada — o próprio
cabeçalho chama o que é iniciado ali de *"the ephemeral security-scoped resource"*. É essa opção que
a medição varia, e é ela que decide também se o par `start`/`stop` em volta do uso está balanceado
ou está parando um acesso que nunca abriu.

**A documentação não responde, em nenhuma direção.** A Apple documenta que o bookmark sobrevive a
*lançamentos* do app; sobre reinício do aparelho, não afirma nem nega. A única frase que alguém
tomaria por resposta é a da página de `withoutImplicitSecurityScope`, sobre o escopo implícito que
todo bookmark criado sem `.withSecurityScope` carrega automaticamente ali: *"Bookmarks that you
create without security scope automatically carry implicit ephemeral security scope. This security
scope is valid until reboot at the latest, and confers access to the resource to any other process
that resolves the bookmark."* Ela é um **teto** sobre uma conveniência transitória entre processos —
o assunto da página é impedir que outro processo ganhe acesso resolvendo um bookmark entregue a ele
—, e um teto não é uma previsão: dizer que algo não passa do reboot não é dizer que chega até lá.
Quem responde pela área na Apple trata a persistência como coisa a medir, não a ler. No fórum de
desenvolvedores (thread 797469, *"iOS folder bookmarks"*, ago/set de 2025), a instrução de Kevin
Elliott, engenheiro do DTS, é *"Make sure you test how things work after you reboot the device."*, e
sobre o escopo em si ele escreve que `NSURLBookmarkCreationWithSecurityScope` *"isn't defined on iOS
because the system doesn't really allow you to create bookmarks that DON'T have security scope, at
least not in the way macOS does."* — no iOS o bookmark carrega escopo por construção, e **quanto
tempo esse escopo dura é o que ninguém documentou**. A pergunta está genuinamente em aberto: nenhum
dos dois lados tem fonte, e a documentação não é prova de nenhum deles.

**Um caso já está fechado, e é negativo.** Bookmark para volume externo ou removível não sobrevive à
remontagem, e reiniciar remonta. No mesmo thread, Elliott atribui isso a um bug reconhecido — *"The
problem here is that due to a bug (r.102995804), NSURL isn't able to resolve bookmarks across volume
mounts."* —, com a consequência dita em seguida: *"bookmarks within the device work fine and
bookmarks to other volumes initially work... but then break completely once the volume has been
unmounted."* Isso não bloqueia nada. D16 já diz que o app não julga o provedor que a pessoa apontar,
de modo que esse modo de falha está **dentro** do desenho aceito, e é a razão de o caminho de vínculo
caído (D12) ser exercitado na prática, não só em teoria.

**Critério de aceitação**: escolher uma pasta, guardar o bookmark, reiniciar o aparelho, resolver e
escrever — **nas duas resoluções**, com e sem `NSURLBookmarkResolutionWithoutImplicitStartAccessing`,
porque a que inicia o acesso sozinha pode passar onde a outra falha, e é de saber qual das duas vale
que depende o par `start`/`stop` do destino. Se falhar, o degrau 2 no iOS precisa de outro desenho —
e o degrau 1 segue intacto.

**O simulador foi tentado antes, e não serve de atalho** — vale registrar por que, para que ninguém
gaste o esforço de novo: ele roda sem sandbox (uma sonda gravou na pasta do usuário do host e
enxergou os contêineres de dados de outros 133 apps), um controle negativo que devia falhar passou,
e `kern.boottime` lido de dentro do app é o do host, não o do dispositivo simulado — reiniciar o
simulador não é um reboot do ponto de vista do processo. O bookmark que ele produz é um bookmark de
macOS sobre o APFS do host, não uma medição de iOS.

**Medida num aparelho real, e sobreviveu — no caminho que a implementação usa.** O dono do produto
apontou o cofre para uma pasta no próprio iPhone, reiniciou o aparelho, e a resolução seguinte
funcionou: as cópias continuaram caindo na pasta escolhida depois do reboot. A medição correu pela
opção que `IosBackupFolder.resolve()` de fato passa, `NSURLBookmarkResolutionWithoutImplicitStartAccessing`
(`IosBackupFolder.kt:236`) — o lado do par que o código usa em produção, com `start`/`stop`
balanceados em volta de cada operação.

**O que essa medição não cobre.** O outro lado do par — resolver deixando o acesso implícito
iniciar sozinho, sem passar a opção — não foi exercitado; o critério pedia os dois. Versão do iOS,
modelo do aparelho e o provedor da pasta (iCloud Drive, armazenamento local do aparelho, ou um
terceiro) não foram relatados, e ficam como não registrados aqui — não há por que supor qual foi. E
a medição fala de escrita continuando depois do reboot; nada foi dito sobre listar, sobre a retenção
rodar ou sobre restaurar uma cópia, e este parágrafo não estica o resultado até lá.

**Isso pesa porque uma falha teria aparecido.** O veredito sobre o vínculo não é "o bookmark
resolveu": é uma listagem da própria pasta apontada, feita de novo a cada operação
(`IosBackupFolder.kt`), e quando o vínculo cai a tela troca a linha de destino para *Dentro
do app*, com uma régua vermelha e duas saídas — reconectar a pasta ou manter dentro do app (design
D12; `backup_destination_app`; `BackupScreen.kt:513-517,589-632`). Um caminho desenhado para
anunciar a própria falha, que não anunciou nada, é um resultado que carrega peso mesmo sendo uma
medição só.

**O critério não disparou.** Ele existia para um cenário — se o bookmark não sobrevivesse, o degrau
2 no iOS precisaria de outro desenho e a entrega pararia aqui —, e não foi isso que aconteceu. O
degrau 2 no iOS segue como desenhado. Isso não fecha a pergunta por inteiro: a documentação da Apple
continua sem dizer nada sobre sobrevivência a reboot, em nenhuma direção, e um aparelho respondendo
uma vez não é garantia de plataforma — o arquivo não passa a ler como se fosse. O caso do volume
externo ou removível, fechado acima como negativo, continua do mesmo jeito, dentro do desenho
aceito (D16).

### Q2 — Uma subpasta de `Download` é selecionável no Android 11+?

A documentação proíbe `ACTION_OPEN_DOCUMENT_TREE` sobre *"o diretório `Download`"*, e escreve *"e
todos os subdiretórios"* apenas para `Android/data` e `Android/obb`. A assimetria sugere que
subpastas passam, mas não está afirmado.

Interessa por um motivo lateral: há relato de julho/2026 de que o Android passou a subir documentos
de `Downloads` para uma pasta "Android backups" no Drive. Se as duas coisas se confirmarem, o
Android ganharia cobertura de nuvem sem esforço. **Duas incertezas empilhadas, uma de fonte
jornalística única** — não é plano, é um teste de dois minutos a mais no mesmo spike.

**Respondida: sim.** Medido em Android 16 (API 36), emulador, DocumentsUI de fábrica. Uma subpasta
de `Download` escolhida por `ACTION_OPEN_DOCUMENT_TREE` aceita `takePersistableUriPermission`,
aceita que o app crie a subpasta dele dentro e escreva, e a permissão sobreviveu a **dois
reinícios** — leitura e escrita ainda válidas, e uma gravação nova bem-sucedida depois deles. O
bloqueio é do diretório, não da árvore: o seletor recusa `Download` **em si** e a raiz do volume,
com *"Can't use this folder"* e `USE THIS FOLDER` desabilitado. `CREATE NEW FOLDER` continua
disponível nessa mesma tela bloqueada, de modo que quem cai nela está a uma pasta de um destino
válido. `Documents` é selecionável e gravável como qualquer outra.

**A pergunta lateral não foi medida, e mudou de tamanho.** Se o Android sobe documentos de
`Downloads` para o Drive continua com fonte única e jornalística. O que foi medido é o mecanismo do
seletor: ele filtra as raízes por `Root.FLAG_SUPPORTS_IS_CHILD`, uma bandeira que **cada provedor
declara por si** — um `DocumentsProvider` escrito para a medição apareceu no seletor, foi
selecionável e teve o tree URI persistido. **Não existe regra de plataforma que mantenha o cofre
local no Android**; existe um conjunto de provedores instalados que não se oferecem. O que isso faz
com a fala da tela está em D16.

**Os limites da medição são parte do resultado.** Do Google Drive não há leitura: o app estava
instalado (2.25.100) e registra um provedor, mas, sem conta conectada, não contribuiu raiz alguma;
OneDrive e Dropbox não estavam instalados. Nenhum seletor de OEM foi exercitado, e Samsung e Xiaomi
embarcam DocumentsUI modificado. E só o Android 16 foi medido — que o bloqueio de `Download` valha
desde o Android 11 é o que a documentação diz, não o que se viu.

**Uma anomalia, sem explicação.** Logo depois do primeiro reinício, a consulta pelos filhos da
subpasta devolveu um cursor não-nulo e vazio enquanto uma árvore vizinha listava normalmente, e um
`createDocument` em seguida provou que a pasta estava lá. Não reproduziu em quatro tentativas nem
num segundo reinício. Fica sem causa conhecida, e é por isso que D9 proíbe ler "vazio" como "não há
cópias".

### Q3 — O Auto Backup da plataforma volta um dia, como opt-in?

D14 recusa por postura, não por técnica, e o argumento técnico que fechou a porta originalmente não
cobre o arquivo capturado. Fica registrado para que ninguém redescubra isto do zero: se a falta de
cobertura contra perda do aparelho no Android incomodar, a resposta é `allowBackup` com regras que
incluam **apenas** a pasta de backups do degrau 1 e continuem excluindo o banco vivo, oferecido
como escolha explícita do usuário. Teto de 25 MB por app, só a cópia mais recente, e reescrita de
`data_extraction_rules.xml`, de `PlatformBackupIsOffTest` e do requisito correspondente em
`local-backup`. Nada aqui decide que isso volta, nem quando: o registro poupa quem reabrir a
pergunta de refazer a investigação, e a pergunta segue em aberto até alguém reabri-la.
