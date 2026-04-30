package org.avromanov.yesihave.web;

import org.avromanov.yesihave.application.AddCoasterUseCase;
import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
public class WebPagesController {
    private static final long WEB_USER_ID = 0L;
    private static final String CHECK_DRAFTS_SESSION_KEY = "webCheckDrafts";
    private static final int UNCERTAIN_WEB_CANDIDATE_LIMIT = 3;

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
                              @RequestParam("back") MultipartFile back,
                              HttpSession session) throws IOException {
        try {
            byte[] frontBytes = uploadedImageNormalizer.normalizeToJpegIfHeic(front);
            byte[] backBytes = uploadedImageNormalizer.normalizeToJpegIfHeic(back);

            MatchResultDto result = checkPairUseCase.check(WEB_USER_ID, frontBytes, backBytes);
            return renderCheckResult(result, storeCheckDraft(session, frontBytes, backBytes));
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

    @PostMapping(value = "/web/add-from-check", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String addFromCheck(@RequestParam("name") String name,
                               @RequestParam("draftId") String draftId,
                               HttpSession session) {
        try {
            String trimmedName = name == null ? "" : name.trim();
            if (trimmedName.isBlank()) {
                throw new IllegalArgumentException("Введите name.");
            }

            CheckDraft draft = getCheckDraft(session, draftId);
            if (draft == null) {
                throw new IllegalArgumentException("Черновик фото не найден. Проверьте заново и попробуйте еще раз.");
            }

            UUID id = addCoasterUseCase.add(trimmedName, draft.frontBytes(), draft.backBytes());
            removeCheckDraft(session, draftId);
            return renderAddedPage(trimmedName, id, "Использованы фото из последней проверки.");
        } catch (IllegalArgumentException e) {
            return errorPage("Ошибка ввода", Objects.toString(e.getMessage(), "Некорректный ввод."), "/web/check");
        } catch (Exception e) {
            return errorPage("Ошибка", "Произошла ошибка добавления. Попробуйте снова.", "/web/check");
        }
    }

    @GetMapping(value = "/web/add-from-check", produces = MediaType.TEXT_HTML_VALUE)
    public String addFromCheckForm(@RequestParam("draftId") String draftId,
                                   HttpSession session) {
        CheckDraft draft = getCheckDraft(session, draftId);
        if (draft == null) {
            return errorPage("Черновик не найден", "Сначала выполните проверку и затем попробуйте добавить по этим фото.", "/web/check");
        }

        return page("Add From Check", """
                <section class="card">
                  <h2>Добавить как новый костер</h2>
                  <p class="muted">Используем фото из последней проверки. Нужно только ввести название.</p>
                  <form method="post" action="/web/add-from-check">
                    <input name="draftId" type="hidden" value="%s">
                    <label>Name
                      <input name="name" type="text" placeholder="Например, Guinness Classic" required>
                    </label>
                    <button type="submit">Добавить по этим фото</button>
                  </form>
                  <div class="actions">
                    <a class="btn secondary" href="/web/check">Вернуться к проверке</a>
                  </div>
                </section>
                """.formatted(escapeHtml(draftId)));
    }

    private String renderCheckResult(MatchResultDto r, String draftId) {
        List<CandidateDto> displayCandidates = candidatesForDisplay(r);
        StringBuilder candidates = new StringBuilder();
        if (displayCandidates.isEmpty()) {
            candidates.append("<p class=\"muted\">Кандидаты: нет</p>");
        } else {
            candidates.append("<ul class=\"list\">");
            for (CandidateDto c : displayCandidates) {
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
        String addFromCheck = renderAddFromCheckSection(r, draftId);
        String candidatesTitle = r.decision() == MatchDecision.UNCERTAIN
                ? "3 лучших кандидата"
                : "Top кандидаты";

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
                  <h3>%s</h3>
                  %s
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
                escapeHtml(candidatesTitle),
                candidates,
                addFromCheck
        ));
    }

    private List<CandidateDto> candidatesForDisplay(MatchResultDto result) {
        if (result.topCandidates() == null || result.topCandidates().isEmpty()) {
            return List.of();
        }
        if (result.decision() != MatchDecision.UNCERTAIN) {
            return result.topCandidates();
        }
        return new ArrayList<>(result.topCandidates().subList(
                0,
                Math.min(UNCERTAIN_WEB_CANDIDATE_LIMIT, result.topCandidates().size())
        ));
    }

    private String renderAddFromCheckSection(MatchResultDto result, String draftId) {
        if (result.decision() == MatchDecision.MATCH) {
            return "";
        }

        if (result.decision() == MatchDecision.NO_MATCH) {
            return """
                    <section class="subcard">
                      <h3>Совпадение не найдено</h3>
                      <p class="muted">Если это новый костер, можно сохранить текущие front/back без повторной съемки.</p>
                      <form method="post" action="/web/add-from-check">
                        <input name="draftId" type="hidden" value="%s">
                        <label>Name
                          <input name="name" type="text" placeholder="Например, Guinness Classic" required>
                        </label>
                        <button type="submit">Добавить по этим фото</button>
                      </form>
                    </section>
                    """.formatted(escapeHtml(draftId));
        }

        return """
                <section class="subcard">
                  <h3>Не уверены, что это дубликат</h3>
                  <p class="muted">Выберите, что делать дальше с этими фото.</p>
                  <div class="actions">
                    <a class="btn" href="/web/add-from-check?draftId=%s">Это новый костер</a>
                    <a class="btn secondary" href="/web/check">Похоже, дубликат</a>
                  </div>
                </section>
                """.formatted(escapeHtml(draftId));
    }

    private String renderAddedPage(String trimmedName, UUID id, String note) {
        return page("Added", """
                <section class="card">
                  <h2>Добавлено</h2>
                  <p><b>Name:</b> %s</p>
                  <p><b>Id:</b> <code>%s</code></p>
                  <p class="muted">%s</p>
                  <div class="actions">
                    <a class="btn" href="/web/add">Добавить ещё</a>
                    <a class="btn secondary" href="/web/check">Проверить</a>
                  </div>
                  <div class="nav">
                    <a href="/">На главную</a>
                  </div>
                </section>
                """.formatted(
                escapeHtml(trimmedName),
                escapeHtml(id.toString()),
                escapeHtml(note)
        ));
    }

    private String storeCheckDraft(HttpSession session, byte[] frontBytes, byte[] backBytes) {
        @SuppressWarnings("unchecked")
        Map<String, CheckDraft> drafts = (Map<String, CheckDraft>) session.getAttribute(CHECK_DRAFTS_SESSION_KEY);
        if (drafts == null) {
            drafts = new HashMap<>();
            session.setAttribute(CHECK_DRAFTS_SESSION_KEY, drafts);
        }

        String draftId = UUID.randomUUID().toString();
        drafts.put(draftId, new CheckDraft(frontBytes, backBytes));
        return draftId;
    }

    private CheckDraft getCheckDraft(HttpSession session, String draftId) {
        @SuppressWarnings("unchecked")
        Map<String, CheckDraft> drafts = (Map<String, CheckDraft>) session.getAttribute(CHECK_DRAFTS_SESSION_KEY);
        if (drafts == null || draftId == null || draftId.isBlank()) {
            return null;
        }
        return drafts.get(draftId);
    }

    private void removeCheckDraft(HttpSession session, String draftId) {
        @SuppressWarnings("unchecked")
        Map<String, CheckDraft> drafts = (Map<String, CheckDraft>) session.getAttribute(CHECK_DRAFTS_SESSION_KEY);
        if (drafts != null && draftId != null) {
            drafts.remove(draftId);
        }
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

    private record CheckDraft(byte[] frontBytes, byte[] backBytes) {
    }
}
