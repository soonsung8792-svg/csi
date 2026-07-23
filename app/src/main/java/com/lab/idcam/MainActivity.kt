package com.lab.idcam

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.nio.charset.Charset
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lab.idcam.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val adapter = ReceiptListAdapter()

    /** PC 엑셀에서 만든 CSV 고르기 */
    private val pickCsv = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { importCsv(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        adapter.onClick = { r ->
            // 기존 접수건 이어서 촬영 / 정보 수정
            startActivity(
                Intent(this, ReceiptActivity::class.java)
                    .putExtra("receiptId", r.id)
            )
        }
        adapter.onLongClick = { r -> confirmDelete(r) }

        b.btnNew.setOnClickListener {
            startActivity(Intent(this, ReceiptActivity::class.java))
        }
        b.btnItems.setOnClickListener {
            startActivity(Intent(this, TestItemsActivity::class.java))
        }
        b.btnImport.setOnClickListener { pickCsv.launch("*/*") }
        b.btnTemplate.setOnClickListener { saveTemplate() }
    }

    override fun onResume() {
        super.onResume()
        adapter.submit(Store.receipts.toList())
        b.empty.visibility = if (Store.receipts.isEmpty()) View.VISIBLE else View.GONE
    }

    /** CSV 읽어서 접수건 목록에 반영 (같은 접수번호는 갱신) */
    private fun importCsv(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            var text = String(bytes, Charsets.UTF_8)
            if (text.contains('\uFFFD')) {          // 한글이 깨지면 엑셀 기본(CP949)로 재시도
                text = String(bytes, Charset.forName("EUC-KR"))
            }
            val rows = CsvHelper.parse(text)
            if (rows.isEmpty()) {
                Toast.makeText(this, "불러올 내용이 없습니다. 양식을 확인하세요", Toast.LENGTH_LONG).show()
                return
            }
            var added = 0; var updated = 0
            for (row in rows) {
                val ex = Store.receipts.firstOrNull { it.receiptNo == row.receiptNo }
                if (ex != null) {
                    ex.csiNo = row.csiNo; ex.workName = row.workName
                    ex.sampleName = row.sampleName; ex.note = row.note
                    updated++
                } else {
                    Store.receipts.add(0, Receipt(
                        receiptNo = row.receiptNo, csiNo = row.csiNo,
                        workName = row.workName, sampleName = row.sampleName, note = row.note
                    ))
                    added++
                }
            }
            Store.save()
            adapter.submit(Store.receipts.toList())
            b.empty.visibility = if (Store.receipts.isEmpty()) View.VISIBLE else View.GONE
            Toast.makeText(this, "불러오기 완료 — 새로 ${added}건, 갱신 ${updated}건", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "불러오기 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** 엑셀 양식(CSV)을 다운로드 폴더에 저장 */
    private fun saveTemplate() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "시험정보_접수건_양식.csv")
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show(); return
            }
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write("\uFEFF".toByteArray(Charsets.UTF_8))   // 엑셀 한글 깨짐 방지
                os.write(CsvHelper.TEMPLATE.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "다운로드 폴더에 양식을 저장했습니다", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(r: Receipt) {
        AlertDialog.Builder(this)
            .setTitle("접수건 삭제")
            .setMessage("‘${r.receiptNo}’ 접수건 기록을 삭제할까요?\n(이미 촬영한 사진 파일은 갤러리에 그대로 남습니다.)")
            .setPositiveButton("삭제") { _, _ ->
                Store.deleteReceipt(r)
                adapter.submit(Store.receipts.toList())
                b.empty.visibility = if (Store.receipts.isEmpty()) View.VISIBLE else View.GONE
            }
            .setNegativeButton("취소", null)
            .show()
    }
}

class ReceiptListAdapter : RecyclerView.Adapter<ReceiptListAdapter.VH>() {
    private var items: List<Receipt> = emptyList()
    var onClick: ((Receipt) -> Unit)? = null
    var onLongClick: ((Receipt) -> Unit)? = null

    fun submit(list: List<Receipt>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val count: TextView = v.findViewById(R.id.count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_receipt, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.title.text = if (r.receiptNo.isBlank()) "(접수번호 없음)" else r.receiptNo
        val sub = buildString {
            if (r.csiNo.isNotBlank()) append("CSI ${r.csiNo}  ")
            if (r.sampleName.isNotBlank()) append(r.sampleName)
        }
        holder.sub.text = sub.ifBlank { r.workName }
        holder.count.text = "${r.photos.size}장"
        holder.itemView.setOnClickListener { onClick?.invoke(r) }
        holder.itemView.setOnLongClickListener { onLongClick?.invoke(r); true }
    }
}
