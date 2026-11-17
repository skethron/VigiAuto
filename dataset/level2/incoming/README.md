# Imagens novas — área de entrada

Coloque aqui os arquivos originais das novas fotos. **Não precisa renomear,
recortar nem anotar.** O pré-processamento e a pré-anotação são feitos por
script; depois os casos duvidosos voltam para confirmação humana.

## Estado em 22/10/2026

As 26 imagens reduzidas que estavam aqui eram duplicatas de cenas já migradas
para `dataset/level2/images_lowres/` e foram removidas com autorização do
usuário. A pasta agora contém apenas este README. As cenas novas devem ser
colocadas aqui a partir de agora.

## O que enviar

- Fotos tiradas diretamente pela câmera do celular, de preferência.
- Formatos: `.jpg`, `.jpeg`, `.png` ou `.webp`.
- **Não** envie screenshot da galeria, foto do WhatsApp ou imagem com barras
  pretas. Se só tiver WhatsApp, envie como **documento/arquivo**, não como
  “foto”, para evitar recompressão.
- Mantenha o arquivo original; não corte a placa nem aumente a imagem.
- Pode mandar em lotes de 20–50; não precisa esperar juntar tudo.

## O que vale a pena fotografar

- carros, SUVs, vans, caminhões e motos;
- placas Mercosul e placas antigas/cinza;
- de dia, à noite, com sol, chuva, farol, movimento e ângulos variados;
- placas sujas, tortas, parcialmente encobertas e cenas com mais de um veículo;
- exemplos sem placa (fachadas, letreiros, outdoors) também ajudam como
  **negativos**.

Evite rostos, documentos e imagens particulares que não tenham relação com
veículos/placas.

## Como é processado

Quando avisar que as imagens estão aqui, o comando é:

```bat
python tools\level2_dataset.py prepare --source dataset\level2\incoming
```

O script copia as imagens para `dataset/level2/images/`, roda o ONNX como
pré-anotador e gera `dataset/level2/labels.template.csv`. Depois as caixas e
placas são revisadas, `dataset/level2/labels.csv` é atualizado e o benchmark é
reexecutado.

Se preferir, também é possível ler uma pasta fora do projeto, por exemplo
`C:\Users\Guilherme\Downloads\TREINO IA 2`.
