package com.kgyms.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await

data class ChatMessageDto(
    val id: String = "",
    val text: String = "",
    val isUser: Boolean = true,
    val timestamp: Long = 0L
)

class ChatRepository(
    private val database: FirebaseDatabase = try {
        FirebaseDatabase.getInstance("https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/")
    } catch (e: Exception) {
        FirebaseDatabase.getInstance()
    },
    private val auth: FirebaseAuth = try { FirebaseAuth.getInstance() } catch (e: Exception) { throw e }
) {
    val currentUid: String?
        get() = try {
            auth.currentUser?.uid ?: "demo_user"
        } catch (e: Exception) {
            "demo_user"
        }

    private fun historyRef(uid: String) = database.getReference("bob_chat_history").child(uid)

    suspend fun saveMessage(uid: String, text: String, isUser: Boolean): ChatMessageDto? {
        return try {
            val ref = historyRef(uid).push()
            val messageId = ref.key ?: System.currentTimeMillis().toString()
            val message = ChatMessageDto(
                id = messageId,
                text = text,
                isUser = isUser,
                timestamp = System.currentTimeMillis()
            )
            ref.setValue(message).await()
            message
        } catch (e: Exception) {
            Log.e("ChatRepo", "Safe catch saveMessage error: ${e.message}", e)
            null
        }
    }

    suspend fun getHistory(uid: String): List<ChatMessageDto> {
        return try {
            val snapshot = historyRef(uid).get().await()
            val list = mutableListOf<ChatMessageDto>()
            for (child in snapshot.children) {
                val msg = child.getValue(ChatMessageDto::class.java)
                if (msg != null) {
                    list.add(msg)
                }
            }
            list.sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Safe catch getHistory error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun clearHistory(uid: String) {
        try {
            historyRef(uid).removeValue().await()
        } catch (e: Exception) {
            Log.e("ChatRepo", "Safe catch clearHistory error: ${e.message}", e)
        }
    }

    fun observeHistory(uid: String, onUpdate: (List<ChatMessageDto>) -> Unit): ValueEventListener? {
        return try {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<ChatMessageDto>()
                    for (child in snapshot.children) {
                        val msg = child.getValue(ChatMessageDto::class.java)
                        if (msg != null) list.add(msg)
                    }
                    onUpdate(list.sortedBy { it.timestamp })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatRepo", "observeHistory cancelled: ${error.message}")
                }
            }
            historyRef(uid).addValueEventListener(listener)
            listener
        } catch (e: Exception) {
            Log.e("ChatRepo", "Safe catch observeHistory error: ${e.message}", e)
            null
        }
    }

    fun removeListener(uid: String, listener: ValueEventListener?) {
        if (listener != null) {
            try {
                historyRef(uid).removeEventListener(listener)
            } catch (_: Exception) {}
        }
    }
}
