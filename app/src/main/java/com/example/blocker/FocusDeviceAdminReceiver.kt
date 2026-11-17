package com.example.blocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        try {
            Toast.makeText(
                context,
                "✅ تم تفعيل مشرف الجهاز لحماية التطبيق من الحذف أثناء التركيز",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "⚠️ تحذير: إلغاء مشرف الجهاز سيسمح بحذف التطبيق وكسر الحماية الصارمة."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        try {
            Toast.makeText(
                context,
                "⚠️ تم إلغاء صلاحية مشرف الجهاز للحظر الصارم",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
        }
    }
}
