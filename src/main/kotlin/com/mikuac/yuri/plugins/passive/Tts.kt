package com.mikuac.yuri.plugins.passive

import com.mikuac.shiro.annotation.MessageHandler
import com.mikuac.shiro.annotation.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.common.utils.RegexUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.yuri.enums.RegexCMD
import com.mikuac.yuri.exception.YuriException
import com.mikuac.yuri.utils.MsgSendUtils
import org.springframework.stereotype.Component

@Shiro
@Component
class Tts {

    @MessageHandler(cmd = RegexCMD.TTS)
    fun ttsHandler(event: AnyMessageEvent, bot: Bot) {
        try {
            val msg = event.arrayMsg.filter { it.type == "text" }.map { it.data["text"] }.joinToString()
            val regex = RegexUtils.regexMatcher(RegexCMD.TTS, msg) ?: throw YuriException("非法输入")
            val txt = regex.group(1).trim()
            if (txt.isBlank()) throw YuriException("非法输入")
            bot.sendMsg(event, MsgUtils.builder().tts(txt).build(), false)
        } catch (e: YuriException) {
            e.message?.let { MsgSendUtils.replySend(event.messageId, event.userId, event.groupId, bot, it) }
        } catch (e: Exception) {
            MsgSendUtils.replySend(event.messageId, event.userId, event.groupId, bot, "未知错误：${e.message}")
            e.printStackTrace()
        }
    }

}