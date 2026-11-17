# Relatório técnico — VigiAuto V2 (1.4.2)

**Data:** 20/10/2026  
**Projeto:** `C:\Users\Guilherme\Documents\VigiAuto`  
**Pacote:** `br.com.vigiauto`  
**Versão:** `versionCode 9`, `versionName 1.4.2` (relatório original)  
**Addendum 1.5.0:** `versionCode 10`, `versionName 1.5.0` — Sinesp v1 real como modo padrão  
**Addendum 1.5.1:** `versionCode 11`, `versionName 1.5.1` — login gov.br no app, fallback e Consulta Simples  
**Revisão 3 da 1.5.1:** cidade/estado, zoom 8x, NMT/OTTC silencioso e banco v3  
**Addendum 1.5.2:** `versionCode 12`, `versionName 1.5.2` — fallback de compatibilidade da câmera  
**Addendum 1.5.3:** `versionCode 13`, `versionName 1.5.3` — botão Remover em uma linha  
**Addendum 1.5.4:** `versionCode 14`, `versionName 1.5.4` — categoria NMT preservada, WebView corrigido e leitura otimizada  
**Addendum 1.7.0:** `versionCode 20`, `versionName 1.7.0` — captura quadrada real e recorte da placa; banco v5  
**Addendum 1.8.0:** `versionCode 21`, `versionName 1.8.0` — miniatura da própria foto, leitura mais rápida, várias placas por quadro e regra O/0

> **Addendum 1.8.0 (25/10/2026):** o OCR embarcado foi ajustado com o conjunto real
> do projeto e passou a usar o modelo anterior apenas como segunda opinião condicional.
> O `ocr.onnx` é o experimento `e2_lr1e4_e26` (3.035.990 bytes; SHA-256
> `5237B829CF6783D0CA633AC1A2C578B577FD8387FFA91FE825B0D482BD234ABC`), exportado
> somente com a cabeça de placa, opset 15, lote estático 1. O `ocr_fallback.onnx` é o
> CCT anterior do app (5.262.230 bytes; SHA-256
> `384BBBD2CEA3EF54761D3DF70822EF3A349EE1A112AEAFDDBE0E3BA06BC6E47B`). A segunda
> inferência só ocorre quando a principal não forma placa válida ou tem um caractere
> abaixo de **0,50**; se a principal é válida, o fallback só assume quando também é
> válido, tem confiança média ≥ **0,90** e difere em exatamente um caractere não
> equivalente (I/1, O/0, U/V, C/G, Q/O). Leituras válidas e confiantes continuam com uma
> única passada e compartilham o mesmo tensor de entrada.
>
> No benchmark offline, o pipeline Level 2 subiu de **19/27** para **23/27 (85,2%)**, o
> recorte anotado de **22/27** para **23/27 (85,2%)**, e o conjunto alvo de 24 recortes
> ficou em **23/24 (95,8%)**; validação **6/6** e teste **5/6**. O fallback foi usado em
> 2 casos do pipeline (`L2-002`, `L2-026`) e 1 recorte anotado (`L2-013`). O detector
> permanece **25/27 (92,6%)**. A suíte local tem **152 testes, 0 falhas** em 23 classes;
> `lintDebug` tem **0 erros** e 36 avisos. O APK é `apk/VigiAuto-1.8.0-debug.apk`
> (71.852.417 bytes; SHA-256
> `CA29BC34181C377580CE7E4ED03CE0210ABF7ED56320F9E90702FB21F6223560`). O banco
> continua **v5**; a migração 4→5 permanece válida (42 registros, `user_version 5` na
> cópia do backup). A instalação e a validação visual no aparelho seguem pendentes: não
> havia dispositivo ADB conectado e o app instalado permanece 1.6.4/19 com banco v4. Não
> execute UTP sem antes copiar banco e fotos.
>
> A miniatura continua sendo recortada da **própria fotografia quadrada** do veículo,
> com a caixa mapeada para o mesmo enquadramento e margem curta (10% horizontal, 14%
> vertical). A cadência padrão é **12 análises/s** (até 15) e leituras excepcionais
> (detector ≥ 0,88, OCR ≥ 0,92, menor caractere ≥ 0,60) são confirmadas no mesmo quadro.
> **Todas** as placas confirmadas no mesmo quadro são consultadas. A regra O/0 segue o
> padrão: Mercosul (`ABC1D23`) usa letra **O** nas posições 1, 2, 3 e 5 e dígito **0** nas
> 4, 6 e 7; a antiga (`ABC1234`) usa letra **O** nas posições 1, 2 e 3 e dígito **0** nas
> 4–7. A variante O/0 da quinta posição é priorizada e consultada também quando o serviço
> devolve sucesso sem dados. As resoluções de análise não mudaram.

> **Addendum 1.7.0 (23/10/2026):** a leitura confirmada passou a gerar, no mesmo
> quadro da confirmação, uma **fotografia quadrada real** do veículo e um **recorte
> colorido da placa** usando exatamente a caixa do YOLO. O recorte preserva os pixels
> da câmera e só é ampliado por interpolação; não há geração/reconstrução de caracteres,
> correção de perspectiva ou IA generativa. A tela de detalhes e o card JPEG exibem a
> miniatura com borda azul, e o toque abre o recorte ampliado. O banco Room v5 guarda
> caminho do recorte, texto do OCR, coordenadas normalizadas, confianças do detector e
> do OCR, proporção, qualidade e verificação recomendada. A migração 4→5 foi validada
> em cópia do backup: 42 registros preservados, `user_version 5` e defaults corretos.
> A suíte local tem **136 testes, 0 falhas** em 22 classes; `lintDebug` tem **0 erros**
> e 36 avisos. O APK é `apk/VigiAuto-1.7.0-debug.apk` (66.566.735 bytes; SHA-256
> `BC66DF980A33D37ABE8AA41B2BE4442FFB6F2B4809C07A5B42744F2A321B57F2`). Os modelos
> `detector.onnx`/`ocr.onnx` e as resoluções de análise **não** mudaram. A instalação
> e a validação visual no aparelho ficaram pendentes porque não havia dispositivo ADB
> conectado; o app instalado permanece 1.6.4/19 com banco v4. Não execute UTP sem antes
> copiar banco e fotos.

> **Limpeza (21/10/2026):** os APKs históricos citados neste relatório (1.3.0, 1.4.0,
> 1.4.1, 1.4.2 e 1.5.0) e os diretórios gerados `app/build`, `.gradle` e `.kotlin` foram
> removidos. Em `apk/` permanecem a **1.5.1** (referência do modelo), a **1.5.2**
> (correção de câmera validada), a **1.5.3** (ajuste visual do botão Remover) e a
> **1.5.4** (categoria NMT preservada, WebView corrigido e leitura otimizada). As
> referências antigas ficam como registro histórico; o próximo build recria `app/build`.

