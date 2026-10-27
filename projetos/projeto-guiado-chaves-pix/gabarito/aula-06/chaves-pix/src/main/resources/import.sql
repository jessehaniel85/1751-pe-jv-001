-- Dados de exemplo (CPFs fictícios com dígito verificador válido).
-- O id vem da sequence que o Hibernate cria para a entidade (ChavePix_SEQ).
insert into ChavePix (id, tipo, valor, agencia, numeroConta, titularCpf, criadaEm) values (next value for ChavePix_SEQ, 'CPF', '52998224725', '0001', '123456', '52998224725', '2026-10-28 15:00:00');
insert into ChavePix (id, tipo, valor, agencia, numeroConta, titularCpf, criadaEm) values (next value for ChavePix_SEQ, 'EMAIL', 'maria@exemplo.com.br', '0001', '123456', '52998224725', '2026-10-28 15:00:00');
insert into ChavePix (id, tipo, valor, agencia, numeroConta, titularCpf, criadaEm) values (next value for ChavePix_SEQ, 'TELEFONE', '+5561988887777', '0002', '654321', '11144477735', '2026-10-28 15:00:00');
