package bg.cosmos.observatory;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.*;
import android.view.View;
import android.widget.Toast;
import android.os.Build;
import android.graphics.Color;
import android.webkit.ValueCallback;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import java.io.*;
import java.net.*;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooserCallback;
    private static final int PICK_IMAGE=501;
    private static final int PERMISSION_GEO=502;
    private String pendingOrigin;
    private GeolocationPermissions.Callback pendingGeolocation;
    private TinyServer server;
    private static final int PORT=18765;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7,16,29));
        getWindow().setNavigationBarColor(Color.rgb(7,16,29));
        web=new WebView(this);
        setContentView(web);
        WebSettings ws=web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req){
                Uri uri=req.getUrl();
                if("127.0.0.1".equals(uri.getHost())) return false;
                try {startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params){
                if(chooserCallback!=null) chooserCallback.onReceiveValue(null);
                chooserCallback=callback;
                Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
                try{startActivityForResult(Intent.createChooser(intent,"Choose observation photo"),PICK_IMAGE);return true;}
                catch(Exception e){chooserCallback=null;callback.onReceiveValue(null);return false;}
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback){
                if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){callback.invoke(origin,true,false);}
                else {pendingOrigin=origin;pendingGeolocation=callback;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},PERMISSION_GEO);}
            }
        });
        web.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength)->{
            if(url.startsWith("blob:")){
                Toast.makeText(this,"For calendar files, use the web version until native export is added.",Toast.LENGTH_LONG).show();
            }else{
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"Cannot open download",Toast.LENGTH_SHORT).show();}
            }
        });
        try {server=new TinyServer();server.start();web.loadUrl("http://127.0.0.1:"+PORT+"/index.html");}
        catch(Exception e){Toast.makeText(this,"COSMOS server error: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_IMAGE && chooserCallback!=null){
            chooserCallback.onReceiveValue(result==RESULT_OK && data!=null && data.getData()!=null?new Uri[]{data.getData()}:null);
            chooserCallback=null;
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==PERMISSION_GEO && pendingGeolocation!=null){
            boolean allowed=false;for(int g:results) if(g==PackageManager.PERMISSION_GRANTED)allowed=true;
            pendingGeolocation.invoke(pendingOrigin,allowed,false);
            pendingGeolocation=null;pendingOrigin=null;
        }
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(server!=null)server.shutdown();if(web!=null)web.destroy();super.onDestroy();}

    private class TinyServer extends Thread{
        private ServerSocket socket;private volatile boolean running=true;
        TinyServer() throws IOException { socket=new ServerSocket(PORT,12,InetAddress.getByName("127.0.0.1"));setDaemon(true); }
        @Override public void run(){while(running){try{Socket conn=socket.accept();new Thread(()->serve(conn)).start();}catch(IOException ignored){if(!running)break;}}}
        void shutdown(){running=false;try{socket.close();}catch(IOException ignored){}}
        void serve(Socket conn){
            try(Socket c=conn){c.setSoTimeout(4000);BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));String request=r.readLine();if(request==null)return;
                String[] parts=request.split(" ");if(parts.length<2)return;
                String path=parts[1].split("\\?")[0];if(path.equals("/"))path="/index.html";
                if(!path.matches("/[a-zA-Z0-9._-]+")){reply(c,404,"text/plain",new byte[0]);return;}
                String name=path.substring(1);byte[] bytes;
                try(InputStream input=getAssets().open(name);ByteArrayOutputStream bout=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)bout.write(buffer,0,n);bytes=bout.toByteArray();}
                catch(IOException e){reply(c,404,"text/plain",new byte[0]);return;}
                String mime=name.endsWith(".html")?"text/html":name.endsWith(".js")?"application/javascript":name.endsWith(".css")?"text/css":name.endsWith(".svg")?"image/svg+xml":name.endsWith(".webmanifest")?"application/manifest+json":"application/octet-stream";
                reply(c,200,mime,bytes);
            }catch(Exception ignored){}
        }
        void reply(Socket c,int code,String mime,byte[] bytes)throws IOException{OutputStream o=c.getOutputStream();o.write(("HTTP/1.1 "+code+(code==200?" OK":" Not Found")+"\r\nContent-Type: "+mime+"; charset=UTF-8\r\nContent-Length: "+bytes.length+"\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));o.write(bytes);o.flush();}
    }
}