> **Addendum 1.5.2 (22/10/2026):** a câmera deixou de depender de uma única combinação
> de Preview + ImageAnalysis. O app tenta, em ordem: a lente escolhida com a resolução
> configurada e estabilização; a mesma combinação sem estabilização; 960×540; 640×480;
> e, por último, somente o preview. Também cai para a câmera traseira padrão se a lente
> secundária escolhida não aceitar a combinação. Isso corrige o erro
> `No supported surface combination is found for camera device - Id : 0` em aparelhos
> que rejeitam a configuração da 1.5.1. Os modelos `detector.onnx`/`ocr.onnx` e o
> comportamento de consulta **não** mudaram. O APK é
> `apk/VigiAuto-1.5.2-debug.apk` (66.555.733 bytes; SHA-256
> `CDD2902FF1B0C293D54191383643B0904ED3054B62BE79B97E36B4F6A247BA8C`). A suíte local
> foi reexecutada em 22/10/2026: **117 testes, 0 falhas** em 17 classes. O fallback foi
> validado no aparelho pelo usuário.

> **Addendum 1.5.3 (22/10/2026):** o botão **Remover** da seção
> **A · Fotografia do veículo**, na tela de detalhes da consulta, passou a ficar em uma
> única linha. O botão de adicionar/trocar foto usa o espaço restante da linha, sem
> empurrar o texto para duas linhas. O fallback de câmera da 1.5.2 e os modelos
> `detector.onnx`/`ocr.onnx` **não** mudaram. O APK é
> `apk/VigiAuto-1.5.3-debug.apk` (66.474.178 bytes; SHA-256
> `43B2148F537B9B973C6DF64B65F6C5655FBAF8DD4296D2B57387E28F9BBCACAB`). A suíte local
> foi reexecutada em 22/10/2026: **117 testes, 0 falhas** em 17 classes; `lintDebug` com
> **0 erros** e 29 avisos.

> **Addendum 1.5.4 (22/10/2026):** a **Categoria** voltou a desaparecer porque o worker
> NMT gravava `UNAVAILABLE`/`UNKNOWN` por cima de um resultado já concluído quando uma
> consulta posterior falhava (timeout, rede ou reCAPTCHA). A 1.5.4 adiciona
> `NmtCategoryPolicy`: uma falha não é uma nova informação sobre o veículo e passa a
> atualizar apenas `nmtCheckedAt`, `nmtRawStatus`, `nmtAttempts` e `nmtNextAttemptAt`; a
> categoria `REGULAR`, `IRREGULAR` ou `NOT_REGISTERED` permanece. Resultados concluídos
> novos continuam substituindo os antigos. A migração 2→3 também deixou de apagar
> `nmtStatus`/`nmtCheckedAt`, e a primeira abertura de cada versão reenfileira uma vez os
> registros que já estavam sem categoria. O ícone de atualizar da tela de detalhes agora
> reconsulta SINESP e NMT. A criação do WebView oculto também foi corrigida: dentro de
> `view.apply`, `handler` resolvia para `View.getHandler()` — nulo porque o WebView não
> está anexado a uma janela — em vez do handler da sessão, e a sessão era descartada com
> `webview_create`; a chamada passou a usar o `mainHandler` da sessão, fora do `apply`.
> No aparelho, duas placas reais que estavam sem categoria voltaram `REGULAR` (3,0 s) e
> `NOT_REGISTERED` (79,1 s, após um timeout de 75 s na primeira tentativa) e foram
> persistidas no Room. No caminho de leitura, a conversão NV21 da fotografia deixou de
> rodar em todo quadro e passou a rodar somente na confirmação; o CameraX entrega o buffer
> já girado (com fallback para a rotação no código se o aparelho recusar); detector/OCR
> reutilizam bitmaps, arrays e buffers diretos; caixas abaixo de
> 0,33 não gastam OCR e no máximo duas são lidas; leituras de alta confiança em movimento
> confirmam em duas observações. Os modelos `detector.onnx`/`ocr.onnx`, a resolução de
> entrada e a quantização **não** mudaram; o benchmark offline permanece 25/27 no detector
> (92,6%), 20/27 no pipeline normalizado (74,1%), 23/27 válidos (85,2%) e 26/27 válidos no
> recorte anotado (96,3%). O APK é `apk/VigiAuto-1.5.4-debug.apk` (66.632.769 bytes;
> SHA-256 `9850E4C766818EA891E323CF520AEC0C8650AE4E903090A11EC28A595B236E30`). A suíte
> local foi reexecutada em 22/10/2026: **122 testes, 0 falhas** em 18 classes; `lintDebug`
> com **0 erros** e 29 avisos. A recuperação da categoria foi validada no aparelho em
> 22/10/2026. O preview da câmera também foi observado funcionando; o banner falso de
> cancelamento da composição foi corrigido nesta build final, que não chegou a ser
> reinstalada porque o aparelho desconectou do USB antes. A latência de leitura de placas
> e a nova rotação de saída do CameraX ainda **não** foram medidas em campo. A suíte
> instrumentada não foi executada porque o HyperOS cancelou a instalação do APK de testes
> com `INSTALL_FAILED_USER_RESTRICTED`.

> **Addendum 1.5.1 — revisão 3 (21/10/2026):** a seção **B · Identificação do veículo**
> passou a mostrar **cidade** e **estado** retornados pela fonte. A câmera ganhou o botão
> **8x** ao lado do histórico (zoom digital, limitado ao máximo da lente, alternando com
> 1x). A **Categoria** é completada pela consulta pública **NMT/OTTC** da Prefeitura de São
> Paulo executada **100% em background**: não há tela, modal, popup, spinner, toast,
> botão, som, notificação ou texto de consulta. Um worker persistente enfileira a placa no
> cadastro, executa uma consulta por vez em WebView oculto, preenche o formulário oficial,
> aguarda o reCAPTCHA invisível e lê `#retornoMsg`/`#errorMsg`. O resultado é salvo no Room
> e aparece em **Categoria** como “Veículo de aplicativo - Situação regular”, “Veículo de
> aplicativo - Situação irregular” ou “Veículo particular”; falhas internas não alteram a
> categoria. O campo **Versão** foi removido da seção **B · Identificação do veículo**.
> O banco Room foi migrado de **v1 → v2 → v3**: a migração 2→3 adiciona `nmtRawStatus`,
> `nmtAttempts` e `nmtNextAttemptAt`; desde a 1.5.4 ela preserva `nmtStatus`/`nmtCheckedAt`
> e apenas reinicia a fila, mantendo histórico, cidade, estado, fotos e categorias. A suíte local passou para
> **117 testes, 0 falhas** em 17 classes; `lintDebug` tem **0 erros** e 29 avisos. O APK
> final é `apk/VigiAuto-1.5.1-debug.apk` (66.474.178 bytes; SHA-256
> `C53255D1155027EBE6693759AADA40C77F762C5C953A5BB1E2776D31C1E5857D`). A consulta
> NMT depende do WebView/reCAPTCHA do site público e foi validada em aparelho na 1.5.4.
> Como o projeto não tem backend, esta revisão usa WebView oculto no processo do app; a
> especificação recomenda um endpoint server-side, e a camada do worker está isolada para
> essa migração sem tocar na UI.

