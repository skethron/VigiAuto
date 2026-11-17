# VigiAuto

> **1.8.0 (25/10/2026):** o OCR embarcado foi ajustado com as imagens reais do
> projeto (`ocr.onnx`, 3.035.990 bytes) e o modelo anterior continua no APK como
> segunda opinião condicional (`ocr_fallback.onnx`). O fallback só roda quando a
> leitura principal não forma uma placa válida ou tem um caractere abaixo de
> **0,50** de confiança; se a principal já é válida, a segunda opinião só assume
> quando também é válida, tem confiança média ≥ **0,90** e difere em exatamente um
> caractere não equivalente (I/1, O/0, U/V, C/G, Q/O). Leituras normais continuam
> com uma única inferência. No benchmark offline, o pipeline subiu de **19/27**
> para **23/27 (85,2%)**, o recorte anotado de **22/27** para **23/27** e o
> conjunto alvo de 24 recortes ficou em **23/24 (95,8%)**. A miniatura da placa
> passou a ser recortada da própria fotografia quadrada do veículo, com margem
> curta e a caixa real no mesmo enquadramento; ela não usa mais uma região de
> outro recorte. A leitura ficou mais rápida: cadência padrão de **12 análises/s**
> (até 15) e confirmação no mesmo quadro para leituras excepcionais (detector
> ≥ 0,88, OCR ≥ 0,92 e menor caractere ≥ 0,60). Todas as placas confirmadas no
> mesmo quadro são consultadas, não apenas a primeira. A regra O/0 segue o padrão
> da placa — Mercosul (`ABC1D23`): letra **O** nas posições 1, 2, 3 e 5, dígito
> **0** nas 4, 6 e 7; antiga (`ABC1234`): letra **O** nas posições 1, 2 e 3,
> dígito **0** nas 4–7. A variante O/0 da quinta posição é consultada primeiro
> quando a placa não é encontrada. O banco continua **v5**. APK:
> `apk/VigiAuto-1.8.0-debug.apk`.
>
> **1.7.0 (23/10/2026):** cada leitura confirmada passa a guardar uma fotografia
> quadrada real do veículo e o recorte colorido da placa na caixa exata devolvida
> pelo YOLO. A tela de detalhes e o card JPEG exibem a miniatura com borda azul;
> tocar na miniatura abre o recorte ampliado. Não há geração, reconstrução ou
> substituição de caracteres: o recorte preserva os pixels da câmera e só é
> ampliado por interpolação. Quando a confiança, a nitidez ou a leitura indicam
> dúvida, o app sinaliza **verificação recomendada** sem alterar a imagem. O card
> de compartilhamento não mostra mais “Ocorrências” (a **Confiança** permanece).
> O APK usa o banco na versão 5; a instalação no aparelho ainda está pendente.
> APK: `apk/VigiAuto-1.7.0-debug.apk`.
>
> **1.6.4 (22/10/2026):** o card JPEG de compartilhamento volta ao layout
> completo da 1.6.2 (cabeçalho, fotografia, dados, localização e rodapé), com a
> placa centralizada no quadro e sem o texto "Consulta concluída". A localização
> da consulta passa a ser exibida em um único parágrafo, com vírgulas, e a linha
> "Capturada em" foi removida da tela de detalhes. APK:
> `apk/VigiAuto-1.6.4-debug.apk`.
>
> **1.6.3 (22/10/2026):** rebranding completo para VigiAuto. O applicationId,
> o namespace e o pacote Kotlin passam a `br.com.vigiauto`; o banco passa a
> `vigiauto.db` e os 41 registros foram migrados no aparelho. A consulta de
> roubo/furto exibe a data como `(Ocorrência: dd-MM-yyyy)`. Naquela build, o
> JPEG de compartilhamento trazia somente a fotografia com a placa centralizada,
> sem status nem detalhes da consulta. APK: `apk/VigiAuto-1.6.3-debug.apk`.
>
> **1.6.2 (22/10/2026):** localização da consulta, busca por marca/modelo,
> download da foto, compartilhamento em JPEG e recuperação da câmera.
> O histórico passa a guardar rua, número aproximado, bairro, cidade, estado e CEP
> no momento da consulta, sem bloquear a leitura. A busca do histórico aceita placa,
> fabricante ou modelo. A tela de detalhes ganhou **Baixar foto** e o ícone
> **Compartilhar**, que gera um card JPEG local. A câmera recebeu foco por toque,
> foco central, estabilização opcional, watchdog de preview e rebind automático ao
> voltar do background. O pipeline mantém os mesmos modelos ONNX, mas passa a
> processar até três caixas de placa por quadro. O banco foi migrado de 3 para 4
> preservando os registros. APK daquela build: `apk/VigiAuto-1.6.2-debug.apk`.

Aplicativo Android para leitura automática de placas brasileiras com a câmera traseira,
consulta de dados veiculares, captura automática de fotografia e histórico local.

Projeto Kotlin + Jetpack Compose, organizado em camadas independentes. O processamento de
imagem e as consultas rodam fora da thread principal; a leitura continua ativa enquanto
consultas, pop ups, fotografias e histórico são utilizados.

A **V2 (1.5.x)** adota por padrão um pipeline **detector-primeiro**: um YOLOv9 local
localiza a placa e apenas o recorte é enviado ao OCR especializado (CCT). O ML Kit deixa
de percorrer o quadro inteiro e passa a atuar como segunda opinião no recorte e como
contingência. O objetivo é reduzir falsos positivos em fachadas, adesivos e letreiros sem
interromper a câmera. A partir da 1.5.0, o modo padrão de consulta é o **Sinesp v1**, com
consulta real ao protocolo do SINESP Cidadão usando o token pessoal do gov.br; a **Consulta
Simples** (API Consulta Placa) continua disponível como alternativa. Na 1.5.1, o login
gov.br pode ser feito dentro do app, e sem token as leituras usam a Consulta Simples
automaticamente. A revisão atual também mostra **cidade/estado** na identificação, adiciona
o botão **8x** na câmera e executa a consulta pública **NMT/OTTC** da Prefeitura de São
Paulo em background, sem tela ou indicador visual; o resultado é gravado em **Categoria**.
O relatório técnico do pipeline está em [`docs/RELATORIO-V2.md`](docs/RELATORIO-V2.md).

A **1.5.2** corrigiu a inicialização da câmera em aparelhos que rejeitavam a
combinação de preview + análise usada antes. A **1.5.3** manteve os mesmos
modelos ONNX e o mesmo comportamento de leitura, e ajustou o botão **Remover**
da tela de detalhes para ficar em uma única linha. A **1.5.4** corrige a
categoria que desaparecia quando uma consulta NMT posterior falhava: uma falha
temporária não substitui mais um resultado `REGULAR`, `IRREGULAR` ou
`NÃO CADASTRADO` já concluído. Também reduz o custo por quadro sem trocar os
modelos: a fotografia só é convertida quando uma placa é confirmada, o CameraX
entrega o buffer já girado, os buffers do detector/OCR são reutilizados e o OCR
deixa de processar caixas que seriam descartadas. A build final também remove um
aviso falso de falha da câmera quando a inicialização é cancelada pelo ciclo de
vida; o preview continua funcionando. O benchmark offline dos modelos permanece
o mesmo.

