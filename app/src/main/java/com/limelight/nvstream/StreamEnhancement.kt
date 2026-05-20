package com.limelight.nvstream

import java.net.URLEncoder

/**
 * Sunshine extension parameters adapted from the protocol usage in
 * qiin2333/moonlight-vplus:
 * - app/src/main/java/com/limelight/nvstream/StreamConfiguration.kt
 * - app/src/main/java/com/limelight/nvstream/http/NvHTTP.kt
 *
 * This file keeps the local implementation small and source-attributed while
 * avoiding a direct copy of the upstream Kotlin code.
 */
object StreamEnhancement {
    @JvmStatic
    fun appendLaunchQuery(baseQuery: String, context: ConnectionContext): String {
        val config = context.streamConfig
        if (context.isNvidiaServerSoftware) {
            return baseQuery
        }

        // 如果用户根本没有开启任何 Sunshine 扩展参数，保持 100% 原始 baseQuery，零侵入保障常规串流绝对安全与兼容性
        if (!hasActiveSunshineExtension(context)) {
            return baseQuery
        }

        val query = StringBuilder(baseQuery)
        if (config.resolutionScale != 100) {
            query.append("&resolutionScale=").append(config.resolutionScale)
        }
        if (config.useVdd) {
            query.append("&useVdd=1")
        } else {
            query.append("&useVdd=0")
        }

        if (config.customScreenMode >= 0) {
            query.append("&customScreenMode=").append(config.customScreenMode)
        }
        if (config.customVddScreenMode >= 0) {
            query.append("&customVddScreenMode=").append(config.customVddScreenMode)
        }
        context.displayName?.takeIf { it.isNotBlank() }?.let {
            query.append("&display_name=").append(URLEncoder.encode(it, "UTF-8"))
        }
        return query.toString()
    }

    @JvmStatic
    fun hasActiveSunshineExtension(context: ConnectionContext): Boolean {
        val config = context.streamConfig
        return config.resolutionScale != 100 ||
            config.useVdd ||
            config.customScreenMode >= 0 ||
            config.customVddScreenMode >= 0 ||
            !context.displayName.isNullOrBlank()
    }

    @JvmStatic
    fun requestedWidth(config: StreamConfiguration): Int =
        scale(config.width, config.hostResolutionScaleX100)

    @JvmStatic
    fun requestedHeight(config: StreamConfiguration): Int =
        scale(config.height, config.hostResolutionScaleX100)

    private fun scale(value: Int, scaleX100: Int): Int {
        val safeScale = scaleX100.coerceAtLeast(1)
        return (value * safeScale + 50) / 100
    }
}