> **Addendum 1.5.1 (21/10/2026):** o app passou a oferecer **Login gov.br** dentro de
> Configurações → Sinesp, com WebView no site oficial, troca do `code` de retorno pelo
> token em `/auth/token` e validação em `/me`. O token continua cifrado no Android
> Keystore e fora do APK. Sem token, as leituras usam a **Consulta Simples**
> automaticamente; o antigo modo **Real** foi renomeado para **Consulta Simples** na
> interface, e a paleta verde foi substituída por azul escuro. No Sinesp e na Consulta
> Simples, a seção **C · Situação e restrições** passou a exibir em azul **“Veículo sem
> registro ou alerta de roubo/furto”** no status e na Descrição quando a consulta termina
> sem roubo/furto; alertas não reconhecidos e históricos indisponíveis continuam
> inconclusivos. A suíte local passou para **109 testes, 0 falhas** em 16 classes; o APK
> final é `apk/VigiAuto-1.5.1-debug.apk` (66.501.406 bytes, SHA-256
> `C10EF61D69B7C7BDD4C962C04EEFCC93EB938A0B5E0AA89ABA712E91E6C751F7`). As métricas de
> detecção continuam sendo as medidas na 1.4.1/1.4.2.

> **Addendum 1.5.0 (20/10/2026):** o adapter Sinesp reservado foi substituído pelo
> cliente real `SinespV1Source`, que fala o protocolo v1 do SINESP Cidadão com o token
> pessoal do gov.br. O Sinesp passa a ser o modo padrão; o token continua fora do APK,
> cifrado no Android Keystore, e precisa ser colado uma vez em Configurações → Sinesp.
> A suíte local passou para **96 testes, 0 falhas** em 13 classes; o APK final é
> `apk/VigiAuto-1.5.0-debug.apk` (66.210.394 bytes, SHA-256
> `98E0D6BDD4138341D5C670E193815985B478EFFE6F539C3F95BBC0D95DCEA130`). As seções 11 a
> 16 foram atualizadas para refletir essa mudança; as métricas de detecção continuam
> sendo as medidas na 1.4.1/1.4.2.

## 1. Resumo executivo

A V2 substitui o pipeline **OCR-first** por um pipeline **detector-primeiro**:

```
câmera → YOLOv9 (ONNX) → recorte → CCT (ONNX) → validação → tracking
       → confirmação → deduplicação → consulta → fotografia
```

O objetivo é impedir que textos comuns do quadro inteiro (letreiros, fachadas,
adesivos) sejam interpretados como placas. O detector localiza regiões com evidência
visual de placa; o OCR especializado lê apenas esses recortes. O ML Kit foi mantido como
segunda opinião no recorte e como contingência.

O que está **comprovado** nesta máquina:

- compilação do APK debug e do APK de testes;
- **69 testes unitários, 0 falhas** em 10 classes;
- `lintDebug` com **0 erros** e 29 avisos;
- inferência offline dos modelos ONNX em quatro imagens de fumaça, com as placas
  esperadas;
- APK final copiado para `apk/VigiAuto-1.4.2-debug.apk`, com tamanho e SHA-256
  registrados.

O que **não está comprovado**:

- execução em aparelho físico (não havia `adb devices` nem emulador);
- acurácia, recall, falsos positivos, latência, CPU e memória com o dataset real;
- leitura em movimento a 25 km/h;
- integração Sinesp autorizada;
- licença/procedência dos pesos ONNX para uso comercial.

Este relatório separa explicitamente esses dois blocos. Nenhuma métrica de campo é
apresentada como se tivesse sido medida.

## 2. O que mudou em relação à 1.3.0

| Área | Antes (1.3.0) | Depois (1.4.2) |
|---|---|---|
| Localização da placa | ML Kit lia o quadro inteiro e o `CandidateExtractor` procurava padrões de 7–10 caracteres | YOLOv9 localiza a caixa; o OCR lê somente o recorte |
| OCR principal | ML Kit Text Recognition | CCT ONNX (10 posições, alfabeto `0-9A-Z_`) |
| Segunda opinião | Recorte ampliado com o mesmo ML Kit | ML Kit apenas no recorte, quando o CCT falha ou tem baixa confiança |
| Confirmação | Contagem de strings iguais | Trilha por caixa (IoU/proximidade), 2 leituras (3 em moto), 75% de concordância, cooldown de 12 s |
| Falsos positivos | Texto de fachada/letreiro podia virar candidato | Sem caixa do detector, o texto não chega ao OCR |
| Contingência | Não havia troca de motor | Inicialização falhou → ML Kit; 5 exceções consecutivas → ML Kit persistido |
| Configuração | Não havia escolha de motor | Switch “Detector visual de placas (YOLO + CCT)” |
| Consulta por placa | Modo Completa (DadosAPI) | DadosAPI removida; modo Sinesp reservado para fonte autorizada, sem cliente reverso |
| Tamanho do APK | 61,90 MiB | 63,20 MiB (ONNX Runtime + cerca de 13,0 MB de modelos) |

O pipeline ML Kit anterior foi preservado em `PlateAnalyzer.kt` e continua disponível
quando o switch é desligado ou quando o ONNX falha.

## 3. Arquitetura do pipeline

```
CameraX ImageAnalysis (YUV)
  │
  ├─ FrameRingBuffer: NV21 + nitidez (para a foto)
  │
  └─ OnnxPlateAnalyzer
       ├─ converte o quadro para Bitmap na orientação do sensor
       ├─ OnnxPlateEngine.detect(bitmap)
       │    ├─ letterbox 384×384, fundo cinza 114
       │    ├─ YOLOv9 → caixas [x1,y1,x2,y2,classe,score]
       │    ├─ recorte de cada caixa
       │    ├─ CCT 128×64 RGB uint8 → 10 posições
       │    └─ PlateNormalizer.normalize
       ├─ refine(): ML Kit no recorte, se necessário
       ├─ isAccepted(): padrão, confiança, proporção
       ├─ dedupe(): mesma placa + IoU ≥ 0,50
       ├─ PlateTrackTracker.update()
       └─ onConfirmed(PlateCandidate)
            └─ MainViewModel → VehicleRepository
                 ├─ deduplicação por normalizedPlate (Room)
                 ├─ consulta (Consulta Simples / Sinesp / HTTP / DEMO)
                 └─ foto + histórico + pop-up
```

