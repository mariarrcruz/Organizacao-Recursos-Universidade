"""Análise de uma rodada do JMeter (.jtl em CSV) segundo docs/performance/rnf.md.

Uso: python3 scripts/analisar_jtl.py perf/resultados/rodada-02.jtl [--ignorar-segundos 60]

Critérios (RNF-03-P1, RNF-04-P1, RNF-06-P1, RNF-06-P2):
- p95 do tempo de resposta ≤ 500 ms, por operação, após o ramp-up;
- taxa de erro < 1 %, em que 409 é recusa esperada e não conta como erro;
- nenhuma resposta fora de {201, 409} na criação de reserva (nenhum 5xx).

Saída: JSON com os números por operação e o veredito. Código de saída 1 se algum critério falhar.
"""
import argparse
import csv
import json
import math
import sys

OPERACOES = {
    "consulta": "GET disponibilidade do recurso",
    "criacao": "POST criar reserva (disputa)",
}
LIMITE_P95_MS = 500
LIMITE_ERRO = 0.01


def p95(valores):
    if not valores:
        return None
    ordenados = sorted(valores)
    indice = math.ceil(0.95 * len(ordenados)) - 1
    return ordenados[max(indice, 0)]


def analisar(caminho, ignorar_segundos):
    linhas = []
    with open(caminho, newline="", encoding="utf-8") as f:
        for linha in csv.DictReader(f):
            linhas.append(linha)
    if not linhas:
        raise SystemExit(f"Arquivo sem amostras: {caminho}")
    inicio = min(int(l["timeStamp"]) for l in linhas)
    corte = inicio + ignorar_segundos * 1000

    resultado = {"amostras_consideradas_apos_ramp_up": 0, "operacoes": {}, "falhas": []}
    for chave, rotulo in OPERACOES.items():
        amostras = [l for l in linhas if l["label"] == rotulo and int(l["timeStamp"]) >= corte]
        tempos = [int(l["elapsed"]) for l in amostras]
        erros = [l for l in amostras if l["success"] != "true" and l["responseCode"] != "409"]
        codigos = {}
        for l in amostras:
            codigos[l["responseCode"]] = codigos.get(l["responseCode"], 0) + 1
        taxa = len(erros) / len(amostras) if amostras else None
        resultado["amostras_consideradas_apos_ramp_up"] += len(amostras)
        resultado["operacoes"][chave] = {
            "rotulo": rotulo,
            "amostras": len(amostras),
            "p95_ms": p95(tempos),
            "taxa_erro": taxa,
            "codigos": codigos,
        }
        if not amostras:
            resultado["falhas"].append(f"{chave}: sem amostras após o ramp-up")
            continue
        if p95(tempos) > LIMITE_P95_MS:
            resultado["falhas"].append(f"{chave}: p95 {p95(tempos)} ms > {LIMITE_P95_MS} ms")
        if taxa >= LIMITE_ERRO:
            resultado["falhas"].append(f"{chave}: taxa de erro {taxa:.2%} ≥ 1%")

    criacao = resultado["operacoes"].get("criacao", {}).get("codigos", {})
    fora = {c: n for c, n in criacao.items() if c not in ("201", "409")}
    if fora:
        resultado["falhas"].append(f"criacao: respostas fora de 201/409 {fora} (RNF-06-P2)")
    resultado["veredito"] = "ATENDIDO" if not resultado["falhas"] else "NAO ATENDIDO"
    return resultado


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("jtl")
    parser.add_argument("--ignorar-segundos", type=int, default=60)
    args = parser.parse_args()
    r = analisar(args.jtl, args.ignorar_segundos)
    print(json.dumps(r, ensure_ascii=False, indent=2))
    sys.exit(0 if r["veredito"] == "ATENDIDO" else 1)
