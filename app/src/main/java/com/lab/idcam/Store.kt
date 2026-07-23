package com.lab.idcam

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 한 장의 사진 기록 */
data class Photo(
    val uri: String,
    val stage: String,
    val item: String,
    val time: Long
)

/** 접수건 하나 (접수번호 기준) */
data class Receipt(
    val id: String = UUID.randomUUID().toString(),
    var receiptNo: String = "",
    var csiNo: String = "",
    var workName: String = "",
    var sampleName: String = "",
    var note: String = "",
    var items: MutableList<String> = mutableListOf(),   // 이 접수건 전용 시험항목
    val photos: MutableList<Photo> = mutableListOf()
)

/**
 * 모든 데이터를 filesDir/data.json 에 저장하는 간단한 저장소.
 * (Room 등 외부 라이브러리 없이 org.json 만 사용 -> 빌드가 단순함)
 */
object Store {
    val receipts = mutableListOf<Receipt>()
    val testItems = mutableListOf<String>()

    private val file: File get() = File(App.ctx.filesDir, "data.json")

    fun load() {
        receipts.clear()
        testItems.clear()
        if (!file.exists()) {
            // 최초 실행 시 시험항목은 비어 있음 (사용자가 직접 추가)
            save()
            return
        }
        try {
            val root = JSONObject(file.readText())
            val items = root.optJSONArray("testItems") ?: JSONArray()
            for (i in 0 until items.length()) testItems.add(items.getString(i))

            val recs = root.optJSONArray("receipts") ?: JSONArray()
            for (i in 0 until recs.length()) {
                val r = recs.getJSONObject(i)
                val rec = Receipt(
                    id = r.optString("id", UUID.randomUUID().toString()),
                    receiptNo = r.optString("receiptNo"),
                    csiNo = r.optString("csiNo"),
                    workName = r.optString("workName"),
                    sampleName = r.optString("sampleName"),
                    note = r.optString("note")
                )
                val its = r.optJSONArray("items") ?: JSONArray()
                for (j in 0 until its.length()) rec.items.add(its.getString(j))
                val ps = r.optJSONArray("photos") ?: JSONArray()
                for (j in 0 until ps.length()) {
                    val p = ps.getJSONObject(j)
                    rec.photos.add(
                        Photo(
                            uri = p.optString("uri"),
                            stage = p.optString("stage"),
                            item = p.optString("item"),
                            time = p.optLong("time")
                        )
                    )
                }
                receipts.add(rec)
            }
        } catch (e: Exception) {
            // 파일 손상 시 초기화 (빈 목록)
        }
    }

    fun save() {
        val root = JSONObject()
        root.put("testItems", JSONArray(testItems))
        val recs = JSONArray()
        for (rec in receipts) {
            val r = JSONObject()
            r.put("id", rec.id)
            r.put("receiptNo", rec.receiptNo)
            r.put("csiNo", rec.csiNo)
            r.put("workName", rec.workName)
            r.put("sampleName", rec.sampleName)
            r.put("note", rec.note)
            r.put("items", JSONArray(rec.items))
            val ps = JSONArray()
            for (p in rec.photos) {
                val o = JSONObject()
                o.put("uri", p.uri)
                o.put("stage", p.stage)
                o.put("item", p.item)
                o.put("time", p.time)
                ps.put(o)
            }
            r.put("photos", ps)
            recs.put(r)
        }
        root.put("receipts", recs)
        file.writeText(root.toString())
    }

    fun findReceipt(id: String): Receipt? = receipts.firstOrNull { it.id == id }

    fun addReceipt(r: Receipt) {
        receipts.add(0, r)
        save()
    }

    fun deleteReceipt(r: Receipt) {
        receipts.remove(r)
        save()
    }
}
