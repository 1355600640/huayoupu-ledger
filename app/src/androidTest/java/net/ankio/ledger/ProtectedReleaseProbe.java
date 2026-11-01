package net.ankio.ledger;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Separate test runner, using only framework and public JS contracts against the actual protected APK. */
public final class ProtectedReleaseProbe extends Instrumentation {
    private WebView web;
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();Activity activity=null;File image=null;JSONArray before=null;
        try {
            Intent intent=new Intent().setComponent(new ComponentName(getTargetContext().getPackageName(),"net.ankio.ledger.ui.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);
            Activity target=activity;runOnMainSync(()->web=find(target.getWindow().getDecorView()));
            await("window.LedgerUI && document.readyState==='complete'");
            JSONObject snapshot=new JSONObject(unquote(js("LedgerNative.snapshot()")));
            if(snapshot.has("ai"))throw new AssertionError("Engine configuration must not cross the UI bridge");
            before=new JSONArray();JSONArray rows=snapshot.getJSONArray("records");for(int i=0;i<rows.length();i++)before.put(rows.getJSONObject(i).getLong("id"));
            image=new File(getTargetContext().getCacheDir(),"protected-release-ocr-smoke.png");
            Bitmap bitmap=Bitmap.createBitmap(900,1600,Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(55);
                canvas.drawText("ReleaseSmoke 测试商户",80,220,paint);paint.setTextSize(95);canvas.drawText("¥ 12.34",180,440,paint);paint.setTextSize(55);canvas.drawText("支付成功",280,580,paint);
                paint.setTextSize(38);canvas.drawText("支付时间 2026年10月19日 14:50:00",70,850,paint);canvas.drawText("付款方式 零钱",70,950,paint);
                try(FileOutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            } finally {bitmap.recycle();}
            Intent response=new Intent().setData(Uri.fromFile(image));AtomicReference<Throwable> error=new AtomicReference<>();
            runOnMainSync(()->{try {Method method=target.getClass().getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);method.setAccessible(true);method.invoke(target,10,Activity.RESULT_OK,response);}catch(Throwable ex){error.set(ex);}});
            if(error.get()!=null)throw new AssertionError(error.get());
            await("window.LedgerUI.getState().draft && Number(document.querySelector('#amount').value)===12.34");
            JSONObject state=new JSONObject(unquote(js("JSON.stringify(LedgerUI.getState())")));
            if(!"EXPENSE".equals(state.getJSONObject("draft").getString("direction")))throw new AssertionError("Direction protocol changed");
            result.putString("stream","PASS protected release: bundled local OCR reads 12.34 and EXPENSE; public JS/storage bridge works\n");
        } catch(Throwable ex) {
            StringWriter trace=new StringWriter();ex.printStackTrace(new PrintWriter(trace));result.putString("stream",trace.toString());result.putString("failure",ex.toString());
        } finally {
            if(image!=null)image.delete();
            if(before!=null && web!=null)try {js("(()=>{const before="+before+";JSON.parse(LedgerNative.snapshot()).records.filter(r=>!before.includes(r.id)).forEach(r=>LedgerNative.delete(r.id));go('ledger');refresh();return true})()");}catch(Exception ignored){}
            if(activity!=null){Activity target=activity;runOnMainSync(target::finish);}
        }
        finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
    private static WebView find(View view){if(view instanceof WebView)return (WebView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){WebView found=find(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private String js(String expression) throws Exception {CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();runOnMainSync(()->web.evaluateJavascript(expression,s->{value.set(s);latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("JS timeout");return value.get();}
    private String unquote(String json) throws Exception{return new JSONArray("["+json+"]").getString(0);}
    private void await(String condition) throws Exception {long until=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<until){if("true".equals(js("Boolean("+condition+")")))return;SystemClock.sleep(50);}throw new AssertionError("Timed out: "+condition+"; "+js("JSON.stringify(LedgerUI.getState())"));}
}
