package com.freefjay.localshare.pages

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.freefjay.localshare.component.Form
import com.freefjay.localshare.component.FormInstance
import com.freefjay.localshare.component.FormItem
import com.freefjay.localshare.component.FormValidateError
import com.freefjay.localshare.component.formState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Test(

) {

    val formInstance = remember {
        FormInstance()
    }
    val value1 = formState("0") {
        if (it?.isBlank() != false) {
            throw FormValidateError("不能为空")
        }
    }

    Form(formInstance = formInstance) {
        FormItem(fieldState = value1) {
            TextField(value = value1.value ?: "", onValueChange = {
                value1.value = it
            })
        }
    }

    if (value1.hasError) {
        Text(text = "错误：${value1.errorMsg}")
    }

    Button(onClick = {
        value1.value += "0"
    }) {
        Text(text = "测试")
    }

    Text(text = value1.value ?: "")
    Button(onClick = {
        formInstance.validate()
    }) {
        Text(text = "验证")
    }
}
