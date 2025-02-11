package com.mikuac.yuri.plugins.passive

import com.huaban.analysis.jieba.JiebaSegmenter
import com.kennycason.kumo.CollisionMode
import com.kennycason.kumo.WordCloud
import com.kennycason.kumo.bg.CircleBackground
import com.kennycason.kumo.font.KumoFont
import com.kennycason.kumo.font.scale.LinearFontScalar
import com.kennycason.kumo.image.AngleGenerator
import com.kennycason.kumo.nlp.FrequencyAnalyzer
import com.kennycason.kumo.nlp.tokenizer.api.WordTokenizer
import com.kennycason.kumo.palette.ColorPalette
import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.MessageHandler
import com.mikuac.shiro.annotation.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.common.utils.ShiroUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.core.BotContainer
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.yuri.annotation.Slf4j.Companion.log
import com.mikuac.yuri.config.Config
import com.mikuac.yuri.entity.WordCloudEntity
import com.mikuac.yuri.enums.RegexCMD
import com.mikuac.yuri.exception.YuriException
import com.mikuac.yuri.repository.WordCloudRepository
import com.mikuac.yuri.utils.MsgSendUtils
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Dimension
import java.io.ByteArrayOutputStream
import java.lang.Thread.sleep
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.regex.Matcher

@Shiro
@Component
class WordCloud {

    companion object {
        private const val ZONE = "Asia/Shanghai"
    }

    @Autowired
    private lateinit var repository: WordCloudRepository

    @Autowired
    private lateinit var botContainer: BotContainer

    @GroupMessageHandler
    fun saveMsg(event: GroupMessageEvent) {
        val data = WordCloudEntity(0, event.userId, event.groupId, event.message, LocalDate.now())
        repository.save(data)
    }

    private fun generateWordCloud(text: List<String>): String {
        val frequencyAnalyzer = FrequencyAnalyzer()
        frequencyAnalyzer.setWordFrequenciesToReturn(300)
        frequencyAnalyzer.setMinWordLength(2)
        frequencyAnalyzer.setWordTokenizer(JieBaTokenizer())
        val wordFrequencies = frequencyAnalyzer.load(text)
        val dimension = Dimension(1000, 1000)
        val wordCloud = WordCloud(dimension, CollisionMode.PIXEL_PERFECT)
        wordCloud.setPadding(2)
        wordCloud.setAngleGenerator(AngleGenerator(0))
        wordCloud.setKumoFont(
            KumoFont(this.javaClass.getResourceAsStream("/font/loli.ttf"))
        )
        val colors = mutableListOf("0000FF", "40D3F1", "40C5F1", "40AAF1", "408DF1", "4055F1")
        wordCloud.setBackground(CircleBackground(((1000 + 1000) / 4)))
        wordCloud.setBackgroundColor(Color(0xFFFFFF))
        wordCloud.setColorPalette(
            ColorPalette(colors.map { it.toIntOrNull(16)?.let { it1 -> Color(it1) } })
        )
        wordCloud.setFontScalar(
            LinearFontScalar(
                Config.plugins.wordCloud.minFontSize, Config.plugins.wordCloud.maxFontSize
            )
        )
        wordCloud.build(wordFrequencies)

        val stream = ByteArrayOutputStream()
        wordCloud.writeToStreamAsPNG(stream)

        return Base64.getEncoder().encodeToString(stream.toByteArray())
    }

    private fun query(userId: Long, groupId: Long, start: LocalDate, end: LocalDate): List<String> {
        return repository.findAllBySenderIdAndGroupIdAndTimeBetween(userId, groupId, start, end).map { it.content }
            .toList()
    }

    private fun query(groupId: Long, start: LocalDate, end: LocalDate): List<String> {
        return repository.findAllByGroupIdAndTimeBetween(groupId, start, end).map { it.content }.toList()
    }

    private fun getWordsForRange(
        userId: Long,
        groupId: Long,
        type: String,
        range: String,
        cronTask: Boolean
    ): List<String> {
        var now = LocalDate.now()
        if (cronTask) now = now.plusDays(-1)
        val startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endOfWeek = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
        val endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth())
        val startOfYear = now.with(TemporalAdjusters.firstDayOfYear())
        val endOfYear = now.with(TemporalAdjusters.lastDayOfYear())

