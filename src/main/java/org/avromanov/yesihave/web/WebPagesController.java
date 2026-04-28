package org.avromanov.yesihave.web;

import org.avromanov.yesihave.application.AddCoasterUseCase;
import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@RestController
public class WebPagesController {
    private static final long WEB_USER_ID = 0L;

    private final CheckPairUseCase checkPairUseCase;
    private final AddCoasterUseCase addCoasterUseCase;
    private final UploadedImageNormalizer uploadedImageNormalizer;

    public WebPagesController(CheckPairUseCase checkPairUseCase,
                              AddCoasterUseCase addCoasterUseCase,
                              UploadedImageNormalizer uploadedImageNormalizer) {
        this.checkPairUseCase = checkPairUseCase;
        this.addCoasterUseCase = addCoasterUseCase;
        this.uploadedImageNormalizer = uploadedImageNormalizer;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return page("YesIHave", """
                <section class="hero">
                  <h1>YesIHave</h1>
                  <p>Минимальный web по аналогии с Telegram: <b>check</b> и <b>add</b>.</p>
                  <div class="actions">
                    <a class="btn" href="/web/check">Проверить (check)</a>
                    <a class="btn secondary" href="/web/add">Добавить (add)</a>
                  </div>
                  <p class="muted">Старый режим (JS + API) доступен на <a href="/index.html">/index.html</a>.</p>
                </section>
                """);
    }

    @GetMapping(value = "/web/check", produces = MediaType.TEXT_HTML_VALUE)
    public String checkForm() {
        return page("Check", """
                <section class="card">
                  <h2>Проверить пару (front/back)</h2>
                  <form method="post" action="/web/check" enctype="multipart/form-data">
                    <label>Front (iPhone HEIC тоже можно)
                      <input name="front" type="file" accept="image/*" required>
                    </label>
                    <label>Back (iPhone HEIC тоже можно)
                      <input name="back" type="file" accept="image/*" required>
                    </label>
                    <button type="submit">Проверить</button>
                  </form>
                  <div class="nav">
                    <a href="/">На главную</a>
                  </div>
                </section>
                """);
    }

    @PostMapping(value = "/web/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String checkSubmit(@RequestParam("front") MultipartFile front,
                              @RequestParam("back") MultipartFile back) throws IOException {
        try {
            byte[] frontBytes = uploadedImageNormalizer.normalizeToJpegIfHeic(front);
            byte[] backBytes = uploadedImageNormalizer.normalizeToJpegIfHeic(back);

            MatchResultDto result = checkPairUseCase.check(WEB_USER_ID, frontBytes, backBytes);
            return renderCheckResult(result);
        } catch (IllegalArgumentException e) {
            return errorPage("Ошибка ввода", Objects.toString(e.getMessage(), "Некорректный ввод."), "/web/check");
        } catch (Exception e) {
            return errorPage("Ошибка", "Произошла ошибка обработки. Попробуйте другое фото.", "/web/check");
        }
    }

    @GetMapping(value = "/web/add", produces = MediaType.TEXT_HTML_VALUE)
    public String addForm() {
        return page("Add", """
                <section class="card">
                  <h2>Добавить новый бирдекель</h2>
                  <form method="post" action="/web/add" enctype="multipart/form-data">
                    <label>Name
                      <input name="name" type="text" placeholder="Например, Guinness Classic" required>
                    </label>
                    <label>Front (iPhone HEIC тоже можно)
                      <input name="front" type="file" accept="image/*" required>
                    </label>
                    <label>Back (iPhone HEIC тоже можно)
                      <input name="back" type="file" accept="image/*" required>
                    </label>
                    <button type="submit">Добавить</button>
                  </form>
                  <div class="nav">
                    <a href="/">На главную</a>
                  </div>
                </section>
                """);
    }

