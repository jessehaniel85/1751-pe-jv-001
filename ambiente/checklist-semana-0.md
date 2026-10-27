# Checklist de ambiente — Introdução ao Quarkus (PE-JV-001)

> **Tarefa prévia: envie o resultado até domingo, 25/10.** A Aula 1 é na quarta, 28/10, e começa direto no conteúdo, sem tempo de aula para montar ambiente. Rode as seções abaixo na máquina em que você vai assistir às aulas, **conectado à rede corporativa (VPN, se for o seu caso)**, e devolva o resultado (print ou texto) no canal da turma.
>
> Tudo aqui costuma funcionar de primeira. O objetivo é só descobrir, com folga, o que não funcionar na sua máquina, para resolvermos antes da aula. Se algum item falhar, **não tente consertar sozinho**: copie a mensagem de erro inteira e mande no canal. Uma falha encontrada hoje custa cinco minutos; descoberta na aula, custa a aula.

**Tempo estimado:** 30 a 40 minutos, quase todo de espera dos downloads.
**O que NÃO precisa:** Docker. Não há Docker na rede e o módulo não usa.

---

## 0. Antes de começar

1. Crie uma pasta de trabalho vazia, por exemplo `quarkus-ambiente`, e abra o terminal nela.
2. Copie o `settings.xml` que a turma recebeu para essa pasta e preencha o endereço do Nexus da empresa e suas credenciais (as credenciais ficam em variáveis de ambiente, não escreva a senha no arquivo). Todos os comandos abaixo usam `-s settings.xml`, para o Maven buscar tudo no Nexus da empresa.
3. Use o comando `mvn`, **não** o `./mvnw` que vem dentro dos projetos gerados: o `mvnw` tenta baixar o Maven de um site externo, que a rede pode bloquear.
4. Nos exemplos, os argumentos `-D...` vão entre aspas duplas. Assim o mesmo comando vale no Git Bash, no cmd e no PowerShell.

## 1. Java e Maven

| # | Verificação | Comando | Resultado esperado |
|---|---|---|---|
| 1.1 | Java 21 | `java -version` | `21` (a pilha do módulo é Java 21; o projeto não compila em 17) |
| 1.2 | Maven | `mvn -version` | 3.9.x (o material foi testado com 3.9.14) e **Java version: 21** na saída |
| 1.3 | Maven usando o Nexus | `mvn -s settings.xml help:effective-settings` | mirror com `mirrorOf` `*` apontando para o Nexus da empresa |

Se `mvn -version` mostrar um Java diferente de 21, o Maven está usando outro JDK: confira a variável `JAVA_HOME` e mande o print. Vale também para versões **mais novas**: com Java 25, as primeiras aulas funcionam, mas o projeto quebra quando o banco de dados entra (Aula 3).

## 2. A plataforma Quarkus resolve pelo Nexus

O módulo inteiro usa **Quarkus 3.15.7** (fixa a versão para todo mundo ter o mesmo comportamento). Rode cada linha e marque se terminou com `BUILD SUCCESS`.

| # | O que | Comando | Resolve? |
|---|---|---|---|
| 2.1 | BOM da plataforma (a lista de versões) | `mvn -s settings.xml dependency:get "-Dartifact=io.quarkus.platform:quarkus-bom:3.15.7:pom"` | ☐ |
| 2.2 | Plugin Maven do Quarkus | `mvn -s settings.xml dependency:get "-Dartifact=io.quarkus.platform:quarkus-maven-plugin:3.15.7"` | ☐ |

Depois, as **extensões** que o módulo usa. O padrão do comando é o mesmo; só muda o nome do artefato:

```
mvn -s settings.xml dependency:get "-Dartifact=io.quarkus:<artefato>:3.15.7"
```

| # | Artefato (`<artefato>`) | Para quê | Resolve? |
|---|---|---|---|
| 2.3 | `quarkus-rest` | API REST | ☐ |
| 2.4 | `quarkus-rest-jackson` | JSON na API | ☐ |
| 2.5 | `quarkus-rest-client-jackson` | uma API chamando outra | ☐ |
| 2.6 | `quarkus-hibernate-orm-panache` | persistência | ☐ |
| 2.7 | `quarkus-jdbc-h2` | banco H2 em memória | ☐ |
| 2.8 | `quarkus-hibernate-validator` | validação | ☐ |
| 2.9 | `quarkus-smallrye-health` | health checks | ☐ |
| 2.10 | `quarkus-smallrye-fault-tolerance` | timeout, retry, circuit breaker | ☐ |
| 2.11 | `quarkus-micrometer-registry-prometheus` | métricas | ☐ |
| 2.12 | `quarkus-smallrye-openapi` | documentação OpenAPI / Swagger UI | ☐ |
| 2.13 | `quarkus-elytron-security-properties-file` | usuários em arquivo | ☐ |
| 2.14 | `quarkus-security-jpa` | usuários em banco | ☐ |
| 2.15 | `quarkus-junit5` | testes `@QuarkusTest` | ☐ |
| 2.16 | `quarkus-junit5-mockito` | mocks nos testes | ☐ |
| 2.17 | `io.rest-assured:rest-assured:5.5.0` (esta não é `io.quarkus`: use o GAV completo; 5.5.0 é a versão que o BOM do Quarkus 3.15.7 gerencia) | testes de API | ☐ |

