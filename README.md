# 🃏 RummyPulse

A modern Android application for Rummy game management and player tracking. Built with modern Android development practices and Firebase integration.

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-039BE5?style=for-the-badge&logo=Firebase&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=Gradle&logoColor=white)

## ✨ Features

### 🎮 Game Management
- **Game Tracking**: Monitor Rummy games with Firebase integration
- **Game Status Management**: Track game states and status
- **Player Management**: Manage players and their game participation
- **Game History**: View and manage game records


### 🎨 Modern UI/UX
- **Material Design**: Clean, modern interface following Material Design principles
- **Dark Theme**: Elegant dark theme with professional color scheme
- **Navigation Drawer**: Intuitive navigation with drawer menu
- **Responsive Layout**: Optimized for various screen sizes
- **Card-based Design**: Beautiful card layouts for better data presentation

## 🚀 Automatic Builds & Releases

This project uses GitHub Actions for continuous integration and deployment:

### Debug Builds (Every Push to Main)
- **Trigger**: Every push to the `main` branch
- **Output**: Debug APK available in GitHub Actions artifacts
- **Release**: Automatic release created with download link
- **Installation**: Download APK from the latest release and install on your Android device

### Release Builds (Tagged Releases)
- **Trigger**: When you create a git tag (e.g., `v1.0.0`)
- **Output**: Release APK (signed) for production
- **Release**: GitHub release with the tagged version
- **Installation**: Download the production APK from the release

## 📱 Installation

### On Your Android Device

1. **Enable Unknown Sources**:
   - Go to Settings → Security → Unknown Sources (or Install unknown apps)
   - Enable installation from unknown sources

2. **Download APK**:
   - Go to the [Releases](https://github.com/yourusername/RummyPulse/releases) page
   - Download the latest APK file

3. **Install**:
   - Open the downloaded APK file
   - Follow the installation prompts

### System Requirements
- **Android Version**: 9.0 (API 28) or higher
- **RAM**: 2GB minimum
- **Storage**: 50MB available space
- **Internet**: Required for Firebase sync

## 🔧 Development Setup

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 17 or higher
- Android SDK 28+
- Git

### Local Development

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/RummyPulse.git
   cd RummyPulse
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build and run**:
   ```bash
   # Build debug APK
   ./gradlew assembleDebug

   # Install on connected device/emulator
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Firebase Setup
1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com)
2. Add your Android app to the project
3. Download `google-services.json` and place it in the `app/` directory
4. Enable Firestore Database in Firebase Console

**Note**: The `google-services.json` file is gitignored for security. For local development, you need to add your own Firebase configuration file. The CI/CD builds will work without Firebase (with limited functionality).

## 🏗️ Architecture

### Tech Stack
- **Language**: Java
- **UI Framework**: Android Views with ViewBinding
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Firebase Firestore
- **Navigation**: Android Navigation Component
- **Dependency Injection**: Manual dependency injection
- **Build System**: Gradle with Kotlin DSL

### Project Structure
```
app/
├── src/main/java/com/example/rummypulse/
│   ├── data/           # Data layer (Repository, Models)
│   ├── ui/             # UI layer (Fragments, ViewModels)
│   └── MainActivity.java
├── src/main/res/       # Resources (layouts, drawables, values)
└── build.gradle.kts    # App-level build configuration
```

### Key Components
- **GameRepository**: Handles Firebase data operations
- **HomeViewModel**: Manages game data and business logic
- **TableAdapter**: RecyclerView adapter for game lists
- **GameItem**: Data model for game information

## 📊 Screenshots

*Screenshots will be added here showing the app's interface*

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Your Name**
- GitHub: [@yourusername](https://github.com/yourusername)
- Email: your.email@example.com

## 🙏 Acknowledgments

- Material Design Components for beautiful UI
- Firebase for backend services
- Android team for excellent development tools
- Open source community for inspiration

---

**Made with ❤️ for Rummy enthusiasts**