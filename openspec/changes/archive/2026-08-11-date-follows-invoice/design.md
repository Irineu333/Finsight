## Context

Nos modais de lançamento em cartão, `data` e `fatura` são hoje dois campos independentes. O
`TransactionForm` carrega ambos (`date: String`, `invoiceDueMonth: YearMonth?`) e
`BuildTransactionUseCaseImpl` os usa para coisas distintas: a fatura resolve a dimensão do razão,
a data só viaja no `TransactionIntent`. Nada os concilia.

Três fatos do código que moldam o desenho:

1. **Uma fatura não tem um mês.** `Invoice` guarda `openingMonth`, `closingMonth` e `dueMonth`
   (`core/model/.../Invoice.kt:18-20`), e a janela de compra — `openingDate` a `closingDate`,
   ambos no dia de fechamento do cartão — atravessa dois meses de calendário. O seletor da
   interface exibe o `dueMonth` (`InvoiceExt.kt:23`), que é posterior à janela inteira.

2. **A derivação `dueMonth → closingMonth` já está copiada quatro vezes**, toda ela em
   `feature/creditcards/impl`: `CreateFutureInvoiceUseCase:33`, `CreateRetroactiveInvoiceUseCase:33`,
   `CreateInvoiceUseCase:49`, `OpenInvoiceUseCase:49`. A inversa — de uma data para o mês de
   abertura — está numa quinta, `AddCreditCardUseCase:51-57`. Nenhuma é a dona; todas são cópias.

3. **O campo de data é de mão única.** `rememberTextFieldState(uiState.form.date)` inicializa uma
   vez e o `snapshotFlow` só flui UI → ViewModel (`AddTransactionModal.kt:64,78-82`; idêntico em
   `AddInstallmentModal.kt:63,78`). Uma mudança decidida no estado não chega ao campo.

O parcelamento acrescenta urgência: `AddInstallmentUseCaseImpl` distribui as parcelas com
`base.date.plus(index, MONTH)` casando com `dueMonth + index` — o código **já assume** que a data
acompanha a fatura, premissa hoje verdadeira apenas por acaso.

## Goals / Non-Goals

**Goals:**
- Dar um dono único, em `:core:model`, à janela de compra e à projeção de uma data nela.
- Fazer a fatura recolocar a data nos modais de adicionar transação e adicionar parcelamento.
- Manter a assimetria por construção: nada no caminho da data pode alcançar a fatura.
- Nunca produzir data futura, e nunca produzir um valor que o seletor de data recusaria.
- Eliminar as quatro cópias da derivação, sem mudar o comportamento delas.

**Non-Goals:**
- Mudar qualquer regra de gravação: razão, banco, migrações e o boundary de escrita ficam intactos.
- Fazer a data escolher a fatura, ou avisar quando as duas divergem.
- Alterar o modal de editar transação.
- Mudar o que o `InvoiceMonthNavigator` exibe (continua o mês de vencimento).
- Acrescentar strings de interface.

## Decisions

### D1 — Projeção na janela, não troca do mês de vencimento

A formulação ingênua — "trocar o mês da data pelo mês da fatura" — está errada em quase todo
caso, porque o mês exibido é o de vencimento e a compra aconteceu na janela, um ou dois meses
antes. A regra adotada é:

```
projeção(dia d, janela W) = a data em W cujo dia é d
```

Concretamente, preferindo o mês de fechamento e recuando para o de abertura quando o dia já
passou do corte:

```kotlin
fun dateOn(day: Int): LocalDate {
    val late = closingMonth.safeOnDay(day)
    return (if (late < closingDate) late else openingMonth.safeOnDay(day))
        .coerceAtLeast(openingDate)
}
```

**Por que esta e não a troca de mês:** a projeção é **idempotente** — uma data já dentro da janela
volta inalterada. Isso é o que torna a seleção inicial (fatura aberta, data hoje) um no-op, e o
que permite ir e voltar entre faturas sem derivar a data. A troca de mês estragaria a data já ao
abrir o modal.

