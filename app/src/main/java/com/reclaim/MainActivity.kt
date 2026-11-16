package com.reclaim

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.enableButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = ServiceStatus.isEnabled(this)
        findViewById<TextView>(R.id.statusText).setText(
            if (enabled) R.string.status_on else R.string.status_off,
        )
    }
}

object ServiceStatus {
    fun isEnabled(activity: Activity): Boolean {
        val expected = ComponentName(activity, ReclaimAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            activity.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { flattened ->
            val component = ComponentName.unflattenFromString(flattened) ?: return@any false
            component == expected || component.className == expected.className
        }
    }
}
