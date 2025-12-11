package hello1.koddata.client;

import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter implements Highlighter {
    private final Pattern VARNAME = Pattern.compile("\\$\\S+");

    private final Pattern KEYWORDS = Pattern.compile("\\S+");

    private final Pattern STRINGS =
            Pattern.compile("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\.[^'\\\\]*)*'");

    private final Pattern NUMBERS = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");


    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        boolean[] highlighted = new boolean[buffer.length()];

        // Mark all matches in priority order (don't build yet)
        markPattern(buffer, highlighted, STRINGS);
        markPattern(buffer, highlighted, NUMBERS);
        markPattern(buffer, highlighted, VARNAME);
        markPattern(buffer, highlighted, KEYWORDS);

        // Now build the output left to right
        int i = 0;
        while (i < buffer.length()) {
            if (highlighted[i]) {
                // Find the style for this highlighted region
                AttributedStyle style = getStyleAt(buffer, i);
                int start = i;
                while (i < buffer.length() && highlighted[i]) {
                    i++;
                }
                builder.styled(style, buffer.substring(start, i));
            } else {
                // Unhighlighted text
                int start = i;
                while (i < buffer.length() && !highlighted[i]) {
                    i++;
                }
                builder.append(buffer.substring(start, i));
            }
        }

        return builder.toAttributedString();
    }

    private void markPattern(String buffer, boolean[] highlighted, Pattern pattern) {
        Matcher matcher = pattern.matcher(buffer);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // Check if this region is already highlighted
            boolean alreadyHighlighted = false;
            for (int i = start; i < end; i++) {
                if (highlighted[i]) {
                    alreadyHighlighted = true;
                    break;
                }
            }

            if (!alreadyHighlighted) {
                // Mark this region as highlighted
                for (int i = start; i < end; i++) {
                    highlighted[i] = true;
                }
            }
        }
    }

    private AttributedStyle getStyleAt(String buffer, int pos) {
        // Check patterns in priority order
        if (matches(buffer, pos, STRINGS)) {
            return AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
        }
        if (matches(buffer, pos, NUMBERS)) {
            return AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
        }
        if (matches(buffer, pos, VARNAME)) {
            return AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
        }
        if (matches(buffer, pos, KEYWORDS)) {
            return AttributedStyle.BOLD.foreground(AttributedStyle.GREEN);
        }
        return AttributedStyle.DEFAULT;
    }

    private boolean matches(String buffer, int pos, Pattern pattern) {
        Matcher matcher = pattern.matcher(buffer);
        while (matcher.find()) {
            if (matcher.start() <= pos && pos < matcher.end()) {
                return true;
            }
        }
        return false;
    }
}