> **Atenção, caso o `dependency:get` falhe:** na turma anterior, um `dependency:get` falhou no Nexus para um artefato que o `mvn clean install` resolvia normalmente. Por isso a seção 3 (o teste de verdade) vale mais que a 2. Se a 2 falhar e a 3 passar, mande os dois resultados; não é motivo de susto.

## 3. Gerar um projeto e rodar um teste (o teste de verdade)

Na pasta de trabalho, gere um projeto com o plugin (é assim que vamos criar projetos no módulo; não usamos o site `code.quarkus.io` nem a CLI do Quarkus):

```
mvn -s settings.xml -B io.quarkus.platform:quarkus-maven-plugin:3.15.7:create "-DprojectGroupId=tech.ada.teste" "-DprojectArtifactId=teste-ambiente" "-Dextensions=quarkus-rest,quarkus-rest-jackson,quarkus-rest-client-jackson,quarkus-hibernate-orm-panache,quarkus-jdbc-h2,quarkus-hibernate-validator,quarkus-smallrye-health,quarkus-smallrye-fault-tolerance,quarkus-micrometer-registry-prometheus,quarkus-smallrye-openapi,quarkus-elytron-security-properties-file,quarkus-security-jpa" "-DjavaVersion=21"
```

Isso cria a pasta `teste-ambiente`. Entre nela e rode:

```
cd teste-ambiente
mvn -s ../settings.xml verify
```

| # | Verificação | Resultado esperado | OK? |
|---|---|---|---|
| 3.1 | O `create` terminou e a pasta `teste-ambiente` existe | `BUILD SUCCESS` | ☐ |
| 3.2 | O `verify` rodou o teste gerado | `Tests run: 1, Failures: 0, Errors: 0` e `BUILD SUCCESS` (o projeto de referência fez isso em cerca de 5 s de teste, fora o download) | ☐ |

Anote **quanto tempo** o primeiro `verify` levou: serve para planejarmos as aulas.

## 4. Subir em modo de desenvolvimento, portas e Dev UI

Abra `teste-ambiente/src/main/resources/application.properties` e deixe estas três linhas (sem Docker, o banco precisa ser configurado à mão):

```
quarkus.devservices.enabled=false
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:teste
```

Se não houver a configuração do banco, o modo de desenvolvimento **não sobe** (erro `Datasource '<default>' is not configured`). Foi assim na verificação do material; por isso as linhas acima.

Agora, na pasta `teste-ambiente`:

```
mvn -s ../settings.xml quarkus:dev
```

| # | Verificação | Como | Resultado esperado | OK? |
|---|---|---|---|---|
| 4.1 | Aplicação sobe na porta **8080** | aguardar a mensagem `Listening on: http://localhost:8080` | sem `ERROR` de configuração | ☐ |
| 4.2 | Endpoint de exemplo | abrir `http://localhost:8080/hello` no navegador | texto `Hello from Quarkus REST` | ☐ |
| 4.3 | **Dev UI** | abrir `http://localhost:8080/q/dev-ui` | painel do Quarkus com as extensões | ☐ |
| 4.4 | Health | abrir `http://localhost:8080/q/health` | JSON com `"status":"UP"` | ☐ |
| 4.5 | Porta **8081** livre | em outro terminal, na mesma pasta: `mvn -s ../settings.xml quarkus:dev "-Dquarkus.http.port=8081" "-Ddebug=false"` e abrir `http://localhost:8081/hello` | mesmo texto da 4.2 | ☐ |

Ao subir, o terminal pode mostrar um `ERROR` do Hibernate sobre a tabela `MyEntity` ausente: vem do exemplo gerado (ainda não configuramos a criação de tabelas), não do seu ambiente; ignore. Encerre com `Ctrl+C` quando terminar. Nas aulas vamos usar duas aplicações ao mesmo tempo (portas 8080 e 8081), por isso o item 4.5.

## 5. Ferramentas

| # | Verificação | Como | OK? |
|---|---|---|---|
| 5.1 | IDE abre e importa o projeto `teste-ambiente` como projeto Maven | IntelliJ, VS Code ou Eclipse; anote qual e a versão | ☐ |
| 5.2 | A IDE roda o teste `GreetingResourceTest` | botão de executar ao lado do teste | ☐ |
| 5.3 | GitHub acessível (para o repositório do projeto final) | `git ls-remote https://github.com/quarkusio/quarkus.git HEAD` responde com um hash | ☐ |

---

## O que devolver no canal da turma

Copie, preencha e envie (print dos erros, se houver):

```
Nome:
Java (java -version):
Maven (mvn -version, linha 1 e linha "Java version"):
Seção 2: resolveu tudo? Se não, quais números falharam:
Seção 3: create OK? verify OK? tempo do primeiro verify:
Seção 4: 8080 OK? 8081 OK? Dev UI abriu?
IDE e versão:
GitHub OK?
Observações / erros (mensagem inteira):
```

Dúvida ou travou em algum item: avise no canal sem esperar o domingo. Se a sua máquina não passar da seção 2 ou da 3, o plano B é um projeto-semente pronto, que o docente envia; assim você não fica de fora da Aula 1.
