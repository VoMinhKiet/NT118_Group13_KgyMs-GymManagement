package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.ActivityLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Đọc/ghi node activityLogs/{logId} trên Firebase Realtime Database.
 * Rules: chỉ admin đọc được; mỗi người chỉ được tạo log mới với actorUid là chính mình.
 */
class ActivityLogService(
    database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val logsReference = database.getReference("activityLogs")

    /** Lắng nghe realtime toàn bộ nhật ký (chưa sắp xếp; ViewModel tự sắp theo thời gian). */
    fun observeLogs(): Flow<List<ActivityLog>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val logs = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    if (key.startsWith("_")) null else child.toLog(key)
                }
                trySend(logs)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        logsReference.addValueEventListener(listener)

        awaitClose {
            logsReference.removeEventListener(listener)
        }
    }

    suspend fun addLog(log: ActivityLog) {
        logsReference.push().setValue(
            mapOf(
                "title" to log.title,
                "actorUid" to log.actorUid,
                "action" to log.action,
                "createdAt" to log.createdAt
            )
        ).await()
    }

    private fun DataSnapshot.toLog(id: String) = ActivityLog(
        id = id,
        title = child("title").value?.toString().orEmpty(),
        actorUid = child("actorUid").value?.toString().orEmpty(),
        action = child("action").value?.toString().orEmpty(),
        createdAt = child("createdAt").value?.toString().orEmpty()
    )
}
