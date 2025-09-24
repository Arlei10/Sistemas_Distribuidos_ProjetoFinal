package main.java.modelo;

import java.io.Serializable;

/**
 * Representa as credenciais (usuário e senha) de um cliente para autenticação.
 * É um objeto de transferência de dados (DTO) simples e serializável.
 */
public class Credenciais implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String usuario;
    private final String senha;

    /**
     * Construtor da classe Credenciais.
     * @param usuario O nome de usuário.
     * @param senha A senha.
     */
    public Credenciais(String usuario, String senha) {
        this.usuario = usuario;
        this.senha = senha;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getSenha() {
        return senha;
    }
}