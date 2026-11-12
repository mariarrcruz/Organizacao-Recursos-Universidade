package com.organizacao.recursos.servico;

/** Canal de notificação de mudanças de reserva (RF-11). A implementação atual é simulada (ADR-006). */
@FunctionalInterface
public interface Notificador {

    /** Notificador que descarta as mensagens. */
    Notificador NULO = (destinatario, mensagem) -> { };

    void notificar(String destinatario, String mensagem);
}
