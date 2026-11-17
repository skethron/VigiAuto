#!/usr/bin/env python3
"""Benchmark offline do pipeline ONNX contra o dataset Level 2.

Compara três coisas separadamente:

1. detector: a caixa prevista tem IoU suficiente com a caixa anotada?
2. pipeline: OCR feito no recorte do detector, sem rotação;
3. OCR isolado: OCR feito no recorte anotado (com a rotação de correção).

O item 3 mostra o limite do OCR quando a caixa é perfeita. O item 2 é o que o
app faz hoje. O benchmark também testa uma TTA de rotação apenas para medir o
ganho potencial; isso não altera o aplicativo.

Uso:
    python tools/level2_benchmark.py
    python tools/level2_benchmark.py --include-unconfirmed --json-out ...
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

import level2_common as common

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_onnx


def _ocr_crop(
    crop: Image.Image,
    session: ort.InferenceSession,
) -> tuple[str, float, float]:
    resized = crop.resize(
        (verify_onnx.OCR_WIDTH, verify_onnx.OCR_HEIGHT),
        Image.BILINEAR,
    )
    output = session.run(
        None,
        {session.get_inputs()[0].name: np.asarray(resized, dtype=np.uint8)[None]},
    )[0][0]
    text, confidence, minimum = verify_onnx.decode_ocr(output)
    return common.normalize_plate(text), confidence, minimum


PRIMARY_RECHECK_MIN_CONFIDENCE = 0.50
FALLBACK_OVERRIDE_MIN_CONFIDENCE = 0.90


def _ocr_reading(
    crop: Image.Image,
    session: ort.InferenceSession,
) -> dict:
    text, confidence, minimum = _ocr_crop(crop, session)
    return {
        "text": text,
        "confidence": confidence,
        "minimum": minimum,
        "normalized": common.app_normalize(text) or "",
    }


def _should_try_fallback(primary: dict) -> bool:
    """Reproduz OcrFallbackPolicy.shouldTryFallback."""
    if not primary["text"]:
        return False
    return not primary["normalized"] or primary["minimum"] < PRIMARY_RECHECK_MIN_CONFIDENCE


def _select_reading(primary: dict, fallback: dict | None) -> tuple[dict, str]:
    """Reproduz OcrFallbackPolicy.preferFallback."""
    if fallback is None:
        return primary, "primary"
    if not primary["normalized"]:
        if fallback["normalized"]:
            return fallback, "fallback"
        return primary, "primary"
    if primary["normalized"] == fallback["normalized"]:
        return primary, "primary"
    if not fallback["normalized"]:
        return primary, "primary"
    if fallback["confidence"] < FALLBACK_OVERRIDE_MIN_CONFIDENCE:
        return primary, "primary"

    first = primary["normalized"]
    second = fallback["normalized"]
    differences = sum(a != b for a, b in zip(first, second))
    if differences != 1:
        return primary, "primary"
    if common.is_visually_equivalent(first, second):
        return primary, "primary"
    return fallback, "fallback"


def _detect(
    image: Image.Image,
    detector: ort.InferenceSession,
    min_score: float,
) -> list[dict]:
    tensor, scale, pad_x, pad_y = verify_onnx.letterbox(image)
    rows = detector.run(None, {detector.get_inputs()[0].name: tensor})[0]
    detections: list[dict] = []
    for row in rows:
        if len(row) < 7 or float(row[6]) < min_score:
            continue
        x1 = int((float(row[1]) - pad_x) / scale)
        y1 = int((float(row[2]) - pad_y) / scale)
        x2 = int((float(row[3]) - pad_x) / scale)
        y2 = int((float(row[4]) - pad_y) / scale)
        left = max(0, min(x1, image.width - 1))
        top = max(0, min(y1, image.height - 1))
        right = max(left + 1, min(x2, image.width))
        bottom = max(top + 1, min(y2, image.height))
        detections.append(
            {
                "box": (left, top, right, bottom),
                "score": float(row[6]),
            }
        )
    detections.sort(key=lambda item: item["score"], reverse=True)
    return detections


def _best_match(
    ground_truth: tuple[int, int, int, int],
    detections: list[dict],
    threshold: float,
) -> tuple[float, dict | None]:
    best_iou = 0.0
    best = None
    for detection in detections:
        value = common.iou(ground_truth, detection["box"])
        if value > best_iou:
            best_iou = value
            best = detection
    if best is None or best_iou < threshold:
        return best_iou, None
    return best_iou, best


def _rotation_tta(
    crop: Image.Image,
    read_crop,
) -> tuple[str, int, float, str]:
    """Seleciona a melhor rotação usando apenas formato válido + confiança.

    A placa esperada é usada somente no relatório, nunca para escolher a
    variante em tempo de execução. Assim o número representa uma TTA que
    poderia ser implementada no app.
    """
    candidates: list[tuple[bool, float, float, str, int, str]] = []
    for rotation in (0, 90, 180, 270):
        rotated = common.rotate_crop(crop, rotation)
        reading, source, _ = read_crop(rotated)
        valid = common.format_is_valid(reading["text"])
        candidates.append(
            (
                valid,
                reading["confidence"],
                reading["minimum"],
                reading["text"],
                rotation,
                source,
            )
        )
    candidates.sort(key=lambda item: (item[0], item[1], item[2]), reverse=True)
    valid, confidence, _, text, rotation, source = candidates[0]
    return text, rotation, confidence, source


def benchmark(args: argparse.Namespace) -> int:
    rows = common.read_labels(args.labels)
    images_dir = Path(args.images) if args.images else common.IMAGES_DIR
    detector = ort.InferenceSession(str(args.detector))
    ocr = ort.InferenceSession(str(args.ocr))
    fallback = (
        None
        if args.no_ocr_fallback
        else ort.InferenceSession(str(args.ocr_fallback))
    )

    def read_crop(crop: Image.Image) -> tuple[dict, str, dict]:
        primary = _ocr_reading(crop, ocr)
        if fallback is None or not _should_try_fallback(primary):
            return primary, "primary", primary
        chosen, source = _select_reading(primary, _ocr_reading(crop, fallback))
        return chosen, source, primary

    selected = []
    skipped = []
    for row in rows:
        if row["split"] == "ignorar":
            skipped.append((row, "split=ignorar"))
            continue
        if row["status"] == "confirmado":
            selected.append(row)
        elif row["status"] == "revisar" and args.include_unconfirmed:
            selected.append(row)
        elif row["status"] == "parcial" and args.include_parcial:
            selected.append(row)
        else:
            skipped.append((row, f"status={row['status']}"))

    results: list[dict] = []
    missing: list[str] = []
    for row in selected:
        path = images_dir / row["arquivo"]
        if not path.is_file():
            missing.append(str(path))
            continue
        with Image.open(path) as opened:
            image = opened.convert("RGB")

        expected = common.normalize_plate(row["placa"])
        gt_box = (row["x1"], row["y1"], row["x2"], row["y2"])
        gt_crop = common.rotate_crop(
            common.crop_plate(image, row, margin=args.crop_margin),
            row["rotacao"],
        )
        gt_primary = _ocr_reading(gt_crop, ocr)
        if fallback is not None and _should_try_fallback(gt_primary):
            gt_reading, gt_source = _select_reading(
                gt_primary,
                _ocr_reading(gt_crop, fallback),
            )
        else:
            gt_reading, gt_source = gt_primary, "primary"
        gt_text = gt_reading["text"]
        gt_confidence = gt_reading["confidence"]

        detections = _detect(image, detector, args.min_detector_score)
        best_iou, best_detection = _best_match(gt_box, detections, args.iou)
        detected = best_detection is not None
        if best_detection is not None:
            pred_crop = image.crop(best_detection["box"])
            pred_reading, pred_source, pred_primary = read_crop(pred_crop)
            pred_text = pred_reading["text"]
            pred_confidence = pred_reading["confidence"]
            tta_text, tta_rotation, tta_confidence, tta_source = _rotation_tta(
                pred_crop, read_crop
            )
            det_score = best_detection["score"]
        else:
            pred_text, pred_confidence = "", 0.0
            pred_source = "none"
            pred_primary = {"text": ""}
            tta_text, tta_rotation, tta_confidence = "", 0, 0.0
            tta_source = "none"
            det_score = detections[0]["score"] if detections else 0.0

        pred_normalized = common.app_normalize(pred_text) or ""
        gt_normalized = common.app_normalize(gt_text) or ""
        tta_normalized = common.app_normalize(tta_text) or ""
        results.append(
            {
                "arquivo": row["arquivo"],
                "placa": expected,
                "split": row["split"],
                "status": row["status"],
                "formato": row["formato"],
                "linhas": row["linhas"],
                "tipo": row["tipo"],
                "detected": detected,
                "iou": round(best_iou, 4),
                "detector_score": round(det_score, 4),
                "pred_text": pred_text,
                "pred_normalized": pred_normalized,
                "pred_confidence": round(pred_confidence, 4),
                "pred_ocr_source": pred_source,
                "pred_primary_text": pred_primary["text"],
                "raw_exact": pred_text == expected,
                "raw_valid": common.format_is_valid(pred_text),
                "app_exact": pred_normalized == expected,
                "app_valid": bool(pred_normalized),
                "distance": common.distance(pred_text, expected),
                "gt_ocr_text": gt_text,
                "gt_ocr_normalized": gt_normalized,
                "gt_ocr_confidence": round(gt_confidence, 4),
                "gt_ocr_source": gt_source,
                "gt_ocr_primary_text": gt_primary["text"],
                "gt_ocr_raw_exact": gt_text == expected,
                "gt_ocr_app_exact": gt_normalized == expected,
                "gt_ocr_raw_valid": common.format_is_valid(gt_text),
                "gt_ocr_app_valid": bool(gt_normalized),
                "gt_ocr_distance": common.distance(gt_text, expected),
                "tta_text": tta_text,
                "tta_normalized": tta_normalized,
                "tta_rotation": tta_rotation,
                "tta_confidence": round(tta_confidence, 4),
                "tta_ocr_source": tta_source,
                "tta_raw_exact": tta_text == expected,
                "tta_app_exact": tta_normalized == expected,
                "tta_raw_valid": common.format_is_valid(tta_text),
                "tta_app_valid": bool(tta_normalized),
                "tta_distance": common.distance(tta_text, expected),
            }
        )

    def rate(key: str) -> tuple[int, float]:
        count = sum(1 for item in results if item[key])
        return count, (count / len(results) if results else 0.0)

    detector_count, detector_rate = rate("detected")
    raw_exact_count, raw_exact_rate = rate("raw_exact")
    raw_valid_count, raw_valid_rate = rate("raw_valid")
    app_exact_count, app_exact_rate = rate("app_exact")
    app_valid_count, app_valid_rate = rate("app_valid")
    gt_raw_exact_count, gt_raw_exact_rate = rate("gt_ocr_raw_exact")
    gt_app_exact_count, gt_app_exact_rate = rate("gt_ocr_app_exact")
    gt_raw_valid_count, gt_raw_valid_rate = rate("gt_ocr_raw_valid")
    gt_app_valid_count, gt_app_valid_rate = rate("gt_ocr_app_valid")
    tta_raw_exact_count, tta_raw_exact_rate = rate("tta_raw_exact")
    tta_app_exact_count, tta_app_exact_rate = rate("tta_app_exact")
    tta_raw_valid_count, tta_raw_valid_rate = rate("tta_raw_valid")
    tta_app_valid_count, tta_app_valid_rate = rate("tta_app_valid")
    fallback_count = sum(1 for item in results if item["pred_ocr_source"] == "fallback")
    gt_fallback_count = sum(1 for item in results if item["gt_ocr_source"] == "fallback")

    summary = {
        "labels": str(args.labels),
        "images": str(images_dir),
        "eligible": len(selected),
        "evaluated": len(results),
        "skipped": len(skipped),
        "ocr": {
            "primary": str(args.ocr),
            "fallback": None
            if args.no_ocr_fallback
            else str(args.ocr_fallback),
            "pipeline_fallbacks": fallback_count,
            "annotated_crop_fallbacks": gt_fallback_count,
        },
        "detector": {
            "detected": detector_count,
            "recall": round(detector_rate, 4),
            "iou_threshold": args.iou,
        },
        "pipeline": {
            "raw_exact": raw_exact_count,
            "raw_exact_accuracy": round(raw_exact_rate, 4),
            "raw_valid": raw_valid_count,
            "raw_valid_rate": round(raw_valid_rate, 4),
            "app_exact": app_exact_count,
            "app_exact_accuracy": round(app_exact_rate, 4),
            "app_valid": app_valid_count,
            "app_valid_rate": round(app_valid_rate, 4),
        },
        "ocr_ground_truth_crop": {
            "raw_exact": gt_raw_exact_count,
            "raw_exact_accuracy": round(gt_raw_exact_rate, 4),
            "app_exact": gt_app_exact_count,
            "app_exact_accuracy": round(gt_app_exact_rate, 4),
            "raw_valid": gt_raw_valid_count,
            "raw_valid_rate": round(gt_raw_valid_rate, 4),
            "app_valid": gt_app_valid_count,
            "app_valid_rate": round(gt_app_valid_rate, 4),
        },
        "rotation_tta": {
            "raw_exact": tta_raw_exact_count,
            "raw_exact_accuracy": round(tta_raw_exact_rate, 4),
            "app_exact": tta_app_exact_count,
            "app_exact_accuracy": round(tta_app_exact_rate, 4),
            "raw_valid": tta_raw_valid_count,
            "raw_valid_rate": round(tta_raw_valid_rate, 4),
            "app_valid": tta_app_valid_count,
            "app_valid_rate": round(tta_app_valid_rate, 4),
        },
        "missing_files": missing,
        "results": results,
        "skipped_rows": [
            {
                "arquivo": row["arquivo"],
                "placa": common.normalize_plate(row["placa"]),
                "reason": reason,
            }
            for row, reason in skipped
        ],
    }

    print(f"Dataset: {args.labels}")
    print(
        "Elegiveis: {eligible} | avaliados: {evaluated} | ignorados: {skipped}".format(
            eligible=summary["eligible"],
            evaluated=summary["evaluated"],
            skipped=summary["skipped"],
        )
    )
    print(
        "OCR: principal {} | fallback {} | usos no pipeline: {}".format(
            Path(args.ocr).name,
            "desligado" if args.no_ocr_fallback else Path(args.ocr_fallback).name,
            fallback_count,
        )
    )
    print(
        "Detector (IoU>={:.2f}): {}/{} = {:.1%}".format(
            args.iou,
            detector_count,
            len(results),
            detector_rate,
        )
    )
    print(
        "Pipeline atual (sem rotacao): bruto {}/{} = {:.1%}; "
        "normalizado pelo app {}/{} = {:.1%}; valido {}/{} = {:.1%}".format(
            raw_exact_count,
            len(results),
            raw_exact_rate,
            app_exact_count,
            len(results),
            app_exact_rate,
            app_valid_count,
            len(results),
            app_valid_rate,
        )
    )
    print(
        "OCR no recorte anotado: bruto {}/{} = {:.1%}; "
        "normalizado pelo app {}/{} = {:.1%}; valido {}/{} = {:.1%}".format(
            gt_raw_exact_count,
            len(results),
            gt_raw_exact_rate,
            gt_app_exact_count,
            len(results),
            gt_app_exact_rate,
            gt_app_valid_count,
            len(results),
            gt_app_valid_rate,
        )
    )
    print(
        "TTA de rotacao (potencial): bruto {}/{} = {:.1%}; "
        "normalizado pelo app {}/{} = {:.1%}; valido {}/{} = {:.1%}".format(
            tta_raw_exact_count,
            len(results),
            tta_raw_exact_rate,
            tta_app_exact_count,
            len(results),
            tta_app_exact_rate,
            tta_app_valid_count,
            len(results),
            tta_app_valid_rate,
        )
    )

    normalized_fixes = [
        item
        for item in results
        if item["detected"] and not item["raw_exact"] and item["app_exact"]
    ]
    if normalized_fixes:
        print("\nCorrigidas apenas pela normalizacao do app:")
        for item in normalized_fixes:
            print(
                "  {arquivo}: {pred_text} -> {pred_normalized} (esperado {placa})".format(
                    **item
                )
            )

    failures = [
        item
        for item in results
        if not item["app_exact"] or not item["detected"]
    ]
    if failures:
        print("\nFalhas do pipeline atual (apos normalizacao do app):")
        for item in failures:
            flags = []
            if not item["detected"]:
                flags.append("NAO DETECTADO")
            if item["pred_text"] != item["placa"]:
                flags.append(f"{item['pred_text'] or '-'} != {item['placa']}")
            elif item["pred_normalized"] != item["placa"]:
                flags.append(f"{item['pred_normalized'] or '-'} != {item['placa']}")
            print(
                "  {arquivo}: {flags} (det={detector_score:.3f}, iou={iou:.3f})".format(
                    flags="; ".join(flags),
                    **item,
                )
            )

    if missing:
        print("\nArquivos ausentes:", file=sys.stderr)
        for path in missing:
            print(f"  - {path}", file=sys.stderr)

    if args.json_out:
        Path(args.json_out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.json_out).write_text(
            json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"\nJSON: {args.json_out}")

    if args.csv_out:
        Path(args.csv_out).parent.mkdir(parents=True, exist_ok=True)
        with Path(args.csv_out).open("w", encoding="utf-8", newline="") as handle:
            fields = list(results[0].keys()) if results else ["arquivo"]
            writer = csv.DictWriter(handle, fieldnames=fields, lineterminator="\n")
            writer.writeheader()
            writer.writerows(results)
        print(f"CSV: {args.csv_out}")

    return 1 if missing else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--labels", type=Path, default=common.LABELS_PATH)
    parser.add_argument("--images", type=Path, default=common.IMAGES_DIR)
    parser.add_argument(
        "--detector",
        type=Path,
        default=common.ROOT / "app/src/main/assets/models/detector.onnx",
    )
    parser.add_argument(
        "--ocr",
        type=Path,
        default=common.ROOT / "app/src/main/assets/models/ocr.onnx",
    )
    parser.add_argument(
        "--ocr-fallback",
        type=Path,
        default=common.ROOT / "app/src/main/assets/models/ocr_fallback.onnx",
        help="Segunda opinião; reproduz OcrFallbackPolicy.",
    )
    parser.add_argument(
        "--no-ocr-fallback",
        action="store_true",
        help="Avalia somente o OCR principal.",
    )
    parser.add_argument("--min-detector-score", type=float, default=0.30)
    parser.add_argument("--iou", type=float, default=0.30)
    parser.add_argument(
        "--include-unconfirmed",
        action="store_true",
        help="Inclui linhas com status revisar.",
    )
    parser.add_argument(
        "--include-parcial",
        action="store_true",
        help="Inclui linhas com status parcial (nao recomendado).",
    )
    parser.add_argument("--crop-margin", type=int, default=0)
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--csv-out", type=Path)
    args = parser.parse_args()
    return benchmark(args)


if __name__ == "__main__":
    raise SystemExit(main())
