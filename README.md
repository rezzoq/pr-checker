# PR Checker

Локальная Java CLI-утилита: получает diff через Git, выполняет простые проверки и отправляет изменения LLM. Поддерживаются локальная Ollama и OpenAI-совместимые API (например, Groq и Gemini); обе реализации подключены через интерфейс `LlmClient`. Источник diff задаётся интерфейсом `DiffSource`.

## Запуск

Требуются JDK 21+, Maven и Git. Из корня репозитория:

```shell
mvn test
mvn exec:java -Dexec.args="main"
mvn exec:java -Dexec.args="main review.md"
```

Аргументы: базовая ветка (по умолчанию `main`) и необязательный путь отчёта. Без пути отчёт выводится в консоль.

Если заданы все три переменные `LLM_BASE_URL`, `LLM_MODEL` и `LLM_API_KEY`, используется OpenAI-совместимый клиент (`/chat/completions`). Это подходит для провайдеров, поддерживающих OpenAI API, например Groq или Gemini OpenAI compatibility. Если все три переменные не заданы, используется Ollama: по умолчанию адрес `http://localhost:11434`, модель `qwen2.5-coder:7b`. Частично заданная конфигурация `LLM_*` считается ошибкой.

- `LLM_BASE_URL` — базовый URL OpenAI-совместимого API (например, `https://api.groq.com/openai/v1`).
- `LLM_MODEL` — имя модели провайдера.
- `LLM_API_KEY` — ключ API.
- `OLLAMA_BASE_URL` — адрес Ollama.
- `OLLAMA_MODEL` — имя установленной модели.
- `PR_CHECKER_MAX_CHUNK_CHARS` — максимальный размер diff-части одного запроса в символах (по умолчанию `12000`).

Пример настройки OpenAI-совместимого API в PowerShell:

```powershell
$env:LLM_BASE_URL = "https://api.groq.com/openai/v1"
$env:LLM_MODEL = "llama-3.3-70b-versatile"
$env:LLM_API_KEY = "your-api-key"
mvn exec:java -Dexec.args="main review.md"
```

Для Ollama очистите эти переменные, чтобы выбрать локальный клиент:

```powershell
Remove-Item Env:LLM_BASE_URL, Env:LLM_MODEL, Env:LLM_API_KEY -ErrorAction SilentlyContinue
$env:OLLAMA_BASE_URL = "http://localhost:11434"
$env:OLLAMA_MODEL = "qwen2.5-coder:7b"
mvn exec:java -Dexec.args="main review.md"
```

Ответ модели должен быть JSON-объектом вида `{"findings":[{"file":"src/Main.java","line":12,"severity":"HIGH","text":"Описание проблемы"}]}`. Поле `line` необязательно; отсутствующее или нулевое значение будет показано без номера строки. Уровни: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`; отсутствие замечаний — `{"findings":[]}`. Файлы из diff объединяются в запрос до лимита; каждый чанк отправляется отдельным запросом. Файл, превышающий лимит сам по себе, пропускается целиком и указывается в отчёте. Rules анализирует исходный diff ровно один раз; замечания модели из всех чанков выводятся подряд без дополнительной сводки.

## Что дальше

- Добавить больше простых правил и настроить их пороги через переменные окружения.
- Сопоставлять номера строк замечаний с diff и улучшить обработку переименованных файлов.
- Добавить опциональную интеграцию с CI/GitHub PR и публикацию review-комментариев.
- Сохранять историю проверок и сравнивать результаты между запусками.
