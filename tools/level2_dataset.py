#!/usr/bin/env python3
"""Prepara, valida e exporta o dataset Level 2 (offline).

Subcomandos:
    prepare   copia imagens novas, roda o ONNX como pre-anotador e gera
              um template de anotação (não sobrescreve labels.csv);
    validate  valida imagens, caixas, formatos de placa, splits e vazamentos;
    export    gera uma cópia em formato YOLO + recortes para OCR.

Exemplos:
    python tools/level2_dataset.py prepare --source "C:\\...\\TREINO IA"
    python tools/level2_dataset.py validate
    python tools/level2_dataset.py export --force

O dataset não treina nada sozinho. O treinamento é uma etapa externa.
"""

from __future__ import annotations

import argparse
import csv
import json
import shutil
import sys
from pathlib import Path

from PIL import Image

import level2_common as common

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}


def _image_files(folder: Path) -> list[Path]:
    return sorted(
        (
            path
            for path in folder.iterdir()
            if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES
        ),
        key=lambda path: path.name.lower(),
    )


def _next_index(rows: list[dict]) -> int:
    highest = 0
    for row in rows:
        stem = Path(row.get("arquivo", "")).stem
        if stem.startswith("L2-"):
            try:
                highest = max(highest, int(stem.split("-", 1)[1]))
            except ValueError:
                continue
    return highest + 1


def _prelabel_rows(
    source: Path,
    rows: list[dict],
    *,
    detector_path: Path,
    ocr_path: Path,
) -> None:
    import onnxruntime as ort

    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import verify_onnx

    detector = ort.InferenceSession(str(detector_path))
    ocr = ort.InferenceSession(str(ocr_path))
    for row in rows:
        path = source / row["arquivo_original"]
        result = verify_onnx.run(path, detector, ocr)
        detections = result.get("detections", [])
        if not detections:
            row["observacao"] = "Prelabel ONNX: nenhuma deteccao; anotar manualmente"
            continue
        best = max(detections, key=lambda item: item["detector_confidence"])
        left, top, right, bottom = best["box"]
        row.update(
            placa=common.normalize_plate(best["raw_text"]),
            formato=common.plate_format(best["raw_text"]),
            x1=left,
            y1=top,
            x2=right,
            y2=bottom,
            observacao=(
                "Prelabel ONNX: detector={:.3f}, OCR={:.3f}; "
                "revisar placa e caixa manualmente"
            ).format(best["detector_confidence"], best["ocr_confidence"]),
        )


def prepare(args: argparse.Namespace) -> int:
    source = Path(args.source)
    if not source.is_dir():
        print(f"Pasta de origem nao encontrada: {source}", file=sys.stderr)
        return 2

    destination = Path(args.dest) if args.dest else common.DATASET_DIR
    images_dir = destination / "images"
    images_dir.mkdir(parents=True, exist_ok=True)
    labels_path = destination / "labels.csv"
    existing = common.read_labels(labels_path) if labels_path.is_file() else []
    known = {row["arquivo_original"]: row for row in existing}

    files = _image_files(source)
    if not files:
        print(f"Nenhuma imagem encontrada em {source}", file=sys.stderr)
        return 2

    new_rows: list[dict] = []
    copied = 0
    index = _next_index(existing)
    for path in files:
        if path.name in known:
            target = images_dir / known[path.name]["arquivo"]
            if not target.exists():
                shutil.copy2(path, target)
                copied += 1
            continue
        target_name = f"L2-{index:03d}{path.suffix.lower()}"
        index += 1
        target = images_dir / target_name
        shutil.copy2(path, target)
        copied += 1
        group = f"g{int(target.stem.split('-', 1)[1]):03d}"
        new_rows.append(
            {
                "arquivo": target_name,
                "arquivo_original": path.name,
                "origem": source.name,
                "placa": "",
                "formato": "Indefinido",
                "linhas": 1,
                "tipo": "",
                "veiculo": "",
                "observacao": "Nova imagem; anotar manualmente",
                "x1": 0,
                "y1": 0,
                "x2": 0,
                "y2": 0,
                "rotacao": 0,
                "grupo": group,
                "split": "ignorar",
                "status": "prelabel",
            }
        )

    if new_rows and not args.no_prelabel:
        if not args.detector.is_file() or not args.ocr.is_file():
            print("Modelo ONNX nao encontrado; template sera gerado sem prelabel.", file=sys.stderr)
        else:
            _prelabel_rows(
                source,
                new_rows,
                detector_path=args.detector,
                ocr_path=args.ocr,
            )

    template = destination / "labels.template.csv"
    if new_rows:
        common.write_labels(new_rows, template)
        print(f"Template de anotacao: {template}")
        print(f"Novas imagens: {len(new_rows)}")
        print("Revise o template e mescle as linhas confirmadas em labels.csv.")
    else:
        print("Nenhuma imagem nova; labels.csv nao foi alterado.")

    print(f"Imagens copiadas: {copied}")
    print(f"Total de imagens em {images_dir}: {len(list(images_dir.glob('*')))}")
    return 0


