package com.organizacao.recursos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TG-05 — testes end-to-end. A aplicação sobe em porta real (RANDOM_PORT), com PostgreSQL 16 em
 * Testcontainers. Nenhum mock: cada passo é uma chamada HTTP de verdade, com o token que o próprio
 * sistema emitiu em /auth/login.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FluxoEndToEndTest {

    private static final String ADMIN_ID = "admin-e2e";
    private static final String SENHA_ADMIN = "senha-admin-e2e-2026";
    private static final String SENHA_USUARIO = "senha-usuario-e2e";
    private static final String INICIO = "2099-06-10T10:00";
    private static final String FIM = "2099-06-10T12:00";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("app.banco.url", POSTGRES::getJdbcUrl);
        registro.add("app.banco.usuario", POSTGRES::getUsername);
        registro.add("app.banco.senha", POSTGRES::getPassword);
        registro.add("app.bootstrap.admin.id", () -> ADMIN_ID);
        registro.add("app.bootstrap.admin.senha", () -> SENHA_ADMIN);
        registro.add("app.dev.tokens", () -> "false");
    }

    @LocalServerPort
    int porta;

    private final HttpClient cliente = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    // ---------------------------------------------------------------- fluxo principal

    @Test
    @DisplayName("TG-05 — fluxo completo: login, pesquisa, reserva restrita, aprovação, uso, retirada, auditoria e notificações")
    void fluxoPrincipalCompleto() throws Exception {
        String s = sufixo();
        String admin = login(ADMIN_ID, SENHA_ADMIN);
        String ana = "ana-" + s;
        String resp = "resp-" + s;
        criarUsuario(admin, ana, "SOLICITANTE");
        criarUsuario(admin, resp, "RESPONSAVEL");

        String sala = "SALA-" + s;
        String bloco = "BLOCO-E2E-" + s;
        criarRecurso(admin, sala, "SALA", true, bloco);
        assertEquals(201, chamar("POST", "/vinculos", admin,
                "{\"responsavelId\":\"" + resp + "\",\"recursoId\":\"" + sala + "\"}").statusCode());

        // Solicitante pesquisa e checa disponibilidade
        String tokAna = login(ana, SENHA_USUARIO);
        JsonNode busca = lerJson(chamar("GET", "/recursos?tipo=SALA&localizacao=" + bloco, tokAna, null));
        assertTrue(contemId(busca, sala), "pesquisa deve encontrar a sala cadastrada");
        JsonNode livre = lerJson(chamar("GET",
                "/disponibilidade?recursoId=" + sala + "&inicio=" + INICIO + "&fim=" + FIM, tokAna, null));
        assertTrue(livre.get("disponivel").asBoolean());

        // Solicitação de recurso restrito nasce SOLICITADA
        JsonNode reserva = lerJson(chamar("POST", "/reservas", tokAna, corpoReserva(sala, INICIO, FIM)));
        String reservaId = reserva.get("id").asText();
        assertEquals("SOLICITADA", reserva.get("estado").asText());
        assertTrue(contemId(lerJson(chamar("GET", "/reservas/minhas", tokAna, null)), reservaId));

        // Responsável vê a solicitação, aprova e o solicitante é notificado
        String tokResp = login(resp, SENHA_USUARIO);
        assertTrue(contemId(lerJson(chamar("GET", "/reservas/pendentes", tokResp, null)), reservaId));
        JsonNode aprovada = lerJson(chamar("POST", "/reservas/" + reservaId + "/aprovacao", tokResp, null));
        assertEquals("APROVADA", aprovada.get("estado").asText());
        assertTrue(textoContem(lerJson(chamar("GET", "/notificacoes", tokAna, null)), "SOLICITADA → APROVADA"));

        // Uso só começa a partir do horário; antes disso a API recusa
        assertEquals(409, chamar("POST", "/reservas/" + reservaId + "/inicio-uso", tokAna, null).statusCode());

        // Conflito de horário na mesma sala é recusado
        assertEquals(409, chamar("POST", "/reservas", tokAna, corpoReserva(sala, "2099-06-10T11:00", "2099-06-10T13:00"))
                .statusCode());

        // Auditoria registra a aprovação para Responsável
        JsonNode auditoria = lerJson(chamar("GET", "/reservas/" + reservaId + "/auditoria", tokResp, null));
        assertTrue(auditoria.size() >= 2, "criação e aprovação devem estar na auditoria");

        // Material: retirada e devolução vinculadas a uma reserva aprovada
        String material = "MAT-" + s;
        criarRecurso(admin, material, "MATERIAL", false, bloco);
        chamar("POST", "/vinculos", admin,
                "{\"responsavelId\":\"" + resp + "\",\"recursoId\":\"" + material + "\"}");
        JsonNode reservaMaterial = lerJson(chamar("POST", "/reservas", tokAna,
                corpoReserva(material, "2099-06-11T09:00", "2099-06-11T10:00")));
        String reservaMat = reservaMaterial.get("id").asText();
        assertEquals("APROVADA", reservaMaterial.get("estado").asText());
        String corpoMov = "{\"reservaId\":\"" + reservaMat + "\",\"materialId\":\"" + material + "\",\"condicao\":\"ok\"}";
        assertEquals(200, chamar("POST", "/movimentacoes/retirada", tokResp, corpoMov).statusCode());
        assertEquals(200, chamar("POST", "/movimentacoes/devolucao", tokResp, corpoMov).statusCode());
        assertEquals(2, lerJson(chamar("GET", "/movimentacoes/" + material, tokResp, null)).size());

        // Administrador não exclui recurso com histórico de reservas
        assertEquals(409, chamar("DELETE", "/recursos/" + sala, admin, null).statusCode());

        // Interface responsiva é servida pela mesma aplicação
        HttpResponse<String> pagina = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/"))
                .header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pagina.statusCode());
        assertTrue(pagina.body().contains("Organização de Recursos"));
    }

    // ---------------------------------------------------------------- casos negativos de ponta a ponta

    @Test
    @DisplayName("TG-05 — sem token, a API responde 401 e não expõe dados")
    void semTokenRecusa() throws Exception {
        HttpResponse<String> r = chamar("GET", "/recursos", null, null);
        assertEquals(401, r.statusCode());
        assertFalse(r.body().contains("Exception"));
    }

    @Test
    @DisplayName("TG-05 — solicitante não cadastra recurso nem usuário (403)")
    void solicitanteNaoAdministra() throws Exception {
        String s = sufixo();
        String admin = login(ADMIN_ID, SENHA_ADMIN);
        String ana = "ana-neg-" + s;
        criarUsuario(admin, ana, "SOLICITANTE");
        String tokAna = login(ana, SENHA_USUARIO);
        assertEquals(403, chamar("POST", "/recursos", tokAna,
                "{\"id\":\"X-" + s + "\",\"tipo\":\"SALA\",\"nome\":\"X\",\"localizacao\":\"B\",\"capacidade\":1,"
                        + "\"restrito\":false,\"status\":\"DISPONIVEL\",\"competencias\":[]}").statusCode());
        assertEquals(403, chamar("POST", "/usuarios", tokAna,
                "{\"id\":\"x-" + s + "\",\"senha\":\"senha-x-2026\",\"perfis\":[\"SOLICITANTE\"]}").statusCode());
    }

    @Test
    @DisplayName("TG-05 — Responsável sem vínculo com o recurso não aprova a reserva (403)")
    void responsavelSemVinculoNaoAprova() throws Exception {
        String s = sufixo();
        String admin = login(ADMIN_ID, SENHA_ADMIN);
        String ana = "ana-v-" + s;
        String respSemVinculo = "resp-sv-" + s;
        criarUsuario(admin, ana, "SOLICITANTE");
        criarUsuario(admin, respSemVinculo, "RESPONSAVEL");
        String sala = "SALA-V-" + s;
        criarRecurso(admin, sala, "SALA", true, "BLOCO-V-" + s);
        String tokAna = login(ana, SENHA_USUARIO);
        String reservaId = lerJson(chamar("POST", "/reservas", tokAna, corpoReserva(sala, "2099-07-01T10:00",
                "2099-07-01T11:00"))).get("id").asText();
        String tokSem = login(respSemVinculo, SENHA_USUARIO);
        assertEquals(403, chamar("POST", "/reservas/" + reservaId + "/aprovacao", tokSem, null).statusCode());
        assertTrue(lerJson(chamar("GET", "/reservas/pendentes", tokSem, null)).isEmpty());
    }

    // ---------------------------------------------------------------- auxiliares

    private String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String login(String id, String senha) throws IOException, InterruptedException {
        HttpResponse<String> r = chamar("POST", "/auth/login", null,
                "{\"id\":\"" + id + "\",\"senha\":\"" + senha + "\"}");
        assertEquals(200, r.statusCode(), "login de " + id);
        return json.readTree(r.body()).get("token").asText();
    }

    private void criarUsuario(String tokenAdmin, String id, String perfil) throws IOException, InterruptedException {
        HttpResponse<String> r = chamar("POST", "/usuarios", tokenAdmin,
                "{\"id\":\"" + id + "\",\"senha\":\"" + SENHA_USUARIO + "\",\"perfis\":[\"" + perfil + "\"]}");
        assertEquals(201, r.statusCode(), "cadastro de " + id);
    }

    private void criarRecurso(String tokenAdmin, String id, String tipo, boolean restrito, String bloco)
            throws IOException, InterruptedException {
        HttpResponse<String> r = chamar("POST", "/recursos", tokenAdmin,
                "{\"id\":\"" + id + "\",\"tipo\":\"" + tipo + "\",\"nome\":\"" + id + "\",\"localizacao\":\"" + bloco
                        + "\",\"capacidade\":30,\"restrito\":" + restrito + ",\"status\":\"DISPONIVEL\","
                        + "\"competencias\":[]}");
        assertEquals(201, r.statusCode(), "cadastro de " + id);
    }

    private static String corpoReserva(String recurso, String inicio, String fim) {
        return "{\"recursoId\":\"" + recurso + "\",\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim + "\"}";
    }

    private HttpResponse<String> chamar(String metodo, String caminho, String token, String corpo)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publicador = corpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(corpo);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .method(metodo, publicador)
                .header("Accept", "application/json");
        if (corpo != null) {
            b.header("Content-Type", "application/json");
        }
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return cliente.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode lerJson(HttpResponse<String> r) throws IOException {
        assertTrue(r.statusCode() >= 200 && r.statusCode() < 300, "status inesperado " + r.statusCode() + ": " + r.body());
        return json.readTree(r.body());
    }

    private static boolean contemId(JsonNode lista, String id) {
        for (JsonNode n : lista) {
            if (id.equals(n.path("id").asText()) || id.equals(n.path("recursoId").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean textoContem(JsonNode lista, String trecho) {
        for (JsonNode n : lista) {
            if (n.path("mensagem").asText().contains(trecho)) {
                return true;
            }
        }
        return false;
    }
}
