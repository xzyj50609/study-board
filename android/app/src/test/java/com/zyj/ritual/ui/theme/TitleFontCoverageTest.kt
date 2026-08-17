package com.zyj.ritual.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标题字体是裁过的（只含工程里出现过的字，见 `tools/subset_title_font.py`）。
 * 裁字体省下了 17MB，但换来一个很阴的故障模式：
 *
 * **改了界面文案、新字没裁进字体，手机上那几个字直接显示成空白。**
 *
 * 这个故障在电脑上完全看不出来——JVM 单测不碰字体，截图测试用的是
 * 渲染器自己的字体回退，都是绿的；只有用户装到手机上才看得见。
 * 正是「故障伪装成正常」的典型，所以必须有一道能自动跑的关卡。
 *
 * 这条测试做的事：把工程里所有字符串字面量里的字捞出来，
 * 逐个查子集字体的 cmap 表，缺一个就红，并告诉你缺的是哪几个字、怎么修。
 *
 * 修法永远是同一句：在 `android/` 下跑 `python tools/subset_title_font.py`。
 */
class TitleFontCoverageTest {

    companion object {
        /**
         * 这几个字符任何中文宋体里都没有（emoji、几何符号），系统会自动回退到别的字体去画，
         * 屏幕上照样显示得出来——这是正常回退，不是缺字。
         *
         * ⚠️ 往这里加字符要非常克制：加一个就等于放弃对它的保护。
         * 只有「这个字形本来就不该由中文字体提供」才能进，
         * 汉字缺失一律不许往这里塞，那是真故障。
         */
        private val SYSTEM_FALLBACK_OK = setOf(
            '▾',            // 下拉小箭头，几何符号区
            '\uD83C',       // 🎉 的高位代理
            '\uDF89',       // 🎉 的低位代理
        )
    }

    @Test
    fun `标题字体覆盖工程里所有文案用字`() {
        val androidDir = locateAndroidDir()
        val fontFile = File(androidDir, "app/src/main/res/font/title_serif.ttf")

        assertTrue(
            "找不到 ${fontFile.path}。在 android/ 下跑：python tools/subset_title_font.py",
            fontFile.isFile,
        )

        val covered = readCmapCodePoints(fontFile)
        val wanted = collectLiteralChars(File(androidDir, "app/src/main/java"))

        val missing = wanted
            .filterNot { it in SYSTEM_FALLBACK_OK }
            .filter { it.code !in covered }
            .sorted()

        assertTrue(
            buildString {
                append("标题字体里缺 ${missing.size} 个字：")
                append(missing.joinToString(""))
                append("\n改过界面文案就要重裁字体，否则手机上这些字会显示成空白。")
                append("\n修法：在 android/ 目录下跑  python tools/subset_title_font.py")
            },
            missing.isEmpty(),
        )
    }

    /** 从工程根往上找到 android/ 目录，避免依赖测试的工作目录到底是哪一级 */
    private fun locateAndroidDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/font").isDirectory) return dir
            dir = dir.parentFile
        }
        error("没找到 android/ 目录（从 ${System.getProperty("user.dir")} 往上找的）")
    }

    /**
     * 只读 TTF 的 cmap 表，拿到字体实际支持哪些码点。
     * 不引第三方字体库——为了一条测试给工程加个依赖不划算，
     * 而 cmap 格式 4 / 格式 12 的解析拢共几十行。
     */
    private fun readCmapCodePoints(file: File): Set<Int> {
        val bytes = file.readBytes()
        fun u8(at: Int) = bytes[at].toInt() and 0xFF
        fun u16(at: Int) = (u8(at) shl 8) or u8(at + 1)
        fun u32(at: Int) = (u16(at).toLong() shl 16 or u16(at + 2).toLong()).toInt()

        val numTables = u16(4)
        var cmapOffset = -1
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            val tag = String(bytes, rec, 4, Charsets.US_ASCII)
            if (tag == "cmap") {
                cmapOffset = u32(rec + 8)
                break
            }
        }
        check(cmapOffset > 0) { "字体里没有 cmap 表：${file.path}" }

        // 挑一个 Unicode 子表：优先 (3,10) 全 Unicode，其次 (3,1) BMP
        val numSubtables = u16(cmapOffset + 2)
        var best = -1
        var bestScore = -1
        for (i in 0 until numSubtables) {
            val rec = cmapOffset + 4 + i * 8
            val platform = u16(rec)
            val encoding = u16(rec + 2)
            val offset = cmapOffset + u32(rec + 4)
            val score = when {
                platform == 3 && encoding == 10 -> 3
                platform == 0 -> 2
                platform == 3 && encoding == 1 -> 1
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = offset
            }
        }
        check(best > 0) { "cmap 里没有可用的 Unicode 子表" }

        val codePoints = mutableSetOf<Int>()
        when (val format = u16(best)) {
            4 -> {
                val segCountX2 = u16(best + 6)
                val segCount = segCountX2 / 2
                val endBase = best + 14
                val startBase = endBase + segCountX2 + 2
                val deltaBase = startBase + segCountX2
                val rangeBase = deltaBase + segCountX2
                for (seg in 0 until segCount) {
                    val end = u16(endBase + seg * 2)
                    val start = u16(startBase + seg * 2)
                    if (start > end) continue
                    val delta = u16(deltaBase + seg * 2)
                    val rangeOffset = u16(rangeBase + seg * 2)
                    for (c in start..end) {
                        if (c == 0xFFFF) continue
                        val glyph = if (rangeOffset == 0) {
                            (c + delta) and 0xFFFF
                        } else {
                            val gi = rangeBase + seg * 2 + rangeOffset + (c - start) * 2
                            if (gi + 1 >= bytes.size) 0 else {
                                val g = u16(gi)
                                if (g == 0) 0 else (g + delta) and 0xFFFF
                            }
                        }
                        if (glyph != 0) codePoints.add(c)
                    }
                }
            }
            12 -> {
                val nGroups = u32(best + 12)
                for (g in 0 until nGroups) {
                    val rec = best + 16 + g * 12
                    val startChar = u32(rec)
                    val endChar = u32(rec + 4)
                    for (c in startChar..endChar) codePoints.add(c)
                }
            }
            else -> error("没处理过的 cmap 格式：$format")
        }
        return codePoints
    }

    /**
     * 捞出所有 Kotlin 字符串字面量里的字符。
     * 跟子集脚本用的是同一套规则——两边必须一致，否则关卡就形同虚设。
     */
    private fun collectLiteralChars(sourceRoot: File): Set<Char> {
        val literal = Regex(""""([^"\\\n]*(?:\\.[^"\\\n]*)*)"""")
        val chars = mutableSetOf<Char>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                literal.findAll(file.readText()).forEach { match ->
                    chars.addAll(match.groupValues[1].toList())
                }
            }
        check(chars.isNotEmpty()) { "没在 ${sourceRoot.path} 下扫到任何字符串" }
        // 只关心会被标题渲染的可见字符；空白和控制字符没有字形，也不该要求字体带
        return chars.filterNot { it.isWhitespace() || it.isISOControl() }.toSet()
    }
}
