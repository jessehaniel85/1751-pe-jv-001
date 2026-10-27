package tech.ada.pix.chaves;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * Aula 7: usuário guardado no banco. @UserDefinition diz ao quarkus-security-jpa onde
 * estão login, senha (hash BCrypt) e papéis; o próprio Quarkus confere a senha no login.
 * Active Record de propósito: é o formato dos exemplos do guia oficial de security-jpa.
 */
@Entity
@UserDefinition
public class Usuario extends PanacheEntity {

    @Username
    @Column(nullable = false, unique = true)
    public String username;

    @Password
    @Column(nullable = false)
    public String senha;

    /** Papéis separados por vírgula (ex.: "titular"). */
    @Roles
    @Column(nullable = false)
    public String papeis;

    public static void adicionar(String username, String senha, String papeis) {
        Usuario usuario = new Usuario();
        usuario.username = username;
        usuario.senha = BcryptUtil.bcryptHash(senha);
        usuario.papeis = papeis;
        usuario.persist();
    }
}