> **Uso em moto:** fixe o aparelho em um suporte rígido e configure tudo antes de sair.
> Não manuseie o celular nem leia pop ups enquanto pilota. A consulta é automática e a
> vibração pode servir de alerta sem exigir que você olhe para a tela.

## Requisitos

- Windows com JDK 17 (testado com Temurin 17.0.20.1)
- Android SDK Platform 35 e Build-Tools 35.0.0
- Gradle 8.9 (wrapper incluído)
- Dispositivo Android 8.0+ (minSdk 26). Referência: POCO X7 Pro

O arquivo `local.properties` aponta para o SDK desta máquina; em outro computador ajuste
` sdk.dir` (ou remova o arquivo e deixe o Android Studio criar).

## Compilar e instalar

```bat
cd "C:\Users\Guilherme\Documents\VigiAuto"
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`. O artefato desta versão
está em `apk/VigiAuto-1.8.0-debug.apk` (tamanho e SHA-256 no fim deste documento).
Os APKs anteriores foram preservados em `apk/` apenas como histórico; o pacote
novo é `br.com.vigiauto`. O APK é **arm64-v8a**, ABI do POCO X7 Pro; para outro
tipo de aparelho, ajuste `abiFilters` em `app/build.gradle.kts`.
No HyperOS, se o `adb install -r` retornar `INSTALL_FAILED_USER_RESTRICTED`,
use `adb install -r --user 0 --no-streaming` ou habilite **Instalar via USB**.

> **Limpeza de 21/10/2026:** APKs históricos e diretórios gerados (`app/build`, `.gradle`,
> `.kotlin`) foram removidos. Em `apk/` ficaram a **1.5.1** (referência do modelo), a
> **1.5.2** (correção de câmera validada) e a **1.5.3** (ajuste visual do botão Remover);
> a **1.5.4** foi adicionada em 22/10/2026.
> O próximo `gradlew.bat assembleDebug` recria `app/build`. `local.properties` foi
> preservado porque aponta para o SDK Android desta máquina; `dataset/` mantém as anotações
> e os assets de teste. O `tools/verify_onnx.py` e os arquivos de identidade visual também
> foram mantidos.

Testes e verificação:

```bat
gradlew.bat testDebugUnitTest assembleDebug lintDebug
gradlew.bat assembleDebugAndroidTest
gradlew.bat connectedDebugAndroidTest
```

`connectedDebugAndroidTest` exige um aparelho ou emulador conectado. Os testes
instrumentados incluem a fumaça do ONNX (`OnnxPlateEngineTest`), o benchmark do dataset
(`OnnxDatasetBenchmarkTest`) e o benchmark do ML Kit (`OcrDatasetTest`). Em aparelhos
HyperOS, se a instalação do APK de testes for bloqueada, o runner pode desinstalar o app;
faça uma cópia do banco antes, pois fotos privadas e tokens do Keystore não são
recuperáveis após a desinstalação.

## Arquitetura

```
app/src/main/java/br/com/vigiauto/
├── alpr/              YOLOv9 + CCT em ONNX (OnnxPlateEngine, 100% local)
├── camera/            CameraX, detector-primeiro (OnnxPlateAnalyzer) e fallback ML Kit
├── ocr/               ML Kit Text Recognition (OcrEngine): 2ª opinião no recorte e fallback
├── domain/
│   ├── model/         VehicleRecord, PlateCapture, VehicleData, enums de estado
│   └── plate/         padrões, normalização, tracking visual e confirmação
├── data/
│   ├── db/            Room v5 (índice UNIQUE em normalizedPlate)
│   ├── repo/          VehicleRepository (deduplicação, consultas, fotos, limites)
│   ├── lookup/        Consulta Simples, Sinesp v1, HTTP manual e DEMO
│   ├── photos/        captura/recorte, armazenamento privado + ring buffer
│   ├── prefs/         DataStore
│   └── network/       monitor de conectividade
├── core/              AppContainer (DI manual), TokenCipher (Keystore)
└── ui/                Compose: main, history, details, settings, tema
```

Regras implementadas:

- **uma única linha por placa** (chave normalizada + índice único; inserção `IGNORE` e
  atualização transacional da última visualização/ocorrências);
- **detector-primeiro**: YOLOv9 localiza a placa; o CCT lê apenas o recorte. Textos
  comuns no quadro inteiro não são mais candidatos;
- **confirmação por repetição na mesma caixa rastreada** antes de consultar: no motor
  ONNX, no mínimo 2 leituras (3 em modo moto), 75% de concordância e cooldown de 12 s;
  duas caixas distantes nunca são somadas;
- **segunda opinião restrita ao recorte**: o ML Kit só é acionado quando o CCT devolve
  texto inválido ou com baixa confiança, e não pode trocar a placa por uma hipótese
  visualmente incompatível;
- **fotografia quadrada e recorte real da placa preservados**: novas leituras nunca
  substituem a captura existente; o recorte usa a caixa do YOLO no mesmo quadro e
  mantém os caracteres, cores, bordas e parafusos originais;
- **consultas com no máximo 2 em paralelo**, deduplicadas por placa, com retentativa
  automática de falhas de conexão quando a rede volta;
- **leitura contínua**: OCR em executor dedicado, descarte de quadros atrasados e
  processamento independente da navegação.

## Motor V2: detector visual local

O motor padrão é o **ONNX** (`DetectionEngine.ONNX`), selecionável em
Configurações → Leitura e reconhecimento → “Detector visual de placas (YOLO + CCT)”.
Desligar a opção restaura o pipeline ML Kit anterior.

```
quadro da câmera
  → YOLOv9 (384×384): localiza regiões de placa
  → recorte de cada caixa
  → CCT (128×64): lê somente o recorte
  → validação (padrão brasileiro, confiança, proporção)
  → tracking por caixa (IoU + proximidade)
  → confirmação por consenso
  → deduplicação por placa
  → consulta e fotografia
```

Modelos embarcados em `app/src/main/assets/models/` (nenhum download em runtime):

| Arquivo | Tamanho | SHA-256 | Entrada | Saída |
|---|---:|---|---|---|
| `detector.onnx` | 7.771.218 bytes | `888397B96D761C89DB40BC9C305838E8652660F5E282C2CADEBBE8D2951A77A8` | `float32[1,3,384,384]` | `float32[batch,7]` (x1, y1, x2, y2, classe, score) |
| `ocr.onnx` | 3.035.990 bytes | `5237B829CF6783D0CA633AC1A2C578B577FD8387FFA91FE825B0D482BD234ABC` | `uint8[1,64,128,3]` | `float32[batch,10,37]` (10 posições, alfabeto `0-9A-Z_`) |
| `ocr_fallback.onnx` | 5.262.230 bytes | `384BBBD2CEA3EF54761D3DF70822EF3A349EE1A112AEAFDDBE0E3BA06BC6E47B` | `uint8[1,64,128,3]` | `float32[batch,10,37]` |

