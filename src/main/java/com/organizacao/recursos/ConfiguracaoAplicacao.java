package com.organizacao.recursos;

import com.organizacao.recursos.persistencia.ArmazenamentoPostgres;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Montagem dos serviços e do armazenamento PostgreSQL (ADR-017). */
@Configuration
public class ConfiguracaoAplicacao {

    @Bean
    ServicoAutenticacao servicoAutenticacao() {
        return new ServicoAutenticacao();
    }

    @Bean
    ArmazenamentoPostgres armazenamentoPostgres(@Value("${app.banco.url}") String url,
                                                @Value("${app.banco.usuario}") String usuario,
                                                @Value("${app.banco.senha}") String senha) {
        ArmazenamentoPostgres armazenamento = new ArmazenamentoPostgres(url, usuario, senha);
        armazenamento.criarEsquema();
        return armazenamento;
    }

    @Bean
    ServicoReservas servicoReservas(ArmazenamentoPostgres armazenamento) {
        return new ServicoReservas(Clock.systemUTC(), armazenamento);
    }
}
