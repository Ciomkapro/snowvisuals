package pl.snowvisuals;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class SnowLanguage {

    public static final List<String> CODES =
            List.of("en", "pl", "ru", "de", "uk");

    private static final Map<String, String[]> TEXT =
            new HashMap<>();

    private SnowLanguage() {}

    static {

        // MENU

        add("Visuals|Wizualne|Визуалы|Visuell|Візуали");
        add("HUD|HUD|HUD|HUD|HUD");
        add("Settings|Ustawienia|Настройки|Einstellungen|Налаштування");
        add("Language|Język|Язык|Sprache|Мова");
        add("Select language|Wybierz język|Выберите язык|Sprache auswählen|Виберіть мову");
        add("Back|Powrót|Назад|Zurück|Назад");

        // MODULES

        add("Player Glow|Podświetlenie graczy|Подсветка игроков|Spielerumrandung|Підсвічування гравців");
        add("Visible target only|Tylko widoczny cel|Только видимая цель|Nur sichtbares Ziel|Лише видима ціль");
        add("Show Coords|Pokaż koordynaty|Показать координаты|Koordinaten anzeigen|Показати координати");
        add("Show FPS|Pokaż FPS|Показать FPS|FPS anzeigen|Показати FPS");
        add("Show Server IP|Pokaż IP serwera|Показать IP сервера|Server-IP anzeigen|Показати IP сервера");
        add("OUTLINE COLOR|KOLOR OBRYSU|ЦВЕТ КОНТУРА|KONTURFARBE|КОЛІР КОНТУРУ");

        // GUI SETTINGS

        add("Appearance|Wygląd|Внешний вид|Aussehen|Зовнішній вигляд");
        add("Black|Czarny|Чёрный|Schwarz|Чорний");
        add("White|Biały|Белый|Weiß|Білий");
        add("Scale|Skala|Масштаб|Größe|Масштаб");
        add("GUI scale|Skala GUI|Масштаб GUI|GUI-Größe|Масштаб GUI");
        add("GUI transparency|Przezroczystość GUI|Прозрачность GUI|GUI-Transparenz|Прозорість GUI");
        add("GUI keybind|Klawisz GUI|Клавиша GUI|GUI-Taste|Клавіша GUI");
        add("Press a key...|Naciśnij klawisz...|Нажмите клавишу...|Taste drücken...|Натисніть клавішу...");
        add("Reset GUI settings|Resetuj ustawienia GUI|Сбросить настройки GUI|GUI zurücksetzen|Скинути налаштування GUI");
        add("Reset HUD|Resetuj HUD|Сбросить HUD|HUD zurücksetzen|Скинути HUD");
        add("ACTIVE|AKTYWNE|АКТИВНЫХ|AKTIV|АКТИВНИХ");

        // HUD

        add("Singleplayer|Gra jednoosobowa|Одиночная игра|Einzelspieler|Одиночна гра");

        // PROFILES

        add("Default|Domyślny|По умолчанию|Standard|Типовий");
        add("Survival|Przetrwanie|Выживание|Überleben|Виживання");
        add("Error|Błąd|Ошибка|Fehler|Помилка");

        // KEYBOARD

        add("Right Shift|Prawy Shift|Правый Shift|Rechte Umschalttaste|Правий Shift");
        add("Left Shift|Lewy Shift|Левый Shift|Linke Umschalttaste|Лівий Shift");
        add("Right Ctrl|Prawy Ctrl|Правый Ctrl|Rechte Strg-Taste|Правий Ctrl");
        add("Left Ctrl|Lewy Ctrl|Левый Ctrl|Linke Strg-Taste|Лівий Ctrl");
        add("Right Alt|Prawy Alt|Правый Alt|Rechte Alt-Taste|Правий Alt");
        add("Left Alt|Lewy Alt|Левый Alt|Linke Alt-Taste|Лівий Alt");
        add("Space|Spacja|Пробел|Leertaste|Пробіл");
        add("Key|Klawisz|Клавиша|Taste|Клавіша");
    }

    private static void add(String line) {

        String[] parts = line.split("\\|", -1);

        if (parts.length == 5) {
            TEXT.put(parts[0], parts);
        }
    }

    public static String normalize(String code) {

        return code != null && CODES.contains(code)
                ? code : "en";
    }

    public static String displayName(String code) {

        return switch (normalize(code)) {

            case "pl" -> "Polski";
            case "ru" -> "Русский";
            case "de" -> "Deutsch";
            case "uk" -> "Українська";

            default -> "English";
        };
    }

    public static String text(
            String language,
            String original
    ) {

        if (original == null) {
            return "";
        }

        int index = CODES.indexOf(
                normalize(language)
        );

        String[] translations = TEXT.get(original);

        if (translations == null) {
            return original;
        }

        return translations[index];
    }
}