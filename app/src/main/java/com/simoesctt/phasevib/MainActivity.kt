package com.simoesctt.phasevib

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyro: Sensor? = null
    private var accel: Sensor? = null

    private lateinit var plotView: LivePlotView
    private lateinit var txtSensor: TextView
    private lateinit var txtCpuTime: TextView
    private lateinit var txtCpuResult: TextView
    private lateinit var txtPhaseTime: TextView
    private lateinit var txtPhaseResult: TextView
    private lateinit var txtOrder: TextView
    private lateinit var txtOrderLabel: TextView

    private var running = false

    // Rolling buffer — keep last 512 samples per channel
    private val bufSize = 512
    private val gyroX = ArrayDeque<Double>(bufSize)
    private val gyroY = ArrayDeque<Double>(bufSize)
    private val gyroZ = ArrayDeque<Double>(bufSize)
    private val accelX = ArrayDeque<Double>(bufSize)
    private val accelY = ArrayDeque<Double>(bufSize)
    private val accelZ = ArrayDeque<Double>(bufSize)

    // Sample counter and analysis window
    private var sampleCount = 0
    private val analysisEvery = 128       // run phase pipeline every 128 samples
    private var analysisRunning = false
    private var cpuTimeNs = 0L
    private var phaseTimeNs = 0L
    private var lastKuramoto = 0.0

    private val push: (ArrayDeque<Double>, Double) -> Unit = { deque, v ->
        if (deque.size >= bufSize) deque.removeFirst()
        deque.addLast(v)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        plotView = findViewById(R.id.plotView)
        txtSensor = findViewById(R.id.txtSensor)
        txtCpuTime = findViewById(R.id.txtCpuTime)
        txtCpuResult = findViewById(R.id.txtCpuResult)
        txtPhaseTime = findViewById(R.id.txtPhaseTime)
        txtPhaseResult = findViewById(R.id.txtPhaseResult)
        txtOrder = findViewById(R.id.txtOrder)
        txtOrderLabel = findViewById(R.id.txtOrderLabel)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        findViewById<Button>(R.id.btnStart).setOnClickListener { start() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stop() }
        findViewById<Button>(R.id.btnExport).setOnClickListener { export() }

        txtSensor.text = "Gyro: ${gyro?.name ?: "not available"}\n" +
                "Accel: ${accel?.name ?: "not available"}"
    }

    private fun start() {
        running = true
        gyroX.clear(); gyroY.clear(); gyroZ.clear()
        accelX.clear(); accelY.clear(); accelZ.clear()
        sampleCount = 0
        cpuTimeNs = 0L; phaseTimeNs = 0L
        plotView.clear()

        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        findViewById<Button>(R.id.btnStart).isEnabled = false
        findViewById<Button>(R.id.btnStop).isEnabled = true
        findViewById<Button>(R.id.btnExport).isEnabled = false
    }

    private fun stop() {
        running = false
        sensorManager.unregisterListener(this)

        findViewById<Button>(R.id.btnStart).isEnabled = true
        findViewById<Button>(R.id.btnStop).isEnabled = false

        // Continuous analysis runs during streaming; nothing to do on stop
        if (gyroX.size >= 256) {
            findViewById<Button>(R.id.btnExport).isEnabled = true
        }
    }

    private fun analyzeLive() {
        if (analysisRunning) return
        if (gyroX.size < bufSize) return
        analysisRunning = true

        // Snapshot the current rolling buffer
        val gx = gyroX.toDoubleArray()
        val gy = gyroY.toDoubleArray()
        val gz = gyroZ.toDoubleArray()

        CoroutineScope(Dispatchers.Default).launch {
            val channels = arrayOf(gx, gy, gz)
            val (cpuResult, cpuTime) = PhaseEngine.cpuPipeline(channels, 200.0)
            val phaseResult = PhaseEngine.phasePipeline(channels)

            withContext(Dispatchers.Main) {
                cpuTimeNs = cpuTime
                phaseTimeNs = phaseResult.phaseTimeNs
                lastKuramoto = phaseResult.kuramotoR

                txtCpuTime.text = "Time: ${"%.2f".format(cpuTime / 1_000_000.0)} ms"
                txtCpuResult.text = "Peak magnitude: ${"%.4f".format(cpuResult)}"
                txtPhaseTime.text = "Time: ${"%.2f".format(phaseResult.phaseTimeNs / 1_000_000.0)} ms"
                txtPhaseResult.text = "Mean coherence: ${"%.4f".format(phaseResult.meanCoherence)}"

                txtOrder.text = "%.3f".format(phaseResult.kuramotoR)
                txtOrderLabel.text = when {
                    phaseResult.kuramotoR > 0.7 -> "Strong synchrony"
                    phaseResult.kuramotoR > 0.4 -> "Moderate synchrony"
                    phaseResult.kuramotoR > 0.2 -> "Weak synchrony"
                    else -> "No synchrony"
                }
                analysisRunning = false
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                push(gyroX, event.values[0].toDouble())
                push(gyroY, event.values[1].toDouble())
                push(gyroZ, event.values[2].toDouble())
                plotView.push(event.values[0], event.values[1], event.values[2])
                sampleCount++
                if (sampleCount % analysisEvery == 0) {
                    analyzeLive()
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                push(accelX, event.values[0].toDouble())
                push(accelY, event.values[1].toDouble())
                push(accelZ, event.values[2].toDouble())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun export() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(cacheDir, "phasevib_$stamp.csv")
        try {
            FileWriter(file).use { w ->
                w.write("# PhaseVib export\n")
                w.write("# timestamp_ms,gyroX,gyroY,gyroZ,accelX,accelY,accelZ\n")
                val gx = gyroX.toList(); val gy = gyroY.toList(); val gz = gyroZ.toList()
                val ax = accelX.toList(); val ay = accelY.toList(); val az = accelZ.toList()
                val n = minOf(gx.size, ax.size)
                val t0 = System.currentTimeMillis()
                for (i in 0 until n) {
                    w.write("${t0 + i * 5},")
                    w.write("${gx[i]},${gy[i]},${gz[i]},")
                    w.write("${ax[i]},${ay[i]},${az[i]}\n")
                }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "PhaseVib export")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share CSV"))
        } catch (e: Exception) {
            android.util.Log.e("PhaseVib", "Export failed", e)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