        when (type) {
            "我的" -> {
                when (range) {
                    "今日" -> return query(userId, groupId, now, now)
                    "本周" -> return query(userId, groupId, startOfWeek, endOfWeek)
                    "本月" -> return query(userId, groupId, startOfMonth, endOfMonth)
                    "本年" -> return query(userId, groupId, startOfYear, endOfYear)
                }
            }

            "本群" -> {
                when (range) {
                    "今日" -> return query(groupId, now, now)
                    "本周" -> return query(groupId, startOfWeek, endOfWeek)
                    "本月" -> return query(groupId, startOfMonth, endOfMonth)
                    "本年" -> return query(groupId, startOfYear, endOfYear)
                }
            }
        }
        return listOf()
    }

    private fun getWords(userId: Long, groupId: Long, type: String, range: String, cronTask: Boolean): List<String> {
        val filterRule = StringUtils.join(Config.plugins.wordCloud.filterRule, "|").toRegex()
        val contents = ArrayList<String>()
        getWordsForRange(userId, groupId, type, range, cronTask).forEach { raw ->
            contents.addAll(
                ShiroUtils
                    .stringToMsgChain(raw)
                    .filter { it.type == "text" }
                    .map { it.data["text"]!!.trim() }
                    .filter { !it.contains(filterRule) }
                    .toList()
            )
        }
        return contents
    }

    @GroupMessageHandler(cmd = RegexCMD.WORD_CLOUD)
    fun wordCloudHandler(event: GroupMessageEvent, bot: Bot, matcher: Matcher) {
        val msgId = event.messageId
        try {
            val type = matcher.group(1)
            val range = matcher.group(2)
            MsgSendUtils.replySend(msgId, event.userId, event.groupId, bot, "数据检索中，请耐心等待～")
            val contents = getWords(event.userId, event.groupId, type, range, false)
            if (contents.isEmpty()) {
                throw YuriException("唔呣～数据库里没有找到你的发言记录呢")
            }
            val msg = MsgUtils.builder().reply(msgId).img("base64://${generateWordCloud(contents)}").build()
            bot.sendGroupMsg(event.groupId, msg, false)
        } catch (e: YuriException) {
            e.message?.let { MsgSendUtils.replySend(msgId, event.userId, event.groupId, bot, it) }
        } catch (e: Exception) {
            MsgSendUtils.replySend(msgId, event.userId, event.groupId, bot, "未知错误：${e.message}")
            e.printStackTrace()
        }
    }

    @MessageHandler(cmd = RegexCMD.WORD_CLOUD_CRON)
    fun wordCloudCronHandler(event: AnyMessageEvent, bot: Bot, matcher: Matcher) {
        if (event.userId !in Config.base.adminList) {
            bot.sendMsg(event, "此操作需要管理员权限", false)
        }
        when (matcher.group(1)) {
            "day" -> taskForDay()
            "week" -> taskForWeek()
            "month" -> taskForMonth()
        }
    }

    @Scheduled(cron = "0 30 23 * * ?", zone = ZONE)
    fun taskForDay() {
        val now = LocalDateTime.now()
        // 跳过周日
        if (now.dayOfWeek == DayOfWeek.SUNDAY) return
        // 跳过每月最后一天
        if (now == now.with(TemporalAdjusters.firstDayOfMonth())) return
        task("今日")
    }

    @Scheduled(cron = "0 30 23 ? * SUN", zone = ZONE)
    fun taskForWeek() {
        val now = LocalDateTime.now()
        // 跳过每月最后一天
        if (now == now.with(TemporalAdjusters.firstDayOfMonth())) return
        task("本周")
    }

    @Scheduled(cron = "0 30 23 L * ?", zone = ZONE)
    fun taskForMonth() {
        task("本月")
    }

    private fun task(range: String) {
        val bot = botContainer.robots[Config.base.selfId]
        if (bot == null) {
            log.error("词云插件定时任务执行失败 未获取到 Bot 对象")
            return
        }
        val cronTaskRate = Config.plugins.wordCloud.cronTaskRate.times(1000L)
        bot.groupList.data.forEach {
            sleep(cronTaskRate)
            bot.sendGroupMsg(it.groupId, "嗨嗨嗨，摸鱼的一天结束啦，让我来看看群友${range}聊了些什么～", false)
            val contents = getWords(0L, it.groupId, "本群", range, true)
            if (contents.isEmpty()) {
                bot.sendGroupMsg(it.groupId, "唔呣～ 居然一条记录都没有，难道你们不聊天的嘛？？", false)
                return@forEach
            }
            val msg = MsgUtils.builder().img("base64://${generateWordCloud(contents)}").build()
            bot.sendGroupMsg(it.groupId, msg, false)
            log.info("${range}词云推送到群 [${it.groupName}](${it.groupId}) 成功")
        }
    }

    private class JieBaTokenizer : WordTokenizer {
        override fun tokenize(sentence: String?): MutableList<String> {
            return JiebaSegmenter().process(sentence, JiebaSegmenter.SegMode.INDEX).map { it.word.trim() }
                .toMutableList()
        }
    }

}