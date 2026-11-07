# Sound Attendance - open and run

1. Android Studio -> **File -> Open** -> select the `SoundAttendance` folder. Wait for Gradle sync (first time takes a few minutes, internet needed).
2. Firebase (needs your Google login):
   - console.firebase.google.com -> Add project.
   - Build -> Realtime Database -> Create Database -> Start in test mode.
   - Rules tab: set ".read": true and ".write": true -> Publish.
   - Project settings -> Add app -> Android -> package name `com.example.soundattendance`.
   - Download `google-services.json` and put it inside the `app` folder (use the "Project" view in the left panel).
3. Plug in a phone (USB debugging on) and click the green Run button. Repeat on a second phone.
4. Phone A: I am a Teacher -> Start Class (volume max). Phone B: I am a Student -> name + roll no -> Mark Attendance, hold near phone A.

If detection fails: raise volume, move closer, or lower RATIO_NEEDED in StudentActivity.java from 8.0 to 4.0.
