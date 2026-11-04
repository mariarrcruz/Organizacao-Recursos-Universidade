package com.organizacao.recursos.testes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Lacunas de risco de ServicoAutenticacao (RF-01, ADR-003): expiração, rotação e validação de claims. */
class CoberturaRiscoAutenticacaoTest {

    private static final Instant BASE = Instant.parse("2026-11-03T12:00:00Z");
    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    /** Relógio controlável pelo teste. */
    static final class RelogioAjustavel extends Clock {
        private Instant instante;

        RelogioAjustavel(Instant instante) {
            this.instante = instante;
        }

        void ajustar(Instant novo) {
            this.instante = novo;
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instante; }
    }

    private final RelogioAjustavel relogio = new RelogioAjustavel(BASE);
    private final ServicoAutenticacao auth = new ServicoAutenticacao(relogio);

    @ParameterizedTest(name = "cabeçalho = {0}")
    @NullSource
    @ValueSource(strings = {"", "Basic abc", "bearer abc"})
    @DisplayName("Cabeçalho ausente ou fora do esquema 'Bearer ' é 401")
    void cabecalhoAusenteOuInvalidoRejeita401(String cabecalho) {
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.autenticar(cabecalho));
        assertEquals(401, e.getStatus());
    }

    @ParameterizedTest(name = "início da validação = expiração {0} s → aceito = {1}")
    @CsvSource({"-1,true", "0,false", "1,false"})
    @DisplayName("Token é aceito até um segundo antes da expiração e recusado a partir dela (trinca -1, 0, +1 s)")
    void expiracaoDoAccessTokenNaFronteira(long segundos, boolean aceito) {
        Instant exp = BASE.plusSeconds(3600);
        String token = auth.gerarToken("alice", List.of("SOLICITANTE"), exp);
        relogio.ajustar(exp.plusSeconds(segundos));
        if (aceito) {
            Usuario u = auth.autenticar("Bearer " + token);
            assertEquals("alice", u.id());
            assertEquals(Perfil.SOLICITANTE, u.perfis().iterator().next());
        } else {
            ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.autenticar("Bearer " + token));
            assertEquals(401, e.getStatus());
        }
    }

    @ParameterizedTest(name = "início da renovação = expiração do refresh {0} s → aceito = {1}")
    @CsvSource({"-1,true", "0,false", "1,false"})
    @DisplayName("Refresh token é aceito até um segundo antes da expiração e recusado a partir dela (trinca -1, 0, +1 s)")
    void expiracaoDoRefreshNaFronteira(long segundos, boolean aceito) {
        Instant exp = BASE.plusSeconds(3600);
        String refresh = auth.gerarRefreshToken("alice", exp);
        relogio.ajustar(exp.plusSeconds(segundos));
        if (aceito) {
            assertNotNull(auth.renovar(refresh));
        } else {
            ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.renovar(refresh));
            assertEquals(401, e.getStatus());
        }
    }

    @Test
    @DisplayName("Renovar sem refresh token é 401")
    void renovarSemRefreshRejeita401() {
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.renovar(null));
        assertEquals(401, e.getStatus());
    }

    @Test
    @DisplayName("Renovar refresh desconhecido é 401")
    void renovarRefreshDesconhecidoRejeita401() {
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.renovar("nao-existe"));
        assertEquals(401, e.getStatus());
    }

    @Test
    @DisplayName("Rotação: refresh usado não pode ser reapresentado; o novo funciona (ADR-003)")
    void rotacaoRejeitaReusoDoRefresh() {
        String r1 = auth.gerarRefreshToken("alice", BASE.plusSeconds(3600));
        String r2 = auth.renovar(r1);
        assertNotNull(r2);
        assertNotEquals(r1, r2);
        ErroAplicacao reuso = assertThrows(ErroAplicacao.class, () -> auth.renovar(r1));
        assertEquals(401, reuso.getStatus());
        assertNotNull(auth.renovar(r2));
    }

    @ParameterizedTest(name = "sub = [{0}]")
    @ValueSource(strings = {"", " "})
    @DisplayName("Claim 'sub' vazio ou em branco é 401")
    void subVazioOuEmBrancoRejeita401(String sub) {
        String token = auth.gerarToken(sub, List.of("SOLICITANTE"), BASE.plusSeconds(3600));
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.autenticar("Bearer " + token));
        assertEquals(401, e.getStatus());
    }

    @Test
    @DisplayName("Token sem claim 'roles' é 401")
    void tokenSemRolesRejeita401() {
        String token = auth.gerarToken("alice", null, BASE.plusSeconds(3600));
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.autenticar("Bearer " + token));
        assertEquals(401, e.getStatus());
    }

    @Test
    @DisplayName("Token com lista de roles vazia é 401")
    void tokenComRolesVaziasRejeita401() {
        String token = auth.gerarToken("alice", List.of(), BASE.plusSeconds(3600));
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.autenticar("Bearer " + token));
        assertEquals(401, e.getStatus());
    }

    @Test
    @DisplayName("Token com perfil desconhecido é 401")
    void tokenComPerfilDesconhecidoRejeita401() {
        String token = auth.gerarToken("alice", List.of("SUPERUSUARIO"), BASE.plusSeconds(3600));
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.autenticar("Bearer " + token));
        assertEquals(401, e.getStatus());
    }

    @Test
    @DisplayName("Assinatura com codificação não canônica (maleabilidade) é 401")
    void assinaturaNaoCanonicaRejeita401() {
        String token = auth.gerarToken("alice", List.of("SOLICITANTE"), BASE.plusSeconds(3600));
        int ultimo = token.lastIndexOf('.');
        String assinatura = token.substring(ultimo + 1);
        char c = assinatura.charAt(assinatura.length() - 1);
        // Troca só o bit de preenchimento do último caractere: os bytes decodificados não mudam.
        char alterado = ALFABETO.charAt(ALFABETO.indexOf(c) ^ 1);
        String adulterada = token.substring(0, ultimo + 1) + assinatura.substring(0, assinatura.length() - 1) + alterado;
        assertArrayEquals(
                Base64.getUrlDecoder().decode(assinatura),
                Base64.getUrlDecoder().decode(adulterada.substring(ultimo + 1)),
                "pré-condição: a alteração não pode mudar os bytes da assinatura");
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.autenticar("Bearer " + adulterada));
        assertEquals(401, e.getStatus());
    }

    @Test
    @DisplayName("Assinatura com caracteres fora do alfabeto Base64 é 401")
    void assinaturaComCaracteresInvalidosRejeita401() {
        String token = auth.gerarToken("alice", List.of("SOLICITANTE"), BASE.plusSeconds(3600));
        String semAssinatura = token.substring(0, token.lastIndexOf('.') + 1);
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> auth.autenticar("Bearer " + semAssinatura + "!!!"));
        assertEquals(401, e.getStatus());
    }
}
