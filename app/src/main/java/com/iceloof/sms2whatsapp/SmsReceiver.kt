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
            //***R***
            val sendTo = sharedPref.getStringSet("phoneNumbers", null)
            //val sendTo = sharedPref.getString("phoneNumber", null)

            val bundle: Bundle? = intent.extras
            Log.d("SmsReceiver", "From: $bundle tO:$sendTo")
            if (bundle != null && sendTo != null) {
                @Suppress("SpellCheckingInspection") val pdus = bundle["pdus"] as Array<*>
                val messages = pdus.map { pdu -> SmsMessage.createFromPdu(pdu as ByteArray) }
                val message = messages.joinToString(separator = "") { it.messageBody }
                val sender = messages.first().originatingAddress

                // Wake up the device
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SMS2WhatsApp::MyWakelockTag"
                )
                wakeLock.acquire(15000) // Wake up the screen for 15 seconds
                // Log the message
                Log.d("SmsReceiver", "From: $sender, Message: $message")
                // Forward the message via WhatsApp
                forwardMessageViaWhatsApp(context, sendTo, sender, message)

            }
        }
    }
    ///***R***
    private fun forwardMessageViaWhatsApp(context: Context, sendTo:  Set<String>?, sender: String?, message: String) {
        if (sendTo.isNullOrEmpty()) return


        val failedNumbers = StringBuilder()

        val safeSender = if (sender != null) sender else "Unknown"
        val text = "From: " + safeSender + "\nMsg: " + message

        val iterator = sendTo.iterator()
        while (iterator.hasNext()) {
            val number = iterator.next()

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
                if (failedNumbers.isNotEmpty()) failedNumbers.append("; ")
                failedNumbers.append(number)
            }
        }

        if (failedNumbers.isNotEmpty()) {
            val summary = failedNumbers.toString() + "\nWhatsApp not installed"
            Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
        }
    }

    /*private fun forwardMessageViaWhatsApp(context: Context, sendTo: String?, sender: String?, message: String) {
        val sendIntent = Intent(Intent.ACTION_VIEW)
        sendIntent.setPackage("com.whatsapp")
        sendIntent.data = Uri.parse("https://api.whatsapp.com/send?phone=$sendTo&text=${Uri.encode("From: $sender\nMsg: $message")}")
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Add this flag
        try {
            context.startActivity(sendIntent)
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
        }
    }*/
}