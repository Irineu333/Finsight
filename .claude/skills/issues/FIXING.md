# Referência de correção

**Sugestiva, não normativa.** Como corrigir é decisão de quem corrige, e a `## Sugestão`
do arquivo de bug também não vincula — ela diz por onde começar, não o que fazer.
A única regra é arquivar ao terminar (`SKILL.md`, modo *arquivar*).

## O ciclo

```
 reproduzir ──▶ morder ──▶ corrigir ──▶ provar ──▶ arquivar
  (o cenário     (teste     (a menor    (verde +   (desfecho +
   acontece?)     vermelho)  mudança)    suíte)     git mv)
      │
      └──▶ não acontece? PARE.
```

## 1. Reproduzir, antes de tocar no código

Execute o `DADO`/`QUANDO` do cenário e confirme que o `ENTÃO` acontece. Se **não**
acontecer, pare: ou a causa é outra, ou o bug já fechou.

- já fechou → arquive como `incidental` e considere deixar a prova de regressão para trás
- a causa é outra → reinvestigue e **corrija o arquivo do bug** antes de corrigir o código

Um diagnóstico não confirmado leva a uma correção que muda a coisa errada e passa nos
testes — o pior desfecho possível, porque parece sucesso.

Para um invariante, "reproduzir" é confirmar que ele é falso hoje, nas âncoras listadas.

## 2. Morder com um teste

Escreva primeiro o teste que afirma o `DEVERIA` (ou o invariante) e **veja-o vermelho**.
Um teste que nasce verde não prova nada sobre o defeito: prova que ele não estava lá.

O teste é a única coisa que impede a volta. Um bug corrigido sem prova volta a ser
disciplina — e disciplina é o que já falhou uma vez, quando o bug entrou.

## 3. Onde a prova vive

Nem todo defeito cabe num teste unitário, e forçar produz teste teatral. Escolha pela
natureza do bug:

| natureza | onde | molde no projeto |
|---|---|---|
| domínio, use case, invariante de modelo | `commonTest`/`jvmTest` do módulo dono | `core/ledger/src/commonTest` |
| query, figura do razão | `core/ledger/src/jvmTest` | `LedgerFixture` |
| migração de banco | `core/database/src/jvmTest` | `Migration*Test`, `MigrationTestHelpers` |
| mapeamento domínio → UI | `core/ui/src/commonTest` | `TransactionItemSignTest` |
| estado de ViewModel | `jvmTest` do `impl` da feature | `*CharacterizationTest` |
| composição, gesto, scroll, foco | `.maestro/` | ver `.maestro/README.md` §2 antes de rodar |
| paridade estrutural (chaves i18n, `testTag`) | teste de invariante sobre os arquivos | — |

A última linha importa: **defeito de classe pede prova de classe.** Corrigir as oito
ocorrências à mão deixa a nona nascer amanhã; o que fecha o bug é a afirmação que vale
sobre todas.

Quando a prova só puder ser manual — e isso acontece —, declare-a: os passos executados
e o resultado observado, no campo **Prova** do desfecho. "Verifiquei" sem passos não é prova.

## 4. Corrigir

A menor mudança que faz o `DEVERIA` valer. Se a correção pedir uma decisão de comportamento
que o cenário não responde, é decisão do usuário, não sua — pergunte.

Se o bug revelou que **a regra não existia em lugar nenhum**, o dono dela é o domínio, não
a tela: uma tela decide *se* aplica uma regra, nunca *qual* regra é.

## 5. Provar

```bash
./gradlew :app:shared:testDebugUnitTest --tests "*.XxxTest"   # o teste que morde
./gradlew jvmTest                                              # a suíte
```

Rode a suíte, não só o teste novo: a correção de um bug é onde regressões nascem.
Relate o que rodou e o que saiu. Se algo falhou, diga qual e mostre a saída.

## 6. Arquivar

Só então: `## Desfecho`, `verdict: fixed`, e `git mv` para `issues/archive/` com a data
de hoje. A correção não está pronta enquanto o arquivo estiver em `issues/`.
