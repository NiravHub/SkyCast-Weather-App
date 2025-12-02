🌦️ SkyCast — JavaFX Weather Application
Developed by ET24BTCO181 & ET24BTCO176 (OOP Project)
SkyCast is a modern, multi‑feature JavaFX weather application that displays current weather, 5‑day forecast, dynamic icons, and favorites, with support for offline mode, real weather API, data caching, and live UI updates.

✨ Features
Feature	Description
🔍 Real‑time Weather Data	Fetch live weather using OpenWeatherMap API
📉 5‑Day Forecast	Dynamic forecast list with icons
💾 Offline / Demo Mode	Uses local JSON if no internet
⭐ Favorites System	Save and quick‑load cities
🔄 Auto‑Refresh Support	Auto‑update weather (if enabled)
🌗 Light / Dark Ready	UI prepared for theme upgrades
📁 Caching	Stores weather responses locally
🧾 Logging	Saves search history & errors
🎯 Smooth Animations	Fade‑in icon + responsive UI
🧑‍💻 OOP Concepts	Interfaces, threading, file I/O, exceptions
🖥️ Tech Stack
Technology	Purpose
Java 17	Backend logic + Models
JavaFX	UI and Controls
Gson Library	JSON parsing
OpenWeatherMap API	Live data
Git & GitHub	Version control
CMD / Terminal	Running and debugging
📂 Project Structure
SkyCast-Weather-App/
 ├─ src/oep/skycast/
 │   ├─ ui/ (JavaFX Controllers)
 │   ├─ model/ (Weather Models)
 │   ├─ service/ (API + Local Providers)
 │   ├─ util/ (Logging + Preferences)
 │   └─ Main.java
 ├─ resources/
 │   ├─ fxml/
 │   ├─ icons/
 │   ├─ sample-data/
 │   ├─ logs/
 │   └─ preferences.properties
 ├─ lib/ (gson.jar)
 └─ README.md
▶️ How to Run
Open CMD in project folder:

javac --module-path "C:\Program Files\Java\javafx-sdk-25.0.1\lib" --add-modules javafx.controls,javafx.fxml -cp lib\gson-2.10.1.jar -d out src\oep\skycast\Main.java src\oep\skycast\**\*.java
xcopy resources\* out\ /E /I /Y
java --module-path "C:\Program Files\Java\javafx-sdk-25.0.1\lib" --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp out;lib\gson-2.10.1.jar oep.skycast.Main
⚠️ If compilation fails, compile folder‑wise (Windows limitation).

🖼️ Screenshots
Create folder:

screenshots/
Add screenshots of:

📌 Home UI

🔍 Searching a city (Sunny / Cloudy / Rain etc.)

⭐ Favorites working

🔄 Auto‑Refresh ON

CMD showing logs and run command

These will be included in Documentation + Viva.

👨‍💻 Developed By
Name	Roll No	GitHub
Nirav Panwala	ET24BTCO181	
Drashtant Mevada	ET24BTCO176	Soon
📚 OOP Concepts Used
✔ Classes / Objects
✔ Inheritance
✔ Interfaces (WeatherProvider)
✔ Polymorphism
✔ Exception Handling
✔ File Handling (JSON, Cache, Logs)
✔ Multithreading (CompletableFuture)
✔ JavaFX GUI Architecture (MVC)

📜 License
MIT License — Free to use, modify, and distribute.

⭐ Final Outcome
SkyCast is a complete, robust, and industry‑style OOP project suitable for:

✔ University Practical Evaluation
✔ GitHub Portfolio
✔ Resume Showcase
✔ Future App Expansion

If you like this project, ⭐ Star the repository on GitHub!

📌 Screenshot Capture Checklist (for Viva & Docs)
Screenshot	Status Box
Main Dashboard UI	☐
Weather search (Sun)	☐
Weather search (Rain)	☐
Weather search (Cloudy/Snow)	☐
Favorites added	☐
App running in CMD	☐
GitHub Repository Screenshot	☐
