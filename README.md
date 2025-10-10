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
- **Real-time Sync**: Multi-user game synchronization via Firebase

### 🎨 Modern UI/UX
- **Material Design**: Clean, modern interface following Material Design principles
- **Dark Theme**: Elegant dark theme with professional color scheme
- **Navigation Drawer**: Intuitive navigation with drawer menu
- **Responsive Layout**: Optimized for various screen sizes
- **Card-based Design**: Beautiful card layouts for better data presentation

### 🔄 Auto-Update System
- **Automatic Update Checks**: Checks GitHub releases for new versions
- **One-Click Updates**: Download and install updates directly from the app
- **Version Management**: Semantic versioning with automatic CI/CD releases
- **Update Notifications**: Smart notifications for available updates

## 🚀 Automatic Builds & Releases

This project uses GitHub Actions for continuous integration and deployment:

### Automatic Releases (Every Push to Main)
- **Trigger**: Every push to the `main` branch
- **Output**: Release APK available in GitHub Actions artifacts
- **Release**: Automatic release created with download link (v1, v2, v3...)
- **Installation**: Download APK from the latest release and install on your Android device

### How It Works
1. **Push code to main branch**
2. **GitHub Actions automatically**:
   - Determines version based on commit message
   - Updates version in build.gradle.kts
   - Builds the release APK
   - Creates a GitHub release with proper versioning
   - Uploads the APK for download

### Versioning System
The app uses **Semantic Versioning** based on commit messages:

- **Major (X.0.0)**: Breaking changes (`BREAKING CHANGE:` or `!:`)
- **Minor (X.Y.0)**: New features (`feat:` or `feature:`)
- **Patch (X.Y.Z)**: Bug fixes (`fix:`, `bugfix:`, `hotfix:`)
- **Patch (X.Y.Z)**: Chores (`chore:`, `docs:`, `style:`, `refactor:`, `perf:`, `test:`)

**Examples:**
- `feat: Add user authentication` → v1.1.0
- `fix: Fix login bug` → v1.1.1
- `chore: Update dependencies` → v1.1.2
- `BREAKING CHANGE: Remove old API` → v2.0.0

## 📱 Installation

### On Your Android Device

1. **Enable Unknown Sources**:
   - Go to Settings → Security → Unknown Sources (or Install unknown apps)
   - Enable installation from unknown sources

2. **Download APK**:
   - Go to the [Releases](https://github.com/debabrata-mandal/RummyPulse/releases) page
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
   git clone https://github.com/debabrata-mandal/RummyPulse.git
   cd RummyPulse
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build and run**:
   ```bash
   # Build release APK (for production)
   ./gradlew assembleRelease

   # Build debug APK (for development)
   ./gradlew assembleDebug

   # Install on connected device/emulator
   adb install app/build/outputs/apk/release/app-release.apk
   ```

### Firebase Setup
1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com)
2. Add your Android app to the project (package: `com.example.rummypulse`)
3. Download `google-services.json` and place it in the `app/` directory
4. Enable Firestore Database in Firebase Console
5. Configure Firebase Authentication (Email/Password)

**Note**: The `google-services.json` file is gitignored for security. For local development, you need to add your own Firebase configuration file. For CI/CD builds to work with real Firebase, you need to set up a GitHub Secret.

### Setting up GitHub Secret for CI/CD

To make your CI/CD builds work with real Firebase:

1. **Copy your `google-services.json` content**:
   ```bash
   cat app/google-services.json
   ```

2. **Add GitHub Secret**:
   - Go to your GitHub repository
   - Click Settings → Secrets and variables → Actions
   - Click "New repository secret"
   - Name: `GOOGLE_SERVICES_JSON`
   - Value: Paste the entire content of your `google-services.json` file
   - Click "Add secret"

3. **Now your CI/CD builds will use real Firebase** and the released APK will work properly!

### Configuring Auto-Update Feature

The app includes an auto-update feature that checks for new releases on GitHub:

1. **Update GitHub Repository URL**:
   - Open `app/src/main/java/com/example/rummypulse/utils/ModernUpdateChecker.java`
   - Find line 44: `private static final String GITHUB_API_URL`
   - Replace `YOUR_USERNAME` with your actual GitHub username
   - Example: `"https://api.github.com/repos/your-username/RummyPulse/releases/latest"`

2. **Publish Releases**:
   - The app will automatically check for updates from your GitHub releases
   - Make sure to attach APK files to your GitHub releases
   - Users will be notified when updates are available

### App Signing Configuration

For release builds, you need to create signing keys:

1. **Generate Release Keystore**:
   ```bash
   keytool -genkey -v -keystore app/release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configure Signing in build.gradle.kts**:
   - Update the signing configuration with your keystore details
   - Store keystore passwords securely (use environment variables or gradle.properties)

3. **Debug Keystore**:
   - Android Studio generates `debug.keystore` automatically for debug builds
   - Located in `~/.android/debug.keystore` by default

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
- **ModernUpdateChecker**: Automatic update checker using GitHub releases
- **LoginActivity**: Firebase authentication integration

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

**Debabrata Mandal**
- GitHub: [@debabrata-mandal](https://github.com/debabrata-mandal)
- Email: debabrata.developer@gmail.com

## 🙏 Acknowledgments

- Material Design Components for beautiful UI
- Firebase for backend services
- Android team for excellent development tools
- Open source community for inspiration

---

**Made with ❤️ for Rummy enthusiasts**