def validate(args: argparse.Namespace) -> int:
    labels_path = Path(args.labels) if args.labels else common.LABELS_PATH
    images_dir = Path(args.images) if args.images else common.IMAGES_DIR
    try:
        rows = common.read_labels(labels_path)
    except (OSError, ValueError) as error:
        print(f"Falha ao ler {labels_path}: {error}", file=sys.stderr)
        return 2

    errors: list[str] = []
    warnings: list[str] = []
    exact_hashes: dict[str, str] = {}
    perceptual_hashes: list[tuple[str, int]] = []
    group_splits: dict[str, set[str]] = {}
    plate_splits: dict[str, set[str]] = {}
    plate_groups: dict[str, set[str]] = {}
    plate_widths: list[float] = []
    plate_heights: list[float] = []
    sharpness_values: list[float] = []

    for number, row in enumerate(rows, 1):
        name = row["arquivo"]
        prefix = f"linha {number} ({name})"
        path = images_dir / name
        if not path.is_file():
            errors.append(f"{prefix}: imagem nao encontrada em {images_dir}")
            continue
        try:
            with Image.open(path) as opened:
                image = opened.convert("RGB")
        except OSError as error:
            errors.append(f"{prefix}: falha ao abrir imagem: {error}")
            continue

        width, height = image.size
        x1, y1, x2, y2 = row["x1"], row["y1"], row["x2"], row["y2"]
        if x1 <= 0 and y1 <= 0 and x2 <= 0 and y2 <= 0 and row["status"] == "prelabel":
            warnings.append(f"{prefix}: caixa pendente de anotacao")
        elif not (0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height):
            errors.append(
                f"{prefix}: caixa invalida ({x1},{y1},{x2},{y2}) para imagem {width}x{height}"
            )
        else:
            crop = image.crop((x1, y1, x2, y2))
            plate_widths.append(float(crop.width))
            plate_heights.append(float(crop.height))
            sharpness_values.append(common.sharpness(crop))
            if crop.width < 32 or crop.height < 12:
                warnings.append(
                    f"{prefix}: recorte muito pequeno ({crop.width}x{crop.height}); "
                    "OCR pode falhar"
                )
            aspect = crop.width / crop.height if crop.height else 0
            if aspect < 1.0 or aspect > 8.0:
                warnings.append(f"{prefix}: proporcao incomum da caixa ({aspect:.2f})")

        plate = common.normalize_plate(row["placa"])
        if row["status"] == "confirmado":
            if not plate:
                errors.append(f"{prefix}: placa vazia em linha confirmada")
            elif not common.format_is_valid(plate, row["formato"]):
                errors.append(
                    f"{prefix}: placa {plate} nao confere com formato {row['formato']}"
                )
        elif row["status"] in {"revisar", "parcial", "prelabel"}:
            warnings.append(f"{prefix}: status {row['status']} (nao entra no treino/benchmark exato)")
        else:
            errors.append(f"{prefix}: status desconhecido {row['status']!r}")

        if row["formato"] not in {"Mercosul", "Antiga", "Indefinido"}:
            errors.append(f"{prefix}: formato desconhecido {row['formato']!r}")
        if row["split"] not in common.SPLITS:
            errors.append(f"{prefix}: split desconhecido {row['split']!r}")
        if row["linhas"] not in {1, 2}:
            errors.append(f"{prefix}: linhas deve ser 1 ou 2, nao {row['linhas']}")
        if row["rotacao"] not in {0, 90, 180, 270}:
            errors.append(f"{prefix}: rotacao deve ser 0/90/180/270, nao {row['rotacao']}")

        group = row["grupo"].strip()
        if not group:
            errors.append(f"{prefix}: grupo vazio")
        else:
            group_splits.setdefault(group, set()).add(row["split"])
        if plate:
            plate_splits.setdefault(plate, set()).add(row["split"])
            plate_groups.setdefault(plate, set()).add(group)

        digest = common.sha256(path)
        if digest in exact_hashes:
            errors.append(f"{prefix}: imagem identica a {exact_hashes[digest]}")
        else:
            exact_hashes[digest] = name
        perceptual = common.average_hash(image)
        for other_name, other_hash in perceptual_hashes:
            if common.hamming_distance(perceptual, other_hash) <= 2:
                warnings.append(f"{prefix}: imagem visualmente parecida com {other_name}")
        perceptual_hashes.append((name, perceptual))

    for group, splits in sorted(group_splits.items()):
        used = splits - {"ignorar"}
        if len(used) > 1:
            errors.append(
                f"grupo {group}: aparece em mais de um split ({', '.join(sorted(used))}); "
                "mantenha a mesma cena junta"
            )
    for plate, splits in sorted(plate_splits.items()):
        used = splits - {"ignorar"}
        if len(used) > 1:
            warnings.append(
                f"placa {plate}: aparece em splits diferentes ({', '.join(sorted(used))}); "
                "verifique vazamento ou clone"
            )
    for plate, groups in sorted(plate_groups.items()):
        if len(groups) > 1:
            warnings.append(
                f"placa {plate}: aparece em grupos diferentes ({', '.join(sorted(groups))}); "
                "pode ser clone ou erro de leitura"
            )

    summary = {
        "labels": str(labels_path),
        "images": str(images_dir),
        "total": len(rows),
        "splits": common.split_counts(rows),
        "status": common.status_counts(rows),
        "plate_formats": {
            key: sum(1 for row in rows if row["formato"] == key)
            for key in ("Mercosul", "Antiga", "Indefinido")
        },
        "plate_width_px": {
            "mean": round(common.mean(plate_widths), 2),
            "p50": round(common.percentile(plate_widths, 0.5), 2),
            "p90": round(common.percentile(plate_widths, 0.9), 2),
            "min": round(min(plate_widths), 2) if plate_widths else 0,
            "max": round(max(plate_widths), 2) if plate_widths else 0,
        },
        "plate_height_px": {
            "mean": round(common.mean(plate_heights), 2),
            "min": round(min(plate_heights), 2) if plate_heights else 0,
            "max": round(max(plate_heights), 2) if plate_heights else 0,
        },
        "sharpness": {
            "mean": round(common.mean(sharpness_values), 2),
            "p10": round(common.percentile(sharpness_values, 0.1), 2),
        },
        "errors": errors,
        "warnings": warnings,
    }

    if args.json_out:
        Path(args.json_out).write_text(
            json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    print(f"Dataset: {labels_path}")
    print(
        "Total: {total} | splits: {splits} | status: {status}".format(
            total=summary["total"],
            splits=", ".join(f"{k}={v}" for k, v in summary["splits"].items()),
            status=", ".join(f"{k}={v}" for k, v in summary["status"].items()),
        )
    )
    print(
        "Placas: largura media={mean}px (p50={p50}, min={min}, max={max}); "
        "altura media={hmean}px".format(
            mean=summary["plate_width_px"]["mean"],
            p50=summary["plate_width_px"]["p50"],
            min=summary["plate_width_px"]["min"],
            max=summary["plate_width_px"]["max"],
            hmean=summary["plate_height_px"]["mean"],
        )
    )
    if errors:
        print(f"\nERROS ({len(errors)}):")
        for error in errors:
            print(f"  - {error}")
    if warnings:
        print(f"\nAVISOS ({len(warnings)}):")
        for warning in warnings:
            print(f"  - {warning}")
    if not errors and not warnings:
        print("\nOK: nenhum erro ou aviso.")
    if errors:
        return 1
    if args.strict and warnings:
        return 1
    return 0


def _yolo_line(row: dict, width: int, height: int) -> str:
    x_center = ((row["x1"] + row["x2"]) / 2.0) / width
    y_center = ((row["y1"] + row["y2"]) / 2.0) / height
    box_width = (row["x2"] - row["x1"]) / width
    box_height = (row["y2"] - row["y1"]) / height
    return f"0 {x_center:.6f} {y_center:.6f} {box_width:.6f} {box_height:.6f}\n"


def export(args: argparse.Namespace) -> int:
    labels_path = Path(args.labels) if args.labels else common.LABELS_PATH
    images_dir = Path(args.images) if args.images else common.IMAGES_DIR
    destination = Path(args.dest) if args.dest else common.EXPORT_DIR
    rows = common.read_labels(labels_path)
    selected = []
    for row in rows:
        if row["split"] == "ignorar":
            continue
        if row["status"] == "confirmado":
            selected.append(row)
        elif row["status"] == "revisar" and args.include_unconfirmed:
            selected.append(row)
        elif row["status"] == "parcial" and args.include_parcial:
            selected.append(row)
    if not selected:
        print("Nenhuma linha selecionada para exportacao.", file=sys.stderr)
        return 1
    if destination.exists():
        if not args.force:
            print(
                f"Destino existente: {destination}; use --force para recriar.",
                file=sys.stderr,
            )
            return 2
        shutil.rmtree(destination)

    yolo_images = destination / "yolo" / "images"
    yolo_labels = destination / "yolo" / "labels"
    ocr_images = destination / "ocr" / "images"
    for split in ("treino", "validacao", "teste"):
        (yolo_images / split).mkdir(parents=True, exist_ok=True)
        (yolo_labels / split).mkdir(parents=True, exist_ok=True)
        (ocr_images / split).mkdir(parents=True, exist_ok=True)

    yolo_done: set[str] = set()
    manifest: list[dict] = []
    counts = {split: 0 for split in ("treino", "validacao", "teste")}
    for row in selected:
        split = row["split"]
        name = row["arquivo"]
        path = images_dir / name
        with Image.open(path) as opened:
            image = opened.convert("RGB")
        width, height = image.size
        if name not in yolo_done:
            shutil.copy2(path, yolo_images / split / name)
            yolo_done.add(name)
        with (yolo_labels / split / f"{Path(name).stem}.txt").open("a", encoding="utf-8") as handle:
            handle.write(_yolo_line(row, width, height))

        crop = common.crop_plate(image, row, margin=args.crop_margin)
        crop = common.rotate_crop(crop, row["rotacao"])
        crop_name = f"{Path(name).stem}__{common.normalize_plate(row['placa'])}.png"
        crop_path = ocr_images / split / crop_name
        crop.save(crop_path)
        manifest.append(
            {
                "arquivo": name,
                "placa": common.normalize_plate(row["placa"]),
                "split": split,
                "formato": row["formato"],
                "linhas": row["linhas"],
                "tipo": row["tipo"],
                "rotacao": row["rotacao"],
                "status": row["status"],
                "crop": str(crop_path.relative_to(destination)).replace("\\", "/"),
                "x1": row["x1"],
                "y1": row["y1"],
                "x2": row["x2"],
                "y2": row["y2"],
            }
        )
        counts[split] += 1

    manifest_path = destination / "ocr" / "manifest.csv"
    with manifest_path.open("w", encoding="utf-8", newline="") as handle:
        fields = [
            "arquivo",
            "placa",
            "split",
            "formato",
            "linhas",
            "tipo",
            "rotacao",
            "status",
            "crop",
            "x1",
            "y1",
            "x2",
            "y2",
        ]
        writer = csv.DictWriter(handle, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(manifest)

    readme = destination / "README.md"
    readme.write_text(
        "# Exportação Level 2\n\n"
        "Gerado por `tools/level2_dataset.py export`.\n\n"
        f"- Linhas exportadas: {len(manifest)}\n"
        f"- Treino: {counts['treino']}\n"
        f"- Validação: {counts['validacao']}\n"
        f"- Teste: {counts['teste']}\n"
        f"- Recortes OCR: `ocr/images/`\n"
        f"- Manifesto OCR: `ocr/manifest.csv`\n"
        f"- Formato YOLO: `yolo/images/` e `yolo/labels/`\n\n"
        "O conjunto ainda é pequeno para treinar um detector do zero. Use a "
        "exportação como insumo de um treinamento externo e valide o modelo "
        "contra o conjunto de teste antes de embarcar qualquer ONNX.\n",
        encoding="utf-8",
    )
    print(f"Exportacao criada em {destination}")
    print(f"Linhas: {len(manifest)} | {counts}")
    print(f"Manifesto: {manifest_path}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare_parser = subparsers.add_parser("prepare", help="Copia novas imagens e gera template.")
    prepare_parser.add_argument(
        "--source",
        default=r"C:\Users\Guilherme\Downloads\TREINO IA",
        help="Pasta com as imagens originais.",
    )
    prepare_parser.add_argument("--dest", type=Path, help="Destino (padrao: dataset/level2).")
    prepare_parser.add_argument(
        "--detector",
        type=Path,
        default=common.ROOT / "app/src/main/assets/models/detector.onnx",
    )
    prepare_parser.add_argument(
        "--ocr",
        type=Path,
        default=common.ROOT / "app/src/main/assets/models/ocr.onnx",
    )
    prepare_parser.add_argument(
        "--no-prelabel",
        action="store_true",
        help="Nao roda o ONNX; apenas copia e cria o template vazio.",
    )
    prepare_parser.set_defaults(func=prepare)

    validate_parser = subparsers.add_parser("validate", help="Valida o dataset.")
    validate_parser.add_argument("--labels", type=Path, help="CSV de anotacoes.")
    validate_parser.add_argument("--images", type=Path, help="Pasta de imagens.")
    validate_parser.add_argument("--json-out", type=Path, help="Salva o relatorio JSON.")
    validate_parser.add_argument(
        "--strict",
        action="store_true",
        help="Falha tambem em avisos (imagens pequenas, placas duplicadas etc.).",
    )
    validate_parser.set_defaults(func=validate)

    export_parser = subparsers.add_parser("export", help="Exporta YOLO + recortes OCR.")
    export_parser.add_argument("--labels", type=Path, help="CSV de anotacoes.")
    export_parser.add_argument("--images", type=Path, help="Pasta de imagens.")
    export_parser.add_argument("--dest", type=Path, help="Destino (padrao: dataset/level2/export).")
    export_parser.add_argument("--force", action="store_true", help="Recria o destino.")
    export_parser.add_argument(
        "--include-unconfirmed",
        action="store_true",
        help="Inclui linhas com status revisar.",
    )
    export_parser.add_argument(
        "--include-parcial",
        action="store_true",
        help="Inclui linhas com status parcial (nao recomendado).",
    )
    export_parser.add_argument(
        "--crop-margin",
        type=int,
        default=2,
        help="Margem em pixels ao redor da placa nos recortes OCR.",
    )
    export_parser.set_defaults(func=export)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
