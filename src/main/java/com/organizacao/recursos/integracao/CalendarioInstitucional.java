package com.organizacao.recursos.integracao;

import java.time.LocalDate;
import java.util.List;

/** Porta de integração: eventos institucionais que bloqueiam reservas (RN-09 "conflito com evento institucional"). */
public interface CalendarioInstitucional {

    /**
     * Eventos cujo período intersecta [inicio, fim].
     *
     * @throws ErroIntegracao quando o provedor está indisponível, rejeita a requisição, não existe ou responde fora do contrato
     */
    List<EventoInstitucional> eventosNoPeriodo(LocalDate inicio, LocalDate fim);
}
