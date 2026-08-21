---
area: report
severity: low
type: i18n
verdict: fixed
---

# O relatório exportado se declara em inglês e carimba a data de geração em ISO

## Cenário

**DADO** o app em português
**QUANDO** o usuário compartilha ou imprime um relatório
**ENTÃO** o HTML gerado traz `<html lang="en">` com todo o conteúdo em português, e o
cabeçalho lê "Gerado em: 2026-08-21", enquanto todas as outras datas do mesmo documento
saem formatadas ("21 de agosto de 2026")
**DEVERIA** declarar o idioma em que o documento foi de fato escrito, e formatar a data de
geração pelo mesmo dono das outras

## Mecânica

`HtmlReportDocumentRenderer.render()` escreve `lang="en"` literal — o renderer não recebe
idioma nenhum, e é aí que está a decisão a tomar: de onde ele vem.

`toReportLayout()` monta `generatedAtLabel` com `generatedAtDate.toString()`, que em
`LocalDate` é ISO-8601. O parâmetro `dateFormats` está no escopo e é usado logo abaixo, em
`periodLabel` e nos títulos de grupo.

## Evidência

- `feature/report/impl/.../render/HtmlReportDocumentRenderer.kt` — `render()`:
  `appendLine("<html lang=\"en\">")`
- `feature/report/impl/.../viewer/ReportExportLayout.kt` — `toReportLayout()`:
  `generatedAtLabel = "${strings.generatedAtPrefix}: ${generatedAtDate.toString()}"`, ao
  lado de `periodLabel = dateFormats.formatReportPeriod(...)`
- `core/common/.../util/DateFormats.kt` — o dono correto do formato

## Consequência

Um leitor de tela, ou o tradutor do navegador, lê o documento com as regras do idioma
errado. E a única data em formato estrangeiro no relatório é justamente a que diz quando ele
foi feito.

## Sugestão

O `lang` sai do idioma resolvido junto com `ReportExportStrings` — que já são resolvidas
fora do mundo `@Composable` exatamente por esse motivo; a data de geração passa por
`dateFormats`. Não vinculante.

## Desfecho

**Causa real** — as duas metades tinham a mesma origem: o documento não sabia em que
idioma estava escrito. O `lang` era literal porque nada dizia ao renderer qual era o
idioma, e a data de geração era ISO porque `LocalDate.toString()` é o fallback de quem
não tem formatador — enquanto o `dateFormats` que formata todas as outras datas já
estava no escopo, uma linha acima.

**Mudança** — `ReportLayout` ganha `languageTag`, e o renderer o declara (escapado, como
todo texto que entra no HTML) em vez de nomear um idioma próprio. O valor sai da nova
chave `app_language_tag` — `pt-BR` em `values`, `en` em `values-en` —, resolvida em
`ReportExportStrings` junto com as outras strings, então o que o documento declara não
tem como divergir do que ele está escrito. A data de geração passa por
`DateFormats.formatFullDate()`, que é o formato completo que `formatReportPeriod()` já
usava para o fim do período, agora extraído em vez de reconstruído a cada chamada.

**Prova** — `ReportExportDocumentLanguageTest`, com os dois casos: o documento declara o
idioma recebido e não nomeia `en` por conta própria; e o rótulo de geração é
`formatFullDate(hoje)`, sem a forma ISO. Vermelhos com as duas linhas antigas
restauradas, verdes depois. `./gradlew jvmTest`: 1278 testes, 0 falhas.
`:feature:report:impl:compileDebugKotlinAndroid` compilando.

**Commit** — `Fix(Report): say which language the exported document is written in`
