package com.limelight.heokami

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import org.json.JSONObject

class GameGridLines @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var numColumns = 0
    private var numRows = 0
    private var snapThreshold = 20 // 吸附阈值
    var opacity = 255 // 透明度
    var red = 255 // 红色分量
    var green = 0 // 绿色分量
    var blue = 0 // 蓝色分量
    private val paint = Paint().apply {
        color = Color.argb(opacity, red, green, blue)
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) { // 正确的重写
        super.onDraw(canvas)
        Log.d("GameGridLines", "onDraw called")
        // 内部进行 null 检查，非常重要！
        canvas.let {  // 使用 let 块安全地操作 canvas
            val width = width
            val height = height

            if (numColumns > 0 && numRows > 0) {
                for (i in 1 until numColumns) {
                    val x = width * i / numColumns
                    it.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
                }

                for (i in 1 until numRows) {
                    val y = height * i / numRows
                    it.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
                }
            }
        } // let 块结束
    }
    fun setGridOpacity(opacity: Int) {
        if (opacity in 0..255) {
            this.opacity = opacity
            paint.color = Color.argb(opacity, red, green, blue)
            invalidate()
        }
    }

    fun setGridRGB(red: Int, green: Int, blue: Int) {
        if (red in 0..255 && green in 0..255 && blue in 0..255) {
            this.red = red
            this.green = green
            this.blue = blue
            paint.color = Color.argb(opacity, red, green, blue)
            invalidate()
        }
    }

    fun hide(){
        visibility = GONE
    }

    fun show(){
        visibility = VISIBLE
    }

    fun setGridSize(columns: Int, rows: Int) {
        Log.d("GameGridLines", "setGridSize called with columns: $columns, rows: $rows")
        numColumns = columns
        numRows = rows
        invalidate()
    }

    fun setSnapThreshold(snapThreshold: Int){
        this.snapThreshold = snapThreshold
    }

    fun getSnapThreshold(): Int{
        return snapThreshold
    }

    fun getRowCount(): Int {
        return numRows
    }

    fun getColumnCount(): Int {
        return numColumns
    }

    fun getCellWidth(): Int {
        return if (numColumns > 0) width / numColumns else 0
    }

    fun getCellHeight(): Int {
        return if (numRows > 0) height / numRows else 0
    }

    fun getConfig():JSONObject {
        val config = JSONObject()
        config.put("numColumns", numColumns)
        config.put("snapThreshold", snapThreshold)
        config.put("numRows", numRows)
        config.put("opacity", opacity)
        config.put("red", red)
        config.put("green", green)
        config.put("blue", blue)
        return config
    }

    fun setConfig(config: JSONObject){
        numColumns = config.getInt("numColumns")
        snapThreshold = config.getInt("snapThreshold")
        numRows = config.getInt("numRows")
        opacity = config.getInt("opacity")
        red = config.getInt("red")
        green = config.getInt("green")
        blue = config.getInt("blue")
        paint.color = Color.argb(opacity, red, green, blue)
        invalidate()
    }
}