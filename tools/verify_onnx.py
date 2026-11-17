#!/usr/bin/env python3
"""Verificação offline dos modelos ONNX embarcados no VigiAuto.

O script reproduz o pré-processamento do app Android:

1. letterbox da imagem para 384x384, fundo cinza 114;
2. detector YOLOv9 devolve caixas [batch, x1, y1, x2, y2, classe, score]
   nas coordenadas da entrada 384x384;
3. cada caixa é recortada na imagem original, redimensionada para 128x64
   e enviada ao CCT como RGB uint8;
4. o OCR decodifica 10 posições com o alfabeto do modelo.

Ele serve para validar os arquivos e a lógica de coordenadas fora do Android.
Não substitui o teste instrumentado no aparelho nem o benchmark do dataset.

Uso:
    python tools/verify_onnx.py app/src/androidTest/assets/alpr/test_plate.png
    python tools/verify_onnx.py --expect ABC1D23 imagem1.png imagem2.png

Dependências: onnxruntime, numpy e Pillow.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

DETECTOR_SIZE = 384
OCR_WIDTH = 128
OCR_HEIGHT = 64
ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_"
PAD = "_"
DETECTOR_SCORE_MIN = 0.30
PAD_COLOR = (114, 114, 114)

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DETECTOR = ROOT / "app/src/main/assets/models/detector.onnx"
DEFAULT_OCR = ROOT / "app/src/main/assets/models/ocr.onnx"


def letterbox(image: Image.Image) -> tuple[np.ndarray, float, float, float]:
    """Retorna tensor NCHW e os parâmetros usados para desfazer o letterbox."""
    width, height = image.size
    scale = min(DETECTOR_SIZE / width, DETECTOR_SIZE / height)
    scaled_width = max(1, round(width * scale))
    scaled_height = max(1, round(height * scale))
    pad_x = (DETECTOR_SIZE - scaled_width) / 2.0
    pad_y = (DETECTOR_SIZE - scaled_height) / 2.0

    canvas = Image.new("RGB", (DETECTOR_SIZE, DETECTOR_SIZE), PAD_COLOR)
    resized = image.resize((scaled_width, scaled_height), Image.BILINEAR)
    canvas.paste(resized, (round(pad_x), round(pad_y)))

    tensor = np.asarray(canvas, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0
    return tensor, scale, pad_x, pad_y


def decode_ocr(probabilities: np.ndarray) -> tuple[str, float, float]:
    indexes = probabilities.argmax(axis=1)
    confidences = probabilities.max(axis=1)
    characters = [
        (ALPHABET[index], float(confidences[position]))
        for position, index in enumerate(indexes)
        if index < len(ALPHABET) and ALPHABET[index] != PAD
    ]
    text = "".join(character for character, _ in characters)
    if not characters:
        return "", 0.0, 0.0
    values = [confidence for _, confidence in characters]
    return text, sum(values) / len(values), min(values)


def run(path: Path, detector: ort.InferenceSession, ocr: ort.InferenceSession) -> dict:
    image = Image.open(path).convert("RGB")
    tensor, scale, pad_x, pad_y = letterbox(image)

    rows = detector.run(None, {detector.get_inputs()[0].name: tensor})[0]
    detections = []
    for row in rows:
        if len(row) < 7 or float(row[6]) < DETECTOR_SCORE_MIN:
            continue
        x1 = int((float(row[1]) - pad_x) / scale)
        y1 = int((float(row[2]) - pad_y) / scale)
        x2 = int((float(row[3]) - pad_x) / scale)
        y2 = int((float(row[4]) - pad_y) / scale)
        left = max(0, min(x1, image.width - 1))
        top = max(0, min(y1, image.height - 1))
        right = max(left + 1, min(x2, image.width))
        bottom = max(top + 1, min(y2, image.height))

        crop = image.crop((left, top, right, bottom))
        resized = crop.resize((OCR_WIDTH, OCR_HEIGHT), Image.BILINEAR)
        output = ocr.run(
            None,
            {ocr.get_inputs()[0].name: np.asarray(resized, dtype=np.uint8)[None]},
        )[0][0]
        text, confidence, minimum = decode_ocr(output)
        detections.append(
            {
                "box": [left, top, right, bottom],
                "detector_confidence": round(float(row[6]), 4),
                "raw_text": text,
                "ocr_confidence": round(confidence, 4),
                "min_character_confidence": round(minimum, 4),
            }
        )

    return {"file": str(path), "size": list(image.size), "detections": detections}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("images", nargs="+", type=Path)
    parser.add_argument("--detector", type=Path, default=DEFAULT_DETECTOR)
    parser.add_argument("--ocr", type=Path, default=DEFAULT_OCR)
    parser.add_argument(
        "--expect",
        help="Placa esperada em todas as imagens; o script falha se não for encontrada.",
    )
    parser.add_argument("--json", action="store_true", help="Imprime JSON.")
    args = parser.parse_args()

    detector = ort.InferenceSession(str(args.detector))
    ocr = ort.InferenceSession(str(args.ocr))

    results = []
    for path in args.images:
        if not path.is_file():
            print(f"Arquivo não encontrado: {path}", file=sys.stderr)
            return 2
        result = run(path, detector, ocr)
        results.append(result)
        if not args.json:
            plates = [item["raw_text"] for item in result["detections"]]
            print(f"{path}: {plates or 'nenhuma detecção'}")
            for item in result["detections"]:
                print(
                    "  caixa={box} detector={detector_confidence:.3f} "
                    "ocr={ocr_confidence:.3f} min={min_character_confidence:.3f} "
                    "texto={raw_text!r}".format(**item)
                )

    if args.json:
        print(json.dumps(results, ensure_ascii=False, indent=2))

    if args.expect:
        expected = args.expect.strip().upper()
        found = [
            item["raw_text"]
            for result in results
            for item in result["detections"]
        ]
        if expected not in found:
            print(
                f"Esperava {expected}; encontrado: {found or 'nada'}",
                file=sys.stderr,
            )
            return 1
        print(f"OK: {expected} encontrado.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
