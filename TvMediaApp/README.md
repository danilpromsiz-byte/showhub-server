# TV Cinema — Приложение для Android Smart TV (Google TV)

Современное медиа-приложение для просмотра фильмов и сериалов на телевизорах под управлением Android TV / Google TV.

---

## Особенности архитектуры и интерфейса

* **Разработано под пульт (D-pad):**
  * Все элементы интерфейса поддерживают навигацию стрелками пульта.
  * Плавное увеличение карточек (scale 1.1x) и акцентная подсветка рамки при наведении фокуса.
  * Интерактивный верхний Hero-баннер, динамически обновляющийся при перемещении фокуса по карточкам.
* **Стек технологий:**
  * **Jetpack Compose for TV:** `@Composable` компоненты из официальных библиотек `androidx.tv:tv-material` и `androidx.tv:tv-foundation`.
  * **AndroidX Media3 (ExoPlayer):** Потоковое воспроизведение видео (HLS `.m3u8` с адаптивным битрейтом и прямые потоки `.mp4`).
  * **Coil Compose:** Асинхронная загрузка и кэширование постеров и фонов.
  * **MVVM:** Реактивное состояние через `StateFlow` и `ViewModel`.

---

## Структура проекта

```
TvMediaApp/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml          # TV Leanback манифест
│       ├── java/com/example/tvmediaapp/
│       │   ├── MainActivity.kt          # Точка входа и навигация
│       │   ├── data/
│       │   │   ├── models/Movie.kt      # Модели фильмов и категорий
│       │   │   └── repository/CatalogRepository.kt # Каталог и потоки
│       │   └── ui/
│       │       ├── theme/               # Темная TV-тема (цвета, typography)
│       │       ├── components/
│       │       │   ├── MovieCard.kt     # Карточка с D-pad фокусом
│       │       │   └── FeaturedMovieBanner.kt # Hero-баннер
│       │       └── screens/
│       │           ├── home/HomeScreen.kt     # Главный каталог
│       │           ├── details/DetailsScreen.kt # Детали фильма
│       │           └── player/PlayerScreen.kt # Media3 видеоплеер
│       └── res/                         # Иконки, баннеры, цвета
├── gradle/
│   ├── libs.versions.toml               # Версии библиотек (Version Catalog)
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Управление в плеере с пульта TV

* **OK / Center:** Пауза / Воспроизведение
* **Влево (D-Pad Left):** Перемотка назад на 10 секунд
* **Вправо (D-Pad Right):** Перемотка вперед на 10 секунд
* **Назад (Back):** Возврат к экрану информации о фильме
* **Автоматическое скрытие:** Интерфейс плеера исчезает через 4 секунды просмотра

---

## Как собрать и запустить на ТВ

### 1. Открытие в Android Studio
1. Откройте **Android Studio**.
2. Выберите **File -> Open...** и укажите папку `c:\WORK\VID\TvMediaApp`.
3. Дождитесь автоматической синхронизации Gradle.

### 2. Сборка APK
В терминале Android Studio выполните:
```bash
./gradlew assembleDebug
```
Собранный файл будет находиться в:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Установка на Android TV по Wi-Fi
1. На телевизоре перейдите в:  
   **Настройки -> Настройки устройства -> Об устройстве -> Сборка** (нажмите 7 раз для включения режима разработчика).
2. В появившемся меню **Для разработчиков** включите **Отладка по сети (ADB over Wi-Fi)**.
3. Посмотрите IP-адрес телевизора в настройках сети (например, `192.168.1.120`).
4. На компьютере выполните:
   ```bash
   adb connect 192.168.1.120:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. Приложение появится в строке приложений Android TV с фирменным баннером.
