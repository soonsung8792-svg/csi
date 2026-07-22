package com.lab.idcam

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/** 카메라 미리보기 위에 시료식별표를 실시간으로 보여주는 뷰 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var title: String = "시료식별표"
    private var rows: List<Pair<String, String>> = emptyList()

    fun update(title: String, rows: List<Pair<String, String>>) {
        this.title = title
        this.rows = rows
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rows.isEmpty()) return
        IdCard.draw(canvas, width, height, title, rows, sizeFactor = 0.030f)
    }
}
