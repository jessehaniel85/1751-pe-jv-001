# agenda-atendimento-jwt

Variação da Agenda de Atendimento (estado do fim da Aula 8) com **Bearer token (JWT)** no lugar do Basic: a própria
aplicação emite o token em `POST /login` e confere a assinatura nas demais chamadas. Explicação completa em
`plano-de-aulas/complemento-aula-07-08-jwt.md`.

Pilha: Quarkus 3.15.7, Java 21, sem Docker.

## Antes de rodar: gere o par de chaves

A pasta `chaves/` está no `.gitignore` (a chave privada nunca vai para o repositório). Na raiz desta pasta:

```bash
mkdir chaves && cd chaves
openssl genrsa -out rsaPrivateKey.pem 2048
openssl rsa -pubout -in rsaPrivateKey.pem -out publicKey.pem
openssl pkcs8 -topk8 -nocrypt -inform pem -in rsaPrivateKey.pem -outform pem -out privateKey.pem
rm rsaPrivateKey.pem
cd ..
```

## Rodar

```bash
mvn test            # 59 testes
mvn quarkus:dev
```

```bash
curl -X POST localhost:8080/login -H 'Content-Type: application/json' -d '{"login":"atendente","senha":"atendente123"}'
TOKEN="cole-aqui-o-valor-do-campo-token"
curl -H "Authorization: Bearer $TOKEN" localhost:8080/agencias/0001/agendamentos
```

Usuários de aula (os do `CargaUsuarios`): `97744180003` e `43930413051` (senha `senha123`, papel `usuario`),
`atendente` (`atendente123`) e `gerente` (`gerente123`).
