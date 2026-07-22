package com.lab.idcam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.lab.idcam.databinding.ActivityCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var b: ActivityCameraBinding
    private lateinit var receipt: Receipt
    private var imageCapture: ImageCapture? = null
    private val exec = Executors.newSingleThreadExecutor()

    private var stage: String = "시험전"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(b.root)

        val id = intent.getStringExtra("receiptId")
        val found = id?.let { Store.findReceipt(it) }
        if (found == null) {
            Toast.makeText(this, "접수건을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        receipt = found

        // 시험항목 스피너 (사용자가 관리한 목록)
        refreshItemSpinner()
        b.addItem.setOnClickListener { showAddItemDialog() }

        // 시험단계 라디오
        b.stageGroup.setOnCheckedChangeListener { _, checkedId ->
            stage = when (checkedId) {
                R.id.stageDuring -> "시험중"
                R.id.stageAfter -> "시험후"
                else -> "시험전"
            }
            refreshOverlay()
        }
        b.stageBefore.isChecked = true

        b.itemSpinner.setOnItemSelectedListenerCompat { refreshOverlay() }

        b.shutter.setOnClickListener { takePhoto() }
        b.done.setOnClickListener { finish() }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED, REQ_CODE)

        refreshOverlay()
    }

    private fun refreshItemSpinner(selectName: String? = null) {
        val items = Store.testItems.toMutableList()
        if (items.isEmpty()) items.add("(＋로 시험항목 추가)")
        b.itemSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, items
        )
        val idx = selectName?.let { items.indexOf(it) } ?: -1
        if (idx >= 0) b.itemSpinner.setSelection(idx)
    }

    private fun showAddItemDialog() {
        val input = EditText(this).apply {
            hint = "예: 인장강도"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("시험항목 추가")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                if (!Store.testItems.contains(name)) {
                    Store.testItems.add(name)
                    Store.save()
                }
                refreshItemSpinner(selectName = name)
                refreshOverlay()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun currentItem(): String {
        val s = (b.itemSpinner.selectedItem as? String) ?: ""
        return if (s.startsWith("(")) "" else s
    }

    private fun refreshOverlay() {
        val rows = IdCard.rows(stage, currentItem(), receipt, System.currentTimeMillis())
        b.overlay.update("시험정보", rows)
        b.counter.text = "촬영 ${receipt.photos.size}장"
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(b.preview.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "카메라 시작 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        b.shutter.isEnabled = false
        val temp = File(cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(temp).build()
        ic.takePicture(opts, exec, object : ImageCapture.OnImageSavedCallback {
            override fun onError(e: ImageCaptureException) {
                runOnUiThread {
                    b.shutter.isEnabled = true
                    Toast.makeText(this@CameraActivity, "촬영 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val stageNow = stage
                    val itemNow = currentItem()
                    val time = System.currentTimeMillis()
                    val uri = processAndSave(temp, stageNow, itemNow, time)
                    temp.delete()
                    runOnUiThread {
                        b.shutter.isEnabled = true
                        if (uri != null) {
                            receipt.photos.add(Photo(uri.toString(), stageNow, itemNow, time))
                            Store.save()
                            refreshOverlay()
                            Toast.makeText(this@CameraActivity, "저장 완료 (갤러리)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@CameraActivity, "저장 실패", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (ex: Exception) {
                    runOnUiThread {
                        b.shutter.isEnabled = true
                        Toast.makeText(this@CameraActivity, "처리 오류: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    /** 임시 JPEG -> 회전보정 -> 식별표 합성 -> MediaStore(Pictures/시험정보/접수번호) 저장 */
    private fun processAndSave(temp: File, stageNow: String, itemNow: String, time: Long): Uri? {
        var bmp = BitmapFactory.decodeFile(temp.absolutePath) ?: return null

        // EXIF 회전 보정 -> 사진을 항상 똑바로 세운다 (식별표가 옆으로 눕지 않도록)
        val exif = ExifInterface(temp.absolutePath)
        val rot = when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rot != 0f) {
            val m = Matrix().apply { postRotate(rot) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }

        // 식별표 합성
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val rows = IdCard.rows(stageNow, itemNow, receipt, time)
        IdCard.draw(canvas, out.width, out.height, "시험정보", rows, sizeFactor = 0.024f)

        // 파일명 / 폴더명
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date(time))
        val safeNo = sanitize(receipt.receiptNo.ifBlank { "접수미지정" })
        val name = "${safeNo}_${stageNow}_${sanitize(itemNow)}_$stamp.jpg"
        val folder = "Pictures/시험정보/$safeNo"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, folder)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        resolver.openOutputStream(uri)?.use { os ->
            out.compress(Bitmap.CompressFormat.JPEG, 95, os)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        out.recycle()
        bmp.recycle()
        return uri
    }

    private fun sanitize(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()

    private fun allPermissionsGranted() = REQUIRED.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CODE) {
            if (allPermissionsGranted()) startCamera()
            else {
                Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdown()
    }

    companion object {
        private const val REQ_CODE = 10
        private val REQUIRED = arrayOf(Manifest.permission.CAMERA)
    }
}