O processamento ocorre em um executor dedicado (`vigiauto-alpr`), com flag de
ocupação e descarte de quadros atrasados. A thread principal não executa inferência. A
conversão NV21 usada na foto roda em paralelo com a inferência, depois que o bitmap já
foi criado; isso evita acesso concorrente ao mesmo `ImageProxy` e não soma a conversão
antes do detector.

## 4. Componentes e responsabilidades

| Arquivo | Responsabilidade |
|---|---|
| `alpr/OnnxPlateEngine.kt` | Sessões ONNX, letterbox, YOLOv9, recorte, CCT, decodificação |
| `camera/OnnxPlateAnalyzer.kt` | Pipeline detector-primeiro, segunda opinião, tracking, fallback |
| `camera/PlateAnalyzer.kt` | Pipeline ML Kit anterior, preservado como contingência |
| `camera/FrameAnalyzer.kt` | Interface comum aos dois analisadores |
| `camera/BitmapUtils.kt` | Conversão YUV → Bitmap com rotação do sensor |
| `domain/plate/PlateDetection.kt` | Caixa, confiança do detector/OCR e score combinado |
| `domain/plate/PlateTrackTracker.kt` | Associação por caixa, consenso, cooldown |
| `domain/plate/PlateNormalizer.kt` | Normalização e equivalência visual |
| `data/prefs/AppSettings.kt` | `DetectionEngine.ONNX` como padrão |
| `ui/main/MainScreen.kt` | Escolha do motor, criação do analisador, fallback |
| `ui/settings/SettingsScreen.kt` | Switch do detector visual |
| `tools/verify_onnx.py` | Verificação offline dos modelos e coordenadas |

## 5. Modelos embarcados

| Arquivo | Tamanho | SHA-256 | Entrada | Saída |
|---|---:|---|---|---|
| `detector.onnx` | 7.771.218 bytes | `888397B96D761C89DB40BC9C305838E8652660F5E282C2CADEBBE8D2951A77A8` | `float32[1,3,384,384]` | `float32[batch,7]` |
| `ocr.onnx` | 3.035.990 bytes | `5237B829CF6783D0CA633AC1A2C578B577FD8387FFA91FE825B0D482BD234ABC` | `uint8[1,64,128,3]` | `float32[batch,10,37]` |
| `ocr_fallback.onnx` | 5.262.230 bytes | `384BBBD2CEA3EF54761D3DF70822EF3A349EE1A112AEAFDDBE0E3BA06BC6E47B` | `uint8[1,64,128,3]` | `float32[batch,10,37]` |

- Detector: `yolo-v9-t-384-license-plate-end2end`, inalterado.
- OCR principal: ajuste `e2_lr1e4_e26` (somente cabeça de placa, opset 15, lote 1).
- OCR fallback: `cct-s-v2-global-model` anterior do app. Não é o checkpoint
  `cct_xs_v2_global.keras`.
- Alfabeto: `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_`.
- Runtime: `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`.
- Modelos em `app/src/main/assets/models/`, com `noCompress += "onnx"`.
- ABI do APK: `arm64-v8a`.

**Licença:** o código de integração segue o `fast-alpr` (MIT), mas os pesos vieram do
projeto de referência e sua licença/procedência não foi documentada. A distribuição
comercial depende de confirmação.

## 6. Regras de decisão

### 6.1 Detector

- score mínimo para entrar no OCR: **0,30**;
- score mínimo para aceitação: `0,38 + (sensibilidade − 0,5) × 0,25`, limitado a
  **0,38–0,49**; no padrão 0,70 → **0,43**;
- proporção da caixa aceita: **0,8–6,0**;
- caixas menores que 8×4 px são descartadas.

### 6.2 OCR e validação

- confiança mínima de OCR: `0,48 + (sensibilidade − 0,5) × 0,35`, limitado a
  **0,48–0,64**; no padrão 0,70 → **0,55**;
- score combinado mínimo: `sensibilidade × 0,78`; no padrão 0,70 → **0,546**;
- `score = detector × 0,60 + OCR × 0,40`;
- a placa precisa passar por `PlatePattern.isPlate` (antiga `LLLDDDD` ou Mercosul
  `LLLDLDD`);
- o OCR principal é o `ocr.onnx` (E2). O `ocr_fallback.onnx` é consultado quando o
  texto principal não forma placa válida ou quando o menor caractere fica abaixo de
  **0,50**. Texto vazio não dispara a segunda inferência;
- se a principal já é válida, o fallback só a substitui quando ele também é válido, tem
  confiança média ≥ **0,90** e difere em exatamente um caractere **não** equivalente
  (I/1, O/0, U/V, C/G, Q/O). Isso preserva correções do E2 e recupera apenas trocas
  grosseiras de um caractere;
- depois do fallback, a leitura é preservada se `normalized != null`, OCR ≥ 0,55 e menor
  confiança por caractere ≥ 0,30; caso contrário, o ML Kit opina apenas no recorte;
- a segunda opinião não pode trocar a placa por uma leitura visualmente incompatível.

### 6.3 Tracking

- associação por IoU ≥ **0,15**; se falhar, aceita caixa próxima com razão de área
  ≥ 0,45, deslocamento horizontal ≤ 80% da largura e vertical ≤ 70% da altura;
- trilha expira após **1,5 s** sem detecção;
- mínimo de **2 leituras** (3 em modo moto);
- concordância mínima de **75%** na trilha;
- cooldown de **12 s** por placa confirmada;
- se a placa dominante muda de forma não equivalente na mesma caixa, a contagem reinicia;
- duas caixas distantes nunca são somadas.

### 6.4 Deduplicação, consulta e foto

- Room com índice `UNIQUE` em `normalizedPlate`; inserção `IGNORE` e atualização
  transacional de última visualização/ocorrências;
- no máximo 2 consultas em paralelo, deduplicadas por placa;
- retentativa automática quando a rede volta;
- uma foto por registro; a original nunca é sobrescrita;
- foto escolhida pela nitidez (variância do Laplaciano), não pela ordem de chegada.

## 7. Fallback e configuração

