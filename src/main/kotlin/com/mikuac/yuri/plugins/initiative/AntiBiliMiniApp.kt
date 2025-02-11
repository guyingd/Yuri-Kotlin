package com.mikuac.yuri.plugins.initiative

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mikuac.shiro.annotation.MessageHandler
import com.mikuac.shiro.annotation.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.common.utils.ShiroUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.yuri.bean.dto.BiliVideoApiDto
import com.mikuac.yuri.exception.YuriException
import com.mikuac.yuri.utils.MsgSendUtils
import com.mikuac.yuri.utils.NetUtils
import com.mikuac.yuri.utils.RegexUtils
import org.springframework.stereotype.Component

@Shiro
@Component
class AntiBiliMiniApp {

    private fun request(shortURL: String): BiliVideoApiDto {
        val data: BiliVideoApiDto
        try {
            val urlResp = NetUtils.get(shortURL)
            val bid = RegexUtils.group(Regex("(?<=video/)(.*)(?=/\\?)"), 1, urlResp.request.url.toString())
            urlResp.close()

            val api = "https://api.bilibili.com/x/web-interface/view?bvid=${bid}"
            val resp = NetUtils.get(api)
            data = Gson().fromJson(resp.body?.string(), BiliVideoApiDto::class.java)
            resp.close()

            if (data.code != 0) throw YuriException(data.message)
        } catch (e: Exception) {
            e.printStackTrace()
            throw YuriException("哔哩哔哩数据获取异常：${e.message}")
        }
        return data
    }

    private fun buildMsg(json: String): String {
        val jsonObject = JsonParser.parseString(json)
        val shortURL = jsonObject.asJsonObject["meta"].asJsonObject["detail_1"].asJsonObject["qqdocurl"].asString
        val data = request(shortURL).data
        return MsgUtils.builder()
            .img(data.pic)
            .text("\n${ShiroUtils.escape2(data.title)}")
            .text("\nUP：${ShiroUtils.escape2(data.owner.name)}")
            .text("\n播放：${data.stat.view} 弹幕：${data.stat.danmaku}")
            .text("\n投币：${data.stat.coin} 点赞：${data.stat.like}")
            .text("\n评论：${data.stat.reply} 分享：${data.stat.share}")
            .text("\nhttps://www.bilibili.com/video/av${data.stat.aid}")
            .text("\nhttps://www.bilibili.com/video/${data.bvid}")
            .build()
    }

    @MessageHandler
    fun handler(bot: Bot, event: AnyMessageEvent) {
        try {
            val msg = event.message
            if (!msg.contains("com.tencent.miniapp_01") || !msg.contains("哔哩哔哩")) return
            val json = event.arrayMsg.filter { it.type == "json" }
            if (json.isNotEmpty()) {
                bot.sendMsg(event, json[0].data["data"]?.let { buildMsg(it) }, false)
            }
        } catch (e: YuriException) {
            e.message?.let { MsgSendUtils.replySend(event.messageId, event.userId, event.groupId, bot, it) }
        } catch (e: Exception) {
            MsgSendUtils.replySend(event.messageId, event.userId, event.groupId, bot, "未知错误：${e.message}")
            e.printStackTrace()
        }
    }

}
