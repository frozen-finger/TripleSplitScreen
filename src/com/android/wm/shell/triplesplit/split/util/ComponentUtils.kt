package com.android.wm.shell.triplesplit.split.util

import android.app.PendingIntent
import android.app.TaskInfo
import android.content.Intent
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.triplesplit.split.HiddenApiWrapper.intent

object ComponentUtils {
    @JvmStatic
    fun getPackageName(intent: Intent?): String? =
        intent?.component?.packageName ?: intent?.`package`

    /** Retrieves the package name from a [PendingIntent].  */
    //wrapper here
    @JvmStatic
    fun getPackageName(pendingIntent: PendingIntent?): String? =
        getPackageName(intent(pendingIntent))

    /** Retrieves the package name from a [taskId].  */
    @JvmStatic
    fun getPackageName(taskId: Int, taskOrganizer: ShellTaskOrganizer): String? {
        return getPackageName(taskOrganizer.getRunningTaskInfo(taskId))
    }

    /** Retrieves the package name from a [TaskInfo]. */
    @JvmStatic
    fun getPackageName(taskInfo: TaskInfo?): String? = getPackageName(taskInfo?.baseIntent)
}