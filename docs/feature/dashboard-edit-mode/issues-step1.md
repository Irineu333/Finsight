# Dashboard Edit Mode — Step 1: Known Issues

## Issue 1 — Long press não aciona o modo edição em componentes com ação

**Severidade:** Crítica

**Descrição:**
Componentes que possuem ação própria (ex.: tap para navegar, tap para abrir modal) não estão detectando o gesto de long press para entrar no modo edição. O gesto é consumido pela ação do componente antes de chegar ao handler de long press.

**Comportamento esperado:**
Long press em **qualquer** componente deve acionar o modo edição, independentemente de o componente ter ou não ação de tap associada.

**Comportamento atual:**
Componentes com ação própria ignoram o long press — o modo edição não é acionado.

**Observação:**
Este é um problema recorrente nas implementações de IA desta feature, indicando que a solução adotada não trata corretamente a coexistência de `clickable`/`combinedClickable` com o gesture de long press em nível de container.

---

## Issue 2 — Arrastar componente não funciona corretamente no modo edição

**Severidade:** Crítica

**Descrição:**
No modo edição, o gesto de arrastar está com comportamento bugado. Não é possível mover livremente o item por todas as posições da lista de forma fluida.

**Comportamento esperado:**
- O componente (card simplificado na etapa 1) deve ser arrastável por todas as posições da lista.
- A reordenação deve ser fluida: os demais itens recuam visualmente enquanto o item arrastado se move.
- A posição é consolidada ao soltar o item.

**Comportamento atual:**
O arrasto não responde corretamente ao gesto — o item não se move livremente ou não reflete a posição esperada durante e após o drag.

---

## Requisitos não negociáveis (referência para a correção)

1. Long press em **qualquer** componente deve acionar o modo edição — inclusive componentes com ação de tap.
2. No modo edição, o componente deve ser arrastável livremente por todas as posições, com reordenação visual fluida e consolidação ao soltar.

> Estes dois comportamentos são gate para aprovação da etapa 1.