- `detector.onnx`: `yolo-v9-t-384-license-plate-end2end`, inalterado.
- `ocr.onnx`: ajuste `e2_lr1e4_e26`, somente cabeça de placa, opset 15, lote estático 1.
- `ocr_fallback.onnx`: o CCT anterior do app (`cct-s-v2-global-model`), usado apenas
  pela política condicional. Ele não é o checkpoint `cct_xs_v2_global.keras`.
- As duas sessões de OCR ficam carregadas, mas compartilham o mesmo tensor de entrada.
  A segunda inferência só acontece nos casos previstos pela política; leituras válidas
  e confiantes continuam pagando uma única passada.
- Em memória, o custo permanente é uma sessão extra; o APK cresce **5.262.230 bytes**
  pelo arquivo adicional, parcialmente compensado pelo `ocr.onnx` menor.

Regras do motor:

- o detector descarta caixas com score abaixo de **0,30**;
- a aceitação final usa limiares que acompanham a sensibilidade configurada: no padrão
  (0,70), detector ≥ **0,43**, OCR ≥ **0,55** e score combinado ≥ **0,546**;
- a caixa precisa ter proporção entre **0,8 e 6,0**, o que admite caixas de carro e de
  moto;
- o OCR principal é o `ocr.onnx`; a segunda opinião `ocr_fallback.onnx` é consultada
  quando o texto principal não forma placa válida ou quando o menor caractere fica
  abaixo de **0,50**. Se a principal já é válida, o fallback só a substitui quando
  também é válido, tem confiança média ≥ **0,90** e difere em exatamente um caractere
  não equivalente (I/1, O/0, U/V, C/G, Q/O). Texto vazio não dispara a segunda
  inferência;
- depois disso, o CCT é preservado quando já leu uma placa válida com confiança de OCR
  ≥ 0,55 e confiança mínima por caractere ≥ 0,30; caso contrário, o ML Kit tenta uma
  segunda leitura **apenas no recorte**;
- **motos em duas linhas:** o CCT recebe o recorte inteiro; se a leitura não passar na
  validação, o ML Kit com `CandidateExtractor` tenta juntar as linhas. Esse caso ainda
  não foi validado em campo;
- o tracker associa detecções por IoU ≥ 0,15 ou por proximidade com área compatível,
  expira trilhas após 1,5 s e exige 2 leituras (3 em modo moto) com **75% de
  concordância**; a mesma placa não é confirmada de novo em 12 s;
- no motor ONNX, duas caixas distantes nunca somam leituras, mesmo que o OCR produza a
  mesma string.

Se a inicialização do ONNX falhar (ABI, memória ou arquivo ausente), o app avisa e usa o
ML Kit. Se cinco quadros consecutivos gerarem exceção na inferência, ele troca a
preferência para ML Kit e persiste a mudança. O APK atual é **arm64-v8a**; em outra ABI o
detector não inicializa e o fallback é acionado.

**Licença e procedência:** o código de integração segue o `fast-alpr` (MIT), mas os pesos
ONNX vieram do projeto de referência e sua licença/procedência ainda precisa ser
confirmada antes de distribuição comercial.

## Desempenho da detecção

- frequência padrão de **12 análises/s** (ajustável de 1 a 15); o limite é um teto, não uma
  garantia: a inferência real pode ocupar mais de um intervalo e reduzir a taxa efetiva;
- resolução de análise configurável: **Rápida 960×540**, **Equilibrada 1280×720**
  (padrão) ou **Alta 1920×1080**;
- o detector ONNX usa 384×384 e o OCR 128×64; as sessões usam 4 threads intra-op;
- o fallback de OCR não roda em todo quadro: ele só é acionado para leitura inválida ou
  com caractere fraco. O custo permanente é uma sessão extra, não uma segunda inferência
  por análise; isso evita aquecimento desnecessário no Poco X7 Pro;
- o bitmap vertical do quadro é criado uma única vez e só quando há leitura de placa;
  a conversão NV21 da fotografia ficou restrita à contingência em que o recorte direto
  falha, em vez de acontecer em todos os quadros;
- `STRATEGY_KEEP_ONLY_LATEST` + flag de ocupação descartam quadros atrasados, mantendo a
  câmera fluida;
- em 12 análises/s, duas leituras consecutivas ficam separadas por pelo menos 83 ms; o tempo
  até a confirmação depende da inferência, da iluminação e do movimento;
- uma leitura excepcional (detector ≥ 0,88, OCR ≥ 0,92 e menor caractere ≥ 0,60) é
  confirmada no **mesmo quadro**; as demais mantêm 2–3 observações na mesma trilha;
- **estabilização de câmera** (OIS/EIS) solicitada ao aparelho quando suportada; ajuda a
  reduzir vibração e fotos tremidas em movimento;
- **modo moto** (ativo por padrão): leituras fortes podem ser confirmadas em 2 quadros e
  leituras oscilantes exigem 3+ observações com 75% de concordância na mesma trilha; no
  fallback ML Kit, usa o consenso de 3+ leituras na janela de 3 s, com no máximo 1
  caractere de diferença e 60% de concordância por posição;
- no motor ONNX, a fotografia quadrada e o recorte são produzidos no **quadro que
  confirmou a leitura**, usando a mesma caixa rastreada; isso evita associar a imagem
  ao veículo errado quando há mais de uma placa na cena;
- no fallback ML Kit, a captura também é tentada no quadro da confirmação; o buffer
  de quadros pela **nitidez** (variância do Laplaciano no plano de luminância)
  permanece apenas como contingência quando essa captura não pode ser gerada.

Ainda **não há medição de latência, CPU, memória ou acurácia em aparelho**. O tempo final
depende do aparelho, iluminação, distância/ângulo da placa e versão do ONNX Runtime/ML Kit.
Os modelos são pré-treinados; não há aprendizado em tempo de execução.

## Captura e recorte da placa (1.8.0)

A captura só é produzida depois que a placa é confirmada pelo consenso de leituras:

- a **fotografia** é um recorte quadrado do quadro real, deslocado para cima em relação
  à placa para preservar o veículo; ela é reduzida no máximo a 1080 px de lado e gravada
  em JPEG 88;
- a **miniatura** é recortada da própria fotografia quadrada, mapeando a caixa da placa
  para o mesmo enquadramento e aplicando margem curta (10% na horizontal, 14% na
  vertical). Assim ela é literalmente um pedaço da foto exibida, sem deslocamento para
  outro recorte;
- o **recorte** preserva exatamente `PlateDetection.box` / `PlateCandidate.box`, sem
  redesenho de caracteres. Ele é ampliado proporcionalmente com interpolação até cerca de
  240 px de altura (limite de 6x) e gravado em JPEG 93; a caixa é axis-aligned, portanto
  **não há correção de perspectiva**;
- a miniatura com borda azul é sobreposta no canto inferior esquerdo da foto na tela de
  detalhes e no card compartilhado; o toque nela abre o recorte ampliado;
- junto com os dois arquivos, o banco v5 guarda texto do OCR, coordenadas normalizadas da
  caixa, confiança do detector e do OCR, proporção do recorte, qualidade da foto/placa e
  o estado de verificação;
- a foto e a miniatura usam nomes ligados ao ID (`vehicle_<id>...` e `plate_<id>...`) no
  armazenamento privado e continuam disponíveis após reiniciar o app;
