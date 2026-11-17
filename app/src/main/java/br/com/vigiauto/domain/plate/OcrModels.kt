package br.com.vigiauto.domain.plate

data class OcrElement(val text: String, val box: PlateBox)
data class OcrLine(val text: String, val box: PlateBox, val elements: List<OcrElement>)
data class OcrDocument(val lines: List<OcrLine>)
