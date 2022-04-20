package com.rfid.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.BuildConfig;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RFIDScanActivity extends Activity {
    private boolean loopFlag = false;
    private int inventoryFlag = 1;
    private Handler handler;
    private ArrayList<HashMap<String, String>> tagList;
    private SimpleAdapter adapter;

    private TextView tv_count;

    private RadioGroup RgInventory;
    private RadioButton RbInventorySingle;
    private RadioButton RbInventoryLoop;

    private Button BtClear;
    private Button BtImport;
    private Button BtInventory;
    private Button BtView;
    private Button btnSave;

    private ListView LvTags;
    private HashMap<String, String> map;
    private RFIDWithUHFUART mReader;

    private String fCurFilePath = "";
    private boolean fIsEmulator = false;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_rfid_scan);
            setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

            tagList = new ArrayList<HashMap<String, String>>();

            BtClear = (Button) findViewById(R.id.BtClear);
            BtImport = (Button) findViewById(R.id.BtImport);
            BtView = (Button) findViewById(R.id.BtView);

            tv_count = (TextView) findViewById(R.id.tv_count);
            RgInventory = (RadioGroup) findViewById(R.id.RgInventory);
            RbInventorySingle = (RadioButton) findViewById(R.id.RbInventorySingle);
            RbInventoryLoop = (RadioButton) findViewById(R.id.RbInventoryLoop);
            BtInventory = (Button) findViewById(R.id.BtInventory);
            LvTags = (ListView) findViewById(R.id.LvTags);
            btnSave=(Button) findViewById(R.id.btnSave);

            adapter = new SimpleAdapter(this, tagList, R.layout.listtag_items,
                    new String[]{"tagUii", "tagLen", "tagCount"},
                    new int[]{R.id.TvTagUii, R.id.TvTagLen, R.id.TvTagCount});

            BtClear.setOnClickListener(new BtClearClickListener());
            BtImport.setOnClickListener(new BtImportClickListener());
            RgInventory.setOnCheckedChangeListener(new RgInventoryCheckedListener());
            BtInventory.setOnClickListener(new BtInventoryClickListener());
            BtView.setOnClickListener(new BtViewClickListener());
            btnSave.setOnClickListener(new BtnSaveClickListener());

            LvTags.setAdapter(adapter);
            clearData();

            handler = new Handler() {
                @SuppressLint("HandlerLeak")
                @Override
                public void handleMessage(Message msg) {
                    String result = msg.obj + "";
                    String[] strs = result.split("@");
                    if (addEPCToList(strs[0], strs[1]))
                        UIHelper.playSoundSuccess();
                }
            };

            fIsEmulator = UIHelper.isEmulator();
            UIHelper.initSound(RFIDScanActivity.this);
            initUHF();
        } catch (Exception ex) {
            UIHelper.showExceptionError(RFIDScanActivity.this, ex);
        }
    }

    public void initUHF() {
        // temporary check this, on emulator device mReader InitTask cause crash application
        if (!fIsEmulator) {
            if (mReader == null) {
                try {
                    mReader = RFIDWithUHFUART.getInstance();
                } catch (Exception ex) {
                    UIHelper.showExceptionError(RFIDScanActivity.this, ex);
                    return;
                }

                if (mReader != null) {
                    new InitTask().execute();
                }
            }
        }
    }

    /**
     *
     * @author liuruifeng
     */
    private class InitTask extends AsyncTask<String, Integer, Boolean> {
        ProgressDialog mypDialog;

        @Override
        protected Boolean doInBackground(String... params) {
            // TODO Auto-generated method stub
            try {
                return mReader.init();
            }
            catch (Exception ex){
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            mypDialog.cancel();

            if (!result) {
                Toast.makeText(RFIDScanActivity.this, "init fail", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            try {
                super.onPreExecute();

                mypDialog = new ProgressDialog(RFIDScanActivity.this);
                mypDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mypDialog.setMessage("init...");
                mypDialog.setCanceledOnTouchOutside(false);
                mypDialog.show();

            } catch (Exception ex) {
                UIHelper.showExceptionError(RFIDScanActivity.this, ex);
                return;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        stopInventory();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 139 || keyCode == 280 || keyCode == 293) {
            if (event.getRepeatCount() == 0) {
                readTag();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     *
     */
    private boolean addEPCToList(String epc, String rssi) {
        if (!TextUtils.isEmpty(epc)) {
            int index = checkIsExist(epc);

            map = new HashMap<String, String>();
            map.put("tagUii", epc);
            map.put("tagCount", String.valueOf(1));
            map.put("tagRssi", rssi);

            if (index == -1) {
                tagList.add(map);
                LvTags.setAdapter(adapter);
                tv_count.setText("" + adapter.getCount());
            } else {
                int tagcount = Integer.parseInt(tagList.get(index).get("tagCount"), 10) + 1;

                map.put("tagCount", String.valueOf(tagcount));
                tagList.set(index, map);
            }

            adapter.notifyDataSetChanged();
            if (index >= 0)
                return false;

            return true;
        }
        return false;
    }

    private class BtClearClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            clearData();
        }
    }

    private class BtImportClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (BtInventory.getText().equals(getString(R.string.btInventory))) {
                if (tagList.size() == 0) {
                    UIHelper.ToastMessage(RFIDScanActivity.this, "No data");
                    return;
                }

                // save to SQL
                // boolean re = FileImport.SaveSQL(tagList, RFIDScanActivity.this);

                try {
                    // save excel file
                    boolean reXls = FileImport.SaveFileXls(tagList, "");
                    boolean re = FileImport.SaveFileTxt(tagList, ""); // save txt file
                    if (re) {
                        fCurFilePath = FileImport.FilePathTxt;
                        UIHelper.ToastMessage(RFIDScanActivity.this, getString(R.string.uhf_msg_inventory_save_success));
                        tv_count.setText("0");
                        tagList.clear();
                        adapter.notifyDataSetChanged();
                    }
                }
                catch (Exception ex){
                    UIHelper.showExceptionError(RFIDScanActivity.this, ex);
                }
            } else {
                UIHelper.ToastMessage(RFIDScanActivity.this, R.string.uhf_msg_inventory_save_wanrning);
            }
        }
    }

    private class BtViewClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (BtInventory.getText().equals(getString(R.string.btInventory))) {
//                if (UIHelper.isNullOrEmpty(fCurFilePath)) {
//                    UIHelper.ToastMessage(RFIDScanActivity.this, "No file!");
//                    return;
//                }
                Intent in = new Intent(RFIDScanActivity.this, RFIDViewActivity.class);
                in.putExtra("IntentObject", fCurFilePath);
                startActivity(in);
            } else {
                UIHelper.ToastMessage(RFIDScanActivity.this, R.string.uhf_msg_inventory_save_wanrning);
            }
        }
    }

    public String executePost(String targetURL) {
        int timeout=5000;
        URL url;
        HttpURLConnection connection = null;
        try {

            // Create connection
            url = new URL(targetURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
//            connection.setDoInput(true);
//            connection.setDoOutput(true);
//            connection.setConnectTimeout(timeout);
//            connection.setReadTimeout(timeout);

            // Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer response = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            return response.toString();

        } catch (SocketTimeoutException ex) {
            ex.printStackTrace();

        } catch (MalformedURLException ex) {
            System.out.println("Loi MalformedURLException");
        } catch (UnknownHostException e) {
            System.out.println("Loi UnknownHostException");
        } catch (IOException ex) {
            System.out.println("Loi IOException");
        } catch (Exception e) {
            System.out.println("Loi Exception");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
/*
    private void postDataUsingVolley(String productInstanceId, String ProductLineId) {
        // url to post our data
        String url = "http://192.168.9.168:8088/ProductInstance/AddProductInstance";


        // creating a new variable for our request queue
        RequestQueue queue = Volley.newRequestQueue(RFIDScanActivity.this);

        // on below line we are calling a string
        // request method to post the data to our API
        // in this we are calling a post method.
        StringRequest request = new StringRequest(Request.Method.POST, url, new com.android.volley.Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                // inside on response method we are
                // hiding our progress bar
                // and setting data to edit text as empty

                // on below line we are displaying a success toast message.
                Toast.makeText(RFIDScanActivity.this, "Data added to API", Toast.LENGTH_SHORT).show();
                try {
                    // on below line we are parsing the response
                    // to json object to extract data from it.
                    JSONObject respObj = new JSONObject(response);

                    // below are the strings which we
                    // extract from our json object.
                    String name = respObj.getString("name");
                    String job = respObj.getString("job");

                    // on below line we are setting this string s to our text view.
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new com.android.volley.Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // method to handle errors.
                Toast.makeText(RFIDScanActivity.this, "Fail to get response = " + error, Toast.LENGTH_SHORT).show();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                // below line we are creating a map for
                // storing our values in key and value pair.
                Map<String, String> params = new HashMap<String, String>();

                // on below line we are passing our key
                // and value pair to our parameters.
                params.put("product_intance_id", productInstanceId);
                params.put("product_line_id", ProductLineId);

                // at last we are
                // returning our params.
                return params;
            }
        };
        // below line is to make
        // a json object request.
        queue.add(request);
    }
*/
    private class BtnSaveClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            tagList.forEach((element) -> {
                //postDataUsingVolley(element.get("tagUii"),element.get("tagRssi"));
                /*
                String product_instance_id = element.get("tagUii");
                String product_line_id = element.get("tagRssi");
                String URL =  "http://localhost:64559/ProductInstance/AddProductInstance";
                JSONObject jsonBody = new JSONObject();
                try {
                    jsonBody.put("product_instance_id", element.get("tagUii"));
                    jsonBody.put("product_line_id", element.get("tagRssi"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                String response = executePost(URL);

                System.out.println("Du lieu tra ve: " +response);

               /*U String url = "http://192.168.9.168:8088/ProductInstance/AddProductInstance";
                CallApi c=new CallApi();
                c.doInBackground(url, element.get("TagUii"), element.get("TagRssi"));

                RequestQueue requestQueue = Volley.newRequestQueue(RFIDScanActivity.this);
                String URL =  "http://localhost:64559/ProductInstance/AddProductInstance";

                JSONObject jsonBody = new JSONObject();

                try {
                    jsonBody.put("product_instance_id", element.get("tagUii"));
                    jsonBody.put("product_line_id", element.get("tagRssi"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                final String requestBody = jsonBody.toString();

                StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.i("VOLLEY", response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("VOLLEY", error.toString());
                    }
                }) {
                    @Override
                    public String getBodyContentType() {
                        return "application/json; charset=utf-8";
                    }

                    @Override
                    public byte[] getBody() throws AuthFailureError {
                        try {
                            return requestBody == null ? null : requestBody.getBytes("utf-8");
                        } catch (UnsupportedEncodingException uee) {
                            VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                            return null;
                        }
                    }

                    @Override
                    protected Response<String> parseNetworkResponse(NetworkResponse response) {
                        String responseString = "";
                        if (response != null) {
                            responseString = String.valueOf(response.statusCode);
                            // can get more details such as response.headers
                        }
                        return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                   }
                };

                requestQueue.add(stringRequest);
                */
            });
        }
    }
    private void clearData() {
        tv_count.setText("0");
        tagList.clear();

        adapter.notifyDataSetChanged();
    }

    public class RgInventoryCheckedListener implements RadioGroup.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (checkedId == RbInventorySingle.getId()) {
                inventoryFlag = 0;
            } else if (checkedId == RbInventoryLoop.getId()) {
                inventoryFlag = 1;
            }
        }
    }

    public class BtInventoryClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            readTag();
        }
    }

    private void readTag() {
        if (BtInventory.getText().equals(getString(R.string.btInventory)))
        {
            if (mReader == null) {
                UIHelper.ToastMessage(RFIDScanActivity.this, R.string.uhf_msg_sdk_open_fail);
                return;
            }

            switch (inventoryFlag) {
                case 0:
                {
                    UHFTAGInfo strUII = mReader.inventorySingleTag();
                    if (strUII != null) {
                        String strEPC = strUII.getEPC();
                        addEPCToList(strEPC, strUII.getRssi());
                        UIHelper.playSoundSuccess();
                        tv_count.setText("" + adapter.getCount());
                    } else {
                        UIHelper.ToastMessage(RFIDScanActivity.this, R.string.uhf_msg_inventory_fail);
                    }
                }
                break;
                case 1://  .startInventoryTag((byte) 0, (byte) 0))
                {
                    if (mReader.startInventoryTag()) {
                        BtInventory.setText(getString(R.string.title_stop_Inventory));
                        loopFlag = true;
                        setViewEnabled(false);
                        new TagThread().start();
                    } else {
                        mReader.stopInventory();
                        UIHelper.ToastMessage(RFIDScanActivity.this, R.string.uhf_msg_inventory_open_fail);
                    }
                }
                break;
                default:
                    break;
            }
        } else {
            stopInventory();
        }
    }

    private void setViewEnabled(boolean enabled) {
        RbInventorySingle.setEnabled(enabled);
        RbInventoryLoop.setEnabled(enabled);
        BtClear.setEnabled(enabled);
    }

    private void stopInventory() {
        if (loopFlag) {
            loopFlag = false;
            setViewEnabled(true);
            if (mReader.stopInventory()) {
                BtInventory.setText(getString(R.string.btInventory));
            } else {
                UIHelper.ToastMessage(RFIDScanActivity.this, R.string.uhf_msg_inventory_stop_fail);
            }
        }
    }

    /**
     *
     * @param strEPC
     * @return
     */
    public int checkIsExist(String strEPC) {
        int existFlag = -1;
        if (strEPC == null || strEPC.length() == 0) {
            return existFlag;
        }
        String tempStr = "";
        for (int i = 0; i < tagList.size(); i++) {
            HashMap<String, String> temp = new HashMap<String, String>();
            temp = tagList.get(i);
            tempStr = temp.get("tagUii");
            if (strEPC.equals(tempStr)) {
                existFlag = i;
                break;
            }
        }
        return existFlag;
    }

    private class TagThread extends Thread {
        public void run() {
            String strTid;
            String strResult;
            UHFTAGInfo res = null;
            while (loopFlag) {
                res = mReader.readTagFromBuffer();
                if (res != null) {
                    strTid = res.getTid();
                    if (strTid.length() != 0 && !strTid.equals("0000000" + "000000000") && !strTid.equals("000000000000000000000000")) {
                        strResult = "TID:" + strTid + "\n";
                    } else {
                        strResult = "";
                    }

                    Message msg = handler.obtainMessage();
                    msg.obj = strResult + res.getEPC() + "@" + res.getRssi();

                    handler.sendMessage(msg);
                }
            }
        }
    }


}
