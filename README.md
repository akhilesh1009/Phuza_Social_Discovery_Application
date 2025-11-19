# **Phuza – Social Discovery App**

Phuza is an **Android app** that helps users discover nearby bars and pubs, connect with friends, and plan social outings. Designed in **Android Studio**, Phuza combines **location discovery, social engagement, and event planning** into one fun and user-friendly experience.

---

## **Purpose of the App**

Phuza was created to make social gatherings **easier, spontaneous, and engaging**.
- Discover **bars, pubs, and events nearby**.

- Share **reviews, photos, and ratings**.



Our goal is to bring people together using a digital platform that enhances **real-life social interactions**.

---

## **Design Considerations**

### Key Features in Part 2
- **Authentication**: Google/Apple Single Sign-On, Firebase Authentication.
- **Profiles**: Avatars, favourite bars & drinks, friend lists.
- **Mapbox API**: Map snippet using Mapbox, switches to map activity and calls the discover pubs endpoint and drops the pins on the map.
- **Onboarding**: Date of birth, location, favourite bars, favourite drink, find friends, avatar.

- **Friends**: Follow your friends on the app and discover new people.
- **Reviews**: Leave a review on a bar, add an image, leave a rating.

---
### Key Features in Part 3
- **Authentication**: Fingerprint Biometrics for login.
- **Messaging**: In-app messaging between friends.
- **Pub Golf**: Host and invite other players/friends to play a friendly and exciting game of pub golf.

## **User Interface Flow**

Splash
↓
Login / Signup
↓
Onboarding (Quick setup)
↓
Dashboard
   ├── Discover (Map, Filters)
   ├── Friends (Discover, Requests, Following)
   ├── Reviews (Add/View)
   ├── Profile (Settings, Logout)


## **Technology Stack**
- **Android Studio** – Project environment
- **Kotlin / Java** – App logic
- **XML** – UI layout
- **Firebase Authentication & Firestore** – Login, user data

- **Mapbox API** – Location services
- **Node.js + Firestore** (backend API for routes)
- **Render Hosting** – API Hosting


## **Installation & Setup**

