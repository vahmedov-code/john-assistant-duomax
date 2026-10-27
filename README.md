# John Assistant — Яндекс Станция Дуо Макс

Исходники исправленной тестовой версии 0.2-test (`moscow.avior.john`).
Основа: ранее подготовленный проект JohnAssistant и APK JohnAssistant-0.2-test.apk.
Добавлены Gradle Wrapper, правила Git и необязательная локальная подпись debug-сборки.

## Первый запуск в Windows PowerShell

Нужны Git, Android Studio, JDK 17, Android SDK Platform 34 и Build Tools 34.0.0.
Версии закреплены: Android Gradle Plugin 8.7.3, Gradle 8.9.
Первый запуск скачивает Gradle и зависимости; контрольная сумма Gradle закреплена в wrapper properties.

```powershell
cd "$HOME\AndroidStudioProjects"
git clone https://github.com/vahmedov-code/john-assistant-duomax.git JohnAssistant
cd JohnAssistant
```

Откройте эту папку в Android Studio и укажите JDK 17 в Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK.
Установите SDK Platform 34 и Build Tools 34.0.0 через SDK Manager.
Android Studio создаст локальный `local.properties` с путём к SDK.

В терминале PowerShell выберите JDK 17. На исходном компьютере он установлен здесь:

```powershell
$env:JAVA_HOME = "$HOME\.jdks\jbr-17.0.14"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat assembleDebug
```

Если JDK 17 находится в другой папке, измените JAVA_HOME. Системный JBR Android Studio может быть более новой версии: для этого проекта используйте JDK 17.
APK: `app\build\outputs\apk\debug\app-debug.apk`.
На Linux/macOS: `./gradlew assembleDebug` с JDK 17 и настроенным Android SDK.

## Получение обновлений

Из папки проекта в терминале Android Studio:

```powershell
git status
git pull --ff-only
if ($LASTEXITCODE -ne 0) { throw 'Обновление не выполнено. Проверьте сообщение Git.' }
.\gradlew.bat assembleDebug
```

Обычный `git pull` тоже работает: для подготовленной локальной копии включён режим fast-forward only.
Для свежего клона можно включить его один раз: `git config pull.ff only`.
Если истории разошлись, Git остановится и сохранит ваши коммиты.

## Обновление с незакоммиченными правками

Сначала проверьте `git status`, затем сохраните правки, включая новые файлы:

```powershell
git stash push --include-untracked -m "before-update"
```

Убедитесь, что Git сообщил о созданном stash. Если изменений нет, stash не создаётся — не применяйте старые записи автоматически.
Затем:

```powershell
git pull --ff-only
```

При ошибке остановитесь и разберите её. После успешного обновления найдите свою запись:

```powershell
git stash list
git stash apply 'stash@{0}'
git status
```

Используйте индекс именно созданной вами записи. `apply` сохраняет резервную запись stash.
Если возникли конфликты, исправьте отмеченные файлы в Android Studio и проверьте сборку.
Только после проверки можно удалить соответствующую резервную запись: `git stash drop 'stash@{0}'`.
Не используйте `git reset --hard` или `git clean -fd` для обычного обновления.
Игнорируемые настройки и ключи stash не сохраняет — храните их резервную копию отдельно.

Для постоянных доработок заведите ветку: `git switch -c my-changes`.
После проверенных коммитов обновляйте её через `git fetch origin`, затем `git merge origin/main`.
При конфликте исправьте файлы и завершите merge либо отмените его командой `git merge --abort`.

## Подпись и обновление установленного APK

Без `signing.properties` Gradle использует стандартный локальный debug-ключ Android.
Он может отличаться от подписи прежнего APK. При `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
нужен прежний ключ; не удаляйте приложение автоматически, иначе можно потерять его данные.

На компьютере, где подготовлен этот проект, прежний тестовый ключ сохранён локально в
`signing/john-test.keystore`, а параметры — в `signing.properties`. Оба пути исключены из Git.
Это обеспечивает прежнюю тестовую подпись для `assembleDebug`; проверяйте установку на станции.
В новом клоне этих файлов нет. Переносите ключ и параметры отдельно через доверенный канал.
Формат локального `signing.properties`:

```properties
storeFile=signing/your-existing-test.keystore
storePassword=YOUR_LOCAL_PASSWORD
keyAlias=YOUR_ALIAS
keyPassword=YOUR_LOCAL_PASSWORD
```

Этот ключ предназначен для тестов. Release-подпись и публичный выпуск здесь не настроены.
Ключи, пароли, токены, `.env`, APK, архивы и `local.properties` не коммитить.
Перед каждым коммитом проверяйте `git diff --cached` и `git status`.

## Реализованные возможности и ограничения

- Управление медиасессией пакета `ru.yandex.music`: play/pause/next/previous.
- Изменение громкости через Android AudioManager.
- Краткая проверка микрофона через AudioRecord без сохранения звука.
- Голосовая команда по кнопке через системный SpeechRecognizer, если он доступен в YaOS.
- NotificationListenerService и запрос переподключения после загрузки/обновления приложения.

Постоянная активация словом «Джон», собственный движок распознавания и постоянное фоновое
прослушивание не реализованы. Совместная работа микрофона с Алисой требует проверки на станции.
Успешная сборка не доказывает работу всех функций на YaOS.
Минимальная версия Android 8/API 26; compileSdk 34, targetSdk 32 сохранены из тестового проекта.

## Репозиторий

https://github.com/vahmedov-code/john-assistant-duomax

`git pull` получает только уже опубликованные коммиты. Изменения из чата должны сначала
быть внесены в проект и загружены в этот репозиторий.
Если репозиторий станет приватным, Git потребует вход через Git Credential Manager/Android Studio.
Пароль аккаунта GitHub для Git не используется; не вставляйте токен в URL remote.

Документация: [совместимость AGP 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes),
[добавление локального проекта в GitHub](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github).
