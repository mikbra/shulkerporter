package dev.shulkerporter;

import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;

/**
 * Turns a box name into a channel key: case-sensitive, with colours, formatting and all
 * whitespace removed. "Gate 1" and "§cGate1" both become "Gate1"; "gate1" does not.
 */
final class Channels {

    private static final Pattern LEGACY_FORMATTING = Pattern.compile("§[0-9A-FK-ORXa-fk-orx]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    private Channels() {
    }

    static @Nullable String of(@Nullable Component name) {
        if (name == null) {
            return null;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(name);
        String key = WHITESPACE.matcher(LEGACY_FORMATTING.matcher(plain).replaceAll("")).replaceAll("");
        return key.isEmpty() ? null : key;
    }

    static @Nullable String of(String name) {
        return of(Component.text(name));
    }
}
