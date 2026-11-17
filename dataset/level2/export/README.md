# Exportação Level 2

Gerado por `tools/level2_dataset.py export`.

- Linhas exportadas: 27
- Treino: 18
- Validação: 3
- Teste: 6
- Recortes OCR: `ocr/images/`
- Manifesto OCR: `ocr/manifest.csv`
- Formato YOLO: `yolo/images/` e `yolo/labels/`

O conjunto ainda é pequeno para treinar um detector do zero. Use a exportação como insumo de um treinamento externo e valide o modelo contra o conjunto de teste antes de embarcar qualquer ONNX.