1. **Inicialização:** se `OnnxPlateEngine` lançar exceção (ABI, memória, asset ausente),
   `MainScreen` mostra “Detector visual indisponível; usando ML Kit” e usa
   `PlateAnalyzer`.
2. **Falhas em execução:** após **5 exceções consecutivas** de quadro,
   `OnnxPlateAnalyzer` chama `fallbackToMlKit()`, que grava
   `DetectionEngine.ML_KIT` no DataStore.
3. **Escolha manual:** o switch em Configurações alterna entre ONNX e ML Kit a qualquer
   momento; a troca recria o analisador e fecha o anterior.

O fallback não apaga histórico, fotos nem configurações de consulta.

## 8. Evidências executadas

### 8.1 Build e testes

Comando:

```bat
$env:JAVA_HOME="C:\Users\Guilherme\AppData\Local\Android\jdk-17"
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
.\gradlew.bat assembleDebugAndroidTest
```

Resultados:

| Verificação | Resultado |
|---|---|
| `testDebugUnitTest` | **69 testes, 0 falhas, 0 erros, 0 ignorados** em 10 classes |
| `assembleDebug` | APK gerado |
| `assembleDebugAndroidTest` | APK de testes gerado (3.593.542 bytes) |
| `lintDebug` | **0 erros**, 29 avisos |
| Ambiente | Windows, JDK 17.0.20.1, Gradle 8.9 |

Distribuição dos testes:

| Classe | Testes |
|---|---:|
| `PlateNormalizerTest` | 15 |
| `VehicleResponseMapperTest` | 15 |
| `CandidateExtractorTest` | 7 |
| `ConfirmationTrackerTest` | 6 |
| `MotionConsensusTrackerTest` | 6 |
| `PlateTrackTrackerTest` | 6 |
| `RemoteApiConfigTest` | 5 |
| `OcrCandidateMergerTest` | 4 |
| `SinespConsultaPlacaServiceTest` | 3 |
| `FrameQualityTest` | 2 |

### 8.2 APK

| Campo | Valor |
|---|---|
| Arquivo | `apk/VigiAuto-1.4.2-debug.apk` |
| Tamanho | 66.194.010 bytes (63,13 MiB) |
| SHA-256 | `75368220938E4C2E4928FEF350010683EB7B4739525C4E500F8B16A033889D88` |
| ABI | `arm64-v8a` |
| Assinatura | depuração |
| Versão | `versionCode 9`, `versionName 1.4.2` |

O hash foi recalculado após a cópia para `apk/` e confere com o artefato de
`app/build/outputs/apk/debug/app-debug.apk`.

A versão 1.4.1 corrigiu um bug crítico da 1.4.0: o detector ONNX recebia RGB
intercalado, mas o tensor é NCHW (três planos). O modelo rodava, porém não detectava
nenhuma placa no aparelho. A 1.4.2 mantém essa correção e remove a integração DadosAPI;
o modo Sinesp passa a ser um adapter para fonte autorizada, sem o cliente reverso do
aplicativo oficial. A 1.4.0 continua obsoleta e não deve ser usada.

A reexecução de `connectedDebugAndroidTest` na 1.4.2 foi bloqueada pelo HyperOS com
`INSTALL_FAILED_USER_RESTRICTED`. Os 9 testes instrumentados passaram na 1.4.1; para
repetir na 1.4.2, é preciso habilitar **Instalar via USB** e aprovar o prompt no aparelho.

Verificações adicionais no artefato final:

- `aapt dump badging`: pacote `br.com.vigiauto`, `versionCode 9`,
  `versionName 1.4.2`, `minSdk 26`, `targetSdk 35`, `native-code: arm64-v8a`;
- `apksigner verify`: assinatura **v2 válida**, certificado `CN=Android Debug`
  (assinatura de depuração).

### 8.3 Fumaça offline dos modelos ONNX

Script reproduzível: `tools/verify_onnx.py`. Ele aplica o mesmo letterbox, recorte e
pré-processamento do app, mas roda em Python (não no Android).

| Imagem | Placa esperada | Placa lida | Detector | OCR | Menor confiança |
|---|---|---|---:|---:|---:|
| `app/src/androidTest/assets/alpr/test_plate.png` | ABC1D23 | **ABC1D23** | 0,945 | 1,000 | 1,000 |
| `detector-placas/saidas/placa_sintetica.png` | ABC1D23 | **ABC1D23** | 0,845 | 1,000 | 1,000 |
| `detector-placas/saidas/placa_usuario.png` | NTO7F45 | **NTO7F45** | 0,813 | 0,998 | 0,985 |
| `detector-placas/saidas/demo_sinesp_frame.png` | NTO7F45 | **NTO7F45** | 0,819 | 0,999 | 0,993 |

Essas quatro imagens são de fumaça/sintéticas ou de demonstração. Elas **não** medem
acurácia em campo e não substituem o dataset real.

### 8.4 Testes instrumentados no POCO X7 Pro

Em 20/10/2026, com o POCO X7 Pro (`2412DPC0AG`, Android 16, arm64-v8a) e a instalação
via USB liberada, o `connectedDebugAndroidTest` executou **9 testes, 0 falhas**:

- `OnnxPlateEngineTest` — passou; `test_plate.png` → `ABC1D23`;
- `OnnxDatasetBenchmarkTest` — passou com o dataset real;
- `OcrDatasetTest` — passou com 9/10 leituras corretas (90%);
- `VehicleRecordDaoTest` — 6/6.

### 8.5 Dataset real — resultado no aparelho

As 9 imagens estão em `app/src/androidTest/assets/dataset/` e as anotações de
`SUK0E51`/`UFG0B86` foram confirmadas pelo usuário. O benchmark instrumentado
reproduziu o resultado local:

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

Latência e memória no aparelho:

| Passagem | Latência média | CPU médio | PSS antes → depois |
|---|---:|---:|---:|
| Primeira (aquecimento) | 153,3 ms | 149,2 ms | 167.763 KB → 209.956 KB |
| Segunda (aquecida) | **101,1 ms** | **99,4 ms** | **170.543 KB → 208.504 KB** |

A única divergência é `STE7129` (placa antiga de moto) lida como `STE7I29` (Mercosul
válido): a ambiguidade `I`/`1` na quinta posição. `PlateNormalizer.alternatives` pode
tentar a hipótese antiga após uma consulta sem resultado, mas o benchmark exato conta a
divergência.

O ML Kit, no mesmo dataset, acertou 9/10 leituras (90%): leu `SUK0E51`, `STE7129` e as
demais corretamente, mas leu `UFG6B86` onde a anotação confirmada é `UFG0B86`. O
`OcrDatasetTest` conta acertos, mas não mede precisão, falsos positivos, latência, CPU ou
memória; portanto, os dois motores não podem ser comparados por um único número e nenhum
vencedor foi declarado.

