# Planejamento de Aulas — 1751-pe-jv-001

Este repositório contém o material da turma 1751 no módulo pe-jv-001 · Introdução ao Quarkus (Escalação Tech, Nível III, **28/10 a 18/11/2026**). O objetivo é organizar materiais e recursos pedagógicos usados durante o curso.

Conteúdo
- Material do aluno por encontro (leitura de consolidação)
- Código do projeto guiado (API de Chaves PIX): ponto de partida e estado ao fim de cada aula
- Brief do projeto final em grupo, com critérios de avaliação

Estrutura
- README.md — visão geral (este arquivo)
- /ambiente — checklist de configuração (tarefa prévia) e `settings.xml` do Nexus
- /plano-de-aulas — material do aluno por encontro (`aula-0X-aluno.md`)
- /projetos/projeto-guiado-chaves-pix — `inicio/` (ponto de partida da Aula 1) e `gabarito/aula-0X/` (estado ao fim de cada aula, cada um passa no `mvn verify`)
- /projetos/projeto-final-grupo — `brief-alunos.md`, descrição do projeto final e da banca

Cronograma (Seg/Qua/Sex, 15h–18h, pelo Teams; sem aula em 02/11)

| Aula | Data | Tema |
|---|---|---|
| 1 | Qua 28/10 | HTTP/REST em revisão + primeiro projeto Quarkus |
| 2 | Sex 30/10 | Quarkus REST, CDI e OpenAPI |
| 3 | Qua 04/11 | Hibernate ORM com Panache |
| 4 | Sex 06/11 | Configuração, health e métricas |
| 5 | Seg 09/11 | REST Client e Fault Tolerance |
| 6 | Qua 11/11 | Validação e tratamento de erros |
| 7 | Sex 13/11 | Autenticação |
| 8 | Seg 16/11 | Autorização |
| 9 | Qua 18/11 | Banca do projeto final |

Pilha: **Java 21** e **Quarkus 3.15.7**, com Maven pelo Nexus. Não precisa de Docker nem de Quarkus CLI.

Como usar
1. Antes da Aula 1, rode o checklist em /ambiente na sua máquina da rede corporativa e devolva o resultado no canal da turma.
2. Acesse /plano-de-aulas a cada aula para ler o conteúdo, a fim de praticar juntos, com foco nas discussões e práticas.
3. Acompanhe o projeto guiado sendo desenvolvido a cada aula (compartilhamento de tela com o código) e use o gabarito para estudar, comparar com o seu código ou retomar se algo travar.

Módulo anterior desta turma: be-jv-010 — repositório `jessehaniel85/1751-be-jv-010`.
