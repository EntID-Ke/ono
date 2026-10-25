package moe.ono.hooks._core.factory;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.robv.android.xposed.XposedBridge;
import moe.ono.BuildConfig;
import moe.ono.hooks._base.BaseHookItem;
import moe.ono.util.LogUtils;


/**
 * 异常处理工厂
 */
public class ExceptionFactory {
    private final static Map<BaseHookItem, List<Throwable>> exceptionMap = new HashMap<>();

    /**
     * 检查是否超过3个或重复
     *
     * @return 为true则已经超过三个或者添加过
     */
    private static boolean check(BaseHookItem item, Throwable throwable) {
        //每个item最多只保存3个Throwable,不然添加太多会占用太多不必要的内存
        List<Throwable> exceptionsList = exceptionMap.get(item);
        if (exceptionsList == null || exceptionsList.size() < 3) {
            return false;
        }
        //判断是否已经添加过了 添加过则不再重复添加
        for (Throwable ex : exceptionsList) {
            if (Objects.equals(ex.getMessage(), throwable.getMessage())) {
                return true;
            }
        }
        return false;
    }

    public static void add(BaseHookItem item, Throwable throwable) {
        io.github.qqmodulemanager.bridge.BridgeClient.reportFailure(
                io.github.qqmodulemanager.bridge.BridgeClient.featureForClass(item.getClass().getName()), throwable);
        moe.ono.integration.QQModuleManagerAdapter.reportDependencyFailure(item, throwable);
        if (check(item, throwable)) {
            return;
        }
        List<Throwable> exceptionsList = exceptionMap.get(item);
        if (exceptionsList == null) {
            exceptionsList = new ArrayList<>();
        }
        exceptionsList.add(0, throwable);
        exceptionMap.put(item, exceptionsList);
        XposedBridge.log(throwable);
        LogUtils.addError("item_" + item.getItemName(), throwable);

    }

    public static String getStackTrace(BaseHookItem hookItem) {
        StringBuilder builder = new StringBuilder();
        List<Throwable> exceptionsList = exceptionMap.get(hookItem);
        if (exceptionsList == null) {
            return builder.toString();
        }
        for (Throwable ex : exceptionsList) {
            builder.append(Log.getStackTraceString(ex));
            builder.append("\n");
        }
        return builder.toString();
    }

}
