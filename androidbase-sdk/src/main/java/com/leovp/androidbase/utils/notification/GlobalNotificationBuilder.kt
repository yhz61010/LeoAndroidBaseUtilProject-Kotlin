/*
Copyright 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.leovp.androidbase.utils.notification

import androidx.core.app.NotificationCompat

/**
 * We use a Singleton for a global copy of the NotificationCompat.Builder to update active
 * Notifications from other Services/Activities.
 */
object GlobalNotificationBuilder {
    var notificationCompatBuilderInstance: NotificationCompat.Builder? = null

}