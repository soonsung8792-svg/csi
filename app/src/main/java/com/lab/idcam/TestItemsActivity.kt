package com.lab.idcam

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lab.idcam.databinding.ActivityItemsBinding

class TestItemsActivity : AppCompatActivity() {

    private lateinit var b: ActivityItemsBinding
    private val adapter = ItemAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityItemsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        adapter.onDelete = { name -> confirmDelete(name) }
        refresh()

        b.btnAdd.setOnClickListener {
            val name = b.input.text.toString().trim()
            if (name.isBlank()) {
                b.input.error = "항목명을 입력하세요"; return@setOnClickListener
            }
            if (Store.testItems.contains(name)) {
                Toast.makeText(this, "이미 있는 항목입니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Store.testItems.add(name)
            Store.save()
            b.input.setText("")
            refresh()
        }
    }

    private fun refresh() {
        adapter.submit(Store.testItems.toList())
        b.empty.visibility = if (Store.testItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("시험항목 삭제")
            .setMessage("‘$name’ 항목을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                Store.testItems.remove(name)
                Store.save()
                refresh()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    inner class ItemAdapter : RecyclerView.Adapter<ItemAdapter.VH>() {
        private var items: List<String> = emptyList()
        var onDelete: ((String) -> Unit)? = null
        fun submit(list: List<String>) { items = list; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.name)
            val del: ImageButton = v.findViewById(R.id.delete)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_testitem, parent, false)
            return VH(v)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val n = items[position]
            holder.name.text = n
            holder.del.setOnClickListener { onDelete?.invoke(n) }
        }
    }
}
