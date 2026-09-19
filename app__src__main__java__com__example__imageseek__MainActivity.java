package com.example.imageseek;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private static final int MAX_EDGE = 1600;
    private static final int JPEG_QUALITY = 82;
    private static final int JS_CHUNK = 90000;

    private FrameLayout root;
    private ImageView preview;
    private TextView status;
    private Button searchButton;
    private ProgressBar progress;
    private WebView webView;
    private byte[] imageBytes;
    private String mimeType = "image/jpeg";
    private String fileName = "image.jpg";
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildHome();
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private void buildHome() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(28));
        page.setBackgroundColor(Color.WHITE);

        TextView title = text("ImageSeek", 30, Color.rgb(17,24,39));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text("Upload a picture and find visually related images and pages online.", 16, Color.rgb(107,114,128));
        subtitle.setPadding(0, dp(8), 0, dp(18));
        page.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(Color.rgb(248,250,252));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(260));
        pp.bottomMargin = dp(16);
        page.addView(preview, pp);

        Button choose = new Button(this);
        choose.setText("Choose image");
        choose.setAllCaps(false);
        choose.setTextSize(16);
        choose.setOnClickListener(v -> pickImage());
        page.addView(choose, new LinearLayout.LayoutParams(-1, dp(52)));

        searchButton = new Button(this);
        searchButton.setText("Search similar images");
        searchButton.setAllCaps(false);
        searchButton.setTextSize(16);
        searchButton.setEnabled(false);
        searchButton.setOnClickListener(v -> searchImage());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(52));
        sp.topMargin = dp(10);
        page.addView(searchButton, sp);

        status = text("No image selected", 14, Color.rgb(107,114,128));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(14), 0, 0);
        page.addView(status, new LinearLayout.LayoutParams(-1, -2));

        TextView privacy = text("The selected image is sent to Google Lens only when you press Search. Results are loaded in this app.", 12, Color.rgb(107,114,128));
        privacy.setPadding(0, dp(20), 0, 0);
        page.addView(privacy, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(page);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams prog = new FrameLayout.LayoutParams(dp(44), dp(44));
        prog.gravity = Gravity.CENTER;
        root.addView(progress, prog);

        setContentView(root);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        new Thread(() -> loadAndPrepare(uri)).start();
    }

    private void loadAndPrepare(Uri uri) {
        try {
            Bitmap src = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            if (src == null) throw new IOException("Could not decode image");

            int w = src.getWidth(), h = src.getHeight();
            float scale = Math.min(1f, (float) MAX_EDGE / Math.max(w, h));
            int nw = Math.max(1, Math.round(w * scale));
            int nh = Math.max(1, Math.round(h * scale));
            Bitmap resized = (nw == w && nh == h) ? src : Bitmap.createScaledBitmap(src, nw, nh, true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            byte[] bytes = out.toByteArray();
            if (resized != src) resized.recycle();
            src.recycle();

            String displayName = queryDisplayName(uri);
            if (displayName == null || displayName.trim().isEmpty()) displayName = "image.jpg";

            imageBytes = bytes;
            mimeType = "image/jpeg";
            fileName = displayName.toLowerCase(Locale.US).endsWith(".jpg") || displayName.toLowerCase(Locale.US).endsWith(".jpeg")
                    ? displayName : displayName + ".jpg";

            final Bitmap shown = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            runOnUiThread(() -> {
                preview.setImageBitmap(shown);
                searchButton.setEnabled(true);
                status.setText(String.format(Locale.US, "Ready • %d KB", bytes.length / 1024));
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Could not load that image.", Toast.LENGTH_LONG).show());
        }
    }

    private String queryDisplayName(Uri uri) {
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        finally { if (c != null) c.close(); }
        return null;
    }

    private void searchImage() {
        if (imageBytes == null || imageBytes.length == 0) return;
        buildResultsView();
        progress.setVisibility(View.VISIBLE);
        final String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        final long start = System.currentTimeMillis();
        final String endpoint = "https://lens.google.com/v3/upload?ep=cntpubb&hl=en&st=" + start + "&re=df&s=4";

        webView.setVisibility(View.VISIBLE);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
            @Override public void onPageFinished(WebView view, String url) { progress.setVisibility(View.GONE); }
        });
        webView.loadDataWithBaseURL("https://lens.google.com/", localPage(), "text/html", "UTF-8", null);

        handler.postDelayed(() -> sendChunksAndSubmit(base64, endpoint), 700);
    }

    private String localPage() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:sans-serif;margin:0;background:#fff;color:#111827}#busy{padding:24px;text-align:center;color:#6b7280}</style>" +
                "</head><body><div id='busy'>Preparing your image search…</div>" +
                "<form id='upload' method='POST' enctype='multipart/form-data'></form>" +
                "<script>window.parts=[];window.addPart=function(s){window.parts.push(s)};window.submitLens=function(ep,name,type){" +
                "try{let b64=window.parts.join('');let bin=atob(b64);let u=new Uint8Array(bin.length);for(let i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);" +
                "let file=new File([u],name,{type:type});let input=document.createElement('input');input.type='file';input.name='encoded_image';let dt=new DataTransfer();dt.items.add(file);input.files=dt.files;" +
                "let src=document.createElement('input');src.type='hidden';src.name='sbisrc';src.value='Google Chrome';let f=document.getElementById('upload');f.action=ep;f.appendChild(input);f.appendChild(src);f.submit();}catch(e){document.getElementById('busy').textContent='Upload failed: '+e}};</script></body></html>";
    }

    private void sendChunksAndSubmit(String base64, String endpoint) {
        final int total = base64.length();
        for (int i = 0; i < total; i += JS_CHUNK) {
            int end = Math.min(total, i + JS_CHUNK);
            String chunk = base64.substring(i, end).replace("\\", "\\\\").replace("'", "\\'");
            String js = "javascript:window.addPart('" + chunk + "')";
            webView.evaluateJavascript(js, null);
        }
        String safeEndpoint = endpoint.replace("'", "\\'");
        String safeName = fileName.replace("'", "\\'");
        webView.evaluateJavascript("javascript:window.submitLens('" + safeEndpoint + "','" + safeName + "','" + mimeType + "')", null);
    }

    private void buildResultsView() {
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setVisibility(View.GONE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(Color.WHITE);

        Button back = new Button(this);
        back.setText("‹  New search");
        back.setAllCaps(false);
        back.setOnClickListener(v -> {
            if (webView != null) webView.destroy();
            buildHome();
        });
        bar.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));

        TextView label = text("ImageSeek results", 16, Color.rgb(17,24,39));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(10);
        bar.addView(label, lp);

        root = new FrameLayout(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(bar, new LinearLayout.LayoutParams(-1, dp(64)));
        container.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(container, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(44), dp(44));
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);
        setContentView(root);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else if (webView != null && webView.getVisibility() == View.VISIBLE) {
            buildHome();
        } else {
            super.onBackPressed();
        }
    }
}
