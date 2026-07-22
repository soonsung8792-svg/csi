package com.lab.idcam

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lab.idcam.databinding.ActivityGalleryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class GalleryActivity : AppCompatActivity() {

    private lateinit var b: ActivityGalleryBinding
    private lateinit var receipt: Receipt
    private val adapter = PhotoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(b.root)

        val id = intent.getStringExtra("receiptId")
        val found = id?.let { Store.findReceipt(it) }
        if (found == null) { finish(); return }
        receipt = found

        b.header.text = "${receipt.receiptNo}  (${receipt.photos.size}장)"
        b.list.layoutManager = GridLayoutManager(this, 2)
        b.list.adapter = adapter
        adapter.onClick = { p -> openPhoto(p.uri) }

        b.btnShoot.setOnClickListener {
            startActivity(
                Intent(this, CameraActivity::class.java)
                    .putExtra("receiptId", receipt.id)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        b.header.text = "${receipt.receiptNo}  (${receipt.photos.size}장)"
        adapter.submit(receipt.photos.sortedByDescending { it.time })
        b.empty.visibility = if (receipt.photos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openPhoto(uri: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(Uri.parse(uri), "image/*")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    inner class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.VH>() {
        private var items: List<Photo> = emptyList()
        var onClick: ((Photo) -> Unit)? = null
        private val exec = Executors.newFixedThreadPool(3)
        private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.KOREA)

        fun submit(list: List<Photo>) { items = list; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.img)
            val label: TextView = v.findViewById(R.id.label)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo, parent, false)
            return VH(v)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            holder.label.text = "${p.stage} · ${p.item}\n${fmt.format(Date(p.time))}"
            holder.img.setImageDrawable(null)
            holder.img.setOnClickListener { onClick?.invoke(p) }
            val uri = Uri.parse(p.uri)
            exec.execute {
                val bmp = loadThumb(uri)
                holder.img.post {
                    if (holder.adapterPosition == position) holder.img.setImageBitmap(bmp)
                }
            }
        }

        private fun loadThumb(uri: Uri): Bitmap? {
            return try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    var sample = 1
                    val target = 400
                    while (o.outWidth / sample > target || o.outHeight / sample > target) sample *= 2
                    val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
                }
            } catch (e: Exception) { null }
        }
    }
}
