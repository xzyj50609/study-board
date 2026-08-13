package com.zyj.ritual.domain.vocab

/**
 * 一条打卡记录（对应 JS 的 records 数组元素）。
 *
 * 同一天可以有多条（同日多次点击不去重，测试用例 8）。
 * 所有字段都是值类型，records 数组本身是纯函数的输入。
 */
data class VocabRecord(
    /** "YYYY-MM-DD"，固定 UTC+8 */
    val date: String,
    /** 这次背/复习了多少个词 */
    val words: Int,
    /** "new" = 新词，"backlog" = 复习/清积压 */
    val kind: String = "new",
    /**
     * 记录写入时刻（epoch 毫秒）。0 = 没有时刻，只知道是哪天。
     *
     * 为什么会有 0：2026-08-08 之前的记录、以及从备份 JSON 导入的记录都没带时刻。
     * 历史页遇到 0 只渲染日期、不编一个时刻出来（编出来会显示 1970 或者混进正常记录里看不出来）。
     * computeState / computeCalendar 完全不读这个字段——它只服务历史页排序。
     */
    val createdAt: Long = 0,
)
