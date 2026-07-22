package com.lab.idcam

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 화면 우측 하단에 붙는 "시료식별표" 카드.
 * 카메라 미리보기(OverlayView)와 실제 저장되는 사진 모두 같은 그림을 사용한다.
 */
object IdCard {

    /** 표에 들어갈 (라벨, 값) 목록 만들기 */
    fun rows(stage: String, item: String, r: Receipt, time: Long): List<Pair<String, String>> {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
        return listOf(
            "시험단계" to stage,
            "시험항목" to item,
            "접수번호" to r.receiptNo,
            "CSI접수번호" to r.csiNo,
            "공사명" to r.workName,
            "시료명" to r.sampleName,
            "비고" to r.note,
            "일시" to fmt.format(Date(time))
        )
    }

    /**
     * canvas 의 (w x h) 영역 우측 하단에 카드를 그린다.
     * @param sizeFactor 글자 크기 비율. 사진은 0.024, 미리보기는 0.030 정도.
     */
    fun draw(
        canvas: Canvas,
        w: Int,
        h: Int,
        title: String,
        rows: List<Pair<String, String>>,
        sizeFactor: Float
    ) {
        val base = minOf(w, h).toFloat()
        var textSize = base * sizeFactor
        val maxCardWidth = w * 0.62f

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 214, 92)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // 폭이 넘치면 글자 크기를 줄여 맞춘다.
        fun longest(ts: Float): Float {
            labelPaint.textSize = ts
            valuePaint.textSize = ts
            var m = 0f
            for ((l, v) in rows) {
                val line = "$l : ${if (v.isBlank()) "-" else v}"
                m = maxOf(m, valuePaint.measureText(line))
            }
            titlePaint.textSize = ts * 1.05f
            m = maxOf(m, titlePaint.measureText(title))
            return m
        }

        val pad = textSize * 0.6f
        var content = longest(textSize)
        while (content + pad * 2 > maxCardWidth && textSize > base * 0.012f) {
            textSize *= 0.94f
            content = longest(textSize)
        }

        val lineH = textSize * 1.42f
        val titleH = textSize * 1.6f
        val cardW = content + pad * 2
        val cardH = pad * 2 + titleH + rows.size * lineH
        val margin = base * 0.02f
        val left = w - cardW - margin
        val top = h - cardH - margin

        // 반투명 배경
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 0, 0, 0) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(210, 255, 255, 255)
            strokeWidth = base * 0.0025f
        }
        val rect = RectF(left, top, left + cardW, top + cardH)
        val radius = textSize * 0.4f
        canvas.drawRoundRect(rect, radius, radius, bg)
        canvas.drawRoundRect(rect, radius, radius, border)

        // 제목
        titlePaint.textSize = textSize * 1.05f
        var y = top + pad + textSize * 1.05f
        canvas.drawText(title, left + pad, y, titlePaint)
        y += titleH - textSize * 1.05f + textSize * 0.2f

        // 각 줄: "라벨 : 값"
        labelPaint.textSize = textSize
        valuePaint.textSize = textSize
        for ((l, v) in rows) {
            y += lineH
            val label = "$l : "
            canvas.drawText(label, left + pad, y, labelPaint)
            val lw = labelPaint.measureText(label)
            val value = if (v.isBlank()) "-" else v
            canvas.drawText(value, left + pad + lw, y, valuePaint)
        }
    }
}
