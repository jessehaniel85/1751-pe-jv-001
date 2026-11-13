package tech.ada.agenda;

public class CredencialInvalidaException extends RuntimeException {

  public CredencialInvalidaException() {
    super("Login ou senha inválidos");
  }
}