1. Clone the repo:
   ```bash
   git clone https://github.com/VCSTDN2024/prog7314-part2-phuza.git
2. Open the project in Android Studio
3. Create a Firebase project and add the `google-services.json` file to the app directory
4. Configure Firebase Authentication and Realtime Database in the Firebase console

5. Build and run the application on an emulator or physical device
---
> ⚠️ **__NOTE__**  
> - Android Studio code → `main` branch  
> - API code → `phuza-api` branch
> - Videos below are also available in the folder `Videos`📁 should a YouTube link fail. To Download a video click on `View Raw`
> - Offline Feature, using `RoomDb`, implemented for messages.
---

## **YouTube Videos**

### Phuza Android Application Walkthrough (PART 3)

[![Watch the video](https://img.youtube.com/vi/YsqVkZKbGZY/maxresdefault.jpg)](https://youtu.be/YsqVkZKbGZY)


### Phuza Android Application Walkthrough (Part 2)

[![Watch the video](https://img.youtube.com/vi/LShW9o5hM8c/maxresdefault.jpg)](https://youtu.be/LShW9o5hM8c)

### Phuza-API Walkthrough PART 3:

[![Watch the video](https://img.youtube.com/vi/YgH_oOAqPWo/maxresdefault.jpg)](https://www.youtube.com/watch?v=YgH_oOAqPWo)

### Phuza-API Walkthrough (Node.js + Firestore - Part 2):

[![Watch the video](https://img.youtube.com/vi/ckw_IdGG_7s/maxresdefault.jpg)](https://youtu.be/ckw_IdGG_7s)

## **GitHub Link**

**GitHub Repository (PART 2):** https://github.com/VCSTDN2024/prog7314-part2-phuza.git

## **Screenshots**

### <ins>General</ins>
### *Splash Screen Animation*
<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
   <img src="screenshots/splash_animation.gif" width="250" alt="Login" style="margin: 5px;">
</div>

### <ins>Required Features</ins>
### *Settings and Preferences*
<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
   <img src="screenshots/profile.jpg" width="250" alt="Login" style="margin: 5px;">
  <img src="screenshots/settings_preferences.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/avatar_popup.jpg" width="250" alt="sso" style="margin: 5px;">
    <img src="screenshots/change_password.jpg" width="250" alt="sso" style="margin: 5px;">
</div>

### *SSO Sign In*

<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
   <img src="screenshots/login.jpg" width="250" alt="Login" style="margin: 5px;">
  <img src="screenshots/sso.jpg" width="250" alt="sso" style="margin: 5px;">
</div>

### Mapbox API linked to Discover-Pubs Endpoint

<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
   <img src="screenshots/dashboard.jpg" width="250" alt="Login" style="margin: 5px;">
  <img src="screenshots/map.jpg" width="250" alt="sso" style="margin: 5px;">
</div>

### <ins>User Defined Features</ins>

### <ins>User Defined Feature 1:</ins> *Onboarding*
<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
   <img src="screenshots/onboarding1.jpg" width="250" alt="Login" style="margin: 5px;">
  <img src="screenshots/onboarding2.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/onboarding3.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/onboarding4.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/onboarding5.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/onboarding6.jpg" width="250" alt="sso" style="margin: 5px;">
<img src="screenshots/onboardingS.jpg" width="250" alt="sso" style="margin: 5px;">
</div>

### <ins>User Defined Feature 2:</ins> *Friends*
<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
  <img src="screenshots/friends_follow.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/friends_pending.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/friends_discover.jpg" width="250" alt="sso" style="margin: 5px;">
</div>

### <ins>User Defined Feature 3:</ins> *Reviews*
<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
  <img src="screenshots/add_review.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/add_review_filled.jpg" width="250" alt="sso" style="margin: 5px;">
</div>

### <ins>User Defined Feature 4:</ins> *Messages*
<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
  <img src="screenshots/chat_overview.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/new_chat.jpg" width="250" alt="sso" style="margin: 5px;">
   <img src="screenshots/search_chats.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/convo1.jpg" width="250" alt="sso" style="margin: 5px;">
</div>

### <ins>User Defined Feature 5:</ins> *Pub Golf*
<div style="display: flex; flex-direction: row; flex-wrap: wrap;">
  <img src="screenshots/pubgolf_create.jpg" width="250" alt="sso" style="margin: 5px;">
  <img src="screenshots/pubgolf_score.jpg" width="250" alt="sso" style="margin: 5px;">
<img src="screenshots/pubgolf_score_tracker.jpg" width="250" alt="sso" style="margin: 5px;">
<img src="screenshots/pubgolf_hole_info.jpg" width="250" alt="sso" style="margin: 5px;">
<img src="screenshots/pubgolf_rules.jpg" width="250" alt="sso" style="margin: 5px;">
</div>

## Publishing

<div style="display: flex; flex-wrap: wrap; gap: 10px;">
  <img src="screenshots/APK_One.jpg"  style="width: 45%;">
  <img src="screenshots/APK_two.jpg"  style="width: 45%;">
  <img src="screenshots/apk_three.jpg" style="width: 45%;">
  <img src="screenshots/apk_4.jpg"    style="width: 45%;">
  <img src="screenshots/apk_5.jpg"    style="width: 45%;">
</div>

## References

- Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”. \<https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0\> [accessed 28 August 2025].

- Ananth.k. 2023. “Kotlin — SerializedName Annotation ” <https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371> [accessed 19 September 2025].

- Android Developer. 2024. “Grant Partial Access to Photos and Videos”. \<https://developer.android.com/about/versions/14/changes/partial-photo-video-access\> [accessed 10 September 2025].

- Android Developer. 2025. “Request Location Permissions | Sensors and Location”. \<https://developer.android.com/develop/sensors-and-location/location/permissions\> [accessed 16 August 2025].

- Android Developers. 2025. “Create Dynamic Lists with RecyclerView”. \<https://developer.android.com/develop/ui/views/layout/recyclerview\> [accessed 18 September 2025].

- Android Knowledge. 2023a. “Bottom Navigation Bar in Android Studio Using Java | Explanation”. \<https://www.youtube.com/watch?v=0x5kmLY16qE\> [accessed 20 September 2023].

- Android Knowledge. 2023b. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”. \<https://www.youtube.com/watch?v=oGyQMBKPuNY\> [accessed 21 September 2025].

- Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.  \<https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494\> [accessed 30 September 2025].

- Firebase. 2019a. “Cloud Firestore | Firebase”. \<https://firebase.google.com/docs/firestore\> [accessed 23 September 2025].

- Firebase. 2019b. “Firebase Authentication | Firebase”. Firebase. \<https://firebase.google.com/docs/auth\> [accessed 24 September 2025].

- Firebase. 2019c. “Firebase Cloud Messaging | Firebase”. Firebase. \<https://firebase.google.com/docs/cloud-messaging\> [accessed 15 September 2025].

- Firebase. 2019d. “Firebase Realtime Database”.  \<https://firebase.google.com/docs/database\> [accessed 23 September 2025].

- GeeksforGeeks. 2017. “How to Use Glide Image Loader Library in Android Apps?” \<https://www.geeksforgeeks.org/android/image-loading-caching-library-android-set-2/\> [accessed 30 September 2025].

- GeeksforGeeks. 2020. “SimpleAdapter in Android with Example”. \<https://www.geeksforgeeks.org/android/simpleadapter-in-android-with-example/\> [accessed 19 August 2025].

- GeeksforGeeks. 2021. “State ProgressBar in Android”. GeeksforGeeks. \<https://www.geeksforgeeks.org/android/state-progressbar-in-android/\> [accessed 22 September 2025].

- GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?” \<https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/\> [accessed 22 September 2025].

- Mapbox. 2025. “Mapbox Docs”. \<https://docs.mapbox.com/#search\> [accessed 16 September 2025].

- Nainal. 2019. “Add Multiple SHA for Same OAuth for Google SignIn Android”. \<https://stackoverflow.com/questions/55142027/add-multiple-sha-for-same-oauth-for-google-signin-android\> [accessed 11 August 2025].


---
## **Group Members**
| Name | Student Number |
|------|----------------|
| Akhilesh Parshotam | ST10281011 |
| Alicia Orren | ST10265835 |
| Erin Chisholm | ST10279615 |
| Connor Tre Van Buuren | ST10275455 |
| Ethan Ruey Huntley | ST10399453 |

---
