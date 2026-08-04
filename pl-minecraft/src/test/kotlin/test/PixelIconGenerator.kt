/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/4
 */


package test

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun main() {
    val outputDir = File("src/main/resources/assets/peerlink/textures/gui")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    val patterns = getPatterns()
    val palettes = getColorPalettes()

    patterns.forEach { (type, pattern) ->
        val palette = palettes[type]!!
        val image = BufferedImage(10, 8, BufferedImage.TYPE_INT_ARGB)

        for (row in 0 until 8) {
            for (col in 0 until 10) {
                val char = pattern[row][col]
                if (char == ' ') {
                    image.setRGB(col, row, 0x00000000)
                    continue
                }

                val color = when (char) {
                    'B' -> 0xFF111827.toInt()
                    'L' -> palette.highlight
                    'M' -> palette.main
                    'D' -> palette.shadow
                    else -> palette.main
                }
                image.setRGB(col, row, color)
            }
        }

        val outputFile = File(outputDir, "${type.lowercase()}.png")
        ImageIO.write(image, "PNG", outputFile)
        println("Saved: ${outputFile.absolutePath}")
    }
}

private data class ColorPalette(val highlight: Int, val main: Int, val shadow: Int)

private fun getPatterns(): Map<String, Array<String>> = mapOf(
    "HOST" to arrayOf(
        "   BBBB   ",
        "  BLLMB   ",
        " BLLLMMMB ",
        "  BMMMMB  ",
        "   BMMB   ",
        "  BMMB    ",
        " BMMB     ",
        "  BB      "
    ),
    "SRFLX" to arrayOf(
        " B        ",
        "BMB   BBB ",
        "BMMMB BMB ",
        " BMB  BMB ",
        " B    BBB ",
        " BBB  B   ",
        " BMB BMMMB",
        " BBB  BMB "
    ),
    "PRFLX" to arrayOf(
        " BBBBBBBB ",
        " BLLLLMMB ",
        " BLBMMMBB ",
        " BLMMMMMB ",
        " BBBMMMBB ",
        "  BMMMMMB ",
        "  BMMMBB  ",
        "   BBBB   "
    ),
    "RELAY" to arrayOf(
        "   BBBB   ",
        "  BLLMMB  ",
        " BLLMMMMB ",
        "BLLMMMMMMB",
        "BMMMMMMMMB",
        " BDDDDDDB ",
        " BMB  BMB ",
        " BBB  BBB "
    ),
    "UNKNOWN" to arrayOf(
        "  BBBBBB  ",
        " BLLLLMMB ",
        " BBB  BMB ",
        "     BMMB ",
        "    BMMB  ",
        "    BMMB  ",
        "          ",
        "    BBBB  "
    )
)

private fun getColorPalettes(): Map<String, ColorPalette> = mapOf(
    "HOST" to ColorPalette(0xFF6EE7B7.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt()),
    "SRFLX" to ColorPalette(0xFF86EFAC.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt()),
    "PRFLX" to ColorPalette(0xFFFDE047.toInt(), 0xFFEAB308.toInt(), 0xFFA16207.toInt()),
    "RELAY" to ColorPalette(0xFFFCA5A5.toInt(), 0xFFEF4444.toInt(), 0xFFB91C1C.toInt()),
    "UNKNOWN" to ColorPalette(0xFFD1D5DB.toInt(), 0xFF9CA3AF.toInt(), 0xFF4B5563.toInt())
)