package uz.tgforward.service;

import org.springframework.stereotype.Service;
import uz.tgforward.domain.ForwardConfig;
import uz.tgforward.service.PostParserService.ParsedProduct;

import java.util.List;

// ─────────────────────────────────────────────────────────
// PostTextBuilder
// ─────────────────────────────────────────────────────────
@Service
class PostTextBuilder {

    public String build(List<ParsedProduct> products, ForwardConfig config) {
        StringBuilder sb = new StringBuilder();
        if (hasText(config.getHeaderText())) sb.append(config.getHeaderText().trim()).append("\n\n");
        for (ParsedProduct p : products) {
            double newPrice = p.markedUpPrice(config.getMarkupPercent());
            sb.append("<b>").append(escape(p.modelName())).append("</b>")
                    .append(" — ").append(p.formatPrice(newPrice)).append(" $\n");
        }
        if (hasText(config.getFooterText())) sb.append("\n").append(config.getFooterText().trim());
        return sb.toString().trim();
    }

    private boolean hasText(String s) { return s != null && !s.isBlank(); }
    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}


