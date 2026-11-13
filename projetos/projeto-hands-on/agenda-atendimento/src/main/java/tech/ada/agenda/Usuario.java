package tech.ada.agenda;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Entity
@UserDefinition
public class Usuario {

  @Id
  @GeneratedValue
  private Long id;

  @Username
  @Column(nullable = false, unique = true)
  private String login;

  @Password
  @Column(nullable = false)
  private String senha;

  @Roles
  @Column(nullable = false)
  private String papeis;

  public static Usuario adicionar(String login, String senha, String papeis) {
    return Usuario.builder()
      .login(login)
      .senha(BcryptUtil.bcryptHash(senha))
      .papeis(papeis)
      .build();
  }

}