O `coerceAtLeast(openingDate)` cobre a degenerescência de fim de mês — fechamento no dia 31,
janela 31/jan–28/fev, dia pedido 30, que não cabe em nenhum dos dois segmentos. Total é melhor
que quase-total.

A borda inclusiva na abertura reproduz `AddCreditCardUseCase:53` (`if (day < closingDay) mês−1`),
que é esta mesma regra lida ao contrário. As duas passam a ser uma só.

### D2 — Trava em hoje, sem ramo por status de fatura

```
resultado = min(projeção, hoje)
```

Uma fatura futura não representa gasto no futuro: representa gasto no presente que se paga no
futuro. Travar em hoje é a tradução disso, e resolve de graça o conflito com
`maxDate = uiState.today` no `DatePickerModal` (`AddTransactionModal.kt:266`) — a projeção nunca
produz algo que o seletor recusaria, então o seletor não precisa mudar.

**Alternativas descartadas:** relaxar o `maxDate` para `min(hoje, closingDate)` (torna o campo
capaz de aceitar datas futuras, contradizendo a semântica); não projetar quando a janela é futura
(faz a regra ter exceção justo onde a confusão é maior).

### D3 — A janela ganha um tipo, e a `InvoiceMonthSelection` passa a expô-la

```kotlin
// :core:model — domain/model/InvoiceWindow.kt
data class InvoiceWindow(
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val closingDay: Int,
) {
    val openingDate get() = openingMonth.safeOnDay(closingDay)   // inclusiva
    val closingDate get() = closingMonth.safeOnDay(closingDay)   // exclusiva
    fun dateOn(day: Int): LocalDate
}

fun CreditCard.invoiceWindowFor(dueMonth: YearMonth): InvoiceWindow
val Invoice.window: InvoiceWindow
```

`Invoice.openingDate` e `Invoice.closingDate` (hoje em `Invoice.kt:27-28`) passam a delegar a
`window`, de modo que a fatura existente responde pelos meses que **gravou** e não por uma
rederivação a partir dos dias atuais do cartão. Duas fontes não podem discordar porque só uma
existe por caso.

`InvoiceMonthSelection` precisa do cartão para derivar a janela de uma fatura que ainda não
existe — que é o caso comum ao navegar adiante, já que `existingInvoice` é `null` ali:

```kotlin
data class InvoiceMonthSelection(
    val creditCard: CreditCard,
    val dueMonth: YearMonth,
    val existingInvoice: Invoice?,
) {
    val window = existingInvoice?.window ?: creditCard.invoiceWindowFor(dueMonth)
}
```

São três sítios de construção (`AddTransactionViewModel:140`, `AddInstallmentViewModel:94`,
`EditTransactionViewModel:183`), todos já com o cartão no mesmo `combine`.

**Por que `:core:model` e não `feature/creditcards/impl`:** `feature/transactions/impl` não pode
ver `feature/creditcards/impl` (regra 2 da dependência). Colocar a regra no domínio compartilhado
é o único lugar de onde os dois modais a alcançam — e é onde a regra de derivação do projeto manda
que ela esteja de qualquer modo.

### D4 — Um coletor, não três chamadas

A recolocação **não** vai em `SelectInvoiceMonth`, `SelectCreditCard` e no auto-select inicial —
seriam três lugares dizendo a mesma frase, e um deles esqueceria. Vai num coletor único na
ViewModel:

```kotlin
// A fatura governa a data: mudou a fatura (ou o cartão, que redefine a janela sob o
// mesmo mês), a data se recoloca. O caminho inverso não existe.
viewModelScope.launch {
    combine(selectedCreditCard, selectedDueMonth, ::Pair).collect { (card, dueMonth) ->
        if (card == null || dueMonth == null) return@collect
        val day = runCatching { dayMonthYear.parse(input.value.date) }
            .getOrNull()?.day ?: clock.today().day
        val date = card.invoiceWindowFor(dueMonth).dateOn(day).coerceAtMost(clock.today())
        input.update { it.copy(date = dayMonthYear.format(date)) }
    }
}
```

