# bookreader


![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Android Studio](https://img.shields.io/badge/Android%20Studio-3DDC84.svg?style=for-the-badge&logo=android-studio&logoColor=white)



# Ключевая особенность
    
    - Католог книг пользователя
    - Возможность добавлять как книги по одной так и целой папкой
    - Возможность чтения книг

## 🚀 Быстрый старт

Перед началом работы убедитесь, что у вас установлены:

- android studio
- библиотеки barteksc:android-pdf-viewer, com.google.code.gson:gson:2.10.1, androidx.core:core-ktx:1.12.0


### Установка

1. **Клонирование репозитория**
   ```bash
   git clone https://github.com/ExtaSsS4106/bookreader.git
   cd /path/to/folder/
    ```
   # Или

    ```bash
   git clone git@github.com:ExtaSsS4106/bookreader.git
   cd /path/to/folder/
    ```

2. **Запуск андроид студио**

    Запустите заранее установленный Android Studio на ваш компьютер и запустите,
    далее выберите деректорию со скаченным репозиторием

3. **Запуск**
    
    Установите библиотеки в файл (build.gradle.kts (:app))
    
    ```bash
            dependencies {

            implementation(libs.android.pdf.viewer)
            implementation("com.google.code.gson:gson:2.10.1")
            implementation("androidx.core:core-ktx:1.12.0")
        }
    ```
    далее в файле libs.versions.toml пропишите
    
    ```bash
        
        adpdfw= "2.8.2"
        
        android-pdf-viewer = { module = "com.github.barteksc:android-pdf-viewer", version.ref = "adpdfw" }
    ```
    В файле (build.gradle.kts (:bookreader))
    
    ```bash
            dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
                maven ( url = "https://repository.liferay.com/nexus/content/repositories/public/" )
            }
        }
     ```   
    И в gradle.properties 
    
    ```bash
            android.enableJetifier=true
            
    ```
