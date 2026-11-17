# Level 2 — dataset e benchmark offline

Este diretório é a base do **Level 2** do VigiAuto: anotação, validação e
avaliação offline dos modelos de detecção e OCR. Ele **não** faz aprendizado no aparelho.
O aplicativo continua apenas inferindo com os modelos ONNX já embarcados.

## O que foi feito

- 27 imagens em alta resolução (**1220×2712**) em `images/`, com nomes estáveis
  (`L2-001.jpeg` … `L2-027.jpeg`);
- as 26 cenas originais em alta resolução foram mapeadas 1:1 para as versões
  reduzidas de 610×1356, que ficaram arquivadas em `images_lowres/`;
- a cena nova `EFR8092` entrou como **L2-027**; `1001073169.jpg` é uma duplicata
  idêntica da cena da L2-019 e não foi incluída duas vezes;
- todas as caixas foram multiplicadas por 2 ao trocar para as telas de
  1220×2712;
- anotação de placa e caixa delimitadora em `labels.csv`;
- **27 anotações confirmadas**: 18 em `treino`, 3 em `validacao` e 6 em `teste`;
- validador, benchmark e exportador em `tools/level2_*.py`;
- benchmark do OCR ajustado da 1.8.0, com fallback condicional, sem alterar o
  `detector.onnx`.

As imagens continuam sendo screenshots/fotos de celular (algumas fotos de uma
tela de computador), não fotos nativas da câmera. As placas agora têm, em média,
**174 px de largura** (mediana 144 px, mínimo 82 px), contra 81 px antes. Isso
melhora a anotação, mas não elimina a diferença de domínio em relação ao que a
câmera do aparelho vê em campo.

## Situação das anotações

| Arquivo | Placa | Split | Status | Observação |
|---|---|---|---|---|
| L2-001 | FLG2C26 | treino | confirmado | Traseira |
| L2-002 | ERZ3G63 | validacao | confirmado | OCR anterior leu `ER23G63` |
| L2-003 | GJK7C04 | treino | confirmado | Traseira |
| L2-004 | TYP4I65 | treino | confirmado | Traseira |
| L2-005 | FVF2A74 | treino | confirmado | Traseira |
| L2-006 | GBI8D31 | treino | confirmado | Traseira |
| L2-007 | FST7115 | treino | confirmado | Placa antiga |
| L2-008 | TJY5G53 | treino | confirmado | Moto em duas linhas |
| L2-009 | GGF2554 | teste | confirmado | Mesma cena/placa da 21 |
| L2-010 | PWJ8089 | treino | confirmado | Placa antiga |
| L2-011 | FXW6J24 | treino | confirmado | Traseira |
| L2-012 | DEN3G89 | treino | confirmado | Traseira |
| L2-013 | EGA5795 | teste | confirmado | Placa de moto de cabeça para baixo (`rotacao=180`) |
| L2-014 | TLL3F44 | treino | confirmado | Baixa resolução |
| L2-015 | STH5H33 | treino | confirmado | Traseira |
| L2-016 | FNO3A80 | treino | confirmado | Traseira |
| L2-017 | EZE6390 | validacao | confirmado | OCR anterior leu `ZE6390` |
| L2-018 | GHF0372 | treino | confirmado | Traseira |
| L2-019 | DOS5108 | treino | confirmado | `1001073169.jpg` é duplicata desta cena |
| L2-020 | DMV7028 | treino | confirmado | Traseira |
| L2-021 | GGF2554 | teste | confirmado | Mesma cena/placa da 09 |
| L2-022 | SUP0B93 | teste | confirmado | Moto em duas linhas; detector atual não detectou |
| L2-023 | SWR3C57 | teste | confirmado | Leitura confirmada em alta resolução; OCR anterior leu `KMAJE57` |
| L2-024 | OEN2H76 | treino | confirmado | OCR anterior leu `OENZH76` |
| L2-025 | DRB7197 | validacao | confirmado | Detector atual não detectou |
| L2-026 | FZJ5118 | teste | confirmado | Placa completa confirmada; antes tratada como parcial |
| L2-027 | EFR8092 | treino | confirmado | Cena nova; placa antiga; detector atual detectou |

`labels.csv` é a fonte de verdade. As colunas são:

