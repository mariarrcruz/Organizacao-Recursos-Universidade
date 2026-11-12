package com.organizacao.recursos.servico;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Notificação simulada (RF-11; ADR-006): registra cada mensagem no log e a guarda em memória para consulta
 * pelo próprio destinatário. Não envia e-mail nem chama API externa.
 */
public final class RegistroNotificacoes implements Notificador {

    /** Mensagem registrada, com o instante de emissão. */
    public record Notificacao(String destinatario, String mensagem, Instant instante) {}

    private static final Logger LOG = Logger.getLogger(RegistroNotificacoes.class.getName());

    private final Clock relogio;
    private final List<Notificacao> enviadas = new ArrayList<>();

    public RegistroNotificacoes(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    public synchronized void notificar(String destinatario, String mensagem) {
        LOG.info(() -> "[NOTIFICACAO simulada] destinatario=" + destinatario + " mensagem=" + mensagem);
        enviadas.add(new Notificacao(destinatario, mensagem, relogio.instant()));
    }

    /** Notificações do destinatário, da mais antiga para a mais recente. */
    public synchronized List<Notificacao> de(String destinatario) {
        return enviadas.stream().filter(n -> n.destinatario().equals(destinatario)).toList();
    }
}
