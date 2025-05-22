# 💸 TightBudget - Personal Finance Tracker

<p align="center">
<a href="#"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-%230095D5.svg?style=for-the-badge&logo=kotlin&logoColor=white"></a>
<a href="#"><img alt="Android" src="https://img.shields.io/badge/Android-11%2B-%233DDC84.svg?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="#"><img alt="Room" src="https://img.shields.io/badge/Room-%23FF6F00.svg?style=for-the-badge&logo=sqlite&logoColor=white"></a>
<a href="#"><img alt="Coroutines" src="https://img.shields.io/badge/Coroutines-%234285F4.svg?style=for-the-badge&logo=kotlin&logoColor=white"></a>
<a href="#"><img alt="View Binding" src="https://img.shields.io/badge/View%20Binding-%23FF4081.svg?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="#"><img alt="Material Design" src="https://img.shields.io/badge/Material%20Design-%230081CB.svg?style=for-the-badge&logo=material-design&logoColor=white"></a>
<a href="#"><img alt="API Level" src="https://img.shields.io/badge/API%20Level-30%2B-%23FF5722.svg?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="#"><img alt="Version" src="https://img.shields.io/badge/Version-1.0.0-%2300C853.svg?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

---

## 📄 Project Overview

TightBudget is a comprehensive budget tracker application designed to help users manage their personal finances effectively through an engaging and intuitive mobile interface. By incorporating gamification elements and powerful visualisation tools, TightBudget transforms the often tedious task of financial management into an enjoyable and rewarding experience.

---

## ✨ Key Features

### 👥 User Authentication
- ✅ Secure Registration & Login
- ✅ Session Management
- ✅ Guest Mode

### 📝 Expense & Income Tracking
- ✅ Comprehensive Transaction Logging
- ✅ Category Classification
- ✅ Date & Time Tracking
- ✅ Merchant Information
- ✅ Descriptive Notes
- ✅ Receipt Attachment
- ✅ Filtering & Sorting

### 💰 Budget Management
- ✅ Monthly Budget Goals
- ✅ Minimum Spending Goals
- ✅ Category Allocations
- ✅ Budget Period Selection
- ✅ Automatic Balancing
- ✅ Previous Budget Copying
- ✅ Budget Goal Progress

### 📊 Financial Analysis & Insights
- ✅ Spending Breakdown
- ✅ Trend Analysis
- ✅ Daily Spending Chart
- ✅ Forecast Projection
- ✅ Budget Performance
- ✅ Category Comparison
- ✅ Historical Comparison

### 🏷️ Category Management
- ✅ Predefined Categories
- ✅ Custom Categories
- ✅ Category Customisation
- ✅ Category Budget Allocation
- ✅ Category Performance

### 🖥️ User Interface & Experience
- ✅ Intuitive Dashboard
- ✅ Interactive Charts
- ✅ Quick Action Buttons
- ✅ Guided Navigation
- ✅ Search Functionality

### 🔒 Data Management & Security
- ✅ Local Database Storage
- ✅ Offline Access
- ✅ Data Backup *(coming soon)*
- ✅ Cloud Synchronisation *(coming soon)*

---

## 🏗️ Technical Implementation

**Architecture & Design Patterns**
- MVVM Pattern
- Repository Pattern
- Room Database
- Kotlin Coroutines
- LiveData
- Data Binding

**Technologies Used**
- Kotlin
- Android SDK
- Room Persistence Library
- RecyclerView
- Material Design Components
- Coroutines
- View Binding

---

## 🚀 Getting Started

### 📦 Prerequisites
✅ Android Studio Meerkat (2024.3.1+)  
✅ JDK 11+  
✅ Android SDK API 30+  
✅ Kotlin Plugin 1.5+  
✅ Git

### 📝 Installation Steps

```bash
git clone https://github.com/yourusername/tightbudget.git
cd tightbudget
```

1. Open project in Android Studio
2. Sync Gradle files
3. Verify Gradle JDK (File > Settings > Build, Execution, Deployment > Gradle → JDK 11+)
4. Update `local.properties` if needed:
   ```properties
   sdk.dir=YOUR_ANDROID_SDK_PATH
   ```
5. Configure emulator (AVD Manager → Pixel 4 → Android 11+)
6. Build → Make Project
7. Run app

<details>
<summary>🛠️ Troubleshooting</summary>

| Issue            | Solution                                  |
|-----------------|-------------------------------------------|
| Build Failures   | Check Gradle/Android plugin versions; `./gradlew clean build` |
| Emulator Issues  | Verify HAXM installed; allocate more RAM  |
| App Crashes      | Check Logcat; verify permissions/API level |
| GitHub Actions   | Check error logs in Actions tab; test locally |

</details>

---

## 🗂️ Project Structure

```
app/
 ├── src/main/java/com/example/tightbudget/
 │   ├── adapters/
 │   ├── data/
 │   ├── models/
 │   ├── ui/
 │   ├── utils/
 │   └── *Activity.kt
 ├── res/
 ├── AndroidManifest.xml
 ├── test/
 └── androidTest/
```

---

## 📖 Detailed App Usage Guide

<details>
<summary>Click to expand usage instructions</summary>

### First-Time Setup
1. ✅ Tap **Sign Up** → enter name, email, password → accept terms *(upcoming)*
2. ✅ Login → email + password → toggle **Remember Me** *(upcoming)* → **LOG IN**
3. ✅ Or tap **Continue as guest** (data not persisted if app cleared)

### Dashboard Navigation
1. ✅ Balance at top, budget progress (circle), spending breakdown (pie chart)
2. ✅ **Quick Actions** → Add Expense, View Budget, Goals
3. ✅ **Recent Transactions** → tap for details; **See All** for full history
4. ✅ Bottom navigation → Dashboard / Reports / Add Transaction / Wallet / Settings

### Adding Transactions
1. ✅ Enter type (Expense/Income), amount, merchant, category, optional notes
2. ✅ Advanced → set date, toggle **Recurring** *(upcoming)*, attach receipt, create category
3. ✅ Tap **SAVE** → returns to Dashboard

### Managing Budget Goals
1. ✅ Go to Budget Goals → select month → set total & minimum → allocate categories
2. ✅ Adjust sliders or input values → **Auto-Balance** remaining
3. ✅ Tap **Copy Previous** → adjust → **Save Changes**

### Viewing & Filtering Transactions
1. ✅ Go to Wallet tab → view all transactions
2. ✅ Filters → **Period**, **Category**, **Sort**, search icon
3. ✅ Tap transaction → view details → view receipt → delete

### Analysing Finances
1. ✅ Reports tab → view charts
2. ✅ Toggle **Week/Month/Year** → pie/bar → daily line chart → forecasts
3. ✅ Category Spending → view category budgets, filter, sort

### App Settings & Preferences
1. ✅ **Settings** → edit profile, change password, manage security
2. ✅ Toggle notifications, dark mode
3. ✅ View help docs, send feedback, app info
4. ✅ **Sign Out**

</details>

---

## 🔭 Future Enhancements

- 🔗 Firebase Integration
- 🏆 Achievement Badges
- 🤖 Budget Forecasting (ML-based)
- 🔄 Recurring Transactions
- 📩 Monthly Reports
- 🚨 Notifications
- 🌐 Cloud Sync

---

## 🏫 Educational Disclaimer

This project is developed for educational purposes as part of the **PROG7313/OPSC7311 module** at *The Independent Institute of Education*.
