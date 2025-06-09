# 💸 TightBudget - Personal Finance Tracker

<p align="center">
<a href="#"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-%230095D5.svg?style=for-the-badge&logo=kotlin&logoColor=white"></a>
<a href="#"><img alt="Android" src="https://img.shields.io/badge/Android-11%2B-%233DDC84.svg?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="#"><img alt="Firebase" src="https://img.shields.io/badge/Firebase-%23FF6F00.svg?style=for-the-badge&logo=firebase&logoColor=white"></a>
<a href="#"><img alt="Coroutines" src="https://img.shields.io/badge/Coroutines-%234285F4.svg?style=for-the-badge&logo=kotlin&logoColor=white"></a>
<a href="#"><img alt="View Binding" src="https://img.shields.io/badge/View%20Binding-%23FF4081.svg?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="#"><img alt="Material Design" src="https://img.shields.io/badge/Material%20Design-%230081CB.svg?style=for-the-badge&logo=material-design&logoColor=white"></a>
<a href="#"><img alt="API Level" src="https://img.shields.io/badge/API%20Level-30%2B-%23FF5722.svg?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="#"><img alt="Version" src="https://img.shields.io/badge/Version-2.0.0-%2300C853.svg?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

---

## 👥 Development Team

**PROG7313 - Programming 3C / OPSC7311 - Open Source Coding**  
**The Independent Institute of Education (IIE)**

| Name | Student Number |
|------|----------------|
| **Gérard Blankenberg** | ST10046280 |
| **Khano Tshivhandekano** | ST10298613 |
| **Ilyaas Kamish** | ST10391174 |
| **Caleb Searle** | ST10254714 |

---

## 📄 Project Overview

TightBudget is a comprehensive, cloud-enabled budget tracker that helps you manage your personal finances through an engaging and intuitive mobile interface. We've built this app to transform the often tedious task of financial management into something genuinely enjoyable and rewarding.

By incorporating advanced gamification elements, powerful visualisation tools, and intelligent forecasting capabilities, TightBudget makes budgeting feel less like a chore and more like a game you actually want to play.

**Built as part of our PROG7313/OPSC7311 Portfolio of Evidence (POE) for 2025.**

---

## ✨ What Makes TightBudget Special

### 👥 User Authentication & Cloud Storage
- ✅ Secure registration & login with Firebase Authentication
- ✅ **Complete Firebase integration** - your data lives safely in the cloud
- ✅ Cross-device synchronisation (start on your phone, continue anywhere)
- ✅ Session management that just works
- ✅ Guest mode for having a quick look around

### 📝 Smart Transaction Tracking
- ✅ Log expenses and income with ease
- ✅ **Recurring transactions** - set up your salary or rent once, forget about it
- ✅ Custom categories with proper emoji support (because why not? 🍕)
- ✅ Receipt photo attachment
- ✅ Advanced filtering that actually makes sense
- ✅ Search functionality that finds what you're looking for

### 📸 Cloud Receipt Storage
- ✅ **Firebase Cloud Storage integration** - Receipt images stored securely in the cloud
- ✅ **Per-user organisation** - Your receipts are private and organised by user account
- ✅ **Automatic cloud backup** - Never lose your receipts again
- ✅ **Cross-device access** - View receipts from any device where you're logged in
- ✅ **Smart fallback system** - Automatically saves locally if cloud upload fails
- ✅ **Full-screen receipt viewing** - Tap any receipt to view it in full detail
- ✅ **Backwards compatibility** - Existing local receipts continue to work perfectly
- ✅ **Intelligent image loading** - Uses Glide for efficient cloud image display

