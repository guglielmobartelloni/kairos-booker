package com.guglielmo.kairosbookerspring;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.google.gson.Gson;
import com.guglielmo.kairosbookerspring.api.response.pojo.LessonsResponse;
import com.guglielmo.kairosbookerspring.api.response.pojo.Prenotazioni;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class BookerScraper {

    private final WebClient webClient;

    public BookerScraper() {
        this.webClient = new WebClient();
        final WebClientOptions options = webClient.getOptions();
        options.setJavaScriptEnabled(false);
        options.setCssEnabled(false);
    }

    protected List<LessonsResponse> loginAndGetBookings(String username, String password) throws IOException, InterruptedException {
        final Set<Cookie> cookies = getLoginCookies(username, password);
        HttpResponse<String> apiResponse = getLessonsJson(cookies);

        String jsonLine = getJsonString(apiResponse);
        final LessonsResponse[] lessonsResponses = new Gson().fromJson(jsonLine, LessonsResponse[].class);

//        final List<Prenotazioni> allLessons = Arrays.stream(lessonsResponses)
//                .map(LessonsResponse::getPrenotazioni)
//                .flatMap(Collection::stream)
//                .collect(Collectors.toList());

        final List<LessonsResponse> lessonsResponseList = Arrays.stream(lessonsResponses).collect(Collectors.toList());

        return lessonsResponseList;
    }

    public boolean bookLessons(String username, String password, Collection<Prenotazioni> lessonsToBook) throws IOException {
        final String formattedCookies = formatCookies(getLoginCookies(username, password));
        String requestUrl = "https://kairos.unifi.it/agendaweb/call_ajax.php?language=it&mode=salva_prenotazioni&codice_fiscale=BRTGLL00C26G713C&id_entries=[" + lessonsToBook.stream().map(Prenotazioni::getEntryId).map(String::valueOf).collect(Collectors.joining(",")) + "]";
        HttpResponse<String> response = Unirest.post(requestUrl)
                .header("Connection", "keep-alive")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("sec-ch-ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"99\", \"Google Chrome\";v=\"99\"")
                .header("DNT", "1")
                .header("sec-ch-ua-mobile", "?0")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.83 Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("sec-ch-ua-platform", "\"macOS\"")
                .header("Origin", "https://kairos.unifi.it")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Dest", "empty")
                .header("Referer", "https://kairos.unifi.it/agendaweb/index.php?view=prenotalezione&include=prenotalezione&_lang=it")
                .header("Accept-Language", "en-US,en;q=0.9,it;q=0.8,ja;q=0.7")
                .header("Cookie", formattedCookies)
                .header("sec-gpc", "1")
                .asString();

        log.info("Response: {}", response.getBody());
        return response.getStatus() == 200;

    }

    private Set<Cookie> getLoginCookies(String username, String password) throws IOException {
        String kairosFormPage = "https://kairos.unifi.it/agendaweb/index.php?view=login&include=login&from=prenotalezione&from_include=prenotalezione&_lang=en";

        final HtmlPage page = webClient.getPage(kairosFormPage);

        // Click conditions buttons
        String privacySliderSelector = "#main-content > div.main-content-body > div.container > div:nth-child(2) > div.col-lg-6 > div > div:nth-child(5) > div.col-xs-3 > label > span";
        String rulesSliderSelector = "#main-content > div.main-content-body > div.container > div:nth-child(2) > div.col-lg-6 > div > div:nth-child(6) > div.col-xs-3 > label > span";
        final HtmlElement privacySlider = page.querySelector(privacySliderSelector);
        final HtmlElement rulesSlider = page.querySelector(rulesSliderSelector);
        privacySlider.click();
        rulesSlider.click();

        // Click login button
        final HtmlElement login = page.querySelector("#oauth_btn");
        webClient.getOptions().setJavaScriptEnabled(true);
        HtmlPage loginPage = login.click();

        // Insert username e password
        final HtmlForm loginForm = loginPage.querySelector("body > div > div > div > div.column.one > form");
        loginForm.getInputByName("j_username").setValueAttribute(username);
        loginForm.getInputByName("j_password").setValueAttribute(password);

        // Login button
        final HtmlElement loginButton = loginForm.querySelector("body > div > div > div > div.column.one > form > div:nth-child(5) > button");
        loginButton.click();

        final Set<Cookie> cookies = webClient.getCookieManager().getCookies();
        webClient.getCookieManager().clearCookies();
        return cookies;
    }

    private HttpResponse<String> getLessonsJson(Set<Cookie> cookies) {
        String formattedCookies = formatCookies(cookies);
        HttpResponse<String> response = Unirest.get("https://kairos.unifi.it/agendaweb/index.php?view=prenotalezione&include=prenotalezione&_lang=it")
                .header("Connection", "keep-alive")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("sec-ch-ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"99\", \"Google Chrome\";v=\"99\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"macOS\"")
                .header("DNT", "1")
                .header("Upgrade-Insecure-Requests", "1")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.83 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-User", "?1")
                .header("Sec-Fetch-Dest", "document")
                .header("Referer", "https://kairos.unifi.it/agendaweb/index.php?view=prenotalezione&include=prenotalezione_home&_lang=it")
                .header("Accept-Language", "en-US,en;q=0.9,it;q=0.8,ja;q=0.7")
                .header("Cookie", formattedCookies)
                .header("sec-gpc", "1")
                .asString();
        return response;
    }

    private String formatCookies(Set<Cookie> cookies) {
        return cookies.stream().map(e -> e.getName() + "=" + e.getValue()).collect(Collectors.joining(";"));
    }

    @NotNull
    private String getJsonString(HttpResponse<String> response) {
        String jsonLine = response.getBody().lines().filter(e -> e.trim().startsWith("var lezioni_prenotabili")).collect(Collectors.joining());
        jsonLine = jsonLine.substring(" \t\t\tvar lezioni_prenotabili = JSON.parse(".length());
        jsonLine = jsonLine.substring(0, jsonLine.length() - 4);
        return jsonLine;
    }

}
