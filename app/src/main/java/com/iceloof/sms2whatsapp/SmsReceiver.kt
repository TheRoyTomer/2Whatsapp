package com.iceloof.sms2whatsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast

@Suppress("DEPRECATION")
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val sharedPref = context.getSharedPreferences("SMS2WhatsAppPreferences", Context.MODE_PRIVATE)
            val sendTo = sharedPref.getStringSet("phoneNumbers", null)

            val bundle: Bundle? = intent.extras
            Log.d("SmsReceiver", "From: $bundle tO:$sendTo")
            if (bundle != null && sendTo != null) {
                @Suppress("SpellCheckingInspection")
                val pdus = bundle["pdus"] as Array<*>
                val messages = pdus.map { pdu -> SmsMessage.createFromPdu(pdu as ByteArray) }
                val message = messages.joinToString(separator = "") { it.messageBody }
                val sender = messages.first().originatingAddress

                // Wake up the device
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SMS2WhatsApp::MyWakelockTag_${System.currentTimeMillis()}"
                )
                wakeLock.acquire(120000) // 2 דקות

                Log.d("SmsReceiver", "From: $sender, Message: $message")
                forwardMessageViaWhatsApp(context, sendTo, sender, message, wakeLock)
            }
        }
    }

    private fun forwardMessageViaWhatsApp(context: Context, sendTo: Set<String>?, sender: String?, message: String, wakeLock: PowerManager.WakeLock) {
        if (sendTo.isNullOrEmpty()) return

        val targets = sendTo.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (targets.isEmpty()) return

        val failedNumbers = StringBuilder()
        val safeSender = if (sender != null) sender else "Unknown"
        val text = message

        targets.forEachIndexed { index, number ->
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val sendIntent = Intent(Intent.ACTION_VIEW)
                sendIntent.setPackage("com.whatsapp")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone="
                        + number
                        + "&text="
                        + Uri.encode(text)
                )
                sendIntent.data = uri
                sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                try {
                    context.startActivity(sendIntent)
                } catch (ex: android.content.ActivityNotFoundException) {
                    if (failedNumbers.isNotEmpty()) failedNumbers.append(";")
                    failedNumbers.append(number)
                }
            }, (index * 4500).toLong())
        }

        val totalTargets = targets.size + 1L
        val totalTime = totalTargets * 4500L

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val componentName = android.content.ComponentName(context, MyDeviceAdminReceiver::class.java)
            if (devicePolicyManager.isAdminActive(componentName)) {
                devicePolicyManager.lockNow()
            }

            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }, totalTime)

        if (failedNumbers.isNotEmpty()) {
            val summary = failedNumbers.toString() + "\nWhatsApp not installed"
            Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
        }
    }
}


/*package com.iceloof.sms2whatsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast

@Suppress("DEPRECATION")
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private var wakeLock: PowerManager.WakeLock? = null
        private var activeProcesses = 0

        @Synchronized
        fun acquireWakeLock(context: Context) {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SMS2WhatsApp::SharedWakeLock"
                )
            }

            if (activeProcesses == 0) {
                wakeLock?.acquire()
            }
            activeProcesses++
        }

        @Synchronized
        fun releaseWakeLock() {
            activeProcesses--
            if (activeProcesses <= 0) {
                activeProcesses = 0
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val sharedPref = context.getSharedPreferences("SMS2WhatsAppPreferences", Context.MODE_PRIVATE)
            val sendTo = sharedPref.getStringSet("phoneNumbers", null)

            val bundle: Bundle? = intent.extras
            Log.d("SmsReceiver", "From: $bundle tO:$sendTo")
            if (bundle != null && sendTo != null) {
                @Suppress("SpellCheckingInspection")
                val pdus = bundle["pdus"] as Array<*>
                val messages = pdus.map { pdu -> SmsMessage.createFromPdu(pdu as ByteArray) }
                val message = messages.joinToString(separator = "") { it.messageBody }
                val sender = messages.first().originatingAddress

                acquireWakeLock(context)

                Log.d("SmsReceiver", "From: $sender, Message: $message")
                forwardMessageViaWhatsApp(context, sendTo, sender, message)
            }
        }
    }

    private fun forwardMessageViaWhatsApp(context: Context, sendTo: Set<String>?, sender: String?, message: String) {
        if (sendTo.isNullOrEmpty()) {
            releaseWakeLock()
            return
        }

        val targets = sendTo.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (targets.isEmpty()) {
            releaseWakeLock()
            return
        }

        val failedNumbers = StringBuilder()
        val safeSender = if (sender != null) sender else "Unknown"
        val text = message

        targets.forEachIndexed { index, number ->
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val sendIntent = Intent(Intent.ACTION_VIEW)
                sendIntent.setPackage("com.whatsapp")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone="
                        + number
                        + "&text="
                        + Uri.encode(text)
                )
                sendIntent.data = uri
                sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                try {
                    context.startActivity(sendIntent)
                } catch (ex: android.content.ActivityNotFoundException) {
                    if (failedNumbers.isNotEmpty()) failedNumbers.append(";")
                    failedNumbers.append(number)
                }
            }, (index * 4500).toLong())
        }

        val totalTargets = targets.size + 1L
        val totalTime = totalTargets * 4500L

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val componentName = android.content.ComponentName(context, MyDeviceAdminReceiver::class.java)
            if (devicePolicyManager.isAdminActive(componentName)) {
                devicePolicyManager.lockNow()
            }
            releaseWakeLock()
        }, totalTime)

        if (failedNumbers.isNotEmpty()) {
            val summary = failedNumbers.toString() + "\nWhatsApp not installed"
            Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
        }
    }
}*/
