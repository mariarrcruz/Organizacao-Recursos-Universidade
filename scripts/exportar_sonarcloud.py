"""Exporta o estado do SonarCloud (quality gate, métricas e issues abertas) para JSON versionável.

Uso: python3 scripts/exportar_sonarcloud.py [--pr N] [--saida docs/qualidade/sonarcloud.json]

Sem --pr, consulta a branch principal. A API pública do SonarCloud não exige token para este projeto.
"""
import argparse
import json
import urllib.parse
import urllib.request
from datetime import datetime, timezone

PROJETO = "mariarrcruz_Organizacao-Recursos-Universidade"
BASE = "https://sonarcloud.io/api"
METRICAS = "bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density,reliability_rating,security_rating"


def get(caminho, params):
    url = f"{BASE}/{caminho}?{urllib.parse.urlencode(params)}"
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.load(r)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pr", type=int)
    parser.add_argument("--saida", default="docs/qualidade/sonarcloud.json")
    args = parser.parse_args()
    escopo = {"pullRequest": args.pr} if args.pr else {}
    gate = get("qualitygates/project_status", {"projectKey": PROJETO, **escopo})["projectStatus"]
    medidas = get("measures/component", {"component": PROJETO, "metricKeys": METRICAS, **escopo})
    issues = get("issues/search", {"componentKeys": PROJETO, "resolved": "false", "ps": 100,
                                   "types": "BUG,VULNERABILITY,CODE_SMELL", **escopo})
    analises = get("project_analyses/search", {"project": PROJETO, "ps": 1, **escopo}).get("analyses", [])
    revisao = analises[0].get("revision") if analises else None
    saida = {
        "projeto": PROJETO,
        "commit_analisado": revisao,
        "data_da_analise": analises[0].get("date") if analises else None,
        "escopo": f"pull request {args.pr}" if args.pr else "branch principal",
        "exportado_em": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "quality_gate": gate["status"],
        "condicoes": gate["conditions"],
        "metricas": {m["metric"]: m.get("value") for m in medidas["component"]["measures"]},
        "issues_abertas": [
            {"tipo": i["type"], "severidade": i["severity"], "regra": i["rule"],
             "arquivo": i["component"].split(":")[-1], "linha": i.get("line"), "mensagem": i["message"]}
            for i in issues.get("issues", [])
        ],
    }
    with open(args.saida, "w", encoding="utf-8") as f:
        json.dump(saida, f, ensure_ascii=False, indent=2)
    print(f"gate={saida['quality_gate']} issues_abertas={len(saida['issues_abertas'])} -> {args.saida}")


if __name__ == "__main__":
    main()
