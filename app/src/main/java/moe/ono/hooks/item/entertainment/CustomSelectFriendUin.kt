package moe.ono.hooks.item.entertainment

import android.app.AlertDialog
import android.widget.EditText
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.util.ContextUtils
import moe.ono.util.Logger
import java.lang.reflect.Modifier

@HookItem(path = "娱乐功能/自定义专属红包 Uin", description = "点击专属红包页面的发送按钮后会拦截")
class CustomSelectFriendUin : BaseSwitchFunctionHookItem() {

    override fun entry(classLoader: ClassLoader) {
        val isConfirm = ThreadLocal.withInitial { false }

        try {
            // BaseHbFragment holds the sender. Its type moved from ru1.g to
            // ji2.g in QQ 9.3.75; resolve the field's type instead of its name.
            val fragment = XposedHelpers.findClass(
                "com.tencent.mobileqq.qwallet.hb.send.impl.BaseHbFragment", classLoader
            )
            val sendMethod = fragment.declaredFields.flatMap { field ->
                field.type.methods.filter {
                    it.name == "b" && it.returnType == Void.TYPE
                        && !Modifier.isStatic(it.modifiers)
                        && it.parameterTypes.contentEquals(arrayOf(java.util.Map::class.java))
                }
            }.distinct().single()
            XposedBridge.hookMethod(sendMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!this@CustomSelectFriendUin.configIsEnable() || isConfirm.get() == true) return
                    val obj = param.thisObject?:return
                    val map = param.args[0] as MutableMap<String, String>
                    if (map.containsKey("grab_uin_list")) {
                        param.result = null
                        AlertDialog.Builder(ContextUtils.getCurrentActivity()).apply {
                            setTitle("自定义专属红包 Uin")
                            val uin = EditText(ContextUtils.getCurrentActivity()).apply {
                                hint = "输入 Uin, 多个用 | 分割"
                                setText(map.get("grab_uin_list"))
                            }
                            setView(uin)
                            setPositiveButton("确定") { _, _ ->
                                map.put("grab_uin_list", uin.text.toString())
                                map.put("total_num", ((uin.text.toString().count { it == '|' }) + 1).toString())
                                isConfirm.set(true)
                                try {
                                    sendMethod.invoke(obj, map)
                                } finally {
                                    isConfirm.set(false)
                                }
                            }
                        }.show()
                    }
                }
            })
        } catch (e: Exception) {
            Logger.e(e)
        }
    }
}
