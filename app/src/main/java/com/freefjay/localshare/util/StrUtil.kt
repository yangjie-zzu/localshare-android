package com.freefjay.localshare.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun String.toUnderCase(): String {
    val len = this.length
    val res = StringBuilder(len + 2);
    var pre = '\u0000';
    val charAry = this.toCharArray()
    for ( i in 0 until len) {
        val ch = charAry[i];
        if (Character.isUpperCase(ch)) {
            if (pre != '_') {
                res.append("_");
            }
            res.append(Character.toLowerCase(ch));
        } else {
            res.append(ch);
        }
        pre = ch;
    }
    return res.toString();
}

fun String.toFirstLower(): String {
    if (this.isEmpty()) {
        return this
    }
    return this.first().lowercaseChar() + this.substring(1)
}

fun String.toDate(pattern: String = "yyyy-MM-dd HH:mm:ss"): Date? {
    val simpleDateFormat = SimpleDateFormat(pattern, Locale.getDefault())
    return simpleDateFormat.parse(this)
}

fun main() {
    println("RemindItem".toFirstLower().toUnderCase())
}