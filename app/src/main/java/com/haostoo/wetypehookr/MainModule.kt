package com.haostoo.wetypehookr

import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle

class MainModule : XposedModule() {

    companion object {

        private const val TAG =
            "WETYPE_HOOK"

        @Volatile
        private var realHookInstalled =
            false

        @Volatile
        private var toastShown =
            false
    }

    override fun onPackageLoaded(
        param: XposedModuleInterface.PackageLoadedParam
    ) {

        if (
            param.getPackageName()
            != "com.tencent.wetype"
        ) {
            return
        }

        try {

            val loadedApkClass =
                Class.forName(
                    "android.app.LoadedApk"
                )

            val stage1Method =
                loadedApkClass.getDeclaredMethod(
                    "createOrUpdateClassLoaderLocked",
                    java.util.List::class.java
                )

            var stage1Handle:
                    HookHandle? = null

            stage1Handle =
                hook(stage1Method)
                    .intercept(

                        ClassLoaderHooker {

                            stage1Handle?.unhook()
                        }

                    )

        }
        catch (t: Throwable) {

            Log.e(
                TAG,
                "Stage1 install failed=$t"
            )
        }
    }

    inner class ClassLoaderHooker(
        private val onSuccess:
            () -> Unit
    ) : XposedInterface.Hooker {

        override fun intercept(
            chain: XposedInterface.Chain
        ): Any? {

            val result =
                chain.proceed()

            if (
                realHookInstalled
            ) {
                return result
            }

            try {

                val loadedApkObj =
                    chain.thisObject

                val cl =
                    loadedApkObj.javaClass
                        .getMethod(
                            "getClassLoader"
                        )
                        .invoke(
                            loadedApkObj
                        ) as ClassLoader

                val clazz =
                    cl.loadClass(
                        "com.tencent.wetype.plugin.hld.view.ImeRootView"
                    )

                val method =
                    clazz.getDeclaredMethod(
                        "dispatchTouchEvent",
                        MotionEvent::class.java
                    )

                hook(method)
                    .intercept(
                        ImeRootViewHooker()
                    )

                realHookInstalled =
                    true

                // Stage1 用完卸载
                onSuccess()

            }
            catch (_: Throwable) {

                // 插件未加载好时静默等待下次触发
            }

            return result
        }
    }

    class ImeRootViewHooker :
        XposedInterface.Hooker {

        override fun intercept(
            chain: XposedInterface.Chain
        ): Any? {

            val view =
                chain.thisObject as? View
                    ?: return chain.proceed()

            if (
                chain.args.isEmpty()
            ) {
                return chain.proceed()
            }

            val event =
                chain.args[0]
                        as? MotionEvent
                    ?: return chain.proceed()

            if (!toastShown) {

                toastShown =
                    true

                view.post {

                    Toast.makeText(
                        view.context,
                        "Hook attached",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            fun doHaptic() {

                val ok =
                    view.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP
                    )

                if (!ok) {

                    view.performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                }
            }

            when (
                event.action
            ) {

                MotionEvent.ACTION_DOWN -> {
                    doHaptic()
                }

                MotionEvent.ACTION_UP -> {
                    doHaptic()
                }
            }

            return chain.proceed()
        }
    }
}