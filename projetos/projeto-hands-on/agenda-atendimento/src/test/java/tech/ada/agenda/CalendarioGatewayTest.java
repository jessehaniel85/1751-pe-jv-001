package tech.ada.agenda;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CalendarioGatewayTest {

  @InjectMock
  @RestClient
  CalendarioClient calendarioClient;

  @Test
  void consultar() {
  }

  @Test
  void calendarioIndisponivel() {
  }
}
