package com.pricebotix;

import static spark.Spark.*;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import com.google.gson.*;
import io.github.bonigarcia.wdm.WebDriverManager;

import org.checkerframework.checker.units.qual.g;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class PricebotixBackend {
    public static void main(String[] args) {
        //adding the kill zombies
          try {
            System.out.println("🧹 Cleaning up zombie ChromeDrivers...");
            Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe /T");
        } catch (IOException e) {
            System.err.println("⚠️ Cleanup failed: " + e.getMessage());
        }

        port(4567);
        WebDriverManager.chromedriver().setup();

        // Enable CORS
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type,Authorization");
        });

        Gson gson = new Gson();

        // Flipkart route
        get("/flipkart", (req, res) -> {
            String query = req.queryParams("q");
            res.type("application/json");
            return gson.toJson(getFlipkartResults(query));
        });

        // eBay route
        get("/ebay", (req, res) -> {
            String query = req.queryParams("q");
            res.type("application/json");
            return gson.toJson(getEbayResults(query));
        });

        // reliance route
        get("/reliance", (req, res) -> {
            String query = req.queryParams("q");
            res.type("application/json");
            return new Gson().toJson(getRelianceResults(query));
        });


        // Combined route
        get("/search", (req, res) -> {
            String query = req.queryParams("q");

            if (query == null || query.isEmpty()) {
                res.status(400);
                return "{\"error\": \"Missing query parameter ?q=...\"}";
            }

            List<Map<String, String>> combined = new ArrayList<>();

            try {
                combined.addAll(getFlipkartResults(query));
            } catch (Exception e) {
                System.out.println("⚠️ Flipkart error: " + e.getMessage());
            }

            try {
                combined.addAll(getEbayResults(query));
            } catch (Exception e) {
                System.out.println("⚠️ eBay error: " + e.getMessage());
            }

            try {
                combined.addAll(getRelianceResults(query));
            } catch (Exception e) {
                System.out.println("⚠️ Reliance error: " + e.getMessage());
            }

            res.type("application/json");
            return gson.toJson(combined);
        });

            // ✅ START: Add graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Shutting down server...");
            stop();
        }));
        // ✅ END: Shutdown hook

        System.out.println("✅ Server running at http://localhost:4567");
    }



   private static List<Map<String, String>> getFlipkartResults(String query) throws Exception {
    List<Map<String, String>> results = new ArrayList<>();

    ChromeOptions options = new ChromeOptions();
    options.addArguments("--disable-blink-features=AutomationControlled");
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--window-size=1920,1080");
    options.addArguments("--headless=new");
    options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

    WebDriver driver = new ChromeDriver(options);

    try {
        String url = "https://www.flipkart.com/search?q=" + URLEncoder.encode(query, "UTF-8");
        System.out.println("🔍 Navigating to: " + url);
        driver.get(url);
        Thread.sleep(500);

        // Close login popup if appears
        try {
            WebElement closeBtn = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.elementToBeClickable(By.cssSelector("button._2KpZ6l._2doB4z")));
            closeBtn.click();
        } catch (TimeoutException ignored) {}

        List<WebElement> cards = driver.findElements(By.cssSelector("div[data-id]"));
        System.out.println("🧩 Flipkart card count: " + cards.size());

        for (WebElement card : cards) {
            try {
                String name = "", price = "", image = "", link = "";

                // --- Product Name & Link (Main layouts) ---
                try {
                    WebElement anchor = card.findElement(By.cssSelector(
                            "a._1fQZEK, a.IRpwTa, a.s1Q9rs, a.CGtC98, a.wjcEIp, a.rPDeLR"));
                    link = anchor.getAttribute("href");

                    // ✅ Normalize Flipkart links
                    if (link != null) {
                        if (link.startsWith("//")) {
                            link = "https:" + link;
                        } else if (link.startsWith("/")) {
                            link = "https://www.flipkart.com" + link;
                        } else if (link.contains("https//")) { // Fix missing colon
                            link = link.replace("https//", "https://");
                        }
                    }

                    // Debug log for link
                    System.out.println("🔗 Final Flipkart link: " + link);

                    // Try text directly in anchor
                    name = anchor.getText().trim();

                    // Remove common unwanted suffixes
                    name = name.replaceAll(
                            "(?i),?\\s*(Add|to|Bestseller|compare|with|warranty|year|free|offer|exchange|no cost|emi|bank).*",
                            "");

                    // Clean name: only take first line or remove keywords
                    String[] lines = name.split("\n");
                    name = lines[0].trim();
                    if (lines.length > 1) {
                        name = lines[1].trim();
                    }
                    name = name.replaceAll("\\s{2,}", " ").trim();

                    // If anchor has no name (jeans, dresses), use title inside div
                    if (name.isEmpty()) {
                        try {
                            name = card.findElement(By.cssSelector("a.WKTcLC")).getText().trim();
                        } catch (NoSuchElementException e) {
                            try {
                                name = card.findElement(By.cssSelector("div.syl9yP")).getText().trim();
                            } catch (NoSuchElementException ignored) {}
                        }
                    }
                } catch (NoSuchElementException ignored) {}

                // --- Price (various containers) ---
                try {
                    price = card.findElement(By.cssSelector(
                            "div._30jeq3, div._4b5DiR, div.Nx9bqj, div._25b18c, div.hl05eU .Nx9bqj")).getText().trim();
                } catch (NoSuchElementException ignored) {}

                // --- Image ---
                try {
                    image = card.findElement(By.cssSelector("img")).getAttribute("src");
                } catch (NoSuchElementException ignored) {}

                // --- Final Save ---
                if (!name.isEmpty() && !price.isEmpty() && !link.isEmpty()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("name", name);
                    item.put("price", price);
                    item.put("image", image);
                    item.put("link", link);
                    item.put("company", "Flipkart");
                    results.add(item);
                }

                if (results.size() >= 6) break;

            } catch (Exception e) {
                System.out.println("❌ Error parsing one card: " + e.getMessage());
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
        throw new Exception("Flipkart scraping failed", e);
    } finally {
        driver.quit();
    }

    return results;
}




    // ✅ eBay API Fetch
    private static List<Map<String, String>> getEbayResults(String query) {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            String clientId = "karthike-pricebot-SBX-80bd30580-16067e09";
            String clientSecret = "SBX-0bd305808981-d6f4-4fdc-a5ce-66b2";
            String accessToken = getAccessToken(clientId, clientSecret);
            if (accessToken == null) return list;

            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String apiUrl = "https://api.sandbox.ebay.com/buy/browse/v1/item_summary/search?q=" + encodedQuery + "&filter=buyerCountry:US";

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Language", "en-US");

            InputStream in = conn.getInputStream();
            String json = new String(in.readAllBytes());
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            for (JsonElement item : obj.getAsJsonArray("itemSummaries")) {
                JsonObject i = item.getAsJsonObject();
                String title = i.get("title").getAsString();
                if (!isEnglish(title)) continue;

                Map<String, String> map = new HashMap<>();
                map.put("name", title);
                map.put("price", i.has("price") ? i.getAsJsonObject("price").get("value").getAsString() : "N/A");
                map.put("link", i.get("itemWebUrl").getAsString());
                map.put("image", i.has("image") ? i.getAsJsonObject("image").get("imageUrl").getAsString() : "");
                map.put("company", "eBay");
                list.add(map);
                 if (list.size() >= 6) break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static String getAccessToken(String clientId, String clientSecret) {
        try {
            String basicAuth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
            HttpURLConnection tokenConn = (HttpURLConnection) new URL("https://api.sandbox.ebay.com/identity/v1/oauth2/token").openConnection();
            tokenConn.setRequestMethod("POST");
            tokenConn.setDoOutput(true);
            tokenConn.setRequestProperty("Authorization", "Basic " + basicAuth);
            tokenConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String body = "grant_type=client_credentials&scope=https://api.ebay.com/oauth/api_scope";
            try (OutputStream os = tokenConn.getOutputStream()) {
                os.write(body.getBytes());
            }

            if (tokenConn.getResponseCode() == 200) {
                String tokenJson = new String(tokenConn.getInputStream().readAllBytes());
                return JsonParser.parseString(tokenJson).getAsJsonObject().get("access_token").getAsString();
            } else {
                System.err.println("❌ Failed to get eBay token: HTTP " + tokenConn.getResponseCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isEnglish(String text) {
        return text != null && text.matches("^[\\p{ASCII}\\p{IsLatin}0-9 \\p{Punct}]+$");
    }
    
// ✅ Final Reliance Scraper with network/rendering fixes
private static List<Map<String, String>> getRelianceResults(String query) throws Exception {
    List<Map<String, String>> results = new ArrayList<>();

    // Keywords that use /collection/ path
    Set<String> collectionKeywords = Set.of("tv", "television", "mobile", "smartphone", "laptop");

    String lowerQuery = query.toLowerCase();
    boolean useCollection = collectionKeywords.stream().anyMatch(lowerQuery::contains);

    String url;
    if (useCollection) {
        String category = lowerQuery.contains("tv") ? "televisions" : "mobiles";
        url = "https://www.reliancedigital.in/collection/" + category + "?q=" + URLEncoder.encode(query, "UTF-8") + "&page_no=1&page_size=12&page_type=number";
    } else {
        url = "https://www.reliancedigital.in/products?q=" + URLEncoder.encode(query, "UTF-8") + "&page_no=1&page_size=12&page_type=number";
    }

    System.out.println("🔍 Navigating to: " + url);

    ChromeOptions options = new ChromeOptions();
    // 👉 Use headless for prod, comment it for debug
    options.addArguments("--headless=new");
    options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-blink-features=AutomationControlled");
    options.addArguments("--window-size=1920,1080");

    WebDriver driver = new ChromeDriver(options);
    driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(40));

    try {
        long start = System.currentTimeMillis();
        driver.get(url);
        long loadTime = System.currentTimeMillis() - start;
        System.out.println("⏱ Page load time: " + loadTime + "ms");

        // Wait for full DOM load
        new WebDriverWait(driver, Duration.ofSeconds(20))
            .until(webDriver -> ((JavascriptExecutor) webDriver)
            .executeScript("return document.readyState").equals("complete"));

        // Scroll to trigger lazy loading
        for (int i = 0; i < 3; i++) {
            ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 800);");
            Thread.sleep(800); // give time for content to render
        }

        List<WebElement> cards;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        if (useCollection) {
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("a.card-wrapper__body")));
            cards = driver.findElements(By.cssSelector("a.card-wrapper__body"));
        } else {
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("div.product-card")));
            cards = driver.findElements(By.cssSelector("div.product-card"));
        }

        System.out.println("🧩 Reliance card count: " + cards.size());

        for (WebElement card : cards) {
            try {
                String name = "", price = "", image = "", link = "";

                if (useCollection) {
                    name = card.findElement(By.cssSelector("h5.card-title")).getText().trim();
                    price = card.findElement(By.cssSelector("span.card-discount-price")).getText().trim();
                    WebElement img = card.findElement(By.cssSelector("img"));
                    image = img.getAttribute("src");
                    if (image == null || image.isEmpty()) image = img.getAttribute("data-src");
                    link = card.getAttribute("href");
                    if (!link.startsWith("http")) link = "https://www.reliancedigital.in" + link;

                } else {
                    name = card.findElement(By.cssSelector(".product-card-title")).getText().trim();
                    price = card.findElement(By.cssSelector(".price-container .price")).getText().trim();
                    WebElement img = card.findElement(By.cssSelector("a.product-card-image img"));
                    image = img.getAttribute("src");
                    if (image == null || image.isEmpty()) image = img.getAttribute("data-src");
                    WebElement linkElem = card.findElement(By.cssSelector("a.product-card-image"));
                    link = linkElem.getAttribute("href");
                    if (!link.startsWith("http")) link = "https://www.reliancedigital.in" + link;
                }

                if (!name.isEmpty() && !price.isEmpty() && !link.isEmpty()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("name", name);
                    item.put("price", price);
                    item.put("image", image);
                    item.put("link", link);
                    item.put("company", "Reliance Digital");
                    results.add(item);
                }

                if (results.size() >= 6) break;

            } catch (Exception e) {
                System.out.println("❌ Error parsing card: " + e.getMessage());
            }
        }

    } catch (Exception e) {
        System.out.println("❌ Final scraping exception: " + e.getMessage());
    } finally {
        driver.quit();
    }

    return results;
}



}
