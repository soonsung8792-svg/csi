package com.lab.idcam

/**
 * PC(엑셀)에서 만든 접수건 목록 CSV 를 읽어들이는 도우미.
 * 열 순서: 접수번호, CSI접수번호, 공사명, 시료명, 비고
 */
object CsvHelper {

    val TEMPLATE = "접수번호,CSI접수번호,공사명,시료명,비고,시험항목\r\n" +
            "M253-00-00000,AC-0000-000000,신청서 참고,신청서 참고,시료번호 등 입력,\"치수;인장강도;비중;두께\"\r\n"

    data class Row(
        val receiptNo: String,
        val csiNo: String,
        val workName: String,
        val sampleName: String,
        val note: String,
        val items: List<String>
    )

    /** 헤더 줄을 보고 구분자 자동 감지 (쉼표 / 세미콜론 / 탭) */
    fun detectDelimiter(text: String): Char {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        var q = false
        for (ch in line) {
            if (ch == '"') q = !q
            else if (!q && counts.containsKey(ch)) counts[ch] = counts[ch]!! + 1
        }
        val best = counts.maxByOrNull { it.value }!!
        return if (best.value == 0) ',' else best.key
    }

    fun parse(text: String): List<Row> {
        val clean = text.removePrefix("\uFEFF")
        val rows = splitRows(clean, detectDelimiter(clean))
        if (rows.isEmpty()) return emptyList()

        var start = 0
        var idxReceipt = 0; var idxCsi = 1; var idxWork = 2; var idxSample = 3
        var idxNote = 4; var idxItems = 5
        val head = rows[0].map { it.replace("\\s".toRegex(), "") }
        if (head.any { it.contains("접수번호") }) {
            start = 1
            idxReceipt = head.indexOfFirst { it.contains("접수번호") && !it.contains("CSI") }
            idxCsi = head.indexOfFirst { it.contains("CSI") }
            idxWork = head.indexOfFirst { it.contains("공사") }
            idxSample = head.indexOfFirst { it.contains("시료명") }
            idxNote = head.indexOfFirst { it.contains("비고") }
            idxItems = head.indexOfFirst { it.contains("시험항목") }
        }

        fun cell(c: List<String>, i: Int): String =
            if (i >= 0 && i < c.size) c[i].trim() else ""

        val out = mutableListOf<Row>()
        for (i in start until rows.size) {
            val c = rows[i]
            if (c.all { it.isBlank() }) continue
            val receipt = cell(c, idxReceipt)
            if (receipt.isBlank()) continue
            out.add(
                Row(
                    receiptNo = receipt,
                    csiNo = cell(c, idxCsi),
                    workName = cell(c, idxWork),
                    sampleName = cell(c, idxSample),
                    note = cell(c, idxNote),
                    items = splitItems(cell(c, idxItems))
                )
            )
        }
        return out
    }

    /** "치수;인장강도;비중" 을 목록으로 */
    fun splitItems(text: String): List<String> =
        if (text.isBlank()) emptyList()
        else text.split(';', ',', '/', '|', '\u00B7')
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()

    /** 따옴표(") 안의 쉼표까지 처리하는 CSV 분해기 */
    private fun splitRows(text: String, delim: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ }
                    else quoted = false
                } else cell.append(ch)
            } else {
                when (ch) {
                    '"' -> quoted = true
                    delim -> { row.add(cell.toString()); cell.setLength(0) }
                    '\n' -> { row.add(cell.toString()); rows.add(row); row = mutableListOf(); cell.setLength(0) }
                    '\r' -> { }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }
}
