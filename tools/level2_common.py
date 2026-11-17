#!/usr/bin/env python3
"""Utilitários compartilhados do dataset Level 2 do VigiAuto.

O Level 2 é um ciclo **offline**: as imagens são anotadas, validadas e usadas
para treinar/avaliar um modelo fora do aparelho. O app continua apenas
inferindo; nenhum aprendizado acontece em tempo de execução.
"""

from __future__ import annotations

import csv
import hashlib
import math
import re
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "dataset" / "level2"
LABELS_PATH = DATASET_DIR / "labels.csv"
IMAGES_DIR = DATASET_DIR / "images"
EXPORT_DIR = DATASET_DIR / "export"

REQUIRED_COLUMNS = [
    "arquivo",
    "arquivo_original",
    "origem",
    "placa",
    "formato",
    "linhas",
    "tipo",
    "veiculo",
    "observacao",
    "x1",
    "y1",
    "x2",
    "y2",
    "rotacao",
    "grupo",
    "split",
    "status",
]

INT_COLUMNS = ("linhas", "x1", "y1", "x2", "y2", "rotacao")
SPLITS = {"treino", "validacao", "teste", "ignorar"}
STATUSES = {"confirmado", "revisar", "parcial", "prelabel"}
MERCOSUL_RE = re.compile(r"^[A-Z]{3}[0-9][A-Z][0-9]{2}$")
OLD_RE = re.compile(r"^[A-Z]{3}[0-9]{4}$")

ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_"

# Mesmos mapas de PlateNormalizer.kt. A normalização é aplicada pelo app antes
# de consultar a placa; o benchmark precisa medir o texto que o app usaria.
DIGIT_TO_LETTER = {
    "0": "O", "1": "I", "2": "Z", "3": "E", "4": "A",
    "5": "S", "6": "G", "7": "T", "8": "B",
}
LETTER_TO_DIGIT = {
    "O": "0", "I": "1", "Z": "2", "E": "3", "A": "4",
    "S": "5", "G": "6", "T": "7", "B": "8",
}
MAX_NORMALIZER_CORRECTIONS = 2

# Mesmas confusões de PlateNormalizer.kt. Duas leituras que diferem apenas
# nesses caracteres não justificam trocar a placa por uma segunda opinião.
VISUAL_CONFUSIONS = {
    "I": {"1"},
    "1": {"I"},
    "O": {"0", "Q", "C", "G"},
    "0": {"O"},
    "Q": {"O"},
    "C": {"G", "O"},
    "G": {"C", "O"},
    "U": {"V"},
    "V": {"U"},
}


def _try_correct(
    value: str,
    letter_positions: set[int],
    digit_positions: set[int],
) -> tuple[str, int] | None:
    """Equivalente a PlateNormalizer.tryCorrect (posições fixas)."""
    chars = list(value)
    corrections = 0
    for index, char in enumerate(chars):
        if index in letter_positions:
            if "A" <= char <= "Z":
                continue
            mapped = DIGIT_TO_LETTER.get(char)
            if mapped is None:
                return None
            chars[index] = mapped
            corrections += 1
        elif index in digit_positions:
            if "0" <= char <= "9":
                continue
            mapped = LETTER_TO_DIGIT.get(char)
            if mapped is None:
                return None
            chars[index] = mapped
            corrections += 1
        else:
            return None
        if corrections > MAX_NORMALIZER_CORRECTIONS:
            return None
    result = "".join(chars)
    return (result, corrections) if format_is_valid(result) else None


def app_normalize(raw: str) -> str | None:
    """Reproduz PlateNormalizer.normalize: 7 caracteres e no máximo 2 trocas."""
    value = normalize_plate(raw)
    if len(value) != 7:
        return None
    if format_is_valid(value):
        return value
    mercosul = _try_correct(value, {0, 1, 2, 4}, {3, 5, 6})
    old = _try_correct(value, {0, 1, 2}, {3, 4, 5, 6})
    if mercosul is not None and old is not None:
        # O Kotlin usa o número de correções; em empate, rejeita.
        if mercosul[1] < old[1]:
            return mercosul[0]
        if old[1] < mercosul[1]:
            return old[0]
        return None
    if mercosul is not None:
        return mercosul[0]
    if old is not None:
        return old[0]
    return None


def is_visually_equivalent(first: str, second: str) -> bool:
    """Reproduz PlateNormalizer.isVisuallyEquivalent."""
    a = normalize_plate(first)
    b = normalize_plate(second)
    if len(a) != len(b) or not a:
        return False
    return all(
        x == y
        or y in VISUAL_CONFUSIONS.get(x, set())
        or x in VISUAL_CONFUSIONS.get(y, set())
        for x, y in zip(a, b)
    )