- uma leitura duvidosa mantém o recorte original e mostra **verificação recomendada**.
  Não há IA generativa, substituição de placa, backend, upload ou OTA.

Em cenas com vários veículos, cada placa confirmada gera sua própria captura a partir da
sua caixa e **todas são consultadas no mesmo quadro**; o app não para na primeira. A regra
de deduplicação por placa continua valendo: a captura existente não é substituída
automaticamente.

## Consulta Simples (modo alternativo)

O contrato foi confirmado pela engenharia reversa e validado contra o serviço em
19/10/2026:

```http
GET https://placa.costaw.com.br/api?placa=ABC1D23&token=...
Accept: application/json
```

A URL e o token **não ficam embutidos no APK**. Eles são obtidos em runtime do remote
config público do próprio serviço:

```text
https://costaw.com.br/remote-config/consultaplaca.json  →  campo "apiPlaca"
```

O app valida que a URL é HTTPS, substitui o marcador `placaaqui` pela placa, preserva o
token e usa cache de 1 hora. Em caso de `401/403`, a configuração é invalidada para ser
buscada novamente. A **Consulta Simples** é uma alternativa; o padrão da 1.5.x é o
**Sinesp v1**, que exige o token pessoal do gov.br configurado uma vez no aparelho. Sem
token, as leituras usam a Consulta Simples automaticamente.

Resposta observada (HTTP 200, `application/json`):

```json
{
  "placa": "ABC1D23",
  "marca": "FIAT",
  "modelo": "CRONOS DRIVE 1.0",
  "ano": "2023",
  "anoModelo": "2023",
  "cor": "BRANCA",
  "situacao": "SEM RESTRICAO",
  "extra": {
    "ano_fabricacao": "2023",
    "ano_modelo": "2023",
    "tipo_veiculo": "Automovel",
    "especie_veiculo": "Passageiro",
    "restricao_1": "SEM RESTRICAO",
    "restricao_2": "SEM RESTRICAO",
    "restricao_3": "SEM RESTRICAO",
    "restricao_4": "SEM RESTRICAO"
  }
}
```

Mapeamento implementado em `VehicleResponseMapper`:

- marca, modelo, cor, ano de fabricação/modelo, categoria e fonte;
- restrições a partir de `situacao` e `extra.restricao_1..4`, com precedência para uma
  restrição positiva real sobre um “SEM RESTRICAO” genérico;
- HTTP 404 + `{"message":"Veículo não encontrado"}` vira o estado **Veículo não
  encontrado** (sem inventar restrições);
- conexão, autenticação, limite de uso, resposta inválida e resultado incompleto têm
  estados separados;
- a ausência de dados nunca é tratada como “sem restrições”.

O token é público no remote config do provedor e é mantido apenas em memória; nenhuma
credencial é embutida no APK. O uso continua sujeito aos termos, cobertura e limites do
serviço. O modo **HTTP manual** permanece disponível em Configurações para um endpoint
alternativo (com token criptografado no Android Keystore), o modo **Sinesp v1** é o padrão
da 1.5.0 e usa o token pessoal do gov.br, e o modo **DEMO** continua existindo para testes
com dados fictícios.

**Limitações externas:** o serviço pode rotacionar o token, mudar o schema, limitar
consultas ou não cobrir determinado veículo/UF. Nesses casos o app mostra o estado
correspondente; não há tentativa de contornar autenticação ou limites.

### Sinesp v1 (padrão, com login gov.br)

O chip **Sinesp (padrão)** (`LookupMode.SINESP`) consulta o protocolo v1 do aplicativo
oficial do SINESP Cidadão. Ele é o **modo padrão da 1.5.x** e faz consultas reais quando o
token pessoal do gov.br está configurado. A **Consulta Simples** continua disponível como
alternativa.

Como funciona:

- consulta `GET https://cidadao2.sinesp.gov.br/api/v1/vehicles/{placa}`;
- envia `Authorization` com o token pessoal do gov.br (token cru, sem `Bearer`), a
  assinatura `AppEnsurer`, `UniqueId` e os cabeçalhos `Geolocation`,
  `GeolocationTimestamp` e `GeolocationAccuracy`;
- o token pode ser obtido pelo botão **Login gov.br** em Configurações → Sinesp: o app
  abre o site oficial do gov.br em um WebView, recebe apenas o `code` de retorno e o troca
  pelo token em `POST /auth/token`, validando em seguida em `GET /me`;
- o app não vê nem armazena a senha do gov.br; o token fica cifrado no Android Keystore e
  nenhum token é embutido no APK;
- quem preferir pode colar um token já obtido; ao salvar, o app ativa automaticamente o
  modo Sinesp; instalações antigas que estavam na Consulta Simples são migradas uma única
  vez para o novo padrão;
- sem token, a tela principal mostra **“Sinesp sem token • Consulta Simples”** com um
  atalho para Ajustes, e as leituras consultam automaticamente pela Consulta Simples; o
  botão **Verificar configuração** continua testando o Sinesp e avisa que falta o token;
- a localização padrão é um ponto neutro em Brasília (`-47.8825,-15.7942`) e pode ser
  trocada; o app não acessa o GPS nesse modo;
- erros: 401/403 → token recusado/expirado; 404 → placa não encontrada; 429 → limite de
  consultas; 400 com `Geolocation` → localização inválida; 5xx/timeout → falha de conexão;
- a resposta v1 traz `alert` e `hasCortexValidation`; um `alert` vazio vira **sem
  restrição identificada**, um alerta ROUBADO/FURTADO vira **restrição confirmada** e um
  tipo desconhecido fica **inconclusivo** — nunca “sem restrição”.

O endpoint **não é uma API pública para terceiros**. O protocolo usa uma chave interna do
aplicativo oficial e o token pessoal do gov.br; ele pode mudar sem aviso e o token expira.
Use como o app oficial: consulta pontual de um veículo que você está vendo, sem consulta em
massa, sem monitoramento de pessoas e respeitando a LGPD. Para uso comercial/produto,
procure o canal oficial do SERPRO/SINESP.

Se o WebView do gov.br não abrir ou travar, o diálogo de login oferece **“Já tenho a URL
de retorno”**: abra a URL de login no Chrome do celular, faça o login, copie a URL que
termina em `.../api/govbr/redirect?code=...` e cole no app. O login do projeto
`detector-placas` (`login-sinesp.bat`/`tools/login_sinesp.py`) também pode ser usado para
obter um token e colá-lo manualmente. O VigiAuto não compartilha o token com nenhum
outro destino.

### Roubo e furto

A API usada na **Consulta Simples** (`/api?placa=...`) devolve apenas `situacao` e
`extra.restricao_1..4`; o JSON observado **não contém histórico de roubo/furto**. O modo
**Sinesp v1** usa o array `alert` e o campo `hasCortexValidation` da resposta oficial.
Nenhum modo trata “SEM RESTRICAO” como prova de que o veículo nunca foi roubado.

Os mapeadores reconhecem, quando a fonte fornecer:

- valores como `ROUBO`, `ROUBADO`, `FURTO`, `FURTADO`, `EXTRAVIO` e `ROUBO_FURTO` (com ou
  sem acento);
