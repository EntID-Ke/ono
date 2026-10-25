package moe.ono.integration;

import android.content.Context;
import moe.ono.BuildConfig;
import moe.ono.hooks._core.factory.HookItemFactory;
import moe.ono.hooks.item.chat.HoldRevokeMsg;
import moe.ono.hooks.base.api.QQOnMsfPush;
import io.github.qqmodulemanager.bridge.BridgeClient;
import io.github.qqmodulemanager.bridge.FeatureCatalog;
import moe.ono.hooks._base.BaseHookItem;
import moe.ono.hooks._base.BaseSwitchFunctionHookItem;
import moe.ono.hooks._base.BaseClickableFunctionHookItem;
import moe.ono.util.SyncUtils;

public final class QQModuleManagerAdapter {
    private QQModuleManagerAdapter() {}
    public static void reportDependencyFailure(BaseHookItem item,Throwable failure) {
        if(item instanceof QQOnMsfPush)BridgeClient.reportFailure(BridgeClient.RECALL,failure);
        if(item instanceof moe.ono.hooks.base.api.QQUpdateSession || item instanceof moe.ono.hooks.base.api.QQMessageViewListener)
            BridgeClient.reportFailure("read_position",failure);
        if(item instanceof moe.ono.hooks.dispatcher.MenuBuilderHook) {
            BridgeClient.reportFailure("local_text",failure);BridgeClient.reportFailure("sticker_panel",failure);
        }
    }
    public static void register(Context context) {
        BridgeClient.initialize(context,"ono",BuildConfig.VERSION_NAME);
        for(FeatureCatalog.Feature f:FeatureCatalog.ALL)if(!f.classes("ono").isEmpty())
            BridgeClient.registerFeature(f.id,()->initialize(f));
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static boolean initialize(FeatureCatalog.Feature feature)throws ClassNotFoundException {
        if(feature.id.equals(BridgeClient.RECALL) && !HookItemFactory.getItem(QQOnMsfPush.class).isQQMMInitializationSuccessful())return false;
        if(feature.id.equals("read_position") && (!HookItemFactory.getItem(moe.ono.hooks.base.api.QQUpdateSession.class).isQQMMInitializationSuccessful()
                || !HookItemFactory.getItem(moe.ono.hooks.base.api.QQMessageViewListener.class).isQQMMInitializationSuccessful()))return false;
        BaseHookItem hook=HookItemFactory.getItem((Class)Class.forName(feature.classes("ono").get(0)));
        if(hook instanceof moe.ono.hooks.dispatcher.OnMenuBuilder) {
            moe.ono.hooks.dispatcher.MenuBuilderHook menus=HookItemFactory.getItem(moe.ono.hooks.dispatcher.MenuBuilderHook.class);
            boolean supported=false;
            for(String type:((moe.ono.hooks.dispatcher.OnMenuBuilder)hook).getTargetTypes())if(menus.supportsQQMMTarget(type))supported=true;
            if(!menus.isQQMMInitializationSuccessful() || !supported)return false;
        }
        int target=hook instanceof BaseSwitchFunctionHookItem ? ((BaseSwitchFunctionHookItem)hook).getTargetProcess()
                : ((BaseClickableFunctionHookItem)hook).getTargetProcess();
        if((target & SyncUtils.getProcessType())==0)return false;
        hook.startLoad();
        return hook.isQQMMInitializationSuccessful();
    }
}
