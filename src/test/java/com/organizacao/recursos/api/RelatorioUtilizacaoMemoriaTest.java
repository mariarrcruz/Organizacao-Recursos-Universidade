package com.organizacao.recursos.api;

import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.persistencia.ArmazenamentoMemoria;

/** Contrato de {@code /relatorios/utilizacao} com armazenamento em memória. */
class RelatorioUtilizacaoMemoriaTest extends RelatorioUtilizacaoContrato {

    @Override
    protected Armazenamento novoArmazenamento() {
        return new ArmazenamentoMemoria();
    }
}
