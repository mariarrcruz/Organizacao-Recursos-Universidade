package com.organizacao.recursos.servico;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Autenticação JWT (RS256) e refresh token opaco (RF-01; ADR-003; arquitetura §6).
 *
 * <p>A chave RSA é gerada em memória na inicialização (chave de desenvolvimento). A arquitetura
 * prevê chave persistida fora do repositório; isso fica para a camada de configuração.
 */
public class ServicoAutenticacao {

    static final String ISSUER = "organizacao-recursos";
    static final String AUDIENCE = "organizacao-recursos-api";
    static final long REFRESH_SEGUNDOS = 604_800L;

    private static final String HEADER_JSON = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
    private static final Pattern SUB = Pattern.compile("\"sub\":\"([^\"]*)\"");
    private static final Pattern ISS = Pattern.compile("\"iss\":\"([^\"]*)\"");
    private static final Pattern AUD = Pattern.compile("\"aud\":\"([^\"]*)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");
    private static final Pattern ROLES = Pattern.compile("\"roles\":\\[([^\\]]*)\\]");
    private static final Pattern ITEM = Pattern.compile("\"([^\"]*)\"");

    private final PrivateKey chavePrivada;
    private final PublicKey chavePublica;
    private final Map<String, Registro> refreshTokens = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock relogio;

    public ServicoAutenticacao() {
        this(Clock.systemUTC());
    }

    /** Relógio injetável para testes de expiração (RF-01). */
    public ServicoAutenticacao(Clock relogio) {
        this.relogio = relogio;
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            KeyPair par = gerador.generateKeyPair();
            chavePrivada = par.getPrivate();
            chavePublica = par.getPublic();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao gerar chave RSA", e);
        }
    }

    /** Gera JWT. Parâmetros nulos omitem o claim correspondente. */
    public String gerarToken(String sub, Collection<String> roles, Instant expiracao) {
        Map<String, Object> claims = new LinkedHashMap<>();
        if (sub != null) {
            claims.put("sub", sub);
        }
        if (roles != null) {
            claims.put("roles", List.copyOf(roles));
        }
        claims.put("iss", ISSUER);
        claims.put("aud", AUDIENCE);
        claims.put("iat", relogio.instant().getEpochSecond());
        claims.put("exp", expiracao.getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json(claims).getBytes(StandardCharsets.UTF_8));
        String entrada = base64Url(HEADER_JSON) + "." + payload;
        return entrada + "." + assinar(entrada);
    }

    /** Valida cabeçalho "Authorization: Bearer ..." e devolve o usuário. */
    public Usuario autenticar(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ErroAplicacao(401, "Token ausente");
        }
        String[] partes = authorizationHeader.substring("Bearer ".length()).trim().split("\\.", -1);
        if (partes.length != 3) {
            throw new ErroAplicacao(401, "Token malformado");
        }
        if (!assinaturaValida(partes[0] + "." + partes[1], partes[2])) {
            throw new ErroAplicacao(401, "Assinatura inválida");
        }
        String json;
        try {
            json = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ErroAplicacao(401, "Token malformado");
        }

        Long exp = extrairLong(EXP, json);
        if (exp == null || relogio.instant().getEpochSecond() >= exp) {
            throw new ErroAplicacao(401, "Token expirado ou sem expiração");
        }
        if (!ISSUER.equals(extrairString(ISS, json)) || !AUDIENCE.equals(extrairString(AUD, json))) {
            throw new ErroAplicacao(401, "Emissor ou audiência inválidos");
        }
        String sub = extrairString(SUB, json);
        if (sub == null || sub.isBlank()) {
            throw new ErroAplicacao(401, "Claim 'sub' ausente");
        }
        List<String> roles = extrairLista(json);
        if (roles == null || roles.isEmpty()) {
            throw new ErroAplicacao(401, "Claim 'roles' ausente");
        }
        Set<Perfil> perfis = EnumSet.noneOf(Perfil.class);
        for (String r : roles) {
            try {
                perfis.add(Perfil.valueOf(r));
            } catch (IllegalArgumentException e) {
                throw new ErroAplicacao(401, "Perfil desconhecido no token");
            }
        }
        return new Usuario(sub, perfis);
    }

    public synchronized String gerarRefreshToken(String sub, Instant expiracao) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.put(hash(token), new Registro(sub, expiracao, false));
        return token;
    }

    /**
     * Renova: invalida o refresh token usado e emite outro (rotação, ADR-003).
     * Reapresentar um token já usado é rejeitado.
     */
    public synchronized String renovar(String refreshToken) {
        if (refreshToken == null) {
            throw new ErroAplicacao(401, "Refresh token ausente");
        }
        Registro reg = refreshTokens.get(hash(refreshToken));
        if (reg == null || reg.usado() || !relogio.instant().isBefore(reg.expiracao())) {
            throw new ErroAplicacao(401, "Refresh token inválido, usado ou expirado");
        }
        refreshTokens.put(hash(refreshToken), new Registro(reg.sub(), reg.expiracao(), true));
        return gerarRefreshToken(reg.sub(), relogio.instant().plusSeconds(REFRESH_SEGUNDOS));
    }

    // ---------------------------------------------------------------- internos

    private record Registro(String sub, Instant expiracao, boolean usado) {}

    private String assinar(String entrada) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(chavePrivada);
            s.update(entrada.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar token", e);
        }
    }

    private boolean assinaturaValida(String entrada, String assinatura) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(chavePublica);
            s.update(entrada.getBytes(StandardCharsets.US_ASCII));
            byte[] bytes = Base64.getUrlDecoder().decode(assinatura);
            // Exige codificação canônica: o decoder ignora bits de preenchimento do último caractere,
            // o que permitiria alterar o token sem invalidar a assinatura (maleabilidade).
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(assinatura)) {
                return false;
            }
            return s.verify(bytes);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(d);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(Map<String, Object> claims) {
        return claims.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + valorJson(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String valorJson(Object v) {
        if (v instanceof Number) {
            return v.toString();
        }
        if (v instanceof Collection<?> c) {
            return c.stream().map(x -> "\"" + x + "\"").collect(Collectors.joining(",", "[", "]"));
        }
        return "\"" + v + "\"";
    }

    private static String extrairString(Pattern p, String json) {
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static Long extrairLong(Pattern p, String json) {
        Matcher m = p.matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private static List<String> extrairLista(String json) {
        Matcher m = ROLES.matcher(json);
        if (!m.find()) {
            return null;
        }
        Matcher item = ITEM.matcher(m.group(1));
        List<String> valores = new java.util.ArrayList<>();
        while (item.find()) {
            valores.add(item.group(1));
        }
        return valores;
    }
}
