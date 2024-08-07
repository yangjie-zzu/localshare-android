package com.freefjay.localshare.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember

class FormInstance {

    val fieldStates = mutableListOf<FieldState<out Any?>>()

    fun addFieldState(fieldState: FieldState<out Any?>) {
        if (fieldStates.add(fieldState)) {
            fieldState.formInstance = this
        }
    }

    fun removeFieldState(fieldState: FieldState<out Any?>) {
        if (fieldStates.remove(fieldState)) {
            fieldState.formInstance = null
        }
    }

    fun validate() {
        fieldStates.forEach {
            it.validate()
        }
    }
}

val LocalForm = compositionLocalOf<FormInstance?> { null }

@Composable
fun Form(
    formInstance: FormInstance,
    content: @Composable () -> Unit
) {

    CompositionLocalProvider(LocalForm provides formInstance) {
        content()
    }
}

@Composable
fun <V: Any?> FormItem(
    fieldState: FieldState<V>,
    content: @Composable () -> Unit
) {
    val formInstance = LocalForm.current
    DisposableEffect(key1 = formInstance, key2 = fieldState, effect = {
        if (fieldState.formInstance == null) {
            formInstance?.addFieldState(fieldState)
        }
        onDispose { formInstance?.removeFieldState(fieldState) }
    })
    content()
}

class FieldState<V: Any?> (
    val flush: () -> Unit,
    var validator: ((value: V?) -> Unit)?
) {

    var formInstance: FormInstance? = null

    var isValidated: Boolean = false

    var value: V? = null
        set(value) {
            field = value
            if (this.isValidated) {
                this.validate()
            }
            flush()
        }

    var hasError: Boolean = false
        set(value) {
            field = value
            flush()
        }

    var errorMsg: String? = null
        set(value) {
            field = value
            flush()
        }

    fun validate() {
        try {
            this.validator?.let { it(this.value) }
            this.hasError = false
        } catch (e: FormValidateError) {
            this.hasError = true
            this.errorMsg = e.errorMsg
        } finally {
            this.isValidated = true
        }
    }
}

class FormValidateError(val errorMsg: String?) : RuntimeException() {

}

@Composable
fun <V : Any?> formState(
    initialValue: V?,
    validator: ((value: V?) -> Unit)? = null
): FieldState<V?> {
    val recompose = currentRecomposeScope
    val fieldState = remember {
        val fieldState = FieldState(
            flush = {recompose.invalidate()},
            validator
        )
        fieldState.value = initialValue
        fieldState
    }
    val formInstance = LocalForm.current
    DisposableEffect(key1 = formInstance) {
        formInstance?.addFieldState(fieldState)
        onDispose {
            formInstance?.removeFieldState(fieldState)
        }
    }
    return fieldState
}

