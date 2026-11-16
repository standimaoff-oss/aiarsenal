# Сторонние компоненты «Оракула»

## wllama (@wllama/wllama) 3.13.0
- Назначение: исполнение GGUF-модели на устройстве (llama.cpp → WebAssembly)
- Лицензия: **MIT**
- Источник: https://github.com/ngxson/wllama (npm: `@wllama/wllama`)
- Файл в сборке: `public/wasm/wllama.wasm` (копируется из npm-пакета при `npm install`, в git не хранится)

## Qwen2.5-0.5B-Instruct (веса модели)
- Назначение: базовое ядро «Оракула» (~0,5 млрд параметров), квантование GGUF Q4_K_M
- Лицензия: **Qwen Research License** (веса), квантизация community-GGUF на Hugging Face
- Источник весов (Заход 2): https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
- Веса **не входят** в репозиторий и в сборки приложения: скачиваются пользователем один раз по кнопке и хранятся локально (OPFS)

## Vite
- Назначение: сборка веб-интерфейса
- Лицензия: MIT
- https://vite.dev

Модель и wllama притягиваются только в Заходе 2+.
