package com.lollipop.mediaflow.view


import android.content.Context
import kotlin.math.acos
import kotlin.math.sqrt


val Context.isScreenRound: Boolean
    get() {
        return resources.configuration.isScreenRound
    }

object DeviceHelper {
    fun arcDimenToAngle(radius: Float, arcDimen: Float): Float {
        // 角度计算长度：2 * PI * radius * (angle / 360)
        // 反过来，通过长度尺寸换算角度
        return (arcDimen / (2 * Math.PI * radius) * 360).toFloat()
    }

    fun densityDpi(context: Context): Int {
        return context.resources.displayMetrics.densityDpi
    }

    fun isPhone(context: Context): Boolean {
        val dpi = densityDpi(context)
        return dpi > 320
    }

    fun calculateCentralAngle(
        ox: Float, oy: Float,  // 圆心坐标
        x1: Float, y1: Float,  // 点A坐标
        x2: Float, y2: Float   // 点B坐标
    ): Float {
        // 计算两点相对于圆心的向量
        val v1x = (x1 - ox).toDouble()
        val v1y = (y1 - oy).toDouble()
        val v2x = (x2 - ox).toDouble()
        val v2y = (y2 - oy).toDouble()

        // 检查向量长度是否为0（点与圆心重合）
        if ((v1x == 0.0 && v1y == 0.0) || (v2x == 0.0 && v2y == 0.0)) {
            // throw IllegalArgumentException("Points cannot coincide with the center")
            return 0F
        }

        // 计算点积和向量模长
        val dotProduct = v1x * v2x + v1y * v2y
        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)

        // 处理浮点精度问题，确保cos值在有效范围内
        val cosTheta = (dotProduct / (mag1 * mag2)).coerceIn(-1.0, 1.0)

        // 计算弧度并转换为角度
        val degrees = Math.toDegrees(acos(cosTheta)).toFloat()

        // 通过叉积判断方向
        val crossProduct = v1x * v2y - v1y * v2x
        val directedDegrees = if (crossProduct < 0) {
            360F - degrees
        } else {
            degrees
        }
        return directedDegrees
    }

}