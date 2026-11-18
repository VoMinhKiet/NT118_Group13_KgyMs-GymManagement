package com.kgyms.app.ui.member.profile

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.viewmodel.membership.MembershipUiState
import com.kgyms.app.viewmodel.membership.MembershipViewModel

internal val LocalMembershipModel = staticCompositionLocalOf<MembershipViewModel?> { null }

@Composable
internal fun membershipState(model: MembershipViewModel?): MembershipUiState {
    val snapshot = model?.state?.collectAsState()?.value
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model?.refreshValidity()
        }
        if (model != null) lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return if (model != null && snapshot != null) model.forCurrentUser(snapshot) else MembershipUiState()
}

@Composable
internal fun MemberMembershipRoute(
    model: MembershipViewModel?,
    state: MembershipUiState,
    onBack: () -> Unit,
    onCheckout: ((String, GymPackage) -> Unit)?,
    openCatalog: Boolean = false,
) {
    val handoff: ((String, GymPackage) -> Unit)? = onCheckout?.let { checkout ->
        { owner, requested ->
            val current = model?.let { it.forCurrentUser(it.state.value) }
            val available = current?.catalog?.firstOrNull { it.id == requested.id }
            if (current?.uid == owner && available != null) checkout(owner, available)
        }
    }
    MemberPackagesMembershipScreen(state, { model?.retry() }, onBack, handoff, openCatalog)
}
