package com.lab.idcam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.lab.idcam.databinding.ActivityScanBinding
import java.io.File
import java.util.concurrent.Executors

class ScanActivity : AppCompatActivity() {

    private lateinit var b: ActivityScanBinding
    private var imageCapture: ImageCapture? = null
    private val exec = Executors.newSingleThreadExecutor()

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { runOcr(InputImage.fromFilePath(this, it)) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScanBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.capture.setOnClickListener { takePhoto() }
        b.pick.setOnClickListener { pickImage.launch("image/*") }
        b.cancel.setOnClickListener { finish() }

        if (hasCamera()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 20)
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
        setBusy(true)
        val temp = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(temp).build()
        ic.takePicture(opts, exec, object : ImageCapture.OnImageSavedCallback {
            override fun onError(e: ImageCaptureException) {
                runOnUiThread {
                    setBusy(false)
                    Toast.makeText(this@ScanActivity, "촬영 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                runOnUiThread { runOcr(InputImage.fromFilePath(this@ScanActivity, Uri.fromFile(temp))) }
            }
        })
    }

    private fun runOcr(image: InputImage) {
        setBusy(true)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val r = OcrParse.parse(text)
                setBusy(false)
                val filled = listOf(r.receiptNo, r.csiNo, r.workName, r.sampleName, r.note)
                    .count { it.isNotBlank() }
                if (filled == 0) {
                    Toast.makeText(this, "글자를 인식하지 못했어요. 더 밝고 반듯하게 다시 촬영해 주세요.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                val data = Intent().apply {
                    putExtra("receiptNo", r.receiptNo)
                    putExtra("csiNo", r.csiNo)
                    putExtra("workName", r.workName)
                    putExtra("sampleName", r.sampleName)
                    putExtra("note", r.note)
                }
                setResult(RESULT_OK, data)
                finish()
            }
            .addOnFailureListener { e ->
                setBusy(false)
                Toast.makeText(this, "인식 오류: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setBusy(busy: Boolean) {
        b.progress.visibility = if (busy) View.VISIBLE else View.GONE
        b.capture.isEnabled = !busy
        b.pick.isEnabled = !busy
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 20) {
            if (hasCamera()) startCamera()
            else Toast.makeText(this, "카메라 권한이 필요합니다 (갤러리 선택은 사용 가능)", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdown()
    }
}
