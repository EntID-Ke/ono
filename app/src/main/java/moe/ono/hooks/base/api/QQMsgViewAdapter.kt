package moe.ono.hooks.base.api

import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import moe.ono.BuildConfig
import moe.ono.config.ConfigManager.cGetInt
import moe.ono.config.ConfigManager.cPutInt
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.reflex.ClassUtils
import moe.ono.reflex.FieldUtils
import moe.ono.reflex.MethodUtils
import moe.ono.util.HostInfo

@HookItem(path = "API/适配QQMsg内容ViewID")
class QQMsgViewAdapter : ApiHookItem() {


    companion object {
        private var contentViewId = 0

        @JvmStatic
        fun getContentView(msgItemView: View): View {
            return msgItemView.findViewById(contentViewId)
        }

        @JvmStatic
        fun getContentViewId(): Int {
            return contentViewId
        }

        @JvmStatic
        fun hasContentMessage(messageRootView: ViewGroup): Boolean {
            return messageRootView.childCount >= 5
        }
    }

    private var unhook: XC_MethodHook.Unhook? = null

    private fun findContentViewId(): Int {
        return cGetInt(
            "contentViewId${HostInfo.getVersionName()}:${BuildConfig.VERSION_NAME}",
            -1
        )
    }

    private fun putContentViewId(id: Int) {
        cPutInt(
            "contentViewId${HostInfo.getVersionName()}:${BuildConfig.VERSION_NAME}",
            id
        )
    }

    override fun entry(loader: ClassLoader) {
        if (findContentViewId() > 0) {
            contentViewId = findContentViewId()
            return
        }
        val onMsgViewUpdate =
            MethodUtils.create("com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB")
                // QQ 9.3.75 dispatches updates through MviUIState rather than the
                // former (int, item, List, Bundle) signature.
                .methodName("handleUIState")
                .returnType(Void.TYPE)
                .first()
        unhook = hookAfter(onMsgViewUpdate) { param ->
            val thisObject = param.thisObject
            val msgView = FieldUtils.create(thisObject)
                .fieldType(View::class.java)
                .firstValue<View>(thisObject)

            val aioMsgItem = FieldUtils.create(thisObject)
                .fieldType(ClassUtils.findClass("com.tencent.mobileqq.aio.msg.AIOMsgItem"))
                .firstValue<Any>(thisObject)

            if (aioMsgItem == null || msgView == null) return@hookAfter

            val msgRecord: Any = MethodUtils.create(aioMsgItem.javaClass).methodName("getMsgRecord")
                .callFirst(aioMsgItem)

            val elements: ArrayList<Any> = FieldUtils.getField(
                msgRecord, "elements",
                ArrayList::class.java
            )

            for (msgElement in elements) {
                val type: Int =
                    FieldUtils.getField(msgElement, "elementType", Int::class.javaPrimitiveType)
                //文本和图片类型的view 不解析其他类型的 否则解析不出来
                if (type <= 2) {
                    findContentView(msgView as ViewGroup)
                    break
                }
            }
        }
    }

    private fun findContentView(itemView: ViewGroup) {
        for (i in 0..<itemView.childCount) {
            val child = itemView.getChildAt(i)
            if (child.javaClass.name == "com.tencent.qqnt.aio.holder.template.BubbleLayoutCompatPress") {
                contentViewId = child.id
                putContentViewId(child.id)
                //解开hook
                unhook?.unhook()
                break
            }
        }
    }

}
