package com.example.bot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.awt.image.BufferedImage;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

public class QRParser {

    public String parseQRCode(BufferedImage bufferedImage) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            com.google.zxing.Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private final Gson gson = new Gson();

    public ParsedQRData parseQRData(String qrData) {
        try {
            JsonObject jsonObject = gson.fromJson(qrData, JsonObject.class);
            String appName = jsonObject.get("app").getAsString();
            String shipmentNumber = jsonObject.get("shipmentId").getAsString(); // Изменение для получения shipmentId
            return new ParsedQRData(appName, shipmentNumber);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}