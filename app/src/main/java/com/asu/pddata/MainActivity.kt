package com.asu.pddata

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import com.asu.pddata.constants.Constants
import com.asu.pddata.databinding.ActivityMainBinding
import com.asu.pddata.service.ForegroundService

class MainActivity : Activity() {


    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        createNotificationChannel()
        binding.button.setOnClickListener {
            val intent = Intent(this, ForegroundService::class.java)
            intent.action = "com.asu.pddata.ADD_MEDICATION_TIMESTAMP"
            startService(intent)
        }
        val serviceIntent = Intent(this, ForegroundService::class.java)
        startService(serviceIntent)
    }


    private fun createNotificationChannel() {


        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = Constants.NOTIFICATION_CHANNEL_DESCRIPTION
        }


        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