O ponto decisivo: o coletor depende de `(cartão, fatura)` e **nunca** de `input.date`. Editar a
data não tem por onde realimentá-lo. A assimetria que o requisito pede é estrutural, não uma
disciplina de quem escreve o código depois.

O `runCatching` cobre o buffer a meio de digitação (`"05/0"`), usando o dia de hoje como dia
preservado — repara em vez de travar.

### D5 — Sincronização reversa do campo com guarda de igualdade

```kotlin
LaunchedEffect(uiState.form.date) {
    if (uiState.form.date != date.text.toString()) {
        date.edit { replace(0, length, uiState.form.date) }
    }
}
```

Mesmo mecanismo que o `DatePickerModal` já usa para escrever no campo
(`AddTransactionModal.kt:270`), então não é padrão novo. A guarda de igualdade fecha o laço:
digitar produz um valor que a ViewModel guarda idêntico, logo a emissão de volta é igual ao
buffer e não escreve; projetar produz valor diferente, escreve uma vez, o `snapshotFlow` devolve
o mesmo valor à ViewModel e converge.

### D6 — O modal de edição fica de fora, por princípio

Na criação a data é um valor padrão do sistema (hoje); sugerir sobre um padrão é legítimo. Na
edição a data é dado que o usuário escreveu; sobrescrevê-la contradiria a mesma regra que
sustenta esta mudança — a palavra final sobre a data é dele. A aparente inconsistência entre os
dois modais é a regra sendo aplicada corretamente.

`EditTransactionViewModel` é tocado apenas pelo campo novo de `InvoiceMonthSelection`, sem
coletor e sem sincronização reversa.

### D7 — As quatro cópias migram junto

Migrar `CreateFutureInvoiceUseCase`, `CreateRetroactiveInvoiceUseCase`, `CreateInvoiceUseCase`,
`OpenInvoiceUseCase` e `AddCreditCardUseCase` para consumir a dona é opcional para fazer a
funcionalidade andar, e obrigatório para não deixar a regra com cinco donos no mesmo commit em
que ela ganha um. É refatoração sem mudança de comportamento, coberta pelos testes existentes
dessas classes.

## Risks / Trade-offs

**[O dia digitado se perde ao travar em hoje]** → Ao navegar para uma fatura futura, a data vira
hoje e o dia original desaparece; voltar reprojeta a partir do dia de hoje. Guardar um "dia
pretendido" separado do texto resolveria e não paga o estado extra. Aceito e documentado.

**[Data e fatura podem divergir depois da edição manual]** → É consequência deliberada do
requisito: a data não altera a fatura. Como a data não decide nada no razão — a dimensão vem de
`form.invoiceDueMonth` —, a divergência é cosmética e reversível. Nenhum número fica errado.

**[A guarda de igualdade da sincronização reversa é sutil]** → Se alguém trocar a comparação por
uma keying diferente, o campo pode passar a brigar com a digitação. Mitigação: comentário no
ponto explicando por que a guarda existe, e um cenário de spec que fixa o comportamento.

**[`InvoiceMonthSelection` ganha um campo obrigatório]** → Três sítios de construção, todos com o
cartão em mãos; o compilador aponta os três. Risco mecânico, não de desenho.

**[Degenerescência de fim de mês]** → Fechamento no dia 29, 30 ou 31 produz janelas com bordas
recolhidas por `safeOnDay`, e a projeção pode não achar o dia pedido em nenhum candidato. O
`coerceAtLeast` torna a função total, mas o resultado nesse canto é uma escolha, não uma dedução.
Fica coberto por teste explícito.

## Open Questions

Nenhuma. As quatro decisões que estavam abertas — qual mês a data recebe (D1), o que fazer com
fatura futura e o `maxDate` (D2), se a divergência manual é aceitável (D6 e o risco acima) e se a
deduplicação entra junto (D7) — foram fechadas antes deste documento.