- booleanos/“sim” em campos de restrição (`roubo: true`, `possui_registro: "sim"`);
- os indicadores `indicadorRouboFurto`, `indicadorRestricaoRenajud`, `indicadorLeilao` e
  `indicadorRestricaoRfb`; `indicadorAlarme` e `indicadorSiniav` são tratados como
  inconclusivos, não como roubo/furto;
- o schema `dados.historico_roubo_furto.registros_roubo_furto` da API oficial
  `consultarHistoricoRouboFurto`, inclusive `tipo_ocorrencia`.

Quando o resultado é positivo, o pop-up, o histórico e os detalhes exibem um bloco
vermelho **“Roubo/Furto identificado”** com a descrição devolvida pela fonte. A ausência
de dados continua sendo mostrada como “situação desconhecida”, nunca como “sem
restrição”.

O aplicativo provedor analisado (Consulta Placa, `br.com.costaw.consultaplaca`) usa um
relatório completo pago, aberto como PDF, e o endpoint `consultacompleta/api` exige a
validação de uma compra RevenueCat (`rcUserId`, `transactionId`, `productIdentifier`).
Não é possível obter esse conteúdo sem uma compra/credencial válida, e o APK do VigiAuto não contém nem contorna essa autenticação.

Outra alternativa autorizada é a API oficial
`GET https://api.consultarplaca.com.br/v2/consultarHistoricoRouboFurto` (Basic Auth com
e-mail + API key), que exige créditos e retorna `possui_registro` e os boletins de
ocorrência. A integração automática dessa API oficial depende de uma conta/API key do
usuário; nenhuma credencial é embutida no APK.

## Dataset de placas reais

As 9 fotos fornecidas foram anotadas em `dataset/labels.csv`, com 10 leituras (incluindo
duas placas na mesma cena). As imagens estão em
`app/src/androidTest/assets/dataset/`; `dataset/` mantém as anotações. Elas **não
treinam** nenhum modelo em execução — servem como validação/calibração. O caminho ML Kit
(`CandidateExtractor`) tem suporte a **placas de moto em duas linhas**
(`SUK0E51`, `STE7129`, `UFG0B86`, `SVZ3B37`) e a separadores (`EFR-8092`), casos presentes
nas anotações. No motor ONNX, o caso depende da segunda opinião.

Para rodar os benchmarks:

1. as imagens já estão em `app/src/androidTest/assets/dataset/` com os nomes de
   `dataset/labels.csv`;
2. conecte um aparelho e execute `gradlew.bat connectedDebugAndroidTest`.

Testes de dataset:

- `OcrDatasetTest` mede o pipeline ML Kit e exige acurácia mínima de 70%;
- `OnnxDatasetBenchmarkTest` mede o pipeline ONNX por imagem: esperadas, detectadas,
  acertos, recall, precisão, falsos positivos, latência, tempo de CPU e PSS aproximado;
  grava `alpr-benchmark.csv` na pasta externa do app.

Resultado no POCO X7 Pro: 10 placas esperadas, 10 detectadas, **9 acertos exatos**,
recall 0,900, precisão 0,900, 1 falso positivo e 1 falso negativo. Na passagem aquecida, a
latência média foi **101,1 ms**, o CPU médio **99,4 ms** e o PSS **170.543 KB → 208.504
KB**. Esse número é da 1.4.1, com um único modelo OCR; a 1.8.0 carrega também o
fallback e ainda não foi medida no aparelho. A única divergência é `STE7129` lida como
`STE7I29` (ambiguidade `I`/`1`).

O ML Kit acertou **9/10 leituras (90%)** no mesmo conjunto, mas leu `UFG6B86` onde a
anotação confirmada é `UFG0B86`. O teste do ML Kit não mede precisão nem falsos positivos,
então não há base para declarar um vencedor. A meta de 25 km/h continua não medida; 101 ms
é latência de imagem estática.

Nove fotos são insuficientes para treinar um detector; nenhum treinamento com elas foi
executado e nenhuma métrica de treino é apresentada. Para um detector próprio seria
necessário um conjunto bem maior, com caixas delimitadoras anotadas.

A lógica de letterbox/recorte/OCR pode ser conferida fora do Android com
`tools/verify_onnx.py` (detalhes em `dataset/README.md`).

### Level 2 — preparação offline

O Level 2 começou com as 26 imagens de `C:\Users\Guilherme\Downloads\TREINO IA`.
Elas foram copiadas e anotadas em `dataset/level2/`, com validador, benchmark e
exportação YOLO/OCR próprios. Com as **27 anotações confirmadas**, o detector
mantém **25/27 (92,6%)** de recall com IoU ≥ 0,30 e o pipeline ONNX da 1.8.0
atinge **23/27 (85,2%)** de acerto exato após a normalização do app, contra
**19/27** do modelo anterior. O recorte anotado fica em **23/27 (85,2%)** e
**27/27** leituras válidas; o conjunto alvo separado de 24 recortes reais fica em
**23/24 (95,8%)**.

| Conjunto | Modelo anterior | E2 sozinho | E2 + fallback (1.8.0) |
|---|---:|---:|---:|
| Alvo, 24 recortes reais | 19/24 (79,2%) | 22/24 (91,7%) | **23/24 (95,8%)** |
| Pipeline Level 2, 27 imagens | 19/27 (70,4%) | 21/27 (77,8%) | **23/27 (85,2%)** |
| Recorte anotado, 27 imagens | 22/27 (81,5%) | 22/27 (81,5%) | **23/27 (85,2%)** |
| Validação interna | 6/6 | 5/6 | **6/6** |
| Teste interno | 3/6 | 5/6 | **5/6** |

O fallback foi usado em **2** casos do pipeline (`L2-002` e `L2-026`) e em **1**
recorte anotado (`L2-013`). As falhas restantes do pipeline são `L2-013`
(`SGL5153`), `L2-017` (`ZZE6390`) e as placas `L2-022`/`L2-025`, que o detector
não encontrou.

O `ocr.onnx` da 1.8.0 foi ajustado fora do app com **50 amostras de 44 grupos**
(38 treino, 6 validação, 6 teste, sem vazamento de cena/placa). A configuração
escolhida foi `lr 1e-4`, 26 épocas, paciência 8, lote 8, seed 42, EMA, label
smoothing 0,01 e weight decay 0,01; o experimento E2 foi o melhor de quatro. O
conjunto interno de validação ficou em **6/6** e o de teste em **5/6**. O detector
continua o mesmo, e não há pacote de treino recuperado para ele. O plano, os
comandos e os critérios estão em
[`dataset/level2/README.md`](dataset/level2/README.md).

### Treino adicional de 26/10/2026

As 45 imagens novas de `TREINO IA\TREINO` (44 usáveis) foram usadas em uma rodada
controlada. No OCR, **E5** caiu para 22/27 no pipeline, **E6** ficou em 22/27 e
22/24 no alvo, e **E7** empatou com o E2 em todos os conjuntos de avaliação,
apenas trocando um acerto de treino por outro (`DQK1266` passou, `LMO8G75` deixou
de ser corrigido). O **E2 continua** sendo o `ocr.onnx` da 1.8.0. No detector, um YOLOv8n
ajustado com 44 caixas geradas pelo próprio detector caiu de **23/27** para
**11/27** no Level 2 e foi descartado; o `detector.onnx` não mudou. Detalhes,
comandos, hashes e artefatos em
[`docs/TREINO-2026-10-26.md`](docs/TREINO-2026-10-26.md).

