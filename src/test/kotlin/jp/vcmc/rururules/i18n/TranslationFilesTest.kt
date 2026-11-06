package jp.vcmc.rururules.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TranslationFilesTest {
    @Test
    fun `どの言語にも日本語と同じ文面がある`() {
        val config = TranslationFilesCheck.resource("config.yml")
        val messages = assertNotNull(config.getConfigurationSection("messages"))
        TranslationFilesCheck.check(
            TranslationFilesCheck.texts(messages) + mapOf(
                "book-title" to config.getString("book-title").orEmpty(),
                "book-author" to config.getString("book-author").orEmpty(),
                // 翻訳が無いことの案内なので、日本語には無い。
                "untranslated-notice" to "",
            ),
        )
    }

    @Test
    fun `どの言語にもルールページの URL の設定がある`() {
        val urls = assertNotNull(TranslationFilesCheck.resource("config.yml").getConfigurationSection("rules-urls"))
        assertEquals(TranslationFilesCheck.translated.keys.map { it.code }.sorted(), urls.getKeys(false).sorted())
    }
}
