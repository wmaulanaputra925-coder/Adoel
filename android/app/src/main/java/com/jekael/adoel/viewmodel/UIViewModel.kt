package com.jekael.adoel.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ToastState(
    val key: Int,
    val msg: String,
)

data class ConfirmState(
    val msg: String,
    val onConfirm: () -> Unit,
    val onCancel: (() -> Unit)? = null,
    // Default ke "Ya"/"Batal" di ConfirmDialog.kt kalau tidak diisi — dipakai satu-satunya oleh
    // pengingat Tali Hijau setelah doff HB, yang butuh label aksi yang lebih spesifik daripada
    // konfirmasi generik.
    val confirmLabel: String? = null,
    val cancelLabel: String? = null,
)

class UIViewModel : ViewModel() {
    private val _toast = MutableStateFlow<ToastState?>(null)
    val toast: StateFlow<ToastState?> = _toast.asStateFlow()

    private val _confirm = MutableStateFlow<ConfirmState?>(null)
    val confirm: StateFlow<ConfirmState?> = _confirm.asStateFlow()

    private var toastKey = 0

    fun showToast(msg: String) {
        toastKey++
        _toast.value = ToastState(toastKey, msg)
    }

    fun dismissToast() {
        _toast.value = null
    }

    fun showConfirm(
        msg: String,
        onCancel: (() -> Unit)? = null,
        confirmLabel: String? = null,
        cancelLabel: String? = null,
        onConfirm: () -> Unit,
    ) {
        _confirm.value = ConfirmState(msg, onConfirm, onCancel, confirmLabel, cancelLabel)
    }

    fun dismissConfirm() {
        _confirm.value = null
    }
}