## Funcionalidades

Implementadas:

- câmera traseira (CameraX), seleção entre lentes traseiras quando disponíveis;
- **detector visual ONNX ativo por padrão** (YOLOv9 + CCT), com segunda opinião ML Kit
  restrita ao recorte e fallback automático para o pipeline anterior;
- reconhecimento de placas antigas e Mercosul; motos em duas linhas têm suporte no
  caminho ML Kit/segunda opinião, ainda não validado no motor ONNX;
- normalização posicional O/0, I/1, B/8 etc., sem alterações arbitrárias. Nas placas
  Mercosul (`ABC1D23`), a letra **O** vale nas posições 1, 2, 3 e 5 e o dígito **0** nas
  posições 4, 6 e 7; nas antigas (`ABC1234`), a letra **O** vale nas posições 1, 2 e 3 e
  o dígito **0** nas posições 4 a 7. A quinta posição é a única ambígua entre os dois
  padrões e sua variante O/0 é a primeira hipótese consultada;
- **desambiguação de leitura**: quando a placa principal não é encontrada, o app testa
  primeiro a variante O/0 da quinta posição e depois hipóteses visuais de I/1, O/0 e U/V
  (inclusive duas trocas); um sucesso sem dados do veículo também libera as alternativas.
  Se a consulta confirmar outra placa, a correção fica persistida para evitar duplicatas;
- **Sinesp v1 como modo padrão**: consulta real ao protocolo do SINESP Cidadão com o token
  pessoal do gov.br, cifrado no Android Keystore; o login gov.br pode ser feito dentro do
  app (WebView) ou por token colado. Sem token, as leituras usam a Consulta Simples
  automaticamente e a tela mostra um aviso com atalho para Ajustes;
- **Consulta Simples** (API Consulta Placa) alternativa, com URL/token obtidos do remote
  config em runtime e mantidos apenas em memória;
- **cidade e estado** retornados pela fonte aparecem na seção **B · Identificação do
  veículo**, ao lado da categoria;
- **consulta pública NMT/OTTC** da Prefeitura de São Paulo: um worker silencioso consulta
  a página oficial em background, sem tela, modal, botão, spinner, toast, notificação ou
  qualquer indicador. Ao cadastrar/importar uma placa, a consulta entra em uma fila
  persistente, preenche a placa, dispara a pesquisa e grava o resultado em **Categoria** —
  “Veículo de aplicativo - Situação regular”, “Veículo de aplicativo - Situação irregular”
  ou “Veículo particular”. Falhas de rede, timeout ou reCAPTCHA ficam internas
  (`Unavailable`) e são repetidas depois, sem aviso ao usuário;
- **zoom 8x** na tela da câmera, em botão ao lado do histórico; o zoom é digital, limitado
  ao máximo suportado pela lente e volta a 1x ao tocar novamente;
- modo **HTTP manual** para endpoints alternativos autorizados e modo **DEMO** com dados
  fictícios; a DadosAPI foi removida;
- confirmação por leituras repetidas, sensibilidade, frequência (1–15 fps) e resolução
  de análise ajustáveis;
- deduplicação por placa com contador de ocorrências;
- captura automática da fotografia quadrada e do recorte real da placa, com miniatura
  sobreposta e ampliação por toque; o fallback ML Kit mantém o buffer pela nitidez;
- estabilização OIS/EIS opcional e modo moto com consenso entre leituras;
- pop ups não modais com fila, duração e supressão de alertas repetidos configuráveis,
  atualizados ao concluir a consulta; status curtos em pílulas e mensagens longas em
  linhas, abaixo de “Consulta concluída”;
- alerta vermelho específico para roubo/furto quando a fonte informar a
  restrição, com destaque também no histórico e nos detalhes;
- ícone adaptativo próprio (emblema VigiAuto) para launcher e ícone redondo;
- histórico com pesquisa, filtros (incluindo “não encontradas”), ordenação e exclusão
  confirmada;
- detalhamento com foto quadrada, miniatura do recorte (zoom), identificação, restrições,
  confianças, fonte e horários;
- configurações completas, tema claro/escuro, exportação JSON, exclusão em massa;
- retentativa automática ao voltar a conexão; estados de consulta diferenciados;
- armazenamento privado, HTTPS, backup desativado; token do remote config somente em
  memória e tokens do Sinesp e do modo HTTP manual criptografados no Keystore.

Dependem de informações externas:

- token do gov.br válido e não expirado para o modo Sinesp; o app faz o login no site
  oficial do gov.br em WebView, mas não emite credencial oficial;
- disponibilidade, cobertura e limites do SINESP Cidadão e do serviço Consulta Placa;
- disponibilidade do site OTTC/NMT e do reCAPTCHA invisível para a consulta silenciosa;
- teste em campo com o POCO X7 Pro e as condições reais de iluminação/ângulo;
- benchmark de OCR com as imagens copiadas para o projeto;
- confirmação da licença/procedência dos pesos ONNX para distribuição comercial.

## Privacidade e limites

- O detector e o OCR rodam localmente; nenhum quadro, recorte ou fotografia é enviado
  para a rede. Apenas a placa é enviada ao serviço de consulta.
- No modo Sinesp v1, a placa, o `UniqueId` e a localização configurada (ponto neutro por
  padrão) são enviados ao SINESP. O token pessoal do gov.br fica cifrado no Android
  Keystore, não é embutido no APK nem gravado em logs. O app não acessa o GPS nesse modo.
  O WebView de login carrega apenas o site oficial do gov.br; o app não injeta JavaScript
  nem lê a senha, e troca o `code` de retorno pelo token fora do WebView.
- A URL/token da **Consulta Simples** são obtidos do remote config em runtime e mantidos em
  memória; não são embutidos no APK nem gravados em logs.
- A consulta **NMT/OTTC** executa a página pública da Prefeitura de São Paulo em um
  WebView **oculto, nunca anexado a uma janela**: preenche apenas a placa e lê o bloco de
  resultado exibido pela própria página. O reCAPTCHA é executado pelo site oficial; o app
  não recebe, não resolve e não grava token, cookie, `__VIEWSTATE` ou o HTML completo no
  banco nem em logs. O `CookieManager` do WebView é compartilhado com o login gov.br; o
  worker não lê nem grava cookies. O resultado é salvo no banco local e só aparece como
  **Categoria** quando resolvido; falhas internas não geram aviso, tela, modal, toast, som
  ou notificação. Não há token gov.br nem chave de serviço embutida.
- Como este projeto não possui backend próprio, a automação NMT roda no processo do app em
  WebView oculto. A especificação recomenda um endpoint server-side (Playwright); a camada
  do worker está isolada para permitir essa migração sem alterar a UI.
- O token do modo HTTP manual é digitado pelo usuário e fica criptografado no Android
  Keystore; ele não é embutido no APK nem gravado em logs. O token legado da DadosAPI é
  apagado na inicialização.
