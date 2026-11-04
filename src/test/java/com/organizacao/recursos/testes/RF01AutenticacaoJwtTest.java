package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Requisito RF-01 — Autenticação JWT e autorização por perfil (RFC RF-01, RF-19; plano § 1.4). */
class RF01AutenticacaoJwtTest {

    private final ServicoAutenticacao auth = new ServicoAutenticacao();
    private final Instant futuro = Instant.now().plusSeconds(3600);

    @Test
    @DisplayName("RF-01.1 — JWT válido e não expirado: aceito")
    void rf01_1_tokenValidoEhAceito() {
        // Arrange
        String token = auth.gerarToken("alice", List.of("SOLICITANTE"), futuro);
        // Act
        Usuario usuario = auth.autenticar("Bearer " + token);
        // Assert
        assertEquals("alice", usuario.id());
    }

    @Test
    @DisplayName("RF-01.2 — Sem header Authorization: rejeitado (401)")
    void rf01_2_semHeaderEhRejeitado() {
        // Arrange
        // Act & Assert
        assertErro(401, () -> auth.autenticar(null));
    }

    @Test
    @DisplayName("RF-01.3 — JWT malformado (Bearer xyz): rejeitado (401)")
    void rf01_3_tokenMalformadoEhRejeitado() {
        // Arrange
        // Act & Assert
        assertErro(401, () -> auth.autenticar("Bearer xyz"));
    }

    @Test
    @DisplayName("RF-01.4 — Token expirado: rejeitado (401)")
    void rf01_4_tokenExpiradoEhRejeitado() {
        // Arrange
        String expirado = auth.gerarToken("alice", List.of("SOLICITANTE"), Instant.now().minusSeconds(60));
        // Act & Assert
        assertErro(401, () -> auth.autenticar("Bearer " + expirado));
    }

    @Test
    @DisplayName("RF-01.5 — Assinatura adulterada: rejeitado (401)")
    void rf01_5_assinaturaAdulteradaEhRejeitada() {
        // Arrange
        String valido = auth.gerarToken("alice", List.of("SOLICITANTE"), futuro);
        char ultimo = valido.charAt(valido.length() - 1);
        String adulterado = valido.substring(0, valido.length() - 1) + (ultimo == 'A' ? 'B' : 'A');
        // Act & Assert
        assertErro(401, () -> auth.autenticar("Bearer " + adulterado));
    }

    @Test
    @DisplayName("RF-01.6 — Claim 'sub' ausente: rejeitado (401)")
    void rf01_6_claimsAusentesSaoRejeitados() {
        // Arrange
        String incompleto = auth.gerarToken(null, List.of("SOLICITANTE"), futuro);
        // Act & Assert
        assertErro(401, () -> auth.autenticar("Bearer " + incompleto));
    }

    @Test
    @DisplayName("RF-01.7 — Perfil SOLICITANTE: aceito para ações de Solicitante")
    void rf01_7_perfilSolicitanteEhAceito() {
        // Arrange
        String token = auth.gerarToken("alice", List.of("SOLICITANTE"), futuro);
        // Act
        Usuario usuario = auth.autenticar("Bearer " + token);
        // Assert
        assertTrue(usuario.perfis().contains(Perfil.SOLICITANTE));
    }

    @Test
    @DisplayName("RF-01.8 — Perfil RESPONSAVEL: aceito para ações de Responsável")
    void rf01_8_perfilResponsavelEhAceito() {
        // Arrange
        String token = auth.gerarToken("resp", List.of("RESPONSAVEL"), futuro);
        // Act
        Usuario usuario = auth.autenticar("Bearer " + token);
        // Assert
        assertTrue(usuario.perfis().contains(Perfil.RESPONSAVEL));
    }

    @Test
    @DisplayName("RF-01.9 — Perfil ADMINISTRADOR: aceito para ações de Administrador")
    void rf01_9_perfilAdministradorEhAceito() {
        // Arrange
        String token = auth.gerarToken("adm", List.of("ADMINISTRADOR"), futuro);
        // Act
        Usuario usuario = auth.autenticar("Bearer " + token);
        // Assert
        assertTrue(usuario.perfis().contains(Perfil.ADMINISTRADOR));
    }

    @Test
    @Disabled("Esclarecer: múltiplos perfis aplicam o mais permissivo? (plano RF-01.10 'aplica mais permissivo?').")
    @DisplayName("RF-01.10 — Múltiplos perfis (SOLICITANTE, RESPONSAVEL): aceito, mais permissivo?")
    void rf01_10_multiplosPerfisEhAceito() {
        // Arrange
        String token = auth.gerarToken("alice", List.of("SOLICITANTE", "RESPONSAVEL"), futuro);
        // Act
        Usuario usuario = auth.autenticar("Bearer " + token);
        // Assert
        assertTrue(usuario.perfis().contains(Perfil.RESPONSAVEL));
    }

    @Test
    @DisplayName("RF-01.11 — Token sem claim 'roles': rejeitado (401)")
    void rf01_11_semClaimRolesEhRejeitado() {
        // Arrange
        String semPerfil = auth.gerarToken("alice", null, futuro);
        // Act & Assert
        assertErro(401, () -> auth.autenticar("Bearer " + semPerfil));
    }

    @Test
    @DisplayName("RF-01.12 — Refresh token expirado: renovação rejeitada (401)")
    void rf01_12_refreshTokenExpiradoEhRejeitado() {
        // Arrange
        String refreshExpirado = auth.gerarRefreshToken("alice", Instant.now().minusSeconds(60));
        // Act & Assert
        assertErro(401, () -> auth.renovar(refreshExpirado));
    }
}
