package pl.snowvisuals;

import java.util.HashMap;
import java.util.Map;

public final class SnowWords {

    private static final Map<String, String[]> WORDS =
            new HashMap<>();

    private SnowWords() {}

    static {

        // CATEGORIES

        add("Camera|Kamera|Камера|Kamera|Камера");
        add("Other|Inne|Другое|Sonstiges|Інше");
        add("Profiles|Profile|Профили|Profile|Профілі");
        add("Workspace|Menu|Меню|Menü|Меню");
        add("Modules|Moduły|Модули|Module|Модулі");

        // HUD MODULES

        add("Armor HUD|Stan zbroi|Состояние брони|Rüstung|Стан броні");
        add("Potion HUD|Efekty mikstur|Эффекты зелий|Trankeffekte|Ефекти зілля");
        add("Playtime|Czas gry|Время игры|Spielzeit|Час гри");
        add("Movement Speed|Prędkość|Скорость|Geschwindigkeit|Швидкість");
        add("Ping Display|Pokaż ping|Показать пинг|Ping anzeigen|Показати пінг");
        add("CPS Counter|Licznik CPS|Счётчик CPS|CPS-Zähler|Лічильник CPS");
        add("Keystrokes|Klawisze|Клавиши|Tasten|Клавіші");

        // VISUAL MODULES

        add("Custom Crosshair|Własny celownik|Свой прицел|Fadenkreuz|Власний приціл");
        add("Low Fire|Niski ogień|Низкий огонь|Niedriges Feuer|Низький вогонь");
        add("Hit Particles|Cząsteczki trafień|Частицы ударов|Trefferpartikel|Частинки ударів");
        add("Player Trails|Ślady gracza|Следы игрока|Spielerspuren|Сліди гравця");
        add("Item Physics|Fizyka przedmiotów|Физика предметов|Itemphysik|Фізика предметів");
        add("Custom Cape|Peleryna|Плащ|Umhang|Плащ");
        add("Custom Chat|Własny czat|Свой чат|Chat-Design|Власний чат");

        add("Toggle Sprint|Sprint przełączany|Переключение спринта|Sprint umschalten|Перемикання спринту");
        add("Toggle Sneak|Skradanie przełączane|Переключение скрытности|Schleichen umschalten|Перемикання присідання");
        add("Better CPS HUD|Rozszerzony licznik CPS|Расширенный счётчик CPS|Erweiterte CPS-Anzeige|Розширений лічильник CPS");
        add("Combo Counter|Licznik serii trafień|Счётчик комбо|Kombozähler|Лічильник комбо");
        add("Hit Marker|Znacznik trafienia|Маркер попадания|Trefferanzeige|Позначка влучання");
        add("Damage Tint|Kolor obrażeń|Оттенок урона|Schadensfarbe|Відтінок пошкодження");
        add("Pearl Cooldown HUD|Czas odnowienia perły|Перезарядка жемчуга|Perlenabklingzeit|Перезаряджання перлини");
        add("Totem Counter|Licznik totemów|Счётчик тотемов|Totemzähler|Лічильник тотемів");
        add("Arrow Counter|Licznik strzał|Счётчик стрел|Pfeilzähler|Лічильник стріл");
        add("Bow Charge Indicator|Naciąg łuku|Натяжение лука|Bogenspannung|Натяг лука");
        add("Target HUD|Panel celu|Панель цели|Zielanzeige|Панель цілі");
        add("Ping Graph|Wykres pingu|График пинга|Ping-Verlauf|Графік пінгу");
        add("Direction / Yaw HUD|Kierunek i kąt obrotu|Направление и угол|Richtung und Winkel|Напрямок і кут");
        add("Fullbright|Pełna jasność|Полная яркость|Volle Helligkeit|Повна яскравість");
        add("Item Count HUD|Licznik przedmiotów|Счётчик предметов|Gegenstandszähler|Лічильник предметів");
        add("Reset in menus|Reset w menu|Сброс в меню|Im Menü zurücksetzen|Скидання в меню");
        add("Window|Okno pomiaru|Интервал|Messfenster|Вікно вимірювання");
        add("Buttons|Przyciski|Кнопки|Tasten|Кнопки");
        add("Both|Oba|Оба|Beide|Обидві");
        add("Left|Lewy|Левая|Links|Ліва");
        add("Right|Prawy|Правая|Rechts|Права");
        add("Show peak|Pokaż rekord|Показать максимум|Bestwert anzeigen|Показати максимум");
        add("Reset after|Reset po|Сброс через|Zurücksetzen nach|Скидання через");
        add("Hide when idle|Ukryj bez aktywności|Скрывать без действий|Bei Inaktivität ausblenden|Ховати без дій");
        add("Duration|Czas trwania|Длительность|Dauer|Тривалість");
        add("Marker size|Rozmiar znacznika|Размер маркера|Markergröße|Розмір позначки");
        add("Strength|Intensywność|Интенсивность|Stärke|Інтенсивність");
        add("Show ready|Pokaż stan gotowości|Показать готовность|Bereitschaft anzeigen|Показати готовність");
        add("Progress bar|Pasek postępu|Полоса прогресса|Fortschrittsbalken|Смуга прогресу");
        add("Hide when empty|Ukryj przy zerze|Скрывать при нуле|Bei null ausblenden|Ховати при нулі");
        add("Include special arrows|Uwzględnij strzały specjalne|Учитывать особые стрелы|Spezialpfeile zählen|Враховувати особливі стріли");
        add("Display|Wyświetlanie|Отображение|Anzeige|Відображення");
        add("Bar|Pasek|Полоса|Balken|Смуга");
        add("Hold time|Czas pokazywania celu|Время показа цели|Anzeigedauer|Час показу цілі");
        add("Health bar|Pasek życia|Полоса здоровья|Lebensleiste|Смуга здоров’я");
        add("Include mobs|Uwzględnij moby|Учитывать мобов|Kreaturen einbeziehen|Враховувати мобів");
        add("Samples|Próbki|Замеры|Messwerte|Виміри");
        add("Show graph|Pokaż wykres|Показать график|Verlauf anzeigen|Показати графік");
        add("Show degrees|Pokaż stopnie|Показать градусы|Gradzahl anzeigen|Показати градуси");
        add("Show cardinal direction|Pokaż kierunek świata|Показать сторону света|Himmelsrichtung anzeigen|Показати сторону світу");
        add("Tracked items|Liczone przedmioty|Отслеживаемые предметы|Gezählte Gegenstände|Відстежувані предмети");
        add("Pearls|Perły|Жемчуг|Perlen|Перлини");
        add("Golden apples|Złote jabłka|Золотые яблоки|Goldene Äpfel|Золоті яблука");
        add("Enchanted apples|Zaklęte jabłka|Зачарованные яблоки|Verzauberte Äpfel|Зачаровані яблука");
        add("Totems|Totemy|Тотемы|Totems|Тотеми");
        add("Arrows|Strzały|Стрелы|Pfeile|Стріли");
        add("Peak|Rekord|Макс.|Bestwert|Макс.");
        add("Combo|Seria|Комбо|Kombo|Комбо");
        add("Ready|Gotowa|Готово|Bereit|Готово");
        add("Select tracked items|Wybierz liczone przedmioty|Выберите предметы для подсчёта|Gegenstände zum Zählen wählen|Виберіть предмети для підрахунку");

        add("Particle count|Liczba cząstek|Число частиц|Partikelanzahl|Кількість частинок");
        add("Particle shape|Kształt cząstek|Форма частиц|Partikelform|Форма частинок");
        add("Mixed|Mieszane|Смешанные|Gemischt|Змішані");
        add("Snowflakes|Śnieżynki|Снежинки|Schneeflocken|Сніжинки");
        add("Pluses|Krzyżyki|Крестики|Pluszeichen|Плюсики");
        add("Snowflake color|Kolor śnieżynek|Цвет снежинок|Schneeflockenfarbe|Колір сніжинок");
        add("Plus color|Kolor krzyżyków|Цвет крестиков|Farbe der Pluszeichen|Колір плюсиків");
        add("Trail color|Kolor śladu|Цвет следа|Spurfarbe|Колір сліду");
        add("Trail lifetime|Czas zanikania śladu|Время исчезновения следа|Lebensdauer der Spur|Час згасання сліду");
        add("Trail width|Szerokość śladu|Ширина следа|Spurbreite|Ширина сліду");
        add("Trail height|Wysokość śladu|Высота следа|Spurhöhe|Висота сліду");
        add("China Hat|Stożkowy kapelusz|Коническая шляпа|Kegelhut|Конічний капелюх");
        add("Hat size|Rozmiar kapelusza|Размер шляпы|Hutgröße|Розмір капелюха");
        add("Hat color|Kolor kapelusza|Цвет шляпы|Hutfarbe|Колір капелюха");
        add("Choose color|Wybierz kolor|Выбрать цвет|Farbe wählen|Вибрати колір");
        add("Done|Gotowe|Готово|Fertig|Готово");

        add("HUD color|Kolor HUD-u|Цвет HUD|HUD-Farbe|Колір HUD");
        add("White|Biały|Белый|Weiß|Білий");
        add("Black|Czarny|Чёрный|Schwarz|Чорний");
        add("New profile|Nowy profil|Новый профиль|Neues Profil|Новий профіль");
        add("Profile name|Nazwa profilu|Имя профиля|Profilname|Назва профілю");
        add("Create|Utwórz|Создать|Erstellen|Створити");
        add("Edit|Edytuj|Изменить|Bearbeiten|Редагувати");
        add("Rename|Zmień nazwę|Переименовать|Umbenennen|Перейменувати");
        add("Update profile|Zapisz ustawienia|Обновить профиль|Profil aktualisieren|Оновити профіль");
        add("Use|Użyj|Применить|Anwenden|Застосувати");
        add("Delete profile|Usuń profil|Удалить профиль|Profil löschen|Видалити профіль");
        add("Delete|Usuń|Удалить|Löschen|Видалити");
        add("Cancel|Anuluj|Отмена|Abbrechen|Скасувати");
        add("Profile saved|Zapisano profil|Профиль сохранён|Profil gespeichert|Профіль збережено");
        add("Profile loaded|Wczytano profil|Профиль загружен|Profil geladen|Профіль завантажено");
        add("Profile renamed|Zmieniono nazwę|Профиль переименован|Profil umbenannt|Профіль перейменовано");
        add("Profile deleted|Usunięto profil|Профиль удалён|Profil gelöscht|Профіль видалено");
        add("Profile action failed|Nie udało się zmienić profilu|Ошибка профиля|Profilaktion fehlgeschlagen|Не вдалося змінити профіль");
        add("No profiles yet|Nie ma jeszcze profili|Пока нет профилей|Noch keine Profile|Профілів ще немає");
        add("Previous|Wstecz|Предыдущая|Zurück|Попередня");
        add("Next|Dalej|Следующая|Weiter|Наступна");

        add("GUI color|Kolor GUI|Цвет интерфейса|GUI-Farbe|Колір інтерфейсу");
        add("Save Profile|Zapisz profil|Сохранить профиль|Profil speichern|Зберегти профіль");
        add("Spotify Listener|Spotify Listener|Spotify Listener|Spotify Listener|Spotify Listener");
        add("Show lyrics (LRCLIB)|Pokaż tekst (LRCLIB)|Показать текст (LRCLIB)|Liedtext anzeigen (LRCLIB)|Показати текст (LRCLIB)");
        add("Open the Spotify app|Uruchom aplikację Spotify|Откройте приложение Spotify|Spotify-App öffnen|Відкрийте застосунок Spotify");
        add("Play / Pause|Odtwórz / wstrzymaj|Воспроизвести / пауза|Wiedergabe / Pause|Відтворити / пауза");
        add("Previous track|Poprzedni utwór|Предыдущий трек|Vorheriger Titel|Попередній трек");
        add("Next track|Następny utwór|Следующий трек|Nächster Titel|Наступний трек");
        add("Key used by GUI|Klawisz otwiera GUI|Клавиша открывает GUI|Taste öffnet die GUI|Клавіша відкриває GUI");

        add("Target marker|Znacznik gracza|Маркер игрока|Spielermarkierung|Позначка гравця");
        add("Marker range|Zasięg znacznika|Радиус маркера|Markierungsreichweite|Радіус позначки");
        add("Marker speed|Prędkość obrotu|Скорость вращения|Drehgeschwindigkeit|Швидкість обертання");
        add("Marker color|Kolor znacznika|Цвет маркера|Markierungsfarbe|Колір позначки");
        add("Circle|Koło|Круг|Kreis|Коло");
        add("Square|Kwadrat|Квадрат|Quadrat|Квадрат");
        add("Triangle|Trójkąt|Треугольник|Dreieck|Трикутник");
        add("Off|Wyłączony|Выключен|Aus|Вимкнено");

        // CAMERA MODULES

        add("ViewModel|Model ręki|Модель руки|Handmodell|Модель руки");
        add("Custom Zoom|Przybliżenie|Приближение|Zoom|Наближення");
        add("No Hurt Cam|Bez trzęsienia|Без тряски|Keine Kamerabewegung|Без тремтіння");
        add("Freelook|Wolna kamera|Свободный обзор|Freie Sicht|Вільний огляд");
        add("Custom FOV|Pole widzenia|Поле зрения|Sichtfeld|Поле зору");
        add("Aspect Ratio|Proporcje obrazu|Соотношение сторон|Seitenverhältnis|Співвідношення сторін");
        add("Actual FOV|Rzeczywisty FOV|Реальный FOV|Tatsächliches FOV|Реальний FOV");

        // MODULE SETTINGS

        add("Opacity|Krycie|Непрозрачность|Deckkraft|Непрозорість");
        add("Percent|Procenty|Проценты|Prozent|Відсотки");
        add("Uses|Użycia|Прочность|Haltbarkeit|Міцність");
        add("Durability|Wytrzymałość|Прочность|Haltbarkeit|Міцність");
        add("Enabled|Włączony|Включено|Aktiv|Увімкнено");
        add("Disabled|Wyłączony|Выключено|Inaktiv|Вимкнено");
        add("Length|Długość|Длина|Länge|Довжина");
        add("Gap|Odstęp|Зазор|Abstand|Проміжок");
        add("Thickness|Grubość|Толщина|Dicke|Товщина");
        add("Rotation|Obrót|Поворот|Drehung|Обертання");
        add("Color|Kolor|Цвет|Farbe|Колір");
        add("Shape|Kształt|Форма|Form|Форма");
        add("Custom|Własne|Своё|Benutzerdefiniert|Власне");

        // KEYBINDS

        add("Keybind|Klawisz|Клавиша|Taste|Клавіша");
        add("Unbound|Brak|Нет|Keine|Немає");

        add("Hold key|Przytrzymaj klawisz|Удерживайте клавишу|Taste halten|Утримуйте клавішу");

        add("Scroll Up|Scroll w górę|Колесо вверх|Scroll hoch|Колесо вгору");

        add("Scroll Down|Scroll w dół|Колесо вниз|Scroll runter|Колесо вниз");

        add("Middle Mouse|Kliknięcie rolką|Средняя кнопка|Mittlere Maustaste|Середня кнопка");

        add("Press a key or scroll|Naciśnij klawisz lub scroll|Нажмите клавишу или колесо|Taste oder Scrollrad|Натисніть клавішу або колесо");

        add("Clear bind|Usuń bind|Удалить клавишу|Taste entfernen|Видалити клавішу");

        // GENERAL SETTINGS

        add("HUD Snap|Siatka HUD|Сетка HUD|HUD-Raster|Сітка HUD");

        add("Font|Czcionka|Шрифт|Schrift|Шрифт");

        add("Accent color|Kolor akcentu|Цвет акцента|Akzentfarbe|Колір акценту");

        add("HUD background|Tło HUD|Фон HUD|HUD-Hintergrund|Фон HUD");

        add("Light|Jasny|Светлый|Hell|Світлий");
        add("Dark|Ciemny|Тёмный|Dunkel|Темний");

        // SEARCH

        add("Search modules...|Szukaj modułów...|Поиск модулей...|Module suchen...|Пошук модулів...");

        add("No modules found|Nie znaleziono modułów|Модули не найдены|Keine Module gefunden|Модулі не знайдено");

        add("Configure|Ustawienia|Настроить|Konfigurieren|Налаштувати");

        add("Right click to configure|PPM: ustawienia|ПКМ: настройки|Rechtsklick: Einstellungen|ПКМ: налаштування");

        // PROFILES

        add("Save|Zapisz|Сохранить|Speichern|Зберегти");
        add("Load|Wczytaj|Загрузить|Laden|Завантажити");
        add("Empty|Pusty|Пусто|Leer|Порожньо");
        add("Saved|Zapisano|Сохранено|Gespeichert|Збережено");
        add("Loaded|Wczytano|Загружено|Geladen|Завантажено");

        // HUD TITLES

        add("Potions|Mikstury|Зелья|Tränke|Зілля");

        add("Armor|Zbroja|Броня|Rüstung|Броня");

        add("Coordinates|Koordynaty|Координаты|Koordinaten|Координати");

        add("Server|Serwer|Сервер|Server|Сервер");

        add("No effects|Brak efektów|Нет эффектов|Keine Effekte|Немає ефектів");

        // MODULE DESCRIPTIONS

        add("Customize your HUD|Personalizuj HUD|Настройка HUD|HUD anpassen|Налаштування HUD");

        add("Customize your camera|Personalizuj kamerę|Настройка камеры|Kamera anpassen|Налаштування камери");

        add("Customize visual effects|Personalizuj efekty|Настройка эффектов|Effekte anpassen|Налаштування ефектів");
    }

    private static void add(String line) {

        String[] parts = line.split("\\|", -1);

        if (parts.length == 5) {

            WORDS.put(parts[0], parts);
        }
    }

    public static String tr(String text) {

        if (text == null) {

            return "";
        }

        SnowConfig config =
                SnowVisualsClient.CONFIG;

        if (config == null) {

            return text;
        }

        String language =
                SnowLanguage.normalize(config.language);

        int index =
                SnowLanguage.CODES.indexOf(language);

        String[] translations =
                WORDS.get(text);

        if (translations != null
                && index >= 0
                && index < translations.length) {

            return translations[index];
        }

        return SnowLanguage.text(
                language,
                text
        );
    }
}