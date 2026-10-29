package com.haseebflix.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.haseebflix.app.util.shareUrl

fun Context.openTrailer(key: String?) {
    if (key == null) { Toast.makeText(this, "No trailer available", Toast.LENGTH_SHORT).show(); return }
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { startActivity(app) } catch (e: ActivityNotFoundException) {
        try { startActivity(web) } catch (_: Exception) { Toast.makeText(this, "No app can play the trailer", Toast.LENGTH_SHORT).show() }
    }
}

fun Context.openUrl(url: String) {
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

fun Context.shareTitle(title: String, type: String, id: Int) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "$title — ${shareUrl(type, id)}")
    startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
