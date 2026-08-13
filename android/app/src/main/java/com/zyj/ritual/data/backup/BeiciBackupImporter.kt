package com.zyj.ritual.data.backup

import com.zyj.ritual.domain.vocab.RateChange
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析并转换背词指南灯的 JSON 备份格式（支持 v1 和 v2 Schema）。
 */
object BeiciBackupImporter {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseBeiciJson(rawJson: String): Pair<VocabConfig, List<VocabRecord>> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1

        if (version == 1) {
            return parseV1(root)
        }
        return parseV2(root)
    }

    private fun parseV2(root: JsonObject): Pair<VocabConfig, List<VocabRecord>> {
        val settingsObj = root["settings"]?.jsonObject
        val recordsArr = root["records"]?.jsonArray ?: emptyList()

        val defaults = VocabConfig()

        val rateChanges = settingsObj?.get("rateChanges")?.jsonArray?.mapNotNull { el ->
            runCatching {
                val obj = el.jsonObject
                val from = obj["from"]?.jsonPrimitive?.content ?: return@runCatching null
                val dailyWords = obj["dailyWords"]?.jsonPrimitive?.int ?: return@runCatching null
                RateChange(from, dailyWords)
            }.getOrNull()
        } ?: defaults.rateChanges

        val config = VocabConfig(
            bookName = settingsObj?.get("bookName")?.jsonPrimitive?.content ?: defaults.bookName,
            startDate = settingsObj?.get("startDate")?.jsonPrimitive?.content ?: defaults.startDate,
            totalWords = settingsObj?.get("totalWords")?.jsonPrimitive?.intOrNull ?: defaults.totalWords,
            initialDone = settingsObj?.get("initialDone")?.jsonPrimitive?.intOrNull ?: defaults.initialDone,
            dailyWords = settingsObj?.get("dailyWords")?.jsonPrimitive?.intOrNull ?: defaults.dailyWords,
            examDate = settingsObj?.get("examDate")?.jsonPrimitive?.content ?: defaults.examDate,
            rateChanges = rateChanges,
            alarmLateDays = settingsObj?.get("alarmLateDays")?.jsonPrimitive?.intOrNull ?: defaults.alarmLateDays,
            reviewMode = settingsObj?.get("reviewMode")?.jsonPrimitive?.content ?: defaults.reviewMode,
            reviewDue = settingsObj?.get("reviewDue")?.jsonPrimitive?.intOrNull ?: defaults.reviewDue,
            reviewDueDate = settingsObj?.get("reviewDueDate")?.jsonPrimitive?.content ?: defaults.reviewDueDate,
            reviewStep = settingsObj?.get("reviewStep")?.jsonPrimitive?.intOrNull ?: defaults.reviewStep,
            backlogTotal = settingsObj?.get("backlogTotal")?.jsonPrimitive?.intOrNull ?: defaults.backlogTotal,
        )

        val records = recordsArr.mapNotNull { el ->
            runCatching {
                val obj = el.jsonObject
                val date = obj["date"]?.jsonPrimitive?.content ?: return@runCatching null
                val words = obj["words"]?.jsonPrimitive?.int ?: return@runCatching null
                val kind = obj["kind"]?.jsonPrimitive?.content ?: "new"
                VocabRecord(date = date, words = words, kind = kind)
            }.getOrNull()
        }

        return config to records
    }

    private fun parseV1(root: JsonObject): Pair<VocabConfig, List<VocabRecord>> {
        val settingsObj = root["settings"]?.jsonObject
        val recordsArr = root["records"]?.jsonArray ?: emptyList()

        val oldSettings = mutableMapOf<String, Any?>()
        settingsObj?.forEach { (k, v) ->
            v.jsonPrimitive.intOrNull?.let { oldSettings[k] = it }
                ?: v.jsonPrimitive.content.let { oldSettings[k] = it }
        }

        val oldRecords = recordsArr.mapNotNull { el ->
            val obj = el.jsonObject
            val date = obj["date"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val groups = obj["groups"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            mapOf("date" to date, "groups" to groups)
        }

        return VocabCalculator.migrateV1(oldRecords, oldSettings)
    }
}