### 💰 Intelligent Budget Management
- ✅ Monthly budget goals with minimum and maximum spending limits
- ✅ Category budget allocations (know exactly where your money goes)
- ✅ Auto-balancing (we'll do the maths for you)
- ✅ Copy previous budgets (because November probably looks like October)
- ✅ **Visual progress indicators** with colour-coded alerts when you're overspending

### 📊 Advanced Financial Insights
- ✅ **Interactive charts** showing category spending with budget limits
- ✅ **Daily spending trends** over whatever period you choose
- ✅ **Financial forecasting** that predicts your month-end spending
- ✅ **Visual progress dashboard** with real-time budget performance
- ✅ Historical comparisons (see how you're improving!)
- ✅ Smart spending tips based on your actual behaviour

### 🎮 Gamification That Actually Works
- ✅ **Achievement system** - unlock badges for hitting financial milestones
- ✅ **Point scoring** - earn points for consistent tracking
- ✅ **Streak tracking** - keep your daily logging streak alive
- ✅ **Level progression** based on your financial journey
- ✅ **Challenge completion** for budget goals and good habits

### 🏷️ Flexible Category Management
- ✅ Sensible default categories (with proper emojis!)
- ✅ Create custom categories that fit your lifestyle
- ✅ Budget allocation per category
- ✅ Performance tracking to see where your money actually goes

### 🖥️ User Experience That Doesn't Suck
- ✅ Modern Material Design that feels familiar
- ✅ Intuitive dashboard with quick actions
- ✅ Charts and visualisations that are actually useful
- ✅ Navigation that makes sense
- ✅ Search that works
- ✅ **Optimised for mobile** because that's where you'll use it

---

## 🎯 Our Custom Features (Part 3 POE Requirements)

### 🔮 1. Advanced Financial Forecasting
**Inspired by PocketSmith's forecasting capabilities**

We've built a proper forecasting system that doesn't just guess where your spending is heading - it uses actual intelligence:

- **Multi-scenario predictions**: See high, medium, and low spending forecasts
- **Historical analysis**: Uses your past 3 months of data for better accuracy
- **Smart algorithms**: Weighted projections that get smarter over time
- **Visual indicators**: Charts showing projected vs actual spending
- **Confidence levels**: Know how reliable the forecast is
- **Budget impact**: See if you'll be over or under budget by month-end
- **Actionable insights**: Get specific recommendations to stay on track

### 🔄 2. Intelligent Recurring Transactions
**Inspired by PocketSmith, Spendee, and Goodbudget automation**

Because entering your rent payment every month is properly tedious:

- **Complete automation**: Set it once, forget about it
- **Multiple frequencies**: Daily, weekly, monthly, yearly - whatever works
- **Smart processing**: Runs in the background automatically
- **Visual status**: Clear indicators for upcoming, due, and overdue transactions
- **Firebase integration**: Everything syncs across your devices
- **Flexible management**: Easy to create, edit, or cancel
- **Reduces manual entry**: Perfect for rent, salary, subscriptions, utilities

### 🏆 3. Comprehensive Gamification System
**Makes financial management engaging and rewarding**

We've gamified the boring bits so you'll actually want to track your spending:

- **Achievement system**: Unlock badges for financial milestones and good habits
- **Point scoring**: Earn points for logging transactions, uploading receipts, hitting budget goals
- **Level progression**: Your financial responsibility level increases with activity
- **Streak tracking**: Daily logging streaks to keep you motivated
- **Challenge completion**: Various financial challenges to keep things interesting
- **Visual progress**: Profile dashboard showing your level, points, achievements, and streaks
- **Motivation through rewards**: Transform budgeting from chore to something you look forward to

---

## 🏗️ Technical Implementation

### **Architecture & Design Patterns**
We've built this using proper software engineering principles:
- **MVVM Pattern** with Firebase integration
- **Repository Pattern** for clean data management
- **Firebase Realtime Database** for cloud storage
- **Kotlin Coroutines** for smooth, responsive UI
- **LiveData** for reactive updates
- **View Binding** for type-safe UI interactions

### **Technologies We're Using**
- **Kotlin** - Because Java is showing its age
- **Android SDK** - Native Android development
- **Firebase Realtime Database** - Reliable cloud data storage
- **Firebase Authentication** - Secure user management
- **Material Design Components** - Modern, familiar UI
- **Custom Chart Views** - Advanced data visualisation
- **RecyclerView** with optimised adapters
- **Coroutines** for background processing that doesn't block the UI

### **Firebase Integration**
Our Firebase setup includes specialised managers for different aspects:
- **FirebaseUserManager** - User authentication and management
- **FirebaseTransactionManager** - All transaction operations
- **FirebaseBudgetManager** - Budget and category management
- **FirebaseDataManager** - Unified data coordination
- **RecurringTransactionManager** - Automated recurring transactions
- **GamificationManager** - Achievement and progress tracking

---

## 🚀 Getting Started

### 📦 What You'll Need
✅ Android Studio Meerkat (2024.3.1+)  
✅ JDK 11+  
✅ Android SDK API 30+  
✅ Kotlin Plugin 1.5+  
✅ Git  
✅ **Physical Android device** (required for final testing - no emulators!)

### 📝 Installation Steps

```bash
git clone https://github.com/ST10046280-Blankenberg/tightbudget.git
cd tightbudget
```

1. Open the project in Android Studio
2. Sync Gradle files (should happen automatically)
3. **Firebase is already configured!** The `google-services.json` file is included in the repository, so you can start using the app straight away
4. Check Gradle JDK settings (File > Settings > Build, Execution, Deployment > Gradle → JDK 11+)
5. Update `local.properties` if needed:
   ```properties
   sdk.dir=YOUR_ANDROID_SDK_PATH
   ```
6. **Important**: Test on a physical device for the final submission
7. Build → Make Project
8. Run the app and see if everything works

<details>
<summary>🔧 Setting Up Your Own Firebase Project (Optional)</summary>

If you want to set up your own Firebase project for development:
- Create a new Firebase project in the Firebase Console
- Add your `google-services.json` to the `app/` directory
- Enable Realtime Database with read/write access
- Configure authentication if needed

For marking purposes, our configured Firebase instance will work perfectly!

</details>

<details>
<summary>🛠️ Troubleshooting (Click to expand)</summary>

| Problem | Solution |
|---------|----------|
| Firebase won't connect | Check your `google-services.json` is in the right place; verify Firebase project setup |
| Build failures | Check Gradle/Android plugin versions; try `./gradlew clean build` |
| Database errors | Verify Firebase Realtime Database rules; check your internet connection |
| App crashes | Check Logcat for Firebase authentication errors |
| GitHub Actions failing | Make sure secrets are configured properly for CI/CD |

</details>

---

## 📱 Demonstration Video

**Required for POE Part 3 Submission**

We'll need to create a professional demonstration video that shows:
- 🎥 **Professional quality** demonstration of all features
- 📱 **Running on a physical mobile device** (emulators won't cut it)
- 🎤 **Clear voiceover** explaining what we're showing
- ☁️ **Demonstrates Firebase data persistence** 
- 🔗 **Video link** will be included here (YouTube unlisted is recommended)

*Video will be linked here once we've recorded it.*

---

## 🗂️ Project Structure

```
app/
 ├── src/main/java/com/example/tightbudget/
 │   ├── adapters/           # RecyclerView adapters
 │   ├── firebase/           # All our Firebase managers
 │   │   ├── FirebaseUserManager.kt
 │   │   ├── FirebaseTransactionManager.kt
 │   │   ├── FirebaseBudgetManager.kt
 │   │   ├── FirebaseDataManager.kt
 │   │   ├── RecurringTransactionManager.kt
 │   │   └── GamificationManager.kt
 │   ├── models/             # Data models
 │   ├── ui/                 # Custom UI components
 │   ├── utils/              # Utility classes
 │   │   ├── ChartUtils.kt   # Custom chart implementations
 │   │   ├── RecurringTransactionProcessor.kt
 │   │   └── CategoryConstants.kt
 │   └── *Activity.kt        # Main activities
 ├── res/                    # Resources
 ├── google-services.json    # Firebase configuration
 ├── AndroidManifest.xml
 ├── test/                   # Unit tests
 └── androidTest/            # Integration tests
```

---

## 📖 How to Use TightBudget

<details>
<summary>Click to expand the full usage guide</summary>

### Getting Started
1. ✅ **Sign up** → Enter your name, email, and password → **CREATE ACCOUNT**
2. ✅ **Log in** → Email and password → **LOG IN**
3. ✅ Or tap **Continue as guest** (limited functionality, but great for testing)

### Dashboard Navigation
1. ✅ **Financial overview** at the top: your balance, budget progress, spending charts
2. ✅ **Quick actions**: Add Expense, View Budget, Set Goals
3. ✅ **Recent transactions**: See your latest entries, tap for details
4. ✅ **Bottom navigation**: Dashboard / Statistics / Add Transaction / Transactions / Profile

### Adding Transactions
1. ✅ **Choose type**: Expense or Income toggle
2. ✅ **Fill in details**: Amount, merchant, category, description
3. ✅ **Advanced options**: Set date, toggle recurring, attach receipt photo
4. ✅ **Save**: Everything syncs to Firebase automatically

### Setting Up Recurring Transactions
1. ✅ **Toggle "Recurring"** when adding a transaction
2. ✅ **Choose frequency**: Daily, weekly, monthly, or yearly
3. ✅ **Let it run**: The app processes due transactions automatically
4. ✅ **Manage**: View, edit, or cancel in the Transactions tab

### Budget Management
1. ✅ **Create a budget**: Set your total monthly budget and minimum spending goal
2. ✅ **Allocate categories**: Distribute your budget across different spending categories
3. ✅ **Use auto-balance**: Let the app balance remaining amounts for you
4. ✅ **Track progress**: Visual indicators show spending vs limits

### Financial Analysis & Forecasting
1. ✅ **Statistics tab**: Access advanced charts and forecasting
2. ✅ **Choose periods**: Week, month, year, or custom date ranges
3. ✅ **Switch chart types**: Toggle between pie, bar, and line charts
4. ✅ **Check forecasts**: See spending predictions and budget impact

### Gamification Features
1. ✅ **Profile tab**: View your level, points, and achievements
2. ✅ **Earn points**: Log transactions, upload receipts, meet budget goals
3. ✅ **Unlock achievements**: Complete various financial milestones
4. ✅ **Track progress**: Monitor streaks and see your improvement over time

</details>

---

## 🧪 Testing & Quality Assurance

We've implemented comprehensive testing to make sure everything works properly:

- **Unit tests**: Core business logic validation
- **Integration tests**: Firebase connectivity and data flow
- **UI tests**: User interaction and navigation flows
- **GitHub Actions**: Automated build and test pipeline
- **Manual testing**: Comprehensive feature validation on physical devices

---

## 🎓 Academic Context

This application represents the culmination of our work for **PROG7313 Programming 3C** and **OPSC7311 Open Source Coding** at The Independent Institute of Education (IIE). 

What we've demonstrated through this project:
- **Advanced Android development** using modern architectural patterns
- **Cloud integration** with Firebase services
- **User experience design** following Material Design principles
- **Software engineering practices** including proper version control and testing
- **Problem-solving skills** through innovative custom feature implementation

**Our Portfolio of Evidence Structure:**
- **Part 1**: Research, Planning & Design
- **Part 2**: App Prototype Development  
- **Part 3**: Final App Development with Firebase Integration (this submission)

---

## 📋 Lecturer Feedback & Improvements

**Part 2 Results**

We were fortunate to receive full marks for Part 2 of the Portfolio of Evidence, with no specific feedback requiring addressed improvements.

---

## 🏆 What We've Achieved

- ✅ **Complete Firebase integration** - All data stored securely in the cloud
- ✅ **Three advanced custom features** - Forecasting, recurring transactions, and gamification
- ✅ **Professional mobile UI** - Modern Material Design implementation
- ✅ **Comprehensive testing** - Unit, integration, and manual testing coverage
- ✅ **Production-ready code** - Clean architecture with proper documentation

---

## 📜 Licence

This project was developed for academic purposes as part of the IIE curriculum. All rights reserved by the development team and The Independent Institute of Education.

## 📚 References & Documentation

### **Firebase & Database**
- [Firebase Authentication Documentation](https://firebase.google.com/docs/auth) - User authentication and security
- [Firebase Android Setup Guide](https://firebase.google.com/docs/android/setup) - Project configuration and integration
- [Firebase Realtime Database Structure](https://firebase.google.com/docs/database/android/structure-data) - Data modeling and best practices

### **Android Development**
- [Android Architecture Components](https://developer.android.com/topic/architecture) - MVVM and modern app architecture
- [Android ViewModel Guide](https://developer.android.com/topic/libraries/architecture/viewmodel) - UI state management
- [RecyclerView Implementation](https://developer.android.com/develop/ui/views/layout/recyclerview) - Efficient list displays
- [View Binding Documentation](https://developer.android.com/topic/libraries/view-binding) - Type-safe view references
- [Android Logcat Debugging](https://developer.android.com/studio/debug/logcat) - Debugging and logging best practices

### **Kotlin & Programming**
- [Kotlin Coroutines Overview](https://kotlinlang.org/docs/coroutines-overview.html) - Asynchronous programming
- [Android UI Design Best Practices](https://www.geeksforgeeks.org/best-practices-for-android-ui-design/) - User interface guidelines

### **Algorithms & Mathematical Models**
- [Weighted Moving Average Forecasting](https://en.wikipedia.org/wiki/Moving_average#Weighted_moving_average) - Financial prediction algorithms
- [Moving Average Definition](https://www.investopedia.com/terms/m/movingaverage.asp) - Financial forecasting fundamentals
- [Time Series Forecasting Techniques](https://otexts.com/fpp3/simple-methods.html) - Statistical forecasting methods
- [Forecasting: Principles and Practice](https://otexts.com/fpp2/simple-methods.html) - Simple forecasting methods
- [Exponential Smoothing](https://en.wikipedia.org/wiki/Exponential_smoothing) - Trend analysis and prediction
- [Gamification Point Systems](https://yukaichou.com/gamification-examples/octalysis-complete-gamification-framework/) - Behavioural motivation algorithms

### **DevOps & Version Control**
- [GitHub Actions Quickstart](https://docs.github.com/en/actions/writing-workflows/quickstart) - CI/CD automation

### **Inspiration & Research**
- [PocketSmith Features](https://www.pocketsmith.com/features/) - Financial forecasting inspiration
- [Gamification in Financial Apps](https://dashdevs.com/blog/gamification-in-financial-apps-unlocking-new-opportunities-for-growth-and-engagement/) - User engagement strategies

---

<p align="center">
<strong>Built with ❤️ by Team TightBudget</strong><br>
<em>The Independent Institute of Education - 2025</em>
</p>