Ainda não executado: meta de 25 km/h, teste de câmera em campo e medição de
precisão/falsos positivos do ML Kit.

### 8.6 Correção crítica: layout NCHW do detector

A 1.4.0 não detectava placas no aparelho. O `OnnxPlateEngine` preenchia o tensor do
detector em RGB intercalado (NHWC), mas a entrada `[1, 3, 384, 384]` é NCHW (três
planos). O modelo executava, mas recebia a imagem embaralhada e devolvia score ≈ 0,001
para a placa; o resultado era uma lista vazia.

O script Python já usava `transpose(2, 0, 1)`, por isso a fumaça offline passava. Um
diagnóstico no aparelho salvou a entrada 384×384 (visualmente correta) e mostrou a saída
baixa do detector. A 1.4.1 preenche primeiro o plano R, depois G e depois B; com isso o
`OnnxPlateEngineTest` passou e o dataset voltou a ser detectado. O teste instrumentado é
a regressão que protege essa correção.

### 8.7 Ajuste de OCR da 1.8.0

O OCR foi ajustado fora do app com **50 recortes de 44 grupos** (38 treino, 6 validação
e 6 teste), separados por cena/placa para não vazar a mesma placa entre splits. Os
rótulos foram conferidos à mão; o nome do arquivo foi usado apenas como pista. A
anotação `L2-015` foi corrigida para **STH5H33** e o dataset foi reexportado.

Quatro experimentos foram comparados. O escolhido foi `e2_lr1e4_e26`: learning rate
`1e-4`, 26 épocas, paciência 8, lote 8, seed 42, EMA ativo, label smoothing 0,01 e
weight decay 0,01. A exportação ONNX usa somente a cabeça de placa, opset 15 e lote
estático 1. O experimento com seed 7 regrediu para 21/24 no conjunto alvo e foi
descartado.

| Conjunto | Modelo anterior | E2 sozinho | E2 + fallback (1.8.0) |
|---|---:|---:|---:|
| Alvo, 24 recortes reais | 19/24 (79,2%) | 22/24 (91,7%) | **23/24 (95,8%)** |
| Pipeline Level 2, 27 imagens | 19/27 (70,4%) | 21/27 (77,8%) | **23/27 (85,2%)** |
| Recorte anotado, 27 imagens | 22/27 (81,5%) | 22/27 (81,5%) | **23/27 (85,2%)** |
| Validação interna | 6/6 | 5/6 | **6/6** |
| Teste interno | 3/6 | 5/6 | **5/6** |

O fallback corrigiu `L2-002` e `L2-026` no pipeline e `L2-013` no recorte anotado. A
regra de confiança baixa recuperou `NIB7212` (o E2 lia `NIB2212`) sem desfazer
correções do E2 como `STE7129` e `SWR3C57`. As falhas restantes do pipeline são
`L2-013` (`SGL5153`), `L2-017` (`ZZE6390`) e as placas `L2-022`/`L2-025`, que o
detector não encontrou; o recorte anotado ainda erra `L2-002`, `L2-017`, `L2-023` e
`L2-025`.

O benchmark oficial (`tools/level2_benchmark.py`) passou a carregar os dois modelos e
a reproduzir a política do app; `--no-ocr-fallback` mantém a comparação com o E2
sozinho. As duas sessões compartilham o mesmo tensor de entrada e o fallback não é
executado em leituras válidas e confiantes. O custo permanente é uma sessão extra e
5.262.230 bytes no APK; não há segunda inferência por quadro no caminho normal.

## 9. Benchmark do dataset real (metodologia)

O teste `OnnxDatasetBenchmarkTest` está implementado, as imagens estão em
`app/src/androidTest/assets/dataset/` e a execução no POCO X7 Pro foi concluída. O
resultado está no item 8.5; o CSV `alpr-benchmark.csv` fica na pasta externa do app.

Por imagem, ele registra:

- arquivo, placas esperadas e placas detectadas;
- acertos, recall, precisão e falsos positivos;
- latência de parede (`elapsedRealtimeNanos`);
- tempo de CPU da thread (`Debug.threadCpuTimeNanos`);
- PSS aproximado antes/depois (`Debug.MemoryInfo`);
- CSV em `alpr-benchmark.csv` na pasta externa do app.

Cuidados de interpretação:

- o teste aquece as sessões com `test_plate.png` antes de medir;
- a latência medida é a de `engine.detect(bitmap)` em uma imagem estática, **não** a
  latência completa da câmera;
- o PSS é do processo inteiro e inclui o runtime Android, não só os modelos;
- o dataset tem 9 fotos e 10 leituras; o intervalo de confiança é largo;
- a comparação ONNX × ML Kit deve usar as mesmas imagens, a mesma resolução e o mesmo
  aparelho.

Critério mínimo proposto para o dataset (a confirmar com o usuário): recall ≥ 80%,
precisão ≥ 90% e nenhuma consulta disparada por placa incorreta. Esses números são uma
**proposta de aceitação**. No conjunto de 9 fotos, o ONNX atingiu recall 0,900 e precisão
0,900 tanto em Python quanto no POCO, mas a amostra é pequena; o resultado não é base para
generalização nem prova de desempenho em movimento.

## 10. Meta de 25 km/h

A meta é um requisito de teste controlado, não uma promessa de leitura universal.

Desenho de teste seguro:

1. área privada/fechada, piso seco, luz do dia, placas limpas e sem obstrução;
2. aparelho fixo em suporte rígido, câmera traseira apontada para a via de passagem;
3. condutor e observador separados; **ninguém manuseia o celular enquanto dirige**;
4. passagens a 10, 20 e 25 km/h, com velocidade medida por GPS ou instrumento do
   veículo;
5. no mínimo 10 passagens por velocidade, variando distância e ângulo dentro de uma
   faixa realista;
6. registrar placa correta, placa errada, tempo até a confirmação e falsos positivos.

Métricas a reportar: taxa de confirmação correta, tempo até a confirmação, taxa de
placas erradas e falsos positivos por minuto. O critério de aprovação deve ser acordado
antes do teste; uma proposta é **≥ 8/10 confirmações corretas em até 3 s a 25 km/h, sem
consulta de placa errada**. A meta **não foi medida**.

Para referência, a latência de 101 ms por imagem estática no POCO corresponde a cerca de
**0,70 m** percorridos a 25 km/h. É apenas uma conta de janela, não uma medição de leitura
em movimento; blur, exposição, distância e tracking ainda precisam ser testados.

