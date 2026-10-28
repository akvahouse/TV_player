# TV_player

Плеєр для Android TV у стилі Apple TV (рідке скло), що бере каталог мультфільмів і серіалів з uaserial.tv. Описи серій «Сімпсонів», «Футурами», «Гріфінів» і «Південного парку» беруться з simpsonsua.tv.

Для особистого використання.

## Збирання

Потрібні JDK 17 і Android SDK (шлях до SDK вказується в `local.properties`, файл не в репозиторії):

```bash
./gradlew assembleDebug
```

## Встановлення на телевізор

```bash
adb connect <IP телевізора>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Тести

Тести в `app/src/test` ходять на живі сайти й перевіряють, що парсер ще відповідає їхній розмітці:

```bash
./gradlew testDebugUnitTest
```

## Шрифти

- **Exo 2** і **Montserrat** поширюються за [SIL Open Font License](licenses/) і лежать у `res/font`. Android 11 не вміє міняти вагу змінних шрифтів, тому з них нарізано окремі файли на кожну вагу (лише латиниця й кирилиця): `tools/instance_fonts.py`.
- **Univia Pro** безкоштовний лише для особистого використання, тому його в репозиторії немає. Щоб він з'явився в налаштуваннях, завантажте шрифт з fontlot.com і доповніть українськими літерами (і, ї, є, ґ):

  ```bash
  python tools/patch_univia.py <папка з UniviaPro-*.otf> app/src/main/res/font
  ```

  Без цих файлів застосунок збирається й працює, просто Univia Pro не з'являється серед шрифтів.

Скриптам потрібен `fonttools` (`pip install fonttools`).
