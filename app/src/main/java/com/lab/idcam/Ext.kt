package com.lab.idcam

import android.view.View
import android.widget.AdapterView
import android.widget.Spinner

/** 스피너 선택이 바뀔 때 콜백을 부르는 간단한 확장 함수 */
fun Spinner.setOnItemSelectedListenerCompat(onSelected: () -> Unit) {
    this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            onSelected()
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
}
