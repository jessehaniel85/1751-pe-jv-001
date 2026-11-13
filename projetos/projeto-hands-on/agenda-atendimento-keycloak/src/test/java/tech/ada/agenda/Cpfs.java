package tech.ada.agenda;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aula 4: com o limite de agendamentos por cliente, cada teste usa um CPF próprio (como já usava uma agência própria),
 * para um teste não gastar a vaga de outro. Os CPFs gerados têm dígitos verificadores válidos (a validação entra na Aula 6).
 */
final class Cpfs {

  private static final AtomicInteger SEQUENCIA = new AtomicInteger(100_000_000);

  private Cpfs() {
  }

  static String novo() {
    String base = String.valueOf(SEQUENCIA.incrementAndGet());
    String comPrimeiro = base + digito(base);
    return comPrimeiro + digito(comPrimeiro);
  }

  private static int digito(String numeros) {
    int soma = 0;
    int peso = numeros.length() + 1;
    for (char c : numeros.toCharArray()) {
      soma += (c - '0') * peso--;
    }
    int resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  }
}
