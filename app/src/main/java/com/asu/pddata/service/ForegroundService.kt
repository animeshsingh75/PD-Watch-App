package com.asu.pddata.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.asu.pddata.constants.Constants
import com.asu.pddata.model.DataCollectionModel
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.util.Locale

class ForegroundService : Service(), SensorEventListener {


    private var isServiceRunning = false
    private var mSensorManager: SensorManager? = null
    private var mAccSensor: Sensor? = null
    private var mGyroSensor: Sensor? = null
    private var mHeartRateSensor: Sensor? = null


    private var accXValue: Float = 0F
    private var accYValue: Float = 0F
    private var accZValue: Float = 0F
    private var angularSpeedX: Float = 0F
    private var angularSpeedY: Float = 0F
    private var angularSpeedZ: Float = 0F
    private var heartRate: Float = 0F

    lateinit var userId: String


    private val DATA_COLLECTION_INTERVAL = 1000 // 1 second
    private val ClOUD_SYNC_INTERVAL = 10000 // 10 second,


    private val headers: List<String> = listOf(
        "Timestamp", "AccX", "AccY", "AccZ", "AngularX",
        "AngularY", "AngularZ", "HeartRate", "Medication"
    )


    private val dataCollectionModel = mutableListOf<DataCollectionModel>()


    private val dataCollectionHandler = Handler()
    private val cloudSyncHandler = Handler()


    private var lastSynced = System.currentTimeMillis()


    override fun onBind(p0: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {


        if (!isServiceRunning) {
            isServiceRunning = true
            startForeground()
        }
        userId = getSharedPreferences("pddata", MODE_PRIVATE).getString("userId", "") ?: ""
        intent?.action?.let {
            if (it == "com.asu.pddata.ADD_MEDICATION_TIMESTAMP") {
                addMedicationTimestamp()
            }
        }
        // Service will be restarted if killed by the system
        return START_STICKY
    }


    private fun addMedicationTimestamp() {
        dataCollectionModel.add(
            DataCollectionModel(
                System.currentTimeMillis().toString(),
                accXValue,
                accYValue,
                accZValue,
                angularSpeedX,
                angularSpeedY,
                angularSpeedZ,
                heartRate,
                1
            )
        )
    }


    private fun startForeground() {
        val notification = Notification.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Service")
            .setContentText("Collecting data")
            .build()


        startForeground(1, notification)
    }


    override fun onCreate() {
        super.onCreate()


        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mAccSensor = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroSensor = mSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mHeartRateSensor = mSensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)


        mSensorManager?.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager?.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager?.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)


        startDataCollection()
    }


    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stopDataCollection()
        mSensorManager?.unregisterListener(this)
    }


    override fun onSensorChanged(event: SensorEvent) {
        // If sensor is unreliable, then just return
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return
        }
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accXValue = event.values[0]
                accYValue = event.values[1]
                accZValue = event.values[2]
            }


            Sensor.TYPE_GYROSCOPE -> {
                angularSpeedX = event.values[0]
                angularSpeedY = event.values[1]
                angularSpeedZ = event.values[2]
            }


            Sensor.TYPE_HEART_RATE -> {
                heartRate = event.values[0]
            }
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //do something
    }


    private fun saveDataToCSV(
        headers: List<String>,
        data: List<DataCollectionModel>,
        fileName: String
    ): Boolean {
        if (isExternalStorageWritable()) {
            val csvFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                fileName
            )


            try {
                Log.v("Cloud", "Saving file to $fileName")
                val fileWriter = FileWriter(csvFile)


                fileWriter.append(headers.joinToString(","))
                fileWriter.append("\n")


                if (data.isNotEmpty()) {
                    for (dataCollectionModel in data) {
                        val row = listOf(
                            dataCollectionModel.timestamp,
                            String.format(Locale.US, "%.2f", dataCollectionModel.accX),
                            String.format(Locale.US, "%.2f", dataCollectionModel.accY),
                            String.format(Locale.US, "%.2f", dataCollectionModel.accZ),
                            String.format(
                                Locale.US,
                                "%.2f",
                                dataCollectionModel.angularX
                            ),
                            String.format(
                                Locale.US,
                                "%.2f",
                                dataCollectionModel.angularY
                            ),
                            String.format(
                                Locale.US,
                                "%.2f",
                                dataCollectionModel.angularZ
                            ),
                            String.format(
                                Locale.US,
                                "%.2f",
                                dataCollectionModel.heartRate
                            ),
                            dataCollectionModel.medication.toString()
                        )
                        fileWriter.append(row.joinToString(","))
                        fileWriter.append("\n")
                    }
                } else {
                    Log.i("data", "List is empty")
                }


                fileWriter.close()
            } catch (e: IOException) {
                e.printStackTrace()
                return false
            }
            return true
        }
        return false
    }


    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }


    private val dataCollectionRunnable = object : Runnable {
        override fun run() {
            collectData()


            dataCollectionHandler.postDelayed(this, DATA_COLLECTION_INTERVAL.toLong())
        }
    }


    private val cloudSyncRunnable = object : Runnable {
        override fun run() {
            val currentSync = System.currentTimeMillis()
            if (saveDataToCSV(
                    headers,
                    dataCollectionModel,
                    "data-$userId-$lastSynced-$currentSync"
                )
            ) {
                dataCollectionModel.clear()
                lastSynced = currentSync
            }
            cloudSyncHandler.postDelayed(this, ClOUD_SYNC_INTERVAL.toLong())
        }
    }


    private fun startDataCollection() {
        dataCollectionHandler.post(dataCollectionRunnable)
        cloudSyncHandler.post(cloudSyncRunnable)
    }


    private fun stopDataCollection() {
        dataCollectionHandler.removeCallbacks(dataCollectionRunnable)
        cloudSyncHandler.removeCallbacks(cloudSyncRunnable)
    }


    fun collectData() {
        dataCollectionModel.add(
            DataCollectionModel(
                System.currentTimeMillis().toString(),
                accXValue,
                accYValue,
                accZValue,
                angularSpeedX,
                angularSpeedY,
                angularSpeedZ,
                heartRate,
                0
            )
        )
    }


}
