package tech.ada.agenda;

import java.time.Instant;
import java.util.Set;

public record QuemSouEuResponse(String login, Set<String> papeis, String emissor, Instant expiraEm) {

}
