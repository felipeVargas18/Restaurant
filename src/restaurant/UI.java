package restaurant;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;

/** Utilidades de UI para CLI. */
public final class UI {
    private UI() {}

    // Ancho "virtual" para centrar
    private static int COLS = 110;

    public static void setCols(int cols) { COLS = Math.max(40, Math.min(200, cols)); }

    // Limpiar pantalla (ANSI)
    public static void clear() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    // ---- helper Java 8: reemplaza "String.repeat" ----
    private static String rep(char ch, int n) {
        if (n <= 0) return "";
        char[] arr = new char[n];
        Arrays.fill(arr, ch);
        return new String(arr);
    }

    // Centrar una línea en COLS
    private static String center(String s, int width) {
        int pad = Math.max(0, (width - s.length()) / 2);
        return rep(' ', pad) + s;
    }

    // ===== Menú principal =====
    public static void printMainMenuPlain(String[] options) {
        int inner = longestLineLen(options) + 11;   // margen interno
        inner = Math.max(inner, 40);
        String top    = "┌" + rep('─', inner) + "┐";
        String empty  = "│" + rep(' ', inner) + "│";
        String bottom = "└" + rep('─', inner) + "┘";

        System.out.println(center(top, COLS));
        System.out.println(center(empty, COLS));
        for (int i = 0; i < options.length; i++) {
            String line = String.format("  %d) %s", i + 1, options[i]);
            int free = inner - (2 + line.length());
            if (free < 0) free = 0;
            String padded = "│  " + line + rep(' ', free) + "│";
            System.out.println(center(padded, COLS));
        }
        System.out.println(center(empty, COLS));
        System.out.println(center(bottom, COLS));
        System.out.print(center("> Selecciona: ", COLS));
    }

    // ===== Banner y tabla =====
    public static void banner(String title) {
        String line = rep('─', Math.max(10, Math.min(COLS - 4, title.length() + 4)));
        System.out.println(center("┌" + line + "┐", COLS));
        System.out.println(center("│ " + title + " │", COLS));
        System.out.println(center("└" + line + "┘", COLS));
    }

    public static void table(String[] header, List<String[]> rows) {
        int cols = header.length;
        int[] w = new int[cols];
        for (int j = 0; j < cols; j++) w[j] = header[j].length();
        for (String[] r : rows) for (int j = 0; j < cols; j++) if (r[j] != null) w[j] = Math.max(w[j], r[j].length());

        StringBuilder top = new StringBuilder("┌");
        StringBuilder mid = new StringBuilder("├");
        StringBuilder bot = new StringBuilder("└");
        for (int j = 0; j < cols; j++) {
            String dash = rep('─', w[j] + 2);
            top.append(dash).append(j == cols - 1 ? "┐" : "┬");
            mid.append(dash).append(j == cols - 1 ? "┤" : "┼");
            bot.append(dash).append(j == cols - 1 ? "┘" : "┴");
        }
        System.out.println(center(top.toString(), COLS));
        StringBuilder h = new StringBuilder("│");
        for (int j = 0; j < cols; j++) h.append(" ").append(pad(header[j], w[j])).append(" │");
        System.out.println(center(h.toString(), COLS));
        System.out.println(center(mid.toString(), COLS));
        for (String[] r : rows) {
            StringBuilder row = new StringBuilder("│");
            for (int j = 0; j < cols; j++) row.append(" ").append(pad(r[j], w[j])).append(" │");
            System.out.println(center(row.toString(), COLS));
        }
        System.out.println(center(bot.toString(), COLS));
    }

    // Mensajes simples
    public static void ok(String s)   { System.out.println(center("✔ " + s, COLS)); }
    public static void warn(String s) { System.out.println(center("⚠ " + s, COLS)); }
    public static void err(String s)  { System.out.println(center("✖ " + s, COLS)); }
    public static void info(String s) { System.out.println(center("ℹ " + s, COLS)); }

    // Utilidades
    public static List<String[]> rows() { return new ArrayList<>(); }
    public static String moneyCOP(long value) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(value);
    }
    private static String pad(String s, int w) { if (s == null) s = ""; return s.length() >= w ? s : s + rep(' ', w - s.length()); }
    private static int longestLineLen(String[] lines) { int m = 0; for (String s : lines) if (s != null) m = Math.max(m, s.length()); return m; }
}
