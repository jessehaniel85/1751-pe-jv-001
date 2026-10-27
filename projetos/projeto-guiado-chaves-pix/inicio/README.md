# API de Chaves PIX: ponto de partida da Aula 1

Este diretório tem só o `pom.xml` agregador do projeto guiado (`tech.ada.pix:projeto-guiado-chaves-pix`, empacotamento
`pom`, sem módulos). Na Aula 1 geramos juntos o primeiro módulo, `chaves-pix`, com o plugin Maven do Quarkus.
Não precisa de Quarkus CLI nem do site code.quarkus.io: só Java 21 e Maven.

## 1. Conferir o ambiente

```bash
java -version    # 21
mvn -v           # 3.9.x, usando o Java 21
mvn verify       # aqui, no inicio/: BUILD SUCCESS (ainda não há módulo)
```

Na rede corporativa, acrescente `-s <caminho do settings.xml do Nexus>` a todos os comandos `mvn` (o caminho está no
checklist de ambiente).

## 2. Gerar o módulo `chaves-pix` (rode dentro de `inicio/`)

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.15.7:create \
    -DprojectGroupId=tech.ada.pix \
    -DprojectArtifactId=chaves-pix \
    -DpackageName=tech.ada.pix.chaves \
    -Dextensions='quarkus-rest-jackson' \
    -DjavaVersion=21
```

No Windows (PowerShell ou cmd), escreva o comando numa linha só e use aspas duplas em `-Dextensions="quarkus-rest-jackson"`.

O que acontece:

- nasce a pasta `chaves-pix/` com `pom.xml` próprio (o BOM do Quarkus 3.15.7 vem nele);
- o plugin acrescenta `<module>chaves-pix</module>` ao `pom.xml` agregador;
- vêm de brinde um `GreetingResource` (`GET /hello`) e dois testes (`GreetingResourceTest` e `GreetingResourceIT`).

## 3. Arrumar o que veio de brinde

| O que veio | O que fazer | Por quê |
|---|---|---|
| `src/main/docker/`, `.dockerignore` | apagar | não usamos Docker neste curso |
| `mvnw`, `mvnw.cmd`, `.mvn/` dentro de `chaves-pix/` | apagar | o wrapper baixa o Maven da internet; usamos o Maven instalado |
| `GreetingResource` e os dois testes | apagar depois de ver funcionando | na aula, viram `ChavePixResource` e `ChavePixResourceTest` |

## 4. Rodar

```bash
cd chaves-pix
mvn quarkus:dev            # http://localhost:8080/hello ; Dev UI em http://localhost:8080/q/dev-ui
mvn verify                 # roda o @QuarkusTest
```

No `quarkus:dev`, a tecla `r` roda os testes de novo e `q` encerra. Mudou o código? Basta recarregar a página: o
Quarkus recompila sozinho.

## Daqui para a frente

Cada aula acrescenta uma camada. O estado esperado ao fim de cada aula está em `../gabarito/aula-0X/`, com um
`ESTADO.md` que diz o que funciona e como rodar. Se o seu projeto travar, copie a pasta da aula anterior e siga dali.
