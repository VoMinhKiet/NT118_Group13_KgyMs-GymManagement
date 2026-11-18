package com.kgyms.app.data.local

import android.content.Context
import android.util.AtomicFile
import com.kgyms.app.data.model.MemberBodyMeasurements
import com.kgyms.app.data.model.memberAccountKey
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UID-scoped app-private file excluded from Android backup and device transfer. */
internal class MemberBodyMeasurementStore(context: Context, uid: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "member_body_${memberAccountKey(uid)}"))

    suspend fun load(): MemberBodyMeasurements = withContext(Dispatchers.IO) {
        val lines = try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { listOf(it.readLine(), it.readLine()) }
        } catch (_: FileNotFoundException) {
            return@withContext MemberBodyMeasurements()
        }
        MemberBodyMeasurements.parse(lines[0].orEmpty(), lines[1].orEmpty()) ?: MemberBodyMeasurements()
    }

    suspend fun save(value: MemberBodyMeasurements) = withContext(Dispatchers.IO) {
        val checked = requireNotNull(MemberBodyMeasurements.parse(value.weightKg, value.heightCm))
        val stream = file.startWrite()
        try {
            stream.write((checked.weightKg + "\n" + checked.heightCm + "\n").toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}