`arquivo,arquivo_original,origem,placa,formato,linhas,tipo,veiculo,observacao,x1,y1,x2,y2,rotacao,grupo,split,status`

- `rotacao`: correção em graus (sentido anti-horário do Pillow) para o recorte
  de OCR; a imagem original não é alterada;
- `grupo`: cenas/placas que não podem ser separadas entre splits;
- `split=ignorar`: não entra no benchmark nem no treinamento;
- `status=revisar` ou `parcial`: fica fora das métricas exatas por padrão.

## Benchmark da 1.8.0

Execução em 25/10/2026 com `tools/level2_benchmark.py`, os ONNX embarcados e as
**27 anotações confirmadas** em alta resolução. O script carrega o `ocr.onnx`
(E2) e o `ocr_fallback.onnx` e reproduz a política condicional do app:

| Métrica | Resultado |
|---|---|
| Detector, recall com IoU ≥ 0,30 | 25/27 = **92,6%** |
| Pipeline, texto bruto exato | 22/27 = **81,5%** |
| Pipeline, normalizado pelo app | 23/27 = **85,2%** |
| Pipeline, formato válido após normalização | 25/27 = **92,6%** |
| OCR no recorte anotado, bruto exato | 23/27 = **85,2%** |
| OCR no recorte anotado, normalizado pelo app | 23/27 = **85,2%** |
| OCR no recorte anotado, formato válido | 27/27 = **100,0%** |
| TTA de rotação, normalizado pelo app | 23/27 = **85,2%** |
| TTA de rotação, formato válido | 25/27 = **92,6%** |

Para comparação, o modelo anterior (hoje `ocr_fallback.onnx`) fazia **19/27** no
pipeline e **22/27** no recorte anotado; o E2 sozinho fazia **21/27** e **22/27**.
O fallback foi usado em **2** casos do pipeline (`L2-002` e `L2-026`) e em **1**
recorte anotado (`L2-013`). Ele não roda em leituras válidas e confiantes.

A normalização do app (`PlateNormalizer`) corrige uma leitura bruta errada nesta
execução: `F2J5118 → FZJ5118` (L2-026). O benchmark não executa a consulta
NMT/OTTC, então não conta o fallback de alternativas visuais que o app tenta
depois de uma consulta sem resultado; a acurácia em campo pode ser diferente.

Falhas do pipeline depois da normalização:

- **L2-013:** `SGLS153` → `SGL5153` em vez de `EGA5795` (placa de moto
  invertida; no recorte anotado girado 180°, o fallback lê `EGA5795`);
- **L2-017:** `ZZE6390` em vez de `EZE6390`;
- **L2-022:** placa `SUP0B93` não detectada;
- **L2-025:** placa `DRB7197` não detectada.

No recorte anotado, as falhas restantes são `L2-002`, `L2-017`, `L2-023` e
`L2-025`. A TTA de rotação também fica em 23/27 e não compensa quatro passadas
por leitura; o problema da L2-013 no pipeline é o enquadramento do detector, não
a falta de rotação do OCR.

Arquivos gerados:

- `validation.json`: relatório de integridade, splits, tamanhos e avisos;
- `benchmark.json` e `benchmark.csv`: baseline por imagem;
- `export/yolo/`: imagens e labels no formato YOLO (classe 0 = placa);
- `export/ocr/`: recortes de placa e `manifest.csv` usados no ajuste do OCR;
- `images_lowres/`: versões reduzidas originais, preservadas para auditoria;
- `originais_email/`: telas extraídas do e-mail original.

## Comandos

```bat
python tools\level2_dataset.py validate --json-out dataset\level2\validation.json
python tools\level2_benchmark.py --json-out dataset\level2\benchmark.json --csv-out dataset\level2\benchmark.csv
python tools\level2_dataset.py export --force
```

Para incluir imagens novas, coloque os originais em
[`incoming/`](incoming/README.md) ou passe outra pasta:

```bat
python tools\level2_dataset.py prepare --source dataset\level2\incoming
```

O `prepare` copia as novas imagens, roda o ONNX como **pré-anotador** e grava
`labels.template.csv`; ele nunca sobrescreve `labels.csv`. A placa e a caixa do
template precisam ser conferidas por uma pessoa. O ONNX atual não é ground
truth: no pipeline ele ainda erra `L2-013` e `L2-017` e não detecta `L2-022` e
`L2-025`.