- Placas não são gravadas em logs técnicos.
- Fotografias e histórico ficam no armazenamento privado do app; o export JSON é uma ação
  manual do usuário.
- Correções de leitura confirmadas pela consulta (placa lida → placa encontrada) ficam
  apenas no DataStore local do aparelho, para evitar registros duplicados.
- A ausência de dados não é interpretada como ausência de restrições.
- A leitura contínua da câmera depende da tela principal visível; não há serviço de câmera
  em segundo plano. O worker NMT roda no processo do app e retoma a fila persistida no
  próximo início, sem serviço em primeiro plano ou notificação.
- Em cenas com várias placas, cada confirmação usa a sua própria caixa no mesmo quadro
  para gerar a foto e o recorte. A caixa do YOLO é axis-aligned: o app não corrige
  perspectiva nem promete enquadramento perfeito. Se a nitidez ou a confiança forem
  baixas, a captura é mantida e marcada para verificação.
- O recorte da placa é sempre a imagem real da câmera; não há geração ou reconstrução de
  caracteres por IA. O card compartilhado não exibe mais a contagem de “Ocorrências”,
  apenas a confiança.
- Motos em duas linhas no motor ONNX: o CCT recebe o recorte inteiro; se devolver uma
  leitura válida porém errada, a segunda opinião não é acionada. O caso ainda não foi
  validado em campo.
- A fotografia manual é única por veículo: para trocá-la é preciso remover a atual antes
  (a original nunca é sobrescrita automaticamente); ao anexar uma foto manual, o recorte
  automático da placa é removido junto, para não exibir uma miniatura de outra captura.
- O APK desta versão é **arm64-v8a**. Outras ABIs exigem ajuste de `abiFilters` e um novo
  build; sem isso, o ONNX não inicializa e o app usa ML Kit.
- A consulta **NMT/OTTC** é uma leitura da página pública da Prefeitura, não uma API
  oficial: depende de internet, de um WebView compatível e do reCAPTCHA invisível do site.
  Mudanças no HTML (`#placa`, `#btn_veiculo`, `#retornoMsg`) podem exigir ajuste. O worker
  usa fila persistente, uma consulta por vez, cache de 24 h, repetição com espera e
  disjuntor após falhas seguidas. Uma falha de rede, timeout ou reCAPTCHA não apaga uma
  categoria NMT já concluída; o worker preserva `REGULAR`, `IRREGULAR` ou `NÃO CADASTRADO`
  e agenda nova tentativa. Na primeira abertura de cada versão, registros que ficaram sem
  categoria são reenfileirados uma vez. Se o site bloquear o WebView, a categoria anterior
  permanece e a consulta é repetida em silêncio; não há verificação manual. O fluxo foi
  validado no aparelho em 22/10/2026, com as categorias `REGULAR` e `NOT_REGISTERED`
  persistidas.
- O **zoom 8x** é digital; em lentes cujo máximo é menor que 8x o CameraX limita ao maior
  valor disponível. O zoom pode reduzir a nitidez e não garante leitura a longa distância.
- A meta de **25 km/h** é um requisito de teste, não uma garantia. Ela ainda **não foi
  medida** em campo; não há promessa de leitura universal em qualquer velocidade, ângulo
  ou iluminação.

## APK final

- Arquivo: `apk/VigiAuto-1.8.0-debug.apk`
- Versão: `versionCode 21`, `versionName 1.8.0` (pacote `br.com.vigiauto`)
- Tamanho: **68,52 MiB** (71.852.417 bytes; cerca de 71,85 MB em base decimal)
- SHA-256: `CA29BC34181C377580CE7E4ED03CE0210ABF7ED56320F9E90702FB21F6223560`
- Assinatura de depuração; instalável com
  `adb install -r --user 0 --no-streaming apk\VigiAuto-1.8.0-debug.apk`.
- Suíte local: **152 testes, 0 falhas** em 23 classes, executada em 25/10/2026
  (`gradlew.bat testDebugUnitTest`); `lintDebug` com **0 erros** e 36 avisos.
  `assembleDebug` e `assembleDebugAndroidTest` compilam; os testes instrumentados
  não foram executados porque não havia aparelho conectado.
- Banco Room **v5** (sem mudança nesta versão): a migração 4→5 foi aplicada em uma cópia
  do backup `backup/vigiauto-2026-10-22-pre164.db`; os **42 registros** foram preservados,
  `user_version` passou a 5 e as novas colunas ficaram com os padrões esperados.
  No aparelho, o app instalado ainda é a 1.6.4 (`versionCode 19`, banco v4); a
  instalação da 1.8.0 continua pendente. **Copie o banco e `files/photos` antes de
  instalar ou de executar UTP** e confirme a migração para v5 preservando os 42 registros.
- Card compartilhado: “Ocorrências” foi removido; a linha **Confiança** permanece.
  A miniatura real é recortada da própria fotografia quadrada, no canto inferior esquerdo;
  a validação visual no aparelho ainda não foi feita porque não havia dispositivo ADB
  conectado.
- A 1.8.0 troca apenas o OCR: o `ocr.onnx` é o ajuste E2 e o `ocr_fallback.onnx` é o
  modelo anterior. O `detector.onnx` e as resoluções de análise não mudaram. As demais
  mudanças de leitura são de cadência (12 fps padrão, até 15), confirmação imediata de
  leituras excepcionais, consulta de todas as placas do quadro e prioridade à variante
  O/0 da quinta posição.
- A amostra histórica do card 1.6.4 continua em `docs/share-card-1.6.4.jpg`; ela ainda
  contém “Ocorrências” e **não representa o card novo**. O card atual, sem essa linha e
  com a confiança preservada, está implementado em `ShareCardGenerator`; falta apenas
  gerar uma nova amostra visual no aparelho.

### Histórico 1.5.4

A 1.5.4 mantém `detector.onnx` e `ocr.onnx` iguais aos da 1.5.1. Ela não é um
modelo novo. Naquele build, o benchmark offline era **25/27 no detector (92,6%)**,
**20/27 no pipeline normalizado (74,1%)**, **23/27 válidos (85,2%)** e
**26/27 válidos no recorte anotado (96,3%)**; esses números foram superados pelo
OCR da 1.8.0. A consulta NMT foi validada no aparelho
em 22/10/2026; a latência de leitura de placas da 1.5.4 ainda não foi medida em campo,
porque as mudanças são de caminho de código, não de modelo.

Atualização de 22/10/2026 (1.5.4):

- **consulta NMT validada no aparelho**: duas placas reais que estavam sem categoria
  passaram a `REGULAR` (3,0 s, uma tentativa) e `NOT_REGISTERED` (79,1 s, duas tentativas)
  e foram persistidas no Room; na segunda, a primeira tentativa atingiu o timeout de 75 s
  e a repetição concluiu;
- **falha `webview_create` corrigida**: dentro de `view.apply`, o nome `handler` resolvia
  para `View.getHandler()` — nulo porque o WebView oculto não está anexado a uma janela —
  em vez do handler da sessão. A chamada foi movida para fora do `apply` e o campo passou
  a se chamar `mainHandler`;
