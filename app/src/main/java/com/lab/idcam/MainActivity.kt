package com.lab.idcam

import android.content.Intent
import android.os.Bundle
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
    }

    override fun onResume() {
        super.onResume()
        adapter.submit(Store.receipts.toList())
        b.empty.visibility = if (Store.receipts.isEmpty()) View.VISIBLE else View.GONE
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
