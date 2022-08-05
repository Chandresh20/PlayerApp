package com.nento.player.app

import org.json.JSONObject
import java.io.File

object RecordsAsJSON {

    private val recordFile = File(MainActivity.storageDir, "records.json")

    fun addRecord(name: String, duration: Int) {
        val allRec = getAllRecords()
        allRec.put(name, duration)
        recordFile.writeText(allRec.toString())
    }

    fun getAllRecords() : JSONObject {
        return if (!recordFile.exists()) {
            JSONObject()
        } else {
            JSONObject(recordFile.readText())
        }
    }

    fun clearAllRecords() {
        if (recordFile.exists()) {
            recordFile.delete()
        }
    }
}