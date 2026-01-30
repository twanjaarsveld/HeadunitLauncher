# HeadunitLauncher

A specialized Android launcher designed for automotive headunits and large-screen tablets. The interface is inspired by the Tesla Model 3 UI, providing a high-contrast, streamlined experience optimized for in-vehicle use. It features an integrated update system and deep customization options.

## Screenshots

![Home Screen](https://github.com/user-attachments/assets/5d0ee959-5f64-4d05-8a30-391d05ae9f26)
![Settings Interface](https://github.com/user-attachments/assets/243aa729-8f1e-4fbb-823b-b8441061af02)
![App Selection](https://github.com/user-attachments/assets/6c0a49d8-22a2-40f9-9849-a4cfc14bdea4)

## Core Features

* **Dashboard Widgets**: Toggle between a digital speedometer and a clock interface on the primary display.
* **Managed Application Grid**: Define which applications appear on the home screen via the settings menu to minimize distractions.
* **UI Scaling**: Adjust the interface density (DPI) within the app to ensure optimal visibility across different screen sizes and resolutions.
* **Visual Personalization**: 
    * Configurable background and vehicle profile images.
    * Support for custom application icons via long-press in the application list.
* **Automated Updates**: Integrated version checking using the GitHub REST API, allowing for seamless background downloading and installation of new releases.
* **System Controls**: Integrated brightness adjustment and dark mode toggle.

## Installation

1. Navigate to the Releases section of this repository.
2. Download the latest `HeadunitLauncher.apk`.
3. Transfer the file to your Android device.
4. Enable "Install from Unknown Sources" in your Android System Settings.
5. Install the APK and set HeadunitLauncher as the default Home application.

## Technical Implementation

The project is developed in Kotlin and utilizes the following Android components:

* **DownloadManager**: Handles background APK retrieval with progress tracking.
* **FileProvider**: Manages secure file sharing with the Android Package Installer, ensuring compatibility with Android 11 through Android 16.
* **Scoped Storage**: Uses external cache directories to comply with modern Android security requirements while maintaining write access.
* **GitHub API Integration**: Polls repository metadata to compare local build versions with the latest available release.

## Project Structure

* `MainActivity.kt`: Manages the primary UI, widgets, and the application launch grid.
* `SettingsActivity.kt`: Contains the configuration logic, UI scaling calculations, and the update engine.
* `AppSelectAdapter.kt`: Handles the logic for selecting and persistence of authorized applications.

## Requirements

* **Android Version**: Android 8.0 (Oreo) or higher.
* **Network**: Internet access required for version checking and downloading updates.
* **Permissions**: Requires `REQUEST_INSTALL_PACKAGES` and `WRITE_SETTINGS` for full functionality.
