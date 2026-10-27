package tech.ada.pix.chaves;

import jakarta.ws.rs.NotFoundException;

/**
 * Aula 2: herda de NotFoundException, então o Quarkus REST responde 404 sozinho.
 */
public class ChaveNaoEncontradaException extends NotFoundException {

    public ChaveNaoEncontradaException(Long id) {
        super("Chave " + id + " não encontrada");
    }
}