- **aviso falso de câmera removido**: o cancelamento normal do escopo da composição não é
  mais tratado como falha, e uma inicialização bem-sucedida limpa o erro anterior. O
  preview já funcionava, mas o banner vermelho “The coroutine scope left the composition”
  podia permanecer na tela;
- **Categoria não desaparece mais**: falha de rede, timeout ou reCAPTCHA não sobrescreve
  `REGULAR`, `IRREGULAR` ou `NOT_REGISTERED` com `UNAVAILABLE`/`UNKNOWN`; a categoria
  anterior permanece visível e o worker reagenda a consulta;
- na primeira abertura de cada versão, registros que já estavam sem categoria por falhas
  antigas são reenfileirados uma vez; o ícone de atualizar da tela de detalhes também
  reinicia a consulta NMT do registro;
- a fotografia do veículo em NV21 só é preparada quando uma placa é confirmada, em vez de
  em todo quadro analisado;
- o CameraX entrega o buffer já girado, eliminando um segundo bitmap girado por quadro;
  se o aparelho recusar esse modo, o app tenta novamente com a rotação no código antigo;
- o detector e o OCR reutilizam bitmaps, arrays e buffers diretos; caixas com confiança
  abaixo de 0,33 não gastam OCR e no máximo duas caixas são lidas por quadro;
- leituras de alta confiança em movimento podem ser confirmadas em duas observações;
  leituras limítrofes continuam exigindo três;
- modelo, resolução de entrada e quantização não mudaram.

Atualização de 22/10/2026 (1.5.3):

- o botão **Remover** da seção **A · Fotografia do veículo** passa a ficar em uma
  única linha; o botão de adicionar/trocar foto usa o espaço restante;
- os modelos ONNX e o fallback de câmera da 1.5.2 não mudaram.

Atualização de 22/10/2026 (1.5.2):

- a câmera tenta a lente/resolução escolhida, depois sem estabilização, depois
  960×540, 640×480 e, em último caso, somente o preview;
- se a lente secundária não aceitar a combinação, volta para a câmera traseira
  padrão;
- o erro `No supported surface combination is found for camera device - Id : 0`
  deixa de derrubar a tela; o app informa quando abriu em modo somente preview;
- o modo de leitura e os modelos ONNX não mudaram.

Atualização de 21/10/2026 (revisão 3 da 1.5.1):

- **cidade e estado** da fonte agora aparecem na seção **B · Identificação do veículo**;
- **consulta NMT/OTTC** da Prefeitura de São Paulo 100% em background, por worker
  silencioso: sem tela, modal, popup, spinner, toast, botão, notificação, som ou texto de
  consulta; a placa é enfileirada ao ser cadastrada, o resultado é salvo no banco e aparece
  em **Categoria** como “Veículo de aplicativo - Situação regular”, “Veículo de aplicativo -
  Situação irregular” ou “Veículo particular”; falhas internas não alteram a categoria;
- worker com fila persistente, uma consulta por vez, intervalo de 2–5 s, cache de 24 h,
  três tentativas, repetição de falhas em 15 min/1 h, disjuntor de 30 min e logs com placa
  mascarada; o worker não grava token, cookie, `__VIEWSTATE` ou HTML completo no banco nem
  em logs;
- campo **Versão** removido da seção **B · Identificação do veículo**;
- **botão 8x** ao lado do histórico na câmera, com retorno a 1x no mesmo botão;
- banco Room **v1 → v2 → v3**: a migração 2→3 adiciona `nmtRawStatus`, `nmtAttempts` e
  `nmtNextAttemptAt`; desde a 1.5.4 ela preserva `nmtStatus`/`nmtCheckedAt` e apenas
  reinicia a fila, mantendo histórico, cidade, estado, fotos e categorias da fonte;
- exportação JSON inclui cidade, estado e o resultado NMT;
- a suíte passou de 109 para **117 testes em 17 classes**, incluindo parser e rótulos NMT;
- a consulta NMT depende do site público e do reCAPTCHA do WebView oculto; foi validada
  em aparelho na 1.5.4.

Mudanças da 1.5.1:

- **Login gov.br dentro do app** em Configurações → Sinesp: WebView no site oficial,
  captura do `code` de retorno e troca por token em `/auth/token`, com validação em `/me`;
- fallback **“Já tenho a URL de retorno”** para colar a URL do Chrome do celular se o
  WebView não abrir ou travar;
- o app não vê a senha do gov.br; o token continua cifrado no Android Keystore e **não é
  embutido no APK**;
- **Sinesp sem token agora usa a Consulta Simples automaticamente** na leitura; o banner e
  o status mostram “Sinesp sem token • Consulta Simples”, e **Verificar configuração**
  continua testando o Sinesp para não dar falso positivo;
- o antigo modo **Real** passou a se chamar **Consulta Simples** na interface, no status e
  na fonte dos registros;
- a paleta verde foi substituída por **azul escuro** no tema claro e azul de destaque
  legível no tema escuro;
- no Sinesp e na Consulta Simples, quando a consulta termina sem roubo/furto, a seção
  **C · Situação e restrições** mostra em azul **“Veículo sem registro ou alerta de
  roubo/furto”** no status e na Descrição; alertas não reconhecidos e históricos
  indisponíveis continuam inconclusivos;
- a suíte ganhou testes do login/troca de token com `MockWebServer`, da extração do
  `code`/expiração do JWT, do fallback sem token e da nova mensagem de restrição.

Mudanças da 1.5.0:

- **Sinesp v1 é o modo padrão** e faz consulta real ao protocolo do SINESP Cidadão quando
  o token pessoal do gov.br está configurado;
- o token é colado em Configurações → Sinesp, fica cifrado no Android Keystore e **não é
  embutido no APK**; salvar um token novo ativa o modo Sinesp automaticamente;
- instalações antigas que estavam na Consulta Simples são migradas uma vez para o Sinesp;
- o modo **Consulta Simples** (API Consulta Placa), o **HTTP manual** e o **DEMO**
  continuam disponíveis;
- a suíte ganhou testes do protocolo/mapeamento, do cliente HTTP com `MockWebServer` e do
  novo padrão de configuração.

**Para consultar de verdade:** instale o APK, abra Configurações → Sinesp e toque em
**Login gov.br** (ou cole um token já obtido) e salve. O token é pessoal, expira e precisa
ser renovado; o APK não contém nem emite credencial oficial. Sem token, as leituras já
funcionam pela Consulta Simples. Nenhuma consulta real foi executada nesta máquina porque
não havia aparelho conectado (`adb devices` vazio); a validação de ponta a ponta depende de
instalar no POCO, concluir o login gov.br e consultar uma placa com autorização.

O APK inclui o detector visual ONNX (YOLOv9 + CCT), o pipeline detector-primeiro com
tracking por caixa, fallback ML Kit, o modo **Sinesp v1** com cliente HTTP real, ícone
adaptativo, estabilização de câmera, modo moto, desambiguação de OCR por consulta e o
layout de status. O tamanho maior em relação à 1.3.0 vem do ONNX Runtime e dos dois
modelos embarcados.

O benchmark no POCO já foi executado (recall/precisão exatas de 0,900; 101,1 ms de latência
média na passagem aquecida). A meta de 25 km/h continua pendente de um teste controlado, sem
testar pilotando e manipulando o celular.
