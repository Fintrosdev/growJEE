# JEE Control Centre · Project M

© 2026 FintrosDev · The Basement · Darshrey Media. All rights reserved.

A static study dashboard with a dark glass interface. The app is one HTML file; Supabase provides optional accounts, cloud sync, and read-only teacher access.

## Run it

Open `index.html` in a current browser for local use. For online accounts, host it over HTTPS on GitHub Pages. Put `index.html` at the root of a GitHub repository, push it, then enable **Settings → Pages → Deploy from a branch** and choose the main branch and root folder.

## Enable login and online sync

1. Create a Supabase project.
2. In its SQL Editor, run [`supabase_setup.sql`](supabase_setup.sql). It creates the progress and sharing tables, enables row level security, and allows each account to edit only its own progress. A teacher can read a limited progress summary only after the student shares with that signed-in email.
3. In Supabase Authentication settings, set the Site URL and allowed redirect URL to your GitHub Pages address, and keep email confirmation enabled. To send sign-up and reset emails to a teacher or student outside your Supabase organization, configure custom SMTP; Supabase’s default mail service only sends to project team addresses. See [Supabase custom SMTP setup](https://supabase.com/docs/guides/auth/auth-smtp).
4. The hosted app has the Supabase project URL and **publishable key** built in, so students and teachers do not need to enter them. These values are public browser settings; never include a secret or service-role key.
5. Create the student account. Sign in from any device using the same project settings to load the same cloud data. The first sign-in can offer to upload existing records from that browser.
6. To share progress, the student enters the teacher’s signed-in account email in **Account & sync**, then sends the teacher the GitHub Pages link. The teacher signs in with that exact email and opens **Students shared with you**. Access is read-only and can be revoked by the student. Teachers see study time, question accuracy, chapter completion, low-star priorities, and recent mock scores; private notes and mistake explanations stay with the student.

The Supabase URL and publishable/anon key are stored in that browser’s local settings. Each device needs the same project settings. The app uses the signed-in user’s access token for its own row; database policies are the security boundary. Never put a Supabase secret or service-role key in this public site. See [Supabase RLS guidance](https://supabase.com/docs/guides/database/postgres/row-level-security).

Cloud storage syncs after edits and keeps a local cache for responsiveness. Signing out hides that account’s records in the app. Keep using **Export backup** as a separate recovery copy.

## Stars and priorities

Enter the latest chapter mock mark out of 100 in **Chapter Master**. The rating updates automatically:

| Mark | Stars |
|---:|:---|
| Under 20 | 0 |
| 20–39 | 1 |
| 40–59 | 2 |
| 60–79 | 3 |
| 80–99 | 4 |
| 100 | 5 |

Chapters with 0–2 stars appear in the priority queue, lowest score first. Each changed mark is retained in score history. All student performance inputs start blank.

## ChatGPT and Trello

- **Study with ChatGPT** opens ChatGPT and copies a prompt containing your low-star chapter list. Paste it into a new chat; the site does not access or send anything to your private ChatGPT conversations.
- **Trello card pack** downloads a CSV of priority chapters that you can use to make Trello cards. This static GitHub Pages version does not keep Trello API tokens or write to a Trello account.

## Saving and sharing progress

With Supabase configured, study data is stored online and synced to the signed-in account. Without configuration or sign-in, the app remains in local-only mode. Use **Share snapshot → Print / Save as PDF** for a portable report, or invite a teacher account for live read-only access.

The starter topics include JEE Main 2027 and a latest available JEE Advanced 2026 Chemistry supplement. Confirm the official syllabus for your exam year.

## Mock-test papers and linked mistakes

Each mistake can be linked to a saved mock test. Open a mock test to see its related mistakes and attach a question paper or answer sheet PDF (up to 10 MB). Run the updated `supabase_setup.sql` once in the Supabase SQL Editor to enable private cloud PDF storage. Files are scoped to the signed-in student's account. If a question-paper cloud upload fails, it is saved in that browser instead; browser-only files do not follow the account to another device. Answer sheets require cloud storage. Teachers can see the shared progress snapshot, but mock PDFs are private to the student account and are not included in the teacher view.

The app may occasionally show a short Project M teaser. Use **Mute for a week** to pause them on that browser.

## Android app

GrowJEE includes a native Android interface in `android/` with native screens, forms, chapter priorities, and Supabase login and sync to the same account. The app is not a website wrapper. The **Build Android APK** GitHub Actions workflow automatically creates an installable debug APK on changes to the Android app. Download `GrowJEE-Android-APK` from the workflow run's **Artifacts** section. This debug APK is for direct installation; it is not a Google Play release.

## Windows desktop app

The `windows/GrowJEE.Desktop` project is a native Windows WPF app with the same chapter and record format, account login, cloud sync, mock-linked mistakes, teacher sharing, and PDF paper attachments. Its **Build Windows desktop app** GitHub Actions workflow produces a self-contained x64 app package. Download `GrowJEE-Windows-Desktop` from the successful run's **Artifacts** section, unzip it, and run `GrowJEE.exe`. Sign in using the same GrowJEE account to load your cloud records.

The website and Windows desktop app use the Project M lunar emblem. The desktop app includes randomized Project M signal teasers.

## Paper to CBT

Use **CBT Builder** on the website to select a PDF question paper. Text is extracted in your browser; scanned pages use browser OCR. The PDF is not uploaded. Review and edit detected question text/options, add official answer keys if you have one, set a timer, then take the test. Results are scored only for items with keys you entered; you can save that real result into Mock Test Analytics. OCR can misread mathematical notation, diagrams, and question boundaries, so verify the parsed paper before starting. The scanner processes up to 30 pages per paper. Requires internet access for the PDF/OCR libraries.

