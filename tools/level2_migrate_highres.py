"""Migra o Level 2 das capturas reduzidas do WhatsApp para as telas em alta.

As 26 imagens antigas foram conferidas como versoes exatas em 610x1356 das
telas de 1220x2712. Este script arquiva as versoes reduzidas, copia as telas
originais, dobra as caixas e atualiza as anotacoes confirmadas. A nova cena
EFR8092 entra como L2-027.

O script e idempotente na parte de copia: se as imagens ja estiverem em alta,
nao dobra as caixas novamente.
"""

from __future__ import annotations

import csv
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "dataset" / "level2"
IMAGES = DATASET / "images"
ARCHIVE = DATASET / "images_lowres"
LABELS = DATASET / "labels.csv"
HIGH = Path(r"C:\Users\Guilherme\Downloads\TREINO IA")

MAPPING = {
    "L2-001.jpeg": "Screenshot_2026-10-21-20-37-28-550_br.com.vigiauto.jpg",
    "L2-002.jpeg": "Screenshot_2026-10-21-20-37-22-525_br.com.vigiauto.jpg",
    "L2-003.jpeg": "Screenshot_2026-10-21-20-37-16-036_br.com.vigiauto.jpg",
    "L2-004.jpeg": "Screenshot_2026-10-21-20-37-07-327_br.com.vigiauto.jpg",
    "L2-005.jpeg": "Screenshot_2026-10-21-20-37-37-201_br.com.vigiauto.jpg",
    "L2-006.jpeg": "Screenshot_2026-10-21-20-36-56-285_br.com.vigiauto.jpg",
    "L2-007.jpeg": "Screenshot_2026-10-21-20-36-50-241_br.com.vigiauto.jpg",
    "L2-008.jpeg": "Screenshot_2026-10-21-20-36-43-519_br.com.vigiauto.jpg",
    "L2-009.jpeg": "Screenshot_2026-10-21-20-36-37-956_br.com.vigiauto.jpg",
    "L2-010.jpeg": "Screenshot_2026-10-21-20-36-32-090_br.com.vigiauto.jpg",
    "L2-011.jpeg": "Screenshot_2026-10-21-20-37-02-850_br.com.vigiauto.jpg",
    "L2-012.jpeg": "Screenshot_2026-10-21-20-35-49-810_br.com.vigiauto.jpg",
    "L2-013.jpeg": "Screenshot_2026-10-21-20-35-27-418_br.com.vigiauto.jpg",
    "L2-014.jpeg": "Screenshot_2026-10-21-20-35-21-390_br.com.vigiauto.jpg",
    "L2-015.jpeg": "Screenshot_2026-10-21-20-35-02-461_br.com.vigiauto.jpg",
    "L2-016.jpeg": "Screenshot_2026-10-21-20-34-46-497_br.com.vigiauto.jpg",
    "L2-017.jpeg": "Screenshot_2026-10-21-20-36-06-179_br.com.vigiauto.jpg",
    "L2-018.jpeg": "Screenshot_2026-10-21-20-34-31-768_br.jpg",
    "L2-019.jpeg": "Screenshot_2026-10-21-20-34-25-174_br.jpg",
    "L2-020.jpeg": "Screenshot_2026-10-21-20-33-58-853_br.com.vigiauto.jpg",
    "L2-021.jpeg": "Screenshot_2026-10-21-20-33-41-537_br.jpg",
    "L2-022.jpeg": "Screenshot_2026-10-21-20-34-38-644_br.com.vigiauto.jpg",
    "L2-023.jpeg": "Screenshot_2026-10-21-20-33-22-866_br.jpg",
    "L2-024.jpeg": "Screenshot_2026-10-21-20-33-15-222_br.jpg",
    "L2-025.jpeg": "Screenshot_2026-10-21-20-33-09-800_br.jpg",
    "L2-026.jpeg": "Screenshot_2026-10-21-20-33-28-287_br.jpg",
}

NEW_ROW = {
    "arquivo": "L2-027.jpeg",
    "arquivo_original": "Screenshot_2026-10-21-20-32-50-758_br.jpg",
    "origem": "TREINO IA",
    "placa": "EFR8092",
    "formato": "Antiga",
    "linhas": "1",
    "tipo": "carro",
    "veiculo": "Hatch prata",
    "observacao": "Traseira; placa antiga; cena nova em alta resolução; detector atual detectou EFR8092",
    "x1": "323",
    "y1": "1338",
    "x2": "825",
    "y2": "1513",
    "rotacao": "0",
    "grupo": "g27",
    "split": "treino",
    "status": "confirmado",
}

UPDATE = {
    "L2-019.jpeg": {
        "observacao": "Traseira; 1001073169.jpg é uma duplicata idêntica desta cena",
    },
    "L2-023.jpeg": {
        "placa": "SWR3C57",
        "formato": "Mercosul",
        "observacao": "Leitura confirmada em alta resolução: SWR3C57; OCR anterior leu KMAJE57",
        "split": "teste",
        "status": "confirmado",
    },
    "L2-026.jpeg": {
        "placa": "FZJ5118",
        "formato": "Antiga",
        "observacao": "Placa completa confirmada em alta resolução: FZJ5118; antes tratada como parcial",
        "split": "teste",
        "status": "confirmado",
    },
}


def main() -> None:
    with Image.open(IMAGES / "L2-001.jpeg") as image:
        current_width = image.width
    scale = 2 if current_width < 1000 else 1
    print(f"Largura atual: {current_width}px; fator das caixas: {scale}")

    if scale == 2:
        ARCHIVE.mkdir(parents=True, exist_ok=True)
        for path in sorted(IMAGES.glob("L2-*.jpeg")):
            shutil.copy2(path, ARCHIVE / path.name)
        print(f"Arquivadas {len(list(ARCHIVE.glob('L2-*.jpeg')))} imagens em {ARCHIVE}")

    for target_name, source_name in MAPPING.items():
        source = HIGH / source_name
        if not source.is_file():
            raise FileNotFoundError(source)
        shutil.copy2(source, IMAGES / target_name)

    shutil.copy2(HIGH / NEW_ROW["arquivo_original"], IMAGES / NEW_ROW["arquivo"])
    print(f"Copiadas {len(MAPPING) + 1} imagens em alta resolução para {IMAGES}")

    with LABELS.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = reader.fieldnames
        rows = list(reader)
    if not fieldnames:
        raise RuntimeError("labels.csv sem cabecalho")

    seen = set()
    for row in rows:
        name = row["arquivo"]
        seen.add(name)
        if name in MAPPING:
            row["arquivo_original"] = MAPPING[name]
        for column in ("x1", "y1", "x2", "y2"):
            row[column] = str(int(row[column]) * scale)
        for column, value in UPDATE.get(name, {}).items():
            row[column] = value

    if NEW_ROW["arquivo"] not in seen:
        rows.append(dict(NEW_ROW))
    rows.sort(key=lambda row: row["arquivo"])

    with LABELS.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)

    print(f"labels.csv atualizado: {len(rows)} linhas")
    for row in rows:
        if row["arquivo"] in {"L2-023.jpeg", "L2-026.jpeg", "L2-027.jpeg"}:
            print(
                f"{row['arquivo']}: {row['placa']} {row['formato']} "
                f"box=({row['x1']},{row['y1']},{row['x2']},{row['y2']}) "
                f"split={row['split']} status={row['status']}"
            )


if __name__ == "__main__":
    main()
