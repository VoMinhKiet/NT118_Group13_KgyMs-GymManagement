package com.kgyms.app.data.firebase

import com.google.firebase.database.FirebaseDatabase
import com.kgyms.app.data.model.PersonalPlanSyncTarget
import kotlinx.coroutines.flow.StateFlow

/** A UID-scoped capability; adapters cannot select arbitrary projects or database paths. */
internal interface PersonalPlanConnection {
    val database: FirebaseDatabase
    val available: StateFlow<Boolean>
    val target: PersonalPlanSyncTarget
    fun readEndpoint(uid: String): String
    fun guard(uid: String)
    suspend fun token(uid: String): String
    fun retry() {}
    fun record(operation: String, uid: String) {}
}
