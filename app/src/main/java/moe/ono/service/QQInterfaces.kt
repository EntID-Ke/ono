package moe.ono.service

import cn.hutool.core.lang.reflect.MethodHandleUtil
import com.github.kyuubiran.ezxhelper.utils.isPublic
import com.github.kyuubiran.ezxhelper.utils.paramCount
import com.tencent.common.app.AppInterface
import com.tencent.qphone.base.remote.FromServiceMsg
import com.tencent.qphone.base.remote.ToServiceMsg
import moe.ono.hooks.base.util.Toasts
import moe.ono.service.inject.servlets.IServlet
import moe.ono.util.FunProtoData
import moe.ono.util.FunProtoData.getUnpPackage
import moe.ono.util.Initiator.load
import moe.ono.util.Logger
import mqq.app.MobileQQ
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

abstract class QQInterfaces {
    companion object {
        private data class ServiceBinding(
            val service: Any,
            val request: Method,
            val response: Method,
        )

        @Volatile
        private var serviceBinding: ServiceBinding? = null

        private fun requireServiceBinding(): ServiceBinding {
            // A service that was not ready during startup can become ready later.
            if (serviceBinding == null) update()
            return checkNotNull(serviceBinding) { "QQ 服务尚未初始化" }
        }

        val seqReceiveMap = ConcurrentHashMap<Int, FromServiceMsg>()
        private val seqFactory = AtomicInteger(0)

        private fun generateSeq(): Int {
            return seqFactory.addAndGet(1)
        }

        private fun getReceiveAndRemove(seq: Int): FromServiceMsg? {
            if (seqReceiveMap.containsKey(seq)) {
                val fromServiceMsg = seqReceiveMap[seq]
                seqReceiveMap.remove(seq)
                return fromServiceMsg
            }
            return null
        }

        var app = (if (PlatformUtils.isMqqPackage())
            MobileQQ.getMobileQQ().waitAppRuntime()
        else
            MobileQQ.getMobileQQ().waitAppRuntime(null)) as AppInterface

        private fun sendToServiceMsg(to: ToServiceMsg) {
            app.sendToService(to)
        }

        private fun createToServiceMsg(cmd: String): ToServiceMsg {
            return ToServiceMsg("mobileqq.service", app.currentAccountUin, cmd)
        }

        fun sendBuffer(
            cmd: String,
            isProto: Boolean,
            data: ByteArray,
        ) {
            val toServiceMsg = createToServiceMsg(cmd)
            toServiceMsg.putWupBuffer(data)
            toServiceMsg.addAttribute("req_pb_protocol_flag", isProto)
            sendToServiceMsg(toServiceMsg)
        }


        // FIXME: 部分情况下发包失败 ，但无法精准复现
        fun sendOidbSvcTrpcTcp(
            cmd: String,
            flag: Int,
            serviceType: Int,
            data: ByteArray,
        ): Int {
            val toServiceMsg = OidbUtil.makeOIDBPkg(
                cmd, flag, serviceType, data).apply {
                appSeq = generateSeq()
            }
            try {
                sendReq(toServiceMsg)
            } catch (e: Exception) {
                Logger.e(e)
                Toasts.show(app.getApp(), Toasts.TYPE_INFO, "未初始化，请稍后重试")
            }

            return toServiceMsg.appSeq
        }

        private fun sendReq(toServiceMsg: ToServiceMsg) {
            val binding = requireServiceBinding()
            // qq <= 8.9.8
            toServiceMsg.extraData.putBoolean("req_pb_protocol_flag", true)
            // qq >= 8.9.10
            toServiceMsg.attributes["req_pb_protocol_flag"] = true
            if (binding.request.parameterTypes.size == 2) {
                MethodHandleUtil.invokeSpecial<Unit>(binding.service, binding.request, toServiceMsg, IServlet::class.java)
            } else {
                MethodHandleUtil.invokeSpecial<Unit>(binding.service, binding.request, toServiceMsg, null, IServlet::class.java)
            }
        }

        private fun decodeResponse(toServiceMsg: ToServiceMsg, fromServiceMsg: FromServiceMsg) {
            val binding = requireServiceBinding()
            MethodHandleUtil.invokeSpecial<Unit>(binding.service, binding.response, fromServiceMsg.isSuccess, toServiceMsg, fromServiceMsg, null)
        }

        fun receive(seq: Int): JSONObject? {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 5_000) {
                Thread.sleep(120)
                val fromServiceMsg = getReceiveAndRemove(seq) ?: continue
                val toServiceMsg = fromServiceMsg.attributes[FromServiceMsg::class.java.simpleName] as ToServiceMsg
                decodeResponse(toServiceMsg, fromServiceMsg)
                val data = FunProtoData()
                data.fromBytes(
                    getUnpPackage(
                        fromServiceMsg.wupBuffer
                    )
                )

                return data.toJSON()
            }
            return null
        }

        @Synchronized
        fun update(){
            // Do not reuse a previous account's service if rebinding fails.
            serviceBinding = null
            app = (if (PlatformUtils.isMqqPackage())
                MobileQQ.getMobileQQ().waitAppRuntime()
            else
                MobileQQ.getMobileQQ().waitAppRuntime(null)) as AppInterface

            val cBaseService = checkNotNull(load("com.tencent.mobileqq.service.MobileQQServiceBase"))
            val request = cBaseService.methods.firstOrNull {
                val types = it.parameterTypes
                it.returnType == Void.TYPE && it.isPublic && types.size in 2..3
                    && types.first() == ToServiceMsg::class.java
                    && types.last() == Class::class.java
            } ?: error("初始化失败 -> mRealHandlerReq")
            val response = cBaseService.methods.firstOrNull {
                val types = it.parameterTypes
                it.returnType == Void.TYPE && it.isPublic && it.paramCount == 4
                    && types[0] == Boolean::class.javaPrimitiveType
                    && types[1] == ToServiceMsg::class.java
                    && types[2] == FromServiceMsg::class.java
                    && types[3] == Exception::class.java
            } ?: error("初始化失败 -> mHandlerResponse")
            val service = MobileQQServiceResolver.resolve(app, cBaseService)
            serviceBinding = ServiceBinding(service, request, response)
            Logger.d("QQ service initialized: ${service.javaClass.name}")
        }
    }


}
