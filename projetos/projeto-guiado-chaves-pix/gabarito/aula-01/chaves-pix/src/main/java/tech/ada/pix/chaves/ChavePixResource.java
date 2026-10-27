package tech.ada.pix.chaves;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aula 1: primeiro recurso REST. A lista é fixa, em memória.
 * Na Aula 2 o recurso ganha o CRUD completo e passa a delegar a um service.
 */
@Path("/chaves")
@Produces(MediaType.APPLICATION_JSON)
public class ChavePixResource {

    private static final LocalDateTime CRIACAO = LocalDateTime.of(2026, 10, 28, 15, 0);

    private static final List<ChavePixResponse> CHAVES = List.of(
            new ChavePixResponse(1L, TipoChave.CPF, "52998224725", "0001", "123456", "52998224725", CRIACAO),
            new ChavePixResponse(2L, TipoChave.EMAIL, "maria@exemplo.com.br", "0001", "123456", "52998224725", CRIACAO),
            new ChavePixResponse(3L, TipoChave.TELEFONE, "+5561988887777", "0002", "654321", "11144477735", CRIACAO));

    @GET
    public List<ChavePixResponse> listar() {
        return CHAVES;
    }
}
