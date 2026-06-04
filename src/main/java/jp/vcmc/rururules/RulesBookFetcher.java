package jp.vcmc.rururules;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ルールページをHTTPで取得し、マイクラの本に変換して表示するクラス。
 */
public class RulesBookFetcher {

    // 1ページあたりの安全な文字数上限 (Minecraft book page ≒ 14行×20文字)
    private static final int MAX_CHARS_PER_PAGE = 220;
    // Minecraftの本の最大ページ数
    private static final int MAX_BOOK_PAGES = 100;

    private final RuruRulesPlugin plugin;

    /** キャッシュ済みの本ページ (null = 未取得 or 取得失敗) */
    private volatile List<Component> cachedPages = null;
    private volatile boolean fetchFailed = false;
    private final AtomicBoolean fetching = new AtomicBoolean(false);

    public RulesBookFetcher(RuruRulesPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── 取得 ─────────────────────────────────────────────────

    /** 非同期でルールページを取得・キャッシュする */
    public void fetchAsync() {
        if (!fetching.compareAndSet(false, true)) return;
        fetchFailed = false;

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                String url = plugin.getConfig().getString("rules-url", "https://www.vcmc.jp/rules");
                int timeout = plugin.getConfig().getInt("fetch-timeout-ms", 10000);
                String selector = plugin.getConfig().getString("content-selector", "main, article, .content, #content");

                plugin.getLogger().info("ルールページを取得中: " + url);

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; RuruRules/1.0)")
                        .timeout(timeout)
                        .get();

                // ナビ・フッター・スクリプトなど不要要素を削除
                doc.select("nav, header, footer, script, style, .nav, .header, .footer, .sidebar").remove();

                // メインコンテンツエリアを特定
                Element content = null;
                if (!selector.isBlank()) {
                    content = doc.selectFirst(selector);
                }
                if (content == null) {
                    content = doc.body();
                }

                // 段落テキストを抽出
                List<String> paragraphs = extractParagraphs(content);

                if (paragraphs.isEmpty()) {
                    plugin.getLogger().warning("ルールページからテキストを抽出できませんでした。チャット表示にフォールバックします。");
                    fetchFailed = true;
                    return;
                }

                cachedPages = buildBookPages(paragraphs);
                plugin.getLogger().info("ルールページを取得しました (" + cachedPages.size() + " ページ)");

            } catch (Exception e) {
                plugin.getLogger().warning("ルールページの取得に失敗しました: " + e.getMessage());
                fetchFailed = true;
            } finally {
                fetching.set(false);
            }
        });
    }

    // ─── HTML解析 ─────────────────────────────────────────────

    /** HTMLエレメントから段落テキストのリストを作る */
    private List<String> extractParagraphs(Element root) {
        List<String> result = new ArrayList<>();

        // 見出し・段落・リスト項目を順に処理
        Elements elements = root.select("h1, h2, h3, h4, h5, p, li, dt, dd");

        for (Element el : elements) {
            String text = el.text().trim();
            if (text.isEmpty()) continue;

            // 見出しは前後に装飾
            String tag = el.tagName().toLowerCase();
            if (tag.equals("h1") || tag.equals("h2")) {
                text = "【" + text + "】";
            } else if (tag.equals("h3") || tag.equals("h4") || tag.equals("h5")) {
                text = "■ " + text;
            } else if (tag.equals("li")) {
                text = "・" + text;
            }

            result.add(text);
        }

        // セレクタでヒットしなかった場合は全テキストを段落分割
        if (result.isEmpty()) {
            String wholeText = root.text();
            for (String line : wholeText.split("[。！？\n]{1,3}")) {
                line = line.trim();
                if (!line.isEmpty()) result.add(line);
            }
        }

        return result;
    }

    // ─── ページ組み立て ───────────────────────────────────────

    /** 段落リストを本のページ (Component リスト) に変換する */
    private List<Component> buildBookPages(List<String> paragraphs) {
        List<Component> pages = new ArrayList<>();
        StringBuilder buf = new StringBuilder();

        for (String para : paragraphs) {
            // para が1ページ上限を超える場合は単語単位で分割
            List<String> chunks = splitToChunks(para);

            for (String chunk : chunks) {
                if (buf.length() + chunk.length() + 1 > MAX_CHARS_PER_PAGE) {
                    if (!buf.isEmpty()) {
                        pages.add(Component.text(buf.toString().stripTrailing()));
                        buf = new StringBuilder();
                    }
                }
                buf.append(chunk).append("\n");
            }
            // 段落間の空行
            if (buf.length() + 1 <= MAX_CHARS_PER_PAGE) {
                buf.append("\n");
            }
        }

        if (!buf.isEmpty()) {
            pages.add(Component.text(buf.toString().stripTrailing()));
        }

        // 最終ページ: 同意ボタン (Minecraft本の最大100ページに収める)
        while (pages.size() >= MAX_BOOK_PAGES) {
            pages.remove(pages.size() - 1);
        }
        pages.add(buildAgreePage());

        return pages;
    }

    /** テキストをMAX_CHARS_PER_PAGE以内のチャンクに分割 */
    private List<String> splitToChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= MAX_CHARS_PER_PAGE) {
            chunks.add(text);
            return chunks;
        }
        // 単語（スペース・句読点）で分割
        String[] words = text.split("(?<=[ 、。！？　])");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            if (cur.length() + w.length() > MAX_CHARS_PER_PAGE) {
                if (!cur.isEmpty()) {
                    chunks.add(cur.toString());
                    cur = new StringBuilder();
                }
                // 単語自体が長すぎる場合は強制分割
                while (w.length() > MAX_CHARS_PER_PAGE) {
                    chunks.add(w.substring(0, MAX_CHARS_PER_PAGE));
                    w = w.substring(MAX_CHARS_PER_PAGE);
                }
            }
            cur.append(w);
        }
        if (!cur.isEmpty()) chunks.add(cur.toString());
        return chunks;
    }

    /** 同意ページを生成 */
    private Component buildAgreePage() {
        return Component.empty()
                .append(Component.text("─────────────\n", NamedTextColor.GRAY))
                .append(Component.text("以上のルールを\n読んで理解し、\n同意します。\n\n", NamedTextColor.DARK_GRAY))
                .append(Component.text("[ ✔ 同意する ]")
                        .color(NamedTextColor.DARK_GREEN)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/rules agree"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("クリックして同意する", NamedTextColor.GREEN)
                        )));
    }

    // ─── 本を開く ─────────────────────────────────────────────

    /**
     * プレイヤーに本を開かせる。
     * キャッシュがなければチャット表示にフォールバック。
     */
    public void openBookForPlayer(Player player) {
        List<Component> pages = cachedPages;
        if (pages == null || pages.isEmpty()) {
            // フォールバック: チャットにURLを表示
            RulesListener.sendRulesMessage(player, plugin.getConfig().getString("rules-url", "https://www.vcmc.jp/rules"));
            return;
        }

        String title = plugin.getConfig().getString("book-title", "vcmc.jp サーバールール");
        String author = plugin.getConfig().getString("book-author", "vcmc.jp");

        Book book = Book.book(
                Component.text(title),
                Component.text(author),
                pages
        );

        // Foliaではエンティティスケジューラで実行
        player.getScheduler().run(plugin, task -> {
            player.openBook(book);
            // 本を開いたあとにチャットにも補助メッセージを表示
            player.sendMessage(
                    Component.text("本の最後のページの ", NamedTextColor.YELLOW)
                            .append(Component.text("[✔ 同意する]", NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.text(" をクリックしてください。", NamedTextColor.YELLOW))
            );
        }, null);
    }

    // ─── 状態取得 ─────────────────────────────────────────────

    public boolean hasCachedBook() {
        return cachedPages != null && !cachedPages.isEmpty();
    }

    public boolean isFetchFailed() {
        return fetchFailed;
    }

    /** キャッシュをクリアして再取得する */
    public void reload() {
        cachedPages = null;
        fetchFailed = false;
        fetchAsync();
    }
}
