package com.organizacao.recursos.testes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.organizacao.recursos.dominio.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.function.Executable;

/** Dados de teste. Não contém regra de negócio. */
final class Fixtures {

    static final LocalDate DIA = LocalDate.of(2026, 11, 5);

    private Fixtures() {}

    static LocalDateTime t(String hhmm) {
        String[] p = hhmm.split(":");
        return DIA.atTime(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    static Usuario solicitante(String id) { return new Usuario(id, Set.of(Perfil.SOLICITANTE)); }
    static Usuario responsavel(String id) { return new Usuario(id, Set.of(Perfil.RESPONSAVEL)); }
    static Usuario administrador(String id) { return new Usuario(id, Set.of(Perfil.ADMINISTRADOR)); }

    static Recurso sala(String id, boolean restrito) {
        return recurso(id, TipoRecurso.SALA, restrito, StatusRecurso.DISPONIVEL, Set.of());
    }

    static Recurso recurso(String id, TipoRecurso tipo, boolean restrito, StatusRecurso status, Set<String> competencias) {
        return new Recurso(id, tipo, "Recurso " + id, "BLOCO_A", 30, restrito, status, competencias);
    }

    static Reserva reserva(String id, Usuario dono, Recurso recurso, String inicio, String fim, EstadoReserva estado) {
        return new Reserva(id, dono, recurso, t(inicio), t(fim), estado);
    }

    static void assertErro(int status, Executable acao) {
        ErroAplicacao erro = assertThrows(ErroAplicacao.class, acao);
        assertEquals(status, erro.getStatus());
    }

    static void assertRejeitado(Executable acao) {
        assertThrows(ErroAplicacao.class, acao);
    }

    /** Dispara {@code n} ações ao mesmo tempo. Cada resultado é o retorno ou a exceção lançada. */
    static List<Object> simultaneas(int n, IntFunction<Object> acao) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch partida = new CountDownLatch(1);
        List<Future<Object>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                final int idx = i;
                futuros.add(pool.submit(() -> {
                    partida.await();
                    try {
                        return acao.apply(idx);
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            partida.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> f : futuros) {
                resultados.add(f.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    static long sucessos(List<Object> resultados) {
        return resultados.stream().filter(o -> o instanceof Reserva).count();
    }

    static long erros(List<Object> resultados, int status) {
        return resultados.stream()
                .filter(o -> o instanceof ErroAplicacao e && e.getStatus() == status)
                .count();
    }
}