## 11. Sinesp v1 (implementado na 1.5.x)

Situação: **cliente real implementado; a consulta depende do token pessoal do gov.br do
usuário e da disponibilidade do serviço**.

- O modo **Sinesp v1** passou a ser o padrão da 1.5.x e faz `GET
  https://cidadao2.sinesp.gov.br/api/v1/vehicles/{placa}`.
- Envia `Authorization` com o token cru do gov.br, a assinatura `AppEnsurer`, `UniqueId` e
  os cabeçalhos `Geolocation`, `GeolocationTimestamp` e `GeolocationAccuracy`.
- Na 1.5.1, o token pode ser obtido pelo botão **Login gov.br** em Configurações →
  Sinesp: um WebView carrega o site oficial do gov.br, captura o `code` de retorno e o
  troca pelo token em `POST /auth/token`, validando em `GET /me`. O app não vê a senha e
  não injeta JavaScript. O token continua cifrado no Android Keystore; **nenhum token é
  embutido no APK**.
- Quem preferir pode colar um token já obtido; salvar um token novo ativa o modo Sinesp.
- Sem token, as leituras usam a **Consulta Simples** automaticamente e a tela principal
  mostra “Sinesp sem token • Consulta Simples”. O botão **Verificar configuração**
  continua testando o Sinesp e informa que falta o token; o teste não gasta consulta.
- Instalações antigas na Consulta Simples são migradas uma única vez para o Sinesp; a
  Consulta Simples (API Consulta Placa), o HTTP manual e o DEMO continuam disponíveis.
- O cliente foi portado do provedor Python `detector-placas` já validado pelo usuário e
  usa a rotina HMAC do `AppEnsurer` presente no protocolo de referência. O endpoint não é
  uma API pública documentada para terceiros; o uso deve ser pontual, para um veículo que
  o usuário está vendo, sem consulta em massa, respeitando a LGPD e os termos do serviço.
- O app abre o login no site oficial do gov.br em WebView e troca o `code` de retorno pelo
  token; não emite credencial oficial e não tenta contornar autenticação, limites ou
  termos de uso.
- Erros tratados: 401/403 → token recusado/expirado; 404 → veículo não encontrado; 429 →
  limite de consultas; 400 com menção a geolocalização → localização inválida; 5xx,
  timeout e falha de rede → erro de conexão.
- O teste de configuração não gasta consulta: ele valida o token e a localização
  localmente; a validação real do token acontece na primeira placa consultada.

O mapeamento da resposta distingue:

| Estado | Significado |
|---|---|
| Restrição confirmada | `alert` com roubo/furto ou restrição positiva |
| Sem restrição identificada | `alert` vazio e resposta sem indicativo positivo |
| Inconclusivo | Resposta ambígua ou tipo de alerta desconhecido |
| Indisponível | Sem token, erro, limite ou cobertura ausente |

Alerta vermelho só para restrição confirmada. Ausência de alerta não prova regularidade.

## 12. Privacidade e segurança

- Detector e OCR rodam localmente; nenhum quadro, recorte ou foto é enviado à rede.
- Apenas a placa é enviada ao serviço de consulta. No Sinesp v1, também são enviados o
  `UniqueId` e a localização configurada (ponto neutro por padrão).
- URL/token da **Consulta Simples** vêm do remote config em runtime e ficam só em memória.
- O token do modo HTTP manual é digitado pelo usuário e cifrado com Android Keystore.
- O token do Sinesp v1 é obtido pelo login gov.br no WebView (ou colado pelo usuário),
  cifrado com Android Keystore e nunca é embutido no APK nem gravado em logs. O app não
  lê a senha do gov.br nem injeta JavaScript na página.
- O protocolo v1 usa uma chave HMAC interna (`AppEnsurer`) herdada do aplicativo oficial;
  ela não é uma credencial de usuário e, sozinha, não permite consulta. O token pessoal do
  gov.br continua obrigatório.
- O token legado da DadosAPI é apagado do DataStore na inicialização; nenhum segredo da
  integração removida permanece armazenado.
- Sem token do Sinesp, nenhuma placa é enviada ao SINESP; as leituras usam a Consulta
  Simples e a tela informa o fallback.
- Placas não são gravadas em logs técnicos.
- Room e fotos ficam no armazenamento privado; backup desativado.
- Exportação JSON é ação manual.
- O APK de depuração é assinado com chave de debug e não deve ser distribuído como
  produção.

## 13. Riscos e limitações

| Risco | Impacto | Mitigação/status |
|---|---|---|
| Pesos ONNX sem licença confirmada | Distribuição comercial | Confirmar antes de publicar |
| Apenas `arm64-v8a` | Não instala em aparelhos antigos/x86 | Ajustar `abiFilters` ou usar App Bundle |
| Bug NCHW na 1.4.0 | Nenhuma placa detectada no aparelho | Corrigido na 1.4.1 e mantido na 1.4.2; `OnnxPlateEngineTest` protege a regressão |
| Latência de 101 ms em imagem estática | Não prova leitura em movimento | Executar teste controlado a 10/20/25 km/h |
| ML Kit sem métrica de precisão | Comparação ONNX × ML Kit incompleta | Instrumentar precisão/falsos positivos ou limitar a conclusão |
| 27 imagens no Level 2 | Amostra pequena; teste com 6 | Coletar mais imagens e anotações |
| Modelo pré-treinado | Pode falhar em motos/ângulos/iluminação | Calibrar e validar em campo |
| Motos em duas linhas no ONNX | `L2-013` ainda falha no recorte do detector; `L2-022`/`L2-025` não são detectadas | Melhorar detector/enquadramento; validar em campo |
| Equivalência Mercosul ↔ antiga não implementada | Risco de registro duplicado | Implementar chave canônica |
| Sinesp depende de token pessoal e de protocolo não público | Token expira e o endpoint pode mudar sem aviso | Login gov.br no app (WebView) ou token colado; renovação pelo usuário; erros explícitos e uso pontual; validar no aparelho |
| 25 km/h não medido | Meta não comprovada | Executar teste controlado |
| Foto do quadro completo em cena com várias placas | Cada placa usa sua própria caixa no mesmo quadro (1.7.0) | Recorte por veículo; qualidade limitada se houver dúvida |
| Debug APK | Não é produção | Gerar release assinado |

## 14. Próximos passos

1. Executar o teste controlado de 25 km/h e registrar a velocidade efetivamente medida.
2. Medir precisão, falsos positivos, latência, CPU, memória e tamanho do ML Kit no mesmo
   aparelho; sem isso, não declarar vencedor.
