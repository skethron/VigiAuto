# Dataset de placas reais — VigiAuto

As 9 fotos fornecidas foram anotadas em `labels.csv`, com 10 leituras (duas placas na
mesma cena). As imagens estão em `app/src/androidTest/assets/dataset/` (9 JPEGs); esta
pasta mantém as anotações e este README. Elas são um **conjunto de validação/calibração**
do pipeline de reconhecimento (detector ONNX + CCT e ML Kit + extração/normalização).
Nenhum modelo é treinado em tempo de execução.

O dataset serve para:

- medir a acurácia real em placas brasileiras (antigas, Mercosul e motos em duas linhas);
- calibrar sensibilidade, confiança e regras de normalização;
- detectar regressões após mudanças no código;
- comparar o motor ONNX com o fallback ML Kit.

Nove fotos são insuficientes para treinar um detector. Para um modelo próprio
(TFLite/OpenCV/ONNX) seria necessário um conjunto bem maior, com caixas delimitadoras
anotadas — isso não foi feito e nenhuma métrica de treino é apresentada.

## Como usar

1. As imagens já estão em `app/src/androidTest/assets/dataset/`.
2. Mantenha exatamente os nomes de arquivo listados em `labels.csv`.
3. Conecte um aparelho ou emulador e rode na raiz do projeto:

   ```bat
   gradlew.bat connectedDebugAndroidTest
   ```

Testes que usam este dataset:

- `OcrDatasetTest` (ML Kit): imprime no Logcat (tag `VigiAutoDataset`) o que foi lido
  em cada imagem e falha se a acurácia ficar abaixo de 70%;
- `OnnxDatasetBenchmarkTest` (ONNX): mede, por imagem, placas esperadas/detectadas,
  acertos, recall, precisão, falsos positivos, latência, CPU e PSS aproximado; grava o
  relatório `alpr-benchmark.csv` na pasta externa do app.

Se as imagens não estiverem presentes, os testes são ignorados (não quebram o build).

## Placas anotadas

| Arquivo | Placa | Formato | Linhas | Veículo |
|---|---|---|---|---|
| SUK0E51.jpeg | SUK0E51 | Mercosul | 2 | Moto |
| STE7I29.jpeg | DET8075 | Antiga | 1 | Carro amarelo |
| STE7I29.jpeg | STE7129 | Antiga | 2 | Moto |
| UFG0B86.jpeg | UFG0B86 | Mercosul | 2 | Moto |
| SHQ9G69.jpeg | SHQ9G69 | Mercosul | 1 | Fiat Cronos |
| DEI3A56.jpeg | DEI3A56 | Mercosul | 1 | VW T-Cross |
| EFR8092.jpeg | EFR8092 | Antiga | 1 | Chevrolet Meriva |
| FUL4798.jpeg | FUL4798 | Antiga | 1 | Hyundai HB20 |
| PVL7H74.jpeg | PVL7H74 | Mercosul | 1 | Fiat hatch |
| SVZ3B37.jpeg | SVZ3B37 | Mercosul | 2 | Moto |

As anotações de `SUK0E51` e `UFG0B86` foram corrigidas de `SUK6E51`/`UFG6B86` e
confirmadas pelo usuário. Nas duas fotos o glifo `0` é ambíguo e pode parecer `G`; o
padrão Mercosul e a leitura do ONNX confirmam o dígito `0`.

## Resultado local do ONNX (20/10/2026)

O script `tools/verify_onnx.py` foi executado nas 9 imagens com os mesmos modelos
embarcados, fora do Android:

| Métrica | Valor |
|---|---:|
| Imagens | 9 |
| Placas esperadas | 10 |
| Placas detectadas | 10 |
| Acertos exatos | 9 |
| Recall exato | 0,900 |
| Precisão exata | 0,900 |
| Falsos positivos | 1 |
| Falsos negativos | 1 |

A única divergência está na cena com duas placas: o modelo leu `STE7I29` (padrão
Mercosul válido) onde a anotação é `STE7129` (placa antiga de moto). É a ambiguidade
`I`/`1` na quinta posição; o app pode tentar `STE7129` como alternativa visual depois de
uma consulta sem resultado, mas o benchmark exato conta a divergência. As demais nove
leituras bateram exatamente.

## Resultado no POCO X7 Pro (20/10/2026)

O `OnnxDatasetBenchmarkTest` rodou no aparelho (Android 16, arm64-v8a) e reproduziu o
resultado local: 9 imagens, 10 placas esperadas, 10 detectadas, **9 acertos exatos**,
recall 0,900, precisão 0,900, 1 falso positivo e 1 falso negativo. Na segunda passagem,
já aquecida: latência média de **101,1 ms**, CPU médio de **99,4 ms** e PSS de
**170.543 KB → 208.504 KB**. A primeira passagem ficou em 153,3 ms de média, mostrando
o custo de aquecimento do runtime.

O `OcrDatasetTest` (ML Kit) acertou **9/10 leituras (90%)**: leu `SUK0E51`, `STE7129` e
as demais corretamente, mas leu `UFG6B86` onde a anotação confirmada é `UFG0B86`. O teste
do ML Kit não mede precisão nem falsos positivos, então não há base para declarar um
vencedor entre os motores. O `connectedDebugAndroidTest` completo passou: **9 testes, 0
falhas**, incluindo o teste de fumaça do ONNX e os 6 testes de Room.

A meta de 25 km/h continua **não medida**; a latência de 101 ms é de imagem estática, não
de captura em movimento.

## O que este dataset já motivou no código

- suporte a **placas de moto em duas linhas** (junção vertical de linhas no
  `CandidateExtractor`);
- aceitação de separadores (espaço, hífen, ponto médio) na normalização;
- pontuação de confiança específica para caixas mais altas (duas linhas);
- teste de cena com múltiplas placas, sinalizando fotografia com qualidade limitada.

## Verificação offline dos modelos ONNX

O script `tools/verify_onnx.py` reproduz o letterbox, o recorte e o OCR fora do Android.
Ele depende de `onnxruntime`, `numpy` e `Pillow` e serve para conferir os arquivos e a
lógica de coordenadas antes de instalar o APK:

```bat
python tools\verify_onnx.py --expect ABC1D23 app\src\androidTest\assets\alpr\test_plate.png
```

Ele **não substitui** o teste instrumentado no aparelho nem o benchmark com as fotos
reais.

## Level 2

O conjunto novo de 26 imagens, as anotações com caixas, o validador e o benchmark
offline ficam em [`level2/`](level2/README.md). Esse conjunto é separado das 9
fotos de validação acima e ainda não treinou nenhum modelo embarcado.
