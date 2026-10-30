package tech.ada.agenda;

import jakarta.ws.rs.NotFoundException;

public class AgendamentoNaoEncontradoException extends NotFoundException {

    public AgendamentoNaoEncontradoException(Long id) {
        super("Agendamento " + id + " não encontrado");
    }
}