    @PostMapping(value = "/web/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String addSubmit(@RequestParam("name") String name,
                            @RequestParam("front") MultipartFile front,
                            @RequestParam("back") MultipartFile back) throws IOException {
        try {
            String trimmedName = name == null ? "" : name.trim();
            if (trimmedName.isBlank()) {
                throw new IllegalArgumentException("Введите name.");
            }

            byte[] frontBytes = uploadedImageNormalizer.normalizeToJpegIfHeic(front);
            byte[] backBytes = uploadedImageNormalizer.normalizeToJpegIfHeic(back);

            UUID id = addCoasterUseCase.add(trimmedName, frontBytes, backBytes);
            return page("Added", """
                    <section class="card">
                      <h2>Добавлено</h2>
                      <p><b>Name:</b> %s</p>
                      <p><b>Id:</b> <code>%s</code></p>
                      <div class="actions">
                        <a class="btn" href="/web/add">Добавить ещё</a>
                        <a class="btn secondary" href="/web/check">Проверить</a>
                      </div>
                      <div class="nav">
                        <a href="/">На главную</a>
                      </div>
                    </section>
                    """.formatted(escapeHtml(trimmedName), escapeHtml(id.toString())));
        } catch (IllegalArgumentException e) {
            return errorPage("Ошибка ввода", Objects.toString(e.getMessage(), "Некорректный ввод."), "/web/add");
        } catch (Exception e) {
            return errorPage("Ошибка", "Произошла ошибка добавления. Попробуйте снова.", "/web/add");
        }
    }

    private String renderCheckResult(MatchResultDto r) {
        StringBuilder candidates = new StringBuilder();
        if (r.topCandidates() == null || r.topCandidates().isEmpty()) {
            candidates.append("<p class=\"muted\">Кандидаты: нет</p>");
        } else {
            candidates.append("<ul class=\"list\">");
            for (CandidateDto c : r.topCandidates()) {
                candidates.append("<li>")
                        .append("<b>").append(escapeHtml(String.valueOf(c.rank()))).append(".</b> ")
                        .append(escapeHtml(c.name()))
                        .append(" <span class=\"muted\">id=").append(escapeHtml(String.valueOf(c.coasterId())))
                        .append(", pair=").append(escapeHtml(formatScore(c.pairScore())))
                        .append("</span>")
                        .append("</li>");
            }
            candidates.append("</ul>");
        }

        String matched = r.matchedCoasterId() == null
                ? "<span class=\"muted\">—</span>"
                : "<code>" + escapeHtml(r.matchedCoasterId().toString()) + "</code>";

        return page("Result", """
                <section class="card">
                  <h2>Результат</h2>
                  <div class="kv">
                    <div><span class="k">Decision</span><span class="v"><b>%s</b></span></div>
                    <div><span class="k">front</span><span class="v">%s</span></div>
                    <div><span class="k">back</span><span class="v">%s</span></div>
                    <div><span class="k">pair</span><span class="v">%s</span></div>
                    <div><span class="k">matchedId</span><span class="v">%s</span></div>
                  </div>
                  <h3>Top кандидаты</h3>
                  %s
                  <div class="actions">
                    <a class="btn" href="/web/check">Проверить ещё</a>
                    <a class="btn secondary" href="/web/add">Добавить</a>
                  </div>
                  <div class="nav">
                    <a href="/">На главную</a>
                  </div>
                </section>
                """.formatted(
                escapeHtml(String.valueOf(r.decision())),
                escapeHtml(formatScore(r.scoreFront())),
                escapeHtml(formatScore(r.scoreBack())),
                escapeHtml(formatScore(r.pairScore())),
                matched,
                candidates
        ));
    }

    private String errorPage(String title, String message, String backHref) {
        return page(title, """
                <section class="card">
                  <h2>%s</h2>
                  <p class="error">%s</p>
                  <div class="actions">
                    <a class="btn" href="%s">Назад</a>
                    <a class="btn secondary" href="/">На главную</a>
                  </div>
                </section>
                """.formatted(escapeHtml(title), escapeHtml(message), escapeHtml(backHref)));
    }

    private String page(String title, String body) {
        return """
                <!doctype html>
                <html lang="ru">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <link rel="stylesheet" href="/web.css">
                </head>
                <body>
                  <main class="wrap">
                    %s
                  </main>
                </body>
                </html>
                """.formatted(escapeHtml(title), body);
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "n/a";
        }
        return String.format("%.4f", score);
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

