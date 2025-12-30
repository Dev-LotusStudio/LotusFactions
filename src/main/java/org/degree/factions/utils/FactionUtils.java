package org.degree.factions.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

public class FactionUtils {
    public String toSlug(String name) {
        if (name == null) return "faction-unknown";
        String slug = transliterateRu(name);
        slug = slug.replaceAll("[\\s.]+", "-");
        slug = slug.replaceAll("[^a-zA-Z0-9_-]", "");
        slug = slug.toLowerCase();
        slug = slug.replaceAll("[-_]{2,}", "-");
        slug = slug.replaceAll("^[-_]+|[-_]+$", "");
        if (slug.isBlank()) {
            slug = "faction-" + crc32Hex(name);
        }
        return slug;
    }

    public @NotNull TextComponent getFactionLink(String encoded) {
        encoded = toSlug(encoded);
        String url = "https://lotuscraft.fun/factions/" + encoded;

        TextComponent link = new TextComponent(url);
        link.setColor(ChatColor.BLUE);
        link.setUnderlined(true);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        return link;
    }

    private String transliterateRu(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            out.append(transliterateRuChar(ch));
        }
        return out.toString();
    }

    private String transliterateRuChar(char ch) {
        switch (ch) {
            case 'А': return "A";
            case 'Б': return "B";
            case 'В': return "V";
            case 'Г': return "G";
            case 'Д': return "D";
            case 'Е': return "E";
            case 'Ё': return "E";
            case 'Ж': return "Zh";
            case 'З': return "Z";
            case 'И': return "I";
            case 'Й': return "Y";
            case 'К': return "K";
            case 'Л': return "L";
            case 'М': return "M";
            case 'Н': return "N";
            case 'О': return "O";
            case 'П': return "P";
            case 'Р': return "R";
            case 'С': return "S";
            case 'Т': return "T";
            case 'У': return "U";
            case 'Ф': return "F";
            case 'Х': return "Kh";
            case 'Ц': return "Ts";
            case 'Ч': return "Ch";
            case 'Ш': return "Sh";
            case 'Щ': return "Shch";
            case 'Ъ': return "";
            case 'Ы': return "Y";
            case 'Ь': return "";
            case 'Э': return "E";
            case 'Ю': return "Yu";
            case 'Я': return "Ya";
            case 'а': return "a";
            case 'б': return "b";
            case 'в': return "v";
            case 'г': return "g";
            case 'д': return "d";
            case 'е': return "e";
            case 'ё': return "e";
            case 'ж': return "zh";
            case 'з': return "z";
            case 'и': return "i";
            case 'й': return "y";
            case 'к': return "k";
            case 'л': return "l";
            case 'м': return "m";
            case 'н': return "n";
            case 'о': return "o";
            case 'п': return "p";
            case 'р': return "r";
            case 'с': return "s";
            case 'т': return "t";
            case 'у': return "u";
            case 'ф': return "f";
            case 'х': return "kh";
            case 'ц': return "ts";
            case 'ч': return "ch";
            case 'ш': return "sh";
            case 'щ': return "shch";
            case 'ъ': return "";
            case 'ы': return "y";
            case 'ь': return "";
            case 'э': return "e";
            case 'ю': return "yu";
            case 'я': return "ya";
            default: return String.valueOf(ch);
        }
    }

    private String crc32Hex(String input) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        crc.update(bytes, 0, bytes.length);
        return Long.toHexString(crc.getValue());
    }
}
