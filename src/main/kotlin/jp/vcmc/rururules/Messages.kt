package jp.vcmc.rururules

import jp.vcmc.rururules.i18n.Language
import jp.vcmc.rururules.i18n.Translations
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.plugin.java.JavaPlugin

/** 受け取る人の言語で文面を作る。日本語は config.yml、ほかの言語は lang フォルダから読む。 */
class Messages(private val plugin: JavaPlugin) {
    private val translations = Translations(plugin)
    private val miniMessage = MiniMessage.miniMessage()

    fun reload() = translations.reload()

    fun text(language: Language, key: String, vararg args: Pair<String, String>): Component {
        val template = translations.string(language, key) ?: plugin.config.getString("messages.$key") ?: key
        return miniMessage.deserialize(template, TagResolver.resolver(args.map { (name, value) -> Placeholder.unparsed(name, value) }))
    }

    fun send(target: Audience, key: String, vararg args: Pair<String, String>) {
        target.sendMessage(text(Language.of(target), key, *args))
    }

    fun bookTitle(language: Language): String =
        translations.string(language, "book-title") ?: plugin.config.getString("book-title").orEmpty()

    fun bookAuthor(language: Language): String =
        translations.string(language, "book-author") ?: plugin.config.getString("book-author").orEmpty()

    /** 翻訳ページが無いときに本の最初に入れる案内。 */
    fun untranslatedNotice(language: Language): Component? =
        translations.string(language, "untranslated-notice")?.let(miniMessage::deserialize)
}
