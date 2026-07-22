package com.lab.idcam

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lab.idcam.databinding.ActivityReceiptBinding

class ReceiptActivity : AppCompatActivity() {

    private lateinit var b: ActivityReceiptBinding
    private var receipt: Receipt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReceiptBinding.inflate(layoutInflater)
        setContentView(b.root)

        val id = intent.getStringExtra("receiptId")
        receipt = id?.let { Store.findReceipt(it) }

        receipt?.let { r ->
            b.receiptNo.setText(r.receiptNo)
            b.csiNo.setText(r.csiNo)
            b.workName.setText(r.workName)
            b.sampleName.setText(r.sampleName)
            b.note.setText(r.note)
            b.title.text = "접수건 정보 (이어찍기)"
            b.btnGallery.visibility = android.view.View.VISIBLE
        }

        b.btnSaveShoot.setOnClickListener { saveThen(shoot = true) }
        b.btnSaveOnly.setOnClickListener { saveThen(shoot = false) }
        b.btnGallery.setOnClickListener {
            receipt?.let {
                startActivity(
                    Intent(this, GalleryActivity::class.java)
                        .putExtra("receiptId", it.id)
                )
            }
        }
    }

    private fun saveThen(shoot: Boolean) {
        val no = b.receiptNo.text.toString().trim()
        if (no.isBlank()) {
            b.receiptNo.error = "접수번호를 입력하세요"
            return
        }
        var r = receipt
        if (r == null) {
            r = Receipt()
            r.receiptNo = no
            r.csiNo = b.csiNo.text.toString().trim()
            r.workName = b.workName.text.toString().trim()
            r.sampleName = b.sampleName.text.toString().trim()
            r.note = b.note.text.toString().trim()
            Store.addReceipt(r)
            receipt = r
        } else {
            r.receiptNo = no
            r.csiNo = b.csiNo.text.toString().trim()
            r.workName = b.workName.text.toString().trim()
            r.sampleName = b.sampleName.text.toString().trim()
            r.note = b.note.text.toString().trim()
            Store.save()
        }
        b.btnGallery.visibility = android.view.View.VISIBLE

        if (shoot) {
            startActivity(
                Intent(this, CameraActivity::class.java)
                    .putExtra("receiptId", r.id)
            )
        } else {
            Toast.makeText(this, "저장되었습니다", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
