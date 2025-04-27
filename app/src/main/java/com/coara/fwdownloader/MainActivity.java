package com.coara.fwdownloader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_PERMISSION_SHOWN = "permission_shown";
    private int selectedOptionIndex = -1;
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);
    private static final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    private View loginLayout;
    private View mainLayout;
    private EditText memberIdInput, passwordInput;
    private Button loginButton;
    private Button downloadButton;
    private Button firmwareSelectionButton;
    private Button switchExecuteButton;
    private ListView firmwareListView;
    private Button apkSelectionButton;

    private String akamaiToken = "";
    private String firmwareBaseUrl = "";
    private final List<String> firmwareList = Collections.synchronizedList(new ArrayList<>());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String selectedFirmwareInfo = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        } else {
            showPermissionToastOnce();
            initApp();
        }
    }

    @Override
    @Deprecated
    public void onBackPressed() {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPermissionToastOnce();
                initApp();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.permission_error)
                        .setMessage(R.string.deny_error_message)
                        .setPositiveButton(R.string.exit_app, (dialog, which) -> finishAndRemoveTask())
                        .setCancelable(false)
                        .show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showPermissionToastOnce() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_PERMISSION_SHOWN, false)) {
            Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
            prefs.edit().putBoolean(KEY_PERMISSION_SHOWN, true).apply();
        }
    }

    private void initApp() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        setContentView(R.layout.activity_main);


        loginLayout = findViewById(R.id.loginLayout);
        mainLayout = findViewById(R.id.mainLayout);
        memberIdInput = findViewById(R.id.memberId);
        passwordInput = findViewById(R.id.password);
        loginButton = findViewById(R.id.loginButton);
        firmwareListView = findViewById(R.id.firmwareListView);
        downloadButton = findViewById(R.id.downloadButton);
        firmwareSelectionButton = findViewById(R.id.firmwareSelectionButton);
        apkSelectionButton = findViewById(R.id.apkSelectionButton);
        Button zipButton = findViewById(R.id.zipButton);
        Button txtButton = findViewById(R.id.txtButton);
        loginLayout.setVisibility(View.VISIBLE);
        mainLayout.setVisibility(View.GONE);
        loginButton.setOnClickListener(v -> {
            String memberId = memberIdInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            if (memberId.length() != 10) {
                Toast.makeText(MainActivity.this, "会員番号は10桁で入力してください", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 8 || password.length() > 16) {
                Toast.makeText(MainActivity.this, "パスワードは8～16文字で入力してください", Toast.LENGTH_SHORT).show();
                return;
            }
            doLogin(memberId, password);
        });

        if (loadLoginInfo()) {
            loginButton.performClick();
        }

        apkSelectionButton.setOnClickListener(v -> showApkSelectionDialog());

        firmwareListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedFirmwareInfo = firmwareList.get(position);
            Toast.makeText(MainActivity.this, "選択: " + selectedFirmwareInfo, Toast.LENGTH_SHORT).show();
        });

        downloadButton.setOnClickListener(v -> {
            if (selectedFirmwareInfo.isEmpty()) {
                Toast.makeText(MainActivity.this, "ファームを選択してください", Toast.LENGTH_SHORT).show();
            } else {
                downloadFirmware(selectedFirmwareInfo);
            }
        });

        firmwareSelectionButton.setOnClickListener(v -> showFirmwareModelSelectionDialog());

        zipButton.setOnClickListener(v -> showFileListByExtension(".zip"));
        txtButton.setOnClickListener(v -> showFileListByExtension(".txt"));

        
        ViewGroup parentLayout = (ViewGroup) zipButton.getParent();
        switchExecuteButton = new Button(this);
        switchExecuteButton.setText("切り替え実行");
        parentLayout.addView(switchExecuteButton);
        switchExecuteButton.setOnClickListener(v -> showSelectionDialog());
    }

    private void showSelectionDialog() {
        String[][] options = {
                {"test A", "test", "A"},
                {"test B", "test", "B"},
                {"test2 A", "test2", "A"},
                {"test2 B", "test2", "B"},
                {"test3 A", "test3", "A"},
                {"test3 B", "test3", "B"},
                {"test4 A", "test4", "A"},
                {"test4 B", "test4", "B"},
                {"rel A", "rel", "A"},
                {"rel B", "rel", "B"},
                {"rel2 A", "rel2", "A"},
                {"rel2 B", "rel2", "B"},
                {"dev A", "dev", "A"},
                {"dev B", "dev", "B"}
        };

        String[] optionNames = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            optionNames[i] = options[i][0];
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("選択してください");
        builder.setSingleChoiceItems(optionNames, selectedOptionIndex, (dialog, which) -> selectedOptionIndex = which);
        builder.setPositiveButton("実行", (dialog, which) -> {
            if (selectedOptionIndex != -1) {
                String akamaiType = options[selectedOptionIndex][1];
                String akamaiTable = options[selectedOptionIndex][2];
                fetchFirmwareList2(akamaiType, akamaiTable, "7", "B");
            }
            Toast.makeText(MainActivity.this, "切り替えました", Toast.LENGTH_SHORT).show();
            firmwareListView.setEnabled(false);
            handler.postDelayed(() -> {
                clearListView();
                firmwareListView.setEnabled(true);
            }, 1000);
        });
        builder.setNegativeButton("キャンセル", null);
        builder.show();
    }

    private void fetchFirmwareList2(String akamaiType, String akamaiTable, String akamaiListParam, String akamaiInfo) {
        executor.execute(() -> {
            try {
                firmwareBaseUrl = "https://townak.benesse.ne.jp/" + akamaiType + "/" + akamaiTable + "/sp_84";
                String firmwareXmlUrl = firmwareBaseUrl + "/authorized/list/200" + akamaiListParam +
                        "/deliveryInfo_APL000" + akamaiInfo + ".xml";
                Log.d("FirmwareFetch", "Firmware XML URL: " + firmwareXmlUrl);
                URL url = new URL(firmwareXmlUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/xml");
                if (!akamaiToken.isEmpty()) {
                    conn.setRequestProperty("Cookie", "town_akamai_token=" + akamaiToken);
                }
                byte[] xmlBytes = readAllBytes(conn.getInputStream());
                String utf8Content = new String(xmlBytes, StandardCharsets.UTF_8);
                Log.d("FirmwareFetch", "Firmware XML: " + utf8Content);
                List<String> list = parseFirmwareXml(utf8Content);
                synchronized (firmwareList) {
                    firmwareList.clear();
                    firmwareList.addAll(list);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e("FirmwareFetch", "エラー発生", e);
            }
        });
    }

    private void clearListView() {
        
        runOnUiThread(() -> {
            ListView firmwareListView = findViewById(R.id.firmwareListView);
            if (firmwareListView != null) {
                ArrayAdapter<String> adapter = (ArrayAdapter<String>) firmwareListView.getAdapter();
                if (adapter != null) {
                    adapter.clear(); 
                    adapter.notifyDataSetChanged(); 
                }
            }
        });
    }


    private void doLogin(String memberId, String password) {
        if (memberId == null || memberId.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            handler.post(() -> Toast.makeText(MainActivity.this, "ログイン失敗：入力が不正です", Toast.LENGTH_SHORT).show());
            return;
        }
        if (memberId.length() != 10) {
            handler.post(() -> Toast.makeText(MainActivity.this, "会員番号は10桁で入力してください", Toast.LENGTH_SHORT).show());
            return;
        }
        if (password.length() < 8 || password.length() > 16) {
            handler.post(() -> Toast.makeText(MainActivity.this, "パスワードは8～16文字で入力してください", Toast.LENGTH_SHORT).show());
            return;
        }
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
    URL url = new URL("https://loginc.benesse.ne.jp/d/login");
    conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    conn.setDoOutput(true);
    String postData = "usr_name=" + URLEncoder.encode(memberId, "UTF-8") +
            "&usr_password=" + URLEncoder.encode(password, "UTF-8");
    byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);
    conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
    try (OutputStream os = conn.getOutputStream()) {
        os.write(postDataBytes);
        os.flush();
    }
    int responseCode = conn.getResponseCode();
    String responseBody = (responseCode == HttpURLConnection.HTTP_OK)
            ? readStream(conn.getInputStream()) : readStream(conn.getErrorStream());
    CookieManager cm = (CookieManager) CookieHandler.getDefault();
    if (responseCode == HttpURLConnection.HTTP_OK && !cm.getCookieStore().getCookies().isEmpty()) {
        getAkamaiToken();
        executor.execute(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!akamaiToken.isEmpty()) {
                handler.post(() -> {
                    Toast.makeText(MainActivity.this, "ログインに成功しました", Toast.LENGTH_SHORT).show();
                    InputMethodManager imm = (InputMethodManager) MainActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                    View view = MainActivity.this.getCurrentFocus();
                    if (view == null) {
                        view = loginLayout;
                    }
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    loginLayout.setVisibility(View.GONE);
                    mainLayout.setVisibility(View.VISIBLE);
                });
                saveLoginInfo(memberId, password);
            } else {
                handler.post(() -> Toast.makeText(MainActivity.this, "ログイン失敗：Akamai Token を取得できませんでした", Toast.LENGTH_SHORT).show());
            }
        });
    } else {
        handler.post(() -> Toast.makeText(MainActivity.this, "ログイン失敗：認証情報が正しくありません", Toast.LENGTH_SHORT).show());
    }
} catch (Exception e) {
    handler.post(() -> Toast.makeText(MainActivity.this, "ネットワーク接続エラー", Toast.LENGTH_SHORT).show());
} finally {
    if (conn != null) conn.disconnect();
}

        });
    }
    private void saveLoginInfo(String memberId, String password) {
    try (FileOutputStream fos = openFileOutput("login.txt", MODE_PRIVATE);
         OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
        writer.write(memberId + "\n" + password);
    } catch (IOException e) {
        e.printStackTrace();
     }
 }
 
 private boolean loadLoginInfo() {
    try (FileInputStream fis = openFileInput("login.txt");
         InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
         BufferedReader reader = new BufferedReader(isr)) {
        String memberId = reader.readLine();
        String password = reader.readLine();
        if (memberId != null && password != null) {
            runOnUiThread(() -> {
                memberIdInput.setText(memberId);
                passwordInput.setText(password);
            });
            return true; 
        }
    } catch (IOException e) {
        
    }
    return false;
  }

    private void getAkamaiToken() {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://d2.benesse.ne.jp/api");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-Api-Version", "1.0");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                CookieManager cm = (CookieManager) CookieHandler.getDefault();
                if (!cm.getCookieStore().getCookies().isEmpty()) {
                    StringBuilder cookieHeader = new StringBuilder();
                    for (HttpCookie cookie : cm.getCookieStore().getCookies()) {
                        cookieHeader.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
                    }
                    conn.setRequestProperty("Cookie", cookieHeader.toString());
                }
                conn.setDoOutput(true);
                String guid = UUID.randomUUID().toString();
                String jsonBody = "{\"jsonrpc\": \"2.0\", \"method\": \"common.akamaiToken.get\", \"id\": \"" + guid + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                String responseContent = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(responseContent);
                if (json.has("result")) {
                    JSONObject result = json.getJSONObject("result");
                    akamaiToken = result.optString("token", "");
                }
                if (akamaiToken.isEmpty()) {
                    handler.post(() -> Toast.makeText(MainActivity.this, "Token取得失敗", Toast.LENGTH_SHORT).show());
                    return;
                }
                try {
                    cm.getCookieStore().add(new URI("https://townak.benesse.ne.jp"),
                            new HttpCookie("town_akamai_token", akamaiToken));
                } catch (Exception ex) {
                }
                handler.post(() -> Toast.makeText(MainActivity.this, "Akamai Token取得成功", Toast.LENGTH_SHORT).show());
                handler.post(() -> {
                    loginLayout.setVisibility(View.GONE);
                    mainLayout.setVisibility(View.VISIBLE);
                });
                handler.post(() -> {
                    ProgressDialog pd = new ProgressDialog(MainActivity.this);
                    pd.setMessage("しばらくおまちください…");
                    pd.setCancelable(false);
                    pd.show();
                    executor.execute(() -> {
                        fetchAndMergeAllFirmwareLists();
                        handler.post(pd::dismiss);
                    });
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(MainActivity.this, "Token取得中にエラー", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        });
    }

    private String readStream(InputStream in) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private byte[] readAllBytes(InputStream in) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    private void fetchFirmwareList() {
        executor.execute(() -> {
            try {
                String akamaiType = "rel";
                String akamaiTable = "A";
                String akamaiListParam = "7";
                String akamaiInfo = "B";
                firmwareBaseUrl = "https://townak.benesse.ne.jp/" + akamaiType + "/" + akamaiTable + "/sp_84";
                String firmwareXmlUrl = firmwareBaseUrl + "/authorized/list/200" + akamaiListParam +
                        "/deliveryInfo_APL000" + akamaiInfo + ".xml";
                URL url = new URL(firmwareXmlUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/xml");
                if (!akamaiToken.isEmpty()) {
                    conn.setRequestProperty("Cookie", "town_akamai_token=" + akamaiToken);
                }
                byte[] xmlBytes = readAllBytes(conn.getInputStream());
                String utf8Content = new String(xmlBytes, StandardCharsets.UTF_8);
                List<String> list = parseFirmwareXml(utf8Content);
                synchronized (firmwareList) {
                    firmwareList.clear();
                    firmwareList.addAll(list);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e("FirmwareFetch", "エラー発生", e);
            }
        });
    }

    private void fetchAndMergeAllFirmwareLists() {
        try {
            File externalDir = new File(Environment.getExternalStorageDirectory(), "xml");
            if (!externalDir.exists() && !externalDir.mkdirs()) {
                handler.post(() -> Toast.makeText(MainActivity.this, "保存先ディレクトリの作成に失敗しました", Toast.LENGTH_SHORT).show());
                return;
            }
            File outFile = new File(externalDir, "allfwlist.xml");
            if (outFile.exists()) {
                handler.post(() -> Toast.makeText(MainActivity.this, "allfwlist.xmlが既に存在するため、保存処理をスキップしました", Toast.LENGTH_SHORT).show());
                proceedWithRelA7BCombination();
                return;
            }
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            final Document mergedDoc = builder.newDocument();
            Element root = mergedDoc.createElement("allDeliveryInfo");
            mergedDoc.appendChild(root);
            String[] akamaiTypes = {"rel", "rel2", "test", "test2", "test3", "test4", "dev"};
            String[] tables = {"A", "B"};
            String[] listParams = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
            String[] infoArray = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
            for (String table : tables) {
                List<Future<Void>> futures = new ArrayList<>();
                for (String type : akamaiTypes) {
                    String baseUrl = "https://townak.benesse.ne.jp/" + type + "/" + table + "/sp_84";
                    for (String listParam : listParams) {
                        for (String info : infoArray) {
                            final String xmlUrlStr = baseUrl + "/authorized/list/200" + listParam +
                                    "/deliveryInfo_APL000" + info + ".xml";
                            futures.add(executor.submit(new Callable<Void>() {
                                @Override
                                public Void call() {
                                    try {
                                        URL url = new URL(xmlUrlStr);
                                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                        conn.setRequestMethod("GET");
                                        conn.setRequestProperty("Content-Type", "application/xml");
                                        if (!akamaiToken.isEmpty()) {
                                            conn.setRequestProperty("Cookie", "town_akamai_token=" + akamaiToken);
                                        }
                                        byte[] xmlBytes = readAllBytes(conn.getInputStream());
                                        String utf8Content = new String(xmlBytes, StandardCharsets.UTF_8);
                                        Document doc = builder.parse(new ByteArrayInputStream(utf8Content.getBytes(StandardCharsets.UTF_8)));
                                        NodeList appNodes = doc.getElementsByTagName("application");
                                        synchronized (mergedDoc) {
                                            for (int i = 0; i < appNodes.getLength(); i++) {
                                                Node node = appNodes.item(i);
                                                Node importedNode = mergedDoc.importNode(node, true);
                                                root.appendChild(importedNode);
                                            }
                                        }
                                        conn.disconnect();
                                    } catch (Exception e) {
                                    }
                                    return null;
                                }
                            }));
                        }
                    }
                }
                for (Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                
                    }
                }
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(mergedDoc), new StreamResult(writer));
            String mergedXmlString = writer.toString();
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(mergedXmlString.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            handler.post(() -> Toast.makeText(MainActivity.this, "全ファーム一覧を保存しました：" + outFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
            proceedWithRelA7BCombination();
        } catch (Exception e) {
            handler.post(() -> Toast.makeText(MainActivity.this, "全ファーム一覧統合中にエラー", Toast.LENGTH_SHORT).show());
        }
    }

    private void proceedWithRelA7BCombination() {
        executor.execute(this::fetchFirmwareList);
    }

    private List<String> parseFirmwareXml(String xmlContent) {
        List<String> list = new ArrayList<>();
        try {
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
            NodeList appNodes = doc.getElementsByTagName("application");
            for (int i = 0; i < appNodes.getLength(); i++) {
                Node node = appNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element appElem = (Element) node;
                    String appId = getTagValue("appId", appElem);
                    String appDispName = getTagValue("appDispName", appElem);
                    String versionInfo = getTagValue("versionInfo", appElem);
                    String appName = getTagValue("appName", appElem);
                    String path = getTagValue("path", appElem);
                    String packageName = getTagValue("packageName", appElem);
                    String appVersion = getTagValue("appVersion", appElem);
                    String modelNo = getTagValue("modelNo", appElem);
                    String line = "appId:" + appId + ", appDispName:" + appDispName +
                            ", versionInfo:" + versionInfo + ", appName:" + appName +
                            ", path:" + path + ", packageName:" + packageName +
                            ", appVersion:" + appVersion + ", modelNo:" + modelNo;
                    list.add(line);
                }
            }
        } catch (Exception e) {
        }
        return list;
    }

    private String getTagValue(String tag, Element element) {
        NodeList nlList = element.getElementsByTagName(tag);
        if (nlList != null && nlList.getLength() > 0) {
            Node node = nlList.item(0);
            if (node != null && node.getFirstChild() != null) {
                return node.getFirstChild().getNodeValue();
            }
        }
        return "";
    }

    private void updateFirmwareListView() {
        Collections.sort(firmwareList, (s1, s2) -> {
            try {
                String version1 = extractVersionString(s1);
                String version2 = extractVersionString(s2);
                return compareVersionNumbers(version1, version2);
            } catch (Exception e) {
                Log.e("UpdateListView", "バージョンの比較エラー", e);
                return 0;
            }
        });
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, firmwareList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextSize(14);
                tv.setSingleLine(false);
                return tv;
            }
        };
        firmwareListView.setAdapter(adapter);
    }

    private String extractVersionString(String s) {
        Pattern pattern = Pattern.compile("appVersion:([^\\s]+)");
        Matcher matcher = pattern.matcher(s);
        return matcher.find() ? matcher.group(1) : "";
    }

    private int compareVersionNumbers(String v1, String v2) {
        List<Integer> numbers1 = extractNumbers(v1);
        List<Integer> numbers2 = extractNumbers(v2);
        int len = Math.max(numbers1.size(), numbers2.size());
        for (int i = 0; i < len; i++) {
            int num1 = (i < numbers1.size()) ? numbers1.get(i) : 0;
            int num2 = (i < numbers2.size()) ? numbers2.get(i) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    private List<Integer> extractNumbers(String version) {
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = Pattern.compile("(\\d+)").matcher(version);
        while (matcher.find()) {
            try {
                numbers.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
    
            }
        }
        return numbers;
    }

    private void downloadFirmware(String firmwareInfo) {
        int idx = firmwareInfo.indexOf("path:");
        if (idx == -1) {
            Toast.makeText(MainActivity.this, "ダウンロード対象のパスが見つかりません", Toast.LENGTH_SHORT).show();
            return;
        }
        String pathPartFull = firmwareInfo.substring(idx + 5).trim();
        int commaIdx = pathPartFull.indexOf(",");
        String pathPart = (commaIdx != -1) ? pathPartFull.substring(0, commaIdx).trim() : pathPartFull;
        String downloadUrlString;
        if (!pathPart.startsWith("https")) {
            String modifiedPath = pathPart.replace(" ", "").replace(":", "");
            if (!firmwareBaseUrl.endsWith("/") && !modifiedPath.startsWith("/")) {
                downloadUrlString = firmwareBaseUrl + "/" + modifiedPath;
            } else {
                downloadUrlString = firmwareBaseUrl + modifiedPath;
            }
        } else {
            downloadUrlString = pathPart;
        }
        try {
            URI uri = new URI(downloadUrlString);
            downloadUrlString = uri.toASCIIString();
        } catch (Exception e) {
            Log.e(TAG, "URIエンコードエラー", e);
        }
        final String finalDownloadUrlString = downloadUrlString;
        final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setMessage("ダウンロード中です...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(finalDownloadUrlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (!akamaiToken.isEmpty()) {
                    conn.setRequestProperty("Cookie", "town_akamai_token=" + akamaiToken);
                }
                conn.connect();
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    handler.post(() -> Toast.makeText(MainActivity.this, "ダウンロード失敗：HTTPコード " + responseCode, Toast.LENGTH_SHORT).show());
                    return;
                }
                try (InputStream in = conn.getInputStream()) {
                    final String finalFileName = finalDownloadUrlString.substring(finalDownloadUrlString.lastIndexOf('/') + 1);
                    String fileName = finalFileName.isEmpty() ? "downloaded_file" : finalFileName;
                    File downloadDir = new File(Environment.getExternalStorageDirectory(), "download");
                    if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                        handler.post(() -> Toast.makeText(MainActivity.this, "保存先ディレクトリの作成に失敗しました", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    File outputFile = new File(downloadDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                        fos.flush();
                    }
                    handler.post(() -> Toast.makeText(MainActivity.this, "ダウンロード完了：" + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(MainActivity.this, "ダウンロード中にエラーが発生しました", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                handler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                });
            }
        });
    }

    private void showFirmwareModelSelectionDialog() {
        final String[] models = {"CT2S", "CT2K", "CT2L", "CT3", "CTX", "CTZ"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("端末モデルを選択してください");
        builder.setItems(models, (dialog, which) -> {
            String selectedModel = models[which];
            Map<String, String> modelMapping = new HashMap<>();
            modelMapping.put("CT2S", "TAB-A03-BS");
            modelMapping.put("CT2K", "TAB-A03-BR");
            modelMapping.put("CT2L", "TAB-A03-BR2");
            modelMapping.put("CT3", "TAB-A03-BR3");
            modelMapping.put("CTX", "TAB-A05-BD");
            modelMapping.put("CTZ", "TAB-A05-BA1");
            String targetModelNo = modelMapping.get(selectedModel);
            if (targetModelNo != null) {
                filterAndDisplayFirmware(targetModelNo);
            }
        });
        builder.show();
    }

    private void filterAndDisplayFirmware(String targetModelNo) {
        executor.execute(() -> {
            try {
                File inFile = new File(Environment.getExternalStorageDirectory(), "xml/allfwlist.xml");
                if (!inFile.exists()) {
                    handler.post(() -> Toast.makeText(MainActivity.this, "allfwlist.xml が存在しません", Toast.LENGTH_SHORT).show());
                    return;
                }
                String xmlContent = readFileToString(inFile);
                List<String> fullList = parseFirmwareXml(xmlContent);
                List<String> filteredList = new ArrayList<>();
                boolean isCT2K = targetModelNo.equals("TAB-A03-BR");
                for (String item : fullList) {
                    if (item.contains("modelNo:" + targetModelNo)) {
                        if (isCT2K && (item.contains("TAB-A03-BR2") || item.contains("TAB-A03-BR3"))) {
                            continue;
                        }
                        filteredList.add(item);
                    }
                }
                synchronized (firmwareList) {
                    firmwareList.clear();
                    firmwareList.addAll(filteredList);
                }
                handler.post(this::updateFirmwareListView);
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(MainActivity.this, "ファーム抽出中にエラー", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showApkSelectionDialog() {
        final Map<String, String[]> apkMapping = new HashMap<>();
        apkMapping.put("Akapen Camera", new String[] {
                "AkapenCamera-chu_device_devRelease_11.1_1100100.apk",
                "AkapenCamera-chu_device_devRelease_11.6_1100600.apk",
                "AkapenCamera-chu_device_devRelease_11.8_1100800.apk",
                "NewDev.apk",
                "app-chu_device_dev-release.apk"
        });
        apkMapping.put("赤ペンカメラ", new String[] {
                "AkapenCamera-chu_device_honbanRelease_4.10_4010000.apk",
                "AkapenCamera-chu_device_honbanRelease_4.11_4011000.apk",
                "AkapenCamera-chu_device_honbanRelease_4.6_34.apk",
                "AkapenCamera-ct2.apk",
                "app-chu_device.apk",
                "app-chu_device_dev-release.apk"
        });
        apkMapping.put("CT2Lb指定用ダミーアプリ", new String[] {"CT2Lb_dummy.apk"});
        apkMapping.put("CT2S指定用ダミーアプリ", new String[] {"CT2S_dummy.apk"});
        apkMapping.put("キャリブレーション", new String[] {"CT2_DchaCalibration.apk"});
        apkMapping.put("UtilService(CT2)", new String[] {"CT2_DchaUtilService.apk"});
        apkMapping.put("カメラアルバム（CT2/3）", new String[] {"CT2_TouchCameraAlbum.apk"});
        apkMapping.put("UtilService（CTx）", new String[] {"CTX_DchaUtilService.apk"});
        apkMapping.put("UtilService（CTZ）", new String[] {"CTZ_DchaUtilService.apk"});
        apkMapping.put("ChallengeEnglish", new String[] {
                "ChallengeEnglishDC2CK.apk",
                "ChallengeEnglishDC2CK_5.0.9_0106.apk",
                "ChallengeEnglishDC2CK_607.0.0_0610.apk",
                "ChallengeEnglishDC2CK_6.0.20_1028.apk"
        });
        apkMapping.put("中ゼミ基盤向けCEアプリ", new String[] {
                "ChallengeEnglishDC2CK_6.0.0_0301.apk",
                "ChallengeEnglishDC2CK_6.0.45_0117.apk",
                "ChallengeEnglishDC2CK_600.0.2_0407.apk",
                "ChallengeEnglishDC2CK_607.0.116_0624.apk",
                "ChallengeEnglishDC2CK_607.0.120_0905.apk",
                "ChallengeEnglishDC2CK_608.0.4_0124.apk",
                "ChallengeEnglishDC2CK_7.0.0_0717.apk"
        });
        apkMapping.put("基礎トレアプリ", new String[] {"ChallengeEnglishDCCK.apk"});
        apkMapping.put("基礎トレアプリ19", new String[] {"ChallengeEnglishDCCK19.apk"});
        apkMapping.put("CE中高アプリ", new String[] {
                "ChallengeEnglishDCCK19.apk",
                "ChallengeEnglishDCCK19_7.0.0_0717.apk"
        });
        apkMapping.put("中ゼミ基盤向けCKアプリ", new String[] {
                "ChallengeEnglishDCCK19_5.0.221_0308.apk",
                "ChallengeEnglishDCCK19_5.0.224_0406.apk",
                "ChallengeEnglishDCCK19_6.0.205_0126.apk",
                "ChallengeEnglishDCCK19_607.0.37_0624.apk",
                "ChallengeEnglishDCCK19_607.0.3_0610.apk",
                "ChallengeEnglishDCCK19_607.0.43_0905.apk",
                "ChallengeEnglishDCCK19_608.0.4_0124.apk"
        });
        apkMapping.put("中2要点復習マスター", new String[] {"Chu2YoutenMaster.apk"});
        apkMapping.put("新アーキブラウザ", new String[] {"ChuTouchBrowser.apk", "ChuTouchBrowserGa4Rel.apk"});
        apkMapping.put("ブルーライトカット", new String[] {"DchaBluelightCutFilter.apk"});
        apkMapping.put("デバイス指定ありからなしのダミーアプリ", new String[] {"Dummy02.apk"});
        apkMapping.put("GigaCast録画ビューアー", new String[] {"GigaCastOnDemand_everes.apk", "GigaCastOnDemand_for_cp2.apk"});
        apkMapping.put("GigaCastビューアー", new String[] {"GigaCastMobile_for_cp2.apk", "GigaCastMobile_everes.apk"});
        apkMapping.put("歴史人物つながりバトルアプリ", new String[] {"K14SC056001000_CURL.apk"});
        apkMapping.put("タイピング英語", new String[] {"K14SC086026999_CURL.apk"});
        apkMapping.put("タイピングマスター", new String[] {"K14SC096008999_CURL.apk"});
        apkMapping.put("プログラミングワールド", new String[] {
                "K14SC103003000_CURL.apk",
                "K14SC103003000_CURL_ST_debug.apk",
                "XWALK_CTX_NoDebug.apk"
        });
        apkMapping.put("わり算ストライク", new String[] {"K14SC110101000_CURL.apk"});
        apkMapping.put("小数のわり算ロボグランプリ", new String[] {"K14SC110102000_CURL.apk"});
        apkMapping.put("にゃん者", new String[] {"K14SC123004000S6_CURL.apk"});
        apkMapping.put("高ゼミ用新アーキブラウザ", new String[] {"KzemiBrowser.apk"});
        apkMapping.put("ニガテシャットアウト総合", new String[] {"NSCommon.apk", "NigashaCommon.apk"});
        apkMapping.put("ニガテシャットアウト英語", new String[] {"NSEnglish.apk", "NigashaEnglish.apk"});
        apkMapping.put("ニガテシャットアウト古文", new String[] {"NigashaKobun.apk"});
        apkMapping.put("ニガテシャットアウト数学１次関数", new String[] {"NigashaMathFunc.apk"});
        apkMapping.put("ニガテシャットアウト１次関数", new String[] {"NigashaMathFunc.apk"});
        apkMapping.put("ニガテシャットアウト数学一次関数", new String[] {"NigashaMathFunc.apk"});
        apkMapping.put("ニガテシャットアウト数学", new String[] {"NigashaMathFunc.apk"});
        apkMapping.put("ニガテシャットアウト証明", new String[] {"NigashaMathProof.apk"});
        apkMapping.put("ニガテシャットアウト数学証明", new String[] {"NigashaMathProof.apk"});
        apkMapping.put("ニガテシャットアウト理科", new String[] {"NigashaRika.apk"});
        apkMapping.put("ニガテシャットアウト社会", new String[] {"NigashaShakai.apk"});
        apkMapping.put("CT2/3向けオンラインレッスンアプリ", new String[] {"OnlineLesson_MT6CT.apk", "onlinelesson_MT6CT_chu.apk"});
        apkMapping.put("辞書アプリ", new String[] {"PerfectStudy_eDic.1.0.5.apk", "PerfectStudy_eDic.apk"});
        apkMapping.put("演出アプリ", new String[] {"Precen.apk"});
        apkMapping.put("アルファベット書き方手帳アプリ", new String[] {"S6E1EigoTechou.apk", "S6E1EigoTechou_2.0.7.2.apk"});
        apkMapping.put("英語手帳アプリ", new String[] {"S6E1EigoTechou.apk"});
        apkMapping.put("英語4技能検定", new String[] {"fourskills_cbt_sho_test-release.apk"});
        apkMapping.put("楽★暗記", new String[] {"T17C105Zsp32.apk"});
        apkMapping.put("楽暗記遷移用アプリ", new String[] {"T17C105Zsp32Link.apk"});
        apkMapping.put("sp018英語音声認識", new String[] {"T17C108Esp18.apk"});
        apkMapping.put("sp087英語音声認識", new String[] {"T17K104Esp87.apk"});
        apkMapping.put("中学英語デビューアプリ", new String[] {"T18S601Esp124.apk"});
        apkMapping.put("アラーム", new String[] {"TouchAlarm.apk"});
        apkMapping.put("ANR送信サービス", new String[] {"TouchAnrSendService.apk"});
        apkMapping.put("非同期通信サービス", new String[] {"TouchAsyncCommService.apk"});
        apkMapping.put("認証サービス", new String[] {"TouchAuthService.apk"});
        apkMapping.put("ブラウザ", new String[] {"TouchBrowser.apk"});
        apkMapping.put("カメラ", new String[] {"TouchCamera.apk"});
        apkMapping.put("カメラアルバム対応", new String[] {"TouchCameraAlbum.apk"});
        apkMapping.put("通信サービス", new String[] {"TouchCommService.apk"});
        apkMapping.put("共通エラー", new String[] {"TouchCommonWarning.apk"});
        apkMapping.put("ホーム", new String[] {"TouchHome.apk"});
        apkMapping.put("どこでも復習アプリ", new String[] {"TouchOdekake.apk"});
        apkMapping.put("春休み復習アプリ", new String[] {"TouchSpringReview.apk"});
        apkMapping.put("UpdateService", new String[] {"TouchUpdateService.apk"});
        apkMapping.put("アップデートサービスアップデータ", new String[] {"TouchUpdateServiceUpdater.apk"});
        apkMapping.put("ユーザー設定", new String[] {"TouchUserSetting.apk"});
        apkMapping.put("WebView71", new String[] {"WebView71.apk", "WebViewGoogle_ver71_arm64.apk"});
        apkMapping.put("WebViewGoogleアプリ", new String[] {"WebViewGoogle.apk"});
        apkMapping.put("WebView83", new String[] {"WebViewGoogle_arm64.apk"});
        apkMapping.put("予想問アプリ", new String[] {"YosoumonApp.apk"});
        apkMapping.put("予想問デジタルアプリ", new String[] {"YosoumonDigital.apk"});
        apkMapping.put("要点復習マスター", new String[] {"YoutenMaster.apk"});
        apkMapping.put("赤ペンアプリ", new String[] {"app-chu_device_dev-release.apk"});
        apkMapping.put("赤ペン提出カメラアプリ", new String[] {"app-chu_device_dev.apk"});
        apkMapping.put("4技能カメラアプリ", new String[] {"AkapenCamera-chu_device_four_skills_honbanRelease_1.2_3.apk"});
        apkMapping.put("4技能検定対策テストカメラアプリ", new String[] {"app-chu_device_four_skills.apk"});
        apkMapping.put("答案提出カメラアプリ", new String[] {
                "app-chu_device_four_skills.apk",
                "app-chu_device_four_skills_dev-release.apk",
                "app-chu_device_four_skills_dev.apk"
        });
        apkMapping.put("カメラアプリ", new String[] {
                "app-chu_device_four_skills_dev-release.apk",
                "app-chu_device_four_skills_dev.apk"
        });
        apkMapping.put("4技能検定対策テスト受検アプリ", new String[] {
                "fourskills_cbt_chu_prd-release-1.2.0.apk",
                "fourskills_cbt_chu_prd.apk"
        });
        apkMapping.put("中学領域向け4技能検定対策テストアプリ", new String[] {
                "fourskills_cbt_chu_prd.apk",
                "fourskills_cbt_chu_test-release-1.1.0.apk",
                "fourskills_cbt_chu_test-release-1.2.0.apk",
                "fourskills_cbt_chu_test.apk"
        });
        apkMapping.put("GJOアプリケーション", new String[] {
                "fourskills_cbt_prd.apk",
                "fourskills_cbt_test.apk"
        });
        apkMapping.put("英語4技能検定", new String[] {
                "fourskills_cbt_sho_prd-release-1.2.0.apk",
                "fourskills_cbt_sho_test-release.apk"
        });
        apkMapping.put("小学領域向け4技能検定対策テストアプリ", new String[] {
                "fourskills_cbt_sho_prd.apk",
                "fourskills_cbt_sho_test-release-1.1.0.apk",
                "fourskills_cbt_sho_test-release-1.2.0.apk",
                "fourskills_cbt_sho_test.apk"
        });
        apkMapping.put("NovaLauncher", new String[] {"nova-launcher-5-5-3.apk"});
        apkMapping.put("プレ中用新アーキブラウザ", new String[] {"precBrowser.apk"});
        apkMapping.put("たま丸農場アプリ", new String[] {"tamamarufarm_CURL.apk"});
        apkMapping.put("紙活用アプリ", new String[] {"textpush.apk"});
        apkMapping.put("紙活用遷移用アプリ", new String[] {"textpushlink.apk"});
        apkMapping.put("オンラインレッスンCE", new String[] {"cemobile.apk", "dev_cemobile.apk"});
        apkMapping.put("新V5オンラインレッスン", new String[] {"app-release-signed_CT_1.1.0.0001.apk"});
        apkMapping.put("GTEC　Junior　Online", new String[] {"fourskills_cbt.apk"});
        apkMapping.put("双方向プログラミング", new String[] {"chuPGA_shk.apk"});
        apkMapping.put("計測制御プログラミング", new String[] {"chuPGA_ks.apk"});
        apkMapping.put("FY21暗記アプリ", new String[] {"chu2021anki.apk"});
        apkMapping.put("FY21英単語アプリ", new String[] {"chu2021ewords.apk"});
        apkMapping.put("FY22暗記アプリ", new String[] {"chu2022anki.apk"});
        apkMapping.put("ChallengeSchoolCT", new String[] {"app-production-release-signed.apk", "app-staging-release-signed.apk"});
        apkMapping.put("Clovaアプリ", new String[] {"czemi-Clova.apk"});
        apkMapping.put("VCUBEアプリ", new String[] {"app-release-signed_CT_1.1.1.0001.apk"});
        apkMapping.put("ネイティブアプリドライバ", new String[] {"NativeAppDriver.apk"});
        apkMapping.put("ネイティブアプリAirスタブ", new String[] {"NativeAppDriverAirStub.apk"});
        apkMapping.put("ネイティブアプリJavaスタブ", new String[] {"NativeAppDriverJavaStub.apk"});
        
        List<String> apkLabelsList = new ArrayList<>(apkMapping.keySet());
        apkLabelsList.sort((s1, s2) -> java.text.Collator.getInstance(Locale.JAPANESE).compare(s1, s2));
        final String[] apkLabels = apkLabelsList.toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("APKファイルを選択してください");
        builder.setItems(apkLabels, (dialog, which) -> {
            String selectedLabel = apkLabels[which];
            String[] apkPatterns = apkMapping.get(selectedLabel);
            if (apkPatterns != null) {
                filterAndDisplayApk(apkPatterns);
            }
        });
        builder.show();
    }

    private void filterAndDisplayApk(final String[] apkPatterns) {
        executor.execute(() -> {
            try {
                File inFile = new File(Environment.getExternalStorageDirectory(), "xml/allfwlist.xml");
                if (!inFile.exists()) {
                    handler.post(() -> Toast.makeText(MainActivity.this, "allfwlist.xml が存在しません", Toast.LENGTH_SHORT).show());
                    return;
                }
                String xmlContent = readFileToString(inFile);
                List<String> fullList = parseFirmwareXml(xmlContent);
                List<String> filteredList = new ArrayList<>();
                for (String item : fullList) {
                    for (String pattern : apkPatterns) {
                        if (item.contains(pattern)) {
                            filteredList.add(item);
                            break;
                        }
                    }
                }
                synchronized (firmwareList) {
                    firmwareList.clear();
                    firmwareList.addAll(filteredList);
                }
                handler.post(this::updateFirmwareListView);
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(MainActivity.this, "apk抽出中にエラー", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showFileListByExtension(final String extension) {
        executor.execute(() -> {
            try {
                File xmlFile = new File(Environment.getExternalStorageDirectory(), "xml/allfwlist.xml");
                if (!xmlFile.exists()) {
                    handler.post(() -> Toast.makeText(MainActivity.this, "allfwlist.xml が見つかりません", Toast.LENGTH_SHORT).show());
                    return;
                }
                String xmlContent = readFileToString(xmlFile);
                List<String> fullList = parseFirmwareXml(xmlContent);
                List<String> filteredList = new ArrayList<>();
                for (String item : fullList) {
                    if (item.contains(extension)) {
                        filteredList.add(item);
                    }
                }
                synchronized (firmwareList) {
                    firmwareList.clear();
                    firmwareList.addAll(filteredList);
                }
                handler.post(this::updateFirmwareListView);
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(MainActivity.this, "XML解析エラー", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String readFileToString(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
