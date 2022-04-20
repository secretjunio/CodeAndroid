package com.rfid.app;

import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class CallApi extends AsyncTask<String, String, Void> {

    public CallApi(){
        //set context variables if required
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Void doInBackground(@NonNull String... params) {
        String urlString = params[0]; // URL to call
        String product_intance_id = params[1]; //data to post
        String product_line_id=params[2];

        OutputStream out = null;

        try {
            URL url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            out = new BufferedOutputStream(urlConnection.getOutputStream());

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
            writer.write(product_intance_id);
            writer.write(product_line_id);
            writer.flush();
            writer.close();
            out.close();

            urlConnection.connect();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }
}