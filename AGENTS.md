# Project Agent Instructions

## Fonte única de contexto local
- Para qualquer tarefa neste repositório, leia e siga `./CLAUDE.md` antes de planejar ou editar código.
- Considere `CLAUDE.md` como a referência principal de convenções, arquitetura e fluxo de trabalho deste projeto.

## Skills locais do projeto (Claude Code)
Use as skills abaixo diretamente dos caminhos locais, sem cópia e sem symlink:

- `commit`: `./.claude/skills/commit/SKILL.md`
- `bump-version`: `./.claude/skills/bump-version/SKILL.md`
- `finsight-ux`: `./.claude/skills/finsight-ux/SKILL.md`
- `jetpack-compose-expert`: `./.claude/skills/jetpack-compose-expert/SKILL.md`
- `kmp-architecture`: `./.claude/skills/kmp-architecture/SKILL.md`
- `kmp-unit-testing`: `./.claude/skills/kmp-unit-testing/SKILL.md`
- `room-database`: `./.claude/skills/room-database/SKILL.md`

## Regra de acionamento das skills
- Se o usuário citar uma skill pelo nome, carregue o respectivo `SKILL.md` e siga as instruções.
- Se a tarefa corresponder claramente ao domínio da skill, acione a skill mesmo sem citação explícita.
- Se mais de uma skill se aplicar, use apenas o conjunto mínimo necessário.
