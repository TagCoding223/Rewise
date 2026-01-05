# Rewise 🧠

Tired of forgetting things you've learned? Rewise is a minimalist Android app that uses the power of **spaced repetition** to help you commit information to long-term memory.

## The Problem with Forgetting

Our brains naturally forget things over time. The "forgetting curve" is steep—we lose a large chunk of new information within days if we don't actively review it. The key is to review information at just the right moment, right before you're about to forget it.

## How Rewise Helps

Rewise makes this process simple and automatic.

1.  **Add a Topic:** Whenever you learn something new you want to remember, just add it to Rewise.
2.  **Review on Schedule:** The app tells you which topics are due for review each day.
3.  **Mark as Done:** Once you've reviewed a topic, simply mark it as "done." Rewise automatically calculates the next best time for you to review it again, pushing it further and further into the future as it solidifies in your memory.

That's it! No complicated settings, no distracting features. Just a straightforward tool to help you learn and remember more effectively.

### Features

-   ✅ Simple interface for adding new topics.
-   🗓️ Automatic scheduling of revision dates based on a spaced repetition algorithm.
-   🔔 Daily notifications to remind you of topics that are due.
-   🧘‍♀️ A clean, focused list of all your active topics.

## Built With

This app is built with modern Android development practices in mind:

-   **Language:** 100% [Kotlin](https://kotlinlang.org/)
-   **Architecture:** Follows recommended Android app architecture.
-   **Core Libraries:**
    -   [Jetpack Room](https://developer.android.com/training/data-storage/room) for local data persistence.
    -   [Jetpack WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for reliable background scheduling of reminders.
    -   [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) for managing background threads.
    -   [RecyclerView](https://developer.android.com/guide/topics/ui/layout/recyclerview) for displaying the list of topics efficiently.