3. Instalar a 1.8.0 no POCO X7 Pro após copiar banco e fotos; validar a migração 4→5,
   a miniatura da foto real, múltiplas placas no quadro, a regra O/0 e as placas de erro.
4. Ampliar o dataset e repetir o benchmark; 27 imagens e 6 de teste não sustentam
   generalização.
5. Implementar equivalência canônica entre placa Mercosul e antiga para evitar registro
   duplicado.
6. Validar no aparelho o **Login gov.br** em WebView, a consulta real e o fallback sem
   token para a Consulta Simples, conferindo o mapeamento de restrições e a renovação do
   token quando ele expirar.
7. Confirmar a licença/procedência dos pesos ONNX antes de distribuição comercial.
8. Avaliar ABI splits/App Bundle para reduzir o tamanho e ampliar compatibilidade.
9. Gerar release assinado, com `versionCode` próprio e política de privacidade.

## 15. Reprodução

```bat
cd "C:\Users\Guilherme\Documents\VigiAuto"
$env:JAVA_HOME="C:\Users\Guilherme\AppData\Local\Android\jdk-17"

:: testes, build e lint
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug

:: APK de testes instrumentados
.\gradlew.bat assembleDebugAndroidTest

:: instalar no aparelho conectado
adb install -r app\build\outputs\apk\debug\app-debug.apk

:: testes instrumentados
.\gradlew.bat connectedDebugAndroidTest

:: verificação offline dos modelos (Python com onnxruntime, numpy e Pillow)
python tools\verify_onnx.py --expect ABC1D23 app\src\androidTest\assets\alpr\test_plate.png

:: benchmark offline com o OCR principal + fallback da 1.8.0
python tools\level2_benchmark.py --json-out dataset\level2\benchmark.json --csv-out dataset\level2\benchmark.csv

:: hash do APK final
Get-FileHash apk\VigiAuto-1.8.0-debug.apk -Algorithm SHA256
```

## 16. Conclusão

A V2 entrega a mudança estrutural pedida: o OCR deixou de varrer o quadro inteiro e passou
a receber apenas recortes com evidência visual de placa. A suíte local tem 152 testes sem
falhas na 1.8.0, incluindo a política de fallback do OCR e a preservação de categoria; a suíte
instrumentada compila 14 testes, e o POCO X7 Pro executou 9 testes sem falhas na 1.4.1,
incluindo a fumaça do ONNX e os 6 testes de Room. A reexecução nas versões seguintes foi
bloqueada pelo HyperOS com `INSTALL_FAILED_USER_RESTRICTED`, uma restrição de instalação do
aparelho, não uma falha dos testes. O fallback de câmera da 1.5.2 foi validado no aparelho
pelo usuário; na 1.5.4 o app foi instalado e a consulta NMT foi validada, com as categorias
`REGULAR` e `NOT_REGISTERED` persistidas. O preview da câmera foi observado funcionando; a
build final, com o ajuste do banner falso de cancelamento, não chegou a ser reinstalada
porque o aparelho desconectou do USB. A latência de leitura de placas e a nova rotação de
saída do CameraX ainda não foram medidas em campo, e a suíte instrumentada não rodou porque
o HyperOS cancelou a instalação do APK de testes.

A 1.4.0 tinha um bug crítico de layout NCHW que impedia qualquer detecção no aparelho.
A 1.4.1 corrigiu o tensor e foi validada no dataset real: 9 acertos exatos em 10 leituras
(recall e precisão de 0,900), latência média de 101,1 ms e CPU médio de 99,4 ms na
passagem aquecida, com PSS de 170.543 KB → 208.504 KB. A única divergência foi `STE7129`
lida como `STE7I29`, a ambiguidade `I`/`1`. A 1.5.0 mantém a correção e substitui o antigo
adapter Sinesp pelo cliente v1 real (`SinespV1Source`), com o token pessoal do gov.br
cifrado no aparelho e o Sinesp como modo padrão.

O ML Kit acertou 9/10 leituras (90%) no mesmo conjunto, mas o teste não mede precisão nem
falsos positivos; não há base para declarar um vencedor. A afirmação “reduz falsos
positivos em fachadas e letreiros” continua uma consequência arquitetural plausível, mas
**não foi medida em campo**. A meta de 25 km/h, a comparação completa ONNX × ML Kit, a
validação de uma consulta Sinesp real no aparelho e a licença comercial permanecem
pendências externas.

## Apêndice A — treino adicional de 26/10/2026

Depois do fechamento da 1.8.0, as 45 imagens novas de `TREINO IA\TREINO` foram usadas em
uma rodada controlada de treino. Quarenta e quatro foram aproveitadas; a imagem
`OFV7383` foi excluída porque mostra a placa `FVT3838` truncada, não a placa do nome do
arquivo. Nenhum asset do APK foi alterado.

No OCR, o dataset `vigiauto_v3` (94 amostras / 87 grupos) foi usado para treinar E5
(inicialização E2, lr 5e-5, 20 épocas), E6 (base, lr 1e-4, 26 épocas) e E7 (E2, lr 1e-5,
15 épocas). Nenhum superou o E2: E5 caiu para 22/27 no pipeline por regredir `L2-002`,
E6 ficou em 22/27 e 22/24 no alvo, e E7 empatou com o E2 em todos os conjuntos de
avaliação (23/27 no pipeline, 23/27 no recorte anotado, 23/24 no alvo, 14/14 na
validação nova e 12/13 no teste novo). No treino, o E7 corrige `DQK1266`, mas passa a errar `LMO8G75`, que
o E2 acerta com o fallback; é uma troca, não um ganho. O `ocr.onnx` (E2) foi mantido.

No detector, o `detector.onnx` é um grafo de inferência (YOLOv9, 384×384) sem pesos
treináveis. O experimento disponível foi um fine-tune de um YOLOv8n separado, com 44
caixas geradas pelo próprio detector como pseudo-rótulos, 36 imagens de treino e 8 de
validação, separadas por placa. O recall no Level 2 caiu de 23/27 (85,2%) no modelo base
para 11/27 (40,7%) no fine-tune; ele não ganhou nenhuma imagem e perdeu 12. Na validação
nova também ficou atrás (6/8 contra 8/8). O candidato foi descartado e o `detector.onnx`
não foi trocado.

A rodada completa, com comandos, hashes, métricas por conjunto e limitações, está em
[`TREINO-2026-10-26.md`](TREINO-2026-10-26.md). A versão continua **1.8.0** /
`versionCode 21`, banco **v5**, com os modelos e o APK da 1.8.0 inalterados. A validação
no aparelho, a migração 4→5 e as medições de latência/memória continuam pendentes.
