package fr.merchanttalk.web;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Convertit un composant Adventure en HTML (couleurs, gras, italique...) pour l'aperçu du site.
 * Le rendu passe par le vrai parseur MiniMessage : dégradés et couleurs hexadécimales compris.
 */
final class HtmlRenderer {
    private HtmlRenderer() {
    }

    static String render(Component component) {
        StringBuilder sb = new StringBuilder();
        append(sb, component, Style.empty());
        return sb.toString();
    }

    private static void append(StringBuilder sb, Component c, Style parent) {
        Style style = c.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        String text = switch (c) {
            case TextComponent t -> t.content();
            case TranslatableComponent t -> t.fallback() != null ? t.fallback() : t.key();
            default -> "";
        };
        if (!text.isEmpty()) {
            StringBuilder css = new StringBuilder();
            TextColor color = style.color();
            if (color != null) css.append("color:").append(color.asHexString()).append(';');
            if (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) css.append("font-weight:700;");
            if (style.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE) css.append("font-style:italic;");
            boolean under = style.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE;
            boolean strike = style.decoration(TextDecoration.STRIKETHROUGH) == TextDecoration.State.TRUE;
            if (under || strike) {
                css.append("text-decoration:").append(under ? "underline " : "").append(strike ? "line-through" : "").append(';');
            }
            boolean obf = style.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE;
            sb.append("<span");
            if (!css.isEmpty()) sb.append(" style=\"").append(css).append('"');
            if (obf) sb.append(" class=\"obf\"");
            sb.append('>').append(escape(text)).append("</span>");
        }
        for (Component child : c.children()) append(sb, child, style);
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
