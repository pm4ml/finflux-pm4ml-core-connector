package com.modusbox.client.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DataFormatUtils {

    public static boolean isJSONValid(String inputData) {
        try {
            new JSONObject(inputData);
        } catch (JSONException ex) {
            try {
                new JSONArray(inputData);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

}