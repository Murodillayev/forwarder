package uz.tgforward.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.tgforward.domain.ForwardConfig;
import uz.tgforward.service.PostParserService.ParsedProduct;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostParserServiceTest {

    private PostParserService parser;

    @BeforeEach
    void setUp() { parser = new PostParserService(); }

    // FORMAT_A ──────────────────────────────────

    @Test @DisplayName("FORMAT_A: bitta mahsulot")
    void formatA_single() {
        List<ParsedProduct> r = parser.parse("H119 1*4 26", configA());
        assertThat(r).hasSize(1);
        assertThat(r.get(0).modelName()).isEqualTo("H119");
        assertThat(r.get(0).originalPrice()).isEqualTo(26.0);
    }

    @Test @DisplayName("FORMAT_A: bir nechta mahsulot")
    void formatA_multiple() {
        String text = "H119 1*4 26\nBXT-M1 1*2 31\nKIDILO S10 2*6 45";
        List<ParsedProduct> r = parser.parse(text, configA());
        assertThat(r).hasSize(3);
        assertThat(r.get(1).modelName()).isEqualTo("BXT-M1");
        assertThat(r.get(1).originalPrice()).isEqualTo(31.0);
    }

    @Test @DisplayName("FORMAT_A: ikki so'zli model")
    void formatA_twoWordModel() {
        List<ParsedProduct> r = parser.parse("KIDILO S10 2*6 45", configA());
        assertThat(r).hasSize(1);
        assertThat(r.get(0).modelName()).isEqualTo("KIDILO S10");
    }

    @Test @DisplayName("FORMAT_A: tegishsiz matn orasida ham ishlaydi")
    void formatA_withNoise() {
        String text = "Yangi keldi!\nH119 1*4 26\nBXT-M1 1*2 31\n@kanal";
        assertThat(parser.parse(text, configA())).hasSize(2);
    }

    // FORMAT_B ──────────────────────────────────

    @Test @DisplayName("FORMAT_B: bitta mahsulot")
    void formatB_single() {
        List<ParsedProduct> r = parser.parse("Kidilo D9 - 1*1*61", configB());
        assertThat(r).hasSize(1);
        assertThat(r.get(0).modelName()).isEqualTo("Kidilo D9");
        assertThat(r.get(0).originalPrice()).isEqualTo(61.0);
    }

    @Test @DisplayName("FORMAT_B: bir nechta + tavsif qatorlar orasida")
    void formatB_withDescription() {
        String text = """
            Kidilo D9 - 1*1*61
            2026 yilda trendi chiqishi kutilyotgan

            Kidilo C820 - 1*1*76
            Ranglari bor
            """;
        List<ParsedProduct> r = parser.parse(text, configB());
        assertThat(r).hasSize(2);
        assertThat(r.get(0).originalPrice()).isEqualTo(61.0);
        assertThat(r.get(1).originalPrice()).isEqualTo(76.0);
    }

    // Markup ────────────────────────────────────

    @Test @DisplayName("20% markup to'g'ri hisoblanadi")
    void markup_20percent() {
        // 26 * 1.20 = 31.2 → 31
        assertThat(new ParsedProduct("X", 26.0).markedUpPrice(20.0)).isEqualTo(31.0);
    }

    @Test @DisplayName("0% markup narxni o'zgartirmaydi")
    void markup_zero() {
        assertThat(new ParsedProduct("X", 50.0).markedUpPrice(0.0)).isEqualTo(50.0);
    }

    // Edge cases ────────────────────────────────

    @Test @DisplayName("Bo'sh matn — bo'sh ro'yxat")
    void empty() {
        assertThat(parser.parse("", configA())).isEmpty();
        assertThat(parser.parse(null, configA())).isEmpty();
    }

    @Test @DisplayName("Pattern mos kelmasa — bo'sh ro'yxat")
    void noMatch() {
        assertThat(parser.parse("Bu oddiy matn", configA())).isEmpty();
    }

    // Helpers ───────────────────────────────────

    private ForwardConfig configA() {
        return ForwardConfig.builder()
            .patternType(ForwardConfig.PatternType.FORMAT_A).markupPercent(20.0).build();
    }

    private ForwardConfig configB() {
        return ForwardConfig.builder()
            .patternType(ForwardConfig.PatternType.FORMAT_B).markupPercent(20.0).build();
    }
}
