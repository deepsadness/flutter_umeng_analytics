package pedia.flutterumenganalytics;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.umeng.analytics.AnalyticsConfig;
import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;
import com.umeng.commonsdk.utils.UMUtils;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterUmengAnalyticsPlugin
 */
public class FlutterUmengAnalyticsPlugin implements MethodCallHandler {
    private Activity activity;

    private static final String TAG = "FlutterUmeng";
    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "umeng_analytics_flutter");
        channel.setMethodCallHandler(new FlutterUmengAnalyticsPlugin(registrar.activity()));
    }

    private FlutterUmengAnalyticsPlugin(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (call.method.equals("init")) {
            init(call, result);
        } else if (call.method.equals("beginPageView")) {
            beginPageView(call, result);
        } else if (call.method.equals("endPageView")) {
            endPageView(call, result);
        } else if (call.method.equals("loginPageView")) {
            loginPageView(call, result);
        } else if (call.method.equals("eventCounts")) {
            eventCounts(call, result);
        } else {
            result.notImplemented();
        }
    }

    public void init(MethodCall call, Result result) {
        String channelName = getAppMetaData(activity,"UMENG_CHANNEL");
        boolean logable = (boolean)call.argument("logEnable");
        Log.d(TAG,"ChannelName="+channelName+", logable= "+logable);
        // 设置组件化的Log开关，参数默认为false，如需查看LOG设置为true
        UMConfigure.setLogEnabled((boolean)call.argument("logEnable"));
        /**
         * 初始化common库 参数1：上下文，不能为空 参数2：【友盟+】Appkey名称 参数3：【友盟+】Channel名称
         * 参数4：设备类型，UMConfigure.DEVICE_TYPE_PHONE为手机、UMConfigure.DEVICE_TYPE_BOX为盒子，默认为手机
         * 参数5：Push推送业务的secret
         */
        UMConfigure.init(activity, (String) call.argument("key"), channelName, UMConfigure.DEVICE_TYPE_PHONE, null);
        // 设置日志加密 参数：boolean 默认为false（不加密）
        UMConfigure.setEncryptEnabled((Boolean) call.argument("encrypt"));

        double interval = call.argument("interval");
        if (call.argument("interval") != null) {
            // Session间隔时长,单位是毫秒，默认Session间隔时间是30秒,一般情况下不用修改此值
            MobclickAgent.setSessionContinueMillis(Double.valueOf(interval).longValue());
        } else {
            // Session间隔时长,单位是毫秒，默认Session间隔时间是30秒,一般情况下不用修改此值
            MobclickAgent.setSessionContinueMillis(30000L);
        }


        // true表示打开错误统计功能，false表示关闭 默认为打开
        MobclickAgent.setCatchUncaughtExceptions((Boolean) call.argument("reportCrash"));

        // 页面采集的两种模式：AUTO和MANUAL，Android 4.0及以上版本使用AUTO,4.0以下使用MANUAL
        int results = call.argument("mode");
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (results == 0) {
                // 大于等于4.4选用AUTO页面采集模式
                MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO);
            }
        } else if (results == 1) {
            MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL);
        }

        //因为好像上面的渠道设置没有用，手动再设置一个
        String channel = AnalyticsConfig.getChannel(activity);
        UMUtils.setChannel(activity,channel);
        Log.d(TAG,"setChannel ChannelName="+channel);

        result.success(true);
    }

    // 打开页面时进行统计
    public void beginPageView(MethodCall call, Result result) {
        MobclickAgent.onPageStart((String) call.argument("name"));
        MobclickAgent.onResume(activity);
        result.success(null);
    }

    // 关闭页面时结束统计
    public void endPageView(MethodCall call, Result result) {
        MobclickAgent.onPageEnd((String) call.argument("id"));
        MobclickAgent.onPause(activity);
        result.success(null);
    }

    // 登陆统计
    public void loginPageView(MethodCall call, Result result) {
        MobclickAgent.onProfileSignIn((String) call.argument("id"));
        // Session间隔时长,单位是毫秒，默认Session间隔时间是30秒,一般情况下不用修改此值
        MobclickAgent.setSessionContinueMillis((Long) call.argument("interval"));
    }

    /**
     * 计数事件统计 例如：统计微博应用中”转发”事件发生的次数，那么在转发的函数里调用该函数
     *
     * @param call
     * @param result
     */
    public void eventCounts(MethodCall call, Result result) {
        /**
         * 参数1： context 当前宿主进程的ApplicationContext上下文 参数2： eventId 为当前统计的事件ID 参数3： label
         * 为事件的标签属性
         */
        MobclickAgent.onEvent((Context) activity, (String) call.argument("eventId"), (String) call.argument("label"));
        result.success(null);
    }

    public static String getAppMetaData(Context ctx, String key) {
        if (ctx == null || TextUtils.isEmpty(key)) {
            return null;
        }
        String resultData = null;
        try {
            PackageManager packageManager = ctx.getPackageManager();
            if (packageManager != null) {
                //注意此处为ApplicationInfo，因为友盟设置的meta-data是在application标签中
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA);
                if (applicationInfo != null) {
                    if (applicationInfo.metaData != null) {
                        //key要与manifest中的配置文件标识一致
                        resultData = applicationInfo.metaData.getString(key);
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return resultData;
    }
}
