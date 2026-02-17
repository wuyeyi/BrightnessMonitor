package com.brightness.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null

    private lateinit var tvLux: TextView
    private lateinit var tvNit: TextView
    private lateinit var tvScreenBrightness: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvMaxLux: TextView

    // 估算的最大亮度（不同设备不同）
    private var maxEstimatedLux = 10000f
    private var thresholdNit = 40

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initViews()
        initSensors()
    }

    private fun initViews() {
        tvLux = findViewById(R.id.tvLux)
        tvNit = findViewById(R.id.tvNit)
        tvScreenBrightness = findViewById(R.id.tvScreenBrightness)
        tvStatus = findViewById(R.id.tvStatus)
        tvMaxLux = findViewById(R.id.tvMaxLux)
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // 获取光线传感器
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor != null) {
            tvStatus.text = "传感器正常"
            // 获取传感器的最大范围
            maxEstimatedLux = lightSensor.maximumRange
            tvMaxLux.text = "传感器最大范围: ${maxEstimatedLux.toInt()} lux"
        } else {
            tvStatus.text = "未找到光线传感器"
            tvLux.text = "--"
            tvNit.text = "--"
            Toast.makeText(this, "您的设备没有光线传感器", Toast.LENGTH_LONG).show()
        }

        // 获取接近传感器（用于判断是否遮挡）
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    override fun onResume() {
        super.onResume()
        // 注册传感器监听
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                tvLux.text = "${lux.toInt()} lux"

                // 将勒克斯转换为尼特（估算）
                // 注意：这是一个粗略估算，真实尼特需要知道屏幕亮度
                val estimatedNit = estimateNit(lux)
                tvNit.text = "约 $estimatedNit nit"

                // 检查是否超过阈值
                checkThreshold(estimatedNit)

                // 显示当前屏幕亮度百分比
                showScreenBrightness()
            }
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 0f
                if (distance < maxRange) {
                    // 接近传感器被遮挡
                    tvStatus.text = "检测到遮挡"
                } else {
                    tvStatus.text = "传感器正常"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 传感器精度变化
    }

    // 估算尼特值
    // 这是一个近似计算，假设：
    // - 环境光传感器在屏幕附近
    // - 屏幕最大亮度约为 500-1000 nit
    // - lux 和 nit 之间的关系取决于具体设备
    private fun estimateNit(lux: Float): Int {
        // 典型转换：nit ≈ lux * 屏幕亮度百分比 * 系数
        // 这是一个非常粗略的估算

        val screenBrightness = getScreenBrightness()
        val brightnessPercent = screenBrightness / 255f

        // 假设屏幕最大亮度约为 600 nit
        val maxScreenNit = 600f

        return (lux * brightnessPercent * (maxScreenNit / maxEstimatedLux)).toInt()
    }

    // 获取当前屏幕亮度 (0-255)
    private fun getScreenBrightness(): Int {
        return try {
            val resolver = contentResolver
            android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            // 如果无法获取，尝试其他方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = windowManager.defaultDisplay
                val params = window.attributes
                (params.screenBrightness * 255).toInt()
            } else {
                128 // 默认中等亮度
            }
        }
    }

    // 显示屏幕亮度
    private fun showScreenBrightness() {
        val brightness = getScreenBrightness()
        val percent = (brightness * 100 / 255)
        tvScreenBrightness.text = "$percent% (${brightness}/255)"
    }

    // 检查是否超过阈值
    private fun checkThreshold(nit: Int) {
        if (nit > thresholdNit) {
            tvStatus.text = "⚠️ 过亮: ${nit}nit > ${thresholdNit}nit"
            tvStatus.setTextColor(getColor(android.R.color.holo_red_light))
        } else {
            tvStatus.text = "✅ 亮度正常"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_light))
        }
    }
}
