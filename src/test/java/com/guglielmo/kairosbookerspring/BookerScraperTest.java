package com.guglielmo.kairosbookerspring;

import com.gargoylesoftware.htmlunit.html.DomNode;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class BookerScraperTest {
    
    @Test
    public void loginAndGetBookingsTest() throws IOException, InterruptedException {
        BookerScraper booker = new BookerScraper();
        Booker bookeSelenium = new Booker();
        List<DomNode> lessons = booker.loginAndGetBookings("", "");
        List<WebElement> lessonsSelenium = bookeSelenium.loginAndGetBookings("", "");
        assertThat(lessons.size()).isEqualTo(lessonsSelenium.size());
    }
}
