#!/usr/bin/env python3
"""Gera o CSV de células da matriz de autorização a partir de docs/seguranca/matriz-autorizacao.md.

Cada célula com código de status vira uma linha: operação, perfil de teste, status esperado.
Células com ❓ (regra não definida), "—" (não se aplica) e linhas condicionadas à configuração são
excluídas, e o motivo é impresso. A matriz continua sendo a fonte; o CSV é gerado, não editado.
"""
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
MATRIZ = RAIZ / "docs/seguranca/matriz-autorizacao.md"
SAIDA = RAIZ / "src/test/resources/seguranca/matriz-autorizacao.csv"

# Linhas que a matriz marca como condicionadas à configuração ou a testes de rota genérica.
EXCLUIDAS = {
    "1": "rota existe só com app.dev.tokens=true (configuração, coberta por AplicacaoContextoTest)",
    "15": "rota genérica: resposta depende da transição, coberta pelas linhas 9 a 14",
}

# Coluna da matriz -> perfis de teste.
PERFIS_POR_COLUNA = {
    "anon": ["anon"],
    "solicitante": ["solicitante-dono", "solicitante-terceiro"],
    "responsavel": ["responsavel-vinculado", "responsavel-nao-vinculado"],
    "administrador": ["administrador"],
}


def perfis_da_parte(coluna: str, condicao: str) -> list[str]:
    """Perfis atingidos por uma parte da célula, conforme a condição escrita (ex.: 'se dono', 'se não')."""
    if coluna == "solicitante":
        if "terceiro" in condicao:
            return ["solicitante-terceiro"]
        if "dono" in condicao:
            return ["solicitante-dono"]
        return PERFIS_POR_COLUNA["solicitante"]
    if coluna == "responsavel":
        if "não" in condicao:
            return ["responsavel-nao-vinculado"]
        if "vinculado" in condicao:
            return ["responsavel-vinculado"]
        return PERFIS_POR_COLUNA["responsavel"]
    return PERFIS_POR_COLUNA[coluna]


def main() -> int:
    linhas_saida = ["operacao;perfil;status;origem"]
    excluidas: list[str] = []
    contagem = 0
    for linha in MATRIZ.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\| (\d+b?) \|", linha)
        if not m:
            continue
        op = m.group(1)
        celulas = [c.strip() for c in linha.strip().strip("|").split("|")]
        if op in EXCLUIDAS:
            excluidas.append(f"linha {op}: {EXCLUIDAS[op]}")
            continue
        for indice, coluna in zip(range(2, 6), ["anon", "solicitante", "responsavel", "administrador"]):
            texto = celulas[indice]
            origem = f"linha {op}, coluna {coluna}"
            if "❓" in texto:
                excluidas.append(f"{origem}: regra não definida (❓) — {texto}")
                continue
            if texto in ("—", ""):
                continue
            for parte in texto.split(";"):
                parte = parte.replace("✅", "").replace("⚠️", "").replace("❌", "").strip()
                st = re.search(r"\b(\d{3})\b", parte)
                if not st:
                    excluidas.append(f"{origem}: sem código — {texto}")
                    continue
                condicao = parte[st.end():]
                for perfil in perfis_da_parte(coluna, condicao):
                    linhas_saida.append(f"{op};{perfil};{st.group(1)};{origem}")
                    contagem += 1
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text("\n".join(linhas_saida) + "\n", encoding="utf-8")
    print(f"{contagem} células geradas em {SAIDA.relative_to(RAIZ)}")
    for e in excluidas:
        print("excluída:", e)
    return 0


if __name__ == "__main__":
    sys.exit(main())