def read_labels(path: Path = LABELS_PATH) -> list[dict]:
    """Lê o CSV e converte as colunas numéricas, preservando o restante."""
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = [column for column in REQUIRED_COLUMNS if column not in (reader.fieldnames or [])]
        if missing:
            raise ValueError(f"Colunas ausentes em {path}: {', '.join(missing)}")
        rows = []
        for raw in reader:
            row = dict(raw)
            for column in INT_COLUMNS:
                value = (row.get(column) or "").strip()
                row[column] = int(value) if value else 0
            rows.append(row)
        return rows


def write_labels(rows: list[dict], path: Path = LABELS_PATH) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=REQUIRED_COLUMNS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def normalize_plate(value: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", (value or "").upper())


def plate_format(plate: str) -> str:
    normalized = normalize_plate(plate)
    if MERCOSUL_RE.fullmatch(normalized):
        return "Mercosul"
    if OLD_RE.fullmatch(normalized):
        return "Antiga"
    return "Indefinido"


def format_is_valid(plate: str, declared: str = "") -> bool:
    normalized = normalize_plate(plate)
    if declared == "Mercosul":
        return MERCOSUL_RE.fullmatch(normalized) is not None
    if declared == "Antiga":
        return OLD_RE.fullmatch(normalized) is not None
    return MERCOSUL_RE.fullmatch(normalized) is not None or OLD_RE.fullmatch(normalized) is not None


def iou(box_a: tuple[int, int, int, int], box_b: tuple[int, int, int, int]) -> float:
    ax1, ay1, ax2, ay2 = box_a
    bx1, by1, bx2, by2 = box_b
    inter_left = max(ax1, bx1)
    inter_top = max(ay1, by1)
    inter_right = min(ax2, bx2)
    inter_bottom = min(ay2, by2)
    inter = max(0, inter_right - inter_left) * max(0, inter_bottom - inter_top)
    if inter == 0:
        return 0.0
    area_a = max(0, ax2 - ax1) * max(0, ay2 - ay1)
    area_b = max(0, bx2 - bx1) * max(0, by2 - by1)
    union = area_a + area_b - inter
    return inter / union if union else 0.0


def crop_plate(image: Image.Image, row: dict, margin: int = 0) -> Image.Image:
    left = max(0, row["x1"] - margin)
    top = max(0, row["y1"] - margin)
    right = min(image.width, row["x2"] + margin)
    bottom = min(image.height, row["y2"] + margin)
    return image.crop((left, top, right, bottom))


def rotate_crop(crop: Image.Image, rotation: int) -> Image.Image:
    rotation = rotation % 360
    if rotation == 0:
        return crop
    return crop.rotate(rotation, expand=True, resample=Image.BICUBIC)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def average_hash(image: Image.Image, size: int = 8) -> int:
    gray = image.convert("L").resize((size, size), Image.Resampling.LANCZOS)
    pixels = list(gray.getdata())
    mean = sum(pixels) / len(pixels)
    value = 0
    for pixel in pixels:
        value = (value << 1) | (1 if pixel >= mean else 0)
    return value


def hamming_distance(a: int, b: int) -> int:
    return (a ^ b).bit_count()


def sharpness(image: Image.Image) -> float:
    """Variância dos gradientes; serve só como triagem, não como métrica de OCR."""
    import numpy as np

    gray = np.asarray(image.convert("L"), dtype=np.float32)
    if gray.shape[0] < 2 or gray.shape[1] < 2:
        return 0.0
    dx = np.diff(gray, axis=1)
    dy = np.diff(gray, axis=0)
    return float(dx.var() + dy.var())


def distance(a: str, b: str) -> int:
    """Distância de Levenshtein para comparar leituras de placa."""
    a = normalize_plate(a)
    b = normalize_plate(b)
    if not a:
        return len(b)
    if not b:
        return len(a)
    previous = list(range(len(b) + 1))
    for i, char_a in enumerate(a, 1):
        current = [i]
        for j, char_b in enumerate(b, 1):
            current.append(
                min(
                    current[-1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + (char_a != char_b),
                )
            )
        previous = current
    return previous[-1]


def split_counts(rows: list[dict]) -> dict[str, int]:
    counts = {split: 0 for split in sorted(SPLITS)}
    for row in rows:
        counts[row["split"]] = counts.get(row["split"], 0) + 1
    return counts


def status_counts(rows: list[dict]) -> dict[str, int]:
    counts = {status: 0 for status in sorted(STATUSES)}
    for row in rows:
        counts[row["status"]] = counts.get(row["status"], 0) + 1
    return counts


def mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    position = min(len(ordered) - 1, max(0, math.ceil(fraction * len(ordered)) - 1))
    return ordered[position]
