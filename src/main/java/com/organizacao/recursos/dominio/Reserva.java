package com.organizacao.recursos.dominio;

import java.time.LocalDateTime;

/** Reserva. Estrutura de dados apenas; transições são responsabilidade do serviço. */
public class Reserva {

    private final String id;
    private final Usuario solicitante;
    private final Recurso recurso;
    private final LocalDateTime inicio;
    private final LocalDateTime fim;
    private final EstadoReserva estado;

    public Reserva(String id, Usuario solicitante, Recurso recurso,
                   LocalDateTime inicio, LocalDateTime fim, EstadoReserva estado) {
        this.id = id;
        this.solicitante = solicitante;
        this.recurso = recurso;
        this.inicio = inicio;
        this.fim = fim;
        this.estado = estado;
    }

    public String getId() { return id; }
    public Usuario getSolicitante() { return solicitante; }
    public Recurso getRecurso() { return recurso; }
    public LocalDateTime getInicio() { return inicio; }
    public LocalDateTime getFim() { return fim; }
    public EstadoReserva getEstado() { return estado; }
}
