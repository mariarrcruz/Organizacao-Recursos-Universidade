package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Token de laboratório: o JWT emitido tem o sub e os perfis pedidos e é aceito pelo serviço de autenticação. */
class DesenvolvimentoTokensControllerTest {

    private final ServicoAutenticacao auth = new ServicoAutenticacao();
    private final DesenvolvimentoTokensController controller = new DesenvolvimentoTokensController(auth);

    @Test
    void tokenEmitidoEAceitoPeloServicoDeAutenticacao() {
        String token = controller.emitir(new DesenvolvimentoTokensController.PedidoToken("resp1",
                List.of("RESPONSAVEL"))).get("token");

        Usuario usuario = auth.autenticar("Bearer " + token);

        assertEquals("resp1", usuario.id());
        assertEquals(Perfil.RESPONSAVEL, usuario.perfis().iterator().next());
    }
}
