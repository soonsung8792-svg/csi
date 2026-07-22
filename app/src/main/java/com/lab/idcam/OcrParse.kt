package com.lab.idcam

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

/** 신청서 스캔 결과에서 각 항목을 뽑아낸 값 */
data class ScanResult(
    val receiptNo: String = "",
    val csiNo: String = "",
    val workName: String = "",
    val sampleName: String = "",
    val note: String = ""
)

/**
 * ML Kit 로 인식한 텍스트(줄 단위 + 위치)를 이용해
 * 시험신청서의 접수번호/CSI/공사명/시료명/비고를 추출한다.
 * - 인쇄 글자는 잘 잡히고, 손글씨는 틀릴 수 있으므로 결과는 반드시 사용자가 확인한다.
 */
object OcrParse {

    private data class Line(val text: String, val box: Rect)

    fun parse(text: Text): ScanResult {
        val lines = mutableListOf<Line>()
        for (block in text.textBlocks) {
            for (l in block.lines) {
                val b = l.boundingBox ?: continue
                lines.add(Line(l.text.trim(), b))
            }
        }
        val allText = lines.joinToString(" ") { it.text }
        val noSpace = allText.replace("\\s".toRegex(), "")

        return ScanResult(
            receiptNo = findReceiptNo(noSpace),
            csiNo = findCsiNo(noSpace),
            workName = findWorkName(lines),
            sampleName = valueRightOf(lines, "시료명") ?: "",
            note = findNote(lines)
        )
    }

    /** 예: M253-26-00496 */
    private fun findReceiptNo(noSpace: String): String {
        val m = Regex("[MmＭ]\\d{3}-\\d{2}-\\d{3,6}").find(noSpace)
        return m?.value?.uppercase() ?: ""
    }

    /** 예: AC-2026-117907 */
    private fun findCsiNo(noSpace: String): String {
        // AC-2026-117907 형태 (OCR 오인식 대비: C 를 0/O 로 읽는 경우 등)
        val m = Regex("A[CcＣ0Oo]?-?(20\\d{2})-?(\\d{5,6})").find(noSpace)
        if (m != null) {
            val year = m.groupValues[1]
            val tail = m.groupValues[2]
            return "AC-$year-$tail"
        }
        return ""
    }

    /** 공사명: 라벨 오른쪽 값 우선, 없으면 '공사' 포함 인쇄줄 */
    private fun findWorkName(lines: List<Line>): String {
        valueRightOf(lines, "공사명")?.let { if (it.isNotBlank()) return it }
        // 라벨 자체/성적서 안내문구는 제외하고, '공사'가 들어간 가장 긴 줄
        val cand = lines
            .map { it.text }
            .filter { it.contains("공사") && !it.contains("공사명") && !it.contains("성적서") }
            .maxByOrNull { it.length }
        return cand ?: ""
    }

    /** 비고: '비고' 라벨 오른쪽 값, 없으면 'CSI' 포함 줄 */
    private fun findNote(lines: List<Line>): String {
        valueRightOf(lines, "비고")?.let { if (it.isNotBlank()) return it }
        val csi = lines.map { it.text }.firstOrNull { it.contains("CSI") }
        return csi ?: ""
    }

    /**
     * 라벨이 들어간 줄을 찾아, 그 줄과 같은 높이대의 '오른쪽' 줄 텍스트를 돌려준다.
     * (표의 "라벨 : 값" 구조에서 값을 뽑는 용도)
     */
    private fun valueRightOf(lines: List<Line>, label: String): String? {
        val labelLine = lines
            .filter { it.text.replace(" ", "").contains(label) }
            .minByOrNull { it.box.left } ?: return null

        // 라벨 텍스트 뒤에 값이 붙어 있으면 그대로 사용 (예: "시료명 합성섬유")
        val inline = labelLine.text.replace(" ", "")
            .substringAfter(label, "")
            .trim(':', '：', ' ', '\t')
        if (inline.length >= 2) return inline

        val cy = labelLine.box.centerY()
        val h = labelLine.box.height().coerceAtLeast(1)
        return lines
            .filter { it !== labelLine }
            .filter { it.box.left >= labelLine.box.right - h * 0.4 }
            .filter { abs(it.box.centerY() - cy) <= h * 1.0 }
            .minByOrNull { it.box.left }
            ?.text
            ?.trim()
    }
}