## Modelo de OCR da 1.8.0 e limite do detector

O `ocr.onnx` foi ajustado fora do app com o pacote de treino em
`C:\Users\Guilherme\Downloads\OCR\training\vigiauto_v2`: **50 recortes de 44
grupos** (38 treino, 6 validação e 6 teste), separados por cena/placa. O melhor
experimento foi `e2_lr1e4_e26`; a exportação usa somente a cabeça de placa,
opset 15 e lote estático 1. O modelo anterior do app foi preservado como
`ocr_fallback.onnx` e só é consultado pela política condicional.

O **detector** continua sendo o `detector.onnx` anterior. Ele é um grafo de
inferência: não traz o código de treino, o otimizador nem os pesos em formato
treinável. O pacote de treino recuperado cobre o OCR, não o YOLOv9. Para o
detector, o mínimo necessário seria:

- o código de treino (YOLOv9 ou similar) e os pesos em formato treinável
  (`.pt`, `.pth` ou `.ckpt`), não apenas `.onnx`;
- configuração de classes, `data.yaml`, hiperparâmetros, tamanho de entrada,
  augmentations e regras de validação;
- o script que exporta para ONNX e o pré-processamento usado (letterbox,
  normalização e decodificação das saídas);
- o dataset original ou, no mínimo, a receita de como ele foi montado.

Além disso, 27 imagens úteis (18 de treino) são insuficientes para treinar um
detector robusto; um modelo treinado com esse conjunto memorizaria as poucas
cenas e pioraria em campo. O OCR também tem apenas 6 imagens de teste, então a
melhora de 19/27 para 23/27 no pipeline é encorajadora, mas não é prova
estatística. O detector não foi trocado.

Em 26/10/2026 a tentativa foi repetida com as 44 imagens novas de
`TREINO IA\TREINO`. O OCR ganhou os experimentos E5–E7 no dataset
`vigiauto_v3` (94 amostras / 87 grupos), mas nenhum superou o E2: E5 e E6
regrediram no pipeline e o E7 empatou em todos os conjuntos isolados. O detector
foi testado com um YOLOv8n separado, usando 44 caixas do próprio detector como
pseudo-rótulos; o recall no Level 2 caiu de 23/27 para 11/27. Nenhum modelo foi
integrado. A rodada completa, com métricas e artefatos, está em
[`docs/TREINO-2026-10-26.md`](../../docs/TREINO-2026-10-26.md).

Para melhorar o detector com segurança, é necessário um destes caminhos:

1. **Recuperar o pacote de treino do detector** (YOLOv9 ou similar: código,
   pesos e configuração) e fazer fine-tuning com um conjunto maior; ou
2. **Treinar uma nova arquitetura** (detector + OCR) e adaptar a integração do
   app, mantendo as entradas/saídas compatíveis ou versionando o motor.

Em qualquer caminho, o mínimo recomendado é:

- 200–500 imagens originais, não comprimidas pelo WhatsApp;
- pelo menos 50 motos, 50 placas antigas e 50 Mercosul;
- variação de distância, ângulo, iluminação, chuva/noite e movimento;
- imagens negativas (fachadas, letreiros, caminhões, placas obstruídas);
- conjunto de teste separado por placa/cena, nunca por recorte aleatório;
- benchmark no aparelho antes de substituir o APK.

Critério de aceitação sugerido: com um conjunto de teste maior (idealmente
≥ 50 imagens), acurácia exata no teste **pelo menos 5 pontos percentuais acima**
do baseline atual de 85,2%, sem aumentar falsos positivos e com latência medida
no aparelho. O teste atual tem só 6 imagens e serve como smoke test, não como
prova estatística.

## Limitações e avisos

- A L2-023 foi confirmada como **SWR3C57**; não é a mesma placa da moto L2-022.
- A L2-026 foi confirmada como **FZJ5118** e agora entra no teste.
- A L2-013 é uma placa de moto invertida; o app atual não corrige rotação.
- As imagens 09 e 21 são da mesma cena/placa e ficaram juntas no teste para não
  vazar informação.
- O dataset é local. Nenhuma imagem é enviada para servidor ou serviço externo.
- O Level 2 não cria ingestão de câmeras RTSP/ONVIF. Isso continua sendo uma
  etapa de servidor/edge separada.
