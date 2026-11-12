package com.organizacao.recursos;

import com.organizacao.recursos.persistencia.ArmazenamentoPostgres;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoMovimentacoes;
import com.organizacao.recursos.servico.ServicoRelatorios;
import com.organizacao.recursos.servico.RegistroNotificacoes;
import com.organizacao.recursos.servico.ServicoReservas;
import com.organizacao.recursos.servico.ServicoUsuarios;
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

    /**
     * Credenciais de login. Se {@code app.bootstrap.admin.senha} estiver definido e ainda não houver
     * administrador, semeia o primeiro administrador (vindo de variável de ambiente, nunca do código).
     */
    @Bean
    ServicoUsuarios servicoUsuarios(@Value("${app.bootstrap.admin.id:admin}") String adminId,
                                    @Value("${app.bootstrap.admin.senha:}") String adminSenha) {
        ServicoUsuarios usuarios = new ServicoUsuarios();
        if (!adminSenha.isBlank()) {
            usuarios.semearAdministrador(adminId, adminSenha);
        }
        return usuarios;
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
    RegistroNotificacoes registroNotificacoes() {
        return new RegistroNotificacoes(Clock.systemUTC());
    }

    @Bean
    ServicoReservas servicoReservas(ArmazenamentoPostgres armazenamento, RegistroNotificacoes notificacoes) {
        ServicoReservas servico = new ServicoReservas(Clock.systemUTC(), armazenamento);
        servico.definirNotificador(notificacoes);
        return servico;
    }

    @Bean
    ServicoMovimentacoes servicoMovimentacoes(ServicoReservas reservas) {
        return new ServicoMovimentacoes(reservas);
    }

    @Bean
    ServicoRelatorios servicoRelatorios(ServicoReservas reservas, ServicoMovimentacoes movimentacoes) {
        return new ServicoRelatorios(reservas, movimentacoes);
    }
}
