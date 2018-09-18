package com.cjt.checkgua;

import android.content.ContentValues;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by CJT on 2018/5/23.
 */

public class CheckGUA implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        final HashMap<String,Long> redBagIdMap = new HashMap<>();
        XposedHelpers.findAndHookMethod(
                "com.tencent.wcdb.database.SQLiteDatabase",
                lpparam.classLoader,
                "insert",
                String.class, String.class, ContentValues.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object tableName = param.args[0];
                        Object obj1 = param.args[1];
                        if("message".equals(tableName) && obj1.equals("msgId")){
                            ContentValues cv = (ContentValues)param.args[2];
                            String content = cv.getAsString("content");
                            XposedBridge.log("CJT_XP: 收到一条信息");
                            XposedBridge.log("CJT_XP: 信息内容为:"+content);
                            long createTime = cv.getAsLong("createTime");
                            XposedBridge.log("CJT_XP: 信息创建时间为:"+createTime);
                            if(!TextUtils.isEmpty(content)){
                                // 解析xml获取paymsgid
                                int startIdx = content.indexOf("<paymsgid>") ;
                                String payMsgId="";
                                if(startIdx!=-1) {
                                    XposedBridge.log("CJT_XP: 收到的信息为红包信息");
                                    payMsgId = content.substring(startIdx+19,startIdx+19+31);
                                    XposedBridge.log("CJT_XP: 红包payMsgId为"+payMsgId);
                                }
                                if(!TextUtils.isEmpty(payMsgId)){
                                    String redBagId = payMsgId.substring(13);
                                    XposedBridge.log("CJT_XP: 红包Id为"+redBagId);
                                    redBagIdMap.put(redBagId,createTime);
                                    XposedBridge.log("CJT_XP: 存入MAP，redBagId:"+redBagId+",createTime:"+createTime);
                                }

                            }
                        }
                        super.afterHookedMethod(param);
                    }
                });
        XposedHelpers.findAndHookMethod(
                "com.tencent.mm.plugin.luckymoney.ui.i",
                lpparam.classLoader,
                "getView",
                int.class, View.class, ViewGroup.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int index = (Integer)param.args[0];
                        Object obj = param.thisObject;
                        Method method = obj.getClass().getDeclaredMethod("sJ",int.class);
                        method.setAccessible(true);
                        Object itemObj = method.invoke(obj, index);
                        Object userName = itemObj.getClass().getField("userName").get(itemObj);
                        Object owJ = itemObj.getClass().getField("owJ").get(itemObj); // payMsgId
                        Object oxe = itemObj.getClass().getField("oxe").get(itemObj); // 红包钱数
                        Object oxf = itemObj.getClass().getField("oxf").get(itemObj); // 时间戳
                        Object oxr = itemObj.getClass().getField("oxr").get(itemObj); // 人名
                        Object oxs = itemObj.getClass().getField("oxs").get(itemObj);
                        Object oxt = itemObj.getClass().getField("oxt").get(itemObj); // 留言
                        Object oxu = itemObj.getClass().getField("oxu").get(itemObj); // 手气最佳
                        XposedBridge.log("CJT_XP: 获取领取红包列表中的\""+oxr+"\"领取红包的信息");
                        XposedBridge.log("CJT_XP: "+"payMsgId为"+owJ
                                +"；红包钱数为:"+oxe+"分元；时间戳为:"+oxf
                                +"；留言为:"+oxt+"；是否手气最佳:"+oxu);

                        String payMsgId = (String)owJ ;
                        String redBagId = payMsgId.substring(13) ;
                        XposedBridge.log("CJT_XP: redBagId为"+redBagId);
                        long time = Long.parseLong((String)oxf)*1000 ;
                        XposedBridge.log("CJT_XP: 某人领取红包的时间戳为"+time);
                        long costTime = -1 ;
                        if(redBagIdMap.containsKey(redBagId)){
                            long redBagCreateTime = redBagIdMap.get(redBagId);
                            costTime = time-redBagCreateTime ;
                        }
                        XposedBridge.log("CJT_XP: 某人领取红包的耗时为"+costTime);

                        Object result = param.getResult();
                        if(result instanceof LinearLayout){
                            LinearLayout itemView = (LinearLayout)result;
                            LinearLayout leftLayout = (LinearLayout)itemView.getChildAt(1);
                            TextView timeText = (TextView)leftLayout.getChildAt(3);
                            if ( costTime!=-1 ){
                                timeText.setTextColor(0xFFFF0000);
                                timeText.setTextSize(19);
                                TextPaint paint = timeText.getPaint();
                                paint.setFakeBoldText(true);
                                timeText.setText("用时"+(costTime/1000.0)+"s");
                            }
                        }
                        super.afterHookedMethod(param);
                    }
                }
        );
    }
}
