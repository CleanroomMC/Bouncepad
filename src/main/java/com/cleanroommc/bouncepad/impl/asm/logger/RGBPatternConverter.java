package com.cleanroommc.bouncepad.impl.asm.logger;

import net.minecrell.terminalconsole.TerminalConsoleAppender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.PropertiesUtil;

import java.util.List;

import static net.minecrell.terminalconsole.MinecraftFormattingConverter.KEEP_FORMATTING_PROPERTY;
import static net.minecrell.terminalconsole.TerminalConsoleAppender.JLINE_OVERRIDE_PROPERTY;

@Plugin(name = RGBPatternConverter.PLUGIN_NAME, category = PatternConverter.CATEGORY)
@ConverterKeys({ RGBPatternConverter.PLUGIN_NAME })
@PerformanceSensitive("allocation")
public final class RGBPatternConverter extends LogEventPatternConverter {

    // TODO: Evaluate
    static {
        System.setProperty(JLINE_OVERRIDE_PROPERTY, "true");
    }

    public static final String PLUGIN_NAME = "rgbFormat";

    private static final boolean KEEP_FORMATTING = PropertiesUtil.getProperties().getBooleanProperty(KEEP_FORMATTING_PROPERTY);
    private static final String ANSI_RESET = "\u001B[m";
    private static final String COLOR_STR = "§";
    private static final String LOOKUP = "0123456789abcdefklmnor";
    private static final String RGB_ANSI = "\u001B[38;2;%d;%d;%dm";
    private static final String[] ANSI_CODES = new String[] {
            "\u001B[0;30m",    // Black §0
            "\u001B[0;34m",    // Dark Blue §1
            "\u001B[0;32m",    // Dark Green §2
            "\u001B[0;36m",    // Dark Aqua §3
            "\u001B[0;31m",    // Dark Red §4
            "\u001B[0;35m",    // Dark Purple §5
            "\u001B[0;33m",    // Gold §6
            "\u001B[0;37m",    // Gray §7
            "\u001B[0;30;1m",  // Dark Gray §8
            "\u001B[0;34;1m",  // Blue §9
            "\u001B[0;32;1m",  // Green §a
            "\u001B[0;36;1m",  // Aqua §b
            "\u001B[0;31;1m",  // Red §c
            "\u001B[0;35;1m",  // Light Purple §d
            "\u001B[0;33;1m",  // Yellow §e
            "\u001B[0;37;1m",  // White §f
            "\u001B[5m",       // Obfuscated §k
            "\u001B[21m",      // Bold §l
            "\u001B[9m",       // Strikethrough §m
            "\u001B[4m",       // Underline §n
            "\u001B[3m",       // Italic §o
            ANSI_RESET,        // Reset §r
    };

    /**
     * Gets a new instance of the {@link RGBPatternConverter} with the
     * specified options.
     *
     * @param config  The current configuration
     * @param options The pattern options
     * @return The new instance
     * @see RGBPatternConverter
     */
    public static RGBPatternConverter newInstance(Configuration config, String[] options) {
        if (options.length < 1 || options.length > 2) {
            LOGGER.error("Incorrect number of options on {}. Expected at least 1, max 2 received {}", RGBPatternConverter.PLUGIN_NAME, options.length);
            return null;
        }
        if (options[0] == null) {
            LOGGER.error("No pattern supplied on {}", RGBPatternConverter.PLUGIN_NAME);
            return null;
        }
        var parser = PatternLayout.createPatternParser(config);
        var formatters = parser.parse(options[0]);
        boolean strip = options.length > 1 && "strip".equals(options[1]);
        return new RGBPatternConverter(formatters, strip);
    }

    private final boolean ansi;
    private final PatternFormatter[] formatters;

    /**
     * Construct the converter.
     *
     * @param formatters The pattern formatters to generate the text to manipulate
     * @param strip      If true, the converter will strip all formatting codes
     */
    private RGBPatternConverter(List<PatternFormatter> formatters, boolean strip) {
        super("rgbFormat", null);
        this.formatters = formatters.toArray(PatternFormatter[]::new);
        this.ansi = !strip && TerminalConsoleAppender.isAnsiSupported();
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        int start = toAppendTo.length();
        for (var formatter : this.formatters) {
            formatter.format(event, toAppendTo);
        }
        if (KEEP_FORMATTING || toAppendTo.length() == start) {
            return;
        }
        this.format(toAppendTo);
    }

    private void format(StringBuilder toAppendTo) {
        int search = 0;
        int colorCharacter = toAppendTo.indexOf(COLOR_STR, search);
        int next = colorCharacter + 1;
        boolean reset = false;
        while (colorCharacter != -1 && next < toAppendTo.length()) {
            char nextChar = toAppendTo.charAt(next);
            int code = LOOKUP.indexOf(nextChar);
            if (code != -1) {
                if (this.ansi) {
                    if (!reset) {
                        reset = true;
                        toAppendTo.replace(colorCharacter, next + 1, ANSI_RESET + ANSI_CODES[code]);
                    } else {
                        toAppendTo.replace(colorCharacter, next + 1, ANSI_CODES[code]);
                    }
                } else {
                    toAppendTo.replace(colorCharacter, next + 1, "");
                }
                search++;
            } else if (nextChar == 'x') {
                int startBracket = toAppendTo.indexOf("[", next);
                if (startBracket != -1) {
                    int endBracket = toAppendTo.indexOf("]", startBracket);
                    if (endBracket != -1 && (endBracket - startBracket) == 7) {
                        var hex = toAppendTo.substring(startBracket + 1, endBracket);
                        int rgb = Integer.parseInt(hex, 16);
                        if (this.ansi) {
                            String rgbAnsi = String.format(RGB_ANSI, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                            toAppendTo.replace(colorCharacter, endBracket + 1, rgbAnsi);
                            search = (colorCharacter + rgbAnsi.length());
                        } else {
                            toAppendTo.replace(colorCharacter, endBracket + 1, "");
                            search++;
                        }
                    } else {
                        search++;
                    }
                } else {
                    search++;
                }
            } else {
                search++;
            }
            colorCharacter = toAppendTo.indexOf(COLOR_STR, search);
            next = colorCharacter + 1;
        }
        if (reset) {
            toAppendTo.append(ANSI_RESET);
        }
    }

}
