package tech.ada.agenda;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.time.LocalTime;

public class HorarioOcupadoException extends ClientErrorException {

    public HorarioOcupadoException(String agencia, LocalDate data, LocalTime horario) {
        super("Horário " + horario + " de " + data + " na agência " + agencia + " já está ocupado",
                Response.Status.CONFLICT);
    }
}
