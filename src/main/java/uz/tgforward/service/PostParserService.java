package uz.tgforward.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.tgforward.domain.ForwardConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ─────────────────────────────────────────────────────────
// PostParserService
// ─────────────────────────────────────────────────────────
@Service
@Slf4j
public class PostParserService {

    // FORMAT_A: "H119 1*4 26"  yoki  "KIDILO S10 2*6 45"
    private static final Pattern FORMAT_A = Pattern.compile(
        "^(?<model>[A-Za-z0-9][\\w\\-]*(?:\\s[\\w\\-]+)?)\\s+\\d+\\*\\d+\\s+(?<price>\\d+(?:[.,]\\d+)?)\\s*$",
        Pattern.MULTILINE
    );

    // FORMAT_B: "Kidilo D9 - 1*1*61"
    private static final Pattern FORMAT_B = Pattern.compile(
        "^(?<model>.+?)\\s+-\\s+\\d+\\*\\d+\\*(?<price>\\d+(?:[.,]\\d+)?)\\s*$",
        Pattern.MULTILINE
    );

    public List<ParsedProduct> parse(String text, ForwardConfig config) {
        if (text == null || text.isBlank()) return List.of();

        Pattern pattern = switch (config.getPatternType()) {
            case FORMAT_A -> FORMAT_A;
            case FORMAT_B -> FORMAT_B;
            case CUSTOM   -> Pattern.compile(config.getCustomPattern(), Pattern.MULTILINE);
        };

        List<ParsedProduct> result = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            try {
                double price = Double.parseDouble(m.group("price").replace(",", "."));
                result.add(new ParsedProduct(m.group("model").trim(), price));
            } catch (NumberFormatException e) {
                log.warn("Narx parse xatosi: '{}'", m.group("price"));
            }
        }
        return result;
    }

    public record ParsedProduct(String modelName, double originalPrice) {
        public double markedUpPrice(double markupPercent) {
            return Math.round(originalPrice * (1.0 + markupPercent / 100.0));
        }
        public String formatPrice(double price) {
            return price == Math.floor(price)
                ? String.valueOf((long) price)
                : String.valueOf(price);
        }
    }
}
