package com.mikuac.yuri.plugins.passive

import com.google.gson.Gson
import com.mikuac.shiro.annotation.MessageHandler
import com.mikuac.shiro.annotation.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.common.utils.OneBotMedia
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.yuri.bean.dto.GithubRepoDto
import com.mikuac.yuri.config.Config
import com.mikuac.yuri.enums.RegexCMD
import com.mikuac.yuri.exception.YuriException
import com.mikuac.yuri.utils.ImageUtils
import com.mikuac.yuri.utils.MsgSendUtils
import com.mikuac.yuri.utils.NetUtils
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Shiro
@Component
class GithubRepo {

    private fun request(repoName: String): GithubRepoDto {
        val data: GithubRepoDto
        try {
            val api = "https://api.github.com/search/repositories?q=${repoName}"
            val resp = NetUtils.get(api)
            data = Gson().fromJson(resp.body?.string(), GithubRepoDto::class.java)
            resp.close()
            if (data.totalCount <= 0) throw YuriException("未找到名为 $repoName 的仓库")
        } catch (e: Exception) {
            throw YuriException("GitHub数据获取异常：${e.message}")
        }
        return data
    }

    private fun buildMsg(matcher: Matcher): String {
        val searchName = matcher.group(1) ?: throw YuriException("请按格式输入正确的仓库名")
        val data = request(searchName).items[0]
        val img = ImageUtils.formatPNG(
            "https://opengraph.githubassets.com/0/${data.fullName}",
            Config.plugins.githubRepo.proxy
        )
        return MsgUtils.builder()
            .text("RepoName: ${data.fullName}")
            .text("\nDefaultBranch: ${data.defaultBranch}")
            .text("\nLanguage: ${data.language}")
            .text("\nStars/Forks: ${data.stars}/${data.forks}")
            .text("\nLicense: ${data.license?.spdxId}")
            .text("\n${data.description}")
            .text("\n${data.htmlUrl}")
            .img(
                OneBotMedia.Builder()
                    .file(img)
                    .cache(false)
                    .build()
            )
            .build()
    }

    @MessageHandler(cmd = RegexCMD.GITHUB_REPO)
    fun githubRepoHandler(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        try {
            bot.sendMsg(event, buildMsg(matcher), false)
        } catch (e: YuriException) {
            e.message?.let { MsgSendUtils.replySend(event.messageId, event.userId, event.groupId, bot, it) }
        } catch (e: Exception) {
            MsgSendUtils.replySend(event.messageId, event.userId, event.groupId, bot, "未知错误：${e.message}")
            e.printStackTrace()
        }
    }

}