package tech.ada.calendario;

import java.time.LocalDate;

/**
 * Aula 5: util = false em feriado e fim de semana; motivo diz qual.
 */
public record DiaDoCalendario(LocalDate data, boolean util, String motivo) {
}
