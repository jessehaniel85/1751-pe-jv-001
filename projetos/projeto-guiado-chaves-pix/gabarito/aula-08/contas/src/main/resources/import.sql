-- Contas de exemplo (CPFs fictícios com dígito verificador válido).
-- As duas primeiras são as contas das chaves do import.sql do chaves-pix.
insert into Conta (id, agencia, numero, titularCpf) values (next value for Conta_SEQ, '0001', '123456', '52998224725');
insert into Conta (id, agencia, numero, titularCpf) values (next value for Conta_SEQ, '0002', '654321', '11144477735');
insert into Conta (id, agencia, numero, titularCpf) values (next value for Conta_SEQ, '0001', '777777', '12345678909');
